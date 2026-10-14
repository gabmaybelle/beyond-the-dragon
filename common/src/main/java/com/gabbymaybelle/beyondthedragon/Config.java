package com.gabbymaybelle.beyondthedragon;

import com.gabbymaybelle.beyondthedragon.platform.Services;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * The settings, in {@code config/beyond_the_dragon.properties}. A missing file gets the defaults
 * written out with a comment per setting; a file with a typo keeps the typo (it's probably a
 * setting from another version) and the default is used with a warning in the log. The file is
 * only rewritten when a setting is missing, and then through a temp file so a crash mid-write
 * can't leave it half done.
 */
public final class Config {
	public static final String FILE_NAME = BeyondTheDragon.MOD_ID + ".properties";

	// Far End rings, in blocks from the End's centre. Each ring starts where the previous ends.
	public static int ringMiddle = 2000;
	public static int ringOuter = 5000;
	public static int ringEdge = 10000;
	public static boolean ringTitles = true;

	// Biome titles: a title when you enter a new biome, after a short stay, with a cooldown.
	public static boolean biomeTitles = true;
	public static int biomeTitleDwellSeconds = 3;
	public static int biomeTitleCooldownSeconds = 15;
	public static boolean biomeTitlesOverworld = true;
	public static boolean biomeTitlesNether = true;
	public static boolean biomeTitlesEnd = true;
	public static boolean endBiomeNames = true;

	// Dragon Ladder.
	public static boolean dragonLadder = true;
	public static boolean dragonShulkerVolleys = true;
	public static boolean dragonPullingBreath = true;
	public static boolean dragonDiveBomb = true;
	// With a mod that reworks the dragon fight (Stellarity, True Ending, ...) the new attacks stay
	// off unless this is on; the tiers and rewards still run.
	public static boolean dragonAbilitiesWithOtherDragonMods = false;
	// Mod ids that rework the dragon fight, comma-separated.
	public static String otherDragonModIds = "stellarity,true_ending,ender_dragon_fight_remastered";

	// Aphelite.
	public static boolean aphelitePerks = true;

	private static final Map<String, String> COMMENTS = new LinkedHashMap<>();
	static {
		COMMENTS.put("ringMiddle", "Where the middle ring starts, in blocks from the End's centre.");
		COMMENTS.put("ringOuter", "Where the outer ring starts. Aphelite only exists past here.");
		COMMENTS.put("ringEdge", "Where the edge ring starts. Nothing stops you flying further.");
		COMMENTS.put("ringTitles", "Show a title when you cross into a new ring.");
		COMMENTS.put("biomeTitles", "Show a title when you enter a new biome.");
		COMMENTS.put("biomeTitleDwellSeconds", "How long you must be in a biome before its title shows.");
		COMMENTS.put("biomeTitleCooldownSeconds", "The least time between two biome titles.");
		COMMENTS.put("biomeTitlesOverworld", "Biome titles in the Overworld.");
		COMMENTS.put("biomeTitlesNether", "Biome titles in the Nether.");
		COMMENTS.put("biomeTitlesEnd", "Biome titles in the End.");
		COMMENTS.put("endBiomeNames", "Use the mod's names for the End's biomes (off: the vanilla names).");
		COMMENTS.put("dragonLadder", "The dragon gets harder each time it's respawned, once the egg sits on a beacon.");
		COMMENTS.put("dragonShulkerVolleys", "Tier 1 attack.");
		COMMENTS.put("dragonPullingBreath", "Tier 2 attack.");
		COMMENTS.put("dragonDiveBomb", "Tier 3 attack.");
		COMMENTS.put("dragonAbilitiesWithOtherDragonMods", "Use the new attacks even when another mod reworks the dragon fight.");
		COMMENTS.put("otherDragonModIds", "Mod ids counted as reworking the dragon fight, comma-separated.");
		COMMENTS.put("aphelitePerks", "Aphelite's perks (shorter levitation, safe landings, bonus shards). Off: stats only.");
	}

	private Config() {
	}

	public static Path path() {
		return Services.PLATFORM.configDir().resolve(FILE_NAME);
	}

	public static void load() {
		Path file = path();
		Properties props = new Properties();
		if (Files.exists(file)) {
			try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				props.load(in);
			} catch (IOException e) {
				BeyondTheDragon.LOGGER.warn("Couldn't read {}; using the defaults", file, e);
			}
		}
		boolean missing = false;
		ringMiddle = readInt(props, "ringMiddle", ringMiddle, 100, 1_000_000);
		ringOuter = readInt(props, "ringOuter", ringOuter, 100, 1_000_000);
		ringEdge = readInt(props, "ringEdge", ringEdge, 100, 1_000_000);
		ringTitles = readBool(props, "ringTitles", ringTitles);
		biomeTitles = readBool(props, "biomeTitles", biomeTitles);
		biomeTitleDwellSeconds = readInt(props, "biomeTitleDwellSeconds", biomeTitleDwellSeconds, 0, 600);
		biomeTitleCooldownSeconds = readInt(props, "biomeTitleCooldownSeconds", biomeTitleCooldownSeconds, 0, 3600);
		biomeTitlesOverworld = readBool(props, "biomeTitlesOverworld", biomeTitlesOverworld);
		biomeTitlesNether = readBool(props, "biomeTitlesNether", biomeTitlesNether);
		biomeTitlesEnd = readBool(props, "biomeTitlesEnd", biomeTitlesEnd);
		endBiomeNames = readBool(props, "endBiomeNames", endBiomeNames);
		dragonLadder = readBool(props, "dragonLadder", dragonLadder);
		dragonShulkerVolleys = readBool(props, "dragonShulkerVolleys", dragonShulkerVolleys);
		dragonPullingBreath = readBool(props, "dragonPullingBreath", dragonPullingBreath);
		dragonDiveBomb = readBool(props, "dragonDiveBomb", dragonDiveBomb);
		dragonAbilitiesWithOtherDragonMods = readBool(props, "dragonAbilitiesWithOtherDragonMods", dragonAbilitiesWithOtherDragonMods);
		otherDragonModIds = props.getProperty("otherDragonModIds", otherDragonModIds).trim();
		aphelitePerks = readBool(props, "aphelitePerks", aphelitePerks);
		if (ringMiddle >= ringOuter || ringOuter >= ringEdge) {
			BeyondTheDragon.LOGGER.warn("The rings must be in order (ringMiddle < ringOuter < ringEdge); using 2000, 5000 and 10000");
			ringMiddle = 2000;
			ringOuter = 5000;
			ringEdge = 10000;
		}
		for (String key : COMMENTS.keySet()) {
			if (!props.containsKey(key)) {
				missing = true;
				props.setProperty(key, current(key));
			}
		}
		if (missing) {
			save(file, props);
		}
	}

	private static String current(String key) {
		return switch (key) {
			case "ringMiddle" -> Integer.toString(ringMiddle);
			case "ringOuter" -> Integer.toString(ringOuter);
			case "ringEdge" -> Integer.toString(ringEdge);
			case "ringTitles" -> Boolean.toString(ringTitles);
			case "biomeTitles" -> Boolean.toString(biomeTitles);
			case "biomeTitleDwellSeconds" -> Integer.toString(biomeTitleDwellSeconds);
			case "biomeTitleCooldownSeconds" -> Integer.toString(biomeTitleCooldownSeconds);
			case "biomeTitlesOverworld" -> Boolean.toString(biomeTitlesOverworld);
			case "biomeTitlesNether" -> Boolean.toString(biomeTitlesNether);
			case "biomeTitlesEnd" -> Boolean.toString(biomeTitlesEnd);
			case "endBiomeNames" -> Boolean.toString(endBiomeNames);
			case "dragonLadder" -> Boolean.toString(dragonLadder);
			case "dragonShulkerVolleys" -> Boolean.toString(dragonShulkerVolleys);
			case "dragonPullingBreath" -> Boolean.toString(dragonPullingBreath);
			case "dragonDiveBomb" -> Boolean.toString(dragonDiveBomb);
			case "dragonAbilitiesWithOtherDragonMods" -> Boolean.toString(dragonAbilitiesWithOtherDragonMods);
			case "otherDragonModIds" -> otherDragonModIds;
			case "aphelitePerks" -> Boolean.toString(aphelitePerks);
			default -> throw new IllegalArgumentException(key);
		};
	}

	private static int readInt(Properties props, String key, int fallback, int min, int max) {
		String raw = props.getProperty(key);
		if (raw == null) {
			return fallback;
		}
		try {
			int value = Integer.parseInt(raw.trim());
			if (value < min || value > max) {
				BeyondTheDragon.LOGGER.warn("{} must be between {} and {} (got {}); using {}", key, min, max, value, fallback);
				return fallback;
			}
			return value;
		} catch (NumberFormatException e) {
			BeyondTheDragon.LOGGER.warn("{} should be a whole number (got \"{}\"); using {}", key, raw, fallback);
			return fallback;
		}
	}

	private static boolean readBool(Properties props, String key, boolean fallback) {
		String raw = props.getProperty(key);
		if (raw == null) {
			return fallback;
		}
		String value = raw.trim().toLowerCase();
		if (value.equals("true") || value.equals("false")) {
			return Boolean.parseBoolean(value);
		}
		BeyondTheDragon.LOGGER.warn("{} should be true or false (got \"{}\"); using {}", key, raw, fallback);
		return fallback;
	}

	/** Writes every setting, commented, keeping any keys we don't know, through a temp file. */
	private static void save(Path file, Properties props) {
		try {
			Files.createDirectories(file.getParent());
			Path temp = file.resolveSibling(FILE_NAME + ".tmp");
			try (Writer out = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
				out.write("# Beyond the Dragon settings. Changes apply when the game starts.\n");
				for (Map.Entry<String, String> entry : COMMENTS.entrySet()) {
					out.write("\n# " + entry.getValue() + "\n");
					out.write(entry.getKey() + "=" + props.getProperty(entry.getKey()) + "\n");
				}
				boolean unknownHeader = false;
				for (String key : props.stringPropertyNames()) {
					if (!COMMENTS.containsKey(key)) {
						if (!unknownHeader) {
							out.write("\n# Settings this version doesn't know (kept as they were)\n");
							unknownHeader = true;
						}
						out.write(key + "=" + props.getProperty(key) + "\n");
					}
				}
			}
			Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			BeyondTheDragon.LOGGER.warn("Couldn't write {}", file, e);
		}
	}
}
