package obro1961.chatpatches.mixin.chat;

import obro1961.chatpatches.ChatPatches;
import obro1961.chatpatches.util.Util;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.message.MessageHandler;
import net.minecraft.network.message.MessageMetadata;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.text.Text;
import org.apache.commons.lang3.StringUtils;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

@Environment(EnvType.CLIENT)
@Mixin(MessageHandler.class)
public abstract class MessageHandlerMixin {
    @Shadow @Final private MinecraftClient client;


    /**
     * Caches the UUID of the player who sent the last message for name modification.
     * This only works for vanilla chat messages, otherwise checks {@link #cps$cacheGameData}
     * and then caches the metadata.
     */
    @Inject(method = "onChatMessage", at = @At("HEAD"))
    private void cps$cacheChatData(SignedMessage message, MessageType.Parameters params, CallbackInfo ci) {
        client.options.getOnlyShowSecureChat().setValue(false);

        ChatPatches.lastMsgData = message.createMetadata();
    }

    /**
     * Checks system messages for playernames to customize
     * IF it's in the pattern of a vanilla chat message
     * AND IF the playername in the message refers to a
     * real player in-game.
     */
    @Inject(method = "onGameMessage", at = @At("HEAD"))
    private void cps$cacheGameData(Text message, boolean overlay, CallbackInfo ci) {

        if( Pattern.matches("^<[a-zA-Z0-9_]{3,16}> .+$", message.getString()) ) {

            String name = StringUtils.substringBetween( Util.strip( message.getString() ), "<", ">" );
            UUID uuid = client.getSocialInteractionsManager().getUuid(name);

            ChatPatches.lastMsgData =
                ( name == null || name.equals("") || uuid.equals(Util.NIL_UUID) )
                    ? Util.NIL_METADATA
                    : new MessageMetadata(uuid, Instant.now(), 0)
            ;
        } else {
            ChatPatches.lastMsgData = Util.NIL_METADATA;
        }
    }
}