package id.ten.mpv_approval.temporal;

import id.ten.mpv_approval.dto.OrderDto.*;
import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface OrderApprovalWorkflow {

    @WorkflowMethod
    void processOrder(CreateRequest request);

    @SignalMethod
    void claimOrder(ClaimRequest claim);

    @SignalMethod
    void submitApproval(ApprovalDecision decision);

    @SignalMethod
    void cancelOrder(String requesterId, String reason);

    @QueryMethod
    OrderState getOrderState();
}
