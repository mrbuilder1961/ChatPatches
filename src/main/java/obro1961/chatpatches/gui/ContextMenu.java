package obro1961.chatpatches.gui;

import com.google.gson.JsonParseException;
import com.mojang.authlib.GameProfile;
import it.unimi.dsi.fastutil.ints.Int2ObjectFunction;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.ParentElement;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.GridWidget;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.text.*;
import net.minecraft.util.JsonHelper;
import net.minecraft.util.StringHelper;
import net.minecraft.util.math.MathHelper;
import obro1961.chatpatches.ChatPatches;
import obro1961.chatpatches.accessor.ChatHudAccessor;
import obro1961.chatpatches.accessor.ChatScreenAccessor;
import obro1961.chatpatches.config.Config;
import obro1961.chatpatches.mixin.accessor.GridWidgetAccessor;
import obro1961.chatpatches.mixin.gui.ChatScreenMixin;
import obro1961.chatpatches.util.RenderUtils;
import obro1961.chatpatches.util.TextUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import static net.minecraft.screen.ScreenTexts.EMPTY;
import static obro1961.chatpatches.ChatPatches.config;
import static obro1961.chatpatches.util.ChatUtils.*;
import static obro1961.chatpatches.util.RenderUtils.NIL_HUD_LINE;
import static obro1961.chatpatches.util.TextUtils.textCodec;

/**
 * Represents the context menu that appears when a chat message is right-clicked.
 * Controls the behavior and raw rendering of the menu buttons, as well as the
 * logic for utilizing, processing, and copying data from the selected message.
 */
public class ContextMenu implements Element {
	// based on the total amount of buttons that currently exist, excluding link buttons
	public static final int MAX_ROWS = 6;
	public static final int MAX_COLUMNS = 2;

	// normal variables
	private static final MinecraftClient mc = MinecraftClient.getInstance();
	private static final int buttonPadding = 4;

	// region text constants
	static final UnaryOperator<Text> UNKNOWN = (id) -> Text.translatable("text.chatpatches.copy.unknownData", id);
	static final Text MENU_STRING = Text.translatable("text.chatpatches.copy.copyText");
	static final Text RAW_TEXT = Text.translatable("text.chatpatches.copy.rawText");
	static final Text FORMATTED_STR = Text.translatable("text.chatpatches.copy.formattedString");
	static final Text NO_TIMESTAMP_TEXT = Text.translatable("text.chatpatches.copy.noTimestampText");
	static final Text NO_DUPE_TEXT = Text.translatable("text.chatpatches.copy.noCounterText");
	static final Text JSON_STR = Text.translatable("text.chatpatches.copy.jsonString");
	static final Text MENU_TIMESTAMP = Text.translatable("text.chatpatches.copy.timestamp");
	static final Text TIMESTAMP = Text.translatable("text.chatpatches.copy.timestampText");
	static final Text TIMESTAMP_HOVER = Text.translatable("text.chatpatches.copy.timestampHoverText");
	static final Text MENU_DUPE_COUNTER = Text.translatable("text.chatpatches.copy.counter");
	static final Text COUNTER_TEXT = Text.translatable("text.chatpatches.copy.counterText");
	static final Text COUNTER_VALUE = Text.translatable("text.chatpatches.copy.counterValue");
	static final Text MENU_UNIX = Text.translatable("text.chatpatches.copy.unix");
	static final Text MENU_LINKS = Text.translatable("text.chatpatches.copy.links");
	static final Int2ObjectFunction<Text> LINK_N = (n) -> Text.translatable("text.chatpatches.copy.linkN", n);
	static final Text MENU_SENDER = Text.translatable("text.chatpatches.copy.sender");
	static final Text NAME = Text.translatable("text.chatpatches.copy.name");
	static final Text UUID = Text.translatable("text.chatpatches.copy.uuid");
	static final Text MENU_REPLY = Text.translatable("text.chatpatches.copy.reply");
	// endregion

	/**
	 * Associates every widget with its relevant
	 * identifiers, which condenses accessing and
	 * mutating operations while also keeping
	 * ugly utility methods constrained in-scope
	 * and out of sight. Contains a positioning
	 * {@link GridWidget} and multiple lists for
	 * sorting and placing buttons in their
	 * intended locations.
	 */
	private final Grid grid;

	// variables derived from selected message
	public final RenderUtils.MousePos clickPos;
	private final ChatHudLine selectedLine;
	private final List<ChatHudLine.Visible> selectedVisibles;
	private final GameProfile messageSender;
	/**
	 * The ChatScreen this menu is contained within.
	 * If {@link #noOp} is {@code false}, this field
	 * is guaranteed to be non-null. However, if
	 * {@link #noOp} is {@code true}, this field may
	 * or may not be null.
	 */
	private final ChatScreen screen;

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
	 * <p>Not to be confused with {@link #init(Consumer)}, which
	 * populates, configures, and positions the button widgets.
	 *
	 * @see ChatScreenMixin#contextMenu
	 * @see ChatScreenMixin#mouseClickedEvents(double, double, int, CallbackInfoReturnable)
	 */
	public ContextMenu(ChatScreen screen, double mX, double mY) {
		if(!config.contextMenu || mX < 0 || mY < 0)
			noOp = true;

		this.clickPos = RenderUtils.MousePos.of(mX, mY);
		this.grid = new Grid(MAX_ROWS, MAX_COLUMNS);
		this.screen = noOp ? ((ChatScreen) mc.currentScreen) : screen;

		this.selectedVisibles = getFullMessageAt(mX, mY);

		// disables the menu if it was passed invalid values or if it's disabled in the config
		if(!noOp && selectedVisibles.isEmpty())
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
			? new GameProfile(ec.uuid, Objects.requireNonNullElse(ec.name, UNKNOWN.apply(Text.of("Sender " + ec.uuid))).getString())
			: NIL_MSG_DATA.sender();
	}


	/**
	 * Registers a button in the {@linkplain Grid#widget button grid} and
	 * {@linkplain #grid associated data lookup manager}. This is done
	 * by creating a new {@link ButtonWidget} (or similarly-implemented
	 * {@link PressableWidget} if {@code renderer} is specified)
	 * according to the passed id, copy text supplier, press action, and
	 * coordinates (the local row and absolute column).
	 *
	 * @param localRow The row relative to the current row, which is
	 *                 specified by the last main button added. The
	 *                 absolute row is automatically calculated and
	 *                 {@linkplain Grid#currentRow kept track of}.
	 * @param col The column in the grid menu where the button should be
	 *            placed. Main buttons are always in column 0, and hover
	 *            buttons are in columns ≥1.
	 */
	private void registerButton(Text id, Supplier<Text> tooltipCopyTextSupplier, ButtonWidget.PressAction pressAction, int localRow, int col,
								RenderUtils.Renderer<PressableWidget> renderer) {
		int w = mc.textRenderer.getWidth(id) + 2 * buttonPadding;
		int h = buttonPadding + 14;

		PressableWidget button = ButtonWidget.builder(id, b -> {
			if(noOp)
				return;

			Text copyText = tooltipCopyTextSupplier != null ? tooltipCopyTextSupplier.get() : EMPTY;
			String copyStr = StringHelper.stripTextFormat(copyText.getString()); //prepub: config opt to copy section signs, disabled by def..?
			if(!copyStr.isEmpty()) {
				mc.keyboard.setClipboard(copyStr);
				mc.getToastManager().add(new SystemToast( SystemToast.Type.PERIODIC_NOTIFICATION, Text.translatable("text.chatpatches.copy.copied"), copyText ));
			}

			if(pressAction != null)
				pressAction.onPress(b);
		}).dimensions((int)clickPos.x, (int)clickPos.y, w, h).build();

		if(tooltipCopyTextSupplier != null)
			button.setTooltip(Tooltip.of( tooltipCopyTextSupplier.get() )); //Text.of( tooltipCopyTextSupplier.get().getString().replaceAll("§", "&") )

		if(renderer != null) {
			PressableWidget effectivelyFinalButton = button;
			button = new PressableWidget(button.getX(), button.getY(), button.getWidth(), button.getHeight(), id) {
				final ButtonWidget.NarrationSupplier narrationSupplier = Supplier::get;

				@Override
				protected void renderButton(DrawContext context, int mX, int mY, float delta) {
					// renders the custom implementation, with the original method passed as a parameter to be called
					renderer.render(context, mX, mY, delta, this, super::renderButton);
				}

				@Override public void onPress() { effectivelyFinalButton.onPress(); }
				// pulled from ButtonWidget
				@Override protected MutableText getNarrationMessage() { return narrationSupplier.createNarrationMessage(super::getNarrationMessage); }
				@Override public void appendClickableNarrations(NarrationMessageBuilder builder) { appendDefaultNarrations(builder); }
			};
		}

		grid.add(button, localRow, col, tooltipCopyTextSupplier, pressAction);
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
		registerButton(id, null, me -> grid.get(proxyId).button.onPress(), localRow, col, renderer);
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
	 * Takes the {@link Screen#addSelectableChild(Element)}
	 * method and registers all buttons with it. Does nothing
	 * if the menu is {@linkplain #noOp disabled}.
	 *
	 * @implNote Registers buttons in the following order:
	 * <ol>
	 *     <li>{@link #MENU_STRING}</li>
	 *     <li>{@link #RAW_TEXT}</li>
	 *     <li>{@link #FORMATTED_STR}</li>
	 *     <li>*{@link #NO_TIMESTAMP_TEXT}</li>
	 *     <li>^{@link #NO_DUPE_TEXT}</li>
	 *     <li>{@link #JSON_STR}</li>
	 *     <li>If a timestamp is present*: {@link #MENU_TIMESTAMP}</li>
	 *     <li>*{@link #TIMESTAMP}</li>
	 *     <li>*{@link #TIMESTAMP_HOVER}</li>
	 *     <li>If a dupe counter is present^: {@link #MENU_DUPE_COUNTER}</li>
	 *     <li>^{@link #COUNTER_TEXT}</li>
	 *     <li>^{@link #COUNTER_VALUE}</li>
	 *     <li>{@link #MENU_UNIX}</li>
	 *     <li>If any web or file links are present**: {@link #MENU_LINKS}</li>
	 *     <li>**{@link #LINK_N} (for each link)</li>
	 *     <li>If the message sender is a player^^: {@link #MENU_SENDER}</li>
	 *     <li>^^{@link #NAME}</li>
	 *     <li>^^{@link #UUID}</li>
	 *     <li>^^{@link #MENU_REPLY}</li>
	 * </ol>
	 * Finally, updates and syncs the button positions and registers them
	 * with the {@link Screen#addSelectableChild(Element)} method.
	 *
	 * @see ChatScreenMixin#initSearchWidgets(CallbackInfo)
	 */
	public void init(Consumer<PressableWidget> addSelectableChild) {
		if(noOp)
			return;

		Text text = selectedLine.content();
		Text timestamp = getPart(text, TIMESTAMP_INDEX);
		boolean timestamped = !timestamp.getString().isBlank();
		Text counter = getPart(text, DUPE_INDEX);
		boolean duped = text.getSiblings().size() > DUPE_INDEX && !counter.getString().isEmpty();


		// string buttons - unconditional
		int strRow = 0; // current row for string and text buttons
		registerProxyButton(MENU_STRING, RAW_TEXT);
			registerCopyOnlyButton(RAW_TEXT, text, strRow++); // 0
			registerCopyOnlyButton(FORMATTED_STR, Text.of(TextUtils.reorder(text.asOrderedText(), true)), strRow++); // 1
			if(timestamped)
				registerCopyOnlyButton(NO_TIMESTAMP_TEXT, TextUtils.newSiblings(text, text.getSiblings().subList(MESSAGE_INDEX, text.getSiblings().size())), strRow++); // 2
			if(duped)
				registerCopyOnlyButton(NO_DUPE_TEXT, TextUtils.newSiblings(text, text.getSiblings().subList(TIMESTAMP_INDEX, DUPE_INDEX)), strRow++); // timestamped ? 3 : 2
			registerCopyOnlyButton(JSON_STR,
				textCodec().encodeStart(ChatPatches.jsonOps(), text)
					.resultOrPartial(e -> ChatPatches.logReportMsg(new JsonParseException(e)))
					.map(JsonHelper::toSortedString)
					.map(Text::of)
					.orElse(UNKNOWN.apply(JSON_STR)),
					//NbtHelper.toPrettyPrintedText(...) //prepub: make the format fancy by somehow converting to nbt (ops?), formatting, lowercasing, and adding quotes
			strRow); // timestamped && duped ? 4 : timestamped || duped ? 3 : 2

		// timestamp buttons - conditional (not on boundary lines)
		if(timestamped) {
			registerProxyButton(MENU_TIMESTAMP, TIMESTAMP);
				registerCopyOnlyButton(TIMESTAMP, timestamp, 0);
				registerCopyOnlyButton(TIMESTAMP_HOVER, () -> {
					HoverEvent hoverEvent = timestamp.getStyle().getHoverEvent();
					return hoverEvent != null ? hoverEvent.getValue(HoverEvent.Action.SHOW_TEXT) : EMPTY;
				}, 1, 1);
		}

		// dupe counter buttons - conditional
		if(duped) {
			registerProxyButton(MENU_DUPE_COUNTER, COUNTER_TEXT);
				registerCopyOnlyButton(COUNTER_TEXT, counter, 0);
				registerCopyOnlyButton(COUNTER_VALUE, Text.of(counter.getString().replaceAll("(§\\d)|\\D", "").trim()), 1);
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

			registerProxyActionButton(MENU_SENDER, NAME, 0, 0, null);
				registerCopyOnlyButton(NAME, Text.of(messageSender.getName()), 0);
				registerCopyOnlyButton(UUID, Text.of(messageSender.getId().toString()), 1);
			registerButton(MENU_REPLY, null, me ->
				((ChatScreenAccessor) screen).chatpatches$getChatField().setText( TextUtils.fillVars(config.copyReplyFormat, messageSender.getName()) )
			, 0, 0, (context, mX, mY, delta, me, supEr) -> {
					supEr.render(context, mX, mY, delta);
					PlayerSkinDrawer.draw(context, playerSkin, me.getX() + 1, me.getY() + 1, 16);
				}
			); // prepub: reevaluate which buttons need the skin renderer, as both make sense but lowkey maybe we should ditch it or add icons for all? it feels out of place
		}

		grid.updateButtonPositions();
		grid.buttons().forEach(addSelectableChild);
	}

	/**
	 * Renders the context menu at the position specified by
	 * {@link #clickPos} and highlights its selected message
	 * in chat.
	 *
	 * @see #renderSelectionOutline(DrawContext)
	 * @see #renderMenuButtons(DrawContext, int, int, float)
	 *
	 * @see ChatScreenMixin#renderCustomWidgets(DrawContext, int, int, float, CallbackInfo)
	 */
	public void render(DrawContext drawContext, int mX, int mY, float delta) {
		if(noOp)
			return;

		renderSelectionOutline(drawContext/*, mX, mY, delta*/);
		renderMenuButtons(drawContext, mX, mY, delta);
	}

	/**
	 * Renders a selection outline around the hovered message lines
	 * in the chat, to indicate which message will be copied.
	 */
	private void renderSelectionOutline(DrawContext drawContext/*, int mX, int mY, float delta*/) {
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
		grid.buttons().forEach(w -> w.render(drawContext, mX, mY, delta));
	}


	/**
	 * If the tab key is pressed and the menu isn't disabled,
	 * checks if the chat screen is hovered/focused on a menu
	 * button, and if so {@linkplain #updateButtons(Optional)
	 * updates the buttons} accordingly. Otherwise, tries to
	 * press the selected button, if it's part of the menu.
	 *
	 * @return {@code true} if the menu was successfully updated
	 * or if a button was pressed, otherwise {@code false} if the
	 * menu is {@linkplain #noOp disabled}.
	 *
	 * @see ChatScreenMixin#allowContextMenuKeyPressing(int, int, int, CallbackInfoReturnable)
	 */
	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if(noOp)
			return false; // failed - did nothing

		Element focused = screen.getFocused();

		if(keyCode == GLFW.GLFW_KEY_TAB) {
			if(focused instanceof PressableWidget tabbed && grid.contains(tabbed)) {
				updateButtons(Optional.of(tabbed));
				return true; // true - extra KeyCodes.isToggle check does NOT pass
			}
		}

		// true - extra KeyCodes.isToggle check DOES pass
		return grid.contains(focused) && focused.keyPressed(keyCode, scanCode, modifiers);
	}

	/**
	 * Handles the logic for triggering button click events,
	 * aka copying text, whenever the mouse is clicked.
	 *
	 * @return {@code true} if any of the menu buttons were
	 * 			clicked, {@code false} otherwise.
	 *
	 * @see ChatScreenMixin#mouseClicked(double, double, int)
	 */
	@Override
	public boolean mouseClicked(double mX, double mY, int button) {
		if(!noOp && button == GLFW.GLFW_MOUSE_BUTTON_LEFT && getHoveredButton(mX, mY) instanceof Optional<PressableWidget> opt) {
			// whether the button at (mX, mY) was clicked or not, otherwise return false and close the menu
			return opt.isPresent() && opt.get().mouseClicked(mX, mY, button);
		}

		return false; // signifies that the menu should be closed (clicked off)
	}

	/**
	 * Handles the logic for showing and hiding buttons when
	 * the mouse is moved over the context menu: Stores the
	 * result of calling {@link #getHoveredButton(double, double)}
	 * and checks if it's different from the currently focused
	 * element, and if so, passes the result to {@link
	 * #updateButtons(Optional)}. This optimizes the somewhat
	 * expensive, iterative {@link #updateButtons(Optional)} call
	 * by only calling it when a new button is hovered over.
	 *
	 * @see ChatScreenMixin#mouseMoved(double, double)
	 */
	@Override
	public void mouseMoved(double mX, double mY) {
		if(!noOp) {
			Optional<PressableWidget> opt = getHoveredButton(mX, mY);
			if(opt.orElse(null) != screen.getFocused())
				updateButtons(opt); // only update (and subsequently iterate through) every button if a new one is hovered over!
		}
	}

	public boolean isMouseOver(double mX, double mY) {
		return !noOp &&
			   mX >= grid.widget.getX() && mX <= grid.widget.getX() + grid.widget.getWidth()
			&& mY >= grid.widget.getY() && mY <= grid.widget.getY() + grid.widget.getHeight();
	}

	/**
	 * Marks the context menu {@linkplain #noOp disabled},
	 * unhooks all button widgets provided by this context
	 * menu (created in {@link #init(Consumer)}) from the
	 * screen, then clears all stored fields and focuses
	 * the {@link ChatScreen#chatField}. It must be focused
	 * at this specific time to ensure the focus call isn't
	 * ignored and delegated to a (now deleted) menu button.
	 */
	public void close(Consumer<PressableWidget> remove) {
		if(noOp)
			return; // if the menu is already disabled, it was born broken, and therefore was never initialized

		noOp = true;
		grid.buttons().forEach(remove);
		grid.clear();
		screen.setFocused( ((ChatScreenAccessor) screen).chatpatches$getChatField() );
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

	/**
	 * Signifies if the menu is focused to allow proper
	 * tab-navigation (aka forces the menu to update its
	 * buttons on keyboard input).
	 *
	 * @see #setFocused(boolean)
	 * @see #updateButtons(Optional)
	 *
	 * @return {@code true} if the menu is
	 * {@linkplain #noOp functional} and if the
	 * {@linkplain #MENU_STRING first button} is
	 * focused, {@code false} otherwise.
	 */
	@Override
	public boolean isFocused() {
		return !noOp && !grid.buttons().isEmpty() && grid.buttons().getFirst().isFocused();
	}

	/**
	 * Marks the {@linkplain #MENU_STRING first button} (and subsequently the
	 * context menu itself) focused according to the passed parameter. Does
	 * nothing if this menu is {@linkplain #noOp disabled}.
	 */
	@Override
	@Deprecated
	public void setFocused(boolean focused) {
		if(noOp)
			return;

		if(!grid.buttons().isEmpty())
			grid.buttons().getFirst().forEachChild(MENU_STRING_BUTTON -> MENU_STRING_BUTTON.setFocused(focused));
	}


	/**
	 * Updates the visibility of all buttons in the context menu,
	 * focuses the hovered button, and underlines the first hover
	 * button in each group, if it exists.
	 *
	 * @see #mouseMoved(double, double)
	 * @see #keyPressed(int, int, int)
	 *
	 * @implNote
	 * <ol>
	 *	 <li>If the passed {@link Optional} is empty, ensures nothing is focused on in the
	 *	 {@linkplain #screen chat screen} and returns</li>
	 *	 <li>Otherwise, focuses the hovered button in the chat screen</li>
	 *	 <li>Then iterates through every {@linkplain Grid#groups group} and every button in those groups:</li>
	 *	 <ol>
	 *	     <li>If the iterated button is not a main button ({@code col > 0}), sets its visibility based
	 *	     on whether the hovered button is in the iterated group or not</li>
	 *	     <li>If the iterated button is the hovered button and the iterated group has at least one hover
	 *	     button, toggles the {@link Style#underlined} attribute of the group's first hovered button
	 *	     based on whether it should show (main button) or hide (hover button aligned with its main
	 *	     button).</li>
	 *	 </ol>
	 * </ol>
	 */
	public void updateButtons(Optional<PressableWidget> widgetOptional) {
		if(widgetOptional.isEmpty()) {
			screen.setFocused(null); // removes the selected outline from the last hovered button.
			return;
		}

		PressableWidget hoveredButton = widgetOptional.get();
		screen.setFocused(hoveredButton); // allows much more efficient update checks, see #mouseMoved(int, int)
		for(List<Grid.Entry> group : grid.groups) {
			for(Grid.Entry itr : group) {
				if(itr.col > 0)
					// if the hovered button is in the group, show all other buttons; otherwise hide them bc they're irrelevant
					itr.button.visible = group.contains(grid.get( hoveredButton.getMessage() ));

				// proceed with underlining if the hovered button is in the iterated group and the group has a hover button
				if(itr.button == hoveredButton && group.size() > 1 && group.get(1).button instanceof PressableWidget firstHoverButton) {
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
	 * @return The button currently being hovered over, wrapped
	 * in an {@link Optional}. If the menu is {@linkplain #noOp
	 * disabled}, not hovered over, or if the parent screen wasn't
	 * specified, then an empty {@code Optional} is returned.
	 * Additionally, silently returns an empty optional if the
	 * hovered element is not a {@link PressableWidget}.
	 *
	 * @see ParentElement#hoveredElement(double, double)
	 */
	private Optional<PressableWidget> getHoveredButton(double mX, double mY) {
		return isMouseOver(mX, mY) ? screen.hoveredElement(mX, mY).map(e -> e instanceof PressableWidget p ? p : null) : Optional.empty();
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
	 * Associates every widget with its relevant
	 * identifiers, which condenses accessing and
	 * mutating operations while also keeping
	 * ugly utility methods constrained in-scope
	 * and out of sight. Contains a positioning
	 * {@link GridWidget} and multiple lists for
	 * sorting and placing buttons in their
	 * intended locations.
	 */
	class Grid { //prepub: use it.unimi.dsi.fastutil classes for lists/maps/etc. for performance! should be ez-pz
		private final GridWidget widget;
		private final ObjectList<Entry> entries;
		/**
		 * Holds a list of buttons at each index (group number)
		 * in the root list.
		 * The group number is used to determine which buttons
		 * should be visible when a button is hovered over.
		 * <p>NOTE: The only group that remains constant
		 * regardless of message contents is the first
		 * group, which contains basic copy options.
		 */
		private final ObjectList<ObjectList<Entry>> groups;

		private int currentRow = -1;
		private int groupCount = 0;

		public Grid(int maxRows, int maxCols) {
			this.widget = new GridWidget((int) clickPos.x, (int) clickPos.y);
			this.entries = new ObjectArrayList<>(maxRows * maxCols);
			this.groups = new ObjectArrayList<>(maxRows);
		}

		public void add(PressableWidget button, int localRow, int col, Supplier<Text> tooltipCopyTextSupplier, ButtonWidget.PressAction pressAction) {
			boolean newGroup = button.visible = (col == 0); // this will only break things if >1 main buttons are grouped together
			int groupId = newGroup ? groupCount++ : groupCount - 1;
			if(newGroup)
				currentRow++;
			int absRow = currentRow + localRow;

			Entry entry = new Entry(absRow, col, groupId, button, tooltipCopyTextSupplier, pressAction);

			widget.add(button, absRow, col);
			entries.add(entry);
			if(groups.size() > groupId)
				groups.get(groupId).add(entry);
			else
				groups.add(groupId, new ObjectArrayList<>(ObjectArrayList.of(entry)));
		}

		/**
		 * @return The {@link Entry} object associated with the given
		 * {@link Text} id, otherwise {@code null} if none exists.
		 *
		 * @implNote While iterating, compares the results of calling
		 * {@link Text#getString()} on the button's message with the
		 * given id, because comparing directly caused very strange
		 * errors and skipping over buttons that should have been
		 * considered equal.
		 */
		public Entry get(Text id) {
			for(Entry e : entries)
				if(e.button.getMessage().getString().equals(id.getString()))
					return e;

			return null;
		}

		@Contract("null -> false")
		public boolean contains(Object o) {
			return o instanceof PressableWidget b && get(b.getMessage()) != null;
		}

		public void clear() {
			entries.clear();
			groups.clear();
			((GridWidgetAccessor) widget).getChildren().clear();
		}

		/**
		 * @return The widgets stored in this Grid's internal
		 * {@link GridWidget} object, cast to
		 * <code>{@link List}<{@link PressableWidget}></code>.
		 * Will log a {@link ClassCastException} and return an
		 * empty list if any of the widgets are not of the correct
		 * type. However, this should never happen, per the
		 * {@linkplain #registerButton(Text, Supplier, ButtonWidget.PressAction, int, int, RenderUtils.Renderer)
		 * button registering methods}.
		 */
		@SuppressWarnings("unchecked")
		public List<PressableWidget> buttons() {
			try {
				return (List<PressableWidget>) (Object) ((GridWidgetAccessor) widget).getChildren();
			} catch(ClassCastException e) {
				ChatPatches.logReportMsg(e);
				return List.of();
			}
		}

		/**
		 * Aligns all buttons in a grid pattern in accordance
		 * with {@link GridWidget#refreshPositions()}.
		 * Synchronizes the widths of the main buttons (col 0)
		 * unconditionally, and the hover buttons (col 1) by
		 * group, so that they are all the same width.
		 * Additionally, ensures the entire grid menu is visible
		 * on-screen by shifting it up and/or left if it would
		 * be cut off.
		 */
		public void updateButtonPositions() {
			widget.refreshPositions();

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
			int y = widget.getY();
			if(widget.getHeight() + y > mc.getWindow().getScaledHeight()) {
				// moves the menu up by the amount it goes off the screen, plus a padding buffer
				widget.setY(y - ((widget.getHeight() + y) - mc.getWindow().getScaledHeight()) - buttonPadding);
			}
			// if the grid menu goes off the screen, shift it left
			int x = widget.getX();
			if(widget.getWidth() + x > mc.getWindow().getScaledWidth()) {
				// moves the menu left by the amount it goes off the screen, plus a padding buffer
				widget.setX(x - ((widget.getWidth() + x) - mc.getWindow().getScaledWidth()) - buttonPadding);
			}
		}

		record Entry(int row, int col, int groupId, PressableWidget button, @NotNull Supplier<Text> tooltipCopyTextSupplier, @Nullable ButtonWidget.PressAction pressAction) {}
	}
}