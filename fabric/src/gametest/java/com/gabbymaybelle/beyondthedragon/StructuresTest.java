package com.gabbymaybelle.beyondthedragon;

import com.gabbymaybelle.beyondthedragon.aphelite.Aphelite;
import com.gabbymaybelle.beyondthedragon.structures.EndJigsawStructure;
import com.gabbymaybelle.beyondthedragon.structures.Observatories;
import com.gabbymaybelle.beyondthedragon.structures.ObservatoryData;
import java.util.List;
import java.util.function.Consumer;
import com.gabbymaybelle.beyondthedragon.harness.ClientGameTestContext;
import com.gabbymaybelle.beyondthedragon.harness.TestServerContext;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

/**
 * The Outer Reaches structures in a real game: the nearest ruin of each kind to the End's centre
 * is found by the game's own locator and stands on land (there is island under its floor, not
 * just its own plinth); each kind's placement yields starts often enough across the seed; the
 * observatory has its lectern and chests, putting back every missing block restores it, which
 * hands out the keeper's chart with markers on it; a death in the End comes back at its
 * lectern, and a death at home does not.
 */
final class StructuresTest {
	private final Consumer<String> ok;
	private final Consumer<String> failed;

	StructuresTest(Consumer<String> ok, Consumer<String> failed) {
		this.ok = ok;
		this.failed = failed;
	}

	private static final List<String> KINDS = List.of("observatory", "wreck", "graveyard", "sinking_city");

	void run(ClientGameTestContext ctx, TestServerContext server) {
		for (String kind : KINDS) {
			String report = server.computeOnServer(s -> footing(s.getLevel(Level.END), kind));
			BeyondTheDragon.LOGGER.info("[test] {}: {}", kind, report);
			check(!report.startsWith("none") && !report.startsWith("no structure"), "a " + kind + " generates within 120 chunks of the End's centre (" + report + ")");
			if (report.startsWith("none") || report.startsWith("no structure")) {
				continue;
			}
			check(report.matches(".*floor y -?\\d+.*"), "the " + kind + "'s floor was found (" + report + ")");
			check(groundBlocks(report) >= 200, "the " + kind + " stands on land, not the void (" + report + ")");
			if (kind.equals("observatory")) {
				check(report.contains("1 lectern(s)") && !report.contains(" 0 chest(s)"), "the observatory has its lectern and chests (" + report + ")");
				check(report.contains("(1 with its book)"), "the observatory's lectern holds its book, with pages (" + report + ")");
			}
			tour(ctx, server, kind, report);
			found(ctx, server, kind, report);
		}
		placementYield(ctx, server);
		endCityOverlap(ctx, server);
		lore(server);
		restoration(ctx, server);
	}

	private static final java.util.Map<String, String> FOUND_ADVANCEMENT = java.util.Map.of(
			"observatory", "ruins", "wreck", "captains_log", "graveyard", "bone_garden", "sinking_city", "the_slip");

	/** Standing inside the ruin grants its advancement (vanilla's location trigger, checked every second). */
	private void found(ClientGameTestContext ctx, TestServerContext server, String kind, String report) {
		var m = java.util.regex.Pattern.compile("box from (-?\\d+),(-?\\d+),(-?\\d+) to (-?\\d+),(-?\\d+),(-?\\d+)").matcher(report);
		if (!m.find()) {
			return;
		}
		double cx = (Integer.parseInt(m.group(1)) + Integer.parseInt(m.group(4))) / 2.0;
		double cy = (Integer.parseInt(m.group(2)) + Integer.parseInt(m.group(5))) / 2.0;
		double cz = (Integer.parseInt(m.group(3)) + Integer.parseInt(m.group(6))) / 2.0;
		server.runOnServer(s -> player(s).teleportTo(s.getLevel(Level.END), cx, cy, cz, java.util.Set.of(), 0, 0));
		ctx.waitTicks(45);
		// With the player there and the chunks ticking, which blocks are the chests now? (Lootr
		// swaps loot chests for its own once a player is near; vanilla keeps minecraft:chest.)
		String chests = server.computeOnServer(s -> {
			ServerLevel end = s.getLevel(Level.END);
			java.util.Map<String, Integer> ids = new java.util.TreeMap<>();
			for (int x = Integer.parseInt(m.group(1)); x <= Integer.parseInt(m.group(4)); x++) {
				for (int y = Integer.parseInt(m.group(2)); y <= Integer.parseInt(m.group(5)); y++) {
					for (int z = Integer.parseInt(m.group(3)); z <= Integer.parseInt(m.group(6)); z++) {
						String id = BuiltInRegistries.BLOCK.getKey(end.getBlockState(new BlockPos(x, y, z)).getBlock()).toString();
						if (id.contains("chest")) {
							ids.merge(id, 1, Integer::sum);
						}
					}
				}
			}
			return ids.toString();
		});
		BeyondTheDragon.LOGGER.info("[test] {} chests with the player there: {}", kind, chests);
		String path = FOUND_ADVANCEMENT.get(kind);
		check(BeyondTheDragonClientTest.advancementDone(server, path), "standing in the " + kind + " grants " + path);
	}

	private static final java.util.Map<String, String> LORE_TITLE = java.util.Map.of(
			"observatory", "The Keeper's Letter", "wreck", "Captain's Log", "graveyard", "Scratched on Bone", "sinking_city", "A Resident's Note");

	/** Every ruin's chest loot carries its note: the tables are rolled on the server, no chest needed. */
	private void lore(TestServerContext server) {
		for (String kind : KINDS) {
			String title = server.computeOnServer(s -> {
				ServerLevel end = s.getLevel(Level.END);
				var table = s.reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, Aphelite.id("chests/" + kind)));
				var params = new LootParams.Builder(end).withParameter(LootContextParams.ORIGIN, Vec3.ZERO).create(LootContextParamSets.CHEST);
				for (ItemStack stack : table.getRandomItems(params)) {
					if (stack.getItem() == Items.WRITTEN_BOOK) {
						var content = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
						return content == null ? "(no content)" : content.title().raw();
					}
				}
				return "(no book)";
			});
			check(LORE_TITLE.get(kind).equals(title), "the " + kind + "'s chests carry '" + LORE_TITLE.get(kind) + "' (got " + title + ")");
		}
	}

	/** A look at the ruin from the air, for the eyes: the screenshot is what the design reviews are made from. */
	private static void tour(ClientGameTestContext ctx, TestServerContext server, String kind, String report) {
		var m = java.util.regex.Pattern.compile("box from (-?\\d+),(-?\\d+),(-?\\d+) to (-?\\d+),(-?\\d+),(-?\\d+)").matcher(report);
		if (!m.find()) {
			return;
		}
		int[] b = new int[6];
		for (int i = 0; i < 6; i++) {
			b[i] = Integer.parseInt(m.group(i + 1));
		}
		double cx = (b[0] + b[3]) / 2.0;
		double cy = (b[1] + b[4]) / 2.0;
		double cz = (b[2] + b[5]) / 2.0;
		int span = Math.max(b[3] - b[0], b[5] - b[2]);
		// From the south-west, above, far enough back to take the whole box in.
		double distance = span * 0.65 + 10;
		double ex = cx - distance * 0.7071;
		double ez = cz + distance * 0.7071;
		double wantedY = b[4] + span * 0.3 + 4;
		server.runOnServer(s -> {
			ServerLevel end = s.getLevel(Level.END);
			// Above the ground between the viewpoint and the ruin too: a modded End can have a ridge there.
			double ey = wantedY;
			for (int i = 0; i <= 4; i++) {
				int sx = (int) (ex + (cx - ex) * i / 4);
				int sz = (int) (ez + (cz - ez) * i / 4);
				end.getChunk(sx >> 4, sz >> 4);
				ey = Math.max(ey, end.getHeight(Heightmap.Types.MOTION_BLOCKING, sx, sz) + 6);
			}
			double dx = cx - ex;
			double dz = cz - ez;
			double dy = cy - ey;
			float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
			float pitch = (float) Math.toDegrees(-Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
			ServerPlayer player = player(s);
			player.setGameMode(GameType.SPECTATOR);
			player.teleportTo(end, ex, ey, ez, java.util.Set.of(), yaw, pitch);
		});
		// The chunks have to reach the client and render (a modded End's generation can be slow);
		// the picture is taken without the HUD (titles, chat, toasts) and with the gamma up, since
		// the End is dim.
		int chunkX = (int) cx >> 4;
		int chunkZ = (int) cz >> 4;
		try {
			ctx.waitFor(mc -> mc.level != null && mc.level.hasChunk(chunkX, chunkZ), 2400);
		} catch (AssertionError slow) {
			BeyondTheDragon.LOGGER.warn("[test] the {}'s chunk never reached the client; its picture may be empty", kind);
		}
		ctx.waitTicks(45);
		double gamma = ctx.computeOnClient(mc -> mc.options.gamma().get());
		ctx.runOnClient(mc -> {
			mc.options.hideGui = true;
			mc.options.gamma().set(1.0);
		});
		ctx.waitTicks(5);
		BeyondTheDragonClientTest.screenshot(ctx, "beyond_the_dragon_" + kind);
		ctx.runOnClient(mc -> {
			mc.options.hideGui = false;
			mc.options.gamma().set(gamma);
		});
	}

	/**
	 * Columns of chunks per server call in the two seed scans below. Fabric's test keeps the client
	 * in step with the server, so one call that scans all 501 columns froze the test window for
	 * minutes on a heavy modded End (and a window closed by hand ends the run); a tick between
	 * slices keeps it drawing.
	 */
	private static final int SLICE = 25;

	/**
	 * How often each kind's placement actually yields a start, asked of the seed alone the way
	 * the locator does (no chunks loaded): every candidate chunk of the structure set within
	 * 4,000 blocks of the centre, against the surface and slope checks. A kind that passes too
	 * rarely is one players would never find.
	 */
	private void placementYield(ClientGameTestContext ctx, TestServerContext server) {
		StringBuilder out = new StringBuilder();
		for (String kind : KINDS) {
			int[] total = new int[4];
			for (int x0 = -250; x0 <= 250; x0 += SLICE) {
				int from = x0;
				int to = Math.min(x0 + SLICE - 1, 250);
				int[] part = server.computeOnServer(s -> yieldSlice(s, kind, from, to));
				for (int i = 0; i < 4; i++) {
					total[i] += part[i];
				}
				ctx.waitTicks(1);
			}
			out.append(kind).append(' ').append(total[1]).append(" of ").append(total[0])
					.append(" (void ").append(total[2]).append(", steep ").append(total[3]).append("); ");
			BeyondTheDragon.LOGGER.info("[test] yield so far: {}", out);
		}
		String report = out.toString();
		BeyondTheDragon.LOGGER.info("[test] yield within 4000 blocks: {}", report);
		for (String kind : KINDS) {
			var m = java.util.regex.Pattern.compile(kind + " (\\d+) of (\\d+)").matcher(report);
			int starts = m.find() ? Integer.parseInt(m.group(1)) : -1;
			check(starts >= 5, "at least five " + kind + " starts within 4000 blocks of the centre (" + report + ")");
		}
	}

	/** Candidates, starts, and rejections for void and for slope, for one kind in chunk columns fromX to toX. */
	private static int[] yieldSlice(MinecraftServer s, String kind, int fromX, int toX) {
		ServerLevel end = s.getLevel(Level.END);
		var state = end.getChunkSource().getGeneratorState();
		var generator = end.getChunkSource().getGenerator();
		Holder<Structure> holder = end.registryAccess().registryOrThrow(Registries.STRUCTURE)
				.getHolderOrThrow(ResourceKey.create(Registries.STRUCTURE, Aphelite.id(kind)));
		EndJigsawStructure structure = (EndJigsawStructure) holder.value();
		int[] counts = new int[4];
		for (StructurePlacement placement : state.getPlacementsForStructure(holder)) {
			for (int cx = fromX; cx <= toX; cx++) {
				for (int cz = -250; cz <= 250; cz++) {
					if (!placement.isStructureChunk(state, cx, cz)) {
						continue;
					}
					counts[0]++;
					var context = new Structure.GenerationContext(end.registryAccess(), generator, generator.getBiomeSource(), state.randomState(),
							s.getStructureManager(), end.getSeed(), new ChunkPos(cx, cz), end, holder.value().biomes()::contains);
					if (holder.value().findValidGenerationPoint(context).isPresent()) {
						counts[1]++;
					} else {
						var surface = structure.surface(context);
						if (surface.isEmpty() || surface.get().lowest() < structure.minSurfaceY()) {
							counts[2]++;
						} else if (surface.get().highest() - surface.get().lowest() > structure.maxSlope()) {
							counts[3]++;
						}
					}
				}
			}
		}
		return counts;
	}

	/**
	 * No ruin overlaps a vanilla End City. Every End City and every ruin within 4,000 blocks of the
	 * centre is built from the seed alone, the way chunk generation would build it (no chunks
	 * loaded), and their pieces' boxes are compared. The structure sets keep a ruin two chunks from
	 * a city's start chunk, but a city sprawls further than that, so this is measured, not assumed.
	 */
	private void endCityOverlap(ClientGameTestContext ctx, TestServerContext server) {
		List<StructureStart> cities = startsSliced(ctx, server,
				s -> s.getLevel(Level.END).registryAccess().registryOrThrow(Registries.STRUCTURE).getHolderOrThrow(BuiltinStructures.END_CITY));
		int ruins = 0;
		int overlaps = 0;
		StringBuilder where = new StringBuilder();
		List<StructureStart> placed = new java.util.ArrayList<>();
		List<String> placedKinds = new java.util.ArrayList<>();
		int ruinOverlaps = 0;
		int closest = Integer.MAX_VALUE;
		int reach = 0;
		for (StructureStart city : cities) {
			BoundingBox box = city.getBoundingBox();
			ChunkPos at = city.getChunkPos();
			reach = Math.max(reach, Math.max(Math.max(at.x - (box.minX() >> 4), (box.maxX() >> 4) - at.x),
					Math.max(at.z - (box.minZ() >> 4), (box.maxZ() >> 4) - at.z)));
		}
		for (String kind : KINDS) {
			for (StructureStart ruin : startsSliced(ctx, server, s -> s.getLevel(Level.END).registryAccess().registryOrThrow(Registries.STRUCTURE)
					.getHolderOrThrow(ResourceKey.create(Registries.STRUCTURE, Aphelite.id(kind))))) {
				ruins++;
				BoundingBox box = ruin.getBoundingBox();
				// Ruins of different kinds don't overlap each other either.
				for (int i = 0; i < placed.size(); i++) {
					if (placed.get(i).getBoundingBox().intersects(box)) {
						ruinOverlaps++;
						where.append(' ').append(kind).append(" in a ").append(placedKinds.get(i)).append(" at ").append(box.getCenter().toShortString());
					}
				}
				placed.add(ruin);
				placedKinds.add(kind);
				for (StructureStart city : cities) {
					closest = Math.min(closest, gap(box, city.getBoundingBox()));
					if (!box.intersects(city.getBoundingBox())) {
						continue;
					}
					boolean touching = false;
					for (StructurePiece a : ruin.getPieces()) {
						for (StructurePiece b : city.getPieces()) {
							touching |= a.getBoundingBox().intersects(b.getBoundingBox());
						}
					}
					if (touching) {
						overlaps++;
						where.append(' ').append(kind).append(" at ").append(box.getCenter().toShortString());
					}
				}
			}
		}
		String report = cities.size() + " End Cities, " + ruins + " ruins, " + overlaps + " overlap(s), " + ruinOverlaps + " ruin(s) in another ruin" + where
				+ "; closest boxes " + closest + " blocks apart; cities reach " + reach + " chunks from their start";
		BeyondTheDragon.LOGGER.info("[test] End City overlap within 4000 blocks: {}", report);
		check(overlaps == 0 && !cities.isEmpty(), "no ruin overlaps an End City within 4000 blocks (" + report + ")");
		check(ruinOverlaps == 0, "no ruin overlaps another ruin within 4000 blocks (" + report + ")");
	}

	/** Every start of a structure within 4,000 blocks of the centre, built from the seed a slice of columns at a time. */
	private static List<StructureStart> startsSliced(ClientGameTestContext ctx, TestServerContext server,
			java.util.function.Function<MinecraftServer, Holder<Structure>> structure) {
		List<StructureStart> out = new java.util.ArrayList<>();
		for (int x0 = -250; x0 <= 250; x0 += SLICE) {
			int from = x0;
			int to = Math.min(x0 + SLICE - 1, 250);
			out.addAll(server.computeOnServer(s -> starts(s, s.getLevel(Level.END), structure.apply(s), from, to)));
			ctx.waitTicks(1);
		}
		return out;
	}

	/** Every start of a structure in chunk columns fromX to toX (rows -250 to 250), built from the seed alone. */
	private static List<StructureStart> starts(MinecraftServer s, ServerLevel end, Holder<Structure> holder, int fromX, int toX) {
		var state = end.getChunkSource().getGeneratorState();
		var generator = end.getChunkSource().getGenerator();
		List<StructureStart> out = new java.util.ArrayList<>();
		for (StructurePlacement placement : state.getPlacementsForStructure(holder)) {
			for (int cx = fromX; cx <= toX; cx++) {
				for (int cz = -250; cz <= 250; cz++) {
					if (!placement.isStructureChunk(state, cx, cz)) {
						continue;
					}
					StructureStart start = holder.value().generate(end.registryAccess(), generator, generator.getBiomeSource(),
							state.randomState(), s.getStructureManager(), end.getSeed(), new ChunkPos(cx, cz), 0, end, holder.value().biomes()::contains);
					if (start.isValid()) {
						out.add(start);
					}
				}
			}
		}
		return out;
	}

	/** The horizontal gap between two boxes, in blocks (0 when they touch or overlap). */
	private static int gap(BoundingBox a, BoundingBox b) {
		int dx = Math.max(0, Math.max(a.minX() - b.maxX(), b.minX() - a.maxX()));
		int dz = Math.max(0, Math.max(a.minZ() - b.maxZ(), b.minZ() - a.maxZ()));
		return Math.max(dx, dz);
	}

	/** Where the nearest ruin of a kind stands and what is under it; the observatory's lectern and chests are counted too. */
	private static String footing(ServerLevel end, String kind) {
		Holder<Structure> structure = end.registryAccess().registryOrThrow(Registries.STRUCTURE)
				.getHolderOrThrow(ResourceKey.create(Registries.STRUCTURE, Aphelite.id(kind)));
		var nearest = end.getChunkSource().getGenerator().findNearestMapStructure(end, HolderSet.direct(structure), new BlockPos(0, 64, 0), 120, false);
		if (nearest == null) {
			return "none within 120 chunks";
		}
		BlockPos start = nearest.getFirst();
		// Loading the start's chunk creates the pieces; the start's bounding box then says where the
		// (randomly rotated) template actually stands, and loading every chunk under it places it.
		end.getChunk(start.getX() >> 4, start.getZ() >> 4);
		var starts = end.structureManager().startsForStructure(SectionPos.of(start.getX() >> 4, 0, start.getZ() >> 4), structure.value());
		if (starts.isEmpty() || !starts.get(0).isValid()) {
			return "no structure start at " + start;
		}
		// The start's box is inflated by twelve for terrain adaptation; the piece's is the template's.
		var box = starts.get(0).getPieces().get(0).getBoundingBox();
		for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
			for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
				end.getChunk(cx, cz);
			}
		}
		// The building's columns are the ones with End City masonry in them, and its floor is the
		// lowest row with any. Under the floor the template carries its plinth (an eroded ledge of
		// end stone, up to ten rows), and the island carries the rest: what's solid below the floor
		// beyond the plinth's own blocks is ground, and a ruin over the void has next to none.
		int lecterns = 0;
		int books = 0;
		int chests = 0;
		String chestBlock = "";
		int floorY = Integer.MAX_VALUE;
		for (int x = box.minX(); x <= box.maxX(); x++) {
			for (int z = box.minZ(); z <= box.maxZ(); z++) {
				for (int y = box.minY(); y <= box.maxY(); y++) {
					var state = end.getBlockState(new BlockPos(x, y, z));
					if (state.is(Blocks.LECTERN)) {
						lecterns++;
						// The book must have loaded: one the game can't read is dropped, leaving the lectern empty.
						if (end.getBlockEntity(new BlockPos(x, y, z)) instanceof net.minecraft.world.level.block.entity.LecternBlockEntity lectern
								&& lectern.hasBook()) {
							var content = lectern.getBook().get(net.minecraft.core.component.DataComponents.WRITTEN_BOOK_CONTENT);
							if (content != null && !content.pages().isEmpty()) {
								books++;
							}
						}
					} else if (state.is(Blocks.CHEST) || BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().contains("chest")) {
						chests++; // Lootr swaps loot chests for its own block; the report names it
						chestBlock = state.is(Blocks.CHEST) ? "" : " [" + BuiltInRegistries.BLOCK.getKey(state.getBlock()) + "]";
					} else if (state.is(Blocks.END_STONE_BRICKS) || state.is(Blocks.PURPUR_BLOCK) || state.is(Blocks.PURPUR_PILLAR) || state.is(Blocks.BONE_BLOCK)) {
						floorY = Math.min(floorY, y);
					}
				}
			}
		}
		if (floorY == Integer.MAX_VALUE) {
			return "box from " + box.minX() + "," + box.minY() + "," + box.minZ() + " to " + box.maxX() + "," + box.maxY() + "," + box.maxZ() + ", no masonry";
		}
		int solid = 0;
		for (int x = box.minX(); x <= box.maxX(); x++) {
			for (int z = box.minZ(); z <= box.maxZ(); z++) {
				for (int y = box.minY(); y < floorY; y++) {
					if (!end.getBlockState(new BlockPos(x, y, z)).isAir()) {
						solid++;
					}
				}
			}
		}
		// The template's own blocks below its floor (the piece sits with its origin at the box's bottom).
		StructureTemplate template = end.getServer().getStructureManager()
				.get(Aphelite.id(kind.equals("observatory") ? "observatory_ruin" : kind)).orElseThrow();
		int templateFloor = floorY - box.minY();
		int plinth = 0;
		for (var info : template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), Blocks.END_STONE)) {
			if (info.pos().getY() < templateFloor) {
				plinth++;
			}
		}
		return "box from " + box.minX() + "," + box.minY() + "," + box.minZ() + " to " + box.maxX() + "," + box.maxY() + "," + box.maxZ()
				+ ", floor y " + floorY + ", " + Math.max(0, solid - plinth) + " blocks of ground under the floor (plinth " + plinth + "), "
				+ lecterns + " lectern(s) (" + books + " with its book), " + chests + " chest(s)" + chestBlock;
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
			BlockPos lectern = Observatories.worldPos(piece, Observatories.plannedLectern());
			player.setGameMode(GameType.CREATIVE);
			player.teleportTo(end, lectern.getX() + 1.5, lectern.getY(), lectern.getZ() + 1.5, java.util.Set.of(), 0, 0);
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
			for (ItemStack stack : player.getInventory().items) {
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
		check(BeyondTheDragonClientTest.advancementDone(server, "keepers_eye") && BeyondTheDragonClientTest.advancementDone(server, "keepers_chart"),
				"restoring grants Keeper's Eye and Keeper's Chart");
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
		// Die a good way off, so coming back at the lectern can't be an accident; the client gets
		// a moment to arrive first, or a death during a dimension change shows no death screen.
		server.runOnServer(s -> {
			ServerLevel end = s.getLevel(Level.END);
			ServerPlayer player = player(s);
			player.setGameMode(GameType.CREATIVE);
			player.teleportTo(end, lectern.getX() + 40.5, lectern.getY() + 10, lectern.getZ() + 40.5, java.util.Set.of(), 0, 0);
		});
		ctx.waitTicks(40);
		server.runOnServer(s -> {
			ServerPlayer player = player(s);
			player.setInvulnerable(false);
			player.setGameMode(GameType.SURVIVAL);
			player.kill();
		});
		ctx.waitForScreen(DeathScreen.class);
		ctx.clickScreenButton("deathScreen.respawn");
		ctx.waitTicks(60);
		String where = server.computeOnServer(s -> {
			ServerPlayer player = player(s);
			player.setInvulnerable(true);
			return player.level().dimension().location() + " " + player.blockPosition() + " (lectern " + lectern + ")";
		});
		double distance = server.computeOnServer(s -> player(s).blockPosition().distSqr(lectern.above()));
		check(where.contains("the_end") && distance <= 9, "a death in the End respawns at the restored observatory's lectern (" + where + ")");

		// The same death at home must be vanilla's: back at the world spawn, not in the End.
		server.runOnServer(s -> {
			ServerPlayer player = player(s);
			player.setGameMode(GameType.CREATIVE);
			player.teleportTo(s.overworld(), 0.5, 200, 0.5, java.util.Set.of(), 0, 0);
		});
		ctx.waitTicks(40);
		server.runOnServer(s -> {
			ServerPlayer player = player(s);
			player.setInvulnerable(false);
			player.setGameMode(GameType.SURVIVAL);
			player.kill();
		});
		ctx.waitForScreen(DeathScreen.class);
		ctx.clickScreenButton("deathScreen.respawn");
		ctx.waitTicks(60);
		String home = server.computeOnServer(s -> {
			ServerPlayer player = player(s);
			player.setInvulnerable(true);
			return player.level().dimension().location() + " " + player.blockPosition();
		});
		check(home.contains("overworld"), "a death in the Overworld respawns at home, not at an observatory (" + home + ")");
	}

	private static StructureStart nearestObservatory(ServerLevel end) {
		Structure structure = Observatories.structure(end);
		BlockPos start = end.getChunkSource().getGenerator().findNearestMapStructure(end, HolderSet.direct(end.registryAccess().registryOrThrow(Registries.STRUCTURE)
				.getHolderOrThrow(Observatories.OBSERVATORY)), new BlockPos(0, 64, 0), 120, false).getFirst();
		return end.structureManager().startsForStructure(SectionPos.of(start.getX() >> 4, 0, start.getZ() >> 4), structure).get(0);
	}

	private static ServerPlayer player(MinecraftServer s) {
		return s.getPlayerList().getPlayers().get(0);
	}

	private static int groundBlocks(String report) {
		var m = java.util.regex.Pattern.compile("(\\d+) blocks of ground").matcher(report);
		return m.find() ? Integer.parseInt(m.group(1)) : -1;
	}

	private void check(boolean condition, String what) {
		(condition ? ok : failed).accept(what);
	}
}
