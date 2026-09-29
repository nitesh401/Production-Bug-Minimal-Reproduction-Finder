package com.example.reproduction.simulator.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

import java.util.List;

/**
 * Declarative bug: rules are evaluated in order, first match wins. A rule matches when ANY of its
 * {@code anyOf} groups has ALL its clauses true (disjunctive normal form).
 */
public record CustomScenarioRequest(@NotBlank @Pattern(regexp = "[A-Za-z0-9_\\-]{1,64}") String id, String description,
                                    @NotEmpty @Valid List<RuleDto> rules) {
    public record RuleDto(@NotEmpty List<List<ClauseDto>> anyOf, int status, String errorCode, String message, Double probability) {}
    public record ClauseDto(@NotBlank String path, @NotBlank String op, Object value) {}
}
