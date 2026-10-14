package com.gabbymaybelle.beyondthedragon.mixin;

import com.gabbymaybelle.beyondthedragon.ladder.DragonLadder;
import com.gabbymaybelle.beyondthedragon.structures.Observatories;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A dragon egg set down by hand: on a lit beacon it unlocks the Dragon Ladder. */
@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
	@Inject(method = "place", at = @At("RETURN"))
	private void beyondTheDragon$eggPlaced(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
		if (cir.getReturnValue() != InteractionResult.SUCCESS) {
			return;
		}
		if (!(context.getLevel() instanceof ServerLevel level)) {
			return;
		}
		if (level.getBlockState(context.getClickedPos()).is(Blocks.DRAGON_EGG)) {
			DragonLadder.onEggPlaced(level, context.getClickedPos(), context.getPlayer());
		}
		Observatories.onBlockPlaced(level, context.getClickedPos(), context.getPlayer());
	}
}
