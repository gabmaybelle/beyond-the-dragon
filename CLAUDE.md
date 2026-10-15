# Beyond the Dragon

A Fabric and NeoForge mod for Minecraft 26.1.2 on this branch (`26.1`; `main` is 26.3; mod ID
`beyond_the_dragon`): a reason to keep
playing after the Ender Dragon. Ruins drift through the outer End, the dragon gets harder every
time it's respawned, and aphelite (a metal a step past netherite) only exists far from the centre.
The design doc with every decision is Gabby's Claude doc "Beyond the Dragon — design doc"; this
file holds what a session needs to work here.
`PROGRESS.md` says what's done, what's broken and what's next, with a row per session: read
it first, and add this session's row before ending.

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
  loot, worldgen, item models with all trim overlays); `TrimGen.java` draws the Dragon Scale
  trim sheets (`java tools/TrimGen.java common/src/main/resources/assets/beyond_the_dragon`); `generate-structures.js` builds the
  structure templates by remixing vanilla's End City and fossil pieces, read straight from the
  game jar (`--jar`, default: Loom's merged jar for this branch's version under `~/.gradle`), through `structure.js`
  + `nbt.js`; the observatory comes as an intact template (what restoration compares against)
  and a `_ruin` (what generates), with the lectern book written from the difference.
  `render-structure.js <template.nbt> --out <png> --jar <jar>` draws a template isometrically
  with vanilla textures (`render-blocks.js` is the gazebo mod's renderer, taught the End blocks)
  for looking at designs without starting the game. Edit the scripts, not their output.
- `common/.../structures/`: `EndJigsawStructure` (the `beyond_the_dragon:end_jigsaw` structure
  type: a jigsaw that refuses to start unless the footprint's corners are on an island) and
  `InRingCondition` (the `beyond_the_dragon:in_ring` loot condition).

The in-game guide is the advancements tab (`data/.../advancement/`, written by `generate-data.py`
with its language keys) and the lore books in the ruins' chest loot (the `LORE` table in the
generator). Advancements that vanilla triggers can't express use an `impossible` criterion and
are granted from code with `Advancements.grant(player, path)` at the moments already noticed
(ring entry in `TitleWatcher`, restoration and chart in `Observatories`, unlock and tier kills in
`DragonLadder`); finding ruins uses `minecraft:location` with the `#beyond_the_dragon:ruins`
structure tag, and the item ones `inventory_changed`.

Registering anything (items, blocks, placement modifiers) goes through
`Services.PLATFORM.register(...)`, never `Registry.register` directly: NeoForge only accepts
registrations in its registry event, so its `Platform` queues them and the result is a
`Supplier`. The same goes for creative tabs (`addToCreativeTab`) and End ore placement
(`addOreToEndBiomes`; NeoForge does that one through `data/.../neoforge/biome_modifier/`).

## Build and test

- `./gradlew build`: jars in `fabric/build/libs/` and `neoforge/build/libs/`.
- `./gradlew :fabric:runClientGameTest`: the settings file and the in-game checks, in a real game
  window (five minutes or so: it climbs the whole Dragon Ladder). Add `-PtestNullscape` (or
  `-PtestStellarity`, by hand) to play it with that mod installed (fetched from Modrinth's
  maven, never bundled); CI runs it on its own and with Nullscape. BetterEnd is left out on
  this branch: every BetterEnd build that claims 26.1 requires Fabric API 0.155+, which only
  exists for 26.2, so none loads on 26.1.2. Stellarity is out of CI here too: 6.0.1 and later
  carry 26.2-format data that fails registry loading on 26.1.2, 5.4.3 never reached the title
  screen, and 5.5.5 (the pinned id) passes the whole test when it starts but hangs at startup
  about one time in three (the log stops right after the resource reload). Gabby has also had
  trouble with BetterEnd on a server, especially alongside Stellarity; the two are never tested
  together. `BTD_TEST_ONLY=structures` (or
  `=aphelite`) in the environment plays only that part of the test, in a couple of minutes, for
  tuning. `BTD_SCREENSHOTS=<folder>` keeps the test's
  screenshots (titles, each ruin from the air, the egg on the beacon, each dragon tier) there;
  the run directory they land in is thrown away otherwise. With a mod that reworks the dragon fight
  installed, the test expects our tier attacks to stay off. (On 26.3, BetterEnd keeps the fight
  but replaces the respawn check; the ladder test still carries that case.)
- NeoForge load check: start a NeoForge server with `neoforge/build/libs/<jar>` and fail on any data or mod error.

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
  the void; our structure type samples the surface on a 3×3 grid over the start template's
  rotated footing (the whole box, or the `footing` sub-box for the city, which hangs over the
  edge by design; the rotation is peeked from a copy of the context's random, whose first draw
  the jigsaw placement uses for it), refuses a start that is too low or too uneven
  (`max_slope`), and sets the height itself from the footing's centre: vanilla's projection
  reads the *whole box's* centre, which put a graveyard on the void floor. The game test's
  placement-yield check prints, per ruin and per End mod, how many candidate chunks were
  rejected for void and for slope; tune from those numbers. Ruins also keep out of End Cities
  (`avoid_end_cities`): the End City set's candidates near a ruin are asked whether the city
  really starts there, and its pieces, laid out from the seed, are compared with the ruin's
  box. A wider exclusion zone in the structure sets can't do it: it keeps clear of every chunk
  where a city *might* start, and cost up to a fifth of the ruins for the margin a city's sprawl
  needs. The game test's End City overlap check measures it per terrain. Every template also stands on a plinth of
  end stone (`PLINTH` in both generator scripts) and starts that deep below the surface, so the
  plinth is buried on flat ground and is the foundation on a slope. Terrain adaptation is off:
  `beard_box` laid a flat slab the size of the whole box under the city and square terraces round
  the observatory, and did nothing on Stellarity anyway.
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

**Commits are made one at a time, by hand.** Never commit or push from a chained script. Run
the test, read its result (and the screenshot, for anything visual), then commit that one
branch, then push it. A cherry-pick that conflicts stops the work until it is resolved by hand.
A worldgen or balance change is tested on every terrain in the branch's matrix before it goes
to any other branch. (Learned 2026-10-14, when scripted chains committed a conflicted
cherry-pick, put one branch's files on another, and sent an untested ore change to all three.)
For a Gradle game test use the toolkit's watcher (`~/.claude/CLAUDE.md`), not a wait loop.

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
- A data change (worldgen, loot, a template) gets the same proof: run the part of the test that
  sees it, on every terrain in the branch's matrix, and quote the numbers in the progress row.
- On Linux without a screen, run game tests under `xvfb-run -a`. 26.3 also needs
  `SDL_VIDEO_FORCE_EGL=1` with `libegl1 libegl-mesa0` installed, or the game hangs at startup.

**Writing.**
- README and CHANGELOG are for players: plain, friendly, no jargon. New player-facing changes
  go in the CHANGELOG's newest section.
- Keep the credit line: "made by Gabby with help from Claude, an AI assistant".
- Gabby publishes on Modrinth herself. Don't upload anything there.

**Minecraft.** Fabric and NeoForge (Mojang names; Loom 1.18.4, ModDevGradle 2.0, Java 25).
Version branches: `main` is the newest Minecraft (26.3), `26.1` is 26.1.2, and older versions get
a branch each. Behaviour changes land on `main` and are cherry-picked to the version branches,
where only the version differences are edited; CI runs on every branch. This branch is `26.1`.
The data formats differ too (loot `functions`/`conditions` here against `modifier`/`condition` on
26.3, `worldgen/configured_feature` against `worldgen/feature`, an `armor_trims` atlas for the
trim): `generate-data.py` writes everything in the 26.3 shape and converts under its `FORMAT`
constant, which is the only line that differs between the branches' generators. Structure
templates differ as well: `structure.js` writes palette entries as `Name`/`Properties` here
(`id`/`properties` on 26.3) and stamps this version's `DATA_VERSION` (4790); a template with
either of those wrong loads with its box but places no blocks, silently. Regenerate the
templates on this branch from this version's jar (`generate-structures.js` defaults to it). Vanilla names changed a lot in 26.x: check the merged jar rather than older docs
(for example `iron_chain`, `pink_petals[flower_amount=N]`, timelines instead of `time query daytime`).
