"""Writes the data and asset JSON for aphelite: recipes, tags, loot, worldgen, the trim pattern,
item models (with every vanilla trim overlay), block state and equipment asset. Run from the
repo root: `python tools/generate-data.py`. Idempotent; edit this script, not the JSON."""
import json
import pathlib

NS = "beyond_the_dragon"
ROOT = pathlib.Path(__file__).resolve().parent.parent / "common" / "src" / "main" / "resources"
DATA = ROOT / "data" / NS
ASSETS = ROOT / "assets" / NS
MC_DATA = ROOT / "data" / "minecraft"
MC_ASSETS = ROOT / "assets" / "minecraft"

ARMOR = ["helmet", "chestplate", "leggings", "boots"]
TOOLS = ["sword", "pickaxe", "axe", "shovel", "hoe"]
TRIM_MATERIALS = ["amethyst", "copper", "diamond", "emerald", "gold", "iron", "lapis", "netherite", "quartz", "redstone", "resin"]


def write(path, content):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(content, indent="\t") + "\n", encoding="utf-8")


def ns(path):
    return f"{NS}:{path}"


# --- recipes -------------------------------------------------------------------------------
write(DATA / "recipe" / "aphelite_ingot.json", {
    "type": "minecraft:crafting_shapeless",
    "group": "aphelite_ingot",
    "ingredients": [ns("aphelite_shard")] * 4 + ["minecraft:popped_chorus_fruit"] * 4,
    "result": {"id": ns("aphelite_ingot")},
})
for piece in ARMOR + TOOLS:
    write(DATA / "recipe" / f"aphelite_{piece}_smithing.json", {
        "type": "minecraft:smithing_transform",
        "template": ns("aphelite_upgrade_smithing_template"),
        "base": f"minecraft:netherite_{piece}",
        "addition": f"#{NS}:aphelite_tool_materials",
        "result": {"id": ns(f"aphelite_{piece}")},
    })
# Templates duplicate the vanilla way: template + diamonds round an End block.
for template, block in (("aphelite_upgrade_smithing_template", "minecraft:end_stone"),
                        ("dragon_scale_armor_trim_smithing_template", "minecraft:purpur_block")):
    write(DATA / "recipe" / f"{template}.json", {
        "type": "minecraft:crafting_shaped",
        "key": {"#": "minecraft:diamond", "C": block, "S": ns(template)},
        "pattern": ["#S#", "#C#", "###"],
        "result": {"count": 2, "id": ns(template)},
    })
write(DATA / "recipe" / "dragon_scale_armor_trim_smithing_template_smithing_trim.json", {
    "type": "minecraft:smithing_trim",
    "template": ns("dragon_scale_armor_trim_smithing_template"),
    "base": "#minecraft:trimmable_armor",
    "addition": "#minecraft:trim_materials",
    "pattern": ns("dragon_scale"),
})

# --- tags ----------------------------------------------------------------------------------
write(DATA / "tags" / "item" / "aphelite_tool_materials.json", {"values": [ns("aphelite_ingot")]})
write(DATA / "tags" / "item" / "repairs_aphelite_armor.json", {"values": [ns("aphelite_ingot")]})
for tag, items in {
    "head_armor": ["aphelite_helmet"], "chest_armor": ["aphelite_chestplate"],
    "leg_armor": ["aphelite_leggings"], "foot_armor": ["aphelite_boots"],
    "swords": ["aphelite_sword"], "pickaxes": ["aphelite_pickaxe"], "axes": ["aphelite_axe"],
    "shovels": ["aphelite_shovel"], "hoes": ["aphelite_hoe"],
}.items():
    write(MC_DATA / "tags" / "item" / f"{tag}.json", {"values": [ns(i) for i in items]})
for tag in ("mineable/pickaxe", "needs_diamond_tool"):
    write(MC_DATA / "tags" / "block" / f"{tag}.json", {"values": [ns("aphelite_ore")]})

# --- the ore's loot and worldgen ------------------------------------------------------------
write(DATA / "loot_table" / "blocks" / "aphelite_ore.json", {
    "type": "minecraft:block",
    "pools": [{
        "rolls": 1,
        "entries": [{
            "type": "minecraft:alternatives",
            "children": [
                {"type": "minecraft:item", "condition": "minecraft:tool/can_silk_touch", "name": ns("aphelite_ore")},
                {"type": "minecraft:item", "name": ns("aphelite_shard"), "modifier": [
                    {"type": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:ore_drops"},
                    {"type": "minecraft:explosion_decay"},
                ]},
            ],
        }],
    }, {
        # Aphelite pickaxes have a chance of one more shard, on every block (so vein-mining counts).
        "rolls": 1,
        "condition": {"type": "minecraft:match_tool", "predicate": {"items": ns("aphelite_pickaxe")}},
        "entries": [
            {"type": "minecraft:item", "name": ns("aphelite_shard"), "weight": 1},
            {"type": "minecraft:empty", "weight": 3},
        ],
    }],
    "random_sequence": ns("blocks/aphelite_ore"),
})
write(DATA / "worldgen" / "feature" / "aphelite_ore.json", {
    "type": "minecraft:ore",
    "size": 4,
    "discard_chance_on_air_exposure": 0.0,
    "targets": [{"state": ns("aphelite_ore"),
                 "target": {"predicate_type": "minecraft:block_match", "block": "minecraft:end_stone"}}],
})
write(DATA / "worldgen" / "placed_feature" / "aphelite_ore.json", {
    "feature": ns("aphelite_ore"),
    "placement": [
        {"type": "minecraft:count", "count": 6},
        {"type": "minecraft:in_square"},
        {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform",
                                                      "min_inclusive": {"absolute": 8}, "max_inclusive": {"absolute": 90}}},
        {"type": ns("in_ring"), "ring": "outer"},
        {"type": "minecraft:biome"},
    ],
})
# NeoForge adds features through data; Fabric does it in code (FabricPlatform).
write(DATA / "neoforge" / "biome_modifier" / "aphelite_ore.json", {
    "type": "neoforge:add_features",
    "biomes": "#minecraft:is_end",
    "features": ns("aphelite_ore"),
    "step": "underground_ores",
})

# --- the trim pattern -----------------------------------------------------------------------
write(DATA / "trim_pattern" / "dragon_scale.json", {
    "asset_id": ns("dragon_scale"),
    "decal": False,
    "description": {"translate": f"trim_pattern.{NS}.dragon_scale"},
})

# --- the ladder's rewards --------------------------------------------------------------------
def reward(template, breath):
    return {
        "type": "minecraft:gift",
        "pools": [
            {"rolls": 1, "entries": [{"type": "minecraft:item", "name": ns(template)}]},
            {"rolls": 1, "entries": [{"type": "minecraft:item", "name": "minecraft:dragon_breath",
                                      "modifier": [{"type": "minecraft:set_count", "count": breath}]}]},
        ],
    }
write(DATA / "loot_table" / "dragon_ladder" / "tier_1.json", reward("dragon_scale_armor_trim_smithing_template", 4))
write(DATA / "loot_table" / "dragon_ladder" / "tier_2.json", reward("aphelite_upgrade_smithing_template", 8))
write(DATA / "loot_table" / "dragon_ladder" / "tier_3.json", reward("aphelite_upgrade_smithing_template", 12))

# --- assets: models and item definitions -----------------------------------------------------
def generated(layer0, layer1=None):
    textures = {"layer0": layer0}
    if layer1:
        textures["layer1"] = layer1
    return {"parent": "minecraft:item/generated", "textures": textures}

def model_ref(name):
    return {"type": "minecraft:model", "model": ns(f"item/{name}")}

simple_items = ["aphelite_shard", "aphelite_ingot", "aphelite_upgrade_smithing_template",
                "dragon_scale_armor_trim_smithing_template"] + [f"aphelite_{t}" for t in TOOLS]
for item in simple_items:
    parent = "minecraft:item/handheld" if item.split("_")[-1] in TOOLS else "minecraft:item/generated"
    write(ASSETS / "models" / "item" / f"{item}.json", {"parent": parent, "textures": {"layer0": ns(f"item/{item}")}})
    write(ASSETS / "items" / f"{item}.json", {"model": model_ref(item)})
for piece in ARMOR:
    item = f"aphelite_{piece}"
    write(ASSETS / "models" / "item" / f"{item}.json", generated(ns(f"item/{item}")))
    cases = []
    for material in TRIM_MATERIALS:
        overlay = f"{item}_{material}_trim"
        write(ASSETS / "models" / "item" / f"{overlay}.json",
              generated(ns(f"item/{item}"), f"minecraft:trims/items/{piece}_trim_{material}"))
        cases.append({"when": f"minecraft:{material}", "model": model_ref(overlay)})
    write(ASSETS / "items" / f"{item}.json", {"model": {
        "type": "minecraft:select", "property": "minecraft:trim_material",
        "cases": cases, "fallback": model_ref(item),
    }})
write(ASSETS / "blockstates" / "aphelite_ore.json", {"variants": {"": {"model": ns("block/aphelite_ore")}}})
write(ASSETS / "models" / "block" / "aphelite_ore.json", {"parent": "minecraft:block/cube_all", "textures": {"all": ns("block/aphelite_ore")}})
write(ASSETS / "items" / "aphelite_ore.json", {"model": {"type": "minecraft:model", "model": ns("block/aphelite_ore")}})
write(ASSETS / "equipment" / "aphelite.json", {"layers": {
    "humanoid": [{"texture": ns("aphelite")}],
    "humanoid_leggings": [{"texture": ns("aphelite")}],
}})

print("data and assets written")

# --- the Outer Reaches structures -------------------------------------------------------------
# Templates come from tools/generate-structures.js; this is the worldgen and loot around them.
write(MC_DATA.parent / NS / "tags" / "worldgen" / "biome" / "has_structure" / "outer_reaches.json",
      {"values": ["minecraft:end_highlands", "minecraft:end_midlands"]})
write(DATA / "worldgen" / "structure" / "observatory.json", {
    "type": ns("end_jigsaw"),
    "biomes": f"#{NS}:has_structure/outer_reaches",
    "step": "surface_structures",
    "terrain_adaptation": "beard_thin",
    "spawn_overrides": {},
    "start_pool": ns("observatory/start"),
    "size": 1,
    "start_height": {"absolute": 0},
    "max_distance_from_center": 32,
    "min_surface_y": 50,
    "footprint": 21,
})
write(DATA / "worldgen" / "structure_set" / "observatories.json", {
    "placement": {
        "type": "minecraft:random_spread",
        "spacing": 24, "separation": 10, "salt": 20261014,
        "exclusion_zone": {"other_set": "minecraft:end_cities", "chunk_count": 2},
    },
    "structures": [{"structure": ns("observatory"), "weight": 1}],
})
write(DATA / "worldgen" / "template_pool" / "observatory" / "start.json", {
    "name": ns("observatory/start"),
    "fallback": "minecraft:empty",
    "elements": [{"weight": 1, "element": {
        "element_type": "minecraft:single_pool_element",
        "location": ns("observatory_ruin"),
        "processors": "minecraft:empty",
        "projection": "rigid",
    }}],
})
write(DATA / "loot_table" / "chests" / "observatory.json", {
    "type": "minecraft:chest",
    "pools": [
        {"rolls": {"type": "minecraft:uniform", "min": 3, "max": 5}, "entries": [
            {"type": "minecraft:item", "name": "minecraft:ender_pearl", "weight": 10, "modifier": [{"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 2, "max": 5}}]},
            {"type": "minecraft:item", "name": "minecraft:chorus_fruit", "weight": 10, "modifier": [{"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 3, "max": 8}}]},
            {"type": "minecraft:item", "name": "minecraft:end_rod", "weight": 6, "modifier": [{"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 2, "max": 6}}]},
            {"type": "minecraft:item", "name": "minecraft:purpur_block", "weight": 6, "modifier": [{"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 4, "max": 12}}]},
            {"type": "minecraft:item", "name": "minecraft:spyglass", "weight": 4},
            {"type": "minecraft:item", "name": "minecraft:experience_bottle", "weight": 5, "modifier": [{"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 2, "max": 6}}]},
            {"type": "minecraft:item", "name": "minecraft:amethyst_shard", "weight": 5, "modifier": [{"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 2, "max": 5}}]},
            {"type": "minecraft:item", "name": "minecraft:map", "weight": 3},
        ]},
        {"rolls": 1, "condition": {"type": ns("in_ring"), "ring": "outer"}, "entries": [
            {"type": "minecraft:item", "name": ns("aphelite_shard"), "weight": 3, "modifier": [{"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1, "max": 3}}]},
            {"type": "minecraft:empty", "weight": 1},
        ]},
    ],
})

# The other three Outer Reaches structures (templates from generate-structures.js).
def structure(name, pool_name, template, size, footprint, spacing, separation, salt, extra_biomes=None, min_surface_y=50):
    write(DATA / "worldgen" / "structure" / f"{name}.json", {
        "type": ns("end_jigsaw"),
        "biomes": f"#{NS}:has_structure/outer_reaches",
        "step": "surface_structures",
        "terrain_adaptation": "beard_thin",
        "spawn_overrides": {},
        "start_pool": ns(f"{pool_name}/start"),
        "size": 1,
        "start_height": {"absolute": 0},
        "max_distance_from_center": size,
        "min_surface_y": min_surface_y,
        "footprint": footprint,
    })
    write(DATA / "worldgen" / "structure_set" / f"{name}s.json", {
        "placement": {
            "type": "minecraft:random_spread",
            "spacing": spacing, "separation": separation, "salt": salt,
            "exclusion_zone": {"other_set": "minecraft:end_cities", "chunk_count": 2},
        },
        "structures": [{"structure": ns(name), "weight": 1}],
    })
    write(DATA / "worldgen" / "template_pool" / pool_name / "start.json", {
        "name": ns(f"{pool_name}/start"),
        "fallback": "minecraft:empty",
        "elements": [{"weight": 1, "element": {
            "element_type": "minecraft:single_pool_element",
            "location": ns(template),
            "processors": "minecraft:empty",
            "projection": "rigid",
        }}],
    })

structure("wreck", "wreck", "wreck", 40, 14, 28, 12, 20261015)
structure("graveyard", "graveyard", "graveyard", 48, 24, 36, 16, 20261016)
structure("sinking_city", "sinking_city", "sinking_city", 80, 44, 44, 20, 20261017)

def chest_loot(name, rolls, entries, far_entries):
    write(DATA / "loot_table" / "chests" / f"{name}.json", {
        "type": "minecraft:chest",
        "pools": [
            {"rolls": rolls, "entries": entries},
            {"rolls": 1, "condition": {"type": ns("in_ring"), "ring": "outer"}, "entries": far_entries},
        ],
    })

def item(name, weight, lo=None, hi=None):
    e = {"type": "minecraft:item", "name": name, "weight": weight}
    if lo is not None:
        e["modifier"] = [{"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}}]
    return e

uniform = lambda lo, hi: {"type": "minecraft:uniform", "min": lo, "max": hi}
chest_loot("wreck", uniform(4, 6), [
    item("minecraft:diamond", 5, 1, 4), item("minecraft:iron_ingot", 8, 2, 6), item("minecraft:gold_ingot", 6, 2, 6),
    item("minecraft:emerald", 4, 1, 3), item("minecraft:firework_rocket", 10, 8, 24), item("minecraft:ender_pearl", 6, 2, 4),
    item("minecraft:chorus_fruit", 8, 4, 8), item("minecraft:enchanted_golden_apple", 1), item("minecraft:experience_bottle", 6, 3, 8),
    item("minecraft:phantom_membrane", 5, 2, 5),
], [item(ns("aphelite_shard"), 3, 2, 4), {"type": "minecraft:empty", "weight": 1}])
chest_loot("graveyard", uniform(3, 5), [
    item("minecraft:bone_block", 8, 4, 10), item("minecraft:bone", 8, 6, 16), item("minecraft:dragon_breath", 6, 2, 5),
    item("minecraft:ender_pearl", 5, 2, 4), item("minecraft:experience_bottle", 6, 4, 10), item("minecraft:end_crystal", 2),
    item("minecraft:popped_chorus_fruit", 6, 4, 8),
], [item(ns("aphelite_upgrade_smithing_template"), 2), item(ns("aphelite_shard"), 3, 1, 3), {"type": "minecraft:empty", "weight": 1}])
chest_loot("sinking_city", uniform(4, 7), [
    item("minecraft:purpur_block", 8, 6, 16), item("minecraft:end_rod", 6, 3, 8), item("minecraft:magenta_stained_glass", 5, 4, 12),
    item("minecraft:diamond", 4, 1, 3), item("minecraft:emerald", 4, 2, 5), item("minecraft:shulker_shell", 3, 1, 2),
    item("minecraft:ender_pearl", 6, 2, 5), item("minecraft:chorus_fruit", 8, 4, 10), item("minecraft:experience_bottle", 6, 3, 8),
    item("minecraft:saddle", 2), item("minecraft:golden_apple", 3, 1, 2),
], [item(ns("aphelite_shard"), 4, 2, 5), item(ns("aphelite_ingot"), 1), {"type": "minecraft:empty", "weight": 1}])
