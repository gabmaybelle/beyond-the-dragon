package com.gabbymaybelle.beyondthedragon;

import com.gabbymaybelle.beyondthedragon.client.VoidJellyRenderer;
import com.gabbymaybelle.beyondthedragon.jelly.JellyEggBlock;
import com.gabbymaybelle.beyondthedragon.jelly.Jellies;
import com.gabbymaybelle.beyondthedragon.jelly.VoidJelly;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import com.gabbymaybelle.beyondthedragon.harness.ClientGameTestContext;
import com.gabbymaybelle.beyondthedragon.harness.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Void Jelly in a real game: it exists and has its renderer (the core, the glowing eyes and
 * the see-through shell; the screenshots show it); a wild one won't take a saddle or a rider; a
 * hatched one takes both; a rider holding a chorus flower on a stick steers it with the forward
 * key, and without the stick it stays put; and it flies faster in the End than in the overworld.
 */
final class JellyTest {
	/** How long the forward key is held for each steering measurement. */
	private static final int STEER_TICKS = 80;

	private final Consumer<String> ok;
	private final Consumer<String> failed;

	JellyTest(Consumer<String> ok, Consumer<String> failed) {
		this.ok = ok;
		this.failed = failed;
	}

	void run(ClientGameTestContext ctx, TestServerContext server) {
		registry(ctx, server);
		server.runOnServer(s -> {
			ServerPlayer player = player(s);
			player.setGameMode(GameType.CREATIVE);
			player.getAbilities().invulnerable = true;
			player.onUpdateAbilities();
			player.getInventory().clearContent();
			player.teleportTo(s.overworld(), 0.5, 40, 0.5, Set.of(), 0, 0);
		});
		ctx.waitTicks(40);
		wild(ctx, server);
		double overworld = ride(ctx, server, Level.OVERWORLD, true);
		check(overworld > 5.0, "a rider holding a chorus flower on a stick steers a hatched jelly forward ("
				+ String.format("%.1f", overworld) + " blocks in " + STEER_TICKS + " ticks)");
		double still = ride(ctx, server, Level.OVERWORLD, false);
		check(still < 1.0, "without the stick the jelly isn't steered (" + String.format("%.1f", still) + " blocks)");
		double end = ride(ctx, server, Level.END, true);
		check(end > overworld * 1.3, "a jelly flies faster in the End (" + String.format("%.1f", end) + " blocks) than in the overworld ("
				+ String.format("%.1f", overworld) + ")");
		portraits(ctx, server);
		spawning(ctx, server);
		egg(ctx, server);
		drift(ctx, server);
		server.runOnServer(s -> {
			ServerPlayer player = player(s);
			player.getInventory().clearContent();
			player.setGameMode(GameType.SPECTATOR);
		});
	}

	/** The entity type, the stick, the stick's recipe and the jelly's textures exist. */
	private void registry(ClientGameTestContext ctx, TestServerContext server) {
		check(BuiltInRegistries.ENTITY_TYPE.containsKey(Jellies.VOID_JELLY_KEY), "the void jelly is a registered entity");
		check(BuiltInRegistries.ITEM.getOptional(ResourceKey.create(Registries.ITEM, id("chorus_flower_on_a_stick"))).isPresent(),
				"the chorus flower on a stick is a registered item");
		boolean recipe = server.computeOnServer(s -> s.getRecipeManager()
				.byKey(id("chorus_flower_on_a_stick")).isPresent());
		check(recipe, "the chorus flower on a stick has its recipe");
		for (ResourceLocation texture : new ResourceLocation[] {VoidJellyRenderer.CORE_TEXTURE, VoidJellyRenderer.SHELL_TEXTURE, VoidJellyRenderer.EYES_TEXTURE,
				VoidJellyRenderer.SADDLE_TEXTURE}) {
			check(ctx.computeOnClient(mc -> mc.getResourceManager().getResource(texture).isPresent()), "the texture " + texture + " is in the jar");
		}
	}

	/** A wild jelly refuses a saddle and a rider; its renderer is the jelly's. */
	private void wild(ClientGameTestContext ctx, TestServerContext server) {
		UUID id = server.computeOnServer(s -> {
			VoidJelly jelly = summon(s.overworld(), new Vec3(0.5, 40, 8.5), false);
			ServerPlayer player = player(s);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SADDLE));
			player.interactOn(jelly, InteractionHand.MAIN_HAND);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Jellies.CHORUS_STICK.get()));
			player.interactOn(jelly, InteractionHand.MAIN_HAND);
			return jelly.getUUID();
		});
		ctx.waitTicks(5);
		boolean saddled = server.computeOnServer(s -> jelly(s, id).isSaddled());
		boolean ridden = server.computeOnServer(s -> jelly(s, id).hasPassenger(player(s)));
		check(!saddled, "a wild jelly won't take a saddle");
		check(!ridden, "a wild jelly can't be ridden");
		boolean renderer = ctx.computeOnClient(mc -> {
			for (Entity entity : mc.level.entitiesForRendering()) {
				if (entity.getUUID().equals(id)) {
					Object drawer = mc.getEntityRenderDispatcher().getRenderer(entity);
					return drawer instanceof VoidJellyRenderer;
				}
			}
			return false;
		});
		check(renderer, "the jelly is drawn by its own renderer (core, glowing eyes and see-through shell)");
		server.runOnServer(s -> jelly(s, id).discard());
	}

	/**
	 * Summons a hatched jelly in the given dimension, saddles it and mounts the player the way a
	 * player does (right-clicking it with the stick in hand), takes the stick away if asked, then
	 * holds the forward key and returns how far the jelly went.
	 */
	private double ride(ClientGameTestContext ctx, TestServerContext server, ResourceKey<Level> dimension, boolean stick) {
		// High in the open: over the flat world's ground, or out in the void between the End's
		// main island and the outer islands.
		Vec3 start = dimension == Level.END ? new Vec3(400.5, 120, 400.5) : new Vec3(0.5, 40, 0.5);
		server.runOnServer(s -> {
			ServerPlayer player = player(s);
			player.stopRiding();
			player.teleportTo(s.getLevel(dimension), start.x, start.y + 6, start.z, Set.of(), 0, 0);
		});
		ctx.waitTicks(dimension == Level.END ? 100 : 20);
		UUID id = server.computeOnServer(s -> {
			ServerPlayer player = player(s);
			VoidJelly jelly = summon(s.getLevel(dimension), start, true);
			jelly.setYRot(0);
			jelly.setYHeadRot(0);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SADDLE));
			player.interactOn(jelly, InteractionHand.MAIN_HAND);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Jellies.CHORUS_STICK.get()));
			player.interactOn(jelly, InteractionHand.MAIN_HAND);
			if (!stick) {
				player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			}
			return jelly.getUUID();
		});
		ctx.waitTicks(20);
		boolean riding = server.computeOnServer(s -> jelly(s, id).hasPassenger(player(s)));
		boolean saddled = server.computeOnServer(s -> jelly(s, id).isSaddled());
		if (stick && dimension == Level.OVERWORLD) {
			check(saddled, "a hatched jelly takes a saddle");
			check(riding, "a saddled, hatched jelly takes a rider");
		}
		ctx.runOnClient(mc -> {
			mc.player.setYRot(0);
			mc.player.setXRot(0);
		});
		Vec3 before = server.computeOnServer(s -> jelly(s, id).position());
		holdForward(ctx, STEER_TICKS);
		ctx.waitTicks(10);
		Vec3 after = server.computeOnServer(s -> jelly(s, id).position());
		double distance = Math.sqrt(Math.pow(after.x - before.x, 2) + Math.pow(after.z - before.z, 2));
		BeyondTheDragon.LOGGER.info("[test] jelly in {} {} the stick: {} blocks", dimension.location(), stick ? "with" : "without",
				String.format("%.2f", distance));
		if (dimension == Level.END) {
			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			ctx.waitTicks(20);
			BeyondTheDragonClientTest.screenshot(ctx, "beyond_the_dragon_void_jelly_ridden");
			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
		}
		server.runOnServer(s -> {
			player(s).stopRiding();
			jelly(s, id).discard();
		});
		ctx.waitTicks(5);
		return distance;
	}

	/**
	 * Gabby's pictures: a jelly from the front in daylight and against the End's dark, and one
	 * wearing a saddle, seen as its rider sees it. (1.21.1 has no harness, so the 26.x check of
	 * the harness's pale cloth has nothing to measure here.)
	 */
	private void portraits(ClientGameTestContext ctx, TestServerContext server) {
		for (String name : java.util.List.of("overworld", "end", "saddled")) {
			ResourceKey<Level> dimension = name.equals("overworld") ? Level.OVERWORLD : Level.END;
			Vec3 at = dimension == Level.END ? new Vec3(400.5, 120, 400.5) : new Vec3(0.5, 40, 0.5);
			boolean saddled = name.equals("saddled");
			UUID[] ids = new UUID[2];
			server.runOnServer(s -> {
				ServerLevel level = s.getLevel(dimension);
				ServerPlayer player = player(s);
				player.getAbilities().flying = true;
				player.onUpdateAbilities();
				// The player faces north (yaw 180) towards the jellies, which face south, back at the camera.
				// The saddled one is seen as its rider sees it: from behind and above (north of it,
				// looking south and down).
				if (saddled) {
					player.teleportTo(level, at.x, at.y + 8, at.z - 8, Set.of(), 0, 35);
				} else {
					player.teleportTo(level, at.x, at.y + 1, at.z + 12, Set.of(), 180, 5);
				}
				VoidJelly adult = summon(level, at.add(saddled ? 0 : -1.5, 0, 0), true);
				adult.setYRot(0);
				adult.setYBodyRot(0);
				adult.setYHeadRot(0);
				adult.setNoAi(true);
				if (saddled) {
					adult.equipSaddle(new ItemStack(Items.SADDLE), null);
				}
				ids[0] = adult.getUUID();
				if (!saddled) {
					VoidJelly baby = summon(level, at.add(3.5, 1.5, 4), true);
					baby.setAge(-24000);
					baby.setYRot(0);
					baby.setYBodyRot(0);
					baby.setYHeadRot(0);
					baby.setNoAi(true);
					ids[1] = baby.getUUID();
				}
			});
			ctx.waitTicks(dimension == Level.END ? 100 : 40);
			ctx.runOnClient(mc -> mc.options.hideGui = true);
			ctx.waitTicks(10);
			BeyondTheDragonClientTest.screenshot(ctx, "beyond_the_dragon_void_jelly_" + name);
			ctx.runOnClient(mc -> mc.options.hideGui = false);
			server.runOnServer(s -> {
				for (UUID id : ids) {
					if (id != null) {
						jelly(s, id).discard();
					}
				}
			});
		}
	}

	/**
	 * The jelly egg: it's a block with its item; the sinking city's chests can hold one; placed
	 * beside a chorus flower it starts hatching (a tick is scheduled), steps through its three
	 * stages and hatches into a baby jelly that counts as hatched; an egg with no chorus near
	 * doesn't move. The four stages are photographed side by side.
	 */
	private void egg(ClientGameTestContext ctx, TestServerContext server) {
		check(BuiltInRegistries.BLOCK.containsKey(Jellies.EGG_KEY)
				&& BuiltInRegistries.ITEM.getOptional(ResourceKey.create(Registries.ITEM, id("jelly_egg"))).isPresent(),
				"the jelly egg is a block with its item");
		boolean inCityLoot = server.computeOnServer(s -> s.getResourceManager().getResource(id("loot_table/chests/sinking_city.json"))
				.map(r -> {
					try (java.io.BufferedReader reader = r.openAsReader()) {
						return reader.lines().anyMatch(line -> line.contains("beyond_the_dragon:jelly_egg"));
					} catch (java.io.IOException e) {
						return false;
					}
				}).orElse(false));
		check(inCityLoot, "the sinking city's chests can hold a jelly egg");
		BlockPos[] eggs = {new BlockPos(397, 120, 379), new BlockPos(399, 120, 379), new BlockPos(401, 120, 379), new BlockPos(403, 120, 379)};
		BlockPos lonely = new BlockPos(420, 120, 379);
		boolean[] scheduled = new boolean[2];
		server.runOnServer(s -> {
			ServerLevel end = s.getLevel(Level.END);
			for (BlockPos p : BlockPos.betweenClosed(new BlockPos(395, 119, 377), new BlockPos(405, 119, 381))) {
				end.setBlock(p, Blocks.END_STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
			end.setBlock(lonely.below(), Blocks.END_STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
			for (int i = 0; i < eggs.length; i++) {
				end.setBlock(eggs[i].north(), Blocks.CHORUS_FLOWER.defaultBlockState(), Block.UPDATE_CLIENTS);
				end.setBlockAndUpdate(eggs[i], Jellies.EGG.get().defaultBlockState());
				end.setBlock(eggs[i], Jellies.EGG.get().defaultBlockState().setValue(JellyEggBlock.STAGE, i), Block.UPDATE_CLIENTS);
			}
			end.setBlockAndUpdate(lonely, Jellies.EGG.get().defaultBlockState());
			scheduled[0] = end.getBlockTicks().hasScheduledTick(eggs[0], Jellies.EGG.get());
			scheduled[1] = end.getBlockTicks().hasScheduledTick(lonely, Jellies.EGG.get());
			ServerPlayer player = player(s);
			player.getAbilities().flying = true;
			player.onUpdateAbilities();
			player.teleportTo(end, 400.5, 121.6, 382.2, Set.of(), 180, 28);
		});
		check(scheduled[0], "an egg placed beside chorus starts hatching");
		check(!scheduled[1], "an egg with no chorus near doesn't start");
		ctx.waitTicks(100);
		ctx.runOnClient(mc -> mc.options.hideGui = true);
		ctx.waitTicks(10);
		BeyondTheDragonClientTest.screenshot(ctx, "beyond_the_dragon_jelly_egg_stages");
		ctx.runOnClient(mc -> mc.options.hideGui = false);
		// Each scheduled step, run by hand: three stages, then the hatching.
		String[] result = server.computeOnServer(s -> {
			ServerLevel end = s.getLevel(Level.END);
			for (int step = 0; step <= JellyEggBlock.MAX_STAGE; step++) {
				for (BlockPos p : new BlockPos[] {eggs[0], lonely}) {
					BlockState state = end.getBlockState(p);
					if (state.is(Jellies.EGG.get())) {
						state.tick(end, p, end.getRandom());
					}
				}
			}
			java.util.List<VoidJelly> near = end.getEntitiesOfClass(VoidJelly.class, new AABB(eggs[0]).inflate(4));
			java.util.List<VoidJelly> nearLonely = end.getEntitiesOfClass(VoidJelly.class, new AABB(lonely).inflate(4));
			String hatched = near.size() == 1 && near.getFirst().isBaby() && near.getFirst().isHatched() && end.getBlockState(eggs[0]).isAir()
					? "yes" : near.size() + " jellies, egg block " + end.getBlockState(eggs[0]);
			BlockState lonelyState = end.getBlockState(lonely);
			String waited = nearLonely.isEmpty() && lonelyState.is(Jellies.EGG.get()) && lonelyState.getValue(JellyEggBlock.STAGE) == 0
					? "yes" : nearLonely.size() + " jellies, block " + lonelyState;
			near.forEach(Entity::discard);
			nearLonely.forEach(Entity::discard);
			for (BlockPos p : BlockPos.betweenClosed(new BlockPos(395, 119, 377), new BlockPos(405, 121, 381))) {
				end.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
			end.setBlock(lonely, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			end.setBlock(lonely.below(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			return new String[] {hatched, waited};
		});
		check(result[0].equals("yes"), "beside chorus an egg goes through its three stages and hatches a baby jelly that counts as hatched"
				+ (result[0].equals("yes") ? "" : " (" + result[0] + ")"));
		check(result[1].equals("yes"), "an egg with no chorus near stays an egg" + (result[1].equals("yes") ? "" : " (" + result[1] + ")"));
	}

	/**
	 * Wild jellies wandering out among the End's islands stay up among them: eight are left to
	 * drift for about two and a half game minutes (at five times speed), and the lowest any of them
	 * gets is measured against {@link VoidJelly#END_MIN_Y}. (Vanilla's wandering sank them under the
	 * islands, to heights of 2 to 15.)
	 */
	private void drift(ClientGameTestContext ctx, TestServerContext server) {
		int x = Config.ringMiddle + 600;
		server.runOnServer(s -> player(s).teleportTo(s.getLevel(Level.END), x + 0.5, 120, 0.5, Set.of(), 0, 0));
		ctx.waitTicks(200);
		UUID[] ids = server.computeOnServer(s -> {
			ServerLevel end = s.getLevel(Level.END);
			UUID[] made = new UUID[8];
			for (int i = 0; i < made.length; i++) {
				made[i] = summon(end, new Vec3(x - 30 + (i % 4) * 20, 70, -20 + (i / 4) * 40), false).getUUID();
			}
			return made;
		});
		double[] lowest = {Double.MAX_VALUE};
		command(server, "tick rate 100");
		for (int sample = 0; sample < 30; sample++) {
			ctx.waitTicks(20);
			server.runOnServer(s -> {
				for (UUID id : ids) {
					if (s.getLevel(Level.END).getEntity(id) instanceof VoidJelly jelly) {
						lowest[0] = Math.min(lowest[0], jelly.getY());
					}
				}
			});
		}
		command(server, "tick rate 20");
		server.runOnServer(s -> {
			for (UUID id : ids) {
				if (s.getLevel(Level.END).getEntity(id) instanceof VoidJelly jelly) {
					jelly.discard();
				}
			}
		});
		check(lowest[0] >= VoidJelly.END_MIN_Y - 6, "wild jellies drift up among the End's islands, not under them (lowest "
				+ String.format("%.0f", lowest[0]) + ", kept to " + VoidJelly.END_MIN_Y + " and up)");
	}

	/** How many of the game's own spawn attempts are made on island ground at each distance. */
	private static final int SPAWN_ATTEMPTS = 200;

	/**
	 * Wild jellies spawn the game's own way (the End biomes' creature list, then the jelly's spawn
	 * rule) on island ground out in the Middle Reaches, and not on the islands nearer in nor
	 * outside the End. The attempts are the game's natural spawner aimed at island surfaces near
	 * the player, so the count doesn't depend on waiting for the game's slow creature timer.
	 */
	private void spawning(ClientGameTestContext ctx, TestServerContext server) {
		int far = spawnsAt(ctx, server, Config.ringMiddle + 600);
		int near = spawnsAt(ctx, server, Config.ringMiddle - 700);
		boolean overworld = server.computeOnServer(s -> VoidJelly.canSpawnNaturallyAt(s.overworld(), new BlockPos(Config.ringMiddle + 600, 64, 0)));
		check(far > 0, "wild jellies spawn on the End's islands in the Middle Reaches (" + far + " from " + SPAWN_ATTEMPTS + " attempts at "
				+ (Config.ringMiddle + 600) + " blocks)");
		check(near == 0, "no wild jellies spawn nearer in than the Middle Reaches (" + near + " at " + (Config.ringMiddle - 700) + " blocks)");
		check(!overworld, "wild jellies don't spawn outside the End");
		if (System.getenv("BTD_JELLY_DENSITY") != null) {
			density(ctx, server, Config.ringMiddle + 600);
		}
	}

	/**
	 * A measurement, not a check (BTD_JELLY_DENSITY=1): how many wild jellies are near a player who
	 * waits in the Middle Reaches through ten minutes of natural spawning, sampled each minute.
	 */
	private void density(ClientGameTestContext ctx, TestServerContext server, int distance) {
		server.runOnServer(s -> player(s).teleportTo(s.getLevel(Level.END), distance + 0.5, 120, 0.5, Set.of(), 0, 0));
		ctx.waitTicks(200);
		StringBuilder counts = new StringBuilder();
		// Five times the normal speed: `tick sprint` stops the window drawing long enough for the
		// game watcher to call it hung. A game minute (1200 ticks) is then about 240 client ticks.
		// The test world has natural spawning off; this measurement needs it on.
		boolean spawning = server.computeOnServer(s -> s.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING));
		command(server, "gamerule doMobSpawning true");
		command(server, "tick rate 100");
		for (int minute = 1; minute <= 10; minute++) {
			ctx.waitTicks(240);
			int near = server.computeOnServer(s -> s.getLevel(Level.END).getEntitiesOfClass(VoidJelly.class,
					new AABB(distance - 128, -64, -128, distance + 128, 320, 128)).size());
			counts.append(minute == 1 ? "" : ", ").append(near);
		}
		command(server, "tick rate 20");
		command(server, "gamerule doMobSpawning " + spawning);
		ok.accept("measured: wild jellies within 128 blocks at " + distance + " blocks out, each minute for ten minutes: " + counts);
	}

	/** Stands the player in the End at that distance, then runs the natural spawner on nearby island ground. */
	private int spawnsAt(ClientGameTestContext ctx, TestServerContext server, int distance) {
		server.runOnServer(s -> player(s).teleportTo(s.getLevel(Level.END), distance + 0.5, 120, 0.5, Set.of(), 0, 0));
		ctx.waitTicks(200);
		int count = server.computeOnServer(s -> {
			ServerLevel end = s.getLevel(Level.END);
			java.util.List<BlockPos> ground = new java.util.ArrayList<>();
			for (int dx = -90; dx <= 90; dx += 6) {
				for (int dz = -90; dz <= 90; dz += 6) {
					int x = distance + dx;
					int z = dz;
					if (dx * dx + dz * dz < 30 * 30 || !end.hasChunkAt(new BlockPos(x, 0, z))) {
						continue;
					}
					int top = end.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
					if (top > end.getMinBuildHeight() + 1) {
						ground.add(new BlockPos(x, top, z));
					}
				}
			}
			if (ground.isEmpty()) {
				return -1;
			}
			AABB around = new AABB(distance - 160, end.getMinBuildHeight(), -160, distance + 160, end.getMaxBuildHeight(), 160);
			int before = end.getEntitiesOfClass(VoidJelly.class, around).size();
			for (int i = 0; i < SPAWN_ATTEMPTS; i++) {
				NaturalSpawner.spawnCategoryForPosition(MobCategory.CREATURE, end, ground.get(i % ground.size()));
			}
			java.util.List<VoidJelly> jellies = end.getEntitiesOfClass(VoidJelly.class, around);
			int spawned = jellies.size() - before;
			jellies.forEach(Entity::discard);
			return spawned;
		});
		if (count < 0) {
			failed.accept("no island ground found near " + distance + " blocks out in the End to spawn on");
			return 0;
		}
		return count;
	}

	/** Holds the forward key for that many ticks (1.21.1's stand-in test API has no input helper). */
	private static void holdForward(ClientGameTestContext ctx, int ticks) {
		ctx.runOnClient(mc -> mc.options.keyUp.setDown(true));
		ctx.waitTicks(ticks);
		ctx.runOnClient(mc -> mc.options.keyUp.setDown(false));
	}

	private static void command(TestServerContext server, String command) {
		server.runOnServer(s -> s.getCommands().performPrefixedCommand(s.createCommandSourceStack(), command));
	}

	private static VoidJelly summon(ServerLevel level, Vec3 at, boolean hatched) {
		VoidJelly jelly = Jellies.VOID_JELLY.get().create(level);
		jelly.setHatched(hatched);
		jelly.moveTo(at.x, at.y, at.z, 0, 0);
		level.addFreshEntity(jelly);
		return jelly;
	}

	private static VoidJelly jelly(MinecraftServer s, UUID id) {
		for (ServerLevel level : s.getAllLevels()) {
			if (level.getEntity(id) instanceof VoidJelly jelly) {
				return jelly;
			}
		}
		throw new IllegalStateException("no jelly " + id);
	}

	private static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(BeyondTheDragon.MOD_ID, path);
	}

	private static ServerPlayer player(MinecraftServer s) {
		return s.getPlayerList().getPlayers().get(0);
	}

	private void check(boolean condition, String what) {
		(condition ? ok : failed).accept(what);
	}
}
