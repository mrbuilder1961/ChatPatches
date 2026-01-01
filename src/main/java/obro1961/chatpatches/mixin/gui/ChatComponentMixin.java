package obro1961.chatpatches.mixin.gui;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
//? if >=1.20.2 {
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
//?}
//? if <=1.20.4 {
//import com.llamalad7.mixinextras.sugar.Local;
//?}
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import obro1961.chatpatches.ChatLog;
import obro1961.chatpatches.accessor.ChatComponentAccess;
import obro1961.chatpatches.config.Config;
import obro1961.chatpatches.util.ChatUtil;
//? if >=1.21.11 {
import obro1961.chatpatches.util.VersionUtil;
//?}
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Intrinsic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

import static obro1961.chatpatches.ChatPatches.config;

/**
 * The main entrypoint mixin for technical chat modifications, notably expansive
 * and complex changes to the way messages are stored, logged, and modified.
 * Note that said changes are called but not necessarily implemented here.
 * <p>
 * {@link ChatComponentAccess} allows accessing some custom public methods outside of
 * this mixin.
 */
@Environment(EnvType.CLIENT)
@Mixin(value = ChatComponent.class, priority = 500)
public abstract class ChatComponentMixin implements ChatComponentAccess {
    @Shadow @Final public List<GuiMessage> allMessages;
    @Shadow @Final public List<GuiMessage.Line> trimmedMessages;

    @Shadow @Final /*? if <1.21.11 {*//*private*//*?}*/ Minecraft minecraft;
    @Shadow @Final private List<?> messageDeletionQueue;

	//? if <1.21.11 {
    /*@Shadow protected abstract double screenToChatX(double x);
    @Shadow protected abstract double screenToChatY(double y);
    @Shadow protected abstract int getMessageEndIndexAt(double chatLineX, double chatLineY);*/
    //?}

    @Shadow public abstract boolean isChatFocused();


    // ChatComponentAccess methods used outside this mixin

	/**
     * Returns the index of the {@link GuiMessage} at the given mouse position.
     *
     * @implNote Different from {@link #getMessageEndIndexAt}, which does <i>not</i>
	 * return a value corresponding to {@link ChatComponent#allMessages}, but returns
	 * the index of a {@linkplain GuiMessage.Line#endOfEntry EoE} line in
	 * {@link ChatComponent#trimmedMessages} at the given mouse position. But when
     * used with {@code allMessages}, it will return inaccurate indices for all
     * messages after the first multiline message (because the two message lists are
     * no longer 1:1).
     * <p>
     * <b>To fix this, we subtract the number of non-EoE messages before the
     * checked index from the index itself, to make it effectively 1:1 again.</b>
     *
     * @see ChatUtil#visible2Message(int)
     * @see #moveChat(int)
     * @see #moveChatLineY(double)
     */
    @Intrinsic // prevents merging or discarding if a conflict unexpectedly occurs, unlike @Unique
    public int getGuiMessageIndex(double mouseX, double mouseY) {
        return ChatUtil.visible2Message(getEoEIndex(mouseX, mouseY));
    }

    /**
     * Simply calls {@link #getMessageEndIndexAt(double, double)} with
     * {@link #screenToChatX(double)} and {@link #screenToChatY(double)} as
     * arguments. Returns the {@link GuiMessage.Line} that is
     * {@linkplain GuiMessage.Line#endOfEntry EoE} at the given mouse position.
	 * In other words, returns the index of the last line that makes up the
     * visible message at the given mouse position. Automatically accounts for
     * any {@link Config#chatShift} offsets with injectors
     * {@link #moveChat(int)} and {@link #moveChatLineY(double)}.
     */
    @Intrinsic // prevents merging or discarding if a conflict unexpectedly occurs, unlike @Unique
    public int getEoEIndex(double mouseX, double mouseY) {
        return
		    //? if >=1.21.11 {
			VersionUtil.getEoEIndex(mouseX, mouseY)
		    //?} else {
			/*getMessageEndIndexAt(screenToChatX(mouseX), screenToChatY(mouseY))*/
		    //?}
		;
    }


    /**
     * Clears the entire chat of all messages and history.
     * Vanilla triggers this method when leaving a world or disconnecting
     * ({@code clearHistory} = false), or when pressing F3+D ({@code clearHistory}
     * = true). In either case, however, the method clears the entire visible chat,
     * which should only be allowed if {@link Config#vanillaClearing} is true.
     *
     * @implNote Since Minecraft 1.20.2, the vanilla method is also called
     * {@linkplain Minecraft#clearClientLevel(Screen) in between
     * switching worlds}, so this method also prevents unwanted chat clearing then too.
     */
    @Inject(method = "clearMessages", at = @At("HEAD"), cancellable = true)
    private void clear(boolean clearHistory, CallbackInfo ci) {
        if(!config.vanillaClearing) {
            // Clear message using F3+D
            if(!clearHistory) {
                minecraft.getChatListener()./*? if >=1.21.9 {*/flushQueue/*?} else {*//*clearQueue*//*?}*/();
                messageDeletionQueue.clear();
                allMessages.clear();
                trimmedMessages.clear();
                // empties the message cache (which on save clears chatlog.json)
                ChatLog.clearMessages();
                ChatLog.clearHistory();
            }

            ci.cancel();
        }
    }

    @ModifyExpressionValue(
        method = {
            "addRecentChat", // sent history
            /*? if >=1.20.5 {*/
            "addMessageToQueue(Lnet/minecraft/client/GuiMessage;)V", "addMessageToDisplayQueue" // actual messages
            /*?} else {*/
            /*"addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;ILnet/minecraft/client/GuiMessageTag;Z)V"*/
            /*?}*/
        },
        at = @At(value = "CONSTANT", args = "intValue=100")
    )
    private int moreMessages(int hundred) {
        return config.chatMaxMessages;
    }

    /**
     * Allows for a chat width larger than 320px. Only used if {@link
     * Config#chatWidth} is a positive number, otherwise uses the default.
     */
    @ModifyReturnValue(method = "getWidth()I", at = @At("RETURN"))
    private int moreWidth(int width) {
        return config.chatWidth > 0 ? config.chatWidth : width;
    }

    /**
     * Allows for a <b>focused</b> chat height taller than 180px. Only used if
     * {@link Config#chatHeight} is a positive number, otherwise uses the default.
     */
    @ModifyReturnValue(method = "getHeight()I", at = @At("RETURN"))
    private int moreFocusedHeight(int height) {
        return config.chatHeight > 0 && isChatFocused() ? config.chatHeight : height;
    }

    /**
     * These methods shift most of the chat hud by
     * {@link Config#chatShift}, including the text
     * and scroll bar, by shifting the y position of the chat.
     *
     * <p>Target: {@code int m = MathHelper.floor((float)(l - 40) / f);}
     *
     * @see Config#calcDynamicChatShift()
     */
    @ModifyVariable(
		method = "render" /*? if >=1.21.11 {*/ + "(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IIZ)V"/*?}*/,
		at = @At("STORE"), // targets ALL store insns for this variable
		ordinal = /*? if >=1.21.11 {*/ 4 /*?} else {*//* 7 *//*?}*/,
        name = "m"
	)
    private int moveChat(int m) {
        return m - config.calcDynamicChatShift();
    }

    /**
     * Moves the chat line by {@link Config#chatShift} to
     * correctly shift the chat with the other components.
     * Used by the {@link ChatComponent} to correctly render
     * message indicators and chat hover tooltips when
     * needed in the shifted position.
     *
     * <p>Target: {@code double d = this.minecraft.getWindow().getScaledHeight() - y - 40.0;}
     *
     * @see Config#calcDynamicChatShift()
     */
    /*? if <1.21.11 {*/
    /*@ModifyVariable(method = "screenToChatY", argsOnly = true, at = @At("HEAD"))
    private double moveChatLineY(double y) {
        return y + config.calcDynamicChatShift();
    }*/
    /*?}*/ // moved to VersionUtil#screenToChatY(double)


    /**
     * Modifies the incoming message in a multitude of ways.
     * Additions vary from version to version, but the bulk
     * of actual message modding is executed here.
     *
     * @implNote Only modifies the message if the chat is not
     * refreshing the hud.
     *
     * @see ChatUtil#modifyMessage(Component)
     * @see ChatUtil#tryCondenseDupes(Component)
     */
    @ModifyVariable(
        method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;"
            + /*? if <=1.20.4 {*//*"I" +*//*?}*/ "Lnet/minecraft/client/GuiMessageTag;"
            + /*? if <=1.20.4 {*//*"Z" +*//*?}*/ ")V",
        at = @At("HEAD"),
        argsOnly = true
    )
    private Component modifyMessage(Component m /*? if <=1.20.4 {*//*, @Local(argsOnly = true) boolean refreshing*//*?}*/) {
        return /*? if <=1.20.4 {*//* refreshing ? m : *//*?}*/ ChatUtil.modifyMessage(m);
    }

    @Inject(
        method = "addRecentChat",
        at = @At(
            value = "INVOKE",
            target = /*? if >=1.20.2 {*/"Lnet/minecraft/util/ArrayListDeque;size()I"/*?} else {*//*"Ljava/util/List;add(Ljava/lang/Object;)Z"*//*?}*/
        )
    )
    private void addHistory(String message, CallbackInfo ci) {
        ChatLog.addHistory(message);
    }

    //? if >=1.20.2 {
    /**
     * Disables the vanilla command log, a feature added in 1.20.2 that logs up to
     * 50 commands only, if the chat log is already enabled.
     *
     * @since 1.20.2, mod WHEN
     */
    @WrapWithCondition(method = "addRecentChat", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/CommandHistory;addCommand(Ljava/lang/String;)V"))
    private boolean toggleCommandLog(net.minecraft.client.CommandHistory manager, String message) {
        return !config.chatlog;
    }
    //?}

    /**
     * Cancels logging chat messages if the chat log is restoring or if the tag is
     * {@link ChatLog#RESTORED_INDICATOR}.
     */
    @Inject(method = "logChatMessage", at = @At("HEAD"), cancellable = true)
    //? if <=1.20.4 {
    /*private void ignoreRestoredMessages(Component message, net.minecraft.client.GuiMessageTag tag, CallbackInfo ci) {
    *///?} else {
    private void ignoreRestoredMessages(GuiMessage message, CallbackInfo ci) {
    //?}
        if(ChatLog.isRestoring() || ChatLog.RESTORED_INDICATOR.equals(/*? if <=1.20.4 {*//*tag*//*?} else {*/message.tag()/*?}*/)) {
			ci.cancel();
		}
    }
}