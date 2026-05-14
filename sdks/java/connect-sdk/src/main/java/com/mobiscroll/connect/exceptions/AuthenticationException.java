package com.mobiscroll.connect.exceptions;

/** Thrown on HTTP 401 after token refresh + retry has been exhausted (or on token-exchange itself). */
public class AuthenticationException extends MobiscrollConnectException {

    private static final long serialVersionUID = 1L;

    public AuthenticationException(String message) {
        super(message);
    }
}
