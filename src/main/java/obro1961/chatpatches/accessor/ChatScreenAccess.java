package obro1961.chatpatches.accessor;

import net.minecraft.client.GuiMessage;
import obro1961.chatpatches.gui.ContextMenu;

import java.util.List;

public interface ChatScreenAccess {
	ContextMenu getContextMenu();

	List<GuiMessage> getSearchResults();

	boolean isMouseOverSettingsMenu(double mX, double mY);

	// everything else is in the access widener now >:)
}