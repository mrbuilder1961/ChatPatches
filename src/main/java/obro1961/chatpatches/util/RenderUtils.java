package obro1961.chatpatches.util;

public class RenderUtils {
	/**
	 * @return {@code rgb} with the alpha channel set to 255
	 *
	 * @param rgb A color in the format 0xAARRGGBB, where AA is the alpha channel.
	 * Technically works with any format where the alpha channel corresponds to the
	 * first two hex digits, but RGB is the most common.
	 */
	public static int opaque(int rgb) {
		return rgb | 0xFF000000;
	}

	public static class MousePos {
		public double x, y;

		private MousePos(double x, double y) {
			this.x = x;
			this.y = y;
		}

		public int xInt() { return (int)x; }
		public int yInt() { return (int)y; }

		public static MousePos of(double x, double y) {
			return new MousePos(x, y);
		}
	}

	// see older versions for the Renderer<W extends Widget> class if more complex button rendering is needed
}