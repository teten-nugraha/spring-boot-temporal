package id.ten.mpv_approval.temporal;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface AutoReleaseWorkflow {
    @WorkflowMethod
    void run();
}
