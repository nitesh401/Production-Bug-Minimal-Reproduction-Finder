package com.example.reproduction.api.application;

import com.example.reproduction.exception.ReproductionException;

public class JobNotFoundException extends ReproductionException {
    public JobNotFoundException(String id) { super("job not found: " + id); }
}
