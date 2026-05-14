package com.mobiscroll.connect.exceptions;

/** Thrown on transport-level failures (timeouts, DNS, connection reset, etc.). */
public class NetworkException extends MobiscrollConnectException {

    private static final long serialVersionUID = 1L;

    public NetworkException(String message, Throwable cause) {
        super(message, cause);
    }
}
