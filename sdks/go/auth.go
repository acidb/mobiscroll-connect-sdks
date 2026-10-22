package mobiscroll

import (
	"context"
	"errors"
	"net/http"
	"net/url"

	"github.com/acidb/mobiscroll-connect-sdks/sdks/go/internal/querybuilder"
)

// authService groups the OAuth + connection-management endpoints.
type authService struct{ api *apiClient }

// GenerateAuthURL builds the consent URL to redirect the user to. UserID is
// the only required field; the rest are optional.
func (a *authService) GenerateAuthURL(p *AuthURLParams) string {
	qs := querybuilder.New().
		Add("response_type", "code").
		Add("client_id", a.api.cfg.clientID).
		Add("redirect_uri", a.api.cfg.redirectURI)
	if p != nil {
		qs.Add("user_id", p.UserID).
			Add("state", p.State).
			Add("scope", p.Scope).
			Add("lng", p.Lng)
		for _, prov := range p.Providers {
			qs.Add("providers", string(prov))
		}
	}
	return a.api.cfg.baseURL + "/oauth/authorize?" + qs.Encode()
}

// GetToken exchanges an authorization code for a token pair, then stores it
// on the client.
func (a *authService) GetToken(ctx context.Context, code string) (*TokenResponse, error) {
	form := url.Values{}
	form.Set("grant_type", "authorization_code")
	form.Set("code", code)
	form.Set("redirect_uri", a.api.cfg.redirectURI)

	tok, err := a.api.postForm(ctx, "/oauth/token", form)
	if err != nil {
		return nil, err
	}
	a.api.setCredentials(tok)
	return tok, nil
}

// SetCredentials is an alias for Client.SetCredentials, included on the auth
// service for symmetry with the other SDKs.
func (a *authService) SetCredentials(tokens *TokenResponse) { a.api.setCredentials(tokens) }

// GetConnectionStatus returns the connected accounts grouped by provider. It
// first tries /oauth/connection-status and falls back to /connection-status
// to support older server deployments.
func (a *authService) GetConnectionStatus(ctx context.Context) (*ConnectionStatus, error) {
	out := &ConnectionStatus{}
	if err := a.api.do(ctx, http.MethodGet, "/oauth/connection-status", "", nil, out); err != nil {
		if isNotFound(err) {
			out = &ConnectionStatus{}
			if err := a.api.do(ctx, http.MethodGet, "/connection-status", "", nil, out); err != nil {
				return nil, err
			}
			return out, nil
		}
		return nil, err
	}
	return out, nil
}

// Disconnect removes a provider account from the user: it stops the account's
// webhook subscriptions and deletes its stored provider credentials. Set
// DisconnectParams.Account to the ID of an account from GetConnectionStatus
// (Connections[provider][].ID), or leave it empty to disconnect every account
// of the provider. It does not revoke the user's Connect access or refresh
// tokens (see Revoke), nor the grant at Google or Microsoft.
func (a *authService) Disconnect(ctx context.Context, p *DisconnectParams) (*DisconnectResponse, error) {
	if p == nil {
		return nil, errors.New("mobiscroll: DisconnectParams must not be nil")
	}
	qs := querybuilder.New().
		Add("provider", string(p.Provider)).
		Add("account", p.Account).
		Encode()

	out := &DisconnectResponse{}
	if err := a.api.do(ctx, http.MethodPost, "/oauth/disconnect", qs, map[string]any{}, out); err != nil {
		if isNotFound(err) {
			out = &DisconnectResponse{}
			if err := a.api.do(ctx, http.MethodPost, "/disconnect", qs, map[string]any{}, out); err != nil {
				return nil, err
			}
			return out, nil
		}
		return nil, err
	}
	return out, nil
}

// Revoke revokes the user's Connect access and refresh tokens.
//
// It revokes the whole grant: every access and refresh token issued for this
// user. It does not disconnect calendar accounts or stop webhook
// subscriptions. To remove a user entirely, call Disconnect for each provider
// first (it needs a valid access token), then Revoke.
//
// token is an access or refresh token. When it is empty, the stored refresh
// token is revoked, else the stored access token, and the stored credentials
// are cleared after a successful call. With no token and no stored
// credentials it returns a *ValidationError without making a request. The
// server reports success for any token, including an unknown or already
// revoked one.
//
//	_, err := client.Auth().Disconnect(ctx, &mobiscroll.DisconnectParams{Provider: mobiscroll.ProviderGoogle})
//	_, err = client.Auth().Revoke(ctx, "")
//
//	// Or revoke a token you stored yourself:
//	_, err = client.Auth().Revoke(ctx, storedRefreshToken)
func (a *authService) Revoke(ctx context.Context, token string) (*RevokeResponse, error) {
	useStored := token == ""
	if useStored {
		if creds := a.api.getCredentials(); creds != nil {
			token = creds.RefreshToken
			if token == "" {
				token = creds.AccessToken
			}
		}
	}
	if token == "" {
		return nil, &ValidationError{Message: "mobiscroll: no token to revoke: pass one or set credentials first"}
	}

	out := &RevokeResponse{}
	if err := a.api.doNoRefresh(ctx, http.MethodPost, "/oauth/revoke", "", map[string]string{"token": token}, out); err != nil {
		return nil, err
	}
	if useStored {
		a.api.clearCredentials()
	}
	return out, nil
}

func isNotFound(err error) bool {
	var nf *NotFoundError
	return errors.As(err, &nf)
}
