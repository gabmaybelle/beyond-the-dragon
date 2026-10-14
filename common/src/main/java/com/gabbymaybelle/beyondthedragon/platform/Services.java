package com.gabbymaybelle.beyondthedragon.platform;

import java.util.ServiceLoader;

/** Finds the loader's {@link Platform} once, at class load. */
public final class Services {
	public static final Platform PLATFORM = load(Platform.class);

	private Services() {
	}

	private static <T> T load(Class<T> type) {
		return ServiceLoader.load(type).findFirst()
				.orElseThrow(() -> new IllegalStateException("No " + type.getSimpleName() + " for this loader; the mod jar is incomplete"));
	}
}
