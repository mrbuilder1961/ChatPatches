package obro1961.chatpatches.mixin.chat;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.*;
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
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

import static obro1961.chatpatches.ChatPatches.config;
import static obro1961.chatpatches.ChatPatches.lastMsg;
import static obro1961.chatpatches.util.ChatUtils.OG_MSG_INDEX;

/**
 * The main entrypoint mixin for most chat modifications.
 * Implements {@link ChatHudAccessor} to widen access to
 * extra fields and methods used elsewhere.
 */
@Environment(EnvType.CLIENT)
@Mixin(ChatHud.class)
public abstract class ChatHudMixin extends DrawableHelper implements ChatHudAccessor {
    @Shadow @Final private MinecraftClient client;
    @Shadow @Final private List<ChatHudLine> messages;
    @Shadow @Final private List<ChatHudLine.Visible> visibleMessages;

    @Shadow private int scrolledLines;


    // shadowed methods for normal (private-local) use
    @Shadow public abstract double getChatScale();
    // shadowed methods just for the below bridge methods (mainly for ChatScreenMixin)
    @Shadow protected abstract int getMessageLineIndex(double x, double y);
    @Shadow protected abstract double toChatLineX(double x);
    @Shadow protected abstract double toChatLineY(double y);
    @Shadow protected abstract int getLineHeight();

    @Shadow public abstract int getVisibleLineCount();

    @Shadow protected abstract void addMessage(Text message, @Nullable MessageSignatureData signature, int ticks, @Nullable MessageIndicator indicator, boolean refresh);

    // ChatHudAccessor methods used outside this mixin
    public List<ChatHudLine> getMessages() { return messages; }
    public List<ChatHudLine.Visible> getVisibleMessages() { return visibleMessages; }
    public int getScrolledLines() { return scrolledLines; }
    // these use underscores to avoid name conflicts with shadowed methods
    // the function of these methods are exactly their shadowed counterparts
    public int _getMessageLineIndex(double x, double y) { return getMessageLineIndex(x, y); }
    public double _toChatLineX(double x) { return toChatLineX(x); }
    public double _toChatLineY(double y) { return toChatLineY(y); }
    public int _getLineHeight() { return getLineHeight(); }


    /** Prevents the game from actually clearing chat history */
    @Inject(method = "clear", at = @At("HEAD"), cancellable = true)
    private void cps$clear(boolean clearHistory, CallbackInfo ci) {
        if(!config.vanillaClearing) {
            if(!clearHistory) {
                client.getMessageHandler().processAll();
                // removalQueue.clear(); // don't feel like using an access widener for whatever this does
                messages.clear();
                visibleMessages.clear();
                // empties the message cache (which on save clears chatlog.json)
                ChatLog.clearMessages();
                ChatLog.clearHistory();
            }

            ci.cancel();
        }
    }

    // uses ModifyExpressionValue to chain with other mods (aka not break)
    @ModifyExpressionValue(
        method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;ILnet/minecraft/client/gui/hud/MessageIndicator;Z)V",
        at = @At(value = "CONSTANT", args = "intValue=100")
    )
    private int cps$moreMessages(int hundred) {
        return config.chatMaxMessages;
    }

    /** allows for a chat width larger than 320px */
    @ModifyReturnValue(method = "getWidth()I", at = @At("RETURN"))
    private int cps$moreWidth(int defaultWidth) {
        return config.chatWidth > 0 ? config.chatWidth : defaultWidth;
    }

    /**
     * These methods shift various parts of the ChatHud by
     * {@link Config#shiftChat}, including the text, scroll
     * bar, indicator bar, and hover text.
     * They all shift the y value, with the name of the parameter
     * corresponding to the (yarn mapped) target variable name.
     */
    @ModifyVariable(method = "render", at = @At(value = "STORE", ordinal = 0), index = 31) // STORE ordinal=0 to not target all x stores
    private int cps$moveChatText(int x) {
        return x - MathHelper.floor(config.shiftChat / this.getChatScale());
    }
    @ModifyVariable(method = "render", at = @At(value = "STORE", ordinal = 0), index = 27)
    private int cps$moveScrollBar(int af) {
        return af + MathHelper.floor(config.shiftChat / this.getChatScale());
    }
    // condensed to one method because the first part of both methods are practically identical
    @ModifyVariable(method = {"getIndicatorAt", "getTextStyleAt"}, argsOnly = true, at = @At("HEAD"), ordinal = 1)
    private double cps$moveINDHoverText(double e) {
        // small bug with this, hover text extends to above chat, likely includes indicator text as well
        // maybe check ChatHud#toChatLineY(double)
        // prob unrelated to this bug, but indicator icons render weird so check that out and send some msgs w/ the icons + text
        return e + ( config.shiftChat * this.getChatScale() );
    }

    /**
     * Modifies the incoming message by adding timestamps, nicer
     * playernames, hover events, and duplicate counters in conjunction with
     * {@link #cps$addCounter(Text, MessageSignatureData, int, MessageIndicator, boolean, CallbackInfo)}
     *
     * @implNote
     * <li>Extra {@link Text} parameter is required to get access to
     * {@code refreshing}, according to the {@link ModifyVariable} docs.</li>
     * <li>Doesn't modify when {@code refreshing} is true, as that signifies
     * re-rendering of chat messages on the hud.</li>
     * <li>This method causes all messages passed to it to be formatted in
     * a new structure for clear data access. This is done by mostly using
     * {@link MutableText#append(Text)}, which deliberately puts message
     * components at specific indices, of which all should be laid out in
     * {@link ChatUtils}.</li>
     */
    @ModifyVariable(
        method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;ILnet/minecraft/client/gui/hud/MessageIndicator;Z)V",
        at = @At("HEAD"),
        argsOnly = true
    )
    private Text cps$modifyMessage(Text message, Text m, MessageSignatureData sig, int ticks, MessageIndicator indicator, boolean refreshing) {
        if( refreshing || Flags.LOADING_CHATLOG.isRaised() || Flags.ADDING_CONDENSED_MESSAGE.isRaised() )
            return message; // cancels modifications when loading the chatlog, regenerating visibles, or sending a boundary message

        final Style style = message.getStyle();
        boolean lastEmpty = lastMsg.equals(ChatUtils.NIL_MESSAGE);
        boolean boundary = Flags.BOUNDARY_LINE.isRaised() && config.boundary && !config.vanillaClearing;
        Date now = lastEmpty ? new Date() : Date.from(lastMsg.timestamp());
        String nowTime = String.valueOf( now.getTime() ); // for copy menu and storing timestamp data! only affects the timestamp


        Text modified =
            Text.empty().setStyle(style)
                .append(
                    config.time && !boundary
                        ? config.makeTimestamp(now).setStyle( config.makeHoverStyle(now).withInsertion(nowTime) )
                        : Text.empty().setStyle( Style.EMPTY.withInsertion(nowTime) )
                )
                .append(
                    !lastEmpty && !boundary && Pattern.matches("^<[a-zA-Z0-9_]{3,16}> .+", message.getString())
                        ? Text.empty().setStyle(style)
                            .append( config.formatPlayername( lastMsg.sender() ) ) // add formatted name
                            .append( // add first part of message (depending on Text style and whether it was a chat or system)
                                message.getContent() instanceof TranslatableTextContent ttc
                                    ? net.minecraft.util.Util.make(() -> { // all message components

                                        MutableText text = Text.empty().setStyle(style);
                                        List<Text> messages = Arrays.stream( ttc.getArgs() ).map( arg -> (Text)arg ).toList();

                                        for(int i = 1; i < messages.size(); ++i)
                                            text.append( messages.get(i) );

                                        return text;
                                    })
                                    : Text.literal( ((LiteralTextContent) message.getContent()).string().split("> ")[1] ).setStyle(style) // default-style message with name
                            )
                            .append( // add any siblings (Texts with different styles)
                                net.minecraft.util.Util.make(() -> {

                                    MutableText msg = Text.empty().setStyle(style);

                                    message.getSiblings().forEach(msg::append);

                                    return msg;
                                })
                            )
                        : message
                );


        ChatLog.addMessage(modified);
        return modified;
    }

    @Inject(method = "addToMessageHistory", at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z"))
    private void cps$addHistory(String message, CallbackInfo ci) {
        if( !Flags.LOADING_CHATLOG.isRaised() )
            ChatLog.addHistory(message);
    }

    @Inject(method = "logChatMessage", at = @At("HEAD"), cancellable = true)
    private void cps$dontLogRestoredMessages(Text message, @Nullable MessageIndicator indicator, CallbackInfo ci) {
        if( Flags.LOADING_CHATLOG.isRaised() && indicator != null )
            ci.cancel();
    }

    /**
     * Adds a counter to the chat message, indicating how many times the same
     * message has been sent. Can check only the last message, or
     * {@link Config#counterCompactDistance} times back. Slightly more
     * efficient than the previous method, outlined here: [{@link ChatUtils#getCondensedMessage(Text, int)}]
     * but it still is quite slow.
     *
     * @implNote
     * <ol>
     *     <li>IF {@code COUNTER} is enabled AND message count >0 AND the message isn't a boundary line AND it's not adding an already condensed message, continue.</li>
     *     <li>Cache the result of trying to condense the incoming message with the last message received.</li>
     *     <li>IF the counter should use the CompactChat method and the message wasn't already condensed:</li>
     *     <ol>
     *         <li>Calculate the adjusted distance to attempt comparing, depending on the amount of messages already in the chat.</li>
     *         <li>Filter all the messages within the target range that are case-insensitively equal to the incoming message.</li>
     *         <li>If a message was the same, call {@link ChatUtils#getCondensedMessage(Text, int)},
     *         which ultimately removes that message and its visibles.</li>
     *     </ol>
     *     <li>IF any messages were condensed:</li>
     *     <ol>
     *         <li>Raise {@link Flags#ADDING_CONDENSED_MESSAGE}.</li>
     *         <li>Call the method this injector is injecting into (without running this method because of the flag).</li>
     *         <li>Lower {@link Flags#ADDING_CONDENSED_MESSAGE}.</li>
     *         <li>Cancel to prevent duplicating this message.</li>
     *     </ol>
     *     <li>Wraps the entire method in a try-catch to prevent any errors from (effectively) disabling the chat.</li>
     * </ol>
     *
     * @apiNote This injector is pretty ugly and could definitely be cleaner and more concise, but I'm going to deal with it
     * in the future when I API-ify the rest of the mod. When that happens, this flag-add-flag-cancel method will be replaced
     * with a simple (enormous) method call alongside
     * {@link #cps$modifyMessage(Text, Text, MessageSignatureData, int, MessageIndicator, boolean)} in a @{@link ModifyVariable}
     * handler.
     */
    @Inject(
        method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;ILnet/minecraft/client/gui/hud/MessageIndicator;Z)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void cps$addCounter(Text incoming, MessageSignatureData msd, int ticks, MessageIndicator mi, boolean refreshing, CallbackInfo ci) {
        try {
            if( config.counter && !refreshing && !messages.isEmpty() && !Flags.ADDING_CONDENSED_MESSAGE.isRaised() && (!Flags.BOUNDARY_LINE.isRaised() && config.boundary && !config.vanillaClearing) ) {
                // condenses the incoming message into the last message if it is the same
                Text condensedLastMessage = ChatUtils.getCondensedMessage(incoming, 0);

                // if the counterCompact option is true but the last message received was not condensed, look for
                // any dupes in the last counterCompactDistance +1 messages and if any are found condense them
                if( config.counterCompact && condensedLastMessage.equals(incoming) ) {
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
                        .filter( hudLine -> hudLine.content().getSiblings().get(OG_MSG_INDEX).getString().equalsIgnoreCase( incoming.getSiblings().get(OG_MSG_INDEX).getString() ) )
                        .findFirst()
                        .ifPresent( hudLine -> ChatUtils.getCondensedMessage(incoming, messages.indexOf(hudLine)) );
                }

                // if any message was condensed add it
                if( !condensedLastMessage.equals(incoming) || (config.counterCompact && condensedLastMessage.equals(incoming)) ) {
                    Flags.ADDING_CONDENSED_MESSAGE.raise();
                    addMessage( condensedLastMessage, msd, ticks, mi, false );
                    Flags.ADDING_CONDENSED_MESSAGE.lower();

                    ci.cancel();
                }
            }

        } catch(IndexOutOfBoundsException e) {
            ChatPatches.LOGGER.error("[ChatHudMixin.addCounter] Couldn't add duplicate counter because message '{}' ({} parts) was not constructed properly.", incoming.getString(), incoming.getSiblings().size());
            ChatPatches.LOGGER.error("[ChatHudMixin.addCounter] This could have also been caused by an issue with the new CompactChat dupe-condensing method.");
            ChatPatches.LOGGER.error("[ChatHudMixin.addCounter] Either way, this was caused by a bug or mod incompatibility. Please report this on GitHub or on the Discord!", e);
        } catch(Exception e) {
            ChatPatches.LOGGER.error("[ChatHudMixin.addCounter] /!\\ Couldn't add duplicate counter because of an unexpected error. Please report this on GitHub or on the Discord! /!\\", e);
        }
    }
}