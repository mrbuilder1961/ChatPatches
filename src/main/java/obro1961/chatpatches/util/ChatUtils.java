package obro1961.chatpatches.util;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.util.ChatMessages;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import obro1961.chatpatches.accessor.ChatHudAccessor;
import obro1961.chatpatches.mixin.gui.ChatHudMixin;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static obro1961.chatpatches.ChatPatches.config;
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
	 * todo doc AFTER todo moving impl here
	 */
	public static Text modifyMessage(Text message, boolean vanilla) {
		// early if-return checks

		// assign variables

		// declare message parts
		// if TranslatableTextContent and known keys, store pre-formatted parts instantly
		// else do typical formatting stuff (except optimize it to make it actually work and not ugly)

		// final cleanup and logging
		return message;
	}

	/**
	 * Tries to condense the {@code index} message into the incoming message
	 * if they're case-insensitively equal. This method is functionally
	 * similar to the original
	 * {@link ChatHudMixin#addCounter(Text, boolean)}
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
		final ChatHudAccessor chat = ChatHudAccessor.from(chatHud);
		final List<ChatHudLine> messages = chat.chatpatches$getMessages();
		final List<ChatHudLine.Visible> visibleMessages = chat.chatpatches$getVisibleMessages();

		ChatHudLine comparingLine = messages.get(index); // message being compared
		List<Text> comparingParts = comparingLine.content().getSiblings();
		List<Text> incomingParts = incoming.getSiblings();


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