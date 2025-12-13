package obro1961.chatpatches.mixin.listener;

import com.mojang.authlib.GameProfile;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.Util;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.util.StringDecomposer;
import obro1961.chatpatches.mixin.gui.ChatComponentMixin;
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

import static obro1961.chatpatches.util.ChatUtils.*;

/**
 * A mixin used to cache the metadata of the most recent message
 * received by the client. This is used in
 * {@link ChatComponentMixin#modifyMessage(Component)}
 * to provide more accurate timestamp data, the correct player
 * name, and the player's UUID.
 */
@Environment(EnvType.CLIENT)
@Mixin(ChatListener.class)
public abstract class MessageHandlerMixin {
	@Shadow protected abstract UUID guessChatUUID(Component text);

    /**
     * Caches the metadata of the last <i>player</i> message received by the client.
     * Only applies to vanilla chat messages, otherwise see {@link #cacheGameData}
     * for other potentially player messages that have been modified by the server.
     * Disregards some messages that are by players but are not chat messages, such
     * as commands like {@linkplain net.minecraft.server.commands.MsgCommand msg} and
     * {@linkplain net.minecraft.server.commands.EmoteCommands me}, to avoid
     * formatting them incorrectly.
     */
    @Inject(method = "handlePlayerChatMessage", at = @At("HEAD"))
    private void cacheChatData(PlayerChatMessage message, GameProfile sender, ChatType.Bound params, CallbackInfo ci) {
        ChatUtils.messageData = PARSEABLE_MESSAGE_KEYS.reset(
            /*? if <1.20.5 {*/ /*params.chatType().chat().translationKey() *//*?} else {*/params.chatType().value().chat().translationKey()/*?}*/
        ).matches()
            ? new MessageData(sender, Date.from(message.timeStamp()), isVanilla(params.decorate(message.decoratedContent())))
            : NIL_MESSAGE_DATA;
    }

    /**
     * Does the same thing as {@link #cacheChatData} if
     * the message contains a valid playername.
     */
    @Inject(method = "handleSystemMessage", at = @At("HEAD"))
    private void cacheGameData(Component message, boolean overlay, CallbackInfo ci) {
        String name = StringUtils.substringBetween(StringDecomposer.getPlainText(message), "<", ">");
        UUID id = guessChatUUID(message);

        ChatUtils.messageData = !id.equals(Util.NIL_UUID)
            ? new MessageData(new GameProfile(id, name), new Date(), isVanilla(message))
            : NIL_MESSAGE_DATA;
    }


    /**
     * Returns true if the given Text is a vanilla message,
     * as specified by {@link ChatUtils#VANILLA_FORMAT}.
     * This should be true for every message sent by a player,
     * which are the only messages that need to be heavily
     * modified in {@link ChatUtils#modifyMessage(Component)}}.
     *
     * @apiNote When called in the chat message handler, the
     * message passed should be
     * {@code params.applyChatDecoration(message.getContent())}
     * to properly include the playername.
     */
    @Unique
    private boolean isVanilla(Component message) {
        return VANILLA_FORMAT.reset(message.getString()).matches();
    }
}