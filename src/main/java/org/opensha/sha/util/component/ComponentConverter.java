package org.opensha.sha.util.component;

import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.sha.imr.param.OtherParams.Component;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

public class ComponentConverter {
	
	private static Table<Component, Component, ComponentTranslation> transTable;
	
	static {
		transTable = HashBasedTable.create();
		
		transTable.put(Component.RotD50, Component.RotD100, new ShahiBaker2014Trans());
		for (Boore2010Trans trans : Boore2010Trans.getAllConverters())
			transTable.put(trans.getFromComponent(), trans.getToComponent(), trans);
	}
	
	/**
	 * 
	 * @param from
	 * @param to
	 * @return true if the given conversion is supported
	 */
	public static boolean isConversionSupported(Component from, Component to) {
		return transTable.contains(from, to);
	}
	
	/**
	 * 
	 * @param from
	 * @param to
	 * @return converter if supported, null otherwise
	 */
	public static ComponentTranslation getConverter(Component from, Component to) {
		return transTable.get(from, to);
	}
	
	/**
	 * @param from
	 * @param to
	 * @param curve
	 * @param period
	 * @return converted curve
	 */
	public static DiscretizedFunc convert(Component from, Component to, DiscretizedFunc curve, double period) {
		Preconditions.checkState(isConversionSupported(from, to), "Conversion is not supported");
		return getConverter(from, to).convertCurve(curve, period);
	}

}
