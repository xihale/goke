# ADR-0002：用手写 SQLite 代替 Room

- 状态：已采纳（修订了手册中「Room」的初始设想）
- 背景：数据模型是 6 类记录（学期、课程、调课、日程、待办、清单），都带 `id / updatedAt / deletedAt`，天然适合「一条记录一行 JSON」。同时需要操作日志表（撤销、历史）。
- 决定：`MintDb`（`SQLiteOpenHelper`）里两张表：`records(id, kind, json, updated_at, deleted_at)` 与 `oplog(id, created_at, description, source, json, undone_at)`。序列化沿用 `:core` 的 kotlinx-serialization；所有写入在一个事务里完成（记录 + 操作日志）。
- 后果：少一个注解处理器（KSP）和一层迁移配置；模型演进靠 JSON 字段默认值兼容。代价是没有编译期 SQL 校验；查询都在内存快照里做（数据量小），所以影响很小。
