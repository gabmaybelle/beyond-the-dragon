package com.gabbymaybelle.beyondthedragon.mixin;

import com.gabbymaybelle.beyondthedragon.ladder.DragonLadder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The tier attacks run after the dragon's own server-side step. */
@Mixin(EnderDragon.class)
public abstract class EnderDragonMixin {
	@Inject(method = "aiStep", at = @At("TAIL"))
	private void beyondTheDragon$tierAttacks(CallbackInfo ci) {
		EnderDragon dragon = (EnderDragon) (Object) this;
		if (dragon.level() instanceof ServerLevel level) {
			DragonLadder.dragonTick(dragon, level);
		}
	}
}
