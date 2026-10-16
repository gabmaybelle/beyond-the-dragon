package com.gabbymaybelle.beyondthedragon.jelly;

import com.gabbymaybelle.beyondthedragon.rings.Rings;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Saddleable;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The Void Jelly on 1.21.1. The game has no happy ghast yet, so this is the jelly built from the
 * parts 1.21.1 has, to play the same as on 26.x (Gabby, 2026-10-16: a saddle instead of the
 * harness, up to four riders):
 * <ul>
 * <li>a flying animal that grows up from a baby, faster when fed chorus fruit;</li>
 * <li>only a jelly that hatched from an egg ({@link #isHatched()}) takes a saddle and riders;
 * wild ones can't be ridden;</li>
 * <li>the rider in front steers while holding a chorus flower on a stick, flying where they
 * look, faster in the End ({@link #END_SPEED}); without the stick it hovers;</li>
 * <li>wild ones spawn among the End's islands from the Middle Reaches out
 * ({@link #canSpawnNaturallyAt}), wander between heights {@link #END_MIN_Y} and
 * {@link #END_MAX_Y}, and drift off (despawn) when no player is near; hatched ones stay;</li>
 * <li>it takes no fall damage, and drops nothing but its saddle.</li>
 * </ul>
 */
public class VoidJelly extends Animal implements FlyingAnimal, Saddleable {
	/** How much faster a ridden jelly flies in the End than anywhere else. */
	public static final float END_SPEED = 1.6F;
	/**
	 * A ridden jelly's push per tick outside the End, tuned so the game test's ride covers what a
	 * happy ghast's does on 26.x (about 13.6 blocks in four seconds; 0.0153 gave 10.7).
	 */
	static final float RIDDEN_SPEED = 0.0195F;
	public static final int MAX_RIDERS = 4;

	/** Of the spawn attempts that find a good spot, how many make a jelly (2 to 4 near a player). */
	public static final float SPAWN_CHANCE = 0.07F;
	/** In the End a wandering jelly keeps between these heights, up among the islands. */
	public static final int END_MIN_Y = 40;
	public static final int END_MAX_Y = 110;

	private static final EntityDataAccessor<Boolean> HATCHED = SynchedEntityData.defineId(VoidJelly.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> SADDLED = SynchedEntityData.defineId(VoidJelly.class, EntityDataSerializers.BOOLEAN);

	public VoidJelly(EntityType<? extends VoidJelly> type, Level level) {
		super(type, level);
		this.moveControl = new FlyingMoveControl(this, 20, true);
		this.setNoGravity(true);
	}

	/** A happy ghast's: 20 health, a slow drift when wandering. */
	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0).add(Attributes.FLYING_SPEED, 0.05)
				.add(Attributes.MOVEMENT_SPEED, 0.05).add(Attributes.FOLLOW_RANGE, 16.0);
	}

	@Override
	protected PathNavigation createNavigation(Level level) {
		FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
		navigation.setCanOpenDoors(false);
		navigation.setCanFloat(true);
		return navigation;
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(3, new FloatGoal(this));
		this.goalSelector.addGoal(5, new FloatAmongIslandsGoal(this));
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(HATCHED, false);
		builder.define(SADDLED, false);
	}

	/** Whether this jelly hatched from an egg, so it can be saddled and ridden. */
	public boolean isHatched() {
		return entityData.get(HATCHED);
	}

	public void setHatched(boolean hatched) {
		entityData.set(HATCHED, hatched);
	}

	@Override
	public boolean isFlying() {
		return true;
	}

	@Override
	public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	protected void checkFallDamage(double y, boolean onGround, net.minecraft.world.level.block.state.BlockState state, BlockPos pos) {
	}

	// --- saddle and riders ----------------------------------------------------------------------

	@Override
	public boolean isSaddleable() {
		return isAlive() && isHatched() && !isBaby();
	}

	@Override
	public void equipSaddle(ItemStack stack, @Nullable SoundSource source) {
		entityData.set(SADDLED, true);
		if (source != null) {
			level().playSound(null, this, SoundEvents.HORSE_SADDLE, source, 0.5F, 1.0F);
		}
	}

	@Override
	public boolean isSaddled() {
		return entityData.get(SADDLED);
	}

	@Override
	protected void dropEquipment() {
		super.dropEquipment();
		if (isSaddled()) {
			spawnAtLocation(Items.SADDLE);
		}
	}

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (isFood(stack)) {
			return super.mobInteract(player, hand);
		}
		if (isSaddled() && !isBaby() && !player.isSecondaryUseActive() && canAddPassenger(player)) {
			if (!level().isClientSide()) {
				player.startRiding(this);
			}
			return InteractionResult.sidedSuccess(level().isClientSide());
		}
		// Anything else (a saddle on a hatched jelly, a name tag) is the item's own business.
		return super.mobInteract(player, hand);
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return isSaddled() && getPassengers().size() < MAX_RIDERS;
	}

	/** The rider in front steers, but only while holding a chorus flower on a stick. */
	@Override
	public @Nullable LivingEntity getControllingPassenger() {
		if (isSaddled() && getFirstPassenger() instanceof Player player && holdsStick(player)) {
			return player;
		}
		return null;
	}

	public static boolean holdsStick(Player player) {
		return player.isHolding(Jellies.CHORUS_STICK.get());
	}

	@Override
	protected void tickRidden(Player player, Vec3 input) {
		super.tickRidden(player, input);
		setRot(player.getYRot(), 0.0F);
		this.yRotO = this.yBodyRot = this.yHeadRot = getYRot();
	}

	/** Forward flies where the rider looks (up and down too); sideways is a gentle drift. */
	@Override
	protected Vec3 getRiddenInput(Player player, Vec3 input) {
		float forward = player.zza;
		float pitch = player.getXRot() * Mth.DEG_TO_RAD;
		return new Vec3(player.xxa * 0.5F, -Mth.sin(pitch) * forward, Mth.cos(pitch) * forward);
	}

	@Override
	protected float getRiddenSpeed(Player player) {
		return level().dimension() == Level.END ? RIDDEN_SPEED * END_SPEED : RIDDEN_SPEED;
	}

	/** Flying for wandering and riding alike: push along the input, then the air slows it. */
	@Override
	public void travel(Vec3 input) {
		if (isControlledByLocalInstance()) {
			moveRelative(getSpeed(), input);
			move(MoverType.SELF, getDeltaMovement());
			setDeltaMovement(getDeltaMovement().scale(0.91));
		}
		calculateEntityAnimation(false);
	}

	@Override
	protected void customServerAiStep() {
		super.customServerAiStep();
		if (isVehicle() && getControllingPassenger() == null) {
			// Carrying someone who isn't steering: hover rather than wander off with them.
			getNavigation().stop();
			setDeltaMovement(getDeltaMovement().scale(0.5));
		}
	}

	// --- growing up and spawning -----------------------------------------------------------------

	@Override
	public boolean isFood(ItemStack stack) {
		return stack.is(Items.CHORUS_FRUIT);
	}

	@Override
	public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
		return Jellies.VOID_JELLY.get().create(level);
	}

	/**
	 * Where a wild jelly may appear: only in the End, from the Middle Reaches outwards, measured
	 * from the End's centre the way the ring titles are, so it doesn't matter which biome (or which
	 * End mod's biome) is there.
	 */
	public static boolean canSpawnNaturallyAt(Level level, BlockPos pos) {
		return level.dimension() == Level.END && Rings.at(pos.getX(), pos.getZ()).compareTo(Rings.MIDDLE) >= 0;
	}

	@Override
	public boolean checkSpawnRules(LevelAccessor level, MobSpawnType reason) {
		if (reason == MobSpawnType.NATURAL || reason == MobSpawnType.CHUNK_GENERATION) {
			return canSpawnNaturallyAt(level(), blockPosition()) && random.nextFloat() < SPAWN_CHANCE && super.checkSpawnRules(level, reason);
		}
		return super.checkSpawnRules(level, reason);
	}

	/**
	 * Any spot suits a flier. An animal's default prefers bright ground, and the End is dark
	 * enough that every spawn there failed it (0 jellies from 200 attempts on 1.21.1).
	 */
	@Override
	public float getWalkTargetValue(BlockPos pos, net.minecraft.world.level.LevelReader level) {
		return 0.0F;
	}

	/** A wild jelly drifts off once no player is near, like a bat; a hatched one stays. */
	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return !isHatched();
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putBoolean("hatched", isHatched());
		tag.putBoolean("saddled", isSaddled());
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		setHatched(tag.getBoolean("hatched"));
		entityData.set(SADDLED, tag.getBoolean("saddled"));
	}

	/**
	 * Wandering: a random spot within 16 blocks, kept between {@link #END_MIN_Y} and
	 * {@link #END_MAX_Y} in the End (on 26.x the happy ghast's wandering sank wild jellies under
	 * the islands). Not while someone rides it.
	 */
	static final class FloatAmongIslandsGoal extends Goal {
		private final VoidJelly jelly;

		FloatAmongIslandsGoal(VoidJelly jelly) {
			this.jelly = jelly;
			setFlags(EnumSet.of(Goal.Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			if (jelly.isVehicle()) {
				return false;
			}
			var move = jelly.getMoveControl();
			if (!move.hasWanted()) {
				return true;
			}
			double dx = move.getWantedX() - jelly.getX();
			double dy = move.getWantedY() - jelly.getY();
			double dz = move.getWantedZ() - jelly.getZ();
			double distSqr = dx * dx + dy * dy + dz * dz;
			return distSqr < 1.0 || distSqr > 3600.0;
		}

		@Override
		public boolean canContinueToUse() {
			return false;
		}

		@Override
		public void start() {
			var random = jelly.getRandom();
			double x = jelly.getX() + (random.nextFloat() * 2.0F - 1.0F) * 16.0F;
			double y = jelly.getY() + (random.nextFloat() * 2.0F - 1.0F) * 16.0F;
			double z = jelly.getZ() + (random.nextFloat() * 2.0F - 1.0F) * 16.0F;
			if (jelly.level().dimension() == Level.END) {
				y = Mth.clamp(y, END_MIN_Y, END_MAX_Y);
			}
			jelly.getMoveControl().setWantedPosition(x, y, z, 1.0);
		}
	}
}
