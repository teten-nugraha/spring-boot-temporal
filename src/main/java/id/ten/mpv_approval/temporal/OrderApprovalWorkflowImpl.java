package id.ten.mpv_approval.temporal;

import id.ten.mpv_approval.dto.OrderDto.*;
import id.ten.mpv_approval.model.Role;
import id.ten.mpv_approval.service.ApprovalRuleEvaluator;
import io.temporal.activity.ActivityOptions;
import io.temporal.api.enums.v1.ParentClosePolicy;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.ChildWorkflowOptions;
import io.temporal.workflow.Workflow;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class OrderApprovalWorkflowImpl implements OrderApprovalWorkflow {

    // 1. Definisikan retryOptions terlebih dahulu
    private final RetryOptions retryOptions = RetryOptions.newBuilder()
            .setInitialInterval(Duration.ofSeconds(10))
            .setBackoffCoefficient(1.0)
            .setMaximumAttempts(5)
            .build();

    // 2. Hubungkan ke Activity Stub
    private final OrderActivities activities = Workflow.newActivityStub(
            OrderActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofSeconds(20))
                    .setRetryOptions(retryOptions)
                    .build()
    );

    private String orderId;
    private OrderType orderType;
    private Status status = Status.PENDING;
    private List<Role> approvalChain = new ArrayList<>();
    private int currentStepIndex = 0;
    private final List<ApprovalDecision> history = new ArrayList<>();

    private ClaimRequest currentClaim = null;
    private ClaimRequest pendingClaimSignal = null;
    private ApprovalDecision pendingDecision = null;

    private boolean isCancelled = false;
    private String cancelReason = "";

    @Override
    public void processOrder(CreateRequest request) {
        this.orderId = request.orderId();
        this.orderType = request.type();

        this.approvalChain = ApprovalRuleEvaluator.determineRequiredApprovals(
                request.type(), request.days(), request.amount()
        );

        if (approvalChain.isEmpty()) {
            this.status = Status.APPROVED;
            activities.saveInitialOrder(request, approvalChain, Status.APPROVED);
            return;
        }

        this.status = Status.IN_PROGRESS;
        activities.saveInitialOrder(request, approvalChain, Status.IN_PROGRESS);

        while (currentStepIndex < approvalChain.size()) {
            Role targetRole = approvalChain.get(currentStepIndex);

            this.currentClaim = null;
            this.pendingClaimSignal = null;
            this.pendingDecision = null;

            activities.updateWaitingState(orderId, currentStepIndex, targetRole);

            while (true) {
                Workflow.await(() -> pendingClaimSignal != null || pendingDecision != null || isCancelled);

                if (isCancelled) {
                    this.status = Status.CANCELLED;
                    activities.finalizeStatus(orderId, Status.CANCELLED);
                    return;
                }

                if (pendingClaimSignal != null) {
                    if (pendingClaimSignal.role() == targetRole) {
                        if (pendingClaimSignal.release()) {
                            this.currentClaim = null;
                            activities.updateClaimState(orderId, null, null);
                        } else {
                            this.currentClaim = pendingClaimSignal;
                            activities.updateClaimState(orderId, pendingClaimSignal.approverId(), pendingClaimSignal.approverName());
                        }
                    }
                    pendingClaimSignal = null;
                }

                if (pendingDecision != null) {
                    if (currentClaim != null && pendingDecision.approverId().equals(currentClaim.approverId())) {
                        break;
                    }
                    pendingDecision = null;
                }
            }

            history.add(pendingDecision);
            activities.recordDecision(orderId, pendingDecision);

            if (!pendingDecision.approved()) {
                this.status = Status.REJECTED;
                activities.finalizeStatus(orderId, Status.REJECTED);
                return;
            }

            pendingDecision = null;
            currentClaim = null;
            currentStepIndex++;
        }

        // =========================================================================
        // EKSEKUSI CHILD WORKFLOW: KHUSUS TIPE BUY_LAMBORGHINI (2 DIREKTUR BERBEDA)
        // =========================================================================
        if (orderType == OrderType.BUY_LAMBORGHINI) {
            String childWorkflowId = "CHILD-LAMBO-DIR-" + orderId;

            DualDirectorChildWorkflow childDirectorWorkflow = Workflow.newChildWorkflowStub(
                    DualDirectorChildWorkflow.class,
                    ChildWorkflowOptions.newBuilder()
                            .setWorkflowId(childWorkflowId)
                            .setParentClosePolicy(ParentClosePolicy.PARENT_CLOSE_POLICY_REQUEST_CANCEL)
                            .build()
            );

            // Blocking call: Parent membeku sampai Child Workflow disetujui 2 Direktur
            boolean isChildApproved = childDirectorWorkflow.executeDualDirectorApproval(orderId, request.amount());

            if (!isChildApproved) {
                this.status = Status.REJECTED;
                activities.finalizeStatus(orderId, Status.REJECTED);
                return;
            }
        }

        this.status = Status.APPROVED;
        activities.finalizeStatus(orderId, Status.APPROVED);
    }

    @Override
    public void claimOrder(ClaimRequest claim) {
        this.pendingClaimSignal = claim;
    }

    @Override
    public void submitApproval(ApprovalDecision decision) {
        this.pendingDecision = decision;
    }

    @Override
    public void cancelOrder(String requesterId, String reason) {
        this.isCancelled = true;
        this.cancelReason = reason;
    }

    @Override
    public OrderState getOrderState() {
        Role waitingFor = (status == Status.IN_PROGRESS && currentStepIndex < approvalChain.size())
                ? approvalChain.get(currentStepIndex)
                : null;
        String claimedId = currentClaim != null ? currentClaim.approverId() : null;
        String claimedName = currentClaim != null ? currentClaim.approverName() : null;

        return new OrderState(orderId, orderType, status, approvalChain, currentStepIndex, waitingFor, claimedId, claimedName, history);
    }
}
