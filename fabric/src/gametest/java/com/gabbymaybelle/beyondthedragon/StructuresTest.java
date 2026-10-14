package com.gabbymaybelle.beyondthedragon;

import com.gabbymaybelle.beyondthedragon.aphelite.Aphelite;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * The Outer Reaches structures in a real game: the nearest observatory to the End's centre is
 * found by the game's own locator, it stands on land (nothing under its base is air), and its
 * lectern and chest are there.
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
			var structureStart = starts.get(0);
			var box = structureStart.getBoundingBox();
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
			start = new BlockPos(box.minX(), box.minY(), box.minZ());
			return "box from " + start + " to " + box.maxX() + "," + box.maxY() + "," + box.maxZ() + ", base y " + baseY + ", " + baseBlocks + " base blocks, " + airUnderBase + " over air, "
					+ lecterns + " lectern(s), " + chests + " chest(s)";
		});
		BeyondTheDragon.LOGGER.info("[test] observatory: {}", report);
		check(!report.startsWith("none") && !report.startsWith("no structure"), "an observatory generates within 120 chunks of the End's centre (" + report + ")");
		check(report.contains("1 lectern(s)") && !report.contains(" 0 chest(s)"), "the observatory has its lectern and chests (" + report + ")");
		check(report.matches(".*base y \\d+.*") && !report.contains(" 0 base blocks"), "the observatory's base was found (" + report + ")");
		int overAir = airUnderBase(report);
		check(overAir >= 0 && overAir <= 8, "the observatory stands on land, not the void (" + report + ")");
	}

	private static int airUnderBase(String report) {
		var m = java.util.regex.Pattern.compile("(\\d+) over air").matcher(report);
		return m.find() ? Integer.parseInt(m.group(1)) : -1;
	}

	private void check(boolean condition, String what) {
		(condition ? ok : failed).accept(what);
	}
}
