package com.gabbymaybelle.beyondthedragon.neoforge;

import com.gabbymaybelle.beyondthedragon.platform.Platform;
import java.nio.file.Path;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

public class NeoForgePlatform implements Platform {
	@Override
	public String name() {
		return "NeoForge";
	}

	@Override
	public Path configDir() {
		return FMLPaths.CONFIGDIR.get();
	}

	@Override
	public boolean isModLoaded(String modId) {
		return ModList.get().isLoaded(modId);
	}
}
