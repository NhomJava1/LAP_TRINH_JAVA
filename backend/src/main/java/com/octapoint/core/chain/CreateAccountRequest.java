package com.octapoint.core.chain;

public record CreateAccountRequest(String ownerId) {

    public CreateAccountRequest {
        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException("ownerId must not be blank");
        }
    }
}
