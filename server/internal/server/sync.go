package server

import (
	"encoding/json"
	"net/http"
	"strconv"

	"github.com/xihale/mintcal/server/internal/store"
)

const (
	maxPullLimit  = 1000
	maxPushBatch  = 1000
	maxRecordSize = 1 << 20
)

func (s *Server) handlePull(w http.ResponseWriter, r *http.Request, ss *session) {
	since, _ := strconv.ParseInt(r.URL.Query().Get("since"), 10, 64)
	limit, _ := strconv.Atoi(r.URL.Query().Get("limit"))
	if limit <= 0 || limit > maxPullLimit {
		limit = 500
	}
	recs, more, err := s.st.Pull(ss.user.ID, since, limit)
	if err != nil {
		internalErr(w, err)
		return
	}
	cursor := since
	if len(recs) > 0 {
		cursor = recs[len(recs)-1].Seq
	}
	writeJSON(w, http.StatusOK, map[string]any{"records": recs, "cursor": cursor, "more": more})
}

func validRecord(r store.Record) bool {
	if r.ID == "" || len(r.ID) > 64 || r.Kind == "" || len(r.Kind) > 32 || r.UpdatedAt <= 0 {
		return false
	}
	if len(r.Data) == 0 || len(r.Data) > maxRecordSize {
		return false
	}
	var obj map[string]json.RawMessage
	return json.Unmarshal(r.Data, &obj) == nil
}

func (s *Server) handlePush(w http.ResponseWriter, r *http.Request, ss *session) {
	var req struct {
		Records []store.Record `json:"records"`
	}
	if !readJSON(w, r, &req) {
		return
	}
	if len(req.Records) > maxPushBatch {
		writeErr(w, http.StatusBadRequest, "batch_too_large", "一次最多推送 1000 条记录")
		return
	}
	for _, rec := range req.Records {
		if !validRecord(rec) {
			writeErr(w, http.StatusBadRequest, "bad_record", "记录格式不正确："+rec.ID)
			return
		}
	}
	accepted, rejected, cursor, err := s.st.Push(ss.user.ID, req.Records)
	if err != nil {
		internalErr(w, err)
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"accepted": accepted, "rejected": rejected, "cursor": cursor})
}
