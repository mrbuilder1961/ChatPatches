package obro1961.chatpatches.util;

/** These are used to fix bugs with messages modifying when unwanted. */
public enum Flags {
	INIT(0b1000),
	LOADING_CHATLOG(0b0001),
	BOUNDARY_LINE(0b0010);

	Flags(int value) {
		this.value = value;
	}

	public static int flags = INIT.value;
	public final int value;


	/** Set or remove this flag according to {@code set} in {@link #flags}. */
	public void set(boolean set) {
		if(set)
			flag();
		else
			remove();
	}

	/** Set this flag to {@code true} in {@link #flags}. */
	public void flag() {
		flags |= value;
	}

	/** Performs an XOR (toggle) operation of this flag on {@link #flags}. */
	public void toggle() {
		flags ^= value;
	}

	/** Set this flag to {@code false} in {@link #flags}. */
	public void remove() {
		if(isSet())
			toggle();
	}

	/** Returns true if this flag has its bit(s) set in {@link #flags}. */
	public boolean isSet() {
		return (flags & value) == value;
	}
}
