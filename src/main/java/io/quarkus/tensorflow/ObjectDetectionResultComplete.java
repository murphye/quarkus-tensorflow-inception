package io.quarkus.tensorflow;

import java.util.List;

public class ObjectDetectionResultComplete {

    private String fileName;
    private List<ObjectDetectionResult> results;

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
}
