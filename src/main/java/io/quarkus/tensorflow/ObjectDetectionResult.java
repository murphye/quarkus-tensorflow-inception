package io.quarkus.tensorflow;

public class ObjectDetectionResult {

    private String label;
    private float score;
    private float x1;
    private float y1;
    private float x2;
    private float y2;

    public ObjectDetectionResult(String label, float score, float x1, float y1, float x2, float y2) {
        this.label = label;
        this.score = score;
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
    }

    public String getLabel() {
        return label;
    }

    public float getScore() {
        return score;
    }

    public float getX1() {
        return x1;
    }

    public float getY1() {
        return y1;
    }

    public float getX2() {
        return x2;
    }

    public float getY2() {
        return y2;
    }
}
