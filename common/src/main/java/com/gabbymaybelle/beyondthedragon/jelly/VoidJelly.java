package com.gabbymaybelle.beyondthedragon.jelly;

import com.gabbymaybelle.beyondthedragon.rings.Rings;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Void Jelly: a gentle floating creature of the outer End, built on vanilla's happy ghast
 * (its body, riding, harness and baby stage). What is its own:
 * <ul>
 * <li>Only a jelly that hatched from an egg ({@link #isHatched()}) takes a harness and riders;
 * wild ones can't be ridden.</li>
 * <li>It is steered only by a rider holding a chorus flower on a stick, the way a strider follows
 * warped fungus on a stick; without one, it hovers where it is.</li>
 * <li>It flies faster in the End than a happy ghast does ({@link #END_SPEED}); elsewhere it is
 * a happy ghast's speed.</li>
 * <li>Nothing tempts it (no luring wild ones with a harness), and it drops nothing.</li>
 * <li>Wild ones spawn among the End's islands from the Middle Reaches out ({@link #canSpawnNaturallyAt}),
 * and drift off (despawn) when no player is near; hatched ones stay.</li>
 * </ul>
 */
public class VoidJelly extends HappyGhast {
	/** How much faster a ridden jelly flies in the End than anywhere else (and than a happy ghast). */
	public static final float END_SPEED = 1.6F;

	/** Synced: the client needs it too, since whether the harness counts as worn depends on it. */
	private static final EntityDataAccessor<Boolean> HATCHED = SynchedEntityData.defineId(VoidJelly.class, EntityDataSerializers.BOOLEAN);

	public VoidJelly(EntityType<? extends VoidJelly> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return HappyGhast.createAttributes();
	}

	/**
	 * Of the spawn attempts that find a good spot, how many make a jelly: a few drifting between
	 * the islands rather than a flock. Gabby asked for about 2 to 4 near a player; measured at
	 * 2,600 blocks out over ten minutes (BTD_JELLY_DENSITY), 1 in 4 gave 3 to 10, 1 in 10 crept
	 * up to 6, and this gives 2 to 4.
	 */
	public static final float SPAWN_CHANCE = 0.07F;

	/**
	 * Where a wild jelly may appear: only in the End, from the Middle Reaches outwards, measured
	 * from the End's centre the way the ring titles are, so it doesn't matter which biome (or which
	 * End mod's biome) is there. The End's biomes list the jelly as a creature
	 * ({@link Jellies#register}); this keeps it off the inner islands.
	 */
	public static boolean canSpawnNaturallyAt(Level level, BlockPos pos) {
		return level.dimension() == Level.END && Rings.at(pos.getX(), pos.getZ()).compareTo(Rings.MIDDLE) >= 0;
	}

	@Override
	public boolean checkSpawnRules(LevelAccessor level, EntitySpawnReason reason) {
		if (reason == EntitySpawnReason.NATURAL || reason == EntitySpawnReason.CHUNK_GENERATION) {
			return canSpawnNaturallyAt(level(), blockPosition()) && random.nextFloat() < SPAWN_CHANCE && super.checkSpawnRules(level, reason);
		}
		return super.checkSpawnRules(level, reason);
	}

	/**
	 * A wild jelly drifts off (despawns) once no player is near, like a bat, so jellies don't pile
	 * up across the End behind a traveller. A hatched one stays, like any animal.
	 */
	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return !isHatched();
	}

	/** Whether this jelly hatched from an egg, so it can be harnessed and ridden. */
	public boolean isHatched() {
		return entityData.get(HATCHED);
	}

	public void setHatched(boolean hatched) {
		entityData.set(HATCHED, hatched);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(HATCHED, false);
	}

	/** The happy ghast's goals without its temptation: a jelly isn't lured by anything. */
	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(3, new FloatGoal(this));
		this.goalSelector.addGoal(5, new FloatAmongIslandsGoal(this));
	}

	/** In the End a wandering jelly keeps between these heights, up among the islands. */
	public static final int END_MIN_Y = 40;
	public static final int END_MAX_Y = 110;

	/**
	 * The happy ghast's wandering, kept up among the End's islands. Vanilla's goal pulls its target
	 * down whenever there is land below it, which kept a happy ghast near the ground but sank wild
	 * jellies under the islands (heights 2 to 15 in a play test, 2026-10-16), where they were only
	 * seen from below. In the End the target's height is kept between {@link #END_MIN_Y} and
	 * {@link #END_MAX_Y}; elsewhere it wanders as a happy ghast does.
	 */
	static final class FloatAmongIslandsGoal extends Ghast.RandomFloatAroundGoal {
		private final VoidJelly jelly;

		FloatAmongIslandsGoal(VoidJelly jelly) {
			super(jelly, 16);
			this.jelly = jelly;
		}

		@Override
		public void start() {
			Vec3 target = Ghast.RandomFloatAroundGoal.getSuitableFlyToPosition(jelly, 16);
			if (jelly.level().dimension() == Level.END) {
				target = new Vec3(target.x(), Math.clamp(target.y(), END_MIN_Y, END_MAX_Y), target.z());
			}
			jelly.getMoveControl().setWantedPosition(target.x(), target.y(), target.z(), 1.0);
		}
	}

	@Override
	public boolean canUseSlot(EquipmentSlot slot) {
		return slot == EquipmentSlot.BODY ? isHatched() && super.canUseSlot(slot) : super.canUseSlot(slot);
	}

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (!isHatched() && !isBaby()) {
			// A wild jelly is never ridden, even wearing a harness it got some other way. Items
			// still work on it (a name tag); a harness doesn't go on (canUseSlot).
			ItemStack stack = player.getItemInHand(hand);
			InteractionResult result = stack.isEmpty() ? InteractionResult.PASS : stack.interactLivingEntity(player, this, hand);
			return result.consumesAction() ? result : InteractionResult.PASS;
		}
		return super.mobInteract(player, hand);
	}

	/**
	 * The rider steers only while holding a chorus flower on a stick. Without one there is no
	 * controller, and {@link #customServerAiStep} keeps the jelly hovering in place.
	 */
	@Override
	public @Nullable LivingEntity getControllingPassenger() {
		LivingEntity controller = super.getControllingPassenger();
		if (controller instanceof Player player && !holdsStick(player)) {
			return null;
		}
		return controller;
	}

	public static boolean holdsStick(Player player) {
		return player.isHolding(Jellies.CHORUS_STICK.get());
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (isVehicle() && getControllingPassenger() == null) {
			// Carrying someone who isn't steering: hover rather than wander off with them.
			getMoveControl().setWait();
		}
	}

	@Override
	public void travel(Vec3 input) {
		if (level().dimension() == Level.END && getControllingPassenger() instanceof Player) {
			float speed = (float) getAttributeValue(Attributes.FLYING_SPEED) * 5.0F / 3.0F * END_SPEED;
			travelFlying(input, speed, speed, speed);
		} else {
			super.travel(input);
		}
	}

	@Override
	public boolean isFood(ItemStack stack) {
		return stack.is(Items.CHORUS_FRUIT);
	}

	@Override
	public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
		return Jellies.VOID_JELLY.get().create(level, EntitySpawnReason.BREEDING);
	}

	@Override
	public void addAdditionalSaveData(ValueOutput tag) {
		super.addAdditionalSaveData(tag);
		tag.putBoolean("hatched", isHatched());
	}

	@Override
	public void readAdditionalSaveData(ValueInput tag) {
		super.readAdditionalSaveData(tag);
		setHatched(tag.getBooleanOr("hatched", false));
	}
}
