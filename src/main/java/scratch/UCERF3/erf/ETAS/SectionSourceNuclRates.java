package scratch.UCERF3.erf.ETAS;

import java.util.Arrays;
import java.util.Collection;

import org.opensha.commons.data.function.IntegerPDF_FunctionSampler;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Ints;

/**
 * Efficient data store for nucleation rates for each source on a given section.
 * 
 * sourceIndexes[i] contains the source index in the ERF of the ith fault section source which rupture this section.
 * nuclRates[i] contains the nucleation rates on this section for the ith source
 * @author kevin
 *
 */
class SectionSourceNuclRates {
	
	private int[] sourceIndexes;
	private float[] nuclRates;
	
	public SectionSourceNuclRates(Collection<Integer> sourceIndexes) {
		this(Ints.toArray(sourceIndexes));
	}
	
	public SectionSourceNuclRates(int[] sourceIndexes) {
		this.sourceIndexes = sourceIndexes;
		Arrays.sort(sourceIndexes);
		this.nuclRates = new float[sourceIndexes.length];
	}
	
	/**
	 * 
	 * @return the number of sources for this section
	 */
	public int size() {
		return sourceIndexes.length;
	}
	
	/**
	 * 
	 * @param index index within this datastore (not the source index)
	 * @return the source index in the ERF at the given index
	 */
	public int getSourceIndex(int index) {
		return sourceIndexes[index];
	}
	
	/**
	 * 
	 * @param index index within this datastore (not the source index)
	 * @return the nucleation rate for the source at the given index
	 */
	public float getSourceNucleationRate(int index) {
		return nuclRates[index];
	}
	
	/**
	 * Sets the source nucleation rate at the given index. Note that this is an internal index in this data store. Use indexOf(sourceIndex)
	 * to find the index for a given source
	 * @param index
	 * @param rate
	 */
	public void setSourceNucleationRate(int index, float rate) {
		nuclRates[index] = rate;
	}
	
	/**
	 * Retrieves the index in this datastore of the given source. Throws an exception if the index is not found
	 * @param sourceIndex
	 * @return
	 */
	public int indexOf(int sourceIndex) {
		int ind = Arrays.binarySearch(sourceIndexes, sourceIndex);
		Preconditions.checkState(ind >= 0 && ind < sourceIndexes.length,
				"Source %s not found in this SectionSourceNuclRates datastore", sourceIndex);
		return ind;
	}
	
	/**
	 * Builds an IntegerPDF_FunctionSampler for this section
	 * @return
	 */
	public IntegerPDF_FunctionSampler buildSampler() {
		return new IntegerPDF_FunctionSampler(nuclRates);
	}

}
