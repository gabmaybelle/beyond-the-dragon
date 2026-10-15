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
 * Draws the Dragon Scale armour trim: overlapping scales over the chest, shoulders, brow, boots
 * and legs, in the greys of vanilla's {@code trim_base} palette (which the game swaps for the
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
		// 1.21.1 reads trim sheets from trims/models/armor/<pattern> and <pattern>_leggings.
		Path assets = Path.of(args[0]).resolve("textures/trims/models/armor");
		BufferedImage humanoid = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
		BufferedImage leggings = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);

		// A trim is an accent, not a skin: the scales sit where a dragon's would show and the
		// armour's own metal stays visible between them.
		// Helmet: a band of scales across the brow, and a crest along the top.
		scales(humanoid, 8, 9, 8, 2);    // brow
		crest(humanoid, 8, 0, 8, 8);     // top face: a ridge front to back
		// Chestplate: a breast panel down the middle, a spine down the back, caps on the shoulders.
		scales(humanoid, 22, 21, 4, 10); // front, centre
		scales(humanoid, 34, 21, 4, 10); // back, centre
		scales(humanoid, 44, 20, 4, 4);  // shoulder, front
		scales(humanoid, 40, 20, 4, 4);  // shoulder, outer side
		scales(humanoid, 52, 20, 4, 4);  // shoulder, back
		// Boots: a cuff of scales round the ankle.
		scales(humanoid, 4, 28, 4, 2);   // front
		scales(humanoid, 0, 28, 4, 2);   // outer side
		scales(humanoid, 8, 28, 4, 2);   // inner side
		scales(humanoid, 12, 28, 4, 2);  // back
		// Leggings: scales down the front of the thighs and a belt of them round the waist.
		scales(leggings, 4, 20, 4, 6);   // thigh front
		scales(leggings, 0, 20, 4, 4);   // thigh outer side
		scales(leggings, 20, 29, 8, 2);  // waist front
		scales(leggings, 32, 29, 8, 2);  // waist back
		scales(leggings, 16, 29, 4, 2);  // waist right
		scales(leggings, 28, 29, 4, 2);  // waist left

		writeGrey(humanoid, assets.resolve("dragon_scale.png"));
		writeGrey(leggings, assets.resolve("dragon_scale_leggings.png"));
		if (args.length > 1) {
			preview(humanoid, leggings, Path.of(args[1]));
		}
		System.out.println("dragon scale trim written");
	}

	/**
	 * Scales three wide and two tall, every other row shifted by half a scale so they overlap:
	 * a bright arc on top, a shaded underside. The region is filled completely.
	 */
	private static void scales(BufferedImage img, int x0, int y0, int w, int h) {
		for (int row = 0; row * 2 < h; row++) {
			int y = y0 + row * 2;
			int shift = row % 2 == 0 ? 0 : 1;
			for (int x = x0 - 3 + shift; x < x0 + w; x += 3) {
				put(img, x, y, 6, x0, y0, w, h);
				put(img, x + 1, y, 7, x0, y0, w, h);
				put(img, x + 2, y, 6, x0, y0, w, h);
				put(img, x, y + 1, 4, x0, y0, w, h);
				put(img, x + 1, y + 1, 5, x0, y0, w, h);
				put(img, x + 2, y + 1, 4, x0, y0, w, h);
			}
		}
	}

	/** A ridge of pale spikes down the middle of a face, dark at its root. */
	private static void crest(BufferedImage img, int x0, int y0, int w, int h) {
		int mid = x0 + w / 2;
		for (int y = y0 + 1; y < y0 + h - 1; y++) {
			put(img, mid - 1, y, y % 2 == 0 ? 7 : 5, x0, y0, w, h);
			put(img, mid, y, y % 2 == 0 ? 7 : 5, x0, y0, w, h);
			put(img, mid - 2, y, 3, x0, y0, w, h);
			put(img, mid + 1, y, 3, x0, y0, w, h);
		}
	}

	private static void put(BufferedImage img, int x, int y, int shade, int x0, int y0, int w, int h) {
		if (x >= x0 && x < x0 + w && y >= y0 && y < y0 + h) {
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
