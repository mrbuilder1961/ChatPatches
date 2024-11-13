package obro1961.chatpatches.util;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.Widget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Style;

public class RenderUtils {
	public static final ChatHudLine NIL_HUD_LINE = new ChatHudLine(0, ScreenTexts.EMPTY, null, null);
	public static final Style BLANK_STYLE = Style.EMPTY.withBold(false).withItalic(false).withStrikethrough(false).withUnderline(false).withObfuscated(false);


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

	/**
	 * A functional interface intended for use alongside a {@linkplain W Widget}'s
	 * {@linkplain ClickableWidget#renderButton(DrawContext, int, int, float)
	 * <code>#renderButton</code>} or {@linkplain ClickableWidget#render(DrawContext, int,
	 * int, float) <code>#render</code>} method to provide more complex or aesthetic rendering.
	 *
	 * <p>It's functional method is {@link #render(Widget, DrawContext, int, int, float)}.
	 */
	@FunctionalInterface
	public interface Renderer<W extends Widget> {
		void render(W widget, DrawContext context, int mouseX, int mouseY, float delta);
	}
}