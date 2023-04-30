package obro1961.chatpatches.accessor;

import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import obro1961.chatpatches.mixin.chat.ChatHudMixin;

import java.util.List;

/**
 * An access-widening interface used with {@link ChatHudMixin}
 * to access necessary fields and methods w/o an extra
 * accessor mixin.
 */
public interface ChatHudAccessor {
    /** {@link ChatHud#messages} */
    List<ChatHudLine> getMessages();
    /** {@link ChatHud#visibleMessages} */
    List<ChatHudLine.Visible> getVisibleMessages();

    /** {@link ChatHud#getChatScale()} */
    double getChatScale();
    /** {@link ChatHud#getWidth()} */
    int getWidth();
}