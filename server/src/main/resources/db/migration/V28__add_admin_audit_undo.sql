ALTER TABLE admin_audit_events ADD COLUMN undo_payload TEXT;

CREATE TABLE admin_audit_undo_events (
    audit_event_id UUID NOT NULL,
    actor_user_id BIGINT,
    reason VARCHAR(512) NOT NULL,
    undone_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_admin_audit_undo_events PRIMARY KEY (audit_event_id),
    CONSTRAINT fk_admin_undo_audit FOREIGN KEY (audit_event_id) REFERENCES admin_audit_events (id),
    CONSTRAINT fk_admin_undo_actor FOREIGN KEY (actor_user_id) REFERENCES users (id) ON DELETE SET NULL
);
