package com.example.reproduction.domain;

/** Edge {@code from -> to}: {@code to} depends on {@code from} (keeping "to" requires keeping "from"). */
public record Dependency(String from, String to, String reason) {}
