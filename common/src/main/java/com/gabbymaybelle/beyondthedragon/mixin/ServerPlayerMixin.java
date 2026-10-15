package com.gabbymaybelle.beyondthedragon.mixin;

import com.gabbymaybelle.beyondthedragon.structures.Observatories;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.DimensionTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A death in the End respawns at the nearest restored observatory; every other death is vanilla's business. */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
	@Inject(method = "findRespawnPositionAndUseSpawnBlock", at = @At("HEAD"), cancellable = true)
	private void beyondTheDragon$observatoryRespawn(boolean useSpawnBlock, DimensionTransition.PostDimensionTransition post, CallbackInfoReturnable<DimensionTransition> cir) {
		Observatories.respawn((ServerPlayer) (Object) this, post).ifPresent(cir::setReturnValue);
	}
}
