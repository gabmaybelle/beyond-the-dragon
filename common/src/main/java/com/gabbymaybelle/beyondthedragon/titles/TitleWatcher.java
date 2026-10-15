package com.gabbymaybelle.beyondthedragon.titles;

import com.gabbymaybelle.beyondthedragon.Advancements;
import com.gabbymaybelle.beyondthedragon.BeyondTheDragon;
import com.gabbymaybelle.beyondthedragon.Config;
import com.gabbymaybelle.beyondthedragon.rings.Rings;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

/**
 * A title when a player enters a new biome, and in the End a subtitle with the ring they're in.
 * Runs on the server once a second per player: the biome under them is compared with the last
 * one shown, and a change is announced only after they've stayed in the new biome for the
 * dwell time and the cooldown since the last title has passed, so fast flight stays quiet.
 */
public final class TitleWatcher {
	private static final int FADE_IN = 10;
	private static final int STAY = 60;
	private static final int FADE_OUT = 20;
	private static final Set<ResourceKey<Biome>> END_BIOMES = Set.of(
			Biomes.THE_END, Biomes.SMALL_END_ISLANDS, Biomes.END_MIDLANDS, Biomes.END_HIGHLANDS, Biomes.END_BARRENS);

	private static final Map<UUID, State> STATES = new HashMap<>();

	/** What's been shown to one player, and what they're waiting on. */
	private static final class State {
		ResourceKey<Biome> shownBiome;
		Rings shownRing;
		ResourceKey<Biome> candidateBiome;
		Rings candidateRing;
		long candidateSince;
		long lastTitle = Long.MIN_VALUE / 2;
		long seen;
	}

	private TitleWatcher() {
	}

	/** Called every server tick; does its work once a second. */
	public static void serverTick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) {
			return;
		}
		long now = server.getTickCount();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			State state = STATES.computeIfAbsent(player.getUUID(), id -> new State());
			state.seen = now;
			watch(player, state, now);
		}
		STATES.values().removeIf(state -> state.seen != now);
	}

	/** Forgets what a player has been shown, so their next biome shows again (used by tests). */
	public static void forget(UUID player) {
		STATES.remove(player);
	}

	/** The biome a player was last shown a title for, if any (used by tests). */
	public static Optional<ResourceKey<Biome>> shownBiome(UUID player) {
		State state = STATES.get(player);
		return state == null ? Optional.empty() : Optional.ofNullable(state.shownBiome);
	}

	/** The ring a player was last shown as a subtitle, if any (used by tests). */
	public static Optional<Rings> shownRing(UUID player) {
		State state = STATES.get(player);
		return state == null ? Optional.empty() : Optional.ofNullable(state.shownRing);
	}

	private static void watch(ServerPlayer player, State state, long now) {
		ResourceKey<Level> dimension = player.level().dimension();
		if (dimension == Level.END) {
			// The rings' advancements, whether or not titles are on.
			Rings reached = Rings.at(player.getX(), player.getZ());
			if (reached.ordinal() >= Rings.MIDDLE.ordinal()) {
				Advancements.grant(player, "middle_reaches");
			}
			if (reached.ordinal() >= Rings.OUTER.ordinal()) {
				Advancements.grant(player, "outer_reaches");
			}
			if (reached.ordinal() >= Rings.EDGE.ordinal()) {
				Advancements.grant(player, "the_edge");
			}
		}
		if (!enabledIn(dimension)) {
			return;
		}
		Optional<ResourceKey<Biome>> biome = player.level().getBiome(player.blockPosition()).unwrapKey();
		if (biome.isEmpty()) {
			return;
		}
		boolean inEnd = dimension == Level.END;
		Rings ring = inEnd && Config.ringTitles ? Rings.at(player.getX(), player.getZ()) : null;
		boolean changed = !biome.get().equals(state.shownBiome) || ring != state.shownRing;
		if (!changed) {
			state.candidateBiome = null;
			return;
		}
		if (!biome.get().equals(state.candidateBiome) || ring != state.candidateRing) {
			state.candidateBiome = biome.get();
			state.candidateRing = ring;
			state.candidateSince = now;
		}
		boolean dwelt = now - state.candidateSince >= Config.biomeTitleDwellSeconds * 20L;
		boolean rested = now - state.lastTitle >= Config.biomeTitleCooldownSeconds * 20L;
		if (!dwelt || !rested) {
			return;
		}
		show(player, biome.get(), ring);
		state.shownBiome = biome.get();
		state.shownRing = ring;
		state.candidateBiome = null;
		state.lastTitle = now;
	}

	private static boolean enabledIn(ResourceKey<Level> dimension) {
		if (!Config.biomeTitles) {
			return false;
		}
		if (dimension == Level.OVERWORLD) {
			return Config.biomeTitlesOverworld;
		}
		if (dimension == Level.NETHER) {
			return Config.biomeTitlesNether;
		}
		if (dimension == Level.END) {
			return Config.biomeTitlesEnd;
		}
		return Config.biomeTitlesOverworld;
	}

	private static void show(ServerPlayer player, ResourceKey<Biome> biome, Rings ring) {
		player.connection.send(new ClientboundSetTitlesAnimationPacket(FADE_IN, STAY, FADE_OUT));
		player.connection.send(new ClientboundSetSubtitleTextPacket(
				ring == null ? Component.empty() : Component.translatable(ring.titleKey())));
		player.connection.send(new ClientboundSetTitleTextPacket(name(biome)));
		BeyondTheDragon.LOGGER.debug("Title for {}: {}{}", player.getScoreboardName(), biome.location(),
				ring == null ? "" : " (" + ring + ")");
	}

	/** The biome's name: the mod's own for the End's biomes when that's on, else the vanilla one. */
	public static Component name(ResourceKey<Biome> biome) {
		if (Config.endBiomeNames && END_BIOMES.contains(biome)) {
			return Component.translatable("biome.beyond_the_dragon." + biome.location().getPath());
		}
		return Component.translatable("biome." + biome.location().getNamespace() + "." + biome.location().getPath());
	}
}
