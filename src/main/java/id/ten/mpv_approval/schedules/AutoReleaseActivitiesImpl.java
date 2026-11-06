package id.ten.mpv_approval.schedules;

import id.ten.mpv_approval.dto.OrderDto;
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
public class AutoReleaseActivitiesImpl implements AutoReleaseActivities{

    private static final Logger log = LoggerFactory.getLogger(AutoReleaseActivitiesImpl.class);


    private final OrderRepository orderRepository;
    private final WorkflowClient workflowClient;

    public AutoReleaseActivitiesImpl(OrderRepository orderRepository, WorkflowClient workflowClient) {
        this.orderRepository = orderRepository;
        this.workflowClient = workflowClient;
    }

    @Override
    @Transactional
    public void releaseStaleOrders() {
        // Cari order yang sedang IN_PROGRESS, sudah di-claim dan didiamkan > 5 detik
        LocalDateTime limaDetikLalu = LocalDateTime.now().minusSeconds(5);

        List<OrderEntity> staleOrders = orderRepository.findAll().stream()
                .filter(o -> o.getStatus() == OrderDto.Status.IN_PROGRESS
                && o.getClaimedById() != null
                && o.getUpdatedAt().isBefore(limaDetikLalu))
                .toList();

        for (OrderEntity order : staleOrders) {
            log.info("⏰ [AUTO-RELEASE] Order #" + order.getOrderId() + " didiamkan > 5 detik. Klaim " + order.getClaimedByName() + " dilepas!");

            // 1. Lepas klaim di Database H2
            order.setClaimedById(null);
            order.setClaimedByName(null);
            order.setUpdatedAt(LocalDateTime.now());
            orderRepository.save(order);

            // 2. Beri tahu Workflow Temporal bahwa klaim dilepas (Signal release)
            try {
                OrderApprovalWorkflow stub = workflowClient.newWorkflowStub(OrderApprovalWorkflow.class, order.getOrderId());
                stub.claimOrder(new OrderDto.ClaimRequest(null, null, order.getWaitingForRole(), true));
            } catch (Exception ignored) {}

        }
    }
}
