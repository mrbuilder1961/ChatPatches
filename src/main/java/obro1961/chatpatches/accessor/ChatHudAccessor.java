package obro1961.chatpatches.accessor;

import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import obro1961.chatpatches.mixin.gui.ChatHudMixin;

import java.util.List;

public interface ChatHudAccessor {
    /** {@link ChatHud#messages} */
    List<ChatHudLine> chatpatches$getMessages();

    /** {@link ChatHud#visibleMessages} */
    List<ChatHudLine.Visible> chatpatches$getVisibleMessages();

    /** {@link ChatHud#scrolledLines} */
    int chatpatches$getScrolledLines();

    /** {@link ChatHud#getLineHeight()} */
    int chatpatches$getLineHeight();


    // unique methods

    /** {@link ChatHudMixin#getChatHudLineIndex(double, double)} */
    int getChatHudLineIndex(double mouseX, double mouseY);

    /** {@link ChatHudMixin#getEoEIndex(double, double)} */
    int getEoEIndex(double mouseX, double mouseY);
}