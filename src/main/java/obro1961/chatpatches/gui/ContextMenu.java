package obro1961.chatpatches.gui;

import com.google.gson.JsonParseException;
import com.mojang.authlib.GameProfile;
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
import net.minecraft.util.StringHelper;
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

	// region text constants
	static final UnaryOperator<Text> UNKNOWN = (id) -> Text.translatable("text.chatpatches.copy.unknownData", id);
	static final Text MENU_STRING = Text.translatable("text.chatpatches.copy.copyText");
	static final Text RAW_STR = Text.translatable("text.chatpatches.copy.rawText");
	static final Text FORMATTED_STR = Text.translatable("text.chatpatches.copy.formattedText");
	static final Text NO_TIMESTAMP = Text.translatable("text.chatpatches.copy.noTimestampText"); // fixes (#129)
	//idea: noDupeText option? seems fitting..!
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
	// endregion

	// methods used for closing and initializing the menu
	private static Consumer<Element> remove;
	private static Consumer<ClickableWidget> addSelectableChild;

	// grid/widget stuff
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
	 * todo: move this visual somewhere else in here, and move the overlapping button comment too
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
	private final GridData gridData; //TODO ALSO: IS THERE ANY MORE OPTIMIZING WE CAN DO HERE? STILL A LOT OF VARS AND LOOKUPS :(((((

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
	private boolean noOp = false;


	/**
	 * Creates a new context menu at the specified mouse position.
	 * Returns a {@linkplain #noOp non-operational} menu if either
	 * coordinate is negative, the selected message lines are empty
	 * or invalid, or if the context menu is disabled in the config.
	 *
	 * <p>Not to be confused with {@link #init()}, which populates,
	 * configures, and positions the widgets.
	 */
	public ContextMenu(double mX, double mY) {
		this.clickPos = RenderUtils.MousePos.of(mX, mY);
		this.gridData = new GridData(MAX_ROWS, MAX_COLUMNS);
		this.buttonGrid = new GridWidget((int) mX, (int) mY);
		this.widgets = ((GridWidgetAccessor) buttonGrid).getChildren();

		this.selectedVisibles = getFullMessageAt(mX, mY);

		// disables the menu if it was passed invalid values or if it's disabled in the config
		if(!config.contextMenu || mX < 0 || mY < 0 || selectedVisibles.isEmpty())
			noOp = true;

		List<ChatHudLine> chatMessages = ((ChatHudAccessor) mc.inGameHud.getChatHud()).chatpatches$getMessages();
		String fH = noOp ? "\u0000" : TextUtils.reorder(selectedVisibles.getFirst().content(), false);
		// find the first message that starts with the hovered message (aka the hovered message)
		this.selectedLine = chatMessages.stream()
			.filter(msg ->
				!noOp && StringHelper.stripTextFormat(msg.content().getString())
					// longer messages sometimes fail because extra spaces appear to be added,
					// so it now uses startsWith() bc the first one never has extra spaces.
					.startsWith(fH.isEmpty() ? "\n" : fH) // detects messages starting with newlines, along with regular messages
			)
			.findFirst().orElse(NIL_HUD_LINE);

		if(!noOp && (selectedLine == NIL_HUD_LINE || !chatMessages.contains(selectedLine)))
			noOp = true;

		Style s = getMsgPart(selectedLine.content(), MSG_SENDER_INDEX).getStyle();
		this.messageSender = s.getHoverEvent() != null && s.getHoverEvent().getValue(HoverEvent.Action.SHOW_ENTITY) instanceof HoverEvent.EntityContent ec
			? new GameProfile(ec.uuid, Objects.requireNonNullElse(ec.name, UNKNOWN.apply(Text.of( "Sender " + ec.uuid.toString() ))).getString())
			: NIL_MSG_DATA.sender();
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
	 * Registers a button in the {@linkplain #buttonGrid button grid} and
	 * {@linkplain #gridData associated data lookup manager}. This is done
	 * by creating a new {@link ButtonWidget} (or similarly-implemented
	 * {@link PressableWidget} if {@code renderer} is specified)
	 * according to the passed id, copy text supplier, press action, and
	 * coordinates (the local row and absolute column).
	 *
	 * @param localRow The row relative to the current row, which is
	 *                 specified by the last main button added. The
	 *                 absolute row is automatically calculated and
	 *                 {@linkplain GridData#currentRow kept track of}.
	 * @param col The column in the grid menu where the button should be
	 *            placed. Main buttons are always in column 0, and hover
	 *            buttons are in columns ≥1.
	 *                 ButtonWidget#renderButton(DrawContext, int, int, float)}
	 *                 method if not {@code null}.
	 */
	private void registerButton(Text id, Supplier<Text> tooltipCopyTextSupplier, ButtonWidget.PressAction pressAction, int localRow, int col,
								RenderUtils.Renderer<PressableWidget> renderer) {
		int w = mc.textRenderer.getWidth(id) + 2 * buttonPadding;
		int h = buttonPadding + 14;

		PressableWidget button = ButtonWidget.builder(id, b -> {
			Text copyText = tooltipCopyTextSupplier != null ? tooltipCopyTextSupplier.get() : ScreenTexts.EMPTY;
			String copyStr = StringHelper.stripTextFormat(copyText.getString()); //prepub: config opt to copy section signs, disabled by def..?
			if(!copyStr.isEmpty()) {
				mc.keyboard.setClipboard(copyStr);
				mc.getToastManager().add(new SystemToast( SystemToast.Type.PERIODIC_NOTIFICATION, Text.translatable("text.chatpatches.copy.copied"), copyText ));
			}

			if(pressAction != null)
				pressAction.onPress(b);

			close();
		}).dimensions((int)clickPos.x, (int)clickPos.y, w, h).build();

		if(tooltipCopyTextSupplier != null)
			button.setTooltip(Tooltip.of( tooltipCopyTextSupplier.get() )); //Text.of( tooltipCopyTextSupplier.get().getString().replaceAll("§", "&") )//prepub.. see the CIS formatting idea by JSON_STR

		if(renderer != null) {
			PressableWidget effectivelyFinalButton = button;
			button = new PressableWidget(button.getX(), button.getY(), button.getWidth(), button.getHeight(), id) {
				final ButtonWidget.NarrationSupplier narrationSupplier = Supplier::get;

				@Override
				public void onPress() {
					effectivelyFinalButton.onPress();
				}
				@Override
				protected void renderButton(DrawContext context, int mX, int mY, float delta) {
					// renders the custom implementation, with the original method passed as a parameter to be called
					renderer.render(context, mX, mY, delta, this, super::renderButton);
				}

				// pulled from ButtonWidget
				@Override protected MutableText getNarrationMessage() { return narrationSupplier.createNarrationMessage(super::getNarrationMessage); }
				@Override public void appendClickableNarrations(NarrationMessageBuilder builder) { appendDefaultNarrations(builder); }
			};
		}

		gridData.add(button, localRow, col, tooltipCopyTextSupplier, pressAction);
	}
	/**
	 * Registers a <b>main</b> button that gets its copy text
	 * from {@code proxyId}, does <b>not</b> perform an extra
	 * press action, and <b>can</b> override this button's
	 * renderer.
	 *
	 * @see #registerProxyButton(Text, Text)
	 * @see #registerActionButton(Text, ButtonWidget.PressAction, int)
	 * @see #MENU_SENDER
	 */
	private void registerProxyActionButton(Text id, Text proxyId, int localRow, int col, RenderUtils.Renderer<PressableWidget> renderer) {
		if(id.equals(proxyId)) {
			ChatPatches.logReportMsg(new IllegalArgumentException("Cannot register proxy action button with own id '" + id.getString() + "'"));
			return;
		}

		// copies the proxy button's text by executing its press action instead
		registerButton(id, null, me -> gridData.idMap.get(proxyId).button.onPress(), localRow, col, renderer);
	}
	/**
	 * Registers a <b>main</b> button that gets its copy text
	 * from {@code proxyId} and does <b>not</b> perform an
	 * extra press action.
	 *
	 * @see #registerProxyActionButton(Text, Text, int, int, RenderUtils.Renderer)
	 * @see #MENU_STRING
	 * @see #MENU_TIMESTAMP
	 * @see #MENU_LINKS
	 */
	private void registerProxyButton(Text id, Text proxyId) {
		registerProxyActionButton(id, proxyId, 0, 0, null);
	}
	/**
	 * Registers a <b>main</b> button that <b>does</b> perform
	 * an extra press action.
	 *
	 * @see #MENU_REPLY
	 */
	private void registerActionButton(Text id, ButtonWidget.PressAction pressAction, int localRow) {
		registerButton(id, null, pressAction, localRow, 0, null);
	}
	/**
	 * Registers a button that gets its copy text from
	 * a <b>supplier</b> and does <b>not</b> perform an
	 * extra press action.
	 *
	 * @see #registerCopyOnlyButton(Text, Text, int)
	 * @see #TIMESTAMP_HOVER
	 * @see #MENU_UNIX
	 */
	private void registerCopyOnlyButton(Text id, Supplier<Text> tooltipCopyTextSupplier, int localRow, int col) {
		registerButton(id, tooltipCopyTextSupplier, null, localRow, col, null);
	}
	/**
	 * Registers a <b>hover</b> button with <b>precalculated</b>
	 * copy text that does <b>not</b> perform an extra press action.
	 *
	 * @see #registerCopyOnlyButton(Text, Supplier, int, int)
	 * @see "Literally every other button not mentioned in the other <code>registerButton</code> methods."
	 */
	private void registerCopyOnlyButton(Text id, Text tooltipCopyText, int localRow) {
		registerButton(id, () -> tooltipCopyText, null, localRow, 1, null);
	}


	/**
	 * Initializes the context menu by registering all buttons,
	 * their features, and by positioning everything correctly.
	 * Does nothing if the menu is {@linkplain #noOp disabled}
	 * or if the {@linkplain #updateHooks(Consumer, Consumer)
	 * addSelectableChild hook} was not specified.
	 *
	 * @implNote Registers buttons in the following order:
	 * <ol>
	 *     <li>{@link #MENU_STRING}</li>
	 *     <li>{@link #RAW_STR}</li>
	 *     <li>{@link #FORMATTED_STR}</li>
	 *     <li>*{@link #NO_TIMESTAMP}</li>
	 *     <li>{@link #JSON_STR}</li>
	 *     <li>If a timestamp is present*: {@link #MENU_TIMESTAMP}</li>
	 *     <li>*{@link #TIMESTAMP}</li>
	 *     <li>*{@link #TIMESTAMP_HOVER}</li>
	 *     <li>{@link #MENU_UNIX}</li>
	 *     <li>If any web or file links are present**: {@link #MENU_LINKS}</li>
	 *     <li>**{@link #LINK_N} (for each link)</li>
	 *     <li>If the message sender is a player***: {@link #MENU_SENDER}</li>
	 *     <li>***{@link #NAME}</li>
	 *     <li>***{@link #UUID}</li>
	 *     <li>***{@link #MENU_REPLY}</li>
	 * </ol>
	 * Finally, updates and syncs the button positions and registers them with
	 * the screen hooks provided by {@link #updateHooks(Consumer, Consumer)}.
	 */
	public void init() {
		if(noOp) {
			return;
		} else if(addSelectableChild == null) {
			ChatPatches.logReportMsg(new IllegalStateException("ChatScreen hook `addSelectableChild` not initialized"));
			return;
		}

		Text text = selectedLine.content();
		Text timestamp = getPart(text, TIMESTAMP_INDEX);

		// string buttons - unconditional
		boolean timestamped = !timestamp.getString().isBlank();
		registerProxyButton(MENU_STRING, RAW_STR);
			registerCopyOnlyButton(RAW_STR, text, 0);
			registerCopyOnlyButton(FORMATTED_STR, Text.of(TextUtils.reorder(text.asOrderedText(), true)), 1);
			if(timestamped)
				registerCopyOnlyButton(NO_TIMESTAMP, TextUtils.newText(text.getContent(), text.getSiblings().subList(1, text.getSiblings().size()), text.getStyle()), 2);
			registerCopyOnlyButton(JSON_STR,
				textCodec().encodeStart(ChatPatches.jsonOps(), text)
					.resultOrPartial(e -> ChatPatches.logReportMsg(new JsonParseException(e)))
					.map(JsonHelper::toSortedString)
					.map(Text::of)
					.orElse(UNKNOWN.apply(JSON_STR)),
					//NbtHelper.toPrettyPrintedText(...) //prepub: make the format fancy by somehow converting to nbt (ops?), formatting, lowercasing, and adding quotes
			(timestamped ? 3 : 2));


		// timestamp buttons - conditional (not on boundary lines) // prepub or if option that disables timestamps on system messages is true, dont add timestamps (ik this is not the right spot leave me alone)
		if(timestamped) {
			registerProxyButton(MENU_TIMESTAMP, TIMESTAMP);
				registerCopyOnlyButton(TIMESTAMP, timestamp, 0);
				registerCopyOnlyButton(TIMESTAMP_HOVER, () -> {
					HoverEvent hoverEvent = timestamp.getStyle().getHoverEvent();
					return hoverEvent != null ? hoverEvent.getValue(HoverEvent.Action.SHOW_TEXT) : Text.empty();
				}, 1, 1);
		}

		// unix timestamp button - unconditional
		registerCopyOnlyButton(MENU_UNIX, () -> {
			String time = timestamp.getStyle().getInsertion();
			return time != null && !time.isEmpty() ? Text.of(time) : UNKNOWN.apply(MENU_UNIX);
		}, 0, 0);

		// link buttons - conditional
		List<String> webLinks = TextUtils.getLinks(text.getString());
		List<String> fileLinks = new ArrayList<>();
		text.visit((style, str) -> {
			if(style.getClickEvent() instanceof ClickEvent ce && ce.getValue() instanceof String v && !v.isBlank()) {
				if(ce.getAction() == ClickEvent.Action.OPEN_URL && !webLinks.contains(v))
					webLinks.add(v);
				else if(ce.getAction() == ClickEvent.Action.OPEN_FILE && !fileLinks.contains(v))
					fileLinks.add(v);
			}
			return Optional.empty();
		}, Style.EMPTY);

		if(!webLinks.isEmpty() || !fileLinks.isEmpty()) {
			registerProxyButton(MENU_LINKS, LINK_N.apply(1));

			for(int i = 0; i < fileLinks.size(); i++)
				registerCopyOnlyButton(LINK_N.apply(i + 1), Text.of("§6§n" + fileLinks.get(i)), i);

			for(int i = fileLinks.size(); i < webLinks.size() + fileLinks.size(); i++)
				// creates link buttons starting at link 1 up to link n, with ids following the same pattern (LINK_1 - LINK_N)
				registerCopyOnlyButton(LINK_N.apply(i + 1), Text.of("§9§n" + webLinks.get(i)), i);
		}

		// sender buttons - conditional
		if( !messageSender.equals(NIL_MSG_DATA.sender()) ) {
			SkinTextures playerSkin = mc.getSkinProvider().getSkinTextures(messageSender);

			registerProxyActionButton(MENU_SENDER, NAME, 0, 0,
				(context, mX, mY, delta, me, sup3r) -> {
					sup3r.render(context, mX, mY, delta);
					PlayerSkinDrawer.draw(context, playerSkin, me.getX() + 1, me.getY() + 1, 16);
				}
			);
				registerCopyOnlyButton(NAME, Text.of(messageSender.getName()), 0);
				registerCopyOnlyButton(UUID, Text.of(messageSender.getId().toString()), 1);
			registerActionButton(MENU_REPLY, me -> {
				if(mc.currentScreen instanceof ChatScreen chatScreen)
					((ChatScreenAccessor) chatScreen).chatpatches$overrideChatText( TextUtils.fillVars(config.copyReplyFormat, messageSender.getName()) );
			}, 0);
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

		// FIXME SOON: so it seems like the chat screen actually handles tabbing fine, as long as you aren't focused on the chat. but
		//  the only issue is that tabbing over menu buttons doesn't trigger the same refreshing of buttons as hovering does!
		//  my guess: impl some override in the registerButton constructor so that if col==0, trigger wtv we need. @see wherever tab hover events are triggered
		// also todo: we should probably auto-select the top-left button on right-click
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
		noOp = true;
		if(remove == null) {
			ChatPatches.logReportMsg(new IllegalStateException("ChatScreen hook `remove` not initialized"));
			return;
		}
		buttonGrid.forEachChild(remove::accept);
		widgets.clear();
	}

	/**
	 * @see #noOp
	 *
	 * @apiNote Intended for external
	 * (non-{@link ContextMenu}) use.
	 */
	public boolean isFunctional() {
		return !noOp;
	}

	public boolean isMouseOver(double mX, double mY) {
		return !noOp &&
			   mX >= buttonGrid.getX() && mX <= buttonGrid.getX() + buttonGrid.getWidth()
			&& mY >= buttonGrid.getY() && mY <= buttonGrid.getY() + buttonGrid.getHeight();
	}


	/**
	 * Returns the last button that is being hovered over.
	 * Doesn't return the first, because buttons can overlap,
	 * so the last one to render should be the one to return.
	 */
	private Optional<PressableWidget> getHoveredButton(double mX, double mY) {
		return isMouseOver(mX, mY)
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
		if(!config.contextMenu || mX < 0 || mY < 0)
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

		public void add(PressableWidget button, int localRow, int col, Supplier<Text> tooltipCopyTextSupplier, ButtonWidget.PressAction pressAction) {
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