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


# The data formats this branch's Minecraft reads. "26.3": loot entries take "modifier"/"condition",
# configured features live in worldgen/feature with a flat config and plain block ids, trims are
# coloured from the texture's palette metadata. "26.1": loot takes "functions"/"conditions",
# features live in worldgen/configured_feature under "config" with {"Name": id} states, and a trim
# pattern must be listed in an armor_trims atlas. Everything else below is written once, in the
# 26.3 shape, and converted here; keep this the only place the versions differ.
FORMAT = "26.1"

SILK_TOUCH = {"condition": "minecraft:match_tool", "predicate": {"predicates": {
    "minecraft:enchantments": [{"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]}}}


def old_loot(obj):
    """A loot table in the 26.3 shape, rewritten for 26.1."""
    if isinstance(obj, list):
        return [old_loot(x) for x in obj]
    if not isinstance(obj, dict):
        return obj
    out = {}
    for key, value in obj.items():
        if key in ("functions", "conditions"):
            out[key] = value  # already in the 26.1 shape (a table read back and amended)
        elif key == "modifier":
            out["functions"] = [{"function": f["type"], **{k: v for k, v in f.items() if k != "type"}} for f in value]
        elif key == "condition":
            if isinstance(value, str):
                assert value == "minecraft:tool/can_silk_touch", value
                out["conditions"] = [SILK_TOUCH]
            else:
                out["conditions"] = [{"condition": value["type"], **{k: old_loot(v) for k, v in value.items() if k != "type"}}]
        else:
            out[key] = old_loot(value)
    return out


def write(path, content):
    if FORMAT == "26.1" and "loot_table" in path.parts:
        content = old_loot(content)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(content, indent="\t") + "\n", encoding="utf-8")


def ns(path):
    return f"{NS}:{path}"


# --- the lore notes: a short written book in every chest of a ruin kind, pointing at another ruin
# or at what the End keeps. They are the in-game guide, with the advancements tab; nothing else
# tells a player these things. Pages stay short enough to read at a glance.
LORE = {
    "wreck": ("Captain's Log", "the captain", [
        "Day 40. Ran her onto the island in the dark. Hull split along the east side; the mast went with the first gust. The crew took the boats and did not come back.",
        "Far out, towards where the sky goes thin, there is a tower with a glass eye on its roof. Lights in it, some nights. The keeper's lectern, the old charts call it.",
        "South of here, shoals of bone. The dragon's kin fell there once, the charts say, and something still circles above them, carrying what it took from the dead.",
    ]),
    "observatory": ("The Keeper's Letter", "the keeper", [
        "If you have found this, the storm has had the roof. The lectern's book says what is missing; put it all back and she will see again. The chart is yours then.",
        "Two things I never wrote down, in case the wrong hands found them. The first: the egg, set on a lit beacon, calls the dragon back. Each time, it comes back angrier.",
        "The second: past the outer reaches, five thousand paces from the island, the end stone holds a metal a step beyond netherite. Four shards and four popped chorus fruit to the ingot.",
    ]),
    "graveyard": ("Scratched on Bone", "unknown", [
        "the vault under the ribs takes a trial key. the phantoms that circle here carry them. one prize to each of us, it said, and it kept its word.",
        "out past the skull there is a city that slipped. half of it hangs over nothing. we did not stay.",
    ]),
    "sinking_city": ("A Resident's Note", "a resident", [
        "We are leaving before the rest goes. If you are reading this, go to the keeper's tower instead; the one with the glass eye.",
        "Mend it, and its chart will show you every ruin and city near. More than that: the lectern is where the dead come back. The End has no other such place.",
    ]),
}


def lore_book(kind):
    title, author, pages = LORE[kind]
    return {"type": "minecraft:item", "name": "minecraft:written_book", "modifier": [
        {"type": "minecraft:set_book_cover", "title": title, "author": author, "generation": 0},
        {"type": "minecraft:set_written_book_pages", "pages": pages, "mode": "replace_all"},
    ]}




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
ore_config = {
    "size": 4,
    "discard_chance_on_air_exposure": 0.0,
    "targets": [{"state": ns("aphelite_ore") if FORMAT == "26.3" else {"Name": ns("aphelite_ore")},
                 "target": {"predicate_type": "minecraft:block_match", "block": "minecraft:end_stone"}}],
}
if FORMAT == "26.3":
    write(DATA / "worldgen" / "feature" / "aphelite_ore.json", {"type": "minecraft:ore", **ore_config})
else:
    write(DATA / "worldgen" / "configured_feature" / "aphelite_ore.json", {"type": "minecraft:ore", "config": ore_config})
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
if FORMAT == "26.1":
    # 26.1 colours trims through an atlas of palette permutations; the pattern has to be listed.
    materials = ["amethyst", "copper", "copper_darker", "diamond", "diamond_darker", "emerald", "gold", "gold_darker",
                 "iron", "iron_darker", "lapis", "netherite", "netherite_darker", "quartz", "redstone", "resin"]
    write(ASSETS / "atlases" / "armor_trims.json", {"sources": [{
        "type": "minecraft:paletted_permutations",
        "palette_key": "minecraft:trims/color_palettes/trim_palette",
        "permutations": {m: f"minecraft:trims/color_palettes/{m}" for m in materials},
        "textures": [ns("trims/entity/humanoid/dragon_scale"), ns("trims/entity/humanoid_leggings/dragon_scale")],
    }]})
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

# --- the advancements tab: the in-game guide. Each entry's description hints at the next step.
# Finding ruins and getting items use vanilla triggers; the rest are granted from the code at the
# moments it already notices (Advancements.grant), through an "impossible" criterion.
ADVANCEMENTS = []  # (path, title, description)


def advancement(path, parent, icon, title, description, criteria, frame=None, background=None):
    ADVANCEMENTS.append((path, title, description))
    display = {
        "icon": {"id": icon},
        "title": {"translate": f"advancements.{NS}.{path}.title"},
        "description": {"translate": f"advancements.{NS}.{path}.description"},
    }
    if frame:
        display["frame"] = frame
    if background:
        display["background"] = background
        display["show_toast"] = False
        display["announce_to_chat"] = False
    data = {
        "criteria": criteria,
        "display": display,
        "requirements": [[name] for name in criteria],
    }
    if parent:
        data["parent"] = ns(parent)
    write(DATA / "advancement" / f"{path}.json", data)


GRANTED = {"granted": {"trigger": "minecraft:impossible"}}


def found(structure):
    # 26.3 takes one typed predicate with a "minecraft:location" sub-predicate; 26.1 a list of
    # conditions with a plain "location" one.
    if FORMAT == "26.3":
        player = {"type": "minecraft:entity_properties", "entity": "this",
                  "predicate": {"minecraft:location": {"structures": structure}}}
    else:
        player = [{"condition": "minecraft:entity_properties", "entity": "this",
                   "predicate": {"location": {"structures": structure}}}]
    return {"found": {"trigger": "minecraft:location", "conditions": {"player": player}}}


def got(items):
    return {"got": {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": items}]}}}


advancement("root", None, "minecraft:dragon_egg", "Beyond the Dragon",
            "You killed the dragon. That was the tutorial.",
            {"entered_end": {"trigger": "minecraft:changed_dimension", "conditions": {"to": "minecraft:the_end"}}},
            background="minecraft:gui/advancements/backgrounds/end")
advancement("middle_reaches", "root", "minecraft:elytra", "The Middle Reaches",
            "Fly 2,000 blocks from the centre. The End gets more rewarding the farther out you go.", GRANTED)
advancement("outer_reaches", "middle_reaches", "minecraft:chorus_fruit", "The Outer Reaches",
            "5,000 blocks out. Something only forms in the end stone here.", GRANTED)
advancement("the_edge", "outer_reaches", "minecraft:end_rod", "The Edge",
            "10,000 blocks from the centre. Few have been this far.", GRANTED, frame="challenge")
advancement("ruins", "root", "minecraft:end_stone_bricks", "Ruins",
            "Someone was here before you. Four kinds of ruin drift through the islands; their chests hold notes.",
            found(f"#{NS}:ruins"))
advancement("keepers_eye", "ruins", "minecraft:lectern", "Keeper's Eye",
            "An observatory's lectern lists what the storm took. Put it all back.", GRANTED, frame="goal")
advancement("keepers_chart", "keepers_eye", "minecraft:filled_map", "Keeper's Chart",
            "The restored lectern's map marks the nearest ruin of each kind. Sneak at it for another.", GRANTED)
advancement("captains_log", "ruins", "minecraft:purpur_block", "Captain's Log",
            "A wreck on the islands. The captain kept a log.", found(ns("wreck")))
advancement("bone_garden", "ruins", "minecraft:bone_block", "Bone Garden",
            "A dragon graveyard. Its vault wants a trial key; the phantoms above carry them.", found(ns("graveyard")))
advancement("the_slip", "ruins", "minecraft:purpur_pillar", "The Slip",
            "A city sinking into the void. Mind the bridges.", found(ns("sinking_city")))
advancement("far_metal", "root", ns("aphelite_shard"), "A Far Metal",
            "A shard from end stone past the outer ring. Four of these and four popped chorus fruit make an ingot.",
            got(ns("aphelite_shard")))
advancement("aphelite", "far_metal", ns("aphelite_ingot"), "Aphelite",
            "An ingot and an Aphelite Upgrade template turn netherite into this at a smithing table. Graveyards keep the templates.",
            got(ns("aphelite_ingot")))
advancement("past_netherite", "aphelite", ns("aphelite_chestplate"), "Past Netherite",
            "Each piece worn shortens a shulker's levitation; helmet, leggings and boots together make every fall in the End harmless.",
            got(f"#{NS}:aphelite_gear"), frame="goal")
advancement("dragon_notices", "root", "minecraft:beacon", "The Dragon Takes Notice",
            "A lit beacon, and the egg on top. Every respawn after that brings it back stronger.", GRANTED)
advancement("tier_1", "dragon_notices", "minecraft:shulker_shell", "Tier 1",
            "It fires shulker bullets now. Its first death drops the Dragon Scale armour trim.", GRANTED)
advancement("tier_2", "tier_1", "minecraft:dragon_breath", "Tier 2",
            "Its breath pools drag you in. Its first death drops an Aphelite Upgrade template.", GRANTED)
advancement("tier_3", "tier_2", "minecraft:dragon_head", "Tier 3",
            "It dives. Its first death drops another Aphelite Upgrade template, and the ladder ends here.", GRANTED, frame="challenge")

write(DATA / "tags" / "worldgen" / "structure" / "ruins.json",
      {"values": [ns("observatory"), ns("wreck"), ns("graveyard"), ns("sinking_city")]})
write(DATA / "tags" / "item" / "aphelite_gear.json",
      {"values": [ns(n) for n in ("aphelite_helmet", "aphelite_chestplate", "aphelite_leggings", "aphelite_boots",
                                  "aphelite_sword", "aphelite_pickaxe", "aphelite_axe", "aphelite_shovel", "aphelite_hoe")]})

# The language file is kept by hand except for these keys, which are rewritten from the list above.
lang_path = ASSETS / "lang" / "en_us.json"
lang = json.loads(lang_path.read_text(encoding="utf-8"))
lang = {k: v for k, v in lang.items() if not k.startswith(f"advancements.{NS}.")}
for path, title, description in ADVANCEMENTS:
    lang[f"advancements.{NS}.{path}.title"] = title
    lang[f"advancements.{NS}.{path}.description"] = description
lang_path.write_text(json.dumps(lang, indent="\t", ensure_ascii=False) + "\n", encoding="utf-8")

# --- the Outer Reaches structures -------------------------------------------------------------
# Templates come from tools/generate-structures.js; this is the worldgen and loot around them.
# Every End biome, vanilla or modded; the structure type's own height and distance checks keep
# the ruins off the void and away from the dragon's island.
# Every template stands on PLINTH blocks of end stone (generate-structures.js); the start sinks
# by as much so the plinth is buried on flat ground and is the foundation on a slope. MAX_SLOPE
# is how uneven (highest minus lowest surface, in blocks) the template's box may be: the plinth covers
# that much of a drop. No terrain adaptation: beard_box laid a flat slab the size of the whole box
# under the sinking city and square terraces round the observatory, and did nothing on Stellarity;
# the plinth does the footing instead.
PLINTH = 10
MAX_SLOPE = 10
write(MC_DATA.parent / NS / "tags" / "worldgen" / "biome" / "has_structure" / "outer_reaches.json",
      {"values": ["#minecraft:is_end"]})
write(DATA / "worldgen" / "structure" / "observatory.json", {
    "type": ns("end_jigsaw"),
    "biomes": f"#{NS}:has_structure/outer_reaches",
    "step": "surface_structures",
    "terrain_adaptation": "none",
    "spawn_overrides": {},
    "start_pool": ns("observatory/start"),
    "size": 1,
    "start_height": {"absolute": -PLINTH},
    "max_distance_from_center": 32,
    "min_surface_y": 50,
    "max_slope": MAX_SLOPE,
    "min_distance": 1000,
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
def with_lore(kind, table):
    table["pools"].append({"rolls": 1, "entries": [lore_book(kind)]})
    return table


write(DATA / "loot_table" / "chests" / "observatory.json", with_lore("observatory", {
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
}))

# The other three Outer Reaches structures (templates from generate-structures.js).
def structure(name, pool_name, template, size, spacing, separation, salt, extra_biomes=None, min_surface_y=50, max_slope=MAX_SLOPE, footing=None):
    write(DATA / "worldgen" / "structure" / f"{name}.json", {
        "type": ns("end_jigsaw"),
        "biomes": f"#{NS}:has_structure/outer_reaches",
        "step": "surface_structures",
        "terrain_adaptation": "none",
        "spawn_overrides": {},
        "start_pool": ns(f"{pool_name}/start"),
        "size": 1,
        "start_height": {"absolute": -PLINTH},
        "max_distance_from_center": size,
        "min_surface_y": min_surface_y,
        "max_slope": max_slope,
        **({"footing": footing} if footing else {}),
        "min_distance": 1000,
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

structure("wreck", "wreck", "wreck", 40, 28, 12, 20261015)
# The graveyard's box is the longest that must be wholly on land (29 by 50), so it takes a little
# more slope than the others; on rugged modded Ends it was the rarest ruin by far.
structure("graveyard", "graveyard", "graveyard", 48, 36, 16, 20261016, max_slope=12)
# The city hangs over the edge by design: only its lowest house (the south cluster's base, template
# x 14-27, z 56-69) must be on land, and the rest may stand over the void. Closer together than the
# others too; with the whole box required to be on land it was one city per 2,500 blocks.
structure("sinking_city", "sinking_city", "sinking_city", 80, 36, 16, 20261017, footing=[14, 56, 27, 69])

def chest_loot(name, rolls, entries, far_entries):
    write(DATA / "loot_table" / "chests" / f"{name}.json", {
        "type": "minecraft:chest",
        "pools": [
            {"rolls": rolls, "entries": entries},
            {"rolls": 1, "condition": {"type": ns("in_ring"), "ring": "outer"}, "entries": far_entries},
            {"rolls": 1, "entries": [lore_book(name)]},
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
    item("minecraft:trial_key", 7),
], [item(ns("aphelite_shard"), 3, 1, 3), {"type": "minecraft:empty", "weight": 1}])
chest_loot("sinking_city", uniform(4, 7), [
    item("minecraft:purpur_block", 8, 6, 16), item("minecraft:end_rod", 6, 3, 8), item("minecraft:magenta_stained_glass", 5, 4, 12),
    item("minecraft:diamond", 4, 1, 3), item("minecraft:emerald", 4, 2, 5), item("minecraft:shulker_shell", 3, 1, 2),
    item("minecraft:ender_pearl", 6, 2, 5), item("minecraft:chorus_fruit", 8, 4, 10), item("minecraft:experience_bottle", 6, 3, 8),
    item("minecraft:saddle", 2), item("minecraft:golden_apple", 3, 1, 2),
], [item(ns("aphelite_shard"), 4, 2, 5), item(ns("aphelite_ingot"), 1), {"type": "minecraft:empty", "weight": 1}])

# The graveyard's vault: one template per player who brings a trial key, plus a little extra.
write(DATA / "loot_table" / "chests" / "graveyard_vault.json", {
    "type": "minecraft:chest",
    "pools": [
        {"rolls": 1, "entries": [item(ns("aphelite_upgrade_smithing_template"), 1)]},
        {"rolls": 1, "entries": [item("minecraft:dragon_breath", 3, 2, 4), item("minecraft:experience_bottle", 3, 4, 8), item(ns("aphelite_shard"), 2, 1, 2)]},
    ],
})
# The graveyard's phantoms carry the key.
write(DATA / "loot_table" / "entities" / "graveyard_phantom.json", {
    "type": "minecraft:entity",
    "pools": [
        {"rolls": 1, "entries": [item("minecraft:trial_key", 1)]},
        {"rolls": 1, "entries": [item("minecraft:phantom_membrane", 1, 0, 1)]},
    ],
})

def enchanted_book(weight, lo=20, hi=39):
    return {"type": "minecraft:item", "name": "minecraft:book", "weight": weight,
            "modifier": [{"type": "minecraft:enchant_with_levels", "levels": {"type": "minecraft:uniform", "min": lo, "max": hi}}]}

# Enchanted books in the observatory and the city, a disc in the city (edits to the tables above).
import json as _json
def amend(name, extra_common):
    f = DATA / "loot_table" / "chests" / f"{name}.json"
    d = _json.loads(f.read_text(encoding="utf-8"))
    d["pools"][0]["entries"].extend(extra_common)
    write(f, d)
amend("observatory", [enchanted_book(3)])
amend("sinking_city", [enchanted_book(3), item("minecraft:music_disc_otherside", 1)])
