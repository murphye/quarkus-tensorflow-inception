package io.quarkus.tensorflow;

import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;

public class ObjectDetectionUtil {

    /**
     * Read in an InputStream until no more lines, then return the full String value.
     * @param is The InputStream
     * @return The full String output of the InputStream
     */
    static String convertStreamToString(InputStream is) {
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

    /**
     * Download a file from a URL and return the data as a byte[].
     * @param url The URL here the file exists.
     * @return byte[] representing the data in the file.
     * @throws IOException
     */
    public static byte[] downloadFile(URL url) throws IOException {
        URLConnection conn = url.openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.connect();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        InputStream inputStream = conn.getInputStream();

        try {
            byte[] chunk = new byte[4096];
            int bytesRead;

            while ((bytesRead = inputStream.read(chunk)) > 0) {
                outputStream.write(chunk, 0, bytesRead);
            }
        }
        finally {
            inputStream.close();
        }

        byte[] bytes = outputStream.toByteArray();
        outputStream.close();

        return bytes;
    }

    /**
     * Convert an array of RBG values (representing an image) as byte[3] representing the RGB values.
     * @param rgbs
     * @return
     */
    static byte[] convertRGBstoBytes(int[] rgbs) {
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
     * Convert an RGB value represented as an int to a byte[3] representing the same RGB value.
     */
    static byte[] convertRGBtoBytes(int rgb) {
        byte r = (byte) (rgb & 0xff);
        byte g = (byte) ((rgb & 0xff00) >> 8);
        byte b = (byte) ((rgb & 0xff0000) >> 16);
        return new byte[]{r, g, b};
    }

    /**
     * Convert an InputStream to byte[] when the buffer is empty.
     * @param inputStream InputStream with the data to be converted to byte[]
     * @return byte[] of all data received before the buffer was emptied
     * @throws FileNotFoundException
     * @throws IOException
     * @throws URISyntaxException
     */
    static byte[] getBytes(InputStream inputStream) throws FileNotFoundException, IOException, URISyntaxException {
        byte[] buffer = new byte[4096];

        BufferedInputStream bis = new BufferedInputStream(inputStream);
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
}
