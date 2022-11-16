package mechanicalarcane.wmch.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;

/**
 * A mixin accessor method that
 * widens ChatHud access to the
 * {@code messages} and
 * {@code visibleMessages} variables.
 *
 * Used for adding boundary lines and
 * for the {@code CopyMessageCommand}
 * execute methods.
 */
@Environment(EnvType.CLIENT)
@Mixin(value = ChatHud.class, priority = 400)
public interface ChatHudAccessor {
    @Accessor
    public List<ChatHudLine> getMessages();
}