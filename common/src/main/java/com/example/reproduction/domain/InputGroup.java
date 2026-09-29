package com.example.reproduction.domain;

import java.util.List;

/** A set of fields that must be reduced together (an SCC or weak component). */
public record InputGroup(int id, List<String> members, String kind) {}
