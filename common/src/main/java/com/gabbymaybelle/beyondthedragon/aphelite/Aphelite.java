package com.gabbymaybelle.beyondthedragon.aphelite;

import com.gabbymaybelle.beyondthedragon.BeyondTheDragon;
import com.gabbymaybelle.beyondthedragon.platform.Services;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SmithingTemplateItem;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * Aphelite: a metal a step past netherite that only exists far out in the End. Shards come from
 * aphelite ore (end stone past the outer ring) and far-ring loot; four shards and four popped
 * chorus fruit make an ingot; an ingot, an upgrade template and a netherite piece make the
 * aphelite piece at a smithing table. The numbers sit just above netherite: the identity is
 * in the perks ({@link AphelitePerks}), not the stats.
 */
public final class Aphelite {
	public static final TagKey<Item> TOOL_MATERIALS = TagKey.create(Registries.ITEM, id("aphelite_tool_materials"));
	public static final TagKey<Item> REPAIRS_ARMOR = TagKey.create(Registries.ITEM, id("repairs_aphelite_armor"));
	public static final ResourceKey<EquipmentAsset> EQUIPMENT = ResourceKey.create(EquipmentAssets.ROOT_ID, id("aphelite"));

	/** Netherite is 2031 durability, speed 9, +4 damage, enchantability 15. */
	public static final ToolMaterial TOOL = new ToolMaterial(BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 2400, 10.0F, 4.5F, 18, TOOL_MATERIALS);
	/** Netherite is durability x37, toughness 3, enchantability 15; the armour points match. */
	public static final ArmorMaterial ARMOR = new ArmorMaterial(42, defense(3, 6, 8, 3, 19), 18,
			SoundEvents.ARMOR_EQUIP_NETHERITE, 4.0F, 0.1F, REPAIRS_ARMOR, EQUIPMENT);

	public static final Supplier<Item> SHARD = item("aphelite_shard", Item::new);
	public static final Supplier<Item> INGOT = item("aphelite_ingot", props -> new Item(props.fireResistant()));
	public static final Supplier<Item> HELMET = armor("aphelite_helmet", ArmorType.HELMET);
	public static final Supplier<Item> CHESTPLATE = armor("aphelite_chestplate", ArmorType.CHESTPLATE);
	public static final Supplier<Item> LEGGINGS = armor("aphelite_leggings", ArmorType.LEGGINGS);
	public static final Supplier<Item> BOOTS = armor("aphelite_boots", ArmorType.BOOTS);
	public static final Supplier<Item> SWORD = item("aphelite_sword", props -> new Item(props.sword(TOOL, 3.0F, -2.4F).fireResistant()));
	public static final Supplier<Item> PICKAXE = item("aphelite_pickaxe", props -> new Item(props.pickaxe(TOOL, 1.0F, -2.8F).fireResistant()));
	public static final Supplier<Item> AXE = item("aphelite_axe", props -> new Item(props.axe(TOOL, 5.0F, -3.0F).fireResistant()));
	public static final Supplier<Item> SHOVEL = item("aphelite_shovel", props -> new Item(props.shovel(TOOL, 1.5F, -3.0F).fireResistant()));
	public static final Supplier<Item> HOE = item("aphelite_hoe", props -> new Item(props.hoe(TOOL, -4.0F, 0.0F).fireResistant()));
	public static final Supplier<Item> UPGRADE_TEMPLATE = item("aphelite_upgrade_smithing_template", props -> new SmithingTemplateItem(
			Component.translatable("item.beyond_the_dragon.smithing_template.aphelite_upgrade.applies_to"),
			Component.translatable("item.beyond_the_dragon.smithing_template.aphelite_upgrade.ingredients"),
			Component.translatable("item.beyond_the_dragon.smithing_template.aphelite_upgrade.base_slot_description"),
			Component.translatable("item.beyond_the_dragon.smithing_template.aphelite_upgrade.additions_slot_description"),
			List.of(slot("helmet"), slot("chestplate"), slot("leggings"), slot("boots"), slot("sword"), slot("pickaxe"), slot("axe"), slot("hoe"), slot("shovel")),
			List.of(slot("ingot")),
			props.rarity(Rarity.RARE)));
	public static final Supplier<Item> DRAGON_SCALE_TEMPLATE = item("dragon_scale_armor_trim_smithing_template",
			props -> SmithingTemplateItem.createArmorTrimTemplate(props.rarity(Rarity.RARE)));

	public static final ResourceKey<Block> ORE_KEY = ResourceKey.create(Registries.BLOCK, id("aphelite_ore"));
	public static final Supplier<Block> ORE = Services.PLATFORM.register(Registries.BLOCK, id("aphelite_ore"),
			() -> new DropExperienceBlock(UniformInt.of(3, 7), BlockBehaviour.Properties.of().setId(ORE_KEY)
					.strength(3.0F, 9.0F).requiresCorrectToolForDrops().sound(SoundType.STONE)));
	public static final Supplier<Item> ORE_ITEM = item("aphelite_ore", props -> new BlockItem(ORE.get(), props.useBlockDescriptionPrefix()));

	public static final ResourceKey<PlacedFeature> ORE_FEATURE = ResourceKey.create(Registries.PLACED_FEATURE, id("aphelite_ore"));

	private Aphelite() {
	}

	/** Touching the class registers everything; this puts the items in the creative tabs. */
	public static void register() {
		tab("ingredients", SHARD, INGOT, UPGRADE_TEMPLATE, DRAGON_SCALE_TEMPLATE);
		tab("combat", HELMET, CHESTPLATE, LEGGINGS, BOOTS, SWORD);
		tab("tools_and_utilities", PICKAXE, AXE, SHOVEL, HOE);
		tab("natural_blocks", ORE_ITEM);
		InRingPlacement.register();
		Services.PLATFORM.addOreToEndBiomes(ORE_FEATURE);
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(BeyondTheDragon.MOD_ID, path);
	}

	private static Supplier<Item> item(String name, Function<Item.Properties, Item> factory) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id(name));
		return Services.PLATFORM.register(Registries.ITEM, id(name), () -> factory.apply(new Item.Properties().setId(key)));
	}

	private static Supplier<Item> armor(String name, ArmorType type) {
		return item(name, props -> new Item(props.humanoidArmor(ARMOR, type).fireResistant()));
	}

	@SafeVarargs
	private static void tab(String vanillaTab, Supplier<Item>... items) {
		ResourceKey<CreativeModeTab> tab = ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace(vanillaTab));
		for (Supplier<Item> item : items) {
			Services.PLATFORM.addToCreativeTab(tab, item);
		}
	}

	private static Map<ArmorType, Integer> defense(int boots, int leggings, int chestplate, int helmet, int body) {
		return new EnumMap<>(Map.of(ArmorType.BOOTS, boots, ArmorType.LEGGINGS, leggings, ArmorType.CHESTPLATE, chestplate,
				ArmorType.HELMET, helmet, ArmorType.BODY, body));
	}

	private static Identifier slot(String name) {
		return Identifier.withDefaultNamespace("container/slot/" + name);
	}
}
