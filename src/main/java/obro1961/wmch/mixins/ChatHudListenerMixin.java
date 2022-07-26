package obro1961.wmch.mixins;

import static net.minecraft.util.registry.BuiltinRegistries.MESSAGE_TYPE;

import java.util.Optional;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHudListener;
import net.minecraft.network.message.MessageSender;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.MessageType.DisplayRule;
import net.minecraft.text.Decoration;
import net.minecraft.text.Text;
import obro1961.wmch.WMCH;
import obro1961.wmch.util.Util;

@Environment(EnvType.CLIENT)
@Mixin(ChatHudListener.class)
public class ChatHudListenerMixin {
    @Shadow @Final private MinecraftClient client;

    /**
     * Caches the player who sent the last message for name modification.
     *
     * Process: Resets the last sender, then creates an Optional from the
     * sender's UUID provided. IF it belongs to a real player, then cache it.
     */
    @Inject(method = "onChatMessage", at = @At("HEAD"), cancellable = true)
    public void filterMessages(MessageType type, Text msg, MessageSender sender, CallbackInfo ci) {
        WMCH.msgSender = Util.NIL_SENDER;

        // IF sender is an valid player AND message was sent by a player THEN cache them
        Optional.ofNullable( client.getNetworkHandler().getPlayerListEntry((sender!=null && !sender.equals(Util.NIL_SENDER)) ? sender.uuid() : Util.NIL_UUID) )
            .ifPresent( entry -> {
                if(entry.getProfile().isComplete() && playerMsg(type))
                    WMCH.msgSender = sender;
            });
    }

    /** true if MessageType is a player-sent chat message (excluding team messages) */
    private boolean playerMsg(MessageType type) {
        final MessageType CHAT = MESSAGE_TYPE.get(MessageType.CHAT);
        return (
            type.equals(CHAT)
            || type.equals(new MessageType(Optional.of(DisplayRule.of(Decoration.ofChat("text.wmch.mutableChat"))), CHAT.overlay(), CHAT.narration()))
        );
    }
}