#!/usr/bin/env python3
"""Keep labeled actions on the app's shared button components."""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/com/poyal/perilog"
TOKENS = re.compile(r'"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|/\*[\s\S]*?\*/|//[^\n]*')
BUTTONS = re.compile(r"\b(TextButton|Button|OutlinedButton|FilledTonalButton|ElevatedButton)\s*\(")


def violations(relative_path: str, source: str) -> list[str]:
    # Keep line numbers while ignoring comments and displayed strings.
    code = TOKENS.sub(lambda m: re.sub(r"[^\n]", " ", m.group()), source)
    errors = []
    for match in BUTTONS.finditer(code):
        if relative_path == "ui/Components.kt" and match[1] != "TextButton":
            continue
        line = code.count("\n", 0, match.start()) + 1
        errors.append(f"{relative_path}:{line}: {match[1]} 대신 Action, SecondaryButton, SmallButton을 사용하세요.")
    return errors


def main() -> int:
    files = sorted(SOURCE.rglob("*.kt"))
    errors = [error for path in files
              for error in violations(path.relative_to(SOURCE).as_posix(), path.read_text())]
    if errors:
        print("\n".join(errors), file=sys.stderr)
        return 1
    print(f"UI button conventions verified ({len(files)} source files).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
