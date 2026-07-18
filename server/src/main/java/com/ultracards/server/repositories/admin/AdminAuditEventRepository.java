package com.ultracards.server.repositories.admin;

import com.ultracards.server.entity.admin.AdminAuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AdminAuditEventRepository extends JpaRepository<AdminAuditEvent, UUID> {
    Page<AdminAuditEvent> findAllByOrderByOccurredAtDesc(Pageable pageable);
}
