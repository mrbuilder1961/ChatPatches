package obro1961.chatpatches.util;

import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

/**
 * A class containing various render-related utilities
 */
public class RenderUtils {
	public static final ChatHudLine NIL_HUD_LINE = new ChatHudLine(0, Text.empty(), null, null);
	/**
	 * An {@linkplain Style#EMPTY empty style} with all boolean formatting values disabled.
	 */
	public static final Style BLANK_STYLE = Style.EMPTY.withBold(false).withItalic(false).withStrikethrough(false).withUnderline(false).withObfuscated(false);


	public static class MousePos {
		public int x, y;

		private MousePos(int x, int y) {
			this.x = x;
			this.y = y;
		}

		public static MousePos of(int x, int y) {
			return new MousePos(x, y);
		}
	}
}