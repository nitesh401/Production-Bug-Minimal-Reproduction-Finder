package com.example.reproduction.algorithm.graph;

import com.example.reproduction.domain.Dependency;

import java.util.ArrayList;
import java.util.List;

/** "dependent" is only valid when "prerequisite" is present (one-directional edge). */
public record PrerequisiteRule(String prerequisite, String dependent, String reason) implements DependencyRule {
    @Override
    public List<Dependency> infer(List<String> paths) {
        List<Dependency> out = new ArrayList<>();
        for (String pre : paths) {
            if (!DependencyRule.matches(pre, prerequisite)) continue;
            for (String dep : paths)
                if (!pre.equals(dep) && DependencyRule.matches(dep, dependent)) out.add(new Dependency(pre, dep, reason));
        }
        return out;
    }
}
