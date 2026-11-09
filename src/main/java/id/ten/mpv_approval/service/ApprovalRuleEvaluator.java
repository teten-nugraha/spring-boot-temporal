package id.ten.mpv_approval.service;

import id.ten.mpv_approval.dto.OrderDto.*;
import id.ten.mpv_approval.model.Role;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class ApprovalRuleEvaluator {

    public static List<Role> determineRequiredApprovals(OrderType type, Integer days, BigDecimal amount) {
        List<Role> chain = new ArrayList<>();

        if (type == OrderType.IJIN_CUTI) {
            if (days != null && days > 2) {
                chain.add(Role.MANAGER);
            }
        }
        else if (type == OrderType.BUY) {
            BigDecimal val = amount != null ? amount : BigDecimal.ZERO;
            BigDecimal oneMillion = new BigDecimal("1000000");
            BigDecimal tenMillion = new BigDecimal("10000000");
            BigDecimal hundredMillion = new BigDecimal("100000000");

            if (val.compareTo(oneMillion) <= 0) {
                chain.add(Role.FINANCE);
            } else if (val.compareTo(tenMillion) <= 0) {
                chain.add(Role.FINANCE);
                chain.add(Role.DIRECTOR);
            } else if (val.compareTo(hundredMillion) <= 0) {
                chain.add(Role.FINANCE);
                chain.add(Role.DIRECTOR);
                chain.add(Role.CEO);
            }
        }
        else if (type == OrderType.BUY_LAMBORGHINI) {
            // Parent Workflow butuh FINANCE lalu CEO
            chain.add(Role.FINANCE);
            chain.add(Role.CEO);
        }

        return chain;
    }
}
