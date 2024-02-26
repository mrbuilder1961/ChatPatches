package obro1961.chatpatches.mixin.chat;

import com.mojang.authlib.GameProfile;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.message.MessageHandler;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.text.Text;
import net.minecraft.text.TextVisitFactory;
import net.minecraft.util.Util;
import obro1961.chatpatches.ChatPatches;
import obro1961.chatpatches.mixin.gui.ChatHudMixin;
import obro1961.chatpatches.util.ChatUtils;
import org.apache.commons.lang3.StringUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Date;
import java.util.UUID;

/**
 * A mixin used to cache the metadata of the most recent message
 * received by the client. This is used in
 * {@link ChatHudMixin#modifyMessage(Text, boolean)}
 * to provide more accurate timestamp data, the correct player
 * name, and the player's UUID.
 */
@Environment(EnvType.CLIENT)
@Mixin(MessageHandler.class)
public abstract class MessageHandlerMixin {
	@Shadow protected abstract UUID extractSender(Text text);

    /**
     * Caches the metadata of the last *player* message received by the client.
     * Only applies to vanilla chat messages, otherwise checks {@link #cacheGameData}
     * for other potentially player messages that have been modified by the server.
     */
    @Inject(method = "onChatMessage", at = @At("HEAD"))
    private void cacheChatData(SignedMessage message, GameProfile sender, MessageType.Parameters params, CallbackInfo ci) {
        // only logs the metadata if it was a player-sent message (otherwise tries to format some commands like /msg and /me)
        if( params.type().chat().translationKey().equals("chat.type.text") )
            ChatPatches.msgData = new ChatUtils.MessageData(sender, Date.from(message.getTimestamp()), true);
        else
            ChatPatches.msgData = ChatUtils.NIL_MSG_DATA;
    }

    /**
     * Does the same thing as {@link #cacheChatData} if
     * the message contains a valid playername.
     */
    @Inject(method = "onGameMessage", at = @At("HEAD"))
    private void cacheGameData(Text message, boolean overlay, CallbackInfo ci) {
        String name = StringUtils.substringBetween(TextVisitFactory.removeFormattingCodes(message), "<", ">");
        UUID id = extractSender(message);

        ChatPatches.msgData = !id.equals(Util.NIL_UUID)
            ? new ChatUtils.MessageData(new GameProfile(id, name), new Date(), true)
            : ChatUtils.NIL_MSG_DATA;
    }
}