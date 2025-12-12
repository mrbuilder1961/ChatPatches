package obro1961.chatpatches.accessor;

import obro1961.chatpatches.mixin.gui.ChatHudMixin;

public interface ChatHudAccess {
    /** {@link ChatHudMixin#getGuiMessageIndex(double, double)} */
    int getGuiMessageIndex(double mouseX, double mouseY);

    /** {@link ChatHudMixin#getEoEIndex(double, double)} */
    int getEoEIndex(double mouseX, double mouseY);

	// everything else is in the access widener now >:)
}