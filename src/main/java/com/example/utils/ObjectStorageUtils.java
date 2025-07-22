package com.example.utils;

import com.oracle.bmc.objectstorage.ObjectStorageClient;
import com.oracle.bmc.objectstorage.requests.GetObjectRequest;
import com.oracle.bmc.objectstorage.requests.PutObjectRequest;
import com.oracle.bmc.objectstorage.responses.GetObjectResponse;
import com.oracle.bmc.objectstorage.responses.PutObjectResponse;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class ObjectStorageUtils {

    private final String namespaceName;
    private final ObjectStorageClient objectStorageClient;

    public ObjectStorageUtils(String namespaceName, ObjectStorageClient objectStorageClient) {
        this.namespaceName = namespaceName;
        this.objectStorageClient = objectStorageClient;
    }
    // Helper method to download a file from a specified bucket
    public InputStream downloadFromObjectStorage(String bucketName, String fileName) {
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .namespaceName(namespaceName)
                    .bucketName(bucketName)
                    .objectName(fileName)
                    .build();
            System.out.println("File Download starting...");
            long starttime = System.currentTimeMillis();
            GetObjectResponse response = objectStorageClient.getObject(request);
            long timeTaken = System.currentTimeMillis() - starttime;
            System.out.println("downloadFromObjectStorage: timeTaken (milli):"+timeTaken);
            System.out.println("downloadFromObjectStorage:File md5:"+response.getContentMd5());
            return response.getInputStream();
        } catch (Exception e) {
            System.err.println("Error downloading from OCI Object Storage: " + e.getMessage());
            return null;
        }
    }

    // Helper method to upload a file to a specified bucket
    public boolean uploadToObjectStorage(InputStream inputStream, String bucketName, String fileName) {
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .namespaceName(namespaceName)
                    .bucketName(bucketName)
                    .objectName(fileName)
                    .putObjectBody(inputStream)
                    .build();

            System.out.println("File upload starting...");
            long starttime = System.currentTimeMillis();

            PutObjectResponse response = objectStorageClient.putObject(request);
            boolean uploadSuccessful = response.getOpcContentMd5() != null;
            System.out.println("uploadToObjectStorage: file md5:"+response.getOpcContentMd5());

            long timeTaken = System.currentTimeMillis() - starttime;
            System.out.println("File upload completed . status: "+uploadSuccessful+" timeTaken(milli):"+timeTaken);
            return uploadSuccessful;
        } catch (Exception e) {
            System.err.println("Error uploading to OCI Object Storage: " + e.getMessage());
            return false;
        }
    }

    // Helper method to save a file to local disk
    public boolean saveToLocalDisk(InputStream inputStream, String filePath) {
        try (OutputStream outputStream = new FileOutputStream(filePath)) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            return true;
        } catch (IOException e) {
            System.err.println("Error saving file to local disk: " + e.getMessage());
            return false;
        } finally {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (IOException e) {
                System.err.println("Error closing input stream: " + e.getMessage());
            }
        }
    }


}
