package com.ultracards.server.repositories.admin;

import com.ultracards.server.entity.admin.AdminAuditUndoEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AdminAuditUndoEventRepository extends JpaRepository<AdminAuditUndoEvent, UUID> {
}
