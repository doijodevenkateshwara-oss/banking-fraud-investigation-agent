package com.bfs.fraudcase.application;

import com.bfs.fraudcase.domain.Actor;
import com.bfs.fraudcase.domain.AuditEventType;
import com.bfs.fraudcase.persistence.AuditWriter;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Authorises an action and audits every denial.
 *
 * @author Venkateshwara Doijode
 */
@Component
public class AccessGuard {

    private final AuthorizationPolicy policy;
    private final AuditWriter audit;

    public AccessGuard(AuthorizationPolicy policy, AuditWriter audit) {
        this.policy = policy;
        this.audit = audit;
    }

    public void require(Actor actor, Action action, String caseId) {
        if (!policy.permits(actor, action)) {
            audit.append(caseId, AuditEventType.ACCESS_DENIED, actor, "Denied " + action + " for roles " + actor.roles(),
                    Map.of("action", action.name(), "actor", actor.id()));
            policy.require(actor, action);
        }
    }

    public boolean permits(Actor actor, Action action) {
        return policy.permits(actor, action);
    }
}
