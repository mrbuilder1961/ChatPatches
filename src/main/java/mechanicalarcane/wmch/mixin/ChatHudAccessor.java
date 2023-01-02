package mechanicalarcane.wmch.mixin;

import mechanicalarcane.wmch.CopyMessageCommand;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
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
    List<ChatHudLine> getMessages();

    @Accessor
    List<ChatHudLine.Visible> getVisibleMessages();
}