package scratch.UCERF3.erf.utils;

import java.awt.Color;
import java.awt.Toolkit;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.PriorityQueue;

import javax.swing.JFrame;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.random.RandomDataGenerator;
import org.apache.commons.math3.random.RandomDataImpl;
import org.opensha.commons.calc.FaultMomentCalc;
import org.opensha.commons.data.TimeSpan;
import org.opensha.commons.data.function.AbstractDiscretizedFunc;
import org.opensha.commons.data.function.ArbDiscrEmpiricalDistFunc_3D;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.HistogramFunction;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.data.xyz.ArbDiscrXYZ_DataSet;
import org.opensha.commons.data.xyz.EvenlyDiscrXYZ_DataSet;
import org.opensha.commons.eq.MagUtils;
import org.opensha.commons.geo.BorderType;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.Region;
import org.opensha.commons.gui.plot.GraphWindow;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSymbol;
import org.opensha.commons.gui.plot.jfreechart.xyzPlot.XYZPlotSpec;
import org.opensha.commons.gui.plot.jfreechart.xyzPlot.XYZPlotWindow;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.earthquake.ProbEqkRupture;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.earthquake.calc.ERF_Calculator;
import org.opensha.sha.earthquake.calc.recurInterval.BPT_DistCalc;
import org.opensha.sha.earthquake.calc.recurInterval.LognormalDistCalc;
import org.opensha.sha.earthquake.calc.recurInterval.WeibullDistCalc;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupOrigTimeComparator;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupture;
import org.opensha.sha.earthquake.param.BPTAveragingTypeOptions;
import org.opensha.sha.earthquake.param.BPTAveragingTypeParam;
import org.opensha.sha.earthquake.param.BPT_AperiodicityParam;
import org.opensha.sha.earthquake.param.HistoricOpenIntervalParam;
import org.opensha.sha.earthquake.param.IncludeBackgroundOption;
import org.opensha.sha.earthquake.param.IncludeBackgroundParam;
import org.opensha.sha.earthquake.param.MagDependentAperiodicityOptions;
import org.opensha.sha.earthquake.param.MagDependentAperiodicityParam;
import org.opensha.sha.earthquake.param.ProbabilityModelOptions;
import org.opensha.sha.earthquake.param.ProbabilityModelParam;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.gui.infoTools.CalcProgressBar;
import org.opensha.sha.magdist.SummedMagFreqDist;
import org.opensha.sha.simulators.utils.General_EQSIM_Tools;

import com.google.common.base.Preconditions;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.erf.FaultSystemSolutionERF;
import scratch.UCERF3.erf.utils.ProbModelsPlottingUtils;
import scratch.UCERF3.erf.ETAS.ETAS_EqkRupture;
import scratch.UCERF3.erf.ETAS.ETAS_SimAnalysisTools;
import scratch.UCERF3.erf.ETAS.ETAS_Utils;
import scratch.UCERF3.erf.ETAS.IntegerPDF_FunctionSampler;
import scratch.UCERF3.inversion.InversionFaultSystemRupSet;
import scratch.UCERF3.utils.FaultSystemIO;
import scratch.UCERF3.utils.UCERF3_DataUtils;
import scratch.UCERF3.utils.paleoRateConstraints.PaleoRateConstraint;
import scratch.UCERF3.utils.paleoRateConstraints.UCERF3_PaleoRateConstraintFetcher;
import scratch.ned.ETAS_ERF.testModels.TestModel1_FSS;
import scratch.ned.ETAS_ERF.testModels.TestModel2_FSS;
import scratch.ned.ETAS_ERF.testModels.TestModel3_FSS;


/**
 * This class does various time-dependent earthquake probability calculations for ruptures in a fault system solution (FSS).
 * 
 * TODO:
 * 
 * 0) implement the getU3_ProbGain2_ForRup method
 * 
 * 1) junit tests
 * 
 * 2) improve efficiency?
 * 
 * 3) remove the "OLD*" methods here, as they were experimental and are no longer needed.
 * 
 * 4) enable changing just the timeSpan and/or aperiodicity (would require setting dependent arrays to null)
 * 
 * @author field
 *
 */
public class ProbabilityModelsCalc {
	
	public final static boolean D=true;
	
	public final static double MILLISEC_PER_YEAR = 1000*60*60*24*365.25;
	public final static long MILLISEC_PER_DAY = 1000*60*60*24;
	
	// passed in values:
	FaultSystemSolutionERF erf;
	FaultSystemSolution fltSysSolution;
	double[] longTermRateOfFltSysRup;	// this has zeros where events were filtered our by the ERF (mags too low); this includes aftershocks
//	double aperiodicity;

	// computed from passed in values
	FaultSystemRupSet fltSysRupSet;
	int numRupsInFaultSystem;
	int numSections;	
	
	double[] longTermPartRateForSectArray;
	double[] sectionArea;
	long[] dateOfLastForSect;
	
	// The following ave recurrence interval of each rupture conditioned on the fact that it is the next event to occur
	double[] aveCondRecurIntervalForFltSysRups_type1; //  for averaging section recurrence intervals and time since last;
	double[] aveCondRecurIntervalForFltSysRups_type2; //  for averaging section rates and normalized time since last

	// for BPT reference calculator (200 year recurrence interval); this is used for efficiency
	static double refRI = 1.0;
	static double deltaT = 0.005;
//	BPT_DistCalc refBPT_DistributionCalc;
	BPT_DistCalc[] refBPT_CalcArray;
	int numAperValues;
	double[] aperValues;
	double[] aperMagBoundaries;	// this must have one less element than aperValues
	
//	double[] defaultAperValues = {0.8,0.4,0.2};
//	double[] defaultAperValues = {0.4,0.2,0.1};
//	double[] defaultAperMagBoundaries = {6.7,7.7};

	// Normalized CDF function used for when looping over possible dates of last event
	static double max_time_for_normBPT_CDF=5;
	static int num_for_normBPT_CDF=501;
	EvenlyDiscretizedFunc[] normBPT_CDF_Array;
//	EvenlyDiscretizedFunc normBPT_CDF;
	
	// this is for getting the BPT time since last that is equivalent to the poisson probability TODO no longer needed?
	ArbitrarilyDiscretizedFunc OLDbptTimeToPoisCondProbFunc;
	double[] OLDbptNormTimeToPoisCondProbFuncForSect;
	
	// this is for getting equivalent date of last event when we only know the open interval
	EvenlyDiscrXYZ_DataSet equivLastEventTimeForHistOpenInterval_XYZ_Func;	// TODO no longer used?
	
	
	double[] sectionGainArray;	// for WG02-type calculations
	boolean[] sectionGainReal;	// for WG02-type calculations
	
	// these global variables are used as diagnostics
	double totRupArea;
	double totRupAreaWithDateOfLast;
	boolean allSectionsHadDateOfLast;
	boolean noSectionsHadDateOfLast;

	// data dir for Elastic Rebound simulations
	final static File dataDir = new File(UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR,File.separator+"erSimulations");
	
	boolean simulationMode=false;
	HistogramFunction simNormTimeSinceLastHist;
	HistogramFunction simNormTimeSinceLastForMagBelow7_Hist;
	HistogramFunction simNormTimeSinceLastForMagAbove7_Hist;
	HistogramFunction simProbGainHist;
	HistogramFunction simProbGainForMagBelow7_Hist;
	HistogramFunction simProbGainForMagAbove7_Hist;


	
	/**
	 * 
	 * This is the main constructor.  Note that longTermRateOfFltSysRupInERF is provided, rather than 
	 * using what's obtained as fltSysSolution.getRateForAllRups(), because the ERF has filtered out
	 * small events (TODO not entirely sure this is necessary; depends on whether small sections should be
	 * included in total section rates; the same filtering could also be done here). 
	 * 
	 * @param fltSysSolution
	 * @param longTermRateOfFltSysRupInERF	- this rate includes aftershocks
	 * @param aperiodicity - aperiodicity of the BPT model
	 * 
	 */
	public ProbabilityModelsCalc(FaultSystemSolution fltSysSolution, double[] longTermRateOfFltSysRupInERF, MagDependentAperiodicityOptions magDepAperiodicity) {
		this.fltSysSolution=fltSysSolution;
		longTermRateOfFltSysRup = longTermRateOfFltSysRupInERF;
//		this.aperiodicity = aperiodicity;
		
		aperValues=magDepAperiodicity.getAperValuesArray();
		numAperValues=aperValues.length;
		aperMagBoundaries=magDepAperiodicity.getAperMagBoundariesArray();
		
		fltSysRupSet = fltSysSolution.getRupSet();
		numRupsInFaultSystem = fltSysRupSet.getNumRuptures();
		numSections = fltSysRupSet.getNumSections();
		
		initializeArrays();
		
//		if(!Double.isNaN(aperiodicity)) {
//			refBPT_DistributionCalc = getRef_BPT_DistCalc(aperiodicity);
//						
//			// set normBPT_CDF
//			BPT_DistCalc tempCalc = new BPT_DistCalc();
//			double delta = max_time_for_normBPT_CDF/(num_for_normBPT_CDF-1);
//			tempCalc.setAll(1.0, aperiodicity, delta, num_for_normBPT_CDF);
//			normBPT_CDF=tempCalc.getCDF();		
//		}	
		
		refBPT_CalcArray = getRef_BPT_DistCalcArray();
		normBPT_CDF_Array = getNormBPT_CDF_Array();

	}
	
	
	/**
	 * The is an alternative constructor.
	 * 
	 * However, it can't be used if ProbabilityModelParam.NAME).getValue() == ProbabilityModelOptions.U3_PREF_BLEND
	 * 
	 * @param erf
	 */
	public ProbabilityModelsCalc(FaultSystemSolutionERF erf) {
		this.erf = erf;
		this.fltSysSolution=erf.getSolution();
		longTermRateOfFltSysRup = erf.getLongTermRateOfFltSysRupInERF();
		
		if(erf.getParameter(ProbabilityModelParam.NAME).getValue() == ProbabilityModelOptions.U3_PREF_BLEND)
			throw new RuntimeException("This constructor can't be used with "+ProbabilityModelOptions.U3_PREF_BLEND);
		
		MagDependentAperiodicityOptions magDepAperiodicity = null;;
		if(erf.getAdjustableParameterList().containsParameter(MagDependentAperiodicityParam.NAME)) {
			magDepAperiodicity = ((MagDependentAperiodicityParam)erf.getParameter(MagDependentAperiodicityParam.NAME)).getValue();
			aperValues=magDepAperiodicity.getAperValuesArray();
			numAperValues=aperValues.length;
			aperMagBoundaries=magDepAperiodicity.getAperMagBoundariesArray();
		}
		else {
			numAperValues=0;
		}
		
		fltSysRupSet = fltSysSolution.getRupSet();
		numRupsInFaultSystem = fltSysRupSet.getNumRuptures();
		numSections = fltSysRupSet.getNumSections();
		
		initializeArrays();
		
		refBPT_CalcArray = getRef_BPT_DistCalcArray();
		normBPT_CDF_Array = getNormBPT_CDF_Array();

	}

	
	
	
	/**
	 * This is for tests
	 * 
	 * @param 
	 */
	public ProbabilityModelsCalc(double aperiodicity) {
		this.numAperValues=1;
		aperValues = new double[1];
		aperValues[0] = aperiodicity;
		aperMagBoundaries=null;
		
//		this.aperiodicity=aperiodicity;
//		refBPT_DistributionCalc = getRef_BPT_DistCalc(aperiodicity);
		
//		// set normBPT_CDF
//		BPT_DistCalc tempCalc = new BPT_DistCalc();
//		double delta = max_time_for_normBPT_CDF/(num_for_normBPT_CDF-1);
//		tempCalc.setAll(1.0, aperiodicity, delta, num_for_normBPT_CDF);	// TODO check this discretization and overall look
//		normBPT_CDF=tempCalc.getCDF();	
////		GraphWindow graph = new GraphWindow(normBPT_CDF, "test");
		
		refBPT_CalcArray = getRef_BPT_DistCalcArray();
		normBPT_CDF_Array = getNormBPT_CDF_Array();

	}

	

	/**
	 * The initializes the following arrays: longTermPartRateForSectArray and sectionArea
	 * 
	 * @return
	 */
	private void initializeArrays() {
		
		// first make longTermPartRateForSectArray[]
		longTermPartRateForSectArray = new double[numSections];
		for(int r=0; r<numRupsInFaultSystem; r++) {
			List<Integer> sectIndices = fltSysRupSet.getSectionsIndicesForRup(r);
			for(int s=0;s<sectIndices.size();s++) {
				int sectID = sectIndices.get(s);
				longTermPartRateForSectArray[sectID] += longTermRateOfFltSysRup[r];
			}
		}
		
		// now make sectionArea[] & dateOfLastForSect[]
		sectionArea = new double[numSections];
		dateOfLastForSect = new long[numSections];
		for(int s=0;s<numSections;s++) {
			FaultSectionPrefData sectData = this.fltSysRupSet.getFaultSectionData(s);
			sectionArea[s]= sectData.getTraceLength()*sectData.getReducedDownDipWidth();
			dateOfLastForSect[s] = sectData.getDateOfLastEvent();
		}
	}
	
		
		
	
	
	/**
	 * This computes average conditional recurrent intervals for each fault system rup
	 * (the recurrence interval assuming the rup is the next to occur), either by averaging
	 * section recurrence intervals (typeCalc=1) or by computing one over the average section
	 * rate (typeCalc=2), both are weighted by section area.
	 * 
	 * @param typeCalc - set as 1 to average RIs, 2 to average rates, 3 for max sect RI
	 * @return
	 */
	private double[] computeAveCondRecurIntervalForFltSysRups(int typeCalc) {
		// now make aveCondRecurIntervalForFltSysRups[]
		double[] aveCondRecurIntervalForFltSysRups = new double[numRupsInFaultSystem];
		for(int r=0;r<numRupsInFaultSystem; r++) {
			List<FaultSectionPrefData> fltData = fltSysRupSet.getFaultSectionDataForRupture(r);
			double ave=0, totArea=0, maxRI=0;
			for(FaultSectionPrefData data:fltData) {
				int sectID = data.getSectionId();
				double area = sectionArea[sectID];
				totArea += area;
				if(1.0/longTermPartRateForSectArray[sectID] > maxRI)
					maxRI = 1.0/longTermPartRateForSectArray[sectID];
				// ave RIs or rates depending on which is set
				if(typeCalc==1)
					ave += area/longTermPartRateForSectArray[sectID];  // this one averages RIs; wt averaged by area
				else if(typeCalc==2)
					ave += longTermPartRateForSectArray[sectID]*area;  // this one averages rates; wt averaged by area
				else 
					throw new RuntimeException("Bad typeCalcForU3_Probs");
			}
			if(typeCalc==1)
				aveCondRecurIntervalForFltSysRups[r] = ave/totArea;	// this one averages RIs
			else
				aveCondRecurIntervalForFltSysRups[r] = 1/(ave/totArea); // this one averages rates
// temp overide:
// aveCondRecurIntervalForFltSysRups[r] = maxRI;
		}
		return aveCondRecurIntervalForFltSysRups;
	}

	
	
	/**
	 * This computes average conditional recurrent interval using only sections that
	 * lack a date of last event, either by averaging section recurrence intervals 
	 * (aveRI_CalcType=true) or by computing one over the average section
	 * rate (aveRI_CalcType=false), both are weighted by section area.  Double.NaN is
	 * returned if all sections have a date of last event.
	 * 
	 * @return
	 */
	private double computeAveCondRecurIntForFltSysRupsWhereDateLastUnknown(int fltSysRupIndex, boolean aveRI_CalcType, long presentTimeMillis) {
			List<Integer> sectID_List = fltSysRupSet.getSectionsIndicesForRup(fltSysRupIndex);
			double ave=0, totArea=0;
			for(Integer sectID:sectID_List) {
				long dateOfLastMillis = dateOfLastForSect[sectID];
				if(dateOfLastMillis == Long.MIN_VALUE || dateOfLastMillis > presentTimeMillis) {
					double area = sectionArea[sectID];
					totArea += area;
					// ave RIs or rates depending on which is set
					if(aveRI_CalcType)
						ave += area/longTermPartRateForSectArray[sectID];  // this one averages RIs; wt averaged by area
					else
						ave += longTermPartRateForSectArray[sectID]*area;  // this one averages rates; wt averaged by area					
				}
			}
			if(totArea == 0.0)
				return Double.NaN;
			else if(aveRI_CalcType)
				return ave/totArea;	// this one averages RIs
			else
				return 1.0/(ave/totArea); // this one averages rates
	}

	
	
	/**
	 * This method returns last-event date (epoch milliseconds) averaged over fault sections
	 * that have such data (and weighted by section area). 
	 * 
	 *  Long.MIN_VALUE is returned if none of the fault sections had a date of last event 
	 *  
	 *  The following global variables are also set for further diagnostics:
	 * 
	 * 		double totRupArea
	 * 		double totRupAreaWithDateOfLast
	 * 		boolean allSectionsHadDateOfLast
	 * 		boolean noSectionsHadDateOfLast
	 *
	 * @param fltSystRupIndex
	 * @param presentTimeMillis if non null, only fault sections with date of last events at or before the present
	 * time in milliseconds will be considered.
	 */
	public long getAveDateOfLastEventWhereKnown(int fltSystRupIndex, Long presentTimeMillis) {
//		System.out.println("getAveDateOfLastEventWhereKnown");
		totRupArea=0;
		totRupAreaWithDateOfLast=0;
		int numWithDateOfLast=0;
		allSectionsHadDateOfLast = true;
		noSectionsHadDateOfLast = false;
		double sumDateOfLast = 0;
		for(int s:fltSysRupSet.getSectionsIndicesForRup(fltSystRupIndex)) {
			long dateOfLast = dateOfLastForSect[s];
			double area = sectionArea[s];
			totRupArea+=area;
			if(dateOfLast != Long.MIN_VALUE && (presentTimeMillis == null || dateOfLast <= presentTimeMillis)) {
//System.out.println("dateOfLast="+dateOfLast+"; presentTimeMillis="+presentTimeMillis);
				sumDateOfLast += (double)dateOfLast*area;
				totRupAreaWithDateOfLast += area;
				numWithDateOfLast+=1;
			}
			else {
				allSectionsHadDateOfLast = false;
			}
		}
		if(numWithDateOfLast>0)
			return Math.round(sumDateOfLast/totRupAreaWithDateOfLast);  // epoch millis
		else {
			noSectionsHadDateOfLast=true;
			return Long.MIN_VALUE;
		}
	}
	
	
	/**
	 * This method returns normalized time since last event (timeSince/meanRecurInt)
	 *  averaged over fault sections that have such data (and weighted by section area). 
	 * 
	 *  Double.NaN is returned in none of the fault sections have a date of last event 
	 *  
	 *  The following global variables are also set for further diagnostics:
	 * 
	 * 		double totRupArea
	 * 		double totRupAreaWithDateOfLast
	 * 		boolean allSectionsHadDateOfLast
	 * 		boolean noSectionsHadDateOfLast
	 *
	 * @param fltSystRupIndex
	 * @param presentTimeMillis - present time in epoch milliseconds
	 */
	public double getAveNormTimeSinceLastEventWhereKnown(int fltSystRupIndex, long presentTimeMillis) {
		
//		System.out.println("getAveNormTimeSinceLastEventWhereKnown");
		
//		List<FaultSectionPrefData> fltData = fltSysRupSet.getFaultSectionDataForRupture(fltSystRupIndex);
		totRupArea=0;
		totRupAreaWithDateOfLast=0;
		allSectionsHadDateOfLast = true;
		int numWithDateOfLast=0;
		noSectionsHadDateOfLast = false;
		double sumNormTimeSinceLast = 0;
		for(int s : fltSysRupSet.getSectionsIndicesForRup(fltSystRupIndex)) {
			long dateOfLast = dateOfLastForSect[s];
			double area = sectionArea[s];
			totRupArea+=area;
// System.out.println("dateOfLast="+dateOfLast+"; presentTimeMillis="+presentTimeMillis);
			if(dateOfLast != Long.MIN_VALUE && dateOfLast <= presentTimeMillis) {
				sumNormTimeSinceLast += area*((double)(presentTimeMillis-dateOfLast)/MILLISEC_PER_YEAR)*longTermPartRateForSectArray[s];
				totRupAreaWithDateOfLast += area;
				numWithDateOfLast += 1;
			}
			else {
				allSectionsHadDateOfLast = false;
			}
		}
		if(numWithDateOfLast>0)
			return sumNormTimeSinceLast/totRupAreaWithDateOfLast; 
		else {
			noSectionsHadDateOfLast=true;
			return Double.NaN;
		}
	}
	
	

	public int writeSectionsWithDateOfLastEvent() {
		List<FaultSectionPrefData> fltData = fltSysRupSet.getFaultSectionDataList();
		int numWith=0;
		System.out.println("Sections With Date of Last Event Data (timeSinceLastYears, dateOfLastMillis, sectName):");
		for(FaultSectionPrefData data:fltData) {
			long dateOfLastMillis = data.getDateOfLastEvent();
			if(dateOfLastMillis != Long.MIN_VALUE) {
				System.out.println(dateOfLastMillis+"\t"+data.getName());
				numWith += 1;
			}
		}
		return numWith;
	}
	
	
	public void writeInfoForRupsOnSect(int sectID) {
		String fileName = "RupInfoForSect"+sectID+".txt";
		if(!dataDir.exists())
			dataDir.mkdir();
		File dataFile = new File(dataDir,File.separator+fileName);
		String sectName = fltSysRupSet.getFaultSectionData(sectID).getName();
		try {
			FileWriter fileWriter = new FileWriter(dataFile);
			fileWriter.write("rupID\trate\tyrsSince\tfracWithDateOfLast\t"+sectName+"\n");
			for(int r=0;r<fltSysRupSet.getNumRuptures();r++) {
				if(fltSysRupSet.getSectionsIndicesForRup(r).contains(sectID)) {
					double rate = longTermRateOfFltSysRup[r];
					long aveDateOfLastMillis = getAveDateOfLastEventWhereKnown(r, null);
					double yrsSinceLast = ((2014-1970)*MILLISEC_PER_YEAR-(double)aveDateOfLastMillis)/MILLISEC_PER_YEAR;
					List<Integer> indices = fltSysRupSet.getSectionsIndicesForRup(r);
					String name1 = fltSysRupSet.getFaultSectionData(indices.get(0)).getName();
					String name2 = fltSysRupSet.getFaultSectionData(indices.get(indices.size()-1)).getName();
					String line = r+"\t"+rate+"\t"+yrsSinceLast+"\t"+(totRupAreaWithDateOfLast/totRupArea)+"\t"+name1+"\t"+name2+"\n";
					fileWriter.write(line);
					
//					if(r==156620) { // test rup
//						indices = fltSysRupSet.getSectionsIndicesForRup(r);
//						System.out.println(fltSysRupSet.getFaultSectionData(indices.get(0)).getName()+"\tTO\t"+
//								fltSysRupSet.getFaultSectionData(indices.get(indices.size()-1)).getName());
//					}
				}
			}
			fileWriter.close();

		}catch(Exception e) {
			e.printStackTrace();
		}

	}
	
	
	/**
	 * This computes the BPT probability gain using the UCERF3.
	 * 
	 * Gain is defined as the BPT probability divided by the expected number (durationYears/aveCondRecurInterval);
	 * (dividing by Poisson probability gives biased results for long durations).  Note that if the expected number
	 * exceeds 1.0, the gain becomes less than 1.0 (since probability can't exceed 1.0), so maybe this shouldn't
	 * be called "gain".
	 * 
	 * This returns Double.NaN if onlyIfAllSectionsHaveDateOfLast=true and one or mare sections
	 * lack date of last event.
	 * 
	 * @param onlyIfAllSectionsHaveDateOfLast
	 * @param fltSystRupIndex
	 * @param histOpenInterval
	 * @param aveRecurIntervals - if false, rates will be averaged in get the conditional recurrence interval
	 * @param aveNormTimeSinceLast - if true, normalized time since last is averaged (divided by section intervals); otherwise time since last is averaged
	 */
	public double getU3_ProbGainForRup(int fltSysRupIndex, double histOpenInterval, boolean onlyIfAllSectionsHaveDateOfLast, 
			boolean aveRecurIntervals, boolean aveNormTimeSinceLast, long presentTimeMillis, double durationYears) {
		
		double rupMag = fltSysRupSet.getMagForRup(fltSysRupIndex);

		// get the average recurrence interval
		double aveCondRecurInterval;
		if(aveRecurIntervals) {
			if(aveCondRecurIntervalForFltSysRups_type1 == null)
				aveCondRecurIntervalForFltSysRups_type1 = computeAveCondRecurIntervalForFltSysRups(1);
//			System.out.println("aveRecurIntervals");
			aveCondRecurInterval = aveCondRecurIntervalForFltSysRups_type1[fltSysRupIndex];
		}
		else {
			if(aveCondRecurIntervalForFltSysRups_type2 == null)
				aveCondRecurIntervalForFltSysRups_type2 = computeAveCondRecurIntervalForFltSysRups(2);
//			System.out.println("aveRecurRates");
			aveCondRecurInterval = aveCondRecurIntervalForFltSysRups_type2[fltSysRupIndex];			
		}
		
		// get aveTimeSinceLastWhereKnownYears
		double aveTimeSinceLastWhereKnownYears;
		double aveNormTimeSinceLastEventWhereKnown=Double.NaN;
		if(aveNormTimeSinceLast) {
			aveNormTimeSinceLastEventWhereKnown = getAveNormTimeSinceLastEventWhereKnown(fltSysRupIndex, presentTimeMillis);
			aveTimeSinceLastWhereKnownYears = aveNormTimeSinceLastEventWhereKnown*aveCondRecurInterval;
//			if(aveTimeSinceLastWhereKnownYears<0)
//				throw new RuntimeException("1st "+aveTimeSinceLastWhereKnownYears);
		}
		else {
			long aveTimeOfLastMillisWhereKnown = getAveDateOfLastEventWhereKnown(fltSysRupIndex, presentTimeMillis);
			if(aveTimeOfLastMillisWhereKnown != Long.MIN_VALUE)
				aveTimeSinceLastWhereKnownYears = (double)(presentTimeMillis-aveTimeOfLastMillisWhereKnown)/MILLISEC_PER_YEAR;	
			else
				aveTimeSinceLastWhereKnownYears = Double.NaN;
//			if(aveTimeSinceLastWhereKnownYears<0)
//				throw new RuntimeException("2nd "+aveTimeSinceLastWhereKnownYears+"\t"+presentTimeMillis+"\t"+aveTimeOfLastMillisWhereKnown+"\t"+Long.MIN_VALUE);

		}
		
		Preconditions.checkState(Double.isNaN(aveTimeSinceLastWhereKnownYears)
				|| aveTimeSinceLastWhereKnownYears >= 0, "aveTimeSinceLastWhereKnownYears="+aveTimeSinceLastWhereKnownYears);
		// the following global variables were just set by the above 
		// 		double totRupArea
		// 		double totRupAreaWithDateOfLast
		// 		boolean allSectionsHadDateOfLast
		// 		boolean noSectionsHadDateOfLast
		
		
		double expNum = durationYears/aveCondRecurInterval;
		
//if(totRupAreaWithDateOfLast==0) {
//	System.out.println("fltSysRupIndex="+fltSysRupIndex+" has no date of last data");
//	System.exit(0);
//}
		
// test
//int testRupID = 156778;	// ~half sectuibs have date if last
//int testRupID = 151834;	// all sections have date of last
//int testRupID = 0;	// no sections have date of last
//int testRupID = 884;
//int testRupID = 249574;
// int testRupID = 198219; Imperial rup
		

		
		double probGain;

		if(onlyIfAllSectionsHaveDateOfLast && !allSectionsHadDateOfLast) {
			probGain = Double.NaN;
// if(fltSysRupIndex==testRupID) System.out.println("Here1");
		}
		else if(allSectionsHadDateOfLast) {
			probGain = computeBPT_ProbFast(aveCondRecurInterval, aveTimeSinceLastWhereKnownYears, durationYears, rupMag)/expNum;	
//if(fltSysRupIndex==testRupID) System.out.println("Here2");
		}
		else if (noSectionsHadDateOfLast) {
			probGain = computeBPT_ProbForUnknownDateOfLastFast(aveCondRecurInterval, histOpenInterval, durationYears, rupMag)/expNum;
// if(fltSysRupIndex==testRupID) System.out.println("Here3");
		}
		else {	// case where some have date of last; loop over all possibilities for those that don't.
//  if(fltSysRupIndex==testRupID) System.out.println("Here4");

			// set normBPT_CDF based on magnitude
			EvenlyDiscretizedFunc normBPT_CDF=normBPT_CDF_Array[getAperIndexForRupMag(rupMag)];
			double sumCondProbGain=0;
			double totWeight=0;
			double areaWithOutDateOfLast = totRupArea-totRupAreaWithDateOfLast;

			
			double condRecurIntWhereUnknown = computeAveCondRecurIntForFltSysRupsWhereDateLastUnknown(fltSysRupIndex, aveRecurIntervals, presentTimeMillis);
//			double condRecurIntWhereUnknown = aveCondRecurInterval;
			
			if(aveNormTimeSinceLast) {
				for(int i=0;i<normBPT_CDF.size();i++) {
					double normTimeSinceYears = normBPT_CDF.getX(i);
					double relProbForTimeSinceLast = 1.0-normBPT_CDF.getY(i);	// this is the probability of the date of last event (not considering hist open interval)
					if(normTimeSinceYears*condRecurIntWhereUnknown>=histOpenInterval && relProbForTimeSinceLast>1e-15) {
						double aveNormTS = (normTimeSinceYears*areaWithOutDateOfLast + aveNormTimeSinceLastEventWhereKnown*totRupAreaWithDateOfLast)/totRupArea;
						double condProb = computeBPT_ProbFast(1.0, aveNormTS, durationYears/aveCondRecurInterval, rupMag);
						sumCondProbGain += (condProb/expNum)*relProbForTimeSinceLast;
						totWeight += relProbForTimeSinceLast;
						
//if(fltSysRupIndex==testRupID) {
//		System.out.println("\t"+i+"\t"+(float)normTimeSinceYears+"\t"+(float)aveNormTS+"\t"+(float)condProb+"\t"+(float)(condProb/expNum)+
//				"\t"+(float)relProbForTimeSinceLast+"\t"+(float)condRecurIntWhereUnknown);
//}
					}
				}
			}
			else {	// average date of last event
				for(int i=0;i<normBPT_CDF.size();i++) {
					double timeSinceYears = normBPT_CDF.getX(i)*condRecurIntWhereUnknown;
					double relProbForTimeSinceLast = 1.0-normBPT_CDF.getY(i);	// this is the probability of the date of last event (not considering hist open interval)
//if(fltSysRupIndex==testRupID) {
//	System.out.println("\t"+i+"\t"+(float)timeSinceYears+"\t"+(float)relProbForTimeSinceLast+"\t"+(float)condRecurIntWhereUnknown+
//			"\t"+(timeSinceYears>=histOpenInterval)+"\t"+(relProbForTimeSinceLast>0.0));
//}

					if(timeSinceYears>=histOpenInterval && relProbForTimeSinceLast>1e-15) {
						// average the time since last between known and unknown sections
						double aveTimeSinceLast = (timeSinceYears*areaWithOutDateOfLast + aveTimeSinceLastWhereKnownYears*totRupAreaWithDateOfLast)/totRupArea;
						double condProb = computeBPT_ProbFast(aveCondRecurInterval, aveTimeSinceLast, durationYears, rupMag);
						sumCondProbGain += (condProb/expNum)*relProbForTimeSinceLast;
						totWeight += relProbForTimeSinceLast;
						
// test
//if(fltSysRupIndex==testRupID) {
//			System.out.println("\t"+i+"\t"+(float)timeSinceYears+"\t"+(float)aveTimeSinceLast+"\t"+(float)condProb+"\t"+(float)(condProb/expNum)+
//					"\t"+(float)relProbForTimeSinceLast+"\t"+(float)condRecurIntWhereUnknown);
//}
					}
				}	
			}
			
			if(totWeight>0) {
				probGain = sumCondProbGain/totWeight;
			}
			else {	// deal with case where there was no viable time since last; use exactly historic open interval
//List<FaultSectionPrefData> fltDataList = fltSysRupSet.getFaultSectionDataForRupture(fltSysRupIndex);
//System.out.println("FIXING: "+fltDataList.get(0).getName()+" to "+fltDataList.get(fltDataList.size()-1).getName()+
//		"\tFractAreaUnknown="+(areaWithOutDateOfLast/totRupArea));
				if(aveNormTimeSinceLast) {
					double normTimeSinceYearsUnknown = histOpenInterval/condRecurIntWhereUnknown;
					double aveNormTS = (normTimeSinceYearsUnknown*areaWithOutDateOfLast + aveNormTimeSinceLastEventWhereKnown*totRupAreaWithDateOfLast)/totRupArea;
					double condProb = computeBPT_ProbFast(1.0, aveNormTS, durationYears/aveCondRecurInterval, rupMag);
					probGain = condProb/expNum;
				}
				else {
					double aveTimeSinceLast = (histOpenInterval*areaWithOutDateOfLast + aveTimeSinceLastWhereKnownYears*totRupAreaWithDateOfLast)/totRupArea;
					double condProb = computeBPT_ProbFast(aveCondRecurInterval, aveTimeSinceLast, durationYears, rupMag);
					probGain = condProb/expNum;
				}
			}
		}
		
//// test
//		if(fltSysRupIndex==testRupID) {
//				System.out.println("\tprobGain="+probGain);
//}

		
		if(simulationMode) {
			if(aveTimeSinceLastWhereKnownYears/aveCondRecurInterval > simNormTimeSinceLastHist.getMaxX()) {
				simNormTimeSinceLastHist.add(simNormTimeSinceLastHist.getMaxX(), longTermRateOfFltSysRup[fltSysRupIndex]);
				if(rupMag <= 7)
					simNormTimeSinceLastForMagBelow7_Hist.add(simNormTimeSinceLastHist.getMaxX(), longTermRateOfFltSysRup[fltSysRupIndex]);
				else
					simNormTimeSinceLastForMagAbove7_Hist.add(simNormTimeSinceLastHist.getMaxX(), longTermRateOfFltSysRup[fltSysRupIndex]);
			}
			else {
				simNormTimeSinceLastHist.add(aveTimeSinceLastWhereKnownYears/aveCondRecurInterval, longTermRateOfFltSysRup[fltSysRupIndex]);
				if(rupMag <= 7)
					simNormTimeSinceLastForMagBelow7_Hist.add(aveTimeSinceLastWhereKnownYears/aveCondRecurInterval, longTermRateOfFltSysRup[fltSysRupIndex]);
				else
					simNormTimeSinceLastForMagAbove7_Hist.add(aveTimeSinceLastWhereKnownYears/aveCondRecurInterval, longTermRateOfFltSysRup[fltSysRupIndex]);
			}
			
			if(probGain > simProbGainHist.getMaxX()) {
				simProbGainHist.add(simProbGainHist.getMaxX(), longTermRateOfFltSysRup[fltSysRupIndex]);
				if(rupMag <= 7)
					simProbGainForMagBelow7_Hist.add(simProbGainHist.getMaxX(), longTermRateOfFltSysRup[fltSysRupIndex]);
				else
					simProbGainForMagAbove7_Hist.add(simProbGainHist.getMaxX(), longTermRateOfFltSysRup[fltSysRupIndex]);
			}
			else {
				simProbGainHist.add(probGain, longTermRateOfFltSysRup[fltSysRupIndex]);
				if(rupMag <= 7)
					simProbGainForMagBelow7_Hist.add(probGain, longTermRateOfFltSysRup[fltSysRupIndex]);
				else
					simProbGainForMagAbove7_Hist.add(probGain, longTermRateOfFltSysRup[fltSysRupIndex]);
			}
		}
		
		if(Double.isNaN(probGain))
			throw new RuntimeException("NaN fltSysRupIndex="+fltSysRupIndex);
		
		return probGain;

	}

	
	/**
	 * This returns the probability gain computed using the WG02 methodology, where the probability
	 * gain of each sections is averaged, weighted by section area (actually, this should be weighted
	 * by moment rate, but tests show no difference).
	 * 
	 * This returns Double.NaN if onlyIfAllSectionsHaveDateOfLast=true and one or mare sections
	 * lack date of last event.
	 * 
	 * @param fltSystRupIndex
	 * @param onlyIfAllSectionsHaveDateOfLast
	 */
	public double getWG02_ProbGainForRup(int fltSysRupIndex, boolean onlyIfAllSectionsHaveDateOfLast, long presentTimeMillis, double durationYears) {
		
		if(aperValues.length>1)
			throw new RuntimeException("WG02 option can only have one aperiodicity value");
		
		BPT_DistCalc refBPT_DistributionCalc = getRef_BPT_DistCalc(aperValues[0]);
		
		// first compute the gains for each fault section if it does not exist
		if(sectionGainArray==null) {
			sectionGainArray = new double[numSections];
			sectionGainReal = new boolean[numSections];
			for(int s=0; s<numSections;s++) {
				long timeOfLastMillis = dateOfLastForSect[s];
				if(timeOfLastMillis != Long.MIN_VALUE && timeOfLastMillis <= presentTimeMillis) {
					double timeSinceLastYears = ((double)(presentTimeMillis-timeOfLastMillis))/MILLISEC_PER_YEAR;
					double refTimeSinceLast = timeSinceLastYears*refRI*longTermPartRateForSectArray[s];
					double refDuration = durationYears*refRI*longTermPartRateForSectArray[s];
					double prob_bpt = refBPT_DistributionCalc.getCondProb(refTimeSinceLast, refDuration);
//					double prob_pois = 1-Math.exp(-durationYears*longTermPartRateForSectArray[s]);
					double prob_pois = durationYears*longTermPartRateForSectArray[s];	// this is there exact calculation, which is a bit different for long durations
					sectionGainArray[s] = prob_bpt/prob_pois;
					sectionGainReal[s]=true;
				}
				else {
					sectionGainArray[s] = 1.0;
					sectionGainReal[s]=false;
				}
			}			
		}
		
		// now compute weight-average gain for rupture
		double totalWt=0;
		double sumGains = 0;
		boolean noneAreReal = true;
		for(int sect : fltSysRupSet.getSectionsIndicesForRup(fltSysRupIndex)) {
//test				double wt = sectionArea[sect]*this.fltSysRupSet.getSlipRateForSection(sect);
			double wt = sectionArea[sect];
			totalWt += wt;
			sumGains += sectionGainArray[sect]*wt;
			if(sectionGainReal[sect] == false && onlyIfAllSectionsHaveDateOfLast) {
				return Double.NaN;
			}
			if(sectionGainReal[sect] == true) {
				noneAreReal=false;
			}
		}
		if(noneAreReal)
			return 1d;
		else
			return sumGains/totalWt;
	}
	
	
	
	/**
	 * This only applies the first aperiodicity value
	 */
	public void plotXYZ_FuncOfCondProbForUnknownDateOfLastEvent() {
		
		double aperiodicity = aperValues[0];

		BPT_DistCalc bptCalc2 = getRef_BPT_DistCalc(aperiodicity);

		EvenlyDiscrXYZ_DataSet condProbForUnknownDateOfLast_xyzData;
		
		double minLogDurOverMean = -7;
		double maxLogDurOverMean = Math.log10(5.01187);	// this is 0.7
		double deltaLogDurOverMean = 0.1;
		int numLogDurOverMean = 1+(int)Math.ceil((maxLogDurOverMean-minLogDurOverMean)/deltaLogDurOverMean);

		double minLogHistOpenIntOverMean = -2;
		double maxLogHistOpenIntOverMean = Math.log10(5.01187);
		double deltaLogHistOpenIntOverMean = 0.025;
		int numLogHistOpenIntOverMean = 1+(int)Math.ceil((maxLogHistOpenIntOverMean-minLogHistOpenIntOverMean)/deltaLogHistOpenIntOverMean);

		// this is what we will return
		EvenlyDiscrXYZ_DataSet xyzDataCondProbForUnknown = new EvenlyDiscrXYZ_DataSet(numLogDurOverMean, numLogHistOpenIntOverMean, 
				minLogDurOverMean, minLogHistOpenIntOverMean, deltaLogDurOverMean,deltaLogHistOpenIntOverMean);
		
		EvenlyDiscrXYZ_DataSet xyzDataProbGain=xyzDataProbGain = new EvenlyDiscrXYZ_DataSet(numLogDurOverMean, numLogHistOpenIntOverMean, 
				minLogDurOverMean, minLogHistOpenIntOverMean, deltaLogDurOverMean,deltaLogHistOpenIntOverMean);
			
		for(int y=0;y<xyzDataCondProbForUnknown.getNumY();y++) {
			double logHistOpenIntOverMean = xyzDataCondProbForUnknown.getY(y);
			double histOpenIntOverMean = Math.pow(10,logHistOpenIntOverMean);
			double histOpenInterval = histOpenIntOverMean*refRI;
			for(int x=0;x<xyzDataCondProbForUnknown.getNumX();x++) {
				double logDurOverMean = xyzDataCondProbForUnknown.getX(x);
				double durOverMean = Math.pow(10,logDurOverMean);
				double duration = durOverMean*refRI;

				// get condProbForUnknownTimeSinceLast & condProbFunc from the calculator
				bptCalc2.setDurationAndHistOpenInterval(duration, histOpenInterval);
				double condProbForUnknownTimeSinceLast = bptCalc2.getCondProbForUnknownTimeSinceLastEvent();
				xyzDataCondProbForUnknown.set(x, y, Math.log10(condProbForUnknownTimeSinceLast));
				double probGain = condProbForUnknownTimeSinceLast/computePoissonProb(refRI, duration);
				xyzDataProbGain.set(x, y, Math.log10(probGain));

//				if(x==0 && y==0)	// print header
//					System.out.println("aperiodicity\tmean\tduration\thistOpenInterval\tlogDurOverMean\tlogHistOpenIntOverMean\tcondProb\tlog10_CondProb\tgain");
//
//				System.out.println(aperiodicity+"\t"+refRI+"\t"+(float)duration+"\t"+(float)histOpenInterval+"\t"+(float)logDurOverMean+"\t"+(float)logHistOpenIntOverMean+
//						"\t"+condProbForUnknownTimeSinceLast+"\t"+Math.log10(condProbForUnknownTimeSinceLast)+"\t"+probGain);

			}
		}

		CPT cpt_prob=null;
		CPT cpt_probGain=null;
		try {
			cpt_prob = GMT_CPT_Files.MAX_SPECTRUM.instance().rescale(-8, 0);
			cpt_probGain = GMT_CPT_Files.MAX_SPECTRUM.instance().rescale(-1, 2);
//			cpt_prob = GMT_CPT_Files.MAX_SPECTRUM.instance().rescale(xyzDataCondProbForUnknown.getMinZ(), 0);
//			cpt_probGain = GMT_CPT_Files.MAX_SPECTRUM.instance().rescale(xyzDataProbGain.getMinZ(), xyzDataProbGain.getMaxZ());
//			System.out.println("\t condProb min & max:\t"+(float)xyzDataCondProbForUnknown.getMinZ()+"\t"+(float)xyzDataCondProbForUnknown.getMaxZ());
//			System.out.println("\t probGain min & max:\t"+(float)xyzDataProbGain.getMinZ()+"\t"+(float)xyzDataProbGain.getMaxZ());
		} catch (IOException e) {
			e.printStackTrace();
		}
		XYZPlotSpec spec_prob = new XYZPlotSpec(xyzDataCondProbForUnknown, cpt_prob, "CondProbForUnknownLast; aper="+(float)aperiodicity, "LogNormDuration", "LogNormHistOpenInt", "Probability");
		XYZPlotWindow window_prob = new XYZPlotWindow(spec_prob);
		XYZPlotSpec spec_probGain = new XYZPlotSpec(xyzDataProbGain, cpt_probGain, "Log10 Prob Gain (vs Poisson); aper="+(float)aperiodicity, "LogNormDuration", "LogNormHistOpenInt", "Log10 Prob Gain");
		XYZPlotWindow window_probGain = new XYZPlotWindow(spec_probGain);

	}
	
	

	/**
	 * 	 This only applies the first aperiodicity value
	 */
	public void plotXYZ_FuncOfCondProb() {
		
		double aperiodicity = aperValues[0];

		BPT_DistCalc bptCalc2 = getRef_BPT_DistCalc(aperiodicity);

		double minLogDurOverMean = -1;
		double maxLogDurOverMean = Math.log10(5.01187);	// this is 0.7
		double deltaLogDurOverMean = 0.01;
		int numLogDurOverMean = 1+(int)Math.ceil((maxLogDurOverMean-minLogDurOverMean)/deltaLogDurOverMean);

		double minLogNormTimeSinceLast = -1;
		double maxLogNormTimeSinceLast = Math.log10(5.01187);
		double deltaLogNormTimeSinceLast = 0.01;
		int numLogNormTimeSinceLast = 1+(int)Math.ceil((maxLogNormTimeSinceLast-minLogNormTimeSinceLast)/deltaLogNormTimeSinceLast);

		EvenlyDiscrXYZ_DataSet xyzDataCondProb = new EvenlyDiscrXYZ_DataSet(numLogNormTimeSinceLast, numLogDurOverMean,
				minLogNormTimeSinceLast, minLogDurOverMean, deltaLogNormTimeSinceLast, deltaLogDurOverMean);
		
		EvenlyDiscrXYZ_DataSet xyzDataProbGain = new EvenlyDiscrXYZ_DataSet(numLogNormTimeSinceLast, numLogDurOverMean,
				minLogNormTimeSinceLast, minLogDurOverMean, deltaLogNormTimeSinceLast, deltaLogDurOverMean);
			
		for(int x=0;x<xyzDataCondProb.getNumX();x++) {
			double logNormTimeSinceLast = xyzDataCondProb.getX(x);
			double normTimeSinceLast = Math.pow(10,logNormTimeSinceLast);
			double timeSinceLast = normTimeSinceLast*refRI;
			for(int y=0;y<xyzDataCondProb.getNumY();y++) {
				double logDurOverMean = xyzDataCondProb.getY(y);
				double durOverMean = Math.pow(10,logDurOverMean);
				double duration = durOverMean*refRI;

				double condProb = bptCalc2.getCondProb(timeSinceLast, duration);
				if(condProb == 0) 
					condProb = Double.NaN;
				xyzDataCondProb.set(x, y, Math.log10(condProb));
//				xyzDataCondProb.set(x, y, condProb);
				double probGain = condProb/computePoissonProb(refRI, duration);
				xyzDataProbGain.set(x, y, Math.log10(probGain));
//				xyzDataProbGain.set(x, y, probGain);

			}
		}

		CPT cpt_prob=null;
		CPT cpt_probGain=null;
		try {
//			cpt_prob = GMT_CPT_Files.MAX_SPECTRUM.instance().rescale(0, 1);
			cpt_prob = GMT_CPT_Files.MAX_SPECTRUM.instance().rescale(xyzDataCondProb.getMinZ(), xyzDataCondProb.getMaxZ());
			cpt_probGain = GMT_CPT_Files.MAX_SPECTRUM.instance().rescale(xyzDataProbGain.getMinZ(), xyzDataProbGain.getMaxZ());
		} catch (IOException e) {
			e.printStackTrace();
		}
		XYZPlotSpec spec_prob = new XYZPlotSpec(xyzDataCondProb, cpt_prob, "Log10 BPT Cond Prob; aper="+(float)aperiodicity, "LogNormTimeSinceLast", "LogNormDuration", "Probability");
		XYZPlotWindow window_prob = new XYZPlotWindow(spec_prob);
		XYZPlotSpec spec_probGain = new XYZPlotSpec(xyzDataProbGain, cpt_probGain, "Log10 Prob Gain (vs Poisson); aper="+(float)aperiodicity, "LogNormTimeSinceLast", "LogNormDuration", "Log10 Prob Gain");
		XYZPlotWindow window_probGain = new XYZPlotWindow(spec_probGain);

	}

	

	
	
	



	
	


	/**
	 * This is made fast by using a reference calculator (with a reference RI), rather than
	 * redoing the calculation each time .
	 * 
	 * @param aveRecurIntervalYears
	 * @param duration
	 * @return
	 */
	public double computeBPT_ProbFast(double aveRecurIntervalYears, double aveTimeSinceLastYears, double durationYears, double rupMag) {
		
		BPT_DistCalc refBPT_DistributionCalc = refBPT_CalcArray[getAperIndexForRupMag(rupMag)];
				
		
		double newTimeSinceLast = aveTimeSinceLastYears*refRI/aveRecurIntervalYears;
		if(newTimeSinceLast<0 && newTimeSinceLast > -1e-10)
			newTimeSinceLast=0;
		double prob=refBPT_DistributionCalc.getCondProb(newTimeSinceLast, durationYears*refRI/aveRecurIntervalYears);
//		if(prob<0d)
//			System.out.println("Negative Prob: "+prob+"\t"+aveRecurIntervalYears+"\t"+aveTimeSinceLastYears+"\t"+durationYears);
		return prob;
	}
	

	
	/**
	 * 
	 * @param aveRecurIntervalYears
	 * @param duration
	 * @return
	 */
	public double computeBPT_Prob(double aveRecurIntervalYears, double aveTimeSinceLastYears, double durationYears, double aperiodicity) {
		double delta = aveRecurIntervalYears/200d;
		int numPts = (int)Math.round((9*aveRecurIntervalYears)/delta);
		BPT_DistCalc bptCalc = new BPT_DistCalc();
		bptCalc.setAll(aveRecurIntervalYears, aperiodicity, delta, numPts);
		return bptCalc.getCondProb(aveTimeSinceLastYears, durationYears);
	}
	
	
	/**
	 * This is made fast by using a reference calculator (with a reference RI), rather than
	 * redoing the calculation each time .
	 * 
	 * @return
	 */
	public double computeBPT_ProbForUnknownDateOfLastFast(double aveRecurIntervalYears, double histOpenIntervalYears, double durationYears, double rupMag) {
		BPT_DistCalc refBPT_DistributionCalc = refBPT_CalcArray[getAperIndexForRupMag(rupMag)];
		refBPT_DistributionCalc.setDurationAndHistOpenInterval(durationYears*refRI/aveRecurIntervalYears, histOpenIntervalYears*refRI/aveRecurIntervalYears);
		return refBPT_DistributionCalc.getCondProbForUnknownTimeSinceLastEvent();	 
	}
	
	
	/**
	 * 
	 * 
	 * @return
	 */
	public double computeBPT_ProbForUnknownDateOfLast(double aveRecurIntervalYears, double histOpenIntervalYears, double durationYears, double aperiodicity) {
		double delta = aveRecurIntervalYears/200d;
		int numPts = (int)Math.round((9*aveRecurIntervalYears)/delta);
		BPT_DistCalc bptCalc = new BPT_DistCalc();
		bptCalc.setAll(aveRecurIntervalYears, aperiodicity, delta, numPts, durationYears, histOpenIntervalYears);
		return bptCalc.getCondProbForUnknownTimeSinceLastEvent();	 
	}

	
	/**
	 * This computes the poisson probability of one or more events
	 * @param aveRecurIntevalYears
	 * @param durationYears
	 * @return
	 */
	public static double computePoissonProb(double aveRecurIntevalYears, double durationYears) {
		return 1.0-Math.exp(-durationYears/aveRecurIntevalYears);
	}

	


	/**
	 * This creates a reference BPT distribution calculator for the given aperiodicity and duration
	 * (the calculator uses refRI and deltaT) 
	 * @param bpt_Aperiodicity
	 * @return
	 */
	protected static BPT_DistCalc getRef_BPT_DistCalc(double bpt_Aperiodicity) {
		int numPts = (int)Math.round((9*refRI)/deltaT);
		BPT_DistCalc bptCalc = new BPT_DistCalc();
		bptCalc.setAll(refRI, bpt_Aperiodicity, deltaT, numPts);
		return bptCalc;
	}

	
	/**
	 * This creates a reference BPT distribution calculators for the given aperiodicities and duration
	 * (the calculator uses refRI and deltaT) 
	 * @param bpt_Aperiodicity
	 * @param durationInYears
	 * @return
	 */
	protected BPT_DistCalc[] getRef_BPT_DistCalcArray() {
		
		BPT_DistCalc[] bptCalcArray = new BPT_DistCalc[numAperValues];
		int numPts = (int)Math.round((9*refRI)/deltaT);
		
		for(int i=0;i<numAperValues;i++) {
			BPT_DistCalc bptCalc = new BPT_DistCalc();
			bptCalc.setAll(refRI, aperValues[i], deltaT, numPts);
			bptCalcArray[i]=bptCalc;			
		}
		
		return bptCalcArray;
	}
	
	
	protected int getAperIndexForRupMag(double rupMag) {
		int index = -1;
		if(numAperValues==1)
			index = 0;	// only one
		else if(rupMag>aperMagBoundaries[numAperValues-2])	// minus 2 to get last value since aperMagBoundaries has one less element
			index = numAperValues-1;	// the last one
		else {
			for(int m=0; m<aperMagBoundaries.length;m++) {
				if(rupMag<=aperMagBoundaries[m]) {
					index = m;
					break;
				}
			}
		}
		return index;
	}
	
	protected String getMagDepAperInfoString(int aperIndex) {
		if(numAperValues == 1) {
			return "aper="+aperValues[0];
		}
		if(aperIndex == 0) {	// first one
			return "M<="+aperMagBoundaries[0]+"; aper="+aperValues[0];
		}
		else if(aperIndex == numAperValues-1) {	// last one
			return "M>"+aperMagBoundaries[numAperValues-2]+"; aper="+aperValues[numAperValues-1];
		}
		else {	// intermediate one
			return aperMagBoundaries[aperIndex-1]+"<M<="+aperMagBoundaries[aperIndex]+"; aper="+aperValues[aperIndex];
		}
		
	}

	
	/**
	 * This creates an array of BPT CDFs (one for each aperiodicity)
	 * @return
	 */
	protected EvenlyDiscretizedFunc[] getNormBPT_CDF_Array() {
		
		EvenlyDiscretizedFunc[] normCDF_Array = new EvenlyDiscretizedFunc[numAperValues];
		
		for(int i=0;i<numAperValues;i++) {
			BPT_DistCalc tempCalc = new BPT_DistCalc();
			double delta = max_time_for_normBPT_CDF/(num_for_normBPT_CDF-1);
			tempCalc.setAll(1.0, aperValues[i], delta, num_for_normBPT_CDF);
			normCDF_Array[i]=tempCalc.getCDF();		
		}
		
		return normCDF_Array;
	}

	

	
	

	
	/**
	 * This test the slow versus fact computations here, monte carlo sampling over a range of aver recur intervals, 
	 * normalized durations, normalized time since last, and normalize historic open intervals.  Fractional discrepancies
	 * greater than 0.001 are listed.  All are generally small and for large normalized durations or historic open intervals,
	 * where differences in how to avoid numerical problems arise.  I don't think any of these are significant for UCERF3, but
	 * the better test for the latter would be to do it both ways for UCERF3 (test the long way against the fast caclulations).
	 * @param numTests
	 */
	public static void testFastCalculations(int numTests) {
		
		System.out.println("Prob\tdiff\tprob\tprob_fast\taperiodicity\taveRI\tdur\ttimeSince\thistOpenInt\tnormDur\tnormTimeSince\tnormHistOpenInt");
		
		double[] apers = {0.1,0.2,0.3,0.4,0.5,0.6,0.7,0.8,0.9,1.0};
		for(double aper:apers) {
			ProbabilityModelsCalc calc = new ProbabilityModelsCalc(aper);
			double diffThresh = 0.001;
			for(int i=0;i<numTests; i++) {
				double aveRI = 20 +Math.random()*1.0e5;	  // get mean between 20 and 100,020 years
				double normDur = 0.001+Math.random()*5.0; // get normalized duration between 0.001 and 5.001
				double normTimeSince = Math.random()*5.0; // get normalized time since last between and 5
				double normHistOpenInt = normTimeSince;
				double dur = normDur*aveRI;
				double timeSince = normTimeSince*aveRI;
				double histOpenInt = normHistOpenInt*aveRI;
				
				double rupMag = Double.NaN;	// this will be ignored because there is only one aperiodicity here
				
				double prob1 = calc.computeBPT_Prob(aveRI, timeSince, dur, aper);
				double prob2 = calc.computeBPT_ProbForUnknownDateOfLast(aveRI, histOpenInt, dur, aper);
				double prob1_fast = calc.computeBPT_ProbFast(aveRI, timeSince, dur, rupMag);
				double prob2_fast = calc.computeBPT_ProbForUnknownDateOfLastFast(aveRI, histOpenInt, dur, rupMag);
				
				double diff1 = Math.abs((prob1-prob1_fast)/prob1);
				if(prob1<1e-12 && prob1_fast<1e-12)
					diff1 = 0;

				double diff2 = Math.abs((prob2-prob2_fast)/prob2);
				if(prob2<1e-12 && prob2_fast<1e-12)
					diff2 = 0;
				
				if(diff1>diffThresh)
					System.out.println("Prob1\t"+diff1+"\t"+prob1+"\t"+prob1_fast+"\t"+aper+"\t"+aveRI+"\t"+dur+"\t"+timeSince+"\t"+Double.NaN+"\t"+normDur+"\t"+normTimeSince+"\t"+Double.NaN);
//					throw new RuntimeException("problem with diff1");
				
				if(diff2>diffThresh)
					System.out.println("Prob2\t"+diff2+"\t"+prob2+"\t"+prob2_fast+"\t"+aper+"\t"+aveRI+"\t"+dur+"\t"+Double.NaN+"\t"+histOpenInt+"\t"+normDur+"\t"+Double.NaN+"\t"+normHistOpenInt);
//					throw new RuntimeException("problem with diff2");
			}			
		}
	}
	
	
	
	/**
	 * This simulates events from using elastic rebound probabilities
	 * 
	 * This assumes the rate of each rupture is constant up until the next event is sampled.
	 * 
	 * TODO:
	 * 
	 * Shouldn't pass in erf (use the local one)
	 * 
	 * Have this ignore non-fault=system ruptures (others take way too long this way).
	 * 
	 * add progress bar
	 * 
	 */
	public void testER_Simulation(String inputDateOfLastFileName, String outputDateOfLastFileName, FaultSystemSolutionERF erf, double numYears, String dirNameSuffix) {
		
		
		int testSectionIndex = 1946;
		
		boolean aveRecurIntervals = erf.aveRecurIntervalsInU3_BPTcalc;
		boolean aveNormTimeSinceLast = erf.aveNormTimeSinceLastInU3_BPTcalc;
		
		this.simulationMode=true;
		simNormTimeSinceLastHist = new HistogramFunction(0.05, 100, 0.1);	// up to 10
		simNormTimeSinceLastForMagBelow7_Hist = new HistogramFunction(0.05, 100, 0.1);	// up to 10
		simNormTimeSinceLastForMagAbove7_Hist = new HistogramFunction(0.05, 100, 0.1);
		simProbGainHist = new HistogramFunction(0.05, 100, 0.1);	// up to 10
		simProbGainForMagAbove7_Hist = new HistogramFunction(0.05, 100, 0.1);	// up to 10
		simProbGainForMagBelow7_Hist = new HistogramFunction(0.05, 100, 0.1);	// up to 10
		
		
		// LABELING AND FILENAME STUFF
		String typeCalcForU3_Probs;
		if(aveRecurIntervals)
			typeCalcForU3_Probs = "aveRI";
		else
			typeCalcForU3_Probs = "aveRate";
		if(aveNormTimeSinceLast)
			typeCalcForU3_Probs += "_aveNormTimeSince";
		else
			typeCalcForU3_Probs += "_aveTimeSince";

//		String aperiodicity="DefaultRange";
//		String aperString = "aper"+aperiodicity;
		
		String probTypeString;
		ProbabilityModelOptions probTypeEnum = (ProbabilityModelOptions)erf.getParameter(ProbabilityModelParam.NAME).getValue();
		
		String aperString = "aper";
		if(probTypeEnum != ProbabilityModelOptions.POISSON) {
			boolean first = true;
			for(double aperVal:this.aperValues) {
				if(first)
					aperString+=aperVal;
				else
					aperString+=","+aperVal;
				first=false;
			}
			aperString = aperString.replace(".", "pt");			
		}
		
		System.out.println("\naperString: "+aperString+"\n");
		
		int tempDur = (int) Math.round(numYears/1000);
		
		if (probTypeEnum == ProbabilityModelOptions.POISSON) {
			probTypeString= "Pois";
		}
		else if(probTypeEnum == ProbabilityModelOptions.U3_BPT) {
			probTypeString= "U3BPT";
		}
		else if(probTypeEnum == ProbabilityModelOptions.WG02_BPT) {
			probTypeString= "WG02BPT";
		}
		else
			throw new RuntimeException("Porbability type unrecognized");
		
		String dirNameForSavingFiles = "U3ER_"+probTypeString+"_"+tempDur+"kyr";
		if(probTypeEnum != ProbabilityModelOptions.POISSON) {
			dirNameForSavingFiles += "_"+aperString;
			dirNameForSavingFiles += "_"+typeCalcForU3_Probs;
		}
		
		dirNameForSavingFiles += "_"+dirNameSuffix;
		
		String plotLabelString = probTypeString;
		if(probTypeEnum == ProbabilityModelOptions.U3_BPT)
			plotLabelString += " ("+aperString+", "+typeCalcForU3_Probs+")";
		else if(probTypeEnum == ProbabilityModelOptions.WG02_BPT)
			plotLabelString += " ("+aperString+")";

		File resultsDir = new File(dirNameForSavingFiles);
		if(!resultsDir.exists()) resultsDir.mkdir();

		
		// INTIALIZE THINGS:
		
		double[] probGainForFaultSystemSource = new double[erf.getNumFaultSystemSources()];
		for(int s=0;s<probGainForFaultSystemSource.length;s++)
			probGainForFaultSystemSource[s] = 1.0;	// default is 1.0

		// set original start time and total duration
		long origStartTimeMillis = 0;
		if(probTypeEnum != ProbabilityModelOptions.POISSON)
			origStartTimeMillis = erf.getTimeSpan().getStartTimeInMillis();
		double origStartYear = ((double)origStartTimeMillis)/MILLISEC_PER_YEAR+1970.0;
		System.out.println("orig start time: "+origStartTimeMillis+ " millis ("+origStartYear+" yrs)");
		System.out.println("numYears: "+numYears);
				
		// initialize some things
		ArrayList<Double> normalizedRupRecurIntervals = new ArrayList<Double>();
		ArrayList<Double> normalizedSectRecurIntervals = new ArrayList<Double>();
		ArrayList<Double> normalizedSectRecurIntervalsForTestSect = new ArrayList<Double>();
		ArrayList<ArrayList<Double>> normalizedRupRecurIntervalsMagDepList = new ArrayList<ArrayList<Double>>();
		ArrayList<ArrayList<Double>> normalizedSectRecurIntervalsMagDepList = new ArrayList<ArrayList<Double>>();
		for(int i=0;i<numAperValues;i++) {
			normalizedRupRecurIntervalsMagDepList.add(new ArrayList<Double>());
			normalizedSectRecurIntervalsMagDepList.add(new ArrayList<Double>());
		}
		ArrayList<Double> yearsIntoSimulation = new ArrayList<Double>();
		ArrayList<Double> totRateAtYearsIntoSimulation = new ArrayList<Double>();
    	ArbDiscrEmpiricalDistFunc_3D normRI_AlongStrike = new ArbDiscrEmpiricalDistFunc_3D(0.05d,0.95d,10);
		double[] obsSectRateArray = new double[numSections];
		double[] obsSectSlipRateArray = new double[numSections];
		double[] obsSectRateArrayM6pt05to6pt65 = new double[numSections];
		double[] obsSectRateArrayM7pt95to8pt25 = new double[numSections];
		double[] obsRupRateArray = new double[erf.getTotNumRups()];
		double[] aveRupProbGainArray = new double[erf.getTotNumRups()];	// averages the prob gains at each event time
		double[] minRupProbGainArray = new double[erf.getTotNumRups()];	// averages the prob gains at each event time
		double[] maxRupProbGainArray = new double[erf.getTotNumRups()];	// averages the prob gains at each event time
		
		// this is for writing out simulated events that occur
		FileWriter eventFileWriter=null;
		try {
			eventFileWriter = new FileWriter(dirNameForSavingFiles+"/sampledEventsData.txt");
			eventFileWriter.write("nthRupIndex\tfssRupIndex\tyear\tepoch\tnormRupRI\n");
		} catch (IOException e1) {
			e1.printStackTrace();
		}


		int numRups=0;
//		RandomDataImpl randomDataSampler = new RandomDataImpl();	// old apache tool for sampling from exponential distribution here
		RandomDataGenerator randomDataSampler = new RandomDataGenerator();
		
		// set the forecast as Poisson to get long-term rates (and update)
		erf.getParameter(ProbabilityModelParam.NAME).setValue(ProbabilityModelOptions.POISSON);
		erf.updateForecast();

		
		// fill in totalRate, longTermRateOfNthRups, magOfNthRups, and longTermSlipRateForSectArray
		double totalRate=0;
		IntegerPDF_FunctionSampler nthRupRandomSampler = new IntegerPDF_FunctionSampler(erf.getTotNumRups());
		double[] longTermRateOfNthRups = new double[erf.getTotNumRups()];	// this will include any aftershock reductions
		double[] magOfNthRups = new double[erf.getTotNumRups()];
		double[] longTermSlipRateForSectArray = new double[numSections];
		int nthRup=0;
		for(ProbEqkSource src:erf) {
			for(ProbEqkRupture rup:src) {
				double rate = rup.getMeanAnnualRate(erf.getTimeSpan().getDuration());
				longTermRateOfNthRups[nthRup] = rate;
				magOfNthRups[nthRup] = rup.getMag();
				totalRate += longTermRateOfNthRups[nthRup];
				nthRupRandomSampler.set(nthRup, rate);
				if(erf.getSrcIndexForNthRup(nthRup)<erf.getNumFaultSystemSources()) {
					// slip rates
					int fltSysIndex = erf.getFltSysRupIndexForNthRup(nthRup);
					List<Integer> sectIndices = fltSysRupSet.getSectionsIndicesForRup(fltSysIndex);
					double slips[];
					if(fltSysRupSet instanceof InversionFaultSystemRupSet) {
						slips = ((InversionFaultSystemRupSet) fltSysRupSet).getSlipOnSectionsForRup(erf.getFltSysRupIndexForNthRup(nthRup));
					}
					else {	// apply ave to all sections
						double mag = fltSysRupSet.getMagForRup(erf.getFltSysRupIndexForNthRup(nthRup));
						double area = fltSysRupSet.getAreaForRup(erf.getFltSysRupIndexForNthRup(nthRup));
						double aveSlip = FaultMomentCalc.getSlip(area, MagUtils.magToMoment(mag));
						slips = new double[sectIndices.size()];
						for(int i=0;i<slips.length;i++)
							slips[i]=aveSlip;
					}
					for(int s=0;s<sectIndices.size();s++) {
						int sectID = sectIndices.get(s);
						longTermSlipRateForSectArray[sectID] += rate*slips[s];
					}					
				}
				nthRup+=1;
			}
		}
		System.out.println("totalRate long term = "+totalRate);
		
		double totalLongTermRate = totalRate;
		
		double simDuration = 1/totalLongTermRate;
		

		// Make local sectIndexArrayForSrcList for faster simulations
		ArrayList<int[]> sectIndexArrayForSrcList = new ArrayList<int[]>();
		for(int s=0; s<erf.getNumFaultSystemSources();s++) {
			List<Integer> indexList = fltSysRupSet.getSectionsIndicesForRup(erf.getFltSysRupIndexForSource(s));
			int[] indexArray = new int[indexList.size()];
			for(int i=0;i<indexList.size();i++)
				indexArray[i] = indexList.get(i);
			sectIndexArrayForSrcList.add(indexArray);
		}

		
		// for plotting SAF events
		ArrayList<XY_DataSet> safEventFuncs = new ArrayList<XY_DataSet>();
		ArrayList<PlotCurveCharacterstics> safPlotChars4 = new ArrayList<PlotCurveCharacterstics>();

		
		// make the target MFD - 
		if(D) System.out.println("Making target MFD");
		SummedMagFreqDist targetMFD = ERF_Calculator.getTotalMFD_ForERF(erf, 5.05, 8.95, 40, true);
		double origTotMoRate = ERF_Calculator.getTotalMomentRateInRegion(erf, null);
		System.out.println("originalTotalMomentRate: "+origTotMoRate);
		targetMFD.setName("Target MFD");
		String tempString = "total rate = "+(float)targetMFD.getTotalIncrRate();
		tempString += "\ntotal rate >= 6.7 = "+(float)targetMFD.getCumRate(6.75);
		tempString += "\ntotal MoRate = "+(float)origTotMoRate;
		targetMFD.setInfo(tempString);
		
//		System.out.println(targetMFD);

		// MFD for simulation
		SummedMagFreqDist obsMFD = new SummedMagFreqDist(5.05,8.95,40);
		double obsMoRate = 0;
		
		// set the ave cond recurrence intervals
		double[] aveCondRecurIntervalForFltSysRups;
		if(aveRecurIntervals) {
			if(aveCondRecurIntervalForFltSysRups_type1 == null)
				aveCondRecurIntervalForFltSysRups_type1 = computeAveCondRecurIntervalForFltSysRups(1);
			aveCondRecurIntervalForFltSysRups = aveCondRecurIntervalForFltSysRups_type1;
		}
		else {
			if(aveCondRecurIntervalForFltSysRups_type2 == null)
				aveCondRecurIntervalForFltSysRups_type2 = computeAveCondRecurIntervalForFltSysRups(2);
			aveCondRecurIntervalForFltSysRups = aveCondRecurIntervalForFltSysRups_type2;			
		}

		// print minimum and maximum conditional rate of rupture
		double minCondRI=Double.MAX_VALUE,maxCondRI=0;
		for(double ri: aveCondRecurIntervalForFltSysRups) {
			if(!Double.isInfinite(ri)) {
				if(ri < minCondRI) minCondRI = ri;
				if(ri > maxCondRI) maxCondRI = ri;
			}
		}
		System.out.println("minCondRI="+minCondRI);
		System.out.println("maxCondRI="+maxCondRI);
		
		// initialize things
		double currentYear=origStartYear;
		long currentTimeMillis = origStartTimeMillis;
		
		// this is to track progress
		int percDoneThresh=0;
		int percDoneIncrement=5;

		long startRunTime = System.currentTimeMillis();
		
//		System.out.println("section part rates:");
//		for(int i=0;i<longTermPartRateForSectArray.length;i++)
//			System.out.println(i+"\t"+longTermPartRateForSectArray[i]);
		
		// read section date of last file if not null
		if(inputDateOfLastFileName != null && probTypeEnum != ProbabilityModelOptions.POISSON)
			readSectTimeSinceLastEventFromFile(inputDateOfLastFileName, currentTimeMillis);
		else {
			getSectNormTimeSinceLastHistPlot(currentTimeMillis, "From Pref Data");
		}
		
		// test override ***********************************
//		for(int i=0;i<dateOfLastForSect.length;i++)
//			dateOfLastForSect[i] = currentTimeMillis-Math.round(1.0*MILLISEC_PER_YEAR/longTermPartRateForSectArray[i]);
		//*****************************************************
		
		CalcProgressBar progressBar = new CalcProgressBar(dirNameForSavingFiles,"Num Years Done");
		progressBar.showProgress(true);
	
		
		boolean firstEvent = true;
		while (currentYear<numYears+origStartYear) {
			
			progressBar.updateProgress((int)Math.round(currentYear-origStartYear), (int)Math.round(numYears));
			
			// write progress
			int percDone = (int)Math.round(100*(currentYear-origStartYear)/numYears);
			if(percDone >= percDoneThresh) {
				double timeInMin = ((double)(System.currentTimeMillis()-startRunTime)/(1000.0*60.0));
				int numGoodDateOfLast=0;
				for(long dateOfLast:dateOfLastForSect) {
					if(dateOfLast != Long.MIN_VALUE)
						numGoodDateOfLast+=1;					
				}
				int percentGood = (int)Math.round((100.0*(double)numGoodDateOfLast/(double)dateOfLastForSect.length));
				System.out.println("\n"+percDoneThresh+"% done in "+(float)timeInMin+" minutes"+";  totalRate="+(float)totalRate+"; yr="+(float)currentYear+";  % sect with date of last = "+percentGood+"\n");	
				percDoneThresh += percDoneIncrement;
			}
			
			// update gains and sampler if not Poisson
			if(probTypeEnum != ProbabilityModelOptions.POISSON) {
				// first the gains
				if(probTypeEnum == ProbabilityModelOptions.U3_BPT) {
					for(int s=0;s<erf.getNumFaultSystemSources();s++) {
						int fltSysRupIndex = erf.getFltSysRupIndexForSource(s);
						probGainForFaultSystemSource[s] = getU3_ProbGainForRup(fltSysRupIndex, 0.0, false, aveRecurIntervals, aveNormTimeSinceLast, currentTimeMillis, simDuration);
					}
				}
				else if(probTypeEnum == ProbabilityModelOptions.WG02_BPT) {
					sectionGainArray=null; // set this null so it gets updated
					for(int s=0;s<erf.getNumFaultSystemSources();s++) {
						int fltSysRupIndex = erf.getFltSysRupIndexForSource(s);
						probGainForFaultSystemSource[s] = getWG02_ProbGainForRup(fltSysRupIndex, false, currentTimeMillis, simDuration);
					}
				}		
				// now update totalRate and ruptureSampler (for all rups since start time changed)
				for(int n=0; n<erf.getTotNumRupsFromFaultSystem();n++) {
					double probGain = probGainForFaultSystemSource[erf.getSrcIndexForNthRup(n)];
					
					// test correction:
//					double gainCorr = gainCorrFunc.getClosestY(erf.getNthRupture(n).getMag());
//					double newRate = longTermRateOfNthRups[n] * probGain/gainCorr;
//					
					// no correction
					double newRate = longTermRateOfNthRups[n] * probGain;
					
					nthRupRandomSampler.set(n, newRate);
					aveRupProbGainArray[n] += probGain;
					if(minRupProbGainArray[n]>probGain)
						minRupProbGainArray[n] = probGain;
					if(maxRupProbGainArray[n]<probGain)
						maxRupProbGainArray[n] = probGain;
				}
				totalRate = nthRupRandomSampler.getSumOfY_vals();				
			}

//// check correlation between gain and mag on first iteration			
//if(firstEvent) {
//	// plot first rupture gains
//	double[] temppMagArray = new double[aveRupProbGainArray.length];
//	for(int i=0;i<aveRupProbGainArray.length;i++) {
//		temppMagArray[i]=magOfNthRups[i];	// the latter may include gridded seis rups
//	}
//	DefaultXY_DataSet firstRupProbGainVsMag = new DefaultXY_DataSet(temppMagArray,aveRupProbGainArray);
//	firstRupProbGainVsMag.setName("First Rup Prob Gain vs Mag");
//	double meanProbGain =0;
//	ArrayList<DefaultXY_DataSet> temppFuncs = new ArrayList<DefaultXY_DataSet>();
//	temppFuncs.add(firstRupProbGainVsMag);
//	ArrayList<PlotCurveCharacterstics> plotCharsAveGain = new ArrayList<PlotCurveCharacterstics>();
//	plotCharsAveGain.add(new PlotCurveCharacterstics(PlotSymbol.CROSS, 4f, Color.BLUE));
//	GraphWindow graphFirstRupProbGainVsMag = new GraphWindow(temppFuncs, "First Rup Prob Gain vs Mag; "+plotLabelString, plotCharsAveGain); 
//	graphFirstRupProbGainVsMag.setX_AxisLabel("Magnitude");
//	graphFirstRupProbGainVsMag.setY_AxisLabel("Rist Rup Prob Gain");
//	
//}
			
			
			yearsIntoSimulation.add(currentYear);
			totRateAtYearsIntoSimulation.add(totalRate);
			
			// sample time of next event
			double timeToNextInYrs = randomDataSampler.nextExponential(1.0/totalRate);
			long eventTimeMillis = currentTimeMillis + (long)(timeToNextInYrs*MILLISEC_PER_YEAR);
			// System.out.println("Event time: "+eventTimeMillis+" ("+(timeToNextInYrs+timeOfNextInYrs)+" yrs)");

			// sample an event
			nthRup = nthRupRandomSampler.getRandomInt();
			int srcIndex = erf.getSrcIndexForNthRup(nthRup);
			
//.			System.out.println(numRups+"\t"+currentYear+"\t"+totalRate+"\t"+timeToNextInYrs+"\t"+nthRup);

			
			obsRupRateArray[nthRup] += 1;

			// set that fault system event has occurred (and save normalized RI)
			if(srcIndex < erf.getNumFaultSystemSources()) {	// ignore other sources
				int fltSystRupIndex = erf.getFltSysRupIndexForSource(srcIndex);
				double rupMag = fltSysRupSet.getMagForRup(erf.getFltSysRupIndexForNthRup(nthRup));

				
				// compute and save the normalize recurrence interval if all sections had date of last
				if(aveNormTimeSinceLast) {	// average time since last
					double aveNormYearsSinceLast = getAveNormTimeSinceLastEventWhereKnown(fltSystRupIndex, eventTimeMillis);
					if(allSectionsHadDateOfLast) {
						normalizedRupRecurIntervals.add(aveNormYearsSinceLast);
						if(numAperValues>0)
							normalizedRupRecurIntervalsMagDepList.get(getAperIndexForRupMag(rupMag)).add(aveNormYearsSinceLast);
					}					
				}
				else {
					long aveDateOfLastMillis = getAveDateOfLastEventWhereKnown(fltSystRupIndex, eventTimeMillis);
					if(allSectionsHadDateOfLast) {
						double timeSinceLast = (eventTimeMillis-aveDateOfLastMillis)/MILLISEC_PER_YEAR;
						double normRI = timeSinceLast/aveCondRecurIntervalForFltSysRups[fltSystRupIndex];
						normalizedRupRecurIntervals.add(normRI);
						if(numAperValues>0)
							normalizedRupRecurIntervalsMagDepList.get(getAperIndexForRupMag(rupMag)).add(normRI);

					}					
				}
				
				
				// write event info out
				try {
					eventFileWriter.write(nthRup+"\t"+fltSystRupIndex+"\t"+(currentYear+timeToNextInYrs)+"\t"+eventTimeMillis+"\t"+normalizedRupRecurIntervals.get(normalizedRupRecurIntervals.size()-1)+"\n");
				} catch (IOException e1) {
					e1.printStackTrace();
				}

				
				// save normalized fault section recurrence intervals & RI along strike
				HistogramFunction sumRI_AlongHist = new HistogramFunction(normRI_AlongStrike.getMinX(), normRI_AlongStrike.getMaxX(), normRI_AlongStrike.getNumX());
				HistogramFunction numRI_AlongHist = new HistogramFunction(normRI_AlongStrike.getMinX(), normRI_AlongStrike.getMaxX(), normRI_AlongStrike.getNumX());
				int[] sectID_Array = sectIndexArrayForSrcList.get(erf.getSrcIndexForFltSysRup(fltSystRupIndex));
				int numSectInRup=sectID_Array.length;
				double slips[];
				if(fltSysRupSet instanceof InversionFaultSystemRupSet) {
					slips = ((InversionFaultSystemRupSet) fltSysRupSet).getSlipOnSectionsForRup(erf.getFltSysRupIndexForNthRup(nthRup));
				}
				else {	// apply ave to all sections
//					double mag = fltSysRupSet.getMagForRup(erf.getFltSysRupIndexForNthRup(nthRup));
					double area = fltSysRupSet.getAreaForRup(erf.getFltSysRupIndexForNthRup(nthRup));
					double aveSlip = FaultMomentCalc.getSlip(area, MagUtils.magToMoment(rupMag));
					slips = new double[numSectInRup];
					for(int i=0;i<slips.length;i++)
						slips[i]=aveSlip;
				}
				// obsSectSlipRateArray
				int ithSectInRup=0;
				for(int sect : sectID_Array) {
					obsSectSlipRateArray[sect] += slips[ithSectInRup];
					long timeOfLastMillis = dateOfLastForSect[sect];
					if(timeOfLastMillis != Long.MIN_VALUE) {
						double normYrsSinceLast = ((eventTimeMillis-timeOfLastMillis)/MILLISEC_PER_YEAR)*longTermPartRateForSectArray[sect];
						normalizedSectRecurIntervals.add(normYrsSinceLast);
						if(sect == testSectionIndex)
							normalizedSectRecurIntervalsForTestSect.add(normYrsSinceLast);;
						if(numAperValues>0)
							normalizedSectRecurIntervalsMagDepList.get(getAperIndexForRupMag(rupMag)).add(normYrsSinceLast);
						
						double normDistAlong = ((double)ithSectInRup+0.5)/(double)numSectInRup;
						sumRI_AlongHist.add(normDistAlong, normYrsSinceLast);
						numRI_AlongHist.add(normDistAlong, 1.0);
					}
					ithSectInRup += 1;
				}
				// now put above averages in normRI_AlongStrike
				if(numSectInRup>10) {
					for(int i =0;i<sumRI_AlongHist.size();i++) {
						double num = numRI_AlongHist.getY(i);
						if(num > 0) {
							normRI_AlongStrike.set(sumRI_AlongHist.getX(i), sumRI_AlongHist.getY(i)/num, 1.0);
						}
					}				
				}
				
				// make SAF event plotting funcs (ONLY A FIRST 10000 YEARS)
				double numYrs = (eventTimeMillis-origStartTimeMillis)/MILLISEC_PER_YEAR;
				if(numYrs < 11000 && numYrs > 1000) {
//					int[] sectID_Array = sectIndexArrayForSrcList.get(erf.getSrcIndexForFltSysRup(fltSystRupIndex));
					// make the function showing 10% RI at bottom of plot
					if(firstEvent) {
						for(int s=0;s<fltSysRupSet.getNumSections();s++) {
							double tenPercentRI = 0.1/longTermPartRateForSectArray[s];
							FaultSectionPrefData sectData= fltSysRupSet.getFaultSectionData(s);
							if(sectData.getParentSectionName().contains("San Andreas")) {
								ArbitrarilyDiscretizedFunc newFunc = new ArbitrarilyDiscretizedFunc();
								newFunc.set(sectData.getFaultTrace().first().getLatitude(),tenPercentRI);
								newFunc.set(sectData.getFaultTrace().last().getLatitude(),tenPercentRI);
								safEventFuncs.add(newFunc);
								safPlotChars4.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1, Color.BLACK));
							}
						}
					}
					// make list of SAF sections in event
					ArrayList<Integer> safSections = new ArrayList<Integer>();
					for(int id : sectID_Array) {
						if(fltSysRupSet.getFaultSectionData(id).getParentSectionName().contains("San Andreas"))
								safSections.add(id);
					}
					if(safSections.size()>0) {
						double[] lats = new double[2*safSections.size()];	// one for each end of the fault section
						ArrayList<Double> shortSectRI_Lats = new ArrayList<Double>();
						for(int i=0;i<safSections.size();i++) {
							lats[2*i] = fltSysRupSet.getFaultSectionData(safSections.get(i)).getFaultTrace().first().getLatitude();
							lats[2*i+1] = fltSysRupSet.getFaultSectionData(safSections.get(i)).getFaultTrace().last().getLatitude();
							
							// check for short interval
							long timeOfLastMillis = dateOfLastForSect[safSections.get(i)];
							if(timeOfLastMillis != Long.MIN_VALUE) {
								double normYrsSinceLast = ((eventTimeMillis-timeOfLastMillis)/MILLISEC_PER_YEAR)*longTermPartRateForSectArray[safSections.get(i)];
								if(normYrsSinceLast<0.1) {
									double lat1 = fltSysRupSet.getFaultSectionData(safSections.get(i)).getFaultTrace().first().getLatitude();
									double lat2 = fltSysRupSet.getFaultSectionData(safSections.get(i)).getFaultTrace().last().getLatitude();
									shortSectRI_Lats.add((lat1+lat2)/2);
								}
							}

						}
						double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
						for(double val: lats) {
							if(min>val) min = val;
							if(max<val) max = val;
						}
						ArbitrarilyDiscretizedFunc newFunc = new ArbitrarilyDiscretizedFunc();
						newFunc.set(min,eventTimeMillis/MILLISEC_PER_YEAR);
						newFunc.set(max,eventTimeMillis/MILLISEC_PER_YEAR);
						
						safEventFuncs.add(newFunc);
						
						safPlotChars4.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1, Color.GRAY));
						
//						double mag = magOfNthRups[nthRup];
//						if(mag<6.5)
//							safPlotChars4.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1, Color.BLUE));
//						else if(mag<7)
//							safPlotChars4.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1, Color.GREEN));
//						else if(mag<7.5)
//							safPlotChars4.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1, Color.ORANGE));
//						else if(mag<8)
//							safPlotChars4.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1, Color.RED));
//						else
//							safPlotChars4.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1, Color.MAGENTA));
						
						// plot circles where there are short section RIs
						if(shortSectRI_Lats.size()>0) {
							DefaultXY_DataSet shortRIsFunc = new DefaultXY_DataSet();
							for(double lat:shortSectRI_Lats) {
								shortRIsFunc.set(lat, eventTimeMillis/MILLISEC_PER_YEAR);
							}
							safEventFuncs.add(shortRIsFunc);
							safPlotChars4.add(new PlotCurveCharacterstics(PlotSymbol.CROSS, 2f, Color.BLACK));
						}
					}			
				}

				
				// reset last event time and increment simulated/obs rate on sections
				for(int sect:sectIndexArrayForSrcList.get(srcIndex)) {
					dateOfLastForSect[sect] = eventTimeMillis;
					obsSectRateArray[sect] += 1.0; // add the event
					
					double mag = magOfNthRups[nthRup];
					if(mag>6 && mag<6.7)
						obsSectRateArrayM6pt05to6pt65[sect] += 1;
					else if (mag>7.9 && mag<8.3)
						obsSectRateArrayM7pt95to8pt25[sect] += 1;
				}
			}

			numRups+=1;
			obsMFD.addResampledMagRate(magOfNthRups[nthRup], 1.0, true);
			obsMoRate += MagUtils.magToMoment(magOfNthRups[nthRup]);
			
			// increment time
			currentYear += timeToNextInYrs;
			currentTimeMillis = eventTimeMillis;
			
//			System.out.println("currentYear="+currentYear+"; currentTimeMillis="+currentTimeMillis+"; timeToNextInYrs="+timeToNextInYrs);
			
			
			firstEvent=false;
		}
		
		progressBar.showProgress(false);
		
		try {
			eventFileWriter.close();
		} catch (IOException e2) {
			// TODO Auto-generated catch block
			e2.printStackTrace();
		}
		
		// write section date of last file if not null
		if(outputDateOfLastFileName != null)
			writeSectTimeSinceLastEventToFile(outputDateOfLastFileName, currentTimeMillis);
		
		String infoString = dirNameForSavingFiles;
		infoString += "\ninputDateOfLastFileName: "+inputDateOfLastFileName;
		infoString += "\nnumRups="+numRups;
		
		
		// plot average rupture gains
		double[] tempMagArray = new double[aveRupProbGainArray.length];
		for(int i=0;i<aveRupProbGainArray.length;i++) {
			aveRupProbGainArray[i]/=numRups;
			tempMagArray[i]=magOfNthRups[i];	// the latter may include gridded seis rups
		}
		DefaultXY_DataSet aveRupProbGainVsMag = new DefaultXY_DataSet(tempMagArray,aveRupProbGainArray);
		DefaultXY_DataSet minRupProbGainVsMag = new DefaultXY_DataSet(tempMagArray,minRupProbGainArray);
		DefaultXY_DataSet maxRupProbGainVsMag = new DefaultXY_DataSet(tempMagArray,maxRupProbGainArray);
		aveRupProbGainVsMag.setName("Ave Rup Prob Gain vs Mag");
		double meanProbGain =0;
		for(double val:aveRupProbGainArray) 
			meanProbGain += val;
		meanProbGain /= aveRupProbGainArray.length;
		aveRupProbGainVsMag.setInfo("meanProbGain="+(float)meanProbGain);
		minRupProbGainVsMag.setName("Min Rup Prob Gain vs Mag");
		maxRupProbGainVsMag.setName("Max Rup Prob Gain vs Mag");
		ArrayList<DefaultXY_DataSet> aveRupProbGainVsMagFuncs = new ArrayList<DefaultXY_DataSet>();
		aveRupProbGainVsMagFuncs.add(aveRupProbGainVsMag);
		aveRupProbGainVsMagFuncs.add(minRupProbGainVsMag);
		aveRupProbGainVsMagFuncs.add(maxRupProbGainVsMag);
		ArrayList<PlotCurveCharacterstics> plotCharsAveGain = new ArrayList<PlotCurveCharacterstics>();
		plotCharsAveGain.add(new PlotCurveCharacterstics(PlotSymbol.CROSS, 4f, Color.BLUE));
		plotCharsAveGain.add(new PlotCurveCharacterstics(PlotSymbol.CROSS, 4f, Color.GREEN));
		plotCharsAveGain.add(new PlotCurveCharacterstics(PlotSymbol.CROSS, 4f, Color.RED));
		GraphWindow graphAveRupProbGainVsMag = new GraphWindow(aveRupProbGainVsMagFuncs, "Ave Rup Prob Gain vs Mag; "+plotLabelString, plotCharsAveGain); 
		graphAveRupProbGainVsMag.setX_AxisLabel("Magnitude");
		graphAveRupProbGainVsMag.setY_AxisLabel("Ave Rup Prob Gain");

		// write out these gains and other stuff
		FileWriter gain_fr;
		try {
			gain_fr = new FileWriter(dirNameForSavingFiles+"/aveRupGainData.txt");
			gain_fr.write("nthRupIndex\taveRupGain\tminRupGain\tmaxRupGain\trupMag\trupLongTermRate\trupCondRI\trupName\n");
			for(int i=0;i<aveRupProbGainArray.length;i++) {
				gain_fr.write(i+"\t"+aveRupProbGainArray[i]+"\t"+minRupProbGainArray[i]+"\t"+maxRupProbGainArray[i]+"\t"+magOfNthRups[i]
						+"\t"+longTermRateOfNthRups[i]+"\t"+aveCondRecurIntervalForFltSysRups[erf.getFltSysRupIndexForNthRup(i)]+"\t"+
						erf.getSource(erf.getSrcIndexForNthRup(i)).getName()+"\n");
			}
			gain_fr.close();
		} catch (IOException e1) {
			e1.printStackTrace();
		}

		// make aveGainVsMagFunction, weighted by rupture rate
		HistogramFunction aveGainVsMagHist = new HistogramFunction(5.05, 40, 0.1);
		HistogramFunction tempWtHist = new HistogramFunction(5.05, 40, 0.1);
		for(int i=0;i<aveRupProbGainArray.length;i++) {
			aveGainVsMagHist.add(magOfNthRups[i], aveRupProbGainArray[i]*longTermRateOfNthRups[i]);
			tempWtHist.add(magOfNthRups[i], longTermRateOfNthRups[i]);
		}
		for(int i=0;i<aveGainVsMagHist.size();i++) {
			double wt = tempWtHist.getY(i);
			if(wt>1e-15) // avoid division by zero
				aveGainVsMagHist.set(i,aveGainVsMagHist.getY(i)/wt);
		}
		aveGainVsMagHist.setName("aveGainVsMagHist");
		aveGainVsMagHist.setInfo("weighted by rupture long-term rates");
		GraphWindow graphAveGainVsMagHist = new GraphWindow(aveGainVsMagHist, "Ave Rup Gain vs Mag; "+plotLabelString); 
		graphAveGainVsMagHist.setX_AxisLabel("Mag");
		graphAveGainVsMagHist.setY_AxisLabel("Ave Gain");
		graphAveGainVsMagHist.setAxisLabelFontSize(22);
		graphAveGainVsMagHist.setTickLabelFontSize(20);
		graphAveGainVsMagHist.setPlotLabelFontSize(22);


		
		
		// make normalized rup recurrence interval plots
		double aper=Double.NaN;
		if(numAperValues==1)
			aper=aperValues[0];	// only one value, so include in plot for al ruptures
		ArrayList<EvenlyDiscretizedFunc> funcList = ProbModelsPlottingUtils.getNormRI_DistributionWithFits(normalizedRupRecurIntervals, aper);
		GraphWindow grapha_a = ProbModelsPlottingUtils.plotNormRI_DistributionWithFits(funcList, "Normalized Rupture RIs; "+plotLabelString);
//		GraphWindow grapha_a = General_EQSIM_Tools.plotNormRI_Distribution(normalizedRupRecurIntervals, "Normalized Rupture RIs; "+plotLabelString, aperiodicity);
		infoString += "\n\nRup "+funcList.get(0).getName()+":";
		infoString += "\n"+funcList.get(0).getInfo();
		infoString += "\n\n"+funcList.get(1).getName();
		infoString += "\n"+funcList.get(1).getInfo();
		
		// now mag-dep:
		if(numAperValues >1) {
			for(int i=0;i<numAperValues;i++) {
				ArrayList<EvenlyDiscretizedFunc> funcListMagDep = ProbModelsPlottingUtils.getNormRI_DistributionWithFits(normalizedRupRecurIntervalsMagDepList.get(i), aperValues[i]);
				String label = getMagDepAperInfoString(i);
				GraphWindow graphaMagDep = ProbModelsPlottingUtils.plotNormRI_DistributionWithFits(funcListMagDep, "Norm Rup RIs; "+label+"; "+plotLabelString);
				infoString += "\n\nRup "+funcListMagDep.get(0).getName()+" for "+label+":";
				infoString += "\n"+funcListMagDep.get(0).getInfo();
				infoString += "\n\n"+funcListMagDep.get(1).getName();
				infoString += "\n"+funcListMagDep.get(1).getInfo();	
				// save plot now
				try {
					graphaMagDep.saveAsPDF(dirNameForSavingFiles+"/normRupRecurIntsForMagRange"+i+".pdf");
					graphaMagDep.saveAsTXT(dirNameForSavingFiles+"/normRupRecurIntsForMagRange"+i+".txt");
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		// make normalized sect recurrence interval plots
		ArrayList<EvenlyDiscretizedFunc> funcList2 = ProbModelsPlottingUtils.getNormRI_DistributionWithFits(normalizedSectRecurIntervals, aper);
		GraphWindow graph2_b = ProbModelsPlottingUtils.plotNormRI_DistributionWithFits(funcList2, "Normalized Section RIs; "+plotLabelString);
		infoString += "\n\nSect "+funcList2.get(0).getName()+":";
		infoString += "\n"+funcList2.get(0).getInfo();
		
		
		
		// make normalized sect recurrence interval plot for test section
		String sectName = erf.getSolution().getRupSet().getFaultSectionData(testSectionIndex).getName();
		ArrayList<EvenlyDiscretizedFunc> funcListTestSect = ProbModelsPlottingUtils.getNormRI_DistributionWithFits(normalizedSectRecurIntervalsForTestSect, aper);
		GraphWindow graphTestSect = ProbModelsPlottingUtils.plotNormRI_DistributionWithFits(funcListTestSect, "Normalized Section RIs for "+sectName);
		infoString += "\n\nTestSect "+funcListTestSect.get(0).getName()+":";
		infoString += "\n"+funcListTestSect.get(0).getInfo();

		
		
		// now mag-dep:
		if(numAperValues >1) {
			for(int i=0;i<numAperValues;i++) {
				ArrayList<EvenlyDiscretizedFunc> funcListMagDep = ProbModelsPlottingUtils.getNormRI_DistributionWithFits(normalizedSectRecurIntervalsMagDepList.get(i), aperValues[i]);
				String label = getMagDepAperInfoString(i);
				GraphWindow graphaMagDep = ProbModelsPlottingUtils.plotNormRI_DistributionWithFits(funcListMagDep, "Norm Sect RIs; "+label+"; "+plotLabelString);
				infoString += "\n\nSect "+funcListMagDep.get(0).getName()+" for "+label+":";
				infoString += "\n"+funcListMagDep.get(0).getInfo();
				infoString += "\n\n"+funcListMagDep.get(1).getName();
				infoString += "\n"+funcListMagDep.get(1).getInfo();	
				// save plot now
				try {
					graphaMagDep.saveAsPDF(dirNameForSavingFiles+"/normSectRecurIntsForMagRange"+i+".pdf");
					graphaMagDep.saveAsTXT(dirNameForSavingFiles+"/normSectRecurIntsForMagRange"+i+".txt");
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		boolean doThis=true;
		

		// 	plot simNormTimeSinceLastHist & simProbGainHist - these are weighted by long-term rates
		if(probTypeEnum == ProbabilityModelOptions.U3_BPT && doThis) {
//			int numObs = (int)Math.round(simNormTimeSinceLastHist.calcSumOfY_Vals());
			simNormTimeSinceLastHist.scale(1.0/(simNormTimeSinceLastHist.calcSumOfY_Vals()*simNormTimeSinceLastHist.getDelta())); // makes it a density function
			ArrayList<EvenlyDiscretizedFunc> funcListForSimNormTimeSinceLastHist = ProbModelsPlottingUtils.addBPT_Fit(simNormTimeSinceLastHist);
			simNormTimeSinceLastHist.setName("simNormTimeSinceLastHist");
//			simNormTimeSinceLastHist.setInfo("Dist of normalized time since last for each rupture at all event times\nMean = "+simNormTimeSinceLastHist.computeMean()+"\nnumObs="+numObs);
			simNormTimeSinceLastHist.setInfo("Dist of normalized time since last for each rupture at all event times\nMean = "+simNormTimeSinceLastHist.computeMean());
			GraphWindow graphSimNormTimeSinceLastHist = ProbModelsPlottingUtils.plotNormRI_DistributionWithFits(funcListForSimNormTimeSinceLastHist, "simNormTimeSinceLastHist; "+plotLabelString);
			
//			simNormTimeSinceLastForMagBelow7_Hist
//			numObs = (int)Math.round(simNormTimeSinceLastForMagBelow7_Hist.calcSumOfY_Vals());
			simNormTimeSinceLastForMagBelow7_Hist.scale(1.0/(simNormTimeSinceLastForMagBelow7_Hist.calcSumOfY_Vals()*simNormTimeSinceLastForMagBelow7_Hist.getDelta())); // makes it a density function
			ArrayList<EvenlyDiscretizedFunc> funcListForSimNormTimeSinceLastHistForSmall = ProbModelsPlottingUtils.addBPT_Fit(simNormTimeSinceLastForMagBelow7_Hist);
			simNormTimeSinceLastForMagBelow7_Hist.setName("simNormTimeSinceLastForMagBelow7_Hist");
//			simNormTimeSinceLastForMagBelow7_Hist.setInfo("Dist of normalized time since last for each rupture at all event times from M<=7\nMean = "+simNormTimeSinceLastForMagBelow7_Hist.computeMean()+"\nnumObs="+numObs);
			simNormTimeSinceLastForMagBelow7_Hist.setInfo("Dist of normalized time since last for each rupture at all event times from M<=7\nMean = "+simNormTimeSinceLastForMagBelow7_Hist.computeMean());
			GraphWindow graphSimNormTimeSinceLastForMagBelow7_Hist = ProbModelsPlottingUtils.plotNormRI_DistributionWithFits(funcListForSimNormTimeSinceLastHistForSmall, "simNormTimeSinceLastForMagBelow7_Hist; "+plotLabelString);

			simNormTimeSinceLastForMagAbove7_Hist.scale(1.0/(simNormTimeSinceLastForMagAbove7_Hist.calcSumOfY_Vals()*simNormTimeSinceLastForMagAbove7_Hist.getDelta())); // makes it a density function
			ArrayList<EvenlyDiscretizedFunc> funcListForSimNormTimeSinceLastHistForLarge = ProbModelsPlottingUtils.addBPT_Fit(simNormTimeSinceLastForMagAbove7_Hist);
			simNormTimeSinceLastForMagAbove7_Hist.setName("simNormTimeSinceLastForMagAbove7_Hist");
			simNormTimeSinceLastForMagAbove7_Hist.setInfo("Dist of normalized time since last for each rupture at all event times from M>7\nMean = "+simNormTimeSinceLastForMagAbove7_Hist.computeMean());
			GraphWindow graphSimNormTimeSinceLastForMagAbove7_Hist = ProbModelsPlottingUtils.plotNormRI_DistributionWithFits(funcListForSimNormTimeSinceLastHistForLarge, "simNormTimeSinceLastForMagAbove7_Hist; "+plotLabelString);

			
//			numObs = (int)Math.round(simProbGainHist.calcSumOfY_Vals());
			simProbGainHist.scale(1.0/(simProbGainHist.calcSumOfY_Vals()*simProbGainHist.getDelta())); // makes it a density function
			simProbGainHist.setName("simProbGainHist");
//			simProbGainHist.setInfo("Dist of gains for each rupture at all event times (simProbGainHist)\nMean = "+simProbGainHist.computeMean()+"\nnumObs="+numObs);
			simProbGainHist.setInfo("Dist of gains for each rupture at all event times (simProbGainHist)\nMean = "+simProbGainHist.computeMean());
			ArrayList<EvenlyDiscretizedFunc> funcListForSimProbGainHist = new ArrayList<EvenlyDiscretizedFunc>();
			funcListForSimProbGainHist.add(simProbGainHist);
			simProbGainForMagBelow7_Hist.scale(1.0/(simProbGainForMagBelow7_Hist.calcSumOfY_Vals()*simProbGainForMagBelow7_Hist.getDelta())); // makes it a density function
			simProbGainForMagAbove7_Hist.scale(1.0/(simProbGainForMagAbove7_Hist.calcSumOfY_Vals()*simProbGainForMagAbove7_Hist.getDelta())); // makes it a density function
			simProbGainForMagBelow7_Hist.setName("simProbGainForMagBelow7_Hist");
			simProbGainForMagAbove7_Hist.setName("simProbGainForMagAbove7_Hist");
			simProbGainForMagBelow7_Hist.setInfo("Mean = "+simProbGainForMagBelow7_Hist.computeMean());
			simProbGainForMagAbove7_Hist.setInfo("Mean = "+simProbGainForMagAbove7_Hist.computeMean());
			funcListForSimProbGainHist.add(simProbGainForMagBelow7_Hist);
			funcListForSimProbGainHist.add(simProbGainForMagAbove7_Hist);
			ArrayList<PlotCurveCharacterstics> plotCharsForSimProbGainHist = new ArrayList<PlotCurveCharacterstics>();
			plotCharsForSimProbGainHist.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 2f, Color.RED));
			plotCharsForSimProbGainHist.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLUE));
			plotCharsForSimProbGainHist.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLACK));

			GraphWindow graphSimProbGainHistWindow = new GraphWindow(funcListForSimProbGainHist, "simProbGainHist", plotCharsForSimProbGainHist); 
			graphSimProbGainHistWindow.setX_AxisLabel("Gain");
			graphSimProbGainHistWindow.setY_AxisLabel("Density");
			graphSimProbGainHistWindow.setAxisLabelFontSize(22);
			graphSimProbGainHistWindow.setTickLabelFontSize(20);
			graphSimProbGainHistWindow.setPlotLabelFontSize(22);

			try {
				graphSimNormTimeSinceLastHist.saveAsPDF(dirNameForSavingFiles+"/simNormTimeSinceLastHist.pdf");
				graphSimProbGainHistWindow.saveAsPDF(dirNameForSavingFiles+"/simProbGainHist.pdf");
				graphSimNormTimeSinceLastForMagBelow7_Hist.saveAsPDF(dirNameForSavingFiles+"/simNormTimeSinceLastForMagBelow7_Hist.pdf");
				graphSimNormTimeSinceLastForMagAbove7_Hist.saveAsPDF(dirNameForSavingFiles+"/simNormTimeSinceLastForMagAbove7_Hist.pdf");
				graphSimNormTimeSinceLastHist.saveAsTXT(dirNameForSavingFiles+"/simNormTimeSinceLastHist.txt");
				graphSimProbGainHistWindow.saveAsTXT(dirNameForSavingFiles+"/simProbGainHist.txt");
				graphSimNormTimeSinceLastForMagBelow7_Hist.saveAsTXT(dirNameForSavingFiles+"/simNormTimeSinceLastForMagBelow7_Hist.txt");
				graphSimNormTimeSinceLastForMagAbove7_Hist.saveAsTXT(dirNameForSavingFiles+"/simNormTimeSinceLastForMagAbove7_Hist.txt");
			} catch (IOException e) {
				e.printStackTrace();
			}

		}

		
		// plot long-term rate versus time
		DefaultXY_DataSet totRateVersusTime = new DefaultXY_DataSet(yearsIntoSimulation,totRateAtYearsIntoSimulation);
		double meanTotRate=0;
		double totWt=0;
		for(int i=0;i<totRateAtYearsIntoSimulation.size()-1;i++) {
			double wt = yearsIntoSimulation.get(i+1)-yearsIntoSimulation.get(i); // apply rate until time of next event
			meanTotRate+=totRateAtYearsIntoSimulation.get(i)*wt;
			totWt+=wt;
		}
		meanTotRate /= totWt;
		totRateVersusTime.setName("Total Rate vs Time");
		totRateVersusTime.setInfo("Mean Total Rate = "+meanTotRate+"\nLong Term Rate = "+totalLongTermRate);
		DefaultXY_DataSet longTermRateFunc = new DefaultXY_DataSet();
		longTermRateFunc.set(totRateVersusTime.getMinX(),totalLongTermRate);
		longTermRateFunc.set(totRateVersusTime.getMaxX(),totalLongTermRate);
		longTermRateFunc.setName("Long term rate");
		ArrayList<DefaultXY_DataSet> funcsTotRate = new ArrayList<DefaultXY_DataSet>();
		funcsTotRate.add(totRateVersusTime);
		funcsTotRate.add(longTermRateFunc);
		ArrayList<PlotCurveCharacterstics> plotCharsTotRate = new ArrayList<PlotCurveCharacterstics>();
		plotCharsTotRate.add(new PlotCurveCharacterstics(PlotSymbol.CROSS, 4f, Color.BLUE));
		plotCharsTotRate.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.RED));
		GraphWindow graphTotalRateVsTime = new GraphWindow(funcsTotRate, "Total Rate vs Time; "+plotLabelString, plotCharsTotRate); 
		graphTotalRateVsTime.setX_AxisLabel("Times (years)");
		graphTotalRateVsTime.setY_AxisLabel("Total Rate (per year)");



		// plot MFDs
		obsMFD.scale(1.0/numYears);
		obsMFD.setName("Simulated MFD");
		obsMoRate /= numYears;
		double obsTotRate = obsMFD.getTotalIncrRate();
		double rateRatio = obsTotRate/targetMFD.getTotalIncrRate();
		String infoString2 = "total rate = "+(float)obsTotRate+" (ratio="+(float)rateRatio+")";
		double obsTotRateAbove6pt7 = obsMFD.getCumRate(6.75);
		double rateAbove6pt7_Ratio = obsTotRateAbove6pt7/targetMFD.getCumRate(6.75);
		infoString2 += "\ntotal rate >= 6.7 = "+(float)obsTotRateAbove6pt7+" (ratio="+(float)rateAbove6pt7_Ratio+")";
		double moRateRatio = obsMoRate/origTotMoRate;
		infoString2 += "\ntotal MoRate = "+(float)obsMoRate+" (ratio="+(float)moRateRatio+")";
		obsMFD.setInfo(infoString2);
		
		infoString += "\n\nSimulationStats:\n";
		infoString += "totRate\tratio\ttotRateM>=6.7\tratio\ttotMoRate\tratio\n";
		infoString += (float)obsTotRate+"\t"+(float)rateRatio+"\t"+(float)obsTotRateAbove6pt7+"\t"+(float)rateAbove6pt7_Ratio+"\t"+(float)obsMoRate+"\t"+(float)moRateRatio;
		
		// write this now in case of crash
		FileWriter info_fr;
		try {
			info_fr = new FileWriter(dirNameForSavingFiles+"/infoString.txt");
			info_fr.write(infoString);
			info_fr.close();
		} catch (IOException e1) {
			e1.printStackTrace();
		}

		
		System.out.println("INFO STRING:\n\n"+infoString);

		ArrayList<EvenlyDiscretizedFunc> funcs = new ArrayList<EvenlyDiscretizedFunc>();
		funcs.add(targetMFD);
		funcs.add(obsMFD);
		funcs.add(targetMFD.getCumRateDistWithOffset());
		funcs.add(obsMFD.getCumRateDistWithOffset());
		GraphWindow graph = new GraphWindow(funcs, "Incremental Mag-Freq Dists; "+plotLabelString); 
		graph.setX_AxisLabel("Mag");
		graph.setY_AxisLabel("Rate");
		graph.setYLog(true);	// this causes problems
		graph.setY_AxisRange(1e-4, 1.0);
		graph.setX_AxisRange(5.5, 8.5);
		
		// plot observed versus imposed rup rates - Is this really meaningful?
		for(int i=0;i<obsRupRateArray.length;i++) {
			obsRupRateArray[i] = obsRupRateArray[i]/numYears;
		}
		DefaultXY_DataSet obsVsImposedRupRates = new DefaultXY_DataSet(longTermRateOfNthRups,obsRupRateArray);
		obsVsImposedRupRates.setName("Simulated vs Imposed Rup Rates");
		DefaultXY_DataSet perfectAgreementFunc4 = new DefaultXY_DataSet();
		perfectAgreementFunc4.set(1e-5,1e-5);
		perfectAgreementFunc4.set(0.05,0.05);
		perfectAgreementFunc4.setName("Perfect agreement line");
		ArrayList<DefaultXY_DataSet> funcs4 = new ArrayList<DefaultXY_DataSet>();
		funcs4.add(obsVsImposedRupRates);
		funcs4.add(perfectAgreementFunc4);
		ArrayList<PlotCurveCharacterstics> plotChars4 = new ArrayList<PlotCurveCharacterstics>();
		plotChars4.add(new PlotCurveCharacterstics(PlotSymbol.CROSS, 4f, Color.BLUE));
		plotChars4.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.RED));
		GraphWindow graph4 = new GraphWindow(funcs4, "Obs vs Imposed Rup Rates; "+plotLabelString, plotChars4); 
		graph4.setX_AxisRange(5d/numYears, 0.01);
		graph4.setY_AxisRange(5d/numYears, 0.01);
		graph4.setYLog(true);
		graph4.setXLog(true);
		graph4.setX_AxisLabel("Imposed Rup Rate (per yr)");
		graph4.setY_AxisLabel("Simulated Rup Rate (per yr)");

		
		
		// plot SAF events
		GraphWindow graph9 = new GraphWindow(safEventFuncs, "SAF events; "+plotLabelString, safPlotChars4); 
		graph9.setX_AxisRange(36.8, 40.2);
		graph9.setY_AxisRange(1000, 11000);
		graph9.setX_AxisLabel("Latitute");
		graph9.setY_AxisLabel("Year");
		graph9.setSize(240, 800);

		
		// plot observed versus imposed section slip rates
		for(int i=0;i<obsSectSlipRateArray.length;i++) {
			obsSectSlipRateArray[i] = obsSectSlipRateArray[i]/numYears;
		}
		DefaultXY_DataSet obsVsImposedSectSlipRates = new DefaultXY_DataSet(longTermSlipRateForSectArray,obsSectSlipRateArray);
		obsVsImposedSectSlipRates.setName("Simulated vs Imposed Section Slip Rates");
		DefaultXY_DataSet perfectAgreementSlipRateFunc = new DefaultXY_DataSet();
		perfectAgreementSlipRateFunc.set(1e-5,1e-5);
		perfectAgreementSlipRateFunc.set(0.05,0.05);
		perfectAgreementSlipRateFunc.setName("Perfect agreement line");
		ArrayList<DefaultXY_DataSet> funcsSR = new ArrayList<DefaultXY_DataSet>();
		funcsSR.add(obsVsImposedSectSlipRates);
		funcsSR.add(perfectAgreementSlipRateFunc);
		ArrayList<PlotCurveCharacterstics> plotCharsSR = new ArrayList<PlotCurveCharacterstics>();
		plotCharsSR.add(new PlotCurveCharacterstics(PlotSymbol.CROSS, 4f, Color.BLUE));
		plotCharsSR.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.RED));
		GraphWindow graphSR = new GraphWindow(funcsSR, "Obs vs Imposed Section Slip Rates; "+plotLabelString, plotCharsSR); 
		graphSR.setX_AxisRange(1e-5, 0.05);
		graphSR.setY_AxisRange(1e-5, 0.05);
		graphSR.setYLog(true);
		graphSR.setXLog(true);
		graphSR.setX_AxisLabel("Imposed Section Slip Rate (mm/yr)");
		graphSR.setY_AxisLabel("Simulated Section Slip Rate (mm/yr)");
		
		
		// plot observed versus imposed section rates
		for(int i=0;i<obsSectRateArray.length;i++) {
			obsSectRateArray[i] = obsSectRateArray[i]/numYears;
			obsSectRateArrayM6pt05to6pt65[i] = obsSectRateArrayM6pt05to6pt65[i]/numYears;
			obsSectRateArrayM7pt95to8pt25[i] = obsSectRateArrayM7pt95to8pt25[i]/numYears;
		}
		DefaultXY_DataSet obsVsImposedSectRates = new DefaultXY_DataSet(longTermPartRateForSectArray,obsSectRateArray);
		obsVsImposedSectRates.setName("Simulated vs Imposed Section Event Rates");
		DefaultXY_DataSet perfectAgreementFunc = new DefaultXY_DataSet();
		perfectAgreementFunc.set(1e-5,1e-5);
		perfectAgreementFunc.set(0.05,0.05);
		perfectAgreementFunc.setName("Perfect agreement line");
		ArrayList<DefaultXY_DataSet> funcs2 = new ArrayList<DefaultXY_DataSet>();
		funcs2.add(obsVsImposedSectRates);
		funcs2.add(perfectAgreementFunc);
		ArrayList<PlotCurveCharacterstics> plotChars = new ArrayList<PlotCurveCharacterstics>();
		plotChars.add(new PlotCurveCharacterstics(PlotSymbol.CROSS, 4f, Color.BLUE));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.RED));
		GraphWindow graph2 = new GraphWindow(funcs2, "Obs vs Imposed Section Rates; "+probTypeString, plotChars); 
		graph2.setX_AxisRange(5d/numYears, 0.05);
		graph2.setY_AxisRange(5d/numYears, 0.05);
		graph2.setYLog(true);
		graph2.setXLog(true);
		graph2.setX_AxisLabel("Imposed Section Participation Rate (per yr)");
		graph2.setY_AxisLabel("Simulated Section Participation Rate (per yr)");
		
		// write section rates with names
		FileWriter eventRates_fr;
		try {
			eventRates_fr = new FileWriter(dirNameForSavingFiles+"/obsVsImposedSectionPartRates.txt");
			eventRates_fr.write("sectID\timposedRate\tsimulatedRate\tsimOverImpRateRatio\thasDateOfLast\tsectName\n");
			for(int i=0;i<fltSysRupSet.getNumSections();i++) {
				FaultSectionPrefData fltData = fltSysRupSet.getFaultSectionData(i);
				double ratio = obsSectRateArray[i]/longTermPartRateForSectArray[i];
				boolean hasDateOfLast=false;
				if(fltData.getDateOfLastEvent() != Long.MIN_VALUE)
					hasDateOfLast=true;
				eventRates_fr.write(fltData.getSectionId()+"\t"+longTermPartRateForSectArray[i]+"\t"+obsSectRateArray[i]+"\t"+ratio+"\t"+hasDateOfLast+"\t"+fltData.getName()+"\n");
			}
			eventRates_fr.close();
		} catch (IOException e1) {
			e1.printStackTrace();
		}


		

		
		
		// plot ave norm RI along strike
		ArrayList<EvenlyDiscretizedFunc> funcs8 = new ArrayList<EvenlyDiscretizedFunc>();
		EvenlyDiscretizedFunc meanAlongFunc = normRI_AlongStrike.getMeanCurve();
		meanAlongFunc.setName("mean");
		funcs8.add(normRI_AlongStrike.getMeanCurve());
		EvenlyDiscretizedFunc alongFunc2pt5 = normRI_AlongStrike.getInterpolatedFractileCurve(0.025);
		EvenlyDiscretizedFunc alongFunc97pt5 = normRI_AlongStrike.getInterpolatedFractileCurve(0.975);
		alongFunc2pt5.setInfo("2.5 percentile");
		alongFunc97pt5.setInfo("97.5 percentile");
		funcs8.add(alongFunc2pt5);
		funcs8.add(alongFunc97pt5);
		ArrayList<PlotCurveCharacterstics> plotChars8 = new ArrayList<PlotCurveCharacterstics>();
		plotChars8.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.RED));
		plotChars8.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLACK));
		plotChars8.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLACK));
		GraphWindow graph8 = new GraphWindow(funcs8, "Normalized RI vs Normalized Dist Along Strike; "+probTypeString, plotChars8); 
		graph8.setX_AxisLabel("Norm Dist Along Strike");
		graph8.setY_AxisLabel("Normalized RI");
		
		
		
//		System.out.println(testSectName+"\tobsSectRateArray="+obsSectRateArray[testSectIndex]+
//				"\tlongTermPartRateForSectArray="+longTermPartRateForSectArray[testSectIndex]+"\tratio="+
//				(obsSectRateArray[testSectIndex]/longTermPartRateForSectArray[testSectIndex]));
		
		// write out test section rates
		ArrayList<String> outStringList = new ArrayList<String>();
		int numSect=fltSysRupSet.getNumSections();
		double[] predSectRateArrayM6pt05to6pt65 = new double[numSect];
		double[] predSectRateArrayM7pt95to8pt25 = new double[numSect];
		for(int s=0;s<numSect;s++) {
			double partRateMlow=0;
			double partRateMhigh=0;
			for (int r : fltSysRupSet.getRupturesForSection(s)) {
				double mag = fltSysRupSet.getMagForRup(r);
				if(mag>6 && mag<6.7)
					partRateMlow += fltSysSolution.getRateForRup(r);
				else if (mag>7.9 && mag<8.3)
					partRateMhigh = fltSysSolution.getRateForRup(r);
			}
			predSectRateArrayM6pt05to6pt65[s]=partRateMlow;
			predSectRateArrayM7pt95to8pt25[s]=partRateMhigh;
			outStringList.add(s+"\t"+obsSectRateArray[s]+"\t"+longTermPartRateForSectArray[s]+"\t"+
					(obsSectRateArray[s]/longTermPartRateForSectArray[s])+"\t"+
					predSectRateArrayM6pt05to6pt65[s]+"\t"+
					obsSectRateArrayM6pt05to6pt65[s]+"\t"+
					predSectRateArrayM7pt95to8pt25[s]+"\t"+
					obsSectRateArrayM7pt95to8pt25[s]+"\t"+
					fltSysRupSet.getFaultSectionData(s).getName()+"\n");
		}
		File dataFile = new File(resultsDir,File.separator+"testSectRates");
		try {
			FileWriter fileWriter = new FileWriter(dataFile);
			for(String line:outStringList) {
				fileWriter.write(line);
			}
			fileWriter.close();
		}catch(Exception e) {
			e.printStackTrace();
		}
		
		DefaultXY_DataSet obs_pred_ratioForSections = new DefaultXY_DataSet();
		for(int s=0;s<numSect;s++) {
			if(predSectRateArrayM6pt05to6pt65[s] >= 10.0/numYears) {	// only keep where 10 should have occurred
				obs_pred_ratioForSections.set(predSectRateArrayM6pt05to6pt65[s], obsSectRateArrayM6pt05to6pt65[s]/predSectRateArrayM6pt05to6pt65[s]);
			}
		}
		DefaultXY_DataSet perfectAgreementFunc2 = new DefaultXY_DataSet();
		perfectAgreementFunc2.set(10.0/numYears,1d);
		perfectAgreementFunc2.set(0.1,1d);
		perfectAgreementFunc2.setName("Perfect agreement line");
		ArrayList<DefaultXY_DataSet> funcs3 = new ArrayList<DefaultXY_DataSet>();
		funcs3.add(obs_pred_ratioForSections);
		funcs3.add(perfectAgreementFunc2);
		ArrayList<PlotCurveCharacterstics> plotChars2 = new ArrayList<PlotCurveCharacterstics>();
		plotChars2.add(new PlotCurveCharacterstics(PlotSymbol.CROSS, 4f, Color.BLUE));
		plotChars2.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.RED));
		GraphWindow graph3 = new GraphWindow(funcs3, "Obs/imposed vs Imposed Section Rates for M 6.0 to 6.7; "+plotLabelString, plotChars2); 
		graph3.setX_AxisLabel("Imposed Section Participation Rate (per yr)");
		graph3.setY_AxisLabel("Ratio of Observed to Imposed");
		graph3.setXLog(true);
		
		try {
			// plots
			graphAveRupProbGainVsMag.saveAsPDF(dirNameForSavingFiles+"/aveRupProbGainVsMag.pdf");
			graphAveRupProbGainVsMag.saveAsTXT(dirNameForSavingFiles+"/aveRupProbGainVsMag.txt");
			grapha_a.saveAsPDF(dirNameForSavingFiles+"/normalizedRupRecurIntervals.pdf");
			grapha_a.saveAsTXT(dirNameForSavingFiles+"/normalizedRupRecurIntervalsPlot.txt");
			graph2_b.saveAsPDF(dirNameForSavingFiles+"/normalizedSectRecurIntervals.pdf");
			graph2_b.saveAsTXT(dirNameForSavingFiles+"/normalizedSectRecurIntervalsPlot.txt");
			graphTestSect.saveAsPDF(dirNameForSavingFiles+"/normalizedRecurIntervalsForTestSect.pdf");
			graphTestSect.saveAsTXT(dirNameForSavingFiles+"/normalizedRecurIntervalsForTestSect.txt");
			graphTotalRateVsTime.saveAsPDF(dirNameForSavingFiles+"/totalRateVsTime.pdf");
			graph.saveAsPDF(dirNameForSavingFiles+"/magFreqDists.pdf");
			graph2.saveAsPDF(dirNameForSavingFiles+"/obsVsImposedSectionPartRates.pdf");
//			graph2.saveAsTXT(dirNameForSavingFiles+"/obsVsImposedSectionPartRates.txt"); // replaced above
			graph4.saveAsPDF(dirNameForSavingFiles+"/obsVsImposedRupRates.pdf");
			graph4.saveAsTXT(dirNameForSavingFiles+"/obsVsImposedRupRates.txt");
			graph3.saveAsPDF(dirNameForSavingFiles+"/obsOverImposedVsImposedSectionPartRatesM6to6pt7.pdf");
			graph8.saveAsPDF(dirNameForSavingFiles+"/normRI_AlongRupTrace.pdf");
//			graph9.saveAsPDF(dirNameForSavingFiles+"/safEventsVsTime.pdf");
			graphSR.saveAsPDF(dirNameForSavingFiles+"/obsVsImposedSectionSlipRates.pdf");
			graphSR.saveAsTXT(dirNameForSavingFiles+"/obsVsImposedSectionSlipRates.txt");
			graphAveGainVsMagHist.saveAsPDF(dirNameForSavingFiles+"/aveRupGainVsMagHist.pdf");
			graphAveGainVsMagHist.saveAsTXT(dirNameForSavingFiles+"/aveRupGainVsMagHist.txt");
			// data:
			FileWriter fr = new FileWriter(dirNameForSavingFiles+"/normalizedRupRecurIntervals.txt");
			for (double val : normalizedRupRecurIntervals)
				fr.write(val + "\n");
			fr.close();

			fr = new FileWriter(dirNameForSavingFiles+"/normalizedSectRecurIntervals.txt");
			for (double val : normalizedSectRecurIntervals)
				fr.write(val + "\n");
			fr.close();

			AbstractDiscretizedFunc.writeSimpleFuncFile(targetMFD, dirNameForSavingFiles+"/targetMFD.txt");
			AbstractDiscretizedFunc.writeSimpleFuncFile(obsMFD, dirNameForSavingFiles+"/simulatedMFD.txt");
			AbstractDiscretizedFunc.writeSimpleFuncFile(targetMFD.getCumRateDistWithOffset(), dirNameForSavingFiles+"/targetCumMFD.txt");
			AbstractDiscretizedFunc.writeSimpleFuncFile(obsMFD.getCumRateDistWithOffset(), dirNameForSavingFiles+"/simulatedCumMFD.txt");


		} catch (IOException e) {
			e.printStackTrace();
		}

	}
	

	
	
	/**
	 * 
	 * TODO Shouldn't pass in erf (use the local one)
	 * 
	 * MFD and moment rate comparisons are not yet implemented correctly
	 */
	public void testER_SimulationOnParentSection(String inputDateOfLastFileName, String outputDateOfLastFileName, FaultSystemSolutionERF erf, double numYears, 
			String dirNameSuffix, int parentSectIndex) {
		
		boolean aveRecurIntervals = erf.aveRecurIntervalsInU3_BPTcalc;
		boolean aveNormTimeSinceLast = erf.aveNormTimeSinceLastInU3_BPTcalc;
		
		this.simulationMode=true;
		simNormTimeSinceLastHist = new HistogramFunction(0.05, 100, 0.1);	// up to 10
		simNormTimeSinceLastForMagBelow7_Hist = new HistogramFunction(0.05, 100, 0.1);	// up to 10
		simNormTimeSinceLastForMagAbove7_Hist = new HistogramFunction(0.05, 100, 0.1);
		simProbGainHist = new HistogramFunction(0.05, 100, 0.1);	// up to 10
		simProbGainForMagAbove7_Hist = new HistogramFunction(0.05, 100, 0.1);	// up to 10
		simProbGainForMagBelow7_Hist = new HistogramFunction(0.05, 100, 0.1);	// up to 10

		
		// LABELING AND FILENAME STUFF
		String typeCalcForU3_Probs;
		if(aveRecurIntervals)
			typeCalcForU3_Probs = "aveRI";
		else
			typeCalcForU3_Probs = "aveRate";
		if(aveNormTimeSinceLast)
			typeCalcForU3_Probs += "_aveNormTimeSince";
		else
			typeCalcForU3_Probs += "_aveTimeSince";

		String probTypeString;
		ProbabilityModelOptions probTypeEnum = (ProbabilityModelOptions)erf.getParameter(ProbabilityModelParam.NAME).getValue();
		
		String aperString = "aper";
		if(probTypeEnum != ProbabilityModelOptions.POISSON) {
			boolean first = true;
			for(double aperVal:this.aperValues) {
				if(first)
					aperString+=aperVal;
				else
					aperString+=","+aperVal;
				first=false;
			}
			aperString = aperString.replace(".", "pt");			
		}
		
		System.out.println("\naperString: "+aperString+"\n");
		
		int tempDur = (int) Math.round(numYears/1000);
		
		if (probTypeEnum == ProbabilityModelOptions.POISSON) {
			probTypeString= "Pois";
		}
		else if(probTypeEnum == ProbabilityModelOptions.U3_BPT) {
			probTypeString= "U3BPT";
		}
		else if(probTypeEnum == ProbabilityModelOptions.WG02_BPT) {
			probTypeString= "WG02BPT";
		}
		else
			throw new RuntimeException("Porbability type unrecognized");
		
		String dirNameForSavingFiles = "Psect_"+parentSectIndex+"_"+probTypeString+"_"+tempDur+"kyr";
		if(probTypeEnum != ProbabilityModelOptions.POISSON) {
			dirNameForSavingFiles += "_"+aperString;
			dirNameForSavingFiles += "_"+typeCalcForU3_Probs;
		}
		
		dirNameForSavingFiles += "_"+dirNameSuffix;
		
		String plotLabelString = probTypeString;
		if(probTypeEnum == ProbabilityModelOptions.U3_BPT)
			plotLabelString += " ("+aperString+", "+typeCalcForU3_Probs+")";
		else if(probTypeEnum == ProbabilityModelOptions.WG02_BPT)
			plotLabelString += " ("+aperString+")";

		File resultsDir = new File(dirNameForSavingFiles);
		if(!resultsDir.exists()) resultsDir.mkdir();

		
		// INTIALIZE THINGS:
		
		double[] probGainForFaultSystemSource = new double[erf.getNumFaultSystemSources()];
		for(int s=0;s<probGainForFaultSystemSource.length;s++)
			probGainForFaultSystemSource[s] = 1.0;	// default is 1.0

		// set original start time and total duration
		long origStartTimeMillis = 0;
		if(probTypeEnum != ProbabilityModelOptions.POISSON)
			origStartTimeMillis = erf.getTimeSpan().getStartTimeInMillis();
		double origStartYear = ((double)origStartTimeMillis)/MILLISEC_PER_YEAR+1970.0;
		System.out.println("orig start time: "+origStartTimeMillis+ " millis ("+origStartYear+" yrs)");
		System.out.println("numYears: "+numYears);
		
		double simDuration = 5;	// 1 year; this could be the expected time to next event?
		
		// initialize some things
		ArrayList<Double> normalizedRupRecurIntervals = new ArrayList<Double>();
		ArrayList<Double> normalizedSectRecurIntervals = new ArrayList<Double>();
		ArrayList<ArrayList<Double>> normalizedRupRecurIntervalsMagDepList = new ArrayList<ArrayList<Double>>();
		ArrayList<ArrayList<Double>> normalizedSectRecurIntervalsMagDepList = new ArrayList<ArrayList<Double>>();
		for(int i=0;i<numAperValues;i++) {
			normalizedRupRecurIntervalsMagDepList.add(new ArrayList<Double>());
			normalizedSectRecurIntervalsMagDepList.add(new ArrayList<Double>());
		}
		ArrayList<Double> yearsIntoSimulation = new ArrayList<Double>();
		ArrayList<Double> totRateAtYearsIntoSimulation = new ArrayList<Double>();

		double[] obsSectRateArray = new double[numSections];
		double[] obsSectSlipRateArray = new double[numSections];
		double[] obsRupRateArray = new double[erf.getTotNumRups()];
		double[] aveRupProbGainArray = new double[erf.getTotNumRups()];	// averages the prob gains at each event time
		double[] minRupProbGainArray = new double[erf.getTotNumRups()];	// averages the prob gains at each event time
		double[] maxRupProbGainArray = new double[erf.getTotNumRups()];	// averages the prob gains at each event time
		
		// this is for writing out simulated events that occur
		FileWriter eventFileWriter=null;
		try {
			eventFileWriter = new FileWriter(dirNameForSavingFiles+"/sampledEventsData.txt");
			eventFileWriter.write("nthRupIndex\tfssRupIndex\tyear\tepoch\tnormRupRI\n");
		} catch (IOException e1) {
			e1.printStackTrace();
		}


		int numRups=0;
		int numSteps=0;
		RandomDataGenerator randomDataSampler = new RandomDataGenerator();
		
		// set the forecast as Poisson to get long-term rates (and update)
		erf.getParameter(ProbabilityModelParam.NAME).setValue(ProbabilityModelOptions.POISSON);
		erf.updateForecast();
		
		List<Integer> fltSysRupIndicesForParentList = fltSysRupSet.getRupturesForParentSection(parentSectIndex);
		
		// fill in totalRate, longTermRateOfNthRups, magOfNthRups, and longTermSlipRateForSectArray
		double totalRate=0;
		IntegerPDF_FunctionSampler nthRupRandomSampler = new IntegerPDF_FunctionSampler(erf.getTotNumRups());
		double[] longTermRateOfNthRups = new double[erf.getTotNumRups()];	// this will include any aftershock reductions
		double[] magOfNthRups = new double[erf.getTotNumRups()];
		double[] longTermSlipRateForSectArray = new double[numSections];
		int nthRup=0;
		for(ProbEqkSource src:erf) {
			for(ProbEqkRupture rup:src) {
				double rate = rup.getMeanAnnualRate(erf.getTimeSpan().getDuration());
				int fltSysRupIndex = erf.getFltSysRupIndexForNthRup(nthRup);
				if(fltSysRupIndicesForParentList.contains(fltSysRupIndex))
					longTermRateOfNthRups[nthRup] = rate;
				else
					longTermRateOfNthRups[nthRup] = 0;
				magOfNthRups[nthRup] = rup.getMag();
				totalRate += longTermRateOfNthRups[nthRup];
				nthRupRandomSampler.set(nthRup, longTermRateOfNthRups[nthRup]);
				if(erf.getSrcIndexForNthRup(nthRup)<erf.getNumFaultSystemSources()) {
					// slip rates
					int fltSysIndex = erf.getFltSysRupIndexForNthRup(nthRup);
					List<Integer> sectIndices = fltSysRupSet.getSectionsIndicesForRup(fltSysIndex);
					double slips[];
					if(fltSysRupSet instanceof InversionFaultSystemRupSet) {
						slips = ((InversionFaultSystemRupSet) fltSysRupSet).getSlipOnSectionsForRup(erf.getFltSysRupIndexForNthRup(nthRup));
					}
					else {	// apply ave to all sections
						double mag = fltSysRupSet.getMagForRup(erf.getFltSysRupIndexForNthRup(nthRup));
						double area = fltSysRupSet.getAreaForRup(erf.getFltSysRupIndexForNthRup(nthRup));
						double aveSlip = FaultMomentCalc.getSlip(area, MagUtils.magToMoment(mag));
						slips = new double[sectIndices.size()];
						for(int i=0;i<slips.length;i++)
							slips[i]=aveSlip;
					}
					for(int s=0;s<sectIndices.size();s++) {
						int sectID = sectIndices.get(s);
						longTermSlipRateForSectArray[sectID] += rate*slips[s];
					}					
				}
				nthRup+=1;
			}
		}
		System.out.println("totalRate long term = "+totalRate);
		
		double totalLongTermRate = totalRate;
		

		// Make local sectIndexArrayForSrcList for faster simulations
		ArrayList<int[]> sectIndexArrayForSrcList = new ArrayList<int[]>();
		for(int s=0; s<erf.getNumFaultSystemSources();s++) {
			List<Integer> indexList = fltSysRupSet.getSectionsIndicesForRup(erf.getFltSysRupIndexForSource(s));
			int[] indexArray = new int[indexList.size()];
			for(int i=0;i<indexList.size();i++)
				indexArray[i] = indexList.get(i);
			sectIndexArrayForSrcList.add(indexArray);
		}
		
		// make the target MFD - TODO
		if(D) System.out.println("Making target MFD");
		SummedMagFreqDist targetMFD = ERF_Calculator.getTotalMFD_ForERF(erf, 5.05, 8.95, 40, true);
		double origTotMoRate = ERF_Calculator.getTotalMomentRateInRegion(erf, null);
		System.out.println("originalTotalMomentRate: "+origTotMoRate);
		targetMFD.setName("Target MFD");
		String tempString = "total rate = "+(float)targetMFD.getTotalIncrRate();
		tempString += "\ntotal rate >= 6.7 = "+(float)targetMFD.getCumRate(6.75);
		tempString += "\ntotal MoRate = "+(float)origTotMoRate;
		targetMFD.setInfo(tempString);
		
		// MFD for simulation
		SummedMagFreqDist obsMFD = new SummedMagFreqDist(5.05,8.95,40);
		double obsMoRate = 0;
		
		// set the ave cond recurrence intervals
		double[] aveCondRecurIntervalForFltSysRups;
		if(aveRecurIntervals) {
			if(aveCondRecurIntervalForFltSysRups_type1 == null)
				aveCondRecurIntervalForFltSysRups_type1 = computeAveCondRecurIntervalForFltSysRups(1);
			aveCondRecurIntervalForFltSysRups = aveCondRecurIntervalForFltSysRups_type1;
		}
		else {
			if(aveCondRecurIntervalForFltSysRups_type2 == null)
				aveCondRecurIntervalForFltSysRups_type2 = computeAveCondRecurIntervalForFltSysRups(2);
			aveCondRecurIntervalForFltSysRups = aveCondRecurIntervalForFltSysRups_type2;			
		}

		
		// initialize things
		double currentYear=origStartYear;
		long currentTimeMillis = origStartTimeMillis;
		
		// this is to track progress
		int percDoneThresh=0;
		int percDoneIncrement=5;

		long startRunTime = System.currentTimeMillis();
		
		// read section date of last file if not null
		if(inputDateOfLastFileName != null && probTypeEnum != ProbabilityModelOptions.POISSON)
			readSectTimeSinceLastEventFromFile(inputDateOfLastFileName, currentTimeMillis);
		else {
			getSectNormTimeSinceLastHistPlot(currentTimeMillis, "From Pref Data");
		}
		
		CalcProgressBar progressBar = new CalcProgressBar(dirNameForSavingFiles,"Num Years Done");
		progressBar.showProgress(true);
	
		
		boolean firstEvent = true;
		while (currentYear<numYears+origStartYear) {
			
			progressBar.updateProgress((int)Math.round(currentYear-origStartYear), (int)Math.round(numYears));
			
			// write progress
			int percDone = (int)Math.round(100*(currentYear-origStartYear)/numYears);
			if(percDone >= percDoneThresh) {
				double timeInMin = ((double)(System.currentTimeMillis()-startRunTime)/(1000.0*60.0));
				System.out.println("\n"+percDoneThresh+"% done in "+(float)timeInMin+" minutes"+";  totalRate="+(float)totalRate+"; yr="+(float)currentYear+": numRups="+numRups+"\n");	
				percDoneThresh += percDoneIncrement;
			}
			
			// update gains and sampler if not Poisson
			if(probTypeEnum != ProbabilityModelOptions.POISSON) {
				// first the gains
				if(probTypeEnum == ProbabilityModelOptions.U3_BPT) {
					for(int fltSysRupIndex:fltSysRupIndicesForParentList) {
						int s = erf.getSrcIndexForFltSysRup(fltSysRupIndex);
						probGainForFaultSystemSource[s] = getU3_ProbGainForRup(fltSysRupIndex, 0.0, false, aveRecurIntervals, aveNormTimeSinceLast, currentTimeMillis, simDuration);
					}
				}
				else if(probTypeEnum == ProbabilityModelOptions.WG02_BPT) {
					sectionGainArray=null; // set this null so it gets updated
					for(int fltSysRupIndex:fltSysRupIndicesForParentList) {
						int s = erf.getSrcIndexForFltSysRup(fltSysRupIndex);
						probGainForFaultSystemSource[s] = getWG02_ProbGainForRup(fltSysRupIndex, false, currentTimeMillis, simDuration);
					}
				}		
				// now update totalRate and ruptureSampler (for all rups since start time changed)
				for(int fltSysRupIndex:fltSysRupIndicesForParentList) {
					int srcIndex = erf.getSrcIndexForFltSysRup(fltSysRupIndex);
					for(int n:erf.get_nthRupIndicesForSource(srcIndex)) {
						double probGain = probGainForFaultSystemSource[srcIndex];
						double newRate = longTermRateOfNthRups[n] * probGain;
						nthRupRandomSampler.set(n, newRate);
						aveRupProbGainArray[n] += probGain;
						if(minRupProbGainArray[n]>probGain)
							minRupProbGainArray[n] = probGain;
						if(maxRupProbGainArray[n]<probGain)
							maxRupProbGainArray[n] = probGain;
					}
				}
				totalRate = nthRupRandomSampler.getSumOfY_vals();				
			}
			numSteps +=1;	// number of times gain is calculated
			yearsIntoSimulation.add(currentYear);
			totRateAtYearsIntoSimulation.add(totalRate);
			
			// sample time of next event
			double timeToNextInYrs;
			if(totalRate > 1e-6)
				timeToNextInYrs = randomDataSampler.nextExponential(1.0/totalRate);
			else
				timeToNextInYrs = simDuration*10;	// just make the next text fail
			
			if(timeToNextInYrs<=simDuration) {	// only keep if withing simDuration

				long eventTimeMillis = currentTimeMillis + (long)(timeToNextInYrs*MILLISEC_PER_YEAR);
				// System.out.println("Event time: "+eventTimeMillis+" ("+(timeToNextInYrs+timeOfNextInYrs)+" yrs)");

				// sample an event
				nthRup = nthRupRandomSampler.getRandomInt();
				int srcIndex = erf.getSrcIndexForNthRup(nthRup);

				//.			System.out.println(numRups+"\t"+currentYear+"\t"+totalRate+"\t"+timeToNextInYrs+"\t"+nthRup);


				obsRupRateArray[nthRup] += 1;

				// set that fault system event has occurred (and save normalized RI)
				if(srcIndex < erf.getNumFaultSystemSources()) {	// ignore other sources
					int fltSystRupIndex = erf.getFltSysRupIndexForSource(srcIndex);
					double rupMag = fltSysRupSet.getMagForRup(erf.getFltSysRupIndexForNthRup(nthRup));

					// compute and save the normalize recurrence interval if all sections had date of last
					if(aveNormTimeSinceLast) {	// average time since last
						double aveNormYearsSinceLast = getAveNormTimeSinceLastEventWhereKnown(fltSystRupIndex, eventTimeMillis);
						if(allSectionsHadDateOfLast) {
							normalizedRupRecurIntervals.add(aveNormYearsSinceLast);
							if(numAperValues>0)
								normalizedRupRecurIntervalsMagDepList.get(getAperIndexForRupMag(rupMag)).add(aveNormYearsSinceLast);
						}					
					}
					else {
						long aveDateOfLastMillis = getAveDateOfLastEventWhereKnown(fltSystRupIndex, eventTimeMillis);
						if(allSectionsHadDateOfLast) {
							double timeSinceLast = (eventTimeMillis-aveDateOfLastMillis)/MILLISEC_PER_YEAR;
							double normRI = timeSinceLast/aveCondRecurIntervalForFltSysRups[fltSystRupIndex];
							normalizedRupRecurIntervals.add(normRI);
							if(numAperValues>0)
								normalizedRupRecurIntervalsMagDepList.get(getAperIndexForRupMag(rupMag)).add(normRI);

						}					
					}

					// write event info out
					try {
						eventFileWriter.write(nthRup+"\t"+fltSystRupIndex+"\t"+(currentYear+timeToNextInYrs)+"\t"+eventTimeMillis+"\t"+normalizedRupRecurIntervals.get(normalizedRupRecurIntervals.size()-1)+"\n");
					} catch (IOException e1) {
						e1.printStackTrace();
					}


					// save normalized fault section recurrence intervals & RI along strike
					int[] sectID_Array = sectIndexArrayForSrcList.get(erf.getSrcIndexForFltSysRup(fltSystRupIndex));
					int numSectInRup=sectID_Array.length;
					double slips[];
					if(fltSysRupSet instanceof InversionFaultSystemRupSet) {
						slips = ((InversionFaultSystemRupSet) fltSysRupSet).getSlipOnSectionsForRup(erf.getFltSysRupIndexForNthRup(nthRup));
					}
					else {	// apply ave to all sections
						double area = fltSysRupSet.getAreaForRup(erf.getFltSysRupIndexForNthRup(nthRup));
						double aveSlip = FaultMomentCalc.getSlip(area, MagUtils.magToMoment(rupMag));
						slips = new double[numSectInRup];
						for(int i=0;i<slips.length;i++)
							slips[i]=aveSlip;
					}
					// obsSectSlipRateArray
					int ithSectInRup=0;
					for(int sect : sectID_Array) {
						obsSectSlipRateArray[sect] += slips[ithSectInRup];
						long timeOfLastMillis = dateOfLastForSect[sect];
						if(timeOfLastMillis != Long.MIN_VALUE) {
							double normYrsSinceLast = ((eventTimeMillis-timeOfLastMillis)/MILLISEC_PER_YEAR)*longTermPartRateForSectArray[sect];
							normalizedSectRecurIntervals.add(normYrsSinceLast);
							if(numAperValues>0)
								normalizedSectRecurIntervalsMagDepList.get(getAperIndexForRupMag(rupMag)).add(normYrsSinceLast);

						}
						ithSectInRup += 1;
					}

					// reset last event time and increment simulated/obs rate on sections
					for(int sect:sectIndexArrayForSrcList.get(srcIndex)) {
						dateOfLastForSect[sect] = eventTimeMillis;
						obsSectRateArray[sect] += 1.0; // add the event
					}
				}

				numRups+=1;
				obsMFD.addResampledMagRate(magOfNthRups[nthRup], 1.0, true);
				obsMoRate += MagUtils.magToMoment(magOfNthRups[nthRup]);

				// increment time
				currentYear += timeToNextInYrs;
				currentTimeMillis = eventTimeMillis;
				firstEvent=false;

			}
			else {
				currentYear+=simDuration;
				currentTimeMillis += (long)(simDuration*MILLISEC_PER_YEAR);
			}
		}

		progressBar.showProgress(false);
		
		try {
			eventFileWriter.close();
		} catch (IOException e2) {
			// TODO Auto-generated catch block
			e2.printStackTrace();
		}
		
		// write section date of last file if not null
		if(outputDateOfLastFileName != null)
			writeSectTimeSinceLastEventToFile(outputDateOfLastFileName, currentTimeMillis);
		
		String infoString = dirNameForSavingFiles;
		infoString += "\ninputDateOfLastFileName: "+inputDateOfLastFileName;
		infoString += "\nnumRups="+numRups;
		
		
		// plot average rupture gains
		double[] tempMagArray = new double[aveRupProbGainArray.length];
		for(int i=0;i<aveRupProbGainArray.length;i++) {
			aveRupProbGainArray[i]/=numSteps;
			tempMagArray[i]=magOfNthRups[i];	// the latter may include gridded seis rups
		}
		DefaultXY_DataSet aveRupProbGainVsMag = new DefaultXY_DataSet(tempMagArray,aveRupProbGainArray);
		DefaultXY_DataSet minRupProbGainVsMag = new DefaultXY_DataSet(tempMagArray,minRupProbGainArray);
		DefaultXY_DataSet maxRupProbGainVsMag = new DefaultXY_DataSet(tempMagArray,maxRupProbGainArray);
		aveRupProbGainVsMag.setName("Ave Rup Prob Gain vs Mag");
		double meanProbGain =0;
		for(double val:aveRupProbGainArray) 
			meanProbGain += val;
		meanProbGain /= aveRupProbGainArray.length;
		aveRupProbGainVsMag.setInfo("meanProbGain="+(float)meanProbGain);
		minRupProbGainVsMag.setName("Min Rup Prob Gain vs Mag");
		maxRupProbGainVsMag.setName("Max Rup Prob Gain vs Mag");
		ArrayList<DefaultXY_DataSet> aveRupProbGainVsMagFuncs = new ArrayList<DefaultXY_DataSet>();
		aveRupProbGainVsMagFuncs.add(aveRupProbGainVsMag);
		aveRupProbGainVsMagFuncs.add(minRupProbGainVsMag);
		aveRupProbGainVsMagFuncs.add(maxRupProbGainVsMag);
		ArrayList<PlotCurveCharacterstics> plotCharsAveGain = new ArrayList<PlotCurveCharacterstics>();
		plotCharsAveGain.add(new PlotCurveCharacterstics(PlotSymbol.CROSS, 4f, Color.BLUE));
		plotCharsAveGain.add(new PlotCurveCharacterstics(PlotSymbol.CROSS, 4f, Color.GREEN));
		plotCharsAveGain.add(new PlotCurveCharacterstics(PlotSymbol.CROSS, 4f, Color.RED));
		GraphWindow graphAveRupProbGainVsMag = new GraphWindow(aveRupProbGainVsMagFuncs, "Ave Rup Prob Gain vs Mag; "+plotLabelString, plotCharsAveGain); 
		graphAveRupProbGainVsMag.setX_AxisLabel("Magnitude");
		graphAveRupProbGainVsMag.setY_AxisLabel("Ave Rup Prob Gain");

		// write out these gains and other stuff
		for(int i=0;i<obsRupRateArray.length;i++) {
			obsRupRateArray[i] = obsRupRateArray[i]/numYears;
		}
		FileWriter gain_fr;
		try {
			gain_fr = new FileWriter(dirNameForSavingFiles+"/aveRupGainData.txt");
			gain_fr.write("nthRupIndex\taveRupGain\tminRupGain\tmaxRupGain\trupMag\trupLongTermRate\tobsRupRate\texpNumRups\trupCondRI\trupName\n");
			for(int i=0;i<aveRupProbGainArray.length;i++) {
				if(longTermRateOfNthRups[i]>0)
					gain_fr.write(i+"\t"+aveRupProbGainArray[i]+"\t"+minRupProbGainArray[i]+"\t"+maxRupProbGainArray[i]+"\t"+magOfNthRups[i]
						+"\t"+longTermRateOfNthRups[i]+"\t"+obsRupRateArray[i]+"\t"+numYears*longTermRateOfNthRups[i]+"\t"+aveCondRecurIntervalForFltSysRups[erf.getFltSysRupIndexForNthRup(i)]+"\t"+
						erf.getSource(erf.getSrcIndexForNthRup(i)).getName()+"\n");
			}
			gain_fr.close();
		} catch (IOException e1) {
			e1.printStackTrace();
		}

		// make aveGainVsMagFunction, weighted by rupture rate
		HistogramFunction aveGainVsMagHist = new HistogramFunction(5.05, 40, 0.1);
		HistogramFunction tempWtHist = new HistogramFunction(5.05, 40, 0.1);
		for(int i=0;i<aveRupProbGainArray.length;i++) {
			aveGainVsMagHist.add(magOfNthRups[i], aveRupProbGainArray[i]*longTermRateOfNthRups[i]);
			tempWtHist.add(magOfNthRups[i], longTermRateOfNthRups[i]);
		}
		for(int i=0;i<aveGainVsMagHist.size();i++) {
			double wt = tempWtHist.getY(i);
			if(wt>1e-15) // avoid division by zero
				aveGainVsMagHist.set(i,aveGainVsMagHist.getY(i)/wt);
		}
		aveGainVsMagHist.setName("aveGainVsMagHist");
		aveGainVsMagHist.setInfo("weighted by rupture long-term rates");
		GraphWindow graphAveGainVsMagHist = new GraphWindow(aveGainVsMagHist, "Ave Rup Gain vs Mag; "+plotLabelString); 
		graphAveGainVsMagHist.setX_AxisLabel("Mag");
		graphAveGainVsMagHist.setY_AxisLabel("Ave Gain");
		graphAveGainVsMagHist.setAxisLabelFontSize(22);
		graphAveGainVsMagHist.setTickLabelFontSize(20);
		graphAveGainVsMagHist.setPlotLabelFontSize(22);


		
		
		// make normalized rup recurrence interval plots
		double aper=Double.NaN;
		if(numAperValues==1)
			aper=aperValues[0];	// only one value, so include in plot for al ruptures
		ArrayList<EvenlyDiscretizedFunc> funcList = ProbModelsPlottingUtils.getNormRI_DistributionWithFits(normalizedRupRecurIntervals, aper);
		GraphWindow grapha_a = ProbModelsPlottingUtils.plotNormRI_DistributionWithFits(funcList, "Normalized Rupture RIs; "+plotLabelString);
//		GraphWindow grapha_a = General_EQSIM_Tools.plotNormRI_Distribution(normalizedRupRecurIntervals, "Normalized Rupture RIs; "+plotLabelString, aperiodicity);
		infoString += "\n\nRup "+funcList.get(0).getName()+":";
		infoString += "\n"+funcList.get(0).getInfo();
		infoString += "\n\n"+funcList.get(1).getName();
		infoString += "\n"+funcList.get(1).getInfo();
		
		// now mag-dep:
		if(numAperValues >1) {
			for(int i=0;i<numAperValues;i++) {
				ArrayList<EvenlyDiscretizedFunc> funcListMagDep = ProbModelsPlottingUtils.getNormRI_DistributionWithFits(normalizedRupRecurIntervalsMagDepList.get(i), aperValues[i]);
				String label = getMagDepAperInfoString(i);
				GraphWindow graphaMagDep = ProbModelsPlottingUtils.plotNormRI_DistributionWithFits(funcListMagDep, "Norm Rup RIs; "+label+"; "+plotLabelString);
				infoString += "\n\nRup "+funcListMagDep.get(0).getName()+" for "+label+":";
				infoString += "\n"+funcListMagDep.get(0).getInfo();
				infoString += "\n\n"+funcListMagDep.get(1).getName();
				infoString += "\n"+funcListMagDep.get(1).getInfo();	
				// save plot now
				try {
					graphaMagDep.saveAsPDF(dirNameForSavingFiles+"/normRupRecurIntsForMagRange"+i+".pdf");
					graphaMagDep.saveAsTXT(dirNameForSavingFiles+"/normRupRecurIntsForMagRange"+i+".txt");
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		// make normalized sect recurrence interval plots
		ArrayList<EvenlyDiscretizedFunc> funcList2 = ProbModelsPlottingUtils.getNormRI_DistributionWithFits(normalizedSectRecurIntervals, aper);
		GraphWindow graph2_b = ProbModelsPlottingUtils.plotNormRI_DistributionWithFits(funcList2, "Normalized Section RIs; "+plotLabelString);
		infoString += "\n\nSect "+funcList2.get(0).getName()+":";
		infoString += "\n"+funcList2.get(0).getInfo();
		
		// now mag-dep:
		if(numAperValues >1) {
			for(int i=0;i<numAperValues;i++) {
				ArrayList<EvenlyDiscretizedFunc> funcListMagDep = ProbModelsPlottingUtils.getNormRI_DistributionWithFits(normalizedSectRecurIntervalsMagDepList.get(i), aperValues[i]);
				String label = getMagDepAperInfoString(i);
				GraphWindow graphaMagDep = ProbModelsPlottingUtils.plotNormRI_DistributionWithFits(funcListMagDep, "Norm Sect RIs; "+label+"; "+plotLabelString);
				infoString += "\n\nSect "+funcListMagDep.get(0).getName()+" for "+label+":";
				infoString += "\n"+funcListMagDep.get(0).getInfo();
				infoString += "\n\n"+funcListMagDep.get(1).getName();
				infoString += "\n"+funcListMagDep.get(1).getInfo();	
				// save plot now
				try {
					graphaMagDep.saveAsPDF(dirNameForSavingFiles+"/normSectRecurIntsForMagRange"+i+".pdf");
					graphaMagDep.saveAsTXT(dirNameForSavingFiles+"/normSectRecurIntsForMagRange"+i+".txt");
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		

		// 	plot simNormTimeSinceLastHist & simProbGainHist - these are weighted by long-term rates
		if(probTypeEnum == ProbabilityModelOptions.U3_BPT) {
//			int numObs = (int)Math.round(simNormTimeSinceLastHist.calcSumOfY_Vals());
			simNormTimeSinceLastHist.scale(1.0/(simNormTimeSinceLastHist.calcSumOfY_Vals()*simNormTimeSinceLastHist.getDelta())); // makes it a density function
			ArrayList<EvenlyDiscretizedFunc> funcListForSimNormTimeSinceLastHist = ProbModelsPlottingUtils.addBPT_Fit(simNormTimeSinceLastHist);
			simNormTimeSinceLastHist.setName("simNormTimeSinceLastHist");
//			simNormTimeSinceLastHist.setInfo("Dist of normalized time since last for each rupture at all event times\nMean = "+simNormTimeSinceLastHist.computeMean()+"\nnumObs="+numObs);
			simNormTimeSinceLastHist.setInfo("Dist of normalized time since last for each rupture at all event times\nMean = "+simNormTimeSinceLastHist.computeMean());
			GraphWindow graphSimNormTimeSinceLastHist = ProbModelsPlottingUtils.plotNormRI_DistributionWithFits(funcListForSimNormTimeSinceLastHist, "simNormTimeSinceLastHist; "+plotLabelString);
			
//			simNormTimeSinceLastForMagBelow7_Hist
//			numObs = (int)Math.round(simNormTimeSinceLastForMagBelow7_Hist.calcSumOfY_Vals());
			simNormTimeSinceLastForMagBelow7_Hist.scale(1.0/(simNormTimeSinceLastForMagBelow7_Hist.calcSumOfY_Vals()*simNormTimeSinceLastForMagBelow7_Hist.getDelta())); // makes it a density function
			ArrayList<EvenlyDiscretizedFunc> funcListForSimNormTimeSinceLastHistForSmall = ProbModelsPlottingUtils.addBPT_Fit(simNormTimeSinceLastForMagBelow7_Hist);
			simNormTimeSinceLastForMagBelow7_Hist.setName("simNormTimeSinceLastForMagBelow7_Hist");
//			simNormTimeSinceLastForMagBelow7_Hist.setInfo("Dist of normalized time since last for each rupture at all event times from M<=7\nMean = "+simNormTimeSinceLastForMagBelow7_Hist.computeMean()+"\nnumObs="+numObs);
			simNormTimeSinceLastForMagBelow7_Hist.setInfo("Dist of normalized time since last for each rupture at all event times from M<=7\nMean = "+simNormTimeSinceLastForMagBelow7_Hist.computeMean());
			GraphWindow graphSimNormTimeSinceLastForMagBelow7_Hist = ProbModelsPlottingUtils.plotNormRI_DistributionWithFits(funcListForSimNormTimeSinceLastHistForSmall, "simNormTimeSinceLastForMagBelow7_Hist; "+plotLabelString);

			simNormTimeSinceLastForMagAbove7_Hist.scale(1.0/(simNormTimeSinceLastForMagAbove7_Hist.calcSumOfY_Vals()*simNormTimeSinceLastForMagAbove7_Hist.getDelta())); // makes it a density function
			ArrayList<EvenlyDiscretizedFunc> funcListForSimNormTimeSinceLastHistForLarge = ProbModelsPlottingUtils.addBPT_Fit(simNormTimeSinceLastForMagAbove7_Hist);
			simNormTimeSinceLastForMagAbove7_Hist.setName("simNormTimeSinceLastForMagAbove7_Hist");
			simNormTimeSinceLastForMagAbove7_Hist.setInfo("Dist of normalized time since last for each rupture at all event times from M>7\nMean = "+simNormTimeSinceLastForMagAbove7_Hist.computeMean());
			GraphWindow graphSimNormTimeSinceLastForMagAbove7_Hist = ProbModelsPlottingUtils.plotNormRI_DistributionWithFits(funcListForSimNormTimeSinceLastHistForLarge, "simNormTimeSinceLastForMagAbove7_Hist; "+plotLabelString);

			
//			numObs = (int)Math.round(simProbGainHist.calcSumOfY_Vals());
			simProbGainHist.scale(1.0/(simProbGainHist.calcSumOfY_Vals()*simProbGainHist.getDelta())); // makes it a density function
			simProbGainHist.setName("simProbGainHist");
//			simProbGainHist.setInfo("Dist of gains for each rupture at all event times (simProbGainHist)\nMean = "+simProbGainHist.computeMean()+"\nnumObs="+numObs);
			simProbGainHist.setInfo("Dist of gains for each rupture at all event times (simProbGainHist)\nMean = "+simProbGainHist.computeMean());
			ArrayList<EvenlyDiscretizedFunc> funcListForSimProbGainHist = new ArrayList<EvenlyDiscretizedFunc>();
			funcListForSimProbGainHist.add(simProbGainHist);
			simProbGainForMagBelow7_Hist.scale(1.0/(simProbGainForMagBelow7_Hist.calcSumOfY_Vals()*simProbGainForMagBelow7_Hist.getDelta())); // makes it a density function
			simProbGainForMagAbove7_Hist.scale(1.0/(simProbGainForMagAbove7_Hist.calcSumOfY_Vals()*simProbGainForMagAbove7_Hist.getDelta())); // makes it a density function
			simProbGainForMagBelow7_Hist.setName("simProbGainForMagBelow7_Hist");
			simProbGainForMagAbove7_Hist.setName("simProbGainForMagAbove7_Hist");
			simProbGainForMagBelow7_Hist.setInfo("Mean = "+simProbGainForMagBelow7_Hist.computeMean());
			simProbGainForMagAbove7_Hist.setInfo("Mean = "+simProbGainForMagAbove7_Hist.computeMean());
			funcListForSimProbGainHist.add(simProbGainForMagBelow7_Hist);
			funcListForSimProbGainHist.add(simProbGainForMagAbove7_Hist);
			ArrayList<PlotCurveCharacterstics> plotCharsForSimProbGainHist = new ArrayList<PlotCurveCharacterstics>();
			plotCharsForSimProbGainHist.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 2f, Color.RED));
			plotCharsForSimProbGainHist.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLUE));
			plotCharsForSimProbGainHist.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLACK));

			GraphWindow graphSimProbGainHistWindow = new GraphWindow(funcListForSimProbGainHist, "simProbGainHist", plotCharsForSimProbGainHist); 
			graphSimProbGainHistWindow.setX_AxisLabel("Gain");
			graphSimProbGainHistWindow.setY_AxisLabel("Density");
			graphSimProbGainHistWindow.setAxisLabelFontSize(22);
			graphSimProbGainHistWindow.setTickLabelFontSize(20);
			graphSimProbGainHistWindow.setPlotLabelFontSize(22);

			try {
				graphSimNormTimeSinceLastHist.saveAsPDF(dirNameForSavingFiles+"/simNormTimeSinceLastHist.pdf");
				graphSimProbGainHistWindow.saveAsPDF(dirNameForSavingFiles+"/simProbGainHist.pdf");
				graphSimNormTimeSinceLastForMagBelow7_Hist.saveAsPDF(dirNameForSavingFiles+"/simNormTimeSinceLastForMagBelow7_Hist.pdf");
				graphSimNormTimeSinceLastForMagAbove7_Hist.saveAsPDF(dirNameForSavingFiles+"/simNormTimeSinceLastForMagAbove7_Hist.pdf");
				graphSimNormTimeSinceLastHist.saveAsTXT(dirNameForSavingFiles+"/simNormTimeSinceLastHist.txt");
				graphSimProbGainHistWindow.saveAsTXT(dirNameForSavingFiles+"/simProbGainHist.txt");
				graphSimNormTimeSinceLastForMagBelow7_Hist.saveAsTXT(dirNameForSavingFiles+"/simNormTimeSinceLastForMagBelow7_Hist.txt");
				graphSimNormTimeSinceLastForMagAbove7_Hist.saveAsTXT(dirNameForSavingFiles+"/simNormTimeSinceLastForMagAbove7_Hist.txt");
			} catch (IOException e) {
				e.printStackTrace();
			}

		}

		
		// plot long-term rate versus time
		DefaultXY_DataSet totRateVersusTime = new DefaultXY_DataSet(yearsIntoSimulation,totRateAtYearsIntoSimulation);
		double meanTotRate=0;
		double totWt=0;
		for(int i=0;i<totRateAtYearsIntoSimulation.size()-1;i++) {
			double wt = yearsIntoSimulation.get(i+1)-yearsIntoSimulation.get(i); // apply rate until time of next event
			meanTotRate+=totRateAtYearsIntoSimulation.get(i)*wt;
			totWt+=wt;
		}
		meanTotRate /= totWt;
		totRateVersusTime.setName("Total Rate vs Time");
		totRateVersusTime.setInfo("Mean Total Rate = "+meanTotRate+"\nLong Term Rate = "+totalLongTermRate);
		DefaultXY_DataSet longTermRateFunc = new DefaultXY_DataSet();
		longTermRateFunc.set(totRateVersusTime.getMinX(),totalLongTermRate);
		longTermRateFunc.set(totRateVersusTime.getMaxX(),totalLongTermRate);
		longTermRateFunc.setName("Long term rate");
		ArrayList<DefaultXY_DataSet> funcsTotRate = new ArrayList<DefaultXY_DataSet>();
		funcsTotRate.add(totRateVersusTime);
		funcsTotRate.add(longTermRateFunc);
		ArrayList<PlotCurveCharacterstics> plotCharsTotRate = new ArrayList<PlotCurveCharacterstics>();
		plotCharsTotRate.add(new PlotCurveCharacterstics(PlotSymbol.CROSS, 4f, Color.BLUE));
		plotCharsTotRate.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.RED));
		GraphWindow graphTotalRateVsTime = new GraphWindow(funcsTotRate, "Total Rate vs Time; "+plotLabelString, plotCharsTotRate); 
		graphTotalRateVsTime.setX_AxisLabel("Times (years)");
		graphTotalRateVsTime.setY_AxisLabel("Total Rate (per year)");



		// plot MFDs
		obsMFD.scale(1.0/numYears);
		obsMFD.setName("Simulated MFD");
		obsMoRate /= numYears;
		double obsTotRate = obsMFD.getTotalIncrRate();
		double rateRatio = obsTotRate/targetMFD.getTotalIncrRate();
		String infoString2 = "total rate = "+(float)obsTotRate+" (ratio="+(float)rateRatio+")";
		double obsTotRateAbove6pt7 = obsMFD.getCumRate(6.75);
		double rateAbove6pt7_Ratio = obsTotRateAbove6pt7/targetMFD.getCumRate(6.75);
		infoString2 += "\ntotal rate >= 6.7 = "+(float)obsTotRateAbove6pt7+" (ratio="+(float)rateAbove6pt7_Ratio+")";
		double moRateRatio = obsMoRate/origTotMoRate;
		infoString2 += "\ntotal MoRate = "+(float)obsMoRate+" (ratio="+(float)moRateRatio+")";
		obsMFD.setInfo(infoString2);
		
		infoString += "\n\nSimulationStats:\n";
		infoString += "totRate\tratio\ttotRateM>=6.7\tratio\ttotMoRate\tratio\n";
		infoString += (float)obsTotRate+"\t"+(float)rateRatio+"\t"+(float)obsTotRateAbove6pt7+"\t"+(float)rateAbove6pt7_Ratio+"\t"+(float)obsMoRate+"\t"+(float)moRateRatio;
		
		// write this now in case of crash
		FileWriter info_fr;
		try {
			info_fr = new FileWriter(dirNameForSavingFiles+"/infoString.txt");
			info_fr.write(infoString);
			info_fr.close();
		} catch (IOException e1) {
			e1.printStackTrace();
		}

		
		System.out.println("INFO STRING:\n\n"+infoString);

		ArrayList<EvenlyDiscretizedFunc> funcs = new ArrayList<EvenlyDiscretizedFunc>();
		funcs.add(targetMFD);
		funcs.add(obsMFD);
		funcs.add(targetMFD.getCumRateDistWithOffset());
		funcs.add(obsMFD.getCumRateDistWithOffset());
		GraphWindow graph = new GraphWindow(funcs, "Incremental Mag-Freq Dists; "+plotLabelString); 
		graph.setX_AxisLabel("Mag");
		graph.setY_AxisLabel("Rate");
		graph.setYLog(true);	// this causes problems
		graph.setY_AxisRange(1e-4, 1.0);
		graph.setX_AxisRange(5.5, 8.5);
		
		// plot observed versus imposed rup rates - Is this really meaningful?
		DefaultXY_DataSet obsVsImposedRupRates = new DefaultXY_DataSet(longTermRateOfNthRups,obsRupRateArray);
		obsVsImposedRupRates.setName("Simulated vs Imposed Rup Rates");
		DefaultXY_DataSet perfectAgreementFunc4 = new DefaultXY_DataSet();
		perfectAgreementFunc4.set(1e-5,1e-5);
		perfectAgreementFunc4.set(0.05,0.05);
		perfectAgreementFunc4.setName("Perfect agreement line");
		ArrayList<DefaultXY_DataSet> funcs4 = new ArrayList<DefaultXY_DataSet>();
		funcs4.add(obsVsImposedRupRates);
		funcs4.add(perfectAgreementFunc4);
		ArrayList<PlotCurveCharacterstics> plotChars4 = new ArrayList<PlotCurveCharacterstics>();
		plotChars4.add(new PlotCurveCharacterstics(PlotSymbol.CROSS, 4f, Color.BLUE));
		plotChars4.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.RED));
		GraphWindow graph4 = new GraphWindow(funcs4, "Obs vs Imposed Rup Rates; "+plotLabelString, plotChars4); 
		graph4.setX_AxisRange(5d/numYears, 0.01);
		graph4.setY_AxisRange(5d/numYears, 0.01);
		graph4.setYLog(true);
		graph4.setXLog(true);
		graph4.setX_AxisLabel("Imposed Rup Rate (per yr)");
		graph4.setY_AxisLabel("Simulated Rup Rate (per yr)");

		
		
		// plot observed versus imposed section slip rates
		for(int i=0;i<obsSectSlipRateArray.length;i++) {
			obsSectSlipRateArray[i] = obsSectSlipRateArray[i]/numYears;
		}
		DefaultXY_DataSet obsVsImposedSectSlipRates = new DefaultXY_DataSet(longTermSlipRateForSectArray,obsSectSlipRateArray);
		obsVsImposedSectSlipRates.setName("Simulated vs Imposed Section Slip Rates");
		DefaultXY_DataSet perfectAgreementSlipRateFunc = new DefaultXY_DataSet();
		perfectAgreementSlipRateFunc.set(1e-5,1e-5);
		perfectAgreementSlipRateFunc.set(0.05,0.05);
		perfectAgreementSlipRateFunc.setName("Perfect agreement line");
		ArrayList<DefaultXY_DataSet> funcsSR = new ArrayList<DefaultXY_DataSet>();
		funcsSR.add(obsVsImposedSectSlipRates);
		funcsSR.add(perfectAgreementSlipRateFunc);
		ArrayList<PlotCurveCharacterstics> plotCharsSR = new ArrayList<PlotCurveCharacterstics>();
		plotCharsSR.add(new PlotCurveCharacterstics(PlotSymbol.CROSS, 4f, Color.BLUE));
		plotCharsSR.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.RED));
		GraphWindow graphSR = new GraphWindow(funcsSR, "Obs vs Imposed Section Slip Rates; "+plotLabelString, plotCharsSR); 
		graphSR.setX_AxisRange(1e-5, 0.05);
		graphSR.setY_AxisRange(1e-5, 0.05);
		graphSR.setYLog(true);
		graphSR.setXLog(true);
		graphSR.setX_AxisLabel("Imposed Section Slip Rate (mm/yr)");
		graphSR.setY_AxisLabel("Simulated Section Slip Rate (mm/yr)");
		
		
		// plot observed versus imposed section rates
		for(int i=0;i<obsSectRateArray.length;i++) {
			obsSectRateArray[i] = obsSectRateArray[i]/numYears;
		}
		DefaultXY_DataSet obsVsImposedSectRates = new DefaultXY_DataSet(longTermPartRateForSectArray,obsSectRateArray);
		obsVsImposedSectRates.setName("Simulated vs Imposed Section Event Rates");
		DefaultXY_DataSet perfectAgreementFunc = new DefaultXY_DataSet();
		perfectAgreementFunc.set(1e-5,1e-5);
		perfectAgreementFunc.set(0.05,0.05);
		perfectAgreementFunc.setName("Perfect agreement line");
		ArrayList<DefaultXY_DataSet> funcs2 = new ArrayList<DefaultXY_DataSet>();
		funcs2.add(obsVsImposedSectRates);
		funcs2.add(perfectAgreementFunc);
		ArrayList<PlotCurveCharacterstics> plotChars = new ArrayList<PlotCurveCharacterstics>();
		plotChars.add(new PlotCurveCharacterstics(PlotSymbol.CROSS, 4f, Color.BLUE));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.RED));
		GraphWindow graph2 = new GraphWindow(funcs2, "Obs vs Imposed Section Rates; "+probTypeString, plotChars); 
		graph2.setX_AxisRange(5d/numYears, 0.05);
		graph2.setY_AxisRange(5d/numYears, 0.05);
		graph2.setYLog(true);
		graph2.setXLog(true);
		graph2.setX_AxisLabel("Imposed Section Participation Rate (per yr)");
		graph2.setY_AxisLabel("Simulated Section Participation Rate (per yr)");
		
		// write section rates with names
		FileWriter eventRates_fr;
		try {
			eventRates_fr = new FileWriter(dirNameForSavingFiles+"/obsVsImposedSectionPartRates.txt");
			eventRates_fr.write("sectID\timposedRate\tsimulatedRate\tsimOverImpRateRatio\thasDateOfLast\tsectName\n");
			for(int i=0;i<fltSysRupSet.getNumSections();i++) {
				FaultSectionPrefData fltData = fltSysRupSet.getFaultSectionData(i);
				double ratio = obsSectRateArray[i]/longTermPartRateForSectArray[i];
				boolean hasDateOfLast=false;
				if(fltData.getDateOfLastEvent() != Long.MIN_VALUE)
					hasDateOfLast=true;
				eventRates_fr.write(fltData.getSectionId()+"\t"+longTermPartRateForSectArray[i]+"\t"+obsSectRateArray[i]+"\t"+ratio+"\t"+hasDateOfLast+"\t"+fltData.getName()+"\n");
			}
			eventRates_fr.close();
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		
		
		try {
			// plots
			graphAveRupProbGainVsMag.saveAsPDF(dirNameForSavingFiles+"/aveRupProbGainVsMag.pdf");
			graphAveRupProbGainVsMag.saveAsTXT(dirNameForSavingFiles+"/aveRupProbGainVsMag.txt");
			grapha_a.saveAsPDF(dirNameForSavingFiles+"/normalizedRupRecurIntervals.pdf");
			grapha_a.saveAsTXT(dirNameForSavingFiles+"/normalizedRupRecurIntervalsPlot.txt");
			graph2_b.saveAsPDF(dirNameForSavingFiles+"/normalizedSectRecurIntervals.pdf");
			graph2_b.saveAsTXT(dirNameForSavingFiles+"/normalizedSectRecurIntervalsPlot.txt");
			graphTotalRateVsTime.saveAsPDF(dirNameForSavingFiles+"/totalRateVsTime.pdf");
			graph.saveAsPDF(dirNameForSavingFiles+"/magFreqDists.pdf");
			graph2.saveAsPDF(dirNameForSavingFiles+"/obsVsImposedSectionPartRates.pdf");
//			graph2.saveAsTXT(dirNameForSavingFiles+"/obsVsImposedSectionPartRates.txt"); // replaced above
			graph4.saveAsPDF(dirNameForSavingFiles+"/obsVsImposedRupRates.pdf");
			graph4.saveAsTXT(dirNameForSavingFiles+"/obsVsImposedRupRates.txt");
//			graph9.saveAsPDF(dirNameForSavingFiles+"/safEventsVsTime.pdf");
			graphSR.saveAsPDF(dirNameForSavingFiles+"/obsVsImposedSectionSlipRates.pdf");
			graphSR.saveAsTXT(dirNameForSavingFiles+"/obsVsImposedSectionSlipRates.txt");
			graphAveGainVsMagHist.saveAsPDF(dirNameForSavingFiles+"/aveRupGainVsMagHist.pdf");
			graphAveGainVsMagHist.saveAsTXT(dirNameForSavingFiles+"/aveRupGainVsMagHist.txt");
			// data:
			FileWriter fr = new FileWriter(dirNameForSavingFiles+"/normalizedRupRecurIntervals.txt");
			for (double val : normalizedRupRecurIntervals)
				fr.write(val + "\n");
			fr.close();

			fr = new FileWriter(dirNameForSavingFiles+"/normalizedSectRecurIntervals.txt");
			for (double val : normalizedSectRecurIntervals)
				fr.write(val + "\n");
			fr.close();

			AbstractDiscretizedFunc.writeSimpleFuncFile(targetMFD, dirNameForSavingFiles+"/targetMFD.txt");
			AbstractDiscretizedFunc.writeSimpleFuncFile(obsMFD, dirNameForSavingFiles+"/simulatedMFD.txt");
			AbstractDiscretizedFunc.writeSimpleFuncFile(targetMFD.getCumRateDistWithOffset(), dirNameForSavingFiles+"/targetCumMFD.txt");
			AbstractDiscretizedFunc.writeSimpleFuncFile(obsMFD.getCumRateDistWithOffset(), dirNameForSavingFiles+"/simulatedCumMFD.txt");


		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	
	
	
	
	
	
	/**
	 * This simulates a number of next x-year catalogs (e.g., to check for biases at long durations).
	 * 
	 * The start time and duration are obtained from the ERF, and we assume updateForecast() has been called.
	 * 
	 * Note that the predicted sections rates are expected to be biased high where second events
	 * are likely (the simulation includes these, but the targets do not).  These cases should be
	 * closer to the long-term rate to the extend that expected number (longTermSecRate*Duration) is high.
	 * @param erf
	 * @param dirNameSuffix
	 * @param inputDateOfLastFileName
	 * @param numCatalogs
	 */
	public  void testER_NextXyrSimulation(File resultsDir, String inputDateOfLastFileName, 
			int numCatalogs, boolean makePlots, Long randomSeed) throws IOException  {
		
		
		
		if(!resultsDir.exists()) resultsDir.mkdir();
		// for writing out info:
		FileWriter info_fr = new FileWriter(new File(resultsDir, "infoString.txt"));
		// TODO
		info_fr.write("resultsDir: "+resultsDir.getPath()+"\n");
		// this is for writing out simulated events that occur
		FileWriter eventFileWriter=null;
		try {
			eventFileWriter = new FileWriter(new File(resultsDir,"sampledEventsData.txt"));
			eventFileWriter.write("nthRupIndex\tfssRupIndex\tyear\tepoch\tnormRI\tmag\tnthCatalog\ttimeToNextInYrs\tutilizedPaleoSite\n");
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		
		ArrayList<PaleoRateConstraint> paleoConstraints = UCERF3_PaleoRateConstraintFetcher.getConstraints(fltSysRupSet.getFaultSectionDataList());
		ArrayList<Integer> paleoConstrainSections = new ArrayList<Integer>();
		info_fr.write("\n"+paleoConstraints.size()+" Paleo Sites (site, sectID, sectName):\n");
		for(PaleoRateConstraint constr : paleoConstraints) {
			paleoConstrainSections.add(constr.getSectionIndex());
			info_fr.write("\t"+constr.getPaleoSiteName()+"\t"+constr.getSectionIndex()+"\t"+constr.getFaultSectionName()+"\n");
		}

		
		// set duration and update forecast
		double forecastDurationYrs = erf.getTimeSpan().getDuration();

		RandomDataGenerator randomDataSampler = new RandomDataGenerator();
		if(randomSeed == null) {
			randomSeed = System.currentTimeMillis();
			info_fr.write("\nRandom Seed: "+randomSeed+" (from System.currentTimeMillis() since input null)\n");
		}
		else {
			randomDataSampler.reSeed(randomSeed);
			info_fr.write("\nRandom Seed: "+randomSeed+" (input value)\n");
		}
		randomDataSampler.reSeed(randomSeed);

		info_fr.write("\nERF Adjustable Paramteres:\n\n");
		for(Parameter param : erf.getAdjustableParameterList()) {
			info_fr.write("\t"+param.getName()+" = "+param.getValue()+"\n");
		}
		TimeSpan tsp = erf.getTimeSpan();
		info_fr.write("\nERF StartTime: "+tsp.getStartTimeYear()+"\n");
		info_fr.write("\nERF TimeSpan Duration: "+erf.getTimeSpan().getDuration()+" years\n");

		info_fr.flush();

		boolean aveRecurIntervals = erf.aveRecurIntervalsInU3_BPTcalc;
		boolean aveNormTimeSinceLast = erf.aveNormTimeSinceLastInU3_BPTcalc;
		
		ProbabilityModelOptions probTypeEnum = (ProbabilityModelOptions)erf.getParameter(ProbabilityModelParam.NAME).getValue();

		// TODO
		String plotLabelString = resultsDir.getPath();


		// INTIALIZE THINGS:
		double[] probGainForFaultSystemSource = new double[erf.getNumFaultSystemSources()];
		for(int s=0;s<probGainForFaultSystemSource.length;s++)
			probGainForFaultSystemSource[s] = 1.0;	// default is 1.0

		// set original start time and total duration
		long origStartTimeMillis = 0;
		if(probTypeEnum != ProbabilityModelOptions.POISSON)
			origStartTimeMillis = erf.getTimeSpan().getStartTimeInMillis();
		double origStartYear = ((double)origStartTimeMillis)/MILLISEC_PER_YEAR+1970.0;
		System.out.println("orig start time: "+origStartTimeMillis+ " millis ("+origStartYear+" yrs)");

		double[] obsRupRateArray = new double[erf.getTotNumRups()];
		double[] obsSectRateArray = new double[numSections];



		// fill in totalTargetRate, targetRateOfNthRups, magOfNthRups
		// note that total target rates do not include likelihood of second event, 
		// so expect simulated event rates to be higher where longTermSectRate*Duration is near 1
		double totalTargetRate=0;
		IntegerPDF_FunctionSampler nthRupRandomSampler = new IntegerPDF_FunctionSampler(erf.getTotNumRups());
		double[] targetRateOfNthRups = new double[erf.getTotNumRups()];	// this will include any aftershock reductions
		double[] targetRateOfSection = new double[numSections];	// this will include any aftershock reductions
		double[] magOfNthRups = new double[erf.getTotNumRups()];
		int nthRup=0;
		double maxRupProb = 0;
		for(ProbEqkSource src:erf) {
			for(ProbEqkRupture rup:src) {
				if(rup.getProbability()>maxRupProb) 
					maxRupProb = rup.getProbability();
				double rate = rup.getMeanAnnualRate(erf.getTimeSpan().getDuration());
				targetRateOfNthRups[nthRup] = rate;
				magOfNthRups[nthRup] = rup.getMag();
				totalTargetRate += targetRateOfNthRups[nthRup];
				nthRupRandomSampler.set(nthRup, rate);
				if(erf.getSrcIndexForNthRup(nthRup)<erf.getNumFaultSystemSources()) {
					// participation rates
					int fltSysIndex = erf.getFltSysRupIndexForNthRup(nthRup);
					for(int sectID : fltSysRupSet.getSectionsIndicesForRup(fltSysIndex)) {
						targetRateOfSection[sectID] += rate;
					}
				}
				nthRup+=1;
			}
		}
		System.out.println("totalRate long term = "+totalTargetRate+"\nmaxRupProb = "+maxRupProb);
		info_fr.write("\ntotalTargetRate = "+totalTargetRate+"\nmaxRupProb = "+maxRupProb+"\n");


		// Make local sectIndexArrayForSrcList for faster simulations
		ArrayList<int[]> sectIndexArrayForSrcList = new ArrayList<int[]>();
		for(int s=0; s<erf.getNumFaultSystemSources();s++) {
			List<Integer> indexList = fltSysRupSet.getSectionsIndicesForRup(erf.getFltSysRupIndexForSource(s));
			int[] indexArray = new int[indexList.size()];
			for(int i=0;i<indexList.size();i++)
				indexArray[i] = indexList.get(i);
			sectIndexArrayForSrcList.add(indexArray);
		}

		// make the target MFD - 
		if(D) System.out.println("Making target MFD");
		SummedMagFreqDist targetMFD = ERF_Calculator.getTotalMFD_ForERF(erf, 5.05, 8.95, 40, true);
		double origTotMoRate = ERF_Calculator.getTotalMomentRateInRegion(erf, null);
		System.out.println("originalTotalMomentRate: "+origTotMoRate);
		targetMFD.setName("Target MFD");
		String tempString = "total rate = "+(float)targetMFD.getTotalIncrRate();
		tempString += "\ntotal rate >= 6.7 = "+(float)targetMFD.getCumRate(6.75);
		tempString += "\ntotal MoRate = "+(float)origTotMoRate;
		targetMFD.setInfo(tempString);

		// MFD for simulation
		SummedMagFreqDist obsMFD = new SummedMagFreqDist(5.05,8.95,40);
		double obsMoRate = 0;

		// set the ave cond recurrence intervals
		double[] aveCondRecurIntervalForFltSysRups;
		if(aveRecurIntervals) {
			if(aveCondRecurIntervalForFltSysRups_type1 == null)
				aveCondRecurIntervalForFltSysRups_type1 = computeAveCondRecurIntervalForFltSysRups(1);
			aveCondRecurIntervalForFltSysRups = aveCondRecurIntervalForFltSysRups_type1;
		}
		else {
			if(aveCondRecurIntervalForFltSysRups_type2 == null)
				aveCondRecurIntervalForFltSysRups_type2 = computeAveCondRecurIntervalForFltSysRups(2);
			aveCondRecurIntervalForFltSysRups = aveCondRecurIntervalForFltSysRups_type2;			
		}

		// print minimum and maximum conditional rate of rupture
		double minCondRI=Double.MAX_VALUE,maxCondRI=0;
		for(double ri: aveCondRecurIntervalForFltSysRups) {
			if(!Double.isInfinite(ri)) {
				if(ri < minCondRI) minCondRI = ri;
				if(ri > maxCondRI) maxCondRI = ri;
			}
		}
		System.out.println("minCondRI="+minCondRI);
		System.out.println("maxCondRI="+maxCondRI);

		// this is to track progress
		int percDoneThresh=0;
		int percDoneIncrement=5;

		long startRunTime = System.currentTimeMillis();

		// read section date of last file if not null
		if(inputDateOfLastFileName != null && probTypeEnum != ProbabilityModelOptions.POISSON) {
			readSectTimeSinceLastEventFromFile(inputDateOfLastFileName, origStartTimeMillis);
			info_fr.write("\nDate of Last Event from File: "+inputDateOfLastFileName+"\n");
			if (makePlots) {// this makes a plot that is not saved
				getSectNormTimeSinceLastHistPlot(origStartTimeMillis, "From File");
			}
		}
		else {
			info_fr.write("\nDate of Last Event from Fault Data in ER\n");
			if (makePlots)	{// this makes a plot that is not saved
				getSectNormTimeSinceLastHistPlot(origStartTimeMillis, "From Pref Data");
			}
		}

		
		CalcProgressBar progressBar;
		try {
			progressBar = new CalcProgressBar(plotLabelString,"Num Catalogs Generated");
			progressBar.showProgress(true);
		} catch (Throwable t) {
			// headless, don't show it
			progressBar = null;
		}
		
		// copy the original date of last
		long[] origDateOfLastForSect = dateOfLastForSect.clone();
		
		ArrayList<Double> normalizedRupRecurIntervals = new ArrayList<Double>();

		int nthCatalog =0;
		info_fr.write("numRupsAtPaleoSites\tnthCatalog\n");
		int numRups=0;
		for (nthCatalog=0;nthCatalog<numCatalogs; nthCatalog++) {

			// write progress
			if (progressBar != null) 
				progressBar.updateProgress(nthCatalog, numCatalogs);
			int percDone = (int)Math.round(100d*(double)nthCatalog/(double)numCatalogs);
			if(percDone >= percDoneThresh) {
				double timeInMin = ((double)(System.currentTimeMillis()-startRunTime)/(1000.0*60.0));
				System.out.println("\n"+percDoneThresh+"% done in "+(float)timeInMin+" minutes"+";  nthCatalog="+nthCatalog+" (out of "+numCatalogs+")\n");	
				percDoneThresh += percDoneIncrement;
			}

			// initialize things
			double currentYear=origStartYear;
			long currentTimeMillis = origStartTimeMillis;
			dateOfLastForSect = origDateOfLastForSect.clone();

			// make the simDuration one over orig target rate (exact value shouldn't matter much)
			double simDuration = 1.0/totalTargetRate;

			// start sampling of events
			numRups=0;
			int numRupsAtPaleoSites=0;
			System.out.print("\n");
			while(currentYear <= origStartYear+forecastDurationYrs) { 
				
// System.out.println(nthCatalog+"\t"+currentYear+"\t"+thisDuration);
				
				System.out.print(", "+Math.round(currentYear));

				// update gains and sampler if not Poisson
				if(probTypeEnum != ProbabilityModelOptions.POISSON) {
					// first the gains
					if(probTypeEnum == ProbabilityModelOptions.U3_BPT) {
						for(int s=0;s<erf.getNumFaultSystemSources();s++) {
							int fltSysRupIndex = erf.getFltSysRupIndexForSource(s);
							probGainForFaultSystemSource[s] = getU3_ProbGainForRup(fltSysRupIndex, 0.0, false, aveRecurIntervals, aveNormTimeSinceLast, currentTimeMillis, simDuration);
						}
					}
					else if(probTypeEnum == ProbabilityModelOptions.WG02_BPT) {
						sectionGainArray=null; // set this null so it gets updated
						for(int s=0;s<erf.getNumFaultSystemSources();s++) {
							int fltSysRupIndex = erf.getFltSysRupIndexForSource(s);
							probGainForFaultSystemSource[s] = getWG02_ProbGainForRup(fltSysRupIndex, false, currentTimeMillis, simDuration);
						}
					}		
					// now update totalRate and ruptureSampler (for all rups since start time changed)
					for(int n=0; n<erf.getTotNumRupsFromFaultSystem();n++) {
						double probGain = probGainForFaultSystemSource[erf.getSrcIndexForNthRup(n)];
						
						// following not needed since duration is low
//						double newRate;
//						if(probTypeEnum == ProbabilityModelOptions.U3_BPT) {
//							double prob = probGain*longTermRateOfFltSysRup[erf.getFltSysRupIndexForNthRup(n)]*thisDuration;	// this will crash if background seismicity included!  should create: longTermRateOfNthRups[n];
//							newRate = -Math.log(1.0-prob)/thisDuration;
//						}
//						else {
//							newRate = probGain*longTermRateOfFltSysRup[erf.getFltSysRupIndexForNthRup(n)];	// this will crash if background seismicity included!  should create: longTermRateOfNthRups[n];				//
//						}
						
						double newRate = probGain*longTermRateOfFltSysRup[erf.getFltSysRupIndexForNthRup(n)];	// this will crash if background seismicity included!  should create: longTermRateOfNthRups[n];				//
						nthRupRandomSampler.set(n, newRate);
					}
					totalTargetRate = nthRupRandomSampler.getSumOfY_vals();				
				}
				
// System.out.println("done with gain calc");


				// sample time of next event
				double timeToNextInYrs = randomDataSampler.nextExponential(1.0/totalTargetRate);
				currentYear += timeToNextInYrs;
				currentTimeMillis += (long)(timeToNextInYrs*MILLISEC_PER_YEAR);
				numRups +=1;

				if(currentYear <= origStartYear+forecastDurationYrs) {

					// System.out.println("Event time: "+eventTimeMillis+" ("+(timeToNextInYrs+timeOfNextInYrs)+" yrs)");

					// sample an event
					nthRup = nthRupRandomSampler.getRandomInt();
					int srcIndex = erf.getSrcIndexForNthRup(nthRup);
					//.			System.out.println(numRups+"\t"+currentYear+"\t"+totalRate+"\t"+timeToNextInYrs+"\t"+nthRup);

					obsRupRateArray[nthRup] += 1;

					// set that fault system event has occurred (and save normalized RI)
					if(srcIndex < erf.getNumFaultSystemSources()) {	// ignore other sources
						int fltSystRupIndex = erf.getFltSysRupIndexForSource(srcIndex);
						
						double normRI=Double.NaN;
						// compute and save the normalize recurrence interval if all sections had date of last
						if(aveNormTimeSinceLast) {	// average time since last
							normRI = getAveNormTimeSinceLastEventWhereKnown(fltSystRupIndex, currentTimeMillis);
							if(allSectionsHadDateOfLast) {
								normalizedRupRecurIntervals.add(normRI);
							}					
						}
						else {
							long aveDateOfLastMillis = getAveDateOfLastEventWhereKnown(fltSystRupIndex, currentTimeMillis);
							if(allSectionsHadDateOfLast) {
								double timeSinceLast = (currentTimeMillis-aveDateOfLastMillis)/MILLISEC_PER_YEAR;
								normRI = timeSinceLast/aveCondRecurIntervalForFltSysRups[fltSystRupIndex];
								normalizedRupRecurIntervals.add(normRI);
							}					
						}
						
						
						// reset last event time and increment simulated/obs rate on sections & check if paleo site hit
						int utilizedPaleoSite = 0;
						for(int sect:sectIndexArrayForSrcList.get(srcIndex)) {
							dateOfLastForSect[sect] = currentTimeMillis;
							obsSectRateArray[sect] += 1.0; // add the event
							if(utilizedPaleoSite == 0 && paleoConstrainSections.contains(sect))
								utilizedPaleoSite=1;
						}
						numRupsAtPaleoSites += utilizedPaleoSite;


						// write event info out
						try {
							eventFileWriter.write(nthRup+"\t"+fltSystRupIndex+"\t"+currentYear+"\t"+currentTimeMillis+"\t"+normRI+"\t"+fltSysRupSet.getMagForRup(fltSystRupIndex)+"\t"+nthCatalog+"\t"+timeToNextInYrs+"\t"+utilizedPaleoSite+"\n");
							eventFileWriter.flush();
						} catch (IOException e1) {
							e1.printStackTrace();
						}

					}
					obsMFD.addResampledMagRate(magOfNthRups[nthRup], 1.0, true);
					obsMoRate += MagUtils.magToMoment(magOfNthRups[nthRup]);
				}
			}
			info_fr.write(numRupsAtPaleoSites+"\t"+nthCatalog+"\n");
			System.out.println("\nnumRupsAtPaleoSites="+numRupsAtPaleoSites+"\n");
		}
		
		if (progressBar != null) 
			progressBar.showProgress(false);
		
		// make a stats string
		obsMFD.scale(1.0/(forecastDurationYrs*numCatalogs));
		obsMFD.setName("Simulated MFD");
		obsMoRate /= (forecastDurationYrs*numCatalogs);
		double obsTotRate = obsMFD.getTotalIncrRate();
		double rateRatio = obsTotRate/targetMFD.getTotalIncrRate();
		String statsString = "total rate = "+(float)obsTotRate+" (ratio="+(float)rateRatio+")";
		double obsTotRateAbove6pt7 = obsMFD.getCumRate(6.75);
		double rateAbove6pt7_Ratio = obsTotRateAbove6pt7/targetMFD.getCumRate(6.75);
		statsString += "\ntotal rate >= 6.7 = "+(float)obsTotRateAbove6pt7+" (ratio="+(float)rateAbove6pt7_Ratio+")";
		double moRateRatio = obsMoRate/origTotMoRate;
		statsString += "\ntotal MoRate = "+(float)obsMoRate+" (ratio="+(float)moRateRatio+")";
		obsMFD.setInfo(statsString);
		
		// write stats to info file
		info_fr.write("\nSimulation Stats:\n"+ statsString+"\n\n");

		
		// close the events file
		try {
			eventFileWriter.close();
			info_fr.close();
		} catch (IOException e2) {
			e2.printStackTrace();
		}

		if(makePlots) {

			// make normalized rup recurrence interval plots
			double aper=Double.NaN;
			if(numAperValues==1)
				aper=aperValues[0];	// only one value, so include in plot for al ruptures
			ArrayList<EvenlyDiscretizedFunc> funcList = ProbModelsPlottingUtils.getNormRI_DistributionWithFits(normalizedRupRecurIntervals, aper);
			GraphWindow normRI_graph = ProbModelsPlottingUtils.plotNormRI_DistributionWithFits(funcList, "Normalized Rupture RIs; "+plotLabelString);	


			// plot MFDs
			ArrayList<EvenlyDiscretizedFunc> funcs = new ArrayList<EvenlyDiscretizedFunc>();
			funcs.add(targetMFD);
			funcs.add(obsMFD);
			funcs.add(targetMFD.getCumRateDistWithOffset());
			funcs.add(obsMFD.getCumRateDistWithOffset());
			GraphWindow mfd_graph = new GraphWindow(funcs, "Mag-Freq Dists; "+plotLabelString); 
			mfd_graph.setX_AxisLabel("Mag");
			mfd_graph.setY_AxisLabel("Rate");
			mfd_graph.setYLog(true);	// this causes problems
			mfd_graph.setY_AxisRange(1e-4, 1.0);
			mfd_graph.setX_AxisRange(5.5, 8.5);

//			// plot observed versus imposed rup rates - Is this really meaningful?
//			for(int i=0;i<obsRupRateArray.length;i++) {
//				obsRupRateArray[i] = obsRupRateArray[i]/(forecastDurationYrs*numCatalogs);
//			}
//			DefaultXY_DataSet obsVsImposedRupRates = new DefaultXY_DataSet(targetRateOfNthRups,obsRupRateArray);
//			obsVsImposedRupRates.setName("Simulated vs Imposed Rup Rates");
//			DefaultXY_DataSet perfectAgreementFunc4 = new DefaultXY_DataSet();
//			perfectAgreementFunc4.set(1e-5,1e-5);
//			perfectAgreementFunc4.set(0.05,0.05);
//			perfectAgreementFunc4.setName("Perfect agreement line");
//			ArrayList<DefaultXY_DataSet> funcs4 = new ArrayList<DefaultXY_DataSet>();
//			funcs4.add(obsVsImposedRupRates);
//			funcs4.add(perfectAgreementFunc4);
//			ArrayList<PlotCurveCharacterstics> plotChars4 = new ArrayList<PlotCurveCharacterstics>();
//			plotChars4.add(new PlotCurveCharacterstics(PlotSymbol.CROSS, 4f, Color.BLUE));
//			plotChars4.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.RED));
//			GraphWindow rupRates_graph = new GraphWindow(funcs4, "Obs vs Imposed Rup Rates; "+plotLabelString, plotChars4); 
//			//		graph4.setX_AxisRange(5d/(forecastDurationYrs*numCatalogs), 0.01);
//			//		graph4.setY_AxisRange(5d/(forecastDurationYrs*numCatalogs), 0.01);
//			rupRates_graph.setYLog(true);
//			rupRates_graph.setXLog(true);
//			rupRates_graph.setX_AxisLabel("Target Rup Rate (per yr)");
//			rupRates_graph.setY_AxisLabel("Simulated Rup Rate (per yr)");

			// plot observed versus target section rates
			for(int i=0;i<obsSectRateArray.length;i++) {
				obsSectRateArray[i] = obsSectRateArray[i]/(forecastDurationYrs*numCatalogs);
			}
			DefaultXY_DataSet obsVsImposedSectRates = new DefaultXY_DataSet(targetRateOfSection,obsSectRateArray);
			obsVsImposedSectRates.setName("Simulated vs Target Section Event Rates");
			double rateTrans = 1.0/forecastDurationYrs;
			obsVsImposedSectRates.setInfo("Simulated rates should be above target for rates above ~"+(float)rateTrans+ " due to second events in simulation");
			DefaultXY_DataSet perfectAgreementFunc = new DefaultXY_DataSet();
			perfectAgreementFunc.set(1e-5,1e-5);
			perfectAgreementFunc.set(0.05,0.05);
			perfectAgreementFunc.setName("Perfect agreement line");
			ArrayList<DefaultXY_DataSet> funcs2 = new ArrayList<DefaultXY_DataSet>();
			funcs2.add(obsVsImposedSectRates);
			funcs2.add(perfectAgreementFunc);
			ArrayList<PlotCurveCharacterstics> plotChars = new ArrayList<PlotCurveCharacterstics>();
			plotChars.add(new PlotCurveCharacterstics(PlotSymbol.CROSS, 4f, Color.BLUE));
			plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.RED));
			GraphWindow sectRates_graph = new GraphWindow(funcs2, "Obs vs Target Section Rates; ", plotChars); 
			//		graph2.setX_AxisRange(5d/(forecastDurationYrs*numCatalogs), 0.05);
			//		graph2.setY_AxisRange(5d/(forecastDurationYrs*numCatalogs), 0.05);
			sectRates_graph.setYLog(true);
			sectRates_graph.setXLog(true);
			sectRates_graph.setX_AxisLabel("Target Participation Rate (per yr)");
			sectRates_graph.setY_AxisLabel("Simulated Participation Rate (per yr)");

			// write section rates with names
			FileWriter eventRates_fr;
			try {
				eventRates_fr = new FileWriter(new File(resultsDir,"obsVsTargetSectionPartRates.txt"));
				eventRates_fr.write("sectID\tlongTermRate\ttargetRate\tsimulatedRate\tsimOverTargetRateRatio\taveSectGain\thasDateOfLast\tnormTimeSince\tsectName\n");
				for(int i=0;i<fltSysRupSet.getNumSections();i++) {
					FaultSectionPrefData fltData = fltSysRupSet.getFaultSectionData(i);
					double ratio = obsSectRateArray[i]/targetRateOfSection[i];
					boolean hasDateOfLast=false;
					if(fltData.getDateOfLastEvent() != Long.MIN_VALUE)
						hasDateOfLast=true;
					double normTimeSince;
					if(hasDateOfLast)
						normTimeSince = (((double)(origStartTimeMillis-origDateOfLastForSect[i]))/MILLISEC_PER_YEAR)*longTermPartRateForSectArray[i];
					else
						normTimeSince = Double.NaN;
					eventRates_fr.write(fltData.getSectionId()+"\t"+longTermPartRateForSectArray[i]+"\t"+targetRateOfSection[i]+"\t"+
							obsSectRateArray[i]+"\t"+ratio+"\t"+(targetRateOfSection[i]/longTermPartRateForSectArray[i])+"\t"+hasDateOfLast+"\t"+normTimeSince+"\t"+fltData.getName()+"\n");
				}
				eventRates_fr.close();
			} catch (IOException e1) {
				e1.printStackTrace();
			}


			String dirNameForSavingFiles = resultsDir.getPath();
			try {
				// plots
				normRI_graph.saveAsPDF(dirNameForSavingFiles+"/normalizedRupRecurIntervals.pdf");
				normRI_graph.saveAsTXT(dirNameForSavingFiles+"/normalizedRupRecurIntervalsPlot.txt");
				mfd_graph.saveAsPDF(dirNameForSavingFiles+"/magFreqDists.pdf");
				sectRates_graph.saveAsPDF(dirNameForSavingFiles+"/obsVsTargetSectionPartRates.pdf");
//				rupRates_graph.saveAsPDF(dirNameForSavingFiles+"/obsVsTargetRupRates.pdf");
//				rupRates_graph.saveAsTXT(dirNameForSavingFiles+"/obsVsTargetRupRates.txt");
				AbstractDiscretizedFunc.writeSimpleFuncFile(targetMFD, dirNameForSavingFiles+"/targetMFD.txt");
				AbstractDiscretizedFunc.writeSimpleFuncFile(obsMFD, dirNameForSavingFiles+"/simulatedMFD.txt");
				AbstractDiscretizedFunc.writeSimpleFuncFile(targetMFD.getCumRateDistWithOffset(), dirNameForSavingFiles+"/targetCumMFD.txt");
				AbstractDiscretizedFunc.writeSimpleFuncFile(obsMFD.getCumRateDistWithOffset(), dirNameForSavingFiles+"/simulatedCumMFD.txt");
			} catch (IOException e) {
				e.printStackTrace();
			}

		}
	}

	
	
	
	
	private void writeSectTimeSinceLastEventToFile(String fileName, long currentTimeMillis) {		
		if(!dataDir.exists())
			dataDir.mkdir();
		File dataFile = new File(dataDir,File.separator+fileName);
		try {
			FileWriter fileWriter = new FileWriter(dataFile);
			int numBad=0;
			for(int i=0; i<dateOfLastForSect.length;i++) {
				// time since last millis
				if(dateOfLastForSect[i] != Long.MIN_VALUE) {
					long timeSince = currentTimeMillis-dateOfLastForSect[i];	// ti
					if(timeSince < 0) {
						if(timeSince > -MILLISEC_PER_YEAR) {
							System.out.println("Converting slightly negative time since last ("+timeSince+") to zero");
							timeSince=0;
						}
						else {
							throw new RuntimeException("bad time since last");
						}
					}
					fileWriter.write(i+"\t"+timeSince+"\n");					
				}
				else {
					fileWriter.write(i+"\t"+Long.MIN_VALUE+"\n");
					numBad+=1;
				}
			}
			fileWriter.close();
			int percBad = (int)Math.round(100.0*(double)numBad/(double)dateOfLastForSect.length);
			System.out.println(numBad+" sections out of "+dateOfLastForSect.length+" had no date of last event in output file ("+percBad+"%)");
			
		}catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	
	
	private GraphWindow getSectNormTimeSinceLastHistPlot(long currentTimeMillis, String labelPrefix) {
		
		ArrayList<Double> normRI_List= new ArrayList<Double>();

		for(int s=0; s<dateOfLastForSect.length;s++) {
			long epochOfLast = dateOfLastForSect[s];
			if(epochOfLast != Long.MIN_VALUE) {
				double yearsSinceLast = ((double)(currentTimeMillis-epochOfLast)/MILLISEC_PER_YEAR);
				double normTimeSinceLast = longTermPartRateForSectArray[s]*yearsSinceLast;
				normRI_List.add(normTimeSinceLast);
//				System.out.println(longTermPartRateForSectArray[s]+"\t"+yearsSinceLast);
			}
		}
		
//		System.out.println("normRI_List.size()="+normRI_List.size());
		HistogramFunction dist = ProbModelsPlottingUtils.getNormRI_Distribution(normRI_List, 0.1);
		dist.setName(labelPrefix+" NormSectTimeSinceLast");
		dist.setInfo(normRI_List.size()+" of "+dateOfLastForSect.length+" sections had date of last");
		GraphWindow graph = new GraphWindow(dist, labelPrefix+" NormSectTimeSinceLast"); 
		return graph;
//		System.exit(0);
	}
	
	/**
	 * This returns an array of normalized time since last event for each section, with
	 * a value of Double.NaN if it's unknown
	 * @param currentTimeMillis
	 * @return
	 */
	public double[] getNormTimeSinceLastForSections(long currentTimeMillis) {
		double[] normTimeSince = new double[numSections];
		for(int s=0; s<numSections;s++) {
			long epochOfLast = dateOfLastForSect[s];
			if(epochOfLast != Long.MIN_VALUE) {
				double yearsSinceLast = ((double)(currentTimeMillis-epochOfLast)/MILLISEC_PER_YEAR);
				normTimeSince[s] = longTermPartRateForSectArray[s]*yearsSinceLast;
			}
			else {
				normTimeSince[s] = Double.NaN;
			}	
		}
		return normTimeSince;
	}
	
	
	private void readSectTimeSinceLastEventFromFile(String fileName, long currentTimeMillis) {
		
		try {
			File dataFile = new File(dataDir,File.separator+fileName);
			
			System.out.println("Reading file "+fileName+"; currentTimeMillis+"+currentTimeMillis);
			
			BufferedReader reader = new BufferedReader(scratch.UCERF3.utils.UCERF3_DataUtils.getReader(dataFile.toURL()));
//			BufferedReader reader = new BufferedReader(scratch.UCERF3.utils.UCERF3_DataUtils.getReader(dataFile.getAbsolutePath()));
			int s=0;
			String line;
			int numBad=0;
			while ((line = reader.readLine()) != null) {
				String[] st = StringUtils.split(line,"\t");
				int sectIndex = Integer.valueOf(st[0]);
				long timeSince = Long.valueOf(st[1]);
				if(timeSince != Long.MIN_VALUE) {
					dateOfLastForSect[s] = currentTimeMillis-timeSince;
//					dateOfLastForSect[s] = Long.MIN_VALUE;
				}
				else {
					dateOfLastForSect[s] = Long.MIN_VALUE;
					numBad +=1;
				}
				if(s != sectIndex)
					throw new RuntimeException("bad index");
				s+=1;

			}
			int percBad = (int)Math.round(100.0*(double)numBad/(double)dateOfLastForSect.length);
			System.out.println(numBad+" sections out of "+dateOfLastForSect.length+" had no date of last event in input file ("+percBad+"%)");

		} catch (Exception e) {
			ExceptionUtils.throwAsRuntimeException(e);
		}
	}

	
	
	
	
	/**
	 * This writes out the rupture probability gains for the 4 different averaging 
	 * combinations to a file, along with other info about the rupture
	 * @param presentTimeMillis
	 * @param durationYears
	 * @param fileName
	 */
	public void writeRupProbGainsForDiffAveragingMethods(long presentTimeMillis, double durationYears, String fileName) {
		writeRupProbGainsForDiffAveragingMethods(presentTimeMillis, durationYears, fileName, -1);
	}

	
	
	/**
	 * This writes out the rupture probability gains for the 4 different averaging 
	 * combinations to a file, along with other info about the rupture
	 * @param presentTimeMillis
	 * @param durationYears
	 * @param fileName
	 * @param subSectIndex - only include ruptures that utilize this subsection (set as -1 for all subsections)
	 */
	public void writeRupProbGainsForDiffAveragingMethods(long presentTimeMillis, double durationYears, String fileName, int subSectIndex) {
		boolean[] aveRI_array = {true,false};
		boolean[] aveNormTS_array = {true,false};
		File dataFile = new File(fileName);
		FileWriter fileWriter;	
		
		String sectName = fltSysRupSet.getFaultSectionData(subSectIndex).getName();
		System.out.println("Working on ruptures for section "+subSectIndex+"; "+sectName);

		
		// make list of ruptures that use the given subsection
		List<Integer> rupIndexList = null;
		if(subSectIndex != -1)
			rupIndexList = fltSysRupSet.getRupturesForSection(subSectIndex);
		
		try {
			fileWriter = new FileWriter(dataFile);
			fileWriter.write("rupIndex\tRI_NTS\tRI_TS\tRateNTS\tRateTS\tcondRI_RI\tcondRI_Rate\tlongTermRate\tmaxOverMin\tMaxMinusMin\tsigDiff\trupMag\trupAper\trupName\n");
			for(int fltSystRupIndex=0; fltSystRupIndex<numRupsInFaultSystem;fltSystRupIndex++) {
				if(longTermRateOfFltSysRup[fltSystRupIndex]>0.0) {
					String line = Integer.toString(fltSystRupIndex);
					// TODO add long term rate and condRI & header line
					double min=Double.MAX_VALUE, max=-1;
					for(boolean aveRI:aveRI_array) {
						for(boolean aveNormTS:aveNormTS_array) {
							double gain = this.getU3_ProbGainForRup(fltSystRupIndex, 0.0, false, aveRI, aveNormTS, presentTimeMillis, durationYears);
							line += "\t"+gain;
							if(min>gain) min=gain;
							if(max<gain) max=gain;
						}
					}
					List<FaultSectionPrefData> data = fltSysRupSet.getFaultSectionDataForRupture(fltSystRupIndex);
					String name = data.size()+" SECTIONS BETWEEN "+data.get(0).getName()+" AND "+data.get(data.size()-1).getName();
					boolean sigDiff = (max/min > 1.1 && max-min > 0.1);
					double rupMag=fltSysRupSet.getMagForRup(fltSystRupIndex);
					line += "\t"+aveCondRecurIntervalForFltSysRups_type1[fltSystRupIndex]+
							"\t"+aveCondRecurIntervalForFltSysRups_type2[fltSystRupIndex]+
							"\t"+longTermRateOfFltSysRup[fltSystRupIndex]+
							"\t"+(max/min)+"\t"+(max-min)+"\t"+sigDiff+
							"\t"+rupMag+"\t"+aperValues[getAperIndexForRupMag(rupMag)]+
							"\t"+name;
					if(subSectIndex == -1)
						fileWriter.write(line+"\n");
					else if (rupIndexList.contains(fltSystRupIndex))
						fileWriter.write(line+"\n");
				}
			}
			fileWriter.close();

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println("Done with writeRupProbGainsForDiffAveragingMethods for "+fileName);
	}
	
	
	
	public String getInfoAboutRupture(int fltSystRupIndex, long presentTimeMillis) {
		String info = "fltSystRupIndex="+fltSystRupIndex+"\n";
		info += "mag="+fltSysRupSet.getMagForRup(fltSystRupIndex)+"\n";
		info += "Index\tRI\tRate\tTimeSince\tNormTimeSince\tArea\tName\n";
		List<Integer> sectIndicesList = fltSysRupSet.getSectionsIndicesForRup(fltSystRupIndex);
		for(int sectIndex:sectIndicesList) {
			FaultSectionPrefData fltData = fltSysRupSet.getFaultSectionData(sectIndex);
			long dateOfLastMillis = fltData.getDateOfLastEvent();
			double yrsSinceLast = Double.NaN;
			if(dateOfLastMillis != Long.MIN_VALUE)
				yrsSinceLast = ((double)(presentTimeMillis-dateOfLastMillis))/MILLISEC_PER_YEAR;
			info+=sectIndex+"\t"+(1.0/longTermPartRateForSectArray[sectIndex])+"\t"+longTermPartRateForSectArray[sectIndex]+"\t"+
			yrsSinceLast+"\t"+yrsSinceLast*longTermPartRateForSectArray[sectIndex]+"\t"+sectionArea[sectIndex]+"\t"+fltData.getName()+"\n";
		}
		return info;
	}
	
	
	public String getInfoAboutRupsOnSection(int sectID, String fileName) {
		String info= new String();
		
		List<Integer> rupIDsList = fltSysRupSet.getRupturesForSection(sectID);
		for(int rupID:rupIDsList) {
			info += rupID+"\t"+longTermRateOfFltSysRup[rupID]+"\t"+fltSysRupSet.getMagForRup(rupID)+"\n";
		}
//		File dataFile = new File(dataDir,File.separator+fileName);
		try {
			FileWriter fileWriter = new FileWriter(fileName);
			fileWriter.write(info);					
			fileWriter.close();
		}catch(Exception e) {
			e.printStackTrace();
		}
		return info;
	}
	
	
	/**
	 * Two segment/section example, where zeroeth rupture only uses section 0, and 1st rup uses both section 0 and 1.
	 * 
	 * @param numSimulatedRups
	 */
	public static void simpleModelTest(int numSimulatedRups) {
		double aper = 0.2;
		
		int[] sampledRupArray = new int[numSimulatedRups];
		double[] yearOfSampledRupArray = new double[numSimulatedRups];
				
		double rup1_RI = 50;
		double rup2_RI = 200;
		double[] longTermRupRateArray = {1.0/rup1_RI,1.0/rup2_RI};
		double[] longTermSectRateArray = new double[2];
		longTermSectRateArray[0] = 1.0/rup1_RI + 1.0/rup2_RI;
		longTermSectRateArray[1] = 1.0/rup2_RI;
		double[] sectYearOfLastArray = {-1.0/longTermSectRateArray[0], -1.0/longTermSectRateArray[1]};	// start of one sect RI since last

		
		double[] condRI_Array = new double[2];
		condRI_Array[0] = 1.0/longTermSectRateArray[0];
		condRI_Array[1] = (1.0/longTermSectRateArray[0]+1.0/longTermSectRateArray[1])/2.0;	// ave section RIs
		// To test alternative cond RI calculation:
//		condRI_Array[1] = 1.0/((longTermSectRateArray[0]+longTermSectRateArray[1])/2.0);	// ave section RIs
		
		System.out.println("longTermRupRateArray: "+longTermRupRateArray[0]+", "+longTermRupRateArray[1]);
		System.out.println("longTermSectRateArray: "+longTermSectRateArray[0]+", "+longTermSectRateArray[1]);
		System.out.println("condRI_Array: "+condRI_Array[0]+", "+condRI_Array[1]);

		double[] aveNormTimeSinceArray = new double[2];
		double[] condProbArray = new double[2];
		double[] tdRupRateArray = new double[2];
		double year = 0;
		int nthRup = 0;
		int num0thRups=0;
		
		CalcProgressBar progressBar = new CalcProgressBar("simpleModelTest","Num Rups Done");
		progressBar.showProgress(true);

		// For BPT
		BPT_DistCalc bptRefCalc = getRef_BPT_DistCalc(aper);
		
//		// For lognormal or weibull:
//		int numPts = (int)Math.round((9*refRI)/deltaT);
//		LognormalDistCalc bptRefCalc = new LognormalDistCalc();
////		WeibullDistCalc bptRefCalc = new WeibullDistCalc();
//		bptRefCalc.setAll(refRI, aper, deltaT, numPts);

		
		
		
		double stepDurationYears = 1.0;
		
		IntegerPDF_FunctionSampler nthRupRandomSampler = new IntegerPDF_FunctionSampler(2);
		RandomDataGenerator randomDataSampler = new RandomDataGenerator();
		
		ArrayList<Double> normalizedRupRecurIntervals = new ArrayList<Double>();
		ArrayList<Double> normalizedSectRecurIntervals = new ArrayList<Double>();

		while(nthRup<numSimulatedRups) {

			progressBar.updateProgress(nthRup, numSimulatedRups);

			aveNormTimeSinceArray[0] = (year-sectYearOfLastArray[0])*longTermSectRateArray[0];
			aveNormTimeSinceArray[1] = ((year-sectYearOfLastArray[0])*longTermSectRateArray[0]+(year-sectYearOfLastArray[1])*longTermSectRateArray[1])/2;
			condProbArray[0] = bptRefCalc.getCondProb(aveNormTimeSinceArray[0], stepDurationYears*refRI/condRI_Array[0]);
			condProbArray[1] = bptRefCalc.getCondProb(aveNormTimeSinceArray[1], stepDurationYears*refRI/condRI_Array[1]);
			tdRupRateArray[0] = (condProbArray[0]/(stepDurationYears/condRI_Array[0])) * (1.0/rup1_RI);	// (gain)*(longTermRate)
			tdRupRateArray[1] = (condProbArray[1]/(stepDurationYears/condRI_Array[1])) * (1.0/rup2_RI);	// (gain)*(longTermRate)
			
			if(year<10000)
				System.out.println((condProbArray[0]/(stepDurationYears/condRI_Array[0]))+"\t"+(condProbArray[1]/(stepDurationYears/condRI_Array[1])));

//			 Poisson test
//			tdRupRateArray[0] = (1.0/rup1_RI);	// (gain=1)*(longTermRate)
//			tdRupRateArray[1] = (1.0/rup2_RI);	// (gain=1)*(longTermRate)

			nthRupRandomSampler.set(0,tdRupRateArray[0]);
			nthRupRandomSampler.set(1,tdRupRateArray[1]);

			double totRate = tdRupRateArray[0]+tdRupRateArray[1];

			double timeToNextInYrs = randomDataSampler.nextExponential(1.0/totRate);

			if(timeToNextInYrs<stepDurationYears) {
				// sample a rup
				int r = nthRupRandomSampler.getRandomInt();
				sampledRupArray[nthRup]=r;
				yearOfSampledRupArray[nthRup]=year+timeToNextInYrs;
				// reset date of last event on sections
				if(r==0) {
					normalizedRupRecurIntervals.add(aveNormTimeSinceArray[0]);
					normalizedSectRecurIntervals.add((year-sectYearOfLastArray[0])*longTermSectRateArray[0]);
					sectYearOfLastArray[0] = year+timeToNextInYrs;
					num0thRups+=1;
				}
				else {
					normalizedRupRecurIntervals.add(aveNormTimeSinceArray[1]);
					normalizedSectRecurIntervals.add((year-sectYearOfLastArray[0])*longTermSectRateArray[0]);
					normalizedSectRecurIntervals.add((year-sectYearOfLastArray[1])*longTermSectRateArray[1]);
					sectYearOfLastArray[0] = year+timeToNextInYrs;
					sectYearOfLastArray[1] = year+timeToNextInYrs;
				}
				year += timeToNextInYrs;
				nthRup+=1;
			}
			else {
				year += stepDurationYears;
			}
		}
		
		double simRate = numSimulatedRups/year;
		double expectedRate = (longTermRupRateArray[0]+longTermRupRateArray[1]);
		
		System.out.println("simulated rate = "+simRate+"\t("+(simRate/expectedRate)+")");
		System.out.println("expected rate = "+expectedRate);
		System.out.println("simulated rate 0th rup = "+(num0thRups/year)+"\t("+((num0thRups/year)/longTermRupRateArray[0])+")");
		System.out.println("expected rate 0th rup = "+longTermRupRateArray[0]);
		System.out.println("simulated rate 1st rup = "+((numSimulatedRups-num0thRups)/year)+"\t("+(((numSimulatedRups-num0thRups)/year)/longTermRupRateArray[1])+")");
		System.out.println("expected rate 1st rup = "+longTermRupRateArray[1]);
		
		ArrayList<EvenlyDiscretizedFunc> funcList = ProbModelsPlottingUtils.getNormRI_DistributionWithFits(normalizedRupRecurIntervals, aper);
		GraphWindow grapha_a = ProbModelsPlottingUtils.plotNormRI_DistributionWithFits(funcList, "Normalized Rupture RIs; "+"simple problem");
		String infoString="";
		infoString += "\nRup "+funcList.get(0).getName()+":";
		infoString += "\n"+funcList.get(0).getInfo();
		
		ArrayList<EvenlyDiscretizedFunc> funcListSect = ProbModelsPlottingUtils.getNormRI_DistributionWithFits(normalizedSectRecurIntervals, aper);
		GraphWindow grapha_b = ProbModelsPlottingUtils.plotNormRI_DistributionWithFits(funcListSect, "Normalized Section RIs; "+"simple problem");
		infoString += "\n\nSect "+funcListSect.get(0).getName()+":";
		infoString += "\n"+funcListSect.get(0).getInfo();

		System.out.println("infoString: \n"+infoString);

		progressBar.dispose();

	}
	
	
	/**
	 * This plot a histogram of the ratio of aveRate vs aveRI conditional RIs, over all ruptures, where each is weighted by their long-term rate.
	 */
	public void plotRatioHistOfRupCondProbs(){
		aveCondRecurIntervalForFltSysRups_type1 = computeAveCondRecurIntervalForFltSysRups(1);
		aveCondRecurIntervalForFltSysRups_type2 = computeAveCondRecurIntervalForFltSysRups(2);
		HistogramFunction hist = new HistogramFunction(0.0, 20, 0.1);
		for(int r=0;r<aveCondRecurIntervalForFltSysRups_type1.length;r++) {
			double ratio = aveCondRecurIntervalForFltSysRups_type2[r]/aveCondRecurIntervalForFltSysRups_type1[r];
			if(ratio < hist.getMaxX()+hist.getDelta()/2)
				hist.add(ratio, longTermRateOfFltSysRup[r]);
			else
				System.out.println(ratio);
		}
		hist.setInfo("mean="+(float)hist.computeMean());
		GraphWindow graphMagHist = new GraphWindow(hist, "Ratio of aveRate to aveRI Cond RI values"); 


	}
	
	/**
	 * This sets values in dateOfLastForSect for the given rupture and date. 
	 * @param fltSysRupIndex
	 * @param epoch
	 */
	public void setFltSystemRupOccurranceTime(int fltSysRupIndex, Long epoch) {
//System.out.println("setFltSystemRupOccurranceTime was called for fltSysRupIndex="+fltSysRupIndex+";  epoch="+epoch);
		for(int sectIndex : fltSysRupSet.getSectionsIndicesForRup(fltSysRupIndex)) {
			dateOfLastForSect[sectIndex] = epoch;
		}
	}

	
	public void setFltSectRupOccurranceTime(int sectIndex, Long epoch) {
					dateOfLastForSect[sectIndex] = epoch;
			}

	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		
//		simpleModelTest(1000000);

//		// THIS CODE WAS RUN ON JAN 30
//		TestModel3_FSS testFSS = new TestModel3_FSS();	// this one is perfectly segmented
//		for(FaultSectionPrefData fltData : testFSS.getRupSet().getFaultSectionDataList())
//			fltData.setDateOfLastEvent(-Math.round(270*MILLISEC_PER_YEAR));
//		FaultSystemSolutionERF erf = new FaultSystemSolutionERF(testFSS);
//		erf.getParameter(IncludeBackgroundParam.NAME).setValue(IncludeBackgroundOption.EXCLUDE);
//		erf.getParameter(ProbabilityModelParam.NAME).setValue(ProbabilityModelOptions.U3_BPT);
////		erf.getParameter(ProbabilityModelParam.NAME).setValue(ProbabilityModelOptions.POISSON);
//		erf.getParameter(MagDependentAperiodicityParam.NAME).setValue(MagDependentAperiodicityOptions.LOW_VALUES);
////		BPTAveragingTypeOptions aveType = BPTAveragingTypeOptions.AVE_RI_AVE_TIME_SINCE;
//		BPTAveragingTypeOptions aveType = BPTAveragingTypeOptions.AVE_RI_AVE_NORM_TIME_SINCE;
////		BPTAveragingTypeOptions aveType = BPTAveragingTypeOptions.AVE_RATE_AVE_NORM_TIME_SINCE;
//		erf.setParameter(BPTAveragingTypeParam.NAME, aveType);
//		erf.updateForecast();
//		ProbabilityModelsCalc testCalc = new ProbabilityModelsCalc(erf);
//		testCalc.testER_Simulation(null, null, erf,1000000d, "SimpleFaultTestRun1_Jan30");
////		testCalc.testER_Next50yrSimulation(erf, "SimpleFaultTest_Next50yrSimBPT_100yrTestGainFix", null, 5000);
//
//		System.exit(0);
		
		
		
		// THIS CODE WAS RUN ON DEC 18
//		TestModel2_FSS testFSS = new TestModel2_FSS();
//		for(FaultSectionPrefData fltData : testFSS.getRupSet().getFaultSectionDataList())
//			fltData.setDateOfLastEvent(-Math.round(270*MILLISEC_PER_YEAR));
//		FaultSystemSolutionERF erf = new FaultSystemSolutionERF(testFSS);
//		erf.getParameter(IncludeBackgroundParam.NAME).setValue(IncludeBackgroundOption.EXCLUDE);
//		erf.getParameter(ProbabilityModelParam.NAME).setValue(ProbabilityModelOptions.U3_BPT);
////		erf.getParameter(ProbabilityModelParam.NAME).setValue(ProbabilityModelOptions.POISSON);
//		erf.getParameter(MagDependentAperiodicityParam.NAME).setValue(MagDependentAperiodicityOptions.MID_VALUES);
////		BPTAveragingTypeOptions aveType = BPTAveragingTypeOptions.AVE_RI_AVE_TIME_SINCE;
////		BPTAveragingTypeOptions aveType = BPTAveragingTypeOptions.AVE_RI_AVE_NORM_TIME_SINCE;
//		BPTAveragingTypeOptions aveType = BPTAveragingTypeOptions.AVE_RATE_AVE_NORM_TIME_SINCE;
//		erf.setParameter(BPTAveragingTypeParam.NAME, aveType);
//		erf.updateForecast();
//		ProbabilityModelsCalc testCalc = new ProbabilityModelsCalc(erf);
//		testCalc.testER_Simulation(null, null, erf,100000000d, "SimpleFaultTestRun1_Dec18_Alt9");
////		testCalc.testER_Next50yrSimulation(erf, "SimpleFaultTest_Next50yrSimBPT_100yrTestGainFix", null, 5000);
		

		String fileName="dev/scratch/UCERF3/data/scratch/InversionSolutions/2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_1_MEAN_BRANCH_AVG_SOL.zip";
		FaultSystemSolutionERF erf = new FaultSystemSolutionERF(fileName);
		erf.getParameter(IncludeBackgroundParam.NAME).setValue(IncludeBackgroundOption.EXCLUDE);
		
		

		
//		erf.getParameter(ProbabilityModelParam.NAME).setValue(ProbabilityModelOptions.POISSON);
//		erf.updateForecast();
//		ProbabilityModelsCalc testCalc = new ProbabilityModelsCalc(erf);
////		testCalc.testER_Simulation(null, null, erf,200000d);
//		testCalc.testER_Next50yrSimulation(erf, "TestER_Next50yrSimulation", 1000000);
//
//		String timeSinceLastFileNamePois = "timeSinceLastForSimulationPois.txt";
		String timeSinceLastFileName = "timeSinceLastForSimulation.txt";
		erf.getParameter(ProbabilityModelParam.NAME).setValue(ProbabilityModelOptions.U3_BPT);
		erf.getParameter(MagDependentAperiodicityParam.NAME).setValue(MagDependentAperiodicityOptions.MID_VALUES);
//		erf.getParameter(MagDependentAperiodicityParam.NAME).setValue(MagDependentAperiodicityOptions.ALL_PT2_VALUES);
//		BPTAveragingTypeOptions aveType = BPTAveragingTypeOptions.AVE_RI_AVE_TIME_SINCE;
		BPTAveragingTypeOptions aveType = BPTAveragingTypeOptions.AVE_RI_AVE_NORM_TIME_SINCE;
//		BPTAveragingTypeOptions aveType = BPTAveragingTypeOptions.AVE_RATE_AVE_NORM_TIME_SINCE;
		erf.setParameter(BPTAveragingTypeParam.NAME, aveType);
//		
//		erf.getParameter(HistoricOpenIntervalParam.NAME).setValue(2014d-1850d);	

//		erf.getTimeSpan().setStartTime(1910);
//		System.out.println("startYear: "+erf.getTimeSpan().getStartTimeYear());
//		erf.eraseDatesOfLastEventAfterStartTime();
//		erf.getTimeSpan().setDuration(100);

		erf.updateForecast();
		
		
		
//		// Write section info for Pitman canyon paleo site
//		Location loc = new Location(34.2544,-117.4340, 0.0);
//		double minDist = Double.MAX_VALUE;
//		int minIndex=-1;
//		int index = 0;
//		for(FaultSectionPrefData data : erf.getSolution().getRupSet().getFaultSectionDataList()) {
//			double dist = data.getStirlingGriddedSurface(1.0).getDistanceJB(loc);
//			if(dist<minDist) {
//				minDist = dist;
//				minIndex = index;
//			}
//			index+=1;
//		}
//		System.out.println("Pitman Canyon section: "+minIndex+"\t"+erf.getSolution().getRupSet().getFaultSectionData(minIndex).getName());
//		System.exit(-1);
		
		// TODO This could be obtained from the ERF, but the ERF would have to use the ProbabilityModelsCalc constructor that takes the ERF 
		ProbabilityModelsCalc testCalc = new ProbabilityModelsCalc(erf);
		
//		testCalc.plotRatioHistOfRupCondProbs();

		testCalc.testER_Simulation(timeSinceLastFileName, null, erf, 20000d, "TestRun_120915");

//		try {
//			testCalc.testER_NextXyrSimulation(new File("TestSim_8"), null, 10, true, null);
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}

		
//		testCalc.testER_SimulationOnParentSection(timeSinceLastFileName, null, erf,60000000d, "CerroP",172);
		
		// Biggest prob diff between viable approaches on Imperial section 0
//		System.out.println(testCalc.getInfoAboutRupture(198219, erf.getTimeSpan().getStartTimeInMillis()));
		
//		// Biggest prob diff between viable approaches on Mojave sections
//		System.out.println(testCalc.getInfoAboutRupture(884, erf.getTimeSpan().getStartTimeInMillis()));
		
		// Biggest prob diff between viable approaches on Owens Valley sections
//		System.out.println(testCalc.getInfoAboutRupture(249574, erf.getTimeSpan().getStartTimeInMillis()));

		// Biggest prob diff between RI_TS and RI_NTS on "San Andreas (Mojave S), Subsection 3"	1840
//		System.out.println(testCalc.getInfoAboutRupture(194929, erf.getTimeSpan().getStartTimeInMillis()));
		
		// Biggest diff between RI_TS and RI_NTS on "White Wolf, subsection 0" 2568
//		System.out.println(testCalc.getInfoAboutRupture(248296, erf.getTimeSpan().getStartTimeInMillis()));
		
		// NSAF Offshore:
//		System.out.println(testCalc.getInfoAboutRupture(160395, erf.getTimeSpan().getStartTimeInMillis()));

		



		
//		testCalc.writeInfoForRupsOnSect(1886);
		
//		System.out.println(testCalc.getInfoAboutRupsOnSection(295, "tempRupOnSectInfo.txt"));
		

		
//		// Biggest gain ratio diff between viable approaches
//		System.out.println(testCalc.getInfoAboutRupture(250420, erf.getTimeSpan().getStartTimeInMillis()));
//		
//		// Biggest gain diff between viable approaches
//		System.out.println(testCalc.getInfoAboutRupture(240628, erf.getTimeSpan().getStartTimeInMillis()));
		

		
		
		
//		testCalc.writeRupProbGainsForDiffAveragingMethods(erf.getTimeSpan().getStartTimeInMillis(), erf.getTimeSpan().getDuration(), "TEST_FILE");

		//testCalc.tempSimulateER_Events(timeSinceLastFileName, null, erf,10000d);
		
		
		
//		testFastCalculations(10000);
		
		
		

		// This shows that CondProb & CondProbForUnknownDateOfLastEvent look 
		// good for a variety of aperiodicities, nomralized durations, normalized 
		// time since last, and normalized historic open intervals (e.g., no
		// outliers from numerical artifacts).
//		double[] apers = {0.1,0.2,0.3,0.4,0.5,0.6,0.7,0.8,0.9,1.0};
//		for(double aper:apers) {
//			System.out.println("working on " +aper);
//			ProbabilityModelsCalc testCalc = new ProbabilityModelsCalc((float)aper);
//			testCalc.plotXYZ_FuncOfCondProbForUnknownDateOfLastEvent();		
//			testCalc.plotXYZ_FuncOfCondProb();
//		}

	}

}
