package obro1961.chatpatches.mixin.chat;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import obro1961.chatpatches.CopyMessageCommand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * A mixin accessor method that widens ChatHud access to the
 * {@code messages} and {@code visibleMessages} variables.
 * Used for adding boundary lines and for the
 * {@link CopyMessageCommand} execute methods.
 */
@Environment(EnvType.CLIENT)
@Mixin(value = ChatHud.class, priority = 400)
public interface ChatHudAccessor {
    @Accessor
    List<ChatHudLine<Text>> getMessages();

    @Accessor
    List<ChatHudLine<OrderedText>> getVisibleMessages();
}