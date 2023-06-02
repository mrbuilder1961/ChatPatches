package obro1961.chatpatches.mixin.gui;

import com.google.common.collect.Lists;
import com.llamalad7.mixinextras.injector.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.TexturedButtonWidget;
import net.minecraft.client.util.ChatMessages;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import obro1961.chatpatches.config.ChatSearchSetting;
import obro1961.chatpatches.util.ChatUtils;
import obro1961.chatpatches.util.StringTextUtils;
import org.apache.commons.lang3.StringUtils;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static obro1961.chatpatches.ChatPatches.config;
import static obro1961.chatpatches.config.ChatSearchSetting.*;

/**
 * An extension of ChatScreen with searching capabilities.
 * Contains a search button, search bar, and settings menu.
 * Certain features can be toggled via the settings menu or
 * the config options.
 */
@Environment(EnvType.CLIENT)
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin extends Screen {
	@Unique private static final String SUGGESTION_TEXT = Text.translatable("text.chatpatches.searchSuggestion").getString();
	@Unique private static final Text SEARCH_TOOLTIP = Text.translatable("text.chatpatches.search.desc");
	@Unique private static final int SEARCH_X = 22, SEARCH_Y_OFFSET = -31, SEARCH_H = 12;
	@Unique private static final double SEARCH_W_MULT = 0.25;
	@Unique private static final int MENU_WIDTH = 146, MENU_HEIGHT = 76;
	@Unique private static final int MENU_X = 2, MENU_Y_OFFSET = SEARCH_Y_OFFSET - MENU_HEIGHT - 6;

	@Unique private static String searchDraft = "";
	@Unique private static String messageDraft = "";
	@Unique private static boolean showSearch = true;
	@Unique private static boolean showSettingsMenu = false; // doesn't really need to be static

	@Unique private TextFieldWidget searchField;
	@Unique private TexturedButtonWidget searchButton;
	@Unique private String lastSearch;
	@Unique private PatternSyntaxException searchError;

	@Shadow	protected TextFieldWidget chatField;

	@Shadow protected abstract Style getTextStyleAt(double x, double y);
	@Shadow public abstract boolean sendMessage(String text, boolean addToHistory);


	protected ChatScreenMixin(Text title) { super(title); }


	@Inject(method = "<init>", at = @At("TAIL"))
	private void cps$ensureClientExists(String originalChatText, CallbackInfo ci) {
		this.client = Objects.requireNonNullElse(client, MinecraftClient.getInstance());

		if(messageDraft.isBlank())
			messageDraft = originalChatText;
	}

	/**
	 * @implNote
	 * <ol>
	 *     <li>Initialize the search button</li>
	 *     <li>Initialize the search field</li>
	 *     <li>Initialize the setting options</li>
	 * </ol>
	 */
	@Inject(method = "init", at = @At("TAIL"))
	protected void cps$initSearchStuff(CallbackInfo ci) {
		chatField.setText(messageDraft);

		searchButton = new TexturedButtonWidget(2, height - 35, 16, 16, 0, 0, 16, Identifier.of("chatpatches", "textures/gui/search_buttons.png"), 16, 32, button -> {});
		searchButton.setTooltip(Tooltip.of(SEARCH_TOOLTIP));

		searchField = new TextFieldWidget(client.textRenderer, SEARCH_X, height + SEARCH_Y_OFFSET, (int)(width * SEARCH_W_MULT), SEARCH_H, Text.translatable("chat.editBox"));
		searchField.setMaxLength(384);
		searchField.setDrawsBackground(false);
		searchField.setSuggestion(SUGGESTION_TEXT);
		searchField.setChangedListener(this::cps$onSearchFieldUpdate);
		searchField.setText(searchDraft);

		final int yPos = height + (MENU_Y_OFFSET / 2) - 51; // had to extract here cause of mixin restrictions
		caseSensitive = new ChatSearchSetting("caseSensitive", true, yPos, 0);
		modifiers = new ChatSearchSetting("modifiers", false, yPos, 22);
		regex = new ChatSearchSetting("regex", false, yPos, 44);

		if(config.hideSearchButton) {
			searchButton.visible = false;
			searchField.visible = false;
		} else {
			addSelectableChild(searchField);
		}
	}

	/**
	 * @implNote Additionally renders search-related stuff:
	 * <ol>
	 *     <li>The {@code searchButton}</li>
	 *     <li>If the search bar should show, render:</li>
	 *     		<li>The {@code searchField} background</li>
	 *     		<li>The {@code searchField} itself</li>
	 *     		<li>If the {@code searchError} is not null, render it</li>
	 *     <li>If the settings menu should show, render:</li>
	 *     		<li>The settings menu background</li>
	 *     		<li>The setting buttons themselves</li>
	 * </ol>
	 */
	@Inject(method = "render", at = @At("HEAD"))
	public void cps$renderSearchStuff(MatrixStack matrices, int mX, int mY, float delta, CallbackInfo ci) {
		searchButton.render(matrices, mX, mY, delta);
		if(showSearch && !config.hideSearchButton) {
			ChatScreen.fill(matrices, SEARCH_X - 2, height + SEARCH_Y_OFFSET - 2, (int) (width * (SEARCH_W_MULT + 0.06)), height + SEARCH_Y_OFFSET + SEARCH_H - 2, client.options.getTextBackgroundColor(Integer.MIN_VALUE));
			searchField.render(matrices, mX, mY, delta);

			// renders a suggestion-esq error message if the regex search is invalid
			if(searchError != null) {
				int x = searchField.getX() + 8 + (int) (width * SEARCH_W_MULT);
				textRenderer.drawWithShadow(matrices, searchError.getMessage().split(System.lineSeparator())[0], x, searchField.getY(), 0xD00000);
			}
		}

		// renders the bg and the buttons for the settings menu
		if(showSettingsMenu && !config.hideSearchButton) {
			RenderSystem.setShaderTexture(0, Identifier.of("chatpatches", "textures/gui/search_settings_panel.png"));
			drawTexture(matrices, MENU_X,  height + MENU_Y_OFFSET, 0, 0, MENU_WIDTH, MENU_HEIGHT, MENU_WIDTH, MENU_HEIGHT);

			caseSensitive.button.render(matrices, mX, mY, delta);
			modifiers.button.render(matrices, mX, mY, delta);
			regex.button.render(matrices, mX, mY, delta);
		}
	}

	/** Only renders a chat hover tooltip if the mouse is not hovering over the settings menu while it's open */
	@WrapWithCondition(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/ChatScreen;renderTextHoverEffect(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/text/Style;II)V"))
	public boolean cps$renderTooltipSmartly(ChatScreen me, MatrixStack matrices, Style style, int mX, int mY) {
		return !showSettingsMenu || !cps$hoveringOverSettings(mX, mY);
	}

	@Inject(method = "resize", at = @At("TAIL"))
	public void cps$updateSearchOnResize(MinecraftClient client, int width, int height, CallbackInfo ci) {
		String text = searchField.getText();
		searchField.setText(text);
		cps$onSearchFieldUpdate(text);

		if(!text.isEmpty())
			searchField.setSuggestion(null);
	}

	@WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/widget/TextFieldWidget;tick()V"))
	public void cps$tickSearchField(TextFieldWidget chatField, Operation<Void> tick) {
		if(updateSearchColor)
			cps$onSearchFieldUpdate(searchField.getText());

		if(searchField.isFocused() && !config.hideSearchButton)
			searchField.tick();
		else
			tick.call(chatField);
	}

	@Inject(method = "removed", at = @At("TAIL"))
	public void cps$onScreenCleared(CallbackInfo ci) {
		searchDraft = ( config.searchDrafting && !searchField.getText().isBlank() ) ? searchField.getText() : "";
		messageDraft = ( config.messageDrafting && !chatField.getText().isBlank() ) ? chatField.getText() : "";

		client.inGameHud.getChatHud().reset();
	}

	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	public void cps$allowClosingSettings(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
		if(showSettingsMenu && keyCode == GLFW.GLFW_KEY_ESCAPE) {
			showSettingsMenu = false;
			cir.setReturnValue(true);
		}

		if(keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
			if( sendMessage(chatField.getText(), true) ) {
				chatField.setText( messageDraft = "" ); // clears the chat field and the message draft
				client.setScreen(null);
			}
			cir.setReturnValue(true);
		}
		
		// fixes #86 (pressing the up arrow key for sent history switches field focus)
		if (keyCode == GLFW.GLFW_KEY_UP) {
			setChatFromHistory(-1);
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	public void cps$allowClickableWidgets(double mX, double mY, int button, CallbackInfoReturnable<Boolean> cir) {

		// a little (not really) fixes chatField being unselectable
		if(searchField.mouseClicked(mX, mY, button)) {
			setFocused(searchField);
			cir.setReturnValue(true);
		} else if(chatField.mouseClicked(mX, mY, button)) {
			setFocused(chatField);
			cir.setReturnValue(true);
		}

		// switch button id bc mouseClicked returns if button != 0
		if(searchButton.mouseClicked(mX, mY, button == 1 ? 0 : button)) {

			if(button == GLFW.GLFW_MOUSE_BUTTON_LEFT)
				showSearch = !showSearch;
			else if(button == GLFW.GLFW_MOUSE_BUTTON_RIGHT)
				showSettingsMenu = !showSettingsMenu;

			cir.setReturnValue(true);
		}

		if(showSettingsMenu) {
			if(caseSensitive.button.mouseClicked(mX, mY, button))
				cir.setReturnValue(true);

			if(modifiers.button.mouseClicked(mX, mY, button))
				cir.setReturnValue(true);

			if(regex.button.mouseClicked(mX, mY, button))
				cir.setReturnValue(true);


			// ensures clicking on the empty space in the settings menu doesn't insert anything into the chat field
			if(button == 0 && cps$hoveringOverSettings(mX, mY))
				if(client.inGameHud.getChatHud().mouseClicked(mX, mY) || getTextStyleAt(mX, mY) != null)
					cir.setReturnValue(false);
		}
	}


	// New/Unique methods

	@Unique
	private boolean cps$hoveringOverSettings(double mX, double mY) {
		return mX >= MENU_X && mX <= MENU_X + MENU_WIDTH && mY >= height + MENU_Y_OFFSET && mY <= height + MENU_Y_OFFSET + MENU_HEIGHT;
	}

	/** Called when the search field is updated; also sets the regex error and the text input color. */
	@Unique
	private void cps$onSearchFieldUpdate(String text) {
		if(text.equals(lastSearch) && !updateSearchColor )
			return;

		if(!text.isEmpty()) {
			searchField.setSuggestion(null);

			if(regex.on) {
				try {
					Pattern.compile(text);
					searchError = null;
				} catch(PatternSyntaxException e) {
					searchError = e;
					client.inGameHud.getChatHud().reset();
				}
			}

			List<ChatHudLine.Visible> filtered = cps$filterMessages( searchError != null ? null : text );
			if(searchError != null) {
				searchField.setEditableColor(0xFF5555);
				client.inGameHud.getChatHud().reset();
			} else if(filtered.isEmpty()) {
				searchField.setEditableColor(0xFFFF55);
				client.inGameHud.getChatHud().reset();
			} else {
				searchField.setEditableColor(0x55FF55);

				ChatUtils.chatHud(client).getVisibleMessages().clear();
				ChatUtils.chatHud(client).getVisibleMessages().addAll(filtered);
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
	 * matching the target string (configuration applied from {@link ChatSearchSetting#caseSensitive},
	 * {@link ChatSearchSetting#modifiers}, and {@link ChatSearchSetting#regex}) into a list of
	 * {@link ChatHudLine.Visible} visibleMessages to be rendered onto the ChatHud. This does <u>not</u>
	 * mutate or modify the actual {@link ChatHud#messages} list, only the {@link ChatHud#visibleMessages}
	 * list that is automatically repopulated with new messages when needed.
	 */
	@Unique
	private List<ChatHudLine.Visible> cps$filterMessages(String target) {
		if(target == null)
			return cps$createVisibles( ChatUtils.chatHud(client).getMessages() );

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

		return cps$createVisibles(msgs);
	}

	/**
	 * Creates a new list of to-be-rendered chat messages from the given list
	 * of chat messages. The steps to achieving this are largely based on
	 * the first half of the {@link ChatHud#addMessage(Text, MessageSignatureData, int, MessageIndicator, boolean)}
	 * method, specifically everything before the {@code while} loop.
	 */
	@Unique
	private List<ChatHudLine.Visible> cps$createVisibles(List<ChatHudLine> messages) {
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
}
