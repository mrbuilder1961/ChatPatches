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
import obro1961.chatpatches.ChatPatches;
import obro1961.chatpatches.chatlog.ChatLog;
import obro1961.chatpatches.config.Config;
import obro1961.chatpatches.util.Util;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

import static obro1961.chatpatches.ChatPatches.config;

@Environment(EnvType.CLIENT)
@Mixin(ChatHud.class)
public abstract class ChatHudMixin extends DrawableHelper {
    @Shadow @Final private MinecraftClient client;

    @Shadow @Final private List<ChatHudLine> messages;
    @Shadow @Final private List<ChatHudLine.Visible> visibleMessages;


    @Shadow public abstract double getChatScale();

    @Shadow public abstract int getWidth();


    /** Prevents the game from actually clearing chat history */
    @Inject(method = "clear", at = @At("HEAD"), cancellable = true)
    private void cps$clear(boolean clearHistory, CallbackInfo ci) {
        if(!clearHistory) {
            client.getMessageHandler().processAll();
            messages.clear();
            visibleMessages.clear();
            // empties the message cache (which on save clears chatlog.json)
            ChatLog.clearMessages();
            ChatLog.clearHistory();
        }

        ci.cancel();
    }

    // uses ModifyExpressionValue to chain with other mods (aka not break)
    @ModifyExpressionValue(
        method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;ILnet/minecraft/client/gui/hud/MessageIndicator;Z)V",
        at = @At(value = "CONSTANT", args = "intValue=100")
    )
    private int cps$moreMessages(int hundred) {
        return config.maxMsgs;
    }

    // allows for a larger chat width (default is 320) up to the window width - padding
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
    @ModifyVariable(method = "render", at = @At("STORE"), ordinal = 11) // try multiplying by guiScale for same pixel shift behavior?
    private int cps$moveChatText(int t) {
        return t - (int)Math.floor( (double)Math.abs(config.shiftChat) / this.getChatScale() );
    }
    @ModifyVariable(method = "render", at = @At(value = "STORE", ordinal = 1), ordinal = 9)
    private int cps$moveScrollBar(int q) {
        return q + (int)Math.floor( (double)Math.abs(config.shiftChat) / this.getChatScale() );
    }
    // condensed to one method because the first part of both methods are almost identical
    @ModifyVariable(method = {"getIndicatorAt", "getTextStyleAt"}, argsOnly = true, at = @At("HEAD"), ordinal = 1)
    private double cps$moveINDAndHoverText(double e) {
        // small bug with this, hover text extends to above chat, likely includes indicator text as well
        // maybe check ChatHud#toChatLineY(double)
        // prob unrelated to this bug, but indicator icons render weird so check that out and send some msgs w/ the icons + text
        return e + ( Math.abs(config.shiftChat) * this.getChatScale() );
    }


    /**
     * Modifies the incoming message by adding timestamps, nicer
     * playernames, hover events, and duplicate counters in conjunction with
     * {@link #cps$addCounter(Text, MessageSignatureData, int, MessageIndicator, boolean, CallbackInfo)}
     *
     * @implNote
     * <li> Extra {@link Text} parameter is required to get access to {@code refreshing},
     * according to the {@link ModifyVariable} docs.
     * <li> Doesn't modify when {@code refreshing} is true, as that signifies
     * re-rendering of chat messages on the hud.
     */
    @ModifyVariable(
        method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;ILnet/minecraft/client/gui/hud/MessageIndicator;Z)V",
        at = @At("HEAD"),
        argsOnly = true
    )
    private Text cps$modifyMessage(Text message, Text m, MessageSignatureData sig, int ticks, MessageIndicator indicator, boolean refreshing) {
        if( Util.Flags.LOADING_CHATLOG.isSet() || refreshing )
            return message; // cancels modifications when loading the chatlog or when regenerating visibles

        final Style style = message.getStyle();
        final boolean lastEmpty = ChatPatches.lastMsgData.equals(Util.NIL_METADATA);
        Date now = lastEmpty ? new Date() : Date.from(ChatPatches.lastMsgData.timestamp());
        boolean boundary = Util.Flags.BOUNDARY_LINE.isSet() && config.boundary;


        Text modified =
            Text.empty().setStyle(style)
                .append(
                    !boundary && config.time
                        ? config.makeTimestamp(now).setStyle( config.makeHoverStyle(now) )
                        : Text.empty()
                )
                .append(
                    !boundary && !lastEmpty && !config.nameFormat.equals("<$>") && Pattern.matches("^<[a-zA-Z0-9_]{3,16}> .+", message.getString())
                        ? Text.empty().setStyle(style)
                        .append( config.formatPlayername(Util.getProfile(client, ChatPatches.lastMsgData.sender())) ) // add formatted name
                        .append( // add first part of message (depending on Text style and whether it was a chat or system)
                            message.getContent() instanceof TranslatableTextContent
                                ? net.minecraft.util.Util.make(() -> { // all message components

                                MutableText text = Text.empty().setStyle(style);
                                List<Text> messages = Arrays.stream( ((TranslatableTextContent) message.getContent()).getArgs() ).map (arg -> (Text)arg ).toList();

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
        if( !Util.Flags.LOADING_CHATLOG.isSet() )
            ChatLog.addHistory(message);
    }

    /**
     * Adds duplicate/spam counters to consecutive, (case-insensitively) equal messages.
     *
     * @implNote
     * <ol>
     * <li> IF {@code COUNTER} is enabled AND message count >0 AND the message isn't a boundary line, continue.
     * <li> (cache last message, incoming's text siblings and last's text siblings)
     * <li> IF not regenerating visible messages AND the incoming and last messages are loosely equal, continue.
     * <li> (save number of duped messages from another counter and current check)
     * <li> Modify the last message to have a dupe counter
     * <li> Update the existing timestamp (if present)
     * <li> Replace old message body with incoming text
     * <li> Break updated message by width into a list of renderable messages
     * <li> Insert them into the hud
     * <li> Cancel to prevent duplicating this message
     * <li> Wraps the entire method in a try-catch to prevent any errors from (effectively) disabling the chat.
     */
    @Inject(
        method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;ILnet/minecraft/client/gui/hud/MessageIndicator;Z)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void cps$addCounter(Text m, MessageSignatureData sig, int ticks, MessageIndicator indicator, boolean refreshing, CallbackInfo ci) {
        try {
            // IF counter is enabled AND not refreshing AND messages >0 AND the message isn't a boundary line, continue
            if(config.counter && !refreshing && !messages.isEmpty() && !Util.Flags.BOUNDARY_LINE.isSet() ) {
                // indexes from (customized) messages
                final int TIME = 0;
                final int OG_MSG = 1;
                final int DUPE = 2;

                ChatHudLine lastHudLine = messages.get(0);
                Text text = lastHudLine.content();
                final List<Text> incSibs = m.getSiblings();
                final List<Text> lastSibs = lastHudLine.content().getSiblings();


                // IF the last and incoming message bodies are equal, continue
                if( incSibs.get(OG_MSG).getString().equalsIgnoreCase( lastSibs.get(OG_MSG).getString()) ) {

                    // how many duped messages plus this one
                    int dupes = (incSibs.size() > DUPE
                        ? Integer.parseInt( Util.delAll( incSibs.get(DUPE).getString(), "(§[0-9a-fk-or])+", "\\D") )
                        : lastSibs.size() > DUPE
                        ? Integer.parseInt( Util.delAll( lastSibs.get(DUPE).getString(), "(§[0-9a-fk-or])+", "\\D") )
                        : 1
                    ) + 1;


                    // modifies the message to have a counter and timestamp
                    Util.setOrAdd( text.getSiblings(), DUPE, config.makeDupeCounter(dupes) );

                    // IF the last message had a timestamp, update it
                    if( !lastSibs.get(TIME).getString().isEmpty() )
                        text.getSiblings().set(TIME, incSibs.get(TIME));

                    // Replace the old text with the incoming text
                    text.getSiblings().set(OG_MSG, incSibs.get(OG_MSG));

                    // modifies the actual message to have a counter
                    messages.set( 0, new ChatHudLine(ticks, text, lastHudLine.headerSignature(), lastHudLine.indicator()) );

                    // modifies the rendered messages to have a counter
                    List<OrderedText> visibles = net.minecraft.client.util.ChatMessages.breakRenderedChatMessageLines(
                        text,
                        net.minecraft.util.math.MathHelper.floor( (double)this.getWidth() / this.getChatScale() ),
                        client.textRenderer
                    );

                    Collections.reverse(visibles);

                    for(OrderedText ordered : visibles)
                        Util.setOrAdd(
                            visibleMessages,
                            visibles.indexOf(ordered),
                            new ChatHudLine.Visible(
                                ticks,
                                ordered,
                                lastHudLine.indicator(),
                                ((visibles.indexOf(ordered)) == (visibles.size() - 1))
                            )
                        );

                    ci.cancel();
                }
            }
        } catch (IndexOutOfBoundsException e) {
            ChatPatches.LOGGER.error("[ChatHudMixin.addCounter] Couldn't add duplicate counter because message '{}' was not constructed properly.", m.getString());
            ChatPatches.LOGGER.error("[ChatHudMixin.addCounter] This is likely caused by a bug or mod incompatibility. Please report this on GitHub!", e);
        }
    }
}
