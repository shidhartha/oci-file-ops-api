package com.example.utils;

import com.example.resources.FileOperationResourceOc10;
import com.example.resources.FileStreamMetadata;
import com.example.resources.MultipartUploadResult;
import com.oracle.bmc.objectstorage.ObjectStorageClient;
import com.oracle.bmc.objectstorage.model.CommitMultipartUploadDetails;
import com.oracle.bmc.objectstorage.model.CommitMultipartUploadPartDetails;
import com.oracle.bmc.objectstorage.requests.GetObjectRequest;
import com.oracle.bmc.objectstorage.requests.PutObjectRequest;
import com.oracle.bmc.objectstorage.responses.*;
import com.oracle.bmc.objectstorage.model.CreateMultipartUploadDetails;
import com.oracle.bmc.objectstorage.model.MultipartUploadPartSummary;
import com.oracle.bmc.objectstorage.requests.CreateMultipartUploadRequest;
import com.oracle.bmc.objectstorage.requests.UploadPartRequest;
import com.oracle.bmc.objectstorage.requests.CommitMultipartUploadRequest;
import com.oracle.bmc.objectstorage.requests.AbortMultipartUploadRequest;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ObjectStorageUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(ObjectStorageUtils.class);


    private @Getter final String namespaceName;

    private @Getter final ObjectStorageClient objectStorageClient;

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

                return new FileStreamMetadata(response.getInputStream(),response.getContentMd5(), response.getContentLength());

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
//            BufferedInputStream bis = new BufferedInputStream(inputStream);
            try {
                PutObjectRequest request = PutObjectRequest.builder()
                        .namespaceName(namespaceName)
                        .bucketName(bucketName)
                        .objectName(fileName)
                        .putObjectBody(inputStream)
                        .build();

                LOGGER.info("File upload starting...");
                long startTime = System.currentTimeMillis();

                PutObjectResponse response = objectStorageClient.putObject(request);
                LOGGER.info("Filename: {}, srcMd5:{}, destMd5:{}", fileName, srcMd5, response.getOpcContentMd5());
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

    public String initiateMultipartUpload(String bucketName, String objectName) {
        try {
            CreateMultipartUploadDetails details = CreateMultipartUploadDetails.builder()
                    .object(objectName)
                    .build();

            CreateMultipartUploadRequest request = CreateMultipartUploadRequest.builder()
                    .namespaceName(namespaceName)
                    .bucketName(bucketName)
                    .createMultipartUploadDetails(details)
                    .build();

            LOGGER.info("Initiating multipart upload for object: {}", objectName);
            CreateMultipartUploadResponse response = objectStorageClient.createMultipartUpload(request);
            return response.getMultipartUpload().getUploadId();
        } catch (Exception e) {
            LOGGER.error("Error initiating multipart upload: {}", e.getMessage(), e);
            return null;
        }
    }

    public MultipartUploadResult uploadParts(InputStream inputStream, String bucketName, String objectName, String uploadId, long fileSize, long partSize) {
        try {
            List<CommitMultipartUploadPartDetails> parts = new ArrayList<>();
            List<byte[]> partMd5Hashes = new ArrayList<>();
            byte[] buffer = new byte[(int) partSize];
            long bytesReadTotal = 0;
            int partNumber = 1;

            LOGGER.info("Starting multipart upload for object: {}, partSize: {}", objectName, partSize);

            while (bytesReadTotal < fileSize) {
                int totalBytesReadForPart = 0;
                // Accumulate data up to partSize or until no more data is available
                while (totalBytesReadForPart < partSize && bytesReadTotal < fileSize) {
                    int bytesRead = inputStream.read(buffer, totalBytesReadForPart, (int) (partSize - totalBytesReadForPart));
                    if (bytesRead == -1) break;
                    totalBytesReadForPart += bytesRead;
                    bytesReadTotal += bytesRead;
                }

                if (totalBytesReadForPart > 0) {
                    // Calculate MD5 hash of the part
                    byte[] md5Hash = calculateMD5(buffer, 0, totalBytesReadForPart);
//                    LOGGER.info("Calculated MD5 for part {} of object {}: {}", partNumber, objectName, md5Hash);
                    partMd5Hashes.add(md5Hash);

                    ByteArrayInputStream partStream = new ByteArrayInputStream(buffer, 0, totalBytesReadForPart);
                    UploadPartRequest request = UploadPartRequest.builder()
                            .namespaceName(namespaceName)
                            .bucketName(bucketName)
                            .objectName(objectName)
                            .uploadId(uploadId)
                            .uploadPartNum(partNumber)
                            .uploadPartBody(partStream)
                            .contentLength((long) totalBytesReadForPart)
                            .build();

                    LOGGER.info("Uploading part {} for object: {}, size: {}", partNumber, objectName, totalBytesReadForPart);
                    UploadPartResponse response = objectStorageClient.uploadPart(request);

                    LOGGER.info("Uploaded part {} for object {}, ETag: {}", partNumber, objectName, response.getETag());

                    // Store the ETag and part number for commit
                    parts.add(CommitMultipartUploadPartDetails.builder()
                            .partNum(partNumber)
                            .etag(response.getETag())
                            .build());

                    partNumber++;
                } else {
                    break;
                }
            }

            LOGGER.info("All parts uploaded for object: {}, total parts: {}", objectName, partNumber - 1);

            //calculateLocalyMultipartUploadMd5Hash(partMd5Hashes);

            return new MultipartUploadResult(true, parts);
        } catch (Exception e) {
            LOGGER.error("Error uploading parts for object: {}, error: {}", objectName, e.getMessage(), e);
            return new MultipartUploadResult(false, new ArrayList<>());
        }
    }

    private String calculateLocalyMultipartUploadMd5Hash(List<byte[]> partMd5Hashes) {
        //compute the final multi-part upload md5
        int length = partMd5Hashes.stream().mapToInt((md5Hash) -> md5Hash.length).sum();
        byte[] allMd5Hashes = new byte[length];
        int position = 0;
        for (byte[] md5Hash : partMd5Hashes) {
            System.arraycopy(md5Hash, 0, allMd5Hashes, position, md5Hash.length);
            position += md5Hash.length;
        }
        byte[] digest = calculateMD5(allMd5Hashes, 0, allMd5Hashes.length);
        String multipartMd5Hash = Base64.getEncoder().encodeToString(digest);
        LOGGER.info("Calculated multipart md5Hash: {}", multipartMd5Hash);
        return multipartMd5Hash;
    }

    // Method to calculate MD5 hash of a byte array segment
    private byte[] calculateMD5(byte[] data, int offset, int length) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(data, offset, length);
            byte[] digest = md.digest();
            return Base64.getEncoder().encode(digest);
        } catch (Exception e) {
            LOGGER.error("Error calculating MD5 hash: {}", e.getMessage(), e);
            return null;
        }
    }
    public boolean completeMultipartUpload(String bucketName, String objectName, String uploadId, List<CommitMultipartUploadPartDetails> parts, long fileSize) {
        try {
            // Build the commit details with the list of parts
            CommitMultipartUploadDetails details = CommitMultipartUploadDetails.builder()
                    .partsToCommit(parts)
                    .build();

            CommitMultipartUploadRequest request = CommitMultipartUploadRequest.builder()
                    .namespaceName(namespaceName)
                    .bucketName(bucketName)
                    .objectName(objectName)
                    .uploadId(uploadId)
                    .commitMultipartUploadDetails(details)
                    .build();

            LOGGER.info("Completing multipart upload for object: {}", objectName);
            CommitMultipartUploadResponse multipartUploadResponse = objectStorageClient.commitMultipartUpload(request);

            GetObjectResponse getObjectResponse = objectStorageClient.getObject(
                    GetObjectRequest.builder()
                            .namespaceName(namespaceName)
                            .bucketName(bucketName)
                            .objectName(objectName)
                            .build());
            LOGGER.info("Multipart upload completed for object. Get object completed for object: {}, sourceSize: {} and destination size:{}", objectName, fileSize,getObjectResponse.getContentLength());


            if (fileSize > 0) {
                return fileSize == getObjectResponse.getContentLength();
            } else {
                return getObjectResponse.getContentLength() > 0;
            }

        } catch (Exception e) {
            LOGGER.error("Error completing multipart upload for object: {}, error: {}", objectName, e.getMessage(), e);
            return false;
        }
    }

    public boolean abortMultipartUpload(String bucketName, String objectName, String uploadId) {
        try {
            AbortMultipartUploadRequest request = AbortMultipartUploadRequest.builder()
                    .namespaceName(namespaceName)
                    .bucketName(bucketName)
                    .objectName(objectName)
                    .uploadId(uploadId)
                    .build();

            LOGGER.info("Aborting multipart upload for object: {}", objectName);
            objectStorageClient.abortMultipartUpload(request);
            return true;
        } catch (Exception e) {
            LOGGER.error("Error aborting multipart upload for object: {}, error: {}", objectName, e.getMessage(), e);
            return false;
        }
    }

}
