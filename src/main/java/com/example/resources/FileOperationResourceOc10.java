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
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.InputStream;

@Path("/oc10")
@Produces(MediaType.APPLICATION_JSON)
public class FileOperationResourceOc10 {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileOperationResourceOc10.class);
    private ObjectStorageUtils objectStorageUtils;

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
    public Response uploadFileToOCI(
            @FormDataParam("file") InputStream fileInputStream,
            @QueryParam("bucketName") String bucketName,
            @QueryParam("objectName") String objectName) {
        try {
            // Validate input parameters
            if (fileInputStream == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("File input stream is required.")
                        .build();
            }
            if (bucketName == null || bucketName.isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("Bucket name is required.")
                        .build();
            }

            if (objectName == null || objectName.isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("File name or object name is required.")
                        .build();
            }

            // Upload the file to OCI Object Storage
            boolean uploadSuccessful = this.objectStorageUtils.uploadToObjectStorage(fileInputStream, bucketName, objectName, null);

            if (uploadSuccessful) {
                return Response.status(Response.Status.OK)
                        .entity("File " + objectName + " uploaded successfully to bucket " + bucketName + " in OCI Object Storage.")
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