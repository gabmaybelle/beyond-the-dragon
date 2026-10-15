import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import javax.imageio.ImageIO;

/**
 * Draws the Dragon Scale armour trim: a spine of vertebrae down the chest and back, a V of
 * ridges on the brow, claw marks on the shoulders, a belt and the boots' rims, in the greys of vanilla's {@code trim_base} palette (which the game swaps for the
 * trim material's colours). The sheets are written as single-channel greyscale PNGs with grey 0
 * as the transparent key, the way vanilla's are: the palette swap keys on that, and an RGBA
 * file of the very same greys is drawn uncoloured. The layout is the standard 64x32 humanoid armour sheet: the helmet
 * in the head box, the chestplate in the body and right-arm boxes (the left arm mirrors), the
 * boots in the leg box; the leggings sheet uses the body (waist) and leg boxes.
 *
 * <pre>java tools/TrimGen.java common/src/main/resources/assets/beyond_the_dragon [preview.png]</pre>
 */
public class TrimGen {
	// trim_base, darkest to lightest: the raw greyscale bytes of vanilla's palette (an image
	// viewer, and ImageIO, show them gamma-lifted; the game keys on the raw values). Grey 0 is
	// the transparent key, so shade 0 is never drawn.
	private static final int[] GREY = {0x000000, 0x202020, 0x404040, 0x606060, 0x808080, 0xa0a0a0, 0xc0c0c0, 0xe0e0e0};

	public static void main(String[] args) throws IOException {
		Path assets = Path.of(args[0]).resolve("textures/trims/entity");
		BufferedImage humanoid = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
		BufferedImage leggings = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);

		// A trim is an accent, not a skin: line work, the way vanilla's are, and the armour's own
		// metal stays the colour you see. The dragon is in the spine down the chest and back, a V
		// of brow ridges on the helmet, claw marks on the shoulders, a belt and the boots' rims
		// (Gabby's choice over filled panels of scales, 2026-10-14).
		// Helmet: a V of ridges on the brow, meeting above the nose.
		for (int y = 9; y <= 11; y++) {
			pix(humanoid, 8 + (y - 9), y, 7);
			pix(humanoid, 15 - (y - 9), y, 7);
			pix(humanoid, 8 + (y - 9), y + 1, 4);
			pix(humanoid, 15 - (y - 9), y + 1, 4);
		}
		pix(humanoid, 11, 12, 7);
		pix(humanoid, 12, 12, 7);
		// Chestplate: vertebrae down the middle of the front and the back, shaded either side, and
		// three claw marks across each shoulder.
		spine(humanoid, 23, 20, 12);     // front
		spine(humanoid, 35, 20, 12);     // back
		claws(humanoid, 44, 20);         // shoulder, front
		claws(humanoid, 40, 20);         // shoulder, outer side
		claws(humanoid, 52, 20);         // shoulder, back
		// Boots: a scalloped rim round the top.
		scallop(humanoid, 4, 28, 4);     // front
		scallop(humanoid, 0, 28, 4);     // outer side
		scallop(humanoid, 8, 28, 4);     // inner side
		scallop(humanoid, 12, 28, 4);    // back
		// Leggings: a scalloped belt round the waist.
		scallop(leggings, 20, 20, 8);    // front
		scallop(leggings, 32, 20, 8);    // back
		scallop(leggings, 16, 20, 4);    // right
		scallop(leggings, 28, 20, 4);    // left

		writeGrey(humanoid, assets.resolve("humanoid/dragon_scale.png"));
		writeGrey(leggings, assets.resolve("humanoid_leggings/dragon_scale.png"));
		if (args.length > 1) {
			preview(humanoid, leggings, Path.of(args[1]));
		}
		System.out.println("dragon scale trim written");
	}

	/** A column of vertebrae two wide at x (bright and mid shades alternating down), a dark edge either side. */
	private static void spine(BufferedImage img, int x, int y0, int h) {
		for (int y = y0; y < y0 + h; y++) {
			int shade = y % 2 == 0 ? 7 : 5;
			pix(img, x, y, shade);
			pix(img, x + 1, y, shade);
			pix(img, x - 1, y, 3);
			pix(img, x + 2, y, 3);
		}
	}

	/** Three claw marks raked diagonally from a corner, each bright with a shadow under it. */
	private static void claws(BufferedImage img, int x0, int y0) {
		for (int i = 0; i < 3; i++) {
			pix(img, x0 + i, y0 + i, 7);
			pix(img, x0 + i, y0 + i + 1, 4);
		}
	}

	/** A one-row scalloped rim: bright arcs with a mid pixel between, a shadow beneath. */
	private static void scallop(BufferedImage img, int x0, int y, int w) {
		for (int x = 0; x < w; x++) {
			pix(img, x0 + x, y, x % 2 == 0 ? 7 : 5);
			pix(img, x0 + x, y + 1, x % 2 == 0 ? 3 : 0);
		}
	}

	private static void pix(BufferedImage img, int x, int y, int shade) {
		if (shade == 0) {
			return;
		}
		if (x >= 0 && x < img.getWidth() && y >= 0 && y < img.getHeight()) {
			img.setRGB(x, y, 0xFF000000 | GREY[shade]);
		}
	}

	private static void write(BufferedImage image, Path file) throws IOException {
		Files.createDirectories(file.getParent());
		ImageIO.write(image, "png", file.toFile());
	}

	/** An 8-bit greyscale PNG (colour type 0) with a tRNS chunk making grey 0 transparent; transparent pixels become 0. */
	private static void writeGrey(BufferedImage image, Path file) throws IOException {
		int w = image.getWidth();
		int h = image.getHeight();
		ByteArrayOutputStream raw = new ByteArrayOutputStream();
		for (int y = 0; y < h; y++) {
			raw.write(0); // filter: none
			for (int x = 0; x < w; x++) {
				int p = image.getRGB(x, y);
				raw.write((p >>> 24) == 0 ? 0 : (p & 0xFF));
			}
		}
		ByteArrayOutputStream deflated = new ByteArrayOutputStream();
		try (DeflaterOutputStream out = new DeflaterOutputStream(deflated, new Deflater(Deflater.BEST_COMPRESSION))) {
			out.write(raw.toByteArray());
		}
		ByteArrayOutputStream png = new ByteArrayOutputStream();
		png.write(new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 0x1a, 10});
		ByteArrayOutputStream ihdr = new ByteArrayOutputStream();
		DataOutputStream d = new DataOutputStream(ihdr);
		d.writeInt(w);
		d.writeInt(h);
		d.write(new byte[] {8, 0, 0, 0, 0}); // 8 bits, greyscale, deflate, no filter, not interlaced
		chunk(png, "IHDR", ihdr.toByteArray());
		chunk(png, "tRNS", new byte[] {0, 0});
		chunk(png, "IDAT", deflated.toByteArray());
		chunk(png, "IEND", new byte[0]);
		Files.createDirectories(file.getParent());
		Files.write(file, png.toByteArray());
	}

	private static void chunk(ByteArrayOutputStream png, String type, byte[] data) throws IOException {
		DataOutputStream d = new DataOutputStream(png);
		d.writeInt(data.length);
		byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
		CRC32 crc = new CRC32();
		crc.update(typeBytes);
		crc.update(data);
		d.write(typeBytes);
		d.write(data);
		d.writeInt((int) crc.getValue());
	}

	/** Both sheets side by side, eight times the size, over a dark background, for a look. */
	private static void preview(BufferedImage a, BufferedImage b, Path file) throws IOException {
		int scale = 8;
		BufferedImage out = new BufferedImage(64 * scale * 2 + scale, 32 * scale, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < out.getHeight(); y++) {
			for (int x = 0; x < out.getWidth(); x++) {
				out.setRGB(x, y, 0xFF303030);
			}
		}
		BufferedImage[] sheets = {a, b};
		for (int s = 0; s < 2; s++) {
			int ox = s * (64 * scale + scale);
			for (int y = 0; y < 32; y++) {
				for (int x = 0; x < 64; x++) {
					int p = sheets[s].getRGB(x, y);
					if ((p >>> 24) == 0) {
						continue;
					}
					for (int dy = 0; dy < scale; dy++) {
						for (int dx = 0; dx < scale; dx++) {
							out.setRGB(ox + x * scale + dx, y * scale + dy, p);
						}
					}
				}
			}
		}
		write(out, file);
	}
}
