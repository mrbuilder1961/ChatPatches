package obro1961.chatpatches.mixin.chat;

import com.mojang.authlib.GameProfile;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.client.network.message.MessageHandler;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.text.Text;
import net.minecraft.text.TextVisitFactory;
import net.minecraft.util.Util;
import obro1961.chatpatches.mixin.gui.ChatHudMixin;
import obro1961.chatpatches.util.ChatUtils;
import obro1961.chatpatches.util.SharedVariables;
import org.apache.commons.lang3.StringUtils;
import org.spongepowered.asm.mixin.Final;
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
 * {@link ChatHudMixin#modifyMessage(Text, Text, MessageSignatureData, int, MessageIndicator, boolean)}
 * to provide more accurate timestamp data, the correct player
 * name, and the player's UUID.
 */
@Environment(EnvType.CLIENT)
@Mixin(MessageHandler.class)
public abstract class MessageHandlerMixin {
    @Shadow @Final private MinecraftClient client;


    /**
     * Caches the metadata of the last *player* message received by the client.
     * Only applies to vanilla chat messages, otherwise checks {@link #cacheGameData}
     * for other potentially player messages that have been modified by the server.
     */
    @Inject(method = "onChatMessage", at = @At("HEAD"))
    private void cacheChatData(SignedMessage message, GameProfile sender, MessageType.Parameters params, CallbackInfo ci) {
        // only logs the metadata if it was a player-sent message (otherwise tries to format some commands like /msg and /me)
        if( params.type().chat().translationKey().equals("chat.type.text") )
            SharedVariables.lastMsg = new ChatUtils.MessageData(sender, Date.from(message.getTimestamp()), true);
        else
            SharedVariables.lastMsg = ChatUtils.NIL_MSG_DATA;
    }

    /**
     * Does the same thing as {@link #cacheChatData} if
     * the message contains a valid playername.
     */
    @Inject(method = "onGameMessage", at = @At("HEAD"))
    private void cacheGameData(Text message, boolean overlay, CallbackInfo ci) {
        String string = TextVisitFactory.removeFormattingCodes(message);
        String name = ChatUtils.VANILLA_MESSAGE.matcher(string).matches() ? StringUtils.substringBetween(string, "<", ">") : null;
        UUID uuid = name == null ? Util.NIL_UUID : client.getSocialInteractionsManager().getUuid(name);

        SharedVariables.lastMsg = !uuid.equals(Util.NIL_UUID)
            ? new ChatUtils.MessageData(new GameProfile(uuid, name), new Date(), true)
            : ChatUtils.NIL_MSG_DATA;
    }
}