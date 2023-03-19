package obro1961.chatpatches.util;

import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

/**
 * A class containing various string and {@link Text} related utilities.
 */
public class StringTextUtils {

	/** Goes through each regex of {@code regexes} and removes <b>all</b> matches from {@code str}. */
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

	/**
	 * Formats a String with {@code &} formatting codes into a {@link Text}.
	 * Uses the same algorithm as {@link Text#of(String)}.
	 * Doesn't support hex colors.
	 */
	public static MutableText toText(String unformatted) {
		return Text.literal( unformatted.replaceAll("(?i)(?m)(?<!\\\\)&([0-9a-fk-or])", "§$1") );
	}
}
