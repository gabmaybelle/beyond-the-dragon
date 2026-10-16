import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import javax.imageio.ImageIO;

/**
 * Draws the Void Jelly's textures and the chorus flower on a stick. Run with
 * `java tools/JellyTextureGen.java <vanilla textures dir> <mod assets dir>`; the vanilla dir holds
 * `assets/minecraft/textures/...` as unpacked from the client jar (only the carrot on a stick is
 * read from it).
 *
 * The jelly is drawn in three sheets on the happy ghast's 64x64 layout (Gabby chose "Teal jelly"
 * from previews, 2026-10-15):
 * - the shell: the happy ghast's 16-pixel body and its tentacles, see-through teal with a pale rim
 *   on every face and a highlight on the front, like a slime's outer layer;
 * - the core: a 10-pixel cube inside it, dark blue-teal, with the face on the front;
 * - the eyes: the face's eyes alone, drawn full bright over the core like a phantom's.
 * The baby uses the same sheets (the happy ghast's baby layout adds an inner body at y 32, left
 * empty here).
 */
public final class JellyTextureGen {
	// The shell: see-through teal, a paler rim, and a near-white glint.
	private static final int SHELL = rgb(0x46, 0x9c, 0x96);
	private static final int SHELL_DARK = rgb(0x38, 0x80, 0x7c);
	private static final int RIM = rgb(0x8c, 0xd8, 0xcc);
	private static final int GLINT = rgb(0xe0, 0xfa, 0xf4);
	private static final int SHELL_ALPHA = 120;
	private static final int RIM_ALPHA = 175;
	private static final int TENTACLE_ALPHA = 150;
	// The core: deep blue-teal, darker at the edges, and the face's blues and cyans.
	private static final int CORE = rgb(0x1e, 0x3c, 0x5a);
	private static final int CORE_EDGE = rgb(0x15, 0x2b, 0x43);
	private static final int BROW = rgb(0x6f, 0x9c, 0xd8);
	private static final int EYE = rgb(0x6c, 0xf2, 0xe2);
	private static final int EYE_LOW = rgb(0x5a, 0x8c, 0xe8);
	private static final int GLOW = rgb(0x9c, 0xff, 0xf2);
	// The chorus flower on the stick: the flower's own petals and purple.
	private static final int PETAL = rgb(0xfb, 0xf3, 0xfe);
	private static final int PETAL_SHADE = rgb(0xee, 0xd6, 0xee);
	private static final int PETAL_DEEP = rgb(0xd6, 0xbb, 0xd6);
	private static final int CHORUS = rgb(0x5b, 0x34, 0x5b);
	private static final int CHORUS_DARK = rgb(0x47, 0x21, 0x47);

	public static void main(String[] args) throws IOException {
		Path vanilla = Path.of(args[0]).resolve("assets/minecraft/textures");
		Path out = Path.of(args[1]).resolve("textures");
		write(shell(), out.resolve("entity/void_jelly/void_jelly_shell.png"));
		write(core(), out.resolve("entity/void_jelly/void_jelly_core.png"));
		write(eyes(), out.resolve("entity/void_jelly/void_jelly_eyes.png"));
		write(stick(ImageIO.read(vanilla.resolve("item/carrot_on_a_stick.png").toFile())), out.resolve("item/chorus_flower_on_a_stick.png"));
		Path harnesses = vanilla.resolve("entity/equipment/happy_ghast_body");
		BufferedImage white = ImageIO.read(harnesses.resolve("white_harness.png").toFile());
		for (String colour : DYES) {
			BufferedImage harness = ImageIO.read(harnesses.resolve(colour + "_harness.png").toFile());
			write(harness(harness, white), out.resolve("entity/void_jelly/harness/" + colour + "_harness.png"));
		}
		System.out.println("wrote the void jelly's shell, core, eyes and harnesses, and the chorus flower on a stick");
	}

	/** The happy ghast's body (a 16-pixel cube at 0,0) and tentacles (0,0 to 8,10). */
	private static BufferedImage shell() {
		BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
		Random random = new Random(20261015L);
		int s = 16;
		// Faces of a cube at texture offset 0,0: top, bottom, then the four sides below them.
		int[][] faces = {{s, 0}, {2 * s, 0}, {0, s}, {s, s}, {2 * s, s}, {3 * s, s}};
		for (int f = 0; f < faces.length; f++) {
			int fx = faces[f][0];
			int fy = faces[f][1];
			int base = f == 1 ? SHELL_DARK : SHELL;
			for (int y = 0; y < s; y++) {
				for (int x = 0; x < s; x++) {
					boolean edge = x == 0 || y == 0 || x == s - 1 || y == s - 1;
					int colour = edge ? RIM : jitter(base, random, 6);
					int alpha = edge ? RIM_ALPHA : SHELL_ALPHA + random.nextInt(11) - 5;
					img.setRGB(fx + x, fy + y, argb(alpha, colour));
				}
			}
		}
		// A soft highlight near the front's top left, as on a slime, and a smaller one on top.
		int[][] glint = {{2, 2}, {3, 2}, {4, 2}, {2, 3}, {3, 3}};
		for (int[] g : glint) {
			img.setRGB(s + g[0], s + g[1], argb(210, GLINT));
		}
		img.setRGB(s + 3, 3, argb(190, GLINT));
		img.setRGB(s + 4, 3, argb(190, GLINT));
		// Tentacles: a little more solid than the body, paler down one side.
		for (int y = 0; y < 10; y++) {
			for (int x = 0; x < 8; x++) {
				int colour = x % 4 == 0 ? RIM : jitter(SHELL, random, 5);
				img.setRGB(x, y, argb(TENTACLE_ALPHA, colour));
			}
		}
		return img;
	}

	/** The core: a 10-pixel cube at 0,0, with the face on its front (10,10 to 20,20). */
	private static BufferedImage core() {
		BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
		Random random = new Random(20261016L);
		int s = 10;
		int[][] faces = {{s, 0}, {2 * s, 0}, {0, s}, {s, s}, {2 * s, s}, {3 * s, s}};
		for (int[] face : faces) {
			for (int y = 0; y < s; y++) {
				for (int x = 0; x < s; x++) {
					boolean edge = x == 0 || y == 0 || x == s - 1 || y == s - 1;
					img.setRGB(face[0] + x, face[1] + y, argb(255, edge ? CORE_EDGE : jitter(CORE, random, 4)));
				}
			}
		}
		// The face, as in the preview: brows, eyes (the left one a pixel taller), a small mouth.
		face(img, 1, 2, BROW);
		face(img, 2, 2, BROW);
		face(img, 7, 2, BROW);
		face(img, 8, 2, BROW);
		for (int[] eye : EYES) {
			face(img, eye[0], eye[1], eye[1] == 4 ? EYE : EYE_LOW);
		}
		face(img, 4, 7, EYE);
		face(img, 5, 7, EYE);
		return img;
	}

	private static final String[] DYES = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
			"light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"};

	/**
	 * The happy ghast's harness, for the jelly: its cloth covers the upper half of every side of
	 * the body, which hid the shell and made a ridden jelly look like a happy ghast. This keeps
	 * only the leather straps, their stitching and the goggles, and leaves all the cloth off, so
	 * the shell shows all teal (Gabby, 2026-10-16: a band of cloth that was kept still read as a
	 * ghast's colouring). Which pixels are cloth is read from the white harness (its cloth is
	 * grey; leather is brown and the stitching gold), so every colour is cut the same way.
	 */
	private static BufferedImage harness(BufferedImage dyed, BufferedImage white) {
		int w = white.getWidth();
		int h = white.getHeight();
		int scale = w / 64;
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		for (int x = 0; x < w; x++) {
			for (int y = 0; y < h; y++) {
				int c = white.getRGB(x, y);
				if ((c >>> 24) == 0) {
					continue;
				}
				boolean goggles = y >= 32 * scale;
				if (goggles || !isCloth(c)) {
					img.setRGB(x, y, dyed.getRGB(x, y));
				}
			}
		}
		return img;
	}

	private static boolean isCloth(int c) {
		int r = (c >> 16) & 0xff;
		int g = (c >> 8) & 0xff;
		int b = c & 0xff;
		return Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) < 12;
	}

	/** The eye pixels of the face, drawn again full bright. */
	private static final int[][] EYES = {{1, 4}, {2, 4}, {7, 4}, {8, 4}, {1, 5}, {2, 5}, {8, 5}};

	private static BufferedImage eyes() {
		BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
		for (int[] eye : EYES) {
			img.setRGB(10 + eye[0], 10 + eye[1], argb(255, GLOW));
		}
		return img;
	}

	private static void face(BufferedImage img, int x, int y, int colour) {
		img.setRGB(10 + x, 10 + y, argb(255, colour));
	}

	/**
	 * Vanilla's carrot on a stick with the carrot (its orange and green pixels) taken off and a
	 * chorus flower bud hung on the line instead.
	 */
	private static BufferedImage stick(BufferedImage carrot) {
		BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				int c = carrot.getRGB(x, y);
				if ((c >>> 24) != 0 && !isCarrot(c)) {
					img.setRGB(x, y, c);
				}
			}
		}
		String[] bud = {
				".ccc.",
				"cbpbc",
				"cppsc",
				"cbsdc",
				".cec.",
		};
		for (int y = 0; y < bud.length; y++) {
			for (int x = 0; x < bud[y].length(); x++) {
				int colour = switch (bud[y].charAt(x)) {
					case 'c' -> CHORUS;
					case 'e' -> CHORUS_DARK;
					case 'p' -> PETAL;
					case 'b' -> PETAL_SHADE;
					case 's' -> PETAL_DEEP;
					case 'd' -> rgb(0xab, 0x8a, 0xab);
					default -> -1;
				};
				if (colour != -1) {
					img.setRGB(9 + x, 11 + y, argb(255, colour));
				}
			}
		}
		return img;
	}

	/** The carrot's pixels: its oranges and its greens (the rod and line are browns and greys). */
	private static boolean isCarrot(int c) {
		int r = (c >> 16) & 0xff;
		int g = (c >> 8) & 0xff;
		int b = c & 0xff;
		boolean orange = r > g + 40 && r > b + 80;
		boolean green = g > r + 40 && g > b + 40;
		return orange || green;
	}

	private static int jitter(int colour, Random random, int amount) {
		int d = random.nextInt(2 * amount + 1) - amount;
		return rgb(clamp(((colour >> 16) & 0xff) + d), clamp(((colour >> 8) & 0xff) + d), clamp((colour & 0xff) + d));
	}

	private static int clamp(int v) {
		return Math.max(0, Math.min(255, v));
	}

	private static int rgb(int r, int g, int b) {
		return (r << 16) | (g << 8) | b;
	}

	private static int argb(int alpha, int colour) {
		return (alpha << 24) | (colour & 0xffffff);
	}

	private static void write(BufferedImage img, Path path) throws IOException {
		Files.createDirectories(path.getParent());
		ImageIO.write(img, "png", path.toFile());
	}
}
