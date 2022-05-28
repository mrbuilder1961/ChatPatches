package obro1961.wmch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.text.CharacterVisitor;
import net.minecraft.text.LiteralText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Random functions that are of some random, obsolete use
 */
public class Util {
	/**
	 * Functions similarly to the JavaScript OR ({@code ||}) operator, which
	 * is a boolean operator but can also return the left side Object if true.
	 * If false, it returns the right side Object. This functions equivalently
	 * except it has a fallback to avoid {@code NullPointerException}s.
	 * <p>
	 * Recommended to cast to the output type if you know it, to avoid errors
	 *
	 * @param o1       An object (acts as the left Object)
	 * @param o2       Another object (acts as the right Object)
	 * @param fallback Returns if {@code o1} and {@code o2} evaluate to null.
	 *                 Replace with {@code o2} for the description of this method.
	 * @return {@code o1}, {@code o2}, or {@code fallback}.
	 * @see Util#or(Object, Object)
	 */
	public static <T> T or(T o1, T o2, T fallback) {
		return o1 != null ? o1 : o2 != null ? o2 : fallback;
	}

	/**
	 * Same as {@link Util#or(Object, Object, Object)}, but passes o2 as itself and
	 * the fallback.
	 */
	public static <T> T or(T o1, T o2) {
		return or(o1, o2, o2);
	}

	/**
	 * Returns a stringified array of {@code Formatting.getName()} for each
	 * {@code Formatting} instance
	 */
	public static String formattingArrToStr(Formatting[] array) {
		if (array.length > 0) {
			ArrayList<String> arr = new ArrayList<>(0);
			for (int i = 0; i < array.length; ++i)
				arr.add("");

			for (int i = 0; i < arr.size(); ++i)
				arr.set(i, array[i].getName());

			return arr.toString();
		} else
			return "[]";
	}

	public static String delAll(String dirty, String toDel) { return dirty.replaceAll(toDel, ""); }
	public static String delOne(String dirty, String toDel) { return dirty.replaceFirst(toDel, ""); }

	// currently unused
	/* public static Text reorder(OrderedText old) {
		LiteralText rebuilt = new LiteralText("");
		old.accept(new CharacterVisitor() {
			@Override public boolean accept(int ix, Style s, int cp) {
				rebuilt.append(new LiteralText(Character.toString(cp)).setStyle(s));
				return true;
			}
		});
		return rebuilt;
	} */

	public static String asString(OrderedText old) {
		StringBuilder str = new StringBuilder();
		old.accept(new CharacterVisitor() {
			@Override public boolean accept(int ix, Style s, int cp) {
				str.append(Character.toString(cp));
				return true;
			}
		});
		return str.toString();
	}

	/**
	 * Turns a String formatted like {@code "&4Dark RED &l&33AAffCUSTOM BOLD BLUE"}
	 * to
	 * a text with all {@code &<?>} replaced with styled colors.
	 * If there are no formatting characters, returns the unstyled string.
	 * Doesn't support hex colors.
	 */
	public static Text getStrTextF(String dirty) {
		LiteralText out = new LiteralText("");
		Pattern finder = Pattern.compile("(?:&[0-9a-fA-Fk-orK-OR])+");
		Matcher results = finder.matcher(dirty);

		if (dirty.matches(".*"+finder.pattern()+".*")) {
			// if there is text before a formatter then add it alone
			if( dirty.split(finder.pattern())[0].length() > 0 ) {
				String prfx = dirty.split(finder.pattern())[0];
				out.append(prfx);
				dirty = dirty.replace(prfx, "");
			};

			List<String> texts = new ArrayList<>( Arrays.asList(dirty.split(finder.pattern())) ); texts.removeIf(s -> s.equals(""));
			int i = 0;

			while(results.find()) {
				Formatting[] style = new Formatting[results.group().length() / 2];
				char[] codes = delAll(results.group(), "&").toCharArray();
				for (int j = 0; j < codes.length; ++j) style[j] = Formatting.byCode(codes[j]);

				out.append( new LiteralText(texts.get(i++)).formatted(style) );
			}

			return out;
		} else
			return Text.of(dirty);
	}
}