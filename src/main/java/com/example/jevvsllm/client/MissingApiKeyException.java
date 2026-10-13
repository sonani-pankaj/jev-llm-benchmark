package com.example.jevvsllm.client;

/** Raised on a live call when the vendor key was not configured. The application still starts. */
public class MissingApiKeyException extends RuntimeException {

    public MissingApiKeyException(String message) {
        super(message);
    }
}
