package obro1961.chatpatches;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import obro1961.chatpatches.util.ChatUtil;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

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
		return !fromMessage(message).equals(UNKNOWN);
	}

	public static Boundary fromMessage(Component message) {
		String insertion = ChatUtil.getPart(message, ChatUtil.MESSAGE_INDEX).getStyle().getInsertion();
		if(insertion != null) {
			return fromInsertion(insertion);
		}

		return UNKNOWN;
	}

	// might be a little volatile! keep an eye on this guy
	public static Boundary fromInsertion(String insertion) {
		if(!insertion.startsWith(ID_STRING)) {
			return UNKNOWN; // not a boundary line
		}

		// Example insertion: chatpatches:boundary_line[level=MyWorld,side=CLIENT]
		String opener = "[level=";
		String separator = ",side=";
		String closer = "]";

		if(!insertion.contains(opener) || !insertion.contains(separator) || !insertion.endsWith(closer)) {
			return UNKNOWN; // invalid format
		}

		var levelName = StringUtils.substringBetween(insertion, opener, separator);
		var side = Side.valueOf(StringUtils.substringBetween(insertion, separator, closer));

		return new Boundary(levelName, side);
	}

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