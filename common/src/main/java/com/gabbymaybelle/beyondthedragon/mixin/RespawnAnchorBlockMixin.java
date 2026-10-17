package com.gabbymaybelle.beyondthedragon.mixin;

import com.gabbymaybelle.beyondthedragon.structures.Observatories;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * A respawn anchor works inside a restored observatory: using it sets your spawn there instead of
 * the explosion it gives anywhere else outside the Nether. On 1.21.1 the game's check takes no
 * position, so the call is redirected where the position is known (the respawn side is in
 * {@link ServerPlayerMixin}).
 */
@Mixin(RespawnAnchorBlock.class)
public abstract class RespawnAnchorBlockMixin {
	@Redirect(method = "useWithoutItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/RespawnAnchorBlock;canSetSpawn(Lnet/minecraft/world/level/Level;)Z"))
	private boolean beyondTheDragon$observatoryAnchor(Level level, BlockState state, Level sameLevel, BlockPos pos, Player player, BlockHitResult hit) {
		return RespawnAnchorBlock.canSetSpawn(level) || (level instanceof ServerLevel server && Observatories.anchorWorks(server, pos));
	}
}
