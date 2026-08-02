# gstack — AI 虚拟工程团队

## 概述

gstack 是 [Garry Tan](https://x.com/garrytan)（Y Combinator 总裁兼 CEO）打造的 Claude Code 技能套件，将 Claude Code 转变为一支虚拟工程团队——CEO、工程经理、设计师、QA 负责人、安全官、发布工程师，全部以 slash 命令形式呈现。23 个角色技能 + 8 个动力工具，全部采用 Markdown 编写，MIT 许可证开源。

gstack 不是一个代码补全工具，而是一套完整的 **软件开发流程**：思考 → 计划 → 构建 → 审查 → 测试 → 交付 → 复盘。每个技能都衔接下一个，形成闭环。

## 核心理念

gstack 遵循三条"构建者信条"(Builder Ethos)：

1. **Boil the Ocean（煮沸海洋）**——AI 将完备性的边际成本降至接近零。能用 150 行完整实现，就别用 80 行偷懒。写完整测试、覆盖全边缘情况、处理所有错误路径。将"大海"分解为一个个可以煮沸的"湖泊"逐个击破。

2. **Search Before Building（构建前先搜索）**——三层知识体系：
    - Layer 1：久经考验的模式（标准做法）
    - Layer 2：新兴流行的事物（需审视，易狂热）
    - Layer 3：第一性原则推导的洞见（最有价值）

3. **User Sovereignty（用户主权）**——AI 推荐，用户决策。两个模型达成一致也不是命令，用户永远拥有模型不具备的上下文。

## 安装

### 前置条件

- [Claude Code](https://docs.anthropic.com/en/docs/claude-code)
- [Git](https://git-scm.com/)
- [Bun](https://bun.sh/) v1.0+
- Node.js（仅 Windows）

### 步骤 1：安装至本机（30 秒）

在 Claude Code 中输入以下指令：

```
Install gstack: run git clone --single-branch --depth 1 https://github.com/garrytan/gstack.git ~/.claude/skills/gstack && cd ~/.claude/skills/gstack && ./setup
```

Claude 将自动完成安装。

### 步骤 2：团队模式（推荐）

在项目仓库内执行，让团队成员自动获取 gstack：

```bash
(cd ~/.claude/skills/gstack && ./setup --team) && ~/.claude/skills/gstack/bin/gstack-team-init required && git add .claude/ CLAUDE.md && git commit -m "require gstack for AI-assisted work"
```

- `required`：强制团队成员安装
- `optional`：仅建议

### 其他 AI 智能体支持

gstack 支持 10 个 AI 编码智能体：

| 智能体           | 安装标志          |
|------------------|-------------------|
| OpenAI Codex CLI | `--host codex`    |
| OpenCode         | `--host opencode` |
| Cursor           | `--host cursor`   |
| Factory Droid    | `--host factory`  |
| Slate            | `--host slate`    |
| Kiro             | `--host kiro`     |
| Hermes           | `--host hermes`   |
| GBrain (mod)     | `--host gbrain`   |

### 卸载

```bash
~/.claude/skills/gstack/bin/gstack-uninstall          # 完整卸载
~/.claude/skills/gstack/bin/gstack-uninstall --keep-state  # 保留配置和分析数据
```

---

## 完整技能列表

### 流程技能（按冲刺流程排列）

| 技能                     | 角色           | 功能                                                                                                                                                      |
|--------------------------|----------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------|
| `/office-hours`          | YC 办公时间    | **起点。** 六个强逼性问题，在写代码前重构你的产品思路。挑战你的假设，生成实现替代方案。输出设计文档供下游技能使用。                                       |
| `/plan-ceo-review`       | CEO / 创始人   | 重新思考问题。发现隐藏在需求背后的 10 星产品。四种模式：扩展、选择性扩展、保持范围、缩减。                                                                |
| `/plan-eng-review`       | 工程经理       | 锁定架构、数据流、图表、边缘案例和测试。将隐藏假设暴露出来。                                                                                              |
| `/plan-design-review`    | 资深设计师     | 对每个设计维度评分 0-10，解释 10 分长什么样，然后编辑计划达成目标。AI 垃圾检测。交互式——每个设计选择一个 AskUserQuestion。                                |
| `/plan-devex-review`     | 开发者体验主管 | 交互式 DX 审查：探索开发者画像，与竞品 TTHW（上手时间）对比，设计魔法时刻。三种模式：DX EXPANSION、DX POLISH、DX TRIAGE。20-45 个强逼性问题。             |
| `/design-consultation`   | 设计合伙人     | 从零构建设计系统。研究竞争格局，提出创意风险，生成真实产品原型。                                                                                          |
| `/review`                | 资深工程师     | 找到通过 CI 但在生产环境中崩溃的 bug。自动修复明显问题，标记完整性差距。                                                                                  |
| `/investigate`           | 调试器         | 系统化根因调试。铁律：不调查不复原因。追踪数据流，测试假设，3 次失败修复后停止。                                                                          |
| `/design-review`         | 懂代码的设计师 | 与 `/plan-design-review` 相同的审查，随后修复发现的问题。原子提交，修改前后截图对比。                                                                     |
| `/devex-review`          | DX 测试员      | 实时开发者体验审查。实际走通你的上手流程：浏览文档、尝试入门、计时 TTHW、截图错误。对比 `/plan-devex-review` 评分。                                       |
| `/design-shotgun`        | 设计探索者     | "给我看选项。"生成 4-6 个 AI 原型变体，在浏览器中打开对比面板，收集反馈并迭代。味觉记忆会学习你的偏好。                                                   |
| `/design-html`           | 设计工程师     | 将原型转化为可交付的 HTML/CSS。Pretext 计算文本布局，30KB 零依赖，自动检测 React/Svelte/Vue，按设计类型智能路由 API。                                     |
| `/qa`                    | QA 主管        | 测试应用、发现 bug、原子提交修复、重新验证。每个修复自动生成回归测试。                                                                                    |
| `/qa-only`               | QA 报告员      | 与 `/qa` 相同的方法论，但仅供报告。纯 bug 报告，不做代码修改。                                                                                            |
| `/pair-agent`            | 多智能体协调器 | 与其他 AI 智能体共享浏览器。一句命令、一次粘贴、即刻连接。支持 OpenClaw、Hermes、Codex、Cursor 等。每个智能体独占标签页，作用域令牌、标签隔离、速率限制。 |
| `/cso`                   | 首席安全官     | OWASP Top 10 + STRIDE 威胁建模。零噪音：17 项误报排除、8/10+ 置信度门控、独立发现验证。每个发现包含具体漏洞场景。                                         |
| `/ship`                  | 发布工程师     | 同步 main、运行测试、审查覆盖率、推送、创建 PR。如没有测试框架则自动搭建。                                                                                |
| `/land-and-deploy`       | 发布工程师     | 合并 PR、等待 CI 和部署、验证生产健康。一条命令从"已批准"到"在生产中已验证"。                                                                             |
| `/canary`                | SRE            | 部署后监控循环。监视控制台错误、性能退化和页面故障。                                                                                                      |
| `/benchmark`             | 性能工程师     | 基线页面加载时间、Core Web Vitals、资源大小。每个 PR 的前后对比。                                                                                         |
| `/document-release`      | 技术文档撰写员 | 更新所有项目文档以匹配刚交付的内容。自动捕获过时的 README。构建 Diataxis 覆盖图。                                                                         |
| `/document-generate`     | 文档作者       | 从零生成文档，遵循 Diataxis 框架。先研究代码库，然后编写真正匹配代码的参考/操作指南/教程/说明文档。                                                       |
| `/retro`                 | 工程经理       | 团队感知的周度复盘。每人贡献分解、交付连续记录、测试健康趋势、增长机会。`/retro global` 跨所有项目和 AI 工具。                                            |
| `/spec`                  | 规格说明书作者 | 将模糊意图转化为精确可执行的规格，分五阶段（why、scope、technical、draft、file）。Codex 质量门控，密文过滤，现有 issue 去重。                             |
| `/browse`                | QA 工程师      | 给智能体装上眼睛。真实 Chromium 浏览器，真实点击，真实截图。每条命令约 100ms。                                                                            |
| `/open-gstack-browser`   | GStack 浏览器  | 启动带侧边栏的 GStack 浏览器，反机器人隐身，自动模型路由（Sonnet 执行动作，Opus 分析）。                                                                  |
| `/setup-browser-cookies` | 会话管理器     | 从真实浏览器（Chrome、Arc、Brave、Edge）导入 cookie 至无头会话，实现认证页面测试。                                                                        |
| `/autoplan`              | 审查流水线     | 一条命令完成全面审查计划。自动运行 CEO → 设计 → 工程审查，内置决策原则。仅将需要品味的决策提交给你。                                                      |
| `/learn`                 | 记忆           | 管理 gstack 跨会话学到的内容。审查、搜索、修剪、导出项目特定的模式、陷阱和偏好。                                                                          |
| `/make-pdf`              | 出版者         | Markdown 输入，出版物品质输出。Mermaid 和 excalidraw 作为矢量图渲染，完全离线。图片自适应页面不截断。`--to html` 输出单文件，`--to docx` 输出 Word。      |
| `/diagram`               | 图表制作       | 英文输入，可编辑图表输出。输出三元组：mermaid 源码、`.excalidraw` 文件、渲染 PNG/SVG。零网络。                                                            |

### 审查技能选用指南

| 构建目标                         | 计划阶段（编码前）            | 实时审查（交付后） |
|----------------------------------|-------------------------------|--------------------|
| 最终用户（UI、Web 应用、移动端） | `/plan-design-review`         | `/design-review`   |
| 开发者（API、CLI、SDK、文档）    | `/plan-devex-review`          | `/devex-review`    |
| 架构（数据流、性能、测试）       | `/plan-eng-review`            | `/review`          |
| 以上全部                         | `/autoplan`（自动检测适用项） | —                  |

### 动力工具

| 技能              | 功能                                                                                                                                                                                                                    |
|-------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `/codex`          | **第二意见。** 来自 OpenAI Codex CLI 的独立代码审查。三种模式：review（通过/失败门控）、adversarial challenge（对抗挑战）、open consultation（开放咨询）。当 `/review` 和 `/codex` 都审查过同一分支后，提供跨模型分析。 |
| `/careful`        | **安全护栏。** 在执行破坏性命令前警告（rm -rf、DROP TABLE、force-push）。说"小心"即可激活。任何警告都可以覆盖。                                                                                                         |
| `/freeze`         | **编辑锁定。** 限制文件编辑在一个目录内。调试时防止意外修改范围外的代码。                                                                                                                                               |
| `/guard`          | **全面安全。** `/careful` + `/freeze` 二合一。生产环境工作最高安全级别。                                                                                                                                                |
| `/unfreeze`       | **解除锁定。** 移除 `/freeze` 边界。                                                                                                                                                                                    |
| `/setup-deploy`   | **部署配置器。** 一次配置 `/land-and-deploy`。自动检测平台、生产 URL、部署命令。                                                                                                                                        |
| `/setup-gbrain`   | **GBrain 上手。** 从零到 gbrain 运行，不超过 5 分钟。PGLite 本地、Supabase 已有 URL、或通过 Management API 自动创建新 Supabase 项目。                                                                                   |
| `/sync-gbrain`    | **保持大脑同步。** 重新索引仓库代码至 gbrain，刷新 CLAUDE.md 中的搜索指引。                                                                                                                                             |
| `/gstack-upgrade` | **自更新器。** 升级 gstack 至最新版本。检测全局 vs 内嵌安装。                                                                                                                                                           |

### iOS 专用技能

| 技能                 | 功能                                                                                                          |
|----------------------|---------------------------------------------------------------------------------------------------------------|
| `/ios-qa`            | 通过 USB CoreDevice 驱动真实 iPhone。嵌入 StateServer，读取 Swift 源码，代码生成类型化 `@Observable` 访问器。 |
| `/ios-fix`           | iOS bug 修复循环。                                                                                            |
| `/ios-design-review` | 设计师视角的 HIG 审查。                                                                                       |
| `/ios-clean`         | 调试桥接清理。                                                                                                |
| `/ios-sync`          | 访问器重新同步。                                                                                              |

### 独立 CLI 工具

| 命令                     | 功能                                                                                     |
|--------------------------|------------------------------------------------------------------------------------------|
| `gstack-model-benchmark` | 跨模型基准测试。同一 prompt 跑 Claude、GPT (via Codex CLI)、Gemini 对比延迟/Token/成本。 |
| `gstack-taste-update`    | 设计品味学习。`/design-shotgun` 的批准和拒绝写入持久化品味档案，每周衰减 5%。            |
| `gstack-ios-qa-daemon`   | iOS QA 守护进程。Mac 端代理，连接 USB iPhone，支持 Tailscale 远程访问。                  |
| `gstack-ios-qa-mint`     | iOS 允许列表管理。tailnet 的身份门控访问。                                               |
| `gstack-ios-qa-regen`    | iOS 桥接重新生成器。确定性安装规范 DebugBridge。                                         |

---

## 典型工作流

```
用户:   我想为日历构建一个每日简报应用。
用户:   /office-hours
Claude: [询问痛点——具体例子，非假设]

用户:   多个 Google 日历，事件信息过时，地点错误。
        准备时间太长，结果不够好...

Claude: 我要质疑你的表述。你说"每日简报应用"，
        但你实际描述的是一个个人幕僚长 AI。
        [提炼出 5 个你未曾意识到的能力]
        [挑战 4 个前提——你同意、反对或调整]
        [生成 3 个实现方案及工作量评估]
        建议：明天交付最窄的切片，从真实使用中学习。

用户:   /plan-ceo-review
        [读取设计文档，挑战范围，运行 10 节审查]

用户:   /plan-eng-review
        [ASCII 数据流图、状态机、错误路径]
        [测试矩阵、故障模式、安全问题]

用户:   批准计划。退出计划模式。
        [写出 2400 行代码，跨 11 个文件。约 8 分钟]

用户:   /review
        [自动修复] 2 个问题。[询问] 竞态条件 → 你批准修复。

用户:   /qa https://staging.myapp.com
        [打开真实浏览器，点击流程，发现并修复 bug]

用户:   /ship
        测试: 42 → 51 (+9 新增)。PR: github.com/you/app/pull/42
```

---

## 配置

### 前缀模式切换

```bash
cd ~/.claude/skills/gstack && ./setup --no-prefix   # 从 /gstack-qa 切换为 /qa
cd ~/.claude/skills/gstack && ./setup --prefix       # 从 /qa 切换为 /gstack-qa
```

### 配置选项（通过 `gstack-config`）

```bash
gstack-config set checkpoint_mode continuous     # 连续 checkpoint（自动 WIP 提交）
gstack-config set checkpoint_push true           # checkpoint 自动推送
gstack-config set telemetry community            # 社区遥测
gstack-config set telemetry anonymous            # 匿名遥测
gstack-config set telemetry off                  # 关闭遥测
gstack-config set explain_level terse            # 简洁输出
gstack-config set proactive false                # 禁用主动技能推荐
```

### CLAUDE.md 路由配置

为了让 gstack 技能自动路由，在项目的 CLAUDE.md 中添加：

```markdown
## gstack

Use /browse from gstack for all web browsing. Never use mcp__claude-in-chrome__* tools. Available skills: /office-hours, /plan-ceo-review, /plan-eng-review, ...

## Skill routing

When the user's request matches an available skill, invoke it via the Skill tool.
```

---

## 隐私与遥测

- **默认关闭。** 除非您明确同意，否则不会发送任何数据。
- **选择开启后发送的内容：** 技能名称、持续时间、成功/失败、gstack 版本、操作系统。仅此而已。
- **绝不发送：** 代码、文件路径、仓库名称、分支名称、提示词或任何用户生成内容。
- **随时更改：** `gstack-config set telemetry off`
- 本地分析始终可用：`gstack-analytics`

---

## 注意事项

1. **模型 overlay**：gstack 针对每个模型家族有行为补丁（`model-overlays/`）。当前会话使用 `claude` overlay，调整偏好以适应 Claude 的行为特征。

2. **Conductor 支持**：在 Conductor 中运行时，需设置 `GSTACK_ANTHROPIC_API_KEY` 和 `GSTACK_OPENAI_API_KEY`（Conductor 会剥离标准 API key 环境变量）。

3. **Windows 用户**：需要 Node.js（除了 Bun）。浏览服务器自动回退至 Node.js。在无开发者模式的 Windows 上，每次 `git pull` 后需重新运行 `./setup`。

4. **技能不显示？** 运行 `cd ~/.claude/skills/gstack && ./setup`

5. **`/browse` 失败？** 运行 `cd ~/.claude/skills/gstack && bun install && bun run build`

6. **Prompt 注入防御**：GStack 浏览器内置分层防御——22MB ML 本地分类器、Claude Haiku 对话检查、随机金丝雀令牌检测。安全状态通过侧边栏盾牌图标显示（绿/黄/红）。

7. **OpenClaw 集成**：gstack 技能在 OpenClaw 通过 ACP 生成的 Claude Code 会话中原生可用。

8. **升级**：运行 `/gstack-upgrade` 或设置 `auto_upgrade: true`

## 资源链接

- GitHub：https://github.com/garrytan/gstack
- 技能详解：https://github.com/garrytan/gstack/blob/main/docs/skills.md
- 架构文档：https://github.com/garrytan/gstack/blob/main/ARCHITECTURE.md
- 浏览器参考：https://github.com/garrytan/gstack/blob/main/BROWSER.md
- 变更日志：https://github.com/garrytan/gstack/blob/main/CHANGELOG.md
- 贡献指南：https://github.com/garrytan/gstack/blob/main/CONTRIBUTING.md
