#!/usr/bin/env python3
import argparse
import re
import subprocess
import sys
import tomllib
from pathlib import Path


def extract_r8_version(toml_str: str) -> str | None:
    data = tomllib.loads(toml_str)
    versions = data.get("versions", {})
    libraries = data.get("libraries", {})

    if "r8" in versions:
        return str(versions["r8"])

    if "r8" in libraries:
        val = libraries["r8"]
        if isinstance(val, str):
            parts = val.split(":")
            if len(parts) >= 3:
                return parts[-1]
        elif isinstance(val, dict):
            if "version" in val:
                return str(val["version"])
            if "version.ref" in val and val["version.ref"] in versions:
                return str(versions[val["version.ref"]])

    return None


def get_pr_info_from_gh() -> tuple[str | None, str | None]:
    try:
        proc = subprocess.run(
            ["gh", "pr", "view", "--json", "number,url", "-q", ".number + \" \" + .url"],
            capture_output=True,
            text=True,
            check=True,
        )
        parts = proc.stdout.strip().split()
        if len(parts) == 2:
            return parts[0], parts[1]
    except Exception:
        pass
    return None, None


def update_changelog(content: str, base_r8_ver: str, new_r8_ver: str, pr_number: str | None, pr_url: str | None) -> str:
    unreleased_match = re.search(r"(##\s*\[Unreleased\][^\n]*\n+)", content)
    if not unreleased_match:
        print("Warning: [Unreleased] section not found in CHANGELOG.md")
        return content

    unreleased_start = unreleased_match.end()
    next_release_match = re.search(r"\n##\s*\[", content[unreleased_start:])
    if next_release_match:
        unreleased_end = unreleased_start + next_release_match.start()
        unreleased_block = content[unreleased_start:unreleased_end]
        after_block = content[unreleased_end:]
    else:
        unreleased_block = content[unreleased_start:]
        after_block = ""

    pr_suffix = f" ([#{pr_number}]({pr_url}))" if pr_number and pr_url else ""

    r8_line_pattern = re.compile(
        r"^[ \t]*-[ \t]*Bump default R8 from `(?P<from>[^`]+)` to `(?P<to>[^`]+)`\.(?:[^\n]*)?",
        re.MULTILINE,
    )

    match = r8_line_pattern.search(unreleased_block)
    if match:
        from_ver = match.group("from")
        new_line = f"- Bump default R8 from `{from_ver}` to `{new_r8_ver}`.{pr_suffix}"
        unreleased_block = r8_line_pattern.sub(new_line, unreleased_block, count=1)
    else:
        new_line = f"- Bump default R8 from `{base_r8_ver}` to `{new_r8_ver}`.{pr_suffix}"
        changed_header_match = re.search(r"(###\s*Changed\s*\n+)", unreleased_block)
        if changed_header_match:
            header_end = changed_header_match.end()
            next_section_match = re.search(r"\n(###\s+\w+|##\s*\[)", unreleased_block[header_end:])
            if next_section_match:
                changed_end = header_end + next_section_match.start()
                changed_content = unreleased_block[header_end:changed_end].rstrip()
                rest = unreleased_block[changed_end:].lstrip("\n")
                unreleased_block = (
                    unreleased_block[:header_end]
                    + (changed_content + "\n" if changed_content else "")
                    + new_line
                    + "\n\n"
                    + rest
                )
            else:
                changed_content = unreleased_block[header_end:].rstrip()
                unreleased_block = (
                    unreleased_block[:header_end]
                    + (changed_content + "\n" if changed_content else "")
                    + new_line
                    + "\n\n"
                )
        else:
            if unreleased_block.strip():
                unreleased_block = f"### Changed\n\n{new_line}\n\n" + unreleased_block.lstrip()
            else:
                unreleased_block = f"### Changed\n\n{new_line}\n\n"

    return content[:unreleased_start] + unreleased_block + after_block


def main() -> None:
    parser = argparse.ArgumentParser(description="Update CHANGELOG.md for Renovate R8 version bumps.")
    parser.add_argument("--base-ref", default="origin/main", help="Base ref or branch name to compare against")
    parser.add_argument("--pr-number", help="Pull Request number")
    parser.add_argument("--pr-url", help="Pull Request URL")
    args = parser.parse_args()

    pr_number = args.pr_number
    pr_url = args.pr_url
    if not pr_number or not pr_url:
        gh_number, gh_url = get_pr_info_from_gh()
        pr_number = pr_number or gh_number
        pr_url = pr_url or gh_url

    if pr_number and not pr_url:
        pr_url = f"https://github.com/GradleUp/shadow/pull/{pr_number}"

    toml_path = Path("gradle/libs.versions.toml")
    if not toml_path.exists():
        print(f"Error: {toml_path} does not exist.")
        sys.exit(1)

    head_content = toml_path.read_text(encoding="utf-8")
    head_r8_version = extract_r8_version(head_content)
    if not head_r8_version:
        print("Error: Could not extract R8 version from gradle/libs.versions.toml.")
        sys.exit(1)

    merge_base_proc = subprocess.run(
        ["git", "merge-base", args.base_ref, "HEAD"],
        capture_output=True,
        text=True,
    )
    if merge_base_proc.returncode == 0 and merge_base_proc.stdout.strip():
        compare_ref = merge_base_proc.stdout.strip()
    else:
        compare_ref = args.base_ref

    git_show_cmd = ["git", "show", f"{compare_ref}:gradle/libs.versions.toml"]
    proc = subprocess.run(git_show_cmd, capture_output=True, text=True)
    if proc.returncode != 0:
        print(f"Warning: Failed to fetch base toml from {compare_ref}: {proc.stderr}")
        base_r8_version = head_r8_version
    else:
        base_r8_version = extract_r8_version(proc.stdout) or head_r8_version

    if base_r8_version == head_r8_version:
        print(f"R8 version has not changed ({head_r8_version}).")
        return

    print(f"Detected R8 version bump: {base_r8_version} -> {head_r8_version}")
    if pr_number:
        print(f"PR: #{pr_number} ({pr_url})")

    changelog_path = Path("CHANGELOG.md")
    if not changelog_path.exists():
        print("Error: CHANGELOG.md does not exist.")
        sys.exit(1)

    original_content = changelog_path.read_text(encoding="utf-8")
    updated_content = update_changelog(
        original_content,
        base_r8_ver=base_r8_version,
        new_r8_ver=head_r8_version,
        pr_number=pr_number,
        pr_url=pr_url,
    )

    if updated_content != original_content:
        changelog_path.write_text(updated_content, encoding="utf-8")
        print("Successfully updated CHANGELOG.md")
    else:
        print("No changes needed in CHANGELOG.md")


if __name__ == "__main__":
    main()
