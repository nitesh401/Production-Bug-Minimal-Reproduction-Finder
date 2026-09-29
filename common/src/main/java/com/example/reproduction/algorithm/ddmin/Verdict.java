package com.example.reproduction.algorithm.ddmin;

/** Three-valued test outcome of classic delta debugging. INCONCLUSIVE is never treated as "bug gone". */
public enum Verdict { REPRODUCES, NOT_REPRODUCES, INCONCLUSIVE }
