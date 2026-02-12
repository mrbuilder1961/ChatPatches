package obro1961.chatpatches.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.Layout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.WarningScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import obro1961.chatpatches.ChatPatches;
import obro1961.chatpatches.util.ChatUtil;
import org.jetbrains.annotations.NotNull;

import static obro1961.chatpatches.gui.ContextMenu.LANG_PREFIX;

public class DeletionWarningScreen extends WarningScreen {
	public static final Component TITLE = Component.translatable(LANG_PREFIX + "delete.confirm").withStyle(ChatFormatting.RED);
	public static final Component DISMISS = Component.translatable("multiplayerWarning.check");

	/**
	 * Should always be a {@link net.minecraft.client.gui.screens.ChatScreen} instance.
	 * This could theoretically change in the future.
	 */
	public final Screen parent;

	private final GuiMessage chatMessage;

	private DeletionWarningScreen(@NotNull Screen parent, @NotNull GuiMessage chatMessage, @NotNull Component MESSAGE_PREVIEW) {
		super(TITLE, MESSAGE_PREVIEW, DISMISS, CommonComponents.joinForNarration(TITLE, MESSAGE_PREVIEW));
		this.parent = parent;
		this.chatMessage = chatMessage;
	}

	public static DeletionWarningScreen of(Screen parent, GuiMessage chatMessage) {
		// requires a factory method because java just NEEDS its constructor first! sigh.
		Component messagePreview = CommonComponents.optionNameValue(Component.translatable("gui.socialInteractions.tooltip.hide"), chatMessage.content());
		return new DeletionWarningScreen(parent, chatMessage, messagePreview);
	}


	private void close() {
		minecraft.setScreen(parent);
	}

	/**
	 * Largely taken from {@link net.minecraft.client.gui.screens.multiplayer.SafetyScreen}
	 */
	/*? if >1.20.4 {*/@Override
	protected Layout addFooterButtons() {
		LinearLayout layout = LinearLayout.horizontal().spacing(8);
		layout.addChild(Button.builder(CommonComponents.GUI_PROCEED, me -> {
			if(stopShowing.selected()) {
				ChatPatches.config.contextDeletionWarning = false;
			}
			ChatUtil.deleteMessage(chatMessage);
			close();
		}).build());
		layout.addChild(Button.builder(CommonComponents.GUI_CANCEL, me -> close()).build());
		return layout;
	}

	// todo: move the button builders to their own methods to then call in this and the modern equivalent method so its less duplication
	/*?} else {*/
	/*@Override
	protected void initButtons(int yOffset) {
		this.addRenderableWidget(Button.builder(CommonComponents.GUI_PROCEED, me -> {
            if(stopShowing.selected()) {
				ChatPatches.config.contextDeletionWarning = false;
            }
			ChatUtil.deleteMessage(chatMessage);
            close();
        }).bounds(this.width / 2 - 155, 100 + yOffset, 150, 20).build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, me -> close()).bounds(this.width / 2 - 155 + 160, 100 + yOffset, 150, 20).build());
	}*/
	//?}
}