package model

import (
	"encoding/xml"
	"net/http"
)

type ErrorResponse struct {
	XMLName xml.Name `xml:"error"`
	Reason  string   `xml:"reason"`
	Message string   `xml:"message"`
}

func WriteError(w http.ResponseWriter, statusCode int, reason, message string) {
	WriteXML(w, statusCode, ErrorResponse{
		Reason:  reason,
		Message: message,
	})
}

func WriteXML(w http.ResponseWriter, statusCode int, v any) {
	w.Header().Set("Content-Type", "application/xml")
	w.Header().Set("X-Embedded-Crowd-Version", "Crowd/5.1.0")
	w.WriteHeader(statusCode)
	xml.NewEncoder(w).Encode(v)
}

func WriteNoContent(w http.ResponseWriter) {
	w.Header().Set("X-Embedded-Crowd-Version", "Crowd/5.1.0")
	w.WriteHeader(http.StatusNoContent)
}

func WriteCreated(w http.ResponseWriter) {
	w.Header().Set("X-Embedded-Crowd-Version", "Crowd/5.1.0")
	w.WriteHeader(http.StatusCreated)
}

func WriteOK(w http.ResponseWriter) {
	w.Header().Set("X-Embedded-Crowd-Version", "Crowd/5.1.0")
	w.WriteHeader(http.StatusOK)
}
