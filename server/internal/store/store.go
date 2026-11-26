// Package store 是服务端的 SQLite 存储：用户、设备令牌与同步记录。
package store

import (
	"database/sql"
	"encoding/json"
	"errors"
	"fmt"
	"strings"
	"time"

	_ "modernc.org/sqlite" // 纯 Go 驱动，不需要 cgo
)

var (
	ErrNotFound = errors.New("not found")
	ErrExists   = errors.New("already exists")
)

type Store struct{ db *sql.DB }

type User struct {
	ID        int64  `json:"id"`
	Username  string `json:"username"`
	PassHash  string `json:"-"`
	Role      string `json:"role"` // admin | user
	Disabled  bool   `json:"disabled"`
	CreatedAt int64  `json:"createdAt"`
	Devices   int    `json:"devices"`
	Records   int    `json:"records"`
}

// Record 是同步的最小单位：客户端的一条记录（课程、事件、待办……）。
type Record struct {
	ID        string          `json:"id"`
	Kind      string          `json:"kind"`
	Data      json.RawMessage `json:"data"`
	UpdatedAt int64           `json:"updatedAt"`
	DeletedAt *int64          `json:"deletedAt"`
	Seq       int64           `json:"seq,omitempty"`
}

func Open(path string) (*Store, error) {
	dsn := "file:" + path + "?_pragma=busy_timeout(5000)&_pragma=journal_mode(WAL)&_pragma=foreign_keys(1)"
	db, err := sql.Open("sqlite", dsn)
	if err != nil {
		return nil, err
	}
	db.SetMaxOpenConns(1) // 个人自建规模，单连接最简单可靠
	s := &Store{db: db}
	if err := s.migrate(); err != nil {
		db.Close()
		return nil, err
	}
	return s, nil
}

func (s *Store) Close() error { return s.db.Close() }

func (s *Store) migrate() error {
	stmts := []string{
		`CREATE TABLE IF NOT EXISTS users (
			id INTEGER PRIMARY KEY AUTOINCREMENT,
			username TEXT NOT NULL UNIQUE COLLATE NOCASE,
			pass_hash TEXT NOT NULL,
			role TEXT NOT NULL DEFAULT 'user',
			disabled INTEGER NOT NULL DEFAULT 0,
			seq INTEGER NOT NULL DEFAULT 0,
			created_at INTEGER NOT NULL)`,
		`CREATE TABLE IF NOT EXISTS tokens (
			id INTEGER PRIMARY KEY AUTOINCREMENT,
			user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
			token_hash TEXT NOT NULL UNIQUE,
			device TEXT NOT NULL DEFAULT '',
			created_at INTEGER NOT NULL,
			last_used_at INTEGER NOT NULL)`,
		`CREATE TABLE IF NOT EXISTS records (
			user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
			id TEXT NOT NULL,
			kind TEXT NOT NULL,
			data TEXT NOT NULL,
			updated_at INTEGER NOT NULL,
			deleted_at INTEGER,
			seq INTEGER NOT NULL,
			PRIMARY KEY (user_id, id))`,
		`CREATE INDEX IF NOT EXISTS idx_records_seq ON records(user_id, seq)`,
		`CREATE TABLE IF NOT EXISTS shared_timetables (
			student_id_hash TEXT PRIMARY KEY,
			share_code TEXT NOT NULL UNIQUE,
			manage_hash TEXT NOT NULL,
			display_name TEXT NOT NULL,
			semester_name TEXT NOT NULL,
			payload_gz BLOB NOT NULL,
			payload_size INTEGER NOT NULL,
			created_at INTEGER NOT NULL,
			updated_at INTEGER NOT NULL,
			expires_at INTEGER NOT NULL)`,
		`CREATE INDEX IF NOT EXISTS idx_shared_timetables_code ON shared_timetables(share_code)`,
		`CREATE INDEX IF NOT EXISTS idx_shared_timetables_expires ON shared_timetables(expires_at)`,
	}
	for _, q := range stmts {
		if _, err := s.db.Exec(q); err != nil {
			return fmt.Errorf("migrate: %w", err)
		}
	}
	return nil
}

func now() int64 { return time.Now().UnixMilli() }

func isUnique(err error) bool {
	return err != nil && strings.Contains(err.Error(), "UNIQUE constraint failed")
}

// ---------- 用户 ----------

const userCols = `id, username, pass_hash, role, disabled, created_at`

func scanUser(sc interface{ Scan(...any) error }) (User, error) {
	var u User
	var dis int
	err := sc.Scan(&u.ID, &u.Username, &u.PassHash, &u.Role, &dis, &u.CreatedAt)
	u.Disabled = dis != 0
	return u, err
}

func (s *Store) CountUsers() (int, error) {
	var n int
	err := s.db.QueryRow(`SELECT COUNT(*) FROM users`).Scan(&n)
	return n, err
}

// CountActiveAdmins 统计未被禁用的管理员数量，用来保证至少留一个可用管理员。
func (s *Store) CountActiveAdmins() (int, error) {
	var n int
	err := s.db.QueryRow(`SELECT COUNT(*) FROM users WHERE role='admin' AND disabled=0`).Scan(&n)
	return n, err
}

func (s *Store) CreateUser(username, passHash, role string) (User, error) {
	res, err := s.db.Exec(`INSERT INTO users(username, pass_hash, role, created_at) VALUES(?,?,?,?)`, username, passHash, role, now())
	if isUnique(err) {
		return User{}, ErrExists
	}
	if err != nil {
		return User{}, err
	}
	id, _ := res.LastInsertId()
	return s.User(id)
}

func (s *Store) User(id int64) (User, error) {
	u, err := scanUser(s.db.QueryRow(`SELECT `+userCols+` FROM users WHERE id=?`, id))
	if errors.Is(err, sql.ErrNoRows) {
		return User{}, ErrNotFound
	}
	return u, err
}

func (s *Store) UserByName(name string) (User, error) {
	u, err := scanUser(s.db.QueryRow(`SELECT `+userCols+` FROM users WHERE username=?`, name))
	if errors.Is(err, sql.ErrNoRows) {
		return User{}, ErrNotFound
	}
	return u, err
}

func (s *Store) ListUsers() ([]User, error) {
	rows, err := s.db.Query(`SELECT ` + userCols + `,
		(SELECT COUNT(*) FROM tokens t WHERE t.user_id = users.id),
		(SELECT COUNT(*) FROM records r WHERE r.user_id = users.id AND r.deleted_at IS NULL)
		FROM users ORDER BY id`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	out := []User{}
	for rows.Next() {
		var u User
		var dis int
		if err := rows.Scan(&u.ID, &u.Username, &u.PassHash, &u.Role, &dis, &u.CreatedAt, &u.Devices, &u.Records); err != nil {
			return nil, err
		}
		u.Disabled = dis != 0
		out = append(out, u)
	}
	return out, rows.Err()
}

func (s *Store) exec1(id int64, q string, args ...any) error {
	res, err := s.db.Exec(q, append(args, id)...)
	if err != nil {
		return err
	}
	if n, _ := res.RowsAffected(); n == 0 {
		return ErrNotFound
	}
	return nil
}

func (s *Store) SetPassword(id int64, hash string) error {
	return s.exec1(id, `UPDATE users SET pass_hash=? WHERE id=?`, hash)
}

func (s *Store) SetRole(id int64, role string) error {
	return s.exec1(id, `UPDATE users SET role=? WHERE id=?`, role)
}

func (s *Store) SetDisabled(id int64, disabled bool) error {
	v := 0
	if disabled {
		v = 1
	}
	return s.exec1(id, `UPDATE users SET disabled=? WHERE id=?`, v)
}

// DeleteUser 删除用户及其全部令牌与同步数据（外键级联）。
func (s *Store) DeleteUser(id int64) error {
	return s.exec1(id, `DELETE FROM users WHERE id=?`)
}

// ---------- 令牌（每台设备一个） ----------

func (s *Store) CreateToken(userID int64, tokenHash, device string) error {
	t := now()
	_, err := s.db.Exec(`INSERT INTO tokens(user_id, token_hash, device, created_at, last_used_at) VALUES(?,?,?,?,?)`,
		userID, tokenHash, device, t, t)
	return err
}

// UserByToken 返回令牌对应的用户（已禁用的用户视为无效）。
func (s *Store) UserByToken(tokenHash string) (User, error) {
	u, err := scanUser(s.db.QueryRow(`SELECT u.id, u.username, u.pass_hash, u.role, u.disabled, u.created_at
		FROM tokens t JOIN users u ON u.id = t.user_id WHERE t.token_hash=?`, tokenHash))
	if errors.Is(err, sql.ErrNoRows) {
		return User{}, ErrNotFound
	}
	if err != nil {
		return User{}, err
	}
	if u.Disabled {
		return User{}, ErrNotFound
	}
	_, _ = s.db.Exec(`UPDATE tokens SET last_used_at=? WHERE token_hash=?`, now(), tokenHash)
	return u, nil
}

func (s *Store) DeleteToken(tokenHash string) error {
	_, err := s.db.Exec(`DELETE FROM tokens WHERE token_hash=?`, tokenHash)
	return err
}

func (s *Store) DeleteUserTokens(userID int64) error {
	_, err := s.db.Exec(`DELETE FROM tokens WHERE user_id=?`, userID)
	return err
}

// ---------- 同步 ----------

// Record 按 id 取单条记录（含墓碑）。
func (s *Store) Record(userID int64, id string) (Record, error) {
	r, err := scanRecord(s.db.QueryRow(`SELECT id, kind, data, updated_at, deleted_at, seq FROM records WHERE user_id=? AND id=?`, userID, id))
	if errors.Is(err, sql.ErrNoRows) {
		return Record{}, ErrNotFound
	}
	return r, err
}

// RecordsByKind 返回用户指定 kind 的全部记录；includeDeleted 为 false 时不含墓碑，按 updated_at 升序。
func (s *Store) RecordsByKind(userID int64, kinds []string, includeDeleted bool) ([]Record, error) {
	q := `SELECT id, kind, data, updated_at, deleted_at, seq FROM records WHERE user_id=? AND kind IN (`
	args := []any{userID}
	for i, k := range kinds {
		if i > 0 {
			q += ","
		}
		q += "?"
		args = append(args, k)
	}
	if !includeDeleted {
		q += ") AND deleted_at IS NULL ORDER BY updated_at"
	} else {
		q += ") ORDER BY updated_at"
	}
	rows, err := s.db.Query(q, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	out := []Record{}
	for rows.Next() {
		r, err := scanRecord(rows)
		if err != nil {
			return nil, err
		}
		out = append(out, r)
	}
	return out, rows.Err()
}

func (s *Store) Cursor(userID int64) (int64, error) {
	var c int64
	err := s.db.QueryRow(`SELECT seq FROM users WHERE id=?`, userID).Scan(&c)
	return c, err
}

// Pull 返回 seq 大于 since 的记录（按 seq 升序，最多 limit 条）。more 表示还有后续。
func (s *Store) Pull(userID, since int64, limit int) (recs []Record, more bool, err error) {
	rows, err := s.db.Query(`SELECT id, kind, data, updated_at, deleted_at, seq FROM records
		WHERE user_id=? AND seq>? ORDER BY seq LIMIT ?`, userID, since, limit+1)
	if err != nil {
		return nil, false, err
	}
	defer rows.Close()
	recs = []Record{}
	for rows.Next() {
		r, err := scanRecord(rows)
		if err != nil {
			return nil, false, err
		}
		recs = append(recs, r)
	}
	if err := rows.Err(); err != nil {
		return nil, false, err
	}
	if len(recs) > limit {
		recs, more = recs[:limit], true
	}
	return recs, more, nil
}

func scanRecord(sc interface{ Scan(...any) error }) (Record, error) {
	var r Record
	var data string
	var del sql.NullInt64
	if err := sc.Scan(&r.ID, &r.Kind, &data, &r.UpdatedAt, &del, &r.Seq); err != nil {
		return r, err
	}
	r.Data = json.RawMessage(data)
	if del.Valid {
		v := del.Int64
		r.DeletedAt = &v
	}
	return r, nil
}

// Push 写入客户端记录。规则：同一条记录 updatedAt 更新者胜（last-write-wins）；
// 内容完全相同视为已接受（不产生新序号）；落后的记录被拒绝并返回服务器上的版本，客户端据此覆盖本地。
func (s *Store) Push(userID int64, in []Record) (accepted []string, rejected []Record, cursor int64, err error) {
	tx, err := s.db.Begin()
	if err != nil {
		return nil, nil, 0, err
	}
	defer tx.Rollback()
	accepted = []string{}
	rejected = []Record{}
	for _, r := range in {
		cur, err := scanRecord(tx.QueryRow(`SELECT id, kind, data, updated_at, deleted_at, seq FROM records WHERE user_id=? AND id=?`, userID, r.ID))
		exists := err == nil
		if err != nil && !errors.Is(err, sql.ErrNoRows) {
			return nil, nil, 0, err
		}
		if exists {
			if sameRecord(cur, r) {
				accepted = append(accepted, r.ID)
				continue
			}
			if r.UpdatedAt <= cur.UpdatedAt {
				rejected = append(rejected, cur)
				continue
			}
		}
		var seq int64
		if err := tx.QueryRow(`UPDATE users SET seq = seq + 1 WHERE id=? RETURNING seq`, userID).Scan(&seq); err != nil {
			return nil, nil, 0, err
		}
		var del any
		if r.DeletedAt != nil {
			del = *r.DeletedAt
		}
		if _, err := tx.Exec(`INSERT INTO records(user_id, id, kind, data, updated_at, deleted_at, seq) VALUES(?,?,?,?,?,?,?)
			ON CONFLICT(user_id, id) DO UPDATE SET kind=excluded.kind, data=excluded.data,
			updated_at=excluded.updated_at, deleted_at=excluded.deleted_at, seq=excluded.seq`,
			userID, r.ID, r.Kind, string(r.Data), r.UpdatedAt, del, seq); err != nil {
			return nil, nil, 0, err
		}
		accepted = append(accepted, r.ID)
	}
	if err := tx.QueryRow(`SELECT seq FROM users WHERE id=?`, userID).Scan(&cursor); err != nil {
		return nil, nil, 0, err
	}
	return accepted, rejected, cursor, tx.Commit()
}

func sameRecord(a, b Record) bool {
	if a.UpdatedAt != b.UpdatedAt || a.Kind != b.Kind || string(a.Data) != string(b.Data) {
		return false
	}
	if (a.DeletedAt == nil) != (b.DeletedAt == nil) {
		return false
	}
	return a.DeletedAt == nil || *a.DeletedAt == *b.DeletedAt
}
