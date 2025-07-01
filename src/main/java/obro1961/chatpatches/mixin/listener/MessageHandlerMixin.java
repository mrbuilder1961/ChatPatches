package obro1961.chatpatches.mixin.listener;

import com.mojang.authlib.GameProfile;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.message.MessageHandler;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.text.Text;
import net.minecraft.text.TextVisitFactory;
import net.minecraft.util.Util;
import obro1961.chatpatches.mixin.gui.ChatHudMixin;
import obro1961.chatpatches.util.ChatUtils;
import org.apache.commons.lang3.StringUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Date;
import java.util.UUID;

/**
 * Allows caching metadata of the most recent
 * message received by the client. This is used in
 * {@link ChatHudMixin#modifyMessage(Text)}
 * to provide more accurate timestamp data,
 * correct player data, and control how the
 * message should be modified.
 */
@Environment(EnvType.CLIENT)
@Mixin(MessageHandler.class)
public abstract class MessageHandlerMixin {
	@Shadow protected abstract UUID extractSender(Text text);

    /**
     * Caches the metadata of the last <i>player</i> message received by the client.
     * Only applies to vanilla chat messages, see {@link #cacheGameData} for
     * other potentially player messages that have been modified by the server.
     */
    @Inject(method = "onChatMessage", at = @At("HEAD"))
    private void cacheChatData(SignedMessage message, GameProfile sender, MessageType.Parameters params, CallbackInfo ci) {
        // only logs the metadata if it was a player-sent message (otherwise tries to format some commands like /msg and /me)
        ChatUtils.messageData = params.type().value().chat().translationKey().matches(ChatUtils.PARSEABLE_MESSAGE_KEYS)
            ? new ChatUtils.MessageData(sender, Date.from(message.getTimestamp()), isVanilla(params.applyChatDecoration(message.getContent())))
            : ChatUtils.NIL_MESSAGE_DATA;
    }

    /**
     * Does the same thing as {@link #cacheChatData} if
     * the message contains a valid playername.
     */
    @Inject(method = "onGameMessage", at = @At("HEAD"))
    private void cacheGameData(Text message, boolean overlay, CallbackInfo ci) {
        String name = StringUtils.substringBetween(TextVisitFactory.removeFormattingCodes(message), "<", ">");
        UUID id = extractSender(message);

        ChatUtils.messageData = !id.equals(Util.NIL_UUID)
            ? new ChatUtils.MessageData(new GameProfile(id, name), new Date(), isVanilla(message))
            : ChatUtils.NIL_MESSAGE_DATA;
    }


    /**
     * Returns true if the given Text is a vanilla message,
     * as specified by {@link ChatUtils#VANILLA_FORMAT}.
     * This should be true for every message sent by a player,
     * which are the only messages that need to be heavily
     * modified in {@link ChatUtils#modifyMessage(Text)}.
     *
     * @apiNote When called in the chat message handler, the
     * message passed should be
     * {@code params.applyChatDecoration(message.getContent())}
     * to properly include the playername.
     */
    @Unique
    private boolean isVanilla(Text message) {
        return message.getString().matches(ChatUtils.VANILLA_FORMAT);
    }
}