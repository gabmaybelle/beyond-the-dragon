package com.gabbymaybelle.beyondthedragon.structures;

import com.gabbymaybelle.beyondthedragon.aphelite.Aphelite;
import com.gabbymaybelle.beyondthedragon.platform.Services;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;

/** The Outer Reaches structures' code side: the structure type and the ring loot condition. */
public final class Structures {
	private Structures() {
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public static void register() {
		Services.PLATFORM.register((ResourceKey) Registries.STRUCTURE_TYPE, Aphelite.id("end_jigsaw"), () -> EndJigsawStructure.TYPE);
		Services.PLATFORM.register((ResourceKey) Registries.LOOT_CONDITION_TYPE, Aphelite.id("in_ring"), () -> InRingCondition.CODEC);
	}
}
