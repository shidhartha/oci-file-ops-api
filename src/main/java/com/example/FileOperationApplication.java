package com.example;

import com.example.resources.FileOperationResourceOc1;
import com.example.resources.FileOperationResourceOc10;
import io.dropwizard.Application;
import io.dropwizard.Configuration;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.codahale.metrics.MetricRegistry;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.dropwizard.DropwizardExports;
import io.prometheus.client.exporter.MetricsServlet;

// Main Dropwizard Application class
public class FileOperationApplication extends Application<Configuration> {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileOperationApplication.class);

    public static void main(String[] args) throws Exception {
        new FileOperationApplication().run(args);
    }

    @Override
    public void initialize(Bootstrap<Configuration> bootstrap) {
        // Add any initialization logic if needed
        LOGGER.info("Initializing FileUploadApplication");

    }

    @Override
    public void run(Configuration configuration, Environment environment) {
        // Register the MultiPartFeature to enable multipart form data support
        environment.jersey().register(MultiPartFeature.class);

        // Get the MetricRegistry from Dropwizard environment
        MetricRegistry metricRegistry = environment.metrics();

        // Register Dropwizard metrics with Prometheus
        CollectorRegistry.defaultRegistry.register(new DropwizardExports(metricRegistry));

        // Expose Prometheus metrics endpoint
        environment.getAdminContext().getServletContext()
                .addServlet("prometheusMetrics", new MetricsServlet())
                .addMapping("/prometheus");

        // Example: Register a simple counter metric
        metricRegistry.counter("FileOperationApplication_start_counter").inc();

        // Register the resource class with Jersey
        environment.jersey().register(new FileOperationResourceOc1());
        environment.jersey().register(new FileOperationResourceOc10());


        environment.lifecycle().manage(new FileOperationManager());

        // Add a simple health check (optional)
        environment.healthChecks().register("simple", new SimpleHealthCheck());
    }
}

// Simple health check class (optional)
class SimpleHealthCheck extends com.codahale.metrics.health.HealthCheck {
    @Override
    protected Result check() {
        return Result.healthy();
    }
}

