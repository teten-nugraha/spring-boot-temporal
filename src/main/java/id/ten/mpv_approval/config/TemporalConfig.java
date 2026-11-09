package id.ten.mpv_approval.config;

import id.ten.mpv_approval.repository.OrderRepository;
import id.ten.mpv_approval.schedules.AutoReleaseActivitiesImpl;
import id.ten.mpv_approval.temporal.AutoReleaseWorkflow;
import id.ten.mpv_approval.temporal.AutoReleaseWorkflowImpl;
import id.ten.mpv_approval.temporal.DualDirectorChildWorkflowImpl;
import id.ten.mpv_approval.temporal.OrderActivitiesImpl;
import id.ten.mpv_approval.temporal.OrderApprovalWorkflowImpl;
import id.ten.mpv_approval.temporal.ProcurementWorkflowImpl;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.schedules.Schedule;
import io.temporal.client.schedules.ScheduleActionStartWorkflow;
import io.temporal.client.schedules.ScheduleClient;
import io.temporal.client.schedules.ScheduleClientOptions;
import io.temporal.client.schedules.ScheduleIntervalSpec;
import io.temporal.client.schedules.ScheduleOptions;
import io.temporal.client.schedules.ScheduleSpec;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

@Configuration
public class TemporalConfig {

    public static final String TASK_QUEUE = "ORDER_APPROVAL_TASK_QUEUE";
    public static final String SCHEDULE_ID = "AUTO_RELEASE_STALE_PICK_SCHEDULE";

    @Bean
    public WorkflowServiceStubs workflowServiceStubs() {
        return WorkflowServiceStubs.newLocalServiceStubs();
    }

    @Bean
    public WorkflowClient workflowClient(WorkflowServiceStubs serviceStubs) {
        return WorkflowClient.newInstance(serviceStubs, WorkflowClientOptions.newBuilder().build());
    }

    @Bean
    public WorkerFactory workerFactory(
            WorkflowClient client,
            OrderActivitiesImpl orderActivities,
            AutoReleaseActivitiesImpl autoReleaseActivities) {

        WorkerFactory factory = WorkerFactory.newInstance(client);
        Worker worker = factory.newWorker(TASK_QUEUE);

        // Registrasi Semua Workflow (Parent, Child, Schedule)
        worker.registerWorkflowImplementationTypes(
                OrderApprovalWorkflowImpl.class,
                DualDirectorChildWorkflowImpl.class,
                AutoReleaseWorkflowImpl.class
        );

        // Registrasi Semua Activities
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