package obro1961.chatpatches.util;

import net.minecraft.ChatFormatting;

public class RenderUtil {
	/**
	 * @return {@code rgb} with the alpha channel set to 255 ({@code argb})
	 *
	 * @param rgb A color in the format 0xAARRGGBB, where AA is the alpha channel.
	 * Technically works with any format where the alpha channel corresponds to the
	 * first two hex digits, but RGB is the most common.
	 */
	public static int opaque(int rgb) {
		return rgb | 0xFF000000;
	}

	/**
	 * On versions >=1.21.6, returns {@code xrgb} as an opaque color, while on versions before
	 * that it returns {@code xrgb} with any alpha channel explicitly removed. In other words,
	 * on versions >=1.21.6, this method is equivalent to {@link #opaque(int)}, while on
	 * older versions it is equivalent to {@code xrgb & 0x00FFFFFF}.
	 *
	 * @implNote Uses Stonecutter
	 *
	 * @see #opaque(int)
	 */
	public static int smartOpaque(int xrgb) {
		return
			/*? if >=1.21.6 {*/
				opaque(xrgb)
			/*?} else {*/
				/*xrgb & 0x00FFFFFF*/
			/*?}*/
		;
	}

	/** @see #smartOpaque(int) */
	public static int smartOpaque(ChatFormatting formatting) {
		return smartOpaque(formatting.getColor());
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