package mechanicalarcane.wmch.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import mechanicalarcane.wmch.WMCH;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;

/** Injects callbacks to game exit events so cached data can still be saved */
@Environment(EnvType.CLIENT)
@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {
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
    public void saveCachedDataOnCrash(CallbackInfo ci) {
        WMCH.writeCachedData(true);
    }

    /*
    this.thread = Thread.currentThread();
        if (Runtime.getRuntime().availableProcessors() > 4) {
            this.thread.setPriority(10);
        }
        try {
            boolean bl = false;
            while (this.running) {
                if (this.crashReportSupplier != null) {
                    MinecraftClient.printCrashReport(this.crashReportSupplier.get());
                    return;
                }
                try {
                    TickDurationMonitor tickDurationMonitor = TickDurationMonitor.create("Renderer");
                    boolean bl2 = this.shouldMonitorTickDuration();
                    this.profiler = this.startMonitor(bl2, tickDurationMonitor);
                    this.profiler.startTick();
                    this.recorder.startTick();
                    this.render(!bl);
                    this.recorder.endTick();
                    this.profiler.endTick();
                    this.endMonitor(bl2, tickDurationMonitor);
                }
                catch (OutOfMemoryError outOfMemoryError) {
                    if (bl) {
                        throw outOfMemoryError;
                    }
                    * -->  <--
                    this.cleanUpAfterCrash();
                    this.setScreen(new OutOfMemoryScreen());
                    System.gc();
                    LOGGER.error(LogUtils.FATAL_MARKER, "Out of memory", outOfMemoryError);
                    bl = true;
                }
            }
        }
        catch (CrashException crashException) {
            * -->  <--
            this.addDetailsToCrashReport(crashException.getReport());
            this.cleanUpAfterCrash();
            LOGGER.error(LogUtils.FATAL_MARKER, "Reported exception thrown!", crashException);
            MinecraftClient.printCrashReport(crashException.getReport());
        }
        catch (Throwable throwable) {
            * -->  <--
            CrashReport crashReport = this.addDetailsToCrashReport(new CrashReport("Unexpected error", throwable));
            LOGGER.error(LogUtils.FATAL_MARKER, "Unreported exception thrown!", throwable);
            this.cleanUpAfterCrash();
            MinecraftClient.printCrashReport(crashReport);
        }
    */
}
