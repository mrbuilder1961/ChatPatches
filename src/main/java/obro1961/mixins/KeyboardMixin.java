package obro1961.mixins;

import com.google.common.collect.Lists;
import com.google.common.collect.Queues;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;

@Environment(EnvType.CLIENT)
@Mixin(Keyboard.class)
public class KeyboardMixin {
    @Shadow @Final MinecraftClient client;

    /** Allows F3+D to properly clear chat without breaking the function for other implementations */
    @Inject( method = "processF3(I)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/ChatHud;clear(Z)V") )
    public void properlyClear(int key, CallbackInfoReturnable<Boolean> ci) {
		IChatHudAccessorMixin cha = ((IChatHudAccessorMixin) client.inGameHud.getChatHud());
		cha.setMessageQueue(Queues.newArrayDeque());
        cha.setVisibleMessages(Lists.newArrayList());
        cha.setMessages(Lists.newArrayList());
    }
}
