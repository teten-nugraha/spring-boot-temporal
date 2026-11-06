package id.ten.mpv_approval.controller;

import id.ten.mpv_approval.config.TemporalConfig;
import id.ten.mpv_approval.dto.OrderDto.*;
import id.ten.mpv_approval.entity.OrderEntity;
import id.ten.mpv_approval.entity.UserEntity;
import id.ten.mpv_approval.model.DummyData;
import id.ten.mpv_approval.model.User;
import id.ten.mpv_approval.repository.OrderRepository;
import id.ten.mpv_approval.repository.UserRepository;
import id.ten.mpv_approval.temporal.OrderApprovalWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import org.springframework.data.domain.Sort;
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

    // 1. ENDPOINT: PICK ME (Claim Order)
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
        if (order == null) {
            return ResponseEntity.notFound().build();
        }

        // Validasi: Apakah role user cocok dengan tahapan saat ini?
        if (order.getWaitingForRole() != user.getRole()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Role Anda (" + user.getRole() + ") tidak sesuai dengan tahapan yang ditunggu (" + order.getWaitingForRole() + ")"
            ));
        }

        // Validasi: Apakah sudah di-claim oleh orang lain?
        if (order.getClaimedById() != null && !order.getClaimedById().equals(user.getId())) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Order ini sudah di-claim oleh " + order.getClaimedByName()
            ));
        }

        // Kirim sinyal klaim ke Temporal Workflow
        OrderApprovalWorkflow workflow = workflowClient.newWorkflowStub(OrderApprovalWorkflow.class, orderId);
        ClaimRequest claim = new ClaimRequest(user.getId(), user.getName(), user.getRole(), false);
        workflow.claimOrder(claim);

        return ResponseEntity.ok(Map.of(
                "message", "Order berhasil di-claim (Pick Me) oleh " + user.getName(),
                "claimedById", user.getId(),
                "claimedByName", user.getName()
        ));
    }

    // 2. ENDPOINT: RELEASE CLAIM (Batal Klaim agar orang lain di role yang sama bisa ambil)
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

    // 3. ENDPOINT: DECISION (Validasi Wajib Pick Me Terlebih Dahulu)
    @PostMapping("/orders/{orderId}/decision")
    public ResponseEntity<?> submitDecision(
            @PathVariable String orderId,
            @RequestBody Map<String, Object> body) {

        String approverId = (String) body.get("approverId");
        boolean approved = (Boolean) body.get("approved");
        String notes = (String) body.getOrDefault("notes", "");

        UserEntity approver = userRepository.findById(approverId).orElse(null);
        if (approver == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Approver tidak ditemukan"));
        }

        OrderEntity order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            return ResponseEntity.notFound().build();
        }

        // VALIDASI KRUSIAL: Order harus sudah di-Pick Me oleh user yang bersangkutan
        if (order.getClaimedById() == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Order belum di-Pick Me! Anda harus klik tombol 'Pick Me' terlebih dahulu sebelum mengambil keputusan."
            ));
        }

        if (!order.getClaimedById().equals(approver.getId())) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Order ini sedang dikunci/di-claim oleh " + order.getClaimedByName() + ". Anda tidak dapat memprosesnya."
            ));
        }

        // Kirim keputusan ke Temporal Workflow
        OrderApprovalWorkflow workflow = workflowClient.newWorkflowStub(OrderApprovalWorkflow.class, orderId);
        ApprovalDecision decision = new ApprovalDecision(approver.getId(), approver.getRole(), approved, notes);
        workflow.submitApproval(decision);

        return ResponseEntity.ok(Map.of(
                "message", "Keputusan persetujuan (" + (approved ? "APPROVED" : "REJECTED") + ") berhasil diproses",
                "approver", approver.getName()
        ));
    }
}