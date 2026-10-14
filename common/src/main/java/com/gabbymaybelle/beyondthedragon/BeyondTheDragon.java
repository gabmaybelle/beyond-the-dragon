package com.gabbymaybelle.beyondthedragon;

import com.gabbymaybelle.beyondthedragon.aphelite.Aphelite;
import com.gabbymaybelle.beyondthedragon.platform.Services;
import com.gabbymaybelle.beyondthedragon.structures.Structures;
import com.gabbymaybelle.beyondthedragon.titles.TitleWatcher;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Beyond the Dragon: a reason to keep playing after the Ender Dragon. The loader entrypoints
 * ({@code fabric} and {@code neoforge}) call {@link #init()} once; everything that doesn't care
 * which loader it runs on lives here in {@code common}.
 */
public final class BeyondTheDragon {
	public static final String MOD_ID = "beyond_the_dragon";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private BeyondTheDragon() {
	}

	public static void init() {
		Config.load();
		Aphelite.register();
		Structures.register();
		LOGGER.info("Beyond the Dragon: ready on {} (rings at {}, {} and {} blocks; biome titles {}; dragon ladder {})",
				Services.PLATFORM.name(), Config.ringMiddle, Config.ringOuter, Config.ringEdge,
				Config.biomeTitles ? "on" : "off", Config.dragonLadder ? "on" : "off");
	}

	/** Each loader calls this at the end of every server tick. */
	public static void serverTick(MinecraftServer server) {
		TitleWatcher.serverTick(server);
	}
}
