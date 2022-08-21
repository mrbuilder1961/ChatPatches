package mechanicalarcane.wmch.mixins;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.option.GameOptions;
import net.minecraft.network.message.MessageSender;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;

@Environment(EnvType.CLIENT)
@Mixin(value = ClientPlayNetworkHandler.class, priority = 1)
public class CPNHMixin {
    @Shadow @Final private MinecraftClient client;

    /**
     * Ignores the {@link GameOptions#onlyShowSecureChat} option so customized messages are viewable.
     * Also removes signed message sender verification and the associated log warnings.
     */
    @Inject(method = "handleMessage", at = @At("HEAD"), cancellable = true)
    public void allowUnsecureMessages(MessageType type, SignedMessage message, MessageSender sender, CallbackInfo ci) {
        client.inGameHud.onChatMessage(type, message.getContent(), sender);

        ci.cancel();
    }
}
