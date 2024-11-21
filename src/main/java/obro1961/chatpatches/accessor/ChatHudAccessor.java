package obro1961.chatpatches.accessor;

import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import obro1961.chatpatches.mixin.gui.ChatHudMixin;

import java.util.List;

public interface ChatHudAccessor {
    //todo: decide in the future if i should add the prefix back, and also re-eval what access is still needed here
    // unique methods, not present in ChatHud
    /** {@link ChatHud#messages} */
    List<ChatHudLine> chatpatches$getMessages();

    /** {@link ChatHud#visibleMessages} */
    List<ChatHudLine.Visible> chatpatches$getVisibleMessages();

    /** {@link ChatHud#scrolledLines} */
    int chatpatches$getScrolledLines();


    // renamed methods to access-widen them from ChatHud
    /** {@link ChatHud#getMessageLineIndex(double, double)} */
    int chatpatches$getMessageLineIndex(double x, double y);

    /** {@link ChatHud#toChatLineX(double)} */
    double chatpatches$toChatLineX(double x);

    /** {@link ChatHud#toChatLineY(double)} */
    double chatpatches$toChatLineY(double y);

    /** {@link ChatHud#getLineHeight()} */
    int chatpatches$getLineHeight();
}