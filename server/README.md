# 薄荷日程同步服务（自建）

给 App 的「账号与同步」用的小型服务：**没有开放注册、没有邮箱**，由管理员在后台创建用户；用户在 App 里填服务器地址、用户名、密码登录后，数据按记录同步。不登录就是纯本地模式。设计见 [ADR-0005](../docs/adr/0005-go-self-hosted-server.md)，接口见 [openapi.yaml](openapi.yaml)。

## 运行

```bash
# 方式一：Docker
cd server && docker compose up -d
docker compose logs mintcal | grep 管理员     # 没设置密码时，初始管理员密码只在日志里出现一次

# 方式二：直接运行（需要 Go 1.24+）
cd server && go run ./cmd/mintcal-server
```

首次启动会创建管理员（默认用户名 `admin`）。用环境变量指定账号：

| 变量 | 含义 | 默认 |
| --- | --- | --- |
| `MINTCAL_ADDR` | 监听地址 | `:8080` |
| `MINTCAL_DB` | SQLite 数据库文件 | `./data/mintcal.db`（Docker 里 `/data/mintcal.db`） |
| `MINTCAL_ADMIN_USER` | 首次启动创建的管理员用户名 | `admin` |
| `MINTCAL_ADMIN_PASSWORD` | 首次启动的管理员密码；不设置则随机生成并打印一次 | 随机 |
| `MINTCAL_TRUST_PROXY` | 设为 `1` 时用 `X-Forwarded-For` 识别客户端 IP（反向代理后面使用） | 关 |

## 管理后台

浏览器打开 `http://服务器:8080/admin/`，用管理员账号登录，可以：创建用户、重置密码、停用 / 启用、设为管理员 / 普通用户、让某用户的所有设备退出登录、删除用户（连同其同步数据）。系统始终至少保留一个可用的管理员；不能停用、降级或删除自己。

忘记管理员密码：`mintcal-server passwd <用户名>`（在服务器上执行，新密码从标准输入读取）。

## 部署建议

- 对外使用请放在 HTTPS 反向代理后面，例如 Caddy：

  ```
  cal.example.com {
      reverse_proxy 127.0.0.1:8080
  }
  ```

  并设置 `MINTCAL_TRUST_PROXY=1`。App 对 `http://` 地址会提示「密码和日程明文传输」。
- 备份：停止服务或使用 `sqlite3 mintcal.db ".backup backup.db"`，备份 `data/` 目录即可。
- 服务端不解析日程内容，只存 JSON；App 升级后新增的字段无需升级服务端。

## 开发

```bash
cd server
go vet ./... && go test ./...
```

仓库根目录的 `scripts/sync-e2e.sh` 会启动一个临时服务端，用两台「设备」（Robolectric 里的两份本地数据库）跑端到端同步测试。
