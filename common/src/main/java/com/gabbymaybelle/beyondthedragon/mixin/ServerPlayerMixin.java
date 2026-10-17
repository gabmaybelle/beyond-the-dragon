package com.gabbymaybelle.beyondthedragon.mixin;

import com.gabbymaybelle.beyondthedragon.structures.Observatories;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Respawning at an anchor set inside a restored observatory works, as using it did ({@link RespawnAnchorBlockMixin}). */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
	@Redirect(method = "findRespawnAndUseSpawnBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/RespawnAnchorBlock;canSetSpawn(Lnet/minecraft/world/level/Level;)Z"))
	private static boolean beyondTheDragon$observatoryAnchor(Level level, ServerLevel sameLevel, BlockPos pos, float angle, boolean forced, boolean keepInventory) {
		return RespawnAnchorBlock.canSetSpawn(level) || Observatories.anchorWorks(sameLevel, pos);
	}
}
