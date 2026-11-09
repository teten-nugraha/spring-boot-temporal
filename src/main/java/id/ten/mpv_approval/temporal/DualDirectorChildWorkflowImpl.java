package id.ten.mpv_approval.temporal;

import id.ten.mpv_approval.dto.OrderDto.*;
import id.ten.mpv_approval.model.Role;
import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DualDirectorChildWorkflowImpl implements DualDirectorChildWorkflow {

    private final OrderActivities activities = Workflow.newActivityStub(
            OrderActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofSeconds(10))
                    .build()
    );

    private final List<ApprovalDecision> approvals = new ArrayList<>();
    private final Set<String> approvedDirectorIds = new HashSet<>();
    private boolean isRejected = false;

    @Override
    public boolean executeDualDirectorApproval(String orderId, BigDecimal amount) {
        String childId = Workflow.getInfo().getWorkflowId();

        activities.updateProcurementProgress(
                orderId, childId, "WAITING_DIRECTORS",
                "Menunggu persetujuan dari 2 Direktur berbeda (0/2 Direktur disetujui)."
        );

        // Blocking: Menunggu 2 Direktur Berbeda menyetujui atau salah satu menolak
        Workflow.await(() -> approvedDirectorIds.size() >= 2 || isRejected);

        if (isRejected) {
            activities.updateProcurementProgress(orderId, childId, "REJECTED_BY_DIRECTOR", "Pengajuan Lamborghini ditolak oleh Direktur.");
            return false;
        }

        activities.updateProcurementProgress(orderId, childId, "DIRECTORS_APPROVED", "Kedua Direktur telah menyetujui (2/2). Pengadaan siap diproses.");
        return true;
    }

    @Override
    public void submitDirectorApproval(ApprovalDecision decision) {
        if (decision.role() != Role.DIRECTOR) {
            return;
        }

        if (!decision.approved()) {
            this.isRejected = true;
            this.approvals.add(decision);
            return;
        }

        // Validasi wajib orang yang berbeda
        if (!approvedDirectorIds.contains(decision.approverId())) {
            approvedDirectorIds.add(decision.approverId());
            approvals.add(decision);

            String orderId = Workflow.getInfo().getWorkflowId()
                    .replace("CHILD-LAMBO-DIR-", "")
                    .replace("CHILD-DIR-", "");

            activities.updateProcurementProgress(
                    orderId,
                    Workflow.getInfo().getWorkflowId(),
                    approvedDirectorIds.size() == 1 ? "WAITING_SECOND_DIRECTOR" : "DIRECTORS_APPROVED",
                    "Persetujuan Direktur: " + approvedDirectorIds.size() + "/2 Direktur (" + decision.approverId() + " menyetujui)."
            );
        }
    }

    @Override
    public List<ApprovalDecision> getDirectorApprovals() {
        return approvals;
    }
}
