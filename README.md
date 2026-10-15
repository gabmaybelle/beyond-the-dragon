# Beyond the Dragon

A Fabric and NeoForge mod for Minecraft 26.3 (26.1.2 is on the `26.1` branch and 1.21.1 on the
`1.21.1` branch). You killed the dragon; that was the tutorial.

Beyond the Dragon is for players who have done it all. It leaves the game you know alone and
adds reasons to pack your elytra and keep flying: ruins at the edge of the End, a dragon that
comes back harder every time, and a metal that only exists very far from home. No magic, no
machines, nothing that fights your other mods.

## What's in it

- **The Far End.** The End gets more rewarding the farther you fly from the centre. A title
  tells you when you cross into the Middle Reaches (2,000 blocks out), the Outer Reaches (5,000)
  or the Edge (10,000), and some things only exist past the outer ring.
- **Names for the End's biomes**, and a title when you enter any new biome, in any dimension.
- **A guide that doesn't spoil.** A "Beyond the Dragon" advancements tab whose entries hint at the
  next step, and a short note in every ruin's chest that points at another ruin or at what the
  End keeps. Nothing is forced on you; look when you want a nudge.
- **The Outer Reaches.** Four kinds of ruin on the End's islands, built from the End's own
  architecture: abandoned observatories, wrecked End ships, dragon graveyards and cities sinking
  into the void. A few shulkers and phantoms guard them. Chests hold End odds and ends and, past
  the outer ring, aphelite.
- **Restore an observatory** and it sees again: its lectern gives the Keeper's Chart, a map
  marking the nearest ruin of each kind and the nearest End City, and from then on anyone who
  dies in the End comes back there. Beds explode in the End and anchors don't work, so this is
  the End's first respawn point. The book on the lectern lists exactly what's missing.
- **The Dragon Ladder.** Sneak to set the dragon egg on a lit beacon and the dragon takes
  notice: every time you respawn it after that, it comes back one tier stronger, up to tier 3,
  with a new attack each tier. The first kill at each tier drops a reward at the exit portal.
- **Aphelite.** A metal a step past netherite. Its ore forms in end stone past the outer ring;
  four shards and four popped chorus fruit make an ingot; an ingot and an Aphelite Upgrade
  template turn any netherite piece into aphelite at a smithing table. Its real worth is what
  it does: each piece worn cuts a shulker's levitation, helmet, leggings and boots together make
  every fall and elytra crash in the End harmless, and an aphelite pickaxe sometimes pulls an
  extra shard from the ore. The upgrade template waits in a vault at every dragon graveyard
  (bring a trial key; the graveyard's phantoms carry them) and drops from the tier 2 and 3
  dragons; the Dragon Scale armour trim drops from the tier 1 dragon.

## Settings

In `config/beyond_the_dragon.properties`: the ring distances, every title, every dragon attack,
the aphelite perks, and the list of mods that count as reworking the dragon fight (with one of
those installed, the new attacks stay off unless you turn them on; the tiers and rewards still
run).

## Works with

Vanilla blocks and vanilla mechanics throughout, so resource packs and shaders just work.
Structures generate on vanilla or modded End terrain and the game test runs with BetterEnd,
Stellarity and Nullscape installed (each on its own). Recipes use the crafting and smithing
tables, so recipe viewers show them; the game test loads with JEI and with REI (EMI has no
26.3 build yet). Nothing scans the world while you play: the rings and titles are one check per
player per second, and structure lookups come from the seed.

Other mods can find aphelite without knowing about this one: the ingot and the ore carry the
shared tags mods look for (`c:ingots/aphelite`, `c:ores/aphelite`). With
[Forgemark](https://github.com/gabmaybelle/forgemark) installed, aphelite gear can be tempered
like netherite: the ingot heats slowly and takes four strikes per heat.

Servers: flying fast into new territory is the heaviest thing any player can do to a server,
with or without mods. Pre-generating the End with Chunky keeps things smooth.

## Building

`./gradlew build` makes both jars (`fabric/build/libs/`, `neoforge/build/libs/`).
`./gradlew :fabric:runClientGameTest` plays the in-game test. See `CLAUDE.md` for the layout.

Made by Gabby with help from Claude, an AI assistant. MIT licence.
