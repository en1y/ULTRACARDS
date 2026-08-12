package com.ultracards.server.service.admin;

import com.ultracards.gateway.dto.admin.AdminPointsAdjustmentDTO;
import com.ultracards.gateway.dto.admin.AdminPointsPatchDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.repositories.UserRepository;
import com.ultracards.server.service.points.PointsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminPointsService {
    private final UserRepository users;
    private final PointsService points;
    private final AdminAuditService audit;

    @Transactional
    public AdminPointsAdjustmentDTO adjust(UserEntity actor, Long userId, AdminPointsPatchDTO patch) {
        if (patch == null || patch.amount() == null || patch.amount() == 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Points adjustment cannot be zero");
        if (patch.reason() == null || patch.reason().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A nonblank reason is required");
        if (patch.dryRun()) {
            var user = users.findById(userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
            var previous = user.getPointsBalance();
            long next;
            try {
                next = Math.addExact(previous, patch.amount());
            } catch (ArithmeticException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Points adjustment is too large", ex);
            }
            if (next < 0)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Adjustment would make Points negative");
            return new AdminPointsAdjustmentDTO(userId, previous, next, patch.amount(), true);
        }

        var adjustment = points.adjust(userId, patch.amount(), "ADMIN:" + UUID.randomUUID());
        audit.record(actor.getId(), "ADJUST_POINTS", "USER", userId.toString(), patch.reason().trim(),
                "adjusted Points by " + patch.amount() + " P", "SUCCESS");
        return new AdminPointsAdjustmentDTO(userId, adjustment.previous(), adjustment.current(), patch.amount(), false);
    }
}
