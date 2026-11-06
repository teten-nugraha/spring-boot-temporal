package id.ten.mpv_approval.temporal;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface SlaMonitorActivities {

    @ActivityMethod
    void checkStaleOrderAndReleaseClaims();
}
