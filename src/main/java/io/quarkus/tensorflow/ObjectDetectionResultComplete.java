package io.quarkus.tensorflow;

import java.util.List;

public class ObjectDetectionResultComplete {

    private String fileName;
    private List<ObjectDetectionResult> results;
    private String mediaType;
    private String base64EncodedData;

    private int width;
    private int height;
    private String error;

    public List<ObjectDetectionResult> getResults() {
        return results;
    }

    public void setResults(List<ObjectDetectionResult> results) {
        this.results = results;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getMediaType() {
        return mediaType;
    }

    public void setMediaType(String mediaType) {
        this.mediaType = mediaType;
    }

    public String getBase64EncodedData() {
        return base64EncodedData;
    }

    public void setBase64EncodedData(String base64EncodedData) {
        this.base64EncodedData = base64EncodedData;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
