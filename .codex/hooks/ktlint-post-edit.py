#!/usr/bin/env python3
"""Codex PostToolUse hook that formats Kotlin modules touched by apply_patch."""

from __future__ import annotations

import json
import os
import re
import subprocess
import sys
from pathlib import Path


def repo_root(cwd: str) -> Path:
    result = subprocess.run(
        ["git", "-C", cwd, "rev-parse", "--show-toplevel"],
        check=True,
        capture_output=True,
        text=True,
    )
    return Path(result.stdout.strip())


def changed_paths(payload: dict, root: Path) -> set[Path]:
    candidates: set[str] = set()
    for key in ("file_path", "path"):
        value = payload.get(key)
        if isinstance(value, str):
            candidates.add(value)
    tool_input = payload.get("tool_input") or {}
    patch = tool_input.get("command", "") if isinstance(tool_input, dict) else ""
    candidates.update(re.findall(r"^\*\*\* (?:Add|Update) File: (.+)$", patch, re.MULTILINE))
    resolved: set[Path] = set()
    for candidate in candidates:
        path = Path(candidate)
        if not path.is_absolute():
            path = root / path
        try:
            path = path.resolve()
            path.relative_to(root.resolve())
        except (OSError, ValueError):
            continue
        if path.suffix in {".kt", ".kts"} and path.exists():
            resolved.add(path)
    return resolved


def module_for(path: Path, root: Path) -> tuple[str, str] | None:
    relative = path.relative_to(root).as_posix()
    match = re.match(r"apps/(scrybe|shelf-snap|price-drop)/(.+?)/src/", relative)
    if not match:
        return None
    app, module_path = match.groups()
    return app, ":" + module_path.replace("/", ":")


def emit_warning(messages: list[str]) -> None:
    if not messages:
        return
    print(
        json.dumps(
            {
                "hookSpecificOutput": {
                    "hookEventName": "PostToolUse",
                    "additionalContext": "\n".join(messages),
                }
            }
        )
    )


def main() -> int:
    try:
        payload = json.load(sys.stdin)
        root = repo_root(payload.get("cwd") or os.getcwd())
    except (json.JSONDecodeError, OSError, subprocess.SubprocessError) as exc:
        emit_warning([f"Codex Kotlin formatter hook could not initialize: {exc}"])
        return 0

    paths = changed_paths(payload, root)
    modules = {item for path in paths if (item := module_for(path, root))}
    if not modules:
        return 0

    staged = subprocess.run(
        ["git", "-C", str(root), "diff", "--cached", "--name-only"],
        check=False,
        capture_output=True,
        text=True,
    ).stdout.splitlines()
    staged_paths = {item.replace("\\", "/") for item in staged}
    warnings: list[str] = []
    env = os.environ.copy()
    env["GRADLE_USER_HOME"] = str(root / ".gradle-user-home")
    for app, module in sorted(modules):
        app_root = root / "apps" / app
        wrapper = app_root / ("gradlew.bat" if os.name == "nt" else "gradlew")
        cache = root / ".gradle-project-cache" / app
        cache.mkdir(parents=True, exist_ok=True)
        result = subprocess.run(
            [
                str(wrapper),
                "-p",
                str(app_root),
                f"{module}:ktlintFormat",
                "--project-cache-dir",
                str(cache),
                "-Pkotlin.compiler.execution.strategy=in-process",
                "--no-build-cache",
                "--no-configuration-cache",
                "--no-daemon",
                "--console=plain",
                "-q",
            ],
            cwd=root,
            env=env,
            check=False,
            capture_output=True,
            text=True,
        )
        if result.returncode:
            detail = (result.stderr or result.stdout).strip().splitlines()
            suffix = f" ({detail[-1]})" if detail else ""
            warnings.append(f"ktlintFormat failed for {app} {module}{suffix}")

    for path in paths:
        relative = path.relative_to(root).as_posix()
        if relative in staged_paths:
            subprocess.run(["git", "-C", str(root), "add", "--", str(path)], check=False)
    emit_warning(warnings)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
