package com.gabbymaybelle.beyondthedragon.structures;

import com.gabbymaybelle.beyondthedragon.rings.Rings;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Locale;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.Vec3;

/**
 * A loot condition: {@code {"type": "beyond_the_dragon:in_ring", "ring": "outer"}} passes when
 * the loot is generated in that ring of the End or further out, so a chest's far-ring pools can
 * hold better things. Outside the End, or with no origin, it never passes.
 */
public record InRingCondition(Rings ring) implements LootItemCondition {
	public static final MapCodec<InRingCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.STRING.fieldOf("ring").forGetter(condition -> condition.ring.name().toLowerCase(Locale.ROOT))
	).apply(instance, name -> new InRingCondition(Rings.valueOf(name.toUpperCase(Locale.ROOT)))));

	@Override
	public MapCodec<? extends LootItemCondition> codec() {
		return CODEC;
	}

	@Override
	public boolean test(LootContext context) {
		if (context.getLevel().dimension() != Level.END) {
			return false;
		}
		Vec3 origin = context.getOptional(LootContextParams.ORIGIN);
		return origin != null && Rings.at(origin.x, origin.z).ordinal() >= ring.ordinal();
	}
}
