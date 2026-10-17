package com.gabbymaybelle.beyondthedragon.structures;

import com.gabbymaybelle.beyondthedragon.BeyondTheDragon;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** The observatories that have been restored, saved with the End: where each stands and where its lectern is. */
public final class ObservatoryData extends SavedData {
	/**
	 * A restored observatory: the structure's start corner (its identity), its lectern (where the
	 * chart is read), and whether its respawn anchor and its waystone have been set. Observatories
	 * restored before there were anchors are saved without the two flags and read as false, so
	 * they get their anchor the next time they load.
	 */
	public record Restored(BlockPos start, BlockPos lectern, boolean anchor, boolean waystone) {
	}

	// 1.21.1 saves through NBT (26.x through a codec); the keys match, so a world moves between them.
	public static final SavedData.Factory<ObservatoryData> TYPE = new SavedData.Factory<>(ObservatoryData::new, ObservatoryData::load, null);
	private static final String NAME = BeyondTheDragon.MOD_ID + "_observatories";

	private final List<Restored> restored;

	public ObservatoryData() {
		this(List.of());
	}

	private ObservatoryData(List<Restored> restored) {
		this.restored = new ArrayList<>(restored);
	}

	private static ObservatoryData load(CompoundTag tag, HolderLookup.Provider registries) {
		List<Restored> restored = new ArrayList<>();
		for (Tag entry : tag.getList("restored", Tag.TAG_COMPOUND)) {
			CompoundTag compound = (CompoundTag) entry;
			restored.add(new Restored(NbtUtils.readBlockPos(compound, "start").orElseThrow(), NbtUtils.readBlockPos(compound, "lectern").orElseThrow(),
					compound.getBoolean("anchor"), compound.getBoolean("waystone")));
		}
		return new ObservatoryData(restored);
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		ListTag list = new ListTag();
		for (Restored observatory : restored) {
			CompoundTag compound = new CompoundTag();
			compound.put("start", NbtUtils.writeBlockPos(observatory.start()));
			compound.put("lectern", NbtUtils.writeBlockPos(observatory.lectern()));
			compound.putBoolean("anchor", observatory.anchor());
			compound.putBoolean("waystone", observatory.waystone());
			list.add(compound);
		}
		tag.put("restored", list);
		return tag;
	}

	public static ObservatoryData get(ServerLevel end) {
		return end.getDataStorage().computeIfAbsent(TYPE, NAME);
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
