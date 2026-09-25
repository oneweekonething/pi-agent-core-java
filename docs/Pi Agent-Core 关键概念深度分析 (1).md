# Pi Agent-Core 关键概念深度分析

## 📌 一、会话（Session）体系

### 1.1 会话树的演进

```
初始状态 (新会话):
  entry-1 (user: "build webapp")
    ↓
  entry-2 (assistant: "I'll help...")
    ↓
  entry-3 (tool-result: success)
    ↓
  entry-4 (user: "add tests")   ← Active Branch Tip


分支操作后 (fork from entry-2):
  entry-1 (user)
    ↓
  entry-2 (assistant) ─────────┬─→ entry-3 (tool-result)
                               │       ↓
                               │   entry-4 (user)
                               │       ↓
                               │   entry-5 (assistant) ← Fork point
                               │
                               └─→ entry-6 (user: "use different approach")
                                       ↓
                                   entry-7 (assistant)
                                       ↓
                                   entry-8 (user) ← Alternative Branch
```

**会话文件内容示例**：
```jsonl
{"id":"e1","type":"user","content":"build webapp","timestamp":"2024-01-15T10:00:00Z"}
{"id":"e2","type":"assistant","content":"I'll create...","toolUse":[{"id":"t1","name":"run"}],"timestamp":"2024-01-15T10:00:05Z"}
{"id":"e3","type":"tool-result","toolUseId":"t1","content":"✓ created","isError":false,"timestamp":"2024-01-15T10:00:06Z"}
{"id":"e4","parentId":"e3","type":"user","content":"add tests","timestamp":"2024-01-15T10:00:10Z"}
{"id":"e5","parentId":"e3","type":"user","content":"use different approach","timestamp":"2024-01-15T10:01:00Z"}
```

### 1.2 会话元数据

```
Session Metadata:
{
  currentEntry: "e8",           // 当前活跃分支的末端
  branches: [
    {
      name: "main",
      tip: "e4",                // 主分支末端
      created: "2024-01-15T10:00:00Z"
    },
    {
      name: "alternative",
      tip: "e8",                // 替代分支末端
      created: "2024-01-15T10:01:00Z"
    }
  ],
  compactions: [
    {
      id: "compact-1",
      createdAt: "2024-01-15T10:30:00Z",
      summary: "Discussed requirements and architecture...",
      replacedEntries: ["e1", "e2", "e3"]
    }
  ]
}
```

### 1.3 分支操作

| 操作 | 作用 | 结果 |
|------|------|------|
| **Continue** | 从当前末端继续 | 线性延伸 |
| **Branch** | 从当前点分岔 | 创建新分支 |
| **Fork** | 从任意点复制部分历史 | 创建独立会话 |
| **Rewind** | 回到某个历史点 | 改变活跃分支末端 |
| **Delete Branch** | 删除某分支 | 保留历史（实际上不删除） |

---

## 🔄 二、工具系统深度解析

### 2.1 工具生命周期

```
工具定义阶段 (Tool Definition):
┌─────────────────────────────────────────────┐
│ Tool Definition Object                       │
│ {                                            │
│   name: "run",                              │
│   description: "Execute shell commands",    │
│   parameters: {                             │
│     type: "object",                         │
│     properties: {                           │
│       command: { type: "string" },         │
│       cwd: { type: "string" },             │
│       env: { type: "object" }              │
│     },                                      │
│     required: ["command"]                  │
│   }                                         │
│ }                                            │
└─────────────────────────────────────────────┘
                    ↓
工具注册阶段 (Tool Registration):
┌─────────────────────────────────────────────┐
│ Extension provides tool to Agent            │
│ agent.registerTool(toolDef, handler)        │
│                                             │
│ Tool Registry Map:                          │
│ "run" → RunCommandHandler                   │
│ "edit" → EditFileHandler                    │
│ "read" → ReadFileHandler                    │
│ "custom" → CustomHandler (from extension)   │
└─────────────────────────────────────────────┘
                    ↓
LLM请求阶段 (LLM Request):
┌─────────────────────────────────────────────┐
│ Tool definitions sent to LLM                │
│ LLM chooses which tools to call             │
│ LLM generates tool_use messages with:       │
│  - toolUseId: unique ID                     │
│  - toolName: registered name                │
│  - parameters: validated against schema     │
└─────────────────────────────────────────────┘
                    ↓
工具执行阶段 (Tool Execution):
┌─────────────────────────────────────────────┐
│ For each tool call in LLM response:         │
│ 1. Lookup handler by toolName               │
│ 2. Validate parameters against schema       │
│ 3. Check OS permissions (uid, gid)          │
│ 4. Execute handler function                 │
│    - Capture stdout/stderr                  │
│    - Capture exit code                      │
│    - Handle timeouts (default: 5min)        │
│    - Catch exceptions                       │
│ 5. Serialize result                         │
└─────────────────────────────────────────────┘
                    ↓
结果记录阶段 (Result Recording):
┌─────────────────────────────────────────────┐
│ Tool Result Entry (in Session):             │
│ {                                            │
│   type: "tool-result",                      │
│   toolUseId: "call-123",                    │
│   content: "output text",                   │
│   isError: false,                           │
│   timestamp: "2024-01-15T10:00:06Z"         │
│ }                                            │
└─────────────────────────────────────────────┘
                    ↓
反馈阶段 (Feedback to LLM):
┌─────────────────────────────────────────────┐
│ Tool result sent to LLM in next turn        │
│ LLM can:                                    │
│ - Analyze result                            │
│ - Call more tools                           │
│ - Provide response to user                  │
│ - Ask for clarification                     │
└─────────────────────────────────────────────┘
```

### 2.2 内置工具集

| 工具 | 功能 | 权限 | 超时 |
|------|------|------|------|
| **run** | 执行 shell 命令 | OS permissions | 5 min |
| **read** | 读取文件内容 | OS permissions | 1 min |
| **edit** | 编辑/创建文件 | OS permissions | 2 min |
| **list** | 列出目录 | OS permissions | 1 min |
| **find** | 搜索文件 | OS permissions | 2 min |
| **grep** | 搜索文本 | OS permissions | 2 min |
| **stat** | 文件信息 | OS permissions | 30 sec |

### 2.3 扩展工具示例

```typescript
// Extension registering custom tool

export const myExtension = (agent: Agent) => {
  // 定义工具
  const myTool = {
    name: "analyze-code",
    description: "Analyze TypeScript code with custom rules",
    parameters: {
      type: "object",
      properties: {
        filePath: { type: "string" },
        rules: { type: "array", items: { type: "string" } }
      },
      required: ["filePath"]
    }
  };

  // 注册工具
  agent.registerTool(myTool, async (params) => {
    try {
      // 读取文件
      const code = fs.readFileSync(params.filePath, 'utf-8');
      
      // 应用规则
      const results = applyRules(code, params.rules || []);
      
      return {
        content: JSON.stringify(results),
        isError: false
      };
    } catch (error) {
      return {
        content: error.message,
        isError: true
      };
    }
  });
};
```

---

## 🧠 三、上下文管理策略

### 3.1 上下文优先级

```
Context Assembly Priority:

Tier 1 - Required (must fit):
  ┌─────────────────────────┐
  │ System Prompt           │ ~500 tokens
  │ (minimal base prompt)   │
  └─────────────────────────┘

Tier 2 - Project Context:
  ┌─────────────────────────┐
  │ AGENTS.md               │ ~2000 tokens (optional)
  │ (project instructions)  │
  └─────────────────────────┘

Tier 3 - Conversation:
  ┌─────────────────────────┐
  │ Message History         │ ~3000-5000 tokens
  │ (compacted if needed)   │
  └─────────────────────────┘

Tier 4 - Tools & Skills:
  ┌─────────────────────────┐
  │ Tool Definitions        │ ~1000 tokens
  │ Tool Descriptions       │
  │ Skill Summaries         │ (on-demand)
  └─────────────────────────┘

Tier 5 - Loaded Skills:
  ┌─────────────────────────┐
  │ Full Skill Instructions │ ~1000-3000 tokens
  │ (only if requested)     │ (loaded on-demand)
  └─────────────────────────┘

Tier 6 - Extensions:
  ┌─────────────────────────┐
  │ Extension Context       │ ~500-2000 tokens
  │ (RAG results, custom)   │
  └─────────────────────────┘
```

### 3.2 动态上下文注入（DCE）

```typescript
// Extension implementing dynamic context

export const ragExtension = (agent: Agent) => {
  agent.addEventListener('beforeModelRequest', async (context) => {
    // 基于当前消息检索相关文档
    const query = context.lastMessage.content;
    const results = await retrieveRelevantDocs(query);
    
    // 注入上下文
    context.prependMessage({
      role: 'user',
      content: `Relevant documentation:\n${results}`
    });
  });
};
```

### 3.3 压缩（Compaction）机制

```
Compaction Trigger:
├─ Token count > context limit
├─ Automatic summarization
└─ Preserves important details

Compaction Process:

Before:
┌─────────────────────────────────┐
│ Turn 1: Initial request (old)   │
│ Turn 2: Response (old)          │ ← Can be compressed
│ Turn 3: Tool call (old)         │
│ Turn 4: Tool result (old)       │
│ Turn 5: Follow-up (recent)      │ ← Keep
│ Turn 6: Current turn (recent)   │ ← Keep
└─────────────────────────────────┘

Compaction Algorithm:
1. Identify old turns (> N turns ago)
2. Generate summary:
   - What was requested
   - What was accomplished
   - Key decisions made
3. Create compaction entry with summary
4. Update session with replacement mapping
5. Subsequent requests use compaction entry

After Compaction:
┌─────────────────────────────────┐
│ [COMPACTION] "User requested    │
│ feature X. Discussed approach   │
│ and implemented solution Y.     │ ← Replaces 4 turns
│ Tests passed."                  │
├─────────────────────────────────┤
│ Turn 5: Follow-up (recent)      │
│ Turn 6: Current turn (recent)   │
└─────────────────────────────────┘

Original entries NOT deleted:
- Preserved in session file
- Can navigate back via /tree
- Can create new branches from old points
```

---

## 🎛️ 四、模型请求构造

### 4.1 系统提示构造

```
System Prompt Assembly:

1. Base System Prompt (static):
   "You are Pi, a coding agent..."
   
2. Apply SYSTEM.md overrides:
   if SYSTEM.md exists:
     if starts with "# ":
       use as complete override
     else:
       append to base prompt
   
3. Add Tool Catalog:
   "You have access to these tools:
    - run: execute commands
    - read: read files
    - edit: edit files
    ..."
   
4. Add Skill Catalog:
   "Capabilities available:
    - python (summarized)
    - web-dev (summarized)
    ..."
   
5. Add Instructions Injection:
   from extensions, if any

Final System Prompt:
+──────────────────────────────────+
│ Base + Overrides + Catalogs      │
│ (varies based on context)        │
+──────────────────────────────────+
```

### 4.2 消息历史构造

```
Message Construction from Session:

Session JSONL:
{"id":"e1","type":"user","content":"build"}
{"id":"e2","type":"assistant","content":"ok"}
{"id":"e3","type":"tool-result",...}

↓ Transform to ↓

Model Messages Format:
[
  {
    "role": "user",
    "content": "build"
  },
  {
    "role": "assistant",
    "content": "ok",
    "toolUse": [{"id":"t1","name":"run","parameters":{...}}]
  },
  {
    "role": "tool",
    "toolUseId": "t1",
    "content": "output"
  }
]

Rules:
- Compaction entries → summarize to user message
- Tool results → must follow corresponding tool_use
- Images/files → converted to model-specific format
- Tree navigation → reconstruct active branch
```

### 4.3 请求参数配置

```typescript
interface ModelRequest {
  // Required
  model: string;                    // "gpt-4", "claude-3-opus"
  messages: Message[];              // conversation history
  system?: string;                  // system prompt
  tools?: ToolDefinition[];         // available tools
  
  // Optional - Model Settings
  temperature?: number;             // 0-2 (default: 1.0)
  maxTokens?: number;              // output token limit
  topP?: number;                    // nucleus sampling
  frequencyPenalty?: number;        // repetition penalty
  presencePenalty?: number;         // diversity boost
  
  // Model-specific
  stream?: boolean;                 // enable streaming
  stopSequences?: string[];         // stop conditions
}
```

---

## 🔌 五、多模式集成架构

### 5.1 四种运行模式详解

```
Mode 1: Interactive (Default)
┌────────────────────────────────┐
│ Terminal UI                     │
│ ├─ Rich rendering              │
│ ├─ Real-time updates           │
│ ├─ Keyboard shortcuts          │
│ ├─ Session navigation (/tree)  │
│ └─ Full tool output display    │
└────────────────────────────────┘
Usage: pi
       pi /some/project


Mode 2: Print Mode
┌────────────────────────────────┐
│ Standard Output                 │
│ ├─ Plain text response         │
│ ├─ No TUI rendering            │
│ ├─ Scriptable                  │
│ └─ Pipeline friendly           │
└────────────────────────────────┘
Usage: pi -p "task"
       pi -p "task" | grep pattern


Mode 3: JSON Mode
┌────────────────────────────────┐
│ JSONL Event Stream              │
│ ├─ Each event = line           │
│ ├─ Machine readable            │
│ ├─ Complete logging            │
│ ├─ Programmatic parsing        │
│ └─ Tool results captured       │
└────────────────────────────────┘
Usage: pi --mode json "task"
Output:
{"type":"start","timestamp":"..."}
{"type":"message","role":"assistant","content":"..."}
{"type":"tool-call","name":"run","parameters":{...}}
{"type":"tool-result","content":"..."}
{"type":"done","timestamp":"..."}


Mode 4: RPC Mode
┌────────────────────────────────┐
│ JSON Protocol (stdin/stdout)    │
│ ├─ Language-agnostic           │
│ ├─ Bidirectional               │
│ ├─ Command/Response            │
│ ├─ Event streaming             │
│ └─ Full control                │
└────────────────────────────────┘
Usage: python script | pi --mode rpc
Commands:
{"command":"startSession","model":"gpt-4"}
{"command":"message","content":"build app"}
{"command":"continue"}


Mode 5: SDK (Programmatic)
┌────────────────────────────────┐
│ TypeScript/Node.js APIs         │
│ ├─ Embed in applications       │
│ ├─ Direct API calls            │
│ ├─ Full session control        │
│ ├─ Event subscriptions         │
│ └─ Custom logic integration    │
└────────────────────────────────┘
Usage:
const agent = new PiAgent(config);
const session = agent.createSession();
await session.addMessage("task");
const response = await session.run();
```

### 5.2 模式适用场景

| 模式 | 用途 | 优势 | 限制 |
|------|------|------|------|
| Interactive | 日常开发 | 完整功能、实时反馈 | 需要终端 |
| Print | 脚本集成 | 简单、文本处理 | 无交互 |
| JSON | 日志分析 | 完整信息、可解析 | 需要解析 |
| RPC | 跨语言集成 | 通用、隔离 | 通信开销 |
| SDK | 应用嵌入 | 灵活、完全控制 | 只支持 Node.js |

---

## 🛡️ 六、安全模型深度分析

### 6.1 权限继承模型

```
User Space:
  $ pi /project
  (running as: uid=1000, gid=1000)
         │
         ▼
Pi Process:
  ├─ UID: 1000 (inherited)
  ├─ GID: 1000 (inherited)
  ├─ Home: /home/user
  ├─ Env vars: inherited
  └─ Capabilities: inherited
         │
         ├─→ Extensions
         │   └─ Execute in process
         │      └─ Same uid/gid
         │
         └─→ Tools (run command)
             └─ Execute shell
                └─ Same uid/gid
                └─ Full filesystem access
                └─ Full network access
```

### 6.2 信任模型

```
Project Trust Chain:

1. User Executes: pi /path/to/project

2. Search for AGENTS.md:
   - ~/.pi/agent/AGENTS.md (home)
   - /path/to/parent/AGENTS.md (parents)
   - /path/to/project/AGENTS.md (project)

3. Trust Check:
   - Is project in trusted list?
   - Is AGENTS.md signed?
   - Does user confirm loading?

4. Load Decision:
   - Trust = load AGENTS.md
   - Untrust = skip instructions
   - Ask = prompt user

5. Extension Loading:
   - Enabled extensions loaded
   - Execute in Pi process
   - Inherit permissions
```

### 6.3 隔离方案对比

```
┌─────────────────┬──────────────┬──────────────┬──────────────┐
│ 特性            │ Gondolin     │ Docker       │ OpenShell    │
├─────────────────┼──────────────┼──────────────┼──────────────┤
│ 隔离层           │ Micro-VM     │ Container    │ Policy       │
├─────────────────┼──────────────┼──────────────┼──────────────┤
│ 文件系统         │ 隔离         │ 挂载点       │ 策略过滤     │
├─────────────────┼──────────────┼──────────────┼──────────────┤
│ 网络             │ 隔离         │ 自定义       │ 策略过滤     │
├─────────────────┼──────────────┼──────────────┼──────────────┤
│ 认证保留         │ ✅ Yes       │ ❌ No        │ ✅ Yes       │
├─────────────────┼──────────────┼──────────────┼──────────────┤
│ 性能             │ 好           │ 中等         │ 最好         │
├─────────────────┼──────────────┼──────────────┼──────────────┤
│ 学习曲线         │ 陡峭         │ 平缓         │ 陡峭         │
├─────────────────┼──────────────┼──────────────┼──────────────┤
│ 配置复杂度       │ 中等         │ 简单         │ 复杂         │
└─────────────────┴──────────────┴──────────────┴──────────────┘
```

---

## 🚀 七、优化和性能考虑

### 7.1 上下文窗口优化

```
Strategy 1: Lazy Skill Loading
Before: 80KB (all skills) + query
After: 20KB (skill summaries) + query

Benefit: 60% reduction

Strategy 2: Compaction
Turn 1-10: 50KB
Turn 11: Compaction triggers
After: 8KB (summary) + Turn 11-20: 30KB
Total: 38KB (24% reduction)

Strategy 3: Message Filtering
Options:
- Only recent N messages
- Only tool calls & results
- Only text, drop tool details
Benefit: 30-50% reduction

Strategy 4: Dynamic Loading
- Load full skill only when needed
- Pre-load frequently used skills
- Cache skill instructions
Benefit: 40% average reduction
```

### 7.2 执行性能指标

```
Typical Latencies (real-world):

Initial Setup:
  Parse input: 10-50ms
  Load context: 50-200ms
  Build request: 20-50ms
  Total: ~100-300ms

LLM Latency:
  Streaming first token: 500-2000ms
  Full response: 2-10 seconds

Tool Execution:
  File operations: 10-100ms
  Shell commands: 100ms-30s (variable)
  Network calls: 100ms-10s (variable)

Total Typical Workflow:
  Simple query: 3-5 seconds
  Complex with tools: 10-30 seconds
  Multi-step tasks: 30-300 seconds
```

---

## 💡 八、扩展系统的高级模式

### 8.1 扩展能力矩阵

```
Tool Extensions
├─ Custom commands (DSL-specific)
├─ External service integrations
└─ Specialized analysis tools

Event Listeners
├─ beforeModelRequest (inject context)
├─ afterModelRequest (process response)
├─ beforeToolCall (validate/log)
├─ afterToolCall (transform result)
└─ onMessageQueued (custom handling)

Commands
├─ Slash commands (/analyze, /deploy)
├─ Keyboard shortcuts (Ctrl+X)
└─ Custom workflows

Renderers
├─ Custom message formatting
├─ Tool result visualization
├─ Status display customization
└─ TUI overlays

Providers
├─ New LLM models
├─ Custom model services
└─ Model-specific settings
```

### 8.2 扩展权限检查

```typescript
// Extension permission model

export const checkExtensionPermissions = (ext: Extension) => {
  const permissions = {
    // File access
    canReadFiles: ext.tools.includes('read'),
    canWriteFiles: ext.tools.includes('edit'),
    canListDirs: ext.tools.includes('list'),
    
    // Process access
    canExecuteShell: ext.tools.includes('run'),
    
    // Network access
    canMakeRequests: ext.hasNetworkCalls,
    
    // Credential access
    canReadEnv: ext.readsEnvironment,
    canReadCredentials: ext.readsSecrets,
    
    // Context access
    canReadHistory: ext.usesMessageHistory,
    canInjectContext: ext.hasEventListeners,
    
    // UI access
    canModifyUI: ext.hasRenderers,
    canAddCommands: ext.registersCommands
  };
  
  return permissions;
};
```

---

## 📚 总结表：Agent-Core 核心设计对比

| 维度 | Pi Agent-Core | 传统 Chatbot |
|------|--------------|-------------|
| **会话存储** | 树状结构 JSONL | 线性数组 |
| **分支支持** | ✅ 原生支持 | ❌ 不支持 |
| **上下文管理** | 自适应压缩 | 固定窗口 |
| **技能加载** | 按需懒加载 | 全量加载 |
| **工具系统** | 动态可扩展 | 硬编码或插件 |
| **权限模型** | 明确文档化 | 隐式或全量 |
| **运行模式** | 5 种（CLI、RPC、SDK 等） | 通常 1-2 种 |
| **扩展机制** | TypeScript 模块 | 受限或专有 |
| **安全隔离** | 可选多层方案 | 通常不支持 |

