package obro1961.chatpatches.util;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.util.ChatMessages;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import obro1961.chatpatches.accessor.ChatHudAccessor;
import obro1961.chatpatches.mixin.gui.ChatHudMixin;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import static obro1961.chatpatches.ChatPatches.config;

/**
 * Utility methods relating directly to the chat.
 */
public class ChatUtils {
	public static final UUID NIL_UUID = new UUID(0, 0);
	public static final MessageData NIL_MSG_DATA = new MessageData(new GameProfile(ChatUtils.NIL_UUID, ""), Instant.EPOCH, false);
	public static final int TIMESTAMP_INDEX = 0, OG_MSG_INDEX = 1, DUPE_COUNTER_INDEX = 2; // indices of all main (modified message) components
	public static final int MSG_NAME_INDEX = 0, MSG_MSG_INDEX = 1, MSG_FORMATTED_TEXT_INDEX = 2; // indices of all OG_MSG_INDEX components
	/**
	 * Matches a vanilla message, with captures for the playername and message.
	 * Considers a message invalid if {@link net.minecraft.SharedConstants#isValidChar(char)}
	 * would return false.
	 */
	public static final Pattern VANILLA_MESSAGE = Pattern.compile("^<(?<name>[a-zA-Z0-9_]{3,16})> (?<message>[^\\u0000-\\u001f\\u007f§]+)$");

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
	 *     <li>IF the actual message content of the incoming message and the message being compared are equal, continue.</li>
	 *     <li>Cache the number of duped messages, either from the message being compared or from inference plus (this) one.</li>
	 *     <li>Add the dupe counter to the incoming message.</li>
	 *     <li>Remove the message being compared.</li>
	 *     <li>Calculate and then remove all visible messages from the last message, compared as {@link String}s from {@link StringTextUtils#reorder(OrderedText, boolean)}.</li>
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


		// IF the last and incoming message bodies are equal, continue
		if( incomingParts.get(OG_MSG_INDEX).getString().equalsIgnoreCase(comparingParts.get(OG_MSG_INDEX).getString()) ) {

			// info/warning: according to some limited testing, incoming messages (incomingParts) will never contain a dupe counter, so it's been omitted from this check
			int dupes = (
				(comparingParts.size() > DUPE_COUNTER_INDEX)
					? Integer.parseInt(StringTextUtils.delAll(comparingParts.get(DUPE_COUNTER_INDEX).getString(), "(§[0-9a-fk-or])+", "\\D"))
					: 1
			) + 1;


			// i think when old messages are re-added into the chat, it keeps the dupe counter so we have to use set() instead of add() sometimes
			if(incomingParts.size() > DUPE_COUNTER_INDEX)
				incomingParts.set(DUPE_COUNTER_INDEX, config.makeDupeCounter(dupes));
			else
				incomingParts.add(DUPE_COUNTER_INDEX, config.makeDupeCounter(dupes));

			messages.remove(index);

			List<String> calcVisibles = ChatMessages.breakRenderedChatMessageLines(comparingLine.content(), MathHelper.floor(chatHud.getWidth() / chatHud.getChatScale()), client.textRenderer)
				.stream()
				.map( visible -> StringTextUtils.reorder(visible, false) )
				.toList();
			if(config.counterCompact) {
				visibleMessages.removeIf(hudLine -> calcVisibles.stream().anyMatch(ot -> ot.equalsIgnoreCase( StringTextUtils.reorder(hudLine.content(), false) )));
			} else {
				visibleMessages.remove(0);
				while( !visibleMessages.isEmpty() && !visibleMessages.get(0).endOfEntry() )
					visibleMessages.remove(0);
			}

			// same as {@code incoming} but with the appropriate transformations
			return incomingParts.stream().map(Text::copy).reduce(MutableText.of( incoming.getContent() ), MutableText::append).setStyle( incoming.getStyle() );
		}

		return incoming;
	}


	/** Represents the metadata of a chat message. */
	public record MessageData(GameProfile sender, Instant timestamp, boolean vanilla) {}
}