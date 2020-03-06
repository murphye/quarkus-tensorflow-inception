package io.quarkus.tensorflow;

import com.google.protobuf.TextFormat;
import org.apache.commons.imaging.ImageReadException;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
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
        List<ObjectDetectionResult> result;

        try {
            URL url = new URL(imageURL);
            byte[] rawData = ObjectDetectionUtil.downloadFile(url);

            result = objectDetectionService.detect(rawData);
        }
        catch(MalformedURLException mue) {
            result = null;
        }
        catch(IOException ioe) {
            result = null;
        }
        catch(ImageReadException ire) {
            result = null;
        }
        catch (URISyntaxException use) {
            result = null;
        }

        return result;
    }

    @GET
    @Path("/labels")
    @Produces(MediaType.TEXT_PLAIN)
    public List<String> labels(@QueryParam("image") String imageURL) {
        return Arrays.asList(objectDetectionService.getLabels());
    }
}