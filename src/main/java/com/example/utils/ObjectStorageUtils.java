package com.example.utils;

import com.example.resources.FileOperationResourceOc10;
import com.example.resources.FileStreamMetadata;
import com.oracle.bmc.objectstorage.ObjectStorageClient;
import com.oracle.bmc.objectstorage.requests.GetObjectRequest;
import com.oracle.bmc.objectstorage.requests.PutObjectRequest;
import com.oracle.bmc.objectstorage.responses.GetObjectResponse;
import com.oracle.bmc.objectstorage.responses.PutObjectResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ObjectStorageUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(ObjectStorageUtils.class);
    private final String namespaceName;
    private final ObjectStorageClient objectStorageClient;

    ExecutorService executorService = Executors.newFixedThreadPool(10);

    public ObjectStorageUtils(String namespaceName, ObjectStorageClient objectStorageClient) {
        this.namespaceName = namespaceName;
        this.objectStorageClient = objectStorageClient;
    }
    // Helper method to download a file from a specified bucket
    public FileStreamMetadata downloadFromObjectStorage(String bucketName, String fileName) throws ExecutionException, InterruptedException {
        return executorService.submit(() -> {
            try {
                GetObjectRequest request = GetObjectRequest.builder()
                        .namespaceName(namespaceName)
                        .bucketName(bucketName)
                        .objectName(fileName)
                        .build();
                LOGGER.info("File Download starting...");
                long starttime = System.currentTimeMillis();
                GetObjectResponse response = objectStorageClient.getObject(request);
                long timeTaken = System.currentTimeMillis() - starttime;
                LOGGER.info("downloadFromObjectStorage: File Download completed. timeTaken (milli):"+timeTaken);
                LOGGER.info("downloadFromObjectStorage:File md5:"+response.getContentMd5());
                return new FileStreamMetadata(new BufferedInputStream(response.getInputStream()),response.getContentMd5());
            } catch (Exception e) {
                LOGGER.error("Error downloading from OCI Object Storage: " + e.getMessage());
                return null;
            }
        }).get();

    }

    // Helper method to upload a file to a specified bucket
    public boolean uploadToObjectStorage(InputStream inputStream, String bucketName, String fileName, String srcMd5) throws ExecutionException, InterruptedException {
        return executorService.submit(() -> {
            boolean uploadSuccessful = false;
            BufferedInputStream bis = new BufferedInputStream(inputStream);
            try {
                PutObjectRequest request = PutObjectRequest.builder()
                        .namespaceName(namespaceName)
                        .bucketName(bucketName)
                        .objectName(fileName)
                        .putObjectBody(bis)
                        .build();

                LOGGER.info("File upload starting...");
                long startTime = System.currentTimeMillis();

                PutObjectResponse response = objectStorageClient.putObject(request);
                uploadSuccessful = srcMd5 != null? response.getOpcContentMd5().equals(srcMd5) : response.getOpcContentMd5() != null;
                LOGGER.info("uploadToObjectStorage: file md5:{}", response.getOpcContentMd5());

                long timeTaken = System.currentTimeMillis() - startTime;
                LOGGER.info("File upload completed . status: {} timeTaken(milli):{}", Optional.of(uploadSuccessful), Optional.of(timeTaken));

            } catch (Exception e) {
                LOGGER.error("Error uploading to OCI Object Storage: {}", e.getMessage(), e);

            }
            return (Boolean) uploadSuccessful;
        }).get();

    }

    // Helper method to save a file to local disk
    public boolean saveToLocalDisk(InputStream inputStream, String filePath) throws ExecutionException, InterruptedException {
        return executorService.submit(()-> {
            try (OutputStream outputStream = new FileOutputStream(filePath)) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                return Boolean.TRUE;
            } catch (IOException e) {
                LOGGER.error("Error saving file to local disk: " + e.getMessage());
                return Boolean.FALSE;
            } finally {
                try {
                    if (inputStream != null) {
                        inputStream.close();
                    }
                } catch (IOException e) {
                    LOGGER.error("Error closing input stream: " + e.getMessage());
                }
            }
        }).get();

    }


}
