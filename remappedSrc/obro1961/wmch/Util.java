package obro1961.wmch;

/**
 * Random functions that are of some random, obsolete use
 */
public class Util {
    /**
	 * Functions similarly to the JavaScript OR ({@code ||}) operator, which
	 * is a boolean operator but also returns the left side Object if truthy.
	 * If falsy, it returns the right side Object. This functions equivalently
	 * except it has a fallback to avoid {@code NullPointerExceptions}.
	 * <p>Recommended to cast to the output type if you know it, to avoid errors
	 * @param o1 An object (acts as the left Object)
	 * @param o2 Another object (acts as the right Object)
	 * @param fallback Returns if {@code o1} and {@code o2} evaluate to null. Replace with {@code o2} for the description of this method.
	 * @return {@code o1}, {@code o2}, or {@code fallback}.
	 * @see Util#or(Object, Object)
	 */
	public static Object or(Object o1, Object o2, Object fallback) {
		return o1!=null ? o1 : o2!=null ? o2 : fallback;
	}

	/** Same as {@link Util#or(Object, Object, Object)}, but passes o2 as itself and fallback. */
	public static Object or(Object o1, Object o2) { return or(o1, o2, o2); }

    /**
     * A returns true if {@code num} is in between {@code min} and {@code max}. Inclusive.
     * @param min Minimum integer in range
     * @param max Minimum integer in range
     * @param num Integer to test
     * @return If {@code num} is in the range
     */
    public static boolean inRange(int min, int max, int num) {
        return num >= min && num <= max;
    }
}
