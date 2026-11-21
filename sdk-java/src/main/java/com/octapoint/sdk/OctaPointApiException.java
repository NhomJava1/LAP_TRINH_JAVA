package com.octapoint.sdk;

public class OctaPointApiException extends RuntimeException {

    private final int status;
    private final String errorCode;

    public OctaPointApiException(int status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public int getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
