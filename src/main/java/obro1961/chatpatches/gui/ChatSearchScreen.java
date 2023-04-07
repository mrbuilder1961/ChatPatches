package obro1961.chatpatches.gui;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.TexturedButtonWidget;
import net.minecraft.client.util.ChatMessages;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import obro1961.chatpatches.mixin.gui.ChatScreenAccessor;
import obro1961.chatpatches.util.ChatUtils;
import obro1961.chatpatches.util.StringTextUtils;
import org.apache.commons.lang3.StringUtils;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * An extension of ChatScreen with searching capabilities.
 * Contains a search button, search bar, and settings menu.
 */
public class ChatSearchScreen extends ChatScreen {
	private static final String SUGGESTION_TEXT = Text.translatable("text.chatpatches.search_suggestion").getString();
	private static final Text SEARCH_TOOLTIP = Text.translatable("text.chatpatches.search.desc");

	private static final int SEARCH_X = 22, SEARCH_Y_OFFSET = -31, SEARCH_H = 12;
	private static final double SEARCH_W_MULT = 0.25;
	private static final int MENU_WIDTH = 146, MENU_HEIGHT = 76;
	private static final int MENU_X = 2, MENU_Y_OFFSET = SEARCH_Y_OFFSET - MENU_HEIGHT - 6;
	public static String lastLastSearch = "";

	private TexturedButtonWidget searchButton;
	private boolean showSearch = true;
	private TextFieldWidget searchField;
	private String lastSearch;
	private PatternSyntaxException searchError;
	private boolean showSettingsMenu = false;

	public ChatSearchScreen(String originalChatText) {
		super(originalChatText);
		this.client = Objects.requireNonNullElse(client, MinecraftClient.getInstance());
	}


	/**
	 * @implNote
	 * <ol>
	 *     <li>Initialize the original chat screen</li>
	 *     <li>Create the search button</li>
	 *     <li>Create the search field</li>
	 *     <li>Create the setting options</li>
	 * </ol>
	 */
	@Override
	protected void init() {
		super.init();

		searchButton = new TexturedButtonWidget(2, height - 35, 16, 16, 0, 0, 16, Identifier.of("chatpatches", "textures/gui/search_buttons.png"), 16, 32, button -> {});
		searchButton.setTooltip(Tooltip.of(SEARCH_TOOLTIP));

		searchField = new TextFieldWidget(client.textRenderer, SEARCH_X, height + SEARCH_Y_OFFSET, (int)(width * SEARCH_W_MULT), SEARCH_H, Text.translatable("chat.editBox"));
		searchField.setMaxLength(384);
		searchField.setDrawsBackground(false);
		searchField.setSuggestion(SUGGESTION_TEXT);
		searchField.setChangedListener(this::onSearchFieldUpdate);
		searchField.setText(ChatSearchScreen.lastLastSearch);
		addSelectableChild(searchField);

		caseSensitive = new Setting("caseSensitive", true, 0);
		modifiers = new Setting("modifiers", false, 22);
		regex = new Setting("regex", false, 44);
	}

	/**
	 * @implNote Renders the original chat screen and search-related stuff:
	 * <ol>
	 *     <li>The {@code chatField} background</li>
	 *     <li>The {@code chatField} itself</li>
	 *     <li>The {@code searchButton}</li>
	 *     <li>If the search bar should show, render:</li>
	 *     		<li>The {@code searchField} background</li>
	 *     		<li>The {@code searchField} itself</li>
	 *     		<li>If the {@code searchError} is not null, render it</li>
	 *     <li>If the settings menu should show, render:</li>
	 *     		<li>The settings menu background</li>
	 *     		<li>The setting buttons themselves</li>
	 *     <li>If the settings menu isn't showing or the mouse isn't hovering over the settings background, (if needed) render a hover tooltip</li>
	 *     <li>The mixin-accessed {@link ChatInputSuggestor}</li>
	 * </ol>
	 */
	@Override
	public void render(MatrixStack matrices, int mX, int mY, float delta) {
		// chatField stuff copied from ChatScreen, by Mojang
		ChatScreen.fill(matrices, 2, height - 14, width - 2, height - 2, client.options.getTextBackgroundColor(Integer.MIN_VALUE));
		chatField.render(matrices, mX, mY, delta);

		searchButton.render(matrices, mX, mY, delta);
		if(showSearch) {
			ChatScreen.fill(matrices, SEARCH_X - 2, height + SEARCH_Y_OFFSET - 2, (int)(width * (SEARCH_W_MULT + 0.06)), height + SEARCH_Y_OFFSET + SEARCH_H - 2,
				client.options.getTextBackgroundColor(Integer.MIN_VALUE));
			searchField.render(matrices, mX, mY, delta);

			// renders a suggestion-esq error message if the regex search is invalid
			if(searchError != null) {
				int x = searchField.getX() + 8 + (int)(width * SEARCH_W_MULT);
				textRenderer.drawWithShadow(matrices, searchError.getMessage().split(System.lineSeparator())[0], x, searchField.getY(), 0xD00000);
			}
		}

		// renders the bg and the buttons for the settings menu
		if(showSettingsMenu) {
			RenderSystem.setShaderTexture(0, Identifier.of("chatpatches", "textures/gui/search_settings_panel.png"));
			drawTexture(matrices, MENU_X,  height + MENU_Y_OFFSET, 0, 0, MENU_WIDTH, MENU_HEIGHT, MENU_WIDTH, MENU_HEIGHT);

			caseSensitive.button.render(matrices, mX, mY, delta);
			modifiers.button.render(matrices, mX, mY, delta);
			regex.button.render(matrices, mX, mY, delta);

		}

		boolean isHoveringOverSettings = mX >= MENU_X && mX <= MENU_X + MENU_WIDTH && mY >= height + MENU_Y_OFFSET && mY <= height + MENU_Y_OFFSET + MENU_HEIGHT;
		if( !showSettingsMenu || !isHoveringOverSettings) {
			// only renders the tooltip if the mouse is not hovering over the settings menu
			// code inside the if statement is copied from ChatScreen, by Mojang
			ChatHud chatHud = client.inGameHud.getChatHud();

			MessageIndicator indicator = chatHud.getIndicatorAt(mX, mY);
			if (indicator != null && indicator.text() != null) {
				renderOrderedTooltip(matrices, textRenderer.wrapLines(indicator.text(), 210), mX, mY);
			} else {
				Style style = chatHud.getTextStyleAt(mX, mY);
				if (style != null && style.getHoverEvent() != null)
					renderTextHoverEffect(matrices, style, mX, mY);
			}
		}

		// because chatInputSuggestor is private
		((ChatScreenAccessor)this).getChatInputSuggestor().render(matrices, mX, mY);
	}

	@Override
	public void resize(MinecraftClient client, int width, int height) {
		String text = searchField.getText();
		super.resize(client, width, height);
		searchField.setText(text);
		onSearchFieldUpdate(text);

		if(!text.isEmpty())
			searchField.setSuggestion(null);
	}

	@Override
	public void tick() {
		if( searchField.isFocused() ) {
			chatField.setFocused(false);
			searchField.tick();
		} else {
			searchField.setFocused(false);
			chatField.tick();
		}
	}

	@Override
	public void removed() {
		if( !searchField.getText().isEmpty() ) // assuming getText() never returns null...
			ChatSearchScreen.lastLastSearch = Objects.requireNonNullElse(searchField.getText(), "");
		else if( lastSearch != null )
			ChatSearchScreen.lastLastSearch = lastSearch;

		client.inGameHud.getChatHud().reset();
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if( showSettingsMenu && keyCode == GLFW.GLFW_KEY_ESCAPE ) {
			showSettingsMenu = false;
			return true;
		}

		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		// fixes chatField being unselectable
		if( searchField.mouseClicked(mouseX, mouseY, button)) {
			searchField.setFocused(true);
			setFocused(searchField);
			return true;
		} else if( chatField.mouseClicked(mouseX, mouseY, button)) {
			chatField.setFocused(true);
			setFocused(chatField);
			return true;
		}

		// switch button id bc mouseClicked returns if button != 0
		if( searchButton.mouseClicked(mouseX, mouseY, button == 1 ? 0 : button)) {

			if(button == GLFW.GLFW_MOUSE_BUTTON_LEFT)
				showSearch = !showSearch;
			else if(button == GLFW.GLFW_MOUSE_BUTTON_RIGHT)
				showSettingsMenu = !showSettingsMenu;

			return true;
		}

		if( showSettingsMenu ) {
			if( caseSensitive.button.mouseClicked(mouseX, mouseY, button) )
				return true;

			if( modifiers.button.mouseClicked(mouseX, mouseY, button) )
				return true;

			if( regex.button.mouseClicked(mouseX, mouseY, button) )
				return true;
		}

		return super.mouseClicked(mouseX, mouseY, button);
	}


	/** Called when the search field is updated; also sets the regex error and the text input color. */
	private void onSearchFieldUpdate(String text) {
		if( text.equals(lastSearch) )
			return;

		if( !text.isEmpty() ) {
			searchField.setSuggestion(null);

			if( regex.on ) {
				try {
					Pattern.compile(text);
					searchError = null;
				} catch (PatternSyntaxException e) {
					searchError = e;
					client.inGameHud.getChatHud().reset();
				}
			}

			List<ChatHudLine.Visible> filtered = filterMessages( searchError != null ? null : text );
			if(searchError != null) {
				searchField.setEditableColor(0xFF5555);
				client.inGameHud.getChatHud().reset();
			} else if(filtered.isEmpty()) {
				searchField.setEditableColor(0xFFFF55);
				client.inGameHud.getChatHud().reset();
			} else {
				searchField.setEditableColor(0x55FF55);

				ChatUtils.chatHud(client).getVisibleMessages().clear();
				ChatUtils.chatHud(client).getVisibleMessages().addAll( filtered );
			}
		} else {
			client.inGameHud.getChatHud().reset();

			searchError = null;
			searchField.setEditableColor(0xE0E0E0); // default from TextFieldWidget
			searchField.setSuggestion(SUGGESTION_TEXT);
		}

		lastSearch = text;
	}


	/**
	 * Filters all {@link ChatHudLine} messages from the {@link #client}'s ChatHud
	 * matching the target string (configuration applied from {@link #caseSensitive},
	 * {@link #modifiers}, and {@link #regex}) into a list of {@link ChatHudLine.Visible}
	 * visibleMessages to be rendered onto the ChatHud. This does <u>not</u> mutate or modify the
	 * actual {@link ChatHud#messages} list, only the {@link ChatHud#visibleMessages} list
	 * that is automatically repopulated with new messages when needed.
	 */
	private List<ChatHudLine.Visible> filterMessages(String target) {
		if(target == null)
			return createVisibles( ChatUtils.chatHud(client).getMessages() );

		List<ChatHudLine> msgs = Lists.newArrayList( ChatUtils.chatHud(client).getMessages() );

		msgs.removeIf(hudLn -> {
			String content = StringTextUtils.reorder(hudLn.content().asOrderedText(), modifiers.on);

			// note that this NOTs the whole expression to simplify the complex nesting
			// *removes* the message if it *doesn't* match
			return !(
				regex.on
					? content.matches( (caseSensitive.on ? "(?i)" : "") + target )
					: (
						caseSensitive.on
							? content.contains(target)
							: StringUtils.containsIgnoreCase(content, target)
					)
			);
		});

		return createVisibles(msgs);
	}

	/**
	 * Creates a new list of to-be-rendered chat messages from the given list
	 * of chat messages. The steps to achieving this are largely based on
	 * the first half of the {@link ChatHud#addMessage(Text, MessageSignatureData, int, MessageIndicator, boolean)}
	 * method, specifically everything before the {@code while} loop.
	 */
	private List<ChatHudLine.Visible> createVisibles(List<ChatHudLine> messages) {
		List<ChatHudLine.Visible> generated = Lists.newArrayListWithExpectedSize(messages.size());
		ChatHud chatHud = client.inGameHud.getChatHud();

		messages.forEach(hudLn -> {
			MessageIndicator ind = hudLn.indicator();
			int width = (int) ((double)chatHud.getWidth() / chatHud.getChatScale()) - (ind != null && ind.icon() != null ? ind.icon().width + 6 : 0);
			List<OrderedText> list = ChatMessages.breakRenderedChatMessageLines(hudLn.content(), width, this.client.textRenderer);

			for(int i = list.size()-1; i >= 0; --i)
				generated.add(new ChatHudLine.Visible(hudLn.creationTick(), list.get(i), ind, (i == list.size() - 1)));
		});

		return generated;
	}


	// search settings
	public static Setting caseSensitive, modifiers, regex;

	private class Setting {
		private static final Text TOGGLE_OFF = Text.of(": §7[§cX§4=§7]"), TOGGLE_ON = Text.of(": §7[§2=§aO§7]");

		public final ButtonWidget button;
		public boolean on;
		private final Text name;

		public Setting(String key, boolean on, int yOffset) {
			this.name = Text.translatable("text.chatpatches.search." + key);
			this.on = on;


			Text text = name.copy().append( on ? TOGGLE_ON : TOGGLE_OFF );
			ButtonWidget.PressAction UPDATE_BUTTON = button -> {
				this.on = !this.on;
				button.setMessage(this.name.copy().append(this.on ? TOGGLE_ON : TOGGLE_OFF));

				// updates the search text color (ex. switched to regex, now invalid)
				String search = searchField.getText();
				searchField.setText(search + " ");
				searchField.setText(search);
			};

			this.button = ButtonWidget.builder(text, UPDATE_BUTTON)
				.dimensions(8, height + (MENU_Y_OFFSET / 2) - 51 + yOffset, client.textRenderer.getWidth(text.asOrderedText()) + 10, 20)
				.tooltip(Tooltip.of( Text.translatable("text.chatpatches.search.desc." + key) ))
				.build();
		}
	}
}
