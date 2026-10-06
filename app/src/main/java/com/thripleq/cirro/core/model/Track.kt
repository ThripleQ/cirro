// 领域模型：**接口返回的东西的形状**。不带 Android / Compose / Dagger 依赖，也不做网络；
// 取数与判断留在 `core/repo` —— 那边的文件只该回答「怎么取、怎么判」。
// 控制流类型（ActionResult / RequestFailedException）不属于这里，留在产生它们的 repo。

package com.thripleq.cirro.core.model

/** A playable song. Album name is captured for the player / lyrics pages. */
data class Track(
    val id: String,
    val name: String,
    val artist: String,
    val artworkUrl: String?,
    val durationMs: Long,
    val albumName: String = "",
    /**
     * 所属专辑 id（`al.id` / 老格式 `album.id`）。**只为「已购」判定而留** ——
     * 数字专辑是整张购买的，所以「这首歌属于一张我已购专辑」等价于「这首歌我买了」，
     * 而曲目对象上并没有别的字段能表达这层归属（见 `LibraryStateStore.isOwned`）。
     * 老端点缺这个字段 → 空串（只看单曲购买记录，退化为保守的「未购」）。
     */
    val albumId: String = "",
    /**
     * 付费档位。接口的 `fee` 原样存下，**不在解析层翻译成「标哪个徽标」** ——
     * 那是展示决策，映射见 `ui/components/PayBadge.kt` 的 `payTagsOf`。
     *
     * 探针实测（2026-10-06）：`playlist/detail`、`v1/album`、`recommend/songs`、
     * `artist/hotSongs` 返回的 song 对象**每个都带 `fee`**，且与同下标
     * `privileges[i].fee` 完全一致 —— 所以只读 song 上这一份就够，不必去对齐
     * privileges 数组（那个数组的下标对齐关系没有保证，不值得依赖）。
     * 例外是 `song/like/get`（只回 id 列表）：那种来源拿不到档位，`0` 即「不画徽标」。
     */
    val fee: Int = 0,
)
