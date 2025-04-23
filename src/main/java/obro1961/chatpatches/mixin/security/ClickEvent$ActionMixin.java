package obro1961.chatpatches.mixin.security;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.Lifecycle;
import net.minecraft.text.ClickEvent;
import obro1961.chatpatches.ChatPatches;
import obro1961.chatpatches.chatlog.ChatLog;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClickEvent.Action.class)
public abstract class ClickEvent$ActionMixin {
	/**
	 * Allows the serialization of {@link ClickEvent.Action#OPEN_FILE} click
	 * events by disabling the validation check while the chat log {@linkplain
	 * ChatLog#isSuspended() is suspended} or when {@link
	 * ChatPatches#usingUnsafeCodec} is {@code true}. This prevents crashes
	 * during serialization and errors when using the context menu, and the
	 * additional check stops malicious click events from being unilaterally
	 * accepted.
	 *
	 * @since 1.20.4
	 */
	@Inject(method = "validate", at = @At("HEAD"), cancellable = true)
	private static void allowDuringSerialization(ClickEvent.Action action, CallbackInfoReturnable<DataResult<ClickEvent.Action>> cir) {
		// only pauses validation during serialization of OPEN_URL events to avoid security exploit(s)
		if(!action.isUserDefinable() && (ChatLog.isSuspended() || ChatPatches.usingUnsafeCodec))
			cir.setReturnValue(DataResult.success(action, Lifecycle.stable()));
	}
}