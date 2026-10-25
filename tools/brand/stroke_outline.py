"""Outline of a stroked polyline, as a closed SVG/VectorDrawable path.

Converts a polyline drawn with a stroke of width ``w`` and round caps and
joins into the filled shape that the stroke covers, using exact circular
arcs (no polygonal approximation):

- each side of the polyline is offset by ``w / 2``;
- on the outer side of a bend, the two offsets are joined by an arc
  centred on the vertex (round join);
- on the inner side, the two offset lines meet at their intersection;
- each end is closed by a half circle (round cap), split into two quarter
  arcs so that the arc direction is never ambiguous.

The result is used by generate_vectors.py to hollow the curve out of the
drop in the monochrome icon (fillType evenOdd).
"""

from __future__ import annotations

import math


def _sub(a, b):
    return a[0] - b[0], a[1] - b[1]


def _add(a, b):
    return a[0] + b[0], a[1] + b[1]


def _mul(a, k):
    return a[0] * k, a[1] * k


def _unit(a):
    n = math.hypot(*a)
    return a[0] / n, a[1] / n


def _cross(a, b):
    return a[0] * b[1] - a[1] * b[0]


def _line_intersection(p, d, q, e):
    """Intersection of p + t d and q + s e; returns (point, t, s)."""
    denom = _cross(d, e)
    if abs(denom) < 1e-12:
        raise ValueError("collinear segments are not supported")
    qp = _sub(q, p)
    t = _cross(qp, e) / denom
    s = _cross(qp, d) / denom
    return _add(p, _mul(d, t)), t, s


def _side(points, r, sign):
    """Offset path along one side (sign = +1 or -1), from first to last point.

    Returns a list of commands starting with the offset of the first point.
    """
    n = len(points)
    dirs = [_unit(_sub(points[k + 1], points[k])) for k in range(n - 1)]
    lengths = [math.hypot(*_sub(points[k + 1], points[k])) for k in range(n - 1)]
    normals = [_mul((-d[1], d[0]), sign) for d in dirs]
    cmds = [("M", *_add(points[0], _mul(normals[0], r)))]
    for k in range(1, n - 1):
        v = points[k]
        d1, d2 = dirs[k - 1], dirs[k]
        n1, n2 = normals[k - 1], normals[k]
        turn = _cross(d1, d2) * sign  # > 0: this side is on the inside of the bend
        a = _add(v, _mul(n1, r))
        b = _add(v, _mul(n2, r))
        if turn > 0:
            point, t, s = _line_intersection(a, d1, b, d2)
            # t <= 0 and s >= 0 measure how far the meeting point lies back
            # along each segment; it must stay within both.
            if -t > lengths[k - 1] or s > lengths[k]:
                raise ValueError("segment too short for this stroke width")
            cmds.append(("L", *point))
        else:
            cmds.append(("L", *a))
            sweep = 1 if _cross(_sub(a, v), _sub(b, v)) > 0 else 0
            cmds.append(("A", r, r, 0, 0, sweep, *b))
    cmds.append(("L", *_add(points[-1], _mul(normals[-1], r))))
    return cmds


def _cap(center, direction, start, end, r):
    """Half circle around ``center`` from ``start`` to ``end`` via the tip."""
    tip = _add(center, _mul(direction, r))
    out = []
    for p, q in ((start, tip), (tip, end)):
        sweep = 1 if _cross(_sub(p, center), _sub(q, center)) > 0 else 0
        out.append(("A", r, r, 0, 0, sweep, *q))
    return out


def outline(points, width):
    """Closed outline commands for ``points`` stroked with round caps/joins."""
    r = width / 2
    if len(points) < 2:
        raise ValueError("a polyline needs at least two points")
    left = _side(points, r, +1)
    right = _side(list(reversed(points)), r, +1)  # +1 on reversed = other side
    d_end = _unit(_sub(points[-1], points[-2]))
    d_start = _unit(_sub(points[0], points[1]))
    cmds = list(left)
    cmds += _cap(points[-1], d_end, left[-1][1:], right[0][1:], r)
    cmds += [("L", *c[1:]) if c[0] == "M" else c for c in right[1:]]
    cmds += _cap(points[0], d_start, right[-1][1:], left[0][1:], r)
    cmds.append(("Z",))
    return cmds
