package io.quarkus.tensorflow;

import com.google.common.io.ByteStreams;
import com.google.common.io.CharStreams;
import com.google.protobuf.TextFormat;
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
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@ApplicationScoped
public class ObjectDetectionService {

    private static final String LABEL_RESOURCE_PATH = "labels/mscoco_label_map.pbtxt";
    private static final String MODEL_FILE_PATH = "saved_model/saved_model.pb";

    private Session session;
    private String[] labels;

    public ObjectDetectionService() throws IOException, ReflectiveOperationException {
        InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(MODEL_FILE_PATH);

        byte[] modelBytes = ByteStreams.toByteArray(is);
        this.labels = loadLabels();

        SavedModel savedModel = SavedModel.parseFrom(modelBytes);
        GraphDef graphDef = savedModel.getMetaGraphsList().get(0).getGraphDef();

        forceInitTensorFlow();
        Graph graph = new Graph();
        graph.importGraphDef(graphDef.toByteArray());
        this.session = new Session(graph);
    }

    public String[] getLabels() {
        return labels;
    }

    public ObjectDetectionResultComplete detect(URL url) throws Exception {
        byte[] rawData = downloadFile(url);
        return detect(rawData, 50);
    }

    public ObjectDetectionResultComplete detect(InputStream inputStream, int threshold) throws Exception {
        byte[] rawData = ByteStreams.toByteArray(inputStream);
        return detect(rawData, threshold);
    }

    public ObjectDetectionResultComplete detect(byte[] rawData, int threshold) throws Exception {
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

            String label = labels[(int) classes[object]];
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
        objectDetectionResultComplete.setBase64EncodedData(Base64.getEncoder().encodeToString(rawData));
        objectDetectionResultComplete.setWidth(imageInfo.getWidth());
        objectDetectionResultComplete.setHeight(imageInfo.getHeight());

        return objectDetectionResultComplete;
    }

    private static String mediaType(ImageInfo imageInfo) throws Exception {
        String extension = imageInfo.getFormat().getExtension();
        switch(extension) {
            case "GIF":
                return "image/gif";
            case "JPEG":
                // code block
                return "image/jpg";
            case "PNG":
                return "image/png";
            default:
                throw new Exception(extension + " format type is unsupported for the MediaType!");
        }
    }

    private static String[] loadLabels() throws IOException {
        InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(LABEL_RESOURCE_PATH);
        String text = CharStreams.toString( new InputStreamReader( is) );
        StringIntLabelMapOuterClass.StringIntLabelMap.Builder builder = StringIntLabelMapOuterClass.StringIntLabelMap.newBuilder();
        TextFormat.merge(text, builder);
        StringIntLabelMapOuterClass.StringIntLabelMap proto = builder.build();
        int maxId = 0;
        for (StringIntLabelMapOuterClass.StringIntLabelMapItem item : proto.getItemList()) {
            if (item.getId() > maxId) {
                maxId = item.getId();
            }
        }
        String[] ret = new String[maxId + 1];
        for (StringIntLabelMapOuterClass.StringIntLabelMapItem item : proto.getItemList()) {
            ret[item.getId()] = item.getDisplayName();
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
     * Download a file from a URL and return the data as a byte[].
     * @param url The URL here the file exists.
     * @return byte[] representing the data in the file.
     * @throws IOException
     */
    private static byte[] downloadFile(URL url) throws IOException {
        URLConnection conn = url.openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.connect();

        InputStream is = conn.getInputStream();
        return ByteStreams.toByteArray(is);
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
     * This is a GraalVM workaround for static blocks not being executed at runtime in SubstrateVM. Since TensorFlow uses
     * static blocks to initialize, and the init API is not public, this method offers a new way to access TensorFlow.init.
     * The alternative is to use --initialize-at-run-time=org.tensorflow.Graph but this takes away compile time benefits
     * and also prevents initialization of TensorFlow objects inside a constructor.
     * @link https://medium.com/graalvm/understanding-class-initialization-in-graalvm-native-image-generation-d765b7e4d6ed
     * @see org.tensorflow.TensorFlow
     * @see org.tensorflow.Graph
     */
    private void forceInitTensorFlow() throws ReflectiveOperationException {
        Method tfInit = Class.forName("org.tensorflow.TensorFlow").getDeclaredMethod("init");
        tfInit.setAccessible(true);
        tfInit.invoke(null);
    }
}