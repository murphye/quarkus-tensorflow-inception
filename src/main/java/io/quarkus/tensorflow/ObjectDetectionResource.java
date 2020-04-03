package io.quarkus.tensorflow;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.core.eventbus.EventBus;
import org.jboss.resteasy.annotations.SseElementType;
import org.jboss.resteasy.plugins.providers.multipart.InputPart;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

@Path("/object")
public class ObjectDetectionResource {

    @Inject
    ObjectDetectionService objectDetectionService;

    @Inject
    EventBus eventBus;

    @Inject
    Vertx vertx;

    private Map<String, String> imageData = new HashMap<>();

    @GET
    @Path("/data")
    @Produces(MediaType.TEXT_PLAIN)
    public String getImageData(@QueryParam("uuid") String uuid){
        return imageData.get(uuid);
    }

    @GET
    @Path("/detect")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<ObjectDetectionResultComplete> detectFromURL(@QueryParam("image") String imageURL) {
        ObjectDetectionResultComplete resultComplete = null;

        try {
            URL url = new URL(imageURL);

            try {
                resultComplete = objectDetectionService.detect(url);
                String uuidRef = UUID.randomUUID().toString();
                imageData.put(uuidRef, resultComplete.getData());
                resultComplete.setData(uuidRef);
            }
            catch(Exception e) {
                e.printStackTrace();
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

        final JsonObject jsonObject = JsonObject.mapFrom(resultComplete);
        eventBus.publish("result_stream", jsonObject.encode());

        return Uni.createFrom().item(resultComplete);
    }

    @POST
    @Path("/detect/{threshold}")
    @Consumes("multipart/form-data")
    @Produces("application/json")
    public Uni<ObjectDetectionResultComplete> loadImage(@HeaderParam("Content-Length") String contentLength, @PathParam("threshold") int threshold, MultipartFormDataInput input) {
        InputPart inputPart = input.getFormDataMap().get("file").iterator().next();
        String fileName = parseFileName(inputPart.getHeaders());
        ObjectDetectionResultComplete resultComplete = null;

        try {
            InputStream is = inputPart.getBody(InputStream.class, null);
            resultComplete = objectDetectionService.detect(is, threshold);
            String uuidRef = UUID.randomUUID().toString();
            imageData.put(uuidRef, resultComplete.getData());
            resultComplete.setData(uuidRef);
        }
        catch (Exception e) {
            e.printStackTrace();
            resultComplete = new ObjectDetectionResultComplete();
            resultComplete.setError("Error reading image data. Please try another file.");
        }
        finally {
            resultComplete.setFileName(fileName);
        }

        final JsonObject jsonObject = JsonObject.mapFrom(resultComplete);
        eventBus.publish("result_stream", jsonObject.encode());

        return Uni.createFrom().item(resultComplete);
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
    public Multi<String> stream()
    {
        return eventBus.<String>consumer("result_stream").toMulti().map(b -> {
            return b.body();
        });
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