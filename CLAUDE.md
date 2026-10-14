# Beyond the Dragon

A Fabric and NeoForge mod for Minecraft 26.3 (mod ID `beyond_the_dragon`): a reason to keep
playing after the Ender Dragon. Ruins drift through the outer End, the dragon gets harder every
time it's respawned, and aphelite (a metal a step past netherite) only exists far from the centre.
The design doc with every decision is Gabby's Claude doc "Beyond the Dragon — design doc"; this
file holds what a session needs to work here.

## Layout

One codebase, two jars:

- `common/`: everything that doesn't care which loader it runs on. `BeyondTheDragon.init()` is the
  one entrypoint; `Config` is the settings file; `platform/` is the tiny loader abstraction
  (`Platform`, found through `ServiceLoader`); `mixin/` the Mixins (listed in
  `beyond_the_dragon.mixins.json`). All data files (structures, loot, tags, lang) live in
  `common/src/main/resources`. Never import Fabric or NeoForge here; `./gradlew :common:build`
  fails if you do.
- `fabric/` and `neoforge/`: the entrypoints and a `Platform` each. Their `build.gradle` compiles
  `common`'s sources straight into their jar.
- `fabric/src/gametest/`: the client game test (`BeyondTheDragonClientTest`, which runs
  `ApheliteTest` and `LadderTest`).
- `tools/`: `TextureGen.java` draws the aphelite textures from vanilla's netherite ones (run it
  with `java tools/TextureGen.java <unpacked vanilla textures> common/src/main/resources/assets/beyond_the_dragon`);
  `generate-data.py` writes every aphelite and structure data and asset JSON (recipes, tags,
  loot, worldgen, item models with all trim overlays); `generate-structures.js` builds the
  structure templates by remixing vanilla's End City and fossil pieces, read straight from the
  game jar (`--jar`, default: Loom's 26.3 merged jar under `~/.gradle`), through `structure.js`
  + `nbt.js`; the observatory comes as an intact template (what restoration compares against)
  and a `_ruin` (what generates), with the lectern book written from the difference.
  `render-structure.js <template.nbt> --out <png> --jar <jar>` draws a template isometrically
  with vanilla textures (`render-blocks.js` is the gazebo mod's renderer, taught the End blocks)
  for looking at designs without starting the game. Edit the scripts, not their output.
- `common/.../structures/`: `EndJigsawStructure` (the `beyond_the_dragon:end_jigsaw` structure
  type: a jigsaw that refuses to start unless the footprint's corners are on an island) and
  `InRingCondition` (the `beyond_the_dragon:in_ring` loot condition).

Registering anything (items, blocks, placement modifiers) goes through
`Services.PLATFORM.register(...)`, never `Registry.register` directly: NeoForge only accepts
registrations in its registry event, so its `Platform` queues them and the result is a
`Supplier`. The same goes for creative tabs (`addToCreativeTab`) and End ore placement
(`addOreToEndBiomes`; NeoForge does that one through `data/.../neoforge/biome_modifier/`).

## Build and test

- `./gradlew build`: jars in `fabric/build/libs/` and `neoforge/build/libs/`.
- `./gradlew :fabric:runClientGameTest`: the settings file and the in-game checks, in a real game
  window (five minutes or so: it climbs the whole Dragon Ladder). Add `-PtestBetterEnd`,
  `-PtestStellarity` or `-PtestNullscape` to play it with that mod installed (fetched from
  Modrinth's maven, never bundled); CI runs all four. With a mod that reworks the dragon fight
  installed, the test expects our tier attacks to stay off. BetterEnd keeps the fight but
  replaces the respawn check (crystals four blocks from the End's origin, on bedrock, any
  height); the ladder test places them there when it is loaded.
- NeoForge load check: `node ~/mods-dev/tools/neoforge-server-test.js <scratch folder> --mod neoforge/build/libs/<jar>`
  starts a NeoForge server with the jar and fails on any data or mod error.
- Fresh test worlds and background game driving: `~/mods-dev/tools/README.md`
  (`new-test-world.js`, `mcf.js`).

## Gotchas

- **The common sources are compiled twice**, once per loader. A class that compiles on Fabric but
  not NeoForge (or the reverse) is a common class that leaked a loader import.
- **Mixins** need no refmap on either loader: Minecraft 26.x ships with Mojang names at runtime, so
  the one `beyond_the_dragon.mixins.json` serves both jars as it is.
- **The ladder's loot tables are placeholders** (dragon's breath) until the aphelite items exist;
  `data/beyond_the_dragon/loot_table/dragon_ladder/tier_N.json`.
- **Dragon fight code is the fragile part.** Vanilla's dragon phases are hard-coded and differ
  between 1.21.1 and 26.x. Keep every new attack behind its own setting and reuse vanilla phases
  and entities (charge phase, shulker bullets, lingering clouds) rather than inventing new ones.
- **End structures must check for ground.** A data-only jigsaw happily generates at height 0 over
  the void; our structure type samples the surface on a 3×3 grid over the footprint and refuses
  a start that is too low or too uneven (`max_slope`). Every template also stands on a plinth of
  end stone (`PLINTH` in both generator scripts) and starts that deep below the surface, so the
  plinth is buried on flat ground and is the foundation on a slope. `beard_box` stays on but
  can't be relied on: Stellarity's End density ignores it.
- **Nothing ticks across the world.** Rings and titles are one check per player per second;
  structure lookups are computed from the seed, never by loading chunks.
- Vanilla names changed a lot in 26.x: check the merged jar rather than older docs. Data files
  too: loot entries take `"modifier": [{"type": "minecraft:set_count", ...}]` and
  `"condition": ...`, not the old `functions`/`function`/`conditions`, and unknown keys are
  ignored without a warning. Copy the shape from a vanilla file in the merged jar
  (`data/minecraft/...`) before writing a new kind of data file.
- Decompiled 26.3 sources (for reading vanilla, not for copying): ModDevGradle leaves them in
  `~/.gradle/caches/neoformruntime/intermediate_results/mergeWithSources_*_output.jar`.
- **Jigsaw pieces have no mirror**: `PoolElementStructurePiece.getMirror()` is null; treat it as
  `Mirror.NONE` before any `StructureTemplate.transform`. A target decoration added with
  `MapItemSavedData.addTargetDecoration` lives in the item's `map_decorations` component until
  the map is carried; read the component, not the saved data, straight after making a map.
- **Observatory restoration** compares the world against
  `data/beyond_the_dragon/restoration/observatory.json` (written by the generator from the
  intact/ruin difference), at each block's rotated world position; it is read from the jar with
  `getResourceAsStream`, so there is no reload listener to keep per loader.
- The egg must be **sneak-placed** on the beacon, like any block on a container; a plain
  right-click opens the beacon's menu. The egg is see-through, so it doesn't dim the beam.

## Working on Gabby's mods (the same in every repo)

Gabby makes these mods with help from Claude. Several Claude sessions work on them: one on her
computer and others in the cloud. These rules keep their work fitting together.

**One session per repo at a time.**
- A cloud session works on a `claude/...` branch and never pushes to `main`. Gabby's local
  session reviews the branch, runs its tests and merges it as one commit.
- Start from the latest `main` (`git pull`), and say in the branch's last commit or a short note
  what is finished and what isn't.

**Commits.**
- Author and committer are always `gabmaybelle <211247938+gabmaybelle@users.noreply.github.com>`:
  `git -c user.name=gabmaybelle -c user.email=211247938+gabmaybelle@users.noreply.github.com commit ...`.
  Never a personal email, and never `Claude <noreply@anthropic.com>` as the author. Claude goes in
  a `Co-Authored-By:` line at the end of the message.
- Ask Gabby before committing or pushing when she's around. Finish the build and the tests first.
- Commit messages say what changed for players, in plain sentences.

**Privacy.** These repos are public. Don't add personal email addresses, real names other than
"Gabby", or anything about her other private projects and modpacks: not in code, docs, commits
or test names. Check the diff for these before every push.

**Tests.**
- `./gradlew build` and `./gradlew :fabric:runClientGameTest` must pass before a commit. The game
  test opens a real game window for a minute or two.
- A new or changed check should fail with the feature switched off: run it that way once, then
  put the code back. A check that compares against the setting it tests proves nothing.
- On Linux without a screen, run game tests under `xvfb-run -a`. 26.3 also needs
  `SDL_VIDEO_FORCE_EGL=1` with `libegl1 libegl-mesa0` installed, or the game hangs at startup.

**Writing.**
- README and CHANGELOG are for players: plain, friendly, no jargon. New player-facing changes
  go in the CHANGELOG's newest section.
- Keep the credit line: "made by Gabby with help from Claude, an AI assistant".
- Gabby publishes on Modrinth herself. Don't upload anything there.

**Minecraft.** Fabric and NeoForge on Minecraft 26.3 (Mojang names; Loom 1.18.4, ModDevGradle
2.0, Java 25). Vanilla names changed a lot in 26.x: check the merged jar rather than older docs
(for example `iron_chain`, `pink_petals[flower_amount=N]`, timelines instead of `time query daytime`).
