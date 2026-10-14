package com.gabbymaybelle.beyondthedragon.ladder;

import com.gabbymaybelle.beyondthedragon.BeyondTheDragon;
import com.gabbymaybelle.beyondthedragon.Config;
import com.gabbymaybelle.beyondthedragon.mixin.BeaconBlockEntityAccessor;
import com.gabbymaybelle.beyondthedragon.platform.Services;
import com.gabbymaybelle.beyondthedragon.titles.Titles;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.PowerParticleOption;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.dimension.end.EnderDragonFight;
import net.minecraft.world.level.levelgen.feature.EndPodiumFeature;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Dragon Ladder. Set the dragon egg on a lit beacon and the ladder is unlocked for the
 * world; every respawn after that brings the dragon back one tier stronger, up to tier 3. Each
 * tier adds an attack on top of the vanilla fight (shulker bullet volleys, breath that pulls
 * you in, a dive-bomb charge), and the first kill at each tier drops that tier's loot table at
 * the exit portal. Past tier 3 the dragon stays at tier 3 and repeat kills give nothing extra.
 *
 * <p>The hooks are three small mixins: the egg being placed ({@link #onEggPlaced}), the respawn
 * sequence ending ({@link #onRespawnCompleted}) and the dragon's death ({@link #onDragonKilled}).
 * The attacks run from the dragon's own tick ({@link #dragonTick}), so nothing runs while no
 * dragon is alive.
 */
public final class DragonLadder {
	public static final int MAX_TIER = 3;

	private static final int VOLLEY_INTERVAL = 70;
	private static final int VOLLEY_MAX_TARGETS = 3;
	private static final int BREATH_INTERVAL = 160;
	private static final int BREATH_DURATION = 140;
	private static final float BREATH_RADIUS = 4.0F;
	private static final double BREATH_PULL = 0.07;
	private static final int DIVE_INTERVAL = 220;
	private static final double ATTACK_RANGE = 96;

	/** Per-dragon cooldowns and the breath clouds that pull, kept only while that dragon ticks. */
	private static final class Abilities {
		int nextVolley;
		int nextBreath;
		int nextDive;
		final List<AreaEffectCloud> pullingClouds = new ArrayList<>();
		long lastTick;
	}

	private static final Map<UUID, Abilities> ABILITIES = new HashMap<>();

	private DragonLadder() {
	}

	/** The ladder's saved state, or null before the End exists. */
	public static LadderData data(ServerLevel level) {
		ServerLevel end = level.getServer().getLevel(Level.END);
		return end == null ? null : LadderData.get(end);
	}

	/** True when a mod that reworks the dragon fight is installed. */
	public static boolean otherDragonModLoaded() {
		for (String id : Config.otherDragonModIds.split(",")) {
			if (!id.isBlank() && Services.PLATFORM.isModLoaded(id.trim())) {
				return true;
			}
		}
		return false;
	}

	/** Whether the tier attacks run: the ladder is on, past tier 0, and no other mod owns the fight. */
	public static boolean attacksActive(ServerLevel level) {
		if (!Config.dragonLadder) {
			return false;
		}
		LadderData data = data(level);
		if (data == null || data.tier() <= 0) {
			return false;
		}
		return Config.dragonAbilitiesWithOtherDragonMods || !otherDragonModLoaded();
	}

	/** A dragon egg was placed by hand: on a lit beacon, that unlocks the ladder. */
	public static boolean onEggPlaced(ServerLevel level, BlockPos egg, Player player) {
		if (!Config.dragonLadder) {
			return false;
		}
		if (!(level.getBlockEntity(egg.below()) instanceof BeaconBlockEntity beacon)) {
			return false;
		}
		if (((BeaconBlockEntityAccessor) beacon).beyondTheDragon$levels() <= 0) {
			if (player != null) {
				player.sendSystemMessage(Component.translatable("message.beyond_the_dragon.ladder.beacon_unlit"));
			}
			return false;
		}
		LadderData data = data(level);
		if (data == null) {
			return false;
		}
		if (data.unlocked()) {
			if (player != null) {
				player.sendSystemMessage(Component.translatable("message.beyond_the_dragon.ladder.already_unlocked"));
			}
			return false;
		}
		data.unlock();
		level.playSound(null, egg, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.BLOCKS, 1.0F, 0.8F);
		if (player != null) {
			player.sendSystemMessage(Component.translatable("message.beyond_the_dragon.ladder.unlocked"));
		}
		BeyondTheDragon.LOGGER.info("The Dragon Ladder is unlocked: egg on a beacon at {}", egg);
		return true;
	}

	/** The respawn sequence has finished and a new dragon is about to appear. */
	public static void onRespawnCompleted(ServerLevel end) {
		if (!Config.dragonLadder) {
			return;
		}
		LadderData data = LadderData.get(end);
		if (!data.unlocked()) {
			return;
		}
		int tier = data.climb();
		BeyondTheDragon.LOGGER.info("The dragon returns at tier {}", tier);
		for (ServerPlayer player : end.players()) {
			Titles.send(player, Component.translatable("title.beyond_the_dragon.ladder.returns"),
					Component.translatable("title.beyond_the_dragon.ladder.tier", tier, MAX_TIER));
		}
	}

	/** The fight's dragon died: the first kill at each tier drops that tier's loot at the portal. */
	public static void onDragonKilled(ServerLevel end, EnderDragon dragon, BlockPos origin) {
		if (!Config.dragonLadder) {
			return;
		}
		ABILITIES.remove(dragon.getUUID());
		LadderData data = LadderData.get(end);
		int tier = data.tier();
		if (tier <= 0 || data.rewarded(tier)) {
			return;
		}
		data.markRewarded(tier);
		ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE,
				Identifier.fromNamespaceAndPath(BeyondTheDragon.MOD_ID, "dragon_ladder/tier_" + tier));
		LootTable table = end.getServer().reloadableRegistries().getLootTable(key);
		BlockPos podium = end.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, EndPodiumFeature.getLocation(origin));
		Vec3 drop = Vec3.atBottomCenterOf(podium.above());
		LootParams params = new LootParams.Builder(end)
				.withParameter(LootContextParams.ORIGIN, drop)
				.withParameter(LootContextParams.THIS_ENTITY, dragon)
				.create(LootContextParamSets.GIFT);
		int dropped = 0;
		for (ItemStack stack : table.getRandomItems(params)) {
			ItemEntity item = new ItemEntity(end, drop.x, drop.y, drop.z, stack);
			item.setDefaultPickUpDelay();
			end.addFreshEntity(item);
			dropped++;
		}
		BeyondTheDragon.LOGGER.info("Tier {} dragon beaten: {} reward stack(s) at {}", tier, dropped, podium);
		for (ServerPlayer player : end.players()) {
			player.sendSystemMessage(Component.translatable("message.beyond_the_dragon.ladder.rewarded", tier));
		}
	}

	/** Runs from the dragon's server tick: the tier attacks, on their own cooldowns. */
	public static void dragonTick(EnderDragon dragon, ServerLevel level) {
		if (dragon.isDeadOrDying() || !attacksActive(level)) {
			return;
		}
		int tier = LadderData.get(level).tier();
		Abilities abilities = ABILITIES.computeIfAbsent(dragon.getUUID(), id -> new Abilities());
		abilities.lastTick = level.getGameTime();
		forgetStaleDragons(level.getGameTime());
		List<ServerPlayer> targets = level.getPlayers(player -> EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(player)
				&& player.isAlive() && player.distanceToSqr(dragon) < ATTACK_RANGE * ATTACK_RANGE);
		pullTowardClouds(abilities, level);
		if (targets.isEmpty()) {
			return;
		}
		if (tier >= 1 && Config.dragonShulkerVolleys) {
			volley(dragon, level, targets, abilities);
		}
		if (tier >= 2 && Config.dragonPullingBreath) {
			breath(dragon, level, targets, abilities);
		}
		if (tier >= 3 && Config.dragonDiveBomb) {
			dive(dragon, level, targets, abilities);
		}
	}

	/** Tier 1: a shulker bullet at each nearby player, from the dragon's head. */
	private static void volley(EnderDragon dragon, ServerLevel level, List<ServerPlayer> targets, Abilities abilities) {
		if (abilities.nextVolley-- > 0) {
			return;
		}
		abilities.nextVolley = VOLLEY_INTERVAL;
		Vec3 head = dragon.head.getBoundingBox().getCenter();
		int fired = 0;
		for (ServerPlayer target : targets) {
			if (fired++ >= VOLLEY_MAX_TARGETS) {
				break;
			}
			ShulkerBullet bullet = new ShulkerBullet(level, dragon, target, Direction.Axis.Y);
			bullet.setPos(head.x, head.y, head.z);
			level.addFreshEntity(bullet);
		}
		level.playSound(null, dragon.blockPosition(), SoundEvents.SHULKER_SHOOT, SoundSource.HOSTILE, 2.0F, 0.6F);
	}

	/** Tier 2: a breath cloud under a player that drags everyone nearby into it while it lasts. */
	private static void breath(EnderDragon dragon, ServerLevel level, List<ServerPlayer> targets, Abilities abilities) {
		if (abilities.nextBreath-- > 0) {
			return;
		}
		abilities.nextBreath = BREATH_INTERVAL;
		ServerPlayer target = targets.get(level.getRandom().nextInt(targets.size()));
		BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, target.blockPosition());
		if (ground.getY() <= level.getMinY() + 1) {
			return;
		}
		AreaEffectCloud cloud = new AreaEffectCloud(level, ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5);
		cloud.setOwner(dragon);
		cloud.setRadius(BREATH_RADIUS);
		cloud.setDuration(BREATH_DURATION);
		cloud.setCustomParticle(PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1.0F));
		cloud.addEffect(new MobEffectInstance(MobEffects.INSTANT_DAMAGE));
		level.addFreshEntity(cloud);
		abilities.pullingClouds.add(cloud);
		level.playSound(null, ground, SoundEvents.ENDER_DRAGON_SHOOT, SoundSource.HOSTILE, 2.0F, 0.5F);
	}

	private static void pullTowardClouds(Abilities abilities, ServerLevel level) {
		abilities.pullingClouds.removeIf(cloud -> cloud.isRemoved());
		for (AreaEffectCloud cloud : abilities.pullingClouds) {
			double reach = cloud.getRadius() + 3;
			AABB box = cloud.getBoundingBox().inflate(reach, 2, reach);
			for (Player player : level.getEntitiesOfClass(Player.class, box, EntitySelector.NO_CREATIVE_OR_SPECTATOR)) {
				Vec3 toCloud = cloud.position().subtract(player.position());
				double distance = toCloud.horizontalDistance();
				if (distance < 0.5) {
					continue;
				}
				Vec3 pull = new Vec3(toCloud.x / distance * BREATH_PULL, 0, toCloud.z / distance * BREATH_PULL);
				player.addDeltaMovement(pull);
				// A player's motion only reaches their client when the server says so.
				player.needsSync = true;
			}
		}
	}

	/** Tier 3: now and then the dragon breaks off circling and charges the nearest player. */
	private static void dive(EnderDragon dragon, ServerLevel level, List<ServerPlayer> targets, Abilities abilities) {
		if (abilities.nextDive-- > 0) {
			return;
		}
		EnderDragonPhase<?> phase = dragon.getPhaseManager().getCurrentPhase().getPhase();
		if (phase != EnderDragonPhase.HOLDING_PATTERN && phase != EnderDragonPhase.STRAFE_PLAYER) {
			return;
		}
		abilities.nextDive = DIVE_INTERVAL;
		ServerPlayer nearest = targets.get(0);
		for (ServerPlayer target : targets) {
			if (target.distanceToSqr(dragon) < nearest.distanceToSqr(dragon)) {
				nearest = target;
			}
		}
		dragon.getPhaseManager().setPhase(EnderDragonPhase.CHARGING_PLAYER);
		dragon.getPhaseManager().getPhase(EnderDragonPhase.CHARGING_PLAYER).setTarget(nearest.position());
		level.playSound(null, dragon.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 3.0F, 1.2F);
	}

	private static void forgetStaleDragons(long now) {
		ABILITIES.values().removeIf(abilities -> now - abilities.lastTick > 200);
	}
}
