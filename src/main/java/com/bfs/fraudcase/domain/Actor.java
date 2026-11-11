package com.bfs.fraudcase.domain;

import java.util.Set;

/**
 * Authenticated principal. Always resolved from the security context, never from request bodies.
 *
 * @author Venkateshwara Doijode
 */
public record Actor(String id, Set<Role> roles) {

    public Actor {
        roles = Set.copyOf(roles);
    }

    public boolean has(Role role) {
        return roles.contains(role);
    }

    public static Actor system() {
        return new Actor("system", Set.of());
    }
}
