package id.ten.mpv_approval.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import id.ten.mpv_approval.model.Role;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "approval_history")
public class ApprovalHistoryEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    @JsonBackReference
    private OrderEntity order;

    private String approverId;

    @Enumerated(EnumType.STRING)
    private Role role;

    private boolean approved;

    @Column(length = 1000)
    private String notes;

    private LocalDateTime timestamp = LocalDateTime.now();

    public ApprovalHistoryEntity() {}

    public ApprovalHistoryEntity(OrderEntity order, String approverId, Role role, boolean approved, String notes) {
        this.order = order;
        this.approverId = approverId;
        this.role = role;
        this.approved = approved;
        this.notes = notes;
    }

    public Long getId() { return id; }
    public OrderEntity getOrder() { return order; }
    public void setOrder(OrderEntity order) { this.order = order; }
    public String getApproverId() { return approverId; }
    public void setApproverId(String approverId) { this.approverId = approverId; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public boolean isApproved() { return approved; }
    public void setApproved(boolean approved) { this.approved = approved; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}