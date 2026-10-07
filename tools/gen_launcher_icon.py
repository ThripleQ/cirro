#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
cirro 启动图标生成器 —— 几何只写一遍，自动居中，并**在脚本内自证安全区**。

产物：
  app/src/main/res/drawable/ic_launcher_foreground.xml     前景层（透明底、图形色 GLYPH）
  app/src/main/res/drawable/ic_launcher_monochrome.xml     单色层（Android 13+ 主题图标）
  app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml        自适应图标（方）
  app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml  自适应图标（圆）
  app/src/main/res/values/ic_launcher_colors.xml            背景色 ic_launcher_background

## 为什么是这个形状
品牌红 #C92027 底 + 白色音符。符头是一朵**卷云**（cirro- 的本义），符干与符旗是标准
八分音符的构成 —— 小尺寸下它读作「符头」，大尺寸下才看出是云。这是本图标唯一
携带名字信息的部件，其余部件刻意保持中性，免得沦为「天气 App」。

## 为什么用生成器而不是手写 XML
自适应图标有一条**硬约束**：108×108 的图层里，所有墨迹必须落在**居中、直径 66 的
安全圆**内，否则圆形启动器会切边。手写坐标时这条只能靠眼睛估，而「切了一点点」
在 48dp 下几乎看不出来、在大图标里又很扎眼 —— 属于最难靠肉眼发现的一类错。
所以几何在这里只写一份，由脚本换算中心、按需缩放，并把「最远墨迹 / 安全半径」
打出来；超了直接 assert 失败。改形状 = 改下面几行数字 + 重跑。

## 三条已定的口径
1. **只做均匀缩放 + 平移**（不含旋转之外的线性变换），所以椭圆可以用四次贝塞尔
   精确表示、而 ARC 不必出场：整份输出只有 M/L/C/Z 与 Z，任何解析器都吃得下。
2. **图形重心 = 外接框中心**（不是视觉重心）。音符的重量在左下、卷云符头整个偏下，
   按外接框居中会让它看着略高；实测按外接框居中最稳，别改成「看起来更舒服」的比例。
3. 画布 108、可视区 72（内缩 18）、安全圆 66。脚本按 **FIT_R=32** 收口，比安全半径
   33 再留 1 的余量 —— 启动器还可能叠一点视差位移。
"""

import argparse
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
KAPPA = 4.0 / 3.0 * math.tan(math.pi / 8)     # 0.5522847… 四分之一圆弧的贝塞尔近似


# ── 颜色 ────────────────────────────────────────────────────────────────
# 背景走独立资源文件（下面生成 values/ic_launcher_colors.xml），不从 colors.xml 里挤 ——
# 那里放的是启动图 / 窗口底色，跟图标不是一回事，混在一起会让人以为改一个要动另一个。
BACKGROUND = "#C92027"     # 品牌红，与 theme/Palette.kt 的 primary 同值
GLYPH      = "#FFFFFFFF"   # 前景/单色层的图形颜色（单色层的颜色会被系统重绘，只为取形）


# ── 几何：只改这里 ──────────────────────────────────────────────────────

# 符干：竖直，居中于 STEM_X，宽 STEM_W
STEM_X, STEM_W = 53.5, 8.0
STEM_TOP, STEM_BOT = 26.0, 72.0

# 符头：椭圆 rx×ry，绕自身中心逆时针 22°（右端抬起 = 上行音符的正确倾角）
HEAD = ("ellipse", 44.0, 71.0, 12.0, 8.0, -22.0)

# 卷云符头：左小凸 + 右大凸 + 平底（三者同底 79）。
# 关键是**两个凸起的半径差** —— 曾用 7:10 相邻两个圆，算下来左凸顶(65)反而低于右凸在该处的
# 边界(63.7)，整个左凸被吞掉，读作「一只鞋」。现在 6.5:9 且右圆心右移 9，交叉点落在 x≈40.5，
# 左凸实得 8 单位宽的可见鼓包。改这里的数之前先手算一遍这条交叉点。
CLOUD = [
    ("rrect", 33.5, 73.5, 22.5, 5.5, 3.0),
    ("circle", 39.0, 71.0, 6.5),
    ("circle", 48.0, 70.0, 9.0),
]

# 符旗。taper = 由根部渐细收成尖（八分音符的旗）；wisp = 两道渐细云丝（读作十六分音符）
FLAGS = {
    "taper": ["M50,25 C64,22.5 75.5,29.5 76.5,42 C76,38 68,34 53.5,36 Z"],
    "wisp": [
        "M50,25 C63,23 74.5,30 77,44 C72,35 64,30.5 53.5,33 Z",
        "M53.5,41 C62,40 68,45 70.5,56 C66,48 60,43.5 53.5,45.5 Z",
    ],
}

# 三个候选：名字 → (符头部件, 旗形)
CONCEPTS = {
    "note":  ("椭圆符头", "taper"),
    "cloud": ("卷云符头", "taper"),
    "wisp":  ("椭圆符头", "wisp"),
}
DEFAULT_CONCEPT = "note"


# ── 路径工具：只认 M / L / C / Z（绝对）────────────────────────────────
_NUM = re.compile(r"-?\d*\.?\d+")


def parse_path(d):
    """→ [(cmd, [(x, y), …]), …]；C 段带 6 个点，其余 2 个。"""
    out, i = [], 0
    for m in re.finditer(r"([MLCZ])([^MLCZ]*)", d.strip()):
        cmd, args = m.group(1), _NUM.findall(m.group(2))
        nums = [float(v) for v in args]
        if cmd == "Z":
            out.append(("Z", []))
        else:
            out.append((cmd, list(zip(nums[0::2], nums[1::2]))))
        i += 1
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
    segs = []
    for cmd, pts in parse_path(d):
        segs.append((cmd, [fn(x, y) for (x, y) in pts]))
    return dump_path(segs)


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


# ── 形状 → 路径 ────────────────────────────────────────────────────────
def ellipse_path(cx, cy, rx, ry, deg):
    th = math.radians(deg)
    ca, sa = math.cos(th), math.sin(th)

    def rot(u, v):
        return (cx + u * ca - v * sa, cy + u * sa + v * ca)

    segs, first = [], None
    for k in range(4):
        a0, a1 = k * math.pi / 2, (k + 1) * math.pi / 2
        p0 = (rx * math.cos(a0), ry * math.sin(a0))
        p1 = (rx * math.cos(a1), ry * math.sin(a1))
        d0 = (-rx * math.sin(a0), ry * math.cos(a0))
        d1 = (-rx * math.sin(a1), ry * math.cos(a1))
        c0 = (p0[0] + KAPPA * d0[0], p0[1] + KAPPA * d0[1])
        c1 = (p1[0] - KAPPA * d1[0], p1[1] - KAPPA * d1[1])
        if first is None:
            first = rot(*p0)
        segs.append((rot(*c0), rot(*c1), rot(*p1)))
    parts = ["M%.3f,%.3f" % first]
    for c0, c1, p in segs:
        parts.append("C%.3f,%.3f %.3f,%.3f %.3f,%.3f" % (c0 + c1 + p))
    return " ".join(parts) + " Z"


def rrect_path(x, y, w, h, r):
    r = min(r, w / 2, h / 2)
    if r <= 1e-6:                       # 直角矩形（符干）：别吐退化的三次段
        return "M%.3f,%.3f L%.3f,%.3f L%.3f,%.3f L%.3f,%.3f Z" % (
            x, y, x + w, y, x + w, y + h, x, y + h)
    k = KAPPA * r
    return (
        "M%.3f,%.3f L%.3f,%.3f "
        "C%.3f,%.3f %.3f,%.3f %.3f,%.3f "
        "L%.3f,%.3f C%.3f,%.3f %.3f,%.3f %.3f,%.3f "
        "L%.3f,%.3f C%.3f,%.3f %.3f,%.3f %.3f,%.3f "
        "L%.3f,%.3f C%.3f,%.3f %.3f,%.3f %.3f,%.3f Z"
    ) % (
        x + r, y, x + w - r, y,
        x + w - r + k, y, x + w, y + r - k, x + w, y + r,
        x + w, y + h - r,
        x + w, y + h - r + k, x + w - r + k, y + h, x + w - r, y + h,
        x + r, y + h,
        x + r - k, y + h, x, y + h - r + k, x, y + h - r,
        x, y + r,
        x, y + r - k, x + r - k, y, x + r, y,
    )


def shape_to_path(shape):
    kind = shape[0]
    if kind == "ellipse":
        _, cx, cy, rx, ry, deg = shape
        return ellipse_path(cx, cy, rx, ry, deg)
    if kind == "circle":
        _, cx, cy, r = shape
        return ellipse_path(cx, cy, r, r, 0.0)
    if kind == "rrect":
        return rrect_path(*shape[1:])
    if kind == "path":
        return shape[1]
    raise ValueError("未知形状 %r" % (kind,))


def concept_paths(concept):
    head_name, flag_name = CONCEPTS[concept]
    shapes = [("rrect", STEM_X - STEM_W / 2, STEM_TOP, STEM_W, STEM_BOT - STEM_TOP, 0.0)]
    if concept == "cloud":
        shapes += CLOUD
    else:
        shapes.append(HEAD)
    shapes += [("path", d) for d in FLAGS[flag_name]]
    return [shape_to_path(s) for s in shapes]


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
<!-- 机器生成，请勿手改 —— 由 tools/gen_launcher_icon.py 从一组几何参数推导。
     换图形：改脚本里的几何常量并重跑，本文件与 mipmap-anydpi-v26/ 一并重写。
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
     三层齐备：background 纯品牌红；foreground 白符；monochrome 供 Android 13+
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


def vector_body(paths, color):
    return "".join(
        '    <path\n        android:fillColor="%s"\n        android:pathData="%s" />\n' % (color, d)
        for d in paths
    )


def main():
    ap = argparse.ArgumentParser(description="生成 cirro 启动图标资源")
    ap.add_argument("concept", nargs="?", default=DEFAULT_CONCEPT, choices=sorted(CONCEPTS))
    args = ap.parse_args()

    head_name, flag_name = CONCEPTS[args.concept]
    raw = concept_paths(args.concept)
    tx, ty, s, far_raw = transform_of(raw)
    paths = apply_tf(raw, tx, ty, s)
    (x0, y0, x1, y1), far = measure(paths)

    print("图形    : %s + %s 旗" % (head_name, flag_name))
    print("归一化  : 平移 (%+.2f, %+.2f)  缩放 %.4f" % (tx, ty, s))
    print("外接框  : x[%.2f, %.2f]  y[%.2f, %.2f]  尺寸 %.2f × %.2f"
          % (x0, x1, y0, y1, x1 - x0, y1 - y0))
    print("外接框中心: (%.2f, %.2f)  目标 (%.2f, %.2f)"
          % ((x0 + x1) / 2, (y0 + y1) / 2, CENTER, CENTER))
    print("最远墨迹: %.3f   安全半径 %.0f   余量 %.3f" % (far, SAFE_R, SAFE_R - far))

    assert far <= SAFE_R + EPS, "最远墨迹 %.4f 超出安全半径 %.0f —— 圆形启动器会切边" % (far, SAFE_R)
    assert far <= FIT_R + EPS, "最远墨迹 %.4f 超出自我要求 %.0f" % (far, FIT_R)

    note = "%s + %s 旗" % (head_name, flag_name)
    targets = [
        (REPO / "app/src/main/res/drawable/ic_launcher_foreground.xml",
         HEADER.format(note=note, body=vector_body(paths, GLYPH))),
        (REPO / "app/src/main/res/drawable/ic_launcher_monochrome.xml",
         HEADER.format(note=note + "（单色层：形状同上，颜色由系统主题重绘）",
                       body=vector_body(paths, GLYPH))),
        (REPO / "app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml", ADAPTIVE),
        (REPO / "app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml", ADAPTIVE),
        (REPO / "app/src/main/res/values/ic_launcher_colors.xml", COLORS_XML % BACKGROUND),
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
