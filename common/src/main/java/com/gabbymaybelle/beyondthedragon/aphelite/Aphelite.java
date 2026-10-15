package com.gabbymaybelle.beyondthedragon.aphelite;

import com.gabbymaybelle.beyondthedragon.BeyondTheDragon;
import com.gabbymaybelle.beyondthedragon.platform.Services;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SmithingTemplateItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.armortrim.TrimPattern;
import net.minecraft.world.item.crafting.Ingredient;
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
 *
 * <p>1.21.1 describes gear through a {@link Tier} and a registered {@link ArmorMaterial} with
 * the item classes of that version (26.x has them as item properties); the numbers are the same.
 */
public final class Aphelite {
	public static final TagKey<Item> TOOL_MATERIALS = TagKey.create(Registries.ITEM, id("aphelite_tool_materials"));
	public static final TagKey<Item> REPAIRS_ARMOR = TagKey.create(Registries.ITEM, id("repairs_aphelite_armor"));
	public static final ResourceKey<ArmorMaterial> ARMOR_KEY = ResourceKey.create(Registries.ARMOR_MATERIAL, id("aphelite"));
	public static final ResourceKey<TrimPattern> DRAGON_SCALE = ResourceKey.create(Registries.TRIM_PATTERN, id("dragon_scale"));

	/** Netherite is 2031 durability, speed 9, +4 damage, enchantability 15. */
	public static final Tier TOOL = new Tier() {
		@Override
		public int getUses() {
			return 2400;
		}

		@Override
		public float getSpeed() {
			return 10.0F;
		}

		@Override
		public float getAttackDamageBonus() {
			return 4.5F;
		}

		@Override
		public TagKey<Block> getIncorrectBlocksForDrops() {
			return BlockTags.INCORRECT_FOR_NETHERITE_TOOL;
		}

		@Override
		public int getEnchantmentValue() {
			return 18;
		}

		@Override
		public Ingredient getRepairIngredient() {
			return Ingredient.of(TOOL_MATERIALS);
		}
	};
	/** Netherite is durability x37, toughness 3, enchantability 15; the armour points match. */
	private static final int ARMOR_DURABILITY = 42;
	public static final Supplier<ArmorMaterial> ARMOR = Services.PLATFORM.register(Registries.ARMOR_MATERIAL, id("aphelite"),
			() -> new ArmorMaterial(defense(3, 6, 8, 3, 19), 18, SoundEvents.ARMOR_EQUIP_NETHERITE, () -> Ingredient.of(REPAIRS_ARMOR),
					List.of(new ArmorMaterial.Layer(id("aphelite"))), 4.0F, 0.1F));

	public static final Supplier<Item> SHARD = item("aphelite_shard", Item::new);
	public static final Supplier<Item> INGOT = item("aphelite_ingot", props -> new Item(props.fireResistant()));
	public static final Supplier<Item> HELMET = armor("aphelite_helmet", ArmorItem.Type.HELMET);
	public static final Supplier<Item> CHESTPLATE = armor("aphelite_chestplate", ArmorItem.Type.CHESTPLATE);
	public static final Supplier<Item> LEGGINGS = armor("aphelite_leggings", ArmorItem.Type.LEGGINGS);
	public static final Supplier<Item> BOOTS = armor("aphelite_boots", ArmorItem.Type.BOOTS);
	public static final Supplier<Item> SWORD = item("aphelite_sword", props -> new SwordItem(TOOL, props.attributes(SwordItem.createAttributes(TOOL, 3, -2.4F)).fireResistant()));
	public static final Supplier<Item> PICKAXE = item("aphelite_pickaxe", props -> new PickaxeItem(TOOL, props.attributes(PickaxeItem.createAttributes(TOOL, 1.0F, -2.8F)).fireResistant()));
	public static final Supplier<Item> AXE = item("aphelite_axe", props -> new AxeItem(TOOL, props.attributes(AxeItem.createAttributes(TOOL, 5.0F, -3.0F)).fireResistant()));
	public static final Supplier<Item> SHOVEL = item("aphelite_shovel", props -> new ShovelItem(TOOL, props.attributes(ShovelItem.createAttributes(TOOL, 1.5F, -3.0F)).fireResistant()));
	public static final Supplier<Item> HOE = item("aphelite_hoe", props -> new HoeItem(TOOL, props.attributes(HoeItem.createAttributes(TOOL, -4.0F, 0.0F)).fireResistant()));
	public static final Supplier<Item> UPGRADE_TEMPLATE = item("aphelite_upgrade_smithing_template", props -> new SmithingTemplateItem(
			Component.translatable("item.beyond_the_dragon.smithing_template.aphelite_upgrade.applies_to"),
			Component.translatable("item.beyond_the_dragon.smithing_template.aphelite_upgrade.ingredients"),
			Component.translatable("upgrade.beyond_the_dragon.aphelite_upgrade"),
			Component.translatable("item.beyond_the_dragon.smithing_template.aphelite_upgrade.base_slot_description"),
			Component.translatable("item.beyond_the_dragon.smithing_template.aphelite_upgrade.additions_slot_description"),
			List.of(slot("empty_armor_slot_helmet"), slot("empty_armor_slot_chestplate"), slot("empty_armor_slot_leggings"), slot("empty_armor_slot_boots"),
					slot("empty_slot_sword"), slot("empty_slot_pickaxe"), slot("empty_slot_axe"), slot("empty_slot_hoe"), slot("empty_slot_shovel")),
			List.of(slot("empty_slot_ingot"))));
	public static final Supplier<Item> DRAGON_SCALE_TEMPLATE = item("dragon_scale_armor_trim_smithing_template",
			props -> SmithingTemplateItem.createArmorTrimTemplate(DRAGON_SCALE));

	public static final ResourceKey<Block> ORE_KEY = ResourceKey.create(Registries.BLOCK, id("aphelite_ore"));
	public static final Supplier<Block> ORE = Services.PLATFORM.register(Registries.BLOCK, id("aphelite_ore"),
			() -> new DropExperienceBlock(UniformInt.of(3, 7), BlockBehaviour.Properties.of()
					.strength(3.0F, 9.0F).requiresCorrectToolForDrops().sound(SoundType.STONE)));
	public static final Supplier<Item> ORE_ITEM = item("aphelite_ore", props -> new BlockItem(ORE.get(), props));

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

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(BeyondTheDragon.MOD_ID, path);
	}

	private static Supplier<Item> item(String name, Function<Item.Properties, Item> factory) {
		return Services.PLATFORM.register(Registries.ITEM, id(name), () -> factory.apply(new Item.Properties()));
	}

	private static Supplier<Item> armor(String name, ArmorItem.Type type) {
		// The material's registry holder: registered before the items on both loaders.
		return item(name, props -> new ArmorItem(armorHolder(), type, props.durability(type.getDurability(ARMOR_DURABILITY)).fireResistant()));
	}

	private static Holder<ArmorMaterial> armorHolder() {
		ARMOR.get();
		return BuiltInRegistries.ARMOR_MATERIAL.getHolderOrThrow(ARMOR_KEY);
	}

	@SafeVarargs
	private static void tab(String vanillaTab, Supplier<Item>... items) {
		ResourceKey<CreativeModeTab> tab = ResourceKey.create(Registries.CREATIVE_MODE_TAB, ResourceLocation.withDefaultNamespace(vanillaTab));
		for (Supplier<Item> item : items) {
			Services.PLATFORM.addToCreativeTab(tab, item);
		}
	}

	private static Map<ArmorItem.Type, Integer> defense(int boots, int leggings, int chestplate, int helmet, int body) {
		return new EnumMap<>(Map.of(ArmorItem.Type.BOOTS, boots, ArmorItem.Type.LEGGINGS, leggings, ArmorItem.Type.CHESTPLATE, chestplate,
				ArmorItem.Type.HELMET, helmet, ArmorItem.Type.BODY, body));
	}

	private static ResourceLocation slot(String name) {
		return ResourceLocation.withDefaultNamespace("item/" + name);
	}
}
