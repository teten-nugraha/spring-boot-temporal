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

    // Menerima aksi approve / reject dari pimpinan
    @SignalMethod
    void submitApproval(ApprovalDecision decision);

    // memungkinkan frontend query state workflow langsung
    @QueryMethod
    OrderState getOrderState();
}
