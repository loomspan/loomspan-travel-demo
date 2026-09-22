package app.detour.trip;

import java.util.Map;

public record DraftReadinessResponse(
        boolean ready,
        Map<String, String> blockingIssues,
        boolean isOverBudget,
        Long budgetOverageCents,
        boolean requiresOverageAcknowledgment
) {
    public DraftReadinessResponse {
        blockingIssues = blockingIssues == null ? Map.of() : Map.copyOf(blockingIssues);
    }
}
