package obro1961.chatpatches.util;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.*;
import obro1961.chatpatches.mixin.security.ClickEvent$ActionMixin;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A class containing various string and {@link Component} related utilities.
 */
public class TextUtil {
	/**
	 * @see ChatFormatting#PREFIX_CODE
	 */
	public static final Matcher AMPERSAND_REGEX = Pattern.compile("(?m)&([\\da-fk-or])").matcher("");
	/**
	 * {@link #AMPERSAND_REGEX} that explicitly does not match any
	 * formatting codes following backslashes
	 */
	public static final Matcher NO_BACKSLASH_AMPERSAND_REGEX = Pattern.compile("(?m)(?<!\\\\)&([\\da-fk-or])").matcher("");
	/**
	 * Matches two ampersand color codes that directly follow one another. Will
	 * also match if there's any whitespace in between them. This is used to
	 * simplify output strings, as in these cases the first color code is entirely
	 * useless. When replacing, {@code $1} becomes the captured whitespace, if any,
	 * and {@code $2} becomes the effective color code (not including the ampersand).
	 *
	 * @see <a href="https://regex101.com/r/D9x2yv/latest">Examples</a>
	 */
	public static final Matcher REDUNDANT_COLOR_REGEX = Pattern.compile("(?m)&(?:#[\\da-f]{6}|[\\da-f])(\\s*)&(#[\\da-f]{6}|[\\da-f])").matcher("");
	/**
	 * Matches an ampersand color code at the end of a message, optionally
	 * succeeded by whitespace. This is used to simplify output strings, as
	 * color codes at the end of a string do nothing. Captures the whitespace,
	 * if any is available.
	 *
	 * @apiNote Should be used in conjunction with (after) {@link
	 * #REDUNDANT_COLOR_REGEX}.
	 *
	 * @see <a href="https://regex101.com/r/aUuwX1/latest">Examples</a>
	 */
	public static final Matcher EOL_COLOR_REGEX = Pattern.compile("(?m)&(?:#[\\da-f]{6}|[\\da-f])(\\s*)$").matcher("");

	/**
	 * Matches any ampersand formatting codes, whether regular (legacy) or hex,
	 * for use with the pretty-printer in {@link #toCodedString(Component)}.
	 * Captures all formatting code(s) present in a string. Note that if any are
	 * back-to-back, they will be captured together as one string.
	 *
	 * @see <a href="https://regex101.com/r/m0AM81/latest">Examples</a>
	 */
	public static final Matcher PRETTY_PRINT_TARGETS_REGEX = Pattern.compile("(?im)(?<!\\\\)(&(?:#[\\da-f]{6}|[\\da-fk-or]))+").matcher("");


	/**
	 * A wrapped {@link Codec} for {@link Component} objects that will not
	 * throw an exception when serializing click events with unsafe
	 * ({@link ClickEvent.Action#OPEN_FILE}) actions. Done by disabling the
	 * {@linkplain #safeCodec safety serialization check} that is used by
	 * {@link ClickEvent$ActionMixin} to temporarily allow all
	 * messages to be serialized.
	 *
	 * @see ClickEvent$ActionMixin#allowConditionalSerialization(boolean)
	 * @see obro1961.chatpatches.ChatLog#CODEC
	 */
	public static final Codec<Component> UNSAFE_CODEC = new Codec<>() {
		/*~ if <=1.20.2 'ComponentSerialization.CODEC' -> 'net.minecraft.util.ExtraCodecs.COMPONENT' {*/
		@Override
		public <T> DataResult<T> encode(Component input, DynamicOps<T> ops, T prefix) {
			safeCodec.set(false);
			var result = ComponentSerialization.CODEC.encode(input, ops, prefix);
			safeCodec.set(true);
			return result;
		}

		@Override
		public <T> DataResult<Pair<Component, T>> decode(DynamicOps<T> ops, T input) {
			safeCodec.set(false);
			var result = ComponentSerialization.CODEC.decode(ops, input);
			safeCodec.set(true);
			return result;
		}

		@Override
		public String toString() {
			return "UnsafeTextCodec[safe=" + safeCodec.get() + ", codec=" + ComponentSerialization.CODEC + "]";
		}
		/*~}*/
	};

	/**
	 * A boolean-wrapped holder that is {@code true} when safety mechanisms
	 * <b>should</b> be used when serializing text components, and {@code false}
	 * otherwise. Allows for the serialization of potentially unsafe objects
	 * like {@link ClickEvent}s.
	 *
	 * @see ClickEvent$ActionMixin#allowConditionalSerialization(boolean)
	 *
	 * @implNote Thread-local because
	 * <a href="https://discord.com/channels/507304429255393322/721100785936760876/1387226885867704401">
	 * TheWhyEvenHow</a> suggested this and its (successful) implementation.
	 */
	public static final ThreadLocal<Boolean> safeCodec = ThreadLocal.withInitial(() -> true);


	/**
	 * Replaces all {@code $} characters in {@code str} with {@code variable}.
	 * Also replaces intended newline characters with {@code \n} to fix (#36).
	 */
	public static String fillVars(String str, String variable) {
		return str.replace("$", variable).replace("\\n", "\n");
	}

	/**
	 * Parses the given object into a {@link MutableComponent}.
	 */
	public static MutableComponent asText(Object o) {
		if(o == null) {
			return Component.empty();
		} else if(o instanceof Component) {
			return (MutableComponent) o;
		} else {
			return Component.literal(o instanceof FormattedText ft ? ft.getString() : String.valueOf(o));
		}
	}

	public static MutableComponent truncate(Component text, int max) {
		if(ChatFormatting.stripFormatting(text.getString()).length() <= max) {
			return (MutableComponent) text;
		}

		var truncated = Component.empty();
		int[] len = {0};

		text.visit((style, str) -> {
			if(str.length() + len[0] > max) {
				if(len[0] >= max) {
					return Optional.empty(); // if the text is the perfect length, don't add anything
				}

				str = str.substring(0, max - len[0]); // truncate the string to the max length
			}

			truncated.append(Component.literal(str).setStyle(style));

			len[0] += str.length();

			return Optional.empty();
		}, Style.EMPTY);

		return truncated;
	}

	/**
	 * Returns a copy of {@code text} with the specified {@code siblings}
	 * parameter replacing the original siblings. The passed {@code text}'s
	 * content and style are preserved.
	 */
	public static MutableComponent newSiblings(Component text, List<Component> siblings) {
		return new MutableComponent(text.getContents(), siblings, text.getStyle());
	}

	/**
	 * Returns a copy of {@code text} with an empty content.
	 * Useful for comparing {@link Component} objects'
	 * metadata (style and siblings) only.
	 * */
	/*public static MutableComponent withoutContent(Component text) {
		//-? if >1.20.2 {
		//import net.minecraft.network.chat.contents.PlainTextContents;
		//-?}
		return new MutableComponent(*//*-? if >1.20.2 {*//*PlainTextContents*//*-?} else {*//**//*ComponentContents*//**//*-?}*//*.EMPTY, text.getSiblings(), text.getStyle());
	}*/


	/**
	 * Formats a String with {@code &} formatting codes into a {@link Component}.
	 * First replaces all {@code &<?>} codes with a section symbol ({@code §}),
	 * then deletes the backslash from all {@code \&<?>} instances. Doesn't
	 * support hex colors.
	 *
	 * @apiNote Hex colors could be supported with the Placeholder API, but using
	 * an entire library just for this one feature seems excessive.
	 */
	public static MutableComponent text(String unformatted) {
		String s = NO_BACKSLASH_AMPERSAND_REGEX.reset(unformatted).replaceAll("§$1");
		s = AMPERSAND_REGEX.reset(s).replaceAll("&$2");

		return Component.literal(s);
	}


	/**
	 * @return {@code true} if `a` and `b` are virtually equal; that is, a
	 * styled color visitor produces the same sequence for both components.
	 * This is intended to compare the string and style data together, although
	 * this is a convoluted task that will always have edge cases and frustrating
	 * edge cases.
	 *
	 * todo note unsolvable issues here.
	 */
	public static <T> boolean virtuallyEqual(Component a, Component b) {
		// this is presumed true due to where this is called in tryCondenseDupes
		/*if(!a.getString().equals(b.getString())) {
			return false; // obviously the strings themselves need to be the same (case-insensitive tho..?)
		}*/

		Function<List<it.unimi.dsi.fastutil.Pair<String, Style>>, FormattedText.StyledContentConsumer<T>> visitMaker = list -> ((style, contents) -> {
			list.add(new ObjectObjectImmutablePair<>(
				contents.toLowerCase(Locale.ROOT),
				// forcibly clears any invisible style data - otherwise this will always fail due to the differing insertions from the timestamp
				style.withClickEvent(null).withHoverEvent(null).withInsertion(null)
			));
			return Optional.empty();
		});

		ObjectList<it.unimi.dsi.fastutil.Pair<String, Style>> aColors = new ObjectArrayList<>(), bColors = new ObjectArrayList<>();
		a.visit(visitMaker.apply(aColors), Style.EMPTY);
		b.visit(visitMaker.apply(bColors), Style.EMPTY);

		return aColors.equals(bColors);
	}

	/**
	 * Converts a {@link Component} into a {@link String} with {@code &<?>} codes.
	 * Strips any complex style data, including hover events, fonts, insertions,
	 * etc. Hex colors are represented in the format {@code &#RRGGBB}.
	 */
	public static String toCodedString(Component text) {
		StringBuilder builder = new StringBuilder();
		AtomicReference<Style> lastStyle = new AtomicReference<>(Style.EMPTY); // ensures that the first equality check returns false

		text.visit((style, str) -> {
			if(str.isBlank()) {
				// add the whitespace but don't format anything
				builder.append(str);
				return Optional.empty();
			}

			String leadingWhitespace = str.substring(0, str.indexOf(str.stripLeading()));

			// if style is different from last, add any formatting codes
			if(!style.equals(lastStyle.get())) {
				if(!leadingWhitespace.isEmpty()) {
					// adds the whitespace BEFORE the formatting codes and regular text to
					// provide better readability (hugs the right-most characters instead
					// of left-most)
					builder.append(leadingWhitespace);
				}

				builder.append(getFormattingCodes(style, lastStyle.get()));

				lastStyle.set(style);
			}

			// sometimes section signs leak and I WANT THEM OUT
			// this has to be done here or else the stylish color codes added above will be erased
			builder.append( str.stripLeading().replace(ChatFormatting.PREFIX_CODE, '&') );

			return Optional.empty();
		}, Style.EMPTY);


		// removes any leading reset codes - all styles begin naturally reset
		while(builder.indexOf("&r") == 0) {
			builder.delete(0, 2);
		}

		// removes redundant white color codes directly succeeding reset codes
		for(int i = builder.indexOf("&r&f"); i != -1; i = builder.indexOf("&r&f")) {
			String s = builder.toString();
			// this works because simplified, legacy strings cannot have custom,
			// root styles, unlike the complex sibling-tree structure used today
			builder.delete(i + 2, i + 4);
			//todo: i think this was only caused in getFormattingCodes()
			ChatPatches.LOGGER.error("Found and removed an instance of '&r&f'. Guilty string:\n'{}'", s);//DEBUG:WORD!
		}

		String result = builder.toString();
		Matcher m;
		while((m = REDUNDANT_COLOR_REGEX.reset(result)).find()) {
			result = m.replaceFirst("$1&$2");
		}

		/*while((m = EOL_COLOR_REGEX.reset(result)).find()) {
			result = m.replaceFirst("$1");
		}*/

		// finally, adds a pop of color to the formatting codes
		if((m = PRETTY_PRINT_TARGETS_REGEX.reset(result)).find()) {
			result = m.replaceAll(ChatFormatting.AQUA + "$0" + ChatFormatting.RESET);
			// maybe replace &<?> codes that were in the original message with \\\\$1 and then ignore those in this regex
		}

		return result;
	}

	/**
	 * Returns the formatting codes of the {@link Style} provided, excluding any already
	 * applied ones according to {@code last}. Returns an empty string if the style is
	 * empty or blank. If any hex colors are specified, they will be returned in the
	 * format {@code &#RRGGBB}. Additionally, any color that exists as a formatting code
	 * (ex. {@code #55FF55} for {@link ChatFormatting#GREEN}) will return as the formatting
	 * code (ex. {@code &a}).
	 *
	 * @see TextColor#formatValue()
	 */
	public static String getFormattingCodes(Style style, Style last) {
		StringJoiner joiner = new StringJoiner("&", "&", "").setEmptyValue(""); // adds the & at the start of the string

		// if the color is named, it will have a name
		// makes the fallback white so changes to colorless but not empty styles don't ignore colors
		// see 'newBuff' in test cases under #311
		TextColor thisColor = Colors.simplify(style.getColor() != null ? style.getColor().getValue() : Colors.WHITE);
		int thisValue = thisColor.getValue();
		TextColor lastColor = Colors.simplify(last.getColor() != null ? last.getColor().getValue() : Colors.WHITE);

		// ensures reset codes are not treated as white codes by forcing necessary reset codes
		boolean anyModifierReset = (last.isBold() && !style.isBold()) || (last.isItalic() && !style.isItalic()) || (last.isUnderlined() && !style.isUnderlined()) ||
			(last.isStrikethrough() && !style.isStrikethrough()) || (last.isObfuscated() && !style.isObfuscated());
		if(anyModifierReset) {
			// this is necessary because, unlike with colors, you cannot overwrite one modifier with another
			joiner.add("r");
		}

		// only add the color code if it's different from the last color and if it won't result in '&r&f'
		if(thisValue != lastColor.getValue() && !(anyModifierReset && thisValue == Colors.WHITE))
		{
			Optional<String> code = Colors.getCode(thisColor);
			// if thisColor is named, add its formatting code, else add its hex color
			joiner.add( code.orElse(thisColor.formatValue()) ); // thisColor.serialize() also works bc at that point we know it's not named so it will call formatValue() for us
		}
		else if(style.equals(Style.EMPTY) && !last.equals(Style.EMPTY))
		{
			return "&r"; // if the current style is empty but the last style wasn't, we've reset!
		}

		if(style.isBold() && !last.isBold()) {
			joiner.add("l");
		}
		if(style.isItalic() && !last.isItalic()) {
			joiner.add("o");
		}
		if(style.isUnderlined() && !last.isUnderlined()) {
			joiner.add("n");
		}
		if(style.isStrikethrough() && !last.isStrikethrough()) {
			joiner.add("m");
		}
		if(style.isObfuscated() && !last.isObfuscated()) {
			joiner.add("k");
		}

		return joiner.toString();
	}
}