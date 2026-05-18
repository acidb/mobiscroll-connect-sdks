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
			Add("scope", p.Scope)
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

// Disconnect revokes the SDK's access to a provider account. Pass an empty
// DisconnectParams.Account to disconnect every account of the provider.
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

func isNotFound(err error) bool {
	var nf *NotFoundError
	return errors.As(err, &nf)
}
