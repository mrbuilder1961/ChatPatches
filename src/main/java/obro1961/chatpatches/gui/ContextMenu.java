package obro1961.chatpatches.gui;

import com.google.common.collect.Iterables;
import com.google.gson.JsonParseException;
import com.mojang.authlib.GameProfile;
import it.unimi.dsi.fastutil.ints.Int2ObjectFunction;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
//? if >=1.21.9 {
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
//?}
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.*;
//? if <1.20.2 {
//import net.minecraft.resources.ResourceLocation;
//?}
import net.minecraft.util.Mth;
import net.minecraft.util.StringUtil;
//? if >=1.21.9 {
import net.minecraft.world.entity.player.PlayerSkin;
//?} elif >=1.20.2 {
/*import net.minecraft.client.resources.PlayerSkin;
*//*?}*/
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import obro1961.chatpatches.ChatPatches;
import obro1961.chatpatches.accessor.ChatComponentAccess;
import obro1961.chatpatches.accessor.ChatScreenAccess;
import obro1961.chatpatches.mixin.gui.ChatScreenMixin;
import obro1961.chatpatches.util.RenderUtils;
import obro1961.chatpatches.util.TextUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import oshi.util.Memoizer;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.minecraft.network.chat.CommonComponents.EMPTY;
import static net.minecraft.network.chat.Component.literal;
import static obro1961.chatpatches.ChatPatches.*;
import static obro1961.chatpatches.util.ChatUtils.*;

/**
 * Represents the context menu that appears when a chat message is right-clicked.
 * Controls the behavior and raw rendering of the menu buttons, as well as the
 * logic for utilizing, processing, and copying data from the selected message.
 */
public class ContextMenu implements GuiEventListener {
	static {
		MAX_ROWS = (int)FieldUtils.getAllFieldsList(ContextMenu.class).stream().filter(f -> f.getName().startsWith("MENU_") && f.getType().isInstance(EMPTY)).count();
	}

	public static final int MAX_ROWS;
	public static final int MAX_COLUMNS = 2; // this will probably never change
	public static final String LANG_PREFIX = "text.chatpatches.context.";

	private static final int BUTTON_HEIGHT = 14;
	private static final int BUTTON_PADDING = 4;
	/**
	 * Slightly modified from <a href="https://stackoverflow.com/a/163398">StackOverflow</a>
	 * to not include file links. Memoized to avoid recompiling the regex every time, and so
	 * it's only compiled once when needed.
	 */
	private static final Supplier<Pattern> URL_PATTERN = Memoizer.memoize(() -> Pattern.compile("(?:https?://|www)[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]"));

	private static Minecraft mc() { return Minecraft.getInstance(); }

	// region text constants
	static final Function<Object, Component> UNKNOWN = (id) -> Component.translatable(LANG_PREFIX + "unknown", TextUtils.asText(id)).withStyle(ChatFormatting.RED);
	static final Component MENU_STRING = Component.translatable(LANG_PREFIX + "copyText");
	static final Component RAW_TEXT = Component.translatable(LANG_PREFIX + "rawText");
	static final Component FORMATTED_STR = Component.translatable(LANG_PREFIX + "formattedString");
	static final Component NO_TIMESTAMP_TEXT = Component.translatable(LANG_PREFIX + "noTimestampText");
	static final Component NO_DUPE_TEXT = Component.translatable(LANG_PREFIX + "noCounterText");
	static final Component JSON_STR = Component.translatable(LANG_PREFIX + "jsonString");
	static final Component MENU_TIMESTAMP = Component.translatable(LANG_PREFIX + "timestamp");
	static final Component TIMESTAMP = Component.translatable(LANG_PREFIX + "timestampText");
	static final Component TIMESTAMP_HOVER = Component.translatable(LANG_PREFIX + "timestampHoverText");
	static final Component MENU_DUPE_COUNTER = Component.translatable(LANG_PREFIX + "counter");
	static final Component COUNTER_TEXT = Component.translatable(LANG_PREFIX + "counterText");
	static final Component COUNTER_VALUE = Component.translatable(LANG_PREFIX + "counterValue");
	static final Component MENU_UNIX = Component.translatable(LANG_PREFIX + "unix");
	static final Component MENU_LINKS = Component.translatable(LANG_PREFIX + "links");
	static final Int2ObjectFunction<Component> LINK_N = (n) -> Component.translatable(LANG_PREFIX + "linkN", n);
	static final Component MENU_SENDER = Component.translatable(LANG_PREFIX + "sender");
	static final Component NAME = Component.translatable(LANG_PREFIX + "name");
	static final Component UUID = Component.translatable(LANG_PREFIX + "uuid");
	static final Component MENU_REPLY = Component.translatable(LANG_PREFIX + "reply");
	// endregion

	/**
	 * Associates every widget with its relevant
	 * identifiers, which condenses accessing and
	 * mutating operations while also keeping
	 * ugly utility methods constrained in-scope
	 * and out of sight. Contains a positioning
	 * {@link GridLayout} and multiple lists for
	 * sorting and placing buttons in their
	 * intended locations.
	 */
	private final Grid grid;

	// reference variables kept primarily for rendering and elegance
	/**
	 * The ChatScreen this menu is contained within.
	 * If {@link #noOp} is {@code false}, this field
	 * is guaranteed to be non-null. However, if
	 * {@link #noOp} is {@code true}, this field may
	 * or may not be null.
	 */
	@Nullable
	private final ChatScreen screen;
	private final ChatComponent chat;

	// variables derived from selected message
	public final RenderUtils.MousePos clickPos;
	private final GuiMessage selectedLine;
	private final GameProfile messageSender;
	/**
	 * The index of the visible message in {@link ChatComponent#trimmedMessages}
	 * that is also {@linkplain GuiMessage.Line#endOfEntry EoE}. Used
	 * for simplifying and optimizing {@link #renderSelectionOutline(GuiGraphics)}
	 * and for calculating {@link #visibleLines}.
	 */
	private final int visibleMessageIndex;
	/**
	 * The number of visible lines in the selected message. Used for
	 * simplifying and optimizing {@link #renderSelectionOutline(GuiGraphics)}.
	 */
	private final int visibleLines;

	/**
	 * If true, effectively disables this context menu, meaning
	 * it will do nothing when interacted with. This is used when
	 * a passed parameter is invalid, the context menu is not
	 * needed yet, it's disabled in the config, or for closing
	 * the menu.
	 */
	private boolean noOp = false;

	/**
	 * Creates a new context menu at the specified mouse position.
	 * Returns a {@linkplain #noOp non-operational} menu if either
	 * coordinate is negative, the selected message is invalid, or
	 * if the context menu is disabled in the config.
	 *
	 * <p>Not to be confused with {@link #init(Consumer)}, which
	 * populates, configures, and positions the button widgets.
	 *
	 * @see ChatScreenMixin#contextMenu
	 * @see ChatScreenMixin#mouseClickedEvents(MouseButtonEvent, boolean, CallbackInfoReturnable)
	 */
	public ContextMenu(ChatScreen screen, double mX, double mY) {
		if(!config.contextMenu || mX < 0 || mY < 0)
			noOp = true;

		// critical fields
		this.clickPos = RenderUtils.MousePos.of(mX, mY);
		this.grid = new Grid();

		// reference and optimization fields
		this.chat = mc().gui.getChat();
		var access = (ChatComponentAccess) chat;
		this.screen = noOp ? null : screen;

		// selected fields
		// ensures the search results are copied properly, when present
		var searchResults = noOp ? ObjectList.<GuiMessage>of() : ((ChatScreenAccess) screen).getSearchResults();
		var messages = searchResults.isEmpty() ? chat.allMessages : searchResults;
		int messageIndex = noOp ? -1 : access.getGuiMessageIndex(mX, mY);

		if(messageIndex == -1) {
			noOp = true;
		}

		this.selectedLine = noOp ? NIL_HUD_LINE : Iterables.get(messages, messageIndex, NIL_HUD_LINE);
		this.visibleMessageIndex = noOp ? -1 : access.getEoEIndex(mX, mY); // returns the index of the EoE line at the mouse position
		this.visibleLines = noOp ? 0 : Util.make(() -> {
			var visibles = chat.trimmedMessages;

			// if the line before (+) this visible message isn't EoE, we need to account for it(them?)
			int l = 1; // minimum of one line
			for(int j = visibleMessageIndex + 1; j < visibles.size() && !visibles.get(j).endOfEntry(); j++) {
				l++;
			}

			return l;
		});


		Style s = getMsgPart(selectedLine.content(), MSG_SENDER_INDEX).getStyle();
		//? if <=1.21.4 {
		/*this.messageSender = s.getHoverEvent() != null && (Object)s.getHoverEvent().getValue(HoverEvent.Action.SHOW_ENTITY) instanceof HoverEvent.EntityTooltipInfo info
		*///?} else {
		this.messageSender = s.getHoverEvent() instanceof HoverEvent.ShowEntity(HoverEvent.EntityTooltipInfo info)
		//?}
			? new GameProfile(
				info./*? if >=1.21.5 {*/uuid/*?} else {*//*id*//*?}*/,
				/*? if <=1.20.2 {*//*Optional.ofNullable*//*?}*/(info.name).orElse(UNKNOWN.apply( MENU_SENDER.copy().append(" " +info./*? if >=1.21.5 {*/uuid/*?} else {*//*id*//*?}*/) )).getString()
			)
			: NIL_MESSAGE_DATA.sender();
	}


	/**
	 * Registers a button in the {@linkplain Grid#layout button grid} and
	 * {@linkplain #grid associated data lookup manager}. This is done
	 * by creating a new {@link Button} (or similarly-implemented
	 * {@link AbstractButton} if {@code icon} is specified)
	 * according to the passed id, copy text supplier, press action, and
	 * coordinates (the local row and absolute column).
	 *
	 * @param localRow The row relative to the current row, which is
	 * specified by the last main button added. The
	 * absolute row is automatically calculated and
	 * {@linkplain Grid#currentRow kept track of}.
	 * @param col The column in the grid menu where the button should be
	 * placed. Main buttons are always in column 0, and hover
	 * buttons are in columns ≥1.
	 * @param icon An {@link Item} or {@link PlayerSkin} object to
	 * render over the leftmost button area, or {@code null}
	 * to not render anything extra.
	 */
	private void registerButton(Component id, int localRow, int col, Object icon, Supplier<Component> tooltipCopyTextSupplier, Button.OnPress pressAction) {
		int w = mc().font.width(id) + 2 * BUTTON_PADDING;
		int h = BUTTON_HEIGHT + BUTTON_PADDING;

		AbstractButton button = Button.builder(id, b -> {
			if(noOp) {
				return;
			}

			Component copyText = tooltipCopyTextSupplier != null ? tooltipCopyTextSupplier.get() : EMPTY;
			String copyStr = StringUtil.stripColor(copyText.getString());
			if(!copyStr.isEmpty()) {
				mc().keyboardHandler.setClipboard(copyStr);
				ChatPatches.pushInfoToast(Component.translatable(LANG_PREFIX + "copied").withStyle(ChatFormatting.GREEN), copyText);
			}

			if(pressAction != null) {
				pressAction.onPress(b);
			}
		}).bounds(clickPos.xInt(), clickPos.yInt(), w, h).build();

		if(icon != null) { // prepub make an AW for ButtonWidget to avoid this ugly custom implementation? OR ACCESSOR MIXIN CLASS
			final AbstractButton src = button;
			// idea: make the button *not* adjust the text if it doesnt need to
			// accounts for the 16x16 icon on the left with the +16 and prefixed 4 spaces (each of width 4) in the id label
			button = new AbstractButton(button.getX(), button.getY(), button.getWidth() + 16, button.getHeight(), literal("    ").append(id)) {
				final Button.CreateNarration narrationSupplier = Supplier::get;

				@Override public void onPress(/*? if >=1.21.9 {*/InputWithModifiers i/*?}*/) { src.onPress(/*? if >=1.21.9 {*/i/*?}*/); }

				@Override
				protected void /*? if <1.21.11 {*/renderWidget/*?} else {*//*renderContents*//*?}*/(GuiGraphics graphics, int mX, int mY, float delta) {
					super.renderWidget(graphics, mX, mY, delta);

					if(icon instanceof Item item) {
						graphics.renderFakeItem(item.getDefaultInstance(), this.getX() + 1, this.getY() + 1);

					} else if(icon instanceof /*? if >=1.20.2 {*/PlayerSkin/*?} else {*//*ResourceLocation*//*?}*/ playerSkin) {
						PlayerFaceRenderer.draw(graphics, playerSkin, this.getX() + 1, this.getY() + 1, 16);
					}
				}

				@Override
				public String toString() {
					// hopefully helps with debugging
					return "ContextMenu.ButtonWidget[id=" + id.getString() + ", icon=" + icon + "]";
				}

				// pulled from ButtonWidget
				@Override protected @NotNull MutableComponent createNarrationMessage() {return narrationSupplier.createNarrationMessage(super::createNarrationMessage);}
				@Override public void updateWidgetNarration(NarrationElementOutput builder) {defaultButtonNarrationText(builder);}
			};
		}

		// set here so buttons with an icon don't have theirs deleted
		if(tooltipCopyTextSupplier != null) {
			button.setTooltip(Tooltip.create( tooltipCopyTextSupplier.get() )); //Text.of( tooltipCopyTextSupplier.get().getString().replace(Formatting.FORMATTING_CODE_PREFIX, '&') )
		}

		grid.add(button, localRow, col, tooltipCopyTextSupplier, pressAction);
	}

	/**
	 * Registers a <b>main</b> button that gets its copy text
	 * from {@code proxyId}, does <b>not</b> perform an extra
	 * press action, and <b>can</b> override this button's
	 * icon.
	 *
	 * @see #registerProxyButton(Component, Component, Object)
	 * @see #registerActionButton(Component, int, Object, Button.OnPress)
	 * @see #MENU_SENDER
	 */
	private void registerProxyActionButton(Component id, Component proxyId, int localRow, int col, Object icon) {
		if(id.equals(proxyId)) {
			logReportMsg(new IllegalArgumentException("Cannot register proxy action button with own id '" + id.getString() + "'"));
			return;
		}

		// copies the proxy button's text by executing its press action instead
		registerButton(id, localRow, col, icon, null, me -> grid.get(proxyId).button.onPress(/*? if >=1.21.9 {*/null/*?}*/));
	}
	/**
	 * Registers a <b>main</b> button that gets its copy text
	 * from {@code proxyId} and does <b>not</b> perform an
	 * extra press action. If provided, it draws the given object
	 * over the leftmost button area.
	 *
	 * @see #registerProxyActionButton(Component, Component, int, int, Object)
	 * @see #MENU_STRING
	 * @see #MENU_TIMESTAMP
	 * @see #MENU_LINKS
	 */
	private void registerProxyButton(Component id, Component proxyId, Object icon) {
		registerProxyActionButton(id, proxyId, 0, 0, icon);
	}
	/**
	 * Registers a <b>main</b> button that <b>does</b> perform
	 * an extra press action.
	 *
	 * @see #MENU_REPLY
	 */
	private void registerActionButton(Component id, int localRow, Object icon, Button.OnPress pressAction) {
		registerButton(id, localRow, 0, icon, null, pressAction);
	}
	/**
	 * Registers a button that gets its copy text from
	 * a <b>supplier</b> and does <b>not</b> perform an
	 * extra press action. If provided, it draws the given
	 * object over the leftmost button area.
	 *
	 * @see #registerCopyButton(Component, int, Component)
	 * @see #TIMESTAMP_HOVER
	 * @see #MENU_UNIX
	 */
	private void registerCopyButton(Component id, int localRow, int col, Object icon, Supplier<Component> tooltipCopyTextSupplier) {
		registerButton(id, localRow, col, icon, tooltipCopyTextSupplier, null);
	}
	/**
	 * Registers a <b>hover</b> button with <b>precalculated</b>
	 * copy text that does <b>not</b> perform an extra press action.
	 *
	 * @see #registerCopyButton(Component, int, int, Object, Supplier)
	 */
	private void registerCopyButton(Component id, int localRow, Component tooltipCopyText) {
		registerButton(id, localRow, 1, null, () -> tooltipCopyText, null);
	}


	/**
	 * Initializes the context menu by registering all buttons,
	 * their features, and by positioning everything correctly.
	 * Takes the {@link Screen#addWidget(GuiEventListener)}
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
	 * with the {@link Screen#addWidget(GuiEventListener)} method.
	 *
	 * @see ChatScreenMixin#initSearchWidgets(CallbackInfo)
	 */
	public void init(Consumer<AbstractButton> addSelectableChild) {
		if(noOp) {
			return;
		}

		Component text = selectedLine.content();
		Component timestamp = getPart(text, TIMESTAMP_INDEX);
		boolean timestamped = !timestamp.getString().isBlank();
		Component counter = getPart(text, DUPE_INDEX);
		boolean duped = text.getSiblings().size() > DUPE_INDEX && !counter.getString().isEmpty();


		// string buttons - unconditional
		int strRow = 0; // current row for string and text buttons
		registerProxyButton(MENU_STRING, RAW_TEXT, Items.OAK_SIGN);
			registerCopyButton(RAW_TEXT, strRow++, text); // 0
			registerCopyButton(FORMATTED_STR, strRow++, literal(TextUtils.toCodedString(text))); // 1
			if(timestamped) {
				registerCopyButton(NO_TIMESTAMP_TEXT, strRow++, TextUtils.newSiblings(text, text.getSiblings().subList(MESSAGE_INDEX, text.getSiblings().size()))); // 2
			}
			if(duped) {
				registerCopyButton(NO_DUPE_TEXT, strRow++, TextUtils.newSiblings(text, text.getSiblings().subList(TIMESTAMP_INDEX, DUPE_INDEX))); // timestamped ? 3 : 2
			}
			registerCopyButton(JSON_STR,
				strRow, TextUtils.UNSAFE_CODEC.encodeStart(ChatPatches.regBack(NbtOps.INSTANCE), text)
					.resultOrPartial(e -> logReportMsg(new JsonParseException(e)))
					.map(NbtUtils::toPrettyComponent)
					.orElse(UNKNOWN.apply(JSON_STR))
			); // (timestamped && duped) ? 4 : (timestamped || duped) ? 3 : 2
			// todo: OG_JSON_STR - json of the original message w/o CPS mods - some sort of check should determine if we can just use the time/dupe-stripped text or if reconstruction is needed

		// timestamp buttons - conditional (not on boundary lines)
		if(timestamped) {
			registerProxyButton(MENU_TIMESTAMP, TIMESTAMP, Items.CLOCK);
				registerCopyButton(TIMESTAMP, 0, timestamp);

				// registers TIMESTAMP_HOVER if the timestamp has hover text in its style
				HoverEvent event = timestamp.getStyle().getHoverEvent();
				Optional<Component> optional =
				//? if >=1.21.5 {
				event instanceof HoverEvent.ShowText(Component value) ? Optional.of(value) : Optional.empty();
				//?} else {
				/*event != null ? Optional.of(event.getValue(HoverEvent.Action.SHOW_TEXT)) : Optional.empty();*//*?}*/
				optional.ifPresent(hoverText -> registerCopyButton(TIMESTAMP_HOVER, 1, hoverText));
		}

		// dupe counter buttons - conditional
		if(duped) {
			registerProxyButton(MENU_DUPE_COUNTER, COUNTER_TEXT, Items.MAP);
				registerCopyButton(COUNTER_TEXT, 0, counter);
				registerCopyButton(COUNTER_VALUE, 1, literal(counter.getString().replaceAll("(§\\d)|\\D", "").trim()));
		}

		// unix timestamp button - unconditional
		registerCopyButton(MENU_UNIX, 0, 0, Items.REDSTONE, () -> {
			String time = timestamp.getStyle().getInsertion();
			return time != null && !time.isEmpty() ? Component.nullToEmpty(time) : UNKNOWN.apply(MENU_UNIX);
		});

		// link buttons - conditional
		ObjectList<String> webLinks = Util.make(new ObjectArrayList<>(), l -> {
			Matcher matcher = URL_PATTERN.get().matcher(text.getString());
			while(matcher.find()) {
				l.add(matcher.group());
			}
		});
		ObjectList<String> filePaths = Util.make(new ObjectArrayList<>(), l ->
			text.visit((style, str) -> {
				//? if >=1.21.5 {
				if(style.getClickEvent() instanceof ClickEvent ce && (ce instanceof ClickEvent.OpenUrl || ce instanceof ClickEvent.OpenFile)) {
					boolean isUrl = ce instanceof ClickEvent.OpenUrl;
					String link = isUrl ? ((ClickEvent.OpenUrl)ce).uri().toString() : ((ClickEvent.OpenFile)ce).path();
					if(link != null && !link.isBlank()) {
						if(isUrl && !webLinks.contains(link)) {
							webLinks.add(link);
						} else if(!isUrl && !l.contains(link)) {
							l.add(link);
						}
					}
				}
				//?} else {
				/*if((Object)style.getClickEvent() instanceof ClickEvent ce && (Object)ce.getValue() instanceof String v && !v.isBlank()) {
					if(ce.getAction() == ClickEvent.Action.OPEN_URL && !webLinks.contains(v)) {
						webLinks.add(v);
					} else if(ce.getAction() == ClickEvent.Action.OPEN_FILE && !l.contains(v)) {
						l.add(v);
					}
				}
				*///?}
				return Optional.empty();
			}, Style.EMPTY));
		if(!webLinks.isEmpty() || !filePaths.isEmpty()) {
			registerProxyButton(MENU_LINKS, LINK_N.apply(1), Items./*? if >=1.21.9 {*/IRON_CHAIN/*?} else {*//*CHAIN*//*?}*/);

			int l = 0; // link index
			for(; l < filePaths.size(); l++) {
				registerCopyButton(LINK_N.apply(l + 1), l, literal(ChatFormatting.GOLD + "" + ChatFormatting.UNDERLINE + filePaths.get(l)));
			}

			int filePathOffset = l; // ensures we're not trying to access out-of-bounds indices bc these are separate lists
			for(; l - filePathOffset < webLinks.size(); l++) {
				registerCopyButton(LINK_N.apply(l + 1), l, literal(ChatFormatting.BLUE + "" + ChatFormatting.UNDERLINE + webLinks.get(l - filePathOffset)));
			}
		}

		// sender buttons - conditional
		if( !messageSender.equals(NIL_MESSAGE_DATA.sender()) ) {
			var name = messageSender./*? if >=1.21.9 {*/name/*?} else {*//*getName*//*?}*/();
			var id = (messageSender./*? if >=1.21.9 {*/id/*?} else {*//*getId*//*?}*/());

			registerProxyActionButton(MENU_SENDER, NAME, 0, 0, Items.NAME_TAG);
				registerCopyButton(NAME, 0, Component.nullToEmpty(name));
				registerCopyButton(UUID, 1, Component.nullToEmpty(id.toString()));

			registerButton(
				MENU_REPLY,
				0, 0,
				(Object)mc().getConnection().getPlayerInfo(id) instanceof PlayerInfo info ? info./*? if >=1.20.2 {*/getSkin/*?} else {*//*getSkinLocation*//*?}*/() : null, null,
				me -> screen.input.setValue(TextUtils.fillVars(config.contextReplyFormat, name))
				// prefer real skin; else nothing
			);
		}

		grid.updateButtonMetadata();
		grid.buttons().forEach(addSelectableChild);
	}

	/**
	 * Renders the context menu at the position specified by
	 * {@link #clickPos} and highlights its selected message
	 * in chat.
	 *
	 * @see #renderSelectionOutline(GuiGraphics)
	 * @see #renderMenuButtons(GuiGraphics, int, int, float)
	 *
	 * @see ChatScreenMixin#renderCustomWidgets(GuiGraphics, int, int, float, CallbackInfo)
	 */
	public void render(GuiGraphics graphics, int mX, int mY, float delta) {
		if(noOp) {
			return;
		}

		renderSelectionOutline(graphics/*, mX, mY, delta*/);
		renderMenuButtons(graphics, mX, mY, delta);
	}

	/**
	 * Renders a selection outline around the hovered message lines
	 * in the chat, to indicate which message will be copied.
	 */
	private void renderSelectionOutline(GuiGraphics graphics/*, int mX, int mY, float delta*/) {
		if(visibleLines == 0 || visibleMessageIndex == -1) {
			return;
		}

		int hoveredParts = visibleLines;
		double s = chat.getScale();
		int lH = chat.getLineHeight();
		int sW = Mth.ceil(chat.getWidth() / s); // scaled width
		int sH = Mth.floor((mc().getWindow().getGuiScaledHeight() - 40) / s); // scaled height
		int shift = Mth.floor(config.calcDynamicChatShift() / s);
		int i = visibleMessageIndex - chat.chatScrollbarPos;
		int hoveredY = sH - (i * lH) - shift;

		//$ push_stack
		graphics.pose().pushMatrix();
		graphics.pose().scale((float) s, (float) s /*? if <=1.21.5 {*//*, 1.0f*//*?}*/);

		int borderW = sW + 8;
		int scissorY1 = Mth.floor((sH - (chat.getLinesPerPage() * lH) - shift - 1) * s);
		int scissorY2 = Mth.floor((sH - shift + 1) * s);
		int selectionY1 = hoveredY - (lH * hoveredParts);
		int selectionH = (lH * hoveredParts) + 1;

		// cuts off any of the selection rect that goes past the chat hud
		graphics.enableScissor(0, scissorY1, borderW, scissorY2);
		//? if <1.21.9 {
		/*graphics.renderOutline(0, selectionY1, borderW, selectionH, RenderUtils.opaque(config.contextOutlineColor));*/
		//?} else {
		int color = RenderUtils.opaque(config.contextOutlineColor);
		graphics.fill(0, selectionY1, borderW, selectionY1 + 1, color);
		graphics.fill(0, selectionY1 + selectionH - 1, borderW, selectionY1 + selectionH, color);
		graphics.fill(0, selectionY1 + 1, 1, selectionY1 + selectionH - 1, color);
		graphics.fill(borderW - 1, selectionY1 + 1, borderW, selectionY1 + selectionH - 1, color);
		// prepub AW GuiGraphics.OutlineBox
		// new GuiGraphics.OutlineBox(0, selectionY1, borderW, selectionH, RenderUtils.opaque(config.contextOutlineColor)).render(graphics);
		//?}
		graphics.disableScissor();

		//$ pop_stack
		graphics.pose().popMatrix();
	}

	private void renderMenuButtons(GuiGraphics graphics, int mX, int mY, float delta) {
		grid.buttons().forEach(w -> w.render(graphics, mX, mY, delta));
	}


	/**
	 * If the tab key is pressed and the menu isn't disabled,
	 * checks if the chat screen is hovered/focused on a menu
	 * button, and if so {@linkplain #updateButtons(Optional)
	 * updates the buttons} accordingly. Otherwise, tries to
	 * press the selected button if it's part of the menu.
	 *
	 * @return {@code true} if the menu was successfully updated
	 * or if a button was pressed, otherwise {@code false} if the
	 * menu is {@linkplain #noOp disabled}.
	 *
	 * @see ChatScreenMixin#allowContextMenuKeyPressing(KeyEvent, CallbackInfoReturnable)
	 */
	@Override
	public boolean keyPressed(/*$ key_event {*/ KeyEvent key/*$}*/) {
		if(noOp) {
			return false;
		}

		GuiEventListener focused = screen.getFocused();
		if(/*? if >=1.21.9 {*/ key.key() /*?} else {*//*keyCode*//*?}*/ == GLFW.GLFW_KEY_TAB) {
			if(focused instanceof AbstractButton tabbed && grid.contains(tabbed)) {
				updateButtons(Optional.of(tabbed));
				return true; // true - extra KeyCodes.isToggle check does NOT pass
			}
		}

		// true - extra KeyCodes.isToggle check DOES pass
		return grid.contains(focused) && focused.keyPressed(/*$ key_args {*/ key/*$}*/);
	}

	/**
	 * Handles the logic for triggering button click events,
	 * aka copying text, whenever the mouse is clicked.
	 *
	 * @return {@code true} if any of the menu buttons were
	 * 			clicked, {@code false} otherwise.
	 *
	 * @see ChatScreenMixin#mouseClicked(MouseButtonEvent, boolean)
	 */
	@Override
	public boolean mouseClicked(/*$ mouse_event {*/ MouseButtonEvent mouse, boolean bl/*$}*/) {
		//? if >=1.21.9 {
		double mX = mouse.x(), mY = mouse.y();
		int button = mouse.button();
		//?}

		if(!noOp && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			Optional<AbstractButton> opt = getHoveredButton(mX, mY);
			// whether the button at (mX, mY) was clicked or not, otherwise return false and close the menu
			return opt.isPresent() && opt.get().mouseClicked(/*$ mouse_args {*/ mouse, bl/*$}*/);
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
			Optional<AbstractButton> opt = getHoveredButton(mX, mY);
			if(opt.orElse(null) != screen.getFocused())
				updateButtons(opt); // only update (and subsequently iterate through) every button if a new one is hovered over!
		}
	}

	public boolean isMouseOver(double mX, double mY) {
		return !noOp &&
			   mX >= grid.layout.getX() && mX <= grid.layout.getX() + grid.layout.getWidth()
			&& mY >= grid.layout.getY() && mY <= grid.layout.getY() + grid.layout.getHeight();
	}

	/**
	 * Marks the context menu {@linkplain #noOp disabled},
	 * unhooks all button widgets provided by this context
	 * menu (created in {@link #init(Consumer)}) from the
	 * screen, then clears all stored fields and focuses
	 * the {@link ChatScreen#input}. It must be focused
	 * at this specific time to ensure the focus call isn't
	 * ignored and delegated to a (now deleted) menu button.
	 */
	public void close(Consumer<AbstractButton> remove) {
		if(noOp) {
			return; // if the menu is already disabled, it was born broken, and therefore was never initialized
		}

		noOp = true;
		grid.buttons().forEach(remove);
		grid.clear();
		screen.setFocused(screen.input);
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
		if(noOp) {
			return;
		}

		if(!grid.buttons().isEmpty()) {
			grid.buttons().getFirst().visitWidgets(MENU_STRING_BUTTON -> MENU_STRING_BUTTON.setFocused(focused));
		}
	}


	/**
	 * Updates the visibility of all buttons in the context menu,
	 * focuses the hovered button, and underlines the first hover
	 * button in each group if it exists.
	 *
	 * @see #mouseMoved(double, double)
	 * @see #keyPressed(KeyEvent)
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
	 *	     based on whether it should show (main) or hide (hover button aligned with its main
	 *	     button).</li>
	 *	 </ol>
	 * </ol>
	 */
	public void updateButtons(Optional<AbstractButton> widgetOptional) {
		if(widgetOptional.isEmpty()) {
			screen.setFocused(null); // removes the selected outline from the last hovered button
			return;
		}

		AbstractButton hoveredButton = widgetOptional.get();
		screen.setFocused(hoveredButton); // allows much more efficient update checks, see #mouseMoved(int, int)
		for(ObjectList<Grid.Entry> group : grid.groups) {
			for(Grid.Entry itr : group) {
				if(itr.isHover()) {
					// if the hovered button is in the group, show all other buttons; otherwise hide them bc they're irrelevant
					itr.button.visible = group.contains(grid.get( hoveredButton.getMessage() ));
				}

				// proceed with underlining if the hovered button is in the iterated group and the group has a hover button
				if(itr.button == hoveredButton && group.size() > 1 && /*? if java: <21 {*//*(Object)*//*?}*/ group.get(1).button instanceof AbstractButton firstHoverButton) {
					// remove if iterated button is in the group and the message is already underlined
					boolean hide = itr.isHover() && itr.row == group.getFirst().row;

					// note: removed `&& group.size() > 1` bc it's already true according to firstHoverButton's null check
					// show if iterated button is a main button and the message is not underlined
					boolean show = itr.isMain(); // main buttons need to underline their copy source!

					firstHoverButton.setMessage(firstHoverButton.getMessage().copy().withStyle( s -> s.withUnderlined(show || !hide) ));
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
	 * hovered element is not a {@link AbstractButton}.
	 *
	 * @see ContainerEventHandler#getChildAt(double, double)
	 */
	private Optional<AbstractButton> getHoveredButton(double mX, double mY) {
		return isMouseOver(mX, mY) ? screen.getChildAt(mX, mY).map(e -> e instanceof AbstractButton p ? p : null) : Optional.empty();
	}


	/**
	 * Associates every widget with its relevant
	 * identifiers, which condenses accessing and
	 * mutating operations while also keeping
	 * ugly utility methods constrained in-scope
	 * and out of sight. Contains a positioning
	 * {@link GridLayout} and multiple lists for
	 * sorting and placing buttons in their
	 * intended locations.
	 */
	class Grid {
		private final GridLayout layout;
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

		public Grid() {
			this.layout = new GridLayout(clickPos.xInt(), clickPos.yInt());
			this.entries = new ObjectArrayList<>(MAX_ROWS * MAX_COLUMNS);
			this.groups = new ObjectArrayList<>(MAX_ROWS);
		}

		public void add(AbstractButton button, int localRow, int col, Supplier<Component> tooltipCopyTextSupplier, Button.OnPress pressAction) {
			boolean newGroup = button.visible = (col == 0); // this will only break things if >1 main buttons are grouped together
			int groupId = newGroup ? groupCount++ : groupCount - 1;
			if(newGroup) {
				currentRow++;
			}
			int absRow = currentRow + localRow;

			Entry entry = new Entry(absRow, col, groupId, button, tooltipCopyTextSupplier, pressAction);

			layout.addChild(button, absRow, col);
			entries.add(entry);
			if(groups.size() > groupId) {
				groups.get(groupId).add(entry);
			} else {
				groups.add(groupId, new ObjectArrayList<>(ObjectArrayList.of(entry)));
			}
		}

		/**
		 * @return The {@link Entry} object associated with the given {@link Component}
		 * id, otherwise {@code null} if none exists.
		 *
		 * @implNote Compares using {@link Component#getString()} because direct equality
		 * checks returned false negatives due to the styles occasionally being
		 * different (typically from the underlined button text).
		 */
		public Entry get(Component id) {
			for(Entry e : entries) {
				if(e.button.getMessage().getString().equals(id.getString())) {
					return e;
				}
			}

			return null;
		}

		@Contract("null -> false")
		public boolean contains(Object o) {
			return o instanceof AbstractButton b && get(b.getMessage()) != null;
		}

		public void clear() {
			entries.clear();
			groups.clear();
			layout.children.clear();
		}

		/**
		 * @return The widgets stored in this Grid's internal
		 * {@link GridLayout} object, cast to
		 * <code>{@link List}<{@link AbstractButton}></code>.
		 * Will log a {@link ClassCastException} and return an
		 * empty list if any of the widgets are not of the correct
		 * type. However, this should never happen, per the
		 * {@linkplain ContextMenu#registerButton(Component, int, int, Object, Supplier, Button.OnPress)
		 * button registering methods}.
		 */
		@SuppressWarnings("unchecked")
		public List<AbstractButton> buttons() {
			try {
				return (List<AbstractButton>) (Object) layout.children;
			} catch(ClassCastException e) {
				logReportMsg(e);
				return ObjectList.of();
			}
		}

		/**
		 * Aligns all buttons in a grid pattern in accordance
		 * with {@link GridLayout#arrangeElements()}.
		 * Synchronizes the widths of the main buttons (col 0)
		 * together, and the hover buttons (col 1) by group,
		 * so they are all the same width. Additionally, ensures
		 * the entire grid menu is visible on-screen
		 * by shifting it up and/or left if necessary.
		 */
		public void updateButtonMetadata() {
			layout.arrangeElements();

			// sync main button widths
			int mainWidth = entries.stream()
				.mapToInt(e -> e.button.getWidth())
				.filter(Entry::isMain)
				.max().orElse(8 * BUTTON_PADDING);
			entries.stream().filter(Entry::isMain).forEach(e -> e.button.setWidth(mainWidth));

			// sync hover button widths
			IntList groupWidths = IntArrayList.toList(
				groups.stream()
					.mapToInt(g -> g.stream()
						.skip(1) // avoid the main button
						.mapToInt(e -> e.button.getWidth()).max()
						.orElse(6 * BUTTON_PADDING)
					)
			);
			groups.forEach(g ->
				g.subList(1, g.size())
					.forEach(b ->
						b.button.setWidth(
							groupWidths.getInt(groups.indexOf(g))
						)
					)
			);


			// if the grid menu goes off the screen, shift it up
			int h = mc().getWindow().getGuiScaledHeight();
			if(layout.getHeight() + layout.getY() > h) {
				// moves the menu up by the amount it goes off the screen, plus a padding buffer
				layout.setY(h - layout.getHeight() - BUTTON_PADDING);
			}
			// if the grid menu goes off the screen, shift it left
			int w = mc().getWindow().getGuiScaledWidth();
			if(layout.getWidth() + layout.getX() > w) {
				// moves the menu left by the amount it goes off the screen, plus a padding buffer
				layout.setX(w - layout.getWidth() - BUTTON_PADDING);
			}
		}

		record Entry(int row, int col, int groupId, AbstractButton button, @NotNull Supplier<Component> tooltipCopyTextSupplier, @Nullable Button.OnPress pressAction) {
			boolean isMain() {
				return col == 0;
			}

			boolean isHover() {
				return col > 0;
			}

			Component id() {
				return button.getMessage();
			}
		}
	}
	}
}