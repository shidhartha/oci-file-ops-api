package com.example.resources;

public class CompleteUploadResult {
    private final boolean success;
    private final String finalETag;

    public CompleteUploadResult(boolean success, String finalETag) {
        this.success = success;
        this.finalETag = finalETag;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getFinalETag() {
        return finalETag;
    }
}