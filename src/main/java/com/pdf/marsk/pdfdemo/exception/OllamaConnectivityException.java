package com.pdf.marsk.pdfdemo.exception;

public class OllamaConnectivityException extends RuntimeException {

    public OllamaConnectivityException(String message) {
        super(message);
    }

    public OllamaConnectivityException(String message, Throwable cause) {
        super(message, cause);
    }
}