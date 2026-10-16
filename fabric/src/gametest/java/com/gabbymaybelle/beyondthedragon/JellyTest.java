package com.gabbymaybelle.beyondthedragon;

import com.gabbymaybelle.beyondthedragon.client.VoidJellyRenderer;
import com.gabbymaybelle.beyondthedragon.jelly.Jellies;
import com.gabbymaybelle.beyondthedragon.jelly.VoidJelly;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Void Jelly in a real game: it exists and has its renderer (the core, the glowing eyes and
 * the see-through shell; the screenshots show it); a wild one won't take a harness or a rider; a
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
			player.setPermanentlyInvulnerable(true);
			player.getInventory().clearContent();
			player.teleportTo(s.overworld(), 0.5, 40, 0.5, Set.of(), 0, 0, false);
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
		server.runOnServer(s -> {
			ServerPlayer player = player(s);
			player.getInventory().clearContent();
			player.setGameMode(GameType.SPECTATOR);
		});
	}

	/** The entity type, the stick, the stick's recipe and the jelly's textures exist. */
	private void registry(ClientGameTestContext ctx, TestServerContext server) {
		check(BuiltInRegistries.ENTITY_TYPE.getOptional(Jellies.VOID_JELLY_KEY).isPresent(), "the void jelly is a registered entity");
		check(BuiltInRegistries.ITEM.getOptional(ResourceKey.create(Registries.ITEM, id("chorus_flower_on_a_stick"))).isPresent(),
				"the chorus flower on a stick is a registered item");
		boolean recipe = server.computeOnServer(s -> s.getRecipeManager()
				.byKey(ResourceKey.create(Registries.RECIPE, id("chorus_flower_on_a_stick"))).isPresent());
		check(recipe, "the chorus flower on a stick has its recipe");
		for (Identifier texture : new Identifier[] {VoidJellyRenderer.CORE_TEXTURE, VoidJellyRenderer.SHELL_TEXTURE, VoidJellyRenderer.EYES_TEXTURE}) {
			check(ctx.computeOnClient(mc -> mc.getResourceManager().getResource(texture).isPresent()), "the texture " + texture + " is in the jar");
		}
		long harnesses = Items.HARNESS.asList().stream().filter(item -> {
			Identifier sheet = VoidJellyRenderer.harnessTexture(new ItemStack(item));
			return sheet != null && ctx.computeOnClient(mc -> mc.getResourceManager().getResource(sheet).isPresent());
		}).count();
		check(harnesses == Items.HARNESS.asList().size(), "every harness colour has the jelly's own sheet (" + harnesses + " of "
				+ Items.HARNESS.asList().size() + ")");
	}

	/** A wild jelly refuses a harness and a rider; its renderer is the jelly's. */
	private void wild(ClientGameTestContext ctx, TestServerContext server) {
		UUID id = server.computeOnServer(s -> {
			VoidJelly jelly = summon(s.overworld(), new Vec3(0.5, 40, 8.5), false);
			ServerPlayer player = player(s);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.HARNESS.asList().getFirst()));
			jelly.mobInteract(player, InteractionHand.MAIN_HAND);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Jellies.CHORUS_STICK.get()));
			jelly.mobInteract(player, InteractionHand.MAIN_HAND);
			return jelly.getUUID();
		});
		ctx.waitTicks(5);
		boolean harnessed = server.computeOnServer(s -> jelly(s, id).isWearingBodyArmor());
		boolean ridden = server.computeOnServer(s -> jelly(s, id).hasPassenger(player(s)));
		check(!harnessed, "a wild jelly won't take a harness");
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
	 * Summons a hatched jelly in the given dimension, harnesses it and mounts the player the way a
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
			player.teleportTo(s.getLevel(dimension), start.x, start.y + 6, start.z, Set.of(), 0, 0, false);
		});
		ctx.waitTicks(dimension == Level.END ? 100 : 20);
		UUID id = server.computeOnServer(s -> {
			ServerPlayer player = player(s);
			VoidJelly jelly = summon(s.getLevel(dimension), start, true);
			jelly.setYRot(0);
			jelly.setYHeadRot(0);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.HARNESS.asList().getFirst()));
			jelly.mobInteract(player, InteractionHand.MAIN_HAND);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Jellies.CHORUS_STICK.get()));
			jelly.mobInteract(player, InteractionHand.MAIN_HAND);
			if (!stick) {
				player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			}
			return jelly.getUUID();
		});
		ctx.waitTicks(20);
		boolean riding = server.computeOnServer(s -> jelly(s, id).hasPassenger(player(s)));
		boolean harnessed = server.computeOnServer(s -> jelly(s, id).isWearingBodyArmor());
		if (stick && dimension == Level.OVERWORLD) {
			check(harnessed, "a hatched jelly takes a harness");
			check(riding, "a harnessed, hatched jelly takes a rider");
		}
		ctx.runOnClient(mc -> {
			mc.player.setYRot(0);
			mc.player.setXRot(0);
		});
		Vec3 before = server.computeOnServer(s -> jelly(s, id).position());
		ctx.getInput().holdKeyFor(options -> options.keyUp, STEER_TICKS);
		ctx.waitTicks(10);
		Vec3 after = server.computeOnServer(s -> jelly(s, id).position());
		double distance = Math.sqrt(Math.pow(after.x - before.x, 2) + Math.pow(after.z - before.z, 2));
		BeyondTheDragon.LOGGER.info("[test] jelly in {} {} the stick: {} blocks", dimension.identifier(), stick ? "with" : "without",
				String.format("%.2f", distance));
		if (dimension == Level.END) {
			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			ctx.waitTicks(20);
			BeyondTheDragonClientTest.screenshot(ctx, TestScreenshotOptions.of("beyond_the_dragon_void_jelly_ridden").withSize(1280, 720));
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
	 * wearing a white harness. The harnessed one is also measured: the happy ghast's harness
	 * covers the top half of the body in white cloth, which made a ridden jelly look like a happy
	 * ghast; with the jelly's own sheets, next to nothing in the picture is that pale grey.
	 */
	private void portraits(ClientGameTestContext ctx, TestServerContext server) {
		for (String name : java.util.List.of("overworld", "end", "harnessed")) {
			ResourceKey<Level> dimension = name.equals("overworld") ? Level.OVERWORLD : Level.END;
			Vec3 at = dimension == Level.END ? new Vec3(400.5, 120, 400.5) : new Vec3(0.5, 40, 0.5);
			boolean harnessed = name.equals("harnessed");
			UUID[] ids = new UUID[2];
			server.runOnServer(s -> {
				ServerLevel level = s.getLevel(dimension);
				ServerPlayer player = player(s);
				player.getAbilities().flying = true;
				player.onUpdateAbilities();
				// The player faces north (yaw 180) towards the jellies, which face south, back at the camera.
				// The harnessed one is seen as its rider sees it: from behind and above (north of it,
				// looking south and down), where the happy ghast's harness shows most.
				if (harnessed) {
					player.teleportTo(level, at.x, at.y + 8, at.z - 8, Set.of(), 0, 35, false);
				} else {
					player.teleportTo(level, at.x, at.y + 1, at.z + 12, Set.of(), 180, 5, false);
				}
				VoidJelly adult = summon(level, at.add(harnessed ? 0 : -1.5, 0, 0), true);
				adult.setYRot(0);
				adult.setYBodyRot(0);
				adult.setYHeadRot(0);
				adult.setNoAi(true);
				if (harnessed) {
					adult.setItemSlot(EquipmentSlot.BODY, new ItemStack(Items.HARNESS.asList().getFirst()));
				}
				ids[0] = adult.getUUID();
				if (!harnessed) {
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
			ctx.runOnClient(mc -> {
				if (!mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
			});
			ctx.waitTicks(10);
			java.nio.file.Path shot = BeyondTheDragonClientTest.screenshot(ctx,
					TestScreenshotOptions.of("beyond_the_dragon_void_jelly_" + name).withSize(1280, 720));
			ctx.runOnClient(mc -> {
				if (mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
			});
			if (harnessed) {
				double pale = paleGreyShare(shot, 70);
				BeyondTheDragon.LOGGER.info("[test] harnessed jelly: {}% grey over 70, {}% over 110", String.format("%.2f", pale * 100),
						String.format("%.2f", paleGreyShare(shot, 110) * 100));
				check(pale < 0.0025, "a harnessed jelly still shows its shell, not the happy ghast's white cloth ("
						+ String.format("%.2f", pale * 100) + "% of the picture pale grey)");
			}
			server.runOnServer(s -> {
				for (UUID id : ids) {
					if (id != null) {
						jelly(s, id).discard();
					}
				}
			});
		}
	}

	/** The share of a picture's pixels that are a pale, colourless grey (the harness's cloth). */
	private static double paleGreyShare(java.nio.file.Path png, int brightness) {
		try {
			java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(png.toFile());
			long pale = 0;
			for (int y = 0; y < img.getHeight(); y++) {
				for (int x = 0; x < img.getWidth(); x++) {
					int c = img.getRGB(x, y);
					int r = (c >> 16) & 0xff;
					int g = (c >> 8) & 0xff;
					int b = c & 0xff;
					int max = Math.max(r, Math.max(g, b));
					if (max > brightness && max - Math.min(r, Math.min(g, b)) < 16) {
						pale++;
					}
				}
			}
			return (double) pale / ((long) img.getWidth() * img.getHeight());
		} catch (java.io.IOException e) {
			return 1.0;
		}
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
		server.runOnServer(s -> player(s).teleportTo(s.getLevel(Level.END), distance + 0.5, 120, 0.5, Set.of(), 0, 0, false));
		ctx.waitTicks(200);
		StringBuilder counts = new StringBuilder();
		// Five times the normal speed: `tick sprint` stops the window drawing long enough for the
		// game watcher to call it hung. A game minute (1200 ticks) is then about 240 client ticks.
		// The test world has natural spawning off; this measurement needs it on.
		boolean spawning = server.computeOnServer(s -> s.getLevel(Level.END).getGameRules().get(net.minecraft.world.level.gamerules.GameRules.SPAWN_MOBS));
		server.runCommand("gamerule spawn_mobs true");
		server.runCommand("tick rate 100");
		for (int minute = 1; minute <= 10; minute++) {
			ctx.waitTicks(240);
			int near = server.computeOnServer(s -> s.getLevel(Level.END).getEntitiesOfClass(VoidJelly.class,
					new AABB(distance - 128, -64, -128, distance + 128, 320, 128)).size());
			counts.append(minute == 1 ? "" : ", ").append(near);
		}
		server.runCommand("tick rate 20");
		server.runCommand("gamerule spawn_mobs " + spawning);
		ok.accept("measured: wild jellies within 128 blocks at " + distance + " blocks out, each minute for ten minutes: " + counts);
	}

	/** Stands the player in the End at that distance, then runs the natural spawner on nearby island ground. */
	private int spawnsAt(ClientGameTestContext ctx, TestServerContext server, int distance) {
		server.runOnServer(s -> player(s).teleportTo(s.getLevel(Level.END), distance + 0.5, 120, 0.5, Set.of(), 0, 0, false));
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
					if (top > end.getMinY() + 1) {
						ground.add(new BlockPos(x, top, z));
					}
				}
			}
			if (ground.isEmpty()) {
				return -1;
			}
			AABB around = new AABB(distance - 160, end.getMinY(), -160, distance + 160, end.getMaxY(), 160);
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

	private static VoidJelly summon(ServerLevel level, Vec3 at, boolean hatched) {
		VoidJelly jelly = Jellies.VOID_JELLY.get().create(level, EntitySpawnReason.COMMAND);
		jelly.setHatched(hatched);
		jelly.snapTo(at.x, at.y, at.z, 0, 0);
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

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(BeyondTheDragon.MOD_ID, path);
	}

	private static ServerPlayer player(MinecraftServer s) {
		return s.getPlayerList().getPlayers().get(0);
	}

	private void check(boolean condition, String what) {
		(condition ? ok : failed).accept(what);
	}
}
