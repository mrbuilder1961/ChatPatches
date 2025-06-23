package obro1961.chatpatches.util;

import com.google.common.collect.Lists;
import com.mojang.authlib.GameProfile;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;
import obro1961.chatpatches.ChatLog;
import obro1961.chatpatches.ChatPatches;
import obro1961.chatpatches.accessor.ChatHudAccess;
import obro1961.chatpatches.config.Config;
import obro1961.chatpatches.mixin.gui.ChatHudMixin;
import obro1961.chatpatches.mixin.listener.MessageHandlerMixin;
import org.apache.logging.log4j.core.util.Integers;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static obro1961.chatpatches.ChatPatches.config;
import static obro1961.chatpatches.util.TextUtils.withoutContent;

/**
 * Utility methods relating directly to the chat.
 */
public class ChatUtils {
	public static final UUID NIL_UUID = new UUID(0, 0);
	public static final MessageData NIL_MESSAGE_DATA = new MessageData(new GameProfile(ChatUtils.NIL_UUID, ""), Date.from(Instant.EPOCH), false);

	public static final int TIMESTAMP_INDEX = 0,   // contains the timestamp (can be empty)
							MESSAGE_INDEX = 1,     // contains the actual chat message
							DUPE_INDEX = 2;        // contains the duplicate counter (can be empty)
	public static final int MSG_TEAM_INDEX = 0,    // contains the sender's team's name; used for `chat.type.team.*` messages (can be empty)
							MSG_SENDER_INDEX = 1,  // contains the sender's name
							MSG_CONTENT_INDEX = 2; // contains the content of the sender's message

	public static final int MAX_MESSAGE_LENGTH = 256; // pulled from chatField's max length

	/**
	 * Contains the sender and timestamp data of the last received chat message.
	 *
	 * @see #modifyMessage(Text)
	 * @see MessageHandlerMixin
	 */
	public static MessageData messageData = NIL_MESSAGE_DATA;

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
	 * @return The message component at the given index; otherwise
	 * {@link ScreenTexts#EMPTY} <u>(not the same as {@link Text#empty()})</u>
	 * if it doesn't exist. This prevents {@code IndexOutOfBoundsException}
	 * and {@code NullPointerException} errors.
	 *
	 * @apiNote Intended to be used with the MAIN
	 * indices specified in this class.
	 * @see #TIMESTAMP_INDEX
	 * @see #MESSAGE_INDEX
	 * @see #DUPE_INDEX
	 */
	public static Text getPart(Text message, int index) {
		return message.getSiblings().size() > index ? message.getSiblings().get(index) : ScreenTexts.EMPTY;
	}

	/**
	 * Returns the message component at the given index of the
	 * given message; returns an empty Text if it doesn't exist.
	 *
	 * @apiNote Intended to be used with the {@code MSG}
	 * indices specified in this class.
	 * @see #MSG_TEAM_INDEX
	 * @see #MSG_SENDER_INDEX
	 * @see #MSG_CONTENT_INDEX
	 */
	public static Text getMsgPart(Text message, int index) {
		return getPart(getPart(message, MESSAGE_INDEX), index);
	}

	/**
	 * Returns a {@link MutableText} representing the argument
	 * located at the given index of the given
	 * {@link TranslatableTextContent}. Needed because of a
	 * weird phenomenon where the
	 * {@linkplain TranslatableTextContent#getArg(int) original
	 * <code>getArg</code> method} can return a non-Text object, which
	 * typically causes a {@link ClassCastException} to be thrown.
	 *
	 * @return Regular {@link Text} objects as expected,
	 * {@link String} arguments as {@linkplain Text#literal(String)
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
	 * This method is used in the
	 * {@link ChatHudMixin#modifyMessage(Text, boolean)} mixin.
	 *
	 * @implNote
	 * <ol>
	 *   <li>Return {@code m} early if the chat log is suspended to not cause
	 *   other issues.</li>
	 * 	 <li>Reconstruct the message if {@linkplain Config#name allowed},
	 * 	 it has player message data, and is {@linkplain #VANILLA_FORMAT in
	 * 	 the vanilla format}:
	 *     	 <ol>
	 *     	     <li>If the message is {@linkplain TranslatableTextContent
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
	 *     	     	  root {@link TextContent}</li>
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
	 * 	 <li>Assemble the message, despite any/all changes and add a duplicate counter
	 * 	 according to {@link #tryCondenseDupes(Text)}.</li>
	 * 	 <li>Log the modified message in the {@link ChatLog}.</li>
	 * 	 <li>Reset the {@link ChatUtils#messageData} to prevent a rare bug.</li>
	 * 	 <li>Return the message, regardless of if it was actually modified or not.</li>
	 * </ol>
	 */
	public static Text modifyMessage(@NotNull Text m) {
		if(ChatLog.isRestoring())
			return m; // cancel modifications when loading the chat log

		boolean lastEmpty = messageData.equals(ChatUtils.NIL_MESSAGE_DATA); // also signifies that this is a system message
		boolean timestampSystemCheck = config.timeSystemMessages || !lastEmpty; // config.timeSystemMessages ? true : !lastEmpty;
		Date now = lastEmpty ? new Date() : messageData.timestamp;
		Style style = m.getStyle();

		MutableText timestamp = null;
		MutableText content = m.copy(); // default to the original message

		try {
			timestamp = (config.time && timestampSystemCheck ? config.makeTimestamp(now) : Text.empty()).setStyle(config.makeHoverStyle(now));

			// reconstruct the player message if it's in the vanilla format & it should be reformatted
			// the messageData vanilla means the original message was vanilla-formatted, and the regex check means it still is.
			// see Xaero's Minimap waypoint sharing for more information (#158)
			if(config.name && !lastEmpty && messageData.vanilla && m.getString().matches(VANILLA_FORMAT)) {
				content = Text.empty().setStyle(style);

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
					content.append( config.formatPlayername(messageData.sender) );
					content.append( getArg(ttc, team ? MSG_CONTENT_INDEX : MESSAGE_INDEX) );
				} else { // reconstructs the message if it matches the vanilla format '<%s> %s' but isn't translatable
					MutableText realContent = Text.empty();
					// collect all message parts into one list, including the root TextContent
					List<Text> parts = Lists.asList( m.copyContentOnly().setStyle(style), m.getSiblings().toArray(new Text[0]) );

					// find the first index of a '>' in the '<%s> %s'-formatted message
					Text firstPart = parts.stream()
						.filter(p -> p.getString().contains(">"))
						.findFirst()
						.orElseGet(() -> {
							String error = "No closing angle bracket found in vanilla message '" + m.getString() + "'!";
							ChatPatches.logReportMsg(new IllegalStateException(error));
							return Text.literal("ERROR: " + error).formatted(Formatting.RED);
						});

					String[] split = firstPart.getString().split(">"); // fixes (#156)
					String afterEndBracket = split.length > 1 ? split[1] : ""; // only get the part after the closing bracket

					// ignore everything before the '>' because it's the playername, which we already know
					// adds the part after the closing bracket but before any remaining siblings, if it exists
					if(!afterEndBracket.isEmpty())
						realContent.append( Text.literal(afterEndBracket).setStyle(firstPart.getStyle()) );

					// we know everything remaining is message content parts, so add everything
					for(int i = parts.indexOf(firstPart) + 1; i < parts.size(); i++)
						realContent.append(parts.get(i));

					content.append(config.formatPlayername(messageData.sender)); // sender data is already known
					content.append(realContent); // adds the reconstructed message content
				}
			}
		} catch(RuntimeException e) {
			ChatPatches.LOGGER.error("[ChatUtils.modifyMessage] An error occurred while modifying message '{}':", m.getString());
			ChatPatches.LOGGER.error("[ChatUtils.modifyMessage] \tModified message structure:");
			ChatPatches.LOGGER.error("[ChatUtils.modifyMessage] \t\tTimestamp structure: {}", timestamp);
			ChatPatches.LOGGER.error("[ChatUtils.modifyMessage] \t\tContent structure: {}", content);
			ChatPatches.logReportMsg(e);
		}

		// assembles constructed message and adds a duplicate counter according to the #addCounter method
		Text modified = tryCondenseDupes( buildMessage(style, timestamp, content, null) );
		ChatLog.addMessage(modified);
		messageData = ChatUtils.NIL_MESSAGE_DATA; // fixes messages that get around MessageHandlerMixin's data caching, usually thru ChatHud#addMessage (ex. open-to-lan message)
		return modified;
	}

	/**
	 * Updated, more efficient version of the original
	 * {@code addCounter} and {@code getCondensedMessage}
	 * method combo. This method is used in conjunction
	 * with (after) {@link #modifyMessage(Text)} to
	 * add a duplicate counter and remove duplicate(s)
	 * to the given message, if they exist and according
	 * to the config.
	 *
	 * @implNote
	 * <ol>
	 *     <li>Return the message if the dupe counter is
	 *     disabled or there are no messages to check.</li>
	 *     <li>Calculate the attempt distance for condensing
	 *     the incoming message with:</li>
	 *     <ol>
	 *         <li>If {@linkplain Config#compactChat
	 *         CompactChat} is enabled, parses
	 *         {@link Config#compactDistance} from
	 *         {@code -1} to {@code messages.size()},
	 *         {@code 0} to
	 *         {@code chatHud.getVisibleLineCount()},
	 *         otherwise to itself.</li>
	 *         <li>Else 1, which is the default and
	 *         corresponds to the most recent message
	 *         only.</li>
	 *     </ol>
	 *     <li>Iterate through the last {@code attemptDistance}
	 * 	   messages to find and condense (remove) any duplicates.</li>
	 *     <ol>
	 *         <li>If the incoming message is different from the
	 *         iterated message, in terms of text or style data
	 *         ({@linkplain Config#counterCheckStyle if we care about
	 *         style data}) don't try to condense (delete) it.</li>
	 *         <li>Remove all number formatting codes and non-digits
	 *         from the iterated message's dupe counter, parse it with
	 *         a fallback of 1 (no counter means 1 message), and add
	 *         it to the total dupe count.</li>
	 *         <li>Remove the message being condensed.</li>
	 *         <li>Remove visible message(s), starting at the iterated
	 *         index, until the next message (EoE) is reached.</li>
	 *         <li>Decrement the index to prevent skipping the next
	 *         message.</li>
	 * 	       <li>Decrement the attempt distance to prevent checking
	 * 	       extra messages.</li>
	 *     </ol>
	 *     <li>Update the incoming message with the new dupe counter,
	 *     if the total dupe count is greater than 1.</li>
	 *     <li>Return the incoming message, regardless of if it was
	 *     actually modified or not, reconstructed to avoid an
	 *     {@link ArrayIndexOutOfBoundsException} in 1.20.3+
	 *     (<a href="https://github.com/mrbuilder1961/ChatPatches/issues/199">#199</a>)</li>
	 * </ol>
	 */
	private static Text tryCondenseDupes(Text incoming) {
		ChatHud chathud = MinecraftClient.getInstance().inGameHud.getChatHud();
		ChatHudAccess chat = (ChatHudAccess) chathud;
		List<ChatHudLine> messages = chat.chatpatches$getMessages();

		if(!config.counter || messages.isEmpty())
			return incoming;

		ObjectList<Text> siblings = new ObjectArrayList<>( incoming.getSiblings() ); // prevents UOEs on 1.20.3+ (#199)
		List<ChatHudLine.Visible> visibles = chat.chatpatches$getVisibleMessages();
		int attemptDistance =
			switch(config.compactChat ? config.compactDistance : 1) {
				case -1 -> messages.size();
				case 0 -> chathud.getVisibleLineCount();
				case 1 -> 1; // only check more messages if compact chat is enabled
				default -> Math.min(config.compactDistance, messages.size()); // max checked = # of messages in chat, else config option
			};

		// iterate through the last `attemptDistance` messages to find and condense (remove) any duplicates
		int dupeCount = 1;
		for(int i = 0; i < attemptDistance && i < messages.size(); i++) {
			Text msg = messages.get(i).content();

			if( !getPart(incoming, MESSAGE_INDEX).getString().equalsIgnoreCase(getPart(msg, MESSAGE_INDEX).getString()) )
				continue; // if the incoming message is different from the iterated message, don't try to condense (delete) it
			else if( config.counterCheckStyle && !withoutContent(incoming).equals(withoutContent(msg)) )
				continue; // if the incoming message has different metadata from the iterated message, skip it

			// remove all number formatting codes and non-digits, then replace empty strings with 1 to prevent NumberFormatExceptions
			// finally add it to the total dupe count
			dupeCount += Integers.parseInt( getPart(msg, DUPE_INDEX).getString().replaceAll("(§\\d)|\\D", "") , 1);

			// remove the message being condensed
			messages.remove(i);

			// remove the visible message(s) of the message being condensed
			do visibles.remove(i);
			while(i < visibles.size() && !visibles.get(i).endOfEntry()); // continue removing them until the next message (EoE) is reached

			i--;  // we removed the first message, but we don't want to skip the next one
			attemptDistance--; // but we also don't want to check messages we shouldn't be checking
		}

		// update the incoming message with the new dupe counter
		if(dupeCount > 1)
			siblings.set(DUPE_INDEX, config.makeDupeCounter(dupeCount)); // this will throw errors if DUPE_INDEX doesn't exist!

		return TextUtils.newText(incoming.getContent(), siblings, incoming.getStyle());
	}


	/** Represents the metadata of a chat message. */
	public record MessageData(GameProfile sender, Date timestamp, boolean vanilla) {}
}