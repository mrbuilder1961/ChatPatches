package obro1961.chatpatches.mixin.gui;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.CommandHistoryManager;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import obro1961.chatpatches.accessor.ChatHudAccessor;
import obro1961.chatpatches.chatlog.ChatLog;
import obro1961.chatpatches.config.Config;
import obro1961.chatpatches.util.ChatUtils;
import org.jetbrains.annotations.Nullable;
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
 * Implements {@link ChatHudAccessor} to widen access to
 * extra fields and methods used elsewhere.
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
    @Shadow public abstract double getChatScale();

    // ChatHudAccessor methods used outside this mixin
    @Intrinsic public List<ChatHudLine> chatpatches$getMessages() { return messages; }
    @Intrinsic public List<ChatHudLine.Visible> chatpatches$getVisibleMessages() { return visibleMessages; }
    @Intrinsic public int chatpatches$getScrolledLines() { return scrolledLines; }
    @Intrinsic public int chatpatches$getMessageLineIndex(double x, double y) { return getMessageLineIndex(x, y); }
    @Intrinsic public double chatpatches$toChatLineX(double x) { return toChatLineX(x); }
    @Intrinsic public double chatpatches$toChatLineY(double y) { return toChatLineY(y); }
    @Intrinsic public int chatpatches$getLineHeight() { return getLineHeight(); }


    /**
     * Clears the entire chat of all messages and history.
     * Vanilla triggers this method when leaving a world or disconnecting
     * ({@code clearHistory} = false), or when pressing F3+D ({@code clearHistory}
     * = true). In either case, however, the method clears the entire visible chat,
     * which should only be allowed if {@link Config#vanillaClearing} is true.
     *
     * @implNote Since Minecraft 1.20.2, the vanilla method is also called
     * {@linkplain MinecraftClient#enterReconfiguration(Screen) in between
     * switching worlds}, so this method also prevents unwanted chat clearing then too.
     */ // prepub: i closed (#147) as completed bc it seems like this already covers that issue case but idk.
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

    /** Increases the chat message limit */
    @ModifyExpressionValue(
        method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;ILnet/minecraft/client/gui/hud/MessageIndicator;Z)V",
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
     * These methods shift most of the chat hud by
     * {@link Config#shiftChat}, including the text
     * and scroll bar, by shifting the y position of the chat.
     *
     * <p>Target: {@code int m = MathHelper.floor((float)(l - 40) / f);}
     */
    @ModifyVariable(method = "render", at = @At("STORE"), ordinal = 7)
    private int moveChat(int m) {
        return m - MathHelper.floor(config.shiftChat / getChatScale());
    }

    /**
     * Moves the chat line by {@link Config#shiftChat} to
     * correctly shift the chat with the other components.
     * Used by the {@link ChatHud} to correctly render
     * message indicators and chat hover tooltips when
     * needed in the shifted position.
     *
     * <p>Target: {@code double d = this.client.getWindow().getScaledHeight() - y - 40.0;}
     */
    @ModifyVariable(method = "toChatLineY", argsOnly = true, at = @At("HEAD"))
    private double moveChatLineY(double y) {
        return y + config.shiftChat;
    }


    /**
     * Modifies the incoming message in a multitude of ways.
     * Additions vary from version to version, but the bulk
     * of actual message modding is executed here.
     *
     * @implNote Only modifies the message if the chat is not
     * refreshing the hud.
     *
     * @see ChatUtils#modifyMessage(Text)
     * @see ChatUtils#tryCondenseDupes(Text)
     */
    @ModifyVariable(
        method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;ILnet/minecraft/client/gui/hud/MessageIndicator;Z)V",
        at = @At("HEAD"),
        argsOnly = true
    )
    private Text modifyMessage(Text m, @Local(argsOnly = true) boolean refreshing) {
        return refreshing ? m : ChatUtils.modifyMessage(m);
    }

    @Inject(method = "addToMessageHistory", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/collection/ArrayListDeque;size()I"))
    private void addHistory(String message, CallbackInfo ci) {
        ChatLog.addHistory(message);
    }

    /**
     * Disables the vanilla command log, a feature added in 1.20.2 that logs up to
     * 50 commands only, if the chat log is already enabled.
     *
     * @since 1.20.2, mod WHEN
     */
    @WrapWithCondition(method = "addToMessageHistory", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/CommandHistoryManager;add(Ljava/lang/String;)V"))
    private boolean toggleCommandLog(CommandHistoryManager manager, String message) {
        return !config.chatlog;
    }

    /**
     * Cancels logging chat messages if the chat log is loading and the indicator isn't null,
     * meaning it's a restored message. Called before a message is logged.
     */
    @Inject(method = "logChatMessage", at = @At("HEAD"), cancellable = true)
    private void ignoreRestoredMessages(Text message, @Nullable MessageIndicator indicator, CallbackInfo ci) {
        if(ChatLog.isSuspended() && indicator != null)
            ci.cancel();
    }
}