package com.ultracards.server.controllers.points;

import com.ultracards.gateway.dto.points.PointTransactionPageDTO;
import com.ultracards.gateway.dto.points.PointTransactionDTO;
import com.ultracards.gateway.dto.points.PointsAccountDTO;
import com.ultracards.gateway.dto.points.PointsClaimDTO;
import com.ultracards.gateway.dto.points.PointsSeriesPointDTO;
import com.ultracards.gateway.dto.points.PointEventDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.service.points.PointsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/points")
@PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
@RequiredArgsConstructor
public class PointsController {
    private final PointsService pointsService;

    @GetMapping
    public PointsAccountDTO account(@AuthenticationPrincipal UserEntity user) {
        return pointsService.summary(user);
    }

    @GetMapping("/balance")
    public long balance(@AuthenticationPrincipal UserEntity user) {
        return pointsService.balance(user);
    }

    @GetMapping("/events")
    public List<PointEventDTO> events(@AuthenticationPrincipal UserEntity user) {
        return pointsService.events(user);
    }

    @PostMapping("/daily-claim")
    public PointsClaimDTO dailyClaim(@AuthenticationPrincipal UserEntity user) {
        return pointsService.claimDaily(user);
    }

    @GetMapping("/transactions")
    public PointTransactionPageDTO transactions(@AuthenticationPrincipal UserEntity user,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "25") int size) {
        return pointsService.transactions(user, page, size);
    }

    @GetMapping("/users/{userId}/transactions")
    public PointTransactionPageDTO userTransactions(@PathVariable Long userId,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "10") int size) {
        var transactions = pointsService.transactions(userId, page, size);
        var publicItems = new ArrayList<PointTransactionDTO>(transactions.items().size());
        for (var item : transactions.items()) {
            publicItems.add(new PointTransactionDTO(item.id(), item.amount(), item.balanceAfter(), item.type(),
                    null, item.createdAt()));
        }
        return new PointTransactionPageDTO(publicItems, transactions.page(), transactions.size(),
                transactions.totalElements(), transactions.totalPages());
    }

    /** Balances for the players a lobby or the friends list is showing. */
    @GetMapping("/balances")
    public Map<Long, Long> balances(@RequestParam List<Long> ids) {
        return pointsService.balances(ids.size() > 50 ? ids.subList(0, 50) : ids);
    }

    /** Balance over time for the chart; {@code days=0} is the whole ledger. */
    @GetMapping("/series")
    public List<PointsSeriesPointDTO> series(@AuthenticationPrincipal UserEntity user,
                                             @RequestParam(defaultValue = "30") int days) {
        return pointsService.series(user.getId(), Math.max(0, days));
    }

    @GetMapping("/users/{userId}/series")
    public List<PointsSeriesPointDTO> userSeries(@PathVariable Long userId,
                                                 @RequestParam(defaultValue = "30") int days) {
        return pointsService.series(userId, Math.max(0, days));
    }
}
