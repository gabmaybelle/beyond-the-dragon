package com.gabbymaybelle.beyondthedragon.structures;

import com.gabbymaybelle.beyondthedragon.aphelite.Aphelite;
import com.gabbymaybelle.beyondthedragon.platform.Services;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;

/** The Outer Reaches structures' code side: the structure type and the ring loot condition. */
public final class Structures {
	/** 1.21.1 registers a type that holds the condition's codec. */
	public static final LootItemConditionType IN_RING = new LootItemConditionType(InRingCondition.CODEC);

	private Structures() {
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public static void register() {
		Services.PLATFORM.register((ResourceKey) Registries.STRUCTURE_TYPE, Aphelite.id("end_jigsaw"), () -> EndJigsawStructure.TYPE);
		Services.PLATFORM.register((ResourceKey) Registries.LOOT_CONDITION_TYPE, Aphelite.id("in_ring"), () -> IN_RING);
	}
}
