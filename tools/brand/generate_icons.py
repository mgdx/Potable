#!/usr/bin/env python3
"""Render the raster icons (legacy mipmaps and Fastlane) from the logo SVG.

Usage (from the repository root):

    python3 tools/brand/generate_icons.py [--preview DIR]

Reads docs/brand/potable_logo.svg (108 x 108 grid, SPEC §18) and writes:

- app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.webp
  (48, 72, 96, 144, 192 px): rounded square;
- the same sizes as ic_launcher_round.webp: circular mask;
- fastlane/metadata/android/en-US/images/icon.png: 512 x 512, the whole
  108-unit grid as drawn in the SVG (full square, stores apply their own
  mask).

The mipmaps show the central 72 x 72 units of the grid, i.e. what an
adaptive icon launcher displays (the outer 18 units on each side are bleed
for masks and parallax). They are drawn directly at their final size with
pycairo, so small sizes are antialiased by the renderer rather than
downscaled. WebP files are lossless.

--preview DIR additionally writes the SPEC §18.4 legibility check: the round
icon and the monochrome icon (curve hollowed out) at 48 and 24 px, as PNG.
Look at them before every release: the curve must stay distinct from the
drop at 24 px.

The vector drawables (adaptive foreground, background, monochrome) are
produced by generate_vectors.py.

Requirements: Python 3.10+, pycairo (python3-cairo), Pillow with WebP.
ImageMagick is not used: its internal SVG renderer drops the transformed
<polyline> of the logo.
"""

from __future__ import annotations

import argparse
import math
import os
import sys

import cairo
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import stroke_outline  # noqa: E402
import svgsubset  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
SVG = os.path.join(ROOT, "docs", "brand", "potable_logo.svg")
RES = os.path.join(ROOT, "app", "src", "main", "res")
FASTLANE_ICON = os.path.join(ROOT, "fastlane", "metadata", "android", "en-US", "images", "icon.png")

DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
VISIBLE = (18.0, 18.0, 72.0)  # x, y, size of the visible area of the 108 grid
FULL = (0.0, 0.0, 108.0)
CORNER = 0.125  # corner radius of the legacy square icon, as a fraction of its size


def rgb(value: str) -> tuple[float, float, float]:
    value = value.lstrip("#")
    if len(value) == 3:
        value = "".join(ch * 2 for ch in value)
    return tuple(int(value[k : k + 2], 16) / 255 for k in (0, 2, 4))


def _clip(ctx, size: int, mask: str) -> None:
    if mask == "circle":
        ctx.arc(size / 2, size / 2, size / 2, 0, 2 * math.pi)
        ctx.clip()
    elif mask == "rounded":
        r = size * CORNER
        ctx.new_sub_path()
        ctx.arc(size - r, r, r, -math.pi / 2, 0)
        ctx.arc(size - r, size - r, r, 0, math.pi / 2)
        ctx.arc(r, size - r, r, math.pi / 2, math.pi)
        ctx.arc(r, r, r, math.pi, 3 * math.pi / 2)
        ctx.close_path()
        ctx.clip()
    elif mask != "square":
        raise ValueError(mask)


def render(logo: svgsubset.Logo, size: int, mask: str, monochrome: bool = False, area=VISIBLE) -> Image.Image:
    surf = cairo.ImageSurface(cairo.FORMAT_ARGB32, size, size)
    ctx = cairo.Context(surf)
    ctx.set_antialias(cairo.ANTIALIAS_BEST)
    _clip(ctx, size, mask)
    x0, y0, side = area
    ctx.scale(size / side, size / side)
    ctx.translate(-x0, -y0)

    shapes = logo.shapes
    if monochrome:
        # Grey background stands in for the launcher's themed colour; the
        # motif is drawn as Android draws ic_launcher_monochrome.xml.
        ctx.set_source_rgb(0.25, 0.25, 0.25)
        ctx.paint()
        strokes = [s for s in shapes if s.stroke]
        fills = [s for s in shapes[1:] if s.fill and not s.stroke]
        ctx.set_source_rgb(1, 1, 1)
        for s in fills:
            svgsubset.cairo_path(ctx, s.commands)
            if any(ctx.in_fill(*st.points[0]) for st in strokes):
                for st in strokes:
                    svgsubset.cairo_path(ctx, stroke_outline.outline(st.points, st.stroke_width))
                ctx.set_fill_rule(cairo.FILL_RULE_EVEN_ODD)
            ctx.fill()
            ctx.set_fill_rule(cairo.FILL_RULE_WINDING)
    else:
        for s in shapes:
            svgsubset.cairo_path(ctx, s.commands)
            if s.fill:
                ctx.set_source_rgb(*rgb(s.fill))
                if s.stroke:
                    ctx.fill_preserve()
                else:
                    ctx.fill()
            if s.stroke:
                ctx.set_source_rgb(*rgb(s.stroke))
                ctx.set_line_width(s.stroke_width)
                ctx.set_line_cap({"round": cairo.LINE_CAP_ROUND, "square": cairo.LINE_CAP_SQUARE}.get(
                    s.paint.get("stroke-linecap"), cairo.LINE_CAP_BUTT))
                ctx.set_line_join({"round": cairo.LINE_JOIN_ROUND, "bevel": cairo.LINE_JOIN_BEVEL}.get(
                    s.paint.get("stroke-linejoin"), cairo.LINE_JOIN_MITER))
                ctx.stroke()
            ctx.new_path()
    surf.flush()
    # cairo stores premultiplied BGRA on little-endian hosts; "BGRa" undoes it.
    return Image.frombuffer("RGBA", (size, size), bytes(surf.get_data()), "raw", "BGRa", surf.get_stride(), 1)


def save(image: Image.Image, path: str) -> None:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    if path.endswith(".webp"):
        image.save(path, "WEBP", lossless=True, quality=100, method=6)
    else:
        image.save(path, "PNG", optimize=True)
    print(f"wrote {os.path.relpath(path, ROOT)}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--preview", metavar="DIR", help="also write 48/24 px legibility previews into DIR")
    args = parser.parse_args()

    logo = svgsubset.load(SVG)
    for density, size in DENSITIES.items():
        folder = os.path.join(RES, f"mipmap-{density}")
        save(render(logo, size, "rounded"), os.path.join(folder, "ic_launcher.webp"))
        save(render(logo, size, "circle"), os.path.join(folder, "ic_launcher_round.webp"))
    save(render(logo, 512, "square", area=FULL).convert("RGB"), FASTLANE_ICON)

    if args.preview:
        for size in (48, 24):
            save(render(logo, size, "circle"), os.path.join(args.preview, f"preview_round_{size}.png"))
            save(render(logo, size, "circle", monochrome=True), os.path.join(args.preview, f"preview_mono_{size}.png"))
    return 0


if __name__ == "__main__":
    sys.exit(main())
