#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
cirro 品牌 mark —— 全项目**唯一**的图形来源。

产物：
  app/src/main/res/drawable/ic_launcher_foreground.xml        自适应图标前景层
  app/src/main/res/drawable/ic_launcher_monochrome.xml        单色层（Android 13+ 主题图标）
  app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml          自适应图标（方）
  app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml    自适应图标（圆）
  app/src/main/res/values/ic_launcher_colors.xml              背景色 ic_launcher_background
  app/src/main/java/com/thripleq/cirro/ui/theme/BrandMark.kt  开屏动画用的同一份轮廓

## 几何从哪来
下面 [MARK] 里那两段 SVG path 就是品牌 mark：品牌红底 + 暖白的 C 形（cirro 首字母的
卷笔）。它只写这一份 —— 启动图标与开屏动画都从这里出，改形状只改这两串，别去手改
任何生成出来的文件。

## 为什么用生成器而不是手写 XML
自适应图标有一条**硬约束**：108×108 的图层里，所有墨迹必须落在**居中、直径 66 的安全
圆**内，否则圆形启动器会切边。手写坐标时这条只能靠眼睛估，而「切了一点点」在 48dp 下
几乎看不出来、在大图标里又很扎眼 —— 属于最难靠肉眼发现的一类错。所以脚本自动居中、
按需缩到自留半径以内，并把「最远墨迹 / 安全半径」打出来；超了直接 assert 失败。

## 三条已定的口径
1. **只做均匀缩放 + 平移**，不旋转、不斜切 —— 保证 mark 与开屏动画里的那一枚是同一枚。
2. **图形重心 = 外接框中心**（不是视觉重心）。
3. 画布 108、可视区 72（四边各内缩 18）、安全圆 66。脚本按 **FIT_R** 收口，比安全半径
   33 再留一点余量 —— 启动器还可能叠一点视差位移。
"""

import math
import re
import sys
from pathlib import Path

# ── 画布常量 ────────────────────────────────────────────────────────────
VIEW      = 108.0          # 自适应图标图层边长（dp）
CENTER    = VIEW / 2       # 54
VISIBLE   = 72.0           # 可见区边长（四边各内缩 18）
SAFE_R    = 33.0           # Android 保证可见的安全圆半径（66 直径）
FIT_R     = 32.0           # 本图标自留余量后的收口半径
SAMPLES   = 64             # 量测折线密度（居中/安全区判定都走它，免得两处采样不一致）
EPS       = 1e-3           # 量测容差

REPO = Path(__file__).resolve().parent.parent

# ── 颜色 ────────────────────────────────────────────────────────────────
# 背景走独立资源文件（下面生成 values/ic_launcher_colors.xml），不从 colors.xml 里挤 ——
# 那里放的是窗口/开屏底色，跟图标不是一回事，混在一起会让人以为改一个要动另一个。
BACKGROUND = "#F51F43"     # 品牌红（开屏动画同色，见 res/values/colors.xml）
GLYPH      = "#FFFAF6"     # 暖白；单色层的颜色会被系统重绘，只为取形


# ── 几何：只改这里 ──────────────────────────────────────────────────────
# 品牌 mark 的两条轮廓。**坐标空间 = 品牌 SVG 的 1248×1248 画布**（绝对坐标），
# 开屏动画那边按外接框自行居中缩放，不需要在这里归一化。
MARK = [
    # 上半：C 的竖笔与上下两道卷笔（含内侧的收笔）
    "M422 359 "
    "C422 329 445 305 475 291 "
    "C513 273 551 280 590 299 "
    "C619 313 646 334 677 350 "
    "C718 371 758 379 797 371 "
    "C819 367 835 359 850 364 "
    "C868 369 879 384 879 403 "
    "L879 668 "
    "C879 713 852 753 811 773 "
    "C782 787 748 787 724 769 "
    "C704 754 692 730 692 704 "
    "C692 664 721 628 760 610 "
    "C782 600 805 598 827 603 "
    "L827 444 "
    "C782 451 738 440 698 422 "
    "C660 405 627 380 591 369 "
    "C551 356 510 365 475 386 "
    "L475 603 "
    "C475 649 447 690 405 708 "
    "C377 720 345 719 321 703 "
    "C296 687 282 660 283 632 "
    "C284 588 316 551 359 537 "
    "C381 529 403 529 423 535 "
    "L422 359 Z",
    # 下半：C 的下缘长尾（从左上兜到右下再收回）
    "M400 778 "
    "C411 775 421 781 430 792 "
    "C451 818 474 842 504 860 "
    "C541 882 582 893 624 894 "
    "C668 895 711 884 748 862 "
    "C776 845 800 823 820 802 "
    "C830 792 841 796 849 806 "
    "C859 819 862 832 854 844 "
    "C834 871 805 894 772 913 "
    "C727 939 677 958 625 957 "
    "C570 956 518 940 473 913 "
    "C440 893 412 869 391 842 "
    "C382 831 378 818 381 806 "
    "C383 792 390 782 400 778 Z",
]

MARK_NOTE = "品牌 mark（C 形卷笔）"


# ── 路径工具：只认 M / L / C / Z（绝对）────────────────────────────────
_NUM = re.compile(r"-?\d*\.?\d+")


def parse_path(d):
    """→ [(cmd, [(x, y), …]), …]；C 段带 3 个点，M/L 带 1 个，Z 带 0 个。"""
    out = []
    for m in re.finditer(r"([MLCZ])([^MLCZ]*)", d.strip()):
        cmd, args = m.group(1), _NUM.findall(m.group(2))
        nums = [float(v) for v in args]
        if cmd == "Z":
            out.append((cmd, []))
        else:
            out.append((cmd, list(zip(nums[0::2], nums[1::2]))))
    return out


def dump_path(segs):
    parts = []
    for cmd, pts in segs:
        if cmd == "Z":
            parts.append("Z")
        else:
            parts.append(cmd + " ".join("%.3f,%.3f" % p for p in pts))
    return " ".join(parts)


def map_path(d, fn):
    return dump_path((cmd, [fn(x, y) for (x, y) in pts]) for cmd, pts in parse_path(d))


def flatten(d, steps=SAMPLES):
    """把路径摊成折线点列 —— 仅用于量测（外接框 / 最远墨迹）。"""
    pts, cur = [], (0.0, 0.0)
    for cmd, p in parse_path(d):
        if cmd == "M":
            cur = p[0]
            pts.append(cur)
        elif cmd == "L":
            pts.append(p[0])
            cur = p[0]
        elif cmd == "C":
            (x0, y0) = cur
            (x1, y1), (x2, y2), (x3, y3) = p
            for k in range(1, steps + 1):
                t = k / steps
                u = 1 - t
                pts.append((
                    u * u * u * x0 + 3 * u * u * t * x1 + 3 * u * t * t * x2 + t * t * t * x3,
                    u * u * u * y0 + 3 * u * u * t * y1 + 3 * u * t * t * y2 + t * t * t * y3,
                ))
            cur = (x3, y3)
    return pts


# ── 归一化：居中，并按需缩到 FIT_R 以内 ────────────────────────────────
def transform_of(paths):
    pts = [p for d in paths for p in flatten(d)]
    xs = [p[0] for p in pts]
    ys = [p[1] for p in pts]
    tx = CENTER - (min(xs) + max(xs)) / 2
    ty = CENTER - (min(ys) + max(ys)) / 2

    # 平移后（s=1）的最远墨迹；绕 CENTER 均匀缩放时该值线性缩放
    far = max(math.hypot(p[0] + tx - CENTER, p[1] + ty - CENTER) for p in pts)
    s = min(1.0, FIT_R / far) if far > FIT_R else 1.0
    return tx, ty, s, far


def apply_tf(paths, tx, ty, s):
    def fn(x, y):
        return (CENTER + s * (x + tx - CENTER), CENTER + s * (y + ty - CENTER))
    return [map_path(d, fn) for d in paths]


def measure(paths):
    pts = [p for d in paths for p in flatten(d)]
    xs = [p[0] for p in pts]
    ys = [p[1] for p in pts]
    far = max(math.hypot(p[0] - CENTER, p[1] - CENTER) for p in pts)
    return (min(xs), min(ys), max(xs), max(ys)), far


# ── 输出 ───────────────────────────────────────────────────────────────
HEADER = """<?xml version="1.0" encoding="utf-8"?>
<!-- 机器生成，请勿手改 —— 由 tools/gen_launcher_icon.py 从品牌 mark 一份几何推导。
     换图形：改脚本里的 MARK 并重跑，本文件与 mipmap-anydpi-v26/ 一并重写。
     {note} -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
{body}</vector>
"""

ADAPTIVE = """<?xml version="1.0" encoding="utf-8"?>
<!-- 机器生成，请勿手改 —— 由 tools/gen_launcher_icon.py 写出。
     三层齐备：background 纯品牌红；foreground 暖白 mark；monochrome 供 Android 13+
     主题化图标取形（系统会用主题色重绘这一层，故它只要形状、不要颜色）。 -->
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />
</adaptive-icon>
"""

COLORS_XML = """<?xml version="1.0" encoding="utf-8"?>
<!-- 机器生成，请勿手改 —— 由 tools/gen_launcher_icon.py 写出（改 BACKGROUND 一行即可）。 -->
<resources>
    <color name="ic_launcher_background">%s</color>
</resources>
"""

BRAND_MARK_KT = '''package com.thripleq.cirro.ui.theme

/**
 * 机器生成，请勿手改 —— 由 tools/gen_launcher_icon.py 从品牌 mark 一份几何写出。
 *
 * 启动图标（res/drawable/ic_launcher_*.xml）与开屏动画（ui/splash/SplashOverlay.kt）
 * 用的是同一份轮廓，改形状只需改脚本里的 MARK 再重跑。
 *
 * 坐标空间 = 品牌 SVG 的 1248×1248 画布（绝对坐标）。开屏那边按 [androidx.compose.ui
 * .graphics.Path.getBounds] 自行居中缩放，所以这里**不要**预先归一化。
 */
object BrandMark {{
    /**
     * 描边动画的每条轮廓。**顺序即绘制顺序**，与 SVG 一致（先上后下）。
     */
    val OUTLINES: List<String> = listOf(
{outlines}    )
}}
'''


def vector_body(paths, color):
    return "".join(
        '    <path\n        android:fillColor="%s"\n        android:pathData="%s" />\n' % (color, d)
        for d in paths
    )


def main():
    raw = list(MARK)
    tx, ty, s, far_raw = transform_of(raw)
    paths = apply_tf(raw, tx, ty, s)
    (x0, y0, x1, y1), far = measure(paths)

    print("图形    : %s" % MARK_NOTE)
    print("归一化  : 平移 (%+.2f, %+.2f)  缩放 %.4f" % (tx, ty, s))
    print("外接框  : x[%.2f, %.2f]  y[%.2f, %.2f]  尺寸 %.2f × %.2f"
          % (x0, x1, y0, y1, x1 - x0, y1 - y0))
    print("外接框中心: (%.2f, %.2f)  目标 (%.2f, %.2f)"
          % ((x0 + x1) / 2, (y0 + y1) / 2, CENTER, CENTER))
    print("最远墨迹: %.3f   安全半径 %.0f   余量 %.3f" % (far, SAFE_R, SAFE_R - far))

    assert far <= SAFE_R + EPS, "最远墨迹 %.4f 超出安全半径 %.0f —— 圆形启动器会切边" % (far, SAFE_R)
    assert far <= FIT_R + EPS, "最远墨迹 %.4f 超出自我要求 %.0f" % (far, FIT_R)

    note = MARK_NOTE
    targets = [
        (REPO / "app/src/main/res/drawable/ic_launcher_foreground.xml",
         HEADER.format(note=note, body=vector_body(paths, GLYPH))),
        (REPO / "app/src/main/res/drawable/ic_launcher_monochrome.xml",
         HEADER.format(note=note + "（单色层：形状同上，颜色由系统主题重绘）",
                       body=vector_body(paths, GLYPH))),
        (REPO / "app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml", ADAPTIVE),
        (REPO / "app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml", ADAPTIVE),
        (REPO / "app/src/main/res/values/ic_launcher_colors.xml", COLORS_XML % BACKGROUND),
        (REPO / "app/src/main/java/com/thripleq/cirro/ui/theme/BrandMark.kt",
         BRAND_MARK_KT.format(outlines="".join('        "%s",\n' % d for d in MARK))),
    ]
    for path, text in targets:
        # 行尾交给 write_text 的默认行为（把 \n 翻成 os.linesep）：本仓库 checkout 出来就是
        # CRLF，写死 "\n" 会让 git 每次报「LF will be replaced by CRLF」的噪音。
        # 提交内容不受影响 —— core.autocrlf=true 在入库时会归一化成 LF。
        path.write_text(text, encoding="utf-8")
        print("写出    : %s" % path.relative_to(REPO).as_posix())
    return 0


if __name__ == "__main__":
    sys.exit(main())
