package obro1961.wmch.mixins;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.text.Text;

/** Used for an alternate {@code clear()} so F3+D actually works */
@Mixin(ChatHud.class)
public interface ChatHudAccessorMixin {
    @Accessor
    public List<ChatHudLine<Text>> getMessages();
}