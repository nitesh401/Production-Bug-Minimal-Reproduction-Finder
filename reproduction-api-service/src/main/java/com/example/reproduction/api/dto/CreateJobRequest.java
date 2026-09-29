package com.example.reproduction.api.dto;

import com.example.reproduction.domain.StrategyType;
import jakarta.validation.constraints.*;

import java.util.Map;

public record CreateJobRequest(
        @NotBlank @Size(max = 200) String name,
        @NotEmpty Map<String, Object> initialInput,
        @NotNull BugSignatureDto bugSignature,
        String scenarioId,
        @Min(1) @Max(20) Integer evaluationAttempts,
        @DecimalMin("0.01") @DecimalMax("1.0") Double minimumReproductionRate,
        @Min(1) @Max(1_000_000) Integer maxEvaluations,
        @Min(1) @Max(3600) Long maxExecutionSeconds,
        @Min(1) @Max(64) Integer maxConcurrentEvaluations,
        @Min(1) @Max(50) Integer maxSolutions,
        @Min(0) @Max(10) Integer randomRestarts,
        Boolean assumeMonotonic,
        StrategyType strategy) {

    public record BugSignatureDto(Integer httpStatus, String errorCode, String bodyPattern, Long minLatencyMillis, String exceptionSignature) {}
}
