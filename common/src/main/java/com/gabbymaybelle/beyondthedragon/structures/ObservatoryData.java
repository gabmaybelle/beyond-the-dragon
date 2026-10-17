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
	/**
	 * A restored observatory: the structure's start corner (its identity), its lectern (where the
	 * chart is read), and whether its respawn anchor and its waystone have been set. Observatories
	 * restored before there were anchors are saved without the two flags and read as false, so
	 * they get their anchor the next time they load.
	 */
	public record Restored(BlockPos start, BlockPos lectern, boolean anchor, boolean waystone) {
		static final Codec<Restored> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				BlockPos.CODEC.fieldOf("start").forGetter(Restored::start),
				BlockPos.CODEC.fieldOf("lectern").forGetter(Restored::lectern),
				Codec.BOOL.optionalFieldOf("anchor", false).forGetter(Restored::anchor),
				Codec.BOOL.optionalFieldOf("waystone", false).forGetter(Restored::waystone)
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

	/** Replaces the record of an observatory already on the list (found by its start). */
	public void update(Restored observatory) {
		restored.replaceAll(r -> r.start().equals(observatory.start()) ? observatory : r);
		setDirty();
	}
}
