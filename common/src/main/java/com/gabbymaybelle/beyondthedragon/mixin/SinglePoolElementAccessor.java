package com.gabbymaybelle.beyondthedragon.mixin;

import com.mojang.datafixers.util.Either;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Which template a pool element places: 1.21.1 keeps it private (26.x has a getter). */
@Mixin(SinglePoolElement.class)
public interface SinglePoolElementAccessor {
	@Accessor("template")
	Either<ResourceLocation, StructureTemplate> beyondTheDragon$template();
}
