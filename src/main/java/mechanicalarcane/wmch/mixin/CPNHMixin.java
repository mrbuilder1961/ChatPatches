package mechanicalarcane.wmch.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.HideMessageS2CPacket;

@Environment(EnvType.CLIENT)
@Mixin(value = ClientPlayNetworkHandler.class, priority = 400)
public abstract class CPNHMixin {
    /**
     * Prevents messages from being hidden.
     * This may be deleted; but I currently
     * don't understand why a sent message
     * would need to be hidden.
     */
    @Inject(method = "onHideMessage", at = @At("HEAD"), cancellable = true)
    private void cancelHideMessage(HideMessageS2CPacket packet, CallbackInfo ci) {
        ci.cancel();
    }
}
