package com.gabbymaybelle.beyondthedragon.ladder;

import com.gabbymaybelle.beyondthedragon.BeyondTheDragon;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The Dragon Ladder's state, saved with the End: whether the egg has been set on a beacon, the
 * tier the next (or current) dragon fights at, and which tiers have paid out their rewards.
 */
public final class LadderData extends SavedData {
	// 1.21.1 saves through NBT (26.x through a codec); the keys match, so a world moves between them.
	public static final SavedData.Factory<LadderData> TYPE = new SavedData.Factory<>(LadderData::new, LadderData::load, null);
	private static final String NAME = BeyondTheDragon.MOD_ID + "_dragon_ladder";

	private boolean unlocked;
	private int tier;
	/** A bit per tier that has already dropped its rewards. */
	private int rewarded;

	public LadderData() {
	}

	private LadderData(boolean unlocked, int tier, int rewarded) {
		this.unlocked = unlocked;
		this.tier = tier;
		this.rewarded = rewarded;
	}

	private static LadderData load(CompoundTag tag, HolderLookup.Provider registries) {
		return new LadderData(tag.getBoolean("unlocked"), tag.getInt("tier"), tag.getInt("rewarded"));
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		tag.putBoolean("unlocked", unlocked);
		tag.putInt("tier", tier);
		tag.putInt("rewarded", rewarded);
		return tag;
	}

	/** The ladder's state for the End; the End is where the dragon fight lives. */
	public static LadderData get(ServerLevel end) {
		return end.getDataStorage().computeIfAbsent(TYPE, NAME);
	}

	public boolean unlocked() {
		return unlocked;
	}

	public int tier() {
		return tier;
	}

	public void unlock() {
		unlocked = true;
		setDirty();
	}

	/** Moves to the next tier, up to {@link DragonLadder#MAX_TIER}; returns the new tier. */
	public int climb() {
		tier = Math.min(tier + 1, DragonLadder.MAX_TIER);
		setDirty();
		return tier;
	}

	public boolean rewarded(int tier) {
		return (rewarded & (1 << tier)) != 0;
	}

	public void markRewarded(int tier) {
		rewarded |= 1 << tier;
		setDirty();
	}
}
