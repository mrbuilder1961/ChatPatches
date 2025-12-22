package obro1961.chatpatches.mixin.gui;

//? if >=1.21.9 {
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
//?}
//? if <=1.21.10 {
//import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
//?}
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import it.unimi.dsi.fastutil.booleans.BooleanArrayList;
import it.unimi.dsi.fastutil.booleans.BooleanList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
//? if >=1.21.11 {
import net.minecraft.client.gui.ActiveTextCollector;
//?} else {
//import net.minecraft.client.gui.Font;
//?}
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
//? if >=1.21.9 {
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
//?} else {
//import net.minecraft.client.gui.navigation.CommonInputs;
//?}
//? if >=1.21.6 {
import net.minecraft.client.renderer.RenderPipelines;
//?} else if >=1.21.2 {
//import net.minecraft.client.renderer.RenderType;
//?}
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
//? if <=1.21.10 {
//import net.minecraft.network.chat.Style;
//?}
import obro1961.chatpatches.ChatLog;
import obro1961.chatpatches.accessor.ChatScreenAccess;
import obro1961.chatpatches.config.Config;
import obro1961.chatpatches.gui.ContextMenu;
import obro1961.chatpatches.gui.SearchButton;
import obro1961.chatpatches.util.ChatUtil;
import obro1961.chatpatches.util.RenderUtil;
import org.apache.commons.lang3./*? if >=1.21.11 {*/Strings/*?} else {*//*StringUtils*//*?}*/;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Intrinsic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;
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
	@Shadow	public EditBox input;
	@SuppressWarnings("MissingUnique") //@Shadow
	@NotNull protected Minecraft minecraft = Minecraft.getInstance(); // removes NPE warnings
	@Shadow protected String initial;
	@Shadow private int historyPos;


	// search text
	@Unique private static final String SEARCH_SUGGESTION = I18n.get("text.chatpatches.search.suggestion");
	@Unique private static final Component SEARCH_TOOLTIP = Component.translatable("text.chatpatches.search.desc");
	/**
	 * @see #chatScreenInit(String, boolean, CallbackInfo)
	 */
	@Unique private static final Matcher SMWYG_ITEM_PATTERN = Pattern.compile("^\\[[\\w\\s]+]$").matcher("");

	// coordinates and positioning // todo: remove magic numbers from the math where these are used
	@Unique private static final int SEARCH_X = 22,
									 SEARCH_Y_OFFSET = -31,
									 SEARCH_HEIGHT = 12;
	// prepub: make this instead a minimum value to hold the suggestion plus a bit of padding and then make it bigger when you type with a max right before the hotbar!
	@Unique private static final double SEARCH_W_MULT = 0.25;
	@Unique private static final int MENU_WIDTH = 146,
									 MENU_HEIGHT = 76,
									 MENU_X = 2,
									 MENU_Y_OFFSET = SEARCH_Y_OFFSET - MENU_HEIGHT - 6;

	// context menu
	@Unique private static ContextMenu contextMenu = new ContextMenu(null, -1, -1);
	/**
	 * @see #charTyped(CharacterEvent)
	 */
	@Unique private /*static*/ boolean blockSpaceConsumption = false;

	// search stuff
	/**
	 * Avoids costly disk writes when messing with search settings.
	 *
	 * @see #onScreenClose(CallbackInfo)
	 */
	@Unique private static final BooleanList searchSettings = new BooleanArrayList(List.of(config.caseSensitive, config.regex));
	/**
	 * Allows the context menu to accurately copy searched messages.
	 * @see ContextMenu#ContextMenu(ChatScreen, double, double)
	 */
	@Unique private static final List<GuiMessage> searchResults = new ObjectArrayList<>();
	@Unique private static boolean showSearchBar = true;
	/**
	 * Caches the compiled pattern matcher to slightly optimize the search regex, so
	 * it can be reused during the search. Initialized to a pattern that lazily
	 * matches everything with an empty string as input, as to return a match as fast
	 * as possible. Only used when {@link Config#regex} is enabled.
	 */
	@Unique private static Matcher searchMatcher = Pattern.compile(".*?").matcher("");
	@Unique private static String searchDraft = "";
	@Unique private static String messageDraft = "";

	@Unique private EditBox searchField;
	@Unique private SearchButton searchButton;
	@Unique private PatternSyntaxException searchError;

	// search settings
	@Unique private boolean showSettingsMenu = false;
	/** @see Config#caseSensitive */
	@Unique private Button caseSensitiveButton;
	/** @see Config#regex */
	@Unique private Button regexButton;

	protected ChatScreenMixin(Component title) { super(title); }

	@Inject(method = "<init>", at = @At("TAIL"))
	private void chatScreenInit(String initialChat, /*? if >=1.21.9 {*/ boolean isDraft, /*?}*/ CallbackInfo ci) {
		// don't touch this unless you're a pro at drafts or have 4+ free hours

		if(initialChat.equals("/")) {
			return; // no reason to mess with or draft blank commands - emptied in #onScreenClose
		}

		if((config.messageDrafting || config.onlyInvasiveDrafting) && !messageDraft.isBlank()) {
			if(FabricLoader.getInstance().isModLoaded("smwyg") && SMWYG_ITEM_PATTERN.reset(initialChat).matches()) {
				// if message drafting is enabled, a draft exists, and SMWYG sent an item message: clear the draft to avoid crashing
				messageDraft = initialChat;
			} else {
				// otherwise if message drafting is enabled and a draft exists: update the draft
				initial = messageDraft;
			}
		}

		//? if >=1.21.9 {
		else if(!config.messageDrafting && !minecraft.options.saveChatDrafts().get()) {
			// finally, if message drafting is disabled and save unsent messages is too, delete the draft
			initial = "";
		}
		/*?}*/
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
		searchButton = new SearchButton(2, height - 35, me -> showSearchBar = !showSearchBar, me -> showSettingsMenu = !showSettingsMenu);
		searchButton.setTooltip(Tooltip.create(SEARCH_TOOLTIP));

		searchField = new EditBox(minecraft.font, SEARCH_X, height + SEARCH_Y_OFFSET, (int)(width * SEARCH_W_MULT), SEARCH_HEIGHT, Component.translatable("chat.editBox"));
		searchField.setMaxLength(ChatUtil.MAX_MESSAGE_LENGTH);
		searchField.setBordered(false);
		searchField.setSuggestion(SEARCH_SUGGESTION);
		searchField.setResponder(newText -> onSearchFieldUpdate(newText, false));
		if(config.searchDrafting) {
			searchField.setValue(searchDraft);
			// if necessary, forces the colors to switch + removes suggestion text
			// normally this would be ignored because the field text = searchDraft
			// see #229/#230
			if(!searchDraft.isEmpty()) {
				onSearchFieldUpdate(searchField.getValue(), true);
			}
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
		//$ push_stack
		graphics.pose().pushMatrix();

		// 1.21.6+ automatically renders everything z=0.1+ relative to the last element :D
		//? if <1.21.6 {
		/*graphics.pose().translate(0, 0, -1); // easiest fix to render everything effectively under the ChatInputSuggestor (#186)
		*///?}

		if(showSearchBar && config.search) {
			graphics.fill(SEARCH_X - 2, height + SEARCH_Y_OFFSET - 2, (int) (width * (SEARCH_W_MULT + 0.06)), height + SEARCH_Y_OFFSET + SEARCH_HEIGHT - 2, minecraft.options.getBackgroundColor(Integer.MIN_VALUE));
			searchField.render(graphics, mX, mY, delta);

			// renders a suggestion-esq error message if the regex search is invalid
			if(searchError != null) {
				int x = searchField.getX() + 8 + (int) (width * SEARCH_W_MULT);
				graphics.drawString(font, searchError.getMessage().split(System.lineSeparator())[0], x, searchField.getY(), RenderUtil.smartOpaque(ChatFormatting.DARK_RED));
				// todo: that option to disable text shadows - raw calls can have the boolean plugged right in, elsewhere needs injectors
			}
		}

		// renders the bg and the buttons for the settings menu
		if(showSettingsMenu && config.search) {
			// prepub use demo_background.png instead, but idk how to make it nine-sliced bc by default it's too big -> update preview photos (config & cf/mr)
			graphics.blit(
				/*? if >=1.21.6 {*/RenderPipelines.GUI_TEXTURED,/*?} elif >=1.21.2 {*//*RenderType::guiTextured,*//*?}*/
				id("textures/gui/search_settings_panel.png"),
				MENU_X, height + MENU_Y_OFFSET, 0, 0, MENU_WIDTH, MENU_HEIGHT, MENU_WIDTH, MENU_HEIGHT
			);
			//Identifier DEMO = Identifier.withDefaultNamespace("textures/gui/demo_background.png");
			//graphics.blit(RenderPipelines.GUI_TEXTURED, DEMO, MENU_X, MENU_Y_OFFSET + height, 0, 0, 248, 166, 256, 256); // too big

			caseSensitiveButton.render(graphics, mX, mY, delta);
			regexButton.render(graphics, mX, mY, delta);
		}

		//$ pop_stack
		graphics.pose().popMatrix(); // stop shifting before the context menu renders so the chat field doesn't cut it off

		contextMenu.render(graphics, mX, mY, delta);
	}

	/**
	 * Only renders a tooltip if the mouse is not hovering over the (opened)
	 * settings menu or the (shown) context menu. In other words, returns
	 * {@code true} if the mouse is NOT hovering over the <i>opened</i> settings
	 * menu or the <i>shown</i> context menu.
	 * */
	/*? if <=1.21.10 {*/
	/*@WrapWithCondition(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;renderComponentHoverEffect(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Style;II)V"))
	public boolean renderTooltipSmartly(GuiGraphics graphics, Font textRenderer, Style style, int mX, int mY) {
		return !isMouseOverSettingsMenu(mX, mY) && !contextMenu.isMouseOver(mX, mY);
	}*/
	/*?}*/


	@Inject(method = "removed", at = @At("TAIL"))
	public void onScreenClose(CallbackInfo ci) {
		// we always save the drafts here, we can decide to use them according to the config

		if(input.getValue().equals("/")) {
			messageDraft = ""; // don't retain blank commands
			//? if >=1.21.9 {
			minecraft.gui.getChat().discardDraft(); // un-fucks the vanilla system
			//?}
		} else {
			messageDraft = input.getValue();
		}
		searchDraft = searchField.getValue();

		if(!searchField.getValue().isEmpty()) {
			minecraft.gui.getChat().rescaleChat(); // reset the hud if it had anything in the field (#102)
		}

		contextMenu.close(this::removeWidget);

		// if the search settings have changed, save them to disk
		var cfg = BooleanList.of(config.caseSensitive, config.regex);
		if(!searchSettings.equals(cfg)) {
			searchSettings.setElements(cfg.toBooleanArray());
			Config.serialize();
		}
	}

	//? if >=1.21.9 {
	@ModifyReturnValue(method = "shouldDiscardDraft", at = @At("RETURN"))
	protected boolean allowMessageDrafting(boolean vanillaCheck) {
		return !config.messageDrafting && vanillaCheck; // if regular drafting is enabled, never discard the draft - we fix the side effects from this in #chatScreenInit and #emptyInvasive/SentDraft
	}
	//?}

	/**
	 * Empties the message draft if the screen was closed manually and <s>only invasive
	 * drafting</s> {@linkplain net.minecraft.client.Options#saveChatDrafts save chat
	 * drafts} is enabled.
	 *
	 * @implNote Injects at the super method call because it closes the screen if
	 * the key is {@link GLFW#GLFW_KEY_ESCAPE}, which is beaten out by the chat
	 * screen's redundant functionality also provided. (?)
	 */
	@Inject(
		method = "keyPressed",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screens/Screen;keyPressed("
				+ /*? if <=1.21.8 {*//*"III"*//*?} else {*/ "Lnet/minecraft/client/input/KeyEvent;" /*?}*/
				+ ")Z"
		)
	)
	private void emptyManualDrafts(/*$ key_event {*/ KeyEvent key /*$}*/, CallbackInfoReturnable<Boolean> cir) {
		if(
			!config.messageDrafting && // if both are enabled, assume all drafts are wanted

			//? if >=1.21.9 {
			minecraft.options.saveChatDrafts().get() && key.isEscape()
			//?} else {
			/*config.onlyInvasiveDrafting && keyCode == GLFW.GLFW_KEY_ESCAPE*/
			/*?}*/
		) {
			input.setValue(""); // required to empty both the chat field and the messageDraft (later on in #onScreenClose)
		}
	}

	/**
	 * Clears the message draft <b>after</b> a message has been
	 * (successfully) sent. Uses {@link At.Shift#AFTER} to ensure
	 * we don't clear if an error occurs.
	 */
	@Inject(
		method = "keyPressed",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/Minecraft;setScreen(Lnet/minecraft/client/gui/screens/Screen;)V"
			/*? if <=1.21.8 {*/
			/*, ordinal = 1, // post 1.21.9 partial drafting feature, only one call exists
			shift = At.Shift.AFTER // not important post 1.21.9 either
			*//*?}*/
		)
	)
	private void emptySentDrafts(CallbackInfoReturnable<Boolean> cir) {
		messageDraft = "";
		//? if >=1.21.9 {
		input.setValue("");
		//?}
	}

	/**
	 * Lets the {@link #input} widget be focused as intended when the search
	 * field is visible. Seems counterintuitive, but it works.
	 *
	 * @return {@code (showSearchBar && config.search) ? false :
	 * input.mouseClicked(x, y, button)}
	 *
	 * @apiNote Since 1.21.9, the call to wrap is gone, so the method can be
	 * safely stone-cut out.
	 */
	/*? if <=1.21.8 {*/
	/*@WrapOperation(method = "mouseClicked", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/EditBox;mouseClicked(DDI)Z"))
	private boolean disableChatFieldFocus(EditBox chatField, double mX, double mY, int button, Operation<Boolean> mouseClicked) {
		// return false (not clicked) if the search field is showing, otherwise delegate to input
		return (!config.search || !showSearchBar) && mouseClicked.call(chatField, mX, mY, button);
	}*/
	/*?}*/

	/*? if <=1.21.10 {*/
	/*@WrapOperation(method = "mouseClicked", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent;handleChatQueueClicked(DD)Z"))
	private boolean fixMenuClickthroughClick(ChatComponent chat, double mX, double mY, Operation<Boolean> mouseClicked) {
		// return false (not clicked) if the context menu is showing and the mouse is over it, otherwise delegate to chat
		return !isMouseOverSettingsMenu(mX, mY) && !contextMenu.isMouseOver(mX, mY) && mouseClicked.call(chat, mX, mY);
	}*/
	/*?}*/

	@WrapOperation(
		method = "mouseClicked",
		at = @At(
			value = "INVOKE",
			target =
				/*? if >=1.21.11 {*/
				"Lnet/minecraft/client/gui/components/ChatComponent;captureClickableText(Lnet/minecraft/client/gui/ActiveTextCollector;IIZ)V"
				/*?} else {*/
				/*"Lnet/minecraft/client/gui/screens/ChatScreen;getComponentStyleAt(DD)Lnet/minecraft/network/chat/Style;"*/
				/*?}*/
		)
	)
	/*? if >=1.21.11 {*/
	private void fixStyleClickthrough(ChatComponent chat, ActiveTextCollector styleFinder, int h, int ticks, boolean focused, Operation<Void> captureClickableText, MouseButtonEvent mouse) {
		if(!isMouseOverSettingsMenu(mouse.x(), mouse.y()) && !contextMenu.isMouseOver(mouse.x(), mouse.y())) {
			captureClickableText.call(chat, styleFinder, h, ticks, focused);
		}
	/*?} else {*/
	/*private Style fixStyleClickthrough(ChatScreen screen, double mX, double mY, Operation<Style> getTextStyleAt) {
		return (isMouseOverSettingsMenu(mX, mY) || contextMenu.isMouseOver(mX, mY))
			? null
			: getTextStyleAt.call(screen, mX, mY);*/
	/*?}*/
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
	private boolean fixContextMenuNotClosing(/*$ mouse_event {*/ MouseButtonEvent mouse, boolean bl /*$}*/, Operation<Boolean> mouseClicked) {
		boolean clicked = mouseClicked.call(/*$ mouse_args {*/ mouse, bl /*$}*/);

		if(/*? if >=1.21.9 {*/ mouse.button() /*?} else {*//*button*//*?}*/ != GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
			contextMenu.close(this::removeWidget); // closes the menu if it wasn't just created, we don't care if anything was actually clicked
		}

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
	 * 		{@linkplain ContextMenu#mouseClicked(MouseButtonEvent, boolean)
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
	public void mouseClickedEvents(/*$ mouse_event {*/ MouseButtonEvent mouse, boolean bl /*$}*/, CallbackInfoReturnable<Boolean> cir) {
		if(cir.getReturnValueZ()) {
			return;
		}

		//? if >=1.21.9 {
		double mX = mouse.x(), mY = mouse.y();
		int button = mouse.button();
		//?}

		if(searchField.mouseClicked(/*$ mouse_args {*/ mouse, bl /*$}*/)) {
			cir.setReturnValue(true);
		}

		if(isMouseOverSettingsMenu(mX, mY)) {
			if(caseSensitiveButton.mouseClicked(/*$ mouse_args {*/ mouse, bl /*$}*/)) {
				cir.setReturnValue(true);
			} else if(regexButton.mouseClicked(/*$ mouse_args {*/ mouse, bl /*$}*/)) {
				cir.setReturnValue(true);
			}
		} else if(contextMenu.mouseClicked(/*$ mouse_args {*/ mouse, bl /*$}*/)) {
			contextMenu.close(this::removeWidget);
			cir.setReturnValue(true);
		} else if(button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
			ContextMenu newMenu = new ContextMenu((ChatScreen)(Object)this, mX, mY);
			// if the mouse right-clicked elsewhere and that location can load a context menu, use it
			if(newMenu.isFunctional()) {
				contextMenu.close(this::removeWidget); // close the old

				newMenu.init(this::addWidget); // load the new menu
				setFocused(newMenu); // shift focus from the chat field
				contextMenu = newMenu;

				cir.setReturnValue(true);
			} else {
				contextMenu = newMenu; // save the no-op menu for later
				setFocused(input); // refocus the input box since there's no usable menu to focus instead
				cir.setReturnValue(false); // we didn't click anything
			}
		}
	}

	/**
	 * Allows the context menu to consume key presses for
	 * accessibility tabbing and registering button clicks
	 * properly (closing the menu after successful keystrokes).
	 *
	 * @see ContextMenu#keyPressed(KeyEvent)
	 * @see #charTyped(CharacterEvent)
	 */
	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void allowContextMenuKeyPressing(/*$ key_event {*/ KeyEvent key /*$}*/, CallbackInfoReturnable<Boolean> cir) {
		// keyPressed must be called first, otherwise tabbing will not work
		if(contextMenu.keyPressed(/*? if >=1.21.9 {*/ key /*?} else {*//*keyCode, scanCode, modifiers*//*?}*/) && /*? if >=1.21.9 {*/ key.isSelection() /*?} else {*//*CommonInputs.selected(keyCode)*//*?}*/) {
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
	 * {@linkplain Screen#charTyped(CharacterEvent) <code>super#charTyped</code>}.
	 */
	@Override
	public boolean charTyped(/*? if >=1.21.9 {*/ CharacterEvent chr /*?} else {*//*char chr, int mods*//*?}*/) {
		return (blockSpaceConsumption && chr/*? if >=1.21.9 {*/.codepoint()/*?}*/ == ' ' && input.isFocused()) ? (blockSpaceConsumption = false) : super.charTyped(chr /*? if <=1.21.8 {*//*, mods*//*?}*/);
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

	@Intrinsic // better than @Unique bc it prevents merging or discarding if a conflict unexpectedly occurs
	public ContextMenu getContextMenu() {
		return contextMenu;
	}

	@Intrinsic // better than @Unique bc it prevents merging or discarding if a conflict unexpectedly occurs
	public List<GuiMessage> getSearchResults() {
		// returns the search results, used by the context menu to display the search results
		return searchResults;
	}

	@Intrinsic // better than @Unique bc it prevents merging or discarding if a conflict unexpectedly occurs
	public boolean isMouseOverSettingsMenu(double mX, double mY) {
		return showSettingsMenu && (mX >= MENU_X && mX <= MENU_X + MENU_WIDTH && mY >= height + MENU_Y_OFFSET && mY <= height + MENU_Y_OFFSET + MENU_HEIGHT);
	}

	@Unique
	private Button makeSettingButton(String key, int yOffset) { // prepub this manual offset is grotesque.
		Config.Setting<Boolean> setting = config.getOption(key);
		Component name = Component.translatable("text.chatpatches.search." + key);
		Component text = CommonComponents.optionStatus(name, setting.get());

		return Button.builder(text, me -> {
				setting.set(!setting.get()); // toggle the setting
				me.setMessage( CommonComponents.optionStatus(name, setting.get()) ); // update the button text
				onSearchFieldUpdate(searchField.getValue(), true); // update the search field color
				// saved in #onScreenClose
			})
			.bounds(
				8, (height + (MENU_Y_OFFSET / 2) - 51) + yOffset,
				minecraft.font.width(text.getString()) + 10, 20
			)
			.tooltip(Tooltip.create( Component.translatable("text.chatpatches.search.desc." + key) ))
			.build();
	}

	/**
	 * Updates and applies {@linkplain #searchSettings search settings}, coloring,
	 * errors, and the "Search..." {@linkplain #SEARCH_SUGGESTION suggestion}.
	 * Additionally, filters chat messages according to the current settings.
	 * <p>
	 * Actual rendering and related field updating is
	 * called in multiple places, most notably in {@link #initSearchWidgets(CallbackInfo)},
	 * {@link #onScreenClose(CallbackInfo)}, and
	 * {@linkplain #makeSettingButton(String, int) by the setting buttons}.
	 */
	@Unique
	private void onSearchFieldUpdate(String text, boolean refresh) {
		if(text.equals(searchDraft) && !refresh) {
			return; // prevent useless updates
		}

		ChatComponent chat = minecraft.gui.getChat();
		if(!text.isEmpty() || refresh) {
			if(!text.isEmpty()) { // ensures the suggestion is kept when there is no query
				searchField.setSuggestion(null);
			}

			ChatFormatting status = ChatFormatting.WHITE;

			// if regex is enabled and the text is invalid, set the error and color
			if(config.regex) {
				try {
					searchMatcher = Pattern.compile(text, config.caseSensitive ? 0 : Pattern.CASE_INSENSITIVE).matcher("");
					searchError = null; // compiled successfully!
				} catch(PatternSyntaxException e) {
					searchError = e;
					// red = invalid regex
					status = ChatFormatting.RED;
					chat.rescaleChat();
				}
			} else {
				searchError = null; // no errors possible, only lack of match(es)!
			}

			if(searchError == null) {
				var messages = chat.allMessages;
				var copy = List.copyOf(messages);

				messages.removeIf(Predicate.not(msg -> {
					String m = ChatFormatting.stripFormatting(msg.content().getString());
					return (config.regex)
						? searchMatcher.reset(m).matches()
						: (config.caseSensitive)
							? m.contains(text)
							: /*? if >=1.21.11 {*/Strings.CI.contains/*?} else {*//*StringUtils.containsIgnoreCase*//*?}*/(m, text);
				}));

				searchResults.clear(); // either there are no results -> clear(), or there are new ones -> clear() + addAll()

				// only update the chat if there are results to show
				if(!messages.isEmpty()) {
					// save the full, filtered messages for the context menu
					searchResults.addAll(messages);
					// generate the visible messages from the filtered messages
					chat.rescaleChat();
					// add the real messages back; doesn't affect the visible messages
					messages.clear();
					messages.addAll(copy);

					status = ChatFormatting.GREEN; // match(es) exist
				} else {
					// already empty
					messages.addAll(copy);
					chat.rescaleChat(); // we need the visible messages back

					status = ChatFormatting.YELLOW; // no matches but valid search
				}
			}

			searchField.setTextColor(RenderUtil.smartOpaque(status));
		} else {
			searchError = null;
			searchField.setTextColor(EditBox.DEFAULT_TEXT_COLOR);
			searchField.setSuggestion(SEARCH_SUGGESTION);
			searchResults.clear();
			chat.rescaleChat();
		}

		searchDraft = text;
	}
}