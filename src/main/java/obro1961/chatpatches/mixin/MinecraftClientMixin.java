package obro1961.chatpatches.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import obro1961.chatpatches.ChatPatches;
import obro1961.chatpatches.chatlog.ChatLog;
import obro1961.chatpatches.gui.ChatSearchScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * An ugly but necessary mixin for a couple random things.
 */
@Environment(EnvType.CLIENT)
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {

    /** Injects callbacks to game exit events so cached data can still be saved */
    @Inject(method = "run", at = {
        @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/MinecraftClient;addDetailsToCrashReport(Lnet/minecraft/util/crash/CrashReport;)Lnet/minecraft/util/crash/CrashReport;"
        ),
        @At(
            value = "INVOKE_ASSIGN",
            target = "Lnet/minecraft/client/MinecraftClient;addDetailsToCrashReport(Lnet/minecraft/util/crash/CrashReport;)Lnet/minecraft/util/crash/CrashReport;"
        ),
        @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/MinecraftClient;cleanUpAfterCrash()V"
        )
    })
    private void cps$saveChatlogOnCrash(CallbackInfo ci) {
        ChatLog.serialize(true);
    }

    /** Replaces the vanilla chat screen with the search-enabled one */
    @WrapOperation(method = "openChatScreen", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;setScreen(Lnet/minecraft/client/gui/screen/Screen;)V", ordinal = 1))
    private void cps$overrideChatScreen(MinecraftClient instance, Screen vanillaChatScreen, Operation<Void> setScreenMethod) {
        if(ChatPatches.config.chatSearchScreen) {
            TextFieldWidget chatField = ((TextFieldWidget) vanillaChatScreen.getFocused());
            setScreenMethod.call(instance, new ChatSearchScreen( chatField != null ? chatField.getText() : "" ));
        } else {
            setScreenMethod.call(instance, vanillaChatScreen);
        }
    }
}
