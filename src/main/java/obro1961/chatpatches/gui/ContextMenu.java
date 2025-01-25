package obro1961.chatpatches.gui;

import com.google.gson.JsonParseException;
import com.mojang.authlib.GameProfile;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.*;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;
import net.minecraft.util.JsonHelper;
import net.minecraft.util.math.MathHelper;
import obro1961.chatpatches.ChatPatches;
import obro1961.chatpatches.accessor.ChatHudAccessor;
import obro1961.chatpatches.accessor.ChatScreenAccessor;
import obro1961.chatpatches.config.Config;
import obro1961.chatpatches.mixin.accessor.GridWidgetAccessor;
import obro1961.chatpatches.util.RenderUtils;
import obro1961.chatpatches.util.TextUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import static obro1961.chatpatches.ChatPatches.config;
import static obro1961.chatpatches.util.ChatUtils.*;
import static obro1961.chatpatches.util.RenderUtils.NIL_HUD_LINE;
import static obro1961.chatpatches.util.TextUtils.textCodec;

/**
 * Represents the context menu that appears when a chat message is right-clicked.
 * Controls the behavior and raw rendering of the menu buttons, as well as the
 * logic for utilizing, processing, and copying data from the selected message.
 */
public class ContextMenu {
	// based on the total amount of buttons that currently exist, excluding link buttons
	public static final int MAX_ROWS = 6;
	public static final int MAX_COLUMNS = 2;

	// normal variables
	private static final MinecraftClient mc = MinecraftClient.getInstance();
	private static final int buttonPadding = 4;
	// text
	static final UnaryOperator<Text> UNKNOWN = (id) -> Text.translatable("text.chatpatches.copy.unknownData", id); // todo make sure this looks okay in practice
	static final Text MENU_STRING = Text.translatable("text.chatpatches.copy.copyString");
	static final Text RAW_STR = Text.translatable("text.chatpatches.copy.rawString");
	static final Text FORMATTED_STR = Text.translatable("text.chatpatches.copy.formattedString");
	// todo close (#129):
	//static final Text NO_TIMESTAMP = Text.translatable("text.chatpatches.copy.noTimestamp"); // same as RAW_STR but substr timestamp
	static final Text JSON_STR = Text.translatable("text.chatpatches.copy.jsonString");
	static final Text MENU_TIMESTAMP = Text.translatable("text.chatpatches.copy.timestamp");
	static final Text TIMESTAMP = Text.translatable("text.chatpatches.copy.timestampText");
	static final Text TIMESTAMP_HOVER = Text.translatable("text.chatpatches.copy.timestampHoverText");
	static final Text MENU_UNIX = Text.translatable("text.chatpatches.copy.unix");
	static final Text MENU_LINKS = Text.translatable("text.chatpatches.copy.links");
	static final Function<Integer, Text> LINK_N = (n) -> Text.translatable("text.chatpatches.copy.linkN", n);
	static final Text MENU_SENDER = Text.translatable("text.chatpatches.copy.sender");
	static final Text NAME = Text.translatable("text.chatpatches.copy.name");
	static final Text UUID = Text.translatable("text.chatpatches.copy.uuid");
	static final Text MENU_REPLY = Text.translatable("text.chatpatches.copy.reply");

	// methods used for closing and initializing the menu
	private static Consumer<Element> remove;
	private static Consumer<ClickableWidget> addSelectableChild;

	// widgets
	/**
	 * The grid widget that contains all the buttons in this context menu.
	 * Buttons can overlap, as the grid is internally a list of buttons.
	 * Overlapping hover buttons should only render when the main, self,
	 * or sibling buttons are hovered.
	 * Organized in a two column layout:
	 * |--------------------|----------------------------|
	 * | always shown (c=0) |  shown by * on hover (c=1) |
	 * |--------------------|----------------------------|
	 * | MENU_STRING        | RAW, FORMATTED, JSON       |
	 * | MENU_TIMESTAMP     | TIMESTAMP, TIMESTAMP_HOVER |
	 * | MENU_UNIX	        | x						     |
	 * | MENU_LINKS         | LINK_N(1), LINK_N(2), ...  |
	 * | MENU_SENDER        | NAME, UUID                 |
	 * | MENU_REPLY         | x                          |
	 * |--------------------|----------------------------|
	 */
	private final GridWidget buttonGrid;
	/**
	 * Contains all buttons in the grid, indiscriminate of
	 * their position in the grid (main/hover).
	 */
	private final List<Widget> widgets;
	/**
	 * Associates every widget with its relevant
	 * identifiers, which condenses accessing and
	 * mutating operations while also keeping
	 * ugly utility methods constrained in-scope
	 * and out of sight.
	 */
	private final GridData gridData;

	// variables derived from selected message
	public final RenderUtils.MousePos clickPos;
	private final ChatHudLine selectedLine;
	private final List<ChatHudLine.Visible> selectedVisibles;
	private final GameProfile messageSender;

	/**
	 * If true, effectively disables this context menu, meaning
	 * it will do nothing when interacted with. This is used
	 * when a passed parameter is invalid, the context menu is
	 * not needed yet, or it's disabled in the config. It is
	 * also very helpful for closing the menu when it's no
	 * longer needed.
	 */
	private boolean noOp = false; public boolean isNoOp() {return noOp;}


	public ContextMenu(double mX, double mY) {
		this.clickPos = RenderUtils.MousePos.of(mX, mY);
		this.gridData = new GridData(MAX_ROWS, MAX_COLUMNS);
		this.buttonGrid = new GridWidget((int) mX, (int) mY);
		this.widgets = ((GridWidgetAccessor) buttonGrid).getChildren();

		this.selectedVisibles = getFullMessageAt(mX, mY);

		List<ChatHudLine> chatMessages = ((ChatHudAccessor) mc.inGameHud.getChatHud()).chatpatches$getMessages();
		String fH = TextUtils.reorder( selectedVisibles.getFirst().content(), false );
		// find the first message that starts with the hovered message (aka the hovered message)
		this.selectedLine = chatMessages.stream()
			.filter(msg ->
				Formatting.strip( msg.content().getString() )
					// longer messages sometimes fail because extra spaces appear to be added,
					// so it now uses startsWith() bc the first one never has extra spaces.
					.startsWith(fH.isEmpty() ? "\n" : fH) // detects messages starting with newlines, along with regular messages
			)
			.findFirst()
			.orElse(NIL_HUD_LINE);

		Style s = getMsgPart(selectedLine.content(), MSG_SENDER_INDEX).getStyle();
		this.messageSender = s.getHoverEvent() != null && s.getHoverEvent().getValue(HoverEvent.Action.SHOW_ENTITY) instanceof HoverEvent.EntityContent ec
			? new GameProfile(ec.uuid, ec.name.getString())
			: NIL_MSG_DATA.sender();


		// disables the menu if it was passed invalid values or if it's disabled in the config
		if(!config.contextMenu || mX < 0 || mY < 0 || selectedVisibles.isEmpty() || !chatMessages.contains(selectedLine))
			noOp = true;
	}

	/**
	 * Ugly but critical method to grant the context menu access to ChatScreen methods.
	 * Must be called before the context menu is initialized, otherwise an
	 * {@link IllegalStateException} will be thrown.
	 */
	public static void updateHooks(Consumer<ClickableWidget> addSelectableChild, Consumer<Element> remove) {
		ContextMenu.addSelectableChild = addSelectableChild;
		ContextMenu.remove = remove;
	}

	/**
	 * Creates a new context menu with the specified Minecraft client
	 * and mouse position. If either mouse coordinate is negative, the
	 * {@link #noOp} menu is returned instead. Calculates the selected
	 * message lines from the mouse coordinates, and also returns the
	 * {@link #noOp} menu if they don't exist.
	 *
	 * <p>This method effectively serves as a constructor and
	 * initializer for the context menu, as it populates the required
	 * fields. However, this shouldn't be confused with
	 * {@link #init()}, which creates the widget buttons and
	 * related data structures.
	 *
	 * @apiNote Needed as a separate method from the constructor to
	 * allow the {@link #noOp} menu to be returned when applicable.
	 */
	public static ContextMenu of(double mX, double mY) {
		List<ChatHudLine.Visible> visibles = getFullMessageAt(mX, mY);
		if(!config.contextMenu || visibles.isEmpty() || mX < 0 || mY < 0)
			return null;

		final List<ChatHudLine> chatMessages = ((ChatHudAccessor) mc.inGameHud.getChatHud()).chatpatches$getMessages();

		// longer messages sometimes fail because extra spaces appear to be added,
		// so it now uses startsWith() bc the first one never has extra spaces.
		String fH = TextUtils.reorder( visibles.getFirst().content(), false );
		String firstHovered = fH.isEmpty() ? "\n" : fH; // fixes messages starting with newlines not being detected

		// get hovered message index (messages) for all copying data
		ChatHudLine selectedLine = chatMessages.stream()
			.filter(msg -> Formatting.strip( msg.content().getString() ).startsWith(firstHovered))
			.findFirst()
			.orElse(NIL_HUD_LINE);

		// ensures the selected line is in ChatHud#messages
		return chatMessages.contains(selectedLine) ? new ContextMenu(mX, mY) : null;
	}


	/**
	 * Registers a button in the {@linkplain #buttonGrid button grid} and
	 * {@linkplain #gridData associated data lookup manager}. This is done
	 * through creating a new {@link ButtonWidget} (or similarly-implemented
	 * {@link PressableWidget} if {@code renderCallback} is specified)
	 * according to the passed id, copy text supplier, press action, and
	 * coordinates (the local row and absolute column).
	 *
	 * @param localRow The row relative to the current row, which is
	 *                 specified by the last main button added. The
	 *                 absolute row is automatically calculated and
	 *                 {@linkplain GridData#currentRow kept track of}.
	 * @param col The column in the grid menu where the button should be
	 *            placed. Main buttons are always in column 0, and hover
	 *            buttons are in columns 1+.
	 */
	private void registerButton(@NotNull Text id, @NotNull Supplier<Text> tooltipCopyTextSupplier, @NotNull ButtonWidget.PressAction pressAction, int localRow, int col, RenderUtils.Renderer<PressableWidget> renderCallback) {
		int w = mc.textRenderer.getWidth(id) + 2 * buttonPadding;
		int h = buttonPadding + 14;

		PressableWidget button = ButtonWidget.builder(id, b -> {
			Text copyText = tooltipCopyTextSupplier.get();
			String copyStr = SharedConstants.stripInvalidChars(copyText.getString(), true); // strip section signs//todo fix?
			if(!copyStr.isEmpty()) {
				mc.keyboard.setClipboard(copyStr);
				mc.getToastManager().add(new SystemToast( SystemToast.Type.PERIODIC_NOTIFICATION, Text.translatable("text.chatpatches.copy.copied"), copyText ));
			}

			pressAction.onPress(b);

			//fixme idk why the menu doesnt close on click... see #mouseClicked
			close(); // close the menu after copying (fixme does this work?)
		}).dimensions((int)clickPos.x, (int)clickPos.y, w, h).build();

		button.setTooltip(Tooltip.of( tooltipCopyTextSupplier.get() )); //Text.of( tooltipCopyTextSupplier.get().getString().replaceAll("§", "&") )//prepub?

		if(renderCallback != null) {
			PressableWidget effectivelyFinalButton = button;
			button = new PressableWidget(button.getX(), button.getY(), button.getWidth(), button.getHeight(), id) {
				final ButtonWidget.NarrationSupplier narrationSupplier = Supplier::get;

				@Override
				public void onPress() {
					effectivelyFinalButton.onPress();
				}
				@Override
				protected void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {
					super.renderButton(context, mouseX, mouseY, delta);
					renderCallback.render(this, context, mouseX, mouseY, delta);
				}

				// pulled from ButtonWidget
				@Override protected MutableText getNarrationMessage() { return narrationSupplier.createNarrationMessage(super::getNarrationMessage); }
				@Override public void appendClickableNarrations(NarrationMessageBuilder builder) { appendDefaultNarrations(builder); }
			};
		}

		gridData.add(button, localRow, col, tooltipCopyTextSupplier, pressAction);
	}
	/** usually main buttons */
	private void registerProxyActionButton(Text id, Text proxyId, @Nullable ButtonWidget.PressAction pressAction, int localRow, int col) {
		// tooltip supplier returns the empty Text because they don't actually copy anything, rather they run their proxy's action (and underline its text; see #mouseMoved)
		registerButton(id, () -> ScreenTexts.EMPTY, me -> {
			// effectively presses the button to actually copy the text
			if(!id.equals(proxyId)) //prepub if this avoids a stack overflow/some error add a comment about it +in javadoc
				gridData.idMap.get(proxyId).button.onPress();

			if(pressAction != null)
				pressAction.onPress(me);
		}, localRow, col, null);
	}
	/** usually main buttons */
	private void registerProxyButton(Text id, Text proxyId, int localRow, int col) {
		registerProxyActionButton(id, proxyId, null, localRow, col);
	}
	private void registerActionButton(Text id, ButtonWidget.PressAction pressAction, int localRow, int col) {
		registerButton(id, () -> ScreenTexts.EMPTY, pressAction, localRow, col, null);
	}
	/** usually hover buttons */
	private void registerCopyOnlyButton(Text id, Supplier<Text> tooltipCopyTextSupplier, int localRow, int col) {
		registerButton(id, tooltipCopyTextSupplier, me -> {}, localRow, col, null);
	}
	/**
	 * Registers a button with pre-calculated copy text
	 * and no extra press action.
	 * Typically used for hover buttons: when the
	 * tooltip/copy text isn't complicated enough to
	 * require a
	 * {@linkplain #registerCopyOnlyButton(Text, Supplier, int, int) Supplier},
	 * and when no extra press action is necessary.
	 */
	private void registerCopyOnlyButton(Text id, Text tooltipCopyText, int localRow, int col) {
		registerButton(id, () -> tooltipCopyText, me -> {}, localRow, col, null);
	}


	/**
	 * todo...
	 */
	public void init() {
		if(noOp)
			return;

		if(addSelectableChild == null) {
			ChatPatches.logReportMsg(new IllegalStateException("ChatScreen hook `addSelectableChild` not initialized"));
			return;
		}

		// string buttons - unconditional
		registerProxyButton(MENU_STRING, RAW_STR, 0, 0);
			registerCopyOnlyButton(RAW_STR, selectedLine.content(), 0, 1);
			registerCopyOnlyButton(FORMATTED_STR, Text.of(TextUtils.reorder(selectedLine.content().asOrderedText(), true)), 1, 1);
			registerCopyOnlyButton(JSON_STR,
				textCodec().encodeStart(ChatPatches.jsonOps(), selectedLine.content())
					.resultOrPartial(e -> ChatPatches.logReportMsg(new JsonParseException(e)))
					.map(JsonHelper::toSortedString)
					.map(Text::of)
					.orElse(UNKNOWN.apply(JSON_STR)),
			2, 1);

		// timestamp buttons - conditional (not on boundary lines)
		if( !getPart(selectedLine.content(), TIMESTAMP_INDEX).getString().isBlank() ) {
			registerProxyButton(MENU_TIMESTAMP, TIMESTAMP, 0, 0);
				registerCopyOnlyButton(TIMESTAMP, getPart(selectedLine.content(), TIMESTAMP_INDEX), 0, 1);
				registerCopyOnlyButton(TIMESTAMP_HOVER, () -> {
					HoverEvent hoverEvent = getPart(selectedLine.content(), TIMESTAMP_INDEX).getStyle().getHoverEvent();
					return hoverEvent != null ? hoverEvent.getValue(HoverEvent.Action.SHOW_TEXT) : Text.empty();
				}, 1, 1);
		}

		// unix timestamp button - unconditional
		registerCopyOnlyButton(MENU_UNIX, () -> {
			String time = getPart(selectedLine.content(), TIMESTAMP_INDEX).getStyle().getInsertion();
			return time != null && !time.isEmpty() ? Text.of(time) : UNKNOWN.apply(MENU_UNIX);
		}, 0, 0);

		// link buttons - conditional
		List<String> webLinks = TextUtils.getLinks(selectedLine.content().getString());
		List<String> fileLinks = new ArrayList<>();
		selectedLine.content().visit((style, str) -> {
			if(style.getClickEvent() instanceof ClickEvent ce) {
				if(ce.getAction() == ClickEvent.Action.OPEN_FILE)
					fileLinks.add(ce.getValue());
				else if(ce.getAction() == ClickEvent.Action.OPEN_URL && !webLinks.contains(ce.getValue()))
					webLinks.add(ce.getValue());
			}
			return Optional.empty();
		}, Style.EMPTY);
		// fixme: image not letting me click (no CE in style) and its not translatable, no idea whats wrong here but it cant work as of now
		ChatPatches.LOGGER.warn("[DEBUG] [ContextMenu] webLinks: {}, fileLinks: {} translatable: {}", webLinks, fileLinks, selectedLine.content().getContent() instanceof TranslatableTextContent ttc ?
			ttc.getArgs() : "x");
		if(!webLinks.isEmpty() || !fileLinks.isEmpty()) {
			registerProxyButton(MENU_LINKS, LINK_N.apply(1), 0, 0);

			for(int i = 0; i < fileLinks.size(); i++)
				registerCopyOnlyButton(LINK_N.apply(i + 1), Text.of("§a§n" + fileLinks.get(i)), i, 1);

			for(int i = fileLinks.size(); i < webLinks.size() + fileLinks.size(); i++)
				// creates link buttons starting at link 1 up to link n, with ids following the same pattern (LINK_1 - LINK_N)
				registerCopyOnlyButton(LINK_N.apply(i + 1), Text.of("§9§n" + webLinks.get(i)), i, 1);
		}

		// sender buttons - conditional
		if( !messageSender.equals(NIL_MSG_DATA.sender()) ) {
			SkinTextures playerSkin = mc.getSkinProvider().getSkinTextures(messageSender);

			registerButton(MENU_SENDER, () -> ScreenTexts.EMPTY, me -> {
				//todo proxy copy thing here for NAME
				ChatPatches.LOGGER.warn("[DEBUG] [ContextMenu] new icon button system using Renderer s not yet implemented... might be canceled");
			}, 0, 0, (me, context, mX, mY, delta) -> PlayerSkinDrawer.draw(context, playerSkin, me.getX() + 1, me.getY() + 1, 16));
				registerCopyOnlyButton(NAME, Text.of(messageSender.getName()), 0, 1);
				registerCopyOnlyButton(UUID, Text.of(messageSender.getId().toString()), 1, 1);
			registerActionButton(MENU_REPLY, me -> {
				if(mc.currentScreen instanceof ChatScreen chatScreen)
					((ChatScreenAccessor) chatScreen).chatpatches$overrideChatText( TextUtils.fillVars(config.copyReplyFormat, messageSender.getName()) );
			}, 0, 0);
		}

		buttonGrid.refreshPositions();
		gridData.syncButtons();
		widgets.forEach(w -> addSelectableChild.accept((ClickableWidget)w));
	}

	/**
	 * Renders the context menu at the position specified by
	 * {@link #clickPos} and highlights its selected message
	 * in chat.
	 *
	 * @see #renderSelectionOutline(DrawContext, int, int, float)
	 * @see #renderMenuButtons(DrawContext, int, int, float)
	 *
	 * @implSpec {@code hoveredVisibles} should not be empty.
	 */
	public void render(DrawContext drawContext, int mX, int mY, float delta) {
		if(noOp)
			return;

		renderSelectionOutline(drawContext, mX, mY, delta);
		renderMenuButtons(drawContext, mX, mY, delta);
	}

	/**
	 * Renders a selection outline around the hovered message lines
	 * in the chat, to indicate which message will be copied.
	 */
	private void renderSelectionOutline(DrawContext drawContext, int mX, int mY, float delta) {
		if(selectedVisibles.isEmpty())
			return;

		ChatHud chatHud = mc.inGameHud.getChatHud();
		ChatHudAccessor chat = (ChatHudAccessor) chatHud;
		List<ChatHudLine.Visible> visibles = chat.chatpatches$getVisibleMessages();


		int hoveredParts = selectedVisibles.size();
		// ChatHud#render variables, most of which are based on the hovered message
		final double s = chatHud.getChatScale();
		final int lH = chat.chatpatches$getLineHeight();
		final int sW = MathHelper.ceil(chatHud.getWidth() / s); // scaled width
		final int sH = MathHelper.floor((mc.getWindow().getScaledHeight() - 40) / s); // scaled height
		int shift = MathHelper.floor(config.shiftChat / s);
		int i = visibles.indexOf( selectedVisibles.getLast() ) - chat.chatpatches$getScrolledLines();
		int hoveredY = sH - (i * lH) - shift;

		drawContext.getMatrices().push();
		drawContext.getMatrices().scale((float) s, (float) s, 1.0f);

		int borderW = sW + 8;
		int scissorY1 = MathHelper.floor((sH - (chatHud.getVisibleLineCount() * lH) - shift - 1) * s);
		int scissorY2 = MathHelper.floor((sH - shift + 1) * s);
		int selectionY1 = hoveredY - (lH * hoveredParts);
		int selectionH = (lH * hoveredParts) + 1;

		// cuts off any of the selection rect that goes past the chat hud
		drawContext.enableScissor(0, scissorY1, borderW, scissorY2);
		drawContext.drawBorder(0, selectionY1, borderW, selectionH, config.copyColor + 0xff000000);
		drawContext.disableScissor();

		drawContext.getMatrices().pop();
	}

	private void renderMenuButtons(DrawContext drawContext, int mX, int mY, float delta) {
		// "official" way to render widgets, however the recursive-nature of it, even if only 1 level deep, feels too laggy
		//widgets.forEach(widget -> widget.forEachChild(clickableWidget -> clickableWidget.render(drawContext, mX, mY, delta)));

		// alternative way to render widgets, but it's not recursive and takes advantage of ClickableWidget#forEachChild passing itself
		widgets.forEach(w -> ((ClickableWidget)w).render(drawContext, mX, mY, delta));
	}

	public void keyPressed(int keyCode, int scanCode, int modifiers) {
		if(noOp)
			return;

		// accessibility tab consumer thing.. how?

		// consume enter and arrow keys to press and navigate buttons

		// todo later: the new fabric wiki with screens/guis mentions that implementing Selectable and another interface should allow for tab navigation
	}

	/** todo...
	 * Handles the logic for triggering button click events,
	 * aka copying text, whenever the mouse is clicked.
	 *
	 * @return Whether the menu was clicked or not.
	 *		   <br><em>Note: the return value of this method
	 *		   is largely useless, because any click after
	 *		   the menu has been opened should either trigger
	 *		   a button (job done, close menu) or close the
	 *		   menu because it's been clicked off.</em>
	 *
	 * @implNote Must be called in
	 * 	  		 {@link ChatScreen#mouseClicked(double, double, int)}
	 * 	  		 to work properly.
	 */
	public boolean mouseClicked(double mX, double mY, int button) {
		if(noOp)
			return false;

		// assumes the menu has been initialized
		if(button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			// whether the button at (mX, mY) was clicked or not, otherwise return false and close the menu
			return getHoveredButton(mX, mY).map(b -> b.mouseClicked(mX, mY, button)).orElse(false);
		}

		return false; // signifies that the menu should be closed (clicked off)
	}

	/**
	 * Handles the logic for showing and hiding buttons when
	 * the mouse is moved over the context menu. Must be called
	 * in {@link ChatScreen#mouseMoved(double, double)} to work
	 * properly.
	 */
	public void mouseMoved(double mX, double mY) {
		Optional<PressableWidget> optional = getHoveredButton(mX, mY);
		if(optional.isEmpty())
			return;

		PressableWidget hoveredButton = optional.get();
		for(List<GridData.Entry> group : gridData.groups) {
			for(GridData.Entry itr : group) {
				PressableWidget itrButton = itr.button;
				PressableWidget firstHoverButton = group.size() > 1 ? group.get(1).button : null;

				if(itr.col > 0)
					// if the hovered button is in the group, show all other buttons; otherwise hide them bc they're irrelevant
					itrButton.visible = group.contains( gridData.idMap.get( hoveredButton.getMessage().copyContentOnly() ) ); // copyContentOnly avoids style (underline) nullifying equavalence

				if(firstHoverButton != null && itrButton.equals(hoveredButton)) {
					// remove if iterated button is in the group and the message is already underlined
					boolean hide = itr.col > 0 && itr.row == group.getFirst().row;

					// note: removed `&& group.size() > 1` bc it's already true according to firstHoverButton's null check
					// show if iterated button is a main button and the message is not underlined
					boolean show = itr.col == 0; // main buttons need to underline their copy source!

					firstHoverButton.setMessage(firstHoverButton.getMessage().copy().styled( s -> s.withUnderline(show || !hide) ));
				}
			}
		}
	}

	/**
	 * Unhooks all widgets provided by this context
	 *  menu(originally from {@link #init()}) from
	 * the screen. Then flags {@link #noOp} as true.
	 *
	 * @apiNote Uses {@link #remove}, aka the
	 * 		    {@link Screen#remove(Element)} method, so
	 * 		    the widgets can be removed from the
	 * 		    screen properly.
	 */
	public void close() {
		if(remove == null) {
			ChatPatches.logReportMsg(new IllegalStateException("ChatScreen hook `remove` not initialized"));
			return;
		}

		buttonGrid.forEachChild(remove::accept);
		noOp = true;
		//selectedVisibles.clear();
		widgets.clear();
	}

	public boolean isMouseOver(double mX, double mY) {
		return
			   mX >= buttonGrid.getX() && mX <= buttonGrid.getX() + buttonGrid.getWidth()
			&& mY >= buttonGrid.getY() && mY <= buttonGrid.getY() + buttonGrid.getHeight();
	}


	/**
	 * Returns the last button that is being hovered over.
	 * Doesn't return the first, because buttons can overlap,
	 * so the last one to render should be the one to return.
	 */
	private Optional<PressableWidget> getHoveredButton(double mX, double mY) {
		return !noOp && isMouseOver(mX, mY)
			? new ArrayList<>(widgets)
				.reversed() // last button to render is the first to be checked, and for any overlap
				.stream()
				.map(b -> (PressableWidget)b)
				.filter(b -> b.isMouseOver(mX, mY))
				.findFirst()
			: Optional.empty();
	}

	/**
	 * Returns the full chat message at the given coordinates, calculated using
	 * the built-in {@link ChatHud#getMessageLineIndex(double, double)},
	 * {@link ChatHud#toChatLineX(double)}, and {@link ChatHud#toChatLineY(double)} methods
	 * combined with some logic to determine the entire message from only the hovered
	 * (visible) message.
	 * Automatically adjusts the parameters {@code mX} and {@code mY} to be accurate values,
	 * including any shifts according to {@link Config#shiftChat}. If either {@code mX} or
	 * {@code mY} are equal to {@code -1}, then an empty List is returned.
	 *
	 * @implNote
	 * <ol>
	 *	 <li>Gets the hovered visible index from {@link ChatHud#getMessageLineIndex(double, double)}
	 *	 with {@link ChatHud#toChatLineX(double)} and {@link ChatHud#toChatLineY(double)} as parameters</li>
	 *	 <li>If the hovered index is -1 then return an empty string</li>
	 *	 <li>If the hovered message <u>IS</u> end of entry (EoE):</li>
	 *	 <ol>
	 *	     <li>Starting at the hovered index +1, iterates up (+) through the visible message list until reaching another EoE message</li>
	 *	     <li>Reduces the start index by one (-1) to refer to the first message rather than the end of another message</li>
	 *	     <li>Saves the end index as the hovered index because we know it's EoE</li>
	 *	 </ol>
	 *	 <li>Otherwise when the hovered message <u>IS NOT</u> EoE:</li>
	 *	 <ol>
	 *	     <li>Starting at the hovered index, iterates up (+) through the visible message list until reaching another EoE message</li>
	 *	     <li>Reduces the start index by one (-1) to refer to the first message rather than the end of another message</li>
	 *		 <li>Then starting at the start index, iterates down (-) through the visible message list until reaching our EoE message </li>
	 *	 </ol>
	 *	 <li>Iterates through the range just determined from start to the end index, concatenating the message parts</li>
	 *	 <li>Returns the full message as a {@link List} of {@link ChatHudLine.Visible}s</li>
	 * </ol>
	 */
	private static @NotNull List<ChatHudLine.Visible> getFullMessageAt(double mX, double mY) {
		if(mX < 0 || mY < 0)
			return new ArrayList<>(0);

		final ChatHudAccessor chat = (ChatHudAccessor) mc.inGameHud.getChatHud();
		final List<ChatHudLine.Visible> visibles = chat.chatpatches$getVisibleMessages();
		// using LineIndex instead of Index bc during testing they both returned the same value; LineIndex has less code
		// above comment doesn't make sense but LineIndex should be used, Index is for regular ChatHudLine s
		final int hoveredI = chat.chatpatches$getMessageLineIndex(chat.chatpatches$toChatLineX(mX), chat.chatpatches$toChatLineY(mY));

		if(hoveredI == -1)
			return new ArrayList<>(0);

		int startI;
		int endI;

		if(visibles.get(hoveredI).endOfEntry()) {
			startI = hoveredI + 1; // w/o the +1, the loop would exit immediately
			while( startI < visibles.size() && !visibles.get(startI).endOfEntry() ) {
				startI++;
			}
			startI--; // now startI is actually the start and not the end of another message

			endI = hoveredI; // endI is the hovered index bc we know it's EoE
		} else {
			startI = hoveredI;
			while( startI < visibles.size() && !visibles.get(startI).endOfEntry() ) {
				startI++;
			}
			startI--;

			endI = startI;
			while( endI >= 0 && !visibles.get(endI).endOfEntry() ) {
				endI--;
			}
			// we don't need to ++ endI bc it's already the end of the message
		}

		// note that the startI is always greater than the endI bc the newest message index = 0
		List<ChatHudLine.Visible> messageParts = new ArrayList<>(startI - endI);
		for(int i = startI; i >= endI; i--)
			messageParts.add( visibles.get(i) );

		return messageParts;
	}


	/**
	 * Util class for organizing the buttons in the context menu,
	 * avoids the need for multiple separate lists and maps.
	 * Although it would make more sense to just add an AW
	 * entry to the GridWidget$Element class and associated
	 * methods, this is a more flexible and less invasive
	 * alternative that simplifies the omniversion approach
	 * required for the long-term goal of the mod.
	 */
	class GridData {
		private final List<Entry> entries;
		/**
		 * Maps {@link Text} button ids (ex. {@link #RAW_STR})
		 * to their respective {@link PressableWidget}s in the grid.
		 */
		private final Map<Text, Entry> idMap;
		/**
		 * Holds a list of buttons at each index (group number)
		 * in the root list.
		 * The group number is used to determine which buttons
		 * should be visible when a button is hovered over.
		 * <p>NOTE: The only group that remains constant
		 * regardless of message contents is the first
		 * group, which contains basic copy options.
		 */
		private final List<List<Entry>> groups;

		private int currentRow = -1;
		private int groupCount = 0;

		public GridData(int maxRows, int maxCols) {
			this.entries = new ArrayList<>(maxRows * maxCols);
			this.idMap = new HashMap<>(maxRows * maxCols);
			this.groups = new ArrayList<>(maxRows);
		}

		public void add(PressableWidget button, int localRow, int col, Supplier<Text> tooltipCopyTextSupplier, ButtonWidget.PressAction pressAction) { //todo final signature: (int lR, int c, ButtonWidget b)
			boolean newGroupAkaIsMain = button.visible = col == 0; // note: this will only break things if >1 main buttons are grouped together
			int groupId = newGroupAkaIsMain ? groupCount++ : groupCount - 1;

			if(newGroupAkaIsMain)
				currentRow++;
			int absRow = currentRow + localRow;

			Entry entry = new Entry(absRow, col, groupId, button, tooltipCopyTextSupplier, pressAction);

			buttonGrid.add(button, absRow, col);
			entries.add(entry);
			idMap.put(button.getMessage(), entry);
			if(groups.size() > groupId)
				groups.get(groupId).add(entry);
			else
				groups.add(groupId, new ArrayList<>(List.of(entry)));
		}

		/**
		 * Synchronizes the widths of the main buttons (col 0)
		 * unconditionally, and the hover buttons (col 1) by
		 * group, so that they are all the same width.
		 * Additionally, ensures the entire grid menu is visible
		 * on-screen by shifting it up if it gets cut off.
		 */
		public void syncButtons() {
			// sync main button widths
			int mainWidth = entries.stream()
				.filter(e -> e.col == 0)
				.mapToInt(e -> e.button.getWidth()).max()
				.orElse(8 * buttonPadding);
			entries.stream().filter(e -> e.col == 0).forEach(e -> e.button.setWidth(mainWidth));

			// sync hover button widths
			List<Integer> groupWidths = groups.stream()
				.map(g -> g.stream()
					.skip(1) // avoid the main button
					.mapToInt(e -> e.button.getWidth()).max()
					.orElse(6 * buttonPadding)
				).toList();
			groups.forEach(g -> g.subList(1, g.size()).forEach(b -> b.button.setWidth(groupWidths.get(groups.indexOf(g)))));


			// if the grid menu goes off the screen, shift it up
			int y = buttonGrid.getY();
			if(buttonGrid.getHeight() + y > mc.getWindow().getScaledHeight()) {
				// moves the menu up by the amount it goes off the screen, plus a padding buffer
				buttonGrid.setY(y - ((buttonGrid.getHeight() + y) - mc.getWindow().getScaledHeight()) - buttonPadding);
			}
		}

		//

		record Entry(int row, int col, int groupId, PressableWidget button, @NotNull Supplier<Text> tooltipCopyTextSupplier, @Nullable ButtonWidget.PressAction pressAction) {}
	}
}