package com.example.reproduction.api.application;

import com.example.reproduction.exception.ReproductionException;

public class InvalidJobRequestException extends ReproductionException {
    public InvalidJobRequestException(String message) { super(message); }
}
