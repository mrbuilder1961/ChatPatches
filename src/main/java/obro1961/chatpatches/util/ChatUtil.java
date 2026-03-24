package obro1961.chatpatches.util;

import com.google.common.collect.Lists;
import com.mojang.authlib.GameProfile;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectObjectMutablePair;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
//? if >1.21.11 {
//import net.minecraft.client.multiplayer.chat.GuiMessageSource;
//?}
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.*;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;
import obro1961.chatpatches.Boundary;
import obro1961.chatpatches.ChatLog;
import obro1961.chatpatches.ChatPatches;
import obro1961.chatpatches.config.Config;
//? if >=1.21.9 {
import obro1961.chatpatches.integration.ChatHeadsIntegration;
//? }
import obro1961.chatpatches.mixin.gui.ChatComponentMixin;
import obro1961.chatpatches.mixin.listener.ChatListenerMixin;
import org.apache.logging.log4j.core.util.Integers;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.minecraft.network.chat.CommonComponents.EMPTY;
import static obro1961.chatpatches.ChatPatches.*;
import static obro1961.chatpatches.util.TextUtil.withoutContent;

public class ChatUtil {
	public static final GameProfile NIL_SENDER = new GameProfile(Util.NIL_UUID, "");
	public static final GuiMessage NIL_HUD_LINE = new GuiMessage(0, EMPTY, null, /*? if >1.21.11 {*//*GuiMessageSource.SYSTEM_CLIENT,*//*?}*/ null);
	public static final MessageData NIL_MESSAGE_DATA = new MessageData(NIL_SENDER, Date.from(Instant.EPOCH), false);

	/** Contains the timestamp (can be empty) */
	public static final int TIMESTAMP_INDEX = 0;
	/** Contains the actual chat message */
	public static final int MESSAGE_INDEX = 1;
	/**
	 * Contains the duplicate counter (can be empty)
	 */
	public static final int DUPE_INDEX = 2;

	/**
	 * Contains the sender's team's name; used for `chat.type.team.*`
	 * messages (can be empty). Held within {@link #MESSAGE_INDEX}
	 */
	public static final int MSG_TEAM_INDEX = 0;
	/**
	 * Contains the sender's name. Held within {@link #MESSAGE_INDEX}
	 */
	public static final int MSG_SENDER_INDEX = 1;
	/**
	 * Contains the content of the sender's message. Held
	 * within {@link #MESSAGE_INDEX}
	 */
	public static final int MSG_CONTENT_INDEX = 2;

	/**
	 * Pulled from {@link ChatScreen#input}'s initializer in
	 * {@link ChatScreen#init()}
	 */
	public static final int MAX_MESSAGE_LENGTH = 256;

	private static Minecraft mc() { return Minecraft.getInstance(); }

	/**
	 * Contains the sender and timestamp data of the last received chat message.
	 *
	 * @see #modifyMessage(Component)
	 * @see ChatListenerMixin
	 */
	public static MessageData messageData = NIL_MESSAGE_DATA;

	/**
	 * Am extension of {@link #VANILLA_FORMAT} that includes support for messages
	 * containing a
	 * {@linkplain net.minecraft.network.chat.contents.objects.PlayerSprite#description()
	 * player head} in the chat message. This was split off from the original
	 * because it has the unavoidable side effect of not being able to parse team
	 * messages with prefixes or suffixes containing {@code ]} or {@code >}. For
	 * more info, <a href="https://github.com/mrbuilder1961/ChatPatches/pull/297">see
	 * the conversation (#297)</a>.
	 *
	 * @see <a href="https://github.com/mrbuilder1961/ChatPatches/pull/297#issuecomment-3724495903">Comment on #297</a>
	 */
	public static final Matcher CHAT_HEADS_FORMAT = Pattern.compile("^((-> )?\\[.+] )?<([^]>]*\\w{1,16}[^]>]*|\\[(\\w{1,16}) head][^]>]*\\4[^]>]*)>\\s.+$").matcher("");
	/**
	 * Matches only an entire vanilla player message. By default, this is
	 * translated under the {@code chat.type.text} and {@code chat.type.team.*}
	 * keys, which resolve to {@code <%s> %s} and {@code %s <%s> %s}*
	 * respectively (assuming no resource packs have modified them).
	 *
	 * <p>*The first argument, the team name, is typically surrounded in square
	 * brackets ({@code []}). Additionally, the {@code sent} team key resolves
	 * with a leading arrow ({@code -> }).
	 *
	 * @apiNote The vanilla player name alone can only match
	 * {@code /<[a-z0-9_]{3,16}>/} (not including legacy 1-2 letter names).
	 *
	 * @see <a href="https://regex101.com/r/LGbGU9/latest">Vanilla Message Format</a>
	 */
	public static final Matcher VANILLA_FORMAT = Pattern.compile("^((-> )?\\[[^<]+] )?<[^>]*(\\w{1,16})[^>]*>\\s.+$").matcher("");
	// ^ test this regex thoroughly (do both -fixes need the no > charclass? particularly the last?)
	/**
	 * The vanilla message format used by {@link #modifyMessage(Component)}
	 * and related methods. Depends on {@linkplain ChatHeadsIntegration#isActive()
	 * whether Chat Heads is installed}: normally, Chat Patches shouldn't
	 * reconstruct chat messages that aren't in the vanilla format, especially
	 * when they've been customized by the server. However, Chat Heads is an
	 * exception to this rule, because it is a client-side mod that operates in
	 * a similar fashion to Chat Patches; not to mention its widespread support
	 * for compatibility. <b>On Minecraft <1.21.9, this field is always equal to
	 * {@link #VANILLA_FORMAT}, as before Chat Heads could do everything itself.</b>
	 */
	public static final Matcher MESSAGE_FORMAT = /*? if >=1.21.9 {*/ ChatHeadsIntegration.isActive() ? CHAT_HEADS_FORMAT : /*?}*/ VANILLA_FORMAT;

	public static final Matcher PARSEABLE_MESSAGE_KEYS = Pattern.compile("chat.type.(text|team.(text|sent))").matcher("");


	/**
	 * @return The index of the {@linkplain GuiMessage.Line#endOfEntry end-of-entry}
	 * visible message that corresponds to the given message index, or {@code -1} if
	 * the message index is invalid.
	 *
	 * @param messageIndex The index of the message in {@link ChatComponent#allMessages} to find
	 * the corresponding visible for.
	 *
	 * @implNote Iterates through the minimum amount of {@linkplain ChatComponent#trimmedMessages
	 * visibles} necessary to get the given index to equal the count of iterated EoE
	 * messages, which signifies an effective 1:1 relationship between {@code messages}
	 * and {@code visibleMessages}. Once reached, the loop's index is returned,
	 * corresponding to the EoE message index.
	 *
	 * @see #tryCondenseDupes(Component)
	 * @see ChatLog#hideRecentMessages()
	 *
	 * @see #visible2Message(int)
	 */
	public static int message2Visible(int messageIndex) {
		var visibles = mc().gui.getChat().trimmedMessages;

		if(messageIndex == -1) {
			return -1; // avoids iterating through the entire list
		}

		int EoEs = -1; // # of EoE messages counted so far
		for(int j = 0; j < visibles.size(); j++) {
			if(visibles.get(j).endOfEntry()) {
				EoEs++;
				if(EoEs == messageIndex) {
					return j; // now the EoE message index that corresponds to the given message index
				}
			}
		}

		return -1;
	}

	/**
	 * @return The message index that corresponds to the given {@linkplain
	 * GuiMessage.Line#endOfEntry end-of-entry} visible message, or {@code -1}
	 * if the visible message index is invalid.
	 *
	 * @param visibleIndex The index of the <b>EoE</b> visible message in {@link
	 * ChatComponent#trimmedMessages} to find the corresponding message index of.
	 *
	 * @implNote After ensuring valid conditions, subtracts the number of non-EoE
	 * messages before the given index from the index itself to get the corresponding
	 * message index. Without this correction, the two message lists would not
	 * correspond 1:1 after the first multiline message, which caused a myriad of
	 * frustrating issues.
	 *
	 * @see ChatComponentMixin#getGuiMessageIndex(double, double)
	 *
	 * @see #message2Visible(int)
	 */
	public static int visible2Message(int visibleIndex) {
		var visibles = mc().gui.getChat().trimmedMessages;

		if(visibleIndex == -1 || visibleIndex >= visibles.size()) {
			return -1;
		}

		return (int)(visibleIndex - visibles.subList(0, visibleIndex).stream().filter(Predicate.not(GuiMessage.Line::endOfEntry)).count());
	}

	/**
	 * @see #deleteMessage(int, boolean)
	 * @see #deleteMessage(GuiMessage)
	 */
	@SuppressWarnings("UnusedReturnValue") // follows the List convention
	public static Pair<GuiMessage, List<GuiMessage.Line>> deleteMessageSilently(GuiMessage message) {
		return deleteMessage(mc().gui.getChat().allMessages.indexOf(message), false);
	}

	/**
	 * @see #deleteMessage(int, boolean)
	 * @see #deleteMessageSilently(GuiMessage)
	 */
	@SuppressWarnings("UnusedReturnValue") // follows the List convention
	public static Pair<GuiMessage, List<GuiMessage.Line>> deleteMessage(GuiMessage message) {
		return deleteMessage(mc().gui.getChat().allMessages.indexOf(message), true);
	}

	/**
	 * Deletes the message at the given index, which includes the actual message and
	 * all associated visible messages. Can be used while searching (visible messages
	 * are altered but the actual messages remain intact), but note the message is
	 * still permanently deleted and will not reappear when the query is changed.
	 *
	 * @param messageIndex The {@link ChatComponent#allMessages} index of the message
	 * to be removed.
	 * @param playBurnSound Whether to play {@link SoundEvents#LAVA_EXTINGUISH} when a
	 * message is deleted. Will not play if the message fails to/cannot be deleted.
	 *
	 * @return A {@link Pair} containing the {@linkplain GuiMessage message} removed
	 * along with the associated {@linkplain GuiMessage.Line visible messages}.
	 *
	 * @throws ArrayIndexOutOfBoundsException If the message index provided is negative
	 * or larger than the amount of messages present in {@link ChatComponent#allMessages}.
	 * Will also throw this error if visible message equivalents cannot be found for this
	 * message; however, this should never happen.
	 */
	public static Pair<GuiMessage, List<GuiMessage.Line>> deleteMessage(int messageIndex, boolean playBurnSound) { // todo see Config#sendBoundaryLine()
		ChatComponent chat = mc().gui.getChat();
		List<GuiMessage> messages = chat.allMessages;
		List<GuiMessage.Line> visibles = chat.trimmedMessages;
		// messages shown when searching don't have any issues (at least in terms of GuiMessage) because the index passed is based in #allMessages - perfect!

		if(messageIndex < 0 || messageIndex >= messages.size()) {
			// prepub: just logReportMsg an error? idk if throwing errors is the best idea
			throw new IndexOutOfBoundsException(messageIndex);
		}

		var deleted = messages.remove(messageIndex); // TODO: delete from chat log too - otherwise it'll come back on relog
		var deletedVisibles = new ObjectArrayList<GuiMessage.Line>();

		// this call works fine here or before the message is removed bc it only accesses visible messages, not the actual ones
		int v = message2Visible(messageIndex); // returns the EoE visible message corresponding to messageIndex

		if(v < 0 || v >= visibles.size()) {
			// prepub: just logReportMsg an error? idk if throwing errors is the best idea
			throw new IndexOutOfBoundsException(messageIndex); // should never happen because all messages should have a visible - this method is only called thru the helper of the same name
		}

		do deletedVisibles.add(visibles.remove(v)); // remove the visible message(s) of the message being deleted, starting with its own EoE
		while(v < visibles.size() && !visibles.get(v).endOfEntry()); // continue removing them until the next message (EoE) is reached

		if(playBurnSound) {
			mc().getSoundManager().playDelayed(SimpleSoundInstance.forUI(SoundEvents.LAVA_EXTINGUISH, 1.0f), 5);
		}

		LOGGER.debug("Deleted '{}' at index {} with {} visible(s)", deleted.content().getString(), messageIndex, deletedVisibles.size());
		return ObjectObjectMutablePair.of(deleted, deletedVisibles);
	}

	/**
	 * @return The message component at the given index of the given text's siblings,
	 * otherwise {@link CommonComponents#EMPTY} <b>(not the same as {@link Component#empty()})</b>
	 * if it doesn't exist. This prevents {@code IndexOutOfBoundsException} and
	 * {@code NullPointerException} errors, in accordance with the intention of Chat
	 * Patches to not brick the game if an error occurs.
	 *
	 * @apiNote Intended to be used with the MAIN indices specified in this class,
	 * although any positive index can be used.
	 *
	 * @see #TIMESTAMP_INDEX
	 * @see #MESSAGE_INDEX
	 * @see #DUPE_INDEX
	 */
	@NotNull
	public static Component getPart(Component message, int index) {
		return message.getSiblings().size() > index ? message.getSiblings().get(index) : EMPTY;
	}

	/**
	 * @return The message component at the given index of the given message's
	 * siblings' siblings, otherwise {@link CommonComponents#EMPTY} <b>(not the same as
	 * {@link Component#empty()})</b> if it doesn't exist. In other words, returns {@code
	 * message.getSiblings().get(MESSAGE_INDEX).getSiblings().get(index)} when
	 * {@code index} is a valid index.
	 *
	 * @apiNote Intended to be used with the {@code MSG} indices specified in this
	 * class, although any positive index can be used.
	 *
	 * @see #getPart(Component, int)
	 * @see #MSG_TEAM_INDEX
	 * @see #MSG_SENDER_INDEX
	 * @see #MSG_CONTENT_INDEX
	 */
	public static Component getMsgPart(Component message, int index) {
		return getPart(getPart(message, MESSAGE_INDEX), index);
	}

	/**
	 * Returns a {@link MutableComponent} representing the argument
	 * located at the given index of the given
	 * {@link TranslatableContents}. Needed because of a
	 * weird phenomenon where the
	 * {@linkplain TranslatableContents#getArgument(int) original
	 * <code>getArg</code> method} can return a non-Text object, which
	 * typically causes a {@link ClassCastException} to be thrown.
	 *
	 * @return Regular {@link Component} objects as expected,
	 * {@link String} arguments as {@linkplain Component#literal(String)
	 * literal texts}, and nulls as {@linkplain Component#empty() empty texts}.
	 */
	public static MutableComponent getArg(TranslatableContents content, int index) {
		// intentionally doesn't do a bounds check bc if that happens I need to know
		return TextUtil.asText( content.getArgs()[index] );
	}

	/**
	 * Builds a chat message from the given components.
	 * If anything is {@code null}, it is replaced with
	 * an empty Text, aside from {@code rootStyle} which
	 * is replaced with {@link Style#EMPTY}.
	 *
	 * @param rootStyle The style of the root Text component
	 * @param first {@link #TIMESTAMP_INDEX} or {@link #MSG_TEAM_INDEX}
	 * @param second {@link #MESSAGE_INDEX} or {@link #MSG_SENDER_INDEX}
	 * @param third {@link #DUPE_INDEX} or {@link #MSG_CONTENT_INDEX}
	 */
	@NotNull
	public static MutableComponent buildMessage(@Nullable Style rootStyle, @Nullable Component first, @Nullable Component second, @Nullable Component third) {
		MutableComponent root = Component.empty();

		if(rootStyle != null) {
			root.setStyle(rootStyle);
		}

		first = Objects.requireNonNullElse(first, Component.empty());
		second = Objects.requireNonNullElse(second, Component.empty());
		third = Objects.requireNonNullElse(third, Component.empty());

		return root.append(first).append(second).append(third);
	}

	/**
	 * @return A {@link GameProfile} representing the sender of the passed
	 * message. If the message doesn't have a sender, the style is empty, or
	 * the {@link HoverEvent} contained within is not an entity type, returns
	 * {@link Util#NIL_UUID}.
	 *
	 * @apiNote Since 1.21.5, the {@link HoverEvent.EntityTooltipInfo#name}
	 * field is {@linkplain Optional optional}, meaning a sender
	 * could have a {@link java.util.UUID} specified but no name.
	 *
	 * @param message The message to extract the sender from. Must be in the
	 * Chat Patches message format, as created by
	 * {@link #buildMessage(Style, Component, Component, Component)} or with
	 * sibling components in line with the established indices.
	 *
	 * @see #MESSAGE_INDEX
	 * @see #MSG_SENDER_INDEX
	 */
	public static GameProfile extractMessageSender(Component message) {
		Style style = getMsgPart(message, MSG_SENDER_INDEX).getStyle();

		/*? if >=1.21.5 {*/
		if(style.getHoverEvent() instanceof HoverEvent.ShowEntity(HoverEvent.EntityTooltipInfo info)) {
		/*?} else {*/
		/*if(style.getHoverEvent() != null && (Object)style.getHoverEvent().getValue(HoverEvent.Action.SHOW_ENTITY) instanceof HoverEvent.EntityTooltipInfo info) {*/
		/*?}*/
			String name = /*? if <=1.20.2 {*//*Optional.ofNullable*//*?}*/(info.name).orElse(EMPTY).getString();
			UUID id = info./*? if >=1.21.5 {*/uuid/*?} else {*//*id*//*?}*/;
			GameProfile result = new GameProfile(id, name);

			// XOR - we'll accept partial senders, but we want to warn about them
			if(name.isEmpty() ^ Util.NIL_UUID.equals(id)) {
				LOGGER.warn("Extracted partial message sender {} from '{}'", result, message.getString());
			}

			if(!name.isEmpty() || !Util.NIL_UUID.equals(id)) {
				return result;
			}
		}

		return NIL_SENDER;
	}

	public static String optimizeEmpties(Object o) {
		return (o instanceof String str ? str : String.valueOf(o))
			.replace("literal{}", "empty")
			.replace("[style={}]", "");
	}

	/**
	 * Reformats the incoming message {@code m} according to configured settings,
	 * message data, {@link #tryCondenseDupes(Component)}, and more.
	 *
	 * @see ChatComponentMixin#modifyMessage(Component)
	 *
	 * @implNote
	 * <ol>
	 *   <li>Return {@code m} early if the chat log is suspended to not cause
	 *   other issues. The only modification provided in this case is done by
	 *   {@link #tryCondenseDupes(Component)}</li>
	 * 	 <li>Reconstruct the message if {@linkplain Config#name it's wanted},
	 * 	 it has player message data, and is {@linkplain #CHAT_HEADS_FORMAT in
	 * 	 the vanilla format}:
	 *     	 <ol>
	 *     	     <li>If the message is {@linkplain TranslatableContents
	 *     	     translatable} and in a {@linkplain #PARSEABLE_MESSAGE_KEYS
	 *     	     known format}:
	 *     	     	<ol>
	 *     	     	  <li>If the message is a team message, add all related
	 *     	     	  team message components.</li>
	 *     	     	  <li>Add the formatted playername and content.</li>
	 *     	     	</ol>
	 *     	     </li>
	 *     	     <li>Otherwise, the message isn't translatable, but it is
	 *     	     formatted correctly, so:
	 *     	     	<ol>
	 *     	     	  <li>Collect all message siblings into a list, including the
	 *     	     	  root {@link ComponentContents}</li>
	 *     	     	  <li>Find the first part that contains a {@code >}.</li>
	 *     	     	  <li>Cache the part after the {@code >} but before any
	 *     	     	  remaining siblings, if present.</li>
	 *     	     	  <li>Add every part succeeding the {@code >} part to
	 *     	     	  {@code realContent}.</li>
	 *     	     	  <li>Add the formatted playername and {@code realContent}
	 *     	     	  variable to the actual content.</li>
	 *     	     	</ol>
	 *     	     </li>
	 *     	 </ol>
	 * 	 </li>
	 *   <li>If the message shouldn't be formatted (doesn't satisfy all
	 *   prerequisites), then don't do anything to {@code m}.</li>
	 *   <li>If {@link Config#logMessageStructures} is toggled, throw an {@link
	 *   AssertionError} to force debug logging of messages.</li>
	 * 	 <li>Assemble the message, despite any changes, and {@linkplain
	 * 	 #tryCondenseDupes(Component) add a duplicate counter}.</li>
	 * 	 <li>Log the modified message in the {@link ChatLog}.</li>
	 * 	 <li>Reset {@link ChatUtil#messageData} to prevent a rare bug.</li>
	 * 	 <li>Return the built message.</li>
	 * </ol>
	 */
	public static Component modifyMessage(@NotNull Component m) {
		if(ChatLog.isRestoring()) {
			return tryCondenseDupes(m); // cancel modifications when loading the chat log minus the minimal dupe counter
		}

		boolean lastEmpty = messageData.equals(NIL_MESSAGE_DATA); // also signifies that this is a system message (when true)
		boolean boundary = Boundary.isBoundaryLine(m);
		Date now = lastEmpty ? new Date() : messageData.timestamp;
		Style style = m.getStyle();

		MutableComponent timestamp = null;
		MutableComponent content = m.copy(); // default to the original message
		// dupe counter is always empty at this stage

		//noinspection UnusedAssignment: will throw an error on <1.21.9
		Optional<MutableComponent> head = Optional.empty();

		try {
			timestamp = config.makeTimestamp(now, lastEmpty, boundary);

			// reconstruct the player message if it's in the vanilla format & it should be reformatted
			// the messageData vanilla means the original message was vanilla-formatted, and the regex check means it still is.
			// see Xaero's Minimap waypoint sharing for more information (#158)
			Matcher matcher = MESSAGE_FORMAT.reset(m.getString());
			if(config.name && !lastEmpty && messageData.vanilla && matcher.matches()) {
				content = Component.empty().setStyle(style);
				/*? if >=1.21.9 {*/head = ChatHeadsIntegration.getHeadIfEnabled(m, matcher);/*?}*/

				// if the message is translatable, then we know exactly where everything is
				if(m.getContents() instanceof TranslatableContents ttc && PARSEABLE_MESSAGE_KEYS.reset(ttc.getKey()).matches()) {
					boolean team = ttc.getKey().contains("team");

					// adds the team name for team messages
					MutableComponent teamPart = Component.empty();
					if(team) {
						// adds the preceding arrow for sent team messages
						if(ttc.getKey().endsWith("sent")) {
							teamPart.append(Component.literal("-> ").setStyle(style)); // "-> {team} <{player}> {content}"
						}

						// adds the team name for team messages
						teamPart.append( getArg(ttc, MSG_TEAM_INDEX).copy().append(" ") ); // copy to prevent UOEs on 1.20.3+ (#199)
					}
					content.append(teamPart); // adds the team part or nothing to keep MSG_TEAM_INDEX constant

					// adds the formatted playername and content for all message types
					content.append( config.formatPlayername(head, messageData.sender) );
					content.append( getArg(ttc, team ? MSG_CONTENT_INDEX : MESSAGE_INDEX) );
				} else { // reconstructs the message if it matches the vanilla format '<%s> %s' but isn't translatable
					MutableComponent realContent = Component.empty();
					// collect all message parts into one list, including the root TextContent
					List<Component> parts = Lists.asList( m.plainCopy().setStyle(style), m.getSiblings().toArray(new Component[0]) );

					// find the first index of a '>' in the '<%s> %s'-formatted message
					Component firstPart = parts.stream()
						.filter(p -> p.getString().contains(">"))
						.findFirst()
						.orElseGet(() -> {
							String error = "No closing angle bracket found in vanilla message '" + m.getString() + "'!";
							logReportMsg(new IllegalStateException(error));
							ChatPatches.pushErrorToast("Message modification error", error);
							return Component.literal(">");
						});

					String[] split = firstPart.getString().split(">"); // fixes (#156)
					String afterEndBracket = split.length > 1 ? split[1] : ""; // only get the part after the closing bracket

					// ignore everything before the '>' because it's the playername, which we already know
					// adds the part after the closing bracket but before any remaining siblings, if it exists
					if(!afterEndBracket.isEmpty()) {
						// stripLeading() prevents the space between the '>' and the message from appearing in the message content
						// also prevents the extra space from appearing with Chat Heads (test: StyledChat too?)
						realContent.append( Component.literal(afterEndBracket.stripLeading()).setStyle(firstPart.getStyle()) );
					}

					// we know everything remaining is message content parts, so add everything
					for(int i = parts.indexOf(firstPart) + 1; i < parts.size(); i++) {
						realContent.append(parts.get(i));
					}

					content.append(EMPTY); // keeps MSG_TEAM_INDEX constant
					content.append(config.formatPlayername(head, messageData.sender)); // sender data is already known
					content.append(realContent); // adds the reconstructed message content
				}
			}

			if(config.logMessageStructures) {
				throw new AssertionError("Time to log those message structures!", null);
			}
		} catch(RuntimeException | AssertionError e) {
			LOGGER.error("An error occurred while modifying '{}'", m.getString());
			LOGGER.error("(lastEmpty={}, boundary={}, messageData.vanilla={}, config.name={}, config.nameFormat='{}')",
				lastEmpty, boundary, messageData.vanilla, config.name, config.nameFormat);
			LOGGER.error("\tTimestamp: {}", optimizeEmpties(timestamp));
			LOGGER.error("\tBody:");

			if(content.getSiblings().size() == 3 && !content.equals(m)) { // modified vanilla message
				// i know these are technically the wrong fields to use but they make sense so leave me alone
				LOGGER.error("\t\tTeam: {}", optimizeEmpties(getPart(content, MSG_TEAM_INDEX)));
				LOGGER.error("\t\tSender: {}", optimizeEmpties(getPart(content, MSG_SENDER_INDEX)));
				LOGGER.error("\t\tContent: {}", optimizeEmpties(getPart(content, MSG_CONTENT_INDEX)));
			} else { // literally everything else
				LOGGER.error("\t\tRoot: {}", optimizeEmpties(content.getContents()));
				for(int i = 0; i < content.getSiblings().size(); i++) {
					LOGGER.error("\t\tSibling {}: {}", i, optimizeEmpties(getPart(content, i)));
				}
			}
			if(m.getSiblings().size() == DUPE_INDEX + 1) {
				LOGGER.error("\tDupes: {}", optimizeEmpties(getPart(m, DUPE_INDEX)));
			}

			LOGGER.error("-- End of message structure --");

			if(e instanceof RuntimeException) { // don't log forced errors
				logReportMsg(e);
				ChatPatches.pushErrorToast("Message modification error", e.getMessage());
			}
		}

		// assembles constructed message and tries to add a dupe counter
		Component modified = tryCondenseDupes(buildMessage(null, timestamp, content, null)); // style is null bc only the message content should take on the original style
		ChatLog.addMessage(modified);
		messageData = NIL_MESSAGE_DATA; // fixes messages that get around ChatListenerMixin's data caching, usually thru ChatHud#addMessage (ex. open-to-lan message)
		return modified;
	}

	/**
	 * Updated, more efficient version of the original {@code addCounter} and {@code
	 * getCondensedMessage} method combo. This method is used in conjunction with
	 * (after) {@link #modifyMessage(Component)} to add a duplicate counter and remove
	 * duplicate(s) to the given message, if they exist, and according to the config.
	 *
	 * @implNote
	 * <ol>
	 *     <li>Returns the message if the dupe counter is disabled or there are no
	 *     messages to check.</li>
	 *     <li>Calculates the attempt distance for condensing the incoming message:</li>
	 *     <ol>
	 *         <li>If {@linkplain Config#compactChat CompactChat} is enabled, takes
	 *         {@link Config#compactDistance} and parses {@code 0} as {@code
	 *         chatHud.getVisibleLineCount()}, otherwise its absolute value.</li>
	 *         <li>Else uses 1, which is the default and corresponds to the most recent
	 *         message only.</li>
	 *     </ol>
	 *     <li>Iterates through the last {@code attemptDistance} messages to find and
	 *     condense (remove) any duplicates.</li>
	 *     <ol>
	 *         <li>If the incoming message is different from the iterated message, in
	 *         terms of text or {@linkplain Config#counterCheckStyle style data},
	 *         doesn't try to condense it. Otherwise:</li>
	 *         <li>Removes all number formatting codes and non-digits from the iterated
	 *         message's dupe counter, parse it with a fallback of 1 (no counter means
	 *         1 message), and add it to the total dupe count.</li>
	 *         <li>Removes all visible message(s), starting at the iterated index (or,
	 *         when CompactChat is enabled, the {@linkplain #message2Visible(int)
	 *         corrected index}) until the next (EoE) message is reached.</li>
	 *         <li>Removes the message being condensed. Done towards the end to ensure
	 *         {@link #message2Visible(int)} returns an accurate value if necessary.</li>
	 *         <li>Decrements the index to prevent skipping the next message.</li>
	 * 	       <li>Decrements the attempt distance to prevent checking extra
	 * 	       messages.</li>
	 *     </ol>
	 *     <li>Updates the incoming message with the new dupe counter, if the total dupe
	 *     count is greater than 1.</li>
	 *     <li>Returns the incoming message, regardless of if it was actually modified
	 *     or not, reconstructed to avoid an {@link ArrayIndexOutOfBoundsException}
	 *     since 1.20.3+
	 *     (<a href="https://github.com/mrbuilder1961/ChatPatches/issues/199">#199</a>)</li>
	 * </ol>
	 */
	private static Component tryCondenseDupes(Component incoming) {
		// prepub: make this method save to the chat log too (so no redundant messages)!
		ChatComponent chat = mc().gui.getChat();
		List<GuiMessage> messages = chat.allMessages;

		if(!config.counter || messages.isEmpty()) {
			return incoming;
		}

		ObjectList<Component> siblings = new ObjectArrayList<>( incoming.getSiblings() ); // prevents UOEs on 1.20.3+ (#199)
		List<GuiMessage.Line> visibles = chat.trimmedMessages;
		int attemptDistance =
			switch(config.compactChat ? Math.abs(config.compactDistance) : 1) {
				case 0 -> chat.getLinesPerPage();
				case 1 -> 1; // only check more messages if compact chat is enabled
				default -> Math.min(config.compactDistance, messages.size()); // max checked = # of messages in chat, else config option
			};

		// iterate through the last `attemptDistance` messages to find and condense (remove) any duplicates
		int dupeCount = 1;
		for(int i = 0; i < attemptDistance && i < messages.size(); i++) {
			Component msg = messages.get(i).content();

			if( !getPart(incoming, MESSAGE_INDEX).getString().equalsIgnoreCase(getPart(msg, MESSAGE_INDEX).getString()) ) {
				// if the incoming message is different from the iterated message, don't try to condense it
				continue;
			} else if( config.counterCheckStyle && !TextUtil.virtuallyEqual(getPart(incoming, MESSAGE_INDEX), getPart(msg, MESSAGE_INDEX)) ) {
				// if the incoming message has different metadata from the iterated message, skip it
				continue; //FIXME: THIS DOES NOT WORK AT ALL - calling a style compiler method again aka getFormattingcodes! RUGGHGHSDKFJH
			}

			// remove all number formatting codes and non-digits, then replace empty strings with 1 to prevent NumberFormatExceptions
			// finally add it to the total dupe count
			dupeCount += Integers.parseInt( getPart(msg, DUPE_INDEX).getString().replaceAll("(§\\d)|\\D", "") , 1);

			int v = config.compactChat ? message2Visible(i) : i; // ensure that `i` correctly maps to its EoE visible
			do visibles.remove(v); // remove the visible message(s) of the message being condensed, starting with its own EoE
			while(v < visibles.size() && !visibles.get(v).endOfEntry()); // continue removing them until the next message (EoE) is reached

			// remove the message being condensed; done last to ensure #message2Visible(.) works correctly
			messages.remove(i);

			i--;  // we removed the first message, but we don't want to skip the next one
			attemptDistance--; // but we also don't want to check messages we shouldn't be checking
		}

		// update the incoming message with the new dupe counter
		if(dupeCount > 1) {
			if(siblings.size() > DUPE_INDEX) {
				siblings.set(DUPE_INDEX, config.makeDupeCounter(dupeCount)); // this will throw errors if DUPE_INDEX doesn't exist!
			} else {
				// don't bother reporting this, restoring dumped logs and other edge cases will cause the same issue
				// no use in creating more headaches when a perfectly valid solution exists right here
				siblings.add(config.makeDupeCounter(dupeCount));
			}
		}

		return TextUtil.newSiblings(incoming, siblings);
	}


	public record MessageData(GameProfile sender, Date timestamp, boolean vanilla) {}
}