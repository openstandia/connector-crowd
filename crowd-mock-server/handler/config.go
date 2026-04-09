package handler

import (
	"encoding/xml"
	"net/http"

	"github.com/openstandia/connector-crowd/crowd-mock-server/model"
)

type cookieConfigEntity struct {
	XMLName xml.Name `xml:"cookie-config"`
	Domain  string   `xml:"domain"`
	Secure  bool     `xml:"secure"`
	Name    string   `xml:"name"`
}

func HandleGetCookieConfig(w http.ResponseWriter, r *http.Request) {
	model.WriteXML(w, http.StatusOK, cookieConfigEntity{
		Domain: ".localhost",
		Secure: false,
		Name:   "crowd.token_key",
	})
}
