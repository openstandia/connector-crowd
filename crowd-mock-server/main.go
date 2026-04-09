package main

import (
	"context"
	"fmt"
	"log"
	"net/http"

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"

	"github.com/openstandia/connector-crowd/crowd-mock-server/config"
	"github.com/openstandia/connector-crowd/crowd-mock-server/db"
	"github.com/openstandia/connector-crowd/crowd-mock-server/handler"
)

func main() {
	cfg := config.Load()
	ctx := context.Background()

	pool, err := db.NewPool(ctx, cfg.DBURL())
	if err != nil {
		log.Fatalf("Failed to connect to database: %v", err)
	}
	defer pool.Close()

	if err := db.RunMigrations(ctx, pool); err != nil {
		log.Fatalf("Failed to run migrations: %v", err)
	}
	log.Println("Database migrations completed")

	userHandler := &handler.UserHandler{Pool: pool}
	groupHandler := &handler.GroupHandler{Pool: pool}
	searchHandler := &handler.SearchHandler{Pool: pool}

	r := chi.NewRouter()
	r.Use(middleware.Logger)
	r.Use(middleware.Recoverer)

	// Test reset endpoint (no auth required)
	r.Post(cfg.ContextPath+"/test/reset", func(w http.ResponseWriter, r *http.Request) {
		if err := db.TruncateAll(r.Context(), pool); err != nil {
			http.Error(w, err.Error(), http.StatusInternalServerError)
			return
		}
		w.WriteHeader(http.StatusOK)
	})

	r.Route(cfg.ContextPath+"/rest/usermanagement/1", func(r chi.Router) {
		r.Use(handler.BasicAuth(cfg.AuthUser, cfg.AuthPassword))

		// User endpoints
		r.Post("/user", userHandler.HandleCreateUser)
		r.Get("/user", userHandler.HandleGetUser)
		r.Put("/user", userHandler.HandleUpdateUser)
		r.Delete("/user", userHandler.HandleDeleteUser)

		r.Put("/user/password", userHandler.HandleUpdatePassword)
		r.Post("/user/rename", userHandler.HandleRenameUser)
		r.Post("/user/attribute", userHandler.HandleStoreUserAttributes)

		// User-group membership (Crowd uses /group/user/direct and /user/group/direct)
		r.Post("/group/user/direct", userHandler.HandleAddUserToGroup)
		r.Delete("/group/user/direct", userHandler.HandleRemoveUserFromGroup)
		r.Get("/user/group/direct", userHandler.HandleGetGroupsForUser)

		// Group endpoints
		r.Post("/group", groupHandler.HandleCreateGroup)
		r.Get("/group", groupHandler.HandleGetGroup)
		r.Put("/group", groupHandler.HandleUpdateGroup)
		r.Delete("/group", groupHandler.HandleDeleteGroup)

		r.Post("/group/attribute", groupHandler.HandleStoreGroupAttributes)
		r.Post("/group/child-group/direct", groupHandler.HandleAddChildGroup)
		r.Delete("/group/child-group/direct", groupHandler.HandleRemoveChildGroup)
		r.Get("/group/parent-group/direct", groupHandler.HandleGetParentGroups)

		// Search endpoint (Crowd client uses POST for search)
		r.Post("/search", searchHandler.HandleSearch)

		// Config endpoint
		r.Get("/config/cookie", handler.HandleGetCookieConfig)
	})

	addr := fmt.Sprintf(":%s", cfg.Port)
	log.Printf("Crowd mock server starting on %s", addr)
	if err := http.ListenAndServe(addr, r); err != nil {
		log.Fatalf("Server failed: %v", err)
	}
}
