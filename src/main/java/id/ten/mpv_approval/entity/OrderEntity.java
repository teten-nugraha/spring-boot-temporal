package id.ten.mpv_approval.entity;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import id.ten.mpv_approval.dto.OrderDto.*;
import id.ten.mpv_approval.model.Role;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
public class OrderEntity {
    @Id
    private String orderId;
    private String requesterId;

    @Enumerated(EnumType.STRING)
    private OrderType type;

    private Integer days;
    private BigDecimal amount;

    @Column(length = 1000)
    private String notes;

    @Enumerated(EnumType.STRING)
    private Status status;

    private int currentStepIndex = 0;

    @Enumerated(EnumType.STRING)
    private Role waitingForRole;

    // Field untuk fitur Pick Me
    private String claimedById;
    private String claimedByName;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "order_approval_chains", joinColumns = @JoinColumn(name = "order_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    private List<Role> approvalChain = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    @JsonManagedReference
    private List<ApprovalHistoryEntity> history = new ArrayList<>();

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    public OrderEntity() {}

    // Getters and Setters
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getRequesterId() { return requesterId; }
    public void setRequesterId(String requesterId) { this.requesterId = requesterId; }
    public OrderType getType() { return type; }
    public void setType(OrderType type) { this.type = type; }
    public Integer getDays() { return days; }
    public void setDays(Integer days) { this.days = days; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public int getCurrentStepIndex() { return currentStepIndex; }
    public void setCurrentStepIndex(int currentStepIndex) { this.currentStepIndex = currentStepIndex; }
    public Role getWaitingForRole() { return waitingForRole; }
    public void setWaitingForRole(Role waitingForRole) { this.waitingForRole = waitingForRole; }

    public String getClaimedById() { return claimedById; }
    public void setClaimedById(String claimedById) { this.claimedById = claimedById; }
    public String getClaimedByName() { return claimedByName; }
    public void setClaimedByName(String claimedByName) { this.claimedByName = claimedByName; }

    public List<Role> getApprovalChain() { return approvalChain; }
    public void setApprovalChain(List<Role> approvalChain) { this.approvalChain = approvalChain; }
    public List<ApprovalHistoryEntity> getHistory() { return history; }
    public void setHistory(List<ApprovalHistoryEntity> history) { this.history = history; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
