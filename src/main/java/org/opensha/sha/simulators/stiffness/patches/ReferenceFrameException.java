package org.opensha.sha.simulators.stiffness.patches;

import java.util.Locale;

/** A reference map is unusable; this does not declare the original 3D geometry invalid. */
final class ReferenceFrameException extends IllegalArgumentException {
	private static final long serialVersionUID=1L;

	ReferenceFrameException(String message,Throwable cause) {
		super(message,cause);
	}

	private ReferenceFrameException(String message) {
		super(message);
	}

	static void check(boolean condition,String message,Object... args) {
		if (!condition) throw new ReferenceFrameException(String.format(Locale.ROOT,message,args));
	}
}
