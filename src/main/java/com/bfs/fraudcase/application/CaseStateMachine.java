package com.bfs.fraudcase.application;

import static com.bfs.fraudcase.domain.CaseStatus.CLOSED;
import static com.bfs.fraudcase.domain.CaseStatus.ENRICHING;
import static com.bfs.fraudcase.domain.CaseStatus.INCOMPLETE;
import static com.bfs.fraudcase.domain.CaseStatus.NEW;
import static com.bfs.fraudcase.domain.CaseStatus.READY_FOR_REVIEW;
import static com.bfs.fraudcase.domain.CaseStatus.ROUTED;
import static com.bfs.fraudcase.domain.CaseStatus.UNDER_REVIEW;

import com.bfs.fraudcase.domain.CaseStatus;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Case lifecycle (guide section 2.2). State transitions live here, never in model output.
 * Extension: UNDER_REVIEW → INCOMPLETE when a reviewer requests more information, allowing re-enrichment.
 *
 * @author Venkateshwara Doijode
 */
@Component
public class CaseStateMachine {

    private static final Map<CaseStatus, Set<CaseStatus>> ALLOWED = Map.of(
            NEW, EnumSet.of(ENRICHING),
            ENRICHING, EnumSet.of(READY_FOR_REVIEW, INCOMPLETE),
            INCOMPLETE, EnumSet.of(ENRICHING, CLOSED),
            READY_FOR_REVIEW, EnumSet.of(UNDER_REVIEW),
            UNDER_REVIEW, EnumSet.of(ROUTED, CLOSED, INCOMPLETE),
            ROUTED, EnumSet.of(CLOSED),
            CLOSED, EnumSet.noneOf(CaseStatus.class));

    public boolean canTransition(CaseStatus from, CaseStatus to) {
        return ALLOWED.get(from).contains(to);
    }

    public CaseStatus transition(CaseStatus from, CaseStatus to) {
        if (!canTransition(from, to)) {
            throw new IllegalStateTransitionException("Transition " + from + " → " + to + " is not allowed");
        }
        return to;
    }

    public Set<CaseStatus> next(CaseStatus from) {
        return Set.copyOf(ALLOWED.get(from));
    }
}
