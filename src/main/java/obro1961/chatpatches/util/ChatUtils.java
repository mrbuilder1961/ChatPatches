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
import obro1961.chatpatches.mixin.gui.ChatHudMixin;
import org.jetbrains.annotations.NotNull;

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
	public static final int TIMESTAMP_INDEX = 0, MESSAGE_INDEX = 1, DUPE_INDEX = 2; // indices of all main (modified message) components
	public static final int MSG_TEAM_INDEX = 0, MSG_SENDER_INDEX = 1, MSG_CONTENT_INDEX = 2; // indices of all MESSAGE_INDEX components

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
	 * Returns a MutableText object representing the argument
	 * located at the given index of the given
	 * {@link TranslatableTextContent}. Needed because of a weird
	 * phenomenon where the {@link TranslatableTextContent#getArg(int)}
	 * method can return a {@link String} or other non-Text related
	 * object, which otherwise causes {@link ClassCastException}s.
	 * <p>
	 * Wraps {@link String}s in {@link Text#literal(String)}
	 * and nulls in {@link Text#empty()}.
	 *
	 * @implNote
	 * If {@code index} is negative, adds it to the args array
	 * length. In other words, passing index {@code -n} will
	 * get the {@code content.getArgs().length-n}th argument.
	 */
	public static MutableText getArg(TranslatableTextContent content, int index) {
		if(index < 0)
			index = content.getArgs().length + index;

		Object /* StringVisitable */ arg = content.getArg(index);

		if(arg == null)
			return Text.empty();
		else if(arg instanceof Text t)
			return (MutableText) t;
		else if(arg instanceof StringVisitable sv)
			return Text.literal(sv.getString());
		else if(arg instanceof String s)
			return Text.literal(s);
		else
			return Text.empty();
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
	public static MutableText buildMessage(Style rootStyle, Text first, Text second, Text third) {
		MutableText root = Text.empty();

		if(rootStyle != null && !rootStyle.equals(Style.EMPTY))
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
	 * 	 according to the {@link ChatHudMixin#addCounter(Text, boolean)} method.</li>
	 * 	 <li>Log the modified message in the {@code ChatLog}.</li>
	 * 	 <li>Reset the {@link ChatPatches#msgData} to prevent an uncommon bug.</li>
	 * 	 <li>Return the modified message, regardless of if it was</li>
	 * </ol>
	 */
	public static Text modifyMessage(@NotNull Text m, boolean refreshing) {
		if( refreshing || Flags.LOADING_CHATLOG.isRaised() )
			return m; // cancels modifications when loading the chatlog or regenerating visibles

		boolean lastEmpty = msgData.equals(ChatUtils.NIL_MSG_DATA);
		boolean boundary = Flags.BOUNDARY_LINE.isRaised() && config.boundary && !config.vanillaClearing;
		Date now = lastEmpty ? new Date() : msgData.timestamp();
		String nowStr = String.valueOf(now.getTime()); // for copy menu and storing timestamp data! only affects the timestamp
		Style style = m.getStyle();

		MutableText timestamp = null;
		MutableText content = m.copy();

		try {
			timestamp = (config.time && !boundary) ? config.makeTimestamp(now).setStyle( config.makeHoverStyle(now) ) : Text.empty().styled(s -> s.withInsertion(nowStr));
			content = Text.empty().setStyle(style);

			// reconstruct the player message if it's in the vanilla format and it should be reformatted
			if(!lastEmpty && !boundary && msgData.vanilla()) {
				// if the message is translatable, then we know exactly where everything is
				if(m.getContent() instanceof TranslatableTextContent ttc && ttc.getKey().matches("chat.type.(text|team.(text|sent))")) {
					String key = ttc.getKey();

					// adds the team name for team messages
					if(key.startsWith("chat.type.team.")) {
						MutableText teamPart = Text.empty();
						// adds the preceding arrow for sent team messages
						if(key.endsWith("sent"))
							teamPart.append(Text.literal("-> ").setStyle(style));

						// adds the team name for team messages
						teamPart.append(getArg(ttc, 0).append(" "));

						content.append(teamPart);
					} else {
						content.append(""); // if there isn't a team message, add an empty string to keep the index constant
					}

					// adds the formatted playername and content for all message types
					content.append(config.formatPlayername(msgData.sender())); // sender data is already known
					content.append(getArg(ttc, -1)); // always at the end
				} else { // reconstructs the message if it matches the vanilla format '<%s> %s' but isn't translatable
					// collect all message parts into one list, including the root TextContent
					// (assuming this accounts for all parts, TextContents, and siblings)
					List<Text> parts = Util.make(new ArrayList<>(m.getSiblings().size() + 1), a -> {
						if(!m.equals(Text.EMPTY))
							a.add( m.copyContentOnly().setStyle(style) );

						a.addAll( m.getSiblings() );
					});

					MutableText realContent = Text.empty();
					// find the first index of a '>' in the message, is formatted like '<%s> %s'
					Text firstPart = parts.stream().filter(p -> p.getString().contains(">")).findFirst()
						.orElseThrow(() -> new IllegalStateException("No closing angle bracket found in vanilla message '" + m.getString() + "' !"));
					String afterEndBracket = firstPart.getString().split(">")[1]; // just get the part after the closing bracket, we know the start

					// ignore everything before the '>' because it's the playername, which we already know
					// adds the part after the closing bracket but before any remaining siblings, if it exists
					if(!afterEndBracket.isEmpty())
						realContent.append( Text.literal(afterEndBracket).setStyle(firstPart.getStyle()) );

					// we know everything remaining is message content parts, so add everything
					for(int i = parts.indexOf(firstPart) + 1; i < parts.size(); i++)
						realContent.append(parts.get(i));

					content.append(config.formatPlayername(msgData.sender())); // sender data is already known
					content.append(realContent); // adds the reconstructed message content
				}
			} else {
				// don't reformat if it isn't vanilla or needed
				content = m.copy();
			}
		} catch(Throwable e) {
			ChatPatches.LOGGER.error("[ChatUtils.modifyMessage] An error occurred while modifying message '{}', returning original:", m.getString());
			ChatPatches.LOGGER.debug("[ChatUtils.modifyMessage] \tOriginal message structure: {}", m);
			ChatPatches.LOGGER.debug("[ChatUtils.modifyMessage] \tModified message structure:");
			ChatPatches.LOGGER.debug("[ChatUtils.modifyMessage] \t\tTimestamp structure: {}", timestamp);
			ChatPatches.LOGGER.debug("[ChatUtils.modifyMessage] \t\tContent structure: {}", content);
			ChatPatches.logInfoReportMessage(e);
		}

		// assembles constructed message and adds a duplicate counter according to the #addCounter method
		Text modified = ChatUtils.buildMessage(style, timestamp, content, null);
		ChatLog.addMessage(modified);
		msgData = ChatUtils.NIL_MSG_DATA; // fixes messages that get around MessageHandlerMixin's data caching, usually thru ChatHud#addMessage (ex. open-to-lan message)
		return modified;
	}

	/**
	 * Tries to condense the {@code index} message into the incoming message
	 * if they're case-insensitively equal. This method is functionally
	 * similar to the original
	 * {@link ChatHudMixin#addCounter(Text, boolean)}
	 * before {@code v194.5.0}.
	 * <br><br>The main difference is that this method
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
		final ChatHudAccessor chat = ChatHudAccessor.from(chatHud);
		final List<ChatHudLine> messages = chat.chatpatches$getMessages();
		final List<ChatHudLine.Visible> visibleMessages = chat.chatpatches$getVisibleMessages();

		ChatHudLine comparingLine = messages.get(index); // message being compared
		List<Text> comparingParts = comparingLine.content().getSiblings();
		List<Text> incomingParts = new ArrayList<>( incoming.getSiblings() ); // prevents UOEs (1.20.3+ only)


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
				visibleMessages.remove(0);
				while( !visibleMessages.isEmpty() && !visibleMessages.get(0).endOfEntry() )
					visibleMessages.remove(0);
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