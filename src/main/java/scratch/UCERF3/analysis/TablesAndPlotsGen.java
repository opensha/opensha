package scratch.UCERF3.analysis;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.dom4j.DocumentException;
import org.jfree.chart.plot.DatasetRenderingOrder;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.HistogramFunction;
import org.opensha.commons.data.function.XY_DataSetList;
import org.opensha.commons.data.region.CaliforniaRegions;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.Region;
import org.opensha.commons.gui.plot.GraphWindow;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotElement;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSymbol;
import org.opensha.commons.util.ClassUtils;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.calc.recurInterval.BPT_DistCalc;
import org.opensha.sha.earthquake.param.ApplyGardnerKnopoffAftershockFilterParam;
import org.opensha.sha.earthquake.param.IncludeBackgroundOption;
import org.opensha.sha.earthquake.param.IncludeBackgroundParam;

import scratch.UCERF3.AverageFaultSystemSolution;
import scratch.UCERF3.CompoundFaultSystemSolution;
import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.analysis.CompoundFSSPlots.MapBasedPlot;
import scratch.UCERF3.analysis.CompoundFSSPlots.MapPlotData;
import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.enumTreeBranches.InversionModels;
import scratch.UCERF3.enumTreeBranches.MaxMagOffFault;
import scratch.UCERF3.enumTreeBranches.MomentRateFixes;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;
import scratch.UCERF3.enumTreeBranches.SlipAlongRuptureModels;
import scratch.UCERF3.enumTreeBranches.SpatialSeisPDF;
import scratch.UCERF3.enumTreeBranches.TotalMag5Rate;
import scratch.UCERF3.erf.FaultSystemSolutionERF;
import scratch.UCERF3.inversion.CommandLineInversionRunner;
import scratch.UCERF3.inversion.InversionFaultSystemRupSet;
import scratch.UCERF3.inversion.InversionFaultSystemRupSetFactory;
import scratch.UCERF3.logicTree.APrioriBranchWeightProvider;
import scratch.UCERF3.logicTree.LogicTreeBranch;
import scratch.UCERF3.logicTree.LogicTreeBranchNode;
import scratch.UCERF3.utils.DeformationModelFetcher;
import scratch.UCERF3.utils.FaultSystemIO;
import scratch.UCERF3.utils.UCERF3_DataUtils;
import scratch.UCERF3.utils.aveSlip.AveSlipConstraint;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class TablesAndPlotsGen {
	
	
	
	/**
	 * This creates the Average Slip data table for the report with columns for each Deformation Model.
	 * 
	 * @param outputFile if null output will be written to the console, otherwise written to the given file
	 * @throws IOException
	 */
	public static void buildAveSlipDataTable(File outputFile) throws IOException {
		boolean includeUCERF2 = true;
		
		List<DeformationModels> dms = Lists.newArrayList();
		if (includeUCERF2)
			dms.add(DeformationModels.UCERF2_ALL);
		dms.add(DeformationModels.ABM);
		dms.add(DeformationModels.GEOLOGIC);
		dms.add(DeformationModels.NEOKINEMA);
		dms.add(DeformationModels.ZENGBB);
		
		Map<FaultModels, List<AveSlipConstraint>> aveSlipConstraints = Maps.newHashMap();
		Map<FaultModels, List<FaultSectionPrefData>> subSectDatasMap = Maps.newHashMap();
		
		List<double[]> dmReducedSlipRates = Lists.newArrayList();
		
		List<String> header = Lists.newArrayList("FM 3.1 Mapping", "Latitude", "Longitude", "Weighted Mean");
		for (DeformationModels dm : dms) {
			FaultModels fm;
			if (dm == DeformationModels.UCERF2_ALL)
				fm = FaultModels.FM2_1;
			else
				fm = FaultModels.FM3_1;
			InversionFaultSystemRupSet rupSet = InversionFaultSystemRupSetFactory.forBranch(
					InversionModels.CHAR_CONSTRAINED, fm, dm);
			dmReducedSlipRates.add(rupSet.getSlipRateForAllSections());
			if (!aveSlipConstraints.containsKey(fm))
				aveSlipConstraints.put(fm, AveSlipConstraint.load(rupSet.getFaultSectionDataList()));
			if (!subSectDatasMap.containsKey(fm))
				subSectDatasMap.put(fm, rupSet.getFaultSectionDataList());
			
			header.add(dm.getName()+" Reduced Slip Rate");
			header.add(dm.getName()+" Proxy Event Rate");
		}
		
		CSVFile<String> csv = new CSVFile<String>(true);
		csv.addLine(header);
		
		List<AveSlipConstraint> fm2Constraints = aveSlipConstraints.get(FaultModels.FM2_1);
		List<AveSlipConstraint> fm3Constraints = aveSlipConstraints.get(FaultModels.FM3_1);
		
		for (AveSlipConstraint constr : fm3Constraints) {
			List<String> line = Lists.newArrayList();
			
			String subSectName = subSectDatasMap.get(FaultModels.FM3_1).get(constr.getSubSectionIndex()).getSectionName();
			line.add(subSectName);
			line.add(constr.getSiteLocation().getLatitude()+"");
			line.add(constr.getSiteLocation().getLongitude()+"");
			line.add(constr.getWeightedMean()+"");
			for (int i=0; i<dms.size(); i++) {
				DeformationModels dm = dms.get(i);
				
				AveSlipConstraint myConstr = null;
				if (dm == DeformationModels.UCERF2_ALL) {
					// find the equivelant ave slip constraint by comparing locations as the list may be of different
					// size (such as with Compton not existing in FM2.1)
					for (AveSlipConstraint u2Constr : fm2Constraints) {
						if (u2Constr.getSiteLocation().equals(constr.getSiteLocation())) {
							myConstr = u2Constr;
							break;
						}
					}
				} else {
					myConstr = constr;
				}
				
				if (myConstr == null) {
					line.add("");
					line.add("");
				} else {
					double reducedSlip = dmReducedSlipRates.get(i)[myConstr.getSubSectionIndex()];
					line.add(reducedSlip+"");
					double proxyRate = reducedSlip / myConstr.getWeightedMean();
					line.add(proxyRate+"");
				}
			}
			csv.addLine(line);
		}
		
		// TODO add notes:
		//		reduced for char branch
		//		lat/lon: center points of sub section
		
		if (outputFile == null) {
			// print it
			for (List<String> line : csv) {
				System.out.println(Joiner.on('\t').join(line));
			}
		} else {
			// write it
			csv.writeToFile(outputFile);
		}
	}
	
	
	public static void makePreInversionMFDsFig() {
		InversionFaultSystemRupSet rupSet = InversionFaultSystemRupSetFactory.forBranch(FaultModels.FM3_1, DeformationModels.ZENG, 
				InversionModels.CHAR_CONSTRAINED, ScalingRelationships.SHAW_2009_MOD, SlipAlongRuptureModels.TAPERED, 
				TotalMag5Rate.RATE_7p9, MaxMagOffFault.MAG_7p6, MomentRateFixes.NONE, SpatialSeisPDF.UCERF3);
		System.out.println(rupSet.getPreInversionAnalysisData(true));
		FaultSystemRupSetCalc.plotPreInversionMFDs(rupSet, false, false, true, "preInvCharMFDs.pdf");
		
		rupSet = InversionFaultSystemRupSetFactory.forBranch(FaultModels.FM3_1, DeformationModels.ZENG, 
				InversionModels.GR_CONSTRAINED, ScalingRelationships.SHAW_2009_MOD, SlipAlongRuptureModels.TAPERED, 
				TotalMag5Rate.RATE_7p9, MaxMagOffFault.MAG_7p6, MomentRateFixes.NONE, SpatialSeisPDF.UCERF3);
		FaultSystemRupSetCalc.plotPreInversionMFDs(rupSet, false, false, false, "preInvGR_MFDs.pdf");
		
		rupSet = InversionFaultSystemRupSetFactory.forBranch(FaultModels.FM3_1, DeformationModels.ZENG, 
				InversionModels.GR_CONSTRAINED, ScalingRelationships.SHAW_2009_MOD, SlipAlongRuptureModels.TAPERED, 
				TotalMag5Rate.RATE_7p9, MaxMagOffFault.MAG_7p6, MomentRateFixes.APPLY_IMPLIED_CC, SpatialSeisPDF.UCERF3);
		FaultSystemRupSetCalc.plotPreInversionMFDs(rupSet, false, false, false, "preInvGR_MFDs_applCC.pdf");
	}
	
	
	
	public static void makeDefModSlipRateMaps() {
		Region region = new CaliforniaRegions.RELM_TESTING();
		File saveDir = GMT_CA_Maps.GMT_DIR;
		boolean display = true;
		try {
			FaultBasedMapGen.plotDeformationModelSlips(region, saveDir, display);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}
	
	
	public static void makeParentSectConvergenceTable(
			File csvOutputFile, AverageFaultSystemSolution aveSol, int parentSectionID) throws IOException {
		List<Integer> rups = aveSol.getRupSet().getRupturesForParentSection(parentSectionID);
		
		CSVFile<String> csv = new CSVFile<String>(true);
		
		int numSols = aveSol.getNumSolutions();
		
		List<String> header = Lists.newArrayList();
		header.add("Rup ID");
		header.add("Mag");
		header.add("Length (km)");
		header.add("Start Sect");
		header.add("End Sect");
		header.add("Mean Rate");
		header.add("Min Rate");
		header.add("Max Rate");
		header.add("Std Dev Rate");
		for (int i=0; i<numSols; i++)
			header.add("Rate #"+i);
		
		csv.addLine(header);
		
		for (int rup : rups) {
			List<String> line = Lists.newArrayList();
			
			List<FaultSectionPrefData> sects = aveSol.getRupSet().getFaultSectionDataForRupture(rup);
			
			double[] rates = aveSol.getRatesForAllSols(rup);
			
			line.add(rup+"");
			line.add(aveSol.getRupSet().getMagForRup(rup)+"");
			line.add(aveSol.getRupSet().getLengthForRup(rup)/1000d+""); // m => km
			line.add(sects.get(0).getSectionName());
			line.add(sects.get(sects.size()-1).getSectionName());
			line.add(aveSol.getRateForRup(rup)+"");
			line.add(aveSol.getRateMin(rup)+"");
			line.add(aveSol.getRateMax(rup)+"");
			line.add(aveSol.getRateStdDev(rup)+"");
			
			for (int i=0; i<rates.length; i++)
				line.add(rates[i]+"");
			
			csv.addLine(line);
		}
		
		csv.writeToFile(csvOutputFile);
	}
	
	private static final String FAULT_SUPRA_TARGET = "Fault Target Supra Seis Moment Rate";
	private static final String FAULT_SUPRA_SOLUTION = "Fault Solution Supra Seis Moment Rate";
	private static final String FAULT_SUB_TARGET = "Fault Target Sub Seis Moment Rate";
	private static final String FAULT_SUB_SOLUTION = "Fault Solution Sub Seis Moment Rate";
	private static final String TRULY_OFF_TARGET = "Truly Off Fault Target Moment Rate";
	private static final String TRULY_OFF_SOLUTION = "Truly Off Fault Solution Moment Rate";
	
	/**
	 * This writes the moment rates table to a CSV file for the given CompoundFaultSystemSolution
	 * @param cfss
	 * @param csvFile
	 * @throws IOException
	 */
	public static void makeCompoundFSSMomentRatesTable(CompoundFaultSystemSolution cfss, File csvFile)
			throws IOException {
		
		CSVFile<String> csv = new CSVFile<String>(true);
		
		List<String> header = Lists.newArrayList();
		for (Class<? extends LogicTreeBranchNode<?>> clazz : LogicTreeBranch.getLogicTreeNodeClasses())
			header.add(ClassUtils.getClassNameWithoutPackage(clazz));
		header.add("A Priori Branch Weight");
		header.add(FAULT_SUPRA_TARGET);
		header.add(FAULT_SUPRA_SOLUTION);
		header.add(FAULT_SUB_TARGET);
		header.add(FAULT_SUB_SOLUTION);
		header.add(TRULY_OFF_TARGET);
		header.add(TRULY_OFF_SOLUTION);
		
		csv.addLine(header);
		
		List<LogicTreeBranch> branches = Lists.newArrayList(cfss.getBranches());
		Collections.sort(branches);
		
		Splitter sp = Splitter.on("\n");
		
		APrioriBranchWeightProvider weightProv = new APrioriBranchWeightProvider();
		Map<DeformationModels, List<Double>> dmWeightsMap = Maps.newHashMap();
		double totWt = 0;
		for (LogicTreeBranch branch : branches) {
			double weight = weightProv.getWeight(branch);
			totWt += weight;
			DeformationModels dm = branch.getValue(DeformationModels.class);
			List<Double> dmWeights = dmWeightsMap.get(dm);
			if (dmWeights == null) {
				dmWeights = Lists.newArrayList();
				dmWeightsMap.put(dm, dmWeights);
			}
			dmWeights.add(weight);
		}
		Map<DeformationModels, Double> dmWeightTotsMap = Maps.newHashMap();
		for (DeformationModels dm : dmWeightsMap.keySet()) {
			double dmTotWt = 0;
			for (double weight : dmWeightsMap.get(dm))
				dmTotWt += weight;
			dmWeightTotsMap.put(dm, dmTotWt);
		}
		
		double hist_min = 0;
		double hist_max = 3;
		double hist_delta = 0.1;
		int hist_num = (int)((hist_max-hist_min)/hist_delta+1);
		
		HistogramFunction totOffHist = new HistogramFunction(hist_min, hist_num, hist_delta);
		Map<DeformationModels, DiscretizedFunc> dmOffHistMap = Maps.newHashMap();
		for (DeformationModels dm : dmWeightTotsMap.keySet())
			dmOffHistMap.put(dm, new HistogramFunction(hist_min, hist_num, hist_delta));
		
		HistogramFunction totOnHist = new HistogramFunction(hist_min, hist_num, hist_delta);
		Map<DeformationModels, DiscretizedFunc> dmOnHistMap = Maps.newHashMap();
		for (DeformationModels dm : dmWeightTotsMap.keySet())
			dmOnHistMap.put(dm, new HistogramFunction(hist_min, hist_num, hist_delta));
		
		HistogramFunction totTotHist = new HistogramFunction(hist_min, hist_num, hist_delta);
		Map<DeformationModels, DiscretizedFunc> dmTotHistMap = Maps.newHashMap();
		for (DeformationModels dm : dmWeightTotsMap.keySet())
			dmTotHistMap.put(dm, new HistogramFunction(hist_min, hist_num, hist_delta));
		
		for (LogicTreeBranch branch : branches) {
			DeformationModels dm = branch.getValue(DeformationModels.class);
			List<String> line = Lists.newArrayList();
			for (int i=0; i<LogicTreeBranch.getLogicTreeNodeClasses().size(); i++)
				line.add(branch.getValue(i).getShortName());
			List<String> info = Lists.newArrayList(sp.split(cfss.getInfo(branch)));
			double origWt = weightProv.getWeight(branch);
			double totScaledWt = origWt/totWt;
			double dmScaledWt = origWt/dmWeightTotsMap.get(dm);
			line.add(totScaledWt+"");
			double supra = getField(info, FAULT_SUPRA_SOLUTION);
			double sub = getField(info, FAULT_SUB_SOLUTION);
			double off = getField(info, TRULY_OFF_SOLUTION);
			line.add(getField(info, FAULT_SUPRA_TARGET)+"");
			line.add(supra+"");
			line.add(getField(info, FAULT_SUB_TARGET)+"");
			line.add(sub+"");
			line.add(getField(info, TRULY_OFF_TARGET)+"");
			line.add(off+"");
			
			double tot = supra+sub+off;
			double onTot = sub+supra;
			
			totTotHist.add(tot/1e19, totScaledWt);
			((HistogramFunction)dmTotHistMap.get(dm)).add(tot/1e19, totScaledWt);
			
			totOnHist.add(onTot/1e19, totScaledWt);
			((HistogramFunction)dmOnHistMap.get(dm)).add(onTot/1e19, totScaledWt);
			
			totOffHist.add(off/1e19, totScaledWt);
			((HistogramFunction)dmOffHistMap.get(dm)).add(off/1e19, totScaledWt);
			
			csv.addLine(line);
		}
		
		csv.writeToFile(csvFile);
		
		Color[] dmColors = { Color.RED, Color.ORANGE, Color.GREEN, Color.MAGENTA };
		List<DeformationModels> dms = Lists.newArrayList(dmTotHistMap.keySet());
		Collections.sort(dms, new Comparator<DeformationModels>() {

			@Override
			public int compare(DeformationModels o1, DeformationModels o2) {
				return o1.name().compareTo(o2.name());
			}
		});
		
		String name = csvFile.getName();
		if (name.contains("_mo_rates.csv"))
			name = name.substring(0, name.indexOf("_mo_rates"));
		if (name.contains(".csv"))
			name = name.substring(0, name.indexOf(".csv"));
		
		Map<DeformationModels, Double> dmTotTargets = Maps.newHashMap();
		Map<DeformationModels, Double> dmOnTargets = Maps.newHashMap();
		Map<DeformationModels, Double> dmOffTargets = Maps.newHashMap();
		
		FaultModels[] fms = { FaultModels.FM3_1, FaultModels.FM3_2 };
		for (DeformationModels dm : dms) {
			double dmTotTarget = 0;
			double dmOnTarget = 0;
			double dmOffTarget = 0;
			
			for (FaultModels fm : fms) {
				DeformationModelFetcher defFetch = new DeformationModelFetcher(fm, dm, UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR, InversionFaultSystemRupSetFactory.DEFAULT_ASEIS_VALUE);
				double moRate = DeformationModelsCalc.calculateTotalMomentRate(defFetch.getSubSectionList(),true);
//				System.out.println(fm.getName()+", "+dm.getName()+ " (reduced):\t"+(float)moRate);
//				System.out.println(fm.getName()+", "+dm.getName()+ " (not reduced):\t"+(float)DeformationModelsCalc.calculateTotalMomentRate(defFetch.getSubSectionList(),false));
				double moRateOffFaults = DeformationModelsCalc.calcMoRateOffFaultsForDefModel(fm, dm);
				double totMoRate = moRate+moRateOffFaults;
				
				dmTotTarget += totMoRate;
				dmOffTarget += moRateOffFaults;
				dmOnTarget += moRate;
			}
			
			dmTotTarget /= (double)fms.length;
			dmOnTarget /= (double)fms.length;
			dmOffTarget /= (double)fms.length;
			
			dmTotTargets.put(dm, dmTotTarget/1e19);
			dmOnTargets.put(dm, dmOnTarget/1e19);
			dmOffTargets.put(dm, dmOffTarget/1e19);
		}
		
//		writeMoRateHist(totTotHist, dmTotHistMap, dmTotTargets, dms, dmColors,
//				name+"_mo_rate_dist_tot", csvFile.getParentFile(), "Total", plotXLog);
//		writeMoRateHist(totOnHist, dmOnHistMap, dmOnTargets, dms, dmColors,
//				name+"_mo_rate_dist_on", csvFile.getParentFile(), "On Fault", plotXLog);
//		writeMoRateHist(totOffHist, dmOffHistMap, dmOffTargets, dms, dmColors,
//				name+"_mo_rate_dist_off", csvFile.getParentFile(), "Off Fault", plotXLog);
		
		File dir = csvFile.getParentFile();
		
		double minX = 0d;
		double maxX = 3;
		double minY = 0;
		double maxY = 0.525;
		
		ArrayList<DiscretizedFunc> funcs = Lists.newArrayList();
		ArrayList<PlotCurveCharacterstics> chars = Lists.newArrayList();
		
		// first make combined on/off hist
		
		totOnHist.setName("On Fault Histogram");
		totOnHist.setInfo("Mean: "+totOnHist.computeMean()+"\nStd Dev: "+totOnHist.computeStdDev());
		funcs.add(totOnHist);
		chars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 10f, Color.BLUE));
		
		for (int i=0; i<dms.size(); i++) {
			DeformationModels dm = dms.get(i);
			Color color = dmColors[i];
			
			DiscretizedFunc func = dmOnHistMap.get(dm);
			func.setName(dm.getName());
			funcs.add(func);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, color));
			
//			if (dm == DeformationModels.GEOLOGIC && !type.equals("On Fault"))
//				continue;
			
			double target = dmOnTargets.get(dm);
			ArbitrarilyDiscretizedFunc targetLine = new ArbitrarilyDiscretizedFunc();
			targetLine.set(target, 0d);
			targetLine.set(target*1.0001, maxY);
			targetLine.setName(dm.getName()+" On Fault Target");
			funcs.add(targetLine);
			System.out.println("On Fault Target ("+dm.name()+"): "+target);
			chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 2f, color));
		}
		
		totOffHist.setName("Off Fault Histogram");
		totOffHist.setInfo("Mean: "+totOffHist.computeMean()+"\nStd Dev: "+totOffHist.computeStdDev());
		funcs.add(totOffHist);
		chars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 10f, Color.GRAY));
		
		for (int i=0; i<dms.size(); i++) {
			DeformationModels dm = dms.get(i);
			Color color = dmColors[i];
			
			DiscretizedFunc func = dmOffHistMap.get(dm);
			func.setName(dm.getName());
			funcs.add(func);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, color));
			
			if (dm == DeformationModels.GEOLOGIC)
				continue;
			
			double target = dmOffTargets.get(dm);
			ArbitrarilyDiscretizedFunc targetLine = new ArbitrarilyDiscretizedFunc();
			targetLine.set(target, 0d);
			targetLine.set(target*1.0001, maxY);
			targetLine.setName(dm.getName()+" Off Fault Target");
			funcs.add(targetLine);
			System.out.println("Off Fault Target ("+dm.name()+"): "+target);
			chars.add(new PlotCurveCharacterstics(PlotLineType.DOTTED, 2f, color));
		}
		
		HeadlessGraphPanel gp = new HeadlessGraphPanel();
		CommandLineInversionRunner.setFontSizes(gp);
		gp.setBackgroundColor(Color.WHITE);
//		gp.setRenderingOrder(DatasetRenderingOrder.REVERSE);
		gp.setUserBounds(minX, maxX, minY, maxY);
		gp.drawGraphPanel("Moment Rate (10^19 Nm/yr)", "Branch Weight", funcs, chars,
				"Moment Rate Distribution");
		
		File outputFile = new File(dir, name+"_mo_rate_dist_on_off");
		gp.getChartPanel().setSize(1000, 800);
		gp.saveAsPNG(outputFile.getAbsolutePath()+".png");
		gp.saveAsPDF(outputFile.getAbsolutePath()+".pdf");
		gp.saveAsTXT(outputFile.getAbsolutePath()+".txt");
		outputFile = new File(outputFile.getAbsolutePath()+"_small");
		gp.getChartPanel().setSize(500, 400);
		gp.saveAsPNG(outputFile.getAbsolutePath()+".png");
		gp.saveAsPDF(outputFile.getAbsolutePath()+".pdf");
		gp.saveAsTXT(outputFile.getAbsolutePath()+".txt");
		
		// total
		funcs = Lists.newArrayList();
		chars = Lists.newArrayList();
		
		totTotHist.setName("Total Histogram");
		totTotHist.setInfo("Mean: "+totTotHist.computeMean()+"\nStd Dev: "+totTotHist.computeStdDev());
		funcs.add(totTotHist);
		chars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 10f, Color.BLACK));
		
		for (int i=0; i<dms.size(); i++) {
			DeformationModels dm = dms.get(i);
			Color color = dmColors[i];
			
			DiscretizedFunc func = dmTotHistMap.get(dm);
			func.setName(dm.getName());
			funcs.add(func);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, color));
			
			if (dm == DeformationModels.GEOLOGIC)
				continue;
			
			double target = dmTotTargets.get(dm);
			ArbitrarilyDiscretizedFunc targetLine = new ArbitrarilyDiscretizedFunc();
			targetLine.set(target, 0d);
			targetLine.set(target*1.0001, maxY);
			targetLine.setName(dm.getName()+" Total Target");
			funcs.add(targetLine);
			System.out.println("Total Target ("+dm.name()+"): "+target);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, color));
		}
		
		gp = new HeadlessGraphPanel();
		CommandLineInversionRunner.setFontSizes(gp);
		gp.setBackgroundColor(Color.WHITE);
//		gp.setRenderingOrder(DatasetRenderingOrder.REVERSE);
		gp.setUserBounds(minX, maxX, minY, maxY);
		gp.drawGraphPanel("Moment Rate (10^19 Nm/yr)", "Branch Weight", funcs, chars,
				"Moment Rate Distribution");
		
		outputFile = new File(dir, name+"_mo_rate_dist_total");
		gp.getChartPanel().setSize(1000, 800);
		gp.saveAsPNG(outputFile.getAbsolutePath()+".png");
		gp.saveAsPDF(outputFile.getAbsolutePath()+".pdf");
		gp.saveAsTXT(outputFile.getAbsolutePath()+".txt");
		outputFile = new File(outputFile.getAbsolutePath()+"_small");
		gp.getChartPanel().setSize(500, 400);
		gp.saveAsPNG(outputFile.getAbsolutePath()+".png");
		gp.saveAsPDF(outputFile.getAbsolutePath()+".pdf");
		gp.saveAsTXT(outputFile.getAbsolutePath()+".txt");
	}
	
	private static double getField(List<String> infoLines, String fieldStart) {
		for (String infoLine : infoLines) {
			infoLine = infoLine.trim();
			if (infoLine.startsWith(fieldStart))
				return Double.parseDouble(infoLine.substring(infoLine.lastIndexOf(" ")+1));
		}
		return Double.NaN;
	}
	
	private static HistogramFunction loadSurfaceRupData() throws IOException {
		POIFSFileSystem fs = new POIFSFileSystem(
				UCERF3_DataUtils.locateResourceAsStream("misc", "Surface_Rupture_Data_Wells_043013.xls"));
		HSSFWorkbook wb = new HSSFWorkbook(fs);
		HSSFSheet sheet = wb.getSheetAt(0);
		
		int allSRL_col = 28;
		
		HistogramFunction hist = buildEmptyLengthHist();
		int cnt = 0;
		
		for (int rowIndex=0; rowIndex<=sheet.getLastRowNum(); rowIndex++) {
			HSSFRow row = sheet.getRow(rowIndex);
			if (row == null)
				continue;
			HSSFCell cell = row.getCell(allSRL_col);
			if (cell == null || cell.getCellType() != HSSFCell.CELL_TYPE_NUMERIC)
				continue;
			double length = cell.getNumericCellValue();
			hist.add(length, 1d);
			cnt++;
		}
		System.out.println("Loaded "+cnt+" values from data file");
		hist.normalizeBySumOfY_Vals();
		return hist;
	}
	
	private static HistogramFunction buildEmptyLengthHist() {
		// should be big enough for all UCERF3 ruptures
		return new HistogramFunction(25, 25, 50d);
	}
	
	public static void buildRupLengthComparisonPlot(CompoundFaultSystemSolution cfss, File dir, String prefix) throws IOException {
		List<HistogramFunction> hists = Lists.newArrayList();
		
		for (LogicTreeBranch branch : cfss.getBranches()) {
			double[] lengths = cfss.getLengths(branch);
			double[] rates = cfss.getRates(branch);
			
			HistogramFunction hist = buildEmptyLengthHist();
			
			for (int r=0; r<lengths.length; r++) {
				hist.add(lengths[r]/1000d, rates[r]);
			}
			hist.normalizeBySumOfY_Vals();
			
			hists.add(hist);
		}
		
		HistogramFunction data = loadSurfaceRupData();
		
		XY_DataSetList xyList = new XY_DataSetList();
		for (HistogramFunction hist : hists) {
			xyList.add(hist);
		}
		APrioriBranchWeightProvider weightProv = new APrioriBranchWeightProvider();
		List<Double> weights = Lists.newArrayList();
		for (LogicTreeBranch branch : cfss.getBranches())
			weights.add(weightProv.getWeight(branch));
		
		List<DiscretizedFunc> solFuncs = CompoundFSSPlots.getFractiles(xyList, weights, "Surface Rupture Length", new double[0]);
		List<PlotCurveCharacterstics> solChars = CompoundFSSPlots.getFractileChars(Color.RED, 0);
		
		ArrayList<DiscretizedFunc> funcs = Lists.newArrayList();
		ArrayList<PlotCurveCharacterstics> chars = Lists.newArrayList();
		funcs.addAll(solFuncs);
		chars.addAll(solChars);
		
		funcs.add(data);
		chars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 1f, Color.BLUE));
		
		HeadlessGraphPanel gp = new HeadlessGraphPanel();
		CommandLineInversionRunner.setFontSizes(gp);
		gp.setBackgroundColor(Color.WHITE);
		gp.setRenderingOrder(DatasetRenderingOrder.REVERSE);
		gp.setUserBounds(0d, 500d, 0d, 1d);
		gp.drawGraphPanel("Rupture Length (km)", "Fraction of Earthquakes", funcs, chars,
				"Rupture Length Distribution");
		File outputFile = new File(dir, prefix+"_length_dists");
		gp.getChartPanel().setSize(1000, 800);
		gp.saveAsPNG(outputFile.getAbsolutePath()+".png");
		gp.saveAsPDF(outputFile.getAbsolutePath()+".pdf");
		gp.saveAsTXT(outputFile.getAbsolutePath()+".txt");
	}
	
	public static void makeNumRunsForRateWithin10Plot(
			AverageFaultSystemSolution avgSol, File outputDir, String prefix) throws IOException {
		DefaultXY_DataSet scatter = new DefaultXY_DataSet();
		
		for (int r=0; r<avgSol.getRupSet().getNumRuptures(); r++) {
			double s = avgSol.getRateStdDev(r);
			double m = avgSol.getRateForRup(r);
			
			double n = Math.pow(19.6*s/m, 2);
			
			scatter.set(m, n);
		}
		
		ArrayList<PlotElement> funcs = Lists.newArrayList();
		funcs.add(scatter);
		ArrayList<PlotCurveCharacterstics> chars = Lists.newArrayList();
		chars.add(new PlotCurveCharacterstics(PlotSymbol.CROSS, 3f, Color.BLACK));
		
		// we want to skip the ones at 1e-16
		double minX = Double.POSITIVE_INFINITY;
		double minY = Double.POSITIVE_INFINITY;
		for (Point2D pt : scatter) {
			double x = pt.getX();
			double y = pt.getY();
			if (x < 1e-14 || y < 1e-14)
				continue;
			if (x < minX)
				minX = x;
			if (y < minY)
				minY = y;
		}
		System.out.println("mins: "+minX+", "+minY);
		
		HeadlessGraphPanel gp = new HeadlessGraphPanel();
		CommandLineInversionRunner.setFontSizes(gp);
		gp.setBackgroundColor(Color.WHITE);
		gp.setXLog(true);
		gp.setYLog(true);
		gp.setUserBounds(minX, scatter.getMaxX(), minY, scatter.getMaxY());
		gp.drawGraphPanel("Mean Rupture Rate", "N for 95%-Conf within 10%", funcs, chars,
				"");
		File file = new File(outputDir, prefix);
		gp.getChartPanel().setSize(1000, 800);
		gp.saveAsPNG(file.getAbsolutePath()+".png");
		gp.saveAsPDF(file.getAbsolutePath()+".pdf");
		file = new File(outputDir, prefix+"_small");
		gp.getChartPanel().setSize(500, 400);
		gp.saveAsPDF(file.getAbsolutePath()+".pdf");
		gp.saveAsPNG(file.getAbsolutePath()+".png");
//		gp.saveAsTXT(file.getAbsolutePath()+".txt");
	}
	
	public static void makeSlipMisfitHistograms(File u3XMLFile, File u2XMLFile, File outputDir)
			throws DocumentException, IOException {
		boolean norm = true;
		
		List<MapPlotData> u3Datas = MapBasedPlot.loadPlotData(u3XMLFile);
		List<MapPlotData> u2Datas = MapBasedPlot.loadPlotData(u2XMLFile);
		
		MapPlotData u3Misfits = null;
		for (MapPlotData data : u3Datas) {
			String name = data.getFileName();
			if (name.endsWith("slip_rate_misfit")) {
				if (name.startsWith("FM") && !name.startsWith("FM3_1"))
					continue;
				u3Misfits = data;
			}
		}
		Preconditions.checkNotNull(u3Misfits,
				"Couldn't find slip misfit data in xml file: "+u3XMLFile.getAbsolutePath());
		
		MapPlotData u2Misfits = null;
		for (MapPlotData data : u2Datas) {
			String name = data.getFileName();
			if (name.endsWith("slip_rate_misfit")) {
				u2Misfits = data;
			}
		}
		Preconditions.checkNotNull(u2Misfits,
				"Couldn't find slip misfit data in xml file: "+u2XMLFile.getAbsolutePath());
		
		int numHist = 100;
		double histDelta = 0.05;
		
		HistogramFunction u2Hist = new HistogramFunction(0d, numHist, histDelta);
		HistogramFunction u3Hist = new HistogramFunction(0d, numHist, histDelta);
		
		
		List<LocationList> u2SkipTraces = null;
		// for skipping San Gregorio and Little Salmon
		List<Integer> u2SkipParentIDs = Lists.newArrayList(12, 29, 16, 17);
//		List<Integer> u2SkipParentIDs = null;
		
		if (u2SkipParentIDs != null && !u2SkipParentIDs.isEmpty()) {
			u2SkipTraces = Lists.newArrayList();
			List<FaultSectionPrefData> u2SubSects = new DeformationModelFetcher(
					FaultModels.FM2_1, DeformationModels.UCERF2_ALL,
					UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR, 0.1).getSubSectionList();
			for (FaultSectionPrefData subSect : u2SubSects) {
				if (u2SkipParentIDs.contains(subSect.getParentSectionId()))
					u2SkipTraces.add(subSect.getFaultTrace());
			}
		}
		
		FileWriter u2FW = new FileWriter(new File(outputDir, "slip_misfit_u2_vals.txt"));
		FileWriter u3FW = new FileWriter(new File(outputDir, "slip_misfit_u3_vals.txt"));
		
		mainLoop:
		for (int f=0; f<u2Misfits.getFaults().size(); f++) {
			double value = u2Misfits.getFaultValues()[f];
			if (u2SkipTraces != null) {
				LocationList trace = u2Misfits.getFaults().get(f);
				traceLoop:
				for (LocationList oTrace : u2SkipTraces) {
					for (int i=0; i<trace.size(); i++) {
						if (!oTrace.get(i).equals(trace.get(i)))
							continue traceLoop;
					}
					System.out.println("Skipping a sub sect!");
					// it's a match!
					continue mainLoop;
				}
			}
			u2Hist.add(Math.abs(value), 1d);
			u2FW.write(Math.abs(value)+"\n");
		}
		
		for (double value : u3Misfits.getFaultValues()) {
			u3Hist.add(Math.abs(value), 1d);
			u3FW.write(Math.abs(value)+"\n");
		}
		
		u2FW.close();
		u3FW.close();
		
		if (norm) {
			u3Hist.normalizeBySumOfY_Vals();
			u2Hist.normalizeBySumOfY_Vals();
		}
		
		u2Hist.setName("UCERF2 Slip Rate Misfit Histogram");
		u2Hist.setInfo("Mean: "+u2Hist.computeMean()+"\nStd Dev: "+u2Hist.computeStdDev());
		u3Hist.setName("UCERF3 Slip Rate Misfit Histogram");
		u3Hist.setInfo("Mean: "+u3Hist.computeMean()+"\nStd Dev: "+u3Hist.computeStdDev());
		
		ArrayList<DiscretizedFunc> funcs = Lists.newArrayList();
		funcs.add(u3Hist);
		funcs.add(u2Hist);
		ArrayList<PlotCurveCharacterstics> chars = Lists.newArrayList();
		chars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 1f, Color.BLACK));
		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.RED));
		
		HeadlessGraphPanel gp = new HeadlessGraphPanel();
		CommandLineInversionRunner.setFontSizes(gp);
		gp.setBackgroundColor(Color.WHITE);
		gp.setXLog(false);
		gp.setYLog(false);
		gp.setUserBounds(0, 3d, 0, u3Hist.getMaxY()*1.1);
		String yAxisLabel;
		if (norm)
			yAxisLabel = "Fraction";
		else
			yAxisLabel = "Number";
		gp.drawGraphPanel("Subsection Slip Rate Misfit (solution/target)", yAxisLabel, funcs, chars,
				"Slip Rate Misfits");
		File file = new File(outputDir, "slip_misfit_hist");
		gp.getChartPanel().setSize(1000, 800);
		gp.saveAsPNG(file.getAbsolutePath()+".png");
		gp.saveAsPDF(file.getAbsolutePath()+".pdf");
		gp.saveAsTXT(file.getAbsolutePath()+".txt");
		file = new File(file.getAbsolutePath()+"_small");
		gp.getChartPanel().setSize(500, 400);
		gp.saveAsPDF(file.getAbsolutePath()+".pdf");
		gp.saveAsPNG(file.getAbsolutePath()+".png");
	}
	
	public static void makeRenewalModelNoDateOfLastPlots() {
		BPT_DistCalc bptCalc = new BPT_DistCalc();
		
		double mean = 100;
		double deltaX = 0.01;
		int numPoints = (int)Math.round(5*mean/deltaX);
		double histOpenInterval=0;
//		double aperiodicity = 0.3;
		double[] aperArray = {0.2,0.7};
		PlotLineType[] lineType = {PlotLineType.DASHED, PlotLineType.SOLID};

		
		ArrayList<DiscretizedFunc> funcList = new ArrayList<DiscretizedFunc>();
		ArrayList<PlotCurveCharacterstics> plotChars = new ArrayList<PlotCurveCharacterstics>();

		int index = -1;
		for(double aperiodicity:aperArray) {
			index += 1;
			EvenlyDiscretizedFunc logX_func = new EvenlyDiscretizedFunc(Math.log10(0.01), Math.log10(4), 100);	// x-axis is log10(duration/mean)
			ArbitrarilyDiscretizedFunc bpt_func = new ArbitrarilyDiscretizedFunc();
			bpt_func.setName("bpt_func");
			ArbitrarilyDiscretizedFunc poisN1_func = new ArbitrarilyDiscretizedFunc();
			poisN1_func.setName("poisN1_func");

			ArbitrarilyDiscretizedFunc poisNge1_func = new ArbitrarilyDiscretizedFunc();
			poisNge1_func.setName("poisNge1_func");

			ArbitrarilyDiscretizedFunc bpt_poisNge1_ratio_func = new ArbitrarilyDiscretizedFunc();

			for(int i=0;i<logX_func.size();i++) {
				double durOverMean = Math.pow(10, logX_func.getX(i));
				double duration = mean*durOverMean;
				bptCalc.setAllParameters(mean, aperiodicity, deltaX, numPoints, duration, histOpenInterval);
				double bptProb = bptCalc.getCondProbForUnknownTimeSinceLastEvent();
				bpt_func.set(durOverMean,bptProb);
				double poisNge1_prob = 1.0 - Math.exp(-durOverMean);
				poisNge1_func.set(durOverMean,poisNge1_prob);
				double poisProbOf1 = (durOverMean)*Math.exp(-durOverMean);
				poisN1_func.set(durOverMean,poisProbOf1);
				bpt_poisNge1_ratio_func.set(durOverMean,bptProb/poisNge1_prob);
			}
			
			double maxRatio = bpt_poisNge1_ratio_func.getMaxY();
			bpt_func.setInfo(bptCalc.getAdjParams().toString()+
					"\nbptProb/poisNge1_prob = 1.1 at durOverMean = "+(float)bpt_poisNge1_ratio_func.getFirstInterpolatedX(1.1)+
					"\nbptProb/poisNge1_prob for durOverMean of 1.2 (30yr Parkfiled forecast) = "+bpt_poisNge1_ratio_func.getInterpolatedY(1.2)+
					"\nbptProb/poisNge1_prob has maximum of "+maxRatio+" durOverMean="+(float)bpt_poisNge1_ratio_func.getFirstInterpolatedX(maxRatio));

			if(index == 1)	{// only do this once
				funcList.add(poisNge1_func);
				plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3, null, 0, Color.GRAY));
			}
		
			funcList.add(bpt_func);
			plotChars.add(new PlotCurveCharacterstics(lineType[index], 3, null, 0, Color.BLACK));
		}
		
		GraphWindow graph = new GraphWindow(funcList, "",plotChars);
		graph.setX_AxisRange(0, 4);
		graph.setY_AxisRange(0,1.01);
		graph.setX_AxisLabel("Duration/Mean");
		graph.setY_AxisLabel("Probability");
		graph.setTickLabelFontSize(24);
		graph.setAxisLabelFontSize(28);
		graph.setPlotLabelFontSize(18);

		String fileName = "RenewalModelNoDateOfLastPlot.pdf";
		try {
			graph.saveAsPDF(fileName);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}		
	}
	
	
	
	public static void makeRenewalModelPDF_CDF_EtcPlot() {
		BPT_DistCalc bptCalc = new BPT_DistCalc();
		double mean = 1;
		double deltaX = 0.01;
		int numPoints = (int)Math.round(4*mean/deltaX);
		double[] aperArray = {0.2,0.7};
//		Color[] color = {Color.BLACK, Color.GRAY};
		PlotLineType[] lineType = {PlotLineType.DASHED, PlotLineType.SOLID};
//		double[] aperArray = {0.3,0.5,0.7};
//		Color[] color = {Color.BLACK, Color.DARK_GRAY, Color.GRAY};
		ArrayList<DiscretizedFunc> funcList1 = new ArrayList<DiscretizedFunc>();
		ArrayList<PlotCurveCharacterstics> plotChars1 = new ArrayList<PlotCurveCharacterstics>();
		ArrayList<DiscretizedFunc> funcList2 = new ArrayList<DiscretizedFunc>();
		ArrayList<PlotCurveCharacterstics> plotChars2 = new ArrayList<PlotCurveCharacterstics>();
		int index = -1;
		for(double aperiodicity:aperArray) {
			index+=1;
			bptCalc.setAllParameters(mean, aperiodicity, deltaX, numPoints, 0.3, 0.0);
			funcList1.add(bptCalc.getPDF());
			funcList1.get(funcList1.size()-1).setName("PDF; aper=+aperiodicity");
			plotChars1.add(new PlotCurveCharacterstics(lineType[index], 3, null, 0, Color.BLACK));
			funcList2.add(bptCalc.getCDF());
			funcList2.get(funcList2.size()-1).setName("CDF; aper=+aperiodicity");
			plotChars2.add(new PlotCurveCharacterstics(lineType[index], 3, null, 0, Color.BLACK));
			funcList2.add(bptCalc.getTimeSinceLastEventPDF());
			funcList2.get(funcList2.size()-1).setName("Prob date of last; aper=+aperiodicity");
			plotChars2.add(new PlotCurveCharacterstics(lineType[index], 3, null, 0, Color.GRAY));
		}
			
		GraphWindow graph1 = new GraphWindow(funcList1, "",plotChars1);
		graph1.setX_AxisRange(0, 4);
		graph1.setY_AxisRange(0,2.1);
		graph1.setX_AxisLabel("Time Since Last Event (years)");
		graph1.setY_AxisLabel("Probability");
		graph1.setTickLabelFontSize(24);
		graph1.setAxisLabelFontSize(28);
		graph1.setPlotLabelFontSize(18);
		String fileName1 = "RenewalModelPDF.pdf";
		
		GraphWindow graph2 = new GraphWindow(funcList2, "",plotChars2);
		graph2.setX_AxisRange(0, 4);
		graph2.setY_AxisRange(0,1.05);
		graph2.setX_AxisLabel("Time Since Last Event (years)");
		graph2.setY_AxisLabel("Probability");
		graph2.setTickLabelFontSize(24);
		graph2.setAxisLabelFontSize(28);
		graph2.setPlotLabelFontSize(18);
		String fileName2 = "RenewalModelCDF_EtcPlot.pdf";

		try {
			graph1.saveAsPDF(fileName1);
			graph2.saveAsPDF(fileName2);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();

		}		
	}

	
	
	
	public static void makeRenewalModelHistoricOpenIntervalPlots() {
		BPT_DistCalc bptCalc = new BPT_DistCalc();
		
		double mean = 100;
//		double[] durationOverMeanArray = {0.001, 0.01, 0.1, 0.3, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0};
		double[] durationOverMeanArray = {0.001, 0.1, 0.3, 0.5, 0.7, 1.0};
		double deltaX = 0.01;
		int numPoints = (int)Math.round(5*mean/deltaX);
//		double[] aperArray = {0.3};
		double[] aperArray = {0.2,0.3,0.7};
		double[] yAxisMaxForPlot = {10.0,5.5,1.5};
		double[] yAxisMinForPlot = {0.0,0.5,0.95};
		

		int aperIndex = -1;
		for(double aperiodicity:aperArray) {
			aperIndex += 1;
			System.out.println("aperiodicity = "+aperiodicity);

			ArrayList<DiscretizedFunc> funcList = new ArrayList<DiscretizedFunc>();
			for(double durOverMean:durationOverMeanArray) {
				double duration = durOverMean*mean;
				bptCalc.setAllParameters(mean, aperiodicity, deltaX, numPoints, duration, 0d);
				double zeroOpenIntBPT_Prob = bptCalc.getCondProbForUnknownTimeSinceLastEvent();

				EvenlyDiscretizedFunc logX_func = new EvenlyDiscretizedFunc(Math.log10(0.0001), Math.log10(2), 100);	// x-axis is log10(openInt/mean)
				ArbitrarilyDiscretizedFunc bpt_func = new ArbitrarilyDiscretizedFunc();
				bpt_func.setName("BPT Prob Ratio for durOverMean="+durOverMean+" & aper="+aperiodicity);
				for(int i=0;i<logX_func.size();i++) {
					double openIntOverMean = Math.pow(10, logX_func.getX(i));
					double histOpenInterval = mean*openIntOverMean;
					bptCalc.setAllParameters(mean, aperiodicity, deltaX, numPoints, duration, histOpenInterval);
					double bptProb = bptCalc.getCondProbForUnknownTimeSinceLastEvent();
					bpt_func.set(openIntOverMean,bptProb/zeroOpenIntBPT_Prob);
				}
				double xAtY_1pt1=Double.NaN;
				try {
					xAtY_1pt1=bpt_func.getFirstInterpolatedX(1.1);
				} catch (Exception e) {
					// TODO Auto-generated catch block
//					e.printStackTrace();
				}
				System.out.println("\tRatio = 1.1 at x = "+xAtY_1pt1+" for durOverMean="+durOverMean);
				bpt_func.setInfo("Ratio = 1.1 at x = "+xAtY_1pt1);
				funcList.add(bpt_func);
			}
		
//			ArrayList<PlotCurveCharacterstics> plotChars = new ArrayList<PlotCurveCharacterstics>();
//			plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3, null, 0, Color.BLACK));
//			plotChars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 3, null, 0, Color.BLACK));
//			plotChars.add(new PlotCurveCharacterstics(PlotLineType.DOTTED_AND_DASHED, 3, null, 0, Color.BLACK));
//			plotChars.add(new PlotCurveCharacterstics(PlotLineType.DOTTED, 3, null, 0, Color.BLACK));
//			GraphWindow graph = new GraphWindow(funcList, "",plotChars);
			GraphWindow graph = new GraphWindow(funcList, "");
			graph.setX_AxisRange(0, 2);
			graph.setY_AxisRange(yAxisMinForPlot[aperIndex],yAxisMaxForPlot[aperIndex]);
			graph.setX_AxisLabel("HistOpenInverval/Mean");
			graph.setY_AxisLabel("Probability Ratio");
			graph.setTickLabelFontSize(24);
			graph.setAxisLabelFontSize(28);
			graph.setPlotLabelFontSize(18);
			
			String fileName = "RenewalModelHistoricOpenIntervalPlot_aper0pt"+(int)Math.round(aperiodicity*10)+".pdf";
			try {
				graph.saveAsPDF(fileName);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				
			}		
		}
	}
	
	
	
	public static void plotCumulativeDistOfAveU3pt3_FM3pt1_SubsectionRecurrenceIntervals(boolean wtBySectoRate) {
		
		// average solution for FM 3.1
		String f ="dev/scratch/UCERF3/data/scratch/InversionSolutions/2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_1_MEAN_BRANCH_AVG_SOL.zip";
		File file = new File(f);

		System.out.println("Instantiating ERF...");
		FaultSystemSolutionERF erf=null;
		try {
			erf = new FaultSystemSolutionERF(FaultSystemIO.loadSol(file));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (DocumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		erf.getParameter(ApplyGardnerKnopoffAftershockFilterParam.NAME).setValue(false);
		erf.getParameter(IncludeBackgroundParam.NAME).setValue(IncludeBackgroundOption.EXCLUDE);
		erf.updateForecast();


		File pdfFileName = new File("CumulativeDistOfAveU3pt3_FM3pt1_SubsectionRecurrenceIntervals");
		FaultSysSolutionERF_Calc.plotCumulativeDistOfSubsectionRecurrenceIntervals(erf, wtBySectoRate, pdfFileName);

	}
	
	public static void writeSubSectRITable(FaultSystemSolution sol, File csvFile) throws IOException {
		CSVFile<String> csv = new CSVFile<String>(true);
		
		csv.addLine("Subsection Index", "Subsection Name", "Supra-Seis Annual Participation Rate", "Supra-Seis Partitipation RI",
				"Subsection Min Mag", "Subsection Area (sq m)", "Rake (Degrees)", "Start Lat", "Start Lon", "End Lat", "End Lon");
		
		double[] particRates = sol.calcParticRateForAllSects(0d, 10d);
		
		FaultSystemRupSet rupSet = sol.getRupSet();
		
		for (int i=0; i<particRates.length; i++) {
			FaultSectionPrefData subsect = rupSet.getFaultSectionData(i);
			
			double minMag = Double.POSITIVE_INFINITY;
			for (int r : rupSet.getRupturesForSection(i)) {
				double mag = rupSet.getMagForRup(r);
				if (sol.getRateForRup(r) > 0 && mag < minMag)
					minMag = mag;
			}
			
			Location startLoc = subsect.getFaultTrace().first();
			Location endLoc = subsect.getFaultTrace().last();
			
			csv.addLine(i+"", subsect.getName(), particRates[i]+"", (1d/particRates[i])+"", minMag+"",
					rupSet.getAreaForSection(i)+"", subsect.getAveRake()+"",
					startLoc.getLatitude()+"", startLoc.getLongitude()+"", endLoc.getLatitude()+"", endLoc.getLongitude()+"");
		}
		
		csv.writeToFile(csvFile);
	}
	

	/**
	 * @param args
	 * @throws IOException 
	 * @throws DocumentException 
	 */
	public static void main(String[] args) throws IOException, DocumentException {
		
//		plotCumulativeDistOfAveU3pt3_FM3pt1_SubsectionRecurrenceIntervals(false);
		makeRenewalModelHistoricOpenIntervalPlots();
//		makeRenewalModelPDF_CDF_EtcPlot();
//		makeRenewalModelNoDateOfLastPlots();
		
//		File invDir = new File(UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR, "InversionSolutions");
//		
//		File baSolFile = new File(invDir, "2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_1_MEAN_BRANCH_AVG_SOL.zip");
//		writeSubSectRITable(FaultSystemIO.loadSol(baSolFile), new File("/tmp/sub_sect_ri.csv"));
//		System.exit(0);
//		
//		makeSlipMisfitHistograms(new File("/tmp/u3_slip_plots.xml"),
//				new File("/tmp/u2_slip_plots.xml"), new File("/tmp"));
//		System.exit(0);
		
//		makePreInversionMFDsFig();
//		makeDefModSlipRateMaps();

		
//		buildAveSlipDataTable(new File("ave_slip_table.csv"));
//		System.exit(0);
		
//		File compoundFile = new File(invDir,
//				"2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL.zip");
//		CompoundFaultSystemSolution cfss = CompoundFaultSystemSolution.fromZipFile(compoundFile);
//		makeCompoundFSSMomentRatesTable(cfss,
//				new File(invDir, compoundFile.getName().replaceAll(".zip", "_mo_rates.csv")));
//		
//		buildRupLengthComparisonPlot(cfss, invDir, compoundFile.getName().replaceAll(".zip", ""));
		
//		File avgSolFile = new File(invDir,
//				"FM3_1_ZENGBB_Shaw09Mod_DsrTap_CharConst_M5Rate7.9_MMaxOff7.6_NoFix_SpatSeisU3_mean_sol.zip");
//		AverageFaultSystemSolution avgSol = FaultSystemIO.loadAvgInvSol(avgSolFile);
//		makeNumRunsForRateWithin10Plot(avgSol, new File("/tmp"), "converge_n_within_10");
//		
//		avgSolFile = new File("/tmp/branch_avg_avg/mean.zip");
//		avgSol = FaultSystemIO.loadAvgInvSol(avgSolFile);
//		makeNumRunsForRateWithin10Plot(avgSol, new File("/tmp"), "branch_avg_n_within_10");
		
//		int mojaveParentID = 301;
//		int littleSalmonParentID = 17;
//		AverageFaultSystemSolution aveSol = AverageFaultSystemSolution.fromZipFile(
//				new File("/home/kevin/workspace/OpenSHA/dev/scratch/UCERF3/data/scratch/" +
//						"InversionSolutions/FM3_1_ZENG_Shaw09Mod_DsrTap_CharConst_M5Rate8.7" +
//						"_MMaxOff7.6_NoFix_SpatSeisU3_VarZeros_mean_sol.zip"));
//		makeParentSectConvergenceTable(new File("/tmp/little_salmon_onshore_rups_start_zero.csv"), aveSol, littleSalmonParentID);
	}

}
