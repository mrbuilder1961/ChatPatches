package obro1961.chatpatches.mixin.codec;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.serialization.DataResult;
import net.minecraft.util.ExtraCodecs;
import obro1961.chatpatches.ChatLog;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Function;

// we can't comment out the entire file otherwise older versions will throw errors bc the mixin won't exist
@Mixin(ExtraCodecs.class)
public abstract class ExtraCodecsMixin {
	//? if >=1.21.5 {
	/**
	 * Allows all chat strings to be serialized while restoring chat logs. Fixes
	 * <a href="https://github.com/mrbuilder1961/ChatPatches/issues/246">#246</a>,
	 * which seems to only affect 1.21.5+.
	 */
	@Definition(id = "CHAT_STRING", field = "Lnet/minecraft/util/ExtraCodecs;CHAT_STRING:Lcom/mojang/serialization/Codec;")
	@Definition(id = "STRING", field = "Lcom/mojang/serialization/Codec;STRING:Lcom/mojang/serialization/codecs/PrimitiveCodec;")
	@Definition(id = "validate", method = "Lcom/mojang/serialization/codecs/PrimitiveCodec;validate(Ljava/util/function/Function;)Lcom/mojang/serialization/Codec;")
	@Expression("CHAT_STRING = STRING.validate(@(?))")
	@ModifyExpressionValue(method = "<clinit>", at = @At("MIXINEXTRAS:EXPRESSION"))
	private static Function<String, DataResult<String>> allowEverythingWhileRestoring(Function<String, DataResult<String>> checker) {
		return ChatLog.isCodecSafe().get() ? checker : DataResult::success;
	}
	//? }
}