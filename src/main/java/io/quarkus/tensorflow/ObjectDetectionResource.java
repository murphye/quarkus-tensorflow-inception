package io.quarkus.tensorflow;

import org.apache.commons.imaging.ImageReadException;
import org.jboss.resteasy.plugins.providers.multipart.InputPart;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Path("/object")
public class ObjectDetectionResource {

    @Inject
    ObjectDetectionService objectDetectionService;

    @GET
    @Path("/detect")
    @Produces(MediaType.APPLICATION_JSON)
    public List<ObjectDetectionResult> detectFromURL(@QueryParam("image") String imageURL) {
        List<ObjectDetectionResult> result = null;

        try {
            URL url = new URL(imageURL);
            result = objectDetectionService.detect(url);
        }
        catch(IOException | URISyntaxException | ImageReadException mue) {
            mue.printStackTrace();
        }

        return result;
    }

    @POST
    @Path("/detect/{threshold}")
    @Consumes("multipart/form-data")
    @Produces("application/json")
    public List<ObjectDetectionResult> loadImage(@HeaderParam("Content-Length") String contentLength, @PathParam("threshold") int threshold, MultipartFormDataInput input) {
        InputPart inputPart = input.getFormDataMap().get("file").iterator().next();
        String fileName = parseFileName(inputPart.getHeaders());

        List<ObjectDetectionResult> result = null;
        try {
            InputStream is = inputPart.getBody(InputStream.class, null);
            result = objectDetectionService.detect(is, threshold);
        }
        catch (IOException | ImageReadException | URISyntaxException e) {
            e.printStackTrace();
            result = new ArrayList<>();
            result.add(new ObjectDetectionResult("Error reading image data. Please try another file.", -1, 0, 0 , 0, 0));
        }

        return result;
    }

    @GET
    @Path("/labels")
    @Produces(MediaType.TEXT_PLAIN)
    public List<String> labels(@QueryParam("image") String imageURL) {
        return Arrays.asList(objectDetectionService.getLabels());
    }

    /**
     * Parse Content-Disposition header to get the original file name.
     */
    private static String parseFileName(MultivaluedMap<String, String> headers) {
        String[] contentDispositionHeader = headers.getFirst("Content-Disposition").split(";");
        for (String name : contentDispositionHeader) {
            if ((name.trim().startsWith("filename"))) {
                String[] tmp = name.split("=");
                String fileName = tmp[1].trim().replaceAll("\"", "");
                return fileName;
            }
        }
        return "randomName";
    }
}