package com.gabbymaybelle.beyondthedragon.fabric;

import com.gabbymaybelle.beyondthedragon.BeyondTheDragon;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/** The Fabric entrypoint: everything happens in {@code common}. */
public class BeyondTheDragonFabric implements ModInitializer {
	@Override
	public void onInitialize() {
		BeyondTheDragon.init();
		ServerTickEvents.END_SERVER_TICK.register(BeyondTheDragon::serverTick);
	}
}
