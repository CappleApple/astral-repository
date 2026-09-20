# Astral Repository base textures

The fourteen newly generated textures are separated at their original generated resolution. Their alpha channels are preserved. The existing network-block materials and supplied wand/orb textures are included at their existing resolution.

Keep filenames, aspect ratios, transparent backgrounds, and the goggles texture-sheet layout when reducing resolution. The mod reads these from `assets/astral_repository/textures/` with the same `block/` and `item/` paths. Runtime previews currently use 16-pixel natural crystals, 32-pixel attunements/books, and a 64-pixel goggles sheet.

The goggles sheet is one UV texture: top-left dark metal, top-right leather strap, bottom-left gem lenses, bottom-right silver trim. Its quadrant arrangement must stay intact.

| File | Pixels |
| --- | --- |
| `block/astral_cluster.png` | 443 x 443 |
| `block/astral_geode.png` | 444 x 444 |
| `block/budding_astral.png` | 443 x 444 |
| `block/large_astral_bud.png` | 444 x 443 |
| `block/medium_astral_bud.png` | 444 x 444 |
| `block/node_channel.png` | 16 x 16 |
| `block/node_crystal.png` | 16 x 16 |
| `block/node_stone.png` | 16 x 16 |
| `block/node_trim.png` | 16 x 16 |
| `block/small_astral_bud.png` | 443 x 444 |
| `item/astral_gem.png` | 443 x 443 |
| `item/astral_goggles.png` | 1254 x 1254 |
| `item/astral_nexus.png` | 47 x 47 |
| `item/attunement_wand.png` | 34 x 36 |
| `item/dimensional_attunement.png` | 512 x 512 |
| `item/field_guide.png` | 512 x 512 |
| `item/moon_attunement.png` | 512 x 512 |
| `item/range_attunement.png` | 512 x 512 |
| `item/recipe_tome.png` | 512 x 512 |
| `item/star_attunement.png` | 512 x 512 |

## Generated artwork

Created with the built-in image-generation tool. Prompt set:

- A Minecraft pixel-art goggles material atlas: midnight-indigo metal, purple leather strap, blue/cyan crystal facets, and muted lavender-silver trim, arranged as four equal quadrants without labels.
- Six distinct Minecraft item sprites: Dimensional, Range, Moon, and Star Attunements; a restrained blue Recipe Tome; and a purple Field Guide. Indigo, royal blue, cyan, purple, and silver; transparent backgrounds and low-resolution silhouettes.
- A four-column natural-crystal atlas: blue crystal block, budding block, progressively sized buds, mature cluster, and diagonal gem. Crisp Minecraft-style pixels, transparent sprite backgrounds, no stars; the shader adds the animated field.
