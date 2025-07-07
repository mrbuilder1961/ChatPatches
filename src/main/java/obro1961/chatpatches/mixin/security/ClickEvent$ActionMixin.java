package obro1961.chatpatches.mixin.security;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.network.chat.ClickEvent;
import obro1961.chatpatches.ChatLog;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClickEvent.Action.class)
public abstract class ClickEvent$ActionMixin {
	/**
	 * Allows the serialization of {@link ClickEvent.Action#OPEN_FILE} click events
	 * by disabling the validation check while {@link ChatLog#isCodecSafe()} is {@code
	 * false}. This prevents crashes during serialization and errors when using the
	 * context menu, and the additional check stops malicious click events from being
	 * unilaterally accepted. <b>However, players are still responsible for being
	 * vigilant when clicking links and opening files through Minecraft</b>.
	 */
	@ModifyReturnValue(method = "isAllowedFromServer", at = @At("RETURN"))
	private boolean allowConditionalSerialization(boolean safe) {
		return safe || !ChatLog.isCodecSafe().get(); // safe ? true : !ChatLog.isCodecSafe().get()
	}
}