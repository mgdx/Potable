#!/usr/bin/env python3
"""Sanity checks for data/src/main/assets/communes.tsv (standard library only).

Exit status 0 when every check passes, 1 otherwise.
"""

import os
import re
import sys

DEFAULT_INPUT = os.path.join(
    os.path.dirname(os.path.abspath(__file__)),
    os.pardir, "data", "src", "main", "assets", "communes.tsv",
)

HEADER = re.compile(r"^# potable-communes v2 source=\S+ events=v_mvt_commune_\d{4} generated=\d{4}-\d{2}-\d{2}$")
CODE = re.compile(r"^(\d{5}|2[AB]\d{3})$")
POSTAL = re.compile(r"^\d{5}$")
NUMBER = re.compile(r"^-?\d+(\.\d{1,5})?$")
POPULATION = re.compile(r"^(0|[1-9]\d*)$")

# (lat_min, lat_max, lon_min, lon_max)
BOXES = {
    "metropole": (41.0, 51.5, -5.5, 10.0),
    "antilles": (14.0, 18.5, -63.5, -60.5),  # Guadeloupe, Martinique, St-Barth, St-Martin
    "guyane": (2.0, 6.0, -55.0, -51.0),
    "reunion": (-21.5, -20.7, 55.0, 56.0),
    "mayotte": (-13.2, -12.5, 44.9, 45.4),
    "spm": (46.7, 47.2, -56.6, -56.0),
}


def in_france(lat, lon):
    return any(a <= lat <= b and c <= lon <= d for a, b, c, d in BOXES.values())


def main():
    path = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_INPUT
    errors = []

    def fail(msg):
        if len(errors) < 50:
            print("FAIL " + msg)
        errors.append(msg)

    with open(path, "rb") as f:
        raw = f.read()
    if b"\r" in raw:
        fail("CR character found (expected \\n line endings)")
    if not raw.endswith(b"\n"):
        fail("file does not end with \\n")
    text = raw.decode("utf-8")
    lines = text.split("\n")[:-1]

    if not lines or not HEADER.match(lines[0]):
        fail("bad header: %r" % (lines[0] if lines else ""))
    print("header: " + lines[0])

    entries = {}
    previous = ""
    for n, line in enumerate(lines[1:], start=2):
        cols = line.split("\t")
        if len(cols) != 7:
            fail("line %d: %d columns" % (n, len(cols)))
            continue
        code, name, postal, lat, lon, hubeau, population = cols
        if not CODE.match(code):
            fail("line %d: bad code %r" % (n, code))
        if code in entries:
            fail("line %d: duplicate code %s" % (n, code))
        if code <= previous:
            fail("line %d: not sorted (%s after %s)" % (n, code, previous))
        previous = code
        if not name or name != name.strip():
            fail("line %d: bad name %r" % (n, name))
        if postal and not all(POSTAL.match(p) for p in postal.split(",")):
            fail("line %d: bad postal codes %r" % (n, postal))
        if not (NUMBER.match(lat) and NUMBER.match(lon)):
            fail("line %d: bad coordinates %r %r" % (n, lat, lon))
        elif not in_france(float(lat), float(lon)):
            fail("line %d: %s out of France+DOM bounds (%s, %s)" % (n, code, lat, lon))
        if hubeau and not CODE.match(hubeau):
            fail("line %d: bad hubeau_code %r" % (n, hubeau))
        if population and not POPULATION.match(population):
            fail("line %d: bad population %r" % (n, population))
        if not hubeau and not population:
            fail("line %d: current commune %s without population" % (n, code))
        entries[code] = (name, postal.split(",") if postal else [], hubeau,
                         int(population) if population else None)

    current = {c for c, e in entries.items() if not e[2]}
    for code, (_, _, hubeau, _) in entries.items():
        if hubeau and hubeau not in current:
            fail("%s: hubeau_code %s is not a current commune" % (code, hubeau))

    def expect(code, name, postal=None, hubeau=""):
        e = entries.get(code)
        if e is None:
            fail("%s %s missing" % (code, name))
        elif e[0] != name or e[2] != hubeau or (postal and postal not in e[1]):
            fail("%s: unexpected entry %r" % (code, e))

    expect("45234", "Orléans", "45000")
    expect("75056", "Paris", "75001")
    expect("75115", "Paris 15e Arrondissement", "75015", "75056")
    expect("69381", "Lyon 1er Arrondissement", "69001", "69123")
    expect("13201", "Marseille 1er Arrondissement", "13001", "13055")
    expect("97411", "Saint-Denis", "97400")
    expect("01330", "Ruffieu", hubeau="01187")  # delegated
    expect("49074", "La Chapelle-Rousselin", hubeau="49092")  # former code (INSEE events)
    strasbourg = entries.get("67482")
    if not strasbourg or not strasbourg[3] or strasbourg[3] < 200000:
        fail("67482 Strasbourg: population missing or too small %r" % (strasbourg,))

    delegated = sum(1 for e in entries.values() if e[2])
    populated = sum(1 for e in entries.values() if e[3] is not None)
    print("entries: %d (current %d, with hubeau_code %d, with population %d)"
          % (len(entries), len(current), delegated, populated))
    if errors:
        print("%d error(s)" % len(errors))
        return 1
    print("OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
