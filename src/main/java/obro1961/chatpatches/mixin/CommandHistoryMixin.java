//? if >=1.20.2 {
package obro1961.chatpatches.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.kikugie.fletching_table.annotation.MixinEnvironment;
import net.minecraft.client.CommandHistory;
import obro1961.chatpatches.ChatPatches;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@MixinEnvironment(type = MixinEnvironment.Env.CLIENT)
@Mixin(CommandHistory.class)
public abstract class CommandHistoryMixin {
	/**
	 * Prevents {@value CommandHistory#PERSISTED_COMMANDS_FILE_NAME} from being
	 * parsed if the chat log is enabled. This prevents a weird melding of the two
	 * (<a href="https://github.com/mrbuilder1961/ChatPatches/issues/300">#300</a>).
	 *
	 * @return {@code true} if the chat log is disabled and the file exists (should
	 * be parsed), {@code false} otherwise
	 *
	 * @since Minecraft 1.20.2
	 */
	@ModifyExpressionValue(method = "<init>", at = @At(value = "INVOKE", target = "Ljava/nio/file/Files;exists(Ljava/nio/file/Path;[Ljava/nio/file/LinkOption;)Z"))
	private boolean cancelCommandRestoration(boolean commandHistoryFileExists) {
		return !ChatPatches.config.chatlog && commandHistoryFileExists;
	}
}
//?}