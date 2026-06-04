#!/usr/bin/env python3
"""
Verify that external dependency download URLs referenced in build files are
reachable before a commit is accepted.

Checks:
  - https:// asset URLs inside Exec task commandLine() blocks in *.gradle.kts
  - JitPack artifact POM URLs for any com.github.* entry in libs.versions.toml

Exits 0 if all URLs are reachable, 1 otherwise.
"""
import re
import subprocess
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[3]
ANDROID_DIR = REPO_ROOT / "apps" / "scrybe"
LIBS_TOML = ANDROID_DIR / "gradle" / "libs.versions.toml"

ASSET_EXTENSIONS = (r"\.aar", r"\.jar", r"\.zip", r"\.tar\.bz2", r"\.tar\.gz")
ASSET_RE = re.compile(
    r'https://\S+(?:' + "|".join(ASSET_EXTENSIONS) + r')(?=["\s,)\\]|$)'
)


def head_url(url: str) -> tuple[bool, int]:
    """Return (reachable, http_status). Uses curl -I -L to follow redirects."""
    result = subprocess.run(
        [
            "curl", "-s", "-I", "-L",
            "--connect-timeout", "15",
            "--max-time", "20",
            "-o", "/dev/null",
            "-w", "%{http_code}",
            url,
        ],
        capture_output=True,
        text=True,
    )
    try:
        status = int(result.stdout.strip())
    except ValueError:
        return False, 0
    return status < 400, status


def find_exec_task_urls() -> list[tuple[str, str]]:
    """Extract download URLs from Exec task commandLine() blocks and ProcessBuilder calls."""
    results = []
    for build_file in ANDROID_DIR.rglob("*.gradle.kts"):
        content = build_file.read_text()
        in_exec_block = False
        for line in content.splitlines():
            if "registering(Exec::class)" in line or "register(Exec::class)" in line:
                in_exec_block = True
            # Also catch ProcessBuilder download calls in settings/build scripts
            if "ProcessBuilder" in line or in_exec_block:
                for url in ASSET_RE.findall(line):
                    results.append((str(build_file.relative_to(REPO_ROOT)), url))
            if in_exec_block and line.strip() == "}":
                in_exec_block = False
    return results


def find_jitpack_dep_urls() -> list[tuple[str, str]]:
    """Construct JitPack POM URLs for com.github.* entries in libs.versions.toml."""
    if not LIBS_TOML.exists():
        return []
    content = LIBS_TOML.read_text()
    versions = dict(re.findall(r'^([\w\-]+)\s*=\s*"([^"]+)"', content, re.MULTILINE))
    results = []
    for line in content.splitlines():
        m = re.search(
            r'group\s*=\s*"(com\.github\.[^"]+)".*?'
            r'name\s*=\s*"([^"]+)".*?'
            r'version\.ref\s*=\s*"([^"]+)"',
            line,
        )
        if m:
            group, name, ver_ref = m.groups()
            version = versions.get(ver_ref, "")
            if version:
                path = group.replace(".", "/")
                pom_url = (
                    f"https://jitpack.io/{path}/{name}/{version}/{name}-{version}.pom"
                )
                results.append((f"{group}:{name}:{version}", pom_url))
    return results


def main() -> int:
    checks = find_exec_task_urls() + find_jitpack_dep_urls()
    if not checks:
        return 0

    failures = []
    for label, url in checks:
        reachable, status = head_url(url)
        if not reachable:
            failures.append(f"  {label}\n    {url}  →  HTTP {status or 'connection failed'}")

    if failures:
        print("UNREACHABLE DEPENDENCY URLS:", file=sys.stderr)
        for msg in failures:
            print(msg, file=sys.stderr)
        return 1

    return 0


if __name__ == "__main__":
    sys.exit(main())
