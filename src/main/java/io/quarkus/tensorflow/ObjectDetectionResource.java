package io.quarkus.tensorflow;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.core.eventbus.EventBus;
import org.apache.commons.imaging.ImageReadException;
import org.jboss.resteasy.annotations.SseElementType;
import org.jboss.resteasy.plugins.providers.multipart.InputPart;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;

@Path("/object")
public class ObjectDetectionResource {

    @Inject
    ObjectDetectionService objectDetectionService;

    @Inject
    EventBus eventBus;

    @GET
    @Path("/detect")
    @Produces(MediaType.APPLICATION_JSON)
    public ObjectDetectionResultComplete detectFromURL(@QueryParam("image") String imageURL) {
        ObjectDetectionResultComplete resultComplete = null;

        try {
            URL url = new URL(imageURL);

            try {
                resultComplete = objectDetectionService.detect(url);

                final JsonObject jsonObject = JsonObject.mapFrom(resultComplete);
                eventBus.publish("result_stream", jsonObject);
            }
            catch(IOException | ImageReadException | MediaTypeException e) {
                resultComplete = new ObjectDetectionResultComplete();
                resultComplete.setError("Error reading image data. Please try another file.");
            }
            catch(Exception e) {
                resultComplete = new ObjectDetectionResultComplete();
                resultComplete.setError(e.getMessage());
            }
            finally {
                resultComplete.setFileName(url.getFile());
            }
        }
        catch (MalformedURLException e) {
            resultComplete = new ObjectDetectionResultComplete();
            resultComplete.setError(e.getMessage());
        }

        return resultComplete;
    }

    @Inject
    Vertx vertx;

    @POST
    @Path("/detect/{threshold}")
    @Consumes("multipart/form-data")
    @Produces("application/json")
    public ObjectDetectionResultComplete loadImage(@HeaderParam("Content-Length") String contentLength, @PathParam("threshold") int threshold, MultipartFormDataInput input) {
        final InputPart inputPart = input.getFormDataMap().get("file").iterator().next();
        final String fileName = parseFileName(inputPart.getHeaders());

        ObjectDetectionResultComplete resultComplete = null;
        try {
            InputStream is = inputPart.getBody(InputStream.class, null);
            resultComplete = objectDetectionService.detect(is, threshold); // Very slow call on first request
            resultComplete.setFileName(fileName);

            final JsonObject jsonObject = JsonObject.mapFrom(resultComplete);
            eventBus.publish("result_stream", jsonObject);
        }
        catch (IOException | ImageReadException | MediaTypeException | URISyntaxException e) {
            resultComplete = new ObjectDetectionResultComplete();
            resultComplete.setFileName(fileName);
            resultComplete.setError("Error reading image data. Please try another file.");
        }
        return resultComplete;
    }

    @GET
    @Path("/labels")
    @Produces(MediaType.TEXT_PLAIN)
    public List<String> labels(@QueryParam("image") String imageURL) {
        return Arrays.asList(objectDetectionService.getLabels());
    }

    @GET
    @Path("/stream")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @SseElementType(MediaType.APPLICATION_JSON)
    public Multi<JsonObject> stream()
    {
        return eventBus.<JsonObject>consumer("result_stream").toMulti().map(b -> b.body());
    }

    @GET
    @Path("/data")
    @Produces(MediaType.TEXT_PLAIN)
    public String getImageData(@QueryParam("uuid") String uuid){
        return objectDetectionService.getImageData(uuid); // Will return 204 No Content if cache miss
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