import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("publisher", Path(__file__).parents[1] / "publish-release.py")
publisher = importlib.util.module_from_spec(spec)
spec.loader.exec_module(publisher)


class FakeGitHub:
    def __init__(self, fail_upload=None):
        self.current = None
        self.files = {}
        self.uploads = []
        self.published = False
        self.fail_upload = fail_upload
        self.corrupt_download = False
        self.commit = "source"

    def tag_commit(self, tag): return self.commit
    def release(self, tag): return self.current
    def create(self, tag, notes):
        self.current = {"id": 1, "tag_name": tag, "draft": True, "prerelease": False}
        return self.current
    def assets(self, release):
        return [{"id": name, "name": name, "size": len(data), "state": "uploaded", "digest": "sha256:" + publisher.sha256(data)} for name, data in self.files.items()]
    def download(self, asset):
        return b"corrupt" if self.corrupt_download else self.files[asset["name"]]
    def upload(self, tag, name, data):
        if name == self.fail_upload: raise OSError("interrupted")
        self.files[name] = data
        self.uploads.append(name)
    def make_public(self, release):
        self.published = True
        self.current["draft"] = False


class PublishTest(unittest.TestCase):
    def setUp(self):
        self.files = {"perilog-2.0.0.apk": b"apk", "perilog-2.0.0-screenshots.zip": b"zip", "perilog-2.0.0.sha256": b"checksums"}
    def run_publish(self, client):
        publisher.publish(client, "v2.0.0", "source", self.files, "notes")
    def test_publish_and_idempotent_retry(self):
        client = FakeGitHub()
        self.run_publish(client)
        self.run_publish(client)
        self.assertTrue(client.published)
        self.assertEqual(len(client.uploads), 3)
    def test_interruption_leaves_draft_and_retry_only_uploads_missing(self):
        client = FakeGitHub("perilog-2.0.0.sha256")
        with self.assertRaises(OSError): self.run_publish(client)
        self.assertFalse(client.published)
        client.fail_upload = None
        self.run_publish(client)
        self.assertEqual(len(client.uploads), 3)
    def test_corrupt_redownload_never_publishes(self):
        client = FakeGitHub()
        client.corrupt_download = True
        with self.assertRaises(ValueError): self.run_publish(client)
        self.assertTrue(client.current["draft"])
    def test_existing_conflict_preserved_before_any_new_upload(self):
        client = FakeGitHub()
        client.create("v2.0.0", "notes")
        client.files["perilog-2.0.0.apk"] = b"different"
        with self.assertRaises(ValueError): self.run_publish(client)
        self.assertEqual(client.files, {"perilog-2.0.0.apk": b"different"})
        self.assertEqual(client.uploads, [])
    def test_changed_tag_is_rejected(self):
        client = FakeGitHub()
        client.commit = "other"
        with self.assertRaises(ValueError): self.run_publish(client)
        self.assertIsNone(client.current)
    def test_bad_metadata_digest_is_rejected(self):
        client = FakeGitHub()
        original = client.assets
        client.assets = lambda release: [dict(a, digest="sha256:" + "0" * 64) for a in original(release)]
        with self.assertRaises(ValueError): self.run_publish(client)
        self.assertFalse(client.published)
    def test_exact_packaged_files_and_no_keys(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / ".release").mkdir()
            (root / "docs/releases").mkdir(parents=True)
            (root / "docs/releases/v2.0.0.md").write_text("notes")
            names = list(self.files)[:2]
            for name in names: (root / ".release" / name).write_bytes(self.files[name])
            sums = "".join(f"{publisher.sha256(self.files[n])}  {n}\n" for n in names)
            (root / ".release/perilog-2.0.0.sha256").write_text(sums)
            self.assertEqual(len(publisher.packaged(root, "v2.0.0")[0]), 3)
            (root / ".release/password").write_text("not-a-real-secret")
            with self.assertRaises(ValueError): publisher.packaged(root, "v2.0.0")

if __name__ == "__main__": unittest.main()
