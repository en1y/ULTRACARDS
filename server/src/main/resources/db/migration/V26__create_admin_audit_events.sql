CREATE TABLE admin_audit_events (
    id UUID NOT NULL,
    actor_user_id BIGINT,
    action VARCHAR(64) NOT NULL,
    target_type VARCHAR(64) NOT NULL,
    target_id VARCHAR(128) NOT NULL,
    reason VARCHAR(512),
    summary VARCHAR(1024) NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_admin_audit_events PRIMARY KEY (id),
    CONSTRAINT fk_admin_audit_actor FOREIGN KEY (actor_user_id) REFERENCES users (id) ON DELETE SET NULL
);

CREATE INDEX idx_admin_audit_occurred_at ON admin_audit_events (occurred_at DESC);
CREATE INDEX idx_admin_audit_actor ON admin_audit_events (actor_user_id, occurred_at DESC);

CREATE FUNCTION reject_admin_audit_event_changes()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'admin_audit_events is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER admin_audit_events_append_only
BEFORE UPDATE OR DELETE ON admin_audit_events
FOR EACH ROW EXECUTE FUNCTION reject_admin_audit_event_changes();
