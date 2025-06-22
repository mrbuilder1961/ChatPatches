package obro1961.chatpatches.util;

import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Style;

public class RenderUtils {
	public static final ChatHudLine NIL_HUD_LINE = new ChatHudLine(0, ScreenTexts.EMPTY, null, null);
	public static final Style BLANK_STYLE = Style.EMPTY.withBold(false).withItalic(false).withStrikethrough(false).withUnderline(false).withObfuscated(false);
	// todo ^ test if this is still necessary, perhaps after making some gametests that work and then removing it and seeing if all is well

	public static class MousePos {
		public double x, y;

		private MousePos(double x, double y) {
			this.x = x;
			this.y = y;
		}

		public static MousePos of(double x, double y) {
			return new MousePos(x, y);
		}
	}

	// see older versions for the Renderer<W extends Widget> class if more complex button rendering is needed
}