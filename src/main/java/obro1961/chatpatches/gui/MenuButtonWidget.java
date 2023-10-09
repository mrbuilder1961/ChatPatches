package obro1961.chatpatches.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.IconButtonWidget;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import obro1961.chatpatches.mixin.gui.ChatScreenMixin;
import obro1961.chatpatches.util.RenderUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class MenuButtonWidget {
	/** The mouse position to anchor all menu buttons to, used here rather than {@link ChatScreenMixin} because of mixin accessing limitations. */
	public static RenderUtils.MousePos anchor = RenderUtils.MousePos.of(-1, -1); // the "origin" of all menu buttons
	public static int mainOffsets = 0, hoverOffsets = 0;

	public static int padding = 4, height = 14 + padding; // padding is 1x vertically and 2x horizontally

	public final ButtonWidget button;
	public final BiConsumer<MenuButtonWidget, Boolean> onMouseMoved; // called when the mouse hovers over this button; used to decide when to render the hover menu buttons
	public final Supplier<String> copySupplier; // for supplying the string to be copied when clicked; mostly uses ChatScreenMixin#selectedLine
	public Consumer<MenuButtonWidget> otherPressAction = menuButton -> {}; // currently only for the reply button
	public List<MenuButtonWidget> children; // the buttons that are rendered when this button is hovered over
	public Identifier skinTexture; // the texture to render over this button, currently only used for the reply button
	public int xOffset, yOffset, localY = 0, width; // localY is the y offset of this button from its parent, used to align the button vertically

	private MenuButtonWidget(int xOffset, Text message, Supplier<String> cS, MenuButtonWidget... c) {
		this.children = new ArrayList<>( List.of(c) );
		this.xOffset = xOffset;
		this.copySupplier = cS != null ? cS : () -> "";
		this.onMouseMoved = (me, isMouseOver) -> children.forEach(hoverButton -> hoverButton.button.visible = isMouseOver);

		this.width = MinecraftClient.getInstance().textRenderer.getWidth(message) + 2*padding;
		this.button =
			IconButtonWidget.builder(message, button -> {
				String string = this.copySupplier.get();
				if(!string.isEmpty()) {
					MinecraftClient.getInstance().keyboard.setClipboard(string);
					MinecraftClient.getInstance().inGameHud.setOverlayMessage(
						Text.translatable("text.chatpatches.copy.copied", string.replaceAll("§", "&"))
							.setStyle( Style.EMPTY.withColor(Formatting.GREEN) ),
						false
					);
				}
				otherPressAction.accept(this);
			})
			.position(anchor.x + this.xOffset, anchor.y + yOffset)
			.size(width, height)
			.build();
	}

	/** Creates a MenuButtonWidget with no mouse hover action, used for buttons with copy actions (mostly hover buttons). */
	public static MenuButtonWidget of(int xOffset, Text text, Supplier<String> componentSupplier) {
		return new MenuButtonWidget(xOffset, text, componentSupplier);
	}
	/** Creates a MenuButtonWidget with no component supplier, used for buttons that reveal more (main buttons). */
	public static MenuButtonWidget of(int xOffset, Text text, MenuButtonWidget... children) {
		return new MenuButtonWidget(xOffset, text, null, children);
	}


	public void updateTooltip() {
		button.setTooltip(Tooltip.of(Text.of( copySupplier.get().replaceAll("§", "&") )));
	}

	/** Sets the width of this button, ignoring alignment. */
	public void setWidth(int width) {
		this.width = width + 2*padding;
		button.setWidth(this.width);
	}

	public MenuButtonWidget setTexture(Identifier skinTexture) {
		this.skinTexture = skinTexture;
		return this;
	}

	/** Updates the vertical position of this button to align with the button provided. */
	public MenuButtonWidget alignTo(MenuButtonWidget parent) {
		this.localY = yOffset = parent.yOffset - height;
		return this;
	}

	/** Shifts the button's position by the given amount of button heights to stack the menu buttons. */
	public MenuButtonWidget offsetY(int places) {
		yOffset += (height * places);
		button.setY(anchor.y + yOffset);
		return this;
	}

	/** Shifts this button down based on which button group it's in for stacking, updates the position, and makes it visible. */
	public void readyToRender(boolean mainMenu) {
		if(mainMenu)
			yOffset += (height * ++mainOffsets);
		else
			yOffset += (height * ++hoverOffsets);
		button.setY(anchor.y + yOffset);
		updateTooltip();
		button.visible = true;
	}

	public void cancelRender() {
		yOffset = localY;
		button.setY(anchor.y + yOffset);
		button.setTooltip(null);
		button.visible = false;
	}

	public MenuButtonWidget setOtherPressAction(Consumer<MenuButtonWidget> otherPressAction) {
		this.otherPressAction = otherPressAction;
		return this;
	}

	/** Returns true if the Text id provided is equal to the button's text (message). */
	public boolean is(Text id) {
		return button.getMessage().equals(id);
	}


	/** Returns and updates the x coordinate where the button should render, using {@link #anchor} and {@link #xOffset}. */
	private int x() {
		button.setX(anchor.x + xOffset);
		return anchor.x + xOffset;
	}

	/** Returns and updates the y coordinate where the button should render, using {@link #anchor} and {@link #yOffset}. */
	private int y() {
		button.setY(anchor.y + yOffset);
		return anchor.y + yOffset;
	}


	public boolean isMouseOver(double mX, double mY) {
		return button.active && button.visible && (mX >= x() && mX <= x() + width && mY >= y() && mY <= y() + height);
	}

	public void mouseMoved(double mX, double mY) {
		if(onMouseMoved != null) {
			if(isMouseOver(mX, mY) || children.stream().anyMatch(hoverButton -> hoverButton.isMouseOver(mX, mY)))
				onMouseMoved.accept(this, true);
			else
				onMouseMoved.accept(this, false);
		}
	}

	/** Returns true if the mouse left-clicked on the actual button or the padded area around it. */
	public boolean mouseClicked(double mX, double mY, int button) {
		if( button == 0 && isMouseOver(mX, mY) ) {
			this.button.playDownSound(MinecraftClient.getInstance().getSoundManager());
			this.button.onPress();
			return true;
		}

		return false;
	}

	public void render(DrawContext drawContext, int mX, int mY, float delta) {
		if(!button.visible || x() < 0 || y() < 0)
			return;

		// x() = anchor.x + x
		// y() = anchor.y + y
		// text: from (x+2p, y-1p) to (x+2p+w, y-1p-height) w width=w and height=height
		// fill: from (x, y) to (x+2p+w, y-1p-height)
		// width: effectively w+2p
		// height: effectively height+1p (with coords, height-1p);
		button.render(drawContext, mX, mY, delta);

		if(skinTexture != null) {
			// thank you to dzwdz's Chat Heads for most of the code to draw the skin texture!

			// draw base layer, then the hat
			int x = anchor.x + xOffset + 1;
			int y = anchor.y + yOffset + 1;
			drawContext.drawTexture(skinTexture, x, y, 16, 16, 8, 8, 8, 8, 64, 64);
			drawContext.drawTexture(skinTexture, x, y, 16, 16, 40, 8, 8, 8, 64, 64);
		}
	}
}