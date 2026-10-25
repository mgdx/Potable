#!/usr/bin/env python3
"""Generate data/src/main/assets/communes.tsv from the official French
communal division (API Découpage administratif, https://geo.api.gouv.fr) and
the INSEE list of events on communes (former codes of merged communes).

Standard library only. Output is deterministic: the same API responses and the
same --date always produce the same file, byte for byte.

See tools/README.md for the format and the choices made.
"""

import argparse
import csv
import datetime
import io
import json
import os
import sys
import time
import urllib.error
import urllib.request

SOURCE = "https://geo.api.gouv.fr"

# name of the cached/saved file -> API path (query string included)
ENDPOINTS = {
    "communes.json": "/communes?fields=code,nom,codesPostaux,centre,population&format=json",
    "deleguees.json": "/communes_associees_deleguees"
    "?fields=code,nom,type,chefLieu,centre&format=json",
    "arrondissements.json": "/communes?type=arrondissement-municipal"
    "&fields=code,nom,codesPostaux,centre,population&format=json",
}

# INSEE, Code officiel géographique, "Liste des événements survenus aux
# communes [...] depuis 1943", one file per vintage (millésime), Licence
# Ouverte. The URL of a vintage does not change; bump both on a new vintage
# (listed on https://www.data.gouv.fr/datasets/code-officiel-geographique-cog/).
MVT_VINTAGE = "v_mvt_commune_2026"
MVT_URL = "https://www.insee.fr/fr/statistiques/fichier/8740222/%s.csv" % MVT_VINTAGE
MVT_FILE = "mvt_commune.csv"
MVT_COLUMNS = ("MOD", "DATE_EFF", "TYPECOM_AV", "COM_AV", "LIBELLE_AV", "TYPECOM_AP", "COM_AP")

# Events that absorb a commune into another one: 31 fusion simple, 32 création
# de commune nouvelle, 33 fusion association, 34 transformation de fusion
# association en fusion simple, 35 suppression de commune déléguée.
MVT_ABSORPTIONS = ("31", "32", "33", "34", "35")
# Code changes (41 changement de département, 50 transfert de chef-lieu): only
# followed to reach the current code of an absorbing commune.
MVT_CODE_CHANGES = ("41", "50")
MVT_SINCE = "2015-01-01"

# Municipal arrondissements of Paris, Lyon and Marseille: Hub'Eau publishes
# these cities under the code of the whole commune only.
PLM = (("751", "75056"), ("6938", "69123"), ("132", "13055"))

# Overseas collectivities outside the Hub'Eau scope (Polynesia, New Caledonia,
# Wallis-et-Futuna, TAAF, Clipperton). The 97x codes are all kept.
EXCLUDED_PREFIXES = ("98",)

DEFAULT_OUTPUT = os.path.join(
    os.path.dirname(os.path.abspath(__file__)),
    os.pardir, "data", "src", "main", "assets", "communes.tsv",
)


def fetch(url, accept="application/json", retries=4):
    req = urllib.request.Request(url, headers={
        "User-Agent": "potable-generate-communes/2",
        "Accept": accept,
    })
    for attempt in range(retries):
        try:
            with urllib.request.urlopen(req, timeout=120) as resp:
                return resp.read()
        except (urllib.error.URLError, TimeoutError) as e:
            if attempt == retries - 1:
                raise SystemExit("error: cannot fetch %s: %s" % (url, e))
            time.sleep(2 ** attempt)
    raise AssertionError("unreachable")


def load(from_dir, save_dir):
    data = {}
    for name, path in ENDPOINTS.items():
        if from_dir:
            with open(os.path.join(from_dir, name), encoding="utf-8") as f:
                data[name] = json.load(f)
        else:
            print("fetching %s%s" % (SOURCE, path), file=sys.stderr)
            try:
                data[name] = json.loads(fetch(SOURCE + path))
            except ValueError as e:
                raise SystemExit("error: invalid JSON for %s: %s" % (name, e))
        if not isinstance(data[name], list) or not data[name]:
            raise SystemExit("error: empty or invalid response for %s" % name)
        if save_dir:
            os.makedirs(save_dir, exist_ok=True)
            with open(os.path.join(save_dir, name), "w", encoding="utf-8") as f:
                json.dump(data[name], f, ensure_ascii=False, sort_keys=True)

    if from_dir:
        with open(os.path.join(from_dir, MVT_FILE), "rb") as f:
            raw = f.read()
    else:
        print("fetching %s" % MVT_URL, file=sys.stderr)
        raw = fetch(MVT_URL, accept="text/csv")
    if save_dir:
        with open(os.path.join(save_dir, MVT_FILE), "wb") as f:
            f.write(raw)
    try:
        reader = csv.DictReader(io.StringIO(raw.decode("utf-8-sig")))
        events = list(reader)
    except (UnicodeDecodeError, csv.Error) as e:
        raise SystemExit("error: invalid %s: %s" % (MVT_FILE, e))
    if not events or any(c not in (reader.fieldnames or ()) for c in MVT_COLUMNS):
        raise SystemExit("error: empty %s or missing columns %s" % (MVT_FILE, MVT_COLUMNS))
    data[MVT_FILE] = events
    return data


def coord(value):
    text = "%.5f" % round(float(value), 5)
    text = text.rstrip("0").rstrip(".")
    return "0" if text in ("-0", "") else text


def clean(text, what):
    text = " ".join(str(text).split())
    if not text:
        raise SystemExit("error: empty %s" % what)
    return text


def population(value, code):
    if value is None:
        return ""
    if isinstance(value, bool) or not isinstance(value, int) or value < 0:
        raise SystemExit("error: invalid population %r for %s" % (value, code))
    return str(value)


def row(code, name, postal_codes, centre, hubeau_code, pop=None):
    if not centre or centre.get("type") != "Point":
        raise SystemExit("error: no centroid for %s" % code)
    lon, lat = centre["coordinates"]
    postal = ",".join(sorted(set(postal_codes or [])))
    return (code, clean(name, "name for " + code), postal, coord(lat), coord(lon), hubeau_code,
            population(pop, code))


def absorbed_codes(events, current, present):
    """Former codes absorbed since MVT_SINCE and absent from [present]:
    ({old code: (name, current code)}, number of codes left out).

    For each old code, its latest absorption event gives its name at that time
    and the commune it went into; that commune may itself have been absorbed
    or have changed code later, so the chain is followed up to a current
    commune. A code whose chain does not end on a current commune (events
    newer than the API data) is left out and counted.
    """
    absorbed, successor = {}, {}
    for e in events:
        if (e["DATE_EFF"] < MVT_SINCE or e["TYPECOM_AP"] != "COM"
                or e["COM_AV"] == e["COM_AP"]):
            continue
        key = (e["DATE_EFF"], e["COM_AP"])
        old = e["COM_AV"]
        if e["MOD"] in MVT_ABSORPTIONS:
            if old not in absorbed or key > absorbed[old][0]:
                absorbed[old] = (key, e["LIBELLE_AV"])
        elif e["MOD"] not in MVT_CODE_CHANGES:
            continue
        if old not in successor or key > successor[old]:
            successor[old] = key
    resolved, unresolved = {}, 0
    for old in sorted(absorbed):
        if old in present:
            continue
        (_, target), name = absorbed[old]
        seen = {old}
        while target not in current and target in successor and target not in seen:
            seen.add(target)
            target = successor[target][1]
        if target in current:
            resolved[old] = (name, target)
        else:
            unresolved += 1
    return resolved, unresolved


def plm_parent(code):
    for prefix, parent in PLM:
        if code.startswith(prefix):
            return parent
    raise SystemExit("error: unknown municipal arrondissement %s" % code)


def build(data):
    rows = {}
    stats = {"current": 0, "delegated": 0, "arrondissement": 0, "former": 0,
             "skipped_same_code": 0, "excluded": 0, "former_unresolved": 0}

    for c in data["communes.json"]:
        code = c["code"]
        if code.startswith(EXCLUDED_PREFIXES):
            stats["excluded"] += 1
            continue
        if code in rows:
            raise SystemExit("error: duplicate current commune %s" % code)
        rows[code] = row(code, c["nom"], c.get("codesPostaux"), c.get("centre"), "", c.get("population"))
        stats["current"] += 1
    current = set(rows)

    for c in data["arrondissements.json"]:
        code = c["code"]
        parent = plm_parent(code)
        if parent not in current or code in rows:
            raise SystemExit("error: inconsistent arrondissement %s" % code)
        rows[code] = row(code, c["nom"], c.get("codesPostaux"), c.get("centre"), parent,
                         c.get("population"))
        stats["arrondissement"] += 1

    for c in data["deleguees.json"]:
        code, parent = c["code"], c.get("chefLieu")
        if c.get("type") not in ("commune-deleguee", "commune-associee"):
            raise SystemExit("error: unexpected type %r for %s" % (c.get("type"), code))
        if code.startswith(EXCLUDED_PREFIXES):
            stats["excluded"] += 1
            continue
        if code in rows:
            # Typically the delegated commune that is the chef-lieu of its
            # commune nouvelle and shares its code: keep the current entry.
            stats["skipped_same_code"] += 1
            continue
        if parent not in current:
            raise SystemExit("error: %s attached to unknown commune %r" % (code, parent))
        # The API does not give postal codes for delegated communes.
        rows[code] = row(code, c["nom"], c.get("codesPostaux"), c.get("centre"), parent)
        stats["delegated"] += 1

    # Former codes of communes absorbed without keeping a delegated status (or
    # whose delegated status was later removed): no centroid or postal code is
    # published for them, so the row reuses the centroid of the current
    # commune and leaves the postal codes and the population empty.
    former, stats["former_unresolved"] = absorbed_codes(data[MVT_FILE], current, set(rows))
    for code, (name, parent) in former.items():
        if code.startswith(EXCLUDED_PREFIXES):
            continue
        _, _, _, lat, lon, _, _ = rows[parent]
        rows[code] = (code, clean(name, "name for " + code), "", lat, lon, parent, "")
        stats["former"] += 1

    return [rows[k] for k in sorted(rows)], stats


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--date", help="generation date written in the header (YYYY-MM-DD, default: today)")
    parser.add_argument("--output", default=DEFAULT_OUTPUT, help="output file (default: %(default)s)")
    parser.add_argument("--from-dir", help="read the API responses from this directory instead of downloading")
    parser.add_argument("--save-dir", help="also save the raw API responses into this directory")
    args = parser.parse_args()

    date = args.date or datetime.date.today().isoformat()
    try:
        datetime.date.fromisoformat(date)
    except ValueError:
        raise SystemExit("error: --date must be YYYY-MM-DD")

    rows, stats = build(load(args.from_dir, args.save_dir))
    lines = ["# potable-communes v2 source=%s events=%s generated=%s" % (SOURCE, MVT_VINTAGE, date)]
    for r in rows:
        if any("\t" in v or "\n" in v for v in r):
            raise SystemExit("error: separator inside a field for %s" % r[0])
        lines.append("\t".join(r))

    tmp = args.output + ".tmp"
    os.makedirs(os.path.dirname(os.path.abspath(args.output)), exist_ok=True)
    with open(tmp, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(lines) + "\n")
    os.replace(tmp, args.output)
    print("wrote %d entries to %s (%s)" % (len(rows), os.path.normpath(args.output),
          ", ".join("%s=%d" % kv for kv in stats.items())), file=sys.stderr)


if __name__ == "__main__":
    main()
