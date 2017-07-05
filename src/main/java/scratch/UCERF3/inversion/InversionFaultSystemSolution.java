package scratch.UCERF3.inversion;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.dom4j.DocumentException;
import org.jfree.chart.plot.DatasetRenderingOrder;
import org.opensha.commons.calc.FaultMomentCalc;
import org.opensha.commons.data.function.ArbDiscrEmpiricalDistFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.geo.Region;
import org.opensha.commons.geo.RegionUtils;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.util.ClassUtils;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.gui.infoTools.CalcProgressBar;
import org.opensha.commons.gui.plot.GraphWindow;
import org.opensha.sha.magdist.ArbIncrementalMagFreqDist;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.magdist.SummedMagFreqDist;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.SlipEnabledSolution;
import scratch.UCERF3.analysis.FaultSystemRupSetCalc;
import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.enumTreeBranches.InversionModels;
import scratch.UCERF3.enumTreeBranches.MaxMagOffFault;
import scratch.UCERF3.enumTreeBranches.MomentRateFixes;
import scratch.UCERF3.enumTreeBranches.SpatialSeisPDF;
import scratch.UCERF3.enumTreeBranches.TotalMag5Rate;
import scratch.UCERF3.griddedSeismicity.GridSourceProvider;
import scratch.UCERF3.griddedSeismicity.UCERF3_GridSourceGenerator;
import scratch.UCERF3.inversion.InversionConfiguration.SlipRateConstraintWeightingType;
import scratch.UCERF3.logicTree.LogicTreeBranch;
import scratch.UCERF3.logicTree.LogicTreeBranchNode;
import scratch.UCERF3.utils.FaultSystemIO;
import scratch.UCERF3.utils.MFD_InversionConstraint;
import scratch.UCERF3.utils.OLD_UCERF3_MFD_ConstraintFetcher;
import scratch.UCERF3.utils.SectionMFD_constraint;
import scratch.UCERF3.utils.OLD_UCERF3_MFD_ConstraintFetcher.TimeAndRegion;
import scratch.UCERF3.utils.aveSlip.AveSlipConstraint;
import scratch.UCERF3.utils.RELM_RegionUtils;
import scratch.UCERF3.utils.UCERF2_MFD_ConstraintFetcher;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * This is a FaultSystemSolution that also contains parameters used in the UCERF3 Inversion
 * 
 * @author kevin
 *
 */
public class InversionFaultSystemSolution extends SlipEnabledSolution {
	
	private InversionFaultSystemRupSet rupSet;
	
	private InversionModels invModel;
	private LogicTreeBranch branch;
	
	/**
	 * Inversion constraint weights and such. Note that this won't include the initial rup model or
	 * target MFDs and cannot be used as input to InversionInputGenerator.
	 */
	private InversionConfiguration inversionConfiguration;
	
	private Map<String, Double> energies;
	private Map<String, Double> misfits;
	
	/**
	 * Can be used on the fly for when InversionConfiguration/energies are not available/relevant
	 * 
	 * @param rupSet
	 * @param rates
	 */
	public InversionFaultSystemSolution(InversionFaultSystemRupSet rupSet, double[] rates) {
		this(rupSet, rates, null, null);
	}
	
	/**
	 * Default constructor, for post inversion or file loading.
	 * 
	 * @param rupSet
	 * @param rates
	 * @param config can be null
	 * @param energies can be null
	 */
	public InversionFaultSystemSolution(InversionFaultSystemRupSet rupSet, double[] rates,
			InversionConfiguration config, Map<String, Double> energies) {
		super();
		
		init(rupSet, rates, config, energies);
	}
	
	/**
	 * Empty constructor, can be used by subclasses when needed.
	 * Make sure to call init(...)!
	 */
	protected InversionFaultSystemSolution() {
		
	}

	/**
	 * Parses the info string for inversion parameters (depricated)
	 * 
	 * @param rupSet
	 * @param rates
	 */
	@Deprecated
	public InversionFaultSystemSolution(InversionFaultSystemRupSet rupSet, String infoString, double[] rates) {
		super(rupSet, rates);
		this.rupSet = rupSet;
		
		ArrayList<String> infoLines = Lists.newArrayList(Splitter.on("\n").split(infoString));
		
		// load inversion properties
		try {
			Map<String, String> invProps = loadProperties(getMetedataSection(infoLines, "Inversion Configuration Metadata"));
			loadInvParams(invProps);
		} catch (Exception e1) {
			System.err.println("Couldn't load in Legacy Inversion Properties: "+e1);
		}
		
		// load branch
		try {
			Map<String, String> branchProps = loadProperties(getMetedataSection(infoLines, "Logic Tree Branch"));
			branch = loadBranch(branchProps);
		} catch (Exception e1) {
			System.err.println("Couldn't load in Legacy Inversion Logic Tree: "+e1);
		} finally {
			if (branch == null) {
				// use logic tree branch if couldn't parse, or isn't fully specified
				branch = rupSet.getLogicTreeBranch();
			} else {
				// see if we can fill anything in from the rupSet
				LogicTreeBranch rBranch = rupSet.getLogicTreeBranch();
				for (int i=0; i<branch.size(); i++) {
					if (branch.getValue(i) == null && rBranch.getValue(i) != null)
						branch.setValue(rBranch.getValue(i));
				}
			}
		}
		invModel = branch.getValue(InversionModels.class);
		
		// load SA properties
		try {
			ArrayList<String> saMetadata = getMetedataSection(infoLines, "Simulated Annealing Metadata");
			if (saMetadata == null)
				saMetadata = Lists.newArrayList();
			Map<String, String> saProps = loadProperties(saMetadata);
			
			loadEnergies(saProps);
		} catch (Exception e1) {
			System.err.println("Couldn't load in Legacy SA Properties: "+e1);
		}
	}
	
	protected void init(InversionFaultSystemRupSet rupSet, double[] rates,
			InversionConfiguration config, Map<String, Double> energies) {
		super.init(rupSet, rates, rupSet.getInfoString(), null);
		this.rupSet = rupSet;
		this.branch = rupSet.getLogicTreeBranch();
		this.invModel = branch.getValue(InversionModels.class);
		
		// these can all be null
		this.inversionConfiguration = config;
		this.energies = energies;
	}
	
	@Override
	public InversionFaultSystemRupSet getRupSet() {
		return rupSet;
	}

	private Map<String, String> loadProperties(ArrayList<String> section) {
		Map<String, String> props = Maps.newHashMap();
		
		for (String line : section) {
			line = line.trim();
			int ind = line.indexOf(':');
			if (ind < 0)
				continue;
			String key = line.substring(0, ind);
			if (key.startsWith("relative")) {
				key = key.substring(8);
				if (key.startsWith("MFD"))
					key = "mfd"+key.substring(3);
				else if (Character.isUpperCase(key.charAt(0)))
					key = new String(key.charAt(0)+"").toLowerCase()+key.substring(1);
			}
			String value = line.substring(ind+1).trim();
			// this is a special case for a bug Morgan had where she didn't add a new line to the metadata field
			if (value.contains("weightSlipRates:")) {
				int badInd = value.indexOf("weightSlipRates:");
				String weightSlipsStr = value.substring(badInd);
				String weightVal = weightSlipsStr.substring(weightSlipsStr.indexOf(":")+1).trim();
				props.put("weightSlipRates", weightVal);
				value = value.substring(0, badInd);
			}
			props.put(key, value);
		}
		
		return props;
	}
	
	private ArrayList<String> getMetedataSection(ArrayList<String> lines, String title) {
		ArrayList<String> section = null;
		
		for (String line : lines) {
			if (section == null) {
				if (line.contains("*") && line.contains(title)) {
					section = new ArrayList<String>();
					continue;
				}
			} else {
				if (line.contains("*"))
					break;
				section.add(line);
			}
		}
		
		return section;
	}
	
	/**
	 * Legacy solutions use this
	 * @param props
	 * @return
	 */
	private LogicTreeBranch loadBranch(Map<String, String> props) {
		List<Class<? extends LogicTreeBranchNode<?>>> classes = LogicTreeBranch.getLogicTreeNodeClasses();
		
		List<LogicTreeBranchNode<?>> values = Lists.newArrayList();
		
		for (String key : props.keySet()) {
			// find the associated class
			Class<? extends LogicTreeBranchNode<?>> clazz = null;
			for (Class<? extends LogicTreeBranchNode<?>> testClass : classes) {
				String className = ClassUtils.getClassNameWithoutPackage(testClass);
				if (className.startsWith(key)) {
					clazz = testClass;
					break;
				}
			}
			Preconditions.checkNotNull(clazz, "Couldn't find class for logic tree branch: "+key);
			
			String valueName = props.get(key);
			if (valueName.equals("RATE_10p6"))
				valueName = "RATE_10p0";
			LogicTreeBranchNode<?> value = null;
			for (LogicTreeBranchNode<?> testValue : clazz.getEnumConstants()) {
				if (testValue.name().equals(valueName)) {
					value = testValue;
					break;
				}
			}
			Preconditions.checkNotNull(value, "Couldn't find matching constant for logic tree value "+key+" (node="
					+ClassUtils.getClassNameWithoutPackage(clazz)+")"+" (val="+props.get(key)+")");
			values.add(value);
		}
		
		return LogicTreeBranch.fromValues(values);
	}
	
//	private double offFaultAseisFactor = Double.NaN;
//	private double mfdConstraintModifier = Double.NaN;
//	private boolean ucerf3MFDs = true;
//	private double bilinearTransitionMag = Double.NaN;
//	private double MFDTransitionMag = Double.NaN;
//	private boolean weightSlipRates = true;
//	private double relativePaleoRateWt = Double.NaN;
//	private double relativeMagnitudeEqualityConstraintWt = Double.NaN;
//	private double relativeMagnitudeInequalityConstraintWt = Double.NaN;
//	private double relativeRupRateConstraintWt = Double.NaN;
//	private double relativeParticipationSmoothnessConstraintWt = Double.NaN;
//	private double relativeMinimizationConstraintWt = Double.NaN;
//	private double relativeMomentConstraintWt = Double.NaN;
//	private double minimumRuptureRateFraction = Double.NaN;
	
	private void loadInvParams(Map<String, String> props) {
		double MFDTransitionMag = Double.NaN;
		double slipRateConstraintWt_normalized = Double.NaN;
		double slipRateConstraintWt_unnormalized = Double.NaN;
		SlipRateConstraintWeightingType slipRateWeighting = null;
		double paleoRateConstraintWt = Double.NaN;
		double paleoSlipConstraintWt = Double.NaN;
		double magnitudeEqualityConstraintWt = Double.NaN;
		double magnitudeInequalityConstraintWt = Double.NaN;
		double rupRateConstraintWt = Double.NaN;
		double rupRateSmoothingConstraintWt = Double.NaN;
		double participationSmoothnessConstraintWt = Double.NaN;
		double participationConstraintMagBinSize = Double.NaN;
		double nucleationMFDConstraintWt = Double.NaN;
		double mfdSmoothnessConstraintWt = Double.NaN;
		double mfdSmoothnessConstraintWtForPaleoParents = Double.NaN;
		double minimizationConstraintWt = Double.NaN;
		double momentConstraintWt = Double.NaN;
		double parkfieldConstraintWt = Double.NaN;
		double smoothnessWt = Double.NaN;
		double eventRateSmoothnessWt = Double.NaN;
		double minimumRuptureRateFraction = Double.NaN;		// water level parameter
		
		if (props.containsKey("MFDTransitionMag"))
			MFDTransitionMag = Double.parseDouble(props.get("MFDTransitionMag"));
		if (props.containsKey("slipRateWeighting")) {
			// new style
			slipRateWeighting = SlipRateConstraintWeightingType.valueOf(props.get("slipRateWeighting"));
			if (props.containsKey("slipRateConstraintWt_normalized"))
				slipRateConstraintWt_normalized =
						Double.parseDouble(props.get("slipRateConstraintWt_normalized"));
			if (props.containsKey("slipRateConstraintWt_unnormalized"))
				slipRateConstraintWt_unnormalized =
						Double.parseDouble(props.get("slipRateConstraintWt_unnormalized"));
		} else {
			// old
			boolean weightSlipRates = true;
			if (props.containsKey("weightSlipRates"))
				weightSlipRates = Boolean.parseBoolean(props.get("weightSlipRates"));
			double wt = 0;
			if (props.containsKey("slipRateConstraintWt"))
				wt = Double.parseDouble(props.get("slipRateConstraintWt"));
			else
				wt = 1d;
			if (weightSlipRates) {
				slipRateConstraintWt_normalized = wt;
				slipRateConstraintWt_unnormalized = 0;
			} else {
				slipRateConstraintWt_normalized = 0;
				slipRateConstraintWt_unnormalized = wt;
			}
		}
		if (props.containsKey("paleoRateConstraintWt"))
			paleoRateConstraintWt = Double.parseDouble(props.get("paleoRateConstraintWt"));
		else if (props.containsKey("paleoRateWt"))
			paleoRateConstraintWt = Double.parseDouble(props.get("paleoRateWt"));
		if (props.containsKey("paleoSlipConstraintWt"))
			paleoSlipConstraintWt = Double.parseDouble(props.get("paleoSlipConstraintWt"));
		else if (props.containsKey("paleoSlipWt"))
			paleoSlipConstraintWt = Double.parseDouble(props.get("paleoSlipWt"));
		if (props.containsKey("magnitudeEqualityConstraintWt"))
			magnitudeEqualityConstraintWt = Double.parseDouble(props.get("magnitudeEqualityConstraintWt"));
		if (props.containsKey("magnitudeInequalityConstraintWt"))
			magnitudeInequalityConstraintWt = Double.parseDouble(props.get("magnitudeInequalityConstraintWt"));
		if (props.containsKey("rupRateConstraintWt"))
			rupRateConstraintWt = Double.parseDouble(props.get("rupRateConstraintWt"));
		if (props.containsKey("rupRateSmoothingConstraintWt"))
			rupRateSmoothingConstraintWt = Double.parseDouble(props.get("rupRateSmoothingConstraintWt"));
		if (props.containsKey("participationSmoothnessConstraintWt"))
			participationSmoothnessConstraintWt = Double.parseDouble(props.get("participationSmoothnessConstraintWt"));
		if (props.containsKey("participationConstraintMagBinSize"))
			participationConstraintMagBinSize = Double.parseDouble(props.get("participationConstraintMagBinSize"));
		if (props.containsKey("nucleationMFDConstraintWt"))
			nucleationMFDConstraintWt = Double.parseDouble(props.get("nucleationMFDConstraintWt"));
		if (props.containsKey("mfdSmoothnessConstraintWt"))
			mfdSmoothnessConstraintWt = Double.parseDouble(props.get("mfdSmoothnessConstraintWt"));
		if (props.containsKey("mfdSmoothnessConstraintWtForPaleoParents"))
			mfdSmoothnessConstraintWtForPaleoParents = Double.parseDouble(props.get("mfdSmoothnessConstraintWtForPaleoParents"));
		if (props.containsKey("minimizationConstraintWt"))
			minimizationConstraintWt = Double.parseDouble(props.get("minimizationConstraintWt"));
		if (props.containsKey("momentConstraintWt"))
			momentConstraintWt = Double.parseDouble(props.get("momentConstraintWt"));
		if (props.containsKey("parkfieldConstraintWt"))
			parkfieldConstraintWt = Double.parseDouble(props.get("parkfieldConstraintWt"));
		if (props.containsKey("smoothnessWt"))
			smoothnessWt = Double.parseDouble(props.get("smoothnessWt"));
		if (props.containsKey("eventRateSmoothnessWt"))
			eventRateSmoothnessWt = Double.parseDouble(props.get("eventRateSmoothnessWt"));
		if (props.containsKey("minimumRuptureRateFraction"))
			minimumRuptureRateFraction = Double.parseDouble(props.get("minimumRuptureRateFraction"));
		
		inversionConfiguration = new InversionConfiguration(
				slipRateConstraintWt_normalized, slipRateConstraintWt_unnormalized, slipRateWeighting, paleoRateConstraintWt, paleoSlipConstraintWt,
				magnitudeEqualityConstraintWt, magnitudeInequalityConstraintWt, rupRateConstraintWt,
				participationSmoothnessConstraintWt, participationConstraintMagBinSize, nucleationMFDConstraintWt,
				mfdSmoothnessConstraintWt, mfdSmoothnessConstraintWtForPaleoParents, rupRateSmoothingConstraintWt,
				minimizationConstraintWt, momentConstraintWt, parkfieldConstraintWt, null,
				null, null, smoothnessWt, eventRateSmoothnessWt, MFDTransitionMag,
				null, null, minimumRuptureRateFraction, null);
	}
	
	private void loadEnergies(Map<String, String> props) {
		energies = Maps.newHashMap();
		
		for (String key : props.keySet()) {
			if (!key.contains("energy"))
				continue;
			if (key.contains("Best") || key.contains("breakdown"))
				continue;
			double val = Double.parseDouble(props.get(key));
			key = key.trim();
			energies.put(key, val);
		}
	}
	
	public Map<String, Double> getEnergies() {
		return energies;
	}
	
	/**
	 * This returns the energies scaled by their weights, should be a fair comparison amung runs even with different weights.
	 * @return
	 */
	public synchronized Map<String, Double> getMisfits() {
		if (misfits == null) {
			misfits = getMisfits(energies, inversionConfiguration);
		}
		return misfits;
	}
	
	public static Map<String, Double> getMisfits(
			Map<String, Double> energies, InversionConfiguration inversionConfiguration) {
		Map<String, Double> misfits = Maps.newHashMap();
		
		if (energies == null)
			return misfits;
		
		for (String energyStr : energies.keySet()) {
			double eVal = energies.get(energyStr);
			double wt;
			if (energyStr.contains("energy"))
				// legacy text parsing will have this
				energyStr = energyStr.substring(0, energyStr.indexOf("energy")).trim();
			if (energyStr.equals("Slip Rate")) {
				switch (inversionConfiguration.getSlipRateWeightingType()) {
				case NORMALIZED_BY_SLIP_RATE:
					wt = inversionConfiguration.getSlipRateConstraintWt_normalized();
					break;
				case UNNORMALIZED:
					wt = inversionConfiguration.getSlipRateConstraintWt_unnormalized();
					break;
				case BOTH:
					System.out.println("WARNING: misfits inaccurate for slip rate since both weights used");
					if (inversionConfiguration.getSlipRateConstraintWt_normalized() >
						inversionConfiguration.getSlipRateConstraintWt_unnormalized())
						wt = inversionConfiguration.getSlipRateConstraintWt_normalized();
					else
						wt = inversionConfiguration.getSlipRateConstraintWt_unnormalized();
					break;

				default:
					throw new IllegalStateException("Can't get here");
				}
				
			} else if (energyStr.equals("Paleo Event Rates"))
				wt = inversionConfiguration.getPaleoRateConstraintWt();
			else if (energyStr.equals("Paleo Slips"))
				wt = inversionConfiguration.getPaleoSlipConstraintWt();
			else if (energyStr.equals("Rupture Rates"))
				wt = inversionConfiguration.getRupRateConstraintWt();
			else if (energyStr.equals("Rupture Rate Smoothing"))
				wt = inversionConfiguration.getRupRateSmoothingConstraintWt();
			else if (energyStr.equals("Minimization"))
				wt = inversionConfiguration.getMinimizationConstraintWt();
			else if (energyStr.equals("MFD Equality"))
				wt = inversionConfiguration.getMagnitudeEqualityConstraintWt();
			else if (energyStr.equals("MFD Participation"))
				wt = inversionConfiguration.getParticipationSmoothnessConstraintWt();
			else if (energyStr.equals("MFD Nucleation"))
				wt = inversionConfiguration.getNucleationMFDConstraintWt();
			else if (energyStr.equals("MFD Smoothness")) {
				if (inversionConfiguration.getMFDSmoothnessConstraintWt() > 0
						&& inversionConfiguration.getMFDSmoothnessConstraintWtForPaleoParents() > 0)
					wt = Double.NaN; // impossible to make a fair comparison here when both weights are mixed
				else if (inversionConfiguration.getMFDSmoothnessConstraintWt() > 0)
					wt = inversionConfiguration.getMFDSmoothnessConstraintWt();
				else if (inversionConfiguration.getMFDSmoothnessConstraintWtForPaleoParents() > 0)
					wt = inversionConfiguration.getMFDSmoothnessConstraintWtForPaleoParents();
				else
					wt = 0d;
			} else if (energyStr.equals("Moment"))
				wt = inversionConfiguration.getMomentConstraintWt();
			else if (energyStr.equals("Parkfield"))
				wt = inversionConfiguration.getParkfieldConstraintWt();
			else if (energyStr.equals("Event-Rate Smoothness"))
				wt = inversionConfiguration.getEventRateSmoothnessWt();
			else
				throw new IllegalStateException("Unknown Energy Type: "+energyStr);
			double misfit = eVal / (wt*wt);
			System.out.println(energyStr+": "+eVal+" / ("+wt+")^2 = "+misfit);
			misfits.put(energyStr, misfit);
		}
		
		return misfits;
	}

	public InversionModels getInvModel() {
		return invModel;
	}

	public LogicTreeBranch getLogicTreeBranch() {
		return branch;
	}

	/**
	 * Inversion constraint weights and such. Note that this won't include the initial rup model or
	 * target MFDs and cannot be used as input to InversionInputGenerator.
	 * @return
	 */
	public InversionConfiguration getInversionConfiguration() {
		return inversionConfiguration;
	}

	/**
	 * This compares the MFDs in the given MFD constraints with the MFDs 
	 * implied by the Fault System Solution
	 * @param mfdConstraints
	 */
	public void plotMFDs() {
		UCERF2_MFD_ConstraintFetcher ucerf2Fetch = new UCERF2_MFD_ConstraintFetcher();
		
		// make sure it's instantiated
		InversionTargetMFDs inversionTargetMFDs = rupSet.getInversionTargetMFDs();
		
		// Statewide
		GraphWindow gw = getMFDPlotWindow(inversionTargetMFDs.getTotalTargetGR(), inversionTargetMFDs.getOnFaultSupraSeisMFD(),
				RELM_RegionUtils.getGriddedRegionInstance(), ucerf2Fetch);
		gw.setVisible(true);
		
		gw = getMFDPlotWindow(inversionTargetMFDs.getTotalTargetGR_NoCal(), inversionTargetMFDs.noCalTargetSupraMFD,
				RELM_RegionUtils.getNoCalGriddedRegionInstance(), ucerf2Fetch);
		gw.setVisible(true);
		
		gw = getMFDPlotWindow(inversionTargetMFDs.getTotalTargetGR_SoCal(), inversionTargetMFDs.soCalTargetSupraMFD,
				RELM_RegionUtils.getSoCalGriddedRegionInstance(), ucerf2Fetch);
		gw.setVisible(true);
	}
	
	private boolean isStatewideDM() {
		return rupSet.getDeformationModel() != DeformationModels.UCERF2_BAYAREA
				&& rupSet.getDeformationModel() != DeformationModels.UCERF2_NCAL;
	}
	
	public GraphWindow getMFDPlotWindow(IncrementalMagFreqDist totalMFD, IncrementalMagFreqDist targetMFD, Region region,
			UCERF2_MFD_ConstraintFetcher ucerf2Fetch) {
		
		PlotSpec spec = getMFDPlots(totalMFD, targetMFD, region, ucerf2Fetch);
		
		GraphWindow gw = new GraphWindow(spec.getPlotElems(), spec.getTitle(), spec.getChars(), true);
		
		gw.setTickLabelFontSize(14);
		gw.setAxisLabelFontSize(16);
		gw.setPlotLabelFontSize(18);
		gw.setX_AxisLabel(spec.getXAxisLabel());
		gw.setY_AxisLabel(spec.getYAxisLabel());
		gw.setYLog(true);
		gw.setY_AxisRange(1e-6, 1.0);
		
		gw.getGraphWidget().setPlottingOrder(DatasetRenderingOrder.FORWARD);
		
		return gw;
	}
	
	public HeadlessGraphPanel getHeadlessMFDPlot(PlotSpec spec, IncrementalMagFreqDist totalMFD) {
		
		HeadlessGraphPanel gp = new HeadlessGraphPanel();
		CommandLineInversionRunner.setFontSizes(gp);
		gp.setYLog(true);
		gp.setRenderingOrder(DatasetRenderingOrder.FORWARD);
		double minX = totalMFD.getMinX();
		if (minX < 5)
			minX = 5;
		gp.setUserBounds(minX, totalMFD.getMaxX(),
				1e-6, 1.0);
		gp.drawGraphPanel(spec);
		
		return gp;
	}
	
	/**
	 * This generates Nucleation MFD plots for the given region. The inversion and overall target MFDs are
	 * passed in.
	 * 
	 * @param totalMFD
	 * @param targetMFD
	 * @param region
	 * @param ucerf2Fetch
	 * @return
	 */
	public PlotSpec getMFDPlots(IncrementalMagFreqDist totalMFD, IncrementalMagFreqDist targetMFD, Region region,
			UCERF2_MFD_ConstraintFetcher ucerf2Fetch) {
		
		ArrayList<DiscretizedFunc> funcs = new ArrayList<DiscretizedFunc>();
		ArrayList<PlotCurveCharacterstics> chars = new ArrayList<PlotCurveCharacterstics>();
		
		boolean statewide = region.getName().startsWith("RELM_TESTING");
		
		IncrementalMagFreqDist solMFD;
//		if (statewide)
//			solMFD = calcNucleationMFD_forRegion(null, // null since we want everything
//					totalMFD.getMinX(), 9.05, 0.1, true);
//		else
			solMFD = calcNucleationMFD_forRegion(region,
					totalMFD.getMinX(), 9.05, 0.1, true);
		solMFD.setName("Solution Supra-Seis MFD");
		solMFD.setInfo("Inversion Solution MFD");
		funcs.add(solMFD);
		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2, Color.BLUE));
		
		// Overall Target
		funcs.add(totalMFD);
		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2, Color.BLACK));
		
		// Inversion Target
		funcs.add(targetMFD);
		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2, Color.CYAN));
		
		// TODO Kevin add dashed line back in?
//		if (mfdConstraintModifier != 0 && mfdConstraintModifier != 1) {
//			// This is the overall target before it was multiplied
//			IncrementalMagFreqDist rolledBack = newSameRange(totalMFD);
//			for (int i=0; i<rolledBack.getNum(); i++) {
//				rolledBack.set(i, totalMFD.getY(i) / mfdConstraintModifier);
//			}
//			rolledBack.setName("Unmodified Original Target MFD");
//			rolledBack.setInfo("Total Target MFD without the mfdConstraintModifier of "+mfdConstraintModifier+" applied");
//			funcs.add(rolledBack);
//			chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 1, Color.BLACK));
//		}
		
		// this data is only available for the Statewide case
		IncrementalMagFreqDist solGriddedMFD = null;
		// this could be cleaner :-/
		if (statewide) {
			solGriddedMFD = getFinalTotalGriddedSeisMFD();
			funcs.add(solGriddedMFD);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2, Color.GRAY));
		}
//		} else
//			solOffFaultMFD = getImpliedOffFaultMFD(totalMFD, solMFD);
		
		// UCERF2 comparisons
		ucerf2Fetch.setRegion(region);
//		IncrementalMagFreqDist ucerf2_OnFaultTargetMFD = ucerf2Fetch.getTargetMinusBackgroundMFD();
//		ucerf2_OnFaultTargetMFD.setTolerance(0.1); 
//		ucerf2_OnFaultTargetMFD.setName("UCERF2 Target minus background+aftershocks");
//		ucerf2_OnFaultTargetMFD.setInfo(region.getName());
		IncrementalMagFreqDist ucerf2_OffFaultMFD = ucerf2Fetch.getBackgroundSeisMFD();
		ucerf2_OffFaultMFD.setName("UCERF2 Background Seismicity MFD"); 
//		funcs.add(ucerf2_OnFaultTargetMFD);
//		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1, Color.GREEN));
		funcs.add(0, ucerf2_OffFaultMFD);
		chars.add(0, new PlotCurveCharacterstics(PlotLineType.SOLID, 1, Color.MAGENTA));
		
		if (solGriddedMFD != null) {
			// total sum
			SummedMagFreqDist totalModelMFD = new SummedMagFreqDist(solMFD.getMinX(), solMFD.getMaxX(), solMFD.size());
//			System.out.println(solMFD.getMinX()+"\t"+solMFD.getMaxX()+"\t"+solMFD.getNum());
//			System.out.println(solOffFaultMFD.getMinX()+"\t"+solOffFaultMFD.getMaxX()+"\t"+solOffFaultMFD.getNum());
			totalModelMFD.addIncrementalMagFreqDist(solMFD);
			totalModelMFD.addIncrementalMagFreqDist(solGriddedMFD);
			totalModelMFD.setName("Total Model Solution MFD");
			funcs.add(totalModelMFD);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2, Color.RED));
		}
		
		String plotTitle = region.getName();
		if (plotTitle == null || plotTitle.isEmpty())
			plotTitle = "Unnamed Region";
		
		return new PlotSpec(funcs, chars, plotTitle, "Magnitude", "Incremental Rate (per yr)");
	}
	
	/**
	 * This returns the list of final sub-seismo MFDs for each fault section (e.g., for use in an ERF).  
	 * What's returned is getInversionMFDs().getTargetSubSeismoOnFaultMFD_List() unless
	 * it's a noFix/GR branch, in which case it returns getImpliedSubSeisGR_MFD_List() to
	 * account for any inversion imposed slip-rate changes.
	 * @return
	 */
	public List<GutenbergRichterMagFreqDist> getFinalSubSeismoOnFaultMFD_List() {
		List<GutenbergRichterMagFreqDist> subSeisMFD_list;
		// make sure we deal with special case for GR moFix branch
		boolean noFix = branch.getValue(MomentRateFixes.class) == MomentRateFixes.NONE;
		boolean gr = branch.getValue(InversionModels.class).isGR();
		// get post-inversion MFDs
		if (noFix && gr) {
			subSeisMFD_list = getImpliedSubSeisGR_MFD_List();	// calculate from final slip rates
		} else {
			subSeisMFD_list = rupSet.getInversionTargetMFDs().getSubSeismoOnFaultMFD_List();
		}
		return subSeisMFD_list;
	}
	
	
	@Override
	public synchronized List<? extends IncrementalMagFreqDist> getSubSeismoOnFaultMFD_List() {
		if (subSeismoOnFaultMFDs == null)
			subSeismoOnFaultMFDs = getFinalSubSeismoOnFaultMFD_List();
		return super.getSubSeismoOnFaultMFD_List();
	}

	/**
	 * This returns the list of final supra-seismo nucleation MFDs for each fault section, where
	 * each comes from the calcNucleationMFD_forSect(*) method of the parent.  
	 * @param minMag - lowest mag in MFD
	 * @param maxMag - highest mag in MFD
	 * @param numMag - number of mags in MFD
	 * @return List<IncrementalMagFreqDist>
	 */
	public List<IncrementalMagFreqDist> getFinalSupraSeismoOnFaultMFD_List(double minMag, double maxMag, int numMag) {
		ArrayList<IncrementalMagFreqDist> mfdList = new ArrayList<IncrementalMagFreqDist>();
		for(int s=0; s<rupSet.getNumSections();s++)
			mfdList.add(calcNucleationMFD_forSect(s, minMag, maxMag, numMag));
		return mfdList;
	}

	
	/**
	 * This returns the sum of the supra-seis and sub-seis MFDs for the section
	 * @param sectIndex
	 * @param minMag
	 * @param maxMag
	 * @param numMag
	 * @return
	 */
	public  IncrementalMagFreqDist getFinalTotalNucleationMFD_forSect(int sectIndex, double minMag, double maxMag, int numMag) {
		IncrementalMagFreqDist supraMFD = this.calcNucleationMFD_forSect(sectIndex, minMag, maxMag, numMag);
		GutenbergRichterMagFreqDist subSeisMFD = getFinalSubSeismoOnFaultMFD_List().get(sectIndex);
//		System.out.println("Subseismo:\n"+subSeisMFD+"\nsupra-seismo\n"+supraMFD);
		ArbIncrementalMagFreqDist mfd = new ArbIncrementalMagFreqDist(minMag, maxMag, numMag);
		for(int i=0;i<numMag;i++) {
			double mag = mfd.getX(i);
			mfd.set(i,supraMFD.getY(mag)+subSeisMFD.getY(mag));
		}
		return mfd;
	}

	
	
	/**
	 * This returns the final total sub-seismo on-fault MFD (the sum of what's returned by getFinalSubSeismoOnFaultMFD_List())
	 * @return
	 */
	public SummedMagFreqDist getFinalTotalSubSeismoOnFaultMFD() {
		SummedMagFreqDist totalSubSeismoOnFaultMFD = new SummedMagFreqDist(InversionTargetMFDs.MIN_MAG, InversionTargetMFDs.NUM_MAG, InversionTargetMFDs.DELTA_MAG);
		for(GutenbergRichterMagFreqDist mfd: getFinalSubSeismoOnFaultMFD_List()) {
			totalSubSeismoOnFaultMFD.addIncrementalMagFreqDist(mfd);
		}
		totalSubSeismoOnFaultMFD.setName("InversionFaultSystemSolution.getFinalTotalSubSeismoOnFaultMFD()");
		return totalSubSeismoOnFaultMFD;
	}
	
	public SummedMagFreqDist getFinalSubSeismoOnFaultMFDForParent(int parentSectionID) {
		
		SummedMagFreqDist mfd = new SummedMagFreqDist(InversionTargetMFDs.MIN_MAG, InversionTargetMFDs.NUM_MAG, InversionTargetMFDs.DELTA_MAG);
		
		List<GutenbergRichterMagFreqDist> subSeismoMFDs = getFinalSubSeismoOnFaultMFD_List();
		
		for (int sectIndex=0; sectIndex<rupSet.getNumSections(); sectIndex++) {
			if (rupSet.getFaultSectionData(sectIndex).getParentSectionId() != parentSectionID)
				continue;
			mfd.addIncrementalMagFreqDist(subSeismoMFDs.get(sectIndex));
		}
		
		return mfd;
	}
	
	/**
	 * This computes the subseismogenic MFD for each section using the final (post-inversion) slip rates
	 * assuming the section nucleates a perfect GR (after moment balancing, values above and equal to
	 * getMinMagForSection(s) are set to zero).
	 * @return
	 */
	private ArrayList<GutenbergRichterMagFreqDist> getImpliedSubSeisGR_MFD_List() {
		
		double minMag = InversionTargetMFDs.MIN_MAG;
		double deltaMag = InversionTargetMFDs.DELTA_MAG;
		int numMag = InversionTargetMFDs.NUM_MAG;
		ArrayList<GutenbergRichterMagFreqDist> grNuclMFD_List = new ArrayList<GutenbergRichterMagFreqDist>();
		GutenbergRichterMagFreqDist tempGR = new GutenbergRichterMagFreqDist(minMag, numMag, deltaMag);
		for(int s=0; s<rupSet.getNumSections(); s++) {
			
			double area = rupSet.getAreaForSection(s); // SI units
			double slipRate = calcSlipRateForSect(s); // SI units
			double newMoRate = FaultMomentCalc.getMoment(area, slipRate);
			if(Double.isNaN(newMoRate)) newMoRate = 0;
			int mMaxIndex = tempGR.getClosestXIndex(rupSet.getMaxMagForSection(s));
			double mMax = tempGR.getX(mMaxIndex);
			GutenbergRichterMagFreqDist gr = new GutenbergRichterMagFreqDist(minMag, numMag, deltaMag, minMag, mMax, newMoRate, 1.0);
//			double minSeismoMag = getMinMagForSection(s);
			double minSeismoMag = rupSet.getUpperMagForSubseismoRuptures(s)+deltaMag/2;
			if(Double.isNaN(minSeismoMag))
				gr.scaleToCumRate(0, 0d);
			else {
				double closestMag = gr.getX(gr.getClosestXIndex(minSeismoMag));
				gr.zeroAtAndAboveMag(closestMag);
			}
			grNuclMFD_List.add(gr);
		}
		return grNuclMFD_List;
	}

	
	/**
	 * This returns the total target minus total on-fault MFD (supra- and sub-seismo) if MomentRateFixes.NONE 
	 * or MomentRateFixes.APPLY_IMPLIED_CC (to make a perfect match with the total target), otherwise
	 * this returns inversionTargetMFDs.getTrulyOffFaultMFD() since we're on a branch that allows a
	 * bulge (we don't match total regional target)
	 * 
	 * @return
	 */
	public IncrementalMagFreqDist getFinalTrulyOffFaultMFD() {
		InversionTargetMFDs inversionTargetMFDs = rupSet.getInversionTargetMFDs();
		
		if(branch.getValue(MomentRateFixes.class) == MomentRateFixes.NONE ||
				branch.getValue(MomentRateFixes.class) == MomentRateFixes.APPLY_IMPLIED_CC ) {
					
			SummedMagFreqDist finalTrulyOffMFD = new SummedMagFreqDist(inversionTargetMFDs.MIN_MAG, inversionTargetMFDs.NUM_MAG, inversionTargetMFDs.DELTA_MAG);
			finalTrulyOffMFD.addIncrementalMagFreqDist(inversionTargetMFDs.getTotalTargetGR());
			finalTrulyOffMFD.subtractIncrementalMagFreqDist(calcNucleationMFD_forRegion(RELM_RegionUtils.getGriddedRegionInstance(), inversionTargetMFDs.MIN_MAG, inversionTargetMFDs.MAX_MAG, inversionTargetMFDs.DELTA_MAG, true));
			finalTrulyOffMFD.subtractIncrementalMagFreqDist(getFinalTotalSubSeismoOnFaultMFD());
			
			// zero out values above mMaxOffFault
			double mMaxOffFault = getLogicTreeBranch().getValue(MaxMagOffFault.class).getMaxMagOffFault();
			mMaxOffFault -= InversionTargetMFDs.DELTA_MAG/2;
			
			// SummedMagFreqDist doesn't allow set, so put it in a new one
			IncrementalMagFreqDist truncatedMFD = new IncrementalMagFreqDist(
					finalTrulyOffMFD.getMinX(), finalTrulyOffMFD.size(), finalTrulyOffMFD.getDelta());
			int startZeroIndex = finalTrulyOffMFD.getClosestXIndex(mMaxOffFault) + 1;	// plus 1
			for (int i=0; i<startZeroIndex && i<finalTrulyOffMFD.size(); i++)
				truncatedMFD.set(i, finalTrulyOffMFD.getY(i));
			finalTrulyOffMFD.setName("InversionFaultSystemSolution.getFinalTrulyOffFaultMFD()");

			return truncatedMFD;
		}
		else {
			IncrementalMagFreqDist finalTrulyOffMFD = inversionTargetMFDs.getTrulyOffFaultMFD().deepClone();
			finalTrulyOffMFD.setName("InversionFaultSystemSolution.getFinalTrulyOffFaultMFD()");
			finalTrulyOffMFD.setInfo("identical to inversionTargetMFDs.getTrulyOffFaultMFD() in this case");
			return finalTrulyOffMFD;
		}
	}

	
	
	/**
	 * This returns the sum of getFinalTrulyOffFaultMFD() and getFinalTotalSubSeismoOnFaultMFD().
	 * @return
	 */
	public IncrementalMagFreqDist getFinalTotalGriddedSeisMFD() {
		SummedMagFreqDist totGridSeisMFD = new SummedMagFreqDist(InversionTargetMFDs.MIN_MAG, InversionTargetMFDs.NUM_MAG, InversionTargetMFDs.DELTA_MAG);
		totGridSeisMFD.addIncrementalMagFreqDist(getFinalTrulyOffFaultMFD());
		totGridSeisMFD.addIncrementalMagFreqDist(getFinalTotalSubSeismoOnFaultMFD());
		totGridSeisMFD.setName("InversionFaultSystemSolution.getFinalTotalGriddedSeisMFD()");
		return totGridSeisMFD;
 	}
	
	/**
	 * Returns GridSourceProvider - creates on demand if necessary
	 * @return
	 */
	public GridSourceProvider getGridSourceProvider() {
		GridSourceProvider gridSourceProvider = super.getGridSourceProvider();
		if (gridSourceProvider == null) {
			gridSourceProvider = new UCERF3_GridSourceGenerator(this);
			super.setGridSourceProvider(gridSourceProvider);
		}
		return gridSourceProvider;
	}
	
	/**
	 * This plots original and final slip rates versus section index.
	 * This also plot these averaged over parent sections.
	 * 
	 * TODO [re]move
	 */
	public void plotSlipRates() {
		int numSections = rupSet.getNumSections();
		int numRuptures = rupSet.getNumRuptures();
		List<FaultSectionPrefData> faultSectionData = rupSet.getFaultSectionDataList();

		ArrayList funcs2 = new ArrayList();		
		EvenlyDiscretizedFunc syn = new EvenlyDiscretizedFunc(0,(double)numSections-1,numSections);
		EvenlyDiscretizedFunc data = new EvenlyDiscretizedFunc(0,(double)numSections-1,numSections);
		for (int i=0; i<numSections; i++) {
			data.set(i, rupSet.getSlipRateForSection(i));
			syn.set(i,0);
		}
		for (int rup=0; rup<numRuptures; rup++) {
			double[] slips = rupSet.getSlipOnSectionsForRup(rup);
			List<Integer> sects = rupSet.getSectionsIndicesForRup(rup);
			for (int i=0; i < slips.length; i++) {
				int row = sects.get(i);
				syn.add(row,slips[i]*getRateForRup(rup));
			}
		}
		for (int i=0; i<numSections; i++) data.set(i, rupSet.getSlipRateForSection(i));
		funcs2.add(syn);
		funcs2.add(data);
		GraphWindow graph2 = new GraphWindow(funcs2, "Slip Rate Synthetics (blue) & Data (black)"); 
		graph2.setX_AxisLabel("Fault Section Index");
		graph2.setY_AxisLabel("Slip Rate");
		
		String info = "index\tratio\tpredSR\tdataSR\tParentSectionName\n";
		String parentSectName = "";
		double aveData=0, aveSyn=0, numSubSect=0;
		ArrayList<Double> aveDataList = new ArrayList<Double>();
		ArrayList<Double> aveSynList = new ArrayList<Double>();
		for (int i = 0; i < numSections; i++) {
			if(!faultSectionData.get(i).getParentSectionName().equals(parentSectName)) {
				if(i != 0) {
					double ratio  = aveSyn/aveData;
					aveSyn /= numSubSect;
					aveData /= numSubSect;
					info += aveSynList.size()+"\t"+(float)ratio+"\t"+(float)aveSyn+"\t"+(float)aveData+"\t"+faultSectionData.get(i-1).getParentSectionName()+"\n";
//					System.out.println(ratio+"\t"+aveSyn+"\t"+aveData+"\t"+faultSectionData.get(i-1).getParentSectionName());
					aveSynList.add(aveSyn);
					aveDataList.add(aveData);
				}
				aveSyn=0;
				aveData=0;
				numSubSect=0;
				parentSectName = faultSectionData.get(i).getParentSectionName();
			}
			aveSyn +=  syn.getY(i);
			aveData +=  data.getY(i);
			numSubSect += 1;
		}
		ArrayList funcs5 = new ArrayList();		
		EvenlyDiscretizedFunc aveSynFunc = new EvenlyDiscretizedFunc(0,(double)aveSynList.size()-1,aveSynList.size());
		EvenlyDiscretizedFunc aveDataFunc = new EvenlyDiscretizedFunc(0,(double)aveSynList.size()-1,aveSynList.size());
		for(int i=0; i<aveSynList.size(); i++ ) {
			aveSynFunc.set(i, aveSynList.get(i));
			aveDataFunc.set(i, aveDataList.get(i));
		}
		aveSynFunc.setName("Predicted ave slip rates on parent section");
		aveDataFunc.setName("Original (Data) ave slip rates on parent section");
		aveSynFunc.setInfo(info);
		funcs5.add(aveSynFunc);
		funcs5.add(aveDataFunc);
		GraphWindow graph5 = new GraphWindow(funcs5, "Average Slip Rates on Parent Sections"); 
		graph5.setX_AxisLabel("Parent Section Index");
		graph5.setY_AxisLabel("Slip Rate");

	}
	
	public static void main(String args[]) throws IOException, DocumentException {
//		SimpleFaultSystemSolution simple = SimpleFaultSystemSolution.fromFile(
//				new File("/home/kevin/workspace/OpenSHA/dev/scratch/UCERF3/data/scratch/InversionSolutions/" +
//						"FM3_1_GLpABM_MaEllB_DsrTap_DrEllB_Char_VarAseis0.2_VarOffAseis0.5_VarMFDMod1_VarNone_sol.zip"));
//		SimpleFaultSystemSolution simple = SimpleFaultSystemSolution.fromFile(new File(
//						"/tmp/ucerf2_fm2_compare.zip"));
//		simple.plotMFDs(Lists.newArrayList(OLD_UCERF3_MFD_ConstraintFetcher.getTargetMFDConstraint(TimeAndRegion.ALL_CA_1850)));
		
		InversionFaultSystemSolution inv = FaultSystemIO.loadInvSol(new File(
				"/tmp/FM2_1_UC2ALL_ShConStrDrp_DsrTap_CharConst_M5Rate8.7_MMaxOff7.6_NoFix_" +
				"SpatSeisU2_VarPaleo0.1_VarSectNuclMFDWt0.01_VarParkfield10000_sol.zip"));
		
		Map<String, Double> misfits = inv.getMisfits();
		for (String name : misfits.keySet()) {
			System.out.println(name+": "+misfits.get(name));
		}
//		inv.plotMFDs();
		
//		CommandLineInversionRunner.writeMFDPlots(inv, new File("/tmp"), "test_plots");
	}

}
