// goke-server：Goke 的自建同步服务。
//
//	goke-server                      启动服务（默认）
//	goke-server passwd <用户名>       从标准输入读取新密码并重置（忘记管理员密码时使用）
//
// 配置（环境变量）：
//
//	GOKE_ADDR            监听地址，默认 :8080
//	GOKE_DB              SQLite 数据库文件，默认 ./data/goke.db
//	GOKE_ADMIN_USER      首次启动时创建的管理员用户名，默认 admin
//	GOKE_ADMIN_PASSWORD  首次启动时的管理员密码；不设置则随机生成并打印一次
//	GOKE_TRUST_PROXY     设为 1 时信任 X-Forwarded-For（部署在反向代理后面时）
package main

import (
	"bufio"
	"context"
	"errors"
	"fmt"
	"log"
	"net/http"
	"os"
	"os/signal"
	"path/filepath"
	"strings"
	"syscall"
	"time"

	"github.com/xihale/goke/server/internal/server"
	"github.com/xihale/goke/server/internal/store"
)

func env(key, def string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return def
}

func main() {
	dbPath := env("GOKE_DB", "./data/goke.db")
	if err := os.MkdirAll(filepath.Dir(dbPath), 0o700); err != nil {
		log.Fatalf("创建数据目录失败：%v", err)
	}
	st, err := store.Open(dbPath)
	if err != nil {
		log.Fatalf("打开数据库失败：%v", err)
	}
	defer st.Close()

	if len(os.Args) >= 2 && os.Args[1] == "passwd" {
		if err := resetPassword(st, os.Args[2:]); err != nil {
			log.Fatal(err)
		}
		return
	}

	srv := server.New(st, server.Options{TrustProxy: os.Getenv("GOKE_TRUST_PROXY") == "1"})
	if name, pw, created, err := srv.Bootstrap(os.Getenv("GOKE_ADMIN_USER"), os.Getenv("GOKE_ADMIN_PASSWORD")); err != nil {
		log.Fatalf("创建管理员失败：%v", err)
	} else if created {
		if os.Getenv("GOKE_ADMIN_PASSWORD") == "" {
			log.Printf("已创建管理员「%s」，初始密码（只显示这一次，请尽快登录后台修改）：%s", name, pw)
		} else {
			log.Printf("已创建管理员「%s」", name)
		}
	}

	addr := env("GOKE_ADDR", ":8080")
	hs := &http.Server{
		Addr:              addr,
		Handler:           srv.Handler(),
		ReadHeaderTimeout: 10 * time.Second,
		ReadTimeout:       60 * time.Second,
		WriteTimeout:      60 * time.Second,
		IdleTimeout:       120 * time.Second,
	}
	go func() {
		log.Printf("Goke 同步服务已启动：%s（管理后台：/admin/）", addr)
		if err := hs.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			log.Fatal(err)
		}
	}()
	stop := make(chan os.Signal, 1)
	signal.Notify(stop, syscall.SIGINT, syscall.SIGTERM)
	<-stop
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	_ = hs.Shutdown(ctx)
}

func resetPassword(st *store.Store, args []string) error {
	if len(args) != 1 {
		return errors.New("用法：goke-server passwd <用户名>（新密码从标准输入读取）")
	}
	u, err := st.UserByName(args[0])
	if err != nil {
		return fmt.Errorf("找不到用户 %q", args[0])
	}
	fmt.Fprint(os.Stderr, "新密码（至少 8 位）：")
	line, _ := bufio.NewReader(os.Stdin).ReadString('\n')
	pw := strings.TrimRight(line, "\r\n")
	if len(pw) < server.MinPasswordLen || len(pw) > server.MaxPasswordLen {
		return errors.New("密码至少 8 位")
	}
	hash, err := server.HashPassword(pw)
	if err != nil {
		return err
	}
	if err := st.SetPassword(u.ID, hash); err != nil {
		return err
	}
	_ = st.DeleteUserTokens(u.ID)
	fmt.Fprintln(os.Stderr, "已重置密码，该用户所有设备需要重新登录。")
	return nil
}
