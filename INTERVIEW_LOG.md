# 面试记录

## 环境
- AI 工具：Codex Desktop（GPT-5）、Cursor(opus 4.)
- 项目：MarketingDecisionSystem，Java 8 + Maven + Spring Boot 2.7.18 + MySQL
- GitHub 仓库：https://github.com/xiaoxolu/MarketingDecisionSystem
- 搭建过程中的问题与解决：
  - 项目最初不是 Git 仓库：使用 `git init` 初始化本地仓库，并将默认分支改为 `main`。
  - 项目缺少 `.gitignore`：新增 `.gitignore`，排除 `target/`、IDE 配置、日志和本地环境文件，避免上传编译产物。
  - 本机没有 GitHub CLI：使用 `winget` 安装官方 GitHub CLI。
  - GitHub CLI 网页授权时，命令行与 GitHub 通信被本机网络中断：改为先在浏览器手动创建私有仓库，再通过 `git push` 触发 Git Credential Manager 浏览器认证，最终推送成功。

## 需求与实现（按时间顺序）
- [2026-07-04 16:48] 需求点：运行项目
- 我给 AI 的提示词（要点）：运行我的项目。
- AI 输出概要 / 我做了哪些采纳或修改：AI 检查项目结构，识别为 Maven/Spring Boot 项目；确认 Java、Maven、MySQL 3306 和 8080 端口状态；使用 `mvn spring-boot:run` 启动服务；验证首页 `http://localhost:8080/` 返回 200。
- 遇到的问题与怎么解决：项目依赖本机 MySQL，先确认 3306 端口可用；8080 未被占用后再启动，避免端口冲突。

- [2026-07-05 09:00] 需求点：上传项目到 GitHub 私有仓库
- 我给 AI 的提示词（要点）：将项目上传到 GitHub 的私有仓库，帮我操作。
- AI 输出概要 / 我做了哪些采纳或修改：AI 检查本地 Git 状态，发现项目还不是 Git 仓库；新增 `.gitignore`；初始化 Git 仓库；暂存源码、SQL、页面资源和测试数据；创建首次提交 `Initial commit`；将分支改为 `main`；把远端设置为 `https://github.com/xiaoxolu/MarketingDecisionSystem.git`；推送 `main` 到 GitHub。
- 遇到的问题与怎么解决：最初 `.gitignore` 放在了上级目录，导致 `target/` 曾被暂存；通过重置暂存区、把 `.gitignore` 移到仓库根目录后重新暂存，确认 `target/` 已被忽略。

- [2026-07-05 09:16] 需求点：了解如何添加 GitHub 协作者，并记录 AI 使用过程
- 我给 AI 的提示词（要点）：说明面试当天如何在 GitHub 仓库 Settings -> Collaborators 添加面试官账号；在仓库根目录创建 `INTERVIEW_LOG.md`，记录提示词要点、AI 输出概要、采纳修改和问题解决过程。
- AI 输出概要 / 我做了哪些采纳或修改：AI 根据 GitHub 仓库协作者管理流程说明操作位置，并按面试方给出的模板创建本文件；内容保留真实操作过程，方便面试时说明如何使用 AI 辅助开发与排错。
- 遇到的问题与怎么解决：GitHub 页面入口可能随版本有细微变化，最终以仓库页面的 `Settings` 和 `Collaborators`/`Manage access` 入口为准。

- [2026-07-06 14:00] 需求点：用户预测结果分页与多格式导出需求文档
- 我给 AI 的提示词（要点）：根据当前页面截图，在“用户预测结果”下方增加分页；把“导出CSV”改为可选择导出 CSV、Excel 或两者都导出；只需要需求文档，不要代码；不确定处必须确认，优先复用现有方法和接口。
- AI 输出概要 / 我做了哪些采纳或修改：AI 先查阅 `index.html`、`MarketingController.java`、`pom.xml` 和相关 DTO，确认当前页面只展示前 20 条、已有 `/export-predictions` CSV 导出、VIP 用户才可导出；随后列出待确认项。我确认了每页固定 20 条、使用数字页码并保留上一页/下一页、导出全部预测结果、CSV+Excel 使用 ZIP、Excel 必须是真实 `.xlsx`。AI 最终整理出完整需求文档。
- 遇到的问题与怎么解决：分页范围、导出范围、两文件下载方式等业务点一开始不明确；通过逐项确认后再定稿，避免 AI 直接猜业务或新增不必要接口。

- [2026-07-06 14:30] 需求点：写入项目级 AI 协作规则
- 我给 AI 的提示词（要点）：把“八荣八耻”加入规则，确保后续对话按认真查阅、寻求确认、复用现有、主动测试、遵循规范等原则执行。
- AI 输出概要 / 我做了哪些采纳或修改：AI 说明无法修改所有未来新对话的系统级规则，但可以写入项目文件；随后在项目根目录新增 `AGENTS.md`，记录“八荣八耻”和具体执行要求，作为本仓库后续协作规范。
- 遇到的问题与怎么解决：首次读取中文文件时 PowerShell 默认输出出现乱码；AI 改用 `Get-Content -Encoding UTF8` 读回确认内容完整。

- [2026-07-06 15:00] 需求点：上传文件支持 CSV 与 Excel 的需求文档
- 我给 AI 的提示词（要点）：上传文件处不仅支持 CSV，也支持 Excel；仍然只需要需求文档；继续遵守不猜接口、不模糊执行、不凭空创造业务的要求。
- AI 输出概要 / 我做了哪些采纳或修改：AI 查阅 `/upload` 上传流程、`DataPreprocessorInterface`、`DataPreprocessorService`、`index.html` 和 `pom.xml`，发现前端 `accept` 已包含 `.xlsx/.xls`，但后端解析仍以 CSV 为主，项目已有 Apache POI 依赖。AI 先输出草案和待确认项；我确认同时支持 `.xlsx` 和 `.xls`、只读取第一个工作表、Excel 结构与 CSV 一致、同时支持标准格式和淘宝 UserBehavior 格式、空行跳过、日期单元格和文本日期都支持。AI 最终生成完整需求文档。
- 遇到的问题与怎么解决：前端显示可选择 Excel 不代表后端真正支持解析；AI 通过查阅代码明确了当前能力边界，并把不确定项交给我确认后再写入文档。

- [2026-07-06 15:10] 需求点：生成 Excel 上传测试数据
- 我给 AI 的提示词（要点）：生成一份 Excel 测试数据，用于验证上传 Excel 的需求。
- AI 输出概要 / 我做了哪些采纳或修改：AI 按标准用户行为格式生成 `test_user_behavior.xlsx`，包含 120 个用户、480 条行为记录，字段为 `user_id`、`product_id`、`behavior_type`、`behavior_time`、`page_duration`；其中 `behavior_time` 同时包含文本日期和真实 Excel 日期单元格，方便测试两种解析方式。
- 遇到的问题与怎么解决：本地没有预配置的表格生成运行时；AI 使用可用的 Python 生成真实 `.xlsx` 工作簿，并校验文件结构和数据行数，确认不是简单改后缀文件。
