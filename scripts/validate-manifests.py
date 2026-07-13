#!/usr/bin/env python3
"""
Validates all AndroidManifest.xml files in the project.

Checks performed:
  1. File is valid XML.
  2. The <manifest> root declares xmlns:android when android:-prefixed
     attributes are present anywhere in the file.
"""

import argparse
import os
import sys
import xml.etree.ElementTree as ET

ANDROID_NS = "http://schemas.android.com/apk/res/android"
MANIFEST_FILENAME = "AndroidManifest.xml"


def find_manifests(root_dir: str) -> list[str]:
    paths = []
    for dirpath, dirnames, filenames in os.walk(root_dir):
        # Prune hidden dirs and build output before os.walk descends into them.
        dirnames[:] = [name for name in dirnames if not name.startswith(".") and name != "build"]
        for filename in filenames:
            if filename == MANIFEST_FILENAME:
                paths.append(os.path.join(dirpath, filename))
    return sorted(paths)


def uses_android_attributes(path: str) -> bool:
    """Return True if the raw file text contains any android: attribute references."""
    with open(path, encoding="utf-8") as f:
        return "android:" in f.read()


def validate_manifest(path: str) -> list[str]:
    errors = []

    # 1. XML validity
    try:
        tree = ET.parse(path)
    except ET.ParseError as exc:
        errors.append(f"XML parse error: {exc}")
        return errors  # further checks are meaningless

    # 2. xmlns:android required when android: attributes are present
    if uses_android_attributes(path):
        # ElementTree strips namespace declarations; check raw text instead
        with open(path, encoding="utf-8") as f:
            content = f.read()
        if f'xmlns:android="{ANDROID_NS}"' not in content:
            errors.append(
                f'Missing namespace declaration: '
                f'xmlns:android="{ANDROID_NS}" must be declared on '
                f'the <manifest> element when android: attributes are used.'
            )

    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description="Validate AndroidManifest.xml files.")
    parser.add_argument(
        "--root",
        default=os.path.join(os.path.dirname(__file__), ".."),
        help="Root directory to search for AndroidManifest.xml files (default: apps/scrybe)",
    )
    args = parser.parse_args()

    search_root = os.path.realpath(args.root)
    manifests = find_manifests(search_root)

    if not manifests:
        print("No AndroidManifest.xml files found.", file=sys.stderr)
        return 1

    failed = False
    for path in manifests:
        rel = os.path.relpath(path, search_root)
        errors = validate_manifest(path)
        if errors:
            failed = True
            for error in errors:
                print(f"ERROR  {rel}: {error}")
        else:
            print(f"OK     {rel}")

    if failed:
        return 1
    print(f"\n{len(manifests)} manifest(s) validated successfully.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
