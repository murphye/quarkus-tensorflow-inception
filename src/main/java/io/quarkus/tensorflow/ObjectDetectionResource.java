package io.quarkus.tensorflow;

import com.google.protobuf.TextFormat;
import com.google.protobuf.TextFormat.ParseException;
import object_detection.protos.StringIntLabelMapOuterClass.StringIntLabelMap;
import object_detection.protos.StringIntLabelMapOuterClass.StringIntLabelMapItem;
import org.apache.commons.imaging.ImageInfo;
import org.apache.commons.imaging.ImageReadException;
import org.apache.commons.imaging.Imaging;
import org.tensorflow.SavedModelBundle;
import org.tensorflow.Tensor;
import org.tensorflow.types.UInt8;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.util.List;

@Path("/detect")
public class ObjectDetectionResource {

    SavedModelBundle model;

    String[] labels;

    public ObjectDetectionResource() throws ParseException {
        this.model = SavedModelBundle
                .load(System.getProperty("user.dir") + "/models/ssd_inception_v2_coco_2017_11_17/saved_model", "serve");

        this.labels = loadLabels("labels/mscoco_label_map.pbtxt");
    }

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String hello(@QueryParam("image") String imageURL) throws Exception {

        URL url = new URL(imageURL);

        List<Tensor<?>> outputs = null;
        try (Tensor<UInt8> input = makeImageTensor(url)) {
            outputs = this.model.session().runner().feed("image_tensor", input).fetch("detection_scores")
                    .fetch("detection_classes").fetch("detection_boxes").run();
        }
        try (Tensor<Float> scoresT = outputs.get(0).expect(Float.class);
             Tensor<Float> classesT = outputs.get(1).expect(Float.class);
             Tensor<Float> boxesT = outputs.get(2).expect(Float.class)) {

            // All these tensors have:
            // - 1 as the first dimension
            // - maxObjects as the second dimension
            // While boxesT will have 4 as the third dimension (2 sets of (x, y)
            // coordinates).
            // This can be verified by looking at scoresT.shape() etc.
            int maxObjects = (int) scoresT.shape()[1];
            float[] scores = scoresT.copyTo(new float[1][maxObjects])[0];
            float[] classes = classesT.copyTo(new float[1][maxObjects])[0];
            float[][] boxes = boxesT.copyTo(new float[1][maxObjects][4])[0];

            // Print all objects whose score is at least 0.5.
            boolean foundSomething = false;
            for (int i = 0; i < scores.length; ++i) {
                if (scores[i] < 0.5) {
                    continue;
                }
                foundSomething = true;
                return String.format("\tFound %-20s (score: %.4f) boxes: " + boxes, labels[(int) classes[i]],
                        scores[i]);
            }
            if (!foundSomething) {
                return "No objects detected with a high enough score.";
            }
        }
        return null;
    }

    public static String convertStreamToString(InputStream is) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();

        String line = null;

        try {
            while ((line = reader.readLine()) != null) {
                sb.append(line + "\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return sb.toString();
    }

    private static String[] loadLabels(String filename) throws ParseException {

        String text = convertStreamToString(ClassLoader.getSystemResourceAsStream(filename));

        StringIntLabelMap.Builder builder = StringIntLabelMap.newBuilder();
        TextFormat.merge(text, builder);
        StringIntLabelMap proto = builder.build();
        int maxId = 0;
        for (StringIntLabelMapItem item : proto.getItemList()) {
            if (item.getId() > maxId) {
                maxId = item.getId();
            }
        }
        String[] ret = new String[maxId + 1];
        for (StringIntLabelMapItem item : proto.getItemList()) {
            ret[item.getId()] = item.getDisplayName();
        }
        return ret;
    }

    static byte[] getBytes(String fileName) throws FileNotFoundException, IOException, URISyntaxException {
        byte[] buffer = new byte[4096];

        InputStream initialStream = ClassLoader.getSystemResource(fileName).toURI().toURL().openStream();

        BufferedInputStream bis = new BufferedInputStream(initialStream);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int bytes = 0;
        while ((bytes = bis.read(buffer, 0, buffer.length)) > 0) {
            baos.write(buffer, 0, bytes);
        }

        baos.flush();
        byte[] byteArray = baos.toByteArray();
        baos.close();
        bis.close();

        return byteArray;
    }

    public static byte[] downloadFile(URL url) throws IOException {
        URLConnection conn = url.openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.connect();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        try {
            byte[] chunk = new byte[4096];
            int bytesRead;
            InputStream stream = conn.getInputStream();

            while ((bytesRead = stream.read(chunk)) > 0) {
                outputStream.write(chunk, 0, bytesRead);
            }

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        return outputStream.toByteArray();
    }

    private static Tensor<UInt8> makeImageTensor(URL url) throws IOException, URISyntaxException, ImageReadException {
        byte[] rawData = downloadFile(url);

        ImageInfo imageInfo = Imaging.getImageInfo(rawData);
        BufferedImage img = Imaging.getBufferedImage(rawData);
        int[] imgData = ((DataBufferInt) img.getData().getDataBuffer()).getData();

        final long BATCH_SIZE = 1;
        final long CHANNELS = 3;
        long[] shape = new long[]{BATCH_SIZE, imageInfo.getHeight(), imageInfo.getWidth(), CHANNELS};
        return Tensor.create(UInt8.class, shape, ByteBuffer.wrap(convertRGBstoBytes(imgData)));
    }

    public static byte[] convertRGBstoBytes(int[] rgbs) {
        byte[] byteData = new byte[rgbs.length * 3];
        int count = 0;
        for (int rgb : rgbs) {
            byte[] bytes = convertRGBtoBytes(rgb);
            for (byte b : bytes) {
                byteData[count] = b;
                count++;
            }
        }
        return byteData;
    }

    /**
     * Convert an RGB value represented as an int to a byte[3] representing the same
     * RGB value.
     */
    public static byte[] convertRGBtoBytes(int rgb) {
        byte r = (byte) (rgb & 0xff);
        byte g = (byte) ((rgb & 0xff00) >> 8);
        byte b = (byte) ((rgb & 0xff0000) >> 16);
        return new byte[]{r, g, b};
    }

}