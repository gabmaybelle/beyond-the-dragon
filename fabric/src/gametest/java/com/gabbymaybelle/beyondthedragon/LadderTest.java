package com.gabbymaybelle.beyondthedragon;

import com.gabbymaybelle.beyondthedragon.BeyondTheDragon;
import com.gabbymaybelle.beyondthedragon.ladder.DragonLadder;
import com.gabbymaybelle.beyondthedragon.ladder.LadderData;
import java.util.List;
import java.util.function.Consumer;
import com.gabbymaybelle.beyondthedragon.harness.ClientGameTestContext;
import com.gabbymaybelle.beyondthedragon.harness.TestServerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import com.gabbymaybelle.beyondthedragon.mixin.BeaconBlockEntityAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.levelgen.feature.EndPodiumFeature;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Dragon Ladder in a real game: the egg on an unlit beacon does nothing, on a lit one it
 * unlocks the ladder; killing the vanilla dragon gives no reward at tier 0; respawning it with
 * four crystals brings it back at tier 1, which fires shulker bullets; killing that one drops
 * tier 1's loot at the portal, once.
 */
final class LadderTest {
	private final Consumer<String> ok;
	private final Consumer<String> failed;

	LadderTest(Consumer<String> ok, Consumer<String> failed) {
		this.ok = ok;
		this.failed = failed;
	}

	void run(ClientGameTestContext ctx, TestServerContext server) {
		unlock(ctx, server);
		if (DragonLadder.otherDragonModLoaded()) {
			BeyondTheDragon.LOGGER.info("[test] another mod reworks the dragon fight; the ladder's climb is theirs to break, so only the unlock is checked here");
			return;
		}
		climb(ctx, server);
	}

	private void unlock(ClientGameTestContext ctx, TestServerContext server) {
		// Back to the Overworld, hovering in creative until the ground below is loaded: a heightmap
		// read in an unloaded column answers with the bottom of the world.
		server.runOnServer(s -> {
			ServerPlayer player = player(s);
			player.setGameMode(GameType.CREATIVE);
			player.getAbilities().flying = true;
			player.onUpdateAbilities();
			player.teleportTo(s.overworld(), 0.5, 200, 0.5, java.util.Set.of(), 0, 0);
		});
		waitOnServer(ctx, server, s -> s.overworld().isLoaded(new BlockPos(3, 64, 3)), 600);
		ctx.waitTicks(20);
		// Next to a beacon with no pyramid under it.
		BlockPos beacon = server.computeOnServer(s -> {
			ServerLevel overworld = s.overworld();
			ServerPlayer player = player(s);
			BlockPos surface = overworld.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, new BlockPos(3, 0, 3));
			player.getAbilities().flying = false;
			player.onUpdateAbilities();
			player.teleportTo(overworld, surface.getX() + 3.5, surface.getY(), surface.getZ() + 0.5, java.util.Set.of(), 0, 0);
			overworld.setBlockAndUpdate(surface, Blocks.BEACON.defaultBlockState());
			return surface;
		});
		check(server.computeOnServer(s -> !s.overworld().getBlockState(beacon.below()).isAir()), "the beacon stands on loaded ground at " + beacon);
		ctx.waitTicks(100);
		placeEgg(server, beacon);
		check(!unlocked(server), "the egg on an unlit beacon does not unlock the ladder");
		check(!BeyondTheDragonClientTest.advancementDone(server, "dragon_notices"), "no advancement for the unlit beacon");

		// Give it a pyramid and try again.
		server.runOnServer(s -> {
			ServerLevel overworld = s.overworld();
			overworld.removeBlock(beacon.above(), false);
			for (int x = -1; x <= 1; x++) {
				for (int z = -1; z <= 1; z++) {
					overworld.setBlockAndUpdate(beacon.offset(x, -1, z), Blocks.IRON_BLOCK.defaultBlockState());
				}
			}
		});
		// A beacon rescans its beam over about forty ticks and only counts its pyramid every eighty.
		ctx.waitTicks(200);
		String beaconState = server.computeOnServer(s -> {
			ServerLevel overworld = s.overworld();
			BeaconBlockEntity entity = (BeaconBlockEntity) overworld.getBlockEntity(beacon);
			return "levels " + ((BeaconBlockEntityAccessor) entity).beyondTheDragon$levels()
					+ ", beam sections " + entity.getBeamSections().size()
					+ ", above " + overworld.getBlockState(beacon.above()).getBlock()
					+ ", base " + overworld.getBlockState(beacon.below()).getBlock()
					+ ", corner " + overworld.getBlockState(beacon.offset(1, -1, 1)).getBlock()
					+ ", world surface " + overworld.getHeight(Heightmap.Types.WORLD_SURFACE, beacon.getX(), beacon.getZ())
					+ ", beacon y " + beacon.getY();
		});
		BeyondTheDragon.LOGGER.info("[test] beacon before the lit attempt: {}", beaconState);
		placeEgg(server, beacon);
		check(unlocked(server), "the egg on a lit beacon unlocks the ladder (" + beaconState + ")");
		check(BeyondTheDragonClientTest.advancementDone(server, "dragon_notices"), "unlocking the ladder grants The Dragon Takes Notice");
		BeyondTheDragonClientTest.screenshot(ctx, "beyond_the_dragon_egg_on_beacon");
	}

	private void climb(ClientGameTestContext ctx, TestServerContext server) {
		// Into the End, invulnerable and in survival so the dragon counts us as a target.
		server.runOnServer(s -> {
			ServerLevel end = end(s);
			ServerPlayer player = player(s);
			player.setInvulnerable(true);
			player.setGameMode(GameType.SURVIVAL);
			// Beside the arena's centre column, never on it: the podium and portal are placed by the
			// heightmap at (0, 0), and a block there would move them.
			end.setBlockAndUpdate(new BlockPos(12, 99, 12), Blocks.OBSIDIAN.defaultBlockState());
			player.teleportTo(end, 12.5, 100, 12.5, java.util.Set.of(), 0, 0);
		});
		// The End unloaded while we were away; the dragon is back once its chunks are.
		waitOnServer(ctx, server, s -> !end(s).getDragons().isEmpty(), 1200);
		check(server.computeOnServer(s -> !end(s).getDragons().isEmpty()), "the dragon is there when we return to the End");
		BlockPos origin = server.computeOnServer(s -> end(s).getDragons().get(0).getFightOrigin());
		ctx.waitTicks(40);

		// Tier 0: the vanilla dragon. Killing it pays nothing.
		killDragon(server);
		waitOnServer(ctx, server, s -> end(s).getDragons().isEmpty(), 600);
		ctx.waitTicks(20);
		check(tier(server) == 0, "the vanilla dragon is tier 0");
		check(rewardsAtPortal(server, origin) == 0, "killing the tier 0 dragon drops no ladder reward");

		// Four crystals round the portal: the respawn sequence runs and the ladder climbs.
		respawn(ctx, server, origin, 1);

		// Tier 1 fires shulker bullets at us; unless another mod owns the dragon fight, in which
		// case our attacks stay off by default and the tiers and rewards still run.
		boolean otherDragonMod = DragonLadder.otherDragonModLoaded();
		waitOnServer(ctx, server, s -> !bulletsFromDragon(s).isEmpty(), otherDragonMod ? 200 : 300);
		boolean bullets = !server.computeOnServer(LadderTest::bulletsFromDragon).isEmpty();
		check(otherDragonMod ? !bullets : bullets, otherDragonMod
				? "with another dragon mod installed the tier 1 attacks stay off (no bullets from the dragon)"
				: "the tier 1 dragon fires shulker bullets owned by the dragon");
		BeyondTheDragonClientTest.screenshot(ctx, "beyond_the_dragon_tier_1");

		// Killing the tier 1 dragon drops tier 1's loot at the portal, and only once.
		EnderDragon tierOneDragon = server.computeOnServer(s -> end(s).getDragons().get(0));
		killDragon(server);
		waitOnServer(ctx, server, s -> end(s).getDragons().isEmpty(), 600);
		ctx.waitTicks(20);
		int dropped = rewardsAtPortal(server, origin);
		check(dropped == 4, "killing the tier 1 dragon drops its reward at the portal (got " + dropped + " dragon's breath, expected 4)");
		server.runOnServer(s -> DragonLadder.onDragonKilled(end(s), tierOneDragon, origin));
		check(rewardsAtPortal(server, origin) == dropped, "a second kill at the same tier drops nothing more");

		// Tier 2 breathes a pulling cloud under us.
		respawn(ctx, server, origin, 2);
		if (!otherDragonMod) {
			waitOnServer(ctx, server, s -> !cloudsFromDragon(s).isEmpty(), 400);
			check(!server.computeOnServer(LadderTest::cloudsFromDragon).isEmpty(), "the tier 2 dragon breathes a cloud it owns");
		}
		BeyondTheDragonClientTest.screenshot(ctx, "beyond_the_dragon_tier_2");
		// The respawn's crystal explosions clear whatever lay on the podium, so count from here.
		int beforeTwo = rewardsAtPortal(server, origin);
		killDragon(server);
		waitOnServer(ctx, server, s -> end(s).getDragons().isEmpty(), 600);
		ctx.waitTicks(20);
		int afterTwo = rewardsAtPortal(server, origin);
		check(afterTwo == beforeTwo + 8, "the tier 2 reward is tier 2's loot (got " + (afterTwo - beforeTwo) + " more dragon's breath, expected 8)");

		// Tier 3 charges us, and the ladder stops there.
		respawn(ctx, server, origin, 3);
		if (!otherDragonMod) {
			waitOnServer(ctx, server, s -> charging(s), 600);
			check(server.computeOnServer(LadderTest::charging), "the tier 3 dragon dive-bombs (its phase is charging a player)");
		}
		BeyondTheDragonClientTest.screenshot(ctx, "beyond_the_dragon_tier_3");
		killDragon(server);
		waitOnServer(ctx, server, s -> end(s).getDragons().isEmpty(), 600);
		respawn(ctx, server, origin, 3);
		check(tier(server) == 3, "a fourth respawn stays at tier 3");
		for (int t = 1; t <= 3; t++) {
			check(BeyondTheDragonClientTest.advancementDone(server, "tier_" + t), "beating the tier " + t + " dragon grants its advancement");
		}
	}

	private void placeEgg(TestServerContext server, BlockPos beacon) {
		server.runOnServer(s -> {
			ServerPlayer player = player(s);
			ItemStack egg = new ItemStack(Items.DRAGON_EGG);
			player.setItemInHand(InteractionHand.MAIN_HAND, egg);
			// Sneaking, as a player must: a plain right-click on a beacon opens its menu instead.
			player.setShiftKeyDown(true);
			player.gameMode.useItemOn(player, s.overworld(), egg, InteractionHand.MAIN_HAND,
					new BlockHitResult(Vec3.atCenterOf(beacon).add(0, 0.5, 0), Direction.UP, beacon, false));
			player.setShiftKeyDown(false);
		});
	}

	private void killDragon(TestServerContext server) {
		server.runOnServer(s -> {
			ServerLevel end = end(s);
			for (EnderDragon dragon : end.getDragons()) {
				dragon.kill();
			}
		});
	}

	private static boolean unlocked(TestServerContext server) {
		return server.computeOnServer(s -> LadderData.get(end(s)).unlocked());
	}

	private static int tier(TestServerContext server) {
		return server.computeOnServer(LadderTest::tier);
	}

	private static int tier(MinecraftServer s) {
		return LadderData.get(end(s)).tier();
	}

	private static int rewardsAtPortal(TestServerContext server, BlockPos origin) {
		return server.computeOnServer(s -> {
			ServerLevel end = end(s);
			BlockPos podium = end.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, EndPodiumFeature.getLocation(origin));
			int count = 0;
			for (ItemEntity item : end.getEntitiesOfClass(ItemEntity.class, new AABB(podium).inflate(8))) {
				if (item.getItem().is(Items.DRAGON_BREATH)) {
					count += item.getItem().getCount();
				}
			}
			return count;
		});
	}

	/** Summons the four crystals round the portal, starts the respawn and waits for the tier. */
	private void respawn(ClientGameTestContext ctx, TestServerContext server, BlockPos origin, int expectedTier) {
		server.runOnServer(s -> {
			ServerLevel end = end(s);
			// The exit portal's level is where its end portal blocks are; the fight keeps that
			// position privately, so read it off the blocks. The crystals go on the bedrock ring.
			BlockPos podium = EndPodiumFeature.getLocation(origin);
			long sx = 0;
			long sy = 0;
			long sz = 0;
			int n = 0;
			for (int x = podium.getX() - 64; x <= podium.getX() + 64; x++) {
				for (int z = podium.getZ() - 64; z <= podium.getZ() + 64; z++) {
					for (int y = Math.max(end.getMinBuildHeight(), podium.getY() - 40); y < Math.min(end.getMaxBuildHeight(), podium.getY() + 80); y++) {
						if (end.getBlockState(new BlockPos(x, y, z)).is(Blocks.END_PORTAL)) {
							sx += x;
							sy += y;
							sz += z;
							n++;
						}
					}
				}
			}
			if (n == 0) {
				throw new IllegalStateException("no exit portal found within 64 blocks of the podium at " + podium);
			}
			BlockPos portal = new BlockPos((int) Math.round((double) sx / n), (int) Math.round((double) sy / n), (int) Math.round((double) sz / n));
			BlockPos center = portal.above(1);
			// BetterEnd replaces vanilla's check with its own podium's: the crystals go four blocks
			// out from the End's origin, on bedrock, at any height. Vanilla wants them three out,
			// a block above the portal.
			boolean betterEnd = FabricLoader.getInstance().isModLoaded("betterend");
			for (Direction direction : Direction.Plane.HORIZONTAL) {
				BlockPos pos = center.relative(direction, 3);
				if (betterEnd) {
					pos = new BlockPos(0, center.getY(), 0).relative(direction, 4);
					for (int y = center.getY() + 48; y >= center.getY() - 16; y--) {
						BlockPos below = new BlockPos(pos.getX(), y - 1, pos.getZ());
						if (end.getBlockState(below).is(Blocks.BEDROCK) && end.getBlockState(below.above()).isAir()) {
							pos = below.above();
							break;
						}
					}
					BeyondTheDragon.LOGGER.info("[test] BetterEnd crystal at {} on {}", pos, end.getBlockState(pos.below()).getBlock());
				}
				EndCrystal crystal = EntityType.END_CRYSTAL.create(end);
				crystal.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
				end.addFreshEntity(crystal);
			}
			end.getDragonFight().tryRespawn();
		});
		int ticks = waitOnServer(ctx, server, s -> !end(s).getDragons().isEmpty() && tier(s) >= Math.min(expectedTier, DragonLadder.MAX_TIER), 2400);
		check(tier(server) == expectedTier, "the respawned dragon is tier " + expectedTier + " (respawn took " + ticks + " ticks, tier " + tier(server) + ")");
		ctx.waitTicks(20);
	}

	private static List<AreaEffectCloud> cloudsFromDragon(MinecraftServer s) {
		ServerLevel end = end(s);
		if (end.getDragons().isEmpty()) {
			return List.of();
		}
		EnderDragon dragon = end.getDragons().get(0);
		return end.getEntitiesOfClass(AreaEffectCloud.class, dragon.getBoundingBox().inflate(160), cloud -> cloud.getOwner() == dragon);
	}

	private static boolean charging(MinecraftServer s) {
		ServerLevel end = end(s);
		return !end.getDragons().isEmpty()
				&& end.getDragons().get(0).getPhaseManager().getCurrentPhase().getPhase() == EnderDragonPhase.CHARGING_PLAYER;
	}

	private static List<ShulkerBullet> bulletsFromDragon(MinecraftServer s) {
		ServerLevel end = end(s);
		if (end.getDragons().isEmpty()) {
			return List.of();
		}
		EnderDragon dragon = end.getDragons().get(0);
		return end.getEntitiesOfClass(ShulkerBullet.class, dragon.getBoundingBox().inflate(160), bullet -> bullet.getOwner() == dragon);
	}

	/** Waits, ten ticks at a time, until the server says so or the ticks run out; returns the ticks waited. */
	private static int waitOnServer(ClientGameTestContext ctx, TestServerContext server, java.util.function.Predicate<MinecraftServer> done, int maxTicks) {
		int waited = 0;
		while (waited < maxTicks && !server.computeOnServer(done::test)) {
			ctx.waitTicks(10);
			waited += 10;
		}
		return waited;
	}

	private static ServerLevel end(MinecraftServer s) {
		return s.getLevel(Level.END);
	}

	private static ServerPlayer player(MinecraftServer s) {
		return s.getPlayerList().getPlayers().get(0);
	}

	private void check(boolean condition, String what) {
		(condition ? ok : failed).accept(what);
	}
}
