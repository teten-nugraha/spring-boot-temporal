package id.ten.mpv_approval.schedules;

import id.ten.mpv_approval.dto.OrderDto.*;
import id.ten.mpv_approval.entity.OrderEntity;
import id.ten.mpv_approval.repository.OrderRepository;
import id.ten.mpv_approval.temporal.OrderActivitiesImpl;
import id.ten.mpv_approval.temporal.OrderApprovalWorkflow;
import io.temporal.client.WorkflowClient;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class AutoReleaseActivitiesImpl implements AutoReleaseActivities {

    private final OrderRepository orderRepository;
    private final WorkflowClient workflowClient;

    public AutoReleaseActivitiesImpl(OrderRepository orderRepository, WorkflowClient workflowClient) {
        this.orderRepository = orderRepository;
        this.workflowClient = workflowClient;
    }

    @Override
    @Transactional
    public void releaseStaleOrders() {
        LocalDateTime limaDetikLalu = LocalDateTime.now().minusSeconds(5);

        List<OrderEntity> staleOrders = orderRepository.findAll().stream()
                .filter(o -> o.getStatus() == Status.IN_PROGRESS
                        && o.getClaimedById() != null
                        && o.getUpdatedAt().isBefore(limaDetikLalu))
                .toList();

        for (OrderEntity order : staleOrders) {
            order.setClaimedById(null);
            order.setClaimedByName(null);
            order.setUpdatedAt(LocalDateTime.now());
            orderRepository.save(order);

            try {
                OrderApprovalWorkflow stub = workflowClient.newWorkflowStub(OrderApprovalWorkflow.class, order.getOrderId());
                stub.claimOrder(new ClaimRequest(null, null, order.getWaitingForRole(), true));
            } catch (Exception ignored) {}
        }
    }
}
