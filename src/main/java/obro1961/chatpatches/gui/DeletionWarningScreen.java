package obro1961.chatpatches.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.chat.GuiMessage;
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
//? if >=1.21.11 {
import org.jspecify.annotations.NonNull;
//?}

import java.util.Objects;

import static obro1961.chatpatches.gui.ContextMenu.LANG_PREFIX;

public class DeletionWarningScreen extends WarningScreen {
	public static final Component TITLE = Component.translatable(LANG_PREFIX + "delete.confirm").withStyle(ChatFormatting.RED);
	public static final Component DISMISS = Component.translatable("multiplayerWarning.check");

	/**
	 * Should always be a {@link net.minecraft.client.gui.screens.ChatScreen} instance.
	 * This could theoretically change in the future.
	 */
	public final Screen parent;

	/**
	 * Fixes the side effect of the chat resetting the scroll position, due to
	 * this new screen being created (and subsequently destroying the old one).
	 */
	private final int scroll;
	private final GuiMessage chatMessage;

	public DeletionWarningScreen(@NotNull Screen parent, @NotNull GuiMessage chatMessage) {
		super(TITLE, chatMessage.content(), DISMISS, CommonComponents.joinForNarration(TITLE, chatMessage.content()));
		this.parent = parent;
		this.chatMessage = chatMessage;
		//noinspection DataFlowIssue: that's what i said too. man fuck 1.20.1
		this.scroll = Objects.requireNonNullElse(minecraft, Minecraft.getInstance()).gui.hud.getChat().chatScrollbarPos;
	}


	private void close() {
		minecraft.gui.setScreen(parent);
		minecraft.gui.hud.getChat().scrollChat(scroll);
	}

	/**
	 * Largely taken from {@link net.minecraft.client.gui.screens.multiplayer.SafetyScreen}
	 */
	@Override
	/*? if >=1.21.11 {*/@NonNull/*?}*/
	protected /*? if >1.20.4 {*/ Layout addFooterButtons() /*?} else {*//*void initButtons(int yOffset)*//*?}*/ {
		var proceedButton = Button.builder(CommonComponents.GUI_PROCEED, me -> {
			if(stopShowing.selected()) {
				ChatPatches.config.contextDeletionWarning = false;
			}
			ChatUtil.deleteMessage(chatMessage, ChatPatches.config.contextDeletionSizzle);
			close();
		});
		var cancelButton = Button.builder(CommonComponents.GUI_CANCEL, me -> close());

		/*? if >1.20.4 {*/
		var layout = LinearLayout.horizontal().spacing(8);

		layout.addChild(proceedButton.build());
		layout.addChild(cancelButton.build());

		return layout;
		/*?} else {*//*
		addRenderableWidget(proceedButton.pos(width / 2 - 155, 120 + yOffset).build());
        addRenderableWidget(cancelButton.pos(width / 2 - 155 + 160, 120 + yOffset).build());
		*//*?}*/
	}
}