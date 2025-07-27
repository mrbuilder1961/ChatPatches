package obro1961.chatpatches.util;

import com.google.common.collect.Lists;
import com.mojang.authlib.GameProfile;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.Util;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.*;
import net.minecraft.network.chat.contents.TranslatableContents;
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
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static net.minecraft.network.chat.CommonComponents.EMPTY;
import static obro1961.chatpatches.ChatPatches.*;
import static obro1961.chatpatches.util.TextUtils.withoutContent;

public class ChatUtils {
	public static final GuiMessage NIL_HUD_LINE = new GuiMessage(0, EMPTY, null, null);
	public static final MessageData NIL_MESSAGE_DATA = new MessageData(new GameProfile(Util.NIL_UUID, ""), Date.from(Instant.EPOCH), false);

	public static final int TIMESTAMP_INDEX = 0,   // contains the timestamp (can be empty)
							MESSAGE_INDEX = 1,     // contains the actual chat message
							DUPE_INDEX = 2;        // contains the duplicate counter (can be empty)
	public static final int MSG_TEAM_INDEX = 0,    // contains the sender's team's name; used for `chat.type.team.*` messages (can be empty)
							MSG_SENDER_INDEX = 1,  // contains the sender's name
							MSG_CONTENT_INDEX = 2; // contains the content of the sender's message

	public static final int MAX_MESSAGE_LENGTH = 256; // pulled from input's max length

	/**
	 * Contains the sender and timestamp data of the last received chat message.
	 *
	 * @see #modifyMessage(Component)
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
	public static final Pattern VANILLA_FORMAT = Pattern.compile("(?i)^((-> )?\\[.+] )?<.{3,}>\\s.+$");
	public static final Pattern PARSEABLE_MESSAGE_KEYS = Pattern.compile("chat.type.(text|team.(text|sent))");


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
	 */
	public static int message2Visible(int messageIndex) {
		var visibles = ((ChatHudAccess) Minecraft.getInstance().gui.getChat()).chatpatches$getVisibleMessages();

		if(messageIndex == -1)
			return -1; // avoids iterating through the entire list

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
	 * @see ChatHudMixin#getChatHudLineIndex(double, double)
	 */
	public static int visible2Message(int visibleIndex) {
		var visibles = ((ChatHudAccess) Minecraft.getInstance().gui.getChat()).chatpatches$getVisibleMessages();

		if(visibleIndex == -1 || visibleIndex >= visibles.size())
			return -1;

		return (int)(visibleIndex - visibles.subList(0, visibleIndex).stream().filter(Predicate.not(GuiMessage.Line::endOfEntry)).count());
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
		return TextUtils.asText( content.getArgs()[index] );
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

		if(rootStyle != null)
			root.setStyle(rootStyle);

		first = Objects.requireNonNullElse(first, Component.empty());
		second = Objects.requireNonNullElse(second, Component.empty());
		third = Objects.requireNonNullElse(third, Component.empty());

		return root.append(first).append(second).append(third);
	}

	// prepub keep or nah...
	private static String optimizeEmpties(Object o) {
		return (o instanceof String str ? str : String.valueOf(o)).replace("literal{}", "empty").replace("[style={}]", "");
	}

	/**
	 * Reformats the incoming message {@code m} according to configured settings,
	 * message data, {@link #tryCondenseDupes(Component)}, and more.
	 *
	 * @see ChatHudMixin#modifyMessage(Component)
	 *
	 * @implNote
	 * <ol>
	 *   <li>Return {@code m} early if the chat log is suspended to not cause
	 *   other issues. The only modification provided in this case is done by
	 *   {@link #tryCondenseDupes(Component)}</li>
	 * 	 <li>Reconstruct the message if {@linkplain Config#name it's wanted},
	 * 	 it has player message data, and is {@linkplain #VANILLA_FORMAT in
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
	 * 	 <li>Reset {@link ChatUtils#messageData} to prevent a rare bug.</li>
	 * 	 <li>Return the built message.</li>
	 * </ol>
	 */
	public static Component modifyMessage(@NotNull Component m) {
		if(ChatLog.isRestoring())
			return tryCondenseDupes(m); // cancel modifications when loading the chat log minus the minimal dupe counter

		boolean lastEmpty = messageData.equals(ChatUtils.NIL_MESSAGE_DATA); // also signifies that this is a system message (when true)
		Date now = lastEmpty ? new Date() : messageData.timestamp;
		Style style = m.getStyle();

		MutableComponent timestamp = null;
		MutableComponent content = m.copy(); // default to the original message
		// dupe counter always empty at this stage

		try {
			timestamp = config.makeTimestamp(now, lastEmpty);

			// reconstruct the player message if it's in the vanilla format & it should be reformatted
			// the messageData vanilla means the original message was vanilla-formatted, and the regex check means it still is.
			// see Xaero's Minimap waypoint sharing for more information (#158)
			if(config.name && !lastEmpty && messageData.vanilla && VANILLA_FORMAT.matcher(m.getString()).matches()) {
				content = Component.empty().setStyle(style);

				// if the message is translatable, then we know exactly where everything is
				if(m.getContents() instanceof TranslatableContents ttc && PARSEABLE_MESSAGE_KEYS.matcher(ttc.getKey()).matches()) {
					boolean team = ttc.getKey().contains("team");

					// adds the team name for team messages
					MutableComponent teamPart = Component.empty();
					if(team) {
						// adds the preceding arrow for sent team messages
						if(ttc.getKey().endsWith("sent"))
							teamPart.append(Component.literal("-> ").setStyle(style)); // "-> {team} <{player}> {content}"

						// adds the team name for team messages
						teamPart.append( getArg(ttc, MSG_TEAM_INDEX).copy().append(" ") ); // copy to prevent UOEs on 1.20.3+ (#199)
					}
					content.append(teamPart); // adds the team part or nothing to keep MSG_TEAM_INDEX constant

					// adds the formatted playername and content for all message types
					content.append( config.formatPlayername(messageData.sender) );
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
					if(!afterEndBracket.isEmpty())
						realContent.append( Component.literal(afterEndBracket).setStyle(firstPart.getStyle()) );

					// we know everything remaining is message content parts, so add everything
					for(int i = parts.indexOf(firstPart) + 1; i < parts.size(); i++)
						realContent.append(parts.get(i));

					content.append(EMPTY); // keeps MSG_TEAM_INDEX constant
					content.append(config.formatPlayername(messageData.sender)); // sender data is already known
					content.append(realContent); // adds the reconstructed message content
				}
			}

			if(config.logMessageStructures) {
				throw new AssertionError("time to log those message structures!", null);
			}
		} catch(RuntimeException | AssertionError e) {
			LOGGER.error("[ChatUtils.modifyMessage] An error occurred while modifying '{}'", m.getString());
			LOGGER.error("[ChatUtils.modifyMessage] \tTimestamp: {}", optimizeEmpties(timestamp));
			LOGGER.error("[ChatUtils.modifyMessage] \tBody:");

			if(content.getSiblings().size() == 3 && !content.equals(m)) { // modified vanilla message
				LOGGER.error("[ChatUtils.modifyMessage] \t\tTeam: {}", optimizeEmpties(getPart(content, MSG_TEAM_INDEX)));
				LOGGER.error("[ChatUtils.modifyMessage] \t\tSender: {}", optimizeEmpties(getPart(content, MSG_SENDER_INDEX)));
				LOGGER.error("[ChatUtils.modifyMessage] \t\tContent: {}", optimizeEmpties(getPart(content, MSG_CONTENT_INDEX)));
			} else { // literally everything else
				LOGGER.error("[ChatUtils.modifyMessage] \t\tRoot: {}", optimizeEmpties(content.getContents()));
				for(int i = 0; i < content.getSiblings().size(); i++) {
					LOGGER.error("[ChatUtils.modifyMessage] \t\tSibling {}: {}", i, optimizeEmpties(getPart(content, i)));
				}
			}

			if(e instanceof RuntimeException) {
				logReportMsg(e); // don't log forced errors
				ChatPatches.pushErrorToast("Message modification error", e.getMessage());
			}
		}

		// assembles constructed message and tries to add a dupe counter
		Component modified = tryCondenseDupes( buildMessage(null, timestamp, content, null) ); // style is null bc only the message content should take on the original style
		ChatLog.addMessage(modified);
		messageData = ChatUtils.NIL_MESSAGE_DATA; // fixes messages that get around MessageHandlerMixin's data caching, usually thru ChatHud#addMessage (ex. open-to-lan message)
		return modified;
	}

	/**
	 * Updated, more efficient version of the original {@code addCounter} and {@code
	 * getCondensedMessage} method combo. This method is used in conjunction with
	 * (after) {@link #modifyMessage(Component)} to add a duplicate counter and remove
	 * duplicate(s) to the given message, if they exist and according to the config.
	 *
	 * @implNote
	 * <ol>
	 *     <li>Returns the message if the dupe counter is disabled or there are no
	 *     messages to check.</li>
	 *     <li>Calculates the attempt distance for condensing the incoming message:</li>
	 *     <ol>
	 *         <li>If {@linkplain Config#compactChat CompactChat} is enabled, takes
	 *         {@link Config#compactDistance} and parses {@code -1} as {@code
	 *         messages.size()}, {@code 0} as {@code chatHud.getVisibleLineCount()},
	 *         else its value as it is.</li>
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
		ChatComponent chathud = Minecraft.getInstance().gui.getChat();
		ChatHudAccess chat = (ChatHudAccess) chathud;
		List<GuiMessage> messages = chat.chatpatches$getMessages();

		if(!config.counter || messages.isEmpty())
			return incoming;

		ObjectList<Component> siblings = new ObjectArrayList<>( incoming.getSiblings() ); // prevents UOEs on 1.20.3+ (#199)
		List<GuiMessage.Line> visibles = chat.chatpatches$getVisibleMessages();
		int attemptDistance =
			switch(config.compactChat ? config.compactDistance : 1) {
				case -1 -> messages.size();
				case 0 -> chathud.getLinesPerPage();
				case 1 -> 1; // only check more messages if compact chat is enabled
				default -> Math.min(config.compactDistance, messages.size()); // max checked = # of messages in chat, else config option
			};

		// iterate through the last `attemptDistance` messages to find and condense (remove) any duplicates
		int dupeCount = 1;
		for(int i = 0; i < attemptDistance && i < messages.size(); i++) {
			Component msg = messages.get(i).content();

			if( !getPart(incoming, MESSAGE_INDEX).getString().equalsIgnoreCase(getPart(msg, MESSAGE_INDEX).getString()) )
				continue; // if the incoming message is different from the iterated message, don't try to condense (delete) it
			else if( config.counterCheckStyle && !withoutContent(incoming).equals(withoutContent(msg)) )
				continue; // if the incoming message has different metadata from the iterated message, skip it

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
				LOGGER.warn("[ChatUtils.tryCondenseDupes] Invalid message structure: {}", optimizeEmpties(incoming));
				logReportMsg(new IllegalStateException("DUPE_INDEX is out of bounds for message '" + incoming.getString() + "'"));
				siblings.add(config.makeDupeCounter(dupeCount));
			}
		}

		return TextUtils.newText(incoming.getContents(), siblings, incoming.getStyle());
	}


	/** Represents the metadata of a chat message. */
	public record MessageData(GameProfile sender, Date timestamp, boolean vanilla) {}
}