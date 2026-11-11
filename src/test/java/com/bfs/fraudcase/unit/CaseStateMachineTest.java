package com.bfs.fraudcase.unit;

import static com.bfs.fraudcase.domain.CaseStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bfs.fraudcase.application.CaseStateMachine;
import com.bfs.fraudcase.application.IllegalStateTransitionException;
import com.bfs.fraudcase.domain.CaseStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * @author Venkateshwara Doijode
 */
class CaseStateMachineTest {

    private final CaseStateMachine sm = new CaseStateMachine();

    @ParameterizedTest
    @CsvSource({"NEW,ENRICHING", "ENRICHING,READY_FOR_REVIEW", "ENRICHING,INCOMPLETE", "INCOMPLETE,ENRICHING",
            "INCOMPLETE,CLOSED", "READY_FOR_REVIEW,UNDER_REVIEW", "UNDER_REVIEW,ROUTED", "UNDER_REVIEW,CLOSED",
            "UNDER_REVIEW,INCOMPLETE", "ROUTED,CLOSED"})
    void allowsGuideTransitions(CaseStatus from, CaseStatus to) {
        assertThat(sm.transition(from, to)).isEqualTo(to);
    }

    @ParameterizedTest
    @CsvSource({"NEW,READY_FOR_REVIEW", "NEW,ROUTED", "ENRICHING,ROUTED", "READY_FOR_REVIEW,ROUTED",
            "READY_FOR_REVIEW,CLOSED", "CLOSED,NEW", "CLOSED,ENRICHING", "ROUTED,ENRICHING", "INCOMPLETE,ROUTED"})
    void rejectsIllegalTransitions(CaseStatus from, CaseStatus to) {
        assertThatThrownBy(() -> sm.transition(from, to)).isInstanceOf(IllegalStateTransitionException.class);
    }

    @Test
    void closedIsTerminal() {
        assertThat(sm.next(CLOSED)).isEmpty();
    }
}
