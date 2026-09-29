package com.example.reproduction.algorithm.graph;

import com.example.reproduction.domain.Dependency;

import java.util.ArrayList;
import java.util.List;

/** Fields that are only meaningful together (amount/currency): mutual dependency => one SCC. */
public record RelatedGroupRule(List<String> patterns, String reason) implements DependencyRule {
    @Override
    public List<Dependency> infer(List<String> paths) {
        List<String> matched = new ArrayList<>();
        for (String p : paths) for (String pat : patterns) if (DependencyRule.matches(p, pat)) { matched.add(p); break; }
        List<Dependency> out = new ArrayList<>();
        for (String a : matched) for (String b : matched) if (!a.equals(b)) out.add(new Dependency(a, b, reason));
        return out;
    }
}
