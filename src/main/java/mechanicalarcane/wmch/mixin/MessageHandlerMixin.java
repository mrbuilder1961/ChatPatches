package mechanicalarcane.wmch.mixin;

import mechanicalarcane.wmch.WMCH;
import mechanicalarcane.wmch.util.Util;
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

import java.util.UUID;
import java.util.regex.Pattern;

@Environment(EnvType.CLIENT)
@Mixin(value = MessageHandler.class, priority = 400)
public abstract class MessageHandlerMixin {
    @Shadow @Final private MinecraftClient client;

    /**
     * Caches the UUID of the player who sent the last message for name modification.
     * This only works for vanilla chat messages, otherwise checks {@link #cacheGameSender}
     * for the NoChatReports {@code convertToGameMessage} option and then finally caches the metadata.
     */
    @Inject(method = "onChatMessage", at = @At("HEAD"))
    private void cacheChatSender(final SignedMessage message, final MessageType.Parameters params, CallbackInfo ci) {
        client.options.getOnlyShowSecureChat().setValue(false);

        WMCH.lastMsgData = message.createMetadata();
    }

    /**
     * Checks system messages for playernames to customize
     * IF it's in the pattern of a vanilla chat message
     * AND IF the playername in the message refers to a
     * real player in-game.
     * returns {@code true}.
     */
    @Inject(method = "onGameMessage", at = @At("HEAD"))
    private void cacheGameSender(Text message, boolean overlay, CallbackInfo ci) {

        if( Pattern.matches("^<[a-zA-Z0-9_]{3,16}> .+$", message.getString()) ) {

            String name = StringUtils.substringBetween( Util.strip( message.getString() ), "<", ">" );
            UUID uuid = client.getSocialInteractionsManager().getUuid(name);

            WMCH.lastMsgData =
                ( name == null || name.equals("") || uuid.equals(Util.NIL_UUID) )
                    ? Util.NIL_METADATA
                    : new MessageMetadata( uuid, java.time.Instant.now(), 0 )
            ;
        } else {
            WMCH.lastMsgData = Util.NIL_METADATA;
        }
    }
}