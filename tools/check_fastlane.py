#!/usr/bin/env python3
"""Check the Fastlane store metadata of Potable (SPEC §11.1, §13).

For each of the two locales of SPEC §11.1, checks that
fastlane/metadata/android/<locale>/ holds title.txt, short_description.txt,
full_description.txt and changelogs/<versionCode>.txt (versionCode read from
app/build.gradle.kts), that each file is non-empty UTF-8 within its length
limit, counted in Unicode characters, and that full_description.txt only uses
the HTML tags F-Droid renders. Also checks the shared en-US/images/icon.png
(PNG, 512 x 512) and refuses any file F-Droid would not expect.

Prints one line per problem, then a summary with the measured lengths.
Exit code: 0 when no error was found, 1 otherwise.

Python 3 standard library only.
"""
from __future__ import annotations

import re
import struct
import sys
from pathlib import Path

LOCALES = ["en-US", "fr-FR"]

# Google Play limits; F-Droid allows 50 for the title, the others are the same.
LIMITS = {
    "title.txt": 30,
    "short_description.txt": 80,
    "full_description.txt": 4000,
    "changelog": 500,
}

# Tags rendered by F-Droid in a description (All About Descriptions, Graphics,
# and Screenshots). <a> is tolerated there but may be stripped: refused here.
ALLOWED_TAGS = {
    "b", "big", "blockquote", "br", "cite", "em", "i", "li", "ol", "small",
    "strike", "strong", "sub", "sup", "tt", "u", "ul",
}

IMAGE_DIRS = {
    "phoneScreenshots", "sevenInchScreenshots", "tenInchScreenshots",
    "tvScreenshots", "wearScreenshots",
}
IMAGE_FILES = {"icon", "featureGraphic", "promoGraphic", "tvBanner"}
IMAGE_EXT = {".png", ".jpg", ".jpeg"}
TEXT_FILES = {"title.txt", "short_description.txt", "full_description.txt", "video.txt"}


def version_code(root: Path) -> int:
    text = (root / "app" / "build.gradle.kts").read_text(encoding="utf-8")
    match = re.search(r"versionCode\s*=\s*(\d+)", text)
    if not match:
        raise SystemExit("check_fastlane: versionCode not found in app/build.gradle.kts")
    return int(match.group(1))


def read_text(path: Path, errors: list[str], label: str) -> str | None:
    if not path.is_file():
        errors.append(f"{label}: missing")
        return None
    try:
        text = path.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        errors.append(f"{label}: not valid UTF-8")
        return None
    if text.startswith("﻿"):
        errors.append(f"{label}: starts with a byte order mark")
    text = text.strip()
    if not text:
        errors.append(f"{label}: empty")
    return text


def check_tags(text: str, errors: list[str], label: str) -> None:
    for tag in re.findall(r"</?\s*([a-zA-Z][a-zA-Z0-9]*)[^>]*>", text):
        if tag.lower() not in ALLOWED_TAGS:
            errors.append(f"{label}: HTML tag <{tag}> not rendered by F-Droid")
    for tag in ALLOWED_TAGS - {"br"}:
        opened = len(re.findall(rf"<{tag}(\s[^>]*)?>", text))
        closed = len(re.findall(rf"</{tag}>", text))
        if opened != closed:
            errors.append(f"{label}: <{tag}> opened {opened} times, closed {closed} times")


def check_icon(path: Path, errors: list[str]) -> None:
    label = "en-US/images/icon.png"
    if not path.is_file():
        errors.append(f"{label}: missing")
        return
    head = path.read_bytes()[:24]
    if head[:8] != b"\x89PNG\r\n\x1a\n" or head[12:16] != b"IHDR":
        errors.append(f"{label}: not a PNG file")
        return
    width, height = struct.unpack(">II", head[16:24])
    if (width, height) != (512, 512):
        errors.append(f"{label}: {width} x {height}, expected 512 x 512")


def check_unexpected(locale_dir: Path, changelog: str, errors: list[str]) -> None:
    for path in sorted(locale_dir.rglob("*")):
        rel = path.relative_to(locale_dir)
        parts = rel.parts
        if path.is_dir():
            ok = parts in {("changelogs",), ("images",)} or (
                len(parts) == 2 and parts[0] == "images" and parts[1] in IMAGE_DIRS)
        elif len(parts) == 1:
            ok = parts[0] in TEXT_FILES
        elif parts[0] == "changelogs":
            ok = len(parts) == 2 and re.fullmatch(r"\d+\.txt", parts[1]) is not None
        elif parts[0] == "images" and len(parts) == 2:
            ok = path.stem in IMAGE_FILES and path.suffix in IMAGE_EXT
        elif parts[0] == "images" and len(parts) == 3:
            ok = parts[1] in IMAGE_DIRS and path.suffix in IMAGE_EXT
        else:
            ok = False
        if not ok:
            errors.append(f"{locale_dir.name}/{rel}: unexpected file or folder")


def main() -> int:
    root = Path(__file__).resolve().parent.parent
    meta = root / "fastlane" / "metadata" / "android"
    code = version_code(root)
    changelog = f"changelogs/{code}.txt"
    errors: list[str] = []
    rows: list[tuple[str, list[str]]] = []

    present = {p.name for p in meta.iterdir() if p.is_dir()} if meta.is_dir() else set()
    for extra in sorted(present - set(LOCALES)):
        errors.append(f"{extra}: locale folder not listed in SPEC §11.1")

    for locale in LOCALES:
        locale_dir = meta / locale
        lengths: list[str] = []
        if not locale_dir.is_dir():
            errors.append(f"{locale}: folder missing")
            rows.append((locale, ["-"] * 4))
            continue
        for name in ("title.txt", "short_description.txt", "full_description.txt", changelog):
            label = f"{locale}/{name}"
            limit = LIMITS["changelog" if name == changelog else name]
            text = read_text(locale_dir / name, errors, label)
            if text is None:
                lengths.append("-")
                continue
            length = len(text)
            lengths.append(f"{length}/{limit}")
            if length > limit:
                errors.append(f"{label}: {length} characters, limit {limit}")
            if name != "full_description.txt" and "<" in text:
                errors.append(f"{label}: HTML is only allowed in full_description.txt")
            if name in ("title.txt", "short_description.txt") and "\n" in text:
                errors.append(f"{label}: must fit on one line")
            if name == "full_description.txt":
                check_tags(text, errors, label)
        check_unexpected(locale_dir, changelog, errors)
        rows.append((locale, lengths))

    check_icon(meta / "en-US" / "images" / "icon.png", errors)

    for error in errors:
        print(error)
    print(f"{'locale':<7} {'title':>7} {'short':>7} {'full':>10} {'changelog ' + str(code):>14}")
    for locale, lengths in rows:
        print(f"{locale:<7} {lengths[0]:>7} {lengths[1]:>7} {lengths[2]:>10} {lengths[3]:>14}")
    if errors:
        print(f"check_fastlane: {len(errors)} error(s)")
        return 1
    print("check_fastlane: OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
