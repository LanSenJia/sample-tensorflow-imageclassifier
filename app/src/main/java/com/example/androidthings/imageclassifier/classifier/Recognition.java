package com.example.androidthings.imageclassifier.classifier;

/**
 * An immutable result returned by a Classifier describing what was recognized.
 */
public class Recognition {
    /**
     * A unique identifier for what has been recognized. Specific to the class, not the instance of
     * the object.
     * 已识别的唯一标识符。具体到类，而不是*对象的实例
     */
    private final String id;

    /**
     * Display name for the recognition.
     * 显示识别的名称。
     */
    private final String title;

    /**
     * A sortable score for how good the recognition is relative to others. Higher should be better.
     * 关于识别与其他人相比有多好的可分类分数。更高应该更好。
     */
    private final Float confidence;

    public Recognition(
            final String id, final String title, final Float confidence) {
        this.id = id;
        this.title = title;
        this.confidence = confidence;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public Float getConfidence() {
        return confidence == null ? 0f : confidence;
    }

    @Override
    public String toString() {
        String resultString = "";
        if (id != null) {
            resultString += "[" + id + "] ";
        }

        if (title != null) {
            resultString += title + " ";
        }

        if (confidence != null) {
            resultString += String.format("(%.1f%%) ", confidence * 100.0f);
        }

        return resultString.trim();
    }
}
