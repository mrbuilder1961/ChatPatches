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
import net.minecraft.client.util.CommandHistoryManager;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import obro1961.chatpatches.ChatPatches;
import obro1961.chatpatches.accessor.ChatHudAccessor;
import obro1961.chatpatches.chatlog.ChatLog;
import obro1961.chatpatches.config.Config;
import obro1961.chatpatches.util.ChatUtils;
import obro1961.chatpatches.util.Flags;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static obro1961.chatpatches.ChatPatches.config;
import static obro1961.chatpatches.util.ChatUtils.MESSAGE_INDEX;
import static obro1961.chatpatches.util.ChatUtils.getPart;

/**
 * The main entrypoint mixin for most chat modifications.
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


    @Shadow public abstract double getChatScale();
    @Shadow public abstract int getVisibleLineCount();
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
        method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;ILnet/minecraft/client/gui/hud/MessageIndicator;Z)V",
        at = @At(value = "CONSTANT", args = "intValue=100")
    )
    private int moreMessages(int hundred) {
        return config.chatMaxMessages;
    }

    /** allows for a chat width larger than 320px */
    @ModifyReturnValue(method = "getWidth()I", at = @At("RETURN"))
    private int moreWidth(int defaultWidth) {
        return config.chatWidth > 0 ? config.chatWidth : defaultWidth;
    }

    /**
     * These methods shift most of the chat hud by
     * {@link Config#shiftChat}, including the text
     * and scroll bar, by shifting the y position of the chat.
     *
     * @implNote Target: <br>{@code int m = MathHelper.floor((float)(l - 40) / f);}
     */
    @ModifyVariable(method = "render", at = @At("STORE"), ordinal = 7)
    private int moveChat(int m) {
        return m - MathHelper.floor(config.shiftChat / this.getChatScale());
    }

    /**
     * Moves the message indicator and hover tooltip
     * by {@link Config#shiftChat} to correctly shift
     * the chat with the other components.
     * Targets two methods because the first part of both
     * methods are identical.
     */
    @ModifyVariable(method = {"getIndicatorAt", "getTextStyleAt"}, argsOnly = true, at = @At("HEAD"), ordinal = 1)
    private double moveINDHoverText(double e) {
        return e + ( config.shiftChat * this.getChatScale() );
    }


    /**
     * Modifies the incoming message by adding timestamps, nicer
     * player names, hover events, and duplicate counters in conjunction with
     * {@link #addCounter(Text, boolean)}.
     * <br>
     * See {@link ChatUtils#modifyMessage(Text, boolean)} for detailed
     * implementation specifications.
     */
    @ModifyVariable(
        method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;ILnet/minecraft/client/gui/hud/MessageIndicator;Z)V",
        at = @At("HEAD"),
        argsOnly = true
    )
    private Text modifyMessage(Text m, @Local(argsOnly = true) boolean refreshing) {
        return addCounter(ChatUtils.modifyMessage(m, refreshing), refreshing);
    }

    @Inject(method = "addToMessageHistory", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/collection/ArrayListDeque;size()I"))
    private void addHistory(String message, CallbackInfo ci) {
        if( !Flags.LOADING_CHATLOG.isRaised() )
            ChatLog.addHistory(message);
    }

    /** Disables logging commands to the vanilla command log if the Chat Patches' ChatLog is enabled. */
    @WrapWithCondition(method = "addToMessageHistory", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/CommandHistoryManager;add(Ljava/lang/String;)V"))
    private boolean disableCommandLog(CommandHistoryManager manager, String message) {
        return !config.chatlog; // if the ChatLog is enabled, don't add to the vanilla command log
    }

    @Inject(method = "logChatMessage", at = @At("HEAD"), cancellable = true)
    private void ignoreRestoredMessages(Text message, @Nullable MessageIndicator indicator, CallbackInfo ci) {
        if( Flags.LOADING_CHATLOG.isRaised() && indicator != null )
            ci.cancel();
    }

    /**
     * Adds a counter to the chat message, indicating how many times the same
     * message has been sent. Can check only the last message, or
     * {@link Config#counterCompactDistance} times back. Slightly more
     * efficient than the previous method, outlined here: [{@link ChatUtils#tryCondenseMessage(Text, int)}]
     * but it still is quite slow.
     *
     * @implNote
     * <ol>
     *     <li>IF {@code COUNTER} is enabled AND the message count >0 AND the message isn't a boundary line, continue.</li>
     *     <li>Cache the result of trying to condense the incoming message with the last message received.</li>
     *     <li>IF the counter should use the CompactChat method and the message wasn't already condensed:</li>
     *     <ol>
     *         <li>Calculate the adjusted distance to attempt comparing, depending on the amount of messages already in the chat.</li>
     *         <li>Filter all the messages within the target range that are case-insensitively equal to the incoming message.</li>
     *         <li>If a message was the same, call {@link ChatUtils#tryCondenseMessage(Text, int)},
     *         which ultimately removes that message and its visibles.</li>
     *     </ol>
     *     <li>Return the (potentially) condensed message, to later be formatted further in {@link #modifyMessage(Text, boolean)}</li>
     * </ol>
     * (Wraps the entire method in a try-catch to prevent any errors accidentally disabling the chat.)
     *
     * @apiNote This injector is pretty ugly and could definitely be cleaner and more concise, but I'm going to deal with it
     * in the future when I API-ify the rest of the mod. When that happens, this flag-add-flag-cancel method will be replaced
     * with a simple (enormous) method call alongside
     * {@link #modifyMessage(Text, boolean)} in a @{@link ModifyVariable}
     * handler. (NOTE: as of v202.6.0, this is partially done already thanks to #132)
     */
    @Unique
    private Text addCounter(Text incoming, boolean refreshing) {
        try {
            if( config.counter && !refreshing && !messages.isEmpty() ) {
                // condenses the incoming message into the last message if it is the same
                AtomicReference<Text> condensedLastMessage = new AtomicReference<>( ChatUtils.tryCondenseMessage(incoming, 0) );

                // if the counterCompact option is true but the last message received was not condensed, look for
                // any dupes in the last counterCompactDistance messages and if any are found condense them
                if( config.counterCompact && condensedLastMessage.get().equals(incoming) ) {
                    // ensures {0 <= attemptDistance <= messages.size()} is true
                    int attemptDistance = MathHelper.clamp((
                        (config.counterCompactDistance == -1)
                            ? messages.size()
                            : (config.counterCompactDistance == 0)
                                ? this.getVisibleLineCount()
                                : config.counterCompactDistance
                    ), 0, messages.size());

                    // exclude the first message, already checked above
                    messages.subList(1, attemptDistance)
                        .stream()
                        .filter(hudLine -> getPart(hudLine.content(), MESSAGE_INDEX).getString().equalsIgnoreCase( getPart(incoming, MESSAGE_INDEX).getString() ))
                        .findFirst()
                        .ifPresent(hudLine -> condensedLastMessage.set( ChatUtils.tryCondenseMessage(incoming, messages.indexOf(hudLine)) ));
                }

                // this result is used in #modifyMessage(...)
                return condensedLastMessage.get();
            }
        } catch(IndexOutOfBoundsException e) {
            ChatPatches.LOGGER.error("[ChatHudMixin.addCounter] Couldn't add duplicate counter because message '{}' ({} parts) was not constructed properly.", incoming.getString(), incoming.getSiblings().size());
            ChatPatches.LOGGER.error("[ChatHudMixin.addCounter] This could have also been caused by an issue with the new CompactChat dupe-condensing method. Either way,");
            ChatPatches.logInfoReportMessage(e);
        } catch(Exception e) {
            ChatPatches.LOGGER.error("[ChatHudMixin.addCounter] /!\\ Couldn't add duplicate counter because of an unexpected error! /!\\");
            ChatPatches.logInfoReportMessage(e);
        }

        return incoming;
    }
}