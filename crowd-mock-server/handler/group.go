package handler

import (
	"context"
	"encoding/xml"
	"fmt"
	"net/http"
	"strconv"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/openstandia/connector-crowd/crowd-mock-server/model"
)

type GroupHandler struct {
	Pool *pgxpool.Pool
}

func (h *GroupHandler) HandleCreateGroup(w http.ResponseWriter, r *http.Request) {
	var group model.GroupEntity
	if err := xml.NewDecoder(r.Body).Decode(&group); err != nil {
		model.WriteError(w, http.StatusBadRequest, "INVALID_GROUP", "Invalid request body")
		return
	}

	if group.Type == "" {
		group.Type = "GROUP"
	}

	now := time.Now().UnixMilli()
	ctx := r.Context()

	_, err := h.Pool.Exec(ctx,
		`INSERT INTO groups (name, description, active, type, created_date, updated_date)
		 VALUES ($1, $2, $3, $4, $5, $6)`,
		group.Name, group.Description, group.Active, group.Type, now, now,
	)
	if err != nil {
		if isDuplicateKeyError(err) {
			model.WriteError(w, http.StatusBadRequest, "INVALID_GROUP",
				fmt.Sprintf("Group <%s> already exists", group.Name))
			return
		}
		model.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}

	// Store attributes if provided
	if group.Attributes != nil {
		var groupID int64
		err := h.Pool.QueryRow(ctx, `SELECT id FROM groups WHERE LOWER(name) = LOWER($1)`, group.Name).Scan(&groupID)
		if err != nil {
			model.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
			return
		}
		if err := storeGroupAttributes(ctx, h.Pool, groupID, group.Attributes.Attributes); err != nil {
			model.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
			return
		}
	}

	group.Attributes = nil
	model.WriteXML(w, http.StatusCreated, group)
}

func (h *GroupHandler) HandleGetGroup(w http.ResponseWriter, r *http.Request) {
	groupname := r.URL.Query().Get("groupname")
	if groupname == "" {
		model.WriteError(w, http.StatusBadRequest, "INVALID_GROUP", "groupname parameter required")
		return
	}

	ctx := r.Context()
	var group model.GroupEntity
	var id int64
	err := h.Pool.QueryRow(ctx,
		`SELECT id, name, description, active, type FROM groups WHERE LOWER(name) = LOWER($1)`, groupname,
	).Scan(&id, &group.Name, &group.Description, &group.Active, &group.Type)

	if err == pgx.ErrNoRows {
		model.WriteError(w, http.StatusNotFound, "GROUP_NOT_FOUND",
			fmt.Sprintf("Group <%s> does not exist", groupname))
		return
	}
	if err != nil {
		model.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}

	expand := r.URL.Query().Get("expand")
	if expand == "attributes" {
		attrs, err := loadGroupAttributes(ctx, h.Pool, id)
		if err != nil {
			model.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
			return
		}
		group.Attributes = attrs
	}

	model.WriteXML(w, http.StatusOK, group)
}

func (h *GroupHandler) HandleUpdateGroup(w http.ResponseWriter, r *http.Request) {
	groupname := r.URL.Query().Get("groupname")
	if groupname == "" {
		model.WriteError(w, http.StatusBadRequest, "INVALID_GROUP", "groupname parameter required")
		return
	}

	var group model.GroupEntity
	if err := xml.NewDecoder(r.Body).Decode(&group); err != nil {
		model.WriteError(w, http.StatusBadRequest, "INVALID_GROUP", "Invalid request body")
		return
	}

	now := time.Now().UnixMilli()
	result, err := h.Pool.Exec(r.Context(),
		`UPDATE groups SET description=$1, active=$2, updated_date=$3 WHERE LOWER(name) = LOWER($4)`,
		group.Description, group.Active, now, groupname,
	)
	if err != nil {
		model.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}
	if result.RowsAffected() == 0 {
		model.WriteError(w, http.StatusNotFound, "GROUP_NOT_FOUND",
			fmt.Sprintf("Group <%s> does not exist", groupname))
		return
	}
	model.WriteNoContent(w)
}

func (h *GroupHandler) HandleDeleteGroup(w http.ResponseWriter, r *http.Request) {
	groupname := r.URL.Query().Get("groupname")
	if groupname == "" {
		model.WriteError(w, http.StatusBadRequest, "INVALID_GROUP", "groupname parameter required")
		return
	}

	result, err := h.Pool.Exec(r.Context(), `DELETE FROM groups WHERE LOWER(name) = LOWER($1)`, groupname)
	if err != nil {
		model.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}
	if result.RowsAffected() == 0 {
		model.WriteError(w, http.StatusNotFound, "GROUP_NOT_FOUND",
			fmt.Sprintf("Group <%s> does not exist", groupname))
		return
	}
	model.WriteNoContent(w)
}

func (h *GroupHandler) HandleStoreGroupAttributes(w http.ResponseWriter, r *http.Request) {
	groupname := r.URL.Query().Get("groupname")
	if groupname == "" {
		model.WriteError(w, http.StatusBadRequest, "INVALID_GROUP", "groupname parameter required")
		return
	}

	var body model.AttributeList
	if err := xml.NewDecoder(r.Body).Decode(&body); err != nil {
		model.WriteError(w, http.StatusBadRequest, "INVALID_GROUP", "Invalid request body")
		return
	}

	ctx := r.Context()
	var groupID int64
	err := h.Pool.QueryRow(ctx, `SELECT id FROM groups WHERE LOWER(name) = LOWER($1)`, groupname).Scan(&groupID)
	if err == pgx.ErrNoRows {
		model.WriteError(w, http.StatusNotFound, "GROUP_NOT_FOUND",
			fmt.Sprintf("Group <%s> does not exist", groupname))
		return
	}
	if err != nil {
		model.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}

	if err := storeGroupAttributes(ctx, h.Pool, groupID, body.Attributes); err != nil {
		model.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}

	model.WriteNoContent(w)
}

func (h *GroupHandler) HandleAddChildGroup(w http.ResponseWriter, r *http.Request) {
	parentName := r.URL.Query().Get("groupname")
	if parentName == "" {
		model.WriteError(w, http.StatusBadRequest, "INVALID_GROUP", "groupname parameter required")
		return
	}

	var ref model.GroupRef
	if err := xml.NewDecoder(r.Body).Decode(&ref); err != nil {
		model.WriteError(w, http.StatusBadRequest, "INVALID_GROUP", "Invalid request body")
		return
	}

	ctx := r.Context()
	var parentID, childID int64

	err := h.Pool.QueryRow(ctx, `SELECT id FROM groups WHERE LOWER(name) = LOWER($1)`, parentName).Scan(&parentID)
	if err == pgx.ErrNoRows {
		model.WriteError(w, http.StatusNotFound, "GROUP_NOT_FOUND",
			fmt.Sprintf("Group <%s> does not exist", parentName))
		return
	}
	if err != nil {
		model.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}

	err = h.Pool.QueryRow(ctx, `SELECT id FROM groups WHERE LOWER(name) = LOWER($1)`, ref.Name).Scan(&childID)
	if err == pgx.ErrNoRows {
		model.WriteError(w, http.StatusNotFound, "GROUP_NOT_FOUND",
			fmt.Sprintf("Group <%s> does not exist", ref.Name))
		return
	}
	if err != nil {
		model.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}

	_, err = h.Pool.Exec(ctx,
		`INSERT INTO group_group_memberships (parent_group_id, child_group_id) VALUES ($1, $2) ON CONFLICT DO NOTHING`,
		parentID, childID,
	)
	if err != nil {
		model.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}

	model.WriteCreated(w)
}

func (h *GroupHandler) HandleRemoveChildGroup(w http.ResponseWriter, r *http.Request) {
	parentName := r.URL.Query().Get("groupname")
	childName := r.URL.Query().Get("child-groupname")
	if parentName == "" || childName == "" {
		model.WriteError(w, http.StatusBadRequest, "INVALID_GROUP", "groupname and child-groupname parameters required")
		return
	}

	ctx := r.Context()
	_, err := h.Pool.Exec(ctx,
		`DELETE FROM group_group_memberships
		 WHERE parent_group_id = (SELECT id FROM groups WHERE LOWER(name) = LOWER($1))
		   AND child_group_id = (SELECT id FROM groups WHERE LOWER(name) = LOWER($2))`,
		parentName, childName,
	)
	if err != nil {
		model.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}
	model.WriteNoContent(w)
}

func (h *GroupHandler) HandleGetParentGroups(w http.ResponseWriter, r *http.Request) {
	groupname := r.URL.Query().Get("groupname")
	if groupname == "" {
		model.WriteError(w, http.StatusBadRequest, "INVALID_GROUP", "groupname parameter required")
		return
	}

	startIndex, _ := strconv.Atoi(r.URL.Query().Get("start-index"))
	maxResults, _ := strconv.Atoi(r.URL.Query().Get("max-results"))
	if maxResults <= 0 {
		maxResults = 50
	}

	ctx := r.Context()
	rows, err := h.Pool.Query(ctx,
		`SELECT pg.name FROM groups pg
		 JOIN group_group_memberships ggm ON pg.id = ggm.parent_group_id
		 JOIN groups cg ON cg.id = ggm.child_group_id
		 WHERE LOWER(cg.name) = LOWER($1)
		 ORDER BY pg.name
		 OFFSET $2 LIMIT $3`,
		groupname, startIndex, maxResults,
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

func storeGroupAttributes(ctx context.Context, pool *pgxpool.Pool, groupID int64, attrs []model.AttributeEntity) error {
	for _, attr := range attrs {
		_, err := pool.Exec(ctx,
			`DELETE FROM group_attributes WHERE group_id=$1 AND attr_name=$2`,
			groupID, attr.Name,
		)
		if err != nil {
			return err
		}
		for _, val := range attr.Values.Value {
			_, err := pool.Exec(ctx,
				`INSERT INTO group_attributes (group_id, attr_name, attr_value) VALUES ($1, $2, $3)`,
				groupID, attr.Name, val,
			)
			if err != nil {
				return err
			}
		}
	}
	return nil
}

func loadGroupAttributes(ctx context.Context, pool *pgxpool.Pool, groupID int64) (*model.AttributeList, error) {
	rows, err := pool.Query(ctx,
		`SELECT attr_name, attr_value FROM group_attributes WHERE group_id=$1 ORDER BY attr_name, id`,
		groupID,
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
