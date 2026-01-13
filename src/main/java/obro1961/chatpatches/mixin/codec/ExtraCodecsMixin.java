package obro1961.chatpatches.mixin.codec;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringUtil;
import obro1961.chatpatches.util.TextUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

// we can't comment out the entire file otherwise older versions will throw errors bc the mixin won't exist
@Mixin(ExtraCodecs.class)
public abstract class ExtraCodecsMixin {
	//? if >=1.21.5 {
	@Unique private static final String LAMBDA_METHOD_NAME = /*? if >1.21.11 {*//*"lambda$static$63"*//*?} else {*/"method_66032"/*?}*/; // stonecutter:todo >=26.1
	@Unique private static final String LAMBDA_METHOD_SIGNATURE = "(Ljava/lang/String;)Lcom/mojang/serialization/DataResult;";
	@Unique private static final String TARGET_METHOD_SIGNATURE = "Lnet/minecraft/util/StringUtil;isAllowedChatCharacter(" + /*? if >=1.21.9 {*/"I"/*?} else {*//*"C"*//*?}*/ + ")Z";

	/**
	 * Allows all chat strings to be serialized despite any section signs present.
	 * Unfortunately, a sole check to {@link TextUtil#safeCodec} will still break
	 * context menu clicks on said messages, so a more comprehensive check is
	 * required. Fixes
	 * <a href="https://github.com/mrbuilder1961/ChatPatches/issues/246">#246</a>,
	 * which seems to only affect 1.21.5+.
	 * <p>
	 * Since 1.21.9, {@link StringUtil#isAllowedChatCharacter(int)} takes an int
	 * instead of a char.
	 *
	 * @implNote Targets the synthetic method corresponding to the lambda in {@link
	 * ExtraCodecs#CHAT_STRING}'s initializer.
	 */
	@ModifyExpressionValue(
		method = LAMBDA_METHOD_NAME + LAMBDA_METHOD_SIGNATURE,
		at = @At(value = "INVOKE", target = TARGET_METHOD_SIGNATURE)
	)
	private static boolean allowSectionSigns(boolean isValidChatCharacter, @Local char c) {
		return isValidChatCharacter || c == '§';
	}
	//?}
}