package com.example.resources;

import com.example.utils.ObjectStorageUtils;
import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import com.oracle.bmc.objectstorage.ObjectStorageClient;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Path("/oc10")
@Produces(MediaType.APPLICATION_JSON)
public class FileOperationResourceOc10 {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileOperationResourceOc10.class);
    private final ObjectStorageUtils objectStorageUtils;

    // ExecutorService for async processing
    private final ExecutorService executorService = Executors.newCachedThreadPool();


    public FileOperationResourceOc10() {
        try {
            AuthenticationDetailsProvider provider =
                    new ConfigFileAuthenticationDetailsProvider("~/.oci/config", "oc10-ap-dcc-canberra-1-test-user-hpt");
            ObjectStorageClient objectStorageClient = new ObjectStorageClient(provider);
            // Replace with your OCI namespace
            String namespaceName = "axrkmdznll4i";
            this.objectStorageUtils = new ObjectStorageUtils(namespaceName, objectStorageClient);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize OCI Object Storage client: " + e.getMessage(), e);
        }
    }

    // Existing endpoint for file upload
    @POST
    @Path("/upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response uploadFile(
            @FormDataParam("file") InputStream uploadedInputStream,
            @FormDataParam("file") FormDataContentDisposition fileDetail) {
        try {
            String fileName = fileDetail.getFileName();
            long fileSize = fileDetail.getSize();

            if (fileName == null || fileName.isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("File name is required.")
                        .build();
            }

            String bucketName = "test-src-bucket"; // Replace with your default source bucket if needed
            boolean uploadSuccessful = this.objectStorageUtils.uploadToObjectStorage(uploadedInputStream, bucketName, fileName, null);

            if (uploadSuccessful) {
                return Response.status(Response.Status.OK)
                        .entity("File " + fileName + " uploaded successfully to OCI Object Storage.")
                        .build();
            } else {
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity("Failed to upload file to OCI Object Storage.")
                        .build();
            }
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Error uploading file: " + e.getMessage())
                    .build();
        }
    }

    // Existing endpoint for copying a file between buckets
    @POST
    @Path("/copy")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response copyFile(@QueryParam("sourceBucket") String sourceBucket,
                             @QueryParam("sourceFile") String sourceFile,
                             @QueryParam("destBucket") String destBucket,
                             @QueryParam("destFile") String destFile) {
        try {
            if (sourceBucket == null || sourceBucket.isEmpty() ||
                    sourceFile == null || sourceFile.isEmpty() ||
                    destBucket == null || destBucket.isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("Source bucket, source file, and destination bucket are required.")
                        .build();
            }

            String destinationFileName = (destFile != null && !destFile.isEmpty()) ? destFile : sourceFile;

            FileStreamMetadata metadata = this.objectStorageUtils.downloadFromObjectStorage(sourceBucket, sourceFile);
            if (metadata.getInputStream() == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity("File " + sourceFile + " not found in source bucket " + sourceBucket)
                        .build();
            }


            boolean uploadSuccessful = this.objectStorageUtils.uploadToObjectStorage(metadata.getInputStream(), destBucket, destinationFileName, metadata.getMd5Hash());


            if (uploadSuccessful) {
                return Response.status(Response.Status.OK)
                        .entity("File copied successfully from " + sourceBucket + "/" + sourceFile +
                                " to " + destBucket + "/" + destinationFileName)
                        .build();
            } else {
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity("Failed to upload file to destination bucket " + destBucket)
                        .build();
            }
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Error copying file: " + e.getMessage())
                    .build();
        }
    }

    // POST API to upload a file to OCI Object Storage
    @POST
    @Path("/uploadFile")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public void uploadFileToOCI(
            @FormDataParam("file") InputStream fileInputStream,
            @QueryParam("bucketName") String bucketName,
            @QueryParam("objectName") String objectName,
            @QueryParam("md5") String srcMd5,
            @Suspended AsyncResponse asyncResponse) {
        executorService.submit(() -> {
            try(InputStream fis = fileInputStream) {
                // Validate input parameters
                if (fis == null) {
                    asyncResponse.resume(Response.status(Response.Status.BAD_REQUEST)
                            .entity("File input stream is required.")
                            .build());
                }
                if (bucketName == null || bucketName.isEmpty()) {
                    asyncResponse.resume(Response.status(Response.Status.BAD_REQUEST)
                            .entity("Bucket name is required.")
                            .build());
                }

                if (objectName == null || objectName.isEmpty()) {
                    asyncResponse.resume(Response.status(Response.Status.BAD_REQUEST)
                            .entity("File name or object name is required.")
                            .build());
                }

                String newObjectName = objectName+"_"+new java.util.Date().getTime();

                // Upload the file to OCI Object Storage
                boolean uploadSuccessful = this.objectStorageUtils.uploadToObjectStorage(fis, bucketName, newObjectName, srcMd5);

                if (uploadSuccessful) {
                    asyncResponse.resume(Response.status(Response.Status.OK)
                            .entity("File " + newObjectName + " uploaded successfully to bucket " + bucketName + " in OCI Object Storage.")
                            .build());
                } else {
                    asyncResponse.resume(Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                            .entity("Failed to upload file to OCI Object Storage.")
                            .build());
                }
            } catch (Exception e) {
                asyncResponse.resume(Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity("Error uploading file: " + e.getMessage())
                        .build());
            }
        });
    }

    @POST
    @Path("/uploadFileMultipart")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public void uploadFileMultipart(
            @FormDataParam("file") InputStream uploadedInputStream,
            @FormDataParam("file") FormDataContentDisposition fileDetail,
            @QueryParam("bucketName") String bucketName,
            @QueryParam("objectName") String objName,
            @QueryParam("md5") String srcMd5,
            @QueryParam("size") @DefaultValue("-1")long size,
            @QueryParam("partSize") @DefaultValue("104857600") long partSize, // Default to 100MB per part
            @Suspended AsyncResponse asyncResponse) {
        executorService.submit(() -> {
            try {
                LOGGER.info("uploadFileMultipart started");
                String fileName = fileDetail.getFileName();
                long fileSize = fileDetail.getSize() > 0L ? fileDetail.getSize() : size;

                if (fileSize <= 0L){
                    asyncResponse.resume(Response.status(Response.Status.BAD_REQUEST)
                            .entity("Invalid file size.")
                            .build());
                    return;
                }

                if (fileName == null || fileName.isEmpty()) {
                    asyncResponse.resume(Response.status(Response.Status.BAD_REQUEST)
                            .entity("File name is required.")
                            .build());
                    return;
                }
                if (bucketName == null || bucketName.isEmpty()) {
                    asyncResponse.resume(Response.status(Response.Status.BAD_REQUEST)
                            .entity("Bucket name is required.")
                            .build());
                    return;
                }
                String checkedObjName = objName == null? fileName:objName;

                if (partSize < 10 * 1024 * 1024) { // Minimum part size for OCI is typically 10MB
                    asyncResponse.resume(Response.status(Response.Status.BAD_REQUEST)
                            .entity("Part size must be at least 10MB.")
                            .build());
                    return;
                }

                // Add a timestamp to the object name to ensure uniqueness
                String objectName = checkedObjName + "_" + new java.util.Date().getTime();

                // Initiate multipart upload
                String uploadId = this.objectStorageUtils.initiateMultipartUpload(bucketName, objectName);
                if (uploadId == null) {
                    asyncResponse.resume(Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                            .entity("Failed to initiate multipart upload.")
                            .build());
                    return;
                }

                // Upload parts
                MultipartUploadResult uploadResult = this.objectStorageUtils.uploadParts(uploadedInputStream, bucketName, objectName, uploadId, fileSize, partSize);

                if (!uploadResult.isSuccess()) {
                    this.objectStorageUtils.abortMultipartUpload(bucketName, objectName, uploadId);
                    asyncResponse.resume(Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                            .entity("Failed to upload all parts.")
                            .build());
                    return;
                }

                // Complete multipart upload
                boolean uploadCompleted = this.objectStorageUtils.completeMultipartUpload(bucketName, objectName, uploadId, uploadResult.getParts(), fileSize);
                if (uploadCompleted) {
                    asyncResponse.resume(Response.status(Response.Status.OK)
                            .entity("File " + objectName + " uploaded successfully to bucket " + bucketName + " using multipart upload.")
                            .build());
                } else {
                    this.objectStorageUtils.abortMultipartUpload(bucketName, objectName, uploadId);
                    asyncResponse.resume(Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                            .entity("Failed to complete multipart upload.")
                            .build());
                }
            } catch (Exception e) {
                asyncResponse.resume(Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity("Error during multipart upload: " + e.getMessage())
                        .build());
            }
        });
    }

                // New endpoint for downloading a file to local disk
    @GET
    @Path("/download")
    public Response downloadFile(@QueryParam("bucketName") String bucketName,
                                 @QueryParam("fileName") String fileName,
                                 @QueryParam("localPath") String localPath) {
        try {
            // Validate input parameters
            if (bucketName == null || bucketName.isEmpty() ||
                    fileName == null || fileName.isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("Bucket name and file name are required.")
                        .build();
            }

            // Download the file from the specified bucket
            FileStreamMetadata metadata = this.objectStorageUtils.downloadFromObjectStorage(bucketName, fileName);
            if (metadata.getInputStream() == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity("File " + fileName + " not found in bucket " + bucketName)
                        .build();
            }

            // Determine the local path to save the file
            String savePath = (localPath != null && !localPath.isEmpty()) ? localPath : "./downloads/";
            String fullPath = savePath + (savePath.endsWith("/") ? "" : "/") + fileName;

            // Create the directory if it doesn't exist
            File directory = new File(savePath);
            if (!directory.exists()) {
                directory.mkdirs();
            }

            // Write the file to local disk
            boolean saveSuccessful = this.objectStorageUtils.saveToLocalDisk(metadata.getInputStream(), fullPath);

            if (saveSuccessful) {
                return Response.status(Response.Status.OK)
                        .entity("File " + fileName + " downloaded successfully to " + fullPath)
                        .build();
            } else {
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity("Failed to save file to local disk at " + fullPath)
                        .build();
            }
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Error downloading file: " + e.getMessage())
                    .build();
        }
    }
}