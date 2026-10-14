package com.gabbymaybelle.beyondthedragon.structures;

import com.gabbymaybelle.beyondthedragon.BeyondTheDragon;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** The observatories that have been restored, saved with the End: where each stands and where its lectern is. */
public final class ObservatoryData extends SavedData {
	/** A restored observatory: the structure's start corner (its identity) and its lectern (the respawn spot). */
	public record Restored(BlockPos start, BlockPos lectern) {
		static final Codec<Restored> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				BlockPos.CODEC.fieldOf("start").forGetter(Restored::start),
				BlockPos.CODEC.fieldOf("lectern").forGetter(Restored::lectern)
		).apply(instance, Restored::new));
	}

	private static final Codec<ObservatoryData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Restored.CODEC.listOf().fieldOf("restored").forGetter(data -> data.restored)
	).apply(instance, ObservatoryData::new));
	public static final SavedDataType<ObservatoryData> TYPE = new SavedDataType<>(
			Identifier.fromNamespaceAndPath(BeyondTheDragon.MOD_ID, "observatories"), ObservatoryData::new, CODEC, null);

	private final List<Restored> restored;

	public ObservatoryData() {
		this(List.of());
	}

	private ObservatoryData(List<Restored> restored) {
		this.restored = new ArrayList<>(restored);
	}

	public static ObservatoryData get(ServerLevel end) {
		return end.getDataStorage().computeIfAbsent(TYPE);
	}

	public List<Restored> restored() {
		return List.copyOf(restored);
	}

	public boolean isRestored(BlockPos start) {
		return restored.stream().anyMatch(r -> r.start().equals(start));
	}

	public Optional<Restored> byLectern(BlockPos lectern) {
		return restored.stream().filter(r -> r.lectern().equals(lectern)).findFirst();
	}

	public void add(Restored observatory) {
		restored.add(observatory);
		setDirty();
	}
}
