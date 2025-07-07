package obro1961.chatpatches.mixin.security;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundDeleteChatPacket;
import obro1961.chatpatches.config.Config;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static obro1961.chatpatches.ChatPatches.config;

@Environment(EnvType.CLIENT)
@Mixin(ClientPacketListener.class)
public abstract class CPNHMixin {
    /**
     * Prevents messages from being deleted.
     * Extremely unclear implementation on Mojang's part,
     * but based on how chat reports work, this is likely
     * unwanted. Configurable using
     * {@link Config#chatHidePacket}.
     *
     * @implNote Could take the {@link ClientboundDeleteChatPacket}
     * as a parameter, however this is omitted to save
     * one headache for the 1.19.3 change from
     * {@code HideMessageS2CPacket}.
     */
    @Inject(method = "handleDeleteChat", at = @At("HEAD"), cancellable = true)
    private void keepMessage(CallbackInfo ci) {
        if(config.chatHidePacket)
            ci.cancel();
    }
}