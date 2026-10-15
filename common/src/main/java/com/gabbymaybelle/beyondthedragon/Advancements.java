package com.gabbymaybelle.beyondthedragon;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The advancements tab is the in-game guide; the entries that vanilla triggers can't express
 * are granted here, from the moments the mod already notices. Granting is idempotent and cheap,
 * so callers don't track what a player already has.
 */
public final class Advancements {
	private Advancements() {
	}

	public static void grant(ServerPlayer player, String path) {
		MinecraftServer server = player.level().getServer();
		if (server == null) {
			return;
		}
		AdvancementHolder holder = server.getAdvancements().get(ResourceLocation.fromNamespaceAndPath(BeyondTheDragon.MOD_ID, path));
		if (holder != null) {
			player.getAdvancements().award(holder, "granted");
		}
	}
}
