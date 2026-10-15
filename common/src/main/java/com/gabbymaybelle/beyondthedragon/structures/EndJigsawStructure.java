package com.gabbymaybelle.beyondthedragon.structures;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pools.DimensionPadding;
import net.minecraft.world.level.levelgen.structure.pools.EmptyPoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasLookup;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * A jigsaw structure that only starts on solid End ground. A plain {@code minecraft:jigsaw}
 * happily projects its start onto a heightmap of 0 where there is no island, and then the
 * structure sits over the void; this type samples the surface on a three-by-three grid over the
 * start template's footing first, the way End Cities check their corners in code. The footing
 * is the whole template's box, or the part of it named by {@code footing} ({@code [x1, z1, x2,
 * z2]}, template coordinates) for a ruin meant to hang over an edge; it turns with the
 * template's rotation, which the jigsaw placement draws first from the context's random and
 * which is peeked here from a copy of that random. The template's height comes from the surface
 * at the footing's centre (not vanilla's projection at the whole box's centre, which could be
 * over the void). {@code min_surface_y} is the lowest surface height that counts as an island
 * (vanilla End land is y 55 to 75 or so), {@code max_slope} is how far the highest and lowest
 * samples may differ (the templates' end stone plinth covers that much of a drop), and
 * {@code min_distance} keeps the structure that many blocks from the End's centre, and
 * {@code keep_out_of} lists structure sets (End Cities, and our other ruins in a fixed order) whose
 * structures a start must not reach into (see {@link #inTheWay}). The biomes are the {@code #minecraft:is_end} tag, so
 * modded End biomes count too; the surface and distance checks do the rest, and they matter
 * there most: {@code beard_box} terrain adaptation does nothing on an End whose density
 * functions it can't outweigh (Stellarity's, for one).
 */
public final class EndJigsawStructure extends Structure {
	public static final MapCodec<EndJigsawStructure> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			settingsCodec(instance),
			StructureTemplatePool.CODEC.fieldOf("start_pool").forGetter(s -> s.startPool),
			Codec.intRange(0, 20).fieldOf("size").forGetter(s -> s.size),
			HeightProvider.CODEC.fieldOf("start_height").forGetter(s -> s.startHeight),
			Codec.intRange(0, 128).fieldOf("max_distance_from_center").forGetter(s -> s.maxDistanceFromCenter),
			Codec.intRange(0, 320).optionalFieldOf("min_surface_y", 50).forGetter(s -> s.minSurfaceY),
			Codec.intRange(0, 64).optionalFieldOf("max_slope", 6).forGetter(s -> s.maxSlope),
			Codec.INT.listOf(4, 4).optionalFieldOf("footing", List.of()).forGetter(s -> s.footing),
			Codec.intRange(0, 100000).optionalFieldOf("min_distance", 0).forGetter(s -> s.minDistance),
			ResourceKey.codec(Registries.STRUCTURE_SET).listOf().optionalFieldOf("keep_out_of", List.of()).forGetter(s -> s.keepOutOf)
	).apply(instance, EndJigsawStructure::new));
	public static final StructureType<EndJigsawStructure> TYPE = () -> CODEC;

	private final Holder<StructureTemplatePool> startPool;
	private final int size;
	private final HeightProvider startHeight;
	private final int maxDistanceFromCenter;
	private final int minSurfaceY;
	private final int maxSlope;
	private final List<Integer> footing;
	private final int minDistance;
	private final List<ResourceKey<StructureSet>> keepOutOf;

	public EndJigsawStructure(StructureSettings settings, Holder<StructureTemplatePool> startPool, int size, HeightProvider startHeight,
			int maxDistanceFromCenter, int minSurfaceY, int maxSlope, List<Integer> footing, int minDistance, List<ResourceKey<StructureSet>> keepOutOf) {
		super(settings);
		this.startPool = startPool;
		this.size = size;
		this.startHeight = startHeight;
		this.maxDistanceFromCenter = maxDistanceFromCenter;
		this.minSurfaceY = minSurfaceY;
		this.maxSlope = maxSlope;
		this.footing = footing;
		this.minDistance = minDistance;
		this.keepOutOf = keepOutOf;
	}

	/**
	 * The box the start template would occupy in a chunk, the footing within it that must be on
	 * land, the lowest and highest surface under that footing, and the first free height at the
	 * footing's centre (where the template's bottom goes, before {@code start_height}).
	 */
	public record Surface(BoundingBox box, BoundingBox footing, int lowest, int highest, int free) {
	}

	public int minSurfaceY() {
		return minSurfaceY;
	}

	public int maxSlope() {
		return maxSlope;
	}

	/**
	 * Where the template would really stand in this chunk and what the ground does under it, or
	 * empty if the start pool has nothing. The placement's first two draws from the context's
	 * random are the rotation and the start element, so the same draws from a copy of that random
	 * (nothing here touches the original before the placement does) give the box; the surface is
	 * then sampled on a three-by-three grid over it: corners, edges and the middle, which is where
	 * the placement reads the height it projects the template to.
	 */
	public Optional<Surface> surface(GenerationContext context) {
		int x = context.chunkPos().getMinBlockX();
		int z = context.chunkPos().getMinBlockZ();
		WorldgenRandom peek = new WorldgenRandom(new LegacyRandomSource(0L));
		peek.setLargeFeatureSeed(context.seed(), context.chunkPos().x(), context.chunkPos().z());
		Rotation rotation = Rotation.getRandom(peek);
		StructurePoolElement element = startPool.value().getRandomTemplate(peek);
		if (element == EmptyPoolElement.INSTANCE) {
			return Optional.empty();
		}
		BoundingBox box = element.getBoundingBox(context.structureTemplateManager(), new BlockPos(x, 0, z), rotation);
		BoundingBox feet = box;
		if (footing.size() == 4) {
			// The footing turns with the template, about the same corner the template turns about.
			BlockPos a = StructureTemplate.transform(new BlockPos(footing.get(0), 0, footing.get(1)), Mirror.NONE, rotation, BlockPos.ZERO).offset(x, 0, z);
			BlockPos b = StructureTemplate.transform(new BlockPos(footing.get(2), 0, footing.get(3)), Mirror.NONE, rotation, BlockPos.ZERO).offset(x, 0, z);
			feet = BoundingBox.fromCorners(a, b);
		}
		int lowest = Integer.MAX_VALUE;
		int highest = Integer.MIN_VALUE;
		for (int i = 0; i <= 2; i++) {
			for (int j = 0; j <= 2; j++) {
				int sampleX = feet.minX() + (feet.maxX() - feet.minX()) * i / 2;
				int sampleZ = feet.minZ() + (feet.maxZ() - feet.minZ()) * j / 2;
				int surface = context.chunkGenerator().getFirstOccupiedHeight(sampleX, sampleZ, Heightmap.Types.WORLD_SURFACE_WG,
						context.heightAccessor(), context.randomState());
				lowest = Math.min(lowest, surface);
				highest = Math.max(highest, surface);
			}
		}
		int free = context.chunkGenerator().getFirstFreeHeight((feet.minX() + feet.maxX()) / 2, (feet.minZ() + feet.maxZ()) / 2,
				Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
		return Optional.of(new Surface(box, feet, lowest, highest, free));
	}

	@Override
	protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
		return generationPoint(context, true);
	}

	/**
	 * Where this ruin would stand in the context's chunk, or empty. Without {@code keepOut}, only the
	 * terrain decides: that is how another ruin asks whether this one is in its way, so the
	 * question never comes back round (the keep-out lists run in one direction).
	 */
	private Optional<GenerationStub> generationPoint(GenerationContext context, boolean keepOut) {
		int x = context.chunkPos().getMinBlockX();
		int z = context.chunkPos().getMinBlockZ();
		// Not near the centre: the dragon's island and its surroundings stay as vanilla made them.
		if (Math.sqrt((double) x * x + (double) z * z) < minDistance) {
			return Optional.empty();
		}
		// The whole box must stand on an island, and on nearly level ground.
		Optional<Surface> surface = surface(context);
		if (surface.isEmpty() || surface.get().lowest() < minSurfaceY || surface.get().highest() - surface.get().lowest() > maxSlope) {
			return Optional.empty();
		}
		if (keepOut && !keepOutOf.isEmpty() && inTheWay(context, surface.get().box())) {
			return Optional.empty();
		}
		// The template's bottom goes at the footing centre's first free block plus the start height
		// (negative: the plinth sinks in). A constant height never reads its random; a copy is
		// passed all the same so the context's own random reaches the placement untouched.
		int y = surface.get().free() + startHeight.sample(new WorldgenRandom(new LegacyRandomSource(0L)),
				new net.minecraft.world.level.levelgen.WorldGenerationContext(context.chunkGenerator(), context.heightAccessor()));
		return JigsawPlacement.addPieces(context, startPool, Optional.empty(), size, new BlockPos(x, y, z), false,
				Optional.empty(), new JigsawStructure.MaxDistance(maxDistanceFromCenter),
				PoolAliasLookup.EMPTY, DimensionPadding.ZERO, LiquidSettings.IGNORE_WATERLOGGING);
	}

	/** How far an End City's pieces reach from its start chunk, at most, with room to spare (eight or nine measured); our ruins reach less. */
	private static final int CITY_REACH_CHUNKS = 10;
	/** Blocks kept clear between a ruin's footprint and a city's pieces. */
	private static final int CITY_MARGIN = 4;

	/**
	 * Whether this ruin's footprint would reach into a structure of one of the {@code keep_out_of}
	 * sets that will really stand nearby. The footprint is the start template's box: the generator
	 * turns every jigsaw block in our templates to air, so a ruin is its start piece and nothing
	 * more. Each set's candidate chunk in reach (one per grid cell) is asked whether its structure
	 * starts there, with that structure's own terrain check (one of our ruins answers without its
	 * own keep-out list); only then are its pieces laid out, from the seed, and compared. A wider
	 * exclusion zone in our structure sets would keep clear of every chunk where a city might
	 * start, real or not: in the game test that cost up to a fifth of the ruins on Stellarity's
	 * dense End to prevent one overlap, and an exclusion zone can name only one other set.
	 */
	private boolean inTheWay(GenerationContext context, BoundingBox start) {
		for (ResourceKey<StructureSet> key : keepOutOf) {
			Optional<Holder.Reference<StructureSet>> set = context.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET).get(key);
			if (set.isPresent() && set.get().value().placement() instanceof RandomSpreadStructurePlacement spread && inTheWay(context, start, set.get().value(), spread)) {
				return true;
			}
		}
		return false;
	}

	private boolean inTheWay(GenerationContext context, BoundingBox start, StructureSet set, RandomSpreadStructurePlacement spread) {
		BoundingBox footprint = BoundingBox.fromCorners(
				new BlockPos(start.minX() - CITY_MARGIN, -2048, start.minZ() - CITY_MARGIN),
				new BlockPos(start.maxX() + CITY_MARGIN, 2048, start.maxZ() + CITY_MARGIN));
		ChunkPos here = context.chunkPos();
		int reach = CITY_REACH_CHUNKS + (Math.max(start.getXSpan(), start.getZSpan()) + CITY_MARGIN) / 16 + 1;
		int spacing = spread.spacing();
		for (int gx = Math.floorDiv(here.x() - reach, spacing); gx <= Math.floorDiv(here.x() + reach, spacing); gx++) {
			for (int gz = Math.floorDiv(here.z() - reach, spacing); gz <= Math.floorDiv(here.z() + reach, spacing); gz++) {
				ChunkPos city = spread.getPotentialStructureChunk(context.seed(), gx * spacing, gz * spacing);
				if (Math.max(Math.abs(city.x() - here.x()), Math.abs(city.z() - here.z())) > reach) {
					continue;
				}
				for (StructureSet.StructureSelectionEntry entry : set.structures()) {
					Structure structure = entry.structure().value();
					GenerationContext there = new GenerationContext(context.registryAccess(), context.chunkGenerator(), context.biomeSource(),
							context.randomState(), context.structureTemplateManager(), context.seed(), city,
							context.heightAccessor(), structure.biomes()::contains);
					Optional<GenerationStub> stub = structure instanceof EndJigsawStructure ruin ? ruin.generationPoint(there, false) : structure.findValidGenerationPoint(there);
					if (stub.isPresent() && stub.get().getPiecesBuilder().build().pieces().stream().anyMatch(piece -> piece.getBoundingBox().intersects(footprint))) {
						return true;
					}
				}
			}
		}
		return false;
	}

	@Override
	public StructureType<?> type() {
		return TYPE;
	}
}
