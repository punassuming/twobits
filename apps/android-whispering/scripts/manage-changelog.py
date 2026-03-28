#!/usr/bin/env python3
from __future__ import annotations

import argparse
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path

UNRELEASED_HEADING = "## Unreleased"
ZERO_SHA = "0" * 40
SECTION_PREFIX = "## "


@dataclass(frozen=True)
class Section:
    heading: str
    body: list[str]


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Validate and promote the repository changelog."
    )
    subparsers = parser.add_subparsers(dest="command", required=True)

    validate_parser = subparsers.add_parser(
        "validate", help="Validate changelog structure."
    )
    validate_parser.add_argument("--changelog", default="../../CHANGELOG.md")

    check_parser = subparsers.add_parser(
        "check-updated",
        help="Require CHANGELOG.md to be part of a diff when other files changed.",
    )
    check_parser.add_argument("--base-ref", required=True)
    check_parser.add_argument("--head-ref", required=True)
    check_parser.add_argument("--changelog", default="CHANGELOG.md")

    promote_parser = subparsers.add_parser(
        "promote-release",
        help="Convert the Unreleased section into a versioned release section.",
    )
    promote_parser.add_argument("--version", required=True)
    promote_parser.add_argument("--date", required=True)
    promote_parser.add_argument("--changelog", default="CHANGELOG.md")
    promote_parser.add_argument("--release-notes-output")

    args = parser.parse_args()

    if args.command == "validate":
        changelog_path = resolve_path(args.changelog)
        validate_changelog(changelog_path)
        print(f"Validated changelog structure: {changelog_path}")
        return 0

    if args.command == "check-updated":
        changelog_path = resolve_path(args.changelog)
        return check_updated(changelog_path, args.base_ref, args.head_ref)

    if args.command == "promote-release":
        changelog_path = resolve_path(args.changelog)
        promote_release(
            changelog_path=changelog_path,
            version=args.version,
            date=args.date,
            release_notes_output=(
                resolve_path(args.release_notes_output)
                if args.release_notes_output
                else None
            ),
        )
        return 0

    parser.error(f"Unsupported command: {args.command}")
    return 1


def resolve_path(raw_path: str) -> Path:
    return Path(raw_path).resolve()


def read_sections(changelog_path: Path) -> list[Section]:
    text = changelog_path.read_text(encoding="utf-8")
    lines = text.splitlines()
    if not lines or lines[0].strip() != "# Changelog":
        raise ValueError(f"{changelog_path} must start with '# Changelog'")

    section_indices = [
        index for index, line in enumerate(lines) if line.startswith(SECTION_PREFIX)
    ]
    if not section_indices:
        raise ValueError(f"{changelog_path} must contain at least one '## ' section")

    sections: list[Section] = []
    for index, start in enumerate(section_indices):
        end = section_indices[index + 1] if index + 1 < len(section_indices) else len(lines)
        sections.append(Section(heading=lines[start].strip(), body=lines[start + 1 : end]))
    return sections


def validate_changelog(changelog_path: Path) -> None:
    sections = read_sections(changelog_path)
    unreleased = [section for section in sections if section.heading == UNRELEASED_HEADING]
    if len(unreleased) != 1:
        raise ValueError(
            f"{changelog_path} must contain exactly one '{UNRELEASED_HEADING}' section"
        )

    if sections[0].heading != UNRELEASED_HEADING:
        raise ValueError(
            f"{changelog_path} must keep '{UNRELEASED_HEADING}' as the first changelog section"
        )

    required_headings = ["### Features", "### Improvements", "### Fixes"]
    body = unreleased[0].body
    positions: list[int] = []
    for heading in required_headings:
        try:
            positions.append(next(i for i, line in enumerate(body) if line.strip() == heading))
        except StopIteration as exc:
            raise ValueError(
                f"{changelog_path} is missing '{heading}' inside '{UNRELEASED_HEADING}'"
            ) from exc
    if positions != sorted(positions):
        raise ValueError(
            f"{changelog_path} must keep Features, Improvements, and Fixes in order"
        )


def check_updated(changelog_path: Path, base_ref: str, head_ref: str) -> int:
    if base_ref == ZERO_SHA:
        print("Base ref is empty; skipping changelog diff check.")
        return 0

    repo_root = git_output("rev-parse", "--show-toplevel").strip()
    changelog_rel = changelog_path.resolve().relative_to(Path(repo_root)).as_posix()
    changed_files = [
        line.strip()
        for line in git_output("diff", "--name-only", base_ref, head_ref).splitlines()
        if line.strip()
    ]

    non_changelog_changes = [path for path in changed_files if path != changelog_rel]
    if not non_changelog_changes:
        print("Diff only contains CHANGELOG.md; changelog check passed.")
        return 0

    if changelog_rel not in changed_files:
        print("CHANGELOG.md must be updated for changes headed to main.", file=sys.stderr)
        print("Changed files:", file=sys.stderr)
        for path in non_changelog_changes:
            print(f"  - {path}", file=sys.stderr)
        return 1

    print("CHANGELOG.md was updated alongside the requested changes.")
    return 0


def promote_release(
    changelog_path: Path,
    version: str,
    date: str,
    release_notes_output: Path | None,
) -> None:
    validate_changelog(changelog_path)
    sections = read_sections(changelog_path)
    unreleased = next(section for section in sections if section.heading == UNRELEASED_HEADING)

    if not has_bullets(unreleased.body):
        raise ValueError(
            f"{changelog_path} '{UNRELEASED_HEADING}' section must contain at least one bullet before release promotion"
        )

    promoted = Section(heading=f"## {version} ({date})", body=unreleased.body)
    remaining = [section for section in sections if section.heading != UNRELEASED_HEADING]
    new_sections = [Section(UNRELEASED_HEADING, empty_unreleased_body()), promoted, *remaining]

    changelog_path.write_text(render_changelog(new_sections), encoding="utf-8")
    print(f"Promoted changelog release notes for {version} in {changelog_path}")

    if release_notes_output is not None:
        release_notes_output.parent.mkdir(parents=True, exist_ok=True)
        release_notes_output.write_text(
            render_section(promoted).strip() + "\n",
            encoding="utf-8",
        )
        print(f"Wrote release notes body to {release_notes_output}")


def empty_unreleased_body() -> list[str]:
    return ["", "### Features", "", "### Improvements", "", "### Fixes"]


def has_bullets(lines: list[str]) -> bool:
    return any(line.strip().startswith(("* ", "- ")) for line in lines)


def render_changelog(sections: list[Section]) -> str:
    rendered = ["# Changelog", ""]
    for index, section in enumerate(sections):
        rendered.extend(render_section_lines(section))
        if index != len(sections) - 1:
            rendered.append("")
    return "\n".join(rendered).rstrip() + "\n"


def render_section(section: Section) -> str:
    return "\n".join(render_section_lines(section))


def render_section_lines(section: Section) -> list[str]:
    return [section.heading, *section.body]


def git_output(*args: str) -> str:
    result = subprocess.run(
        ["git", *args],
        check=True,
        capture_output=True,
        text=True,
    )
    return result.stdout


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except ValueError as exc:
        print(exc, file=sys.stderr)
        raise SystemExit(1) from exc
