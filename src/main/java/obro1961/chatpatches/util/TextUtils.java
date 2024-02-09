package obro1961.chatpatches.util;

import com.google.common.collect.Lists;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A class containing various string and {@link Text} related utilities.
 */
public class TextUtils {
	public static final String AMPERSAND_REGEX = "(?im)&([0-9a-fk-or])";
	public static final String NO_BACKSLASH_AMPERSAND_REGEX = "(?im)(?<!\\\\)&([0-9a-fk-or])";


	/**
	 * Replaces all {@code $} characters in {@code str} with {@code variable}.
	 * Also replaces intended newline characters with {@code \n} to fix #36.
	 */
	public static String fillVars(String str, String variable) {
		return str.replaceAll("\\$", variable).replaceAll("\\\\n", "\n");
	}

	/**
	 * Returns a list of web URL links captured from {@code str}.
	 * Returns an empty list if none are found.
	 */
	public static List<String> getLinks(String str) {
		// slightly modified from https://stackoverflow.com/a/163398 to not include file links
		final String urlRegex = "\\bhttps?://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]";
		final Matcher matcher = Pattern.compile(urlRegex).matcher(str);
		List<String> urls = Lists.newArrayList();

		while(matcher.find())
			urls.add(matcher.group());

		return urls;
	}

	/**
	 * Creates a new MutableText object with explicit
	 * sibling and style data specified. Behaves
	 * effectively the same as the private constructor
	 * {@link MutableText#MutableText(TextContent, List, Style)}.
	 */
	public static MutableText newText(TextContent content, List<Text> siblings, Style style) {
		MutableText text = MutableText.of(content).setStyle(style);
		siblings.forEach(text::append);
		return text;
	}

	/**
	 * Returns a copy of {@code text} with an empty content.
	 * Useful for comparing {@link Text} objects'
	 * metadata (style and siblings) only.
	 * */
	public static MutableText copyWithoutContent(Text text) {
		return newText(TextContent.EMPTY, text.getSiblings(), text.getStyle());
	}

	/**
	 * Formats a String with {@code &} formatting codes into a {@link Text}.
	 * Uses the same base algorithm as {@link Text#of(String)}.
	 * Doesn't support hex colors.
	 */
	public static MutableText text(String unformatted) {
		return Text.literal(
			unformatted
				.replaceAll(NO_BACKSLASH_AMPERSAND_REGEX, "§$1")
				.replaceAll(AMPERSAND_REGEX, "&$2")
		);
	}

	/**
	 * Converts an {@link OrderedText} into a {@link String} with {@code &<?>}
	 * codes. Strips any complex style data, including hover events, fonts,
	 * insertion text, etc. If {@code includeStyleData} is true, then the
	 * returned string will not include any formatting codes.
	 *
	 * @apiNote Intended for use with general and regex comparisons, and not
	 * for actually obtaining a complete Text object.
	 * @implNote Visits the OrderedText by each character, and accounts for
	 * Formatting style data by adding {@code &?} codes when the style changes.
	 */
	public static String reorder(OrderedText renderable, boolean includeStyleData) {
		StringBuilder reordered = new StringBuilder();
		Style[] last = {null}; // ensures that the first equality check returns false

		renderable.accept((index, style, codepoint) -> {

			// if style is different from last, add any formatting codes
			if( !style.equals(last[0]) && includeStyleData )
				reordered.append( getFormattingCodes(last[0] = style) );

			reordered.append( Character.toChars(codepoint) );

			return true;
		});

		// trusting this for now...
		return reordered.toString().replaceAll("^(&r)+|(&r)+$", ""); // strips any redundant reset codes
	}

	/**
	 * Takes a {@link Style} and returns a string of {@code &<?>}
	 * codes based upon the style's formatting data.
	 */
	@SuppressWarnings("StringBufferReplaceableByString") // StringBuilder is faster and String concatenation looks ugly
	public static String getFormattingCodes(Style style) {
		StringBuilder codes = new StringBuilder(18);
		Formatting color;

		codes.append(
			style.getColor() != null
				? (color = Formatting.byName(style.getColor().getName())) != null
					? "&" + color.getCode()
					: "&" + style.getColor().getHexCode()
				: "&r"
		);
		codes.append( style.isBold() ? "&l" : "" );
		codes.append( style.isItalic() ? "&o" : "" );
		codes.append( style.isUnderlined() ? "&n" : "" );
		codes.append( style.isStrikethrough() ? "&m" : "" );
		codes.append( style.isObfuscated() ? "&k" : "" );

		return codes.toString();
	}
}