package com.gabbymaybelle.beyondthedragon.structures;

import com.gabbymaybelle.beyondthedragon.BeyondTheDragon;
import com.gabbymaybelle.beyondthedragon.platform.Services;
import java.lang.reflect.Method;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * The Waystones mod, when it's installed: a restored observatory gets a purpur waystone, so
 * restored observatories join its network. Reached through its public API by reflection, so
 * the mod is never needed to build or to play.
 */
final class WaystonesCompat {
	private static final String API = "net.blay09.mods.waystones.api.WaystonesAPI";
	// 1.21.1's Waystones names the look a style (26.x: a type).
	private static final String TYPES = "net.blay09.mods.waystones.api.WaystoneStyles";

	private WaystonesCompat() {
	}

	static boolean available() {
		return Services.PLATFORM.isModLoaded("waystones");
	}

	/** Places a waystone (two blocks tall, from pos up) and activates it for the player who restored the observatory, if any. */
	static void place(ServerLevel level, BlockPos pos, ServerPlayer player) {
		try {
			Class<?> api = Class.forName(API);
			Object purpur = Class.forName(TYPES).getField("PURPUR").get(null);
			Optional<?> waystone = (Optional<?>) method(api, "placeWaystone", 3).invoke(null, level, pos, purpur);
			if (waystone.isPresent() && player != null) {
				method(api, "activateWaystone", 2).invoke(null, player, waystone.get());
			}
			BeyondTheDragon.LOGGER.info("Waystones: a waystone at {}{}", pos, waystone.isPresent() ? "" : " did not register");
		} catch (ReflectiveOperationException | ClassCastException e) {
			BeyondTheDragon.LOGGER.warn("Waystones is installed but its API didn't answer as expected; no waystone at {}", pos, e);
		}
	}

	private static Method method(Class<?> owner, String name, int parameters) throws NoSuchMethodException {
		for (Method method : owner.getMethods()) {
			if (method.getName().equals(name) && method.getParameterCount() == parameters) {
				return method;
			}
		}
		throw new NoSuchMethodException(owner.getName() + "." + name);
	}
}
