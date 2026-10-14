package com.gabbymaybelle.beyondthedragon.ladder;

import com.gabbymaybelle.beyondthedragon.BeyondTheDragon;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * The Dragon Ladder's state, saved with the End: whether the egg has been set on a beacon, the
 * tier the next (or current) dragon fights at, and which tiers have paid out their rewards.
 */
public final class LadderData extends SavedData {
	private static final Codec<LadderData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.BOOL.fieldOf("unlocked").forGetter(data -> data.unlocked),
			Codec.INT.fieldOf("tier").forGetter(data -> data.tier),
			Codec.INT.fieldOf("rewarded").forGetter(data -> data.rewarded)
	).apply(instance, LadderData::new));
	public static final SavedDataType<LadderData> TYPE = new SavedDataType<>(
			Identifier.fromNamespaceAndPath(BeyondTheDragon.MOD_ID, "dragon_ladder"), LadderData::new, CODEC, null);

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

	/** The ladder's state for the End; the End is where the dragon fight lives. */
	public static LadderData get(ServerLevel end) {
		return end.getDataStorage().computeIfAbsent(TYPE);
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
