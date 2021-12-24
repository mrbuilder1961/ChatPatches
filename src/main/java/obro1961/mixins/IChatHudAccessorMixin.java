package obro1961.mixins;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.text.Text;

@Mixin(ChatHud.class)
public interface IChatHudAccessorMixin {
    @Accessor
    public List<ChatHudLine<Text>> getMessages();
}
