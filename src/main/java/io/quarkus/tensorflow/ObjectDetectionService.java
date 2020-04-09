package io.quarkus.tensorflow;

import com.google.common.io.ByteStreams;
import com.google.common.io.CharStreams;
import com.google.protobuf.TextFormat;
import io.quarkus.cache.CacheResult;
import object_detection.protos.StringIntLabelMapOuterClass;
import org.apache.commons.imaging.ImageInfo;
import org.apache.commons.imaging.ImageReadException;
import org.apache.commons.imaging.Imaging;
import org.tensorflow.Graph;
import org.tensorflow.Session;
import org.tensorflow.Tensor;
import org.tensorflow.framework.GraphDef;
import org.tensorflow.framework.SavedModel;
import org.tensorflow.types.UInt8;

import javax.enterprise.context.ApplicationScoped;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@ApplicationScoped
public class ObjectDetectionService {

    private static final String LABEL_RESOURCE_PATH = "labels/mscoco_label_map.pbtxt";
    private static final String MODEL_FILE_PATH = "saved_model/saved_model.pb";

    private Session session;
    private String[] labels;

    private Map<String, String> imageData = new HashMap<>();

    public ObjectDetectionService() throws IOException, ReflectiveOperationException, URISyntaxException {
        InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(MODEL_FILE_PATH);

        byte[] modelBytes = ByteStreams.toByteArray(is);
        this.labels = loadLabels();

        SavedModel savedModel = SavedModel.parseFrom(modelBytes);
        GraphDef graphDef = savedModel.getMetaGraphsList().get(0).getGraphDef();

        LoadTensorFlow.load();

        Graph graph = new Graph();
        graph.importGraphDef(graphDef.toByteArray());
        this.session = new Session(graph);
    }

    @CacheResult(cacheName = "labels")
    public List<String> getLabels() {
        List<String> labelsList = Arrays.asList(labels);
        labelsList = labelsList.stream().filter(Objects::nonNull).collect(Collectors.toList());
        Collections.sort(labelsList);
        return labelsList;
    }

    public ObjectDetectionResultComplete detect(InputStream inputStream, int threshold) throws IOException, URISyntaxException, ImageReadException, MediaTypeException {
        byte[] rawData = ByteStreams.toByteArray(inputStream);
        return detect(rawData, threshold);
    }

    public ObjectDetectionResultComplete detect(byte[] rawData, int threshold) throws IOException, ImageReadException, URISyntaxException, MediaTypeException {
        // Get metadata about the image from the raw bytes
        ImageInfo imageInfo = Imaging.getImageInfo(rawData);

        List<Tensor<?>> outputs = null;

        Tensor<UInt8> input = makeImageTensor(rawData, imageInfo);
        outputs= session.runner().feed("image_tensor", input)
                .fetch("detection_scores")
                .fetch("detection_classes")
                .fetch("detection_boxes").run();

        Tensor<Float> scoresT = outputs.get(0).expect(Float.class);
        Tensor<Float> classesT = outputs.get(1).expect(Float.class);
        Tensor<Float> boxesT = outputs.get(2).expect(Float.class);

        // All these tensors have: 1 as the first dimension, maxObjects as the second dimension
        // Boxes will have 4 as the third dimension (2 sets of (x, y) coordinates).
        int maxObjects = (int) scoresT.shape()[1];
        float[] scores = scoresT.copyTo(new float[1][maxObjects])[0];
        float[] classes = classesT.copyTo(new float[1][maxObjects])[0];
        float[][] boxes = boxesT.copyTo(new float[1][maxObjects][4])[0];

        List<ObjectDetectionResult> results = new ArrayList<>();

        for(int object = 0; object < maxObjects; object++) {
            float minScore = threshold / 100f;

            if (scores[object] < minScore) {
                continue;
            }

            String label = labels[(int) classes[object] - 1];
            float score = scores[object];

            float y1 = boxes[object][0];
            float x1 = boxes[object][1];
            float y2 = boxes[object][2];
            float x2 = boxes[object][3];

            ObjectDetectionResult result = new ObjectDetectionResult(label, score, x1, y1, x2, y2);
            results.add(result);
        }

        ObjectDetectionResultComplete objectDetectionResultComplete = new ObjectDetectionResultComplete();
        objectDetectionResultComplete.setResults(results);
        objectDetectionResultComplete.setMediaType(mediaType(imageInfo));
        objectDetectionResultComplete.setWidth(imageInfo.getWidth());
        objectDetectionResultComplete.setHeight(imageInfo.getHeight());

        // Encode the data and add to the cache with a UUID reference
        byte[] base64Data = Base64.getEncoder().encode(rawData);
        String uuid = UUID.randomUUID().toString();
        imageData.put(uuid, new String(base64Data));
        objectDetectionResultComplete.setUuid(uuid);

        return objectDetectionResultComplete;
    }

    private static String mediaType(ImageInfo imageInfo) throws MediaTypeException {
        String extension = imageInfo.getFormat().getExtension();
        switch(extension) {
            case "GIF":
                return "image/gif";
            case "JPEG":
                return "image/jpg";
            case "PNG":
                return "image/png";
            default:
                throw new MediaTypeException(extension + " format type is unsupported for the MediaType!");
        }
    }

    private static String[] loadLabels() throws IOException {
        InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(LABEL_RESOURCE_PATH);
        String text = CharStreams.toString( new InputStreamReader( is) );
        StringIntLabelMapOuterClass.StringIntLabelMap.Builder builder = StringIntLabelMapOuterClass.StringIntLabelMap.newBuilder();
        TextFormat.merge(text, builder);
        StringIntLabelMapOuterClass.StringIntLabelMap proto = builder.build();
        int maxId = 1;
        for (StringIntLabelMapOuterClass.StringIntLabelMapItem item : proto.getItemList()) {
            if (item.getId() > maxId) {
                maxId = item.getId();
            }
        }
        String[] ret = new String[maxId];
        for (StringIntLabelMapOuterClass.StringIntLabelMapItem item : proto.getItemList()) {
            ret[item.getId()-1] = item.getDisplayName();
        }
        return ret;
    }

    private static Tensor<UInt8> makeImageTensor(byte[] rawData, ImageInfo imageInfo) throws IOException, URISyntaxException, ImageReadException {
        // Load the image from the raw bytes
        BufferedImage img = Imaging.getBufferedImage(rawData);
        // Get the image as an array of RGB integer values
        int[] rgbInts = ((DataBufferInt) img.getData().getDataBuffer()).getData();
        // Convert RGB values into byte[3] representation
        byte[] rgbBytes = convertRGBstoBytes(rgbInts);
        // Convert the RGB bytes to a ByteBuffer
        ByteBuffer byteBuffer = ByteBuffer.wrap(rgbBytes);

        final long BATCH_SIZE = 1;
        final long CHANNELS = 3;
        long[] shape = new long[]{BATCH_SIZE, imageInfo.getHeight(), imageInfo.getWidth(), CHANNELS};
        return Tensor.create(UInt8.class, shape, byteBuffer);
    }

    /**
     * Convert an array of RBG values as byte[3] representing the RGB values.
     * @param rgbs Array of RGB values representing an image
     * @return Converted RGB values as bytes
     */
    private static byte[] convertRGBstoBytes(int[] rgbs) {
        byte[] byteData = new byte[rgbs.length * 3];
        int count = 0;
        for (int rgb : rgbs) {
            byte r = (byte) (rgb & 0xff);
            byte g = (byte) ((rgb & 0xff00) >> 8);
            byte b = (byte) ((rgb & 0xff0000) >> 16);
            byte[] bytes = new byte[]{r, g, b};
            for (byte rgbByte : bytes) {
                byteData[count] = rgbByte;
                count++;
            }
        }
        return byteData;
    }

    /**
     * This is a workaround as SSE chunks are being limited to 8192 bytes. Cannot fit image data into the chunk, so we
     * instead need to make a callback for it. Opened a feature request to make the chunk size limit configurable:
     * https://github.com/quarkusio/quarkus/issues/8379
     * This feature request may not be honored if there is a performance impact on large chunk sizes, and so this
     * workaround would be reasonable.
     * @param uuid Reference to the image data in the cache
     * @return
     */
    @CacheResult(cacheName = "image-data")
    public String getImageData(String uuid){
        String imageDataVal = imageData.get(uuid);
        imageData.remove(uuid); // Remove from the Map, as the value will now reside in the cache instead
        return imageDataVal; // First result will be stored in the cache for the uuid key
    }
}