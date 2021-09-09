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
import org.opensha.sha.earthquake.faultSysSolution.modules.InfoModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.RupMFDsModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.SubSeismoOnFaultMFDs;
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
 * TODO: deprecate
 * 
 * @author Field, Milner, Page, and Powers
 *
 */
public class U3FaultSystemSolution extends org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution implements Serializable {
	
	/**
	 * Default constructor, validates inputs
	 * @param rupSet
	 * @param rates
	 */
	public U3FaultSystemSolution(U3FaultSystemRupSet rupSet, double[] rates) {
		this(rupSet, rates, null);
	}
	
	/**
	 * Default constructor, validates inputs
	 * @param rupSet
	 * @param rates
	 * @param subSeismoOnFaultMFDs
	 */
	public U3FaultSystemSolution(U3FaultSystemRupSet rupSet, double[] rates,
			List<? extends IncrementalMagFreqDist> subSeismoOnFaultMFDs) {
		super(rupSet, rates);
		init(rupSet, rates, null, subSeismoOnFaultMFDs);
	}
	
	/**
	 * Returns the fault system rupture set for this solution
	 * @return
	 */
	public U3FaultSystemRupSet getRupSet() {
		return (U3FaultSystemRupSet)rupSet;
	}
	
	/**
	 * Builds a solution from the given rupSet/rates. If the rupSet is an InversionFaultSystemRupSet,
	 * an InversionFaultSystemSolution will be returned (else a normal FaultSystemSolution).
	 * @param rupSet
	 * @param rates
	 * @return
	 */
	public static U3FaultSystemSolution buildSolAsApplicable(U3FaultSystemRupSet rupSet, double[] rates) {
		if (rupSet instanceof InversionFaultSystemRupSet)
			return new InversionFaultSystemSolution((InversionFaultSystemRupSet)rupSet, rates);
		return new U3FaultSystemSolution(rupSet, rates);
	}
	
	/**
	 * Not recommended, must call init
	 */
	protected U3FaultSystemSolution() {
		
	}
	
	protected void init(U3FaultSystemRupSet rupSet, double[] rates, String infoString,
			List<? extends IncrementalMagFreqDist> subSeismoOnFaultMFDs) {
		super.init(rupSet, rates);
		if (infoString != null && !infoString.isBlank())
			addModule(new InfoModule(infoString));
		if (subSeismoOnFaultMFDs != null) {
			Preconditions.checkState(subSeismoOnFaultMFDs.size() == rupSet.getNumSections(),
					"Sub seismo MFD count and sub section count inconsistent");
			addModule(new SubSeismoOnFaultMFDs(subSeismoOnFaultMFDs));
		}
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
	 * Return MFDs for the given rupture if present, otherwise null. DiscretizedFunc
	 * is returned as they often won't be evenly spaced and sum of y values will
	 * equal total rate for the rupture.
	 * @param rupIndex
	 * @return
	 */
	public DiscretizedFunc getRupMagDist(int rupIndex) {
		RupMFDsModule rupMFDs = getModule(RupMFDsModule.class);
		if (rupMFDs == null)
			return null;
		return rupMFDs.getRuptureMFD(rupIndex);
	}
	
	/**
	 * Return MFDs for the each rupture if present, otherwise null. DiscretizedFunc
	 * is returned as they often won't be evenly spaced and sum of y values will
	 * equal total rate for the rupture.
	 * @param rupIndex
	 * @return
	 */
	public DiscretizedFunc[] getRupMagDists() {
		RupMFDsModule rupMFDs = getModule(RupMFDsModule.class);
		if (rupMFDs == null)
			return null;
		DiscretizedFunc[] mfds = new DiscretizedFunc[getRupSet().getNumRuptures()];
		for (int r=0; r<mfds.length; r++)
			mfds[r] = rupMFDs.getRuptureMFD(r);
		return mfds;
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
//		this.rupMFDs = rupMFDs;
		addModule(new RupMFDsModule(this, rupMFDs));
	}
	
	/**
	 * This returns the list of final sub-seismo MFDs for each fault section (e.g., for use in an ERF),
	 * or null if not applicable to this FaultSystemSolution.
	 * @return
	 */
	public List<? extends IncrementalMagFreqDist> getSubSeismoOnFaultMFD_List() {
		SubSeismoOnFaultMFDs subSeismoMFDs = getModule(SubSeismoOnFaultMFDs.class);
		if (subSeismoMFDs == null)
			return null;
		return subSeismoMFDs.getAll();
	}
	
	public void setSubSeismoOnFaultMFD_List(List<? extends IncrementalMagFreqDist> subSeismoOnFaultMFDs) {
		if (subSeismoOnFaultMFDs == null)
			removeModuleInstances(SubSeismoOnFaultMFDs.class);
		else
			addModule(new SubSeismoOnFaultMFDs(subSeismoOnFaultMFDs));
	}

}
