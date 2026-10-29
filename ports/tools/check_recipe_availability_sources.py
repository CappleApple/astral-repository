"""Check the shared availability cache and regression suite in maintained inputs."""

import argparse
import hashlib
import json
from pathlib import Path


FAMILIES = {
    "neoforge-1.21.1": "src",
    "forge-1.20.1": "ports/forge-1.20.1/src",
    "fabric-1.20.1": "ports/fabric-1.20.1/src",
    "fabric-1.21.1": "ports/fabric-1.21.1/src",
    "fabric-modern": "ports/fabric-modern-common/src",
    "neoforge-modern": "ports/neoforge-modern-common/src",
}
FILES = (
    "main/java/com/cappleapple/astralrepository/client/RecipeAvailabilityCache.java",
    "test/java/com/cappleapple/astralrepository/client/RecipeAvailabilityCacheTest.java",
)


def audit(root):
    rows = []
    for relative in FILES:
        expected = None
        for family, source in FAMILIES.items():
            path = root / source / relative
            if not path.is_file():
                raise AssertionError(f"Missing maintained source: {path}")
            content = path.read_text(encoding="utf-8").replace("\r\n", "\n")
            if expected is None:
                expected = content
            elif content != expected:
                raise AssertionError(f"Shared availability source differs: {path}")
            rows.append({
                "family": family,
                "path": path.relative_to(root).as_posix(),
                "normalized_sha256": hashlib.sha256(content.encode("utf-8")).hexdigest(),
            })
    return {"status": "PASS", "maintained_families": len(FAMILIES), "files": rows}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()
    result = audit(args.root.resolve())
    serialized = json.dumps(result, indent=2) + "\n"
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(serialized, encoding="utf-8")
    print(serialized, end="")


if __name__ == "__main__":
    main()
