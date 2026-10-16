package com.gabbymaybelle.beyondthedragon.jelly;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The jelly egg (Gabby chose the "Chorus pod" look: a pale teal egg in a cup of chorus petals),
 * a rare find in sinking-city chests. Placed beside chorus (a chorus plant or flower in the 3x3x3
 * around it), it moves through three stages, a step every {@link #STEP_TICKS}, and then hatches
 * into a baby Void Jelly that counts as hatched, so it can be harnessed and ridden once grown.
 * Away from chorus it waits. Vanilla's dried ghast, which revives in water, is the model.
 */
public class JellyEggBlock extends Block {
	public static final int MAX_STAGE = 3;
	public static final IntegerProperty STAGE = IntegerProperty.create("stage", 0, MAX_STAGE);
	/** A minute a step, so an egg by chorus hatches in about four minutes. */
	public static final int STEP_TICKS = 1200;
	private static final VoxelShape SHAPE = Block.column(10.0, 0.0, 12.0);

	public JellyEggBlock(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(STAGE, 0));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(STAGE);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	/** Whether a chorus plant or flower is in the 3x3x3 around the egg. */
	public static boolean nearChorus(LevelReader level, BlockPos pos) {
		for (BlockPos around : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
			BlockState state = level.getBlockState(around);
			if (state.is(Blocks.CHORUS_PLANT) || state.is(Blocks.CHORUS_FLOWER)) {
				return true;
			}
		}
		return false;
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);
		if (!level.isClientSide() && !oldState.is(this) && nearChorus(level, pos)) {
			level.scheduleTick(pos, this, STEP_TICKS);
		}
	}

	/** Chorus planted beside an egg that is already down starts it too. */
	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
			BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		if (neighbourState.is(Blocks.CHORUS_PLANT) || neighbourState.is(Blocks.CHORUS_FLOWER)) {
			ticks.scheduleTick(pos, this, STEP_TICKS);
		}
		return super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbourState, random);
	}

	/** A safety net for chorus that grew in diagonally (no neighbour update reaches the egg). */
	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (nearChorus(level, pos) && !level.getBlockTicks().hasScheduledTick(pos, this)) {
			level.scheduleTick(pos, this, STEP_TICKS);
		}
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!nearChorus(level, pos)) {
			return;
		}
		int stage = state.getValue(STAGE);
		if (stage < MAX_STAGE) {
			level.setBlock(pos, state.setValue(STAGE, stage + 1), Block.UPDATE_CLIENTS);
			level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(state));
			level.playSound(null, pos, SoundEvents.SNIFFER_EGG_CRACK, SoundSource.BLOCKS, 0.7F, 1.4F);
			level.scheduleTick(pos, this, STEP_TICKS);
		} else {
			hatch(level, pos);
		}
	}

	private static void hatch(ServerLevel level, BlockPos pos) {
		level.removeBlock(pos, false);
		VoidJelly baby = Jellies.VOID_JELLY.get().create(level, EntitySpawnReason.BREEDING);
		if (baby != null) {
			Vec3 at = Vec3.atBottomCenterOf(pos);
			baby.setBaby(true);
			baby.setHatched(true);
			baby.setPersistenceRequired();
			baby.snapTo(at.x(), at.y(), at.z(), level.getRandom().nextFloat() * 360.0F, 0.0F);
			level.addFreshEntity(baby);
			level.playSound(null, baby, SoundEvents.SNIFFER_EGG_HATCH, SoundSource.BLOCKS, 1.0F, 1.3F);
		}
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(8) <= state.getValue(STAGE) && nearChorus(level, pos)) {
			level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.5 + (random.nextFloat() - 0.5F) * 0.5, pos.getY() + 0.8,
					pos.getZ() + 0.5 + (random.nextFloat() - 0.5F) * 0.5, 0.0, 0.02, 0.0);
		}
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType type) {
		return false;
	}
}
