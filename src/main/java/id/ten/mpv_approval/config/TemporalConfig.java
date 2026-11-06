package id.ten.mpv_approval.config;

import id.ten.mpv_approval.repository.OrderRepository;
import id.ten.mpv_approval.schedules.AutoReleaseActivitiesImpl;
import id.ten.mpv_approval.temporal.AutoReleaseWorkflow;
import id.ten.mpv_approval.temporal.AutoReleaseWorkflowImpl;
import id.ten.mpv_approval.temporal.OrderActivitiesImpl;
import id.ten.mpv_approval.temporal.OrderApprovalWorkflowImpl;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TemporalConfig {

    public static final String TASK_QUEUE = "ORDER_APPROVAL_TASK_QUEUE";

    @Bean
    public WorkflowServiceStubs workflowServiceStubs() {
        return WorkflowServiceStubs.newLocalServiceStubs();
    }

    @Bean
    public WorkflowClient workflowClient(WorkflowServiceStubs serviceStubs) {
        return WorkflowClient.newInstance(serviceStubs, WorkflowClientOptions.newBuilder().build());
    }

    @Bean
    public AutoReleaseActivitiesImpl autoReleaseActivities(OrderRepository orderRepository, WorkflowClient workflowClient) {
        return new AutoReleaseActivitiesImpl(orderRepository, workflowClient);
    }

    @Bean
    public WorkerFactory workerFactory(WorkflowClient client, OrderActivitiesImpl orderActivities, AutoReleaseActivitiesImpl autoReleaseActivities) {
        WorkerFactory factory = WorkerFactory.newInstance(client);
        Worker worker = factory.newWorker(TASK_QUEUE);

        // Daftarkan Workflow dan Activity
        worker.registerWorkflowImplementationTypes(OrderApprovalWorkflowImpl.class, AutoReleaseWorkflowImpl.class);
        worker.registerActivitiesImplementations(orderActivities, autoReleaseActivities);

        return factory;
    }

    @Configuration
    public static class WorkerLifecycle {
        private final WorkerFactory workerFactory;

        public WorkerLifecycle(WorkerFactory workerFactory) {
            this.workerFactory = workerFactory;
        }

        @PostConstruct
        public void start() {
            workerFactory.start();
        }

        @PreDestroy
        public void stop() {
            workerFactory.shutdown();
        }
    }
}
