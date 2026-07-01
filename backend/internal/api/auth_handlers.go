package api

import (
	"context"
	"net/http"
	"strings"
	"time"

	"nicetv/backend/internal/auth"
	"nicetv/backend/internal/models"
)

type registerRequest struct {
	Username   string  `json:"username"`
	Email      *string `json:"email"`
	Password   string  `json:"password"`
	DeviceName string  `json:"deviceName"`
}

type loginRequest struct {
	Login      string `json:"login"`
	Password   string `json:"password"`
	DeviceName string `json:"deviceName"`
}

type refreshRequest struct {
	RefreshToken string `json:"refreshToken"`
	DeviceName   string `json:"deviceName"`
}

type logoutRequest struct {
	RefreshToken string `json:"refreshToken"`
}

type updateMeRequest struct {
	Username  *string `json:"username"`
	Email     *string `json:"email"`
	AvatarURL *string `json:"avatarUrl"`
	Bio       *string `json:"bio"`
}

type authResponse struct {
	AccessToken           string      `json:"accessToken"`
	AccessTokenExpiresAt  time.Time   `json:"accessTokenExpiresAt"`
	RefreshToken          string      `json:"refreshToken"`
	RefreshTokenExpiresAt time.Time   `json:"refreshTokenExpiresAt"`
	User                  models.User `json:"user"`
}

func (s *Server) handleRegister(w http.ResponseWriter, r *http.Request) {
	var req registerRequest
	if !readJSON(w, r, &req) {
		return
	}
	username, err := normalizeUsername(req.Username)
	if err != nil {
		writeError(w, http.StatusBadRequest, "bad_request", err.Error())
		return
	}
	email, err := normalizeEmail(req.Email)
	if err != nil {
		writeError(w, http.StatusBadRequest, "bad_request", err.Error())
		return
	}
	if err := normalizePassword(req.Password); err != nil {
		writeError(w, http.StatusBadRequest, "bad_request", err.Error())
		return
	}
	passwordHash, err := auth.HashPassword(req.Password)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "internal_error", "internal server error")
		return
	}
	user, err := s.store.CreateUser(r.Context(), username, email, passwordHash)
	if err != nil {
		handleStoreError(w, err)
		return
	}
	resp, err := s.issueSession(r.Context(), user, req.DeviceName)
	if err != nil {
		handleStoreError(w, err)
		return
	}
	writeJSON(w, http.StatusCreated, resp)
}

func (s *Server) handleLogin(w http.ResponseWriter, r *http.Request) {
	var req loginRequest
	if !readJSON(w, r, &req) {
		return
	}
	login, err := normalizeLogin(req.Login)
	if err != nil {
		writeError(w, http.StatusBadRequest, "bad_request", err.Error())
		return
	}
	userWithPassword, err := s.store.GetUserByLogin(r.Context(), login)
	if err != nil || !auth.CheckPassword(userWithPassword.PasswordHash, req.Password) {
		writeError(w, http.StatusUnauthorized, "invalid_credentials", "invalid username/email or password")
		return
	}
	if isUserBanned(userWithPassword.User) {
		writeError(w, http.StatusForbidden, "account_banned", "account is banned")
		return
	}
	resp, err := s.issueSession(r.Context(), userWithPassword.User, req.DeviceName)
	if err != nil {
		handleStoreError(w, err)
		return
	}
	writeJSON(w, http.StatusOK, resp)
}

func (s *Server) handleRefresh(w http.ResponseWriter, r *http.Request) {
	var req refreshRequest
	if !readJSON(w, r, &req) {
		return
	}
	req.RefreshToken = strings.TrimSpace(req.RefreshToken)
	if req.RefreshToken == "" {
		writeError(w, http.StatusBadRequest, "bad_request", "refreshToken is required")
		return
	}
	tokenHash := auth.HashRefreshToken(req.RefreshToken)
	refreshToken, err := s.store.FindRefreshToken(r.Context(), tokenHash)
	if err != nil {
		handleStoreError(w, err)
		return
	}
	if err := s.store.RevokeRefreshToken(r.Context(), tokenHash); err != nil {
		handleStoreError(w, err)
		return
	}
	user, err := s.store.GetUserByID(r.Context(), refreshToken.UserID)
	if err != nil {
		handleStoreError(w, err)
		return
	}
	if isUserBanned(user) {
		writeError(w, http.StatusForbidden, "account_banned", "account is banned")
		return
	}
	resp, err := s.issueSession(r.Context(), user, req.DeviceName)
	if err != nil {
		handleStoreError(w, err)
		return
	}
	writeJSON(w, http.StatusOK, resp)
}

func (s *Server) handleLogout(w http.ResponseWriter, r *http.Request) {
	var req logoutRequest
	if !readJSON(w, r, &req) {
		return
	}
	req.RefreshToken = strings.TrimSpace(req.RefreshToken)
	if req.RefreshToken == "" {
		writeError(w, http.StatusBadRequest, "bad_request", "refreshToken is required")
		return
	}
	if err := s.store.RevokeRefreshToken(r.Context(), auth.HashRefreshToken(req.RefreshToken)); err != nil {
		handleStoreError(w, err)
		return
	}
	writeJSON(w, http.StatusNoContent, nil)
}

func (s *Server) handleMe(w http.ResponseWriter, r *http.Request) {
	user, err := s.store.GetUserByID(r.Context(), currentUserID(r))
	if err != nil {
		handleStoreError(w, err)
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"user": user})
}

func (s *Server) handleUpdateMe(w http.ResponseWriter, r *http.Request) {
	var req updateMeRequest
	if !readJSON(w, r, &req) {
		return
	}

	var username *string
	if req.Username != nil {
		value, err := normalizeUsername(*req.Username)
		if err != nil {
			writeError(w, http.StatusBadRequest, "bad_request", err.Error())
			return
		}
		username = &value
	}

	var email *string
	if req.Email != nil {
		value := strings.ToLower(strings.TrimSpace(*req.Email))
		if value != "" && (!strings.Contains(value, "@") || len(value) > 255) {
			writeError(w, http.StatusBadRequest, "bad_request", "invalid email")
			return
		}
		email = &value
	}

	avatarURL, err := normalizeOptionalText(req.AvatarURL, 2048)
	if err != nil {
		writeError(w, http.StatusBadRequest, "bad_request", "avatarUrl is too long")
		return
	}
	bio, err := normalizeOptionalText(req.Bio, 500)
	if err != nil {
		writeError(w, http.StatusBadRequest, "bad_request", "bio is too long")
		return
	}

	user, err := s.store.UpdateUser(r.Context(), currentUserID(r), username, email, avatarURL, bio)
	if err != nil {
		handleStoreError(w, err)
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"user": user})
}

func (s *Server) issueSession(ctx context.Context, user models.User, deviceName string) (authResponse, error) {
	accessToken, accessExpiresAt, err := auth.IssueAccessToken(user.ID, s.cfg.JWTSecret, s.cfg.AccessTokenTTL)
	if err != nil {
		return authResponse{}, err
	}
	refreshToken, refreshHash, err := auth.NewRefreshToken()
	if err != nil {
		return authResponse{}, err
	}
	refreshExpiresAt := time.Now().UTC().Add(s.cfg.RefreshTokenTTL)
	if _, err := s.store.CreateRefreshToken(ctx, user.ID, refreshHash, strings.TrimSpace(deviceName), refreshExpiresAt); err != nil {
		return authResponse{}, err
	}
	return authResponse{
		AccessToken:           accessToken,
		AccessTokenExpiresAt:  accessExpiresAt,
		RefreshToken:          refreshToken,
		RefreshTokenExpiresAt: refreshExpiresAt,
		User:                  user,
	}, nil
}
