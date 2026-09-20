"""Refresh natural-crystal JSON from Minecraft 1.21.1's shipped assets and data.

Vanilla model geometry, transforms, blockstates, loot, and world generation
are retained. Models bind the mod-owned blue mineral artwork; the shader adds
the purple parallax overlay without recoloring the base texture.
Run after Gradle's createMinecraftArtifacts, or pass --minecraft-resources JAR.
"""
import argparse
import json
from pathlib import Path
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "src/main/resources"
MOD = "astral_repository"
BLOCKS = {
    "astral_geode": "amethyst_block",
    "budding_astral": "budding_amethyst",
    "small_astral_bud": "small_amethyst_bud",
    "medium_astral_bud": "medium_amethyst_bud",
    "large_astral_bud": "large_amethyst_bud",
    "astral_cluster": "amethyst_cluster",
}
IDS = {f"minecraft:{old}": f"{MOD}:{new}" for new, old in BLOCKS.items()}
IDS["minecraft:amethyst_shard"] = f"{MOD}:astral_gem"


def write(path, content):
    dest = RES / path
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_text(json.dumps(content, indent=2) + "\n", encoding="utf-8")


def remap(value):
    if isinstance(value, str):
        return IDS.get(value, value.replace("minecraft:blocks/amethyst", f"{MOD}:blocks/astral"))
    if isinstance(value, list):
        return [remap(entry) for entry in value]
    if isinstance(value, dict):
        return {key: remap(entry) for key, entry in value.items()}
    return value


def generate(jar):
    with ZipFile(jar) as archive:
        def vanilla(path):
            return json.loads(archive.read(path))

        for new, old in BLOCKS.items():
            # Keep the cube/cross parent and bind only the mod's own mineral sprite.
            block_model = vanilla(f"assets/minecraft/models/block/{old}.json")
            block_model["textures"] = {key: f"{MOD}:block/{new}" for key in block_model["textures"]}
            write(f"assets/{MOD}/models/block/{new}.json", block_model)
            states = vanilla(f"assets/minecraft/blockstates/{old}.json")
            state_text = json.dumps(states).replace(f"minecraft:block/{old}", f"{MOD}:block/{new}")
            write(f"assets/{MOD}/blockstates/{new}.json", json.loads(state_text))
            loot = remap(vanilla(f"data/minecraft/loot_table/blocks/{old}.json"))
            if "random_sequence" in loot:
                loot["random_sequence"] = f"{MOD}:blocks/{new}"
            write(f"data/{MOD}/loot_table/blocks/{new}.json", loot)

            original_item = vanilla(f"assets/minecraft/models/item/{old}.json")
            if original_item.get("parent") == f"minecraft:block/{old}":
                original_item["parent"] = f"{MOD}:block/{new}"
            if "textures" in original_item:
                original_item["textures"] = {key: f"{MOD}:block/{new}" for key in original_item["textures"]}
            write(f"assets/{MOD}/models/item/{new}_geometry.json", original_item)
            template = vanilla("assets/minecraft/models/block/block.json" if new in ["astral_geode", "budding_astral"] else "assets/minecraft/models/item/generated.json")
            display = dict(template.get("display", {}))
            display.update(original_item.get("display", {}))
            write(f"assets/{MOD}/models/item/{new}.json", {"parent": "builtin/entity", "gui_light": template["gui_light"], "display": display})

        gem = vanilla("assets/minecraft/models/item/amethyst_shard.json")
        gem["textures"]["layer0"] = f"{MOD}:item/astral_gem"
        write(f"assets/{MOD}/models/item/astral_gem_geometry.json", gem)
        template = vanilla("assets/minecraft/models/item/generated.json")
        for name in ["astral_gem"]:
            write(f"assets/{MOD}/models/item/{name}.json", {"parent": "builtin/entity", "gui_light": "front", "display": template["display"]})

        configured = remap(vanilla("data/minecraft/worldgen/configured_feature/amethyst_geode.json"))
        write(f"data/{MOD}/worldgen/configured_feature/astral_geode.json", configured)
        placed = vanilla("data/minecraft/worldgen/placed_feature/amethyst_geode.json")
        placed["feature"] = f"{MOD}:astral_geode"
        write(f"data/{MOD}/worldgen/placed_feature/astral_geode.json", placed)

    # Match the vanilla natural family's behavioral block tags.
    write("data/minecraft/tags/block/crystal_sound_blocks.json", {"replace": False, "values": [f"{MOD}:astral_geode", f"{MOD}:budding_astral"]})
    write("data/minecraft/tags/block/inside_step_sound_blocks.json", {"replace": False, "values": [f"{MOD}:small_astral_bud"]})
    write("data/minecraft/tags/block/vibration_resonators.json", {"replace": False, "values": [f"{MOD}:astral_geode"]})
    write(f"data/{MOD}/tags/item/crystals.json", {"replace": False, "values": [f"{MOD}:astral_gem"]})
    write("data/c/tags/item/gems/astral.json", {"replace": False, "values": [f"{MOD}:astral_gem"]})
    write("data/c/tags/item/gems.json", {"replace": False, "values": ["#c:gems/astral"]})
    lang_path = RES / f"assets/{MOD}/lang/en_us.json"
    language = json.loads(lang_path.read_text(encoding="utf-8-sig"))
    language.update({f"block.{MOD}.astral_geode": "Block of Astral Gem", f"block.{MOD}.budding_astral": "Budding Astral Gem",
                     f"item.{MOD}.astral_gem": "Astral Gem"})
    write(f"assets/{MOD}/lang/en_us.json", language)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--minecraft-resources", type=Path, default=ROOT / "build/moddev/artifacts/neoforge-21.1.244-client-extra-aka-minecraft-resources.jar")
    generate(parser.parse_args().minecraft_resources)
