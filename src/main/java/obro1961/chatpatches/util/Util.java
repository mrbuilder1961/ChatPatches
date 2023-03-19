package obro1961.chatpatches.util;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import obro1961.chatpatches.mixinesq.ChatHudAccessor;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/** Random functions that are of some random, obsolete use */
public class Util {
	public static final UUID NIL_UUID = new UUID(0, 0);
	public static final SignedMessage NIL_MESSAGE = SignedMessage.ofUnsigned("");


	/** These are used to fix bugs with messages modifying when unnecessary. */
	public enum Flags {
		INIT(0b1000),
		LOADING_CHATLOG(0b0001),
		BOUNDARY_LINE(0b0010);

		public static int flags = INIT.value;
		public final int value;

		Flags(int value) {
			this.value = value;
		}

		public static String binary() { return Integer.toBinaryString(flags); }

		//public static boolean allSet(Flags... flags) { return Arrays.stream(flags).allMatch(Flags::isSet); }
		//public static boolean anySet(Flags... flags) { return Arrays.stream(flags).anyMatch(Flags::isSet); }


		public void set() { flags |= value; } // set this flag
		public void toggle() { flags ^= value; } // invert this flag
		public void remove() { if( isSet() ) toggle(); } // remove this flag
		public boolean isSet() { return (flags & value) == value; } // true if flags has these bit(s) set
		//public boolean otherFlagsSet() { return (isSet() ? (flags ^ value) : flags) > 0; } // true if this flag is not the *sole* flag set
	}


	/**
	 * Takes a {@code UUID} and
	 * {@code NetworkHandler} and searches
	 * for a player with the id provided,
	 * otherwise returns {@code NIL_PROFILE}.
	 */
	public static GameProfile getProfile(@NotNull MinecraftClient client, UUID id) {
		List<PlayerListEntry> players = new ArrayList<>( client.getNetworkHandler() != null ? client.getNetworkHandler().getPlayerList() : Collections.emptyList() );

		for(PlayerListEntry player : players)
			if(player.getProfile().getId().equals(id))
				return player.getProfile();

		return new GameProfile(NIL_UUID, "");
	}

	/** A shorthand method for accessing methods from {@code client}'s {@code net.minecraft.client.gui.hud.ChatHud} object. */
	public static ChatHudAccessor chatHud(@NotNull MinecraftClient client) {
		return ((ChatHudAccessor) client.inGameHud.getChatHud());
	}


	/**
	 * If there's space to overwrite, runs {@code list.set(index, object)}. Otherwise runs {@code list.add(index, object)}.
	 */
	public static <T> void setOrAdd(List<T> list, final int index, T object) {
		if(list.size() > index)
			list.set(index, object);
		else
			list.add(index, object);

	}

	/**
	 * Takes an {@code Iterable} and a {@code Predicate}, and tests the
	 * {@code Predicate} on each item. {@return Returns a list of the items that
	 * passed the test, or an empty list if none passed.}
	 * @param iterable The Iterable to search in.
	 * @param test Predicate to find the item you're looking for.
	 */
	/*public static <T> List<T> find(Iterable<T> iterable, Predicate<T> test) {
		List<T> matches = Lists.newArrayList();

		for(T item : iterable)
			if(test.test(item))
				matches.add(item);

		return matches;
	}*/


	/**
	 * Returns the current ClientWorld's name. For singleplayer,
	 * returns the level name. For multiplayer, returns the
	 * server entry name. Falls back to the IP if it was a direct
	 * connect. Leads with "C_" or "S_" depending on the
	 * source of the ClientWorld.
	 * @param client A non-null MinecraftClient that must be in-game.
	 * @return A 2 item list consisting of the world name and whether
	 * it was a ClientWorld or not.
	 */
	@SuppressWarnings("DataFlowIssue")
	public static String currentWorldName(@NotNull MinecraftClient client) {
		Objects.requireNonNull(client, "MinecraftClient must exist to access client data:");
		String entryName;

		return client.isIntegratedServerRunning()
			? "C_" + client.getServer().getSaveProperties().getLevelName()
			: (entryName = client.getCurrentServerEntry().name) == null || entryName.isBlank() // check if null/empty then use IP
				? "S_" + client.getCurrentServerEntry().address
				: "S_" + client.getCurrentServerEntry().name
		;
	}


	public static String delAll(String str, String... regexes) {
		for(String regex : regexes)
			str = str.replaceAll(regex, "");

		return str;
	}

	/**
	 * Replaces all {@code $} characters in {@code str} with {@code variable}.
	 * Also replaces intended newline characters with {@code \n} to fix #36.
	 */
	public static String fillVars(String str, String variable) {
		return str.replaceAll("\\$", variable).replaceAll("\\\\n", "\n");
	}

	/** Removes all intended (ampersand or section sign) + formatting code sequences from {@code formatted}. */
	public static String strip(String formatted) {
		return delAll(formatted, "(?i)(?<!\\\\)([&§][0-9a-fk-or])+");
	}

	/**
	 * Turns a String formatted like {@code "&4Dark RED &l&33AAffCUSTOM BOLD BLUE"}
	 * into a Text with all {@code &<?>} codes replaced with styled colors.
	 * If there are no formatting characters, returns the unstyled string.
	 * Doesn't support hex colors.
	 *
	 * @param plain String to format
	 *
	 * @apiNote Apparently if you just force-use the '§' character in a text,
	 * it formats according to that. For now this seems to work fine, so goodbye
	 * horrendously made string-text splicer!
	 */
	public static MutableText formatString(String plain) {
		return Text.literal( plain.replaceAll("(?i)(?m)(?<!\\\\)&([0-9a-fk-or])", "§$1") );
	}
}
