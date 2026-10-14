package com.gabbymaybelle.beyondthedragon;

import com.gabbymaybelle.beyondthedragon.aphelite.Aphelite;
import com.gabbymaybelle.beyondthedragon.structures.Observatories;
import com.gabbymaybelle.beyondthedragon.structures.ObservatoryData;
import java.util.List;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * The Outer Reaches structures in a real game: the nearest observatory to the End's centre is
 * found by the game's own locator, it stands on land (nothing under its base is air), its
 * lectern and chests are there; putting back every missing block restores it, which hands out
 * the keeper's chart with markers on it; and a death in the End comes back at its lectern.
 */
final class StructuresTest {
	private final Consumer<String> ok;
	private final Consumer<String> failed;

	StructuresTest(Consumer<String> ok, Consumer<String> failed) {
		this.ok = ok;
		this.failed = failed;
	}

	void run(ClientGameTestContext ctx, TestServerContext server) {
		String report = server.computeOnServer(s -> {
			ServerLevel end = s.getLevel(Level.END);
			Holder<Structure> observatory = end.registryAccess().lookupOrThrow(Registries.STRUCTURE)
					.getOrThrow(ResourceKey.create(Registries.STRUCTURE, Aphelite.id("observatory")));
			BlockPos start = end.findNearestMapStructure(HolderSet.direct(observatory), new BlockPos(0, 64, 0), 120, false);
			if (start == null) {
				return "none within 120 chunks";
			}
			// Loading the chunks round the start generates the pieces; the start's bounding box then
			// says where the (randomly rotated) piece actually stands.
			for (int cx = -2; cx <= 2; cx++) {
				for (int cz = -2; cz <= 2; cz++) {
					end.getChunk((start.getX() >> 4) + cx, (start.getZ() >> 4) + cz);
				}
			}
			var starts = end.structureManager().startsForStructure(start.getX() >> 4, start.getZ() >> 4, observatory.value());
			if (starts.isEmpty() || !starts.get(0).isValid()) {
				return "no structure start at " + start;
			}
			var box = starts.get(0).getBoundingBox();
			int lecterns = 0;
			int chests = 0;
			int airUnderBase = 0;
			int baseBlocks = 0;
			int baseY = Integer.MAX_VALUE;
			for (int x = box.minX(); x <= box.maxX(); x++) {
				for (int z = box.minZ(); z <= box.maxZ(); z++) {
					for (int y = box.minY(); y <= box.maxY(); y++) {
						var state = end.getBlockState(new BlockPos(x, y, z));
						if (state.is(Blocks.LECTERN)) {
							lecterns++;
						} else if (state.is(Blocks.CHEST)) {
							chests++;
						} else if (state.is(Blocks.END_STONE_BRICKS)) {
							baseY = Math.min(baseY, y);
						}
					}
				}
			}
			if (baseY != Integer.MAX_VALUE) {
				for (int x = box.minX(); x <= box.maxX(); x++) {
					for (int z = box.minZ(); z <= box.maxZ(); z++) {
						if (end.getBlockState(new BlockPos(x, baseY, z)).is(Blocks.END_STONE_BRICKS)) {
							baseBlocks++;
							if (end.getBlockState(new BlockPos(x, baseY - 1, z)).isAir()) {
								airUnderBase++;
							}
						}
					}
				}
			}
			return "box from " + box.minX() + "," + box.minY() + "," + box.minZ() + " to " + box.maxX() + "," + box.maxY() + "," + box.maxZ()
					+ ", base y " + baseY + ", " + baseBlocks + " base blocks, " + airUnderBase + " over air, " + lecterns + " lectern(s), " + chests + " chest(s)";
		});
		BeyondTheDragon.LOGGER.info("[test] observatory: {}", report);
		check(!report.startsWith("none") && !report.startsWith("no structure"), "an observatory generates within 120 chunks of the End's centre (" + report + ")");
		if (report.startsWith("none") || report.startsWith("no structure")) {
			return;
		}
		check(report.contains("1 lectern(s)") && !report.contains(" 0 chest(s)"), "the observatory has its lectern and chests (" + report + ")");
		check(report.matches(".*base y \\d+.*") && !report.contains(" 0 base blocks"), "the observatory's base was found (" + report + ")");
		int overAir = airUnderBase(report);
		check(overAir >= 0 && overAir <= 8, "the observatory stands on land, not the void (" + report + ")");
		restoration(ctx, server);
	}

	/** Puts back every missing block, which should restore the observatory and hand out the chart. */
	private void restoration(ClientGameTestContext ctx, TestServerContext server) {
		String result = server.computeOnServer(s -> {
			ServerLevel end = s.getLevel(Level.END);
			ServerPlayer player = player(s);
			StructureStart start = nearestObservatory(end);
			PoolElementStructurePiece piece = Observatories.ruinPiece(start);
			if (piece == null) {
				return "no ruin piece";
			}
			List<BlockPos> missing = Observatories.missingBlocks(end, piece);
			int before = missing.size();
			if (before == 0) {
				return "nothing missing";
			}
			// Stand by the lectern so the restored title and the chart reach a real player.
			BlockPos lectern = Observatories.worldPos(piece, new BlockPos(8, 13, 13));
			player.setGameMode(GameType.CREATIVE);
			player.teleportTo(end, lectern.getX() + 1.5, lectern.getY(), lectern.getZ() + 1.5, java.util.Set.of(), 0, 0, false);
			player.getInventory().clearContent();
			// Put back all but one by hand, then the last one through the mod's own hook.
			for (int i = 0; i < missing.size() - 1; i++) {
				end.setBlockAndUpdate(missing.get(i), Observatories.expectedBlock(piece, missing.get(i)).defaultBlockState());
			}
			boolean earlyRestored = ObservatoryData.get(end).isRestored(Observatories.startKey(start));
			Observatories.onBlockPlaced(end, missing.get(0), player);
			boolean stillUnrestored = !ObservatoryData.get(end).isRestored(Observatories.startKey(start));
			BlockPos last = missing.get(missing.size() - 1);
			end.setBlockAndUpdate(last, Observatories.expectedBlock(piece, last).defaultBlockState());
			Observatories.onBlockPlaced(end, last, player);
			boolean restored = ObservatoryData.get(end).isRestored(Observatories.startKey(start));
			int charts = 0;
			int markers = 0;
			for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
				if (stack.getItem() == Items.FILLED_MAP) {
					charts++;
					// Target markers ride on the item until the map is carried and updates.
					var decorations = stack.get(DataComponents.MAP_DECORATIONS);
					markers += decorations == null ? 0 : decorations.decorations().size();
				}
			}
			return before + " missing; early " + earlyRestored + "; one short " + (stillUnrestored ? "unrestored" : "RESTORED") + "; last " + (restored ? "restored" : "NOT restored")
					+ "; charts " + charts + "; markers " + markers + "; lectern " + lectern;
		});
		BeyondTheDragon.LOGGER.info("[test] restoration: {}", result);
		check(result.contains("early false") && result.contains("one short unrestored"), "an observatory one block short is not restored (" + result + ")");
		check(result.contains("last restored"), "the last missing block restores the observatory (" + result + ")");
		check(result.matches(".*charts [1-9].*") && !result.contains("markers 0"), "restoring hands out the keeper's chart with markers on it (" + result + ")");
		respawn(ctx, server);
	}

	/** Dying in the End after a restoration comes back at the observatory's lectern. */
	private void respawn(ClientGameTestContext ctx, TestServerContext server) {
		BlockPos lectern = server.computeOnServer(s -> {
			ServerLevel end = s.getLevel(Level.END);
			var restored = ObservatoryData.get(end).restored();
			return restored.isEmpty() ? null : restored.get(0).lectern();
		});
		if (lectern == null) {
			check(false, "a restored observatory is on record for the respawn check");
			return;
		}
		server.runOnServer(s -> {
			ServerLevel end = s.getLevel(Level.END);
			ServerPlayer player = player(s);
			player.setPermanentlyInvulnerable(false);
			player.setGameMode(GameType.SURVIVAL);
			// Die a good way off, so coming back at the lectern can't be an accident.
			player.teleportTo(end, lectern.getX() + 40.5, lectern.getY() + 10, lectern.getZ() + 40.5, java.util.Set.of(), 0, 0, false);
			player.kill(end);
		});
		ctx.waitForScreen(DeathScreen.class);
		ctx.clickScreenButton("deathScreen.respawn");
		ctx.waitTicks(60);
		String where = server.computeOnServer(s -> {
			ServerPlayer player = player(s);
			player.setPermanentlyInvulnerable(true);
			return player.level().dimension().identifier() + " " + player.blockPosition() + " (lectern " + lectern + ")";
		});
		double distance = server.computeOnServer(s -> player(s).blockPosition().distSqr(lectern.above()));
		check(where.contains("the_end") && distance <= 9, "a death in the End respawns at the restored observatory's lectern (" + where + ")");
	}

	private static StructureStart nearestObservatory(ServerLevel end) {
		Structure structure = Observatories.structure(end);
		BlockPos start = end.findNearestMapStructure(HolderSet.direct(end.registryAccess().lookupOrThrow(Registries.STRUCTURE)
				.getOrThrow(Observatories.OBSERVATORY)), new BlockPos(0, 64, 0), 120, false);
		return end.structureManager().startsForStructure(start.getX() >> 4, start.getZ() >> 4, structure).get(0);
	}

	private static ServerPlayer player(MinecraftServer s) {
		return s.getPlayerList().getPlayers().get(0);
	}

	private static int airUnderBase(String report) {
		var m = java.util.regex.Pattern.compile("(\\d+) over air").matcher(report);
		return m.find() ? Integer.parseInt(m.group(1)) : -1;
	}

	private void check(boolean condition, String what) {
		(condition ? ok : failed).accept(what);
	}
}
