package com.gabbymaybelle.beyondthedragon.mixin;

import com.gabbymaybelle.beyondthedragon.ladder.DragonLadder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.dimension.end.DragonRespawnStage;
import net.minecraft.world.level.dimension.end.EnderDragonFight;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The two moments the Dragon Ladder cares about: the respawn sequence reaching its end (vanilla
 * creates the new dragon right there) and the fight's dragon dying.
 */
@Mixin(EnderDragonFight.class)
public abstract class EnderDragonFightMixin {
	@Shadow @Final private ServerLevel level;
	@Shadow private BlockPos origin;

	@Shadow public abstract java.util.UUID dragonUUID();

	@Inject(method = "setRespawnStage", at = @At("HEAD"))
	private void beyondTheDragon$respawnEnding(DragonRespawnStage stage, CallbackInfo ci) {
		if (stage == DragonRespawnStage.END) {
			DragonLadder.onRespawnCompleted(level);
		}
	}

	@Inject(method = "setDragonKilled", at = @At("TAIL"))
	private void beyondTheDragon$dragonKilled(EnderDragon dragon, CallbackInfo ci) {
		if (dragon.getUUID().equals(dragonUUID())) {
			DragonLadder.onDragonKilled(level, dragon, origin);
		}
	}
}
