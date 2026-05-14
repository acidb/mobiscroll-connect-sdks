package com.mobiscroll.connect.exceptions;

/** Base type for all exceptions thrown by the Mobiscroll Connect SDK. */
public class MobiscrollConnectException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public MobiscrollConnectException(String message) {
        super(message);
    }

    public MobiscrollConnectException(String message, Throwable cause) {
        super(message, cause);
    }
}
