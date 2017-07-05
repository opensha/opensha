/**
 * 
 */
package scratch.UCERF3;


import java.awt.Color;
import java.awt.geom.Point2D;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.eq.MagUtils;
import org.opensha.commons.geo.Region;
import org.opensha.commons.gui.plot.GraphWindow;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSymbol;
import org.opensha.sha.gui.infoTools.CalcProgressBar;
import org.opensha.sha.magdist.ArbIncrementalMagFreqDist;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.magdist.SummedMagFreqDist;

import scratch.UCERF3.griddedSeismicity.GridSourceProvider;
import scratch.UCERF3.inversion.InversionFaultSystemRupSet;
import scratch.UCERF3.inversion.InversionFaultSystemSolution;
import scratch.UCERF3.utils.MFD_InversionConstraint;
import scratch.UCERF3.utils.OLD_UCERF3_MFD_ConstraintFetcher;
import scratch.UCERF3.utils.OLD_UCERF3_MFD_ConstraintFetcher.TimeAndRegion;
import scratch.UCERF3.utils.UCERF2_MFD_ConstraintFetcher;
import scratch.UCERF3.utils.paleoRateConstraints.PaleoProbabilityModel;
import scratch.UCERF3.utils.paleoRateConstraints.PaleoRateConstraint;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;

/**
 * This abstract class is intended to represent an Earthquake Rate Model solution 
 * for a fault system, coming from either the Grand Inversion or from a physics-based
 * earthquake simulator.
 * 
 * In addition to adding two methods to the FaultSystemRupSet interface (to get the rate of 
 * each rupture), this class contains many common utility methods for both types of subclass.
 * 
 * Notes:
 * 
 * 1) the getProbPaleoVisible(mag) method may become more complicated (e.g., site specific)
 * 
 * 2) calc methods here are untested
 * 
 * 
 * @author Field, Milner, Page, and Powers
 *
 */
public class FaultSystemSolution implements Serializable {
	
	private FaultSystemRupSet rupSet;
	private double[] rates;
	// this is separate from the rupSet info string as you can have multiple solutions with one rupSet
	private String infoString;
	
	// grid sources, can be null
	private GridSourceProvider gridSourceProvider;
	
	// MFDs for each rupture (mags from different scaling relationships for example)
	// usually null.
	private DiscretizedFunc[] rupMFDs;
	
	protected List<? extends IncrementalMagFreqDist> subSeismoOnFaultMFDs;
	
	/**
	 * Default constructor, validates inputs
	 * @param rupSet
	 * @param rates
	 */
	public FaultSystemSolution(FaultSystemRupSet rupSet, double[] rates) {
		this(rupSet, rates, null);
	}
	
	/**
	 * Default constructor, validates inputs
	 * @param rupSet
	 * @param rates
	 * @param subSeismoOnFaultMFDs
	 */
	public FaultSystemSolution(FaultSystemRupSet rupSet, double[] rates,
			List<? extends IncrementalMagFreqDist> subSeismoOnFaultMFDs) {
		init(rupSet, rates, null, subSeismoOnFaultMFDs);
	}
	
	/**
	 * Builds a solution from the given rupSet/rates. If the rupSet is an InversionFaultSystemRupSet,
	 * an InversionFaultSystemSolution will be returned (else a normal FaultSystemSolution).
	 * @param rupSet
	 * @param rates
	 * @return
	 */
	public static FaultSystemSolution buildSolAsApplicable(FaultSystemRupSet rupSet, double[] rates) {
		if (rupSet instanceof InversionFaultSystemRupSet)
			return new InversionFaultSystemSolution((InversionFaultSystemRupSet)rupSet, rates);
		return new FaultSystemSolution(rupSet, rates);
	}
	
	/**
	 * Not recommended, must call init
	 */
	protected FaultSystemSolution() {
		
	}
	
	protected void init(FaultSystemRupSet rupSet, double[] rates, String infoString,
			List<? extends IncrementalMagFreqDist> subSeismoOnFaultMFDs) {
		this.rupSet = rupSet;
		this.rates = rates;
		Preconditions.checkArgument(rates.length == rupSet.getNumRuptures(), "# rates and ruptures is inconsistent!");
		if (infoString == null)
			this.infoString = rupSet.getInfoString();
		else
			this.infoString = infoString;
		if (subSeismoOnFaultMFDs != null)
			Preconditions.checkState(subSeismoOnFaultMFDs.size() == rupSet.getNumSections(),
					"Sub seismo MFD count and sub section count inconsistent");
		this.subSeismoOnFaultMFDs = subSeismoOnFaultMFDs;
	}
	
	/**
	 * Returns the fault system rupture set for this solution
	 * @return
	 */
	public FaultSystemRupSet getRupSet() {
		return rupSet;
	}
	
	/**
	 * These gives the long-term rate (events/yr) of the rth rupture
	 * @param rupIndex
	 * @return
	 */
	public double getRateForRup(int rupIndex) {
		return rates[rupIndex];
	}
	
	/**
	 * This gives the long-term rate (events/yr) of all ruptures
	 * @param rupIndex
	 * @return
	 */
	public double[] getRateForAllRups() {
		return rates;
	}
	
	/**
	 * This returns the total long-term rate (events/yr) of all fault-based ruptures
	 * (fault based in case off-fault ruptures are added to subclass)
	 * @return
	 */
	public double getTotalRateForAllFaultSystemRups() {
		double totRate=0;
		for(double rate:getRateForAllRups())
			totRate += rate;
		return totRate;
	}
	
	public String getInfoString() {
		return infoString;
	}

	public void setInfoString(String infoString) {
		this.infoString = infoString;
	}

	/**
	 * This enables/disables visible progress bars for long calculations
	 * 
	 * @param showProgress
	 */
	public void setShowProgress(boolean showProgress) {
		rupSet.setShowProgress(showProgress);
	}
	
	public void clearCache() {
		rupSet.clearCache();
		clearSolutionCacheOnly();
	}
	
	public void clearSolutionCacheOnly() {
		particRatesCache.clear();
		nucleationRatesCache.clear();
		totParticRatesCache = null;
		paleoVisibleRatesCache = null;
	}

	/**
	 * This returns the rate that pairs of section rupture together.  
	 * Most entries are zero because the sections are far from each other, 
	 * so a sparse matrix might be in order if this bloats memory.
	 * @return
	 * TODO move?
	 */
	public double[][] getSectionPairRupRates() {
		double[][] rates = new double[rupSet.getNumSections()][rupSet.getNumSections()];
		for(int r=0; r<rupSet.getNumRuptures(); r++) {
			List<Integer> indices = rupSet.getSectionsIndicesForRup(r);
			double rate = getRateForRup(r);
			if (rate == 0)
				continue;
			for(int s=1;s<indices.size();s++) {
				rates[indices.get(s-1)][indices.get(s)] += rate;
				rates[indices.get(s)][indices.get(s-1)] += rate;    // fill in the symmetric point
			}
		}
		return rates;
	}

	private HashMap<String, double[]> particRatesCache = new HashMap<String, double[]>();
	
	/**
	 * This computes the participation rate (events/yr) of the sth section for magnitudes 
	 * greater and equal to magLow and less than magHigh.
	 * @param sectIndex
	 * @param magLow
	 * @param magHigh
	 * @return
	 */
	public double calcParticRateForSect(int sectIndex, double magLow, double magHigh) {
		return calcParticRateForAllSects(magLow, magHigh)[sectIndex];
	}
		
	private double doCalcParticRateForSect(int sectIndex, double magLow, double magHigh) {
		double partRate=0;
		for (int r : rupSet.getRupturesForSection(sectIndex)) {
			double mag = rupSet.getMagForRup(r);
			DiscretizedFunc mfd = getRupMagDist(r);
			if (mfd == null || mfd.size() == 1) {
				if(mag>=magLow && mag<magHigh)
					partRate += getRateForRup(r);
			} else {
				// use rup MFDs
				for (Point2D pt : mfd) {
					if(pt.getX()>=magLow && pt.getX()<magHigh)
						partRate += pt.getY();
				}
			}
		}
		return partRate;
	}
	
	/**
	 * This computes the participation rate (events/yr) of all sections for magnitudes 
	 * greater and equal to magLow and less than magHigh.
	 * @param sectIndex
	 * @param magLow
	 * @param magHigh
	 * @return
	 */
	public synchronized double[] calcParticRateForAllSects(double magLow, double magHigh) {
		String key = (float)magLow+"_"+(float)magHigh;
		if (!particRatesCache.containsKey(key)) {
			double[] particRates = new double[rupSet.getNumSections()];
			CalcProgressBar p = null;
			if (rupSet.isShowProgress()) {
				p = new CalcProgressBar("Calculating Participation Rates", "Calculating Participation Rates");
			}
			for (int i=0; i<particRates.length; i++) {
				if (p != null) p.updateProgress(i, particRates.length);
				particRates[i] = doCalcParticRateForSect(i, magLow, magHigh);
			}
			if (p != null) p.dispose();
			particRatesCache.put(key, particRates);
		}
		return particRatesCache.get(key);
	}

	private HashMap<String, double[]> nucleationRatesCache = new HashMap<String, double[]>();
	
	/**
	 * This computes the nucleation rate (events/yr) of the sth section for magnitudes 
	 * greater and equal to magLow and less than magHigh. This assumes a uniform distribution
	 * of possible hypocenters over the rupture surface.
	 * @param sectIndex
	 * @param magLow
	 * @param magHigh
	 * @return
	 */
	public double calcNucleationRateForSect(int sectIndex, double magLow, double magHigh) {
		return calcNucleationRateForAllSects(magLow, magHigh)[sectIndex];
	}
		
	private double doCalcNucleationRateForSect(int sectIndex, double magLow, double magHigh) {
		double nucleationRate=0;
		for (int r : rupSet.getRupturesForSection(sectIndex)) {
			double mag = rupSet.getMagForRup(r);
			DiscretizedFunc mfd = getRupMagDist(r);
			if (mfd == null || mfd.size() == 1) {
				if(mag>=magLow && mag<magHigh) {
					double rate = getRateForRup(r);
					double sectArea = rupSet.getAreaForSection(sectIndex);
					double rupArea = rupSet.getAreaForRup(r);
					nucleationRate += rate * (sectArea / rupArea);
				}
			} else {
				// use rup MFDs
				double sectArea = rupSet.getAreaForSection(sectIndex);
				double rupArea = rupSet.getAreaForRup(r);
				for (Point2D pt : mfd) {
					if(pt.getX()>=magLow && pt.getX()<magHigh)
						nucleationRate += pt.getY() * (sectArea / rupArea);
				}
			}
			
		}
		return nucleationRate;
	}
	
	
	/**
	 * This computes the nucleation rate (events/yr) of all sections for magnitudes 
	 * greater and equal to magLow and less than magHigh.   This assumes a uniform distribution
	 * of possible hypocenters over the rupture surface.
	 * @param sectIndex
	 * @param magLow
	 * @param magHigh
	 * @return
	 */
	public synchronized double[] calcNucleationRateForAllSects(double magLow, double magHigh) {
		String key = (float)magLow+"_"+(float)magHigh;
		if (!nucleationRatesCache.containsKey(key)) {
			double[] nucleationRates = new double[rupSet.getNumSections()];
			CalcProgressBar p = null;
			if (rupSet.isShowProgress()) {
				p = new CalcProgressBar("Calculating Nucleation Rates", "Calculating Participation Rates");
			}
			for (int i=0; i<nucleationRates.length; i++) {
				if (p != null) p.updateProgress(i, nucleationRates.length);
				nucleationRates[i] = doCalcNucleationRateForSect(i, magLow, magHigh);
			}
			if (p != null) p.dispose();
			nucleationRatesCache.put(key, nucleationRates);
		}
		return nucleationRatesCache.get(key);
	}
	
	private double[] totParticRatesCache;
	
	/**
	 * This computes the total participation rate (events/yr) of the sth section.
	 * 
	 * @param sectIndex
	 * @return
	 */
	public double calcTotParticRateForSect(int sectIndex) {
		return calcTotParticRateForAllSects()[sectIndex];
	}
	
	private double doCalcTotParticRateForSect(int sectIndex) {
		double partRate=0;
		for (int r : rupSet.getRupturesForSection(sectIndex))
			partRate += getRateForRup(r);
		return partRate;
	}
	
	
	/**
	 * This computes the total participation rate (events/yr) for all sections.
	 * 
	 * @return
	 */
	public synchronized double[] calcTotParticRateForAllSects() {
		if (totParticRatesCache == null) {
			totParticRatesCache = new double[rupSet.getNumSections()];
			CalcProgressBar p = null;
			if (rupSet.isShowProgress()) {
				p = new CalcProgressBar("Calculating Total Participation Rates", "Calculating Total Participation Rates");
			}
			for (int i=0; i<totParticRatesCache.length; i++) {
				if (p != null) p.updateProgress(i, totParticRatesCache.length);
				totParticRatesCache[i] = doCalcTotParticRateForSect(i);
			}
			if (p != null) p.dispose();
		}
		return totParticRatesCache;
	}
	
	private Map<PaleoProbabilityModel, double[]> paleoVisibleRatesCache;
	
	/**
	 * This gives the total paleoseismically observable rate (events/yr) of the sth section.
	 * the probability of observing an event is given by the getProbPaleoVisible(mag)
	 * method.
	 * 
	 * @param sectIndex
	 * @return
	 */
	public double calcTotPaleoVisibleRateForSect(int sectIndex, PaleoProbabilityModel paleoProbModel) {
		return calcTotPaleoVisibleRateForAllSects(paleoProbModel)[sectIndex];
	}
	
	public double doCalcTotPaleoVisibleRateForSect(int sectIndex, PaleoProbabilityModel paleoProbModel) {
		double partRate=0;
		for (int r : rupSet.getRupturesForSection(sectIndex))
			partRate += getRateForRup(r)*paleoProbModel.getProbPaleoVisible(rupSet, r, sectIndex);
		return partRate;
	}

	
	/**
	 * This gives the total paleoseismically observable rate of all sections.
	 * the probability of observing an event is given by the getProbPaleoVisible(mag)
	 * method
	 * 
	 * @return
	 */
	public synchronized double[] calcTotPaleoVisibleRateForAllSects(PaleoProbabilityModel paleoProbModel) {
		if (paleoVisibleRatesCache == null) {
			paleoVisibleRatesCache = Maps.newHashMap();
		}
		
		double[] paleoRates = paleoVisibleRatesCache.get(paleoProbModel);
		
		if (paleoRates == null) {
			paleoRates = new double[rupSet.getNumSections()];
			paleoVisibleRatesCache.put(paleoProbModel, paleoRates);
			CalcProgressBar p = null;
			if (rupSet.isShowProgress()) {
				p = new CalcProgressBar("Calculating Paleo Visible Rates", "Calculating Paleo Visible Rates");
			}
			for (int i=0; i<paleoRates.length; i++) {
				if (p != null) p.updateProgress(i, paleoRates.length);
				paleoRates[i] = doCalcTotPaleoVisibleRateForSect(i, paleoProbModel);
			}
			if (p != null) p.dispose();
		}
		return paleoRates;
	}
	
	/**
	 * This assumes a uniform probability of hypocenter location over the rupture surface
	 * @param parentSectionID
	 * @param minMag
	 * @param maxMag
	 * @param numMag
	 * @return
	 */
	public SummedMagFreqDist calcNucleationMFD_forParentSect(int parentSectionID, double minMag, double maxMag, int numMag) {
		SummedMagFreqDist mfd = new SummedMagFreqDist(minMag, maxMag, numMag);
		
		for (int sectIndex=0; sectIndex<rupSet.getNumSections(); sectIndex++) {
			if (rupSet.getFaultSectionData(sectIndex).getParentSectionId() != parentSectionID)
				continue;
			IncrementalMagFreqDist subMFD = calcNucleationMFD_forSect(sectIndex, minMag, maxMag, numMag);
			mfd.addIncrementalMagFreqDist(subMFD);
		}
		
		return mfd;
	}

	/**
	 * This give a Nucleation Mag Freq Dist (MFD) for the specified section.  Nucleation probability 
	 * is defined as the area of the section divided by the area of the rupture.  
	 * This preserves rates rather than moRates (can't have both)
	 * @param sectIndex
	 * @param minMag - lowest mag in MFD
	 * @param maxMag - highest mag in MFD
	 * @param numMag - number of mags in MFD
	 * @return IncrementalMagFreqDist
	 */
	public  IncrementalMagFreqDist calcNucleationMFD_forSect(int sectIndex, double minMag, double maxMag, int numMag) {
		ArbIncrementalMagFreqDist mfd = new ArbIncrementalMagFreqDist(minMag, maxMag, numMag);
		List<Integer> rups = rupSet.getRupturesForSection(sectIndex);
		if (rups != null) {
			for (int r : rups) {
				double nucleationRate = getRateForRup(r)*rupSet.getAreaForSection(sectIndex)/rupSet.getAreaForRup(r);
				mfd.addResampledMagRate(rupSet.getMagForRup(r), nucleationRate, true);
			}
		}
		return mfd;
	}
	
	
	/**
	 * This give a Participation Mag Freq Dist for the specified section.
	 * This preserves rates rather than moRates (can't have both).
	 * @param sectIndex
	 * @param minMag - lowest mag in MFD
	 * @param maxMag - highest mag in MFD
	 * @param numMag - number of mags in MFD
	 * @return IncrementalMagFreqDist
	 */
	public IncrementalMagFreqDist calcParticipationMFD_forParentSect(int parentSectionID, double minMag, double maxMag, int numMag) {
		ArbIncrementalMagFreqDist mfd = new ArbIncrementalMagFreqDist(minMag, maxMag, numMag);
		List<Integer> rups = rupSet.getRupturesForParentSection(parentSectionID);
		if (rups != null) {
			for (int r : rups)
				mfd.addResampledMagRate(rupSet.getMagForRup(r), getRateForRup(r), true);
		}
		return mfd;
	}
	
	
	/**
	 * This give a Participation Mag Freq Dist for the specified section.
	 * This preserves rates rather than moRates (can't have both).
	 * @param sectIndex
	 * @param minMag - lowest mag in MFD
	 * @param maxMag - highest mag in MFD
	 * @param numMag - number of mags in MFD
	 * @return IncrementalMagFreqDist
	 */
	public IncrementalMagFreqDist calcParticipationMFD_forSect(int sectIndex, double minMag, double maxMag, int numMag) {
		ArbIncrementalMagFreqDist mfd = new ArbIncrementalMagFreqDist(minMag, maxMag, numMag);
		List<Integer> rups = rupSet.getRupturesForSection(sectIndex);
		if (rups != null) {
			for (int r : rups)
				mfd.addResampledMagRate(rupSet.getMagForRup(r), getRateForRup(r), true);
		}
		return mfd;
	}
	
	/**
	 * This gives the total nucleation Mag Freq Dist of this solution.  
	 * This preserves rates rather than moRates (can't have both).
	 * @param minMag - lowest mag in MFD
	 * @param maxMag - highest mag in MFD
	 * @param delta - width of each mfd bin
	 * @return IncrementalMagFreqDist
	 */
	public IncrementalMagFreqDist calcTotalNucleationMFD(double minMag, double maxMag, double delta) {
		return calcNucleationMFD_forRegion(null, minMag, maxMag, delta, true);
	}

	/**
	 * This gives the total nucleation Mag Freq Dist inside the supplied region.  
	 * If <code>traceOnly == true</code>, only the rupture trace is examined in computing the fraction of the rupture 
	 * inside the region.  This preserves rates rather than moRates (can't have both).
	 * @param region - a Region object
	 * @param minMag - lowest mag in MFD
	 * @param maxMag - highest mag in MFD
	 * @param delta - width of each mfd bin
	 * @param traceOnly - if true only fault traces will be used for fraction inside region calculations, otherwise the
	 * entire rupture surfaces will be used (slower)
	 * @return IncrementalMagFreqDist
	 */
	public IncrementalMagFreqDist calcNucleationMFD_forRegion(Region region, double minMag, double maxMag, double delta, boolean traceOnly) {
		int numMag = (int)((maxMag - minMag) / delta+0.5) + 1;
		return calcNucleationMFD_forRegion(region, minMag, maxMag, numMag, traceOnly);
	}

	/**
	 * This gives the total nucleation Mag Freq Dist inside the supplied region.  
	 * If <code>traceOnly == true</code>, only the rupture trace is examined in computing the fraction of the rupture
	 * inside the region.  This preserves rates rather than moRates (can't have both).
	 * @param region - a Region object
	 * @param minMag - lowest mag in MFD
	 * @param maxMag - highest mag in MFD
	 * @param numMag - number of mags in MFD
	 * @param traceOnly - if true only fault traces will be used for fraction inside region calculations, otherwise the
	 * entire rupture surfaces will be used (slower)
	 * @return IncrementalMagFreqDist
	 */
	public IncrementalMagFreqDist calcNucleationMFD_forRegion(Region region, double minMag, double maxMag, int numMag, boolean traceOnly) {
		ArbIncrementalMagFreqDist mfd = new ArbIncrementalMagFreqDist(minMag, maxMag, numMag);
		double[] fractRupsInside = null;
		if (region != null)
			fractRupsInside = rupSet.getFractRupsInsideRegion(region, traceOnly);
		for(int r=0;r<rupSet.getNumRuptures();r++) {
			double fractInside = 1;
			if (region != null)
				fractInside = fractRupsInside[r];
			double rateInside=getRateForRup(r)*fractInside;
//			if (fractInside < 1)
//				System.out.println("inside: "+fractInside+"\trate: "+rateInside+"\tID: "+r);
			mfd.addResampledMagRate(rupSet.getMagForRup(r), rateInside, true);
		}
		return mfd;
	}
	
	/**
	 * This plots the rupture rates (rate versus rupture index)
	 */
	public void plotRuptureRates() {
		// Plot the rupture rates
		ArrayList funcs = new ArrayList();		
		EvenlyDiscretizedFunc ruprates = new EvenlyDiscretizedFunc(0,(double)rupSet.getNumRuptures()-1,rupSet.getNumRuptures());
		for(int i=0; i<rupSet.getNumRuptures(); i++)
			ruprates.set(i,getRateForRup(i));
		funcs.add(ruprates); 	
		GraphWindow graph = new GraphWindow(funcs, "Solution Rupture Rates"); 
		graph.setX_AxisLabel("Rupture Index");
		graph.setY_AxisLabel("Rate");

	}
	
	/**
	 * This compares observed paleo event rates (supplied) with those implied by the
	 * Fault System Solution.
	 * 
	 */
	public void plotPaleoObsAndPredPaleoEventRates(List<PaleoRateConstraint> paleoRateConstraints, PaleoProbabilityModel paleoProbModel, InversionFaultSystemRupSet rupSet) {
		int numSections = rupSet.getNumSections();
		int numRuptures = rupSet.getNumRuptures();
		ArrayList funcs3 = new ArrayList();		
		EvenlyDiscretizedFunc finalEventRateFunc = new EvenlyDiscretizedFunc(0,(double)numSections-1,numSections);
		EvenlyDiscretizedFunc finalPaleoVisibleEventRateFunc = new EvenlyDiscretizedFunc(0,(double)numSections-1,numSections);	
		for (int r=0; r<numRuptures; r++) {
			List<Integer> sectsInRup= rupSet.getSectionsIndicesForRup(r);
			for (int i=0; i<sectsInRup.size(); i++) {			
				finalEventRateFunc.add(sectsInRup.get(i),getRateForRup(r));  
				
				// UCERF2 Paleo Prob Model
//				finalPaleoVisibleEventRateFunc.add(rup.get(i),rupSet.getProbPaleoVisible(rupSet.getMagForRup(r))*getRateForRup(r));  
				
				// UCERF3 Paleo Prob Model
				double paleoProb = paleoProbModel.getProbPaleoVisible(rupSet, r, sectsInRup.get(i));
				finalPaleoVisibleEventRateFunc.add(sectsInRup.get(i),paleoProb*getRateForRup(r));  
				
			}
		}	
		finalEventRateFunc.setName("Total Event Rates oer Section");
		finalPaleoVisibleEventRateFunc.setName("Paleo Visible Event Rates oer Section");
		funcs3.add(finalEventRateFunc);
		funcs3.add(finalPaleoVisibleEventRateFunc);	
		int num = paleoRateConstraints.size();
		ArbitrarilyDiscretizedFunc func;
		ArrayList obs_er_funcs = new ArrayList();
		PaleoRateConstraint constraint;
		double totalError=0;
		for (int c = 0; c < num; c++) {
			func = new ArbitrarilyDiscretizedFunc();
			constraint = paleoRateConstraints.get(c);
			int sectIndex = constraint.getSectionIndex();
			func.set((double) sectIndex - 0.0001, constraint.getLower95ConfOfRate());
			func.set((double) sectIndex, constraint.getMeanRate());
			func.set((double) sectIndex + 0.0001, constraint.getUpper95ConfOfRate());
			func.setName(constraint.getFaultSectionName());
			funcs3.add(func);
			double r=(constraint.getMeanRate()-finalPaleoVisibleEventRateFunc.getClosestYtoX(sectIndex))/(constraint.getUpper95ConfOfRate()-constraint.getLower95ConfOfRate());
			// System.out.println("Constraint #"+c+" misfit: "+r);
			totalError+=Math.pow(r,2);
		}			
		System.out.println("Event-rate constraint error = "+totalError);
		ArrayList<PlotCurveCharacterstics> plotChars = new ArrayList<PlotCurveCharacterstics>();
		plotChars.add(new PlotCurveCharacterstics(
				PlotLineType.SOLID, 2f, Color.BLACK));
		plotChars.add(new PlotCurveCharacterstics(
				PlotLineType.SOLID, 2f, Color.BLUE));
		for (int c = 0; c < num; c++)
			plotChars.add(new PlotCurveCharacterstics(
					PlotLineType.SOLID, 1f, PlotSymbol.FILLED_CIRCLE, 4f, Color.RED));
		GraphWindow graph3 =
				new GraphWindow(funcs3,
						"Synthetic Event Rates (total - black & paleo visible - blue) and Paleo Data (red)",
						plotChars);
		graph3.setX_AxisLabel("Fault Section Index");
		graph3.setY_AxisLabel("Event Rate (per year)");

	}
	
	/**
	 * This compares the MFDs in the given MFD constraints with the MFDs 
	 * implied by the Fault System Solution
	 * @param mfdConstraints
	 */
	public void plotMFDs(List<MFD_InversionConstraint> mfdConstraints) {
		// Add ALL_CA to bring up another plot
//		mfdConstraints.add(UCERF3_MFD_ConstraintFetcher.getTargetMFDConstraint(TimeAndRegion.ALL_CA_1850));
		
		for (int i=0; i<mfdConstraints.size(); i++) {  // Loop over each MFD constraint 	
			MFD_InversionConstraint mfdConstraint = mfdConstraints.get(i);
			Region region = mfdConstraint.getRegion();
			
//			boolean traceOnly = region.getArea() > (300 * 300);
			boolean traceOnly = true;
			IncrementalMagFreqDist magHist = calcNucleationMFD_forRegion(region, 5.05, 9.05, 0.1, traceOnly);
			
			System.out.println("Total solution moment/yr for "+mfdConstraints.get(i).getRegion().getName()+" region = "+magHist.getTotalMomentRate());
			ArrayList<IncrementalMagFreqDist> funcs4 = new ArrayList<IncrementalMagFreqDist>();
			magHist.setName("Magnitude Distribution of SA Solution");
			magHist.setInfo("(number in each mag bin)");
			funcs4.add(magHist);
			IncrementalMagFreqDist targetMagFreqDist = mfdConstraints.get(i).getMagFreqDist();; 
			targetMagFreqDist.setName("Target Magnitude Distribution");
			targetMagFreqDist.setInfo(mfdConstraints.get(i).getRegion().getName());
			funcs4.add(targetMagFreqDist);
			
			// OPTIONAL: Add UCERF2 plots for comparison (Target minus off-fault component with aftershocks added back in & Background Seismicity)
			UCERF2_MFD_ConstraintFetcher ucerf2Constraints = new UCERF2_MFD_ConstraintFetcher();
			ucerf2Constraints.setRegion(mfdConstraints.get(i).getRegion());
			IncrementalMagFreqDist ucerf2_OnFaultTargetMFD = ucerf2Constraints.getTargetMinusBackgroundMFD();
			ucerf2_OnFaultTargetMFD.setTolerance(0.1); 
			ucerf2_OnFaultTargetMFD.setName("UCERF2 Target minus background+aftershocks");
			ucerf2_OnFaultTargetMFD.setInfo(mfdConstraints.get(i).getRegion().getName());
			IncrementalMagFreqDist ucerf2_OffFaultMFD = ucerf2Constraints.getBackgroundSeisMFD();
			ucerf2_OffFaultMFD.setName("UCERF2 Background Seismicity MFD"); 
			funcs4.add(ucerf2_OnFaultTargetMFD); funcs4.add(ucerf2_OffFaultMFD);
			
			// OPTIONAL: Plot implied off-fault MFD % Total Target
			if (mfdConstraints.get(i).getRegion().getName()=="RELM_NOCAL Region") {
				IncrementalMagFreqDist totalTargetMFD = OLD_UCERF3_MFD_ConstraintFetcher.getTargetMFDConstraint(TimeAndRegion.NO_CA_1850).getMagFreqDist();
				IncrementalMagFreqDist offFaultMFD = new IncrementalMagFreqDist(totalTargetMFD.getMinX(), totalTargetMFD.size(), totalTargetMFD.getDelta());
				for (double m=totalTargetMFD.getMinX(); m<=totalTargetMFD.getMaxX(); m+=totalTargetMFD.getDelta()) {
					offFaultMFD.set(m, totalTargetMFD.getClosestYtoX(m) - magHist.getClosestYtoX(m));		
				}
				offFaultMFD.setName("Implied Off-fault MFD for Solution"); totalTargetMFD.setName("Total Seismicity Rate for Region");
				offFaultMFD.setInfo("Total Target minus on-fault solution");totalTargetMFD.setInfo("Northern CA 1850-2007");
				funcs4.add(totalTargetMFD); funcs4.add(offFaultMFD);
			}
			if (mfdConstraints.get(i).getRegion().getName()=="RELM_SOCAL Region") {
				IncrementalMagFreqDist totalTargetMFD = OLD_UCERF3_MFD_ConstraintFetcher.getTargetMFDConstraint(TimeAndRegion.SO_CA_1850).getMagFreqDist();
				IncrementalMagFreqDist offFaultMFD = new IncrementalMagFreqDist(totalTargetMFD.getMinX(), totalTargetMFD.size(), totalTargetMFD.getDelta());
				for (double m=totalTargetMFD.getMinX(); m<=totalTargetMFD.getMaxX(); m+=totalTargetMFD.getDelta()) {
					offFaultMFD.set(m, totalTargetMFD.getClosestYtoX(m) - magHist.getClosestYtoX(m));
					
				}
				offFaultMFD.setName("Implied Off-fault MFD for Solution"); totalTargetMFD.setName("Total Seismicity Rate for Region");
				offFaultMFD.setInfo("Total Target minus on-fault solution");totalTargetMFD.setInfo("Southern CA 1850-2007");
				funcs4.add(totalTargetMFD); funcs4.add(offFaultMFD);
			}
			if (mfdConstraints.get(i).getRegion().getName()=="RELM_TESTING Region") {
				IncrementalMagFreqDist totalTargetMFD = OLD_UCERF3_MFD_ConstraintFetcher.getTargetMFDConstraint(TimeAndRegion.ALL_CA_1850).getMagFreqDist();
				IncrementalMagFreqDist offFaultMFD = new IncrementalMagFreqDist(totalTargetMFD.getMinX(), totalTargetMFD.size(), totalTargetMFD.getDelta());
				for (double m=totalTargetMFD.getMinX(); m<=totalTargetMFD.getMaxX(); m+=totalTargetMFD.getDelta()) {
					offFaultMFD.set(m, totalTargetMFD.getClosestYtoX(m) - magHist.getClosestYtoX(m));
					
				}
				offFaultMFD.setName("Implied Off-fault MFD for Solution"); totalTargetMFD.setName("Total Seismicity Rate for Region");
				offFaultMFD.setInfo("Total Target minus on-fault solution");totalTargetMFD.setInfo("All CA 1850-2007");
				funcs4.add(totalTargetMFD); funcs4.add(offFaultMFD);
			}
			
			
			
			GraphWindow graph4 = new GraphWindow(funcs4, "Magnitude Histogram for Final Rates"); 
			graph4.setX_AxisLabel("Magnitude");
			graph4.setY_AxisLabel("Frequency (per bin)");
			graph4.setYLog(true);
			graph4.setY_AxisRange(1e-6, 1.0);
		}
	}
	
	/**
	 * This returns the total moment of the solution (this does not include any off fault moment).<br>
	 * <br>
	 * This is calculated as the sum of the rates or each rupture times its moment (which is calculated form the magnitude)
	 * @return
	 */
	public double getTotalFaultSolutionMomentRate() {
		// calculate the moment
		double totalSolutionMoment = 0;
		for (int rup=0; rup<rupSet.getNumRuptures(); rup++) 
			totalSolutionMoment += getRateForRup(rup)*MagUtils.magToMoment(rupSet.getMagForRup(rup));
		return totalSolutionMoment;
	}
	
	/**
	 * Returns GridSourceProvider
	 * @return
	 */
	public GridSourceProvider getGridSourceProvider() {
		return gridSourceProvider;
	}
	
	public void setGridSourceProvider(GridSourceProvider gridSourceProvider) {
		this.gridSourceProvider = gridSourceProvider;
	}
	
	/**
	 * Return MFDs for the given rupture if present, otherwise null. DiscretizedFunc
	 * is returned as they often won't be evenly spaced and sum of y values will
	 * equal total rate for the rupture.
	 * @param rupIndex
	 * @return
	 */
	public DiscretizedFunc getRupMagDist(int rupIndex) {
		if (rupMFDs == null)
			return null;
		return rupMFDs[rupIndex];
	}
	
	/**
	 * Return MFDs for the each rupture if present, otherwise null. DiscretizedFunc
	 * is returned as they often won't be evenly spaced and sum of y values will
	 * equal total rate for the rupture.
	 * @param rupIndex
	 * @return
	 */
	public DiscretizedFunc[] getRupMagDists() {
		return rupMFDs;
	}
	
	/**
	 * sets MFDs for the each rupture (or null for no rup specific MFDs). DiscretizedFunc
	 * is returned as they often won't be evenly spaced and sum of y values should
	 * equal total rate for the rupture.
	 * @param rupMFDs rup MFD list or null
	 * @return
	 */
	public void setRupMagDists(DiscretizedFunc[] rupMFDs) {
		Preconditions.checkArgument(rupMFDs == null || rupMFDs.length == getRupSet().getNumRuptures());
		this.rupMFDs = rupMFDs;
	}
	
	/**
	 * This returns the list of final sub-seismo MFDs for each fault section (e.g., for use in an ERF),
	 * or null if not applicable to this FaultSystemSolution.
	 * @return
	 */
	public List<? extends IncrementalMagFreqDist> getSubSeismoOnFaultMFD_List() {
		return subSeismoOnFaultMFDs;
	}
	
	public void setSubSeismoOnFaultMFD_List(List<? extends IncrementalMagFreqDist> subSeismoOnFaultMFDs) {
		this.subSeismoOnFaultMFDs = subSeismoOnFaultMFDs;
	}

}
