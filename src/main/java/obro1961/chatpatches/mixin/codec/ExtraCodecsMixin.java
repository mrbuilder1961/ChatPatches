package obro1961.chatpatches.mixin.codec;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.util.ExtraCodecs;
import obro1961.chatpatches.ChatLog;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

// we can't comment out the entire file otherwise older versions will throw errors bc the mixin won't exist
@Mixin(ExtraCodecs.class)
public abstract class ExtraCodecsMixin {
	//? if >=1.21.5 {
	@Unique private static final String LAMBDA_METHOD_NAME = "method_66032";

	/**
	 * Allows all chat strings to be serialized despite any section signs present.
	 * Unfortunately, a sole check to {@link ChatLog#isCodecSafe()} will still break
	 * context menu clicks on said messages, so a more comprehensive check is
	 * required. Fixes
	 * <a href="https://github.com/mrbuilder1961/ChatPatches/issues/246">#246</a>,
	 * which seems to only affect 1.21.5+.
	 *
	 * @implNote Targets the synthetic method corresponding to the lambda in {@link
	 * ExtraCodecs#CHAT_STRING}'s initializer.
	 */
	@ModifyExpressionValue(
		method = LAMBDA_METHOD_NAME + "(Ljava/lang/String;)Lcom/mojang/serialization/DataResult;",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/util/StringUtil;isAllowedChatCharacter(C)Z")
	)
	private static boolean allowSectionSigns(boolean isValidChatCharacter, @Local char c) {
		return isValidChatCharacter || c == '§';
	}
	//? }
}