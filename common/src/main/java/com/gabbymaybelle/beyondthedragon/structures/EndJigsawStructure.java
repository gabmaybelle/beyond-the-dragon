package com.gabbymaybelle.beyondthedragon.structures;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pools.DimensionPadding;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasLookup;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;

/**
 * A jigsaw structure that only starts on solid End ground. A plain {@code minecraft:jigsaw}
 * happily projects its start onto a heightmap of 0 where there is no island, and then the
 * structure sits over the void; this type samples the surface on a three-by-three grid over the
 * {@code footprint} first, the way End Cities check their corners in code. {@code min_surface_y}
 * is the lowest surface height that counts as an island (vanilla End land is y 55 to 75 or so),
 * {@code max_slope} is how far the highest and lowest samples may differ (the templates' end
 * stone plinth covers that much of a drop), and {@code min_distance} keeps the structure that
 * many blocks from the End's centre. The biomes are the {@code #minecraft:is_end} tag, so
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
			Codec.intRange(0, 64).optionalFieldOf("footprint", 16).forGetter(s -> s.footprint),
			Codec.intRange(0, 64).optionalFieldOf("max_slope", 6).forGetter(s -> s.maxSlope),
			Codec.intRange(0, 100000).optionalFieldOf("min_distance", 0).forGetter(s -> s.minDistance)
	).apply(instance, EndJigsawStructure::new));
	public static final StructureType<EndJigsawStructure> TYPE = () -> CODEC;

	private final Holder<StructureTemplatePool> startPool;
	private final int size;
	private final HeightProvider startHeight;
	private final int maxDistanceFromCenter;
	private final int minSurfaceY;
	private final int footprint;
	private final int maxSlope;
	private final int minDistance;

	public EndJigsawStructure(StructureSettings settings, Holder<StructureTemplatePool> startPool, int size, HeightProvider startHeight,
			int maxDistanceFromCenter, int minSurfaceY, int footprint, int maxSlope, int minDistance) {
		super(settings);
		this.startPool = startPool;
		this.size = size;
		this.startHeight = startHeight;
		this.maxDistanceFromCenter = maxDistanceFromCenter;
		this.minSurfaceY = minSurfaceY;
		this.footprint = footprint;
		this.maxSlope = maxSlope;
		this.minDistance = minDistance;
	}

	@Override
	protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
		int x = context.chunkPos().getMinBlockX();
		int z = context.chunkPos().getMinBlockZ();
		// Not near the centre: the dragon's island and its surroundings stay as vanilla made them.
		if (Math.sqrt((double) x * x + (double) z * z) < minDistance) {
			return Optional.empty();
		}
		// The whole footprint must stand on an island, and on nearly level ground: nine samples,
		// corners, edges and middle.
		int half = footprint / 2;
		int lowest = Integer.MAX_VALUE;
		int highest = Integer.MIN_VALUE;
		for (int i = 0; i <= 2; i++) {
			for (int j = 0; j <= 2; j++) {
				int surface = context.chunkGenerator().getFirstOccupiedHeight(x + i * half, z + j * half, Heightmap.Types.WORLD_SURFACE_WG,
						context.heightAccessor(), context.randomState());
				lowest = Math.min(lowest, surface);
				highest = Math.max(highest, surface);
			}
		}
		if (lowest < minSurfaceY || highest - lowest > maxSlope) {
			return Optional.empty();
		}
		BlockPos start = new BlockPos(x, startHeight.sample(context.random(), new net.minecraft.world.level.levelgen.WorldGenerationContext(
				context.chunkGenerator(), context.heightAccessor())), z);
		return JigsawPlacement.addPieces(context, startPool, Optional.empty(), size, start, false,
				Optional.of(Heightmap.Types.WORLD_SURFACE_WG), new JigsawStructure.MaxDistance(maxDistanceFromCenter),
				PoolAliasLookup.EMPTY, DimensionPadding.ZERO, LiquidSettings.IGNORE_WATERLOGGING);
	}

	@Override
	public StructureType<?> type() {
		return TYPE;
	}
}
