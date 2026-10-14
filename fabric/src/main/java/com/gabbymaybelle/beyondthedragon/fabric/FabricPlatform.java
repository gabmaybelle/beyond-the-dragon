package com.gabbymaybelle.beyondthedragon.fabric;

import com.gabbymaybelle.beyondthedragon.platform.Platform;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

public class FabricPlatform implements Platform {
	@Override
	public String name() {
		return "Fabric";
	}

	@Override
	public Path configDir() {
		return FabricLoader.getInstance().getConfigDir();
	}

	@Override
	public boolean isModLoaded(String modId) {
		return FabricLoader.getInstance().isModLoaded(modId);
	}
}
