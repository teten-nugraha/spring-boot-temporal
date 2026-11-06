package id.ten.mpv_approval.config;

import id.ten.mpv_approval.temporal.AutoReleaseWorkflow;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.schedules.Schedule;
import io.temporal.client.schedules.ScheduleActionStartWorkflow;
import io.temporal.client.schedules.ScheduleClient;
import io.temporal.client.schedules.ScheduleClientOptions;
import io.temporal.client.schedules.ScheduleIntervalSpec;
import io.temporal.client.schedules.ScheduleOptions;
import io.temporal.client.schedules.ScheduleSpec;
import io.temporal.serviceclient.WorkflowServiceStubs;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

@Configuration
public class TemporalScheduleConfig {

    public static final String SCHEDULE_ID = "AUTO_RELEASE_STALE_PICK_SCHEDULE";

    @Bean
    public ScheduleClient scheduleClient(WorkflowServiceStubs serviceStubs) {
        return ScheduleClient.newInstance(serviceStubs, ScheduleClientOptions.newBuilder().build());
    }

    // Inisialisasi Schedule otomatis saat aplikasi start
    @Bean
    public CommandLineRunner initSchedule(ScheduleClient scheduleClient) {
        return args -> {
            try {
                // Cek apakah schedule sudah pernah dibuat di cluster
                scheduleClient.getHandle(SCHEDULE_ID).describe();
            } catch (Exception e) {
                // Buat aksi: jalankan AutoReleaseWorkflow
                ScheduleActionStartWorkflow action = ScheduleActionStartWorkflow.newBuilder()

                        .setWorkflowType(AutoReleaseWorkflow.class)
                        .setOptions(WorkflowOptions.newBuilder()
                                .setWorkflowId("auto-release-execution")
                                .setTaskQueue(TemporalConfig.TASK_QUEUE)
                                .build())
                        .build();

                // Buat interval: Berjalan setiap 5 detik
                ScheduleSpec spec = ScheduleSpec.newBuilder()
                        .setIntervals(List.of(new ScheduleIntervalSpec(Duration.ofSeconds(5))))
                        .build();

                scheduleClient.createSchedule(
                        SCHEDULE_ID,
                        Schedule.newBuilder().setAction(action).setSpec(spec).build(),
                        ScheduleOptions.newBuilder().build()
                );
                System.out.println("✅ [TEMPORAL SCHEDULE] Jadwal Auto-Release aktif (Setiap 5 detik).");
            }
        };
    }
}