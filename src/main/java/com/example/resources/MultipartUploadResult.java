package com.example.resources;

import com.oracle.bmc.objectstorage.model.CommitMultipartUploadPartDetails;

import java.util.List;

// Store parts information during upload
public class MultipartUploadResult {
    private final boolean success;
    private final List<CommitMultipartUploadPartDetails> parts;


    public MultipartUploadResult(boolean success, List<CommitMultipartUploadPartDetails> parts) {
        this.success = success;
        this.parts = parts;
    }

    public boolean isSuccess() {
        return success;
    }

    public List<CommitMultipartUploadPartDetails> getParts() {
        return parts;
    }

}