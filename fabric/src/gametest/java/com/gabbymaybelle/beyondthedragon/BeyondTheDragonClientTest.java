package com.gabbymaybelle.beyondthedragon;

import com.gabbymaybelle.beyondthedragon.titles.TitleWatcher;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

/**
 * The mod in a real game. Run with {@code ./gradlew :fabric:runClientGameTest}; screenshots land
 * in fabric/build/run/clientGameTest/screenshots. Each feature adds its own checks here as it
 * lands, and each check is proven by running it once with the feature switched off.
 */
public class BeyondTheDragonClientTest implements FabricClientGameTest {
	private final List<String> failures = new ArrayList<>();

	@Override
	public void runTest(ClientGameTestContext ctx) {
		settingsFile();
		// The test world is flat (one biome, quick to make), but flat worlds switch structures off
		// by default and the End still needs them.
		try (TestSingleplayerContext world = ctx.worldBuilder().adjustSettings(ui -> ui.setGenerateStructures(true)).create()) {
			world.getConnection().waitForChunksRender();
			boolean loaded = world.getServer().computeOnServer(server -> FabricLoader.getInstance().isModLoaded(BeyondTheDragon.MOD_ID));
			check(loaded, "the mod is loaded in a new world");
			// BTD_TEST_ONLY=structures or =aphelite (an environment variable, passed through by Gradle)
			// plays just that part: a couple of minutes instead of five while tuning.
			String only = System.getenv("BTD_TEST_ONLY");
			boolean all = only == null || only.isBlank();
			if (all) {
				titles(ctx, world.getServer());
			}
			if (all || only.equals("aphelite")) {
				new ApheliteTest(what -> check(true, what), what -> check(false, what)).run(ctx, world.getServer());
			}
			if (all || only.equals("structures")) {
				new StructuresTest(what -> check(true, what), what -> check(false, what)).run(ctx, world.getServer());
			}
			if (all) {
				new LadderTest(what -> check(true, what), what -> check(false, what)).run(ctx, world.getServer());
			}
		}
		if (!failures.isEmpty()) {
			throw new AssertionError(failures.size() + " check(s) failed:\n - " + String.join("\n - ", failures));
		}
	}

	/** The settings file is written with the defaults, survives a typo, and keeps unknown keys. */
	private void settingsFile() {
		Path file = Config.path();
		check(Files.exists(file), "the settings file is written on first start");
		check(Config.ringMiddle == 2000 && Config.ringOuter == 5000 && Config.ringEdge == 10000, "the rings default to 2000, 5000 and 10000");
		try {
			String original = Files.readString(file, StandardCharsets.UTF_8);
			Files.writeString(file, original.replace("ringOuter=5000", "ringOuter=five") + "someFutureSetting=yes\n", StandardCharsets.UTF_8);
			Config.load();
			check(Config.ringOuter == 5000, "a setting with a typo falls back to its default");
			String rewritten = Files.readString(file, StandardCharsets.UTF_8);
			check(rewritten.contains("ringOuter=five"), "a typo is left in the file for the player to fix (not overwritten)");
			check(rewritten.contains("someFutureSetting=yes"), "a setting this version doesn't know is kept");
			Files.writeString(file, original, StandardCharsets.UTF_8);
			Config.load();
		} catch (IOException e) {
			failures.add("couldn't edit the settings file: " + e);
		}
	}

	/**
	 * Biome and ring titles. The test world is flat with one biome, so the End is used for the
	 * changes: the biome you start in gets a title after the dwell time; arriving in the End
	 * inside the cooldown shows nothing; after the cooldown the End's title shows, with the
	 * inner ring as its subtitle; flying out past the middle ring's start changes the subtitle.
	 * The watcher's own record of what it showed is checked, which is what the packets are sent
	 * from; the screenshots show the titles themselves.
	 */
	private void titles(ClientGameTestContext ctx, TestServerContext server) {
		int dwell = 20 * Config.biomeTitleDwellSeconds;
		int cooldown = 20 * Config.biomeTitleCooldownSeconds;
		UUID id = server.computeOnServer(s -> player(s).getUUID());
		ResourceKey<Biome> start = server.computeOnServer(s -> biomeAt(player(s)));
		ctx.waitTicks(dwell + 40);
		check(TitleWatcher.shownBiome(id).equals(Optional.of(start)),
				"the biome you start in (" + start.identifier() + ") gets its title after " + Config.biomeTitleDwellSeconds + " seconds");

		// Into the End, above the main island, as a spectator so nothing falls.
		server.runOnServer(s -> {
			ServerPlayer player = player(s);
			player.setGameMode(GameType.SPECTATOR);
			player.teleportTo(s.getLevel(Level.END), 0.5, 90, 0.5, Set.of(), 0, 0, false);
		});
		ctx.waitTicks(dwell + 40);
		check(TitleWatcher.shownBiome(id).equals(Optional.of(start)),
				"arriving in the End inside the " + Config.biomeTitleCooldownSeconds + " second cooldown shows nothing yet");
		// The title fires on the watcher's next second once the cooldown has passed; wait for it,
		// then a moment more so the screenshot catches it faded in.
		ctx.waitFor(mc -> TitleWatcher.shownBiome(id).equals(Optional.of(Biomes.THE_END)), cooldown + 40);
		ctx.waitTicks(15);
		screenshot(ctx, "beyond_the_dragon_end_title");
		check(TitleWatcher.shownBiome(id).equals(Optional.of(Biomes.THE_END)),
				"after the cooldown the End's title shows (The Dragon's Island)");
		check(TitleWatcher.shownRing(id).map(ring -> ring.name().equals("INNER")).orElse(false),
				"the inner ring is the subtitle");
		check(advancementDone(server, "root"), "entering the End opens the Beyond the Dragon advancements tab");

		// Out past the middle ring's start, in the same biome or another: the ring change shows.
		server.runOnServer(s -> player(s).teleportTo(Config.ringMiddle + 100.5, 90, 0.5));
		ctx.waitFor(mc -> TitleWatcher.shownRing(id).map(ring -> ring.name().equals("MIDDLE")).orElse(false), cooldown + dwell + 40);
		ctx.waitTicks(15);
		screenshot(ctx, "beyond_the_dragon_ring_title");
		check(TitleWatcher.shownRing(id).map(ring -> ring.name().equals("MIDDLE")).orElse(false),
				"crossing into the middle ring shows the ring subtitle");
		ctx.waitTicks(30);
		check(advancementDone(server, "middle_reaches"), "reaching the middle ring grants its advancement");
		check(!advancementDone(server, "outer_reaches"), "the outer ring's advancement waits (not granted at the middle ring)");
	}

	/**
	 * A screenshot, kept: the test's run directory is thrown away afterwards, so the file is
	 * copied to the folder named by BTD_SCREENSHOTS in the environment, when there is one.
	 */
	static Path screenshot(ClientGameTestContext ctx, String name) {
		return screenshot(ctx, name, ctx.takeScreenshot(name));
	}

	static Path screenshot(ClientGameTestContext ctx, TestScreenshotOptions options) {
		Path shot = ctx.takeScreenshot(options);
		return screenshot(ctx, stripPng(shot.getFileName().toString()), shot);
	}

	private static String stripPng(String fileName) {
		return fileName.endsWith(".png") ? fileName.substring(0, fileName.length() - 4) : fileName;
	}

	private static Path screenshot(ClientGameTestContext ctx, String name, Path shot) {
		String keep = System.getenv("BTD_SCREENSHOTS");
		if (keep != null && !keep.isBlank()) {
			try {
				Path dir = Path.of(keep);
				Files.createDirectories(dir);
				Files.copy(shot, dir.resolve(name + ".png"), StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException e) {
				BeyondTheDragon.LOGGER.warn("[test] couldn't keep the screenshot {}: {}", name, e.toString());
			}
		}
		return shot;
	}

	/** Whether the test player has finished one of the mod's advancements. */
	static boolean advancementDone(TestServerContext server, String path) {
		return server.computeOnServer(s -> {
			var holder = s.getAdvancements().get(Identifier.fromNamespaceAndPath(BeyondTheDragon.MOD_ID, path));
			return holder != null && player(s).getAdvancements().getOrStartProgress(holder).isDone();
		});
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().get(0);
	}

	private static ResourceKey<Biome> biomeAt(ServerPlayer player) {
		ServerLevel level = player.level();
		return level.getBiome(player.blockPosition()).unwrapKey().orElseThrow();
	}

	private void check(boolean ok, String what) {
		if (ok) {
			BeyondTheDragon.LOGGER.info("[test] ok: {}", what);
		} else {
			BeyondTheDragon.LOGGER.error("[test] FAILED: {}", what);
			failures.add(what);
		}
	}
}
