package obro1961.chatpatches.mixin.gui;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.util.CommandHistoryManager;
import net.minecraft.text.Text;
import obro1961.chatpatches.accessor.ChatHudAccessor;
import obro1961.chatpatches.chatlog.ChatLog;
import obro1961.chatpatches.config.Config;
import obro1961.chatpatches.util.ChatUtils;
import org.spongepowered.asm.mixin.Final;
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
 */
@Environment(EnvType.CLIENT)
@Mixin(value = ChatHud.class, priority = 500)
public abstract class ChatHudMixin implements ChatHudAccessor {
    @Shadow @Final private MinecraftClient client;
    @Shadow @Final private List<ChatHudLine> messages;
    @Shadow @Final private List<ChatHudLine.Visible> visibleMessages;
    @Shadow @Final private List<?> removalQueue;
    @Shadow private int scrolledLines;

    @Shadow protected abstract double toChatLineX(double x);
    @Shadow protected abstract double toChatLineY(double y);
    @Shadow protected abstract int getLineHeight();
    @Shadow protected abstract int getMessageLineIndex(double x, double y);

    // ChatHudAccessor methods used outside this mixin
    public List<ChatHudLine> chatpatches$getMessages() { return messages; }
    public List<ChatHudLine.Visible> chatpatches$getVisibleMessages() { return visibleMessages; }
    public int chatpatches$getScrolledLines() { return scrolledLines; }
    public int chatpatches$getMessageLineIndex(double x, double y) { return getMessageLineIndex(x, y); }
    public double chatpatches$toChatLineX(double x) { return toChatLineX(x); }
    public double chatpatches$toChatLineY(double y) { return toChatLineY(y); }
    public int chatpatches$getLineHeight() { return getLineHeight(); }


    /** Prevents the game from actually clearing chat history */
    @Inject(method = "clear", at = @At("HEAD"), cancellable = true)
    private void clear(boolean clearHistory, CallbackInfo ci) {
        if(!config.vanillaClearing) {
            // Clear message using F3+D
            if(!clearHistory) {
                client.getMessageHandler().processAll();
                removalQueue.clear();
                messages.clear();
                visibleMessages.clear();
                // empties the message cache (which on save clears chatlog.json)
                ChatLog.clearMessages();
                ChatLog.clearHistory();
            }

            ci.cancel();
        }
    }

    @ModifyExpressionValue(
        method = {"addMessage(Lnet/minecraft/client/gui/hud/ChatHudLine;)V", "addVisibleMessage"},
        at = @At(value = "CONSTANT", args = "intValue=100")
    )
    private int moreMessages(int hundred) {
        return config.chatMaxMessages;
    }

    /** Allows for chat width larger than 320px */
    @ModifyReturnValue(method = "getWidth()I", at = @At("RETURN"))
    private int moreWidth(int defaultWidth) {
        return config.chatWidth > 0 ? config.chatWidth : defaultWidth;
    }

    /** Allows for chat height larger than 180px */
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
     */
    @ModifyVariable(method = "render", at = @At("STORE"), ordinal = 7)
    private int moveChat(int m) {
        return m - config.calcDynamicChatShift();
    }

    /**
     * Moves the chat line by {@link Config#chatShift} to
     * correctly shift the chat with the other components.
     * Used by the {@link ChatHud} to correctly render
     * message indicators and chat hover tooltips when
     * needed in the shifted position.
     *
     * <p>Target: {@code double d = this.client.getWindow().getScaledHeight() - y - 40.0;}
     */
    @ModifyVariable(method = "toChatLineY", at = @At("HEAD"), argsOnly = true)
    private double moveChatLineY(double y) {
        return y + config.calcDynamicChatShift();
    }

    /**
     * Modifies the incoming message in a multitude of ways.
     * Additions vary from version to version, but the bulk
     * of actual message modding is executed here.
     *
     * @implNote The refreshing parameter is no longer
     * specified because the method is now called in such a
     * way that it only ever modifies real messages, not
     * visible messages that are subject to refreshing.
     *
     * @see ChatUtils#modifyMessage(Text)
     * @see ChatUtils#tryCondenseDupes(Text)
     */
    @ModifyVariable(
        method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
        at = @At("HEAD"),
        argsOnly = true
    )
    private Text modifyMessage(Text m) {
        return ChatUtils.modifyMessage(m);
    }

    @Inject(method = "addToMessageHistory", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/collection/ArrayListDeque;size()I"))
    private void addHistory(String message, CallbackInfo ci) {
        ChatLog.addHistory(message);
    }

    /** Disables logging commands to the vanilla command log if the Chat Patches' ChatLog is enabled. */
    @WrapWithCondition(method = "addToMessageHistory", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/CommandHistoryManager;add(Ljava/lang/String;)V"))
    private boolean disableCommandLog(CommandHistoryManager manager, String message) {
        return !config.chatlog; // if the ChatLog is enabled, don't add to the vanilla command log
    }

    @Inject(method = "logChatMessage", at = @At("HEAD"), cancellable = true)
    private void ignoreRestoredMessages(ChatHudLine hudLine, CallbackInfo ci) {
        if(ChatLog.isRestoring() && hudLine.indicator() != null)
            ci.cancel();
    }
}