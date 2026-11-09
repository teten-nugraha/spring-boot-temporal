package id.ten.mpv_approval.temporal;

import id.ten.mpv_approval.dto.OrderDto.*;
import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

import java.math.BigDecimal;
import java.util.List;

@WorkflowInterface
public interface DualDirectorChildWorkflow {

    @WorkflowMethod
    boolean executeDualDirectorApproval(String orderId, BigDecimal amount);

    @SignalMethod
    void submitDirectorApproval(ApprovalDecision decision);

    @QueryMethod
    List<ApprovalDecision> getDirectorApprovals();
}
