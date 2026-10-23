# S004 本地数据库与仓库（含高级修改）

## 背景与目标
手写 SQLite 仓库（见 ADR-0002）；所有修改经 ChangeSet（ADR-0003）；回收站、历史、撤销；:script 用 QuickJS 执行高级修改，支持 Dry Run 预览与应用。

## 验收
Dry Run 不落库；应用后可撤销最近一次；脚本沙箱无 IO、有超时。

## 测试
:core TimetableDiffTest 等；:script 17 个测试。
