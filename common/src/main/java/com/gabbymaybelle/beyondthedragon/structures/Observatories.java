package com.gabbymaybelle.beyondthedragon.structures;

import com.gabbymaybelle.beyondthedragon.Advancements;
import com.gabbymaybelle.beyondthedragon.BeyondTheDragon;
import com.gabbymaybelle.beyondthedragon.aphelite.Aphelite;
import com.gabbymaybelle.beyondthedragon.mixin.SinglePoolElementAccessor;
import com.gabbymaybelle.beyondthedragon.titles.Titles;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.Vec3;

/**
 * Abandoned observatories and what restoring one does. The ruin template is the intact one with
 * some blocks missing; {@code data/beyond_the_dragon/restoration/observatory.json} (written by
 * tools/generate-structures.js) lists those blocks. When a player places a block inside an
 * observatory, every missing block is checked at its real (rotated) position; once all are
 * back, the observatory is restored: it hands out the keeper's chart, a map marking the nearest
 * ruin of each kind and the nearest End City, from its lectern (sneak and use it), and sets a
 * charged respawn anchor in its top room (and a waystone, with Waystones installed). Respawn
 * anchors work inside a restored observatory, the one place in the End they do.
 */
public final class Observatories {
	public static final ResourceKey<Structure> OBSERVATORY = ResourceKey.create(Registries.STRUCTURE, Aphelite.id("observatory"));
	private static final ResourceLocation RUIN_TEMPLATE = Aphelite.id("observatory_ruin");
	private static final int CHART_SEARCH_CHUNKS = 100;

	/** A block the ruin is missing: where in the template, and what should be there. */
	private record Missing(BlockPos pos, Block block) {
	}

	/** The restoration plan, read once from the jar: what's missing, and where the lectern, anchor and waystone stand. */
	private record Plan(List<Missing> missing, BlockPos lectern, BlockPos anchor, BlockPos waystone) {
	}

	private static Plan plan;

	private Observatories() {
	}

	/** Where the plan puts the lectern, relative to the template. */
	public static BlockPos plannedLectern() {
		return plan().lectern();
	}

	/** Where restoring sets the respawn anchor, relative to the template. */
	public static BlockPos plannedAnchor() {
		return plan().anchor();
	}

	/** Where restoring sets the waystone (with Waystones installed), relative to the template. */
	public static BlockPos plannedWaystone() {
		return plan().waystone();
	}

	private static Plan plan() {
		if (plan == null) {
			try (Reader in = new InputStreamReader(Observatories.class.getResourceAsStream("/data/beyond_the_dragon/restoration/observatory.json"), StandardCharsets.UTF_8)) {
				JsonObject json = JsonParser.parseReader(in).getAsJsonObject();
				List<Missing> missing = new ArrayList<>();
				for (var element : json.getAsJsonArray("missing")) {
					JsonObject entry = element.getAsJsonObject();
					missing.add(new Missing(pos(entry.getAsJsonArray("pos")), BuiltInRegistries.BLOCK.get(ResourceLocation.parse(entry.get("block").getAsString()))));
				}
				plan = new Plan(List.copyOf(missing), pos(json.getAsJsonArray("lectern")), pos(json.getAsJsonArray("anchor")), pos(json.getAsJsonArray("waystone")));
			} catch (Exception e) {
				throw new IllegalStateException("The observatory restoration plan is missing from the jar", e);
			}
		}
		return plan;
	}

	private static BlockPos pos(JsonArray array) {
		return new BlockPos(array.get(0).getAsInt(), array.get(1).getAsInt(), array.get(2).getAsInt());
	}

	/** A block was placed by hand in the End: if it's inside an observatory, see whether that finished it. */
	public static void onBlockPlaced(ServerLevel level, BlockPos pos, Player player) {
		if (level.dimension() != Level.END) {
			return;
		}
		Structure structure = structure(level);
		StructureStart start = level.structureManager().getStructureAt(pos, structure);
		if (!start.isValid()) {
			return;
		}
		ObservatoryData data = ObservatoryData.get(level);
		BlockPos key = startKey(start);
		if (data.isRestored(key)) {
			return;
		}
		PoolElementStructurePiece piece = ruinPiece(start);
		if (piece == null || !missingBlocks(level, piece).isEmpty()) {
			return;
		}
		BlockPos lectern = worldPos(piece, plan().lectern());
		data.add(furnish(level, piece, new ObservatoryData.Restored(key, lectern, false, false), player));
		level.playSound(null, lectern, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 1.0F, 1.2F);
		for (ServerPlayer nearby : level.getPlayers(p -> p.distanceToSqr(Vec3.atCenterOf(lectern)) < 96 * 96)) {
			Titles.send(nearby, Component.translatable("title.beyond_the_dragon.observatory.restored"),
					Component.translatable("title.beyond_the_dragon.observatory.chart_hint"));
		}
		if (player instanceof ServerPlayer serverPlayer) {
			give(serverPlayer, chart(level, lectern));
			Advancements.grant(serverPlayer, "keepers_eye");
			Advancements.grant(serverPlayer, "keepers_chart");
		}
		BeyondTheDragon.LOGGER.info("An observatory was restored at {}", key);
	}

	/** The blocks of this observatory still missing, at their world positions (empty = restored). */
	public static List<BlockPos> missingBlocks(ServerLevel level, PoolElementStructurePiece piece) {
		List<BlockPos> missing = new ArrayList<>();
		for (Missing entry : plan().missing()) {
			BlockPos world = worldPos(piece, entry.pos());
			if (!level.getBlockState(world).is(entry.block())) {
				missing.add(world);
			}
		}
		return missing;
	}

	/** What a world position inside this observatory should be, by the plan, or null if the plan doesn't cover it. */
	public static Block expectedBlock(PoolElementStructurePiece piece, BlockPos world) {
		for (Missing entry : plan().missing()) {
			if (worldPos(piece, entry.pos()).equals(world)) {
				return entry.block();
			}
		}
		return null;
	}

	/** The observatory's one jigsaw piece (the ruin template), or null if this start isn't one. */
	public static PoolElementStructurePiece ruinPiece(StructureStart start) {
		for (StructurePiece piece : start.getPieces()) {
			if (piece instanceof PoolElementStructurePiece pool && pool.getElement() instanceof SinglePoolElement single
					&& RUIN_TEMPLATE.equals(((SinglePoolElementAccessor) single).beyondTheDragon$template().left().orElse(null))) {
				return pool;
			}
		}
		return null;
	}

	/** A template-relative position turned into the world position the piece put it at. */
	public static BlockPos worldPos(PoolElementStructurePiece piece, BlockPos relative) {
		// Jigsaw pieces never set a mirror; vanilla reads that as none.
		Mirror mirror = piece.getMirror() == null ? Mirror.NONE : piece.getMirror();
		return piece.getPosition().offset(StructureTemplate.transform(relative, mirror, piece.getRotation(), BlockPos.ZERO));
	}

	public static BlockPos startKey(StructureStart start) {
		return new BlockPos(start.getBoundingBox().minX(), start.getBoundingBox().minY(), start.getBoundingBox().minZ());
	}

	public static Structure structure(ServerLevel level) {
		return level.registryAccess().registryOrThrow(Registries.STRUCTURE).getOrThrow(OBSERVATORY);
	}

	/** Sneaking at a restored observatory's lectern: the keeper's chart. True when handled. */
	public static boolean onLecternUsed(ServerLevel level, BlockPos pos, Player player) {
		if (level.dimension() != Level.END || !player.isSecondaryUseActive() || !(player instanceof ServerPlayer serverPlayer)) {
			return false;
		}
		Optional<ObservatoryData.Restored> restored = ObservatoryData.get(level).byLectern(pos);
		if (restored.isEmpty()) {
			return false;
		}
		give(serverPlayer, chart(level, pos));
		Advancements.grant(serverPlayer, "keepers_chart");
		return true;
	}

	/**
	 * The keeper's chart: a map centred on the observatory marking the nearest ruin of each kind
	 * and the nearest End City, each found by the game's own locator within 100 chunks.
	 */
	public static ItemStack chart(ServerLevel level, BlockPos centre) {
		ItemStack map = MapItem.create(level, centre.getX(), centre.getZ(), (byte) 3, true, true);
		var structures = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
		for (String name : List.of("observatory", "wreck", "graveyard", "sinking_city")) {
			mark(level, map, centre, structures.getHolderOrThrow(ResourceKey.create(Registries.STRUCTURE, Aphelite.id(name))), name, true);
		}
		mark(level, map, centre, structures.getHolderOrThrow(ResourceKey.create(Registries.STRUCTURE, ResourceLocation.withDefaultNamespace("end_city"))), "end_city", false);
		MapItem.renderBiomePreviewMap(level, map);
		map.set(DataComponents.CUSTOM_NAME, Component.translatable("item.beyond_the_dragon.keepers_chart"));
		return map;
	}

	private static void mark(ServerLevel level, ItemStack map, BlockPos centre, Holder<Structure> structure, String name, boolean ours) {
		// 1.21.1's level has only the tag form of the locator; the generator's takes a holder set and answers with the structure too.
		var nearest = level.getChunkSource().getGenerator().findNearestMapStructure(level, HolderSet.direct(structure), centre, CHART_SEARCH_CHUNKS, false);
		BlockPos found = nearest == null ? null : nearest.getFirst();
		if (found != null) {
			MapItemSavedData.addTargetDecoration(map, found, BeyondTheDragon.MOD_ID + ":" + name, ours ? MapDecorationTypes.TARGET_X : MapDecorationTypes.RED_X);
		}
		BeyondTheDragon.LOGGER.info("Keeper's chart: nearest {} to {} is {}", name, centre, found == null ? "nowhere within " + CHART_SEARCH_CHUNKS + " chunks" : found);
	}

	private static void give(ServerPlayer player, ItemStack stack) {
		if (!player.addItem(stack)) {
			player.level().addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), stack));
		}
	}

	/**
	 * Sets what a restored observatory still lacks: its charged respawn anchor, and its waystone
	 * when Waystones is installed. Each goes only on an empty spot; one that is built over is
	 * left out (a player can still set their own anchor anywhere inside). Returns the record
	 * with its flags updated.
	 */
	private static ObservatoryData.Restored furnish(ServerLevel level, PoolElementStructurePiece piece, ObservatoryData.Restored observatory, Player player) {
		boolean anchor = observatory.anchor();
		if (!anchor) {
			BlockPos pos = worldPos(piece, plan().anchor());
			if (level.getBlockState(pos).canBeReplaced()) {
				level.setBlockAndUpdate(pos, Blocks.RESPAWN_ANCHOR.defaultBlockState().setValue(RespawnAnchorBlock.CHARGE, RespawnAnchorBlock.MAX_CHARGES));
				BeyondTheDragon.LOGGER.info("The observatory at {} has its respawn anchor at {}", observatory.start(), pos);
			} else {
				BeyondTheDragon.LOGGER.info("The observatory at {} has something built where its anchor goes ({}); left as it is", observatory.start(), pos);
			}
			anchor = true;
		}
		boolean waystone = observatory.waystone();
		if (!waystone && WaystonesCompat.available()) {
			BlockPos pos = worldPos(piece, plan().waystone());
			if (level.getBlockState(pos).canBeReplaced() && level.getBlockState(pos.above()).canBeReplaced()) {
				WaystonesCompat.place(level, pos, player instanceof ServerPlayer serverPlayer ? serverPlayer : null);
			}
			waystone = true;
		}
		return new ObservatoryData.Restored(observatory.start(), observatory.lectern(), anchor, waystone);
	}

	/**
	 * Every five seconds: restored observatories that are loaded but lack their anchor (restored
	 * before there were anchors) or their waystone (Waystones installed since) get them.
	 */
	public static void serverTick(MinecraftServer server) {
		if (server.getTickCount() % 100 != 0) {
			return;
		}
		ServerLevel end = server.getLevel(Level.END);
		if (end == null) {
			return;
		}
		ObservatoryData data = ObservatoryData.get(end);
		for (ObservatoryData.Restored observatory : data.restored()) {
			if ((observatory.anchor() && (observatory.waystone() || !WaystonesCompat.available())) || !end.isLoaded(observatory.lectern())) {
				continue;
			}
			StructureStart start = end.structureManager().getStructureAt(observatory.lectern(), structure(end));
			PoolElementStructurePiece piece = start.isValid() ? ruinPiece(start) : null;
			if (piece != null) {
				data.update(furnish(end, piece, observatory, null));
			}
		}
	}

	/** Whether a respawn anchor here works: inside a restored observatory in the End. */
	public static boolean anchorWorks(ServerLevel level, BlockPos pos) {
		if (level.dimension() != Level.END) {
			return false;
		}
		StructureStart start = level.structureManager().getStructureAt(pos, structure(level));
		return start.isValid() && ObservatoryData.get(level).isRestored(startKey(start));
	}
}
