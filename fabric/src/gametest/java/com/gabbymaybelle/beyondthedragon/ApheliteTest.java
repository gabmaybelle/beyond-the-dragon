package com.gabbymaybelle.beyondthedragon;

import com.gabbymaybelle.beyondthedragon.aphelite.Aphelite;
import com.gabbymaybelle.beyondthedragon.rings.Rings;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

/**
 * Aphelite in a real game: every item and the ore exist; the ingot recipe and the smithing
 * upgrades resolve; the ore drops shards, more with an aphelite pickaxe; the ore only generates
 * past the outer ring; and the perks: shorter levitation per piece, no fall or crash damage in
 * the End with helmet, leggings and boots.
 */
final class ApheliteTest {
	private static final int ORE_SAMPLE = 40;

	private final Consumer<String> ok;
	private final Consumer<String> failed;

	ApheliteTest(Consumer<String> ok, Consumer<String> failed) {
		this.ok = ok;
		this.failed = failed;
	}

	void run(ClientGameTestContext ctx, TestServerContext server) {
		registry();
		recipes(server);
		oreDrops(ctx, server);
		oreGeneration(server);
		perks(server);
	}

	private void registry() {
		for (String name : List.of("aphelite_shard", "aphelite_ingot", "aphelite_helmet", "aphelite_chestplate", "aphelite_leggings",
				"aphelite_boots", "aphelite_sword", "aphelite_pickaxe", "aphelite_axe", "aphelite_shovel", "aphelite_hoe",
				"aphelite_upgrade_smithing_template", "dragon_scale_armor_trim_smithing_template", "aphelite_ore")) {
			check(BuiltInRegistries.ITEM.getOptional(ResourceKey.create(Registries.ITEM, Aphelite.id(name))).isPresent(), "item " + name + " is registered");
		}
		check(BuiltInRegistries.BLOCK.getOptional(Aphelite.ORE_KEY).isPresent(), "the ore block is registered");
	}

	private void recipes(TestServerContext server) {
		for (String recipe : List.of("aphelite_ingot", "aphelite_helmet_smithing", "aphelite_pickaxe_smithing",
				"aphelite_upgrade_smithing_template", "dragon_scale_armor_trim_smithing_template_smithing_trim")) {
			boolean present = server.computeOnServer(s -> s.getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, Aphelite.id(recipe))).isPresent());
			check(present, "recipe " + recipe + " loads");
		}
		ItemStack result = server.computeOnServer(s -> {
			SmithingRecipeInput input = new SmithingRecipeInput(new ItemStack(Aphelite.UPGRADE_TEMPLATE.get()),
					new ItemStack(Items.NETHERITE_HELMET), new ItemStack(Aphelite.INGOT.get()));
			Optional<RecipeHolder<SmithingRecipe>> holder = s.getRecipeManager().getRecipeFor(RecipeType.SMITHING, input, s.overworld());
			return holder.map(h -> h.value().assemble(input)).orElse(ItemStack.EMPTY);
		});
		check(result.getItem() == Aphelite.HELMET.get(), "template + netherite helmet + ingot smiths an aphelite helmet (got " + result + ")");
	}

	/** Breaks the ore with a netherite pickaxe, then an aphelite one: the second drops more shards. */
	private void oreDrops(ClientGameTestContext ctx, TestServerContext server) {
		int plain = mine(ctx, server, Items.NETHERITE_PICKAXE);
		int bonus = mine(ctx, server, Aphelite.PICKAXE.get());
		check(plain == ORE_SAMPLE, "a netherite pickaxe gets one shard per ore (got " + plain + " from " + ORE_SAMPLE + ")");
		check(bonus > ORE_SAMPLE, "an aphelite pickaxe gets bonus shards (got " + bonus + " from " + ORE_SAMPLE + ")");
	}

	private int mine(ClientGameTestContext ctx, TestServerContext server, Item pickaxe) {
		BlockPos base = new BlockPos(20, 120, 20);
		server.runOnServer(s -> {
			ServerLevel end = s.getLevel(Level.END);
			ServerPlayer player = player(s);
			// Stand beside the ore so its chunk ticks and the drops are visible to entity queries.
			player.setPermanentlyInvulnerable(true);
			player.setGameMode(GameType.SURVIVAL);
			end.setBlockAndUpdate(base.offset(-2, -1, -2), Blocks.OBSIDIAN.defaultBlockState());
			player.teleportTo(end, base.getX() - 1.5, base.getY(), base.getZ() - 1.5, java.util.Set.of(), 0, 0, false);
			player.getInventory().clearContent();
			player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(pickaxe));
			for (ItemEntity item : end.getEntitiesOfClass(ItemEntity.class, new AABB(base).inflate(16))) {
				item.discard();
			}
			for (int i = 0; i < ORE_SAMPLE; i++) {
				BlockPos pos = base.offset(i % 8, 0, i / 8);
				end.setBlockAndUpdate(pos, Aphelite.ORE.get().defaultBlockState());
				player.gameMode.destroyBlock(pos);
			}
		});
		ctx.waitTicks(10);
		return server.computeOnServer(s -> {
			int shards = 0;
			for (ItemEntity item : s.getLevel(Level.END).getEntitiesOfClass(ItemEntity.class, new AABB(base).inflate(16))) {
				if (item.getItem().getItem() == Aphelite.SHARD.get()) {
					shards += item.getItem().getCount();
				}
				item.discard();
			}
			return shards;
		});
	}

	/** The inner ring has no ore; an outer-ring chunk with land has some. */
	private void oreGeneration(TestServerContext server) {
		int inner = server.computeOnServer(s -> countOre(s.getLevel(Level.END), 0, 2, 3, 3));
		check(inner == 0, "no aphelite ore in the inner ring (" + inner + " found)");
		// Land is sparse that far out: walk outward along eight directions until enough end stone
		// has been seen, then expect ore among it.
		int outerStart = Config.ringOuter / 16 + 2;
		int[] outer = server.computeOnServer(s -> {
			ServerLevel end = s.getLevel(Level.END);
			int endStone = 0;
			int ore = 0;
			int[][] directions = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}, {1, 1}, {-1, 1}, {1, -1}, {-1, -1}};
			for (int step = 0; step < 24 && endStone < 2000; step++) {
				for (int[] direction : directions) {
					int cx = direction[0] * (outerStart + step);
					int cz = direction[1] * (outerStart + step);
					LevelChunk chunk = end.getChunk(cx, cz);
					for (int x = 0; x < 16; x++) {
						for (int z = 0; z < 16; z++) {
							for (int y = 0; y < 120; y++) {
								var state = chunk.getBlockState(new BlockPos(x, y, z));
								if (state.is(Blocks.END_STONE)) {
									endStone++;
								} else if (state.is(Aphelite.ORE.get())) {
									ore++;
								}
							}
						}
					}
				}
			}
			return new int[] {endStone, ore};
		});
		check(outer[0] >= 2000, "found End land in the outer ring to look at (" + outer[0] + " end stone)");
		check(outer[1] > 0, "aphelite ore generates in the outer ring (" + outer[1] + " ore among " + outer[0] + " end stone)");
		check(Rings.at(outerStart * 16, 0) == Rings.OUTER, "the sampled chunks are in the outer ring");
	}

	private static int countOre(ServerLevel end, int fromChunkX, int fromChunkZ, int chunksX, int chunksZ) {
		int ore = 0;
		for (int cx = fromChunkX; cx < fromChunkX + chunksX; cx++) {
			for (int cz = fromChunkZ; cz < fromChunkZ + chunksZ; cz++) {
				LevelChunk chunk = end.getChunk(cx, cz);
				for (int x = 0; x < 16; x++) {
					for (int z = 0; z < 16; z++) {
						for (int y = 0; y < 120; y++) {
							if (chunk.getBlockState(new BlockPos(x, y, z)).is(Aphelite.ORE.get())) {
								ore++;
							}
						}
					}
				}
			}
		}
		return ore;
	}

	private void perks(TestServerContext server) {
		// Levitation from a shulker: 200 ticks bare, 80 in a full set (four pieces, 15% each).
		int bare = levitation(server, 0);
		int full = levitation(server, 4);
		check(bare == 200, "without aphelite a shulker's levitation lasts 200 ticks (got " + bare + ")");
		check(full == 80, "in a full aphelite set it lasts 80 ticks (got " + full + ")");

		// Fall and crash damage in the End: cancelled with helmet, leggings and boots; not with two
		// pieces. The player is made vulnerable for this so the damage would really land.
		float[] landing = server.computeOnServer(s -> {
			ServerLevel end = s.getLevel(Level.END);
			ServerPlayer player = player(s);
			player.setPermanentlyInvulnerable(false);
			player.setHealth(20.0F);
			wear(player, 4);
			boolean fallHurt = player.hurtServer(end, end.damageSources().fall(), 6.0F);
			boolean crashHurt = player.hurtServer(end, end.damageSources().flyIntoWall(), 6.0F);
			float afterSet = player.getHealth();
			wear(player, 2);
			player.setInvulnerableTime(0);
			boolean twoPiecesHurt = player.hurtServer(end, end.damageSources().fall(), 6.0F);
			float afterTwo = player.getHealth();
			player.setHealth(20.0F);
			player.setPermanentlyInvulnerable(true);
			wear(player, 0);
			return new float[] {fallHurt ? 1 : 0, crashHurt ? 1 : 0, afterSet, twoPiecesHurt ? 1 : 0, afterTwo};
		});
		check(landing[0] == 0 && landing[1] == 0 && landing[2] == 20.0F,
				"falls and elytra crashes in the End do nothing in helmet, leggings and boots (health " + landing[2] + ")");
		check(landing[3] == 1 && landing[4] < 20.0F,
				"two pieces don't make a safe landing (a fall took health to " + landing[4] + ")");
	}

	private static int levitation(TestServerContext server, int pieces) {
		return server.computeOnServer(s -> {
			ServerLevel end = s.getLevel(Level.END);
			ServerPlayer player = player(s);
			player.removeAllEffects();
			wear(player, pieces);
			Shulker shulker = EntityTypes.SHULKER.create(end, EntitySpawnReason.COMMAND);
			player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 200), shulker);
			MobEffectInstance effect = player.getEffect(MobEffects.LEVITATION);
			int duration = effect == null ? 0 : effect.getDuration();
			player.removeAllEffects();
			return duration;
		});
	}

	private static void wear(ServerPlayer player, int pieces) {
		List<Supplier<Item>> set = List.of(Aphelite.HELMET, Aphelite.LEGGINGS, Aphelite.BOOTS, Aphelite.CHESTPLATE);
		EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.CHEST};
		for (int i = 0; i < 4; i++) {
			player.setItemSlot(slots[i], i < pieces ? new ItemStack(set.get(i).get()) : ItemStack.EMPTY);
		}
	}

	private static ServerPlayer player(MinecraftServer s) {
		return s.getPlayerList().getPlayers().get(0);
	}

	private void check(boolean condition, String what) {
		(condition ? ok : failed).accept(what);
	}
}
