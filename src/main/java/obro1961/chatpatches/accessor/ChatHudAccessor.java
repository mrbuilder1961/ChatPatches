package obro1961.chatpatches.accessor;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import obro1961.chatpatches.mixin.chat.ChatHudMixin;

import java.util.List;

/**
 * An access-widening interface used with {@link ChatHudMixin}
 * to access necessary fields and methods w/o an extra
 * accessor mixin. To get an instance, use
 * {@link ChatHudAccessor#from(ChatHud)} or
 * {@link ChatHudAccessor#from(MinecraftClient)}.
 */
public interface ChatHudAccessor {
    // these two methods avoid needing to cast everywhere because it looks ugly
    static ChatHudAccessor from(ChatHud chatHud) {
        return ((ChatHudAccessor) chatHud);
    }
    static ChatHudAccessor from(MinecraftClient client) {
        return from(client.inGameHud.getChatHud());
    }

    /** {@link ChatHud#messages} */
    List<ChatHudLine> getMessages();
    /** {@link ChatHud#visibleMessages} */
    List<ChatHudLine.Visible> getVisibleMessages();
    /** {@link ChatHud#scrolledLines} */
    int getScrolledLines();

    /** {@link ChatHud#getMessageLineIndex(double, double)} */
    int _getMessageLineIndex(double x, double y);
    /** {@link ChatHud#toChatLineX(double)} */
    double _toChatLineX(double x);
    /** {@link ChatHud#toChatLineY(double)} */
    double _toChatLineY(double y);
    /** {@link ChatHud#getLineHeight()} */
    int _getLineHeight();
}