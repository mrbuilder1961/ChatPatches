package obro1961.chatpatches.util;

/** These are used to fix bugs with messages modifying when unwanted. */
public enum Flags {
	INIT(0b1000),
	LOADING_CHATLOG(0b0001),
	BOUNDARY_LINE(0b0010),
	ADDING_CONDENSED_MESSAGE(0b0100);

	Flags(int value) {
		this.value = value;
	}

	public static int flags = INIT.value;
	public final int value;


	/** Set or remove this flag according to {@code set} in {@link #flags}. */
	public void set(boolean set) {
		if(set)
			raise();
		else
			lower();
	}

	/** Set this flag to {@code true} in {@link #flags}. */
	public void raise() {
		flags |= value;
	}

	/** Performs an XOR (toggle) operation of this flag on {@link #flags}. */
	public void toggle() {
		flags ^= value;
	}

	/** Removes this flag's bits in {@link #flags}. */
	public void lower() {
		if( isRaised())
			toggle();
	}

	/** Returns true if this flag has its bit(s) set in {@link #flags}. */
	public boolean isRaised() {
		return (flags & value) == value;
	}
}
