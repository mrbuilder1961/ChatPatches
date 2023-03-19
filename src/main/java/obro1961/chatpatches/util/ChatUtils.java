package obro1961.chatpatches.util;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import obro1961.chatpatches.mixinesq.ChatHudAccessor;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.UUID;

/**
 * Utility methods relating to the chat.
 */
public class ChatUtils {
	public static final UUID NIL_UUID = new UUID(0, 0);
	public static final MessageData NIL_MESSAGE = new MessageData(Text.empty(), new GameProfile(ChatUtils.NIL_UUID, ""), Instant.EPOCH);


	/** A shorthand method for accessing methods from {@code client}'s {@code net.minecraft.client.gui.hud.ChatHud} object. */
	public static ChatHudAccessor chatHud(@NotNull MinecraftClient client) {
		return ((ChatHudAccessor) client.inGameHud.getChatHud());
	}


	/** Represents the metadata of a chat message. */
	public record MessageData(Text message, GameProfile sender, Instant timestamp) {}
}
