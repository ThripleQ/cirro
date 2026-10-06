# 代码整洁纪律（Composing Code）

本文件是 cirro 的编码契约。规则在可能处被机器强制，其余靠约定。任何新增界面/依赖/状态都必须先对照本节。

---

## 1. 依赖规矩（机器强制，Hilt）

> 谁要用 X，必须在构造函数里声明 X。

- 依赖只通过 **构造函数注入**，经 Hilt 解析。
- 禁止 `object` 全局单例放业务状态；有状态的类一律做成 `@Singleton` 注入类。
- 不做全局 `object Gateway` 之类；数据访问统一由 `@Inject` Repository 提供。
- 特例（稳定底层基建，保持单例不纳入 DI）：`PlayerHolder`、`PlaybackCache`。
  它们是不可变内核，不属于数据层，不随界面增长而分叉。
- 修改依赖关系时，原则：能让 Hilt 图自洽，就别绕过它手动 new。

## 2. 职责守恒（靠约定）

> 一个类只干一类事，一个函数只干一件事。

- **ViewModel**：管状态（UiState）+ 管事件（onXxx）。不碰渲染。
- **Screen（@Composable）**：只摆样子 + 收点击。不取数据、不启动播放、不做导航决策。
- **Repository**：只取/转换数据，不碰 UI。
- **PlaybackLauncher**：播放路径唯一入口（byte-cache → ExoPlayer）。
- 屏幕与 ViewModel 之间保持单向：View 读 `UiState` → 点击发事件 → ViewModel 改状态 → 新 UiState 流回 View。
- 一个 ViewModel 只服务一个屏幕、只关心一组状态。
- 方法超一两屏、或能拆出"另一件事"，就拆。

## 3. 命名与结构（靠约定）

- 每屏一对：`ui/screens/XxxScreen.kt` + `ui/xxx/XxxViewModel.kt`（含该屏 `UiState`）。
- 状态统一叫 `UiState`，用 `sealed interface` 表达 `Loading / Error / Ready(data)`。
- 事件触发统一走 ViewModel；数据经 Repository 出、进接口。
- 看到路径就该知道放什么；看到函数名就该知道它干嘛。
- 导航一律 type-safe（`@Serializable` destination + `navController.navigate(Destination)`），目的地集集中定义在 `CirroApp`。禁止字符串路径 + 手工 `Uri.encode`。

## 4. 其他硬约定

- 别急着抽象：出现第三份重复代码（Rule of Three）才抽。
- `docs/architecture.md` 是唯一事实源，与本文件不一致时改文档。