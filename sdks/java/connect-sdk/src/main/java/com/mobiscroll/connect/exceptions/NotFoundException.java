package com.mobiscroll.connect.exceptions;

/** Thrown on HTTP 404. */
public class NotFoundException extends MobiscrollConnectException {

    private static final long serialVersionUID = 1L;

    public NotFoundException(String message) {
        super(message);
    }
}
