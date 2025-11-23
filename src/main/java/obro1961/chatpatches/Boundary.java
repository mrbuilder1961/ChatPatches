package obro1961.chatpatches;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import obro1961.chatpatches.util.ChatUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Represents the boundary line between chat messages from different levels,
 * whether client or server worlds. Note that this record does not store the
 * actual boundary line message -- to identify boundary lines, use the
 * {@link #isBoundaryLine(Component)} method, which checks the insertion text
 * for the appropriate ID.
 */
public record Boundary(String levelName, @NotNull Side side) {
	public static final String ID_STRING = ChatPatches.id("boundary_line").toString();
	public static final Boundary UNKNOWN = new Boundary("?", Side.UNKNOWN);


	public static Boundary createFromCurrentLevel() {
		Minecraft mc = Minecraft.getInstance();

		if(mc.hasSingleplayerServer()) {
			return new Boundary(mc.getSingleplayerServer().getWorldData().getLevelName(), Side.CLIENT);
		} else if((Object) mc.getCurrentServer() instanceof ServerData entry) {
			return new Boundary(entry.name.isBlank() ? entry.ip : entry.name, Side.SERVER);
		} else {
			return UNKNOWN; // prevents weird game states (ex. from ReplayMod)
		}
	}

	public Component format(MutableComponent boundaryText) {
		return boundaryText.withStyle(s -> s.withInsertion(this.toString()));
	}

	/**
	 * @since 8.0-alpha.6
	 *
	 * @apiNote Will not work with old boundary lines.
	 */
	public static boolean isBoundaryLine(Component message) {
		String insertion = Objects.requireNonNullElse(
			ChatUtils.getPart(message, ChatUtils.MESSAGE_INDEX), // first checks the message component of the whole message
			message // alternatively checks the root style, although this will probably never work
		).getStyle().getInsertion();

		return insertion != null && insertion.startsWith(ID_STRING);
	}

	// prepub ts is untested and probably doesn't work. but it will be used for copying the world name from boundary lines!
	/*public static Boundary fromInsertion(String insertion) {
		if(!insertion.startsWith(ID_STRING)) {
			return UNKNOWN; // not a boundary line
		}

		// Example insertion: chatpatches:boundary_line[level=MyWorld,side=CLIENT]
		int levelI = insertion.indexOf("[level="); //0
		int sideI = insertion.indexOf(",side="); //14
		int end = insertion.indexOf(']'); //26

		if(levelI == -1 || end == -1 || end <= levelI) {
			return UNKNOWN; // invalid format
		}

		String levelName = insertion.substring(levelI, sideI);//StringUtils.substringBetween(insertion, "level=", ",side=");
		Side side = Side.valueOf(insertion.substring(sideI, end)*//*StringUtils.substringBetween(insertion, "side=", "]")*//*);

		return new Boundary(levelName, side);
	}*/

	@Override
	public @NotNull String toString() {
		return String.format("%s[level=%s,side=%s]", ID_STRING, levelName, side);
	}


	private enum Side {
		CLIENT,
		SERVER,
		UNKNOWN
	}
}