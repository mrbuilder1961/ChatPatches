package mechanicalarcane.wmch.mixins;

import static mechanicalarcane.wmch.WMCH.config;
import static mechanicalarcane.wmch.WMCH.msgSender;
import static mechanicalarcane.wmch.config.Option.MAX_MESSAGES;
import static mechanicalarcane.wmch.util.Util.delAll;
import static mechanicalarcane.wmch.util.Util.Flag.RESET_FINAL;
import static mechanicalarcane.wmch.util.Util.Flag.RESET_FINISHING;
import static mechanicalarcane.wmch.util.Util.Flag.RESET_NORMAL;

import java.util.Collections;
import java.util.Date;
import java.util.Deque;
import java.util.List;
import java.util.regex.Pattern;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import mechanicalarcane.wmch.config.Option;
import mechanicalarcane.wmch.util.ChatLog;
import mechanicalarcane.wmch.util.Util;
import mechanicalarcane.wmch.util.Util.Flag;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

@Environment(EnvType.CLIENT)
@Mixin(value = ChatHud.class, priority = 1)
public class ChatHudMixin {
    private final int TIME = 0;
    private final int NAME_MSG = 1;
    private final int DUPE = 2;

    @Shadow @Final MinecraftClient client;
    @Shadow @Final List<String> messageHistory;
    @Shadow @Final List<ChatHudLine<Text>> messages;
    @Shadow @Final List<ChatHudLine<OrderedText>> visibleMessages;
    @Shadow @Final Deque<Text> messageQueue;

    /** Prevents the game from actually clearing chat history */
    @Inject(method = "clear", at = @At("HEAD"), cancellable = true)
    public void clear(boolean delHist, CallbackInfo ci) {
        if(!delHist) {
            visibleMessages.clear();
            messageQueue.clear();
            messages.clear();
            // empties the message cache (which on save clears chatlog.json)
            ChatLog.clearMessages();
        }

        ci.cancel();
    }

    /** Increases the amount of chat messages allowed to be cached */
    @ModifyConstant(method = "addMessage(Lnet/minecraft/text/Text;IIZ)V", constant = @Constant(intValue = 100))
    private int moreMsgs(int old) {
        return MAX_MESSAGES.changed() ? MAX_MESSAGES.get() : MAX_MESSAGES.getDefault();
    };

    /** Prevents messages from modifying when changing chat settings run twice before and after message is added */;
    @Inject(
        method = "reset",
        locals = LocalCapture.CAPTURE_FAILSOFT,
        at = {
            @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/ChatHud;addMessage(Lnet/minecraft/text/Text;IIZ)V", shift = At.Shift.BEFORE),
            @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/ChatHud;addMessage(Lnet/minecraft/text/Text;IIZ)V", shift = At.Shift.AFTER)
        }
    )
    public void reset(CallbackInfo ci, int i) {
        if(messages.size() == 1) {
            if(RESET_FINISHING.isSet())
                RESET_FINISHING.unSet();
            else
                RESET_FINISHING.set();
        } else {
            if(i > 0)
                RESET_NORMAL.set();
            else if(RESET_FINAL.isSet())
                RESET_FINAL.unSet();
            else
                RESET_FINISHING.set();
        }
    }

    /**
     * These next 4 ModifyArg/ModifyVariable injectors
     * all work together to move parts of the chat,
     * including the message texts, background, scroll
     * bar, and hover data.
     *
     * <p> All of these methods together (when enabled by
     * {@link Option#SHIFT_HUD_POS}) shifts the rendered
     * chat messages box up a few pixels as to not
     * interfere with the armor bar.
     */
    @ModifyArg(method = "render", index = 3, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/font/TextRenderer;drawWithShadow(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/text/OrderedText;FFI)I"))
    private float moveChatText(float yPos) {
        return yPos - (Option.SHIFT_HUD_POS.get() ? 10f : 0f);
    }
    @ModifyArgs(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/ChatHud;fill(Lnet/minecraft/client/util/math/MatrixStack;IIIII)V", ordinal = 0))
    private void moveChatBg(Args args) {
        if( !Option.SHIFT_HUD_POS.get() )
            return;

        args.set(2, ((int) args.get(2)) - 10);
        args.set(4, ((int) args.get(4)) - 10);
    }
    @ModifyArg(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/ChatHud;fill(Lnet/minecraft/client/util/math/MatrixStack;IIIII)V", ordinal = 2))
    private MatrixStack moveScrollBar(MatrixStack matrices) {
        if( Option.SHIFT_HUD_POS.get() )
            matrices.translate(0D, -10D, 0D);

        return matrices;
    }
    @ModifyVariable(method = "getTextStyleAt(DD)Lnet/minecraft/text/Style;", argsOnly = true, ordinal = 1, at = @At("HEAD"))
    private double moveHoverData(double x) {
        return x + (Option.SHIFT_HUD_POS.get() ? 10 : 0);
    }

    /**
     * Modifies the incoming message by adding timestamps,
     * nicer (vanilla) playername texts, hover events, and
     * in conjunction with {@link #injectCounter(Text, int,
     * int, boolean, CallbackInfo)}, even duplicate counters.
     *
     * <p>Process explained:
     * IF not boundary AND timestamp enabled THEN add the formatted and styled timestamp
     * IF modified name string, not boundary, last message sent by a *valid* player,
     * AND message contains an unformatted name THEN reformat message sender's name
     */
    @ModifyVariable(method = "addMessage(Lnet/minecraft/text/Text;IIZ)V", at = @At("HEAD"))
    public Text modifyMessage(Text m) {
        if( Flag.INIT.isSet() ? (Flag.flags > 8) : (Flag.flags > 0) )
            return m;

        final String mStr = m.getString(); final Style mStyle = m.getStyle();
        Date now = new Date();
        boolean boundary = Util.isBoundaryLine(mStr) && Option.BOUNDARY.get();


        Text modified = Text.empty().setStyle(mStyle)
            .append(!boundary && Option.TIME.get()
                ? config.getFormattedTime(now) .setStyle( config.getHoverStyle(now) )
                : Text.empty()
            )
            .append( (Option.NAME_STR.changed() && !boundary && (msgSender!=null && !msgSender.equals(Util.NIL_SENDER)) && Pattern.matches("^<[a-zA-Z0-9_]{3,16}> .+", mStr))
                ? Text.empty().setStyle(mStyle)
                    .append( config.getFormattedName(msgSender) )
                    .append( Text.literal( mStr.replaceFirst("^<[a-zA-Z0-9_]{3,16}> ", "") ).setStyle(mStyle) )
                : m
            );


        ChatLog.addMessage(modified, Option.MAX_MESSAGES.get());
        return modified;
    }

    /** Saves sent message history */
    @Inject(method = "addToMessageHistory", at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z"))
    public void saveHistory(String message, CallbackInfo ci) {
        if( !Flag.LOADING_CHATLOG.isSet() )
            ChatLog.addHistory(message, Option.MAX_MESSAGES.get());
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
    @Inject(method = "addMessage(Lnet/minecraft/text/Text;IIZ)V", at = @At("HEAD"), cancellable = true)
    public void injectCounter(Text m, int id, int tick, boolean rfrs, CallbackInfo ci) {
        // IF counter is enabled AND there are messages AND the message isn't a boundary line THEN continue
        if( Option.COUNTER.get() && messages.size() > 0 && !m.getString().equals(Util.formatString(Option.BOUNDARY_STR.get()).getString()) ) {
            ChatHudLine<Text> last = messages.get(0);
            final List<Text> sibs = m.getSiblings();
            final List<Text> lSibs = last.getText().getSiblings();

            // IF the last and incoming message bodies are equal AND the 4+2 flags aren't set THEN continue
            if( !RESET_FINISHING.isSet() && sibs.get(NAME_MSG).getString() .equalsIgnoreCase( lSibs.get(NAME_MSG).getString()) ) {
                // how many duped messages plus this one
                int dupes = (sibs.size() > DUPE
                    ? Integer.valueOf(delAll(sibs.get(DUPE).getString(), "\\D"))
                    : lSibs.size() > DUPE
                        ? Integer.valueOf(delAll(lSibs.get(DUPE).getString(), "\\D"))
                        : 1
                ) + 1;


                // modifies the message to have a counter and timestamp
                Util.setOrAdd( last.getText().getSiblings(), DUPE, config.getFormattedCounter(dupes) );

                // IF the last message had a timestamp THEN update it
                if(lSibs.get(TIME).getString().length() > TIME)
                    last.getText().getSiblings().set(TIME, sibs.get(TIME));
                // Replace the old text with the incoming text
                last.getText().getSiblings().set(NAME_MSG, sibs.get(NAME_MSG));

                // modifies the actual message to have a counter
                if( !messages.isEmpty() )
                    messages.remove(0);
                messages.add(0, new ChatHudLine<Text>(tick, last.getText(), id));


                // modifies the rendered messages to have a counter
                List<OrderedText> visibles = net.minecraft.client.util.ChatMessages.breakRenderedChatMessageLines(
                    last.getText(),
                    net.minecraft.util.math.MathHelper.floor( (double)ChatHud.getWidth(client.options.getChatWidth().getValue()) / client.options.getChatScale().getValue() ),
                    client.textRenderer
                );
                Collections.reverse(visibles);

                for(OrderedText text : visibles)
                    Util.setOrAdd( visibleMessages, visibles.indexOf(text), new ChatHudLine<>(tick, text, id) );

                ci.cancel();
            }
        }
    }
}