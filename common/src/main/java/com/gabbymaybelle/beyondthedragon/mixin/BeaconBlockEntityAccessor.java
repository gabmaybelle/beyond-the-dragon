package com.gabbymaybelle.beyondthedragon.mixin;

import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The beacon's pyramid level: 0 while it's unlit. */
@Mixin(BeaconBlockEntity.class)
public interface BeaconBlockEntityAccessor {
	@Accessor("levels")
	int beyondTheDragon$levels();
}
