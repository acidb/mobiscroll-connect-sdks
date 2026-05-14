package com.mobiscroll.connect.exceptions;

/** Thrown on HTTP 5xx — the Mobiscroll Connect service or an upstream calendar provider failed. */
public class ServerException extends MobiscrollConnectException {

    private static final long serialVersionUID = 1L;

    private final int statusCode;

    public ServerException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
