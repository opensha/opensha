package scratch.UCERF3.utils.UCERF2_Section_MFDs;

import java.awt.Color;
import java.io.File;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.jfree.chart.plot.DatasetRenderingOrder;
import org.opensha.commons.calc.FractileCurveCalculator;
import org.opensha.commons.data.function.AbstractXY_DataSet;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.data.function.XY_DataSetList;
import org.opensha.commons.geo.Location;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSymbol;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.ERF;
import org.opensha.sha.earthquake.ProbEqkRupture;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.FaultSegmentData;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.UCERF2;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.UCERF2_TimeIndependentEpistemicList;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.UnsegmentedSource;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.A_Faults.A_FaultSegmentedSourceGenerator;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.MeanUCERF2.MeanUCERF2;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.magdist.SummedMagFreqDist;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;

import scratch.UCERF3.analysis.FaultSystemRupSetCalc;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.inversion.CommandLineInversionRunner;
import scratch.UCERF3.inversion.InversionFaultSystemSolution;
import scratch.UCERF3.inversion.UCERF2_ComparisonSolutionFetcher;
import scratch.UCERF3.utils.UCERF3_DataUtils;


/**
 * This class generates the min, max, and mean parent-section MFDs implied by all time-independent UCERF2 
 * branches, for either nucleation or participation and for either incremental or cumulative MFDs.
 * 
 * Note that the MFDs for ELSINORE_COMBINED_STEPOVER_FAULT_SECTION_ID, GLEN_IVY_STEPOVER_FAULT_SECTION_ID, and
 * TEMECULA_STEPOVER_FAULT_SECTION_ID are all identical, so only one should be used (e.g., if summing all nucleation
 * MFDs to get a total for the region).  Likewise for the 3 overlapping/redundant SJ sections below.
 * 
 * The saveAllMFDPlot() method makes plots of the mean, min, and max nucleation (or participation 
 * depending on constructor argument) for each parent section from UCERF2_TimeIndependentEpistemicList, 
 * plus the average implied by MeanUCERF2.  The means agree on both participation and nucleation.
 * 
 * The saveAllMFDPlotComparisonsWithMappedUCERF2_FM2pt1_FltSysSol(*) method compares the mean, min, and max from
 * UCERF2_TimeIndependentEpistemicList to the MFDs implied by the FM 2.1 UCERF2 mapped fault system solution
 * (from UCERF2_ComparisonSolutionFetcher.getUCERF2Solution(FaultModels.FM2_1)).  Differences are listed and explained
 * in a file name "" available from Ned Field.
 * 
 * @author field
 *
 */
public class UCERF2_Section_MFDsCalc {
	
	private static boolean D = false; // debug flag
	
	public static int ELSINORE_COMBINED_STEPOVER_FAULT_SECTION_ID = 402; // replaces the following two:
	public static int GLEN_IVY_STEPOVER_FAULT_SECTION_ID = 297;
	public static int TEMECULA_STEPOVER_FAULT_SECTION_ID = 298;

	public static int SJ_COMBINED_STEPOVER_FAULT_SECTION_ID = 401; // replaced the following two:
	public static int SJ_VALLEY_STEPOVER_FAULT_SECTION_ID = 290;
	public static int SJ_ANZA_STEPOVER_FAULT_SECTION_ID = 291;

	final static String DATA_SUB_DIR = "UCERF2_Section_MFDs";
	final static String PART_SUB_DIR = "ParticipationFiles";
	final static String NUCL_SUB_DIR = "NucleationFiles";
	final static String SECT_LIST_FILE_NAME = "UCERF2_sectionIDs_AndNames.txt";
	final static String MFD_PLOTS_DIR = "dev/scratch/UCERF3/data/scratch/UCERF2_SectionMFD_Plots";
	HashMap<String,Integer> sectionIDfromNameMap;
	HashMap<Integer,String> sectionNamefromID_Map;
	
	// this will hold a list of MFDs for each fault section
	HashMap<Integer,XY_DataSetList> mfdList_ForSectID_Map;
	// this will hold the ERF index number associated with each MFD
	HashMap<Integer,ArrayList<Integer>> mfdBranches_ForSectID_Map;
	// this will hold the ERF branch weight associated with each MFD
	HashMap<Integer,ArrayList<Double>> mfdWts_ForSectID_Map;
	// this holds the total weight for each section (which equals the probability of existance)
	HashMap<Integer,Double> totWtForSectID_Map;
	
	// A map giving a list of fault sections for each segmented type-A source
//	HashMap<String,ArrayList<FaultSectionPrefData>> sectionsForTypeA_RupsMap;


	// MFDs for meanUCERF2 
	HashMap<String,SummedMagFreqDist> meanUCERF2_MFD_ForSectID_Map;
	
	boolean isParticipation;
	
	final public static double MIN_MAG = 5.05;
	final public static int NUM_MAG = 40;
	final public static double DELTA_MAG = 0.1;
	
	File precomputedDataDir;

	
	/**
	 * 
	 * @param isParticipation - set true for participation MFDs and false for nucleation MFDs
	 */
	private UCERF2_Section_MFDsCalc(boolean isParticipation) {
		this.isParticipation = isParticipation;
		this.precomputedDataDir = UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR.getParentFile(); // main data dir
		
		System.out.println("UCERF2_Source_MFDsCalc is creating data; this will take some time...");
		
		// make data dir if it doesn't exist
		File dataDir = new File(precomputedDataDir,DATA_SUB_DIR);
		if(!dataDir.exists())dataDir.mkdirs();
		
		// check for fault sections list data
		File sectListFile = new File(dataDir,SECT_LIST_FILE_NAME);
		if(!sectListFile.exists())
			writeListOfAllFaultSections(sectListFile);

		// populate sectionIDfromNameMap and sectionNamefromID_Map
		readListOfAllFaultSections();
		
//		System.out.println(dataDir);

		makeMFD_Lists();
		
		writeMinMaxMeanMFDsToFiles(dataDir);
				
	}
	
	/**
	 * This method returns an ArrayList<IncrementalMagFreqDist> giving the mean, min, and max (in that order) 
	 * UCERF2 section MFD for the specified attributes (over the 144 time-independent logic tree branches).
	 * @param parID parent section ID (either UCERF2 or UCERF3, which will be mapped to a UCERF2 ID)
	 * @param isParticipation - specifies whether to get participation vs nucleation MFDs
	 * @param cumDist - specifies whether to return cumulative vs nucleation MFDs
	 * @return - null is returned if parID is not one in UCERF2
	 */
	public static ArrayList<IncrementalMagFreqDist> getMeanMinAndMaxMFD(int parID, boolean isParticipation, boolean cumDist) {
		HashMap<Integer, String> sectionNamesMap = getSectionNamesMap();
		
		if (!sectionNamesMap.containsKey(parID)) {
			HashMap<Integer, Integer> idMapping = getUCERF3toUCERF2ParentSectionIDMap();
			if (idMapping.containsKey(parID))
				// renamed in UCERF3
				parID = idMapping.get(parID);
			else
				// no UCERF2 data for this parent ID
				return null;
		}
		
		String subDirName;
		if(isParticipation)
			subDirName = PART_SUB_DIR;
		else
			subDirName = NUCL_SUB_DIR;
		
		String distType;
		if(cumDist)
			distType = "cum";
		else
			distType = "incr";
		
		String fileName = distType+parID+".txt";

		ArrayList<IncrementalMagFreqDist> mfdList = null;

		try {
			BufferedReader br = new BufferedReader(UCERF3_DataUtils.getReader(DATA_SUB_DIR, subDirName, fileName));
			ArrayList<String> lineList = new ArrayList<String>();
			String line;
			while ((line = br.readLine()) != null) {
				lineList.add(line);
			}
			String[] strArray = StringUtils.split(lineList.get(0),"\t");
			String name = strArray[0];

			strArray = StringUtils.split(lineList.get(2),"\t");
			double minMag = Double.valueOf(strArray[0]);
			strArray = StringUtils.split(lineList.get(lineList.size()-1),"\t");
			double maxmag = Double.valueOf(strArray[0]);
			int numMag = lineList.size()-2;

			IncrementalMagFreqDist meanMFD = new IncrementalMagFreqDist(minMag, maxmag, numMag);
			IncrementalMagFreqDist minMFD = new IncrementalMagFreqDist(minMag, maxmag, numMag);
			IncrementalMagFreqDist maxMFD = new IncrementalMagFreqDist(minMag, maxmag, numMag);

			for(int l=2;l<lineList.size();l++) {
				strArray = StringUtils.split(lineList.get(l),"\t");
				meanMFD.set(l-2, Double.valueOf(strArray[1]));
				minMFD.set(l-2, Double.valueOf(strArray[2]));
				maxMFD.set(l-2, Double.valueOf(strArray[3]));
			}

			meanMFD.setName(name+" mean "+distType+" MFD");
			meanMFD.setInfo("section ID = "+parID);
			minMFD.setName(name+" min "+distType+" MFD");
			minMFD.setInfo("section ID = "+parID);
			maxMFD.setName(name+" max "+distType+" MFD");
			maxMFD.setInfo("section ID = "+parID);

			mfdList = new ArrayList<IncrementalMagFreqDist>();
			mfdList.add(meanMFD);
			mfdList.add(minMFD);
			mfdList.add(maxMFD);


		} catch (Exception e) {
			ExceptionUtils.throwAsRuntimeException(e);
		}
		
		return mfdList;

	}

	
	/**
	 * Note that MFDs are divided by the total weight (which is either 1.0 or 05) in order to
	 * give full weight to results that are only on FM 2.1 or Fm 2.2.
	 * @param dataDir
	 */
	private void writeMinMaxMeanMFDsToFiles(File dataDir) {
		// make the directory
		File directory;
		if(isParticipation) {
			directory=new File(dataDir,PART_SUB_DIR);
			if(!directory.exists())directory.mkdirs();
		}
		else {
			directory=new File(dataDir,NUCL_SUB_DIR);
			if(!directory.exists())directory.mkdirs();			
		}
		
		for(int parID : mfdList_ForSectID_Map.keySet()) {
			XY_DataSetList mfdList = mfdList_ForSectID_Map.get(parID);
			double weight = totWtForSectID_Map.get(parID);
			if(mfdList != null) {
				// write incremental file
				FractileCurveCalculator frCurveCalc = new FractileCurveCalculator(mfdList,mfdWts_ForSectID_Map.get(parID));
				AbstractXY_DataSet meanCurve = frCurveCalc.getMeanCurve();
				AbstractXY_DataSet minCurve = frCurveCalc.getMinimumCurve();
				AbstractXY_DataSet maxCurve = frCurveCalc.getMaximumCurve();
				File fileName = new File(directory,"incr"+parID+".txt");
				
				try {
					FileWriter fw = new FileWriter(fileName);
					fw.write(sectionNamefromID_Map.get(parID)+"\n");
					fw.write("mag\tmean\tmin\tmax\n");
					for(int i=0;i<meanCurve.size();i++) {
						// note that we are dividing by weight
						fw.write(meanCurve.getX(i)+"\t"+meanCurve.getY(i)/weight+"\t"+minCurve.getY(i)/weight+"\t"+maxCurve.getY(i)/weight+"\n");
					}
					fw.close ();
				}
				catch (IOException e) {
					System.out.println ("IO exception = " + e );
				}
				
				// write cumulative file
				FractileCurveCalculator frCurveCalcCum = new FractileCurveCalculator(getCumMFD_ListForSect(parID),mfdWts_ForSectID_Map.get(parID));
				AbstractXY_DataSet meanCurveCum = frCurveCalcCum.getMeanCurve();
				AbstractXY_DataSet minCurveCum = frCurveCalcCum.getMinimumCurve();
				AbstractXY_DataSet maxCurveCum = frCurveCalcCum.getMaximumCurve();
				File fileNameCum = new File(directory,"cum"+parID+".txt");
				
				try {
					FileWriter fwCum = new FileWriter(fileNameCum);
					fwCum.write(sectionNamefromID_Map.get(parID)+"\n");
					fwCum.write("mag\tmean\tmin\tmax\n");
					for(int i=0;i<meanCurve.size();i++) {
						// note that we are dividing by weight
						fwCum.write(meanCurveCum.getX(i)+"\t"+meanCurveCum.getY(i)/weight+"\t"+minCurveCum.getY(i)/weight+"\t"+maxCurveCum.getY(i)/weight+"\n");
					}
					fwCum.close ();
				}
				catch (IOException e) {
					System.out.println ("IO exception = " + e );
				}
			}
			else {
				System.out.println("Null MFD List for: id = "+parID+";  name = "+sectionNamefromID_Map.get(parID));
			}
		}
	}
	
	private XY_DataSetList getCumMFD_ListForSect(int parID) {
		XY_DataSetList mfdList = mfdList_ForSectID_Map.get(parID);
		XY_DataSetList cumMFD_List = new XY_DataSetList();
		for(XY_DataSet mfd:mfdList)
			cumMFD_List.add(((SummedMagFreqDist)mfd).getCumRateDistWithOffset());
		return cumMFD_List;
	}
	
	/**
	 * This writes out the total weight for each section (should be 0.5 if section is only on 
	 * FM 2.1 or FM 2.2).  Otherwise this should be 1.0.
	 */
	public void writeSectionWeights() {
		for(int parIndex:sectionNamefromID_Map.keySet()) {
			double wt = totWtForSectID_Map.get(parIndex);
			System.out.println((float)wt+"\t"+sectionNamefromID_Map.get(parIndex)+"\t"+parIndex);
		}
	}
	
	

	
	
	/**
	 * This class writes pdf plots of min, max, and mean results for all parent sections in UCERF2 FM 2.1 (blue lines).
	 * For comparison, this also plots the results from MeanUCERF2 (blue circle, which agree with the means computed
	 * from logic-tree branches).  This also adds the min, max, and mean as read from files (using the static method);
	 * these are plotted as blue crosses.  Finally, this also plots the MFD implied by the UCERF2 model mapped into a 
	 * fault system solution (UCERF2_ComparisonSolutionFetcher.getUCERF2Solution(FaultModels.FM2_1)).
	 * 
	 * This scales MFDs that have a total weight of 0.5 by this value in order to make a fair comparison (meaning 
	 * weights are applied here).
	 * 
	 * This has not been tested for FM 2.2 (would need to add this as method argument).
	 */
	public void saveAllTestMFD_Plots(boolean plotCumDist) {
		
		if(meanUCERF2_MFD_ForSectID_Map == null)
			makeMeanUCERF2_MFD_List();
		
		// get the UCERF2 mapped fault system solution
		InversionFaultSystemSolution fltSysSol =
				UCERF2_ComparisonSolutionFetcher.getUCERF2Solution(FaultModels.FM2_1);
		
		// get list of parent section IDs
		ArrayList<Integer> u2_parIds = new ArrayList<Integer>();
		for(FaultSectionPrefData data : fltSysSol.getRupSet().getFaultSectionDataList()) {
			int parID = data.getParentSectionId();
			if(!u2_parIds.contains(parID)) {
				// check that we have this one here
				if(sectionNamefromID_Map.keySet().contains(parID))
					u2_parIds.add(parID);
				else {
					if(D)
						System.out.println("Not including "+data.getParentSectionName());

				}
				
			}
		}
		
		for(Integer parID : u2_parIds) {
			if(D) System.out.println("Working on "+sectionNamefromID_Map.get(parID));
			
			EvenlyDiscretizedFunc mappedMFD;
			if(isParticipation) {
				if(plotCumDist)
					mappedMFD = fltSysSol.calcParticipationMFD_forParentSect(parID, MIN_MAG, MIN_MAG+DELTA_MAG*(NUM_MAG-1), NUM_MAG).getCumRateDistWithOffset();
				else
					mappedMFD = fltSysSol.calcParticipationMFD_forParentSect(parID, MIN_MAG, MIN_MAG+DELTA_MAG*(NUM_MAG-1), NUM_MAG);
				mappedMFD.setName("UCERF2 Mapped Participation MFD for "+sectionNamefromID_Map.get(parID));
			}
			else {
				if(plotCumDist)
					mappedMFD = fltSysSol.calcNucleationMFD_forParentSect(parID, MIN_MAG, MIN_MAG+DELTA_MAG*(NUM_MAG-1), NUM_MAG).getCumRateDistWithOffset();
				else
					mappedMFD = fltSysSol.calcNucleationMFD_forParentSect(parID, MIN_MAG, MIN_MAG+DELTA_MAG*(NUM_MAG-1), NUM_MAG);
				mappedMFD.setName("UCERF2 Mapped Nucleation MFD for "+sectionNamefromID_Map.get(parID));				
			}
	
			// apply the weight (1.0 or 0.5) for a meaningful comparison
			double totWeight = totWtForSectID_Map.get(parID);
			mappedMFD.scale(totWeight);
				
			ArrayList<XY_DataSet> funcs = new ArrayList<XY_DataSet>();
			funcs.add(mappedMFD);
			ArrayList<PlotCurveCharacterstics> chars = new ArrayList<PlotCurveCharacterstics>();
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, PlotSymbol.CROSS, 3f, Color.RED));

			
			XY_DataSetList mfdList = mfdList_ForSectID_Map.get(parID);
//			double totalWeight = totWtForSectID_Map.get(parID);
			if(mfdList != null) {
				FractileCurveCalculator frCurveCalc;
				if(plotCumDist)
					frCurveCalc = new FractileCurveCalculator(getCumMFD_ListForSect(parID),mfdWts_ForSectID_Map.get(parID));
				else
					frCurveCalc = new FractileCurveCalculator(mfdList,mfdWts_ForSectID_Map.get(parID));
//				
//				XY_DataSetList tempMFD_List = new XY_DataSetList();
//				for(XY_DataSet mfd:mfdList)
//					if(plotCumDist)
//						tempMFD_List.add(((SummedMagFreqDist)mfd).getCumRateDistWithOffset());
//					else
//						tempMFD_List.add(((SummedMagFreqDist)mfd));
//
//				FractileCurveCalculator frCurveCalc = new FractileCurveCalculator(tempMFD_List,mfdWts_ForSectID_Map.get(parID));
				funcs.add(frCurveCalc.getMeanCurve());
				funcs.add(frCurveCalc.getMinimumCurve());
				funcs.add(frCurveCalc.getMaximumCurve());				
			}
			else {
				System.out.println("Null MFD List for: id = "+parID+";  name = "+sectionNamefromID_Map.get(parID));
			}
			
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLUE));
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.BLUE));
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.BLUE));
			
			// add the mean from meanUCERF2_MFD_ForSectID_Map
			if(plotCumDist)
				funcs.add(meanUCERF2_MFD_ForSectID_Map.get(sectionNamefromID_Map.get(parID)).getCumRateDistWithOffset());
			else
				funcs.add(meanUCERF2_MFD_ForSectID_Map.get(sectionNamefromID_Map.get(parID)));
			chars.add(new PlotCurveCharacterstics(PlotSymbol.CIRCLE, 5f, Color.BLUE));
			
			// add the MFDs read from static files (and apply totWeight for meaningful comparison)
			ArrayList<IncrementalMagFreqDist> mfds = getMeanMinAndMaxMFD(parID, isParticipation, plotCumDist);
			if(totWeight < 0.99999) {	// reapply weight if less that 1
				for(IncrementalMagFreqDist mfd : mfds)
					mfd.scale(totWeight);
			}
			funcs.addAll(mfds);
			
			chars.add(new PlotCurveCharacterstics(PlotSymbol.CROSS, 3f, Color.BLUE));
			chars.add(new PlotCurveCharacterstics(PlotSymbol.CROSS, 3f, Color.BLUE));
			chars.add(new PlotCurveCharacterstics(PlotSymbol.CROSS, 3f, Color.BLUE));

			HeadlessGraphPanel gp = new HeadlessGraphPanel();
			CommandLineInversionRunner.setFontSizes(gp);
			gp.setYLog(true);
			gp.setRenderingOrder(DatasetRenderingOrder.FORWARD);
			gp.setUserBounds(5, 9, 1e-7, 1.0);
			float totWt=totWtForSectID_Map.get(parID).floatValue();
			String title;
			if(isParticipation)
				title = sectionNamefromID_Map.get(parID)+" Participation Cum MFDs (totWt="+totWt+")";
			else
				title = sectionNamefromID_Map.get(parID)+" Nucleation Cum MFDs (totWt="+totWt+")";

			gp.drawGraphPanel("Magnitude", "Rate (per year)", funcs, chars, title);

			String fileName = sectionNamefromID_Map.get(parID).replace("\\s+","");
			String subDir;
			if(plotCumDist) {
				if(isParticipation)
					subDir = "/CumulativeParticipation";
				else
					subDir = "/CumulativeNucleation";
			}
			else {
				if(isParticipation)
					subDir = "/IncrementalParticipation";
				else
					subDir = "/IncrementalNucleation";
			}
			File filePath = new File(MFD_PLOTS_DIR+subDir);
			if(!filePath.exists())filePath.mkdirs();
			
			File file = new File(MFD_PLOTS_DIR+subDir, fileName);
			gp.getChartPanel().setSize(1000, 800);
			try {
				gp.saveAsPDF(file.getAbsolutePath()+".pdf");
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
//			gp.saveAsPNG(file.getAbsolutePath()+".png");

		}
		
		

	}


	
	private void makeMFD_Lists() {
		
		mfdList_ForSectID_Map = new HashMap<Integer,XY_DataSetList>();
		mfdBranches_ForSectID_Map = new HashMap<Integer,ArrayList<Integer>>();
		mfdWts_ForSectID_Map = new HashMap<Integer,ArrayList<Double>>();
		totWtForSectID_Map = new HashMap<Integer,Double>();
		
		UCERF2_TimeIndependentEpistemicList ucerf2EpistemicList = getUCERF2_TimeIndependentEpistemicList();
		int numERFs = ucerf2EpistemicList.getNumERFs();
		if(D) System.out.println("Num Branches="+numERFs);

		for(int branch=0; branch<numERFs; ++branch) {
//		for(int branch=0; branch<1; ++branch) {
			if(D) System.out.println(branch);
			ERF erf = ucerf2EpistemicList.getERF(branch);
			double duration = erf.getTimeSpan().getDuration();
			
			// make map of MFD for each section here
			HashMap<String,SummedMagFreqDist> mfd_ForSectName_Map = new HashMap<String,SummedMagFreqDist>();
			
			// Make map of section list for each source for segmented Type-A sources (so we don't have to loop through all sections below):
			HashMap<String,ArrayList<FaultSectionPrefData>> sectionsForTypeA_RupsMap = new HashMap<String,ArrayList<FaultSectionPrefData>>();
			ArrayList<Object> objList = ((UCERF2)erf).get_A_FaultSourceGenerators();	// regrettably, this method returns different object types
			for(Object obj: objList) { 
				if(obj instanceof A_FaultSegmentedSourceGenerator) { // Segmented source (rather than an unsegmented source, which is the other possible type of object)
					A_FaultSegmentedSourceGenerator srcGen = (A_FaultSegmentedSourceGenerator)obj;	// cast for convenience below
					ArrayList<FaultSectionPrefData> dataList = srcGen.getFaultSegmentData().getPrefFaultSectionDataList();
					for(int r=0;r<srcGen.getNumRupSources();r++) {
						String srcName = srcGen.getFaultSegmentData().getFaultName()+";"+srcGen.getLongRupName(r);
						sectionsForTypeA_RupsMap.put(srcName, dataList);	// same data list for all sources of the given type-A fault
					}
				}
			}
			
//			System.out.println("Type-A fault segmented source names");
//			System.out.println(sectionsForTypeA_RupsMap.keySet());

			// now loop over all sources
			for(int s=0;s<erf.getNumSources();s++) {
				ProbEqkSource src = erf.getSource(s);
				if(src instanceof UnsegmentedSource) {  // For Type-B & Unsegmented Type-A sources
					ArrayList<FaultSectionPrefData> dataList = ((UnsegmentedSource)src).getFaultSegmentData().getPrefFaultSectionDataList();
					
					if(dataList.size() == 1) { //it's a single-section type-B source
						String name = src.getName();
						// check that names are same
						if(!name.equals(dataList.get(0).getSectionName()))
							throw new RuntimeException("Problem");
						SummedMagFreqDist mfd;
						if(mfd_ForSectName_Map.keySet().contains(name)) { // actually, this shouldn't be in this list
							mfd = mfd_ForSectName_Map.get(name);
						}
						else {
							mfd = getNewSummedMagFreqDist();
							mfd_ForSectName_Map.put(name, mfd);
							mfd.setName(name);
							mfd.setInfo("Section ID = "+sectionIDfromNameMap.get(name));
						}
						for(ProbEqkRupture rup : src) {
							mfd.addResampledMagRate(rup.getMag(), rup.getMeanAnnualRate(duration), true);  // nucleation and participation is the same
						}
					}
					else {	// it's a stitched together unsegmented source
						processSource(src, dataList, mfd_ForSectName_Map, duration);
					}
				}
				else {	// type-A source
					String name = src.getName();
					if(sectionsForTypeA_RupsMap.keySet().contains(name)) {	// check whether it's a type A source
						processSource(src, sectionsForTypeA_RupsMap.get(name), mfd_ForSectName_Map, duration);
					}
//					else
//						System.out.println("Ignored source: " + name);
				}
			}
			
			// now populate the maps with the results of this branch
			double branchWt = ucerf2EpistemicList.getERF_RelativeWeight(branch);
			for(String name:mfd_ForSectName_Map.keySet()) {
				int id = sectionIDfromNameMap.get(name);
				// add lists if it's the first element
				if(!mfdList_ForSectID_Map.keySet().contains(id)) {
					mfdList_ForSectID_Map.put(id, new XY_DataSetList());
					mfdBranches_ForSectID_Map.put(id, new ArrayList<Integer>());
					mfdWts_ForSectID_Map.put(id, new ArrayList<Double>());
				}
				mfdList_ForSectID_Map.get(id).add(mfd_ForSectName_Map.get(name));
				mfdBranches_ForSectID_Map.get(id).add(branch);
				mfdWts_ForSectID_Map.get(id).add(branchWt);
			}
		}
				
		
		// now compute totWtForSectID_Map
		for(Integer id:mfdWts_ForSectID_Map.keySet()) {
			double totWt=0;
			for(Double wt:mfdWts_ForSectID_Map.get(id)) {
				totWt +=wt;
			}
			totWtForSectID_Map.put(id,totWt);
		}
		
		// add a zero MFD where the total wt is less than 1
		SummedMagFreqDist zeroMFD = getNewSummedMagFreqDist();
		for(Integer id:mfdWts_ForSectID_Map.keySet()) {
			double totWt=totWtForSectID_Map.get(id);
			if(totWt < 0.99999999) {
				mfdList_ForSectID_Map.get(id).add(zeroMFD);
				mfdWts_ForSectID_Map.get(id).add(1.0-totWt);
			}
		}

		
//		System.out.println(mfdList_ForSectID_Map.keySet().size());
//		System.out.println(mfdList_ForSectID_Map.get(1));
		
	}
	
	/**
	 * This method adds the given source to the participation MFDs of the given list of
	 * fault sections
	 * @param src
	 * @param dataList
	 * @param mfd_ForSectName_Map
	 * @param duration
	 */
	private void processSource(ProbEqkSource src, ArrayList<FaultSectionPrefData> dataList,
			HashMap<String,SummedMagFreqDist> mfd_ForSectName_Map, double duration) {
		
		// first get list of MFDs for each fault section
		ArrayList<SummedMagFreqDist> mfdList = new ArrayList<SummedMagFreqDist>();
		for(FaultSectionPrefData data : dataList) {
			String name = data.getSectionName();
			SummedMagFreqDist mfd;
			if(mfd_ForSectName_Map.keySet().contains(name)) {
				mfd = mfd_ForSectName_Map.get(name);
			}
			else {
				mfd = getNewSummedMagFreqDist();
				mfd.setName(name);
				mfd.setInfo("Section ID = "+sectionIDfromNameMap.get(name));
				
				// make sure stepover/overlapping sections point to same MFD
				if( name.equals(sectionNamefromID_Map.get(ELSINORE_COMBINED_STEPOVER_FAULT_SECTION_ID)) ||
					name.equals(sectionNamefromID_Map.get(GLEN_IVY_STEPOVER_FAULT_SECTION_ID)) ||
					name.equals(sectionNamefromID_Map.get(TEMECULA_STEPOVER_FAULT_SECTION_ID))) {
//System.out.println(name);
						mfd.setName(sectionNamefromID_Map.get(ELSINORE_COMBINED_STEPOVER_FAULT_SECTION_ID));
						mfd_ForSectName_Map.put(sectionNamefromID_Map.get(ELSINORE_COMBINED_STEPOVER_FAULT_SECTION_ID), mfd);
						mfd_ForSectName_Map.put(sectionNamefromID_Map.get(GLEN_IVY_STEPOVER_FAULT_SECTION_ID), mfd);
						mfd_ForSectName_Map.put(sectionNamefromID_Map.get(TEMECULA_STEPOVER_FAULT_SECTION_ID), mfd);
				}
				else if( name.equals(sectionNamefromID_Map.get(SJ_COMBINED_STEPOVER_FAULT_SECTION_ID)) ||
						 name.equals(sectionNamefromID_Map.get(SJ_ANZA_STEPOVER_FAULT_SECTION_ID)) ||
						 name.equals(sectionNamefromID_Map.get(SJ_VALLEY_STEPOVER_FAULT_SECTION_ID))) {
//System.out.println(name);
							mfd.setName(sectionNamefromID_Map.get(SJ_COMBINED_STEPOVER_FAULT_SECTION_ID));
							mfd_ForSectName_Map.put(sectionNamefromID_Map.get(SJ_COMBINED_STEPOVER_FAULT_SECTION_ID), mfd);
							mfd_ForSectName_Map.put(sectionNamefromID_Map.get(SJ_ANZA_STEPOVER_FAULT_SECTION_ID), mfd);
							mfd_ForSectName_Map.put(sectionNamefromID_Map.get(SJ_VALLEY_STEPOVER_FAULT_SECTION_ID), mfd);
				}
				else {
					mfd_ForSectName_Map.put(name, mfd);
				}
			}
			mfdList.add(mfd);
		}
		
		// make sure lists are the same size
		if(mfdList.size() != dataList.size())
			throw new RuntimeException("Problem");
		
		// now process each rupture of the source
		for(ProbEqkRupture rup : src) {
			double[] fracOnEachSect;
			
			if(isParticipation)
				fracOnEachSect = getSectionParticipationsForRup(rup, dataList);
			else
				fracOnEachSect = getFractionOfRupOnEachSection(rup, dataList);
			double mag = rup.getMag();
			boolean stepoverDone= false;
			for(int i=0;i<mfdList.size();i++) {
				if(fracOnEachSect[i] > 0) {
					String name = dataList.get(i).getSectionName();	// get name to check for stepover
					if( name.equals(sectionNamefromID_Map.get(GLEN_IVY_STEPOVER_FAULT_SECTION_ID)) ||
						name.equals(sectionNamefromID_Map.get(TEMECULA_STEPOVER_FAULT_SECTION_ID))) {
							if(!stepoverDone) {		// avoid double counting the stepovers
								mfdList.get(i).addResampledMagRate(mag, fracOnEachSect[i]*rup.getMeanAnnualRate(duration), true);	
								stepoverDone=true;
							}
					}
					else if(name.equals(sectionNamefromID_Map.get(SJ_ANZA_STEPOVER_FAULT_SECTION_ID)) ||
							name.equals(sectionNamefromID_Map.get(SJ_VALLEY_STEPOVER_FAULT_SECTION_ID))) {
								if(!stepoverDone) { 	// avoid double counting the stepovers
									mfdList.get(i).addResampledMagRate(mag, fracOnEachSect[i]*rup.getMeanAnnualRate(duration), true);	
									stepoverDone=true;
								}
					}
					else {
						mfdList.get(i).addResampledMagRate(mag, fracOnEachSect[i]*rup.getMeanAnnualRate(duration), true);	
					}

					
//if(dataList.get(i).getSectionName().equals("San Jacinto (Anza, stepover)"))
//	System.out.println(dataList.get(i).getSectionName()+"\t"+mfdList.get(i).getName());
//if(dataList.get(i).getSectionName().equals("San Jacinto (San Jacinto Valley, stepover)"))
//	System.out.println(dataList.get(i).getSectionName()+"\t"+mfdList.get(i).getName());
//if(dataList.get(i).getSectionName().equals("San Jacinto (Stepovers Combined)"))
//	System.out.println(dataList.get(i).getSectionName()+"\t"+mfdList.get(i).getName());

//	if(mag <6.1)
//		System.out.println((float)mag+"\t"+(float)(rateOnEachSect[i]*rup.getMeanAnnualRate(duration))+"\t"+dataList.get(i).getSectionName()+"\t"+src.getName());
				}
			}
		}
	}
	
	
	/**
	 * This computes the fraction of nucleations for the given rupture on each 
	 * section in the dataList (assuming a uniform distribution of nucleation
	 * points), where nucleation probability is based on parent section area
	 * @param rup
	 * @param dataList
	 * @return
	 */
	private double[] getFractionOfRupOnEachSection(ProbEqkRupture rup, ArrayList<FaultSectionPrefData> dataList) {
		double[] fracOnEachSect = new double[dataList.size()];
		double[] ddwForSect =  new double[dataList.size()];
		for(int i=0;i<dataList.size();i++)
			ddwForSect[i] = dataList.get(i).getReducedDownDipWidth();
		
		FaultTrace rupTrace = rup.getRuptureSurface().getEvenlyDiscritizedUpperEdge();
		
		// loop over each location on rupture trace; exclude endpoints because the association may be ambiguous for ruptures confined to one parent
		for(int l=1;l<rupTrace.size()-1;l++) {
			Location loc = rupTrace.get(l);
			double minDist = Double.MAX_VALUE;
			int minLocSectIndex = -1;
			for(int s=0;s<dataList.size();s++) {
				double dist = dataList.get(s).getFaultTrace().minDistToLine(loc);
				if(dist<minDist) {
					minDist=dist;
					minLocSectIndex = s;
				}
			}
			fracOnEachSect[minLocSectIndex] += ddwForSect[minLocSectIndex];	// wted by down-dip width (so nucleation faction is by area)
//			fracOnEachSect[minLocSectIndex] += 1d/(double)(rupTrace.size()-2);
		}
		
		// normalize so sum is 1.0
		double total=0;
		for(int i=0;i<dataList.size();i++)
			total += fracOnEachSect[i];
		for(int i=0;i<dataList.size();i++)
			fracOnEachSect[i] = fracOnEachSect[i]/total;
		
		return fracOnEachSect;
	}
	
	
	/**
	 * This determines which sections of the given dataList are utilized by the given rupture
	 * 
	 * @param rup
	 * @param dataList
	 * @return double[] - one if utilized and zero if not
	 */
	private double[] getSectionParticipationsForRup(ProbEqkRupture rup, ArrayList<FaultSectionPrefData> dataList) {
		double[] partOfSect = new double[dataList.size()];
		
		FaultTrace rupTrace = rup.getRuptureSurface().getEvenlyDiscritizedUpperEdge();
		
		// loop over each location on rupture trace, but skip first and last point because this could get mapped to neighbor
		for(int l=1;l<rupTrace.size()-1;l++) {
			Location loc = rupTrace.get(l);
			double minDist = Double.MAX_VALUE;
			int minLocSectIndex = -1;
			// find closest section for surface loc
			for(int s=0;s<dataList.size();s++) {
				double dist = dataList.get(s).getFaultTrace().minDistToLine(loc);
				if(dist<minDist) {
					minDist=dist;
					minLocSectIndex = s;
				}
			}
			partOfSect[minLocSectIndex] = 1.0;	// set that section participates
		}
		return partOfSect;
	}

	
	
	private SummedMagFreqDist getNewSummedMagFreqDist() {
		return new SummedMagFreqDist(MIN_MAG, NUM_MAG, DELTA_MAG);
	}
	
	/**
	 * This writes out a list of all fault sections among all ERF branches
	 */
	private static void writeListOfAllFaultSections(File sectListFile) {
		UCERF2_TimeIndependentEpistemicList ucerf2EpistemicList = getUCERF2_TimeIndependentEpistemicList();
		int numERFs = ucerf2EpistemicList.getNumERFs();
		if(D) System.out.println("Num Branches="+numERFs);
		ArrayList<String> sectionNames = new ArrayList<String>();
		ArrayList<Integer> sectionIDs = new ArrayList<Integer>();

		ArrayList<String> typeA_RupNamesList = new ArrayList<String>();

		for(int i=0; i<numERFs; ++i) {
//		for(int i=72; i<73; ++i) {
//			System.out.println("\nWeight of Branch "+i+"="+ucerf2EpistemicList.getERF_RelativeWeight(i));
//			System.out.println("Parameters of Branch "+i+":");
//			System.out.println(ucerf2EpistemicList.getParameterList(i).getParameterListMetadataString("\n"));
			if(D) System.out.println(i);
			ERF erf = ucerf2EpistemicList.getERF(i);
						
			// Get sections names for Type-A sections:
			ArrayList<Object> objList = ((UCERF2)erf).get_A_FaultSourceGenerators();
			for(Object obj: objList) { 
				if(obj instanceof A_FaultSegmentedSourceGenerator) { // Segmented source 
					A_FaultSegmentedSourceGenerator srcGen = (A_FaultSegmentedSourceGenerator)obj;
					FaultSegmentData segData = ((A_FaultSegmentedSourceGenerator)obj).getFaultSegmentData();
					ArrayList<FaultSectionPrefData> dataList = segData.getPrefFaultSectionDataList();
					for(FaultSectionPrefData data:dataList) {
						int id = data.getSectionId();
						if(!sectionIDs.contains(id)) {
							String name = data.getSectionName();
							if(sectionNames.contains(name)) // this is a double check
								throw new RuntimeException("Error - duplicate name but not id");
							sectionIDs.add(id);
							sectionNames.add(name);
						}
					}
				}
			}

			// now loop over sources to get the unsegmented sources
			for(int s=0;s<erf.getNumSources();s++) {
				ProbEqkSource src = erf.getSource(s);
				if(src instanceof UnsegmentedSource) {  // For Type-B & Unsegmented Type-A sources
					FaultSegmentData segData = ((UnsegmentedSource)src).getFaultSegmentData();
					ArrayList<FaultSectionPrefData> dataList = segData.getPrefFaultSectionDataList();
					for(FaultSectionPrefData data:dataList) {
						int id = data.getSectionId();
						if(!sectionIDs.contains(id)) {
							String name = data.getSectionName();
							if(sectionNames.contains(name)) // this is a double check
								throw new RuntimeException("Error - duplicate name but not id");
							sectionIDs.add(id);
							sectionNames.add(name);
						}
//						System.out.println(data.getSectionId()+"\t"+data.getSectionName());
					}
				}

			}
		}
		
		// write out results
		try {
			FileWriter fw = new FileWriter(sectListFile);
			for(int i=0;i<sectionNames.size();i++) {
				if(D) System.out.println(sectionIDs.get(i)+"\t"+sectionNames.get(i));
				fw.write(sectionIDs.get(i)+"\t"+sectionNames.get(i)+"\n");
			}
			fw.close ();
		}
		catch (IOException e) {
			System.out.println ("IO exception = " + e );
		}
	}
	
	
	private void readListOfAllFaultSections() {
		sectionIDfromNameMap = new HashMap<String,Integer>();
		sectionNamefromID_Map = getSectionNamesMap();
		for (Integer id : sectionNamefromID_Map.keySet())
			sectionIDfromNameMap.put(sectionNamefromID_Map.get(id), id);
		
		for(String name : sectionIDfromNameMap.keySet()){
			Integer id = sectionIDfromNameMap.get(name);
			if(D) System.out.println(id+"\t"+name);
			if(!name.equals(sectionNamefromID_Map.get(id)))
				throw new RuntimeException("Problem");
		}
	}
	
	private static HashMap<Integer, String> sectionNamesCache;
	
	private synchronized static HashMap<Integer, String> getSectionNamesMap() {
		if (sectionNamesCache == null) {
			sectionNamesCache = Maps.newHashMap();
			
			try {
				BufferedReader reader = new BufferedReader(UCERF3_DataUtils.getReader(DATA_SUB_DIR, SECT_LIST_FILE_NAME));
				int l=-1;
				String line;
				while ((line = reader.readLine()) != null) {
					l+=1;
					String[] st = StringUtils.split(line,"\t");
					Integer id = Integer.valueOf(st[0]);
					String name = st[1];
					sectionNamesCache.put(id, name);
				}
			} catch (Exception e) {
				ExceptionUtils.throwAsRuntimeException(e);
			}
		}
		return sectionNamesCache;
	}
	
	private static HashMap<Integer, Integer> UCERF3toUCERF2ParentSectionIDMap;
	
	/**
	 * This gets the mapping of UCERF3 parent section IDs to UCERF2 parent section IDs. Note that this only includes
	 * IDs which changed (does not include IDs which are identical in both models), and omits any mapping where a fault
	 * was subdivided or combined from UCERF2 to UCERF3.
	 * @return
	 */
	public synchronized static HashMap<Integer, Integer> getUCERF3toUCERF2ParentSectionIDMap() {
		if (UCERF3toUCERF2ParentSectionIDMap == null) {
			UCERF3toUCERF2ParentSectionIDMap = Maps.newHashMap();
			
			// needed to get UCERF2 IDs for mapping
			HashMap<Integer, String> ucerf2SectIDsToNamesMap = getSectionNamesMap();
			HashMap<String, Integer> ucerf2SectNamesToIDsMap = Maps.newHashMap();
			for (Integer id : ucerf2SectIDsToNamesMap.keySet())
				ucerf2SectNamesToIDsMap.put(ucerf2SectIDsToNamesMap.get(id), id);
			
			// now get UCERF3 IDs for mapping
			HashMap<String, Integer> ucerf3SectNamesToIDsMap = Maps.newHashMap();
			for (FaultSectionPrefData sect : FaultModels.FM3_1.fetchFaultSections())
				ucerf3SectNamesToIDsMap.put(sect.getName(), sect.getSectionId());
			for (FaultSectionPrefData sect : FaultModels.FM3_2.fetchFaultSections())
				if (!ucerf3SectNamesToIDsMap.containsKey(sect.getSectionName()))
					ucerf3SectNamesToIDsMap.put(sect.getName(), sect.getSectionId());
			
			try {
				// load in ucerf3 section name mapping files
				HashMap<String, String> namesUCERF3toUCERF2Map = loadUCERF3toUCER2NameMappingFile(FaultModels.FM3_1);
				namesUCERF3toUCERF2Map.putAll(loadUCERF3toUCER2NameMappingFile(FaultModels.FM3_2));
				
				// now create ID mapping
				for (String ucerf3Name : namesUCERF3toUCERF2Map.keySet()) {
					String ucerf2Name = namesUCERF3toUCERF2Map.get(ucerf3Name);
					Integer ucerf2ID = ucerf2SectNamesToIDsMap.get(ucerf2Name);
					Preconditions.checkNotNull(ucerf2ID, "UCERF3 to UCERF2 mapping has incorrect name (UCERF2 name incorrect): '"
									+ucerf3Name+"' => '"+ucerf2Name+"'");
					Integer ucerf3ID = ucerf3SectNamesToIDsMap.get(ucerf3Name);
					Preconditions.checkNotNull(ucerf2ID, "UCERF3 to UCERF2 mapping has incorrect name (UCERF3 name incorrect): '"
							+ucerf3Name+"' => '"+ucerf2Name+"'");
					UCERF3toUCERF2ParentSectionIDMap.put(ucerf3ID, ucerf2ID);
				}
			} catch (IOException e) {
				ExceptionUtils.throwAsRuntimeException(e);
			}
		}
		return UCERF3toUCERF2ParentSectionIDMap;
	}
	
	public static HashMap<String, String> loadUCERF3toUCER2NameMappingFile(FaultModels fm) throws IOException {
		String subDir = "FindEquivUCERF2_Ruptures";
		String fName;
		switch (fm) {
		case FM3_1:
			fName = "FM2to3_1_sectionNameChanges.txt";
			break;
		case FM3_2:
			fName = "FM2to3_1_sectionNameChanges.txt";
			break;

		default:
			throw new IllegalArgumentException("No mapping for "+fm+" to UCERF2 parent sections");
		}
		BufferedReader br = new BufferedReader(UCERF3_DataUtils.getReader(subDir, fName));
		
		HashMap<String, String> mapping = Maps.newHashMap();
		
		String line;
		while ((line = br.readLine()) != null) {
			line = line.trim();
			if (line.contains("REMOVED") || line.contains("COMBINED") || line.contains("MULTIPLE"))
				continue;
			String[] split = line.split("\t");
			Preconditions.checkState(split.length == 2, "incorrectly formatted line (should have exaclty" +
					" 2 items separated by a tab): "+line);
			String ucerf2Name = split[0];
			String ucerf3Name = split[1];
			mapping.put(ucerf3Name, ucerf2Name);
		}
		
		return mapping;
	}
	
	
	/**
	 * This returns an instance with the background seismicity turned off
	 * @return
	 */
	private static UCERF2_TimeIndependentEpistemicList getUCERF2_TimeIndependentEpistemicList() {
		UCERF2_TimeIndependentEpistemicList ucerf2EpistemicList = new UCERF2_TimeIndependentEpistemicList();
		ucerf2EpistemicList.getAdjustableParameterList().getParameter(UCERF2.BACK_SEIS_NAME).setValue(UCERF2.BACK_SEIS_EXCLUDE);
		return ucerf2EpistemicList;
	}
	
	
	private void makeMeanUCERF2_MFD_List() {
		
		// make sectionsForTypeA_RupsMap
		UCERF2_TimeIndependentEpistemicList ucerf2EpistemicList = getUCERF2_TimeIndependentEpistemicList();
		ERF erf = ucerf2EpistemicList.getERF(0);
		double duration = erf.getTimeSpan().getDuration();
		// Make map of section list for each source for segmented Type-A sources (so we don't have to loop through all sections below):
		HashMap<String,ArrayList<FaultSectionPrefData>> sectionsForTypeA_RupsMap = new HashMap<String,ArrayList<FaultSectionPrefData>>();
		ArrayList<Object> objList = ((UCERF2)erf).get_A_FaultSourceGenerators();	// regrettably, this method returns different object types
		for(Object obj: objList) { 
			if(obj instanceof A_FaultSegmentedSourceGenerator) { // Segmented source (rather than an unsegmented source, which is the other possible type of object)
				A_FaultSegmentedSourceGenerator srcGen = (A_FaultSegmentedSourceGenerator)obj;	// cast for convenience below
				ArrayList<FaultSectionPrefData> dataList = srcGen.getFaultSegmentData().getPrefFaultSectionDataList();
				for(int r=0;r<srcGen.getNumRupSources();r++) {
					String srcName = srcGen.getFaultSegmentData().getFaultName()+";"+srcGen.getLongRupName(r);
					sectionsForTypeA_RupsMap.put(srcName, dataList);	// same data list for all sources of the given type-A fault
				}
			}
		}
		
//		System.out.println("keys for sectionsForTypeA_RupsMap");
//		for(String srcName:sectionsForTypeA_RupsMap.keySet())
//			System.out.println(srcName);
//System.exit(1);
		
		meanUCERF2_MFD_ForSectID_Map = new HashMap<String,SummedMagFreqDist>();

		MeanUCERF2 meanUCERF2 = new MeanUCERF2();
		meanUCERF2.setParameter(UCERF2.PROB_MODEL_PARAM_NAME, UCERF2.PROB_MODEL_POISSON);
		meanUCERF2.setParameter(UCERF2.BACK_SEIS_NAME, UCERF2.BACK_SEIS_EXCLUDE);
		meanUCERF2.updateForecast();
		duration = meanUCERF2.getTimeSpan().getDuration();
		
		if(D) System.out.println(meanUCERF2.getAdjustableParameterList().toString());

		if(sectionsForTypeA_RupsMap == null)
			throw new RuntimeException("Error: sectionsForTypeA_RupsMap is null; need to run makeMFD_Lists()");

		// now loop over all sources
		for(int s=0;s<meanUCERF2.getNumSources();s++) {
			ProbEqkSource src = meanUCERF2.getSource(s);
			if(src instanceof UnsegmentedSource) {  // For Type-B & Unsegmented Type-A sources
				ArrayList<FaultSectionPrefData> dataList = ((UnsegmentedSource)src).getFaultSegmentData().getPrefFaultSectionDataList();

//				if(src.getName().equals("Death Valley Connected")) {
//					System.out.println(src.getName());
//					for(FaultSectionPrefData data:dataList)
//						System.out.println(data.getSectionName()+"\t"+data.getSectionId());
//				}


				if(dataList.size() == 1) { //it's a single-section type-B source
					String name = src.getName();
					// check that names are same
					if(!name.equals(dataList.get(0).getSectionName()))
						throw new RuntimeException("Problem");
					SummedMagFreqDist mfd;
					if(meanUCERF2_MFD_ForSectID_Map.keySet().contains(name)) { // actually, this shouldn't be in this list
						mfd = meanUCERF2_MFD_ForSectID_Map.get(name);
					}
					else {
						mfd = getNewSummedMagFreqDist();
						meanUCERF2_MFD_ForSectID_Map.put(name, mfd);
						mfd.setName(name);
						mfd.setInfo("Section ID = "+sectionIDfromNameMap.get(name));
					}
					for(ProbEqkRupture rup : src) {
						mfd.addResampledMagRate(rup.getMag(), rup.getMeanAnnualRate(duration), true);
					}
				}
				else {	// it's a stitched together unsegmented source
					processSource(src, dataList, meanUCERF2_MFD_ForSectID_Map, duration);
				}
			}
			else {
				String name = src.getName();
				if(sectionsForTypeA_RupsMap.keySet().contains(name)) {	// check whether it's a type A source
					processSource(src, sectionsForTypeA_RupsMap.get(name), meanUCERF2_MFD_ForSectID_Map, duration);
				}
				else
					if(D) System.out.println("Ignored source: " + name);
			}
		}
		
//		System.out.println("test: " + meanUCERF2_MFD_ForSectID_Map.get("Point Reyes"));

	}



	/**
	 * @param args
	 */
	public static void main(String[] args) {
		
		// 68	Hayward (No)
//		ArrayList<IncrementalMagFreqDist> list = getMeanMinAndMaxMFD(68, false, false);
//		for(IncrementalMagFreqDist mfd:list) {
//			System.out.println(mfd);
//		}
		
		
//		for (FaultSectionPrefData sect : FaultModels.FM3_2.fetchFaultSections())
//			if (getMeanMinAndMaxMFD(sect.getSectionId(), true, false) == null)
//				System.out.println("NO MAPPING FOR: "+sect.getSectionId()+". "+sect.getSectionName());
		
//		UCERF2_Section_MFDsCalc test = new UCERF2_Section_MFDsCalc(true);
//		test.saveAllTestMFD_Plots(true);
//		test.saveAllTestMFD_Plots(false);
		UCERF2_Section_MFDsCalc test = new UCERF2_Section_MFDsCalc(false);
		test.saveAllTestMFD_Plots(false);
		test.saveAllTestMFD_Plots(true);
		System.out.println("DONE");
		
	}

}
