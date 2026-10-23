# S003 ICS 解析器

## 背景与目标
解析/生成 ICS（含 WakeUp 导出、RRULE、EXDATE、时区），xlsx 课表导入导出。

## 验收
导入 ICS 得到课程/事件；导出后可被再次导入且内容一致（往返）。

## 测试
:core ICS 解析与往返测试。
