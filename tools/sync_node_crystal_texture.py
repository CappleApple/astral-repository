"""Synchronize the existing network crystal PNG into editable Blockbench sources.

No raster pixels or model UVs are changed. --check verifies embedded copies and
texture provenance without writing.
"""
import argparse
import base64
import hashlib
import json
from pathlib import Path

from generate_wand_geometry import read_rgba

ROOT = Path(__file__).resolve().parents[1]
TEXTURE = "block/node_crystal.png"


def synchronize(check):
    image = (ROOT / "src/main/resources/assets/astral_repository/textures" / TEXTURE).read_bytes()
    pixels = read_rgba(image)
    assert len(pixels) == 16 and all(len(row) == 16 for row in pixels)
    assert all(pixel[3] == 255 for row in pixels for pixel in row), "The crystal material must remain opaque"
    embedded = "data:image/png;base64," + base64.b64encode(image).decode()
    count = 0
    for path in sorted((ROOT / "art/blockbench").glob("*.bbmodel")):
        model = json.loads(path.read_text(encoding="utf-8"))
        textures = [texture for texture in model.get("textures", []) if texture["name"] == "node_crystal.png"]
        if not textures:
            continue
        for texture in textures:
            assert all(texture[key] == 16 for key in ("width", "height", "uv_width", "uv_height"))
            if check:
                assert texture["source"] == embedded, (path, "Embedded crystal material differs")
            else:
                texture["source"] = embedded
        if not check:
            path.write_text(json.dumps(model, ensure_ascii=False, separators=(',', ':')) + "\n", encoding="utf-8")
        count += 1
    assert count == 12, "Expected all twelve current and retained network model sources"
    manifest = ROOT / "art/reduced-textures.json"
    records = json.loads(manifest.read_text(encoding="utf-8"))
    record = next(record for record in records if record["texture"] == TEXTURE)
    digest = hashlib.sha256(image).hexdigest()
    if check:
        assert record.get("installed_sha256") == digest, "Texture provenance differs"
    else:
        record["installed_sha256"] = digest
        record.pop("replacement_source", None)
        manifest.write_text(json.dumps(records, indent=2) + "\n", encoding="utf-8")
    print(f"{'Verified' if check else 'Updated'} {count} embedded network crystal textures; existing opaque 16x16 artwork preserved exactly.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    synchronize(parser.parse_args().check)
