package store

import (
	"database/sql"
	"errors"
)

// SharedTimetable 表示一条公开分享的只读课表快照（按学号哈希 1:1 唯一）。
type SharedTimetable struct {
	StudentIDHash string
	ShareCode     string
	ManageHash    string
	DisplayName   string
	SemesterName  string
	PayloadGz     []byte
	PayloadSize   int
	CreatedAt     int64
	UpdatedAt     int64
	ExpiresAt     int64
}

const shareCols = `student_id_hash, share_code, manage_hash, display_name, semester_name, payload_gz, payload_size, created_at, updated_at, expires_at`

func scanShare(sc interface{ Scan(...any) error }) (SharedTimetable, error) {
	var sh SharedTimetable
	err := sc.Scan(
		&sh.StudentIDHash,
		&sh.ShareCode,
		&sh.ManageHash,
		&sh.DisplayName,
		&sh.SemesterName,
		&sh.PayloadGz,
		&sh.PayloadSize,
		&sh.CreatedAt,
		&sh.UpdatedAt,
		&sh.ExpiresAt,
	)
	return sh, err
}

// UpsertShare 写入或覆盖按 student_id_hash 索引的课表分享记录。
// 若 share_code 与其他记录冲突，返回 ErrExists 供上层重试生成新分享码。
func (s *Store) UpsertShare(sh SharedTimetable) error {
	_, err := s.db.Exec(
		`INSERT INTO shared_timetables (`+shareCols+`) VALUES (?,?,?,?,?,?,?,?,?,?)
		ON CONFLICT(student_id_hash) DO UPDATE SET
			share_code = excluded.share_code,
			manage_hash = excluded.manage_hash,
			display_name = excluded.display_name,
			semester_name = excluded.semester_name,
			payload_gz = excluded.payload_gz,
			payload_size = excluded.payload_size,
			created_at = excluded.created_at,
			updated_at = excluded.updated_at,
			expires_at = excluded.expires_at`,
		sh.StudentIDHash,
		sh.ShareCode,
		sh.ManageHash,
		sh.DisplayName,
		sh.SemesterName,
		sh.PayloadGz,
		sh.PayloadSize,
		sh.CreatedAt,
		sh.UpdatedAt,
		sh.ExpiresAt,
	)
	if isUnique(err) {
		return ErrExists
	}
	return err
}

// GetShareByCode 按分享码查询课表分享记录。
func (s *Store) GetShareByCode(code string) (SharedTimetable, error) {
	sh, err := scanShare(s.db.QueryRow(`SELECT `+shareCols+` FROM shared_timetables WHERE share_code=?`, code))
	if errors.Is(err, sql.ErrNoRows) {
		return SharedTimetable{}, ErrNotFound
	}
	return sh, err
}

// GetShareByStudentHash 按学号哈希查询课表分享记录。
func (s *Store) GetShareByStudentHash(hash string) (SharedTimetable, error) {
	sh, err := scanShare(s.db.QueryRow(`SELECT `+shareCols+` FROM shared_timetables WHERE student_id_hash=?`, hash))
	if errors.Is(err, sql.ErrNoRows) {
		return SharedTimetable{}, ErrNotFound
	}
	return sh, err
}

// DeleteShare 删除指定学号哈希的课表分享记录。
func (s *Store) DeleteShare(studentIDHash string) (bool, error) {
	res, err := s.db.Exec(`DELETE FROM shared_timetables WHERE student_id_hash=?`, studentIDHash)
	if err != nil {
		return false, err
	}
	n, _ := res.RowsAffected()
	return n > 0, nil
}

// PurgeExpiredShares 清理所有已过期的课表分享记录，返回清理条数。
func (s *Store) PurgeExpiredShares(nowMs int64) (int64, error) {
	res, err := s.db.Exec(`DELETE FROM shared_timetables WHERE expires_at <= ?`, nowMs)
	if err != nil {
		return 0, err
	}
	return res.RowsAffected()
}
