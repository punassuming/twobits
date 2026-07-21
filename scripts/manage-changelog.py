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
CANONICAL_SUBHEADINGS = ["### Features", "### Improvements", "### Fixes"]


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
    validate_parser.add_argument("--changelog", default="../CHANGELOG.md")

    check_parser = subparsers.add_parser(
        "check-updated",
        help="Require CHANGELOG.md to be part of a diff when other files changed.",
    )
    check_parser.add_argument("--base-ref", required=True)
    check_parser.add_argument("--head-ref", required=True)
    check_parser.add_argument("--changelog", default="CHANGELOG.md")

    staged_parser = subparsers.add_parser(
        "check-staged-unreleased-new",
        help="Fail if the staged changelog has no new ## Unreleased bullets compared to HEAD.",
    )
    staged_parser.add_argument("--changelog", required=True)

    has_bullets_parser = subparsers.add_parser(
        "has-unreleased-bullets",
        help="Print whether the Unreleased section contains any changelog bullets.",
    )
    has_bullets_parser.add_argument("--changelog", default="CHANGELOG.md")

    new_since_tag_parser = subparsers.add_parser(
        "has-new-unreleased-since-tag",
        help=(
            "Print 'true' only if ## Unreleased contains bullets that were NOT "
            "present at the given git tag. Prevents duplicate releases when a "
            "feature branch is rebased onto a main that already cut a release."
        ),
    )
    new_since_tag_parser.add_argument("--changelog", required=True)
    new_since_tag_parser.add_argument(
        "--tag",
        required=True,
        help="Git tag to compare against (e.g. scrybe-v1.6.0).",
    )
    new_since_tag_parser.add_argument(
        "--fallback-changelog",
        default=None,
        help="Fallback path inside the repo to try if --changelog is absent at the tag (e.g. CHANGELOG.md).",
    )

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

    if args.command == "check-staged-unreleased-new":
        changelog_path = resolve_path(args.changelog)
        return check_staged_unreleased_new(changelog_path)

    if args.command == "has-unreleased-bullets":
        changelog_path = resolve_path(args.changelog)
        print("true" if has_unreleased_bullets(changelog_path) else "false")
        return 0

    if args.command == "has-new-unreleased-since-tag":
        changelog_path = resolve_path(args.changelog)
        result = has_new_unreleased_since_tag(
            changelog_path=changelog_path,
            tag=args.tag,
            fallback_changelog=args.fallback_changelog,
        )
        print("true" if result else "false")
        return 0

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

    body = unreleased[0].body
    positions: list[int] = []
    for heading in CANONICAL_SUBHEADINGS:
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

    # Every section — Unreleased and each already-released '## X.Y.Z' — must not
    # repeat a Features/Improvements/Fixes heading. A duplicate almost always means
    # two releases' worth of content got merged under one '## ' heading, e.g. a
    # '## X.Y.Z' boundary line was accidentally deleted during a hand-edit, so a
    # later `promote-release` stamped both versions' content under a single new
    # heading. Order is also checked wherever more than one heading is present, so
    # older sections that legitimately omit a heading (pre-dating the three-heading
    # convention) aren't flagged.
    for section in sections:
        _check_no_duplicate_subheadings(changelog_path, section)


def _check_no_duplicate_subheadings(changelog_path: Path, section: Section) -> None:
    seen_order = [
        stripped
        for line in section.body
        if (stripped := line.strip()) in CANONICAL_SUBHEADINGS
    ]

    counts: dict[str, int] = {}
    for heading in seen_order:
        counts[heading] = counts.get(heading, 0) + 1
    duplicated = sorted(heading for heading, count in counts.items() if count > 1)
    if duplicated:
        raise ValueError(
            f"{changelog_path} section '{section.heading}' repeats heading(s) "
            f"{duplicated} — two releases' worth of content were likely merged "
            "under one version heading. Check for a missing '## X.Y.Z (date)' "
            "boundary line immediately above or within this section."
        )

    positions = [CANONICAL_SUBHEADINGS.index(heading) for heading in seen_order]
    if positions != sorted(positions):
        raise ValueError(
            f"{changelog_path} section '{section.heading}' has Features, "
            "Improvements, and Fixes out of order"
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

    base_bullets = _extract_unreleased_bullets(_git_file_at_ref(f"{base_ref}:{changelog_rel}"))
    head_bullets = _extract_unreleased_bullets(_git_file_at_ref(f"{head_ref}:{changelog_rel}"))
    new_bullets = head_bullets - base_bullets
    if not new_bullets:
        print(
            "CHANGELOG.md was modified but ## Unreleased has no new entries compared to base.",
            file=sys.stderr,
        )
        print(
            "Add at least one entry under ### Features, ### Improvements, or ### Fixes.",
            file=sys.stderr,
        )
        return 1

    print(f"CHANGELOG.md updated with {len(new_bullets)} new unreleased bullet(s).")
    return 0


def check_staged_unreleased_new(changelog_path: Path) -> int:
    repo_root = git_output("rev-parse", "--show-toplevel").strip()
    changelog_rel = changelog_path.resolve().relative_to(Path(repo_root)).as_posix()
    staged_bullets = _extract_unreleased_bullets(_git_file_at_ref(f":{changelog_rel}"))
    head_bullets = _extract_unreleased_bullets(_git_file_at_ref(f"HEAD:{changelog_rel}"))
    new_bullets = staged_bullets - head_bullets
    if not new_bullets:
        print(
            f"{changelog_path.name} is staged but ## Unreleased has no new entries compared to HEAD.",
            file=sys.stderr,
        )
        print(
            "Add at least one entry under ### Features, ### Improvements, or ### Fixes.",
            file=sys.stderr,
        )
        return 1
    print(f"Staged changelog has {len(new_bullets)} new unreleased bullet(s).")
    return 0


def has_unreleased_bullets(changelog_path: Path) -> bool:
    validate_changelog(changelog_path)
    sections = read_sections(changelog_path)
    unreleased = next(section for section in sections if section.heading == UNRELEASED_HEADING)
    return has_bullets(unreleased.body)


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
    # Accept plain bullets ("* ", "- ") and bold-title items ("**Component** — desc")
    # so that a bold-only entry (no sub-bullets) still counts as release content.
    return any(line.strip().startswith(("* ", "- ", "**")) for line in lines)


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


def has_new_unreleased_since_tag(
    changelog_path: Path,
    tag: str,
    fallback_changelog: str | None,
) -> bool:
    """Return True only if ## Unreleased has bullets not already in a versioned section.

    The `tag` parameter is accepted for CLI consistency but the authoritative
    check compares against the current changelog file's versioned sections, not
    git history. After a `promote-release` run the ## Unreleased section at any
    tag is always empty, so a git-based diff would incorrectly treat every
    stale bullet as new. Instead: a bullet is a duplicate if it already appears
    verbatim in any versioned section (## X.Y.Z ...) of the CURRENT file.
    """
    validate_changelog(changelog_path)
    sections = read_sections(changelog_path)

    unreleased = next(section for section in sections if section.heading == UNRELEASED_HEADING)
    unreleased_bullets = {
        line.strip()
        for line in unreleased.body
        if line.strip().startswith(("* ", "- ", "**"))
    }

    if not unreleased_bullets:
        return False

    released_bullets: set[str] = set()
    for section in sections:
        if section.heading == UNRELEASED_HEADING:
            continue
        released_bullets.update(
            line.strip()
            for line in section.body
            if line.strip().startswith(("* ", "- ", "**"))
        )

    new_bullets = unreleased_bullets - released_bullets
    if new_bullets:
        return True

    print(
        f"All {len(unreleased_bullets)} bullet(s) in ## Unreleased already appear in a "
        "versioned section — skipping to avoid a duplicate release.",
        file=sys.stderr,
    )
    return False


def _git_file_at_ref(ref_path: str) -> str:
    result = subprocess.run(["git", "show", ref_path], capture_output=True, text=True)
    return result.stdout if result.returncode == 0 else ""


def _extract_unreleased_bullets(content: str) -> set[str]:
    in_unreleased = False
    bullets: set[str] = set()
    for line in content.splitlines():
        stripped = line.strip()
        if stripped == UNRELEASED_HEADING:
            in_unreleased = True
            continue
        if in_unreleased and stripped.startswith(SECTION_PREFIX):
            break
        if in_unreleased and stripped.startswith(("* ", "- ", "**")):
            bullets.add(stripped)
    return bullets


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
