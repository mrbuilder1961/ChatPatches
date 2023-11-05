package obro1961.chatpatches.accessor;

import net.minecraft.client.gui.screen.ChatScreen;

public interface ChatScreenAccessor {
    static ChatScreenAccessor from(ChatScreen chatScreen) {
        return ((ChatScreenAccessor) chatScreen);
    }
    default void chatPatches$clearMessageDraft() {}
}
