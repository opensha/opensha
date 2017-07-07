package scratch.peter.curves;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * Class that encapsulates statistical data from suite of logic tree results
 * necessary to create a modified tornado diagram.
 * 
 * @author Peter Powers
 * @version $Id:$
 */
public class TornadoData {

	private static final Joiner J = Joiner.on(",");
	private static final String LF = IOUtils.LINE_SEPARATOR;

	
	private double median;
	private Map<Class<?>, TornadoEntry<? extends Enum<?>>> entryMap;
	
	public TornadoData(double median) {
		this.median = median;
		entryMap = Maps.newHashMap();
	}
	
	@Override
	public String toString() {
		return buildString(median, entryMap.values());
	}
	
	public String toSortedString() {
		List<? extends TornadoEntry<? extends Enum<?>>> entries = 
				Lists.newArrayList(entryMap.values());
		Collections.sort(entries);
		return buildString(median, entries);
	}
	
	private static String buildString(double median,
			Collection<? extends TornadoEntry<? extends Enum<?>>> entries) {
		StringBuilder sb = new StringBuilder();
		// putting in twice so format matches subsequent rows
		sb.append(J.join("median", median, "median", median)).append(LF);
		for (TornadoEntry<?> e : entries) {
			sb.append(e).append(LF);
		}
		return sb.toString();
	}
	
	/**
	 * Adds a leaf identifier and its value to the internal data store.
	 * @param c class of branch point id enum
	 * @param e branching point identifier enum type
	 * @param v value
	 */
	public <T extends Enum<T>> void add(Class<?> c, Enum<T> e, double v) {
		// if an entry does not yet exist create it; if an entry does exist,
		// update it's min or max value; the first time an entry is added to,
		// both min and max data are set to the initial add data
		TornadoEntry<?> entry = entryMap.get(c);
		if (entry == null) {
			TornadoEntry<T> te = new TornadoEntry<T>();
			te.eMin = e;
			te.eMax = e;
			te.vMin = v;
			te.vMax = v;
			entryMap.put(c, te);
			return;
		}
		// we know we have a non-null value for which class will match so
		// perform unchecked cast
		TornadoEntry<T> te = (TornadoEntry<T>) entry;
		if (v < entry.vMin) {
			te.vMin = v;
			te.eMin = e;
		} else if (v > entry.vMax) {
			te.vMax = v;
			te.eMax = e;
		} else {
			// ensures different min max leaf ids if all leaves
			// yield the same reult
			te.eMax = e;
		}
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO do nothing
//		System.out.println(9/2);

	}
	
	/*
	 * An Entry encapsulates the min and max enum identifiers along with their
	 * values for a logic tree branch.
	 */
	private static class TornadoEntry<E extends Enum<E>> implements
			Comparable<TornadoEntry<E>> {
		
		Enum<E> eMin;
		double vMin;
		Enum<E> eMax;
		double vMax;
		
		@Override
		public String toString() {
			return J.join(eMin.name(), vMin, eMax.name(), vMax);
		}
		
		@Override
		public int compareTo(TornadoEntry<E> te) {
			double r1 = te.vMax - te.vMin;
			double r2 = vMax - vMin;
			return (r1<r2) ? -1 : (r1>r2) ? 1 : 0;
		}
	}
	

}
