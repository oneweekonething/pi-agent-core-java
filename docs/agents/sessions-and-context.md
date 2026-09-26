# 会话与上下文组装

**何时使用本文档：** 修改会话树、分支/回退行为、消息转换或上下文裁剪时。

`Sessions.Tree` 保留全部节点并维护唯一的 `activeTipId`。`activePath()` 从活跃尖端沿父指针走到根，是唯一会发给模型的历史。

规则：

- 分支会创建一个新 user 节点，其父节点是选中的历史节点。
- 回退（rewind）只改变活跃尖端，不删除历史。
- assistant 节点存储结构化的 tool-call 快照。
- tool-result 节点存储 `toolCallId`、工具名、错误状态与 observation 内容。
- 上下文组装必须保持 assistant-tool-call -> tool-result 的顺序。
- 预算裁剪优先移除最老的消息，且总是至少保留最新一条消息。估算包含 tool-call 参数与工具参数 schema，而不只是消息文本。
- `Manager.getOrCreate` 用 per-id single-flight 包住整个 find-or-create：并发调用共享同一次查找/创建，成功与失败都会清理飞行记录，失败后可重试。"single-flight 结束后、save 对后续 find 可见前"的新调用仍可能重复创建，跨 Manager/跨进程的互斥同样需要 Repository 层原子性。
- token 估算在核心层刻意保持启发式。供应商专属的分词器属于适配器实现。
