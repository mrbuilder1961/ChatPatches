package obro1961.chatpatches.util;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.Widget;
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

	/**
	 * A functional interface intended for use within any {@linkplain W Widget}'s
	 * {@linkplain ClickableWidget#render(DrawContext, int, int, float)
	 * <code>#render</code>} method to provide more complex or aesthetic rendering.
	 *
	 * <p>Its functional method is
	 * {@link #render(DrawContext, int, int, float, Widget, Drawable)}.
	 *
	 * @param <W> A renderable {@link Widget}
	 */
	@FunctionalInterface
	public interface Renderer<W extends Widget> {
		/**
		 * @param widget The widget being rendered. If this was in a {@link Widget} class,
		 *               this parameter would be the {@code this} object.
		 * @param parentRenderer The original method that this one is replacing; must be
		 *                       called exactly once inside this implementation. This allows
		 * 						 both the extra and normal rendering to occur in tandem.
		 *                       To render without a parent, see {@link
		 *                       #renderWithoutParent(DrawContext, int, int, float, Widget)}
		 *                       or {@link Drawable} (if you don't need the widget parameter).
		 */
		void render(DrawContext context, int mX, int mY, float delta, W widget, Drawable parentRenderer);

		default void renderWithoutParent(DrawContext context, int mX, int mY, float delta, W widget) {
			render(context, mX, mY, delta, widget, (c, x, y, d) -> {});
		}
	}
}