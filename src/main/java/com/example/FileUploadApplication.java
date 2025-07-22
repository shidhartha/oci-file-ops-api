package com.example;

import com.example.resources.FileUploadResource;
import io.dropwizard.Application;
import io.dropwizard.Configuration;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.glassfish.jersey.media.multipart.MultiPartFeature;

// Main Dropwizard Application class
public class FileUploadApplication extends Application<Configuration> {

    public static void main(String[] args) throws Exception {
        new FileUploadApplication().run(args);
    }

    @Override
    public void initialize(Bootstrap<Configuration> bootstrap) {
        // Add any initialization logic if needed
        System.out.println("Initializing FileUploadApplication");
    }

    @Override
    public void run(Configuration configuration, Environment environment) {
        // Register the MultiPartFeature to enable multipart form data support
        environment.jersey().register(MultiPartFeature.class);
        // Register the resource class with Jersey
        environment.jersey().register(new FileUploadResource());
    }
}

