package obro1961.chatpatches.accessor;

import obro1961.chatpatches.mixin.gui.ChatComponentMixin;

public interface ChatComponentAccess {
    /** {@link ChatComponentMixin#getGuiMessageIndex(double, double)} */
    int getGuiMessageIndex(double mouseX, double mouseY);

    /** {@link ChatComponentMixin#getEoEIndex(double, double)} */
    int getEoEIndex(double mouseX, double mouseY);

	// everything else is in the access widener now >:)
}