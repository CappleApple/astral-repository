"""Remove partial-alpha pixels and author goggles UVs and their pixel item icon.

Run after import_reduced_textures.py. Fully opaque pixels in supplied textures
are preserved. Near-opaque artwork (alpha 240-254) becomes opaque; fainter
pixels are deleted. The goggles use render-material translucency for lenses.
"""
from pathlib import Path
import base64
import hashlib
import json

from generate_wand_geometry import read_rgba, build_geometry
from import_reduced_textures import encode_rgba

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/astral_repository'


def write_json(path, data):
    path.write_text(json.dumps(data, indent=2) + '\n')


def main():
    manifest = ROOT / 'art/reduced-textures.json'
    records = json.loads(manifest.read_text())
    removed = 0
    normalized = 0
    for record in records:
        path = ASSETS / 'textures' / record['texture']
        rows = read_rgba(path.read_bytes())
        count = sum(0 < pixel[3] < 255 for row in rows for pixel in row)
        if count:
            def clean(pixel):
                return (0, 0, 0, 0) if 0 < pixel[3] < 240 else (*pixel[:3], 255) if 240 <= pixel[3] < 255 else pixel
            cleaned = [[clean(pixel) for pixel in row] for row in rows]
            path.write_bytes(encode_rgba(cleaned))
            actual = read_rgba(path.read_bytes())
            assert all(actual[y][x] == clean(p)
                       for y, row in enumerate(rows) for x, p in enumerate(row))
            deleted = sum(0 < p[3] < 240 for row in rows for p in row)
            promoted = count - deleted
            record['removed_partial_alpha_pixels'] = record.get('removed_partial_alpha_pixels', 0) + deleted
            record['normalized_near_opaque_pixels'] = record.get('normalized_near_opaque_pixels', 0) + promoted
            record['opaque_alpha_threshold'] = 240
            removed += deleted
            normalized += promoted
        assert all(p[3] in (0, 255) for row in read_rgba(path.read_bytes()) for p in row)
        record['installed_sha256'] = hashlib.sha256(path.read_bytes()).hexdigest()
    write_json(manifest, records)

    model_path = ASSETS / 'models/item/astral_goggles_geometry.json'
    model = json.loads(model_path.read_text())
    for element in model['elements']:
        name = element['name']
        tile_x = 29 if ('strap' in name or 'hinge' in name or name == 'bridge') else 0
        tile_y = 28 if ('gem' in name or 'hinge' in name or name in ('bridge', 'strap_buckle')) else 0
        x0, y0, z0 = element['from']
        x1, y1, z1 = element['to']
        projections = {
            'north': [16-x1, 16-y1, 16-x0, 16-y0],
            'south': [x0, 16-y1, x1, 16-y0],
            'east': [16-z1, 16-y1, 16-z0, 16-y0],
            'west': [z0, 16-y1, z1, 16-y0],
            'up': [x0, z0, x1, z1],
            'down': [x0, 16-z1, x1, 16-z0],
        }
        # Consistent pixel density, projected in model space. Adjacent lens/rim
        # pieces share UVs at their edges; thin faces no longer squash a full tile.
        for side, face in element['faces'].items():
            pixel_uv = [2 + value * 1.5 + (tile_x if i % 2 == 0 else tile_y)
                        for i, value in enumerate(projections[side])]
            face['uv'] = [round(value * 16 / 57, 9) for value in pixel_uv]
    write_json(model_path, model)
    editable_path = ROOT / 'art/blockbench/astral_goggles.bbmodel'
    editable = json.loads(editable_path.read_text())
    for element, runtime in zip(editable['elements'], model['elements']):
        assert element['name'] == runtime['name']
        for side, face in element['faces'].items():
            face['uv'] = [round(value * 57 / 16, 7) for value in runtime['faces'][side]['uv']]
    editable['textures'][0]['source'] = 'data:image/png;base64,' + base64.b64encode((ASSETS / 'textures/item/astral_goggles.png').read_bytes()).decode()
    write_json(editable_path, editable)

    # Hand-authored 16-pixel silhouette. Palette and lens facets reuse the
    # supplied atlas; the icon is independent of the equipped armor mesh.
    atlas = read_rgba((ASSETS / 'textures/item/astral_goggles.png').read_bytes())
    pixels = [[(0, 0, 0, 0)] * 16 for _ in range(16)]
    palette = {'#': (15, 17, 38, 255), 'f': (43, 52, 87, 255),
               'h': (86, 108, 155, 255), 's': (65, 29, 84, 255),
               'b': (157, 157, 192, 255)}
    silhouette = [
        '................',
        '................',
        '................',
        '................',
        '..hhhhh..hhhhh..',
        '.hf###ffhf###fh.',
        '.fLLLLLffLLLLLf.',
        'sfLLLLLbbLLLLLfs',
        'sfLLLLLbbLLLLLfs',
        '.fLLLLLffLLLLLf.',
        '.hf###ffhf###fh.',
        '..fffff..fffff..',
        '................',
        '................',
        '................',
        '................',
    ]
    mask_rows = {}
    for y, row in enumerate(silhouette):
        assert len(row) == 16
        for x, char in enumerate(row):
            if char == 'L':
                # Two different 5x4 crystal patches, copied without interpolation.
                pixels[y][x] = atlas[36 + y - 6][(x - 2 if x < 8 else x - 9) + (6 if x < 8 else 17)]
                mask_rows.setdefault(str(y), []).append([x, x])
            elif char != '.':
                pixels[y][x] = palette[char]
    icon = ASSETS / 'textures/item/astral_goggles_icon.png'
    icon.write_bytes(encode_rgba(pixels))
    mask = ROOT / 'tools/astral_goggles_lens_mask.json'
    write_json(mask, {'width': 16, 'height': 16, 'rows': mask_rows})
    elements = build_geometry(icon, mask)
    for element in elements:
        for face in element['faces'].values():
            face['texture'] = '#goggles'
    write_json(ASSETS / 'models/item/astral_goggles_icon_geometry.json', {
        'parent': 'minecraft:block/block', 'ambientocclusion': False, 'gui_light': 'front',
        'textures': {'goggles': 'astral_repository:item/astral_goggles_icon',
                     'particle': 'astral_repository:item/astral_goggles_icon'},
        'elements': elements,
    })
    print(f'Deleted {removed} faint pixels, made {normalized} near-opaque pixels opaque; remapped armor faces and built a one-pixel-thick goggles icon.')


if __name__ == '__main__':
    main()
