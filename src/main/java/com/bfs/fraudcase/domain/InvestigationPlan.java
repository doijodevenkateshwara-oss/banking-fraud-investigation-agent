package com.bfs.fraudcase.domain;

import java.util.List;

/**
 * Bounded, deterministic evidence plan shown to investigators (US-02).
 *
 * @author Venkateshwara Doijode
 */
public record InvestigationPlan(String goal, List<Step> steps) {

    public InvestigationPlan {
        steps = List.copyOf(steps);
    }

    public record Step(int order, String label, List<String> tools) {
        public Step {
            tools = List.copyOf(tools);
        }
    }

    public List<String> allTools() {
        return steps.stream().flatMap(s -> s.tools().stream()).distinct().toList();
    }
}
