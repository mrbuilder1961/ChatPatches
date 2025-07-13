package obro1961.chatpatches.mixin.gui;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.navigation.CommonInputs;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import obro1961.chatpatches.accessor.ChatHudAccess;
import obro1961.chatpatches.accessor.ChatScreenAccess;
import obro1961.chatpatches.config.Config;
import obro1961.chatpatches.gui.ContextMenu;
import obro1961.chatpatches.gui.SearchButton;
import obro1961.chatpatches.util.ChatUtils;
import obro1961.chatpatches.util.RenderUtils;
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
 * Implements {@link ChatScreenAccess} to widen access to
 * critical fields and methods used elsewhere.
 */
@Environment(EnvType.CLIENT)
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin extends Screen implements ChatScreenAccess {
	// search text
	@Unique private static final String SEARCH_SUGGESTION = I18n.get("text.chatpatches.search.suggestion");
	@Unique private static final Component SEARCH_TOOLTIP = Component.translatable("text.chatpatches.search.desc");
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
	@Unique private EditBox searchField;
	@Unique private SearchButton searchButton;
	@Unique private PatternSyntaxException searchError;
	// search settings
	@Unique private boolean showSettingsMenu = false;
	/** @see Config#caseSensitive */
	@Unique private Button caseSensitiveButton;
	/** @see Config#regex */
	@Unique private Button regexButton;

	// ChatScreen fields
	@SuppressWarnings("MissingUnique") //@Shadow
	@NotNull protected Minecraft minecraft = Minecraft.getInstance(); // removes NPE warnings
	@Shadow	protected EditBox input;
	@Shadow private String initial;
	@Shadow private int historyPos;

	/**
	 * Allows access to {@link #input}. Notably used
	 * in {@link ContextMenu#init(Consumer)} for the
	 * {@code #MENU_REPLY} action.
	 */
	public EditBox chatpatches$getChatField() { return input; }

	protected ChatScreenMixin(Component title) { super(title); }

	@Inject(method = "<init>", at = @At("TAIL"))
	private void chatScreenInit(String originalChatText, CallbackInfo ci) {
		if(config.messageDrafting && !messageDraft.isBlank()) {
			// if message drafting is enabled, a draft exists, and SMWYG sent an item message, clear the draft to avoid crashing
			if(FabricLoader.getInstance().isModLoaded("smwyg") && originalChatText.matches("^\\[[\\w\\s]+]$"))
				messageDraft = originalChatText;
			// otherwise if message drafting is enabled, a draft exists and this is not triggered by command key, update the draft
			else if(!originalChatText.equals("/"))
				this.initial = messageDraft;
		}
	}

	/**
	 * @implNote
	 * <ol>
	 *     <li>Initializes the search button</li>
	 *     <li>Initializes the search field</li>
	 *     <li>Initializes the setting options</li>
	 *     <li>If {@link Config#search} is true,
	 *     initializes the search widgets</li>
	 * </ol>
	 */
	@Inject(method = "init", at = @At("TAIL"))
	protected void initSearchWidgets(CallbackInfo ci) {
		searchButton = new SearchButton(2, height - 35, me -> showSearch = !showSearch, me -> showSettingsMenu = !showSettingsMenu);
		searchButton.setTooltip(Tooltip.create(SEARCH_TOOLTIP));

		searchField = new EditBox(minecraft.font, SEARCH_X, height + SEARCH_Y_OFFSET, (int)(width * SEARCH_W_MULT), SEARCH_H, Component.translatable("chat.editBox"));
		searchField.setMaxLength(ChatUtils.MAX_MESSAGE_LENGTH);
		searchField.setBordered(false);
		searchField.setSuggestion(SEARCH_SUGGESTION);
		searchField.setResponder(newText -> onSearchFieldUpdate(newText, false));
		if(config.searchDrafting) {
			searchField.setValue(searchDraft);
			// if necessary, forces the colors to switch + removes suggestion text
			// normally this would be ignored because the field text = searchDraft
			// see (#229)/(#230)
			if(!searchDraft.isEmpty())
				onSearchFieldUpdate(searchField.getValue(), true);
		}

		caseSensitiveButton = makeSettingButton("caseSensitive", 0); // todo redo this thing
		regexButton = makeSettingButton("regex", 22);

		if(config.search) {
			addRenderableWidget(searchButton); // simplifies rendering; it should be called automatically bc it's unconditionally shown or hidden
			addWidget(searchField);
		}
	}

	/**
	 * @implNote In order, renders:
	 * <ol>
	 *     <li>(Everything shifted backwards on the Z-axis in
	 *     order to render under the {@link CommandSuggestions}
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
	 *     <li>If the context menu has been loaded and should show:</li>
	 *     <ol>
	 *     		<li>The outline around the selected chat message</li>
	 *     		<li>The context menu buttons (the menu itself)</li>
	 *     </ol>
	 * </ol>
	 */
	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"))
	private void renderCustomWidgets(GuiGraphics graphics, int mX, int mY, float delta, CallbackInfo ci) {
		//$ pushStack
		graphics.pose().pushMatrix();

		// 1.21.6+ automatically renders everything z=0.1+ relative to the last element :D
		//? if <1.21.6 {
		/*graphics.pose().translate(0, 0, -1); // easiest fix to render everything effectively under the ChatInputSuggestor (#186)
		*///?}

		if(showSearch && config.search) {
			graphics.fill(SEARCH_X - 2, height + SEARCH_Y_OFFSET - 2, (int) (width * (SEARCH_W_MULT + 0.06)), height + SEARCH_Y_OFFSET + SEARCH_H - 2, minecraft.options.getBackgroundColor(Integer.MIN_VALUE));
			searchField.render(graphics, mX, mY, delta);

			// renders a suggestion-esq error message if the regex search is invalid
			if(searchError != null) {
				int x = searchField.getX() + 8 + (int) (width * SEARCH_W_MULT);
				graphics.drawString(font, searchError.getMessage().split(System.lineSeparator())[0], x, searchField.getY(), /*?if >=1.21.6 {*/RenderUtils.opaque/*?}*/(ChatFormatting.DARK_RED.getColor()));
			}
		}

		// renders the bg and the buttons for the settings menu
		if(showSettingsMenu && config.search) {
			graphics.blit(
				// stonecutter: remove qualifier when import optimizer fix is available
				/*? if >=1.21.6 {*/net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,/*?} elif >=1.21.2 {*//*net.minecraft.client.renderer.RenderType::guiTextured,*//*?}*/
				id("textures/gui/search_settings_panel.png"), // todo: use a nine-sliced aka dynamic texture, even better find an existing bg
				MENU_X, height + MENU_Y_OFFSET, 0, 0, MENU_WIDTH, MENU_HEIGHT, MENU_WIDTH, MENU_HEIGHT
			);

			caseSensitiveButton.render(graphics, mX, mY, delta);
			regexButton.render(graphics, mX, mY, delta);
		}

		//$ popStack
		graphics.pose().popMatrix(); // stop shifting before the context menu renders so the chat field doesn't cut it off

		contextMenu.render(graphics, mX, mY, delta);
	}

	/**
	 * Only renders a tooltip if the mouse is not hovering over the (opened)
	 * settings menu or the (shown) context menu. In other words, returns
	 * {@code true} if the mouse is NOT hovering over the <i>opened</i> settings
	 * menu or the <i>shown</i> context menu.
	 * */
	@WrapWithCondition(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;renderComponentHoverEffect(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Style;II)V"))
	public boolean renderTooltipSmartly(GuiGraphics graphics, Font textRenderer, Style style, int mX, int mY) {
		return !isMouseOverSettingsMenu(mX, mY) && !contextMenu.isMouseOver(mX, mY);
	}


	/**
	 * Either resets or saves the drafts for the search and chat fields, depending on
	 * {@link Config#searchDrafting} and {@link Config#messageDrafting}.
	 * Additionally, resets the chat if needed, and closes the context menu.
	 */
	@Inject(method = "removed", at = @At("TAIL"))
	public void onScreenClose(CallbackInfo ci) {
		// we always save the drafts here, we can decide to use them according to the config
		messageDraft = input.getValue();
		searchDraft = searchField.getValue();

		if(!searchField.getValue().isEmpty())
			minecraft.gui.getChat().rescaleChat(); // reset the hud if it had anything in the field (#102)

		contextMenu.close(this::removeWidget);
	}

	/**
	 * Empties the message draft if the screen was closed manually and only
	 * invasive drafting is enabled.
	 * Injects at the super method call because it closes the screen if the
	 * key is an escape key, which is beaten out by the chat screen's redundant
	 * functionality also provided.
	 */
	@Inject(method = "keyPressed", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;keyPressed(III)Z"))
	private void emptyNonInvasiveDrafts(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
		if(config.onlyInvasiveDrafting && keyCode == GLFW.GLFW_KEY_ESCAPE)
			input.setValue(""); // required to empty both the chat field and the messageDraft (later on in #onScreenClose)
	}

	/**
	 * Clears the message draft <b>after</b> a message has been
	 * (successfully) sent. Uses {@link At.Shift#AFTER} to ensure
	 * we don't clear if an error occurs.
	 */
	@Inject(method = "keyPressed", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;setScreen(Lnet/minecraft/client/gui/screens/Screen;)V", ordinal = 1, shift = At.Shift.AFTER))
	private void onMessageSentEmptyDraft(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
		messageDraft = "";
	}

	/**
	 * Lets the {@link #input} widget be focused as intended
	 * when the search field is visible. Seems counterintuitive,
	 * but it works.
	 *
	 * @return {@code (showSearch && config.search) ?
	 * false : input.mouseClicked(x, y, button)}
	 */
	@WrapOperation(method = "mouseClicked", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/EditBox;mouseClicked(DDI)Z"))
	private boolean disableChatFieldFocus(EditBox chatField, double mX, double mY, int button, Operation<Boolean> mouseClicked) {
		// return false (not clicked) if the search field is showing, otherwise delegate to input
		return (!config.search || !showSearch) && mouseClicked.call(chatField, mX, mY, button);
	}
	@WrapOperation(method = "mouseClicked", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent;handleChatQueueClicked(DD)Z"))
	private boolean fixMenuClickthroughClick(ChatComponent chatHud, double mX, double mY, Operation<Boolean> mouseClicked) {
		// return false (not clicked) if the context menu is showing and the mouse is over it, otherwise delegate to chatHud
		return !isMouseOverSettingsMenu(mX, mY) && !contextMenu.isMouseOver(mX, mY) && mouseClicked.call(chatHud, mX, mY);
	}

	@WrapOperation(method = "mouseClicked", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/ChatScreen;getComponentStyleAt(DD)Lnet/minecraft/network/chat/Style;"))
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
			contextMenu.close(this::removeWidget); // closes the menu if it wasn't just created, we don't care if anything was actually clicked

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
			else if(regexButton.mouseClicked(mX, mY, button))
				cir.setReturnValue(true);
		} else if(contextMenu.mouseClicked(mX, mY, button)) {
			contextMenu.close(this::removeWidget);
			cir.setReturnValue(true);
		} else if(button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
			ContextMenu newMenu = new ContextMenu((ChatScreen)(Object)this, mX, mY);
			// if the mouse right-clicked elsewhere and that location can load a context menu, use it
			if(contextMenu.clickPos.x != mX || contextMenu.clickPos.y != mY && newMenu.isFunctional()) {
				contextMenu.close(this::removeWidget);

				newMenu.init(this::addWidget); // load the new menu
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
		if(contextMenu.keyPressed(keyCode, scanCode, modifiers) && CommonInputs.selected(keyCode)) {
			contextMenu.close(this::removeWidget);
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
		return (blockSpaceConsumption && chr == ' ' && input.isFocused()) ? (blockSpaceConsumption = false) : super.charTyped(chr, modifiers);
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
	 *     <li>{@link ChatScreen#commandSuggestions} should not be activated, as it interrupts consecutive keys</li>
	 * </ul>
	 */
	@WrapOperation(method = "keyPressed", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/ChatScreen;moveInHistory(I)V"))
	private void searchHistory(ChatScreen chatScreen, int offset, Operation<Void> setChatFromHistory) {
		if(!config.searchPrefix) {
			setChatFromHistory.call(chatScreen, offset);
			return;
		}

		int cursor = input.getCursorPosition();
		String prefix = input.getValue().substring(0, cursor);
		List<String> history = minecraft.gui.getChat().getRecentChat();

		int newHistoryIndex = historyPos + offset;
		int newOffset = 0;
		while(0 <= newHistoryIndex && newHistoryIndex < history.size()) {
			if(history.get(newHistoryIndex).startsWith(prefix)) {
				newOffset = newHistoryIndex - historyPos;
				break;
			}
			newHistoryIndex += offset;
		}

		setChatFromHistory.call(chatScreen, newOffset);

		input.setCursorPosition(cursor);
		input.setHighlightPos(cursor);
	}


	// New/Unique methods

	@Unique
	private boolean isMouseOverSettingsMenu(double mX, double mY) {
		return showSettingsMenu && (mX >= MENU_X && mX <= MENU_X + MENU_WIDTH && mY >= height + MENU_Y_OFFSET && mY <= height + MENU_Y_OFFSET + MENU_HEIGHT);
	}

	@Unique
	private Button makeSettingButton(String key, int yOffset) {
		Config.Setting<Boolean> setting = config.getOption(key);
		Component name = Component.translatable("text.chatpatches.search." + key);
		Component text = CommonComponents.optionStatus(name, setting.get());

		return Button.builder(text, me -> {
				setting.set(!setting.get()); // toggle the setting
				me.setMessage( CommonComponents.optionStatus(name, setting.get()) ); // update the button text
				onSearchFieldUpdate(searchField.getValue(), true); // update the search field color
				Config.serialize(); // save the setting
			})
			.bounds(
				8, (height + (MENU_Y_OFFSET / 2) - 51) + yOffset,
				minecraft.font.width(text.getString()) + 10, 20
			)
			.tooltip(Tooltip.create( Component.translatable("text.chatpatches.search.desc." + key) ))
			.build();
	}

	/**
	 * Called when the search field is updated, and
	 * applies search settings, field suggestions,
	 * and field coloring.
	 */
	@Unique
	private void onSearchFieldUpdate(String text, boolean refresh) { // fixme: when regex is enabled, saved, and mc is restarted, it doesn't search & color=white until any opt is toggled. may work w CS too(?)
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
					searchField.setTextColor(/*?if >=1.21.6 {*/RenderUtils.opaque/*?}*/(ChatFormatting.RED.getColor())); // mark the text red if the regex is invalid
					minecraft.gui.getChat().rescaleChat();
				}
			} else {
				searchError = null;
				var results = filterMessages(text); // search messages for the target string and return results

				if(results.isEmpty()) {
					// mark the text yellow if there are no results
					searchField.setTextColor(/*?if >=1.21.6 {*/RenderUtils.opaque/*?}*/(ChatFormatting.YELLOW.getColor()));
					minecraft.gui.getChat().rescaleChat();
				} else {
					// mark the text green if there are results
					searchField.setTextColor(/*?if >=1.21.6 {*/RenderUtils.opaque/*?}*/(ChatFormatting.GREEN.getColor()));
				}
			}

		} else {
			searchError = null;
			searchField.setTextColor(EditBox.DEFAULT_TEXT_COLOR);
			searchField.setSuggestion(SEARCH_SUGGESTION);
			minecraft.gui.getChat().rescaleChat();
		}

		searchDraft = text;
	}

	/**
	 * Filters all {@linkplain ChatComponent#allMessages chat messages} using the
	 * given string and according to all search settings, and returns the
	 * generated {@linkplain ChatComponent#trimmedMessages visible messages} as
	 * they are on {@linkplain Gui#chat the chat hud}. This can
	 * be easily reversed by calling {@link ChatComponent#rescaleChat()}.
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
	private List<GuiMessage.Line> filterMessages(String target) { //prepub: re-eval this method, it can def be simplified right? mayhaps even inlined?
		if(target == null)
			return ObjectList.of();

		ChatComponent chatHud = minecraft.gui.getChat();
		ChatHudAccess chat = (ChatHudAccess) chatHud;
		List<GuiMessage> messageSnapshot = List.copyOf(chat.chatpatches$getMessages());

//fixme real issue: changing options removes the `Search...` suggestion for some reason??
		// filter messages by removing those that don't match the target
		chat.chatpatches$getMessages().removeIf(msg -> {
			String text = msg.content().getString();
			// *removes* the message if it *doesn't* match: *keeps* those that *do* match
			return !(
				config.regex
					? text.matches( (config.caseSensitive ? "(?i)" : "") + target )
					: (config.caseSensitive ? text.contains(target) : StringUtils.containsIgnoreCase(text, target))
			);
		});

		// generate the visible messages from the filtered messages
		chatHud.rescaleChat();
		chat.chatpatches$getMessages().clear();
		// add the real messages back to the chat to keep the visual messages
		chat.chatpatches$getMessages().addAll(messageSnapshot);

		return chat.chatpatches$getVisibleMessages();
	}
}