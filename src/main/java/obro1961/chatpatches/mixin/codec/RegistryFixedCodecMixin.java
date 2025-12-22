package obro1961.chatpatches.mixin.codec;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.resources.RegistryFixedCodec;
import obro1961.chatpatches.util.TextUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(RegistryFixedCodec.class)
public class RegistryFixedCodecMixin {
	/**
	 * I'm not sure how much this counts as a proper fix, but it seems to prevent
	 * the undying error that is {@code Element Reference ... is not valid in
	 * current registry set}, and that's enough to me. I look forward to another
	 * issue popping up ~6 months from now, as a consequence of this band-aid.
	 *
	 * @author Thanks so much to @skynotthelimit on Discord for this solution!
	 * <a href="https://discord.com/channels/507304429255393322/1452165613756879044">
	 * See our conversation</a>
	 */
	@ModifyExpressionValue(
		method = "encode(Lnet/minecraft/core/Holder;Lcom/mojang/serialization/DynamicOps;Ljava/lang/Object;)Lcom/mojang/serialization/DataResult;",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/core/Holder;canSerializeIn(Lnet/minecraft/core/HolderOwner;)Z")
	)
	private boolean silenceInvalidRegistryErrors(boolean use) {
		return use || !TextUtil.isCodecSafe().get();
	}
}