package id.ten.mpv_approval.dto;

import id.ten.mpv_approval.model.Role;

import java.math.BigDecimal;
import java.util.List;

public class OrderDto {
    public enum OrderType { IJIN_CUTI, BUY }
    public enum Status { PENDING, IN_PROGRESS, APPROVED, REJECTED, CANCELLED }

    public record CreateRequest(
            String orderId,
            String requesterId,
            OrderType type,
            Integer days,
            BigDecimal amount,
            String notes
    ) {}

    public record ClaimRequest(
            String approverId,
            String approverName,
            Role role,
            boolean release // true jika ingin membatalkan/melepas klaim
    ) {}

    public record ApprovalDecision(
            String approverId,
            Role role,
            boolean approved,
            String notes
    ) {}

    public record OrderState(
            String orderId,
            OrderType type,
            Status status,
            List<Role> approvalChain,
            int currentStepIndex,
            Role waitingForRole,
            String claimedById,
            String claimedByName,
            List<ApprovalDecision> history
    ) {}
}
