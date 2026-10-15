import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Draws the aphelite textures from vanilla's netherite ones: the shapes and shading stay, the
 * colours move to an End palette (void violet through a cold steel blue to a teal glint). Run
 * with `java tools/TextureGen.java <vanilla textures dir> <mod assets dir>`; the vanilla dir
 * holds `assets/minecraft/textures/...` as unpacked from the client jar. The result is a
 * starting point for Gabby to approve or repaint, never final art on its own.
 */
public final class TextureGen {
	/**
	 * The aphelite ramp, darkest to lightest: void, deep violet, then the light turns cold through
	 * a steel blue to teal and an icy glint. Violet in the shadows keeps it of the End; the teal
	 * light keeps it apart from netherite, amethyst and purpur (chosen by Gabby, 2026-10-14).
	 */
	private static final int[][] RAMP = {
			{0x1a, 0x12, 0x30},
			{0x44, 0x2a, 0x78},
			{0x5e, 0x6a, 0xb8},
			{0x72, 0xbf, 0xd4},
			{0xb6, 0xf0, 0xee},
			{0xf4, 0xff, 0xf6},
	};

	public static void main(String[] args) throws IOException {
		Path vanilla = Path.of(args[0]).resolve("assets/minecraft/textures");
		Path out = Path.of(args[1]).resolve("textures");
		List<String> made = new ArrayList<>();

		// Items: same shapes as netherite, aphelite colours.
		String[] items = {"ingot", "helmet", "chestplate", "leggings", "boots", "sword", "pickaxe", "axe", "shovel", "hoe"};
		for (String item : items) {
			recolour(vanilla.resolve("item/netherite_" + item + ".png"), out.resolve("item/aphelite_" + item + ".png"));
			made.add("item/aphelite_" + item);
		}
		// The shard: an amethyst shard's shape in aphelite colours.
		recolour(vanilla.resolve("item/amethyst_shard.png"), out.resolve("item/aphelite_shard.png"));
		made.add("item/aphelite_shard");
		// The upgrade template: netherite's template with the aphelite ramp.
		recolour(vanilla.resolve("item/netherite_upgrade_smithing_template.png"), out.resolve("item/aphelite_upgrade_smithing_template.png"));
		made.add("item/aphelite_upgrade_smithing_template");
		// The dragon scale trim template: a trim template's shape with the aphelite ramp.
		recolour(vanilla.resolve("item/sentry_armor_trim_smithing_template.png"), out.resolve("item/dragon_scale_armor_trim_smithing_template.png"));
		made.add("item/dragon_scale_armor_trim_smithing_template");
		// Worn armour layers.
		// 1.21.1 keeps armour sheets as the material's layer_1 (body) and layer_2 (leggings) textures.
		recolour(vanilla.resolve("models/armor/netherite_layer_1.png"), out.resolve("models/armor/aphelite_layer_1.png"));
		recolour(vanilla.resolve("models/armor/netherite_layer_2.png"), out.resolve("models/armor/aphelite_layer_2.png"));
		made.add("models/armor/aphelite_layer_1 and _2");
		// The ore: end stone with aphelite flecks where ancient debris has its bright spots.
		ore(vanilla.resolve("block/end_stone.png"), vanilla.resolve("block/ancient_debris_side.png"), out.resolve("block/aphelite_ore.png"));
		made.add("block/aphelite_ore");

		for (String name : made) {
			System.out.println("wrote " + name);
		}
	}

	/** Keeps each pixel's brightness and alpha, replaces its colour with the ramp at that brightness. */
	private static void recolour(Path in, Path outFile) throws IOException {
		BufferedImage source = ImageIO.read(in.toFile());
		BufferedImage result = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < source.getHeight(); y++) {
			for (int x = 0; x < source.getWidth(); x++) {
				int argb = source.getRGB(x, y);
				int alpha = (argb >>> 24) & 0xff;
				if (alpha == 0) {
					result.setRGB(x, y, 0);
					continue;
				}
				double lum = luminance(argb);
				// Netherite sits in the dark half; stretch it so the full ramp is used.
				double t = Math.min(1.0, Math.max(0.0, (lum - 0.08) / 0.62));
				result.setRGB(x, y, (alpha << 24) | ramp(t));
			}
		}
		write(result, outFile);
	}

	/** End stone with aphelite flecks on the pixels where the debris texture is brightest. */
	private static void ore(Path stone, Path debris, Path outFile) throws IOException {
		BufferedImage base = ImageIO.read(stone.toFile());
		BufferedImage spots = ImageIO.read(debris.toFile());
		int w = base.getWidth(), h = base.getHeight();
		boolean[][] fleck = new boolean[h][w];
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				fleck[y][x] = luminance(spots.getRGB(x % spots.getWidth(), y % spots.getHeight())) > 0.42;
			}
		}
		BufferedImage result = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int argb = base.getRGB(x, y);
				if (fleck[y][x]) {
					// A fleck, shaded by its shape: the ends of a streak deep violet, its middle the
					// ramp's teal glint, a lone pixel in between. Vanilla's debris spots are all one
					// brightness, so their brightness can't do it (that gave one flat violet).
					int n = 0;
					for (int dy = -1; dy <= 1; dy++) {
						for (int dx = -1; dx <= 1; dx++) {
							int nx = x + dx, ny = y + dy;
							if ((dx != 0 || dy != 0) && nx >= 0 && ny >= 0 && nx < w && ny < h && fleck[ny][nx]) {
								n++;
							}
						}
					}
					argb = 0xff000000 | ramp(n >= 2 ? 0.72 : n == 1 ? 0.18 : 0.45);
				} else if (y > 0 && fleck[y - 1][x]) {
					// The fleck's shadow: a deep violet pixel under it, so it reads as set into the stone
					// and stays visible at a distance (chosen by Gabby, 2026-10-15).
					argb = 0xff000000 | ramp(0.1);
				}
				result.setRGB(x, y, argb);
			}
		}
		write(result, outFile);
	}

	private static double luminance(int argb) {
		int r = (argb >> 16) & 0xff;
		int g = (argb >> 8) & 0xff;
		int b = argb & 0xff;
		return (0.299 * r + 0.587 * g + 0.114 * b) / 255.0;
	}

	/** A colour along the ramp, t from 0 (darkest) to 1 (lightest), blended between stops. */
	private static int ramp(double t) {
		double scaled = t * (RAMP.length - 1);
		int i = (int) Math.floor(scaled);
		if (i >= RAMP.length - 1) {
			return rgb(RAMP[RAMP.length - 1]);
		}
		double f = scaled - i;
		int r = (int) Math.round(RAMP[i][0] + (RAMP[i + 1][0] - RAMP[i][0]) * f);
		int g = (int) Math.round(RAMP[i][1] + (RAMP[i + 1][1] - RAMP[i][1]) * f);
		int b = (int) Math.round(RAMP[i][2] + (RAMP[i + 1][2] - RAMP[i][2]) * f);
		return (r << 16) | (g << 8) | b;
	}

	private static int rgb(int[] c) {
		return (c[0] << 16) | (c[1] << 8) | c[2];
	}

	private static void write(BufferedImage image, Path file) throws IOException {
		Files.createDirectories(file.getParent());
		ImageIO.write(image, "png", file.toFile());
	}
}
