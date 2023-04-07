package obro1961.chatpatches.mixin.gui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import net.minecraft.client.gui.screen.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Environment(EnvType.CLIENT)
@Mixin(ChatScreen.class)
public interface ChatScreenAccessor {

	@Accessor
	ChatInputSuggestor getChatInputSuggestor();
}
