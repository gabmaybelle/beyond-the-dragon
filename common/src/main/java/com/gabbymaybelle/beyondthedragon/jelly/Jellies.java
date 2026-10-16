package com.gabbymaybelle.beyondthedragon.jelly;

import com.gabbymaybelle.beyondthedragon.BeyondTheDragon;
import com.gabbymaybelle.beyondthedragon.platform.Services;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.Vec3;

/**
 * The Void Jelly and what goes with it: the entity type, the chorus flower on a stick that
 * steers it, and the jelly egg it hatches from. Touching the class registers them.
 */
public final class Jellies {
	public static final ResourceKey<EntityType<?>> VOID_JELLY_KEY = ResourceKey.create(Registries.ENTITY_TYPE, id("void_jelly"));
	/** The happy ghast's size, seats and tracking range: the jelly is its body under a new skin. */
	public static final Supplier<EntityType<VoidJelly>> VOID_JELLY = entityType(() -> EntityType.Builder.of(VoidJelly::new, MobCategory.CREATURE)
			.sized(4.0F, 4.0F)
			.eyeHeight(2.6F)
			.passengerAttachments(new Vec3(0.0, 4.0, 1.7), new Vec3(-1.7, 4.0, 0.0), new Vec3(0.0, 4.0, -1.7), new Vec3(1.7, 4.0, 0.0))
			.ridingOffset(0.5F)
			.clientTrackingRange(10)
			.build(VOID_JELLY_KEY));

	/** Crafted like a carrot on a stick. It only steers: no boost, so it never wears out. */
	public static final Supplier<Item> CHORUS_STICK = Services.PLATFORM.register(Registries.ITEM, id("chorus_flower_on_a_stick"),
			() -> new Item(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id("chorus_flower_on_a_stick"))).stacksTo(1)));

	public static final ResourceKey<Block> EGG_KEY = ResourceKey.create(Registries.BLOCK, id("jelly_egg"));
	/** Soft and quick to break, like a dried ghast; random ticks restart it if chorus grows in later. */
	public static final Supplier<Block> EGG = Services.PLATFORM.register(Registries.BLOCK, id("jelly_egg"),
			() -> new JellyEggBlock(BlockBehaviour.Properties.of().setId(EGG_KEY).mapColor(MapColor.COLOR_CYAN)
					.strength(0.5F).sound(SoundType.FROGSPAWN).noOcclusion().randomTicks().lightLevel(state -> 3)));
	public static final Supplier<Item> EGG_ITEM = Services.PLATFORM.register(Registries.ITEM, id("jelly_egg"),
			() -> new BlockItem(EGG.get(), new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id("jelly_egg")))
					.useBlockDescriptionPrefix().rarity(Rarity.UNCOMMON)));

	private Jellies() {
	}

	public static void register() {
		Services.PLATFORM.registerAttributes(VOID_JELLY, VoidJelly::createAttributes);
		Services.PLATFORM.addCreatureToEndBiomes(VOID_JELLY);
		Services.PLATFORM.addToCreativeTab(ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("tools_and_utilities")), CHORUS_STICK);
		Services.PLATFORM.addToCreativeTab(ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("natural_blocks")), EGG_ITEM);
	}

	@SuppressWarnings("unchecked")
	private static <T extends net.minecraft.world.entity.Entity> Supplier<EntityType<T>> entityType(Supplier<EntityType<T>> factory) {
		Supplier<EntityType<?>> registered = Services.PLATFORM.register(Registries.ENTITY_TYPE, VOID_JELLY_KEY.identifier(), factory::get);
		return () -> (EntityType<T>) registered.get();
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(BeyondTheDragon.MOD_ID, path);
	}
}
