package obro1961.chatpatches.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import obro1961.chatpatches.chatlog.ChatLog;
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
    private void saveChatlogOnCrash(CallbackInfo ci) {
        ChatLog.serialize(true);
    }
}
