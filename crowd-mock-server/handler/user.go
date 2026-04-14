package handler

import (
	"context"
	"encoding/xml"
	"fmt"
	"net/http"
	"strconv"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/openstandia/connector-crowd/crowd-mock-server/model"
)

type UserHandler struct {
	Pool *pgxpool.Pool
}

func (h *UserHandler) HandleCreateUser(w http.ResponseWriter, r *http.Request) {
	var user model.UserEntity
	if err := xml.NewDecoder(r.Body).Decode(&user); err != nil {
		model.WriteError(w, http.StatusBadRequest, "INVALID_USER", "Invalid request body")
		return
	}

	now := time.Now().UnixMilli()
	user.Key = uuid.New().String()
	user.CreatedDate = now
	user.UpdatedDate = now

	password := ""
	if user.Password != nil {
		password = user.Password.Value
	}

	ctx := r.Context()
	var id int64
	err := h.Pool.QueryRow(ctx,
		`INSERT INTO users (name, key, first_name, last_name, display_name, email, active, password, created_date, updated_date)
		 VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
		 RETURNING id`,
		user.Name, user.Key, user.FirstName, user.LastName, user.DisplayName,
		user.Email, user.Active, password, user.CreatedDate, user.UpdatedDate,
	).Scan(&id)
	if err != nil {
		if isDuplicateKeyError(err) {
			model.WriteError(w, http.StatusBadRequest, "INVALID_USER",
				fmt.Sprintf("User <%s> already exists", user.Name))
			return
		}
		model.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}

	// Store attributes if provided
	if user.Attributes != nil {
		if err := storeUserAttributes(ctx, h.Pool, id, user.Attributes.Attributes); err != nil {
			model.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
			return
		}
	}

	// Reload attributes so the response includes them (Crowd client checks attribute keys)
	attrs, err := loadUserAttributes(ctx, h.Pool, id)
	if err != nil {
		model.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}
	user.Attributes = attrs

	user.Password = nil
	model.WriteXML(w, http.StatusCreated, user)
}

func (h *UserHandler) HandleGetUser(w http.ResponseWriter, r *http.Request) {
	ctx := r.Context()
	key := r.URL.Query().Get("key")
	username := r.URL.Query().Get("username")

	var user model.UserEntity
	var id int64
	var err error

	if key != "" {
		err = h.Pool.QueryRow(ctx,
			`SELECT id, name, key, first_name, last_name, display_name, email, active, created_date, updated_date
			 FROM users WHERE key = $1`, key,
		).Scan(&id, &user.Name, &user.Key, &user.FirstName, &user.LastName,
			&user.DisplayName, &user.Email, &user.Active, &user.CreatedDate, &user.UpdatedDate)
	} else if username != "" {
		err = h.Pool.QueryRow(ctx,
			`SELECT id, name, key, first_name, last_name, display_name, email, active, created_date, updated_date
			 FROM users WHERE LOWER(name) = LOWER($1)`, username,
		).Scan(&id, &user.Name, &user.Key, &user.FirstName, &user.LastName,
			&user.DisplayName, &user.Email, &user.Active, &user.CreatedDate, &user.UpdatedDate)
	} else {
		model.WriteError(w, http.StatusBadRequest, "INVALID_USER", "key or username parameter required")
		return
	}

	if err == pgx.ErrNoRows {
		model.WriteError(w, http.StatusNotFound, "USER_NOT_FOUND", "User not found")
		return
	}
	if err != nil {
		model.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}

	expand := r.URL.Query().Get("expand")
	if expand == "attributes" {
		attrs, err := loadUserAttributes(ctx, h.Pool, id)
		if err != nil {
			model.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
			return
		}
		user.Attributes = attrs
	}

	model.WriteXML(w, http.StatusOK, user)
}

func (h *UserHandler) HandleUpdateUser(w http.ResponseWriter, r *http.Request) {
	username := r.URL.Query().Get("username")
	if username == "" {
		model.WriteError(w, http.StatusBadRequest, "INVALID_USER", "username parameter required")
		return
	}

	var user model.UserEntity
	if err := xml.NewDecoder(r.Body).Decode(&user); err != nil {
		model.WriteError(w, http.StatusBadRequest, "INVALID_USER", "Invalid request body")
		return
	}

	now := time.Now().UnixMilli()
	result, err := h.Pool.Exec(r.Context(),
		`UPDATE users SET first_name=$1, last_name=$2, display_name=$3, email=$4, active=$5, updated_date=$6
		 WHERE LOWER(name) = LOWER($7)`,
		user.FirstName, user.LastName, user.DisplayName, user.Email, user.Active, now, username,
	)
	if err != nil {
		model.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}
	if result.RowsAffected() == 0 {
		model.WriteError(w, http.StatusNotFound, "USER_NOT_FOUND",
			fmt.Sprintf("User <%s> does not exist", username))
		return
	}
	model.WriteNoContent(w)
}

func (h *UserHandler) HandleDeleteUser(w http.ResponseWriter, r *http.Request) {
	username := r.URL.Query().Get("username")
	if username == "" {
		model.WriteError(w, http.StatusBadRequest, "INVALID_USER", "username parameter required")
		return
	}

	result, err := h.Pool.Exec(r.Context(), `DELETE FROM users WHERE LOWER(name) = LOWER($1)`, username)
	if err != nil {
		model.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}
	if result.RowsAffected() == 0 {
		model.WriteError(w, http.StatusNotFound, "USER_NOT_FOUND",
			fmt.Sprintf("User <%s> does not exist", username))
		return
	}
	model.WriteNoContent(w)
}

func (h *UserHandler) HandleUpdatePassword(w http.ResponseWriter, r *http.Request) {
	username := r.URL.Query().Get("username")
	if username == "" {
		model.WriteError(w, http.StatusBadRequest, "INVALID_USER", "username parameter required")
		return
	}

	var cred model.PasswordCred
	if err := xml.NewDecoder(r.Body).Decode(&cred); err != nil {
		model.WriteError(w, http.StatusBadRequest, "INVALID_USER", "Invalid request body")
		return
	}

	result, err := h.Pool.Exec(r.Context(),
		`UPDATE users SET password=$1, updated_date=$2 WHERE LOWER(name) = LOWER($3)`,
		cred.Value, time.Now().UnixMilli(), username,
	)
	if err != nil {
		model.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}
	if result.RowsAffected() == 0 {
		model.WriteError(w, http.StatusNotFound, "USER_NOT_FOUND",
			fmt.Sprintf("User <%s> does not exist", username))
		return
	}
	model.WriteNoContent(w)
}

func (h *UserHandler) HandleRenameUser(w http.ResponseWriter, r *http.Request) {
	username := r.URL.Query().Get("username")
	if username == "" {
		model.WriteError(w, http.StatusBadRequest, "INVALID_USER", "username parameter required")
		return
	}

	// Body is a RenameEntity: <new-name>newUsername</new-name>
	var renameReq model.RenameRequest
	if err := xml.NewDecoder(r.Body).Decode(&renameReq); err != nil {
		model.WriteError(w, http.StatusBadRequest, "INVALID_USER", "Invalid request body")
		return
	}
	newName := renameReq.NewName

	now := time.Now().UnixMilli()
	result, err := h.Pool.Exec(r.Context(),
		`UPDATE users SET name=$1, updated_date=$2 WHERE LOWER(name) = LOWER($3)`,
		newName, now, username,
	)
	if err != nil {
		if isDuplicateKeyError(err) {
			model.WriteError(w, http.StatusBadRequest, "INVALID_USER",
				fmt.Sprintf("User <%s> already exists", newName))
			return
		}
		model.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}
	if result.RowsAffected() == 0 {
		model.WriteError(w, http.StatusNotFound, "USER_NOT_FOUND",
			fmt.Sprintf("User <%s> does not exist", username))
		return
	}

	// Return the renamed user
	var user model.UserEntity
	err = h.Pool.QueryRow(r.Context(),
		`SELECT name, key, first_name, last_name, display_name, email, active, created_date, updated_date
		 FROM users WHERE LOWER(name) = LOWER($1)`, newName,
	).Scan(&user.Name, &user.Key, &user.FirstName, &user.LastName,
		&user.DisplayName, &user.Email, &user.Active, &user.CreatedDate, &user.UpdatedDate)
	if err != nil {
		model.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}
	model.WriteXML(w, http.StatusOK, user)
}

func (h *UserHandler) HandleStoreUserAttributes(w http.ResponseWriter, r *http.Request) {
	username := r.URL.Query().Get("username")
	if username == "" {
		model.WriteError(w, http.StatusBadRequest, "INVALID_USER", "username parameter required")
		return
	}

	var body model.AttributeList
	if err := xml.NewDecoder(r.Body).Decode(&body); err != nil {
		model.WriteError(w, http.StatusBadRequest, "INVALID_USER", "Invalid request body")
		return
	}

	ctx := r.Context()
	var userID int64
	err := h.Pool.QueryRow(ctx, `SELECT id FROM users WHERE LOWER(name) = LOWER($1)`, username).Scan(&userID)
	if err == pgx.ErrNoRows {
		model.WriteError(w, http.StatusNotFound, "USER_NOT_FOUND",
			fmt.Sprintf("User <%s> does not exist", username))
		return
	}
	if err != nil {
		model.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}

	if err := storeUserAttributes(ctx, h.Pool, userID, body.Attributes); err != nil {
		model.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}

	model.WriteNoContent(w)
}

// POST /group/user/direct?groupname={groupname} (body: UserEntity with name)
func (h *UserHandler) HandleAddUserToGroup(w http.ResponseWriter, r *http.Request) {
	groupname := r.URL.Query().Get("groupname")
	if groupname == "" {
		model.WriteError(w, http.StatusBadRequest, "INVALID_GROUP", "groupname parameter required")
		return
	}

	var userRef model.UserEntity
	if err := xml.NewDecoder(r.Body).Decode(&userRef); err != nil {
		model.WriteError(w, http.StatusBadRequest, "INVALID_USER", "Invalid request body")
		return
	}

	ctx := r.Context()
	var userID, groupID int64
	err := h.Pool.QueryRow(ctx, `SELECT id FROM users WHERE LOWER(name) = LOWER($1)`, userRef.Name).Scan(&userID)
	if err == pgx.ErrNoRows {
		model.WriteError(w, http.StatusNotFound, "USER_NOT_FOUND",
			fmt.Sprintf("User <%s> does not exist", userRef.Name))
		return
	}
	if err != nil {
		model.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}

	err = h.Pool.QueryRow(ctx, `SELECT id FROM groups WHERE LOWER(name) = LOWER($1)`, groupname).Scan(&groupID)
	if err == pgx.ErrNoRows {
		model.WriteError(w, http.StatusNotFound, "GROUP_NOT_FOUND",
			fmt.Sprintf("Group <%s> does not exist", groupname))
		return
	}
	if err != nil {
		model.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}

	_, err = h.Pool.Exec(ctx,
		`INSERT INTO user_group_memberships (user_id, group_id) VALUES ($1, $2) ON CONFLICT DO NOTHING`,
		userID, groupID,
	)
	if err != nil {
		model.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}

	model.WriteCreated(w)
}

// DELETE /group/user/direct?groupname={groupname}&username={username}
func (h *UserHandler) HandleRemoveUserFromGroup(w http.ResponseWriter, r *http.Request) {
	groupname := r.URL.Query().Get("groupname")
	username := r.URL.Query().Get("username")
	if username == "" || groupname == "" {
		model.WriteError(w, http.StatusBadRequest, "INVALID_USER", "username and groupname parameters required")
		return
	}

	ctx := r.Context()
	_, err := h.Pool.Exec(ctx,
		`DELETE FROM user_group_memberships
		 WHERE user_id = (SELECT id FROM users WHERE LOWER(name) = LOWER($1))
		   AND group_id = (SELECT id FROM groups WHERE LOWER(name) = LOWER($2))`,
		username, groupname,
	)
	if err != nil {
		model.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}
	model.WriteNoContent(w)
}

func (h *UserHandler) HandleGetGroupsForUser(w http.ResponseWriter, r *http.Request) {
	username := r.URL.Query().Get("username")
	if username == "" {
		model.WriteError(w, http.StatusBadRequest, "INVALID_USER", "username parameter required")
		return
	}

	startIndex, _ := strconv.Atoi(r.URL.Query().Get("start-index"))
	maxResults, _ := strconv.Atoi(r.URL.Query().Get("max-results"))
	if maxResults <= 0 {
		maxResults = 50
	}

	ctx := r.Context()
	rows, err := h.Pool.Query(ctx,
		`SELECT g.name FROM groups g
		 JOIN user_group_memberships ugm ON g.id = ugm.group_id
		 JOIN users u ON u.id = ugm.user_id
		 WHERE LOWER(u.name) = LOWER($1)
		 ORDER BY g.name
		 OFFSET $2 LIMIT $3`,
		username, startIndex, maxResults,
	)
	if err != nil {
		model.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}
	defer rows.Close()

	groups := []model.GroupEntity{}
	for rows.Next() {
		var name string
		if err := rows.Scan(&name); err != nil {
			model.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
			return
		}
		groups = append(groups, model.GroupEntity{Name: name, Type: "GROUP", Active: true})
	}

	model.WriteXML(w, http.StatusOK, model.GroupEntityList{Groups: groups})
}

// Helper functions

func storeUserAttributes(ctx context.Context, pool *pgxpool.Pool, userID int64, attrs []model.AttributeEntity) error {
	for _, attr := range attrs {
		_, err := pool.Exec(ctx,
			`DELETE FROM user_attributes WHERE user_id=$1 AND attr_name=$2`,
			userID, attr.Name,
		)
		if err != nil {
			return err
		}
		for _, val := range attr.Values.Value {
			_, err := pool.Exec(ctx,
				`INSERT INTO user_attributes (user_id, attr_name, attr_value) VALUES ($1, $2, $3)`,
				userID, attr.Name, val,
			)
			if err != nil {
				return err
			}
		}
	}
	return nil
}

func loadUserAttributes(ctx context.Context, pool *pgxpool.Pool, userID int64) (*model.AttributeList, error) {
	rows, err := pool.Query(ctx,
		`SELECT attr_name, attr_value FROM user_attributes WHERE user_id=$1 ORDER BY attr_name, id`,
		userID,
	)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	attrMap := map[string][]string{}
	var orderedNames []string
	for rows.Next() {
		var name, value string
		if err := rows.Scan(&name, &value); err != nil {
			return nil, err
		}
		if _, exists := attrMap[name]; !exists {
			orderedNames = append(orderedNames, name)
		}
		attrMap[name] = append(attrMap[name], value)
	}

	attrs := make([]model.AttributeEntity, 0, len(orderedNames))
	for _, name := range orderedNames {
		attrs = append(attrs, model.AttributeEntity{
			Name:   name,
			Values: model.AttributeValues{Value: attrMap[name]},
		})
	}

	return &model.AttributeList{
		Attributes: attrs,
	}, nil
}

func isDuplicateKeyError(err error) bool {
	if err == nil {
		return false
	}
	s := err.Error()
	return contains(s, "duplicate key") || contains(s, "unique constraint")
}

func contains(s, substr string) bool {
	for i := 0; i <= len(s)-len(substr); i++ {
		if s[i:i+len(substr)] == substr {
			return true
		}
	}
	return false
}
