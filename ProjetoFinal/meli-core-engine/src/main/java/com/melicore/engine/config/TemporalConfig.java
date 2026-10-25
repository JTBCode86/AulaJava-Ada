package com.melicore.engine.config;

import com.melicore.engine.saga.OrderActivitiesImpl;
import com.melicore.engine.saga.OrderWorkflowImpl;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Configuration
public class TemporalConfig {

    @Value("${temporal.target:temporal:7233}")
    private String temporalTarget;

    private WorkerFactory factory;

    @Bean
    public WorkflowServiceStubs workflowServiceStubs() {
        return WorkflowServiceStubs.newServiceStubs(
                WorkflowServiceStubsOptions.newBuilder()
                        .setTarget(temporalTarget)
                        .build());
    }

    @Bean
    public WorkflowClient workflowClient(WorkflowServiceStubs serviceStubs) {
        return WorkflowClient.newInstance(serviceStubs,
                WorkflowClientOptions.newBuilder().build());
    }

    @Bean
    @ConditionalOnProperty(name = "temporal.worker.enabled", havingValue = "true", matchIfMissing = true)
    public WorkerFactory workerFactory(WorkflowClient workflowClient, OrderActivitiesImpl orderActivities) {
        factory = WorkerFactory.newInstance(workflowClient);
        Worker worker = factory.newWorker("CHECKOUT_TASK_QUEUE");
        
        // Registo do Workflow SAGA e das respetivas Atividades no Worker
        worker.registerWorkflowImplementationTypes(OrderWorkflowImpl.class);
        worker.registerActivitiesImplementations(orderActivities);

        factory.start();
        return factory;
    }

    @PreDestroy
    public void destroy() {
        if (factory != null) {
            factory.shutdown();
        }
    }
}