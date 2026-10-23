package com.thripleq.cirro.ui.components

/**
 * 封面地址的**唯一出口** —— 全 app 的封面请求都要经过 [coverSizedUrl]。
 *
 * **为什么单独成文件**：原来它写在 `Artwork.kt` 里，而那个文件 import 了一堆 Compose，
 * JVM 单测碰不得（同 [PayTagRules] 的理由）。这里的判据（域名、已有参数、非法尺寸）
 * 全是容易写错、错了又只在真机上表现为「图不显示」或「流量暴涨」的那种，值得断言。
 *
 * 纯字符串处理，**零 Android / Compose 依赖**。
 */

/** 网易图床的域名后缀。`param` 缩放服务只挂在这个域上。 */
private const val NeteaseImageHostSuffix = ".music.126.net"

/**
 * 网易图床**自家的图片处理参数**（官方 web 端 / app 端那套）。
 *
 * 与 `param=` **不能共存**：2026-10-23 实测，`/weapi/playlist/list` 返回的歌单封面
 * 地址长这样——
 *
 * ```
 * …/109951168679686581.jpg?imageView=1&thumbnail=800y800&enlarge=1%7CimageView=1&watermark
 * &type=1&image=<base64 水印图>&dx=0&dy=0%7C…&imageView=1&thumbnail=140y140&
 * ```
 *
 * 服务端按 query 里最后那个 `thumbnail=140y140` 干活，回一张 **140×140 / 4KB** 的图；
 * 在后面追加 `param=480y480` **完全无效**（实测原样返回 140×140）—— 直接放大到卡面上
 * 就是「场景音乐封面发虚」。剥掉整段 query 再拼 `param=480y480` 才拿到 480×480。
 *
 * 这些参数只描述「服务端要怎么处理这张图」，全部由我们自己的 `param=` 取代即可。
 */
private val NeteaseImageParams = setOf(
    "imageView", "thumbnail", "enlarge", "watermark", "type", "image", "dx", "dy",
)

/**
 * 让网易 CDN 按需缩放封面：`?param=WxH`。
 *
 * ## 为什么必须加
 * Coil 的 `size(px)` **只约束解码尺寸，不约束下载** —— 它先把整张原图拉下来再缩到 px。
 * 于是列表行那张 52dp / 解码 120px 的小封面，实际下的是 2048×2048 的原图。
 * 2026-10-07 在 K40 上对同一张封面实测（同一 CDN、同一 hash）：
 *
 * | 请求 | 实际下载 |
 * |---|---|
 * | 原图（无参数） | **4.5 MB** |
 * | `param=1024y1024` | 1.0 MB |
 * | `param=480y480` | 约 0.3 MB |
 * | `param=360y360` | 216 KB |
 * | `param=120y120` | 32 KB |
 * | `param=24y24` | **2.4 KB** |
 *
 * 磁盘缓存也印证了这一点：加参数前 97 张封面占 78MB，最大单张 9.2MB（2048² PNG）。
 * 一个 117 首的列表首次进入，封面由此从约 90MB 降到约 3.5MB。
 *
 * ## 为什么是零视觉变化
 * 服务端返回的正是当前 `.size(px)` 会解码出的那个尺寸，`.size()` 保持不动 —— 拿到手
 * 的位图与改前同尺寸，只是不再多下一张用不到的大图。
 *
 * ## 边界（都是实测过的）
 * - **只对 `*.music.126.net` 生效**：`param` 是这个图床自家的服务，换域名加参数会 404。
 * - **`px <= 0` 不加**：`param=0y0` 直接 400，不是回退原图。
 * - 地址里**已有 `param=` 就不动**，尊重调用方传进来的既定尺寸。
 * - 地址里带**网易自家的图片处理参数**（`imageView` / `thumbnail` / `watermark` …）时
 *   **剥掉整段 query** 再拼 `param=`：那套参数与 `param=` 不能共存，见
 *   [NeteaseImageParams]（2026-10-23 场景音乐封面发虚的根因）。
 * - 其他 query（`?foo=bar` 这类与图片处理无关的）原样保留，用 `&` 接。
 * - 服务端不认识的参数值会**忽略并返回原图** —— 最坏情况也只是退回现状，不会更糟。
 * - 尺寸超过原图会自动封顶（`4096y4096` 与 `2048y2048` 同字节），不必自己算上限。
 */
internal fun coverSizedUrl(url: String?, px: Int): String? {
    if (url.isNullOrBlank() || px <= 0) return url
    val schemeEnd = url.indexOf("://")
    if (schemeEnd < 0) return url
    val hostStart = schemeEnd + 3
    val pathStart = url.indexOf('/', hostStart).let { if (it < 0) url.length else it }
    // 去掉可能的端口；只认后缀，p1 / p2 / m1… 这些镜像子域都命中。
    val host = url.substring(hostStart, pathStart).substringBefore(':')
    if (!host.endsWith(NeteaseImageHostSuffix)) return url
    val queryStart = url.indexOf('?')
    if (queryStart < 0) return "$url?param=${px}y$px"
    val query = url.substring(queryStart + 1)
    // 已有 param=：调用方既定尺寸，原样不动。
    if (query.split('&').any { it.startsWith("param=") }) return url
    val keys = query.split('&').map { it.substringBefore('=') }
    // 带网易自家的图片处理参数：那套与 param= 不能共存，剥掉整段 query 换自己的。
    if (keys.any { it in NeteaseImageParams }) {
        return url.substring(0, queryStart) + "?param=${px}y$px"
    }
    // 无关 query 保留，用 & 接（不能拼出两个 ?）。
    return "$url&param=${px}y$px"
}
