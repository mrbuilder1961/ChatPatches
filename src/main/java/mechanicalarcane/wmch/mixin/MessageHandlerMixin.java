package mechanicalarcane.wmch.mixin;

import java.util.UUID;
import java.util.regex.Pattern;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import mechanicalarcane.wmch.WMCH;
import mechanicalarcane.wmch.integration.NCRConfigAccessor;
import mechanicalarcane.wmch.util.Util;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.message.MessageHandler;
import net.minecraft.network.message.MessageMetadata;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.text.Text;

@Environment(EnvType.CLIENT)
@Mixin(value = MessageHandler.class, priority = 1)
public class MessageHandlerMixin {
    @Shadow @Final private MinecraftClient client;

    /**
     * Caches the player who sent the last message for name modification.
     * This only works for vanilla chat messages, otherwise checks
     * {@link #cacheGameSender} for the NoChatReports
     * {@code convertToGameMessage} option and finally caches the metadata.
     *
     * <p>If it fails to locate a player, caches {@link Util#NIL_SENDER}.
     */
    @Inject(method = "onChatMessage", at = @At("HEAD"))
    public void cacheChatSender(final SignedMessage message, final MessageType.Parameters params, CallbackInfo ci) {
        client.options.getOnlyShowSecureChat().setValue(false);

        WMCH.lastMeta = message.createMetadata();
    }

    /**
     * Checks system messages for playernames to customize
     * IF it's in the pattern of a vanilla chat message
     * AND IF {@link NCRConfigAccessor#chatToSys()}
     * returns {@code true}.
     */
    @Inject(method = "onGameMessage", at = @At("HEAD"))
    public void cacheGameSender(Text message, boolean overlay, CallbackInfo ci) {
        if( Pattern.matches("^<[a-zA-Z0-9_]{3,16}> .+$", message.getString()) && NCRConfigAccessor.chatToSys() ) {
            String messagename = org.apache.commons.lang3.StringUtils.substringBetween( net.minecraft.client.font.TextVisitFactory.removeFormattingCodes( message ), "<", ">" );
            UUID uuid = client.getSocialInteractionsManager().getUuid(messagename);

            WMCH.lastMeta =
                ( messagename == null || messagename == "" || uuid.equals(Util.NIL_UUID) )
                    ? Util.NIL_METADATA
                    : new MessageMetadata( uuid, java.time.Instant.now(), 0 )
            ;
        } else {
            WMCH.lastMeta = Util.NIL_METADATA;
        }
    }
}