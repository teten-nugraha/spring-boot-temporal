package id.ten.mpv_approval.temporal;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

import java.math.BigDecimal;

@WorkflowInterface
public interface ProcurementWorkflow {

    @WorkflowMethod
    void executeProcurement(String orderId, BigDecimal amount);
}
