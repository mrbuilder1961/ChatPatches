package obro1961.chatpatches.mixin.codec;

import com.mojang.serialization.DataResult;
import net.minecraft.util.ExtraCodecs;
import obro1961.chatpatches.ChatLog;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Slice;

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
	@ModifyArg(
		method = "<clinit>",
		slice = @Slice(
			to = @At(value = "FIELD:LAST", target = "Lnet/minecraft/util/ExtraCodecs;CHAT_STRING:Lcom/mojang/serialization/Codec;", opcode = Opcodes.PUTSTATIC)
		),
		at = @At(value = "INVOKE:FIRST", target = "Lcom/mojang/serialization/Codec;validate(Ljava/util/function/Function;)Lcom/mojang/serialization/Codec;")
	)
	private static Function<String, DataResult<String>> allowEverythingWhileRestoring(Function<String, DataResult<String>> checker) {
		return ChatLog.isCodecSafe().get() ? checker : DataResult::success;
	}
	//? }
}