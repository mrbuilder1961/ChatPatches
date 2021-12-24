package obro1961.mixins;

import java.util.Calendar;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

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
import net.minecraft.util.Formatting;

@Environment(EnvType.CLIENT)
@Mixin(value = ChatHud.class, priority = 9999)
public class ChatHudMixin {
    @Shadow @Final List<ChatHudLine<Text>> messages;
    @Shadow @Final List<ChatHudLine<OrderedText>> visibleMessages;
    @Shadow @Final Deque<Text> messageQueue;

    /**
     * Prevents the game from clearing chat history
     * @param clearHistory
     * @param ci
     */
    @Inject(method = "clear(Z)V", at = @At("HEAD"), cancellable = true)
    public void clear(boolean clearHistory, CallbackInfo ci) {
        ci.cancel();
    }

    /**
     * Increases the amount of maximum chat messages
     * @param oldMax
     * @returns
     */
    @ModifyConstant(method = "addMessage(Lnet/minecraft/text/Text;IIZ)V", constant = @Constant(intValue = 100))
    private int maxMessages(int oldMax) {
        final long gb = 1073741824; final long mem = Runtime.getRuntime().freeMemory();
        return mem<gb*9 ? 2048 : mem<gb*13 ? 4096 : mem<gb*17 ? 8192 : mem>gb*23 ? 16384 : 1024;
    };
    /**
     * Adds a timestamp, formatted like [hh:mm:ss], light_purple colored; tooltip shows full date
     * @param m Text message
     * @return
     */
    @ModifyVariable(method = "addMessage(Lnet/minecraft/text/Text;IIZ)V", at = @At("HEAD"))
    public Text addTimestamp(Text m) {
        Calendar c = Calendar.getInstance();
        String formatL = String.format("%s %d, %d @ %d:%d:%d.%d\nClick to copy!",
            c.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault()), c.get(Calendar.DATE), c.get(Calendar.YEAR),
            c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), c.get(Calendar.SECOND), c.get(Calendar.MILLISECOND)
        );

        LiteralText msg = (LiteralText) new LiteralText("").setStyle(m.getStyle())
        .append( (Text)new LiteralText(
                String.format( "[%s:%s:%s] ", Integer.toString(c.get(Calendar.HOUR_OF_DAY)),Integer.toString(c.get(Calendar.MINUTE)),Integer.toString(c.get(Calendar.SECOND)) )
                .replaceAll("((?<!\\d)\\d(?!\\d))","0$1") // makes all numbers have 2 digits
            ).setStyle(
                m.getStyle()
                .withColor(Formatting.LIGHT_PURPLE).withItalic(false).withUnderline(false) // just verifying stable text
                .withHoverEvent( new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.of(formatL)) )
                .withClickEvent( new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, String.format("%s|%d",formatL.replaceFirst("\nClick to copy!",""),c.getTime().getTime()) ) )
            )
        )
        .append( m );

        return m.asString().startsWith("<]")
        ? new LiteralText(m.asString().replaceFirst("\\[\\d{2}/\\d{2} \\d{2}:\\d{2}:\\d{2}\\]","")).formatted(Formatting.DARK_AQUA).formatted(Formatting.BOLD)
        : msg;
    }
}