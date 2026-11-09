package id.ten.mpv_approval.temporal;

import id.ten.mpv_approval.dto.OrderDto.*;
import id.ten.mpv_approval.model.Role;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

import java.math.BigDecimal;
import java.util.List;

@ActivityInterface
public interface OrderActivities {

    @ActivityMethod
    void saveInitialOrder(CreateRequest request, List<Role> approvalChain, Status status);

    @ActivityMethod
    void updateWaitingState(String orderId, int currentStepIndex, Role waitingForRole);

    @ActivityMethod
    void updateClaimState(String orderId, String claimedById, String claimedByName);

    @ActivityMethod
    void recordDecision(String orderId, ApprovalDecision decision);

    @ActivityMethod
    void finalizeStatus(String orderId, Status status);

    @ActivityMethod
    void updateProcurementProgress(String orderId, String childWorkflowId, String stage, String notes);
}