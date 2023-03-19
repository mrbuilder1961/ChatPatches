package obro1961.chatpatches.mixin.chat;

import com.mojang.authlib.GameProfile;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.client.network.message.MessageHandler;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import obro1961.chatpatches.ChatPatches;
import obro1961.chatpatches.util.ChatUtils;
import org.apache.commons.lang3.StringUtils;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * A mixin used to cache the metadata of the most recent message
 * received by the client. This is used in
 * {@link ChatHudMixin#cps$modifyMessage(Text, Text, MessageSignatureData, int, MessageIndicator, boolean)}
 * to provide more accurate timestamp data, the correct player
 * name, and the player's UUID.
 */
@Environment(EnvType.CLIENT)
@Mixin(MessageHandler.class)
public abstract class MessageHandlerMixin {
    @Shadow @Final private MinecraftClient client;


    /**
     * Caches the metadata of the last message received by the client.
     * Only applies to vanilla chat messages, otherwise checks {@link #cps$cacheGameData}
     * for other potentially player messages that have been modified by the server.
     */
    @Inject(method = "onChatMessage", at = @At("HEAD"))
    private void cps$cacheChatData(SignedMessage message, GameProfile sender, MessageType.Parameters params, CallbackInfo ci) {
        client.options.getOnlyShowSecureChat().setValue(false);

        ChatPatches.lastMsg = new ChatUtils.MessageData(message.getContent(), sender, message.getTimestamp());
    }

    /**
     * Does the same thing as {@link #cps$cacheChatData} if
     * the message is formatted like a vanilla chat message
     * and contains a valid playername.
     */
    @SuppressWarnings("DataFlowIssue") // Formatting.strip is nullable, but IntelliJ doesn't realize the null is only from the parameter
    @Inject(method = "onGameMessage", at = @At("HEAD"))
    private void cps$cacheGameData(Text message, boolean overlay, CallbackInfo ci) {
        String text = Objects.requireNonNullElse(message.getString(), "");

        if( Pattern.matches("^<[a-zA-Z0-9_]{3,16}> .+$", text) ) {

            String name = StringUtils.substringBetween(SharedConstants.stripInvalidChars(Formatting.strip( text )), "<", ">");
            UUID uuid = client.getSocialInteractionsManager().getUuid(name);

            ChatPatches.lastMsg =
                ( name == null || name.equals("") || uuid.equals(ChatUtils.NIL_UUID) )
                    ? ChatUtils.NIL_MESSAGE
                    : new ChatUtils.MessageData( message, new GameProfile(uuid, name), Instant.now() );
        } else {
            ChatPatches.lastMsg = ChatUtils.NIL_MESSAGE;
        }
    }
}