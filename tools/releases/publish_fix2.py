#!/usr/bin/env python3
"""Import and publish the previously signed fix2 APK; never build or sign software."""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile

REPO = "Tx13758627780/Orbit-Assistant-zh-CN"
TAG = "v0.8.4.1-zh-CN-OnePlus-Dock-fix2"
NAME = "Orbit-Assistant-v0.8.4.1-zh-CN-OnePlus-Dock-fix2.apk"
EXPECTED_SHA256 = "7fc7f1bdfd5a0b624f1dd90749dfd166b2d431790c33ba3bab07ee918f6eadee"
EXPECTED_SIZE = 39927013
DIRECTORY = Path("releases") / TAG
APK = DIRECTORY / NAME


def run(*args, capture=False):
    return subprocess.run(args, check=True, stdout=subprocess.PIPE if capture else None)


def output(*args):
    return run(*args, capture=True).stdout.decode().strip()


def verify(path):
    data = path.read_bytes()
    if len(data) != EXPECTED_SIZE or hashlib.sha256(data).hexdigest() != EXPECTED_SHA256:
        raise SystemExit("APK size or SHA-256 differs from the verified fix2 artifact")


def main():
    if os.environ.get("GITHUB_REPOSITORY") != REPO or os.environ.get("GITHUB_REF") != "refs/heads/main":
        raise SystemExit("This publisher is restricted to the independent community repository's main branch")
    manifest = json.loads(Path("tools/releases/fix2-publish-ready.json").read_text())
    upload_sha = manifest["upload_commit"]
    if not re.fullmatch(r"[0-9a-f]{40}", upload_sha):
        raise SystemExit("Invalid pinned upload commit")
    head = output("git", "rev-parse", "HEAD")
    remote_head = output("git", "ls-remote", "origin", "refs/heads/main").split()[0]
    if remote_head != head:
        raise SystemExit("main moved; review the new head before restarting publication")
    if not APK.exists():
        run("git", "fetch", "--no-tags", "--depth=1", "origin", upload_sha)
        pieces = manifest["parts"]
        if len(pieces) != 89:
            raise SystemExit("Incomplete APK transport manifest")
        temporary = APK.with_suffix(".apk.part")
        with temporary.open("wb") as destination:
            for index, piece in enumerate(pieces):
                path = f"upload/fix2-apk/part-{index:04d}.bin"
                if piece["path"] != path or not re.fullmatch(r"[0-9a-f]{40}", piece["sha"]):
                    raise SystemExit("Invalid APK part identity")
                data = run("git", "show", f"{upload_sha}:{path}", capture=True).stdout
                blob = hashlib.sha1(b"blob " + str(len(data)).encode() + b"\0" + data).hexdigest()
                if blob != piece["sha"] or len(data) != piece["size"]:
                    raise SystemExit(f"APK part {index} failed validation")
                destination.write(data)
        verify(temporary)
        temporary.replace(APK)
        run("git", "add", "--", str(APK))
        run("git", "-c", "user.name=github-actions[bot]", "-c",
            "user.email=41898282+github-actions[bot]@users.noreply.github.com", "commit", "-m",
            "提交已验证且签名不变的 OnePlus Dock fix2 APK")
        run("git", "push", "origin", "HEAD:refs/heads/main")
        head = output("git", "rev-parse", "HEAD")
    verify(APK)
    # Read first, so a retry can resume an existing draft without replacing a public release.
    listing = json.loads(output("gh", "api", f"repos/{REPO}/releases?per_page=100"))
    existing = next((release for release in listing if release["tag_name"] == TAG), None)
    if existing and existing["target_commitish"] != head:
        raise SystemExit("The existing release targets a different source commit")
    if existing and not existing["draft"]:
        raise SystemExit("Release is already public; verify it rather than replacing its assets")
    if not existing:
        run("gh", "release", "create", TAG, "--draft", "--target", head, "--title",
            "Orbit Assistant 简体中文版 v0.8.4.1 · OnePlus Dock fix2", "--notes-file",
            str(DIRECTORY / "RELEASE_NOTES.zh-CN.md"))
    names = {asset["name"] for asset in (existing or {}).get("assets", [])}
    for path in [APK, DIRECTORY / "SHA256SUMS"]:
        if path.name not in names:
            run("gh", "release", "upload", TAG, str(path))
    # Verify the uploaded bytes before turning the draft into a non-prerelease.
    with tempfile.TemporaryDirectory() as directory:
        run("gh", "release", "download", TAG, "--pattern", NAME, "--dir", directory)
        verify(Path(directory) / NAME)
    run("gh", "release", "edit", TAG, "--draft=false", "--prerelease=false", "--latest=true")
    print(output("gh", "release", "view", TAG, "--json", "url", "--jq", ".url"))
    # Only remove the disposable transport branch if it still points to our pinned commit.
    branch = "refs/heads/upload/fix2-apk"
    tip = output("git", "ls-remote", "origin", branch)
    if tip and tip.split()[0] == upload_sha:
        run("git", "push", "origin", "--delete", "upload/fix2-apk")


if __name__ == "__main__":
    main()
