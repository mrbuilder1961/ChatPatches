package obro1961.chatpatches.mixin.gui;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import obro1961.chatpatches.ChatLog;
import obro1961.chatpatches.accessor.ChatHudAccess;
import obro1961.chatpatches.config.Config;
import obro1961.chatpatches.util.ChatUtils;
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
 * The main entrypoint mixin for technical chat modifications,
 * notably expansive and complex changes to the way messages
 * are stored, logged, and modified in the chat.
 * Implements {@link ChatHudAccess} to widen access to
 * extra fields and methods used elsewhere.
 */
@Environment(EnvType.CLIENT)
@Mixin(value = ChatComponent.class, priority = 500)
public abstract class ChatHudMixin implements ChatHudAccess {
    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private List<GuiMessage> allMessages;
    @Shadow @Final private List<GuiMessage.Line> trimmedMessages;
    @Shadow @Final private List<?> messageDeletionQueue;
    @Shadow private int chatScrollbarPos;

    @Shadow protected abstract double screenToChatX(double x);
    @Shadow protected abstract double screenToChatY(double y);
    @Shadow protected abstract int getLineHeight();
    @Shadow protected abstract int getMessageEndIndexAt(double chatLineX, double chatLineY);

    // ChatHudAccess methods used outside this mixin
    // @Intrinsic > @Unique bc it prevents merging or discarding if a conflict unexpectedly occurs
    @Intrinsic public List<GuiMessage> chatpatches$getMessages() { return allMessages; }
    @Intrinsic public List<GuiMessage.Line> chatpatches$getVisibleMessages() { return trimmedMessages; }
    @Intrinsic public int chatpatches$getScrolledLines() { return chatScrollbarPos; }
    @Intrinsic public int chatpatches$getLineHeight() { return getLineHeight(); }


    /**
     * Returns the index of the chat line at the given mouse position.
     *
     * @implNote Unfortunately, Yarn's name choice for the {@link #getMessageEndIndexAt}
     * method (called in {@link #getEoEIndex(double, double)}) is <b>extremely
     * misleading and inaccurate, because it implies a return value corresponding
     * to {@link ChatComponent#allMessages}, which is not true</b>. In reality, the method
     * returns the index of a {@linkplain GuiMessage.Line#endOfEntry EoE} line
     * in {@link ChatComponent#trimmedMessages} at the given mouse position. But when
     * used with {@code allMessages}, it will return inaccurate indices for all messages
     * after the first multiline message (because the two message lists are no longer
     * 1:1).
     * <br>
     * <i>To fix this, we subtract the number of non-EoE messages before the
     * checked index from the index itself, to make it effectively 1:1 again.</i>
     *
     * @see ChatUtils#visible2Message(int)
     * @see #moveChat(int)
     * @see #moveChatLineY(double)
     */
    @Intrinsic // better than @Unique bc it prevents merging or discarding if a conflict unexpectedly occurs
    public int getChatHudLineIndex(double mouseX, double mouseY) {
        return ChatUtils.visible2Message(getEoEIndex(mouseX, mouseY));
    }

    /**
     * Simply calls {@link #getMessageEndIndexAt(double, double)} with
     * {@link #screenToChatX(double)} and {@link #screenToChatY(double)} as
     * arguments. Returns the {@link GuiMessage.Line} that is {@linkplain
     * GuiMessage.Line#endOfEntry EoE} at the given mouse position. In
     * other words, returns the index of the last line that makes up the
     * visible message at the given mouse position. Automatically accounts
     * for any {@link Config#chatShift} offsets with injectors
     * {@link #moveChat(int)} and {@link #moveChatLineY(double)}.
     */
    @Intrinsic // better than @Unique bc it prevents merging or discarding if a conflict unexpectedly occurs
    public int getEoEIndex(double mouseX, double mouseY) {
        return getMessageEndIndexAt(screenToChatX(mouseX), screenToChatY(mouseY));
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
                minecraft.getChatListener().clearQueue();
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

    /** Increases the chat message limit */
    @ModifyExpressionValue(
        //? if <=1.20.4 {
        /*method = {"addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;ILnet/minecraft/client/GuiMessageTag;Z)V", "addRecentChat"},
        *///?} else {
        method = {"addMessageToQueue(Lnet/minecraft/client/GuiMessage;)V", "addMessageToDisplayQueue", "addRecentChat"},
        //?}
        at = @At(value = "CONSTANT", args = "intValue=100")
    )
    private int moreMessages(int hundred) {
        return config.chatMaxMessages;
    }

    /**
     * Allows for a chat width larger than 320px. Only used if the
     * {@link Config#chatWidth} value is configured to be greater
     * than 0, otherwise uses the default width option.
     */
    @ModifyReturnValue(method = "getWidth()I", at = @At("RETURN"))
    private int moreWidth(int defaultWidth) {
        return config.chatWidth > 0 ? config.chatWidth : defaultWidth;
    }

    /**
     * Allows for a chat height taller than 180px. Only used if the
     * {@link Config#chatHeight} value is configured to be greater
     * than 0, otherwise uses the default width option.
     */
    @ModifyReturnValue(method = "getHeight()I", at = @At("RETURN"))
    private int moreHeight(int defaultHeight) {
        return config.chatHeight > 0 ? config.chatHeight : defaultHeight;
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
    @ModifyVariable(method = "render", at = @At("STORE"), ordinal = 7)
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
    @ModifyVariable(method = "screenToChatY", argsOnly = true, at = @At("HEAD"))
    private double moveChatLineY(double y) {
        return y + config.calcDynamicChatShift();
    }


    /**
     * Modifies the incoming message in a multitude of ways.
     * Additions vary from version to version, but the bulk
     * of actual message modding is executed here.
     *
     * @implNote Only modifies the message if the chat is not
     * refreshing the hud.
     *
     * @see ChatUtils#modifyMessage(Component)
     * @see ChatUtils#tryCondenseDupes(Component)
     */
    @ModifyVariable(
        //? if <=1.20.4 {
        /*method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;ILnet/minecraft/client/GuiMessageTag;Z)V",
        *///?} else {
        method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
        //?}
        at = @At("HEAD"),
        argsOnly = true
    ) // stonecutter: remove qualifier when import optimizer fix is available
    private Component modifyMessage(Component m /*? if <=1.20.4 {*//*, @com.llamalad7.mixinextras.sugar.Local(argsOnly = true) boolean refreshing *//*?}*/) {
        return /*? if <=1.20.4 {*/ /*refreshing ? m : *//*?}*/ ChatUtils.modifyMessage(m);
    }

    @Inject(
        method = "addRecentChat",
        at = @At(
            value = "INVOKE",
            //? if >=1.20.2 {
            target = "Lnet/minecraft/util/ArrayListDeque;size()I"
            //?} else {
            /*target = "Ljava/util/List;add(Ljava/lang/Object;)Z"
            *///?}
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
    @com.llamalad7.mixinextras.injector.v2.WrapWithCondition( // stonecutter: remove qualifier when import optimizer fix is available
        method = "addRecentChat",
        at = @At(
            value = "INVOKE",
            //? if >=1.20.2 {
            target = "Lnet/minecraft/client/CommandHistory;addCommand(Ljava/lang/String;)V"
            //?} else {
            /*target = "Ljava/util/List;add(Ljava/lang/Object;)V"
            *///?}
        )
    )
    private boolean toggleCommandLog(net.minecraft.client.CommandHistory manager, String message) {
        return !config.chatlog;
    }
    //?}

    /**
     * Cancels logging chat messages if the chat log is restoring or if the tag is
     * {@link ChatLog#RESTORED_INDICATOR}.
     */
    @Inject(method = "logChatMessage", at = @At("HEAD"), cancellable = true) // stonecutter: remove qualifier when import optimizer fix is available
    //? if <=1.20.4 {
    /*private void ignoreRestoredMessages(Component message, net.minecraft.client.GuiMessageTag tag, CallbackInfo ci) {
    *///?} else {
    private void ignoreRestoredMessages(GuiMessage message, CallbackInfo ci) {
    //?}

        if(ChatLog.isRestoring() || ChatLog.RESTORED_INDICATOR.equals(/*? if <=1.20.4 {*//*tag*//*?} else {*/message.tag()/*?}*/))
            ci.cancel();
    }
}