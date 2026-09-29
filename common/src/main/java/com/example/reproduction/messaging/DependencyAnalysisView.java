package com.example.reproduction.messaging;

import com.example.reproduction.domain.Dependency;
import com.example.reproduction.domain.InputGroup;

import java.util.List;
import java.util.Map;

public record DependencyAnalysisView(List<String> fields, List<Dependency> dependencies, List<InputGroup> stronglyCoupledGroups,
                                     List<InputGroup> independentGroups, List<String> criticalFields,
                                     int sccCount, int weakComponentCount, Map<String, Double> keepHints) {}
