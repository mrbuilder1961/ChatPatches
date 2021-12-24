package obro1961.mixins;

import java.util.Deque;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

@Mixin(ChatHud.class)
public interface IChatHudAccessorMixin {
    // used for an alternate clear() so F3+D properly clears chat
    @Accessor
    public List<ChatHudLine<Text>> getMessages();

    @Accessor @Mutable
    public void setMessages(List<ChatHudLine<Text>> messages);
    @Accessor @Mutable
    public void setVisibleMessages(List<ChatHudLine<OrderedText>> visibles);
    @Accessor @Mutable
    public void setMessageQueue(Deque<Text> queue);
}
