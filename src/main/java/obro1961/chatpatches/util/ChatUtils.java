package obro1961.chatpatches.util;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.util.ChatMessages;
import net.minecraft.text.*;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import obro1961.chatpatches.ChatPatches;
import obro1961.chatpatches.accessor.ChatHudAccessor;
import obro1961.chatpatches.chatlog.ChatLog;
import obro1961.chatpatches.config.Config;
import obro1961.chatpatches.mixin.gui.ChatHudMixin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.*;

import static obro1961.chatpatches.ChatPatches.config;
import static obro1961.chatpatches.ChatPatches.msgData;
import static obro1961.chatpatches.util.TextUtils.copyWithoutContent;
import static obro1961.chatpatches.util.TextUtils.reorder;

/**
 * Utility methods relating directly to the chat.
 */
public class ChatUtils {
	public static final UUID NIL_UUID = new UUID(0, 0);
	public static final MessageData NIL_MSG_DATA = new MessageData(new GameProfile(ChatUtils.NIL_UUID, ""), Date.from(Instant.EPOCH), false);
	public static final int TIMESTAMP_INDEX = 0,   // contains the timestamp (can be empty)
							MESSAGE_INDEX = 1,     // contains the actual chat message
							DUPE_INDEX = 2;        // contains the duplicate counter (can be empty)
	public static final int MSG_TEAM_INDEX = 0,    // contains the sender's team's name; used for `chat.type.team.*` messages (can be empty)
							MSG_SENDER_INDEX = 1,  // contains the sender's name
							MSG_CONTENT_INDEX = 2; // contains the content of the sender's message
	/**
	 * Matches only an entire vanilla player message.
	 * By default, this is translated under the
	 * {@code chat.type.text} and
	 * {@code chat.type.team.*} keys, which resolve
	 * to {@code <%s> %s} and {@code %s <%s> %s}*
	 * respectively (assuming no resource
	 * packs have modified them).
	 *
	 *
	 * <p>*The first argument, the team name, is
	 * typically surrounded in square brackets
	 * ({@code []}). Additionally, the team key
	 * ending in {@code sent} resolves with a
	 * leading arrow ({@code -> }).
	 *
	 *
	 * @implNote The vanilla player name alone
	 * can only match {@code /<[a-z0-9_]{3,16}>/};
	 * however, when factoring in team pre- and
	 * suf-fixes, this limit becomes irrelevant.
	 */
	public static final String VANILLA_FORMAT = "(?i)^((-> )?\\[.+] )?<.{3,}>\\s.+$";
	public static final String PARSEABLE_MESSAGE_KEYS = "chat.type.(text|team.(text|sent))";

	/**
	 * Returns the message component at the given index;
	 * returns an empty Text if it doesn't exist. This
	 * prevents {@code IndexOutOfBoundsException} and
	 * {@code NullPointerException} errors.
	 *
	 * @apiNote Intended to be used with the MAIN
	 * indices specified in this class.
	 */
	public static Text getPart(Text message, int index) {
		return message.getSiblings().size() > index ? message.getSiblings().get(index) : Text.empty();
	}

	/**
	 * Returns the message component at the given index of the
	 * given message; returns an empty Text if it doesn't exist.
	 *
	 * @apiNote Intended to be used with the {@code MSG}
	 * indices specified in this class.
	 */
	public static Text getMsgPart(Text message, int index) {
		return getPart(getPart(message, MESSAGE_INDEX), index);
	}

	/**
	 * Returns a {@link MutableText} representing the argument
	 * located at the given index of the given
	 * {@link TranslatableTextContent}. Needed because of a
	 * phenomenon where the {@link TranslatableTextContent#getArg(int)}
	 * weird phenomenon where the
	 * method can return a {@link String} or other non-Text related
	 * {@linkplain TranslatableTextContent#getArg(int) original
	 * object, which otherwise causes {@link ClassCastException}s.
	 * <code>getArg</code> method} can return a non-Text object, which
	 * <p>
	 * typically causes a {@link ClassCastException} to be thrown.
	 * Wraps {@link String}s in {@link Text#literal(String)}
	 * and nulls in {@link Text#empty()}.
	 *
	 *
	 * @implNote
	 * @return Regular {@link Text} objects as expected,
	 * If {@code index} is negative, it's added to the args array
	 * {@link String} arguments as {@linkplain Text#literal(String)
	 * length. In other words, passing index {@code -n} will
	 * literal texts}, and nulls as {@linkplain Text#empty() empty texts}.
	 */
	public static MutableText getArg(TranslatableTextContent content, int index) {
		return switch( content.getArgs()[index] ) {
			case Text t -> (MutableText) t;
			case StringVisitable sv -> Text.literal(sv.getString());
			case String s -> Text.literal(s);
			default -> Text.empty();
		};
	}

	/**
	 * Builds a chat message from the given components.
	 * If anything is {@code null}, it is replaced with
	 * an empty Text, aside from {@code rootStyle} which
	 * is replaced with {@link Style#EMPTY}.
	 *
	 * @param rootStyle The style of the root Text component
	 * @param first   The first component of the message,
	 *                  either the timestamp or team name
	 * @param second   The second component of the message,
	 *                  either the message or the sender
	 * @param third The third component of the message,
	 *                  either the dupe counter or the message
	 *                  content
	 */
	@NotNull
	public static MutableText buildMessage(@Nullable Style rootStyle, @Nullable Text first, @Nullable Text second, @Nullable Text third) {
		MutableText root = Text.empty();

		if(rootStyle != null)
			root.setStyle(rootStyle);

		first = Objects.requireNonNullElse(first, Text.empty());
		second = Objects.requireNonNullElse(second, Text.empty());
		third = Objects.requireNonNullElse(third, Text.empty());

		return root.append(first).append(second).append(third);
	}

	/**
	 * Reformats the incoming message {@code m} according to configured
	 * settings, message data, and at indices specified in this class.
	 * This method is used in the {@link ChatHudMixin#modifyMessage(Text, boolean)}
	 * mixin for functionality.
	 *
	 * @implNote
	 * <ol>
	 *   <li>Don't modify when {@code refreshing} is true, as that signifies
	 * 	 re-rendering chat messages, so simply return {@code m}.</li>
	 * 	 <li>Declare relevant variables, most notably the {@code timestamp}
	 * 	 and {@code content} components.</li>
	 * 	 <li>Reconstruct the player message if it should be reformatted
	 * 	 (message has player data, not a boundary line, and in vanilla
	 * 	 format):
	 *     	 <ol>
	 *     	     <li>If the message is translatable and in a known format:
	 *     	     	<ol>
	 *     	     	  <li>If the message is a team message, add all related
	 *     	     	  team message components.</li>
	 *     	     	  <li>Add the formatted playername and content.</li>
	 *     	     	</ol>
	 *     	     </li>
	 *     	     <li>Otherwise, the message must be in an unknown format where all
	 *     	     we know for sure is the format ({@code <$name> $message}):
	 *     	     	<ol>
	 *     	     	  <li>Collect all message components into a list, including the
	 *     	     	  root {@link TextContent} (assuming this accounts for all parts,
	 *     	     	  {@link TextContent}s, and siblings).</li>
	 *     	     	  <li>Find the first part that contains a '>'.</li>
	 *     	     	  <li>Add the part after the '>' but before any
	 *     	     	  remaining siblings, if it exists, to the {@code realContent}
	 * 	      	      local Text variable (with the proper Style).</li>
	 *     	     	  <li>Add every part succeeding the '>' component to
	 *     	     	  the {@code realContent} variable.</li>
	 *     	     	  <li>Add the formatted playername and {@code realContent}
	 *     	     	  variable to the actual content.</li>
	 *     	     	</ol>
	 *     	     </li>
	 *     	 </ol>
	 * 	 </li>
	 *   <li>If the message shouldn't be formatted (doesn't satisfy all
	 *   prerequisites), then don't change {@code m} and store it.</li>
	 * 	 <li>Assemble the constructed message and add a duplicate counter
	 * 	 according to the {@link ChatUtils#addCounter(Text)} method.</li>
	 * 	 <li>Log the modified message in the {@link ChatLog}.</li>
	 * 	 <li>Reset the {@link ChatPatches#msgData} to prevent an uncommon bug.</li>
	 * 	 <li>Return the message, regardless of if it was actually modified or not.</li>
	 * </ol>
	 */
	public static Text modifyMessage(@NotNull Text m) {
		if(ChatLog.isSuspended())
			return m; // cancels modifications when loading the chat log or regenerating visibles

		boolean lastEmpty = msgData.equals(ChatUtils.NIL_MSG_DATA);
		Date now = lastEmpty ? new Date() : msgData.timestamp;
		String nowStr = String.valueOf(now.getTime()); // for context menu and storing timestamp data! only affects the timestamp
		Style style = m.getStyle();

		MutableText timestamp = null;
		MutableText content = m.copy();

		try {
			timestamp = config.time ? config.makeTimestamp(now).setStyle( config.makeHoverStyle(now) ) : Text.empty().styled(s -> s.withInsertion(nowStr));
			content = Text.empty().setStyle(style);

			// reconstruct the player message if it's in the vanilla format and it should be reformatted
			// the msgData vanilla means the original message was vanilla-formatted, and the regex check means it still is.
			// see Xaero's Minimap waypoint sharing for more information (#158)
			if(!lastEmpty && msgData.vanilla && m.getString().matches(VANILLA_FORMAT)) {
				// if the message is translatable, then we know exactly where everything is
				if(m.getContent() instanceof TranslatableTextContent ttc && ttc.getKey().matches(PARSEABLE_MESSAGE_KEYS)) {
					boolean team = ttc.getKey().contains("team");

					// adds the team name for team messages
					MutableText teamPart = Text.empty();
					if(team) {
						// adds the preceding arrow for sent team messages
						if(ttc.getKey().endsWith("sent"))
							teamPart.append(Text.literal("-> ").setStyle(style)); // "-> {team} <{player}> {content}"

						// adds the team name for team messages
						teamPart.append( getArg(ttc, MSG_TEAM_INDEX).copy().append(" ") ); // copy to prevent UOEs on 1.20.3+ (#199)
					}
					content.append(teamPart); // adds the team part or nothing to keep MSG_TEAM_INDEX constant

					// adds the formatted playername and content for all message types
					content.append( config.formatPlayername(msgData.sender) );
					content.append( getArg(ttc, team ? MSG_CONTENT_INDEX : MESSAGE_INDEX) );
				} else { // reconstructs the message if it matches the vanilla format '<%s> %s' but isn't translatable
					// collect all message parts into one list, including the root TextContent
					// (assuming this accounts for all parts, TextContents, and siblings)
					List<Text> parts = Util.make(new ArrayList<>(m.getSiblings().size() + 1), a -> {
						if(!m.equals(Text.EMPTY))
							a.add( m.copyContentOnly().setStyle(style) );

						a.addAll( m.getSiblings() );
					});

					MutableText realContent = Text.empty();
					// find the first index of a '>' in the '<%s> %s'-formatted message
					Text firstPart = parts.stream()
						.filter(p -> p.getString().contains(">"))
						.findFirst()
						.orElseThrow(() -> ChatPatches.logAndThrowReportMsg(
							new IllegalStateException("No closing angle bracket found in vanilla message '" + m.getString() + "'!")
						));

					String[] endBracketSplit = firstPart.getString().split(">"); // part of #156 AIOOBE i=1 fix
					String afterEndBracket = endBracketSplit.length > 1 ? endBracketSplit[1] : ""; // just get the part after the closing bracket, we know the start

					// ignore everything before the '>' because it's the playername, which we already know
					// adds the part after the closing bracket but before any remaining siblings, if it exists
					if(!afterEndBracket.isEmpty())
						realContent.append( Text.literal(afterEndBracket).setStyle(firstPart.getStyle()) );

					// we know everything remaining is message content parts, so add everything
					for(int i = parts.indexOf(firstPart) + 1; i < parts.size(); i++)
						realContent.append(parts.get(i));

					content.append(config.formatPlayername(msgData.sender)); // sender data is already known
					content.append(realContent); // adds the reconstructed message content
				}
			} else {
				// don't reformat if it isn't vanilla or needed
				content = m.copy();
			}
		} catch(RuntimeException e) {
			ChatPatches.LOGGER.error("[ChatUtils.modifyMessage] An error occurred while modifying message '{}':", m.getString());
			ChatPatches.LOGGER.error("[ChatUtils.modifyMessage] \tModified message structure:");
			ChatPatches.LOGGER.error("[ChatUtils.modifyMessage] \t\tTimestamp structure: {}", timestamp);
			ChatPatches.LOGGER.error("[ChatUtils.modifyMessage] \t\tContent structure: {}", content);
			ChatPatches.logReportMsg(e);
		}

		// assembles constructed message and adds a duplicate counter according to the #addCounter method
		Text modified = addCounter( ChatUtils.buildMessage(style, timestamp, content, null) );
		ChatLog.addMessage(modified);
		msgData = ChatUtils.NIL_MSG_DATA; // fixes messages that get around MessageHandlerMixin's data caching, usually thru ChatHud#addMessage (ex. open-to-lan message)
		return modified;
	}

	/**
	 * Adds a duplicate counter to the chat message, indicating how many times
	 * the same message has been sent. Can check only the last message, or
	 * {@link Config#counterCompactDistance} times back. Slightly more
	 * efficient than the older method, although it's still quite slow.
	 *
	 * @implNote
	 * <ol>
	 *     <li>IF {@code COUNTER} is enabled AND the message count >0 AND the message isn't a boundary line, continue.</li>
	 *     <li>Cache the result of trying to condense the incoming message with the last message received.</li>
	 *     <li>IF the counter should use the CompactChat method and the message wasn't already condensed:</li>
	 *     <ol>
	 *         <li>Calculate the adjusted distance to attempt comparing, depending on the amount of messages already in the chat.</li>
	 *         <li>Filter all the messages within the target range that are case-insensitively equal to the incoming message.</li>
	 *         <li>If a message was the same, call {@link ChatUtils#getCondensedMessage(Text, int)},
	 *         which ultimately removes that message and its visibles.</li>
	 *     </ol>
	 *     <li>Return the (potentially) condensed message, to then be used further in {@link #modifyMessage(Text)}.</li>
	 * </ol>
	 * (Wraps the entire method in a try-catch to prevent any errors accidentally disabling the chat.)
	 *
	 * @see ChatUtils#getCondensedMessage(Text, int)
	 */
	public static Text addCounter(Text incoming) {
		ChatHud hud = MinecraftClient.getInstance().inGameHud.getChatHud();
		List<ChatHudLine> messages = ((ChatHudAccessor) hud).chatpatches$getMessages();

		try {
			if( config.counter && !messages.isEmpty() ) {
				// condenses the incoming message into the last message if it is the same
				Text condensedLastMessage = getCondensedMessage(incoming, 0);

				// if the counterCompact option is true but the last message received was not condensed, look for
				// any dupes in the last counterCompactDistance messages and if any are found condense them
				if( config.counterCompact && condensedLastMessage.equals(incoming) ) {
					// ensures {0 <= attemptDistance <= messages.size()} is true
					int attemptDistance = MathHelper.clamp((
						(config.counterCompactDistance == -1)
							? messages.size()
							: (config.counterCompactDistance == 0)
								? hud.getVisibleLineCount()
								: config.counterCompactDistance
					), 0, messages.size());

					// exclude the first message, already checked above
					messages.subList(1, attemptDistance)
						.stream()
						.filter( hudLine -> getPart(hudLine.content(), MESSAGE_INDEX).getString().equalsIgnoreCase( getPart(incoming, MESSAGE_INDEX).getString() ) )
						.findFirst()
						.ifPresent( hudLine -> getCondensedMessage(incoming, messages.indexOf(hudLine)) );
				}

				// this result is used in #modifyMessage(...)
				return condensedLastMessage;
			}
		} catch(IndexOutOfBoundsException e) {
			ChatPatches.LOGGER.error("[ChatHudMixin.addCounter] Couldn't add duplicate counter because message '{}' ({} parts) was not constructed properly.", incoming.getString(), incoming.getSiblings().size());
			ChatPatches.logReportMsg(e);
		} catch(Exception e) {
			ChatPatches.LOGGER.error("[ChatHudMixin.addCounter] /!\\ Couldn't add duplicate counter because of an unexpected error! /!\\");
			ChatPatches.logReportMsg(e);
		}

		return incoming;
	}

	/**
	 * Tries to condense the {@code index} message into the incoming message
	 * if they're case-insensitively equal. This method is functionally
	 * similar to the original
	 * {@code ChatHudMixin#addCounter(Text, boolean)}
	 * before {@code v194.5.0}.
	 * <padding><br>The main difference is that this method
	 * removes the old message and edits the incoming message, rather than
	 * editing the old message and ignoring the incoming message, which
	 * makes it slightly faster. The other difference is that this accepts
	 * the {@code index} of the message to condense, rather than always
	 * assuming index {@code 0} (the most recent received message). This
	 * lets it be used repetitively for the CompactChat method option.</padding>
	 * <padding><br>Returns a condensed version of {@code incoming} if the two messages
	 * were case-insensitively equal and the caller should {@code return},
	 * otherwise simply returns {@code incoming}.</padding>
	 *
	 * @implNote
	 * <ol>
	 *     <li>IF the actual message content of the incoming message and the message being compared are equal,
	 *     AND (if we need to check the style) if the messages' metadata are equal, continue.</li>
	 *     <li>Cache the number of duped messages, either from the message being compared or from inference plus (this) one.</li>
	 *     <li>Add the dupe counter to the incoming message.</li>
	 *     <li>Remove the message being compared.</li>
	 *     <li>Calculate and then remove all visible messages from the last message, compared as {@link String}s from {@link TextUtils#reorder(OrderedText, boolean)}.</li>
	 *     <li>Return the incoming message, regardless of if it was modified or not.</li>
	 * </ol>
	 */
	public static Text getCondensedMessage(Text incoming, int index) {
		final MinecraftClient client = MinecraftClient.getInstance();
		final ChatHud chatHud = client.inGameHud.getChatHud();
		final ChatHudAccessor chat = (ChatHudAccessor) chatHud;
		final List<ChatHudLine> messages = chat.chatpatches$getMessages();
		final List<ChatHudLine.Visible> visibleMessages = chat.chatpatches$getVisibleMessages();

		ChatHudLine comparingLine = messages.get(index); // message being compared
		List<Text> comparingParts = comparingLine.content().getSiblings();
		List<Text> incomingParts = new ArrayList<>( incoming.getSiblings() ); // prevents UOEs on 1.20.3+


		// IF the comparing and incoming message bodies are case-insensitively equal,
		// AND (if we need to check the style) if the messages' metadata are equal, continue
		Text incMsg = incomingParts.get(MESSAGE_INDEX), compMsg = comparingParts.get(MESSAGE_INDEX);
		boolean equalIgnoreCase = incMsg.getString().equalsIgnoreCase( compMsg.getString() );
		if( equalIgnoreCase && (!config.counterCheckStyle || copyWithoutContent(incMsg).equals(copyWithoutContent(compMsg))) ) {

			// info: according to some limited testing, incoming messages (incomingParts) will never contain a dupe counter, so it's been omitted from this check
			int dupes = (
				comparingParts.size() > DUPE_INDEX
					? Integer.parseInt( comparingParts.get(DUPE_INDEX).getString()
						.replaceAll("(§[0-9a-fk-or])+", "")
						.replaceAll("\\D", "")
						.replaceAll("^$", "1") // if the string is empty, replace it with 1 (to prevent NumberFormatException)
					)
					: 1
			) + 1;


			// i think when old messages are re-added into the chat, it keeps the dupe counter so we have to use set() instead of add() sometimes
			if(incomingParts.size() > DUPE_INDEX)
				incomingParts.set(DUPE_INDEX, config.makeDupeCounter(dupes));
			else
				incomingParts.add(DUPE_INDEX, config.makeDupeCounter(dupes));

			messages.remove(index);

			List<String> calcVisibles = ChatMessages.breakRenderedChatMessageLines(comparingLine.content(), MathHelper.floor(chatHud.getWidth() / chatHud.getChatScale()), client.textRenderer)
				.stream()
				.map( visible -> reorder(visible, config.counterCheckStyle) ) // note: config opt may not be necessary/have any effect here
				.toList();

			if(config.counterCompact) {
				// note: could be unnecessarily slow? should only be checking config.counterCompactDistance ahead, but this always checks everything
				// same here w/ config.counterCheckStyle as previous note
				visibleMessages.removeIf(hudLine -> calcVisibles.stream().anyMatch(ot -> ot.equalsIgnoreCase( reorder(hudLine.content(), config.counterCheckStyle) )));
			} else {
				do visibleMessages.removeFirst();
				while(!visibleMessages.isEmpty() && !visibleMessages.getFirst().endOfEntry());
			}

			// according to some testing, modifying incomingParts DOES modify incoming.getSiblings(), so all changes are taken care of!
			// if this breaks, uncomment the following line:
			//return TextUtils.newText(incoming.getContent(), incomingParts, incoming.getStyle());
		}

		return incoming.copy(); // fixes IntelliJ flagging the return value as always being equal to incoming (not true!)
	}

	/** Represents the metadata of a chat message. */
	public record MessageData(GameProfile sender, Date timestamp, boolean vanilla) {}
}