package id.ten.mpv_approval.schedules;

import io.temporal.activity.ActivityInterface;

@ActivityInterface
public interface AutoReleaseActivities {

    void releaseStaleOrders();
}
