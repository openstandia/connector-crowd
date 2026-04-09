package handler

import (
	"encoding/base64"
	"net/http"
	"strings"

	"github.com/openstandia/connector-crowd/crowd-mock-server/model"
)

func BasicAuth(expectedUser, expectedPass string) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			auth := r.Header.Get("Authorization")
			if auth == "" {
				model.WriteError(w, http.StatusUnauthorized, "UNAUTHORIZED", "Authentication required")
				return
			}

			if !strings.HasPrefix(auth, "Basic ") {
				model.WriteError(w, http.StatusUnauthorized, "UNAUTHORIZED", "Basic authentication required")
				return
			}

			decoded, err := base64.StdEncoding.DecodeString(auth[6:])
			if err != nil {
				model.WriteError(w, http.StatusUnauthorized, "UNAUTHORIZED", "Invalid credentials")
				return
			}

			parts := strings.SplitN(string(decoded), ":", 2)
			if len(parts) != 2 || parts[0] != expectedUser || parts[1] != expectedPass {
				model.WriteError(w, http.StatusUnauthorized, "UNAUTHORIZED", "Invalid credentials")
				return
			}

			next.ServeHTTP(w, r)
		})
	}
}
