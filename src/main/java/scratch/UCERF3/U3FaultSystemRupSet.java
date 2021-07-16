/**
 * 
 */
package scratch.UCERF3;

import java.io.Serializable;
import java.util.List;

import org.opensha.commons.calc.FaultMomentCalc;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.modules.InfoModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.SectAreas;
import org.opensha.sha.earthquake.faultSysSolution.modules.SectSlipRates;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityConfiguration;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RuptureConnectionSearch;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;

import scratch.UCERF3.analysis.DeformationModelsCalc;


/**
 * This class represents the attributes of ruptures in a fault system, 
 * where the latter is composed of some number of fault sections.
 * 
 * TODO: deprecate
 * 
 * @author Field, Milner, Page, & Powers
 *
 */
public class U3FaultSystemRupSet extends org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet implements Serializable {

	/**
	 * Constructor for precomputed data where everything is passed in.
	 * 
	 * @param faultSectionData fault section data list (CANNOT be null)
	 * @param sectSlipRates slip rates for each fault section with any reductions applied (CAN be null)
	 * @param sectSlipRateStdDevs slip rate std deviations for each fault section (CAN be null)
	 * @param sectAreas areas for each fault section (CAN be null)
	 * @param sectionForRups list of fault section indexes for each rupture (CANNOT be null)
	 * @param mags magnitudes for each rupture (CANNOT be null)
	 * @param rakes rakes for each rupture (CANNOT be null)
	 * @param rupAreas areas for each rupture (CANNOT be null)
	 * @param rupLengths lengths for each rupture (CAN be null)
	 * @param info metadata string
	 */
	public U3FaultSystemRupSet(
			List<? extends FaultSection> faultSectionData,
			double[] sectSlipRates,
			double[] sectSlipRateStdDevs,
			double[] sectAreas,
			List<List<Integer>> sectionForRups,
			double[] mags,
			double[] rakes,
			double[] rupAreas,
			double[] rupLengths,
			String info) {
		super();
		init(faultSectionData, sectSlipRates, sectSlipRateStdDevs, sectAreas, sectionForRups,
				mags, rakes, rupAreas, rupLengths, info);
	}
	
	/**
	 * Default constructor for subclasses which will call init on their own.
	 * 
	 * Protected so it can only be invoked by subclasses.
	 */
	protected U3FaultSystemRupSet() {
		super();
		// do nothing, it's up to subclass to call init.
	}
	
	/**
	 * Initialize from another rupSet
	 * @param rupSet
	 */
	protected void init(U3FaultSystemRupSet rupSet) {
		init(rupSet.getFaultSectionDataList(), rupSet.getSlipRateForAllSections(),
				rupSet.getSlipRateStdDevForAllSections(), rupSet.getAreaForAllSections(),
				rupSet.getSectionIndicesForAllRups(), rupSet.getMagForAllRups(), rupSet.getAveRakeForAllRups(),
				rupSet.getAreaForAllRups(), rupSet.getLengthForAllRups(), rupSet.getInfoString());
		copyCacheFrom(rupSet);
	}
	
	/**
	 * Sets all parameters
	 * 
	 * @param faultSectionData fault section data list (CANNOT be null)
	 * @param sectSlipRates slip rates for each fault section with any reductions applied (CAN be null)
	 * @param sectSlipRateStdDevs slip rate std deviations for each fault section (CAN be null)
	 * @param sectAreas areas for each fault section (CAN be null)
	 * @param sectionForRups list of fault section indexes for each rupture (CANNOT be null)
	 * @param mags magnitudes for each rupture (CANNOT be null)
	 * @param rakes rakes for each rupture (CANNOT be null)
	 * @param rupAreas areas for each rupture (CANNOT be null)
	 * @param rupLengths lengths for each rupture (CAN be null)
	 * @param info metadata string
	 */
	protected void init(
			List<? extends FaultSection> faultSectionData,
			double[] sectSlipRates,
			double[] sectSlipRateStdDevs,
			double[] sectAreas,
			List<List<Integer>> sectionForRups,
			double[] mags,
			double[] rakes,
			double[] rupAreas,
			double[] rupLengths,
			String info) {
		init(faultSectionData, sectionForRups,
				mags, rakes, rupAreas, rupLengths);
		
		if (sectSlipRates != null || sectSlipRateStdDevs != null)
			addModule(SectSlipRates.precomputed(this, sectSlipRates, sectSlipRateStdDevs));
		
		if (sectAreas != null)
			addModule(SectAreas.precomputed(this, sectAreas));
		
		if (info != null && !info.isBlank())
			addModule(new InfoModule(info));
	}
	
	
	/**
	 * This computes the fractional slip rate (or moment rate) taken away for sub-seismogenic ruptures
	 * (relative to creep reduced moment rate).  Actually, this will also include any additional coupling
	 * coefficient reductions applied by subclasses (e.g., InversionFaultSystemRuptureSet has the option
	 * of also applying an implied coupling coefficient that will be reflected in getSlipRateForSection(sectIndex))
	 * @param sectIndex
	 * @return
	 */
	public double getMomentRateReductionFraction(int sectIndex) {
		double origSlipRate = getFaultSectionData(sectIndex).getReducedAveSlipRate() * 1e-3; // convert to meters
		double reducedSlipRate = getSlipRateForSection(sectIndex);
		return 1d - reducedSlipRate/origSlipRate;
	}
	
	/**
	 * This returns the total reduction in moment rate for subseimogenic ruptures
	 * and any coupling coefficient applied (the amount removed).  Actually, this 
	 * reduction also includes any additional coupling coefficient reductions applied 
	 * by subclasses (e.g., InversionFaultSystemRuptureSet has the option of also 
	 * applying an implied coupling coefficient that will be reflected in 
	 * getSlipRateForSection(sectIndex))
	 * 
	 * @return
	 */
	public double getTotalMomentRateReduction() {
		return getTotalOrigMomentRate() - getTotalReducedMomentRate();
	}
	
	/**
	 * This returns the total fraction of moment that is reduced by subseismogenic ruptures.
	 * Actually, this reduction also includes any additional coupling coefficient reductions  
	 * applied by subclasses (e.g., InversionFaultSystemRuptureSet has the option of also 
	 * applying an implied coupling coefficient that will be reflected in 
	 * getSlipRateForSection(sectIndex))
	 * 
	 */
	public double getTotalMomentRateReductionFraction() {
		return getTotalMomentRateReduction() / getTotalOrigMomentRate();
	}

	/**
	 * This returns the original moment rate (with creep reductions but without subseismogenic
	 * rupture reductions) for a fault subsection
	 */
	public double getOrigMomentRate(int sectIndex) {
		FaultSection sectData = getFaultSectionData(sectIndex);
		double moRate = sectData.calcMomentRate(true);
		if (Double.isNaN(moRate))
			return 0;
		return moRate;
	}
	
	/**
	 * This returns the total moment rate for the given rupSet without taking into account any
	 * moment rate reductions for subseismogenic ruptures (but does include all default creep reductions).<br>
	 * <br>
	 * This simply calls <code>DeformationModelsCalc.calculateTotalMomentRate(sectData, true)</code> 
	 * 
	 * @param rupSet
	 * @return
	 */
	public double getTotalOrigMomentRate() {
		return DeformationModelsCalc.calculateTotalMomentRate(getFaultSectionDataList(), true);
	}
	
	/**
	 * This returns the moment rate after removing that for subseimogenic ruptures 
	 * (and default creep effects). This also include any additional
	 * coupling coefficients applied by subclasses (e.g., InversionFaultSystemRuptureSet 
	 * has the option of also applying an implied coupling coefficient that will be 
	 * reflected in getSlipRateForSection(sectIndex)).
	 * 
	 * @param sectIndex
	 * @return
	 */
	public double getReducedMomentRate(int sectIndex) {
		return getOrigMomentRate(sectIndex) * (1 - getMomentRateReductionFraction(sectIndex));
	}
	
	/**
	 * This returns the total moment rate after removing that for subseismogenic  
	 * ruptures (and default creep influences).  This also include any additional
	 * coupling coefficients applied by subclasses; e.g., InversionFaultSystemRuptureSet 
	 * has the option of also applying an implied coupling coefficient that will be 
	 * reflected in getSlipRateForSection(sectIndex)).
	 * @return
	 */
	public double getTotalReducedMomentRate() {
		double totMoRate = 0d;
		for (int sectIndex=0; sectIndex<getNumSections(); sectIndex++) {
			double sectMoment = getReducedMomentRate(sectIndex);
			if (!Double.isNaN(sectMoment))
				totMoRate += sectMoment;
		}
		return totMoRate;
	}
	
	/**
	 * This sets the magnitudes for each rupture. This is needed for special cases where magnitudes are
	 * overridden, for example UCERF2 comparison solutions.
	 * @param mags
	 */
	public void setMagForallRups(double[] mags) {
		Preconditions.checkArgument(mags.length == getNumRuptures(),
				"Called setMag for "+mags.length+" rups but rup set has "+getNumRuptures()+" rups!");
		super.init(getFaultSectionDataList(), getSectionIndicesForAllRups(), mags, getAveRakeForAllRups(),
				getAreaForAllRups(), getLengthForAllRups());
	}
	
	/**
	 * This represents the total moment rate available to the rupture (with creep and 
	 * subseis ruptures removed), assuming it is the only event to occur along the sections it uses.
	 * @param rupIndex
	 * @return
	 */
	protected double calcTotalAvailableMomentRate(int rupIndex) {
		List<Integer> sectsInRup = getSectionsIndicesForRup(rupIndex);
		double totMoRate = 0;
		for(Integer sectID:sectsInRup) {
			double area = getAreaForSection(sectID);
			totMoRate += FaultMomentCalc.getMoment(area, getSlipRateForSection(sectID));
		}
		return totMoRate;
	}
	
	/**
	 * This gives the plausibility configuration used to create this rupture set if available,
	 * otherwise null
	 * 
	 * @return
	 */
	@Deprecated
	public PlausibilityConfiguration getPlausibilityConfiguration() {
		return getModule(PlausibilityConfiguration.class);
	}
	
	/**
	 * Sets the plausibility configuration used to create this rupture set
	 * 
	 * @param plausibilityConfig
	 */
	@Deprecated
	public void setPlausibilityConfiguration(PlausibilityConfiguration plausibilityConfig) {
		if (plausibilityConfig == null)
			removeModuleInstances(PlausibilityConfiguration.class);
		else
			addModule(plausibilityConfig);
	}

	/**
	 * This gives a list of ClusterRupture instances if available, otherwise null. This list can be
	 * built if needed via the buildClusterRuptures(...) method.
	 * 
	 * @return
	 */
	@Deprecated
	public List<ClusterRupture> getClusterRuptures() {
		if (hasModule(ClusterRuptures.class))
			return getModule(ClusterRuptures.class).getAll();
		return null;
	}

	/**
	 * Sets the list of ClusterRupture instances
	 * 
	 * @param clusterRuptures
	 */
	@Deprecated
	public void setClusterRuptures(List<ClusterRupture> clusterRuptures) {
		if (clusterRuptures == null) {
			removeModuleInstances(ClusterRuptures.class);
		} else {
			Preconditions.checkState(clusterRuptures.size() == getNumRuptures(),
					"Cluster ruptures list is of size=%s but numRuptures=%s",
					clusterRuptures.size(), getNumRuptures());
			addModule(ClusterRuptures.instance(this, clusterRuptures));
		}
	}
	
	/**
	 * Builds cluster ruptures for this RuptureSet. If the plausibility configuration has been set
	 * and no splays are allowed, then they will be built assuming an ordered single strand rupture.
	 * Otherwise, the given RuptureConnectionSearch will be used to construct ClusterRupture representations
	 * 
	 * @param search
	 */
	@Deprecated
	public void buildClusterRups(RuptureConnectionSearch search) {
		addModule(ClusterRuptures.instance(this, search));
	}

	public void copyCacheFrom(U3FaultSystemRupSet rupSet) {
		super.copyCacheFrom(rupSet);
	}
	
}
