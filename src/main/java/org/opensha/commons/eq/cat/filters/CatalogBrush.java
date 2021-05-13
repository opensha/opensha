package org.opensha.commons.eq.cat.filters;

import static org.opensha.commons.eq.cat.util.DataType.*;
import static org.opensha.commons.eq.cat.CatTools.*;
import static org.opensha.commons.geo.GeoTools.*;
import static org.opensha.commons.eq.cat.filters.CatalogBrush.LimitKey.*;
import static org.opensha.commons.eq.cat.filters.CatalogBrush.SelectionChange.*;

import java.util.Arrays;
import java.util.BitSet;
import java.util.EnumSet;
import java.util.Map;

import org.opensha.commons.eq.cat.MutableCatalog;
import org.opensha.commons.eq.cat.util.DataType;
import org.opensha.commons.util.DataUtils;

/**
 * This class may be used to efficiently brush catalog data. Specifically, the
 * following <code>DataType</code>s can be filtered using min-max constraints:
 * <code>[ TIME, LATITUDE,LONGITUDE, DEPTH, MAGNITUDE ]</code> For large
 * catalogs it has a significant memory footprint once instantiated because it
 * internally manages index arrays and sorted copies of catalog source data
 * arrays.
 * 
 * @author Peter Powers
 * @version $Id: CatalogBrush.java 7478 2011-02-15 04:56:25Z pmpowers $
 */
public class CatalogBrush {

	private MutableCatalog catalog;
	private EnumSet<DataType> brushables = EnumSet.of(TIME, LATITUDE,
		LONGITUDE, DEPTH, MAGNITUDE);

	private Map<DataType, double[]> sortedSources;
	private Map<DataType, int[]> sourceIndices;
	private Map<DataType, Integer> minCarets; // inclusive
	private Map<DataType, Integer> maxCarets; // exclusive

	private BitSet selectionChange;
	private BitSet selection;

	/**
	 * Initializes a new data brush with the supplied catalog.
	 * 
	 * @param catalog
	 */
	public CatalogBrush(MutableCatalog catalog) {
		this.catalog = catalog;
		brushables.retainAll(catalog.getDataTypes());
		selectionChange = new BitSet(catalog.size());
		selection = new BitSet(catalog.size());
		selection.set(0, selection.length());
		initMaps();
	}

	/**
	 * Returns the current selection as a <code>BitSet</code>.
	 * 
	 * @return the current selection
	 */
	public BitSet selection() {
		return selection;
	}

	/**
	 * Returns the current selection change as a <code>BitSet</code>.
	 *
	 * @return the current selection change
	 */
	public BitSet selectionChange() {
		return selectionChange;
	}

	
	public SelectionChange adjust(LimitKey key, long value) {
		return NONE;
	}

	/**
	 * Adjusts the catalog selection and returns a flag describing the change.
	 * If the <code>DataType</code> supplied is not applicable for the
	 * associated catalog, the supplied value is out of range for the the
	 * <code>DataType</code>, or the selection hasn't changed,
	 * <code>SelectionChange.NONE</code> is returned. Otherwise method returns
	 * <code>SelectionChange.ADD</code> or <code>SelectionChange.REMOVE</code>.
	 * Once method returns, the <code>BitSet</code> returned by
	 * {@link #selectionChange()} represents the change.
	 * 
	 * @param type of data to adjust
	 * @param key to adjust - min or max limit
	 * @param newValue for limit
	 * @return <code>true</code> if catalog selection has changed,
	 *         <code>false</code> otherwise
	 */
	public SelectionChange adjust(DataType type, LimitKey key, double newValue) {
		// reset change tracker
		selectionChange.clear();
		// check inputs
		if (!brushables.contains(type)) return NONE;
		if (!validateValue(type, newValue)) return NONE;
		// update adjustor
		SelectionChange change;
		if (key == MIN) {
			change = adjustMin(type, newValue);
		} else {
			change = adjustMax(type, newValue);
		}
		// update selection
		if (change == ADD) {
			selection.or(selectionChange);
		} else {
			selection.andNot(selectionChange);
		}
		return change;
	}

	/*
	 * Performs an update to a min limit change.
	 */
	private SelectionChange adjustMin(DataType type, double newValue) {
		double[] source = sortedSources.get(type);
		int minCaret = minCarets.get(type);
		int maxCaret = maxCarets.get(type);
		double oldValue = source[minCaret];
		int idx = calcMinCaret(source, minCaret, maxCaret, oldValue, newValue);
		if (idx == -1) return NONE;
		int minSelect = (idx < minCaret) ? idx : minCaret;
		int maxSelect = (idx < minCaret) ? minCaret : idx;
		minCarets.put(type, idx);
		updateSelection(type, minSelect, maxSelect);
		return (idx < minCaret || idx > maxCaret) ? ADD : REMOVE;
	}

	/*
	 * Performs an update to a max limit change.
	 */
	private SelectionChange adjustMax(DataType type, double newValue) {
		double[] source = sortedSources.get(type);
		int minCaret = minCarets.get(type);
		int maxCaret = maxCarets.get(type);
		double oldValue = source[maxCaret];
		int idx = calcMaxCaret(source, minCaret, maxCaret, oldValue, newValue);
		if (idx == -1) return NONE;
		int minSelect = (idx < maxCaret) ? idx : maxCaret;
		int maxSelect = (idx < maxCaret) ? maxCaret : idx;
		minCarets.put(type, idx);
		updateSelection(type, minSelect, maxSelect);
		return (idx < minCaret || idx > maxCaret) ? ADD : REMOVE;
	}

	/*
	 * Determines new minimum caret. Package private for testing.
	 */
	static int calcMinCaret(double[] data, int minCaret, int maxCaret,
			double oldValue, double newValue) {
		if (oldValue == newValue) return -1;
		int idx = (newValue > oldValue) ? Arrays.binarySearch(data, minCaret,
			maxCaret, newValue) : Arrays.binarySearch(data, 0, minCaret,
			newValue);
		// adjust caret values outside source array limits
		idx = (idx == -1) ? 0 : (idx < -1) ? -idx - 1 : idx;
		// prevent min caret from equalling or passing max caret
		idx = (idx >= maxCaret) ? maxCaret - 1 : idx;
		// adjust caret down so that all equal to value are included
		while (idx != 0 && data[idx - 1] == data[idx]) {
			idx--;
		}
		return (idx == minCaret) ? -1 : idx;
	}

	/*
	 * Determines new maximum caret. Package private for independent testing.
	 */
	static int calcMaxCaret(double[] data, int minCaret, int maxCaret,
			double oldValue, double newValue) {
		if (oldValue == newValue) return -1;
		int idx = (newValue > oldValue) ? Arrays.binarySearch(data, maxCaret,
			data.length, newValue) : Arrays.binarySearch(data, minCaret,
			maxCaret, newValue);
		// set idx so that it can be incremented by one at end of method
		// adjust caret values outside source array limits
		idx = (idx == -1) ? 0 : (idx < -1) ? -idx - 2 : idx;
		// prevent max caret from equalling or passing min caret
		idx = (idx <= minCaret) ? minCaret : idx;
		// adjust caret up so that all equal to value are included
		while (idx < data.length - 1 && data[idx + 1] == data[idx]) {
			idx++;
		}
		return (idx + 1 == maxCaret) ? -1 : ++idx;
	}

	/*
	 * Flips the required bits. 'adjustor' has always been cleared prior to
	 * calliong this methd
	 */
	private void updateSelection(DataType type, int min, int max) {
		int[] indices = sourceIndices.get(type);
		for (int i = min; i < max; i++) {
			selectionChange.flip(indices[i]);
		}
	}

	/* initialize all data tracking maps */
	private void initMaps() {
		for (DataType type : brushables) {
			// retainAll in constructor already filtered out
			// data type missing from catalog
			double[] source = (double[]) catalog.copyData(type);
			sourceIndices.put(type, DataUtils.indexAndSort(source));
			sortedSources.put(type, source);
			minCarets.put(type, 0);
			maxCarets.put(type, catalog.size());
		}
	}

	/* determine whether a supplied value is allowed */
	private boolean validateValue(DataType type, double value) {
		try {
			switch (type) {
				case LATITUDE:
					validateLat(value);
				case LONGITUDE:
					validateLon(value);
				case DEPTH:
					validateDepth(value);
				case MAGNITUDE:
					validateMag(value);
			}
		} catch (IllegalArgumentException iae) {
			return false;
		}
		return true;
	}

	/**
	 * Key used to identify whether DataBrush should adjust a minimum or a
	 * maximum value.
	 */
	public enum LimitKey {
		/** Minimum value identifier. */
		MIN,
		/** Maximum value identifier. */
		MAX;
	}

	/**
	 * Identifies a type of selection change.
	 */
	public enum SelectionChange {
		/** Selection was added to. */
		ADD,
		/** Selection was reduced. */
		REMOVE,
		/** No change occurred. */
		NONE;
	}

}
