package org.opensha.nshmp2.erf.source;

import org.opensha.nshmp2.util.FaultType;
import org.opensha.nshmp2.util.FocalMech;

import com.google.common.base.Predicate;

/**
 * Source filtering predicate utilties.
 * 
 * @author Peter Powers
 * @version $Id:$
 */
public class SourcePredicates {
	// @formatter:off

	/**
	 * Name predicate.
	 * @param name
	 * @return the predicate
	 */
	public static Predicate<FaultSource> name(String name) {
		return new SourceName(name);
	}
	
	/**
	 * Dip predicate.
	 * @param dip
	 * @return the predicate
	 */
	public static Predicate<FaultSource> dip(double dip) {
		return new SourceDip(dip);
	}

	/**
	 * Fault type predicate.
	 * @param type
	 * @return the predicate
	 */
	public static Predicate<FaultSource> type(FaultType type) {
		return new SourceType(type);
	}

	/**
	 * Focal mech predicate.
	 * @param mech
	 * @return the predicate
	 */
	public static Predicate<FaultSource> mech(FocalMech mech) {
		return new SourceMech(mech);
	}

	/**
	 * Floating rupture predicate.
	 * @param floats
	 * @return the predicate
	 */
	public static Predicate<FaultSource> floats(boolean floats) {
		return new SourceFloats(floats);
	}

	
	private static class SourceName implements Predicate<FaultSource> {
		String name;
		SourceName(String name) { this.name = name; }
		@Override public boolean apply(FaultSource input) {
			return input.name.equals(name);
		}
		@Override public String toString() { return "Name: " + name; }
	}

	private static class SourceDip implements Predicate<FaultSource> {
		double dip;
		SourceDip(double dip) { this.dip = dip; }
		@Override public boolean apply(FaultSource input) {
			return input.dip == dip;
		}
		@Override public String toString() { return "Dip: " + dip; }
	}

	private static class SourceType implements Predicate<FaultSource> {
		FaultType type;
		SourceType(FaultType type) { this.type = type; }
		@Override public boolean apply(FaultSource input) {
			return input.type.equals(type);
		}
		@Override public String toString() { return "FaultType: " + type; }
	}

	private static class SourceMech implements Predicate<FaultSource> {
		FocalMech mech;
		SourceMech(FocalMech mech) { this.mech = mech; }
		@Override public boolean apply(FaultSource input) {
			return input.mech.equals(mech);
		}
		@Override public String toString() { return "FocalMech: " + mech; }
	}

	private static class SourceFloats implements Predicate<FaultSource> {
		boolean floats;
		SourceFloats(boolean floats) { this.floats = floats; }
		@Override public boolean apply(FaultSource input) {
			return input.floats == floats;
		}
		@Override public String toString() { return "Floats: " + floats; }
	}

	// @formatter:on
}
