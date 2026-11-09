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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

@Configuration
public class TemporalScheduleConfig {

    private static final Logger log = LoggerFactory.getLogger(TemporalScheduleConfig.class);
    public static final String SCHEDULE_ID = "AUTO_RELEASE_STALE_PICK_SCHEDULE";

    // 1. Inisialisasi ScheduleClient untuk mengelola Jadwal di Temporal
    @Bean
    public ScheduleClient scheduleClient(WorkflowServiceStubs serviceStubs) {
        return ScheduleClient.newInstance(serviceStubs, ScheduleClientOptions.newBuilder().build());
    }

    // 2. Mendaftarkan Jadwal Otomatis (Setiap 5 Detik) saat Aplikasi Spring Boot Start
    @Bean
    public CommandLineRunner initSchedule(ScheduleClient scheduleClient) {
        return args -> {
            try {
                // Cek apakah schedule sudah pernah terdaftar di Temporal Cluster
                scheduleClient.getHandle(SCHEDULE_ID).describe();
                log.info("Temporal Schedule '{}' sudah aktif di cluster.", SCHEDULE_ID);
            } catch (Exception e) {
                log.info("Membuat Temporal Schedule baru '{}'...", SCHEDULE_ID);

                // Tentukan aksi: Menjalankan AutoReleaseWorkflow
                ScheduleActionStartWorkflow action = ScheduleActionStartWorkflow.newBuilder()
                        .setWorkflowType(AutoReleaseWorkflow.class)
                        .setOptions(WorkflowOptions.newBuilder()
                                .setWorkflowId("auto-release-execution") // ID wajib untuk Schedule Action
                                .setTaskQueue(TemporalConfig.TASK_QUEUE)
                                .build())
                        .build();

                // Tentukan interval pengulangan: Setiap 5 Detik
                ScheduleSpec spec = ScheduleSpec.newBuilder()
                        .setIntervals(List.of(new ScheduleIntervalSpec(Duration.ofSeconds(5))))
                        .build();

                Schedule schedule = Schedule.newBuilder()
                        .setAction(action)
                        .setSpec(spec)
                        .build();

                scheduleClient.createSchedule(
                        SCHEDULE_ID,
                        schedule,
                        ScheduleOptions.newBuilder().build()
                );

                log.info("✅ Temporal Schedule '{}' berhasil dibuat (Interval: Setiap 5 Detik).", SCHEDULE_ID);
            }
        };
    }
}