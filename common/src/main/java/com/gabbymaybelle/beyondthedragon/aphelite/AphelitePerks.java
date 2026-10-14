package com.gabbymaybelle.beyondthedragon.aphelite;

import com.gabbymaybelle.beyondthedragon.Config;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.level.Level;

/**
 * What aphelite does beyond its numbers. Levitation from shulker bullets (a shulker's or the
 * tier 1 dragon's) is shorter for each aphelite armour piece worn; with helmet, leggings and
 * boots on, falls and elytra crashes in the End do no damage. The chest slot is left out of the
 * landing perk on purpose: that's where the elytra goes.
 */
public final class AphelitePerks {
	/** Each piece takes this much off a shulker bullet's levitation. */
	public static final float LEVITATION_CUT_PER_PIECE = 0.15F;

	private AphelitePerks() {
	}

	/** How many aphelite armour pieces an entity wears, 0 to 4. */
	public static int piecesWorn(LivingEntity entity) {
		int pieces = 0;
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			if (isAphelite(entity, slot)) {
				pieces++;
			}
		}
		return pieces;
	}

	/** Helmet, leggings and boots: the set that still fits round an elytra. */
	public static boolean wearsLandingSet(LivingEntity entity) {
		return isAphelite(entity, EquipmentSlot.HEAD) && isAphelite(entity, EquipmentSlot.LEGS) && isAphelite(entity, EquipmentSlot.FEET);
	}

	private static boolean isAphelite(LivingEntity entity, EquipmentSlot slot) {
		return switch (slot) {
			case HEAD -> entity.getItemBySlot(slot).getItem() == Aphelite.HELMET.get();
			case CHEST -> entity.getItemBySlot(slot).getItem() == Aphelite.CHESTPLATE.get();
			case LEGS -> entity.getItemBySlot(slot).getItem() == Aphelite.LEGGINGS.get();
			case FEET -> entity.getItemBySlot(slot).getItem() == Aphelite.BOOTS.get();
			default -> false;
		};
	}

	/**
	 * The effect an entity is about to get, shortened when it's a shulker bullet's levitation and
	 * the entity wears aphelite. Anything else comes back unchanged.
	 */
	public static MobEffectInstance adjustIncoming(LivingEntity target, MobEffectInstance effect, Entity source) {
		if (!Config.aphelitePerks || !effect.getEffect().equals(MobEffects.LEVITATION) || !fromShulkerBullet(source)) {
			return effect;
		}
		int pieces = piecesWorn(target);
		if (pieces == 0) {
			return effect;
		}
		int duration = Math.max(1, Math.round(effect.getDuration() * (1.0F - LEVITATION_CUT_PER_PIECE * pieces)));
		return new MobEffectInstance(effect.getEffect(), duration, effect.getAmplifier(), effect.isAmbient(), effect.isVisible(), effect.showIcon());
	}

	private static boolean fromShulkerBullet(Entity source) {
		return source instanceof ShulkerBullet || source instanceof Shulker || source instanceof EnderDragon;
	}

	/** True when this damage is a fall or an elytra crash in the End and the landing set is on. */
	public static boolean cancelsLanding(LivingEntity entity, DamageSource source) {
		if (!Config.aphelitePerks || entity.level().dimension() != Level.END) {
			return false;
		}
		if (!source.is(DamageTypes.FALL) && !source.is(DamageTypes.FLY_INTO_WALL)) {
			return false;
		}
		return wearsLandingSet(entity);
	}
}
