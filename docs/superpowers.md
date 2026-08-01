# Superpowers — AI 编码智能体的软件开发方法论

## 概述

Superpowers 是一套完整的软件开发方法论，以插件形式为 AI 编码智能体提供技能套件。由 [Jesse Vincent](https://blog.fsck.com) 及 [Prime Radiant](https://primeradiant.com) 团队构建，MIT 许可证开源（https://github.com/obra/superpowers）。

**核心理念：** 系统化优于临时应对。先写测试再写代码。用证据代替声称。降低复杂度是首要目标。

Superpowers 通过在每次会话启动时注入 `using-superpowers` 引导指令来工作——智能体在采取任何行动之前，必须检查并触发相关技能。这不是建议，而是强制执行的工作流。

**当前版本：** 6.2.0（2026-07-23 发布）

## 设计哲学

- **测试驱动开发**——始终先写测试
- **系统化优于临时应对**——流程优于猜测
- **降低复杂度**——简洁是首要目标
- **证据优于声称**——先验证再声称成功

## 安装

### Claude Code（官方插件市场）

```bash
# 方式一：直接从官方市场安装
/plugin install superpowers@claude-plugins-official

# 方式二：通过 Superpowers 市场安装
/plugin marketplace add obra/superpowers-marketplace
/plugin install superpowers@superpowers-marketplace
```

安装后无需任何手动配置——插件的 SessionStart 钩子会自动将引导指令注入每个会话。

### 其他编码智能体

Superpowers 支持 11 个编码智能体平台：

| 智能体                 | 安装命令                                                                                                                        |
|------------------------|---------------------------------------------------------------------------------------------------------------------------------|
| **Antigravity**        | `agy plugin install https://github.com/obra/superpowers`                                                                        |
| **Codex App**          | 在应用内 Plugins 中搜索 "Superpowers"，点击 `+`                                                                                 |
| **Codex CLI**          | `/plugins` → 搜索 `superpowers` → Install Plugin                                                                                |
| **Cursor**             | `/add-plugin superpowers`                                                                                                       |
| **Factory Droid**      | `droid plugin marketplace add https://github.com/obra/superpowers` 然后 `droid plugin install superpowers@superpowers`          |
| **Gemini CLI**         | `gemini extensions install https://github.com/obra/superpowers`                                                                 |
| **GitHub Copilot CLI** | `copilot plugin marketplace add obra/superpowers-marketplace` 然后 `copilot plugin install superpowers@superpowers-marketplace` |
| **Kimi Code**          | `/plugins` → Marketplace → Superpowers → 安装                                                                                   |
| **OpenCode**           | 让 OpenCode 访问并执行 `https://raw.githubusercontent.com/obra/superpowers/refs/heads/main/.opencode/INSTALL.md`                |
| **Pi**                 | `pi install git:github.com/obra/superpowers`                                                                                    |

---

## 完整技能列表（14 个技能）

### 流程技能

#### 1. `brainstorming`（头脑风暴）

**触发条件：** 任何创造性工作之前必须使用。构建功能、创建组件、添加功能、修改行为之前自动触发。

**功能：**

- 通过一次一个的澄清问题探索用户意图
- 提出 2-3 种方案并分析各自的权衡
- 以分段形式呈现设计方案供验证
- 将设计文档写入 `docs/superpowers/specs/YYYY-MM-DD-<topic>-design.md`
- 执行自我审查
- 包含 `<HARD-GATE>`——设计批准前禁止所有实现

**附带工具：**

- **Visual Companion（视觉伴侣）：** 可选的零依赖 Node.js 浏览器服务器，用于展示原型、图表和并排对比。会话密钥认证，`start-server.sh` 启动，`stop-server.sh` 停止。

#### 2. `writing-plans`（编写计划）

**触发条件：** 当你有经过批准的设计规范或需求，准备编码前自动触发。

**功能：**

- 将批准的设计规范转化为详细的实现计划
- 每个任务仅需 2-5 分钟，包含精确的文件路径、代码片段和验证步骤
- 严格执行 TDD、YAGNI、DRY 原则
- 计划包含全局约束块（项目级规则）和接口块（每个任务的输入/输出）
- 自我审查（规范覆盖扫描、占位符检查、类型一致性）
- 完成后提供两种执行路径：Subagent-Driven（推荐）或 Inline Execution

**输出位置：** `docs/superpowers/plans/YYYY-MM-DD-<feature-name>.md`

#### 3. `subagent-driven-development`（子智能体驱动开发）

**触发条件：** 当有了计划后，推荐的主要执行引擎。

**功能：**

- 每个任务分派一个全新的子智能体执行
- **两阶段审查：** 规范合规审查 → 代码质量审查
- 核心循环：分派实现者 → 接收状态报告 → 生成审查包 → 分派任务审查者 → 如有发现则进入修复循环（最多 5 轮）
- 基于账本的进度跟踪
- 最终整体分支审查
- 使用计划范围工作区 `.superpowers/sdd/<plan-basename>/`

**模型选择指引：**

- 机械性任务 → 廉价模型
- 集成任务 → 标准模型
- 架构/审查 → 最强模型

#### 4. `executing-plans`（内联执行计划）

**触发条件：** 当编码智能体不支持子智能体时，作为 `subagent-driven-development` 的替代方案。

**功能：** 在当前会话中按顺序执行所有任务，遇到阻塞时暂停。会告知用户使用子智能体效果更好。

#### 5. `test-driven-development`（测试驱动开发）

**触发条件：** 实现过程中自动触发。

**功能：**

- **铁律：没有失败的测试，就不许写生产代码**
- 严格的 RED-GREEN-REFACTOR 循环
- 在测试之前编写的代码必须删除
- 包含 13+ 个常见借口的反驳表（"这太简单了不需要测试"、"测试框架还没搭建"等）
- 包含红牌（Red Flags）列表

**附带文件 `writing-good-tests.md`：** 六条诚实测试规则：

1. 明确指出要捕获的失败
2. 独立推导期望值
3. 测试行为而非文本
4. 在恰当的层级进行模拟
5. 测试失败路径和成功路径
6. 变异检查门控（修改代码确认测试能捕获）

#### 6. `systematic-debugging`（系统化调试）

**触发条件：** 遇到 bug、错误、崩溃时自动触发。

**功能：**

- **铁律：在定位根因之前不许修复**
- **四阶段流程：**
    1. **根因调查：** 阅读错误、复现问题、检查最近变更、收集证据、追踪数据流
    2. **模式分析：** 找到正常工作的示例，进行比较
    3. **假设与测试：** 形成单一假设，最小化测试
    4. **实现：** 创建失败测试，修复根因，验证
- 如果 3 次以上修复失败 → 质疑架构设计

**附带技术文档：**

- `root-cause-tracing.md`：反向追踪调用栈
- `defense-in-depth.md`：在每一层进行验证
- `condition-based-waiting.md`：用条件轮询替代任意超时等待

#### 7. `verification-before-completion`（完成前验证）

**触发条件：** 声称任何工作完成之前自动触发。

**功能：**

- **铁律：没有新鲜验证证据，不许声称完成**
- 门控流程：识别验证命令 → 重新运行 → 阅读完整输出 → 确认输出验证了声称 → 然后才可声称
- 禁止在验证前使用"应该"、"可能"、"似乎"等措辞
- 必须在提交前、创建 PR 前、任务完成前、进入下一任务前以及任何成功声明前应用

#### 8. `dispatching-parallel-agents`（分派并行智能体）

**触发条件：** 面对 2 个以上独立任务且无共享状态或顺序依赖时。

**功能：**

- 识别独立领域 → 创建专注的智能体任务 → 并行分派 → 审查并整合
- 记录常见错误：任务过宽、缺少上下文、无约束条件

#### 9. `requesting-code-review`（请求代码审查）

**触发条件：** 任务之间、合并前。

**功能：**

- 分派代码审查子智能体，防止问题级联
- 检查维度：计划对齐、代码质量、架构、测试、生产就绪
- 返回结果：优势、问题（严重/重要/次要）、建议、评估（可以合并？是/否/修复后）

#### 10. `receiving-code-review`（接收代码审查）

**触发条件：** 收到审查反馈后。

**功能：**

- 技术评估，非情绪化回应
- **禁止表演性赞同**（"你说得太对了！"、"好观点！"）
- 正确响应模式：阅读反馈 → 理解 → 对照代码库验证 → 评估 → 响应/实现
- 外部反馈在实现前必须验证
- YAGNI 检查：对"专业"的功能建议进行过滤
- GitHub 讨论回复必须在对应评论线程中，不可作为顶级 PR 评论

#### 11. `using-git-worktrees`（使用 Git 工作树）

**触发条件：** 设计批准后，开始实现前。

**功能：**

- 确保工作在隔离的 Git 工作树中
- Step 0：检测已有隔离（检查 `GIT_DIR` vs `GIT_COMMON`，子模块防护）
- Step 1：优先尝试原生工作树工具（如 `EnterWorktree`），回退到 `git worktree add`
- Step 2：运行项目设置
- Step 3：验证干净的测试基线
- 默认工作树目录：项目根下的 `.worktrees/`

#### 12. `finishing-a-development-branch`（完成开发分支）

**触发条件：** 所有任务完成时。

**功能：**

- Step 1：验证测试通过
- Step 2：检测环境（普通仓库 / 命名分支工作树 / 分离 HEAD）
- Step 3：确定基础分支
- Step 4：呈现选项——（1）本地合并，（2）推送并创建 PR，（3）保持原样。仅当用户明确要求并输入确认文本时才可丢弃分支
- Step 5：执行选择
- Step 6：清理（仅限 Superpowers 创建的工作树）

### 元技能

#### 13. `writing-skills`（编写技能）

**触发条件：** 创建或修改技能时。

**附带文件：**

- `anthropic-best-practices.md`：Anthropic 技能编写最佳实践
- `testing-skills-with-subagents.md`：用子智能体测试技能
- `persuasion-principles.md`：说服原则（权威、承诺、稀缺、社会认同、统一）
- `examples/CLAUDE_MD_TESTING.md`

**功能：**

- 应用于流程文档的 TDD：
    - **RED 阶段：** 在无技能的情况下运行压力场景，记录失败和借口
    - **GREEN 阶段：** 编写技能解决那些具体失败
    - **REFACTOR 阶段：** 封堵漏洞，构建借口反驳表
- 指导：SKILL.md 结构、YAML 前置元数据要求（name + description，最多 1024 字符）、技能发现优化（SDO——描述仅陈述触发条件，绝不总结工作流）、流程图使用、代码示例、匹配形式与失败的对照表
- 个人技能存放在 `~/.claude/skills/`

#### 14. `using-superpowers`（使用 Superpowers）

**触发条件：** 每次会话开始时自动注入。

**功能：**

- 确立规则： **在任何响应或行动之前必须调用相关技能**——包括澄清问题
- 包含"红旗"借口反驳表（见下方）
- 包含 `<SUBAGENT-STOP>` 块，防止子智能体加载引导指令
- 定义技能优先级：流程技能优先，实现技能其次

---

## 核心工作流

```
头脑风暴 → Git 工作树 → 编写计划 → 子智能体驱动开发+ TDD → 请求代码审查 → 完成开发分支
```

1. **brainstorming** 在写代码前激活——提问澄清、探索替代方案、呈现设计
2. **using-git-worktrees** 创建隔离工作区和新分支
3. **writing-plans** 将工作分解为 2-5 分钟的小任务
4. **subagent-driven-development**（或 executing-plans）逐任务分派并审查
5. **test-driven-development** 全程执行 RED-GREEN-REFACTOR
6. **requesting-code-review** 在任务之间对照计划审查工作
7. **finishing-a-development-branch** 验证、呈现选项（合并/PR/保留）、清理

智能体在 **任何任务之前**都会检查相关技能。这不是建议，而是强制工作流。

---

## 使用方式

**无需手动操作。** 插件的 SessionStart 钩子将引导指令加载到每个会话中。智能体自动检查并在采取行动前调用相关技能。无需输入 `/superpowers:xxx`——只需描述你想做什么：

- "构建一个登录系统" → 自动触发 brainstorming
- "修复这个崩溃" → 自动触发 systematic-debugging
- "审查我的代码" → 自动触发 requesting-code-review

技能通过 `Skill` 工具以命名空间形式调用（如 `superpowers:brainstorming`、`superpowers:systematic-debugging`）。`using-superpowers` 技能已预加载；其余技能按需加载。

---

## 注意事项

### 借口反驳（Red Flags）

`using-superpowers` 技能包含一个智能体常用借口的反驳表。当智能体想跳过技能时，这些借口都会被识别并驳回：

| 智能体的借口               | 现实                                     |
|----------------------------|------------------------------------------|
| "这只是一个简单问题"       | 问题就是任务。检查技能。                 |
| "我需要先了解更多上下文"   | 技能检查在澄清问题之前。                 |
| "让我先探索代码库"         | 技能告诉你如何探索。先检查。             |
| "我先快速查一下 git/files" | 文件缺少会话上下文。先检查技能。         |
| "让我先收集信息"           | 技能告诉你如何收集信息。                 |
| "这不适合正式的技能"       | 如果存在技能就用。                       |
| "我记得这个技能"           | 技能会演进。读取当前版本。               |
| "这不算是任务"             | 行动 = 任务。检查技能。                  |
| "技能是大材小用"           | 简单的事情会变复杂。使用它。             |
| "我先只做这一件事"         | 做任何事之前先检查。                     |
| "这感觉像是在高效工作"     | 不守纪律的行动浪费时间。技能能防止这点。 |
| "我知道那是什么意思"       | 知道概念 ≠ 使用技能。调用它。            |

### 技能描述原则（SDO — Skill Discovery Optimization）

技能描述必须 **仅陈述触发条件**，绝不总结工作流。这让智能体能够精确匹配技能和任务，而非被误导以为已经了解了技能内容。

### 平台兼容性

Superpowers 设计上跨平台工作。每个受支持平台有对应的工具映射文件：

- `references/codex-tools.md` — Codex 特定功能
- `references/gemini-tools.md` — Gemini 特定功能
- `references/antigravity-tools.md` — Antigravity 特定功能
- `references/pi-tools.md` — Pi 特定功能

### 遥测

Visual Companion 从 `primeradiant.com` 加载 logo，其中包含 Superpowers 版本号。 **不包含**项目细节、提示词或任何个人数据。设置 `SUPERPOWERS_DISABLE_TELEMETRY` 环境变量可完全关闭。Superpowers 也遵守 Claude Code 的 `DISABLE_TELEMETRY` 和 `CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC` 选择退出设置。

### 商业服务

企业用户如需商业支持、额外工具或托管消费，请联系：sales@primeradiant.com

---

## 资源链接

- GitHub：https://github.com/obra/superpowers
- 发布公告：https://blog.fsck.com/2025/10/09/superpowers/
- Discord 社区：https://discord.gg/35wsABTejz
- 发布通知（邮件）：https://primeradiant.com/superpowers/
- 评估工具：https://github.com/prime-radiant-inc/superpowers-evals/
