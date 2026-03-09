package org.opensha.sha.earthquake;

import java.util.ArrayList;
import java.util.Arrays;

import com.google.common.base.Preconditions;

/**
 * This class adds methods for accessing ruptures by their nth index (where N is the total number of ruptures
 * given all sources).
 * 
 * Subclasses must run the setAllNthRupRelatedArrays() method every time the total number of ruptures changes
 * (which may depend on adjustable parameters).
 * 
 * @author Ned Field
 * @version $Id: AbstractNthRupERF.java 10801 2014-08-11 17:32:51Z field $
 */
public abstract class AbstractNthRupERF extends AbstractERF {
	
	private static final long serialVersionUID = 1L;

	/**
	 * total number of ruptures across all sources
	 */
	private int totNumRups=-1;
	
	/**
	 * array of length {#link {@link #getNumSources()}.
	 * 
	 * the value at the ith index is the nth rupture index for the first rupture in that source. If a source is empty,
	 * it should contain the first rupture following that source (and NOT -1) to ensure this is in order for
	 * binary searches
	 */
	private int[] sourceRupIndexes;
	
	protected synchronized final void sourceRupIndexesChanged() {
		sourceRupIndexes = null;
		totNumRups = -1;
	}
	
	private void checkInitSourceRupIndexes() {
		if (sourceRupIndexes == null) {
			synchronized (this) {
				if (sourceRupIndexes == null) {
					initSourceRupIndexes();
				}
			}
		}
	}
	
	private synchronized final void initSourceRupIndexes() {
		int[] sourceRupIndexes = new int[getNumSources()];
		int numRuptures = 0;
		for (int srcIndex=0; srcIndex<sourceRupIndexes.length; srcIndex++) {
			sourceRupIndexes[srcIndex] = numRuptures;
			int srcRups = getSource(srcIndex).getNumRuptures();
			Preconditions.checkState(numRuptures+srcRups <= Integer.MAX_VALUE,
					"The rupture count exceeds Integer.MAX_VALUE=%s", Integer.MAX_VALUE);
			numRuptures += srcRups;
		}
		this.totNumRups = numRuptures; 
		this.sourceRupIndexes = sourceRupIndexes;
	}
	
	/**
	 * This returns the nth rup indices for the given source
	 */
	public int[] get_nthRupIndicesForSource(int iSource) {
		checkInitSourceRupIndexes();
		int startInclusive = sourceRupIndexes[iSource];
		int endExclusive;
		if (iSource == sourceRupIndexes.length-1)
			endExclusive = totNumRups;
		else
			endExclusive = sourceRupIndexes[iSource+1];
		int[] ret = new int[endExclusive-startInclusive];
		for (int i=0; i<ret.length; i++)
			ret[i] = startInclusive+i;
		return ret;
	}
	
	/**
	 * This returns the total number of ruptures (the sum of all ruptures in all sources)
	 */
	public int getTotNumRups() {
		checkInitSourceRupIndexes();
		return totNumRups;
	}
	
	/**
	 * This returns the nth rupture index for the given source and rupture index
	 * (where the latter is the rupture index within the source)
	 */	
	public int getIndexN_ForSrcAndRupIndices(int s, int r) {
		return get_nthRupIndicesForSource(s)[r];
	}
	
	/**
	 * This returns the source index for the nth rupture
	 * @param nthRup
	 * @return
	 */
	public int getSrcIndexForNthRup(int nthRup) {
		checkInitSourceRupIndexes();
		
		Preconditions.checkState(nthRup >= 0 && nthRup < totNumRups, "Bad nthRup=%s for totNumRups=%s", nthRup, totNumRups);
		
		int ind = Arrays.binarySearch(sourceRupIndexes, nthRup);

		if (ind >= 0) {
			// nthRup is a "start index". If there are consecutive duplicates due to empty sources,
			// return the LAST one (the actual source that contains nthRup).
			int val = sourceRupIndexes[ind];
			while (ind + 1 < sourceRupIndexes.length && sourceRupIndexes[ind + 1] == val)
				ind++;
			return ind;
		}

		// Otherwise, binarySearch returns -(insertionPoint) - 1.
		// insertionPoint is the first index with value > nthRup (or length if none).
		int insertionPoint = -ind - 1;
		int srcIndex = insertionPoint - 1;

		if (srcIndex < 0)
			throw new IllegalStateException("Invalid sourceRupIndexes: first start=" + sourceRupIndexes[0]);

		return srcIndex;
	}

	/**
	 * This returns the rupture index (with its source) for the
	 * given nth rupture.
	 * @param nthRup
	 * @return
	 */
	public int getRupIndexInSourceForNthRup(int nthRup) {
		int srcIndex = getSrcIndexForNthRup(nthRup);
		return nthRup - sourceRupIndexes[srcIndex];
	}
	
	/**
	 * This returns the nth rupture in the ERF
	 * @param n
	 * @return
	 */
	public ProbEqkRupture getNthRupture(int n) {
		return getRupture(getSrcIndexForNthRup(n), getRupIndexInSourceForNthRup(n));
	}
	
	/**
	 * This checks whether what's returned from get_nthRupIndicesForSource(s) gives
	 *  successive integer values when looped over all sources.
	 *  TODO move this to a test class?
	 *  
	 */
	public void testNthRupIndicesForSource() {
		int index = 0;
		for(int s=0; s<this.getNumSources(); s++) {
			int[] test = get_nthRupIndicesForSource(s);
			for(int r=0; r< test.length;r++) {
				int nthRup = test[r];
				if(nthRup !=index)
					throw new RuntimeException("Error found");
				index += 1;
			}
		}
		System.out.println("testNthRupIndicesForSource() was successful");
	}

}
