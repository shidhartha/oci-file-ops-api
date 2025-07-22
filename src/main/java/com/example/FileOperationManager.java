package com.example;

import io.dropwizard.lifecycle.Managed;

// dummy as of now.. no usage
public class FileOperationManager implements Managed {
    @Override
    public void start() throws Exception {
        System.out.println("Starting FileOperationManager");
        Managed.super.start();
    }

    @Override
    public void stop() throws Exception {
        System.out.println("Stopping FileOperationManager");
        Managed.super.stop();
    }
}
