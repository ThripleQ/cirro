package com.thripleq.nume.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Rect

/**
 * 打开评论浮层。
 *
 * [threadId] 是网易那套资源线程序号（[com.thripleq.nume.core.repo.CommentThread]：
 * 单曲 `R_SO_4_`、专辑 `R_AL_3_`、歌单 `A_PL_0_`、节目 `R_VI_62_`）。
 * [origin] 是浮现起点（点下去那颗按钮的窗口矩形）；null = 居中浮现。
 */
fun interface CommentsOpener {
    fun open(threadId: String, origin: Rect?)
}

/**
 * 评论浮层的打开入口 —— **由 [com.thripleq.nume.NumeApp] 提供**。
 *
 * ## 为什么用 CompositionLocal 而不是一路传回调
 *
 * 评论浮层必须挂在**根**（盖在整页之上、含 dock），但它是**从内容深处**触发的：
 * 播放页那颗评论键、歌单页头部那枚胶囊 —— 而歌单页自己挂了五处
 * （nav 详情页 ×2、探索页展开壳、我的页两个面板）。浮层若就地画在歌单页里，
 * 会被展开壳的 `clip(paperShape)` 连人带内容一起裁掉。
 *
 * 沿调用链传回调意味着给五条路径都加一层参数，还要穿过 HomeScreen / ProfileScreen
 * / ProfilePanels / HomeExpandShell 四个中间层 —— 中间层完全不关心这件事，纯粹是
 * 管道。站内已有 [LocalShellSettled] / [LocalShellHeroAlpha] 这类「壳环境快照」
 * 的先例，这一条属同一性质：**根提供、深处即取**。
 *
 * 默认 null（未提供）= 没有宿主，调用方自己安静地不做任何事。预览、
 * 单测、非 App 宿主因此都不需要额外接线。
 */
val LocalCommentsOpener = staticCompositionLocalOf<CommentsOpener?> { null }
