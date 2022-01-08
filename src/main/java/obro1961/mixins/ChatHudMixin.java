package obro1961.mixins;

import java.util.Date;
import java.util.Deque;
import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.LiteralText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import obro1961.WMCH;
import obro1961.config.Config;

@Environment(EnvType.CLIENT)
@Mixin(value = ChatHud.class, priority = 9999)
public class ChatHudMixin {
    @Shadow @Final List<ChatHudLine<Text>> messages;
    @Shadow @Final List<ChatHudLine<OrderedText>> visibleMessages;
    @Shadow @Final Deque<Text> messageQueue;

    /** Prevents the game from clearing chat history;  */
    @Inject(method = "clear(Z)V", at = @At("HEAD"), cancellable = true)
    public void clear(boolean clearHistory, CallbackInfo ci) { ci.cancel(); }

    /** Increases the amount of maximum chat messages, configurable */
    @ModifyConstant(method = "addMessage(Lnet/minecraft/text/Text;IIZ)V", constant = @Constant(intValue = 100))
    private int moreMsgs(int oldMax) {
        final long gb = 1073741824; final long mem = Runtime.getRuntime().freeMemory();
        return WMCH.config.maxMsgs != Config.MAXMSGS ? WMCH.config.maxMsgs : ( mem<gb*9 ? 2048 : mem<gb*13 ? 4096 : mem<gb*17 ? 8192 : mem>gb*23 ? 16384 : 1024 );
    };

    /** Adds a timestamp, configurable */
    @ModifyVariable(method = "addMessage(Lnet/minecraft/text/Text;IIZ)V", at = @At("HEAD"))
    public Text addTimestamp(Text m) {
        Date now = new Date();
        boolean isBoundary = m.asString() == WMCH.config.boundaryStr && WMCH.config.boundary;

        return new LiteralText("").setStyle(m.getStyle())
        .append( isBoundary||!WMCH.config.time
            ? Text.of("")
            : (Text)new LiteralText(WMCH.config.getFormattedTime(now))
                .setStyle(m.getStyle()
                    .withFormatting(WMCH.config.timeFormatting)
                    .withHoverEvent( WMCH.config.hover ? new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.of(WMCH.config.getFormattedHover(now))) : null )
                    .withClickEvent( WMCH.config.hover ? new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, WMCH.config.getFormattedHover(now) ) : null )
                )
            )
        .append(!isBoundary&&WMCH.config.time ? Text.of("") : new LiteralText(" ").setStyle(m.getStyle()))
        .append(m);
    }
}