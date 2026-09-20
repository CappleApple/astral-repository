"""Extrude the exact mod-owned wand bitmap; only its explicit large-crystal mask is tinted.

The PNG is never rewritten. Front/back rectangles partition opaque pixels, and side
faces appear only at the silhouette boundary, never between crystal and untinted pixels.
"""
from pathlib import Path
import json
import struct
import zlib

ROOT = Path(__file__).resolve().parents[1]
SPRITE_PATH = ROOT / 'src/main/resources/assets/astral_repository/textures/item/attunement_wand.png'
MASK_PATH = ROOT / 'tools/attunement_wand_crystal_mask.json'
SPRITE = 'astral_repository:item/attunement_wand'
ALPHA_THRESHOLD = 26


def read_rgba(png):
    """Decode non-interlaced 8-bit RGB/RGBA or indexed PNGs using only the standard library."""
    if png[:8] != b'\x89PNG\r\n\x1a\n':
        raise ValueError('Expected PNG sprite')
    chunks = {}
    offset = 8
    while offset < len(png):
        size = struct.unpack_from('>I', png, offset)[0]
        kind = png[offset + 4:offset + 8]
        chunks.setdefault(kind, bytearray()).extend(png[offset + 8:offset + 8 + size])
        offset += size + 12
    width, height, depth, color, compression, filtering, interlace = struct.unpack('>IIBBBBB', chunks[b'IHDR'])
    if compression or filtering or interlace or not ((color in (2, 6) and depth == 8) or (color == 3 and depth in (1, 2, 4, 8))):
        raise ValueError(f'Unsupported sprite PNG format: {depth=}, {color=}, {interlace=}')
    bits_per_pixel = depth * (4 if color == 6 else 3 if color == 2 else 1)
    row_bytes = (width * bits_per_pixel + 7) // 8
    pixel_bytes = max(1, (bits_per_pixel + 7) // 8)
    raw = zlib.decompress(chunks[b'IDAT'])
    prior = bytearray(row_bytes)
    pixels = []
    cursor = 0
    for _ in range(height):
        mode = raw[cursor]
        row = bytearray(raw[cursor + 1:cursor + 1 + row_bytes])
        cursor += row_bytes + 1
        for index in range(row_bytes):
            left = row[index - pixel_bytes] if index >= pixel_bytes else 0
            above = prior[index]
            upper_left = prior[index - pixel_bytes] if index >= pixel_bytes else 0
            if mode == 0:
                predictor = 0
            elif mode == 1:
                predictor = left
            elif mode == 2:
                predictor = above
            elif mode == 3:
                predictor = (left + above) // 2
            elif mode == 4:
                prediction = left + above - upper_left
                predictor = min((left, above, upper_left), key=lambda value: abs(prediction - value))
            else:
                raise ValueError(f'Unsupported PNG filter {mode}')
            row[index] = (row[index] + predictor) & 255
        if color == 6:
            pixels.append(tuple(tuple(row[x * 4:x * 4 + 4]) for x in range(width)))
        elif color == 2:
            pixels.append(tuple(tuple(row[x * 3:x * 3 + 3]) + (255,) for x in range(width)))
        else:
            palette, alpha = chunks[b'PLTE'], chunks.get(b'tRNS', b'')
            indices = ((row[(x * depth) // 8] >> (8 - depth - (x * depth) % 8)) & ((1 << depth) - 1) for x in range(width))
            pixels.append(tuple(tuple(palette[i * 3:i * 3 + 3]) + (alpha[i] if i < len(alpha) else 255,) for i in indices))
        prior = row
    return tuple(pixels)


def read_alpha(png):
    return tuple(tuple(pixel[3] for pixel in row) for row in read_rgba(png))


def crystal_pixels(mask=MASK_PATH):
    """Return zero-based (x,y) sprite pixels from inclusive row spans, origin at top left."""
    data = json.loads(Path(mask).read_text(encoding='utf-8'))
    result = set()
    for row, spans in data['rows'].items():
        y = int(row)
        for first, last in spans:
            if not (0 <= y < data['height'] and 0 <= first <= last < data['width']):
                raise ValueError('Crystal mask span is outside the declared sprite')
            result.update((x, y) for x in range(first, last + 1))
    return frozenset(result)


def build_geometry(sprite=SPRITE_PATH, mask=MASK_PATH):
    """Build closed geometry in a 16-unit square, retaining the bitmap aspect ratio."""
    alpha = read_alpha(Path(sprite).read_bytes())
    width, height = len(alpha[0]), len(alpha)
    mask_data = json.loads(Path(mask).read_text(encoding='utf-8'))
    if (width, height) != (mask_data['width'], mask_data['height']):
        raise ValueError('Sprite dimensions differ from the reviewed crystal mask')
    crystal = crystal_pixels(mask)
    cells = {(x, y): (x, y) in crystal for y in range(height) for x in range(width) if alpha[y][x] >= ALPHA_THRESHOLD}
    if not crystal or not crystal.issubset(cells):
        raise ValueError('Crystal mask must contain only opaque sprite pixels')
    pixel = 16 / max(width, height)
    left, top = (16 - width * pixel) / 2, (16 + height * pixel) / 2
    front, back = 7.5, 8.5
    elements = []

    def face(tinted, uv):
        return {'texture': '#wand', 'uv': uv, **({'tintindex': 0} if tinted else {})}

    def element(start, end, faces):
        elements.append({'from': list(start), 'to': list(end), 'faces': faces})

    pending = dict(cells)
    while pending:
        x, y = min(pending, key=lambda cell: (cell[1], cell[0]))
        tinted = pending[x, y]
        right = x + 1
        while (right, y) in pending and pending[right, y] == tinted:
            right += 1
        bottom = y + 1
        while all((column, bottom) in pending and pending[column, bottom] == tinted for column in range(x, right)):
            bottom += 1
        for row in range(y, bottom):
            for column in range(x, right):
                del pending[column, row]
        u0, u1, v0, v1 = x * 16 / width, right * 16 / width, y * 16 / height, bottom * 16 / height
        element((left + x * pixel, top - bottom * pixel, front), (left + right * pixel, top - y * pixel, back), {
            'north': face(tinted, [u1, v0, u0, v1]),
            'south': face(tinted, [u0, v0, u1, v1]),
        })

    for (x, y), tinted in sorted(cells.items(), key=lambda pair: (pair[0][1], pair[0][0])):
        x0, x1, y0, y1 = left + x * pixel, left + (x + 1) * pixel, top - y * pixel, top - (y + 1) * pixel
        u0, u1, v0, v1 = x * 16 / width, (x + 1) * 16 / width, y * 16 / height, (y + 1) * 16 / height
        mid_u, mid_v = (u0 + u1) / 2, (v0 + v1) / 2
        for direction, neighbor, start, end, uv in (
            ('west', (x - 1, y), (x0, y1, front), (x0, y0, back), [mid_u, v0, mid_u, v1]),
            ('east', (x + 1, y), (x1, y1, front), (x1, y0, back), [mid_u, v0, mid_u, v1]),
            ('up', (x, y - 1), (x0, y0, front), (x1, y0, back), [u0, mid_v, u1, mid_v]),
            ('down', (x, y + 1), (x0, y1, front), (x1, y1, back), [u0, mid_v, u1, mid_v]),
        ):
            if neighbor not in cells:
                element(start, end, {direction: face(tinted, uv)})
    return elements


if __name__ == '__main__':
    from collections import Counter
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sprite', type=Path, default=SPRITE_PATH)
    parser.add_argument('--mask', type=Path, default=MASK_PATH)
    args = parser.parse_args()
    geometry = build_geometry(args.sprite, args.mask)
    print(json.dumps(Counter(direction for element in geometry for direction in element['faces']), sort_keys=True))

