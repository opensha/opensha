package scratch.UCERF3.erf.ETAS;

import java.util.Collection;

import com.google.common.base.Preconditions;
import com.google.common.collect.BoundType;
import com.google.common.collect.Range;

public class ETAS_SimulationMetadata {

	/**
	 * Number of ruptures in the original, unfiltered catalog
	 */
	public final int totalNumRuptures;
	/**
	 * Random seed used for simulation
	 */
	public final long randomSeed;
	/**
	 * Index of this catalog in the cast of a multi-catalog simulation, or -1 for a single catalog simulation
	 */
	public final int catalogIndex;
	/**
	 * Range of historical catalog parent IDs, forced to be closed on both ends such that 
	 * rangeHistCatalogParentIDs.upperBoundType() will equal the maximum ID. Null if historical
	 * catalog is excluded from the simulation
	 */
	public final Range<Integer> rangeHistCatalogIDs;
	/**
	 * Range of trigger rupture parent IDs, forced to be closed on both ends such that 
	 * rangeTriggerRupIDs.upperBoundType() will equal the maximum ID. Null if no trigger
	 * ruptures are included in the simulation
	 */
	public final Range<Integer> rangeTriggerRupIDs;
	/**
	 * Time that the simulation began in epoch milliseconds 
	 */
	public final long simulationStartTime;
	/**
	 * Time that the simulation completed in epoch milliseconds 
	 */
	public final long simulationEndTime;
	/**
	 * Number of spontaneous ruptures in this catalog. This quantity is updated to reflect the new value for filtered catalogs
	 */
	public final int numSpontaneousRuptures;
	/**
	 * Number of supraseismogenic ruptures in this catalog (fssIndex>=0). This quantity is updated to reflect the new value
	 * for filtered catalogs
	 */
	public final int numSupraSeis;
	/**
	 * Minimum magnitude for this catalog. Note that for the case of filtered catalogs, this is the filter
	 * magnitude and there may be some ruptures below this level if preserveChain=true
	 */
	public final double minMag;
	/**
	 * Maximum magnitude in this catalog. This quantity is updated to reflect the new value for filtered catalogs
	 */
	public final double maxMag;
	
	private ETAS_SimulationMetadata(int totalNumRuptures, long randomSeed, int catalogIndex, Range<Integer> rangeHistCatalogIDs,
			Range<Integer> rangeTriggerRupIDs, long simulationStartTime, long simulationEndTime, int numSpontaneousRuptures,
			int numSupraSeis, double minMag, double maxMag) {
		super();
		this.totalNumRuptures = totalNumRuptures;
		this.randomSeed = randomSeed;
		this.catalogIndex = catalogIndex;
		if (rangeHistCatalogIDs != null)
			Preconditions.checkState(rangeHistCatalogIDs.upperBoundType() == BoundType.CLOSED
					&& rangeHistCatalogIDs.lowerBoundType() == BoundType.CLOSED,
					"upper and lower bounds should be closed to avoid possible confusion");
		this.rangeHistCatalogIDs = rangeHistCatalogIDs;
		if (rangeTriggerRupIDs != null)
			Preconditions.checkState(rangeTriggerRupIDs.upperBoundType() == BoundType.CLOSED
					&& rangeTriggerRupIDs.lowerBoundType() == BoundType.CLOSED,
					"upper and lower bounds should be closed to avoid possible confusion");
		this.rangeTriggerRupIDs = rangeTriggerRupIDs;
		this.simulationStartTime = simulationStartTime;
		this.simulationEndTime = simulationEndTime;
		this.numSpontaneousRuptures = numSpontaneousRuptures;
		this.numSupraSeis = numSupraSeis;
		this.minMag = minMag;
		this.maxMag = maxMag;
	}
	
	/**
	 * Instance of simulation metadata where all fields are explicitly defined
	 * @param totalNumRuptures
	 * @param randomSeed
	 * @param catalogIndex
	 * @param rangeHistCatalogIDs
	 * @param rangeTriggerRupIDs
	 * @param simulationStartTime
	 * @param simulationEndTime
	 * @param numSpontaneousRuptures
	 * @param numSupraSeis
	 * @param minMag
	 * @param maxMag
	 * @return
	 */
	public static ETAS_SimulationMetadata instance(int totalNumRuptures, long randomSeed, int catalogIndex,
			Range<Integer> rangeHistCatalogIDs, Range<Integer> rangeTriggerRupIDs, long simulationStartTime,
			long simulationEndTime, int numSpontaneousRuptures, int numSupraSeis, double minMag, double maxMag) {
		return new ETAS_SimulationMetadata(totalNumRuptures, randomSeed, catalogIndex, rangeHistCatalogIDs, rangeTriggerRupIDs,
				simulationStartTime, simulationEndTime, numSpontaneousRuptures, numSupraSeis, minMag, maxMag);
	}
	
	/**
	 * Instance of simulation metadata where size, numSpontaneous, numSupraSeis, and maxMag are determined from
	 * the supplied catalog
	 * @param randomSeed
	 * @param catalogIndex
	 * @param rangeHistCatalogIDs
	 * @param rangeTriggerRupIDs
	 * @param simulationStartTime
	 * @param simulationEndTime
	 * @param minMag
	 * @param catalog
	 * @return
	 */
	public static ETAS_SimulationMetadata instance(long randomSeed, int catalogIndex,
			Range<Integer> rangeHistCatalogIDs, Range<Integer> rangeTriggerRupIDs, long simulationStartTime,
			long simulationEndTime, double minMag, Collection<ETAS_EqkRupture> catalog) {
		ETAS_SimulationMetadata meta = new ETAS_SimulationMetadata(catalog.size(), randomSeed, catalogIndex, rangeHistCatalogIDs,
				rangeTriggerRupIDs, simulationStartTime, simulationEndTime, -1, -1, minMag, Double.NaN);
		return meta.getUpdatedForCatalog(catalog);
	}
	
	public ETAS_SimulationMetadata getModMinMag(double minMag) {
		return new ETAS_SimulationMetadata(totalNumRuptures, randomSeed, catalogIndex, rangeHistCatalogIDs,
				rangeTriggerRupIDs, simulationStartTime, simulationEndTime, numSpontaneousRuptures, numSupraSeis, minMag, maxMag);
	}
	
	public ETAS_SimulationMetadata getModCatalogIndex(int catalogIndex) {
		return new ETAS_SimulationMetadata(totalNumRuptures, randomSeed, catalogIndex, rangeHistCatalogIDs,
				rangeTriggerRupIDs, simulationStartTime, simulationEndTime, numSpontaneousRuptures, numSupraSeis, minMag, maxMag);
	}
	
	public ETAS_SimulationMetadata getUpdatedForCatalog(Collection<ETAS_EqkRupture> catalog) {
		double maxMag = Double.NaN;
		int numSpontaneousRuptures = 0;
		int numSupraSeis = 0;
		for (ETAS_EqkRupture rup : catalog) {
			if (Double.isNaN(maxMag))
				maxMag = rup.getMag();
			else
				maxMag = Math.max(maxMag, rup.getMag());
			if (rup.getFSSIndex() >= 0)
				numSupraSeis++;
			if (rup.getGeneration() == 0)
				numSpontaneousRuptures++;
		}
		return new ETAS_SimulationMetadata(totalNumRuptures, randomSeed, catalogIndex, rangeHistCatalogIDs,
				rangeTriggerRupIDs, simulationStartTime, simulationEndTime, numSpontaneousRuptures, numSupraSeis, minMag, maxMag);
	}
	
	private static String rangeStr(Range<Integer> range) {
		if (range == null)
			return null;
		return "["+range.lowerEndpoint()+" "+range.upperEndpoint()+"]";
	}

	@Override
	public String toString() {
		return "totalNumRuptures="+totalNumRuptures+", seed="+randomSeed+", index="+catalogIndex
				+", rangHistCatalogIDs="+rangeStr(rangeHistCatalogIDs)+", rangeTriggerIDs="+rangeStr(rangeTriggerRupIDs)
				+", simStartTime="+simulationEndTime+", simEndTime="+simulationEndTime+", numSpontaneous="+numSpontaneousRuptures
				+", numSupraSeis="+numSupraSeis+", minMag="+minMag+", maxMag="+maxMag;
	}

}
