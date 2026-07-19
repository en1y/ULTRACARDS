package com.ultracards.server.service.admin;

import com.ultracards.gateway.dto.admin.AdminAuditEventDTO;
import com.ultracards.gateway.dto.admin.AdminPageDTO;
import com.ultracards.server.entity.admin.AdminAuditEvent;
import com.ultracards.server.repositories.admin.AdminAuditEventRepository;
import com.ultracards.server.repositories.admin.AdminAuditUndoEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;
import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AdminAuditService {
    private final AdminAuditEventRepository repository;
    private final AdminAuditUndoEventRepository undoRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void record(Long actorId, String action, String targetType, String targetId,
                       String reason, String summary, String outcome) {
        repository.save(new AdminAuditEvent(actorId, action, targetType, targetId,
                clean(reason, 512), clean(summary, 1024), outcome));
    }

    @Transactional
    public void record(Long actorId, String action, String targetType, String targetId,
                       String reason, String summary, String outcome, Object undoPayload) {
        repository.save(new AdminAuditEvent(actorId, action, targetType, targetId,
                clean(reason, 512), clean(summary, 1024), outcome, payload(undoPayload)));
    }

    public String payload(Object value) {
        if (value == null) return null;
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new IllegalArgumentException("Could not persist undo state", ex); }
    }

    @Transactional(readOnly = true)
    public AdminPageDTO<AdminAuditEventDTO> list(int page, int size) {
        return list(page, size, null, null);
    }

    @Transactional(readOnly = true)
    public AdminPageDTO<AdminAuditEventDTO> list(int page, int size, String targetType, String targetId) {
        var pageRequest = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(200, size)));
        var filtered = targetType != null && !targetType.isBlank() && targetId != null && !targetId.isBlank();
        var result = filtered
                ? repository.findByTargetTypeAndTargetIdOrderByOccurredAtDesc(targetType.trim().toUpperCase(), targetId.trim(), pageRequest)
                : repository.findAllByOrderByOccurredAtDesc(pageRequest);
        return new AdminPageDTO<>(result.getContent().stream().map(this::toDto).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public AdminAuditEventDTO get(UUID id) {
        return repository.findById(id).map(this::toDto)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Audit event not found"));
    }

    private AdminAuditEventDTO toDto(AdminAuditEvent event) {
        return new AdminAuditEventDTO(event.getId(), event.getActorUserId(), event.getAction(),
                event.getTargetType(), event.getTargetId(), event.getReason(), event.getSummary(),
                event.getOutcome(), event.getOccurredAt(), event.getUndoPayload() != null,
                undoRepository.existsById(event.getId()));
    }

    private String clean(String value, int max) {
        if (value == null) return null;
        var clean = value.replaceAll("[\\r\\n\\t]+", " ").trim();
        return clean.length() <= max ? clean : clean.substring(0, max);
    }
}
