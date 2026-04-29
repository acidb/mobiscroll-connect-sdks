"""Minimal Flask app — mirrors the PHP/Node/dotnet reference implementations.

Routes (all on the same base URL, action-dispatched like the PHP app):
  GET  /?action=ui                — SDK tester UI (home)
  GET  /?action=calendars-page    — Calendars page
  GET  /?action=events-page       — Events page
  GET  /?action=event-edit-page   — Event create/edit/delete page
  GET  /?action=config            — JSON: env-level config defaults
  GET  /?action=auth-url          — JSON: generate OAuth URL
  GET  /?action=callback          — OAuth callback, stores tokens in session
  GET  /?action=session           — JSON: session state
  GET  /?action=clear-session     — JSON: clears session
  GET  /?action=calendars         — JSON: list calendars
  GET  /?action=events            — JSON: list events (with filters)
  GET  /?action=connection-status — JSON: connection status
  POST /?action=create-event      — JSON: create event
  POST /?action=update-event      — JSON: update event
  POST /?action=delete-event      — JSON: delete event
  POST /?action=disconnect        — JSON: disconnect provider
"""

from __future__ import annotations

import os
from pathlib import Path

from dotenv import load_dotenv
from flask import Flask, jsonify, redirect, render_template, request, session

load_dotenv(Path(__file__).parent / ".env")

from mobiscroll_connect import (
    MobiscrollConnectClient,
    TokenResponse,
)
from mobiscroll_connect.exceptions import MobiscrollConnectError

app = Flask(__name__, template_folder="templates")
app.secret_key = os.environ.get("FLASK_SECRET_KEY", "dev-secret-change-in-prod")


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _env(key: str, default: str = "") -> str:
    return os.environ.get(key, default)


def _make_client() -> MobiscrollConnectClient:
    client_id = session.get("cfg_client_id") or _env("MOBISCROLL_CLIENT_ID")
    client_secret = session.get("cfg_client_secret") or _env("MOBISCROLL_CLIENT_SECRET")
    redirect_uri = _env("MOBISCROLL_REDIRECT_URI", "http://localhost:8000/?action=callback")

    if not client_id or not client_secret:
        raise ValueError(
            "No client credentials configured. "
            "Open the Configuration panel in the UI and save your Client ID and Client Secret."
        )

    client = MobiscrollConnectClient(
        client_id=client_id,
        client_secret=client_secret,
        redirect_uri=redirect_uri,
    )

    def _persist_tokens(tokens: TokenResponse) -> None:
        session["access_token"] = tokens.access_token
        session["token_type"] = tokens.token_type
        session["expires_in"] = tokens.expires_in
        session["refresh_token"] = tokens.refresh_token

    client.on_tokens_refreshed(_persist_tokens)
    return client


def _restore_credentials(client: MobiscrollConnectClient) -> bool:
    at = session.get("access_token")
    if not at:
        return False
    client.auth.set_credentials(TokenResponse(
        access_token=at,
        token_type=session.get("token_type", "Bearer"),
        expires_in=session.get("expires_in"),
        refresh_token=session.get("refresh_token"),
    ))
    return True


def _event_to_dict(event) -> dict:
    return {
        "provider": event.provider,
        "id": event.id,
        "calendarId": event.calendar_id,
        "title": event.title,
        "start": event.start.isoformat(),
        "end": event.end.isoformat(),
        "allDay": event.all_day,
        "recurringEventId": event.recurring_event_id,
        "color": event.color,
        "location": event.location,
        "description": event.description,
        "attendees": [
            {"email": a.email, "status": a.status, "organizer": a.organizer}
            for a in (event.attendees or [])
        ],
        "custom": event.custom,
        "conference": event.conference,
        "availability": event.availability,
        "privacy": event.privacy,
        "status": event.status,
        "link": event.link,
    }


def _err(message: str, status: int = 500, **extra):
    return jsonify({"ok": False, "error": message, **extra}), status


# ---------------------------------------------------------------------------
# Single dispatch route — identical pattern to PHP minimal app
# ---------------------------------------------------------------------------

@app.route("/callback")
def callback_redirect():
    """Accept /callback?code=... and forward to /?action=callback&code=..."""
    params = dict(request.args)
    params["action"] = "callback"
    from urllib.parse import urlencode
    return redirect("/?" + urlencode(params))


@app.route("/", methods=["GET", "POST"])
def index():
    # Accept config overrides from query string (same as PHP)
    for key in ("client_id", "client_secret", "user_id", "scope", "providers"):
        val = request.args.get(key, "")
        if val:
            session[f"cfg_{key}"] = val

    code = request.args.get("code")
    action = request.args.get("action") or ("callback" if code else "auth-url")

    # ---- config (env defaults for UI) ------------------------------------
    if action == "config":
        return jsonify({
            "clientId": _env("MOBISCROLL_CLIENT_ID"),
            "clientSecret": _env("MOBISCROLL_CLIENT_SECRET"),
            "userId": _env("MOBISCROLL_USER_ID", ""),
            "scope": _env("MOBISCROLL_SCOPE", "read-write"),
            "providers": _env("MOBISCROLL_PROVIDER", ""),
        })

    # ---- HTML pages ------------------------------------------------------
    if action == "ui":
        return render_template("ui.html")
    if action == "calendars-page":
        return render_template("calendars.html")
    if action == "events-page":
        return render_template("events.html")
    if action == "event-edit-page":
        return render_template("event_edit.html")

    # ---- build client (may raise if no credentials configured) ----------
    try:
        client = _make_client()
    except ValueError as exc:
        return _err(str(exc), 400, hint="Set MOBISCROLL_CLIENT_ID and MOBISCROLL_CLIENT_SECRET in .env")

    # ---- OAuth callback --------------------------------------------------
    if action == "callback":
        error = request.args.get("error")
        if error:
            return _err(f"OAuth error: {error}", 400)
        if not code:
            return _err("Missing ?code", 400)
        try:
            token = client.auth.get_token(code)
            session["access_token"] = token.access_token
            session["token_type"] = token.token_type
            session["expires_in"] = token.expires_in
            session["refresh_token"] = token.refresh_token
            if request.args.get("response") == "json":
                return jsonify({"ok": True, "action": "callback",
                                "token": {"access_token": token.access_token[:20] + "...",
                                          "expires_in": token.expires_in}})
            return redirect("/?action=ui&callback=success")
        except MobiscrollConnectError as exc:
            return _err(str(exc), 400, hint="Start from /?action=auth-url, complete OAuth, use the fresh callback URL.")

    # ---- auth-url --------------------------------------------------------
    if action == "auth-url":
        user_id = session.get("cfg_user_id") or _env("MOBISCROLL_USER_ID", "test-user-123")
        scope = session.get("cfg_scope") or _env("MOBISCROLL_SCOPE", "read-write")
        providers = session.get("cfg_providers") or _env("MOBISCROLL_PROVIDER", "google")
        redirect_uri = _env("MOBISCROLL_REDIRECT_URI", "http://localhost:8000/?action=callback")

        auth_url = client.auth.generate_auth_url(
            user_id=user_id, scope=scope, providers=providers
        )
        return jsonify({
            "ok": True,
            "action": "auth-url",
            "authUrl": auth_url,
            "redirectUri": redirect_uri,
            "next": f"Open authUrl in browser. After OAuth you'll be redirected to {redirect_uri}?code=...",
        })

    # ---- session ---------------------------------------------------------
    if action == "session":
        return jsonify({
            "ok": True,
            "action": "session",
            "stored": {
                "has_access_token": bool(session.get("access_token")),
                "token_type": session.get("token_type"),
                "expires_in": session.get("expires_in"),
            },
        })

    if action == "clear-session":
        session.clear()
        return jsonify({"ok": True, "action": "clear-session", "message": "Session cleared."})

    # ---- authenticated actions — require stored token --------------------
    AUTHENTICATED_ACTIONS = {
        "calendars", "events", "connection-status",
        "create-event", "update-event", "delete-event", "disconnect",
    }
    if action in AUTHENTICATED_ACTIONS:
        if not _restore_credentials(client):
            return _err("No stored token. Complete OAuth flow first.",
                        401, next="Visit /?action=auth-url to start OAuth")

    try:
        # ---- calendars ---------------------------------------------------
        if action == "calendars":
            calendars = client.calendars.list()
            return jsonify([
                {"provider": c.provider, "id": c.id, "title": c.title,
                 "timeZone": c.time_zone, "color": c.color, "description": c.description}
                for c in calendars
            ])

        # ---- connection-status -------------------------------------------
        if action == "connection-status":
            status = client.auth.get_connection_status()
            return jsonify({
                "connections": {
                    provider: [{"id": a.id, "display": a.display} for a in accounts]
                    for provider, accounts in status.connections.items()
                },
                "limitReached": status.limit_reached,
                "limit": status.limit,
            })

        # ---- events ------------------------------------------------------
        if action == "events":
            args = request.args
            kwargs: dict = {}
            if args.get("start"):
                kwargs["start"] = args["start"]
            if args.get("end"):
                kwargs["end"] = args["end"]
            if args.get("pageSize"):
                kwargs["page_size"] = int(args["pageSize"])
            if args.get("nextPageToken"):
                kwargs["next_page_token"] = args["nextPageToken"]
            if "singleEvents" in args:
                kwargs["single_events"] = args["singleEvents"].lower() in ("1", "true", "yes", "on")
            if args.get("calendars"):
                provider = args.get("provider", "google")
                ids = [c.strip() for c in args["calendars"].split(",") if c.strip()]
                if ids:
                    kwargs["calendar_ids"] = {provider: ids}

            result = client.events.list(**kwargs)
            return jsonify({
                "events": [_event_to_dict(e) for e in result.events],
                "pageSize": result.page_size,
                "nextPageToken": result.next_page_token,
            })

        # ---- create-event ------------------------------------------------
        if action == "create-event":
            body = request.get_json(force=True) or {}
            event = client.events.create(body)
            return jsonify(_event_to_dict(event))

        # ---- update-event ------------------------------------------------
        if action == "update-event":
            body = request.get_json(force=True) or {}
            event = client.events.update(body)
            return jsonify(_event_to_dict(event))

        # ---- delete-event ------------------------------------------------
        if action == "delete-event":
            body = request.get_json(force=True) or {}
            client.events.delete(body)
            return jsonify({"success": True})

        # ---- disconnect --------------------------------------------------
        if action == "disconnect":
            body = request.get_json(force=True) or {}
            result = client.auth.disconnect(
                body.get("provider", "google"),
                account=body.get("account") or None,
            )
            return jsonify({"success": result.success, "message": result.message})

    except MobiscrollConnectError as exc:
        return _err(str(exc), 500, action=action, code=exc.code)
    except Exception as exc:
        return _err(str(exc), 500, action=action)

    # ---- unknown action --------------------------------------------------
    return _err("Unknown action", 400, supportedActions=[
        "config", "ui", "calendars-page", "events-page", "event-edit-page",
        "auth-url", "callback", "calendars", "events", "connection-status",
        "create-event", "update-event", "delete-event", "disconnect",
        "session", "clear-session",
    ])


if __name__ == "__main__":
    app.run(port=8000, debug=True)
