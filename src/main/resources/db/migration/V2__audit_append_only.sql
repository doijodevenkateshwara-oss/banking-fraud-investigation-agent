-- Enforce append-only audit at the database level: any UPDATE or DELETE on audit_event fails.
CREATE TRIGGER audit_event_no_update BEFORE UPDATE ON audit_event FOR EACH ROW
    CALL "com.bfs.fraudcase.persistence.AppendOnlyTrigger";
CREATE TRIGGER audit_event_no_delete BEFORE DELETE ON audit_event FOR EACH ROW
    CALL "com.bfs.fraudcase.persistence.AppendOnlyTrigger";
