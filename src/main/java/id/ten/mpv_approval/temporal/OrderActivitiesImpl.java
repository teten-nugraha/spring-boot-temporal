package id.ten.mpv_approval.temporal;

import id.ten.mpv_approval.dto.OrderDto.*;
import id.ten.mpv_approval.entity.ApprovalHistoryEntity;
import id.ten.mpv_approval.entity.OrderEntity;
import id.ten.mpv_approval.model.Role;
import id.ten.mpv_approval.repository.ApprovalHistoryRepository;
import id.ten.mpv_approval.repository.OrderRepository;
import io.temporal.activity.Activity;
import io.temporal.failure.ApplicationFailure;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class OrderActivitiesImpl implements OrderActivities {

    private final OrderRepository orderRepository;
    private final ApprovalHistoryRepository historyRepository;

    public OrderActivitiesImpl(OrderRepository orderRepository, ApprovalHistoryRepository historyRepository) {
        this.orderRepository = orderRepository;
        this.historyRepository = historyRepository;
    }

    @Override
    @Transactional
    public void saveInitialOrder(CreateRequest request, List<Role> approvalChain, Status status) {
        OrderEntity order = new OrderEntity();
        order.setOrderId(request.orderId());
        order.setRequesterId(request.requesterId());
        order.setType(request.type());
        order.setDays(request.days());
        order.setAmount(request.amount());
        order.setNotes(request.notes());
        order.setStatus(status);
        order.setApprovalChain(approvalChain);
        order.setWaitingForRole(approvalChain.isEmpty() ? null : approvalChain.get(0));
        order.setClaimedById(null);
        order.setClaimedByName(null);
        orderRepository.save(order);
    }

    @Override
    @Transactional
    public void updateWaitingState(String orderId, int currentStepIndex, Role waitingForRole) {
        orderRepository.findById(orderId).ifPresent(order -> {
            order.setCurrentStepIndex(currentStepIndex);
            order.setWaitingForRole(waitingForRole);
            order.setClaimedById(null);
            order.setClaimedByName(null);
            order.setStatus(Status.IN_PROGRESS);
            order.setUpdatedAt(LocalDateTime.now());
            orderRepository.save(order);
        });
    }

    @Override
    @Transactional
    public void updateClaimState(String orderId, String claimedById, String claimedByName) {
        orderRepository.findById(orderId).ifPresent(order -> {
            order.setClaimedById(claimedById);
            order.setClaimedByName(claimedByName);
            order.setUpdatedAt(LocalDateTime.now());
            orderRepository.save(order);
        });
    }

    @Override
    @Transactional
    public void recordDecision(String orderId, ApprovalDecision decision) {
        orderRepository.findById(orderId).ifPresent(order -> {
            ApprovalHistoryEntity history = new ApprovalHistoryEntity(
                    order,
                    decision.approverId(),
                    decision.role(),
                    decision.approved(),
                    decision.notes()
            );
            historyRepository.save(history);
            order.getHistory().add(history);
            order.setUpdatedAt(LocalDateTime.now());
            orderRepository.save(order);
        });
    }

    @Override
    @Transactional
    public void finalizeStatus(String orderId, Status status) {
        orderRepository.findById(orderId).ifPresent(order -> {
            order.setStatus(status);
            order.setWaitingForRole(null);
            order.setClaimedById(null);
            order.setClaimedByName(null);
            order.setUpdatedAt(LocalDateTime.now());
            orderRepository.save(order);
        });
    }

    @Override
    @Transactional
    public void updateProcurementProgress(String orderId, String childWorkflowId, String stage, String notes) {
        orderRepository.findById(orderId).ifPresent(order -> {
            order.setProcurementWorkflowId(childWorkflowId);
            order.setProcurementStage(stage);
            order.setProcurementNotes(notes);
            order.setUpdatedAt(LocalDateTime.now());
            orderRepository.save(order);
        });
    }
}