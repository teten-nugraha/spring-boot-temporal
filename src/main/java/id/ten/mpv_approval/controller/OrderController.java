package id.ten.mpv_approval.controller;

import id.ten.mpv_approval.config.TemporalConfig;
import id.ten.mpv_approval.dto.OrderDto.*;
import id.ten.mpv_approval.entity.OrderEntity;
import id.ten.mpv_approval.entity.UserEntity;
import id.ten.mpv_approval.model.DummyData;
import id.ten.mpv_approval.model.Role;
import id.ten.mpv_approval.model.User;
import id.ten.mpv_approval.repository.OrderRepository;
import id.ten.mpv_approval.repository.UserRepository;
import id.ten.mpv_approval.temporal.DualDirectorChildWorkflow;
import id.ten.mpv_approval.temporal.OrderApprovalWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin("*")
public class OrderController {

    private final WorkflowClient workflowClient;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    public OrderController(WorkflowClient workflowClient, OrderRepository orderRepository, UserRepository userRepository) {
        this.workflowClient = workflowClient;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/users")
    public List<UserEntity> getUsers() {
        return userRepository.findAll();
    }

    @GetMapping("/orders")
    public List<OrderEntity> getAllOrders() {
        return orderRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<OrderEntity> getOrderDetail(@PathVariable String orderId) {
        return orderRepository.findById(orderId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/orders")
    public ResponseEntity<?> createOrder(@RequestBody CreateRequest request) {
        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setWorkflowId(request.orderId())
                .setTaskQueue(TemporalConfig.TASK_QUEUE)
                .build();

        OrderApprovalWorkflow workflow = workflowClient.newWorkflowStub(OrderApprovalWorkflow.class, options);
        WorkflowClient.start(workflow::processOrder, request);

        return ResponseEntity.ok(Map.of(
                "message", "Order berhasil dibuat",
                "orderId", request.orderId()
        ));
    }

    @PostMapping("/orders/{orderId}/claim")
    public ResponseEntity<?> claimOrder(
            @PathVariable String orderId,
            @RequestBody Map<String, String> body) {

        String approverId = body.get("approverId");
        UserEntity user = userRepository.findById(approverId).orElse(null);
        if (user == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "User tidak ditemukan"));
        }

        OrderEntity order = orderRepository.findById(orderId).orElse(null);
        if (order == null) return ResponseEntity.notFound().build();

        if (order.getWaitingForRole() != user.getRole()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Role Anda (" + user.getRole() + ") tidak sesuai dengan tahapan (" + order.getWaitingForRole() + ")"
            ));
        }

        if (order.getClaimedById() != null && !order.getClaimedById().equals(user.getId())) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Order ini sudah di-claim oleh " + order.getClaimedByName()
            ));
        }

        OrderApprovalWorkflow workflow = workflowClient.newWorkflowStub(OrderApprovalWorkflow.class, orderId);
        ClaimRequest claim = new ClaimRequest(user.getId(), user.getName(), user.getRole(), false);
        workflow.claimOrder(claim);

        return ResponseEntity.ok(Map.of(
                "message", "Order berhasil di-claim oleh " + user.getName(),
                "claimedById", user.getId(),
                "claimedByName", user.getName()
        ));
    }

    @PostMapping("/orders/{orderId}/release")
    public ResponseEntity<?> releaseClaim(
            @PathVariable String orderId,
            @RequestBody Map<String, String> body) {

        String approverId = body.get("approverId");
        OrderEntity order = orderRepository.findById(orderId).orElse(null);
        if (order == null) return ResponseEntity.notFound().build();

        if (order.getClaimedById() == null || !order.getClaimedById().equals(approverId)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Anda bukan pemegang klaim order ini"));
        }

        UserEntity user = userRepository.findById(approverId).orElseThrow();
        OrderApprovalWorkflow workflow = workflowClient.newWorkflowStub(OrderApprovalWorkflow.class, orderId);
        ClaimRequest release = new ClaimRequest(user.getId(), user.getName(), user.getRole(), true);
        workflow.claimOrder(release);

        return ResponseEntity.ok(Map.of("message", "Klaim order berhasil dilepaskan"));
    }

    @PostMapping("/orders/{orderId}/decision")
    public ResponseEntity<?> submitDecision(
            @PathVariable String orderId,
            @RequestBody Map<String, Object> body) {

        String approverId = (String) body.get("approverId");
        Boolean approved = (Boolean) body.get("approved");
        String notes = (String) body.getOrDefault("notes", "");

        if (approverId == null || approved == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Parameter 'approverId' dan 'approved' wajib dikirim."));
        }

        UserEntity approver = userRepository.findById(approverId).orElse(null);
        if (approver == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Approver tidak ditemukan"));
        }

        OrderEntity order = orderRepository.findById(orderId).orElse(null);
        if (order == null) return ResponseEntity.notFound().build();

        if (order.getClaimedById() == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Order belum di-Pick Me! Anda harus klik tombol 'Pick Me' terlebih dahulu."
            ));
        }

        if (!order.getClaimedById().equals(approver.getId())) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Order ini sedang dikunci oleh " + order.getClaimedByName() + ". Anda tidak dapat memprosesnya."
            ));
        }

        OrderApprovalWorkflow workflow = workflowClient.newWorkflowStub(OrderApprovalWorkflow.class, orderId);
        ApprovalDecision decision = new ApprovalDecision(approver.getId(), approver.getRole(), approved, notes);
        workflow.submitApproval(decision);

        return ResponseEntity.ok(Map.of(
                "message", "Keputusan persetujuan (" + (approved ? "APPROVED" : "REJECTED") + ") berhasil diproses",
                "approver", approver.getName()
        ));
    }

    @PostMapping("/orders/{orderId}/cancel")
    public ResponseEntity<?> cancelOrder(
            @PathVariable String orderId,
            @RequestBody Map<String, String> body) {

        String requesterId = body.get("requesterId");
        String reason = body.getOrDefault("reason", "Dibatalkan oleh pemohon");

        OrderEntity order = orderRepository.findById(orderId).orElse(null);
        if (order == null) return ResponseEntity.notFound().build();

        if (order.getStatus() != Status.IN_PROGRESS && order.getStatus() != Status.PENDING) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Order sudah berstatus " + order.getStatus() + " dan tidak dapat dibatalkan."
            ));
        }

        UserEntity user = userRepository.findById(requesterId).orElse(null);
        String userName = (user != null) ? user.getName() : "";

        boolean isMatch = order.getRequesterId().equalsIgnoreCase(requesterId)
                || order.getRequesterId().equalsIgnoreCase(userName);

        if (!isMatch) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "error", "Akses Ditolak! Hanya pemohon (" + order.getRequesterId() + ") yang berhak membatalkan pengajuan ini."
            ));
        }

        OrderApprovalWorkflow workflow = workflowClient.newWorkflowStub(OrderApprovalWorkflow.class, orderId);
        workflow.cancelOrder(requesterId, reason);

        return ResponseEntity.ok(Map.of(
                "message", "Pengajuan #" + orderId + " berhasil dibatalkan oleh pemohon.",
                "status", "CANCELLED"
        ));
    }

    @PostMapping("/orders/{orderId}/child-director-approve")
    public ResponseEntity<?> approveChildByDirector(
            @PathVariable String orderId,
            @RequestBody Map<String, Object> body) {

        String approverId = (String) body.get("approverId");
        Boolean approved = (Boolean) body.get("approved");
        String notes = (String) body.getOrDefault("notes", "");

        UserEntity user = userRepository.findById(approverId).orElse(null);
        if (user == null || user.getRole() != Role.DIRECTOR) {
            return ResponseEntity.badRequest().body(Map.of("error", "Hanya akun dengan role DIRECTOR yang dapat menyetujui tahap ini."));
        }

        OrderEntity order = orderRepository.findById(orderId).orElse(null);
        if (order == null) return ResponseEntity.notFound().build();

        String childWorkflowId = (order.getType() == OrderType.BUY_LAMBORGHINI)
                ? "CHILD-LAMBO-DIR-" + orderId
                : "CHILD-DIR-" + orderId;

        try {
            DualDirectorChildWorkflow childWorkflow = workflowClient.newWorkflowStub(
                    DualDirectorChildWorkflow.class,
                    childWorkflowId
            );

            ApprovalDecision decision = new ApprovalDecision(user.getId(), user.getRole(), approved, notes);
            childWorkflow.submitDirectorApproval(decision);

            return ResponseEntity.ok(Map.of(
                    "message", "Persetujuan Direktur (" + user.getName() + ") berhasil diproses ke Child Workflow.",
                    "childWorkflowId", childWorkflowId
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Gagal mengirim ke Child Workflow: " + e.getMessage()));
        }
    }
}