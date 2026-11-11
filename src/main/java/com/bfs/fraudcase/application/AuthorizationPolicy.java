package com.bfs.fraudcase.application;

import com.bfs.fraudcase.domain.Actor;
import com.bfs.fraudcase.domain.Role;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Role matrix for every case endpoint. Denials are audited by the calling service.
 *
 * @author Venkateshwara Doijode
 */
@Component
public class AuthorizationPolicy {

    private static final Map<Action, Set<Role>> MATRIX = Map.of(
            Action.CREATE_CASE, EnumSet.of(Role.SERVICE_AGENT, Role.INVESTIGATOR),
            Action.VIEW_CASE, EnumSet.allOf(Role.class),
            Action.INVESTIGATE, EnumSet.of(Role.INVESTIGATOR),
            Action.ASK_FOLLOW_UP, EnumSet.of(Role.INVESTIGATOR, Role.REVIEWER),
            Action.REVIEW, EnumSet.of(Role.REVIEWER),
            Action.OVERRIDE, EnumSet.of(Role.TEAM_LEAD),
            Action.VERIFY_AUDIT, EnumSet.of(Role.AUDITOR, Role.TEAM_LEAD));

    public boolean permits(Actor actor, Action action) {
        return actor.roles().stream().anyMatch(MATRIX.get(action)::contains);
    }

    public void require(Actor actor, Action action) {
        if (!permits(actor, action)) {
            throw new AuthorizationException("Role not permitted to " + action.name().toLowerCase().replace('_', ' '));
        }
    }
}
