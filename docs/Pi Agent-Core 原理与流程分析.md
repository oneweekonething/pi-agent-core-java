# @earendil-works/pi-agent-core 原理与流程分析

## 🏗️ 架构设计核心

### 1. Agent-Core 在 Pi 生态中的位置

```
┌──────────────────────────────────────────────────────────┐
│                     Application Layer                     │
│        (@earendil-works/pi-coding-agent CLI)             │
└─────────────────────┬──────────────────────────────────────┘
                      │
┌─────────────────────▼──────────────────────────────────────┐
│            Agent Runtime Layer (Core)                      │
│    • 会话管理（Session Management）                        │
│    • 工具执行（Tool Execution）                           │
│    • 状态维护（State Management）                         │
│    • LLM 协调（LLM Coordination）                         │
└─────────────┬────────────┬────────────┬────────────────────┘
              │            │            │
    ┌─────────▼──┐  ┌──────▼───┐  ┌────▼──────┐
    │  pi-ai     │  │ pi-durable│  │  Chord    │
    │ LLM API    │  │ Runtime   │  │Composition│
    └────────────┘  └───────────┘  └───────────┘
```

### 2. 核心模块职责

| 模块 | 职责 | 关键特性 |
|------|------|---------|
| **Session Manager** | 会话生命周期、树状结构管理 | 支持分支、fork、回溯 |
| **Tool System** | 工具定义、注册、调用、执行 | 动态工具加载、权限控制 |
| **Context Engine** | 上下文组装、历史管理 | 智能压缩、动态加载 |
| **LLM Coordinator** | 模型请求、响应处理、流式传输 | 多供应商支持、模型切换 |
| **Event Loop** | 异步事件处理、消息队列 | 转向消息、后续消息、中止 |

---

## 🔄 Agent 执行流程详解

### 3. 核心循环（Agent Loop）

```
┌─────────────────────────────────────────────────────────────┐
│                    START - User Input                       │
└───────────────────┬─────────────────────────────────────────┘
                    │
        ┌───────────▼──────────┐
        │  Parse User Message  │
        │  (含文件、图像、命令) │
        └───────────┬──────────┘
                    │
        ┌───────────▼──────────────────────┐
        │  Apply Prompt Templates          │
        │  (if message starts with `/name`)│
        └───────────┬──────────────────────┘
                    │
        ┌───────────▼──────────────────────┐
        │  Build Model Request             │
        │  ├─ System Prompt (static)       │
        │  ├─ Context Files (AGENTS.md)    │
        │  ├─ Active Branch History        │
        │  ├─ Tool Definitions             │
        │  ├─ Skill Descriptions           │
        │  └─ Extension-injected Context   │
        └───────────┬──────────────────────┘
                    │
        ┌───────────▼──────────────────────────────┐
        │  Send to LLM Provider via pi-ai          │
        │  ├─ Respect Context Window Limits       │
        │  ├─ Apply Model Settings                │
        │  └─ Stream Response                      │
        └───────────┬──────────────────────────────┘
                    │
        ┌───────────▼──────────────────────────────┐
        │  Parse LLM Response                      │
        │  ├─ Extract Text Content                │
        │  └─ Extract Tool Calls                   │
        └───────────┬──────────────────────────────┘
                    │
        ┌───────────▼──────────────────────────────┐
        │  Record Assistant Message                │
        │  (in current active branch)              │
        └───────────┬──────────────────────────────┘
                    │
        ┌───────────▼──────────────────────────────┐
        │  Execute Tool Calls (Sequential)         │
        │  For each tool call:                     │
        │  ├─ Locate tool implementation           │
        │  ├─ Validate parameters                  │
        │  ├─ Execute with process permissions    │
        │  ├─ Capture output/errors               │
        │  └─ Record result in session             │
        └───────────┬──────────────────────────────┘
                    │
        ┌───────────▼──────────────────────────────┐
        │  Check Continuation Conditions           │
        │  ├─ Are there pending tool results?     │
        │  ├─ Are there queued messages?          │
        │  └─ Did an extension request another turn?│
        └───────────┬──────────────────────────────┘
                    │
            ┌───────┴────────┐
            │                │
        YES │                │ NO
            │                │
        ┌───▼─────────────┐  │
        │ Start New Turn  │  │
        │ (Loop Back)     │  │
        └────────────────┘   │
                            │
                    ┌───────▼──────────────┐
                    │  RUN COMPLETE        │
                    │  Store Session       │
                    │  Return to User      │
                    └──────────────────────┘
```

### 4. 关键概念详解

#### 4.1 Session（会话）结构

**文件格式**：JSONL（JSON Lines）
```json
{"id":"entry-1","type":"user","content":"Build a web app","timestamp":"..."}
{"id":"entry-2","type":"assistant","content":"I'll help you...","timestamp":"..."}
{"id":"entry-3","type":"tool-result","toolId":"run_command","result":"..."}
{"id":"entry-4","type":"compaction","summary":"...","replacedEntries":["entry-1","entry-2"]}
{"id":"entry-5","parentId":"entry-3","type":"user","content":"Now add tests","timestamp":"..."}
```

**树状结构**：
```
entry-1 (user) ─┐
                ├─→ entry-2 (assistant) ─→ entry-3 (tool-result) ─→ entry-4 (user)
                │                                                      ↑
                └────────── ACTIVE BRANCH (current) ──────────────────┘

entry-5 (user) ─→ entry-6 (assistant)  [Alternative Branch]
```

**特点**：
- ✅ 支持任意分支点回溯
- ✅ 单文件存储整个树
- ✅ 支持继续（continue）和分叉（fork）操作
- ✅ 压缩（compaction）只影响后续请求，原数据保留

#### 4.2 Context Assembly（上下文组装）

```
Input Sources:
  │
  ├─ Base System Prompt (system-prompt.ts)
  │   └─ "You are a coding agent..."
  │
  ├─ AGENTS.md (Project Instructions)
  │   ├─ From ~/.pi/agent/AGENTS.md
  │   ├─ From parent directories
  │   └─ From current directory
  │
  ├─ SYSTEM.md (Custom System Prompt)
  │   └─ Per-project overrides/appends
  │
  ├─ Active Branch History
  │   ├─ User messages
  │   ├─ Assistant responses
  │   ├─ Tool results
  │   └─ Compaction summaries
  │
  ├─ Tool Definitions
  │   ├─ Built-in tools (run, edit, read, etc.)
  │   └─ Extension tools
  │
  ├─ Skill Descriptions
  │   ├─ Available skills (loaded on-demand)
  │   └─ Capability summaries
  │
  └─ Extension-Injected Context
      ├─ RAG results
      ├─ Dynamic instructions
      └─ Custom data
        │
        ▼
    ┌──────────────────────────────┐
    │  Context Window               │
    │  (respects max_tokens limit)  │
    │  ├─ System Prompt             │
    │  ├─ Context Files             │
    │  ├─ Active Branch             │
    │  ├─ Tools Summary             │
    │  └─ Skill Instructions        │
    └──────────────────────────────┘
```

#### 4.3 Tool Execution（工具执行）

```
Tool Definition:
{
  "name": "run",
  "description": "Execute shell commands",
  "parameters": {
    "type": "object",
    "properties": {
      "command": {"type": "string"},
      "cwd": {"type": "string"}
    }
  }
}
        │
        ▼
Tool Call (from LLM):
{
  "toolUseId": "call-123",
  "toolName": "run",
  "parameters": {"command": "npm test", "cwd": "/project"}
}
        │
        ▼
Tool Execution Flow:
  1. Lookup tool handler
  2. Validate parameters
  3. Apply OS permissions
  4. Execute handler function
  5. Capture output + errors
  6. Handle timeouts
        │
        ▼
Tool Result:
{
  "type": "tool-result",
  "toolUseId": "call-123",
  "content": "✓ All tests passed",
  "isError": false
}
        │
        ▼
Record in Session
        │
        ▼
Send back to LLM in next turn
```

---

## 🎯 核心设计原则

### 5. Tree-Structured Sessions

**设计优势**：
```
传统线性历史                    Pi 树状历史
┌─────────┐                   ┌─────────┐
│ message │                   │ message │
├─────────┤                   ├─────────┤
│ message │                   │ message ├──→ Branch A (explore option 1)
├─────────┤                   ├─────────┤
│ message │    vs.            │ message ├──→ Branch B (explore option 2)
├─────────┤                   ├─────────┤
│ message │                   │ message └──→ Branch C (explore option 3)
└─────────┘                   └─────────┘

- 线性历史：一旦分岔就丢失原路径      - 树状结构：保留所有分支
- 重复工作                            - 可复用公共前缀
- 无法比较不同策略                    - 支持策略比对
```

### 6. Lazy Skill Loading

```
Agent Request Size Over Time:

Without Lazy Loading:
┌────────────────────────────────────────────────────┐
│ System Prompt + All Skills + All Tools + Context   │
│ Size: ~80KB (potentially exceeds token limit)      │
└────────────────────────────────────────────────────┘

With Lazy Loading:
Request 1:
┌──────────────────────────────────────────┐
│ System Prompt + Skill Summaries + Context│
│ Size: ~20KB (within budget)              │
└──────────────────────────────────────────┘
        │ (Agent asks for skill)
        ▼
Request 2:
┌──────────────────────────────────────────┐
│ Skill Instructions + Context             │
│ Size: ~15KB (focused request)            │
└──────────────────────────────────────────┘
```

**好处**：
- 📉 初始请求更小
- 💾 节省 token 预算
- 🚀 更快的响应时间
- 🔄 适应不同任务规模

### 7. Compaction（上下文压缩）

```
Original Session (5 turns):
┌────────────────────────────────────┐
│ Turn 1: Plan data migration       │  ← Older, can compress
│ Turn 2: Review schema              │  ← Older, can compress
│ Turn 3: Write migration script     │  ← Older, can compress
│ Turn 4: Test on staging            │  ← Recent, keep
│ Turn 5: Deploy to production       │  ← Current, keep
└────────────────────────────────────┘
                │
                ▼ (When approaching context limit)
        
After Compaction:
┌──────────────────────────────────────────┐
│ COMPACTION ENTRY: "Planned data         │
│ migration, reviewed schema, wrote and    │
│ tested migration script successfully"   │  ← Summary replaces 3 turns
├──────────────────────────────────────────┤
│ Turn 4: Test on staging                 │
│ Turn 5: Deploy to production            │
└──────────────────────────────────────────┘

Session File: Original entries preserved (for tree navigation)
Model Request: Uses compaction summary for subsequent turns
```

### 8. Steering & Follow-up Messages

```
┌─────────────────────────────────────────────────────────┐
│            Agent Executing Tool 1, Tool 2, Tool 3       │
│                                                         │
│  Press Enter    ←────────┐    Press Alt+Enter ←────┐  │
│  (Steering)              │    (Follow-up)          │  │
│                          │                         │  │
│  ┌──────────────────┐    │  ┌──────────────────┐  │  │
│  │ Interrupts       │    │  │ Waits for        │  │  │
│  │ immediately      │    │  │ current batch    │  │  │
│  │ after current    │    │  │ to complete      │  │  │
│  │ tool             │    │  │                  │  │  │
│  └──────────────────┘    │  └──────────────────┘  │  │
│                          │                         │  │
│  Tool 1  [Done]          │  Tool 1  [Done]        │  │
│  Tool 2  [Running]  ─────┘  Tool 2  [Done]        │  │
│  Tool 3  [Queued]  ─────┘   Tool 3  [Done]        │  │
│                              Then execute         │  │
│          New Turn             follow-up ──────────┘  │
│       (Steering applied)                             │
│                                                      │
│       Subsequent Turn                               │
│       (Follow-up message queued)                    │
└─────────────────────────────────────────────────────────┘
```

---

## 📊 Multi-Interface Architecture（多接口架构）

### 9. 四种运行模式

```
User Input
    │
    ├──→ Interactive Mode (Default)
    │    └─ TUI rendering in terminal
    │       └─ Full session navigation
    │       └─ Real-time tool output
    │
    ├──→ Print/JSON Mode
    │    └─ pi -p "query"  (print mode)
    │    └─ pi --mode json (JSON events)
    │       └─ Scripting, pipelines
    │
    ├──→ RPC Mode
    │    └─ JSON protocol via stdin/stdout
    │    └─ Language-agnostic integration
    │       └─ Command/response streaming
    │
    └──→ SDK/Programmatic
         └─ Embedded in Node.js apps
         └─ Direct API access
            └─ Full control and customization
```

### 10. Extension System（扩展系统）

**Extension 可以注册**：
```typescript
interface ExtensionFactory {
  // Tools
  registerTool(tool: ToolDefinition): void
  
  // Commands (slash commands like /reload, /model)
  registerCommand(name: string, handler: CommandHandler): void
  
  // Keyboard shortcuts
  registerShortcut(key: string, handler: ShortcutHandler): void
  
  // Event listeners
  addEventListener(event: string, handler: EventHandler): void
  
  // Custom renderers
  registerRenderer(type: string, renderer: Renderer): void
  
  // Providers (new LLM models)
  registerProvider(provider: ProviderDefinition): void
}
```

**Extension 执行权限**：
- 在 Pi 进程内执行
- 继承 Pi 进程的 OS 权限
- 可访问 tools、commands、events、TUI
- 运行时加载和热重载

---

## 🔐 权限和安全模型

### 11. Permission Architecture

```
┌──────────────────────────────────────────────────┐
│        User Runs: pi /path/to/project           │
│        (with uid=1000, gid=1000)                │
└────────────────────┬─────────────────────────────┘
                     │
        ┌────────────▼──────────────┐
        │  Project Trust Check      │
        │  ├─ Is project trusted?   │
        │  └─ Load AGENTS.md?       │
        └────────────┬──────────────┘
                     │
        ┌────────────▼──────────────┐
        │  Extensions Load          │
        │  (Inside Pi process)      │
        │  └─ Run as uid=1000       │
        └────────────┬──────────────┘
                     │
        ┌────────────▼──────────────┐
        │  Tool Execution          │
        │  ├─ run: uid=1000        │
        │  ├─ edit: uid=1000       │
        │  ├─ read: uid=1000       │
        │  └─ custom: uid=1000     │
        └────────────┬──────────────┘
```

**默认模型的局限**：
- ⚠️ 无文件系统限制
- ⚠️ 无网络隔离
- ⚠️ 无进程隔离
- ⚠️ 凭证访问无限制

**隔离方案**（可选）：
```
方案 1: Gondolin 扩展
├─ 工具运行在 Linux micro-VM
├─ 保留主机认证
└─ 强边界隔离

方案 2: Docker 容器
├─ 整个 Pi 进程容器化
├─ 挂载工作目录
└─ 简单隔离

方案 3: OpenShell 沙箱
├─ 策略控制执行
├─ 细粒度权限
└─ 性能友好
```

---

## 🔗 与其他模块的集成

### 12. Agent-Core 依赖关系

```
agent-core
├─ pi-ai (LLM 协调)
│   ├─ OpenAI API
│   ├─ Anthropic API
│   ├─ Google API
│   └─ 15+ 其他供应商
│
├─ pi-durable (持久化)
│   ├─ Session 存储
│   ├─ 消息序列化
│   └─ 树状结构管理
│
├─ Chord (组合运行时)
│   ├─ 服务组合
│   ├─ 状态管理
│   └─ RPC 通信
│
└─ Extension System
    ├─ 自定义工具
    ├─ 事件处理
    └─ 上下文注入
```

---

## 💡 关键创新点总结

| 创新点 | 价值 | 实现方式 |
|-------|------|---------|
| **树状会话** | 分支探索、无损回溯 | JSONL + 父指针 |
| **延迟加载** | 节省 token、加快响应 | 技能描述 + 按需展开 |
| **自适应压缩** | 支持长对话 | 智能摘要 + 原数据保留 |
| **工具系统** | 可扩展、自定义能力 | 动态加载、运行时注册 |
| **多接口** | 灵活集成 | CLI、RPC、SDK、TUI |
| **扩展架构** | 无限定制 | TypeScript 模块系统 |
| **明确安全边界** | 透明权限模型 | 文档化、可选隔离 |

---

## 🚀 最佳实践

1. **使用会话树**：不要删除分支，利用回溯功能
2. **定义 AGENTS.md**：项目级指导，自动加载
3. **实现自定义工具**：扩展而非修改核心
4. **监控上下文大小**：观察压缩触发时机
5. **审查扩展代码**：安全执行权限高
6. **使用隔离容器**：处理不信任的代码

