package obro1961.chatpatches.mixin.codec;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.resources.RegistryFixedCodec; // net.minecraft.core.registries.codec
import obro1961.chatpatches.util.TextUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(RegistryFixedCodec.class)
public abstract class RegistryFixedCodecMixin {
	@ModifyExpressionValue(
		method = "encode(Lnet/minecraft/core/Holder;Lcom/mojang/serialization/DynamicOps;Ljava/lang/Object;)Lcom/mojang/serialization/DataResult;",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/core/Holder;canSerializeIn(Lnet/minecraft/core/HolderOwner;)Z")
	)
	private boolean silenceInvalidRegistryErrors(boolean use) {
		return use || !TextUtil.safeCodec.get();
	}
}