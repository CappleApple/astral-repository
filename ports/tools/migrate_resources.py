"""Copy original resources to a 26.x target, converting versioned vanilla data.

Java classes are maintained separately. Callers supply the registered
special-item renderer id and retain their loader metadata/mixin configuration.
"""
from __future__ import annotations

import argparse
import re
import copy
import json
from pathlib import Path
import shutil

REPOSITORY = Path(__file__).resolve().parents[2]
SOURCE = REPOSITORY / 'src/main/resources'
NAMESPACE = 'astral_repository'
SPECIAL_ITEMS = {
    'astral_geode', 'budding_astral', 'small_astral_bud', 'medium_astral_bud',
    'large_astral_bud', 'astral_cluster', 'astral_gem', 'attunement_wand',
    'astral_nexus', 'resonance_goggles', 'storage_nexus', 'relay_crystal',
    'power_node', 'seed_storage_crystal',
}
ORDINARY_ITEMS = {'range_attunement', 'moon_attunement', 'star_attunement',
                  'dimensional_attunement', 'recipe_tome', 'field_guide'}


def ingredient(value):
    if isinstance(value, list):
        return [ingredient(entry) for entry in value]
    if isinstance(value, dict):
        if set(value) == {'item'}:
            return value['item']
        if set(value) == {'tag'}:
            return '#' + value['tag']
        raise ValueError(f'Unrecognized ingredient schema: {value!r}')
    if isinstance(value, str):
        return value
    raise ValueError(f'Invalid ingredient: {value!r}')


def migrate_263_loot(value):
    """26.3 uses holder predicates/modifiers and `type` dispatch keys."""
    if isinstance(value, list):
        return [migrate_263_loot(entry) for entry in value]
    if not isinstance(value, dict):
        return value
    result = {}
    for key, entry in value.items():
        if key in {'conditions', 'functions'}:
            converted = migrate_263_loot(entry)
            if not converted:
                continue
            target = 'condition' if key == 'conditions' else 'modifier'
            if key == 'conditions' and len(converted) > 1:
                result[target] = {'type': 'minecraft:all_of', 'terms': converted}
            else:
                result[target] = converted[0] if len(converted) == 1 else converted
        elif key in {'condition', 'function'} and isinstance(entry, str):
            result['type'] = entry
        else:
            result[key] = migrate_263_loot(entry)
    return result


def transform(path: str, original: dict, minecraft: str, loader: str):
    data = copy.deepcopy(original)
    if minecraft == '26.3' and '/loot_table/' in path:
        data = migrate_263_loot(data)
    if '/recipe/' in path:
        if 'key' in data:
            data['key'] = {key: ingredient(value) for key, value in data['key'].items()}
        if 'ingredients' in data:
            data['ingredients'] = [ingredient(value) for value in data['ingredients']]
        if 'ingredient' in data:
            data['ingredient'] = ingredient(data['ingredient'])
        if loader == 'fabric' and 'neoforge:conditions' in data:
            conditions = []
            for condition in data.pop('neoforge:conditions'):
                if condition['type'] != 'neoforge:mod_loaded':
                    raise ValueError(f'Unrecognized resource condition: {condition!r}')
                conditions.append({'condition': 'fabric:all_mods_loaded', 'values': [condition['modid']]})
            data['fabric:load_conditions'] = conditions
    if '/models/' in path and data.get('parent') in {'builtin/entity', 'minecraft:builtin/entity'}:
        data.pop('parent')
    if '/models/' in path:
        textures = data.setdefault('textures', {})
        if data.get('loader') in {'neoforge:obj', 'astral_repository:obj'}:
            model_id = data['model'].split(':', 1)
            mesh = SOURCE / 'assets' / model_id[0] / model_id[1]
            for library in re.findall(r'^mtllib\s+(.+)$', mesh.read_text(), re.MULTILINE):
                material = mesh.parent / library.strip()
                for texture in re.findall(r'^map_Kd\s+(\S+)', material.read_text(), re.MULTILINE):
                    if not texture.startswith('#'):
                        textures[texture] = texture
        if data.get('parent') == 'minecraft:block/cross':
            textures.setdefault('particle', '#cross')
        if '/models/item/' in path and Path(path).stem in SPECIAL_ITEMS and 'particle' not in textures:
            item = Path(path).stem
            if (SOURCE / f'assets/{NAMESPACE}/textures/block/{item}.png').is_file():
                textures['particle'] = f'{NAMESPACE}:block/{item}'
            elif (SOURCE / f'assets/{NAMESPACE}/textures/item/{item}.png').is_file():
                textures['particle'] = f'{NAMESPACE}:item/{item}'
            elif item == 'resonance_goggles':
                textures['particle'] = f'{NAMESPACE}:item/astral_goggles_icon'
            else:
                textures['particle'] = f'{NAMESPACE}:block/node_crystal'
        if not textures:
            data.pop('textures', None)
    if loader == 'fabric' and data.get('loader') == 'neoforge:obj':
        data.pop('loader')
        data['fabric:type'] = 'astral_repository:obj'
    if path == 'assets/minecraft/atlases/blocks.json':
        path = 'assets/minecraft/atlases/items.json'
        if minecraft == '26.3':
            for source in data['sources']:
                source['palette_key'] = 'minecraft:trim_base'
                source['permutations']['astral_repository_astral_gem'] = 'astral_repository:trim/astral_gem'
    if path == 'assets/minecraft/atlases/armor_trims.json':
        for source in data['sources']:
            source['textures'] = ['trims/entity/humanoid_leggings/' + texture.rsplit('/', 1)[1].removesuffix('_leggings')
                                  if texture.endswith('_leggings') else 'trims/entity/humanoid/' + texture.rsplit('/', 1)[1]
                                  for texture in source['textures']]
    if '/trim_material/' in path:
        data.pop('ingredient', None)
        data.pop('item_model_index', None)
        if minecraft == '26.3':
            data.pop('asset_name', None)
            data['palette_id'] = 'astral_repository:trim/astral_gem'
    if minecraft == '26.3' and '/worldgen/configured_feature/' in path:
        if data['type'] != 'minecraft:geode':
            raise ValueError('Only the original geode feature is supported by this migration.')
        data = {'type': data['type'], **data['config']}
        for key, value in tuple(data['blocks'].items()):
            if key.endswith('_provider'):
                if value['type'] != 'minecraft:simple_state_provider':
                    raise ValueError(f'Unrecognized state provider: {value!r}')
                state = value['state']
                data['blocks'][key] = {'id': state['Name']}
                if state.get('Properties'):
                    data['blocks'][key]['properties'] = state['Properties']
        # The new geode codec accepts block IDs and derives facing/waterlogging.
        data['blocks']['inner_placements'] = [state['Name'] for state in data['blocks']['inner_placements']]
        path = path.replace('/worldgen/configured_feature/', '/worldgen/feature/')
    return path, data


def migrate_263_shaders(directory: Path):
    """RenderPearl uses standard includes and explicit stage interface locations."""
    attributes = {'Position': 0, 'Color': 1, 'UV0': 2, 'UV1': 3, 'UV2': 4, 'Normal': 5}
    for vertex in sorted(directory.rglob('*.vsh')):
        outputs = re.findall(r'^out\s+\w+\s+(\w+);', vertex.read_text(), re.MULTILINE)
        varying = {name: index for index, name in enumerate(outputs)}
        for path in (vertex, vertex.with_suffix('.fsh')):
            if not path.exists():
                continue
            text = path.read_text().replace('#moj_import', '#include')
            text = text.replace('#version 330', '#version 330\n#extension GL_ARB_separate_shader_objects : require')
            def location(match):
                direction, kind, name = match.groups()
                mapping = attributes if path.suffix == '.vsh' and direction == 'in' else varying
                index = 0 if path.suffix == '.fsh' and direction == 'out' else mapping[name]
                return f'layout(location = {index}) {direction} {kind} {name};'
            text = re.sub(r'^(in|out)\s+(\w+)\s+(\w+);', location, text, flags=re.MULTILINE)
            if path.suffix == '.fsh' and path.stem in {'astral_plane', 'resource_transfer'}:
                text = text.replace('#include <minecraft:fog.glsl>', '#include <minecraft:fog.glsl>\n#include <minecraft:oit.glsl>')
                text = text.replace('layout(location = 0) out vec4 fragColor;', '#ifndef OIT_ALPHA_ONLY\nlayout(location = 0) out vec4 fragColor;\n#endif')
                # Preserve each shader's computed alpha, circle mask and color through vanilla OIT.
                pattern = r'(    )(fragColor\s*=\s*apply_fog\()(color|shaded)(,[^;]*),\s*FogColor(\);)'
                def oit_output(match):
                    value = match[3]
                    output = match[1] + match[2] + value + match[4] + ',fogColor' + match[5]
                    return (f'    #ifdef OIT_ALPHA_ONLY\n    executeAlphaOnlyPhase(gl_FragCoord.z,{value}.a);\n    #else\n'
                            f'    #ifdef OIT_ACCUMULATE\n    {value}=sampleColorForAccumulation({value});\n'
                            f'    vec4 fogColor=vec4(FogColor.rgb*{value}.a,FogColor.a);\n    #else\n'
                            f'    vec4 fogColor=FogColor;\n    #endif\n{output}\n    #endif')
                text = re.sub(pattern, oit_output, text)
            path.write_text(text)


def migrate(output: Path, minecraft: str, loader: str):
    output = output.resolve()
    ports = (REPOSITORY / 'ports').resolve()
    if not output.is_relative_to(ports) or output == ports:
        raise ValueError('Output must be a target resource directory inside ports/.')
    written = []
    for source in sorted(SOURCE.rglob('*')):
        if not source.is_file():
            continue
        relative = source.relative_to(SOURCE).as_posix()
        if relative.startswith('META-INF/') or relative.endswith('.mixins.json'):
            continue
        if '/structure/empty_' in relative:
            continue
        if loader == 'fabric' and ('/neoforge/' in relative or '/curios/' in relative or '/tags/curios/' in relative):
            continue
        if minecraft == '26.3' and relative == 'assets/minecraft/atlases/armor_trims.json':
            continue
        if minecraft == '26.3' and relative == 'assets/astral_repository/textures/trims/color_palettes/astral_gem.png':
            relative = 'assets/astral_repository/textures/palettes/trim/astral_gem.png'
        data = None
        if source.suffix == '.json':
            relative, data = transform(relative, json.loads(source.read_text(encoding='utf-8-sig')), minecraft, loader)
        target = output / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        if data is None:
            shutil.copyfile(source, target)
        else:
            target.write_text(json.dumps(data, indent=2) + '\n', encoding='utf-8')
        written.append(relative)
    for item in sorted(SPECIAL_ITEMS | ORDINARY_ITEMS):
        model = {'type': 'minecraft:model', 'model': f'{NAMESPACE}:item/{item}',
                 'tints': [{'type': f'{NAMESPACE}:item_color'}]}
        if item in SPECIAL_ITEMS:
            model = {'type': 'minecraft:special', 'base': f'{NAMESPACE}:item/{item}',
                     'model': {'type': f'{NAMESPACE}:astral_mineral', 'item': f'{NAMESPACE}:{item}'}}
        if item == 'resonance_goggles':
            head = copy.deepcopy(model)
            head['model']['head'] = True
            model = {'type': 'minecraft:select', 'property': 'minecraft:display_context',
                     'cases': [{'when': 'head', 'model': head}], 'fallback': model}
        relative = f'assets/{NAMESPACE}/items/{item}.json'
        target = output / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(json.dumps({'model': model}, indent=2) + '\n', encoding='utf-8')
        written.append(relative)
    # A combined mod pack contains both resource and data formats for its one game version.
    lower, upper = ([88, 0], [107, 1]) if minecraft == '26.2' else ([97, 1], [121, 0])
    (output / 'pack.mcmeta').write_text(json.dumps({'pack': {'description': 'Astral Repository resources',
        'min_format': lower, 'max_format': upper}}, indent=2) + '\n', encoding='utf-8')
    # Shader conversion is maintained with the modern client implementation.
    shaders = REPOSITORY / 'ports/neoforge-modern-common/src/main/resources/assets/astral_repository/shaders'
    if shaders.exists():
        shader_output = output / "assets/astral_repository/shaders"
        shutil.copytree(shaders, shader_output, dirs_exist_ok=True)
        if minecraft == "26.3":
            migrate_263_shaders(shader_output)
    # These are superseded by the versioned paths above, and may exist after a previous generation.
    obsolete = [output / 'assets/minecraft/atlases/blocks.json']
    if minecraft == '26.3':
        obsolete += [output / 'assets/minecraft/atlases/armor_trims.json',
                     output / 'data/astral_repository/worldgen/configured_feature/astral_geode.json']
    for path in obsolete:
        if path.is_file():
            path.unlink()
    return written


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--minecraft', choices=['26.2', '26.3'], required=True)
    parser.add_argument('--loader', choices=['fabric', 'neoforge'], required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    files = migrate(args.output, args.minecraft, args.loader)
    print(f'Migrated {len(files)} resources for {args.loader} {args.minecraft}.')
