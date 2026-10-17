package com.gabbymaybelle.beyondthedragon.mixin;

import com.gabbymaybelle.beyondthedragon.structures.Observatories;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A respawn anchor works inside a restored observatory: setting your spawn there, and coming back
 * there after a death, instead of the explosion it gives anywhere else outside the Nether.
 */
@Mixin(RespawnAnchorBlock.class)
public abstract class RespawnAnchorBlockMixin {
	@Inject(method = "canSetSpawn", at = @At("HEAD"), cancellable = true)
	private static void beyondTheDragon$observatoryAnchor(ServerLevel level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		if (Observatories.anchorWorks(level, pos)) {
			cir.setReturnValue(true);
		}
	}
}
