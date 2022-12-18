package mechanicalarcane.wmch.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import mechanicalarcane.wmch.config.Config;
import mechanicalarcane.wmch.util.ChatLog;
import mechanicalarcane.wmch.util.Util;
import mechanicalarcane.wmch.util.Util.Flags;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.*;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

import static mechanicalarcane.wmch.WMCH.config;
import static mechanicalarcane.wmch.WMCH.lastMsgData;
import static mechanicalarcane.wmch.util.Util.Flags.*;
import static mechanicalarcane.wmch.util.Util.delAll;

@Environment(EnvType.CLIENT)
@Mixin(value = ChatHud.class, priority = 400)
public abstract class ChatHudMixin {
    @Shadow @Final private MinecraftClient client;

    @Shadow @Final private List<ChatHudLine> messages;
    @Shadow @Final private List<ChatHudLine.Visible> visibleMessages;


    @Shadow public abstract double getChatScale();


    /** Prevents the game from actually clearing chat history */
    @Inject(method = "clear", at = @At("HEAD"), cancellable = true)
    private void clear(boolean clearHistory, CallbackInfo ci) {
        if(!clearHistory) {
            client.getMessageHandler().processAll();
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
    private int increaseMaxMessages(int hundred) {
        return config.maxMsgs;
    }

    /** Prevents messages from modifying when changing chat settings run twice before and after message is added */
    @Inject(
        method = "refresh",
        locals = LocalCapture.CAPTURE_FAILSOFT,
        at = {
            @At(
                value = "INVOKE",
                target = "Lnet/minecraft/client/gui/hud/ChatHud;addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;ILnet/minecraft/client/gui/hud/MessageIndicator;Z)V",
                shift = At.Shift.BEFORE
            ),
            @At(
                value = "INVOKE",
                target = "Lnet/minecraft/client/gui/hud/ChatHud;addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;ILnet/minecraft/client/gui/hud/MessageIndicator;Z)V",
                shift = At.Shift.AFTER
            )
        }
    )
    private void removeSettingDupe(CallbackInfo ci, int i) {
        if(messages.size() == 1) {
            if(RESET_FINISHING.isSet())
                RESET_FINISHING.remove();
            else
                RESET_FINISHING.set();
        } else {
            if(i > 0)
                RESET_NORMAL.set();
            else if(RESET_FINAL.isSet())
                RESET_FINAL.remove();
            else
                RESET_FINISHING.set();
        }
    }


    /**
     * These methods shift various parts of the ChatHud by
     * {@link Config#shiftChat}, including the text, scroll
     * bar, indicator bar, and hover text.
     */
    @ModifyVariable(method = "render", at = @At("STORE"), ordinal = 11) // try multiplying by guiScale for same pixel shift behavior?
    private int moveChatText(int t) {
        return t - (int)Math.floor( (double)Math.abs(config.shiftChat) / this.getChatScale() );
    }
    @ModifyVariable(method = "render", at = @At(value = "STORE", ordinal = 1), ordinal = 9)
    private int moveScrollBar(int q) {
        return q + (int)Math.floor( (double)Math.abs(config.shiftChat) / this.getChatScale() );
    }
    // condensed to one method because the first part of both methods are almost identical
    @ModifyVariable(method = {"getIndicatorAt", "getTextStyleAt"}, argsOnly = true, at = @At("HEAD"), ordinal = 1)
    private double moveIndicatorAndHoverText(double e) {
        // small bug with this, hover text extends to above chat, likely includes indicator text as well
        // maybe check ChatHud#toChatLineY(double)
        return e + ( Math.abs(config.shiftChat) * this.getChatScale() );
    }


    /**
     * Modifies the incoming message by adding timestamps,
     * nicer (vanilla) playername texts, hover events, and
     * in conjunction with {@link #injectCounter(Text, MessageSignatureData, int, MessageIndicator, boolean, CallbackInfo)}, even duplicate counters.
     *
     * <p>Process explained:
     * IF not boundary AND timestamp enabled THEN add the formatted and styled timestamp
     * IF modified name string, not boundary, last message sent by a *valid* player,
     * AND message contains an unformatted name THEN reformat message sender's name
     */
    @ModifyVariable(
        method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;ILnet/minecraft/client/gui/hud/MessageIndicator;Z)V",
        at = @At("HEAD"),
        argsOnly = true
    )
    private Text modifyMessage(Text message) {
        if(Flags.anySet( Flags.LOADING_CHATLOG, RESET_NORMAL, RESET_FINISHING, RESET_FINAL ))
            return message; // cancels modifications if there are any non-INIT flags set, excluding BOUNDARY_LINE

        final Style style = message.getStyle();
        final boolean lastEmpty = lastMsgData.equals(Util.NIL_METADATA);
        Date now = lastEmpty ? new Date() : Date.from(lastMsgData.timestamp());
        boolean boundary = BOUNDARY_LINE.isSet() && config.boundary;


        Text modified =
            Text.empty().setStyle(style)
                .append(
                    !boundary && config.time
                        ? config.getFormattedTime(now).setStyle( config.getHoverStyle(now) )
                        : Text.empty()
                )
                .append(
                    !boundary && !lastEmpty && !config.nameStr.equals("<$>") && Pattern.matches("^<[a-zA-Z0-9_]{3,16}> .+", message.getString())
                        ? Text.empty().setStyle(style)
                            .append( config.getFormattedName(Util.getProfile(client, lastMsgData.sender())) ) // add formatted name
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

    /** Saves sent message history */
    @Inject(method = "addToMessageHistory", at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z"))
    private void saveHistory(String message, CallbackInfo ci) {
        if( !Flags.LOADING_CHATLOG.isSet() )
            ChatLog.addHistory(message);
    }

    /**
     * Adds duplicate/spam counters to consecutive, loosely-equal messages.
     *
     * <p>Process:
     * IF {@code COUNTER} is enabled AND message count >0 AND the message isn't a boundary line, continue.
     * (cache last message, incoming's text siblings and last's text siblings)
     * IF the incoming and last messages are loosely equal AND the 2+4 bit flags aren't set, continue.
     * (save number of duped messages from another counter or current check)
     * Modify the last message to have a dupe counter,
     * Update any existing timestamp to now,
     * Replace old text with incoming text,
     * Remove the most recent chat line and replace it with the newly updated one,
     * Create and then add the renderable-only messages to the chat hud,
     * Exit.
     */
    @Inject(
        method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;ILnet/minecraft/client/gui/hud/MessageIndicator;Z)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void injectCounter(Text m, MessageSignatureData sig, int tick, MessageIndicator indicator, boolean refresh, CallbackInfo ci) {
        // IF counter is enabled AND there are messages AND the message isn't a boundary line THEN continue
        if(config.counter && messages.size() > 0 && !BOUNDARY_LINE.isSet() ) {

            ChatHudLine last = messages.get(0);
            Text text = last.content();
            final List<Text> sibs = m.getSiblings();
            final List<Text> lastSibs = last.content().getSiblings();


            // IF the last and incoming message bodies are equal AND the 4+2 flags aren't set THEN continue
            int OG_MSG = 1;
            if( !RESET_FINISHING.isSet() && sibs.get(OG_MSG).getString() .equalsIgnoreCase( lastSibs.get(OG_MSG).getString()) ) {
                // how many duped messages plus this one
                int DUPE = 2;
                int dupes = (sibs.size() > DUPE
                    ? Integer.parseInt( delAll( sibs.get(DUPE).getString(), "((?:&|§)[0-9a-fk-or])+", "\\D") )
                    : lastSibs.size() > DUPE
                        ? Integer.parseInt( delAll( lastSibs.get(DUPE).getString(), "((?:&|§)[0-9a-fk-or])+", "\\D") )
                        : 1
                ) + 1;


                // modifies the message to have a counter and timestamp
                Util.setOrAdd( text.getSiblings(), DUPE, config.getFormattedCounter(dupes) );

                // IF the last message had a timestamp THEN update it
                // these constants represent the indexes of (customized) message siblings
                int TIME = 0;
                if(lastSibs.get(TIME).getString().length() > 0)
                    text.getSiblings().set(TIME, sibs.get(TIME));
                // Replace the old text with the incoming text
                text.getSiblings().set(OG_MSG, sibs.get(OG_MSG));

                // modifies the actual message to have a counter
                if( !messages.isEmpty() )
                    messages.remove(0);
                messages.add( 0, new ChatHudLine(tick, text, last.headerSignature(), last.indicator()) );

                // modifies the rendered messages to have a counter
                List<OrderedText> visibles = net.minecraft.client.util.ChatMessages.breakRenderedChatMessageLines(
                    text,
                    net.minecraft.util.math.MathHelper.floor( (double)ChatHud.getWidth(client.options.getChatWidth().getValue()) / client.options.getChatScale().getValue() ),
                    client.textRenderer
                );

                Collections.reverse(visibles);

                for(OrderedText ordered : visibles)
                    Util.setOrAdd(
                        visibleMessages,
                        visibles.indexOf(ordered),
                        new ChatHudLine.Visible(
                            tick,
                            ordered,
                            last.indicator(),
                            ((visibles.indexOf(ordered)) == (visibles.size() - 1))
                        )
                    );

                ci.cancel();
            }
        }
    }
}