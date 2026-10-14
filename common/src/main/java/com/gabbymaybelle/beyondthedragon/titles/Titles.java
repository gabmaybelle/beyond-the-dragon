package com.gabbymaybelle.beyondthedragon.titles;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;

/** Sends a title and subtitle with the mod's usual timing. */
public final class Titles {
	private Titles() {
	}

	public static void send(ServerPlayer player, Component title, Component subtitle) {
		player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
		player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
		player.connection.send(new ClientboundSetTitleTextPacket(title));
	}
}
