package obro1961.chatpatches.config;

// search settings

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import obro1961.chatpatches.mixin.gui.ChatScreenMixin;

/** Represents a search setting pertaining to the {@link ChatScreenMixin} screen. */
public class ChatSearchSettingButton {
	public static ChatSearchSettingButton caseSensitive, modifiers, regex;

	public static boolean updateSearchColor = false;

	private static final Text TOGGLE_OFF = Text.of(": §7[§cX§4=§7]"), TOGGLE_ON = Text.of(": §7[§2=§aO§7]");

	public final ButtonWidget button;
	public boolean on;
	private final Text name;

	public ChatSearchSettingButton(String key, boolean on, final int y, int yOffset) {
		this.name = Text.translatable("text.chatpatches.search." + key);
		this.on = on;


		Text text = name.copy().append( on ? TOGGLE_ON : TOGGLE_OFF );
		ButtonWidget.PressAction UPDATE_BUTTON = button -> {
			this.on = !this.on;
			button.setMessage(this.name.copy().append(this.on ? TOGGLE_ON : TOGGLE_OFF));

			// flags ChatScreenMixin to update the search text color
			updateSearchColor = true;
		};

		this.button = ButtonWidget.builder(text, UPDATE_BUTTON)
			.dimensions(8, y + yOffset, MinecraftClient.getInstance().textRenderer.getWidth(text.asOrderedText()) + 10, 20)
			.tooltip(Tooltip.of( Text.translatable("text.chatpatches.search.desc." + key) ))
			.build();
	}
}