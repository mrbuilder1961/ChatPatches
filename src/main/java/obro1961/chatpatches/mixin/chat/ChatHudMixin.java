package obro1961.chatpatches.mixin.chat;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
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

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

import static obro1961.chatpatches.ChatPatches.config;
import static obro1961.chatpatches.ChatPatches.lastSender;
import static obro1961.chatpatches.util.Util.delAll;

@Environment(EnvType.CLIENT)
@Mixin(value = ChatHud.class, priority = 400)
public abstract class ChatHudMixin extends DrawableHelper {
    @Shadow @Final private MinecraftClient client;

    @Shadow @Final private List<ChatHudLine<Text>> messages;
    @Shadow @Final private List<ChatHudLine<OrderedText>> visibleMessages;


    @Shadow public abstract double getChatScale();

    @Shadow public abstract int getWidth();


    /** Prevents the game from actually clearing chat history */
    @Inject(method = "clear", at = @At("HEAD"), cancellable = true)
    private void cps$clear(boolean clearHistory, CallbackInfo ci) {
        if(!clearHistory) {
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
        method = "addMessage(Lnet/minecraft/text/Text;IIZ)V",
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
    @ModifyVariable(method = "render", at = @At(value = "STORE", ordinal = 0), index = 25)
    private double cps$moveChatText(double s) {
        return s - Math.floor( (double)config.shiftChat / this.getChatScale() );
    }
    @ModifyVariable(method = "render", at = @At(value = "STORE", ordinal = 0), index = 20)
    private int cps$moveScrollBar(int u) {
        return u + (int)Math.floor( (double)config.shiftChat / this.getChatScale() );
    }
    @ModifyVariable(method = "getText", argsOnly = true, at = @At("HEAD"), ordinal = 1)
    private double cps$moveHoverText(double e) {
        // small bug with this, hover text extends to above chat
        // maybe check ChatHud#toChatLineY(double)
        return e + ( config.shiftChat * this.getChatScale() );
    }

    /**
     * Modifies the incoming message by adding timestamps, nicer
     * playernames, hover events, and duplicate counters in conjunction with
     * {@link #cps$addCounter(Text, int, int, boolean, CallbackInfo)}
     *
     * @implNote
     * <li> Extra {@link Text} parameter is required to get access to {@code refreshing},
     * according to the {@link ModifyVariable} docs.
     * <li> Doesn't modify when {@code refreshing} is true, as that signifies
     * re-rendering of chat messages on the hud.
     */
    @ModifyVariable(method = "addMessage(Lnet/minecraft/text/Text;IIZ)V", at = @At("HEAD"), argsOnly = true)
    private Text cps$modifyMessage(Text m, Text message, int id, int ticks, boolean refreshing) {
        if( Util.Flags.LOADING_CHATLOG.isSet() || refreshing )
            return m; // cancels modifications when loading the chatlog or when regenerating visibles

        final Style style = m.getStyle();
        final boolean lastEmpty = lastSender.equals(Util.NIL_SENDER);
        Date now = new Date();
        boolean boundary = Util.Flags.BOUNDARY_LINE.isSet() && config.boundary;


        Text modified =
            Util.empty(style)
                .append(
                    !boundary && config.time
                        ? config.makeTimestamp(now).setStyle( config.makeHoverStyle(now) )
                        : Util.empty(null)
                )
                .append(
                    !boundary && !lastEmpty && !config.nameFormat.equals("<$>") && Pattern.matches("^<[a-zA-Z0-9_]{3,16}> .+$", m.getString())
                        ? Util.empty(style)
                            .append( config.formatPlayername(lastSender) ) // add formatted name
                            .append( m.getString().replaceFirst("^<[a-zA-Z0-9_]{3,16}> ", "") )
                        : m
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
     * <li> (cache last message, incoming text siblings and last's text siblings)
     * <li> IF not regenerating visible messages AND the incoming and last messages are loosely equal, continue.
     * <li> (save number of duped messages from another counter and current check)
     * <li> Modify the last message to have a dupe counter
     * <li> Update the existing timestamp (if present)
     * <li> Replace old message body with incoming text
     * <li> Break updated message by width into a list of renderable messages
     * <li> Insert them into the hud
     * <li> Cancel to prevent duplicating this message
     */
    @Inject(
        method = "addMessage(Lnet/minecraft/text/Text;IIZ)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void cps$addCounter(Text m, int id, int ticks, boolean refreshing, CallbackInfo ci) {
        // IF counter is enabled AND not refreshing AND messages >0 AND the message isn't a boundary line, continue
        if(config.counter && !refreshing && !messages.isEmpty() && !Util.Flags.BOUNDARY_LINE.isSet() ) {
            // indexes from (customized) messages
            final int TIME = 0;
            final int OG_MSG = 1;
            final int DUPE = 2;

            ChatHudLine<Text> lastHudLine = messages.get(0);
            Text text = lastHudLine.getText();
            final List<Text> incSibs = m.getSiblings();
            final List<Text> lastSibs = lastHudLine.getText().getSiblings();


            // IF the last and incoming message bodies are equal, continue
            if( incSibs.get(OG_MSG).getString().equalsIgnoreCase( lastSibs.get(OG_MSG).getString()) ) {

                // how many duped messages plus this one
                int dupes = (incSibs.size() > DUPE
                    ? Integer.parseInt( delAll( incSibs.get(DUPE).getString(), "(§[0-9a-fk-or])+", "\\D") )
                    : lastSibs.size() > DUPE
                    ? Integer.parseInt( delAll( lastSibs.get(DUPE).getString(), "(§[0-9a-fk-or])+", "\\D") )
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
                messages.set( 0, new ChatHudLine<>(ticks, text, lastHudLine.getId()) );

                // modifies the rendered messages to have a counter
                List<OrderedText> visibles = net.minecraft.client.util.ChatMessages.breakRenderedChatMessageLines(
                    text,
                    net.minecraft.util.math.MathHelper.floor( (double)this.getWidth() / this.getChatScale() ),
                    client.textRenderer
                );

                Collections.reverse(visibles);

                for(OrderedText ordered : visibles)
                    Util.setOrAdd( visibleMessages, visibles.indexOf(ordered), new ChatHudLine<>(ticks, ordered, id) );

                ci.cancel();
            }
        }
    }
}