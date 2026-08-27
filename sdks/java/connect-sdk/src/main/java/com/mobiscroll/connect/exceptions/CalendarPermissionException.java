package com.mobiscroll.connect.exceptions;

import java.util.Collections;
import java.util.List;

import com.mobiscroll.connect.models.BlockedAccount;

/**
 * Thrown when no connected account has the calendar access the request needs.
 *
 * <p>The user completed sign-in but did not grant the calendar permission &mdash; Google's
 * consent screen presents it as a separate checkbox. This cannot be repaired server-side,
 * because providers only issue permissions at consent time: the accounts returned by
 * {@link #getAccounts()} have to run the connect flow again and allow access.
 *
 * <p>Extends {@link AuthenticationException}, so existing handlers keep working.
 */
public class CalendarPermissionException extends AuthenticationException {

    private static final long serialVersionUID = 1L;

    private final transient List<BlockedAccount> accounts;

    public CalendarPermissionException(String message, List<BlockedAccount> accounts) {
        super(message);
        this.accounts = accounts == null ? Collections.emptyList() : Collections.unmodifiableList(accounts);
    }

    /** Connected accounts that withheld calendar access and must reconnect. */
    public List<BlockedAccount> getAccounts() {
        return accounts;
    }
}
