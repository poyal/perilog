"""Bundle only actual PNG captures referenced by the offline guide; --check never writes."""
import argparse
from pathlib import Path
import re
import shutil

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--check", action="store_true")
args = parser.parse_args()
content = (root / "app/src/main/java/com/poyal/perilog/ui/GuideContent.kt").read_text(encoding="utf-8")
names = sorted(set(re.findall(r'"(\d{2}-[a-z0-9-]+)"', content)))
assert names, "No guide screenshot references"
destination = root / "app/src/main/assets/guide"
if not args.check:
    destination.mkdir(parents=True, exist_ok=True)
total = 0
for name in names:
    source = root / "docs/screenshots" / f"{name}.png"
    target = destination / source.name
    data = source.read_bytes()
    assert data.startswith(b"\x89PNG\r\n\x1a\n"), f"Not an actual PNG capture: {source}"
    if not args.check:
        shutil.copyfile(source, target)
    assert target.read_bytes() == data, f"Guide asset differs from documentation capture: {name}"
    total += len(data)
print(f"{'Verified' if args.check else 'Bundled'} {len(names)} guide captures ({total:,} bytes)")
