# Beyond the Dragon

A Fabric and NeoForge mod for Minecraft 1.21.1 (this is the `1.21.1` branch; `main` is for 26.3
and `26.1` for 26.1.2). You killed the dragon; that was the tutorial.

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
  marking the nearest ruin of each kind and the nearest End City, and a charged respawn anchor
  wakes in its top room. Beds explode in the End and so do anchors anywhere else, so a restored
  observatory is the one place you can set your spawn out there. With Waystones installed, it
  gets a waystone too. The book on the lectern says how to mend its telescope.
- **The Dragon Ladder.** Sneak to set the dragon egg on a lit beacon and the dragon takes
  notice: every time you respawn it after that, it comes back one tier stronger, up to tier 3,
  with a new attack each tier. The first kill at each tier drops a reward at the exit portal.
- **The Void Jelly.** A gentle creature of the far End, half slime and half ghast: a see-through
  teal jelly that glows softly in the dark. Wild ones drift among the islands from the Middle
  Reaches out. Find a Jelly Egg in a sinking city's chest, set it beside chorus and it hatches;
  feed the baby chorus fruit to help it grow, then give it a saddle and take up to three friends
  along. Steer with a Chorus Flower on a Stick; it flies faster in the End than anywhere else.
- **Aphelite.** A metal a step past netherite. Its ore forms in end stone past the outer ring;
  four shards and four popped chorus fruit make an ingot; an ingot and an Aphelite Upgrade
  template turn any netherite piece into aphelite at a smithing table. Its real worth is what
  it does: each piece worn cuts a shulker's levitation, helmet, leggings and boots together make
  every fall and elytra crash in the End harmless, and an aphelite pickaxe sometimes pulls an
  extra shard from the ore. The upgrade template waits in a vault at every dragon graveyard
  (bring a trial key; the graveyard's phantoms carry them) and drops from the tier 2 and 3
  dragons; the Dragon Scale armour trim drops from the tier 1 dragon, and now and then turns up in a
  graveyard's chest.

## Settings

In `config/beyond_the_dragon.properties`: the ring distances, every title, every dragon attack,
the aphelite perks, and the list of mods that count as reworking the dragon fight (with one of
those installed, the new attacks stay off unless you turn them on; the tiers and rewards still
run).

## Works with

Vanilla blocks and vanilla mechanics throughout, so resource packs and shaders just work.
Structures generate on vanilla or modded End terrain and the game test runs with Stellarity
installed. BetterEnd and Nullscape are tested on the 26.3 branch only: BetterEnd's last 1.21.1
builds don't load together, and Nullscape's 1.21.1 End is so sparse that graveyards fall just
short of the test's count there (they still generate). Recipes use the crafting and smithing
tables, so recipe viewers show them; the game test loads with JEI and with REI. Nothing scans the world while you play: the rings and titles are one check per
player per second, and structure lookups come from the seed.

Other mods can find aphelite without knowing about this one: the ingot and the ore carry the
shared tags mods look for (`c:ingots/aphelite`, `c:ores/aphelite`).

Servers: flying fast into new territory is the heaviest thing any player can do to a server,
with or without mods. Pre-generating the End with Chunky keeps things smooth.

## Building

`./gradlew build` makes both jars (`fabric/build/libs/`, `neoforge/build/libs/`).
`./gradlew :fabric:runClientGameTest` plays the in-game test. See `CLAUDE.md` for the layout.

Made by Gabby with help from Claude, an AI assistant. MIT licence.
