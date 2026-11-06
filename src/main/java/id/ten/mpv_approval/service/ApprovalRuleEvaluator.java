package id.ten.mpv_approval.service;

import id.ten.mpv_approval.dto.OrderDto;
import id.ten.mpv_approval.model.Role;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class ApprovalRuleEvaluator {

    public static List<Role> determineRequiredApprovals(OrderDto.OrderType type, Integer days, BigDecimal amount) {
        List<Role> chain = new ArrayList<>();

        if (type == OrderDto.OrderType.IJIN_CUTI) {
            // Rules:
            // Cuti = 1 hari -> Auto approve (chain kosong)
            // Cuti > 2 hari -> Butuh MANAGER
            if (days != null && days > 2) {
                chain.add(Role.MANAGER);
            }
        } else if (type == OrderDto.OrderType.BUY) {
            // Rules BUY:
            // <= 1jt -> FINANCE
            // > 1jt s/d 10jt -> FINANCE -> DIRECTOR
            // > 10jt s/d 100jt -> FINANCE -> DIRECTOR -> CEO
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
        return chain;
    }
}
