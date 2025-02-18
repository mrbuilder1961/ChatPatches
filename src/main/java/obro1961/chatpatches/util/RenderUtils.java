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
//FIXME: SEE THE DRAWABLE CLASS AND MAKE SURE IM NOT RE-IMPL ING A PREEXISTING CLASS
	/**
	 * A simpler version of {@link ParentRenderer} that does not provide any original
	 * parent method to invoke during execution. Useful for renderers that are
	 * actually creating extra details, versus ones that are simply wrappers for
	 * preexisting render methods.
	 *
	 * <p>Its functional method is {@link #render(DrawContext, int, int, float, Widget)}.
	 *
	 * @param <W> A renderable Widget.
	 * @see ParentRenderer
	 */
	@FunctionalInterface
	public interface Renderer<W extends Widget> {
		/**
		 * @param widget The widget being rendered. If this was in a {@link Widget} class,
		 *               this parameter would be the {@code this} object.
		 */
		void render(DrawContext context, int mX, int mY, float delta, W widget);

	}
	/**
	 * A functional interface intended for use within any {@linkplain W Widget}'s
	 * {@linkplain ClickableWidget#render(DrawContext, int, int, float)
	 * <code>#render</code>} method to provide more complex or aesthetic rendering.
	 * The {@code Parent} signifies that this renderer takes an original or super
	 * method that must be called exactly once inside this renderer. This allows
	 * both the extra and normal rendering to occur in tandem.
	 *
	 * <p>Its functional method is
	 * {@link #renderWithParent(DrawContext, int, int, float, Widget, Renderer)}.
	 */
	@FunctionalInterface
	public interface ParentRenderer<W extends Widget> {
		/**
		 * @param widget The widget being rendered. If this was in a {@link Widget} class,
		 *               this parameter would be the {@code this} object.
		 * @param parentRenderer The original method that this one is replacing; must be
		 *                       called exactly once inside this implementation. To render
		 *                       without a parent (ex. for implementing custom rendering
		 *                       independently), see
		 *                       {@link Renderer#render(DrawContext, int, int, float, Widget)}.
		 */
		void renderWithParent(DrawContext context, int mX, int mY, float delta, W widget, Renderer<W> parentRenderer);
	}
}