package obro1961.wmch.mixins;

import static net.minecraft.text.ClickEvent.Action.SUGGEST_COMMAND;
import static net.minecraft.text.HoverEvent.Action.SHOW_ENTITY;
import static net.minecraft.text.HoverEvent.Action.SHOW_TEXT;

import java.util.Date;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.EntityType;
import net.minecraft.network.MessageType;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.LiteralText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import obro1961.wmch.Util;
import obro1961.wmch.WMCH;
import obro1961.wmch.config.Config;
import obro1961.wmch.config.Option;
@Environment(EnvType.CLIENT)
@Mixin(value = ChatHud.class)
public class ChatHudMixin  {
    @Shadow @Final MinecraftClient client;
    @Shadow @Final List<String> messageHistory;
    @Shadow @Final List<ChatHudLine<Text>> messages;
    @Shadow @Final List<ChatHudLine<OrderedText>> visibleMessages;
    @Shadow @Final Deque<Text> messageQueue;
    private OrderedText hovered = Text.of("").asOrderedText();

    /** Prevents the game from actually clearing chat history */
    @Inject(method = "clear", at = @At("HEAD"), cancellable = true)
    public void clear(boolean delHist, CallbackInfo ci) {
        if(!delHist) {
            visibleMessages.clear();
            messageQueue.clear();
            messages.clear();
        }
        ci.cancel();
    }

    /** Increases the amount of maximum chat messages */
    @ModifyConstant(method = "addMessage(Lnet/minecraft/text/Text;IIZ)V", constant = @Constant(intValue = 100))
    private int moreMsgs(int oldMax) {
        final long gb = 1073741824; final long mem = Runtime.getRuntime().freeMemory();
        return Option.MAXMSGS.changed() ? Option.MAXMSGS.get() : ( mem<gb*9 ? 2048 : mem<gb*13 ? 4096 : mem<gb*17 ? 8192 : 1024 );
    };

    /** Modifies the incoming message in many ways */
    @ModifyVariable(method = "addMessage(Lnet/minecraft/text/Text;IIZ)V", at = @At("HEAD"))
    public Text modifyMessage(Text m) {
        Config c = WMCH.config; Date now = new Date(); final String mStr = m.getString();
        boolean boundary = mStr.equals( Util.delAll(Option.BOUNDARYSTR.get(), "(&[0-9a-fA-Fk-orK-OR])+") ) && Option.BOUNDARY.get();
        //String ip = null; //boolean specialText = og==new TranslatableText("commands.publish.started").getString();

        // gets message sender
        String name = WMCH.msgSender.getName();
        UUID pID = WMCH.msgSender.getId();

        /**
         * process explained:
         * IF not boundary AND timestamp enabled THEN add the formatted and styled timestamp
         * IF modified name string, not boundary, last message was chat, not debug, and can be formatted THEN reformat message sender's name
         */
        return new LiteralText("").setStyle(m.getStyle())
            .append(!boundary && Option.TIME.get()
                ? ((LiteralText)c.getTimeF(now))
                    .setStyle(Style.EMPTY
                        .withHoverEvent( !Option.HOVER.get() ? null : new HoverEvent(SHOW_TEXT, Text.of(c.getHoverF(now))) )
                        .withClickEvent( new ClickEvent(SUGGEST_COMMAND, c.getHoverF(now) ) )
                        .withColor(Option.TIMECOLOR.get())
                    )
                : Text.of("")
                )
            .append( (Option.NAMESTR.changed() && !boundary && !mStr.startsWith("[Debug]") && Pattern.matches("^<[a-zA-Z0-9_]{3,16}> .+", mStr))
                ? new LiteralText("").setStyle(m.getStyle())
                    .append(new LiteralText( c.getNameF(name)+" " ).setStyle( m.getStyle()
                        .withHoverEvent( new HoverEvent(SHOW_ENTITY, new HoverEvent.EntityContent(EntityType.PLAYER, pID, Text.of(name))) )
                        .withClickEvent( new ClickEvent(SUGGEST_COMMAND, "/tell "+name) )
                    )).append( new LiteralText(Util.delOne(mStr, "^<[a-zA-Z0-9_]{3,16}> ")).setStyle(m.getStyle()) )
                : m
            )
        ;

        // unimplemented "smart copy" feature
        /* og.replaceFirst("\\d{5}$","%s")==(new TranslatableText("commands.publish.started")).getString()
        new LiteralText( og.replaceFirst("\\d{5}$","") ).setStyle(m.getStyle())
        .append(new LiteralText( og.replaceFirst("^\\D+","") ).setStyle( Style.EMPTY.withColor(c.timeColor)
        .withClickEvent( new ClickEvent(COPY_TO_CLIPBOARD, ip+":"+og.replaceFirst("^\\D+","")) )
        )) */
    }

    /** Allows copying messages */
    @ModifyArg(method = "render", at = @At(
        value = "INVOKE",
        target = "Lnet/minecraft/client/font/TextRenderer;drawWithShadow(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/text/OrderedText;FFI)I"
    ))
    public OrderedText transform(OrderedText old) {
        // if hovering and ctrl+c then copy
        if(hovered.equals(old) && Screen.hasControlDown() && InputUtil.isKeyPressed(client.getWindow().getHandle(), GLFW.GLFW_KEY_C)) {
            String raw = Util.asString(old);
            client.keyboard.setClipboard(raw);
            client.inGameHud.addChatMessage(
                MessageType.GAME_INFO,
                new LiteralText( "'%s' copied!".formatted(raw.strip()) ).formatted(Formatting.GREEN),
                net.minecraft.util.Util.NIL_UUID
            );
        }

        return old;
    }
    @Inject(
        at = @At(value="INVOKE", target="Lnet/minecraft/client/font/TextHandler;getStyleAt(Lnet/minecraft/text/OrderedText;I)Lnet/minecraft/text/Style;"),
        method = "getText",
        locals = LocalCapture.CAPTURE_FAILSOFT
    )
    private void grabText(double x,double y,CallbackInfoReturnable<Style> ci,double d,double e,int i,int j,ChatHudLine<OrderedText> chatHudLine) {
        hovered = chatHudLine.getText();
    }

    /** Decides which messages need dupe counters and which don't */
    @Inject(method = "addMessage(Lnet/minecraft/text/Text;IIZ)V", at = @At("HEAD"), cancellable = true)
    public void injectCounter(Text m, int time, int id, boolean rfrs, CallbackInfo ci) {
        //* index 0 is the message before this one (current message hasnt been added yet)
        if( Option.COUNTER.get() && !m.getString().equals( Util.delAll(Option.BOUNDARYSTR.get(), "(?:&[0-9a-fA-Fk-orK-OR])+") ) && messages.size() > 0 ) {
            ChatHudLine<Text> last = messages.get(0);
            final List<Text> sibs = m.getSiblings(); final List<Text> lSibs = last.getText().getSiblings();

            Short dupes = sibs.size() > 2
                ? Short.valueOf( Util.delAll(sibs.get(2).getString(), "\\D") )
                : lSibs.size() > 2
                    ? Short.valueOf( Util.delAll(lSibs.get(2).getString(), "\\D") )
                    : 1
            ;

            // if the current or last message have a counter or the messages are equal, continue
            if( dupes > 1 || Option.LENIANTEQUALS.get()
                ? sibs.get(1).getString().equalsIgnoreCase(lSibs.get(1).getString())
                : sibs.get(1).getString().equals(lSibs.get(1).getString())
            ) {
                ++dupes;
                // adds the updated counter and timestamp
                if(lSibs.size() > 2) last.getText().getSiblings().set(2, WMCH.config.getDupeF(dupes));
                else last.getText().getSiblings().add(2, WMCH.config.getDupeF(dupes));
                if(lSibs.get(0).getString().length() > 0) last.getText().getSiblings().set(0, sibs.get(0));
                // modifies the message to have a counter
                messages.set(0, new ChatHudLine<Text>(last.getCreationTick(), last.getText(), last.getId()));

                if(messages.size() > 0) visibleMessages.remove(0);
                net.minecraft.client.util.ChatMessages.breakRenderedChatMessageLines(
                    last.getText(),
                    net.minecraft.util.math.MathHelper.floor((double)ChatHud.getWidth(client.options.chatWidth) / client.options.chatScale),
                    client.textRenderer
                ).forEach(vt -> visibleMessages.add(0, new ChatHudLine<>(last.getCreationTick(), vt, last.getId())));

                ci.cancel(); //? might screw up logs or something, maybe add a warning that says a message was removed for duping
            }
        }
    }
}