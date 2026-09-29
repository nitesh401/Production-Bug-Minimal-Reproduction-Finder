package com.example.reproduction.algorithm.search;

/** Strategy pattern: how to shrink the failing input. Implementations must be stateless. */
public interface ReductionStrategy {
    String name();

    /** Reduces {@code ctx.startFields() \ ctx.bannedFields()}; the returned set is 1-minimal under this strategy's units. */
    ReductionOutcome reduce(ReductionContext ctx);
}
