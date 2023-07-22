package obro1961.chatpatches.util;

import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.text.Text;
import obro1961.chatpatches.gui.MenuButtonWidget;
import org.spongepowered.asm.mixin.Unique;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static obro1961.chatpatches.util.RenderUtils.NIL_HUD_LINE;

public class SharedVariables {
    // search stuff
    @Unique public static boolean showSearch = true;
    @Unique public static boolean showSettingsMenu = false; // note: doesn't really need to be static
    // copy menu stuff
    @Unique public static boolean showCopyMenu = false; // true when a message was right-clicked on
    @Unique public static ChatHudLine selectedLine = NIL_HUD_LINE;
    @Unique public static Map<Text, MenuButtonWidget> mainButtons = new LinkedHashMap<>(); // buttons that appear on the initial click
    @Unique public static Map<Text, MenuButtonWidget> hoverButtons = new LinkedHashMap<>(); // buttons that are revealed on hover
    @Unique public static List<ChatHudLine.Visible> hoveredVisibles = new ArrayList<>();
    // drafting
    @Unique public static String searchDraft = "";
    @Unique public static String messageDraft = "";
}
