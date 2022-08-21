package mechanicalarcane.wmch.mixins;

import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.aizistral.nochatreports.handlers.NoReportsConfig;

import mechanicalarcane.wmch.WMCH;
import mechanicalarcane.wmch.WMCH.Relation;
import mechanicalarcane.wmch.util.Util;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHudListener;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.network.message.MessageSender;
import net.minecraft.network.message.MessageType;
import net.minecraft.text.Text;
import net.minecraft.util.registry.Registry;

@Environment(EnvType.CLIENT)
@Mixin(value = ChatHudListener.class, priority = 1)
public class ChatHudListenerMixin {
    @Shadow @Final private MinecraftClient client;

    /**
     * Caches the player who sent the last message for name modification.
     * Ignores team messages, and if NoChatReports is installed it
     * will modify the system message to style the name.
     *
     * <p>Calculates if the message was sent by a real player,
     * and factors in NoChatReport's chat-to-system message feature.
     * Then tries to get the corresponding {@link PlayerListEntry},
     * and if that doesn't fail and all other checks have passed
     * then caches {@code sender}. Otherwise caches {@link Util#NIL_SENDER}
     */
    @Inject(method = "onChatMessage", at = @At("HEAD"))
    public void cacheSender(MessageType type, Text msg, MessageSender sender, CallbackInfo ci) {
        final Registry<MessageType> typeRegistry = client.player.clientWorld.getRegistryManager().get(Registry.MESSAGE_TYPE_KEY);
        final MessageType CHAT = typeRegistry.get(MessageType.CHAT);

        final boolean sentByPlayer = Relation.NOCHATREPORTS.installed()
            ? (
                NoReportsConfig.convertsToGameMessage()
                    ? (
                        type.equals( typeRegistry.get(MessageType.SYSTEM) ) &&
                        !client.inGameHud.extractSender(msg).equals( Util.NIL_UUID )
                    )
                    : type.equals(CHAT)
            )
            : type.equals(CHAT)
        ;

        UUID source = sender != null ? sender.uuid() : client.inGameHud.extractSender(msg);
        PlayerListEntry player = client.getNetworkHandler().getPlayerListEntry(source);

        // IF sender is an player AND message was sent by a real player THEN cache the (reconstructed if necessary) sender
        WMCH.msgSender = (
            ( (player != null ? player.getProfile().isComplete() : false) && sentByPlayer )
                ? new MessageSender(
                    source,
                    sender != null
                        ? sender.name()
                        : Text.of( StringUtils.substringBetween(msg.getString(), "<", ">") )
                )
                : Util.NIL_SENDER
        );
    }
}