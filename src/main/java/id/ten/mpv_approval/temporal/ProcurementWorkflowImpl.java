package id.ten.mpv_approval.temporal;

import id.ten.mpv_approval.schedules.AutoReleaseActivitiesImpl;
import io.temporal.activity.ActivityOptions;
import io.temporal.client.WorkflowClient;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import io.temporal.workflow.Workflow;
import org.springframework.context.annotation.Bean;

import java.math.BigDecimal;
import java.time.Duration;

import static id.ten.mpv_approval.config.TemporalConfig.TASK_QUEUE;

public class ProcurementWorkflowImpl implements ProcurementWorkflow{

    private final OrderActivities activities = Workflow.newActivityStub(
            OrderActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofSeconds(10))
                    .build()
    );

    @Bean
    public WorkerFactory workerFactory(
            WorkflowClient client,
            OrderActivitiesImpl orderActivities,
            AutoReleaseActivitiesImpl autoReleaseActivities) {

        WorkerFactory factory = WorkerFactory.newInstance(client);
        Worker worker = factory.newWorker(TASK_QUEUE);

        // =========================================================================
        // PERBAIKAN: Tambahkan ProcurementWorkflowImpl.class ke dalam baris ini:
        // =========================================================================
        worker.registerWorkflowImplementationTypes(
                OrderApprovalWorkflowImpl.class,
                AutoReleaseWorkflowImpl.class,
                ProcurementWorkflowImpl.class // <-- TAMBAHKAN INI
        );

        // Daftarkan Activities
        worker.registerActivitiesImplementations(orderActivities, autoReleaseActivities);

        return factory;
    }

    @Override
    public void executeProcurement(String orderId, BigDecimal amount) {
        String childId = Workflow.getInfo().getWorkflowId();

        // 1. Tahap 1: Menerbitkan Purchase Order (PO)
        activities.updateProcurementProgress(
                orderId, childId, "PO_CREATED",
                "PO-" + orderId + " berhasil diterbitkan ke Vendor."
        );
        Workflow.sleep(Duration.ofSeconds(4)); // Simulasi jeda proses

        // 2. Tahap 2: Transfer Dana (Disbursement)
        activities.updateProcurementProgress(
                orderId, childId, "FUNDS_DISBURSED",
                "Dana Rp " + amount + " berhasil ditransfer ke rekening Vendor."
        );
        Workflow.sleep(Duration.ofSeconds(6));

        // 3. Tahap 3: Pengadaan Selesai
        activities.updateProcurementProgress(
                orderId, childId, "COMPLETED",
                "Pengadaan selesai. Resi pengiriman dibuat dan barang siap dikirim ke gudang."
        );
    }

}
