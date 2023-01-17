package mechanicalarcane.wmch.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.RemoveMessageS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(value = ClientPlayNetworkHandler.class, priority = 400)
public abstract class CPNHMixin {
    /**
     * Prevents messages from being hidden.
     * Extremely unclear implementation on Mojang's part,
     * but based on how chat reports work, this is likely
     * not wanted.
     */
    @Inject(method = "onRemoveMessage", at = @At("HEAD"), cancellable = true)
    private void wmch$cancelHideMessage(RemoveMessageS2CPacket packet, CallbackInfo ci) {
        ci.cancel();
    }
}