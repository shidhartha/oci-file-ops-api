package com.example.resources;

import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import com.oracle.bmc.objectstorage.ObjectStorage;
import com.oracle.bmc.objectstorage.ObjectStorageClient;
import com.oracle.bmc.objectstorage.requests.GetObjectRequest;
import com.oracle.bmc.objectstorage.requests.PutObjectRequest;
import com.oracle.bmc.objectstorage.responses.GetObjectResponse;
import com.oracle.bmc.objectstorage.responses.PutObjectResponse;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.*;

@Path("/osOps")
@Produces(MediaType.APPLICATION_JSON)
public class FileOperationResource {

    private final ObjectStorage objectStorageClient;
    private final String namespaceName = "idvwg0eaivf3"; // Replace with your OCI namespace

    public FileOperationResource() {
        try {
            AuthenticationDetailsProvider provider =
                    new ConfigFileAuthenticationDetailsProvider("~/.oci/config", "oc1-ashburn-test-user-hpt");
            this.objectStorageClient = new ObjectStorageClient(provider);
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
            boolean uploadSuccessful = uploadToObjectStorage(uploadedInputStream, bucketName, fileName);

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

            InputStream fileStream = downloadFromObjectStorage(sourceBucket, sourceFile);
            if (fileStream == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity("File " + sourceFile + " not found in source bucket " + sourceBucket)
                        .build();
            }


            boolean uploadSuccessful = uploadToObjectStorage(fileStream, destBucket, destinationFileName);


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
            InputStream fileStream = downloadFromObjectStorage(bucketName, fileName);
            if (fileStream == null) {
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
            boolean saveSuccessful = saveToLocalDisk(fileStream, fullPath);

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

    // Helper method to upload a file to a specified bucket
    private boolean uploadToObjectStorage(InputStream inputStream, String bucketName, String fileName) {
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

    // Helper method to download a file from a specified bucket
    private InputStream downloadFromObjectStorage(String bucketName, String fileName) {
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .namespaceName(namespaceName)
                    .bucketName(bucketName)
                    .objectName(fileName)
                    .build();

            GetObjectResponse response = objectStorageClient.getObject(request);
            System.out.println("downloadFromObjectStorage:File md5:"+response.getContentMd5());
            return response.getInputStream();
        } catch (Exception e) {
            System.err.println("Error downloading from OCI Object Storage: " + e.getMessage());
            return null;
        }
    }

    // Helper method to save a file to local disk
    private boolean saveToLocalDisk(InputStream inputStream, String filePath) {
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