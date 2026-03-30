package obro1961.chatpatches.util;

//? if <=1.20.1 {
//import com.mojang.datafixers.util.Either;
//import com.mojang.serialization.Codec;
//?}
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.ChatVisiblity;
import obro1961.chatpatches.ChatPatches;
import obro1961.chatpatches.config.Config;
import obro1961.chatpatches.mixin.gui.ChatComponentMixin;

//? if <=1.20.1 {
//import java.util.function.Function;
//?}

/**
 * Contains methods present in some versions but not others - whether because
 * Mojang is dumb or hates mod developers, we'll never know.
 *
 * @author Unless otherwise noted, assume all methods in this class were written
 * by Mojang, not me; I do not claim their code as my own.
 */
public class VersionUtil {
	static Minecraft mc() { return Minecraft.getInstance(); }
	static ChatComponent chat() { return mc().gui.getChat(); }

	//? if >=1.21.11 {
	public static double screenToChatX(double x) {
		return x / chat().getScale() - 4.0;
	}

	/**
	 * Code taken from Minecraft 1.21.10's {@link ChatComponent}{@code #screenToChatY}
	 * method, with code "injected" by Chat Patches. The description of that code is
	 * as follows:
	 *
	 * <p>
	 * Moves the chat line by {@link Config#chatShift} to
	 * correctly shift the chat with the other components.
	 * Used by the {@link ChatComponent} to correctly render
	 * message indicators and chat hover tooltips when
	 * needed in the shifted position.
	 *
	 * <p>Target: {@code double d = this.minecraft.getWindow().getScaledHeight() - y - 40.0;}
	 *
	 * @see Config#calcDynamicChatShift()
	 *
	 * @since Minecraft 1.21.11 gutted {@link ChatComponent} again.
	 *
	 * @implNote For whatever reason, in 1.21.11+, the shift value needs to be
	 * subtracted instead of added to the base y shift - I couldn't tell you
	 * what changed or why though.
	 */
	public static double screenToChatY(double y) {
		double d = (double)mc().getWindow().getGuiScaledHeight() - y - 40.0;
		// -- ChatPatches' code - previously injected via ChatComponentMixin#moveChatLineY --
		d -= ChatPatches.config.calcDynamicChatShift(); // negative instead of positive now...?!
		// -- ChatPatches' code --
		return d / (chat().getScale() * (double)chat().getLineHeight());
	}

	/**
	 * Inlined {@link ChatComponent#getMessageLineIndexAt(double, double)} into
	 * {@link ChatComponent#getMessageEndIndexAt(double, double)}.
	 */
	public static int getMessageEndIndexAt(double chatLineX, double chatLineY) {
		if(!chat().isChatFocused() || mc().options.chatVisibility().get() == ChatVisiblity.HIDDEN) {
			return -1;
		}
		if(chatLineX < -4.0 || chatLineX > (double) Mth.floor((double) chat().getWidth() / chat().getScale())) {
			return -1;
		}

		int i,
			linesOnScreen = Math.min(chat().getLinesPerPage(), chat().trimmedMessages.size());
		if(chatLineY >= 0.0
			&& chatLineY < (double) linesOnScreen
			&& (i = Mth.floor(chatLineY + (double) chat().chatScrollbarPos)) >= 0
			&& i < chat().trimmedMessages.size()
		) {
			//return i;
			// getMessageLine ends ... getMessage begins
			//i = chat().getMessageLineIndexAt(chatLineX, chatLineY);

			while(i >= 0) {
				if(chat().trimmedMessages.get(i).endOfEntry()) {
					return i;
				}
				--i;
			}
		}

		return -1;
	}

	/**
	 * Technically a custom method although it's only uses vanilla methods.
	 *
	 * @see ChatComponentMixin#getEoEIndex(double, double)
	 */
	public static int getEoEIndex(double mX, double mY) {
		return getMessageEndIndexAt(screenToChatX(mX), screenToChatY(mY));
	}
	//?}


	/*? if <=1.20.1 {*/
	/*public static <T> Codec<T> withAlternative(Codec<T> codec, Codec<? extends T> alternative) {
		return Codec.either(codec, alternative).xmap(either -> either.map(Function.identity(), Function.identity()), Either::left);
	}*/
	/*?}*/
}