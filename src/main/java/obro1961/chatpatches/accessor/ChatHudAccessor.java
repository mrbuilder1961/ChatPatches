package obro1961.chatpatches.accessor;

import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;

import java.util.List;

public interface ChatHudAccessor {
    //todo: re-eval what method access is still needed outside of ChatHud
    /** {@link ChatHud#messages} */
    List<ChatHudLine> chatpatches$getMessages();

    /** {@link ChatHud#visibleMessages} */
    List<ChatHudLine.Visible> chatpatches$getVisibleMessages();

    /** {@link ChatHud#scrolledLines} */
    int chatpatches$getScrolledLines();


    // proxy methods for accessing the originals in ChatHud
    /** {@link ChatHud#getMessageLineIndex(double, double)} */
    int chatpatches$getMessageLineIndex(double x, double y);

    /** {@link ChatHud#toChatLineX(double)} */
    double chatpatches$toChatLineX(double x);

    /** {@link ChatHud#toChatLineY(double)} */
    double chatpatches$toChatLineY(double y);

    /** {@link ChatHud#getLineHeight()} */
    int chatpatches$getLineHeight();
}