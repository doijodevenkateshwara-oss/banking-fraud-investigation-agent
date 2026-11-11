package com.bfs.fraudcase.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Typed, validated {@code capstone.*} configuration (guide section 5.3).
 *
 * @author Venkateshwara Doijode
 */
@Validated
@ConfigurationProperties(prefix = "capstone")
public record CapstoneProperties(
        boolean humanReviewRequired,
        boolean allowFinancialActions,
        boolean allowCustomerContact,
        String fixedClock,
        @Valid @NotNull Retrieval retrieval,
        @Valid @NotNull Memory memory,
        @Valid @NotNull Tools tools,
        @Valid @NotNull Model model,
        @Valid @NotNull Security security) {

    public record Retrieval(@Min(1) @Max(20) int topK,
                            @DecimalMin("0.0") @DecimalMax("1.0") double similarityThreshold) {
    }

    public record Memory(@Min(2) @Max(100) int maxMessages) {
    }

    public record Tools(@Min(50) @Max(30000) long timeoutMs, @NotBlank String deterministicSeed) {
    }

    /**
     * Anthropic model settings (the only provider). The API key is only ever read from the environment
     * ({@code ANTHROPIC_API_KEY}).
     */
    public record Model(@NotBlank String modelName,
                        String apiKey,
                        @Min(1) @Max(300) int timeoutSeconds,
                        @DecimalMin("0.0") @DecimalMax("1.0") double temperature,
                        @Min(256) int maxTokens,
                        @Min(1) @Max(20) int maxToolInvocations) {

        public boolean apiKeyPresent() {
            return apiKey != null && !apiKey.isBlank();
        }
    }

    /** Demo-user password; supplied via {@code CAPSTONE_DEMO_PASSWORD}. Blank means "generate at startup". */
    public record Security(String demoPassword) {
    }
}
