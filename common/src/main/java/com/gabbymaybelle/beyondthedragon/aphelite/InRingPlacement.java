package com.gabbymaybelle.beyondthedragon.aphelite;

import com.gabbymaybelle.beyondthedragon.platform.Services;
import com.gabbymaybelle.beyondthedragon.rings.Rings;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

/**
 * A placement filter for worldgen: {@code {"type": "beyond_the_dragon:in_ring", "ring": "outer"}}
 * lets a feature through only in that ring of the End or further out. Vanilla's placement
 * modifiers can't read distance from the centre, which is the whole point of the Far End.
 */
public final class InRingPlacement extends PlacementFilter {
	public static final MapCodec<InRingPlacement> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.STRING.fieldOf("ring").forGetter(filter -> filter.ring.name().toLowerCase(Locale.ROOT))
	).apply(instance, name -> new InRingPlacement(Rings.valueOf(name.toUpperCase(Locale.ROOT)))));

	// 1.21.1 registers a type that holds the codec (26.3 registers the codec itself).
	public static final PlacementModifierType<InRingPlacement> TYPE = () -> CODEC;

	private final Rings ring;

	public InRingPlacement(Rings ring) {
		this.ring = ring;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public static void register() {
		Services.PLATFORM.register((ResourceKey) Registries.PLACEMENT_MODIFIER_TYPE, Aphelite.id("in_ring"), () -> TYPE);
	}

	@Override
	protected boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos pos) {
		return Rings.at(pos.getX(), pos.getZ()).ordinal() >= ring.ordinal();
	}

	@Override
	public PlacementModifierType<?> type() {
		return TYPE;
	}
}
