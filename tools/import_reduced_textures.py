"""Install user-reduced base textures without resampling their pixels.

Usage: python tools/import_reduced_textures.py <texture-directory>
Then run finish_base_textures.py to apply alpha cleanup and final goggles UVs.
"""
from pathlib import Path
import base64
import hashlib
import json
import shutil
import struct
import sys
import zlib

from generate_wand_geometry import read_rgba

ROOT = Path(__file__).resolve().parents[1]
TEXTURES = ROOT / 'src/main/resources/assets/astral_repository/textures'
PADDING = {
    'block/astral_cluster.png': (16, 16, 1, 1),
    'block/large_astral_bud.png': (16, 16, 1, 3),
    'block/medium_astral_bud.png': (16, 16, 2, 7),
    'block/small_astral_bud.png': (16, 16, 4, 11),
    'item/astral_goggles.png': (57, 57, 0, 0),
    'item/moon_attunement.png': (35, 35, 0, 0),
}


def encode_rgba(rows):
    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data))
    raw = b''.join(b'\0' + bytes(channel for pixel in row for channel in pixel) for row in rows)
    return (b'\x89PNG\r\n\x1a\n'
            + chunk(b'IHDR', struct.pack('>IIBBBBB', len(rows[0]), len(rows), 8, 6, 0, 0, 0))
            + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b''))


def main(source):
    records = []
    for path in sorted(source.rglob('*.png')):
        relative = path.relative_to(source).as_posix()
        destination = TEXTURES / relative
        if not destination.is_file():
            raise ValueError(f'Unknown texture: {relative}')
        data = path.read_bytes()
        original = read_rgba(data)
        height, width = len(original), len(original[0])
        canvas_width, canvas_height, left, top = PADDING.get(relative, (width, height, 0, 0))
        if width + left > canvas_width or height + top > canvas_height:
            raise ValueError(f'Texture does not fit padding specification: {relative}')
        if (canvas_width, canvas_height) == (width, height):
            shutil.copyfile(path, destination)
        else:
            padded = [[(0, 0, 0, 0)] * canvas_width for _ in range(canvas_height)]
            for y, row in enumerate(original):
                padded[y + top][left:left + width] = row
            destination.write_bytes(encode_rgba(padded))
        actual = read_rgba(destination.read_bytes())
        for y in range(canvas_height):
            for x in range(canvas_width):
                expected = original[y - top][x - left] if left <= x < left + width and top <= y < top + height else (0, 0, 0, 0)
                assert actual[y][x] == expected, (relative, x, y)
        records.append({'texture': relative, 'source_size': [width, height],
                        'canvas_size': [canvas_width, canvas_height], 'offset': [left, top],
                        'source_sha256': hashlib.sha256(data).hexdigest(),
                        'installed_sha256': hashlib.sha256(destination.read_bytes()).hexdigest()})

    # The supplied atlas has a 29-pixel left column and a 28-pixel right column.
    # Its two rows are each 28 pixels high. The last canvas row is padding.
    model_path = ROOT / 'src/main/resources/assets/astral_repository/models/item/astral_goggles_geometry.json'
    model = json.loads(model_path.read_text())
    for element in model['elements']:
        for face in element['faces'].values():
            right, bottom = face['uv'][0] > 0, face['uv'][1] > 0
            pixel_uv = [29 if right else 0, 28 if bottom else 0, 57 if right else 29, 56 if bottom else 28]
            face['uv'] = [value * 16 / 57 for value in pixel_uv]
    model_path.write_text(json.dumps(model, indent=2) + '\n')
    editable_path = ROOT / 'art/blockbench/astral_goggles.bbmodel'
    editable = json.loads(editable_path.read_text())
    editable['resolution'] = {'width': 57, 'height': 57}
    for texture in editable['textures']:
        for key in ('width', 'height', 'uv_width', 'uv_height'):
            texture[key] = 57
        texture['source'] = 'data:image/png;base64,' + base64.b64encode((TEXTURES / 'item/astral_goggles.png').read_bytes()).decode()
    assert len(editable['elements']) == len(model['elements'])
    for element, runtime in zip(editable['elements'], model['elements']):
        for side, face in element['faces'].items():
            face['uv'] = [round(value * 57 / 16, 8) for value in runtime['faces'][side]['uv']]
    editable_path.write_text(json.dumps(editable, indent=2) + '\n')
    manifest = ROOT / 'art/reduced-textures.json'
    manifest.write_text(json.dumps(records, indent=2) + '\n')
    print(f'Installed {len(records)} textures; every source RGBA pixel and transparent padding verified.')


if __name__ == '__main__':
    main(Path(sys.argv[1]))
