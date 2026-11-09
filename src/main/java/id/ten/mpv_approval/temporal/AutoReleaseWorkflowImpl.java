package id.ten.mpv_approval.temporal;

import id.ten.mpv_approval.schedules.AutoReleaseActivities;
import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;

import java.time.Duration;

public class AutoReleaseWorkflowImpl implements AutoReleaseWorkflow {

    private final AutoReleaseActivities activities = Workflow.newActivityStub(
            AutoReleaseActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofSeconds(10))
                    .build()
    );

    @Override
    public void run() {
        activities.releaseStaleOrders();
    }
}