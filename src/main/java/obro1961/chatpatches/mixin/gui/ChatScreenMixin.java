package obro1961.chatpatches.mixin.gui;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyCodes;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import obro1961.chatpatches.accessor.ChatHudAccessor;
import obro1961.chatpatches.accessor.ChatScreenAccessor;
import obro1961.chatpatches.config.Config;
import obro1961.chatpatches.gui.ContextMenu;
import obro1961.chatpatches.gui.SearchButton;
import obro1961.chatpatches.util.ChatUtils;
import obro1961.chatpatches.util.TextUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static obro1961.chatpatches.ChatPatches.config;
import static obro1961.chatpatches.ChatPatches.id;

/**
 * The main entrypoint mixin for chat GUI modifications,
 * notably search and context menu functionality.
 * Implements {@link ChatScreenAccessor} to widen access to
 * critical fields and methods used elsewhere.
 */
@Environment(EnvType.CLIENT)
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin extends Screen implements ChatScreenAccessor {
	// search text
	@Unique private static final String SEARCH_SUGGESTION = I18n.translate("text.chatpatches.search.suggestion");
	@Unique private static final Text SEARCH_TOOLTIP = Text.translatable("text.chatpatches.search.desc");
	// coordinates and positioning
	@Unique private static final int SEARCH_X = 22,
									 SEARCH_Y_OFFSET = -31,
									 SEARCH_H = 12;
	@Unique private static final double SEARCH_W_MULT = 0.25;
	@Unique private static final int MENU_WIDTH = 146,
									 MENU_HEIGHT = 76,
									 MENU_X = 2,
									 MENU_Y_OFFSET = SEARCH_Y_OFFSET - MENU_HEIGHT - 6;
	// context menu
	@Unique private static ContextMenu contextMenu = new ContextMenu(null, -1, -1);
	/**
	 * @see #charTyped(char, int)
	 */
	@Unique private boolean blockSpaceConsumption = false;
	// search stuff
	@Unique private static String searchDraft = "";
	@Unique private static String messageDraft = "";

	@Unique private boolean showSearch = true;
	@Unique private TextFieldWidget searchField;
	@Unique private SearchButton searchButton;
	@Unique private PatternSyntaxException searchError;
	// search settings
	@Unique private boolean showSettingsMenu = false;
	/** @see Config#caseSensitive */
	@Unique private ButtonWidget caseSensitiveButton;
	/** @see Config#formatting */
	@Unique private ButtonWidget formattingButton;
	/** @see Config#regex */
	@Unique private ButtonWidget regexButton;

	// ChatScreen fields
	@SuppressWarnings("MissingUnique") //@Shadow
	@NotNull protected MinecraftClient client = MinecraftClient.getInstance(); // removes NPE warnings
	@Shadow	protected TextFieldWidget chatField;
	@Shadow private String originalChatText;
	@Shadow private int messageHistorySize;

	/**
	 * Allows access to {@link #chatField}. Notably used
	 * in {@link ContextMenu#init(Consumer)} for the
	 * {@code #MENU_REPLY} action.
	 */
	public TextFieldWidget chatpatches$getChatField() { return chatField; }

	protected ChatScreenMixin(Text title) { super(title); }

	@Inject(method = "<init>", at = @At("TAIL"))
	private void chatScreenInit(String originalChatText, CallbackInfo ci) {
		if(config.messageDrafting && !messageDraft.isBlank()) {
			// if message drafting is enabled, a draft exists, and SMWYG sent an item message, clear the draft to avoid crashing
			if(FabricLoader.getInstance().isModLoaded("smwyg") && originalChatText.matches("^\\[[\\w\\s]+]$"))
				messageDraft = originalChatText;
			// otherwise if message drafting is enabled, a draft exists and this is not triggered by command key, update the draft
			else if(!originalChatText.equals("/"))
				this.originalChatText = messageDraft;
		}
	}

	/**
	 * @implNote
	 * <ol>
	 *     <li>Initializes the search button</li>
	 *     <li>Initializes the search field</li>
	 *     <li>Initializes the setting options</li>
	 *     <li>If {@link Config#hideSearchButton} is true,
	 *     hides the search widgets</li>
	 * </ol>
	 */
	@Inject(method = "init", at = @At("TAIL"))
	protected void initSearchWidgets(CallbackInfo ci) {
		searchButton = new SearchButton(2, height - 35, me -> showSearch = !showSearch, me -> showSettingsMenu = !showSettingsMenu);
		searchButton.setTooltip(Tooltip.of(SEARCH_TOOLTIP));

		searchField = new TextFieldWidget(client.textRenderer, SEARCH_X, height + SEARCH_Y_OFFSET, (int)(width * SEARCH_W_MULT), SEARCH_H, Text.translatable("chat.editBox"));
		searchField.setMaxLength(ChatUtils.MAX_MESSAGE_LENGTH);
		searchField.setDrawsBackground(false);
		searchField.setSuggestion(SEARCH_SUGGESTION);
		searchField.setChangedListener(newText -> onSearchFieldUpdate(newText, false));
		if(config.searchDrafting)
			searchField.setText( searchDraft.length() > 1 ? searchDraft.substring(1) : "" ); // remove the null char from the draft

		caseSensitiveButton = makeSettingButton("caseSensitive", 0);
		formattingButton = makeSettingButton("formatting", 22);
		regexButton = makeSettingButton("regex", 44);

		if(!config.hideSearchButton) {
			addDrawableChild(searchButton); // simplifies rendering; it should be called automatically bc it's unconditionally shown or hidden
			addSelectableChild(searchField);
		}
	}

	/**
	 * @implNote In order, renders:
	 * <ol>
	 *     <li>(Everything shifted backwards on the Z-axis in
	 *     order to render under the {@link ChatInputSuggestor}
	 *     and suggestion text, see <a href="https://github.com/mrbuilder1961/ChatPatches/issues/186">
	 *     (#186)</a>)</li>
	 *     <li>The {@link #searchButton}</li>
	 *     <li>If the search bar should show:</li>
	 *     <ol>
	 *     		<li>The {@link #searchField} background</li>
	 *     		<li>The {@link #searchField} itself</li>
	 *     		<li>If it isn't null, the {@link #searchError}</li>
	 *     </ol>
	 *	   <li>If the settings menu should show:</li>
	 *     <ol>
	 *     		<li>The settings menu background</li>
	 *     		<li>The setting buttons themselves</li>
	 *     </ol>
	 *     <li>If the copy menu has been loaded and should show:</li>
	 *     <ol>
	 *     		<li>The outline around the selected chat message</li>
	 *     		<li>The copy menu buttons (the menu itself)</li>
	 *     </ol>
	 * </ol>
	 */
	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/Screen;render(Lnet/minecraft/client/gui/DrawContext;IIF)V"))
	private void renderCustomWidgets(DrawContext context, int mX, int mY, float delta, CallbackInfo ci) {
		context.getMatrices().push();
		context.getMatrices().translate(0, 0, -1); // easiest fix to render everything effectively under the ChatInputSuggestor (#186)

		if(showSearch && !config.hideSearchButton) {
			context.fill(SEARCH_X - 2, height + SEARCH_Y_OFFSET - 2, (int) (width * (SEARCH_W_MULT + 0.06)), height + SEARCH_Y_OFFSET + SEARCH_H - 2, client.options.getTextBackgroundColor(Integer.MIN_VALUE));
			searchField.render(context, mX, mY, delta);

			// renders a suggestion-esq error message if the regex search is invalid
			if(searchError != null) {
				int x = searchField.getX() + 8 + (int) (width * SEARCH_W_MULT);

				context.drawTextWithShadow(textRenderer, searchError.getMessage().split(System.lineSeparator())[0], x, searchField.getY(), Formatting.DARK_RED.getColorValue());
			}
		}

		// renders the bg and the buttons for the settings menu
		if(showSettingsMenu && !config.hideSearchButton) {
			context.drawTexture(
				id("textures/gui/search_settings_panel.png"),
				MENU_X, height + MENU_Y_OFFSET, 0, 0, MENU_WIDTH, MENU_HEIGHT, MENU_WIDTH, MENU_HEIGHT
			);

			caseSensitiveButton.render(context, mX, mY, delta);
			formattingButton.render(context, mX, mY, delta);
			regexButton.render(context, mX, mY, delta);
		}

		context.getMatrices().pop(); // stop shifting before the context menu renders so the chat field doesn't cut it off

		contextMenu.render(context, mX, mY, delta);
	}

	/**
	 * Only renders a tooltip if the mouse is not hovering over the (opened)
	 * settings menu or the (shown) context menu. In other words, returns
	 * {@code true} if the mouse is NOT hovering over the <i>opened</i> settings
	 * menu or the <i>shown</i> context menu.
	 * */
	@WrapWithCondition(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawHoverEvent(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Style;II)V"))
	public boolean renderTooltipSmartly(DrawContext drawContext, TextRenderer textRenderer, Style style, int mX, int mY) {
		return !isMouseOverSettingsMenu(mX, mY) && !contextMenu.isMouseOver(mX, mY);
	}


	/**
	 * Either resets or saves the drafts for the search and chat fields, depending on
	 * {@link Config#searchDrafting} and {@link Config#messageDrafting}.
	 * Additionally, resets the chat if needed, and closes the context menu.
	 */
	@Inject(method = "removed", at = @At("TAIL"))
	public void onScreenClose(CallbackInfo ci) {
		messageDraft = config.messageDrafting ? chatField.getText() : "";

		if(config.searchDrafting)
			searchDraft = '\u0000' + searchField.getText(); // lead with a null char to remove it later and trigger the search field update
		else if(!searchField.getText().isEmpty())
			client.inGameHud.getChatHud().reset(); // reset the hud if it had anything in the field (#102)

		contextMenu.close(this::remove);
	}

	/**
	 * Empties the message draft if the screen was closed manually and only
	 * invasive drafting is enabled.
	 * Injects at the super method call because it closes the screen if the
	 * key is an escape key, which is beaten out by the chat screen's redundant
	 * functionality also provided.
	 */
	@Inject(method = "keyPressed", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/Screen;keyPressed(III)Z"))
	private void emptyNonInvasiveDrafts(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
		if(config.onlyInvasiveDrafting && keyCode == GLFW.GLFW_KEY_ESCAPE)
			chatField.setText(""); // required to empty both the chat field and the messageDraft (later on in #onScreenClose)
	}

	/**
	 * Clears the message draft <b>after</b> a message has been
	 * (successfully) sent. Uses {@link At.Shift#AFTER} to ensure
	 * we don't clear if an error occurs.
	 */
	@Inject(method = "keyPressed", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;setScreen(Lnet/minecraft/client/gui/screen/Screen;)V", ordinal = 1, shift = At.Shift.AFTER))
	private void onMessageSentEmptyDraft(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
		messageDraft = "";
	}

	/**
	 * Lets the {@link #chatField} widget be focused as intended
	 * when the search field is visible. Seems counterintuitive,
	 * but it works.
	 *
	 * @return {@code (showSearch && !config.hideSearchButton) ?
	 * false : chatField.mouseClicked(x, y, button)}
	 */
	@WrapOperation(method = "mouseClicked", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/widget/TextFieldWidget;mouseClicked(DDI)Z"))
	private boolean disableChatFieldFocus(TextFieldWidget chatField, double mX, double mY, int button, Operation<Boolean> mouseClicked) {
		// return false (not clicked) if the search field is showing, otherwise delegate to chatField
		return (config.hideSearchButton || !showSearch) && mouseClicked.call(chatField, mX, mY, button);
	}
	@WrapOperation(method = "mouseClicked", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/ChatHud;mouseClicked(DD)Z"))
	private boolean fixMenuClickthroughClick(ChatHud chatHud, double mX, double mY, Operation<Boolean> mouseClicked) {
		// return false (not clicked) if the context menu is showing and the mouse is over it, otherwise delegate to chatHud
		return !isMouseOverSettingsMenu(mX, mY) && !contextMenu.isMouseOver(mX, mY) && mouseClicked.call(chatHud, mX, mY);
	}

	@WrapOperation(method = "mouseClicked", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/ChatScreen;getTextStyleAt(DD)Lnet/minecraft/text/Style;"))
	private Style fixStyleClickthrough(ChatScreen screen, double mX, double mY, Operation<Style> getTextStyleAt) {
		return (isMouseOverSettingsMenu(mX, mY) || contextMenu.isMouseOver(mX, mY))
			? null
			: getTextStyleAt.call(screen, mX, mY);
	}

	/**
	 * We always want to close the context menu,
	 * EXCEPT when we created one (via right-click).
	 *
	 * @apiNote This wrapper can be called a LOT, so
	 * we want to keep its footprint as minimal as
	 * possible.
	 */
	@WrapMethod(method = "mouseClicked")
	private boolean fixContextMenuNotClosing(double mX, double mY, int button, Operation<Boolean> mouseClicked) {
		boolean clicked = mouseClicked.call(mX, mY, button);

		if(button != GLFW.GLFW_MOUSE_BUTTON_RIGHT)
			contextMenu.close(this::remove); // closes the menu if it wasn't just created, we don't care if anything was actually clicked

		return clicked;
	}

	/**
	 * Returns {@code true} if the mouse previously clicked on an
	 * existing chat widget, or if it clicked on any of the following:
	 * <ol>
	 * 		<li>The {@linkplain #searchField search bar}</li>
	 * 		<li>If {@linkplain #isMouseOverSettingsMenu(double, double)
	 * 		the settings menu is visible}:</li>
	 * 		<ol>
	 * 			<li>The {@linkplain #caseSensitiveButton case sensitive
	 * 			button}</li>
	 * 			<li>The {@linkplain #formattingButton formatting button}</li>
	 * 			<li>The {@linkplain #regexButton regex button}</li>
	 * 		</ol>
	 * 		<li>Otherwise, anything encapsulated by the
	 * 		{@linkplain ContextMenu#mouseClicked(double, double, int)
	 * 		context menu}</li>
	 * 		<li>Finally, the chat box in an attempt to make a new context
	 * 		menu. If the mouse clicked successfully:</li>
	 * 		<ol>
	 * 			<li>Unloads the old context menu</li>
	 * 			<li>Saves the new one</li>
	 * 			<li>Initializes it</li>
	 * 			<li>Focuses it</li>
	 * 		</ol>
	 * </ol>
	 */
	@Inject(method = "mouseClicked", at = @At("TAIL"), cancellable = true)
	public void mouseClickedEvents(double mX, double mY, int button, CallbackInfoReturnable<Boolean> cir) {
		if(cir.getReturnValueZ())
			return;

		if(searchField.mouseClicked(mX, mY, button))
			cir.setReturnValue(true);

		if(isMouseOverSettingsMenu(mX, mY)) {
			if(caseSensitiveButton.mouseClicked(mX, mY, button))
				cir.setReturnValue(true);
			else if(formattingButton.mouseClicked(mX, mY, button))
				cir.setReturnValue(true);
			else if(regexButton.mouseClicked(mX, mY, button))
				cir.setReturnValue(true);
		} else if(contextMenu.mouseClicked(mX, mY, button)) {
			contextMenu.close(this::remove);
			cir.setReturnValue(true);
		} else if(button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
			ContextMenu newMenu = new ContextMenu((ChatScreen)(Object)this, mX, mY);
			// if the mouse right-clicked elsewhere and that location can load a context menu, use it
			if(contextMenu.clickPos.x != mX || contextMenu.clickPos.y != mY && newMenu.isFunctional()) {
				contextMenu.close(this::remove);

				newMenu.init(this::addSelectableChild); // load the new menu
				setFocused(newMenu); // shift focus from the chat field
				contextMenu = newMenu;

				cir.setReturnValue(true);
			}
		}
	}

	/**
	 * Allows the context menu to consume key presses for
	 * accessibility tabbing and registering button clicks
	 * properly (closing the menu after successful keystrokes).
	 *
	 * @see ContextMenu#keyPressed(int, int, int)
	 * @see #charTyped(char, int)
	 */
	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void allowContextMenuKeyPressing(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
		// keyPressed must be called first otherwise tabbing will not work
		if(contextMenu.keyPressed(keyCode, scanCode, modifiers) && KeyCodes.isToggle(keyCode)) {
			contextMenu.close(this::remove);
			blockSpaceConsumption = true; // see #charTyped
			cir.setReturnValue(true);
		}
	}


	/**
	 * Blocks the chat field from consuming space characters {@linkplain #blockSpaceConsumption
	 * if it shouldn't}. Prevents a space being both entered into the chat field and also
	 * triggering a context menu button press.
	 *
	 * @implNote If {@link #blockSpaceConsumption} is true, the character is a space, and the chat field
	 * is focused, sets {@link #blockSpaceConsumption} to false and returns such. Otherwise, delegates to
	 * {@linkplain Screen#charTyped(char, int) <code>super#charTyped</code>}.
	 */
	@Override
	public boolean charTyped(char chr, int modifiers) {
		return (blockSpaceConsumption && chr == ' ' && chatField.isFocused()) ? (blockSpaceConsumption = false) : super.charTyped(chr, modifiers);
	}

	@Override
	public void mouseMoved(double mX, double mY) {
		contextMenu.mouseMoved(mX, mY);
	}

	/**
	 * Searches chat history for a message with the same prefix before the cursor when pressing
	 * arrow keys, instead of scrolling to the immediate previous/next sent message.
	 * <br><br>
	 * Mimics the Bash <i>history-search-backward</i> and <i>history-search-forward</i> features.
	 *
	 * @implSpec
	 * <ul>
	 *     <li>To support consecutive keys, the cursor should not move (but the text selection may cancel)</li>
	 *     <li>{@link ChatScreen#chatInputSuggestor} should not be activated, as it interrupts consecutive keys</li>
	 * </ul>
	 */
	@WrapOperation(method = "keyPressed", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/ChatScreen;setChatFromHistory(I)V"))
	private void searchHistory(ChatScreen chatScreen, int offset, Operation<Void> setChatFromHistory) {
		if(!config.searchPrefix) {
			setChatFromHistory.call(chatScreen, offset);
			return;
		}

		int cursor = chatField.getCursor();
		String prefix = chatField.getText().substring(0, cursor);
		List<String> history = client.inGameHud.getChatHud().getMessageHistory();

		int newHistoryIndex = messageHistorySize + offset;
		int newOffset = 0;
		while(0 <= newHistoryIndex && newHistoryIndex < history.size()) {
			if(history.get(newHistoryIndex).startsWith(prefix)) {
				newOffset = newHistoryIndex - messageHistorySize;
				break;
			}
			newHistoryIndex += offset;
		}

		setChatFromHistory.call(chatScreen, newOffset);

		chatField.setSelectionStart(cursor);
		chatField.setSelectionEnd(cursor);
	}


	// New/Unique methods

	@Unique
	private boolean isMouseOverSettingsMenu(double mX, double mY) {
		return showSettingsMenu && (mX >= MENU_X && mX <= MENU_X + MENU_WIDTH && mY >= height + MENU_Y_OFFSET && mY <= height + MENU_Y_OFFSET + MENU_HEIGHT);
	}

	@Unique
	private ButtonWidget makeSettingButton(String key, int yOffset) {
		Config.Setting<Boolean> setting = config.getOption(key);
		Text name = Text.translatable("text.chatpatches.search." + key);
		Text text = ScreenTexts.composeToggleText(name, setting.get());

		return ButtonWidget.builder(text, me -> {
				setting.set(!setting.get()); // toggle the setting
				me.setMessage( ScreenTexts.composeToggleText(name, setting.get()) ); // update the button text
				onSearchFieldUpdate(searchField.getText(), true); // update the search field color
			})
			.dimensions(
				8, (height + (MENU_Y_OFFSET / 2) - 51) + yOffset,
				client.textRenderer.getWidth(text.getString()) + 10, 20
			)
			.tooltip(Tooltip.of( Text.translatable("text.chatpatches.search.desc." + key) ))
			.build();
	}

	/**
	 * Called when the search field is updated, and
	 * applies search settings, field suggestions,
	 * and field coloring.
	 */
	@Unique
	private void onSearchFieldUpdate(String text, boolean refresh) {
		if(text.equals(searchDraft) && !refresh)
			return; // prevent useless updates

		if(!text.isEmpty() || refresh) {
			searchField.setSuggestion(null);

			// if regex is enabled and the text is invalid, set the error and color
			if(config.regex) {
				try {
					Pattern.compile(text);
					searchError = null;
				} catch(PatternSyntaxException e) {
					searchError = e;
					searchField.setEditableColor(Formatting.RED.getColorValue()); // mark the text red if the regex is invalid
					client.inGameHud.getChatHud().reset();
				}
			} else {
				searchError = null;
				var results = filterMessages(text); // search messages for the target string and return results

				if(results.isEmpty()) {
					// mark the text yellow if there are no results
					searchField.setEditableColor(Formatting.YELLOW.getColorValue());
					client.inGameHud.getChatHud().reset();
				} else {
					// mark the text green if there are results
					searchField.setEditableColor(Formatting.GREEN.getColorValue());
				}
			}

		} else {
			searchError = null;
			searchField.setEditableColor(TextFieldWidget.DEFAULT_EDITABLE_COLOR);
			searchField.setSuggestion(SEARCH_SUGGESTION);
			client.inGameHud.getChatHud().reset();
		}

		searchDraft = text;
	}

	/**
	 * Filters all {@linkplain ChatHud#messages chat messages} using the
	 * given string and according to all search settings, and returns the
	 * generated {@linkplain ChatHud#visibleMessages visible messages} as
	 * they are on {@linkplain InGameHud#chatHud the chat hud}. This can
	 * be easily reversed by calling {@link ChatHud#reset()}.
	 *
	 * @return Whether the search was successful and modified visible
	 * messages.
	 * @implNote This method momentarily mutates the chat hud's messages
	 * to filter out messages that don't match the target string,
	 * then resets the chat hud to generate the visible messages
	 * from the filtered messages. This method does not
	 * <u>effectively</u> modify the original messages, only the
	 * visible messages.
	 */
	@Unique
	private List<ChatHudLine.Visible> filterMessages(String target) {
		if(target == null)
			return ObjectList.of();

		ChatHud chatHud = client.inGameHud.getChatHud();
		ChatHudAccessor chat = (ChatHudAccessor) chatHud;
		List<ChatHudLine> messageSnapshot = List.copyOf(chat.chatpatches$getMessages());

		// filter messages by removing those that don't match the target
		chat.chatpatches$getMessages().removeIf(msg -> {
			String text = config.formatting ? TextUtils.reorder(msg.content().asOrderedText(), true) : msg.content().getString();

			// note that this NOTs the whole expression to simplify the complex nesting
			// *removes* the message if it *doesn't* match AKA *keeps* those that *do* match
			return !(
				config.regex
					? text.matches( (config.caseSensitive ? "(?i)" : "") + target )
					: (config.caseSensitive ? text.contains(target) : StringUtils.containsIgnoreCase(text, target))
			);
		});
		// generate the visible messages from the filtered messages
		chatHud.reset();
		chat.chatpatches$getMessages().clear();
		// add the real messages back to the chat to keep the visual messages
		chat.chatpatches$getMessages().addAll(messageSnapshot);

		return chat.chatpatches$getVisibleMessages();
	}
}