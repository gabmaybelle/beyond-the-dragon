package com.gabbymaybelle.beyondthedragon.neoforge;

import com.gabbymaybelle.beyondthedragon.BeyondTheDragon;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** The NeoForge entrypoint: everything happens in {@code common}. */
@Mod(BeyondTheDragon.MOD_ID)
public class BeyondTheDragonNeoForge {
	public BeyondTheDragonNeoForge() {
		BeyondTheDragon.init();
		NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, event -> BeyondTheDragon.serverTick(event.getServer()));
	}
}
