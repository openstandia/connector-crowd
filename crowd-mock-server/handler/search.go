package handler

import (
	"context"
	"net/http"
	"strconv"

	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/openstandia/connector-crowd/crowd-mock-server/model"
)

type SearchHandler struct {
	Pool *pgxpool.Pool
}

func (h *SearchHandler) HandleSearch(w http.ResponseWriter, r *http.Request) {
	entityType := r.URL.Query().Get("entity-type")
	startIndex, _ := strconv.Atoi(r.URL.Query().Get("start-index"))
	maxResults, _ := strconv.Atoi(r.URL.Query().Get("max-results"))
	if maxResults <= 0 {
		maxResults = 50
	}

	ctx := r.Context()

	switch entityType {
	case "user":
		h.searchUsers(ctx, w, r, startIndex, maxResults)
	case "group":
		h.searchGroups(ctx, w, r, startIndex, maxResults)
	default:
		model.WriteError(w, http.StatusBadRequest, "INVALID_SEARCH", "entity-type must be 'user' or 'group'")
	}
}

func (h *SearchHandler) searchUsers(ctx context.Context, w http.ResponseWriter, r *http.Request, startIndex, maxResults int) {
	expand := r.URL.Query().Get("expand")
	includeAttrs := expand == "user,attributes"

	rows, err := h.Pool.Query(ctx,
		`SELECT id, name, key, first_name, last_name, display_name, email, active, created_date, updated_date
		 FROM users ORDER BY name OFFSET $1 LIMIT $2`,
		startIndex, maxResults,
	)
	if err != nil {
		model.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}
	defer rows.Close()

	users := []model.UserEntity{}
	for rows.Next() {
		var u model.UserEntity
		var id int64
		if err := rows.Scan(&id, &u.Name, &u.Key, &u.FirstName, &u.LastName,
			&u.DisplayName, &u.Email, &u.Active, &u.CreatedDate, &u.UpdatedDate); err != nil {
			model.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
			return
		}

		if includeAttrs {
			attrs, err := loadUserAttributes(ctx, h.Pool, id)
			if err != nil {
				model.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
				return
			}
			u.Attributes = attrs
		}

		users = append(users, u)
	}

	model.WriteXML(w, http.StatusOK, model.UserEntityList{Users: users})
}

func (h *SearchHandler) searchGroups(ctx context.Context, w http.ResponseWriter, r *http.Request, startIndex, maxResults int) {
	expand := r.URL.Query().Get("expand")
	includeAttrs := expand == "group,attributes"

	rows, err := h.Pool.Query(ctx,
		`SELECT id, name, description, active, type FROM groups ORDER BY name OFFSET $1 LIMIT $2`,
		startIndex, maxResults,
	)
	if err != nil {
		model.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}
	defer rows.Close()

	groups := []model.GroupEntity{}
	for rows.Next() {
		var g model.GroupEntity
		var id int64
		if err := rows.Scan(&id, &g.Name, &g.Description, &g.Active, &g.Type); err != nil {
			model.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
			return
		}

		if includeAttrs {
			attrs, err := loadGroupAttributes(ctx, h.Pool, id)
			if err != nil {
				model.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
				return
			}
			g.Attributes = attrs
		}

		groups = append(groups, g)
	}

	model.WriteXML(w, http.StatusOK, model.GroupEntityList{Groups: groups})
}
