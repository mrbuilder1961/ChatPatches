package obro1961.chatpatches.config;

// search settings

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import obro1961.chatpatches.mixin.gui.ChatScreenMixin;

/** Represents a search setting pertaining to the {@link ChatScreenMixin} screen. */
public class ChatSearchSetting {
	public static ChatSearchSetting caseSensitive = new ChatSearchSetting("caseSensitive", true),
									modifiers = new ChatSearchSetting("modifiers", false),
									regex = new ChatSearchSetting("regex", false);

	public boolean on;
	public ButtonWidget button;
	private final Text name;
	private final String key;

	private ChatSearchSetting(String key, boolean on) {
		this.name = Text.translatable("text.chatpatches.search." + key);
		this.key = key;
		this.on = on;
	}

	public void update(int yWithOffset, ButtonWidget.PressAction onPress) {
		Text text = ScreenTexts.composeToggleText(name, on);

		ButtonWidget.PressAction UPDATE_BUTTON = button -> {
			on = !on;
			button.setMessage(ScreenTexts.composeToggleText(name, on));
			onPress.onPress(button); // flags ChatScreenMixin to update the search text color
		};

		this.button = ButtonWidget.builder(text, UPDATE_BUTTON)
			.dimensions(8, yWithOffset, MinecraftClient.getInstance().textRenderer.getWidth(text.asOrderedText()) + 10, 20)
			.tooltip(Tooltip.of( Text.translatable("text.chatpatches.search.desc." + key) ))
			.build();
	}
}