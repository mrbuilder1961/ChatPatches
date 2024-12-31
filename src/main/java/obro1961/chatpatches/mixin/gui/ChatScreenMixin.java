package obro1961.chatpatches.mixin.gui;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.util.ChatMessages;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import obro1961.chatpatches.accessor.ChatHudAccessor;
import obro1961.chatpatches.accessor.ChatScreenAccessor;
import obro1961.chatpatches.config.ChatSearchSetting;
import obro1961.chatpatches.config.Config;
import obro1961.chatpatches.gui.ContextMenu;
import obro1961.chatpatches.gui.SearchButton;
import obro1961.chatpatches.util.RenderUtils;
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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static obro1961.chatpatches.ChatPatches.config;
import static obro1961.chatpatches.ChatPatches.id;
import static obro1961.chatpatches.config.ChatSearchSetting.*;

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
	@Unique private static final String SUGGESTION = I18n.translate("text.chatpatches.search.suggestion");
	@Unique private static final Text SEARCH_TOOLTIP = Text.translatable("text.chatpatches.search.desc");
	// coordinates and positioning
	@Unique private static final int SEARCH_X = 22, SEARCH_Y_OFFSET = -31, SEARCH_H = 12;
	@Unique private static final double SEARCH_W_MULT = 0.25;
	@Unique private static final int MENU_WIDTH = 146, MENU_HEIGHT = 76;
	@Unique private static final int MENU_X = 2, MENU_Y_OFFSET = SEARCH_Y_OFFSET - MENU_HEIGHT - 6;

	// context menu
	@Unique private static ContextMenu contextMenu = ContextMenu.NO_OP;
	// search stuff
	@Unique private static String searchDraft = "";
	@Unique private static String messageDraft = "";

	@Unique private boolean showSearch = true;
	@Unique private boolean showSettingsMenu = false;
	@Unique private TextFieldWidget searchField;
	@Unique private SearchButton searchButton;
	@Unique private PatternSyntaxException searchError;

	// ChatScreen fields
	@SuppressWarnings("MissingUnique") //@Shadow
	@NotNull protected MinecraftClient client = MinecraftClient.getInstance(); // removes NPE warnings
	@Shadow	protected TextFieldWidget chatField;
	@Shadow private String originalChatText;
	@Shadow private int messageHistorySize;

	/**
	 * Allows overriding the chat text stored in {@link #chatField}.
	 * Notably used to clear the message draft in
	 * {@link ScreenMixin#clearMessageDraft} to only do this when
	 * the user closes the ChatScreen; also in
	 * {@link ContextMenu#init(Consumer)} for the
	 * {@code #MENU_REPLY} action.
	 */
	@Unique public void chatpatches$overrideChatText(String str) { chatField.setText(str); }

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
	 *     <li>Initialize the search button</li>
	 *     <li>Initialize the search field</li>
	 *     <li>Initialize the setting options</li>
	 *     <li>If {@link Config#hideSearchButton} is true, hide the search widgets</li>
	 *     <li>Initialize the context menu</li>
	 * </ol>
	 */
	@Inject(method = "init", at = @At("TAIL"))
	protected void initSearchStuff(CallbackInfo ci) {
		searchButton = new SearchButton(2, height - 35, me -> showSearch = !showSearch, me -> showSettingsMenu = !showSettingsMenu);
		searchButton.setTooltip(Tooltip.of(SEARCH_TOOLTIP));

		searchField = new TextFieldWidget(client.textRenderer, SEARCH_X, height + SEARCH_Y_OFFSET, (int)(width * SEARCH_W_MULT), SEARCH_H, Text.translatable("chat.editBox"));
		searchField.setMaxLength(384);
		searchField.setDrawsBackground(false);
		searchField.setSuggestion(SUGGESTION);
		searchField.setChangedListener(this::onSearchFieldUpdate);
		if(config.searchDrafting)
			searchField.setText(searchDraft);

		int yPos = height + (MENU_Y_OFFSET / 2) - 51; // had to extract here cause of mixin restrictions
		ButtonWidget.PressAction updateSearch = button -> onSearchFieldUpdate(searchField.getText());
		caseSensitive.update(yPos, updateSearch);
		modifiers.update(yPos + 22, updateSearch);
		regex.update(yPos + 44, updateSearch);

		if(!config.hideSearchButton) {
			addSelectableChild(searchButton);
			addSelectableChild(searchField);
		}
	}

	/**
	 * @implNote In order, renders:
	 * <ol>
	 *     <li>(Everything shifted backwards on the Z-axis in
	 *     order to render under the {@link ChatInputSuggestor}
	 *     and suggestion text)</li>
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
	private void renderSearchAndContextMenuStuff(DrawContext context, int mX, int mY, float delta, CallbackInfo ci) {
		client.getProfiler().push("chatpatches"); //prepub keep profiler stuff? see ContextMenu#render for more deets

		context.getMatrices().push();
		context.getMatrices().translate(0, 0, -1); // easiest fix to render everything effectively under the ChatInputSuggestor (#186)

		RenderUtils.profile("searchButton", () -> searchButton.render(context, mX, mY, delta));
		if(showSearch && !config.hideSearchButton) {
			RenderUtils.profile("searchField", () -> {
				context.fill(SEARCH_X - 2, height + SEARCH_Y_OFFSET - 2, (int) (width * (SEARCH_W_MULT + 0.06)), height + SEARCH_Y_OFFSET + SEARCH_H - 2, client.options.getTextBackgroundColor(Integer.MIN_VALUE));
				searchField.render(context, mX, mY, delta);

				// renders a suggestion-esq error message if the regex search is invalid
				if(searchError != null)
					RenderUtils.profile("searchError", () -> {
						int x = searchField.getX() + 8 + (int) (width * SEARCH_W_MULT);
						context.drawTextWithShadow(textRenderer, searchError.getMessage().split( System.lineSeparator() )[0], x, searchField.getY(), 0xD00000);
					});
			});
		}

		// renders the bg and the buttons for the settings menu
		if(showSettingsMenu && !config.hideSearchButton) {
			RenderUtils.profile("settingsMenu", () -> {
				context.drawTexture(
					id("textures/gui/search_settings_panel.png"),
					MENU_X, height + MENU_Y_OFFSET, 0, 0, MENU_WIDTH, MENU_HEIGHT, MENU_WIDTH, MENU_HEIGHT
				);

				caseSensitive.button.render(context, mX, mY, delta);
				modifiers.button.render(context, mX, mY, delta);
				regex.button.render(context, mX, mY, delta);
			});
		}

		context.getMatrices().pop(); // stop shifting before the context menu renders so the chat field doesn't cut it off

		// renders the context menu if the settings menu is not open
		if(!isMouseOverSettingsMenu(mX, mY)) //todo does this make sense? what about `!showSettingsMenu`? experiment.
			RenderUtils.profile("contextMenu", () -> contextMenu.render(context, mX, mY, delta));

		client.getProfiler().pop();
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

	@Inject(method = "resize", at = @At("HEAD"))
	public void resizeContextMenu(MinecraftClient client, int width, int height, CallbackInfo ci) {
		contextMenu = ContextMenu.resize(contextMenu, this.width, this.height);
	}

	/**
	 * Either resets or saves the drafts for the search and chat fields, depending on
	 * {@link Config#searchDrafting} and {@link Config#messageDrafting}.
	 * Additionally, resets the chat hud.
	 */
	@Inject(method = "removed", at = @At("TAIL"))
	public void onScreenClose(CallbackInfo ci) {
		if(config.messageDrafting)
			messageDraft = chatField.getText();

		if(config.searchDrafting)
			searchDraft = searchField.getText();
		else if(!searchField.getText().isEmpty()) // reset the hud if it had anything in the field (#102)
			client.inGameHud.getChatHud().reset();

		// todo where needed (#close): unhook buttons from chatscreen drawables first..? or is this even needed...
		contextMenu.close(this::remove);
	}

	/** Closes the settings menu if the escape key was pressed and it was already open, otherwise closes the screen. */
	@Inject(method = "keyPressed", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;setScreen(Lnet/minecraft/client/gui/screen/Screen;)V", ordinal = 0), cancellable = true )
	public void allowClosingSettings(CallbackInfoReturnable<Boolean> cir) {
		if(showSettingsMenu) {
			showSettingsMenu = false;
			cir.setReturnValue(true);
		}
	}
	/** Clears the message draft **AFTER** a message has been (successfully) sent. Uses At.Shift.AFTER to ensure we don't clear if an error occurs */
	@Inject(method = "keyPressed", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;setScreen(Lnet/minecraft/client/gui/screen/Screen;)V", ordinal = 1, shift = At.Shift.AFTER))
	private void onMessageSentEmptyDraft(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
		messageDraft = "";
	}

	/**
	 * Lets the {@link #chatField} widget be focused as intended
	 * when the search field is visible. Seems counterintuitive,
	 * but it works.
	 *
	 * @apiNote todo: see 1.19.2 port comment for more details
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
	private Style fixMenuClickthroughStyle(ChatScreen screen, double mX, double mY, Operation<Style> getTextStyleAt) {
		return (isMouseOverSettingsMenu(mX, mY) || contextMenu.isMouseOver(mX, mY))
			? null
			: getTextStyleAt.call(screen, mX, mY);
	}
	/**
	 * Returns {@code true} if the mouse clicked on any of the following:
	 * <ul>
	 * 		<li>If {@linkplain #isMouseOverSettingsMenu(double, double)
	 * 		the settings menu is visible}, check every search setting:</li>
	 * 		<ul>
	 * 			<li>{@link ChatSearchSetting#caseSensitive}</li>
	 * 			<li>{@link ChatSearchSetting#modifiers}</li>
	 * 			<li>{@link ChatSearchSetting#regex}</li>
	 * 		</ul>
	 * 		<li>Otherwise if the settings menu is closed, see if the
	 *      {@link ContextMenu} should open:</li>
	 * 		<ul>
	 * 			<li>If the mouse right-clicked, tries to load a new context menu</li>
	 * 			<li>Otherwise if the mouse left-clicked and it isn't a
	 * 			{@linkplain ContextMenu#NO_OP no-op}, delegates to
	 * 			{@link ContextMenu#mouseClicked(double, double, int)}</li>
	 * 		</ul>
	 * </ul>
	 * TODO: REWRITE THIS JAVADOC LIST ORDER
	 */
	@Inject(method = "mouseClicked", at = @At("TAIL"), cancellable = true)
	public void registerClickEvents(double mX, double mY, int button, CallbackInfoReturnable<Boolean> cir) {
		if(cir.getReturnValue())
			return;

		if(searchField.mouseClicked(mX, mY, button))
			cir.setReturnValue(true);

		if(isMouseOverSettingsMenu(mX, mY)) {
			if(caseSensitive.button.mouseClicked(mX, mY, button))
				cir.setReturnValue(true);
			if(modifiers.button.mouseClicked(mX, mY, button))
				cir.setReturnValue(true);
			if(regex.button.mouseClicked(mX, mY, button))
				cir.setReturnValue(true);
		} else { // context menu (prepub: clarify what)
			// todo: clicking on search bar w cm open moves selection box to the bottom, clicking on the buttons doesnt close the cm
			// also todo: this can def (really? maybe...) be moved into a static ContextMenu method
			if(button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
				ContextMenu mousePosMenu = ContextMenu.of(mX, mY);
				// if the mouse right-clicked elsewhere and that location can load a context menu, use it
				if(contextMenu.clickPos.x != mX || contextMenu.clickPos.y != mY && mousePosMenu != ContextMenu.NO_OP) {
					contextMenu.close(this::remove); // unhook the old context menu buttons
					contextMenu = mousePosMenu; // keep and use the updated context menu
					contextMenu.init(this::addSelectableChild); // initialize the context menu and register the provided buttons
					cir.setReturnValue(true);
				}
			} else { // if we're not initializing the context menu, then delegate back to it
				//todo: mouse clicks are not registering
				contextMenu.mouseClicked(mX, mY, button);

				// close the menu because if it clicked it should close; otherwise it clicked off and should still close
				cir.setReturnValue(true);
				contextMenu.close(this::remove);
				contextMenu = ContextMenu.NO_OP;
			}
		}
	}

	/**
	 * Provides functionality to the
	 * {@link #contextMenu}.
	 */
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

	/** Called when the search field is updated; also sets the regex error and the text input color. */
	@Unique
	private void onSearchFieldUpdate(String text) {
		if(!text.isEmpty()) {
			searchField.setSuggestion(null);

			// if regex is enabled and the text is invalid, set the error and color
			if(regex.on) {
				try {
					Pattern.compile(text);
					searchError = null;
				} catch(PatternSyntaxException e) {
					searchError = e;
					searchField.setEditableColor(0xFF5555);
					client.inGameHud.getChatHud().reset();
				}
			}

			List<ChatHudLine.Visible> searchResults = filterMessages( searchError != null ? null : text );
			if(searchError == null && searchResults.isEmpty()) { // mark the text yellow if there are no results
				searchField.setEditableColor(0xFFFF55);
				client.inGameHud.getChatHud().reset();
			} else if(!searchResults.isEmpty()) { // mark the text green if there are results, and only show those
				searchField.setEditableColor(0x55FF55);

				ChatHudAccessor chat = (ChatHudAccessor) client.inGameHud.getChatHud();
				chat.chatpatches$getVisibleMessages().clear();
				chat.chatpatches$getVisibleMessages().addAll(searchResults);
			}
		} else {
			client.inGameHud.getChatHud().reset();

			searchError = null;
			searchField.setEditableColor(0xE0E0E0); // default from TextFieldWidget
			searchField.setSuggestion(SUGGESTION);
		}

		searchDraft = text;
	}

	/**
	 * Filters all {@link ChatHudLine} messages from the {@link #client}'s ChatHud
	 * matching the target string (configuration applied from {@link ChatSearchSetting#caseSensitive},
	 * {@link ChatSearchSetting#modifiers}, and {@link ChatSearchSetting#regex}) into a list of
	 * {@link ChatHudLine.Visible} visibleMessages to be rendered onto the ChatHud. This does <u>not</u>
	 * mutate or modify the actual {@link ChatHud#messages} list, only the {@link ChatHud#visibleMessages}
	 * list that is automatically repopulated with new messages when needed.
	 */
	@Unique
	private List<ChatHudLine.Visible> filterMessages(String target) {
		if(target == null) // todo || target.isEmpty() ?
			// todo this might be a good idea to uncomment, just make sure to re-analyze side effects of this method
			// counter todo: just call chatHud.refresh() instead of manually re-writing the method and calling it? idk look into this
			return List.of(); //createVisibles( chatHud.chatpatches$getMessages() );

		List<ChatHudLine> msgs = new ArrayList<>( ((ChatHudAccessor) client.inGameHud.getChatHud()).chatpatches$getMessages() );

		msgs.removeIf(hudLn -> {
			String content = TextUtils.reorder(hudLn.content().asOrderedText(), modifiers.on);

			// note that this NOTs the whole expression to simplify the complex nesting
			// *removes* the message if it *doesn't* match AKA *keeps* those that *do* match
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
	@Unique
	private List<ChatHudLine.Visible> createVisibles(List<ChatHudLine> messages) {
		List<ChatHudLine.Visible> generated = new ArrayList<>(messages.size());
		ChatHud chatHud = client.inGameHud.getChatHud();
		int width = (int)(chatHud.getWidth() / chatHud.getChatScale());

		messages.forEach(hudLn -> {
			MessageIndicator ind = hudLn.indicator();
			int indDiff = (ind != null && ind.icon() != null) ? ind.icon().width + 6 : 0;
			List<OrderedText> list = ChatMessages.breakRenderedChatMessageLines(hudLn.content(), width - indDiff, client.textRenderer);

			for(int i = list.size() - 1; i >= 0; --i)
				generated.add(new ChatHudLine.Visible(hudLn.creationTick(), list.get(i), ind, (i == list.size() - 1)));
		});

		return generated;
	}
}