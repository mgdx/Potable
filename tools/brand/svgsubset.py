"""Minimal reader for the SVG subset used by docs/brand/potable_logo.svg.

Shared by generate_icons.py (raster outputs) and generate_vectors.py
(Android VectorDrawables). Only what the logo needs is supported, and
anything else raises an error instead of being silently ignored:

- elements: <rect> (x, y, width, height, rx, ry), <path> (absolute
  M, L, H, V, C, A, Z), <polyline>, <g>, <title>;
- transform: translate(tx [ty]) only, accumulated through groups;
- paint: fill, stroke, stroke-width, stroke-linecap, stroke-linejoin,
  inherited from parent groups.

Every shape is returned with its translation already applied, so callers
work in plain viewport coordinates.
"""

from __future__ import annotations

import math
import re
import xml.etree.ElementTree as ET
from dataclasses import dataclass, field

SVG_NS = "{http://www.w3.org/2000/svg}"
PAINT_ATTRS = ("fill", "stroke", "stroke-width", "stroke-linecap", "stroke-linejoin")


@dataclass
class Shape:
    """A shape as a list of absolute path commands, plus its paint."""

    kind: str  # "rect", "path" or "polyline"
    commands: list[tuple]  # ("M", x, y) ("L", x, y) ("C", x1, y1, x2, y2, x, y)
    # ("A", rx, ry, rot, large, sweep, x, y) ("Z",)
    paint: dict[str, str] = field(default_factory=dict)
    points: list[tuple[float, float]] = field(default_factory=list)  # polyline only

    @property
    def fill(self) -> str | None:
        value = self.paint.get("fill", "#000000")
        return None if value == "none" else value

    @property
    def stroke(self) -> str | None:
        value = self.paint.get("stroke", "none")
        return None if value == "none" else value

    @property
    def stroke_width(self) -> float:
        return float(self.paint.get("stroke-width", "1"))


@dataclass
class Logo:
    view_box: tuple[float, float, float, float]
    shapes: list[Shape]


def _translate(attr: str | None) -> tuple[float, float]:
    if not attr:
        return 0.0, 0.0
    match = re.fullmatch(r"\s*translate\(\s*([-\d.]+)(?:[\s,]+([-\d.]+))?\s*\)\s*", attr)
    if not match:
        raise ValueError(f"unsupported transform: {attr!r}")
    return float(match.group(1)), float(match.group(2) or 0)


def _numbers(text: str) -> list[float]:
    return [float(n) for n in re.findall(r"-?\d*\.?\d+(?:[eE][-+]?\d+)?", text)]


def _parse_path(d: str, tx: float, ty: float) -> list[tuple]:
    tokens = re.findall(r"[A-Za-z]|-?\d*\.?\d+(?:[eE][-+]?\d+)?", d)
    arity = {"M": 2, "L": 2, "H": 1, "V": 1, "C": 6, "A": 7, "Z": 0}
    out: list[tuple] = []
    i, cmd = 0, None
    cx = cy = 0.0
    while i < len(tokens):
        if re.fullmatch(r"[A-Za-z]", tokens[i]):
            cmd = tokens[i]
            i += 1
            if cmd not in arity:
                raise ValueError(f"unsupported path command {cmd!r} (absolute M L H V C A Z only)")
            if cmd == "Z":
                out.append(("Z",))
                continue
        if cmd is None:
            raise ValueError("path data must start with a command")
        args = [float(t) for t in tokens[i : i + arity[cmd]]]
        i += arity[cmd]
        if cmd == "M":
            cx, cy = args[0] + tx, args[1] + ty
            out.append(("M", cx, cy))
            cmd = "L"  # implicit lineto after moveto
        elif cmd == "L":
            cx, cy = args[0] + tx, args[1] + ty
            out.append(("L", cx, cy))
        elif cmd == "H":
            cx = args[0] + tx
            out.append(("L", cx, cy))
        elif cmd == "V":
            cy = args[0] + ty
            out.append(("L", cx, cy))
        elif cmd == "C":
            x1, y1, x2, y2, x, y = args
            cx, cy = x + tx, y + ty
            out.append(("C", x1 + tx, y1 + ty, x2 + tx, y2 + ty, cx, cy))
        elif cmd == "A":
            rx, ry, rot, large, sweep, x, y = args
            cx, cy = x + tx, y + ty
            out.append(("A", rx, ry, rot, int(large), int(sweep), cx, cy))
    return out


def _rect(el: ET.Element, tx: float, ty: float) -> list[tuple]:
    x = float(el.get("x", 0)) + tx
    y = float(el.get("y", 0)) + ty
    w, h = float(el.get("width")), float(el.get("height"))
    rx = el.get("rx")
    ry = el.get("ry")
    rx = float(rx) if rx is not None else (float(ry) if ry is not None else 0.0)
    ry = float(ry) if ry is not None else rx
    rx, ry = min(rx, w / 2), min(ry, h / 2)
    if rx == 0 or ry == 0:
        return [("M", x, y), ("L", x + w, y), ("L", x + w, y + h), ("L", x, y + h), ("Z",)]
    cmds: list[tuple] = [("M", x + rx, y)]
    if w - 2 * rx > 0:
        cmds.append(("L", x + w - rx, y))
    cmds.append(("A", rx, ry, 0, 0, 1, x + w, y + ry))
    if h - 2 * ry > 0:
        cmds.append(("L", x + w, y + h - ry))
    cmds.append(("A", rx, ry, 0, 0, 1, x + w - rx, y + h))
    if w - 2 * rx > 0:
        cmds.append(("L", x + rx, y + h))
    cmds.append(("A", rx, ry, 0, 0, 1, x, y + h - ry))
    if h - 2 * ry > 0:
        cmds.append(("L", x, y + ry))
    cmds.append(("A", rx, ry, 0, 0, 1, x + rx, y))
    cmds.append(("Z",))
    return cmds


def load(path: str) -> Logo:
    root = ET.parse(path).getroot()
    vb = tuple(_numbers(root.get("viewBox", "")))
    if len(vb) != 4:
        raise ValueError("the SVG needs a viewBox")
    shapes: list[Shape] = []

    def walk(el: ET.Element, tx: float, ty: float, paint: dict[str, str]) -> None:
        for child in el:
            tag = child.tag.replace(SVG_NS, "")
            dx, dy = _translate(child.get("transform"))
            ctx, cty = tx + dx, ty + dy
            cpaint = dict(paint)
            for attr in PAINT_ATTRS:
                if child.get(attr) is not None:
                    cpaint[attr] = child.get(attr)
            if child.get("style") or child.get("opacity") or child.get("fill-opacity"):
                raise ValueError(f"unsupported styling on <{tag}>")
            if tag == "title":
                continue
            if tag == "g":
                walk(child, ctx, cty, cpaint)
            elif tag == "rect":
                shapes.append(Shape("rect", _rect(child, ctx, cty), cpaint))
            elif tag == "path":
                shapes.append(Shape("path", _parse_path(child.get("d"), ctx, cty), cpaint))
            elif tag == "polyline":
                nums = _numbers(child.get("points"))
                pts = [(nums[k] + ctx, nums[k + 1] + cty) for k in range(0, len(nums), 2)]
                cmds = [("M", *pts[0])] + [("L", *p) for p in pts[1:]]
                shapes.append(Shape("polyline", cmds, cpaint, pts))
            else:
                raise ValueError(f"unsupported element <{tag}>")

    walk(root, 0.0, 0.0, {})
    return Logo(vb, shapes)


def fmt(value: float) -> str:
    """Compact number formatting for path data."""
    text = f"{value:.3f}".rstrip("0").rstrip(".")
    return "0" if text in ("-0", "") else text


def to_path_data(commands: list[tuple]) -> str:
    parts = []
    for c in commands:
        op, args = c[0], c[1:]
        if op == "A":
            rx, ry, rot, large, sweep, x, y = args
            parts.append(f"A{fmt(rx)},{fmt(ry)} {fmt(rot)} {large},{sweep} {fmt(x)},{fmt(y)}")
        elif op == "Z":
            parts.append("Z")
        else:
            parts.append(op + " ".join(f"{fmt(args[k])},{fmt(args[k + 1])}" for k in range(0, len(args), 2)))
    return " ".join(parts)


def arc_to_center(x1, y1, rx, ry, large, sweep, x2, y2):
    """SVG endpoint arc (no rotation) to cairo center form.

    Returns (cx, cy, r, a1, a2, positive) for circular arcs only.
    """
    if abs(rx - ry) > 1e-9:
        raise ValueError("only circular arcs are supported")
    r = rx
    mx, my = (x1 - x2) / 2, (y1 - y2) / 2
    lam = (mx * mx + my * my) / (r * r)
    if lam > 1:
        r *= math.sqrt(lam)
    d2 = (x2 - x1) ** 2 + (y2 - y1) ** 2
    h = math.sqrt(max(r * r - d2 / 4, 0.0))
    ux, uy = (x2 - x1) / math.sqrt(d2), (y2 - y1) / math.sqrt(d2)
    px, py = (x1 + x2) / 2, (y1 + y2) / 2
    # Two candidate centres, either side of the chord.
    sign = 1 if large != sweep else -1
    cx, cy = px - sign * h * uy, py + sign * h * ux
    a1 = math.atan2(y1 - cy, x1 - cx)
    a2 = math.atan2(y2 - cy, x2 - cx)
    return cx, cy, r, a1, a2, bool(sweep)


def cairo_path(ctx, commands: list[tuple]) -> None:
    """Replay absolute path commands on a cairo context."""
    x = y = 0.0
    for c in commands:
        op = c[0]
        if op == "M":
            x, y = c[1], c[2]
            ctx.move_to(x, y)
        elif op == "L":
            x, y = c[1], c[2]
            ctx.line_to(x, y)
        elif op == "C":
            ctx.curve_to(*c[1:])
            x, y = c[5], c[6]
        elif op == "A":
            rx, ry, _rot, large, sweep, nx, ny = c[1:]
            cx, cy, r, a1, a2, positive = arc_to_center(x, y, rx, ry, large, sweep, nx, ny)
            (ctx.arc if positive else ctx.arc_negative)(cx, cy, r, a1, a2)
            x, y = nx, ny
        elif op == "Z":
            ctx.close_path()
