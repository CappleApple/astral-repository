"""Check the Field Guide resources and, optionally, the packaged mod; no game is launched.

Run after exporting the guide screenshots:
    python tools/verify_guide_resources.py
    python tools/verify_guide_resources.py --jar build/libs/astral_repository-<version>.jar

Python 3.11+ standard library only. Checks resources, not gameplay or rendered layout.
"""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import re
import struct
import sys
import tomllib
import zipfile

ROOT = Path(__file__).resolve().parents[1]
MOD = 'astral_repository'
BOOK = f'{MOD}:field_guide'
BOOK_JSON = f'data/{MOD}/patchouli_books/field_guide/book.json'
GUIDE_ROOT = f'assets/{MOD}/patchouli_books/field_guide/en_us'
IMAGE_ROOT = f'assets/{MOD}/textures/gui/guide/'
EXPECTED_ENTRIES = {'crafting', 'filters', 'first_nexus', 'gems', 'remote', 'runes', 'storage', 'crystal_storage_nexus', 'crystal_relay_crystal', 'crystal_seed_storage_crystal', 'crystal_power_node'}
EXPECTED_IMAGES = {IMAGE_ROOT + name + '.png' for name in (
    'crystals', 'filter_search', 'hover', 'nexus', 'nexus_cursor', 'wand_editor', 'painted_runes', 'settings', 'tome', 'tools')}
EXACT_TEXTURES = {
    f'assets/{MOD}/textures/item/attunement_wand.png': ((34, 36), 'c65346b63cec2924ce1c86021c66bdf4b32c4b13e44afe7477ae1c9b07ad924c'),
    f'assets/{MOD}/textures/item/astral_nexus.png': ((47, 47), 'a581d26c9365e257cdef16531dad01d1d28f79624daf46207dffe3e63a0394ac'),
}

CUSTOM_ITEMS = ('dimensional_attunement', 'range_attunement', 'moon_attunement',
                'star_attunement', 'recipe_tome', 'field_guide')
NATURAL_BLOCKS = {
    'astral_geode': 'amethyst_block',
    'budding_astral': 'budding_amethyst',
    'small_astral_bud': 'small_amethyst_bud',
    'medium_astral_bud': 'medium_amethyst_bud',
    'large_astral_bud': 'large_amethyst_bud',
    'astral_cluster': 'amethyst_cluster',
}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def no_duplicate_keys(pairs):
    result = {}
    for key, value in pairs:
        require(key not in result, f'Duplicate JSON key: {key}')
        result[key] = value
    return result


class Resources:
    def __init__(self, location):
        self.location = location
        self.archive = zipfile.ZipFile(location) if location.is_file() else None
        listed = ([entry.filename for entry in self.archive.infolist() if not entry.is_dir()]
                  if self.archive else [file.relative_to(location).as_posix() for file in location.rglob('*') if file.is_file()])
        require(len(listed) == len(set(listed)), f'Duplicate archive entries: {location}')
        self.names = set(listed)
        self.case_names = {name.casefold(): name for name in listed}
        require(len(self.case_names) == len(self.names), f'Case-colliding resource paths: {location}')
        self.checked = set()

    def read(self, name):
        require(name in self.names, f'{self.location}: missing or incorrect-case path {name}'
                + (f' (found {self.case_names[name.casefold()]})' if name.casefold() in self.case_names else ''))
        self.checked.add(name)
        return self.archive.read(name) if self.archive else (self.location / name).read_bytes()

    def json(self, name):
        try:
            return json.loads(self.read(name).decode('utf-8'), object_pairs_hook=no_duplicate_keys)
        except (UnicodeError, json.JSONDecodeError) as error:
            raise ValueError(f'{name}: {error}') from error

    def close(self):
        if self.archive:
            self.archive.close()


def identifier(value):
    require(isinstance(value, str) and re.fullmatch(r'[a-z0-9_.-]+:[a-z0-9_./-]+', value), f'Invalid resource ID: {value!r}')
    return value.split(':', 1)


def png_size(raw, name):
    require(len(raw) >= 33 and raw[:8] == b'\x89PNG\r\n\x1a\n' and raw[12:16] == b'IHDR', f'Invalid PNG header: {name}')
    return struct.unpack('>II', raw[16:24])


def validate(resources, minecraft, patchouli):
    providers = {MOD: resources, 'minecraft': minecraft, 'patchouli': patchouli}
    def resource_file(resource_id, prefix='', suffix=''):
        namespace, path = identifier(resource_id)
        require(namespace in providers, f'Unverified external namespace: {resource_id}')
        filename = f'assets/{namespace}/{prefix}{path}{suffix}'
        return providers[namespace].read(filename)
    def icon(value):
        namespace, path = identifier(value)
        raw = resource_file(value, 'models/item/', '.json')
        json.loads(raw.decode('utf-8'), object_pairs_hook=no_duplicate_keys)
    def local_ids(folder):
        prefix = f'{GUIDE_ROOT}/{folder}/'
        return {f'{MOD}:' + name[len(prefix):-5]: name for name in resources.names if name.startswith(prefix) and name.endswith('.json')}

    book = resources.json(BOOK_JSON)
    require(book.get('name') == 'Astral Field Guide' and book.get('use_resource_pack') is True, 'Unexpected Field Guide definition')
    require(book.get('creative_tab') == f'{MOD}:{MOD}', 'Field Guide creative tab differs from the registered mod tab')
    require(book.get('dont_generate_book') is True and book.get('custom_book_item') == BOOK, 'Field Guide must use its mod-owned item without a duplicate Patchouli book')
    resource_file(book['book_texture'])
    icon(book['model'])
    categories, entries = local_ids('categories'), local_ids('entries')
    require(set(categories) == {f'{MOD}:workshop', f'{MOD}:crystals'}, 'Expected workshop and crystal categories')
    require(set(entries) == {f'{MOD}:{name}' for name in EXPECTED_ENTRIES}, 'Expected the eleven shipped guide entries')
    images, recipes = set(), set()
    documents = [book]
    for key, filename in categories.items():
        category = resources.json(filename); documents.append(category); icon(category['icon'])
        if 'parent' in category:
            require(category['parent'] in categories, f'{key}: unknown parent category')
    for key, filename in entries.items():
        entry = resources.json(filename); documents.append(entry); icon(entry['icon'])
        if key == f'{MOD}:crystal_power_node':
            require(entry.get('flag') == f'{MOD}:power_enabled', 'Power Node guide entry must follow the synchronized power flag')
        require(entry.get('category') in categories, f'{key}: unknown category {entry.get("category")}')
        require(isinstance(entry.get('pages'), list) and entry['pages'], f'{key}: no pages')
        for page in entry['pages']:
            require(page.get('type') in {'patchouli:text', 'patchouli:image', 'patchouli:crafting', 'patchouli:spotlight'}, f'{key}: unverified page type')
            for image in page.get('images', []):
                namespace, path = identifier(image)
                require(namespace == MOD, f'{key}: screenshot is outside the mod resources')
                filename = f'assets/{namespace}/{path}'; images.add(filename)
                require(png_size(resources.read(filename), filename) == (256, 256), f'{filename}: expected 256x256')
            for field in ('recipe', 'recipe2'):
                if field not in page:
                    continue
                namespace, path = identifier(page[field])
                require(namespace in {MOD, 'minecraft'}, f'Unverified recipe namespace: {page[field]}')
                source = resources if namespace == MOD else minecraft
                recipe = source.json(f'data/{namespace}/recipe/{path}.json'); recipes.add(page[field])
                require(isinstance(recipe.get('type'), str), f'{page[field]}: missing recipe type')
    # Resolve Patchouli entry/category links if any are introduced into the shipped prose.
    for document in documents:
        for target in re.findall(r'\$\(l:([^)]+)\)', json.dumps(document)):
            target = target.split('#')[0]
            if not target:
                continue
            target_id = target if ':' in target else f'{MOD}:{target}'
            require(target_id in entries or target_id in categories, f'Unresolved book link: {target}')
    actual_images = {name for name in resources.names if name.startswith(IMAGE_ROOT) and name.endswith('.png')}
    require(images == EXPECTED_IMAGES and actual_images == EXPECTED_IMAGES, 'Expected exactly ten referenced guide PNGs')
    guide_recipe = resources.json(f'data/{MOD}/recipe/field_guide.json')
    require(guide_recipe.get('type') == 'minecraft:crafting_shapeless', 'Field Guide recipe must be shapeless')
    require(Counter(ingredient.get('item') for ingredient in guide_recipe.get('ingredients', [])) == Counter({'minecraft:book': 1, f'{MOD}:astral_gem': 2}), 'Field Guide requires one book and two Astral Gems')
    require(guide_recipe.get('result') == {'id': BOOK, 'count': 1}, 'Field Guide result must use its mod-owned item ID')
    require(guide_recipe.get('neoforge:conditions') == [{'type': 'neoforge:mod_loaded', 'modid': 'patchouli'}], 'Field Guide recipe must be conditional on Patchouli')
    for name, (dimensions, checksum) in EXACT_TEXTURES.items():
        raw = resources.read(name)
        require(png_size(raw, name) == dimensions, f'{name}: supplied bitmap dimensions changed')
        require(hashlib.sha256(raw).hexdigest() == checksum, f'{name}: supplied bitmap bytes changed')
    audit_models(resources, minecraft)
    return len(categories), len(entries), len(images), len(recipes)


def audit_models(resources, minecraft):
    """Resolve every shipped model/material texture and retain vanilla natural forms."""
    models = f'assets/{MOD}/models/'
    textures = f'assets/{MOD}/textures/'

    def pixel_sprite(name):
        width, height = png_size(resources.read(name), name)
        require(width == height and width > 0,
                f'{name}: expected a nonempty square sprite; resolution may be reduced independently')

    def texture(value, owner):
        if value.startswith('#'):
            return
        namespace, path = identifier(value)
        require(namespace == MOD, f'{owner}: stock texture reference {value}')
        name = f'{textures}{path}.png'
        size = png_size(resources.read(name), name)
        require(min(size) > 0, f'{name}: empty texture')

    for name in sorted(resources.names):
        if not name.startswith(models):
            continue
        if name.endswith('.json'):
            model = resources.json(name)
            for value in model.get('textures', {}).values():
                texture(value, name)
            parent = model.get('parent')
            if parent and parent not in {'builtin/entity', 'minecraft:builtin/entity'}:
                namespace, path = identifier(parent if ':' in parent else 'minecraft:' + parent)
                require(namespace in {MOD, 'minecraft'}, f'{name}: unverified model parent {parent}')
                provider = resources if namespace == MOD else minecraft
                provider.read(f'assets/{namespace}/models/{path}.json')
            if model.get('loader') == 'neoforge:obj':
                namespace, path = identifier(model['model'])
                require(namespace == MOD, f'{name}: OBJ mesh must belong to the mod')
                resources.read(f'assets/{namespace}/{path}')
        elif name.endswith('.mtl'):
            for value in re.findall(r'^map_Kd\s+(\S+)', resources.read(name).decode('utf-8'), re.M):
                texture(value, name)

    for item in CUSTOM_ITEMS:
        name = f'{textures}item/{item}.png'
        pixel_sprite(name)
        model = resources.json(f'{models}item/{item}.json')
        require(model.get('parent') == 'minecraft:item/generated'
                and model.get('textures', {}).get('layer0') == f'{MOD}:item/{item}',
                f'{item}: generated icon must use its own texture')
    for retired in ('bridge_attunement', 'remote_attunement'):
        require(f'{models}item/{retired}.json' not in resources.names,
                f'{retired}: retired attunement icon still packaged')
        require(f'data/{MOD}/recipe/{retired}.json' not in resources.names,
                f'{retired}: retired attunement recipe still packaged')
    resources.json(f'data/{MOD}/recipe/dimensional_attunement.json')

    for own, vanilla in NATURAL_BLOCKS.items():
        name = f'{textures}block/{own}.png'
        pixel_sprite(name)
        block = resources.json(f'{models}block/{own}.json')
        expected = minecraft.json(f'assets/minecraft/models/block/{vanilla}.json')
        expected['textures'] = {key: f'{MOD}:block/{own}' for key in expected['textures']}
        require(block == expected, f'{own}: custom texture changed the vanilla cube/cross geometry')
        item = resources.json(f'{models}item/{own}_geometry.json')
        expected = minecraft.json(f'assets/minecraft/models/item/{vanilla}.json')
        if expected.get('parent') == f'minecraft:block/{vanilla}':
            expected['parent'] = f'{MOD}:block/{own}'
        if 'textures' in expected:
            expected['textures'] = {key: f'{MOD}:block/{own}' for key in expected['textures']}
        require(item == expected, f'{own}: item geometry or display transforms differ from vanilla')
    name = f'{textures}item/astral_gem.png'
    pixel_sprite(name)
    gem = resources.json(f'{models}item/astral_gem_geometry.json')
    expected = minecraft.json('assets/minecraft/models/item/amethyst_shard.json')
    expected['textures']['layer0'] = f'{MOD}:item/astral_gem'
    require(gem == expected, 'Astral Gem must retain generated item geometry with its custom sprite')


def audit_jar(resources, source):
    forbidden = []
    for name in sorted(resources.names):
        lower = name.lower()
        if (lower.endswith('.jar') or '/gametest/' in lower or '/tests/' in lower or '/fixtures/' in lower
                or re.match(r'data/[^/]+/structures?/', lower)
                or (lower.endswith('.class') and (lower.startswith('vazkii/patchouli/')
                    or re.search(r'(gametests?|smoke|fixture)(\$[^/]*)?\.class$', lower)))):
            forbidden.append(name)
    require(not forbidden, 'Forbidden packaged files:\n' + '\n'.join(forbidden))
    metadata = tomllib.loads(resources.read('META-INF/neoforge.mods.toml').decode('utf-8'))
    expected_version = re.search(r'^mod_version=(.+)$', (ROOT / 'gradle.properties').read_text(encoding='utf-8'), re.M).group(1).strip()
    mod = next((value for value in metadata['mods'] if value['modId'] == MOD), None)
    require(mod and mod['version'] == expected_version, 'Packaged mod version differs from gradle.properties')
    dependency = next((value for value in metadata['dependencies'][MOD] if value['modId'] == 'patchouli'), None)
    require(dependency and dependency.get('type') == 'optional' and dependency.get('side') == 'BOTH'
            and dependency.get('versionRange') == '[1.21.1-93,)', 'Packaged Patchouli dependency is incorrect')
    # Also reject an artifact built before the last guide export or prose edit.
    for name in source.checked:
        require(resources.read(name) == source.read(name), f'Artifact is stale: {name}')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', type=Path, help='Also audit the final mod JAR and compare checked resources to source')
    parser.add_argument('--minecraft-resources', type=Path, default=ROOT / 'build/moddev/artifacts/neoforge-21.1.244-client-extra-aka-minecraft-resources.jar')
    parser.add_argument('--patchouli', type=Path, default=ROOT / 'test-libs/Patchouli-1.21.1-93-NEOFORGE.jar')
    args = parser.parse_args()
    opened = []
    try:
        for location in (args.minecraft_resources, args.patchouli):
            require(location.is_file(), f'Local dependency resources missing: {location}; supply the matching command-line option')
        source, minecraft, patchouli = [Resources(location) for location in (ROOT / 'src/main/resources', args.minecraft_resources, args.patchouli)]
        opened.extend((source, minecraft, patchouli))
        counts = validate(source, minecraft, patchouli)
        print('SOURCE PASS: %d category, %d entries, %d screenshots (256x256), %d recipe references; optional custom book item, custom model textures, vanilla natural geometry, paths and exact wand/orb hashes match.' % counts)
        if args.jar:
            require(args.jar.is_file(), f'Mod JAR missing: {args.jar}')
            artifact = Resources(args.jar); opened.append(artifact)
            validate(artifact, minecraft, patchouli); audit_jar(artifact, source)
            print(f'JAR PASS: {args.jar}; checked resources match source; no test fixtures, structures, nested JARs or Patchouli implementation classes.')
        return 0
    except (ValueError, OSError, KeyError, zipfile.BadZipFile) as error:
        print(f'FAIL: {error}', file=sys.stderr)
        return 1
    finally:
        for resources in opened:
            resources.close()


if __name__ == '__main__':
    raise SystemExit(main())
