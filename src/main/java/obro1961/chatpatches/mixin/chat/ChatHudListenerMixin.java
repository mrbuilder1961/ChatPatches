package obro1961.chatpatches.mixin.chat;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHudListener;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.network.MessageType;
import net.minecraft.text.Text;
import obro1961.chatpatches.ChatPatches;
import obro1961.chatpatches.util.Util;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Environment(EnvType.CLIENT)
@Mixin(ChatHudListener.class)
public abstract class ChatHudListenerMixin {
    @Shadow @Final private MinecraftClient client;

    /**
     * Caches the GameProfile representing the most recent sender
     * of a chat message.
     *
     * @apiNote {@code client.getNetworkHandler()} should never be null
     * because {@link MinecraftClient#player} is never null when in game,
     * (chat messages are only accessible in game) and it returns
     * based on {@code player}'s nullability.
     */
    @Inject(method = "onChatMessage", at = @At("HEAD"))
    public void cps$cacheChatData(MessageType type, Text msg, UUID sender, CallbackInfo ci) {
        PlayerListEntry player = client.getNetworkHandler().getPlayerListEntry(sender);

        ChatPatches.lastSender = (player != null && player.getProfile().isComplete() && type == MessageType.CHAT) ? player.getProfile() : Util.NIL_SENDER;
    }
}