#!/usr/bin/env python3
"""Publish the three already-verified assets; never build or replace remote bytes."""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def packaged(root, tag):
    if not re.fullmatch(r"v(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)", tag):
        raise ValueError("Invalid release tag")
    version = tag[1:]
    names = [f"perilog-{version}.apk", f"perilog-{version}-screenshots.zip"]
    sums_name = f"perilog-{version}.sha256"
    folder = root / ".release"
    if {p.name for p in folder.iterdir()} != set(names + [sums_name]):
        raise ValueError("The release directory must contain exactly the three public files")
    files = {name: (folder / name).read_bytes() for name in names + [sums_name]}
    if any(not data for data in files.values()):
        raise ValueError("Empty release asset")
    lines = files[sums_name].decode("utf-8").splitlines()
    entries = [re.fullmatch(r"([a-f0-9]{64})  ([^/\\\s]+)", line) for line in lines]
    if len(entries) != 2 or any(entry is None for entry in entries):
        raise ValueError("Malformed checksums")
    checksums = {entry[2]: entry[1] for entry in entries}
    if set(checksums) != set(names):
        raise ValueError("Unexpected or duplicate checksum entries")
    for name in names:
        if checksums[name] != sha256(files[name]):
            raise ValueError(f"Local checksum mismatch: {name}")
    notes = (root / "docs" / "releases" / f"{tag}.md").read_text()
    if not notes.strip():
        raise ValueError("Missing release notes")
    return files, notes


def verify_asset(client, asset, expected):
    name = asset["name"]
    if asset.get("state") != "uploaded" or asset.get("size") != len(expected):
        raise ValueError(f"Incomplete or conflicting asset: {name}")
    if asset.get("digest") != f"sha256:{sha256(expected)}":
        raise ValueError(f"Remote digest mismatch: {name}")
    if client.download(asset) != expected:
        raise ValueError(f"Downloaded bytes differ: {name}")


def asset_map(assets, names):
    result = {asset["name"]: asset for asset in assets}
    if len(result) != len(assets) or not set(result) <= set(names):
        raise ValueError("Unexpected or duplicate remote assets")
    return result


def publish(client, tag, commit, files, notes):
    if client.tag_commit(tag) != commit:
        raise ValueError("Remote tag differs from the checked-out commit")
    release = client.release(tag)
    if release:
        if release["tag_name"] != tag or release.get("prerelease"):
            raise ValueError("Existing release does not match")
        existing = asset_map(client.assets(release), files)
        # Reject all conflicts before making any remote changes.
        for name, asset in existing.items():
            verify_asset(client, asset, files[name])
    else:
        existing = {}
        release = client.create(tag, notes)
    for name, data in files.items():
        if name not in existing:
            client.upload(tag, name, data)
    current = asset_map(client.assets(release), files)
    if set(current) != set(files):
        raise ValueError("Release assets are missing")
    for name, data in files.items():
        verify_asset(client, current[name], data)
    if client.tag_commit(tag) != commit:
        raise ValueError("Remote tag changed during upload")
    if release["draft"]:
        client.make_public(release)
    print(f"Verified {len(files)} assets and published {tag}; no rebuild performed.")


class GitHub:
    def __init__(self, repo):
        if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repo):
            raise ValueError("Invalid repository")
        self.repo = repo
        self.base = f"repos/{repo}"

    def api(self, path, method="GET", body=None, missing=False, binary=False):
        command = ["gh", "api", f"{self.base}/{path}", "--method", method]
        if binary:
            command += ["-H", "Accept: application/octet-stream"]
        if body is not None:
            command += ["--input", "-"]
        result = subprocess.run(command, input=json.dumps(body).encode() if body is not None else None,
                                capture_output=True, check=False)
        if result.returncode:
            if missing and b"HTTP 404" in result.stderr:
                return None
            raise RuntimeError(result.stderr.decode(errors="replace"))
        return result.stdout if binary else json.loads(result.stdout or b"null")

    def tag_commit(self, tag):
        obj = self.api(f"git/ref/tags/{tag}")["object"]
        for _ in range(8):
            if obj["type"] == "commit":
                return obj["sha"]
            if obj["type"] != "tag":
                break
            obj = self.api(f"git/tags/{obj['sha']}")["object"]
        raise ValueError("Release tag must resolve to a commit")

    def release(self, tag):
        return self.api(f"releases/tags/{tag}", missing=True)

    def assets(self, release):
        assets = []
        page = 1
        while True:
            batch = self.api(f"releases/{release['id']}/assets?per_page=100&page={page}")
            assets.extend(batch)
            if len(batch) < 100:
                return assets
            page += 1

    def download(self, asset):
        return self.api(f"releases/assets/{asset['id']}", binary=True)

    def create(self, tag, notes):
        return self.api("releases", "POST", {"tag_name": tag, "name": f"페리로그 {tag}", "body": notes, "draft": True, "prerelease": False})

    def upload(self, tag, name, data):
        with tempfile.TemporaryDirectory(prefix="perilog-publish-") as directory:
            path = Path(directory) / name
            path.write_bytes(data)  # Exact preflight bytes, independent of later source edits.
            subprocess.run(["gh", "release", "upload", tag, str(path), "--repo", self.repo], check=True)

    def make_public(self, release):
        self.api(f"releases/{release['id']}", "PATCH", {"draft": False, "make_latest": "true"})


if __name__ == "__main__":
    root = Path(__file__).resolve().parent.parent
    tag = os.environ["RELEASE_TAG"]
    files, notes = packaged(root, tag)
    commit = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=root, text=True).strip()
    publish(GitHub(os.environ["GH_REPO"]), tag, commit, files, notes)
