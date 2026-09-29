package com.example.reproduction.algorithm.graph;

import com.example.reproduction.domain.Dependency;

import java.util.List;

/** Strategy for inferring dependency edges between field paths. */
public interface DependencyRule {
    List<Dependency> infer(List<String> paths);

    /** A pattern matches a path if it equals it or is a dotted suffix ("currency" matches "order.currency"). */
    static boolean matches(String path, String pattern) {
        return path.equals(pattern) || path.endsWith("." + pattern);
    }
}
