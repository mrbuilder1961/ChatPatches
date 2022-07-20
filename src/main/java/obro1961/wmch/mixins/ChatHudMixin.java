package obro1961.wmch.mixins;

import static net.minecraft.text.ClickEvent.Action.SUGGEST_COMMAND;
import static net.minecraft.text.HoverEvent.Action.SHOW_ENTITY;
import static net.minecraft.text.HoverEvent.Action.SHOW_TEXT;
import static obro1961.wmch.WMCH.cachedMsgs;
import static obro1961.wmch.WMCH.config;
import static obro1961.wmch.WMCH.flags;
import static obro1961.wmch.WMCH.msgSender;
import static obro1961.wmch.util.Util.delAll;

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
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.entity.EntityType;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.LiteralText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import obro1961.wmch.config.Option;
import obro1961.wmch.util.Util;

@Environment(EnvType.CLIENT)
@Mixin(value = ChatHud.class)
public class ChatHudMixin  {
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
            cachedMsgs.clear();
        }
        ci.cancel();
    }

    /** Increases the amount of maximum chat messages */
    @ModifyConstant(method = "addMessage(Lnet/minecraft/text/Text;IIZ)V", constant = @Constant(intValue = 100))
    private int moreMsgs(int oldMax) {
        final long gb = 1073741824; final long mem = Runtime.getRuntime().freeMemory();
        return Option.MAXMSGS.changed() ? Option.MAXMSGS.get() : ( mem<gb*9 ? 2048 : mem<gb*13 ? 4096 : mem<gb*17 ? 8192 : 1024 );
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
        if(messages.size()-1 == 0) {
            if((flags & 4) == 4)
                flags ^= 4;
            else
                flags |= 4;
        } else {
            if(i > 0)
                flags |= 2;
            else if((flags & 6) == 6)
                flags ^= 6;
            else
                flags |= 4;
        }
    }

    /** Modifies the incoming message in many ways */
    @ModifyVariable(method = "addMessage(Lnet/minecraft/text/Text;IIZ)V", at = @At("HEAD"))
    public Text modifyMessage(Text m) {
        if(flags > 0) return m; // IF something is flagged AND its not the counter correction THEN cancel

        final String mStr = m.getString();
        Date now = new Date();
        boolean boundary = mStr.equals( delAll(Option.BOUNDARYSTR.get(), "(&[0-9a-fA-Fk-orK-OR])+") ) && Option.BOUNDARY.get();
        String name = msgSender.getName();

        /**
         * process explained:
         * IF not boundary AND timestamp enabled THEN add the formatted and styled timestamp
         * IF modified name string, not boundary, last message sent by a player,
         * AND message contains an unformatted name THEN reformat message sender's name
         */
        Text modified = new LiteralText("").setStyle(m.getStyle())
            .append(!boundary && Option.TIME.get()
                ? ((LiteralText)config.getTimeF(now))
                    .setStyle(Style.EMPTY
                        .withHoverEvent( !Option.HOVER.get() ? null : new HoverEvent(SHOW_TEXT, Text.of(config.getHoverF(now))) )
                        .withClickEvent( new ClickEvent(SUGGEST_COMMAND, config.getHoverF(now) ) )
                        .withColor(Option.TIMECOLOR.get())
                    )
                : Text.of("")
                )
            .append( (Option.NAMESTR.changed() && !boundary && msgSender.isComplete() && Pattern.matches("^<[a-zA-Z0-9_]{3,16}> .+", mStr))
                ? new LiteralText("").setStyle(m.getStyle())
                    .append(new LiteralText( config.getNameF(name)+" " ).setStyle( m.getStyle()
                        .withHoverEvent( new HoverEvent(SHOW_ENTITY, new HoverEvent.EntityContent(EntityType.PLAYER, msgSender.getId(), Text.of(name))) )
                        .withClickEvent( new ClickEvent(SUGGEST_COMMAND, "/tell "+name) )
                    )).append( new LiteralText(mStr.replaceFirst("^<[a-zA-Z0-9_]{3,16}> ", "")).setStyle(m.getStyle()) )
                : m
            )
        ;

        // saves this message to the cache if space is available
        if(cachedMsgs.size() < 1024)
            cachedMsgs.add(Text.Serializer.toJsonTree(modified));

        return modified;
    }

    /** Adds dupe counters to applicable messages */
    @Inject(method = "addMessage(Lnet/minecraft/text/Text;IIZ)V", at = @At("HEAD"), cancellable = true)
    public void injectCounter(Text m, int id, int tick, boolean rfrs, CallbackInfo ci) {
        // IF counter is enabled AND there are messages AND the message isn't a boundary line THEN continue
        if( Option.COUNTER.get() && messages.size() > 0 && !m.getString().equals(Util.getStrTextF(Option.BOUNDARYSTR.get()).getString()) ) {
            ChatHudLine<Text> last = messages.get(0);
            final List<Text> sibs = m.getSiblings(); final List<Text> lSibs = last.getText().getSiblings();


            // IF the last and incoming message bodies are equal AND the 4+2 flags aren't set THEN continue
            if((flags & 6) != 6 && sibs.get(1).getString() .equalsIgnoreCase( lSibs.get(1).getString())) {
                // how many duped messages plus this one
                int dupes = (sibs.size() > 2
                    ? Integer.valueOf( delAll(sibs.get(2).getString(), "\\D") )
                    : lSibs.size() > 2
                        ? Integer.valueOf( delAll(lSibs.get(2).getString(), "\\D") )
                        : 1)
                + 1;

                // modifies the message to have a counter and timestamp
                if(lSibs.size() > 2)
                    last.getText().getSiblings().set(2, config.getDupeF(dupes));
                else
                    last.getText().getSiblings().add(2, config.getDupeF(dupes));

                // IF the last message had a timestamp THEN update it
                if(lSibs.get(0).getString().length() > 0)
                    last.getText().getSiblings().set(0, sibs.get(0));
                // IF incoming text case is different from the last THEN update it
                if( !sibs.get(1).getString() .equals( lSibs.get(1).getString()) )
                    last.getText().getSiblings().set(1, sibs.get(1));

                // modifies the actual message to have a counter
                if(messages.size() > 0)
                    messages.remove(0);
                messages.add(0, new ChatHudLine<Text>(tick, last.getText(), id));


                // modifies the rendered messages to have a counter
                List<OrderedText> visibles = net.minecraft.client.util.ChatMessages.breakRenderedChatMessageLines(
                    last.getText(),
                    net.minecraft.util.math.MathHelper.floor((double)ChatHud.getWidth(client.options.chatWidth) / client.options.chatScale),
                    client.textRenderer
                ); Collections.reverse(visibles);

                for(OrderedText text : visibles) {
                    if(visibleMessages.size() > visibles.indexOf(text)) visibleMessages.set( visibles.indexOf(text), new ChatHudLine<>(tick, text, id) );
                    else visibleMessages.add( visibles.indexOf(text), new ChatHudLine<>(tick, text, id) );
                }

                ci.cancel();
            }
        }
    }
}