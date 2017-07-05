/**
 * 
 */
package scratch.UCERF3.analysis;

import java.awt.Color;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.dom4j.DocumentException;
import org.jfree.chart.plot.DatasetRenderingOrder;
import org.opensha.commons.calc.FaultMomentCalc;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.HistogramFunction;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.eq.MagUtils;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSymbol;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.commons.gui.plot.GraphWindow;
import org.opensha.sha.magdist.ArbIncrementalMagFreqDist;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.magdist.SummedMagFreqDist;
import org.opensha.sha.magdist.TaperedGR_MagFreqDist;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.enumTreeBranches.InversionModels;
import scratch.UCERF3.enumTreeBranches.MaxMagOffFault;
import scratch.UCERF3.enumTreeBranches.MomentRateFixes;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;
import scratch.UCERF3.enumTreeBranches.SlipAlongRuptureModels;
import scratch.UCERF3.enumTreeBranches.SpatialSeisPDF;
import scratch.UCERF3.enumTreeBranches.TotalMag5Rate;
import scratch.UCERF3.griddedSeismicity.GriddedSeisUtils;
import scratch.UCERF3.inversion.InversionFaultSystemRupSet;
import scratch.UCERF3.inversion.InversionFaultSystemRupSetFactory;
import scratch.UCERF3.inversion.InversionFaultSystemSolution;
import scratch.UCERF3.inversion.InversionInputGenerator;
import scratch.UCERF3.inversion.InversionTargetMFDs;
import scratch.UCERF3.inversion.UCERF2_ComparisonSolutionFetcher;
import scratch.UCERF3.inversion.laughTest.LaughTestFilter;
import scratch.UCERF3.logicTree.LogicTreeBranch;
import scratch.UCERF3.utils.DeformationModelOffFaultMoRateData;
import scratch.UCERF3.utils.RELM_RegionUtils;
import scratch.UCERF3.utils.SectionMFD_constraint;
import scratch.UCERF3.utils.UCERF2_A_FaultMapper;
import scratch.UCERF3.utils.UCERF2_MFD_ConstraintFetcher;
import scratch.UCERF3.utils.UCERF2_Section_MFDs.UCERF2_Section_MFDsCalc;
import scratch.UCERF3.utils.paleoRateConstraints.PaleoProbabilityModel;

/**
 * This class hosts various calculations for a FaultSystemRupSet
 * @author field
 *
 */
public class FaultSystemRupSetCalc {
	
	final static boolean D=false;
	
	/**
	 * This the mean final minimum magnitude among all the fault 
	 * sections in the given FaultSystemRupSet
	 * @param faultSystemRupSet
	 * @param wtByMoRate - determines whether or not it's a weighted average based on orignal moment rate
	 */
	public static double getMeanMinMag(InversionFaultSystemRupSet fltSysRupSet, boolean wtByMoRate) {
		double wt=1;
		double totWt=0;
		double sum=0;
		for(int i=0;i<fltSysRupSet.getNumSections();i++) {
			if(wtByMoRate) {
				wt = fltSysRupSet.getFaultSectionData(i).calcMomentRate(true);
				if(Double.isNaN(wt)) {
					wt=0;
					if(D) System.out.println(i+" has NaN moRate; "+fltSysRupSet.getFaultSectionData(i).getName()+
							"\tarea="+(float)fltSysRupSet.getAreaForSection(i)+"\tslipRate="+(float)fltSysRupSet.getSlipRateForSection(i));
				}
			}
			sum += fltSysRupSet.getFinalMinMagForSection(i)*wt;
			totWt+=wt;
		}
		if(D) System.out.println("meanMinMag="+(sum/totWt));
		return sum/totWt;
	}

	
	/**
	 * This computes a histogram of the original minimum magnitude (before any filtering) 
	 * among all the fault sections in the given FaultSystemRupSet
	 * @param faultSystemRupSet
	 * @param minMag
	 * @param numMag
	 * @param deltaMag
	 */
	public static HistogramFunction getOrigMinMagHistogram(FaultSystemRupSet faultSystemRupSet, double minMag, int numMag, double deltaMag, boolean wtByMoRate) {
		HistogramFunction hist = new HistogramFunction(minMag, numMag, deltaMag);
		double wt=1;
		for(int i=0;i<faultSystemRupSet.getNumSections();i++) {
			if(wtByMoRate)
				wt = faultSystemRupSet.getAreaForSection(i)*faultSystemRupSet.getSlipRateForSection(i);
			if(D && Double.isNaN(wt)) {
				System.out.println(i+" has NaN moRate; "+faultSystemRupSet.getFaultSectionData(i).getName()+
						"\tarea="+(float)faultSystemRupSet.getAreaForSection(i)+"\tslipRate="+(float)faultSystemRupSet.getSlipRateForSection(i));
			}
			double min = faultSystemRupSet.getOrigMinMagForSection(i);
			if(!Double.isNaN(wt)) {
				hist.add(min, wt);
			}
		}
		if(D) System.out.println(hist);
		hist.setName("Orig Min Mag Histogram for FaultSystemRupSet");
		hist.setInfo("(among the "+faultSystemRupSet.getNumSections()+" sections; wtByMoRate="+wtByMoRate+")");
		hist.normalizeBySumOfY_Vals();
		return hist;
	}
	
	/**
	 * This computes a histogram of the final minimum magnitude (before any filtering) 
	 * among all the fault sections in the given FaultSystemRupSet
	 * @param faultSystemRupSet
	 * @param minMag
	 * @param numMag
	 * @param deltaMag
	 */
	public static HistogramFunction getFinalMinMagHistogram(InversionFaultSystemRupSet faultSystemRupSet, double minMag, int numMag, double deltaMag, boolean wtByMoRate) {
		HistogramFunction hist = new HistogramFunction(minMag, numMag, deltaMag);
		double wt=1;
		for(int i=0;i<faultSystemRupSet.getNumSections();i++) {
			if(wtByMoRate)
				wt = faultSystemRupSet.getAreaForSection(i)*faultSystemRupSet.getSlipRateForSection(i);
			if(D && Double.isNaN(wt)) {
				System.out.println(i+" has NaN moRate; "+faultSystemRupSet.getFaultSectionData(i).getName()+
						"\tarea="+(float)faultSystemRupSet.getAreaForSection(i)+"\tslipRate="+(float)faultSystemRupSet.getSlipRateForSection(i));
			}
			double min = faultSystemRupSet.getFinalMinMagForSection(i);
			if(!Double.isNaN(wt)) {
				hist.add(min, wt);
			}
		}
		if(D) System.out.println(hist);
		hist.setName("Final Min Mag Histogram for FaultSystemRupSet");
		hist.setInfo("(among the "+faultSystemRupSet.getNumSections()+" sections; wtByMoRate="+wtByMoRate+")");
		hist.normalizeBySumOfY_Vals();
		return hist;
	}

	
	
	/**
	 * This computes a histogram of the maximum magnitude among all the fault 
	 * sections in the given FaultSystemRupSet
	 * @param faultSystemRupSet
	 * @param minMag
	 * @param numMag
	 * @param deltaMag
	 */
	public static HistogramFunction getMaxMagHistogram(FaultSystemRupSet faultSystemRupSet, double minMag, int numMag, double deltaMag, boolean wtByMoRate) {
		HistogramFunction hist = new HistogramFunction(minMag, numMag, deltaMag);
		double wt=1;
		for(int i=0;i<faultSystemRupSet.getNumSections();i++) {
			if(wtByMoRate)
				wt = faultSystemRupSet.getAreaForSection(i)*faultSystemRupSet.getSlipRateForSection(i);
			if(D && Double.isNaN(wt)) {
				System.out.println(i+" has NaN moRate; "+faultSystemRupSet.getFaultSectionData(i).getName()+
						"\tarea="+(float)faultSystemRupSet.getAreaForSection(i)+"\tslipRate="+(float)faultSystemRupSet.getSlipRateForSection(i));
			}
			double max = faultSystemRupSet.getMaxMagForSection(i);
			if(!Double.isNaN(wt)) {
				hist.add(max, wt);
			}
		}
		if(D) System.out.println(hist);
		hist.setName("Max Mag Histogram for FaultSystemRupSet");
		hist.setInfo("(among the "+faultSystemRupSet.getNumSections()+" sections)");
		hist.normalizeBySumOfY_Vals();
		return hist;
	}
	
	
	/**
	 * This computes a histogram of the the magnitudes among all ruptures 
	 * in the given FaultSystemRupSet
	 * @param faultSystemRupSet
	 * @param minMag
	 * @param numMag
	 * @param deltaMag
	 */
	public static HistogramFunction getMagHistogram(InversionFaultSystemRupSet faultSystemRupSet, double minMag, int numMag, double deltaMag) {
		HistogramFunction hist = new HistogramFunction(minMag, numMag, deltaMag);
			for (int r=0;r<faultSystemRupSet.getNumRuptures(); r++) {
				if(!faultSystemRupSet.isRuptureBelowSectMinMag(r))
					hist.add(faultSystemRupSet.getMagForRup(r), 1.0);
			}
		if(D) System.out.println(hist);
//		hist.normalizeBySumOfY_Vals();
		hist.setName("Mag Histogram for FaultSystemRupSet");
		hist.setInfo("(based on "+faultSystemRupSet.getNumRuptures()+" ruptures)");
		return hist;
	}
	
	
	/**
	 * This computes a histogram of the the lengths (in KM) among all ruptures 
	 * in the given FaultSystemRupSet
	 * @param faultSystemRupSet
	 * @param minLength
	 * @param numLengths
	 * @param deltaLength
	 */
	public static HistogramFunction getLengthHistogram(FaultSystemRupSet faultSystemRupSet, double minLength, int numLengths, double deltaLength) {
		HistogramFunction hist = new HistogramFunction(minLength, numLengths, deltaLength);
			for (int r=0;r<faultSystemRupSet.getNumRuptures(); r++) {
				double length = 0;
				for (int sectID : faultSystemRupSet.getSectionsIndicesForRup(r))
					length += faultSystemRupSet.getFaultSectionData(sectID).getTraceLength();
				hist.add(length, 1.0);
			}
		if(D) System.out.println(hist);
		hist.normalizeBySumOfY_Vals();
		hist.setName("Length Histogram for FaultSystemRupSet");
		hist.setInfo("(based on "+faultSystemRupSet.getNumRuptures()+" ruptures)");
		return hist;
	}

	/**
	 * Note that this is plotting the OrigMinMagHistogram, and not the FinalMinMagHistogram
	 * (difference between these was implemented after this method was created)
	 * @param faultSystemRupSet
	 * @param minMag
	 * @param numMag
	 * @param deltaMag
	 * @param wtByMoRate
	 */
	public static void plotAllHistograms(InversionFaultSystemRupSet faultSystemRupSet, double minMag, int numMag, double deltaMag, boolean wtByMoRate) {
		ArrayList<HistogramFunction> hists = new ArrayList<HistogramFunction>();
		hists.add(getMagHistogram(faultSystemRupSet, minMag, numMag, deltaMag));
		hists.add(getMaxMagHistogram(faultSystemRupSet, minMag, numMag, deltaMag, wtByMoRate));
		hists.add(getOrigMinMagHistogram(faultSystemRupSet, minMag, numMag, deltaMag, wtByMoRate));
		
		hists.add(hists.get(0).getCumulativeDistFunction());
		hists.get(3).setName("Cumulative "+hists.get(0).getName());
		hists.get(3).setInfo(hists.get(0).getInfo());
		hists.add(hists.get(1).getCumulativeDistFunction());
		hists.get(4).setName("Cumulative "+hists.get(1).getName());
		hists.get(4).setInfo(hists.get(1).getInfo());
		hists.add(hists.get(2).getCumulativeDistFunction());
		hists.get(5).setName("Cumulative "+hists.get(2).getName());
		hists.get(5).setInfo(hists.get(2).getInfo());
		
		ArrayList<PlotCurveCharacterstics> list = new ArrayList<PlotCurveCharacterstics>();
		list.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLACK));
		list.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLUE));
		list.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.RED));
		list.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 2f, Color.BLACK));
		list.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 2f, Color.BLUE));
		list.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 2f, Color.RED));
		
		GraphWindow graph = new GraphWindow(hists, "Histograms",list); 
		graph.setX_AxisLabel("Magnitude");
		graph.setY_AxisLabel("Normalized Number");

	}
	
	/**
	 * This plots a histogram of moment rate reductions for subseismogenic ruptures
	 * (not including creep reductions, which have already been applied)
	 * 
	 * 
	 * @param faultSystemRupSet
	 * @param wtByMoRate
	 * @param plotResult
	 * @return
	 */
	public static HistogramFunction getMomentRateReductionHistogram(InversionFaultSystemRupSet faultSystemRupSet, boolean wtByMoRate, boolean plotResult) {
		HistogramFunction hist = new HistogramFunction(0.005, 100, 0.01);
		double wt=1;
		double mean=0, totWt=0;;
		for(int i=0;i<faultSystemRupSet.getNumSections();i++) {
			if(wtByMoRate)
				wt = faultSystemRupSet.getAreaForSection(i)*faultSystemRupSet.getSlipRateForSection(i);
			if(!Double.isNaN(wt)) {
				double reduction = faultSystemRupSet.getMomentRateReductionFraction(i);
				if (Double.isNaN(reduction)) {
					System.out.println("NaN reduction for section: "+faultSystemRupSet.getFaultSectionData(i).getName()
							+" with slip: "+faultSystemRupSet.getSlipRateForSection(i));
				} else {
					hist.add(reduction, wt);
					mean += reduction*wt;
					totWt +=wt;
					if(reduction>0.5)
						System.out.println(reduction+"\t"+faultSystemRupSet.getFaultSectionData(i).getName()+
								"\tmagLower="+(float)faultSystemRupSet.getFinalMinMagForSection(i)
								+"\tmagUpper="+(float)faultSystemRupSet.getMaxMagForSection(i));
				}
			}
		}
		mean /= totWt;
		if (D) System.out.println(hist);
		hist.setName("Distribution of Moment Rate Reductions for FaultSystemRupSet");
		hist.setInfo("(among the "+faultSystemRupSet.getNumSections()+" sections; mean = "+(float)mean+")");
		hist.normalizeBySumOfY_Vals();
		
		if(plotResult) {
			GraphWindow graph = new GraphWindow(hist, "Moment Rate Reductions Histogram"); 
			graph.setX_AxisLabel("Reduction");
			graph.setY_AxisLabel("Normalized Number");

		}
		return hist;
	}
	
	
	
	public static void listAllParentSectionNames(FaultSystemRupSet faultSystemRupSet) {
		ArrayList<String> parNames = new ArrayList<String>();
		for(FaultSectionPrefData data : faultSystemRupSet.getFaultSectionDataList()) {
			if(!parNames.contains(data.getParentSectionName())) {
				parNames.add(data.getParentSectionName());
			}
		}
		
		for(String name: parNames)
			System.out.println(name);
		
	}

	
	/**
	 * This plots the sum of all the implied GR nucleation MFDs for each section,
	 * assuming all moment at a section goes into a GR with b=1 from M=0 to the
	 * maximum magnitude the section participates in.
	 */
	public static void plotImpliedTotalSectGR_MFD(InversionFaultSystemRupSet faultSysRupSet, String label) {
		SummedMagFreqDist impliedGR = calcImpliedGR_NucleationMFD(faultSysRupSet, InversionTargetMFDs.MIN_MAG,InversionTargetMFDs.NUM_MAG, InversionTargetMFDs.DELTA_MAG);
		
		double mMax = faultSysRupSet.getMaxMag();
		double mMaxRounded = impliedGR.getX(impliedGR.getClosestXIndex(mMax));
		
		GutenbergRichterMagFreqDist totTargetMoMatched = new GutenbergRichterMagFreqDist(InversionTargetMFDs.MIN_MAG,InversionTargetMFDs.NUM_MAG, 
				InversionTargetMFDs.DELTA_MAG, InversionTargetMFDs.MIN_MAG, mMaxRounded, impliedGR.getTotalMomentRate(), 1.0);
		totTargetMoMatched.setName("Perfect GR, matching def mod moment");
		totTargetMoMatched.setInfo("(up to mag of largest event in fault system; MoRate ="+(float)totTargetMoMatched.getTotalMomentRate()+"; Rate ge M5 = "+(float)totTargetMoMatched.getCumRate(5.05)+")");
		
		GutenbergRichterMagFreqDist totTargetRateMatched = new GutenbergRichterMagFreqDist(InversionTargetMFDs.MIN_MAG,InversionTargetMFDs.NUM_MAG, 
				InversionTargetMFDs.DELTA_MAG, InversionTargetMFDs.MIN_MAG, mMaxRounded, 1.0, 1.0);
		TotalMag5Rate rate = TotalMag5Rate.RATE_7p9;
		totTargetRateMatched.scaleToCumRate(0, rate.getRateMag5()*1e5);
		totTargetRateMatched.setName("Perfect GR, matching regional rate");
		totTargetRateMatched.setInfo("(up to mag of largest event in fault system; MoRate ="+(float)totTargetRateMatched.getTotalMomentRate()+"; Rate ge M5 = "+(float)totTargetRateMatched.getCumRate(5.05)+")");

		double fractOn = faultSysRupSet.getInversionTargetMFDs().getFractionSeisOnFault();
		
		GutenbergRichterMagFreqDist totTargetOnFault = new GutenbergRichterMagFreqDist(InversionTargetMFDs.MIN_MAG,InversionTargetMFDs.NUM_MAG, 
				InversionTargetMFDs.DELTA_MAG, InversionTargetMFDs.MIN_MAG, mMaxRounded, 1.0, 1.0);
		totTargetOnFault.scaleToCumRate(0, fractOn*rate.getRateMag5()*1e5);
		totTargetOnFault.setName("On Fault Target");
		totTargetOnFault.setInfo("(MoRate ="+(float)totTargetOnFault.getTotalMomentRate()+"; Rate ge M5 = "+(float)totTargetOnFault.getCumRate(5.05)+")");

		double mMaxOff = faultSysRupSet.getInversionTargetMFDs().getMmaxOffFault();

		GutenbergRichterMagFreqDist totTargetOffFault = new GutenbergRichterMagFreqDist(InversionTargetMFDs.MIN_MAG,InversionTargetMFDs.NUM_MAG, 
				InversionTargetMFDs.DELTA_MAG, InversionTargetMFDs.MIN_MAG, mMaxRounded, 1.0, 1.0);
		totTargetOffFault.scaleToCumRate(0, (1.0-fractOn)*rate.getRateMag5()*1e5);
		totTargetOffFault.zeroAboveMag(mMaxOff);
		totTargetOffFault.setName("Off Fault Target");
		totTargetOffFault.setInfo("(MoRate ="+(float)totTargetOffFault.getTotalMomentRate()+"; Rate ge M5 = "+(float)totTargetOffFault.getCumRate(5.05)+")");

		ArrayList<XY_DataSet> funcs = new ArrayList<XY_DataSet>();
		funcs.add(impliedGR);
		funcs.add(totTargetMoMatched);
		funcs.add(totTargetRateMatched);
		funcs.add(totTargetOnFault);
		funcs.add(totTargetOffFault);
		ArrayList<PlotCurveCharacterstics> plotChars = new ArrayList<PlotCurveCharacterstics>();
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2, null, 0, Color.RED));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2, null, 0, Color.LIGHT_GRAY));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2, null, 0, Color.BLACK));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2, null, 0, Color.ORANGE));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2, null, 0, Color.GREEN));
		String morelabel = " (M>=5 rate = "+((float)Math.round(impliedGR.getCumRate(5.05)*10.0))/10+")";
		// note that mendocino is included here (but filtered in the UCERF2 ERF)
		if(faultSysRupSet.getFaultModel() == FaultModels.FM2_1) {
			morelabel += " -- Mendocino included!";
		}
		GraphWindow graph = new GraphWindow(funcs, label+morelabel,plotChars);
		graph.setX_AxisRange(5, 9);
		graph.setY_AxisRange(1e-5, 20);
		graph.setYLog(true);
		graph.setX_AxisLabel("Mag");
		graph.setY_AxisLabel("Rate (per year)");

		graph.setTickLabelFontSize(14);
		graph.setAxisLabelFontSize(16);
		graph.setPlotLabelFontSize(18);
		String fileName = "TargetGR_"+label + ".pdf";
		if(fileName != null) {
			try {
				graph.saveAsPDF(fileName);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}			
		}		
		
		
		ArrayList<XY_DataSet> funcsCum = new ArrayList<XY_DataSet>();
		funcsCum.add(impliedGR.getCumRateDistWithOffset());
		funcsCum.add(totTargetMoMatched.getCumRateDistWithOffset());
		funcsCum.add(totTargetRateMatched.getCumRateDistWithOffset());
		funcsCum.add(totTargetOnFault.getCumRateDistWithOffset());
		funcsCum.add(totTargetOffFault.getCumRateDistWithOffset());
		GraphWindow graphCum = new GraphWindow(funcsCum, label+morelabel,plotChars);
		graphCum.setX_AxisRange(5, 9);
		graphCum.setY_AxisRange(1e-5, 20);
		graphCum.setYLog(true);
		graphCum.setX_AxisLabel("Mag");
		graphCum.setY_AxisLabel("Rate (per year)");

		graphCum.setTickLabelFontSize(14);
		graphCum.setAxisLabelFontSize(16);
		graphCum.setPlotLabelFontSize(18);
		String fileNameCum = "TargetGR_Cum_"+label + ".pdf";
		if(fileName != null) {
			try {
				graphCum.saveAsPDF(fileNameCum);
			} catch (IOException e) {
				e.printStackTrace();
			}			
		}		


		
	}
	
	/**
	 * This computes the total implied nucleation MFD assuming each fault section has a perfect GR
	 * up to the maximum magnitude it participates in.
	 * @param faultSysRupSet
	 * @return
	 */
	public static SummedMagFreqDist calcImpliedGR_NucleationMFD(InversionFaultSystemRupSet faultSysRupSet, double minMag, int numMag, double deltaMag) {
		ArrayList<GutenbergRichterMagFreqDist> gr_mfds = calcImpliedGR_NuclMFD_ForEachSection(faultSysRupSet, minMag, numMag, deltaMag);
		SummedMagFreqDist mfd = new SummedMagFreqDist(minMag, numMag, deltaMag);
		for(GutenbergRichterMagFreqDist gr : gr_mfds) {
			mfd.addIncrementalMagFreqDist(gr);
		}
		mfd.setName(faultSysRupSet.getDeformationModel()+ " Target GR MFD");
		mfd.setInfo("Rate ge M5 = "+(float)mfd.getCumRate(5.05 )+"; totMoRate = "+(float)mfd.getTotalMomentRate());
		return mfd;
	}
	
	
	/**
	 * This computes the implied nucleation MFD for each fault section assuming it is a perfect GR
	 * from mag zero up to the maximum magnitude it participates in (w/ b=1).
	 * @param faultSysRupSet
	 * @return
	 */
	public static ArrayList<GutenbergRichterMagFreqDist> calcImpliedGR_NuclMFD_ForEachSection(FaultSystemRupSet faultSysRupSet,
			double minMag, int numMag, double deltaMag) {
		List<FaultSectionPrefData> sectDataList = faultSysRupSet.getFaultSectionDataList();
		ArrayList<GutenbergRichterMagFreqDist> mfds = new ArrayList<GutenbergRichterMagFreqDist>();
		GutenbergRichterMagFreqDist tempGR = new GutenbergRichterMagFreqDist(minMag, numMag, deltaMag);
		for(int i=0; i< sectDataList.size();i++) {
			FaultSectionPrefData sectData = sectDataList.get(i);
			int mMaxIndex = tempGR.getClosestXIndex(faultSysRupSet.getMaxMagForSection(i));
			double mMax = tempGR.getX(mMaxIndex);
			double moRate = sectData.calcMomentRate(true);
			if(Double.isNaN(moRate)) {
				moRate = 0;
//				GutenbergRichterMagFreqDist gr = new GutenbergRichterMagFreqDist(0.05, 90, 0.1, 0.05, mMax, moRate, 1.0);
//System.out.println("NaN MoRate; set to zero");
//System.out.println(gr);
			}
			GutenbergRichterMagFreqDist gr = new GutenbergRichterMagFreqDist(minMag, numMag, deltaMag, minMag, mMax, moRate, 1.0);
//if(Double.isNaN(gr.getTotalIncrRate())) {
//	System.out.println(i+"\t"+mMax+"\t"+moRate);
//	System.exit(0);
//}
			mfds.add(gr);
		}
		return mfds;
	}
	

	
	public static void plotAllImpliedTotalSectGR_MFD() {
		
		ArrayList<ScalingRelationships> scalingRelList = new ArrayList<ScalingRelationships>();
		scalingRelList.add(ScalingRelationships.ELLSWORTH_B);
		scalingRelList.add(ScalingRelationships.HANKS_BAKUN_08);
		scalingRelList.add(ScalingRelationships.SHAW_2009_MOD);

		ArrayList<DeformationModels> defModList= new ArrayList<DeformationModels>();
		FaultModels fm = FaultModels.FM3_1;
		
		defModList.add(DeformationModels.ABM);
		defModList.add(DeformationModels.GEOLOGIC);
		defModList.add(DeformationModels.NEOKINEMA);
		defModList.add(DeformationModels.ZENGBB);
		
		// for UCERF3
		for(DeformationModels dm :defModList) {
			for(ScalingRelationships sr:scalingRelList) {
				InversionFaultSystemRupSet faultSysRupSet = InversionFaultSystemRupSetFactory.forBranch(fm, dm, 
						sr, SlipAlongRuptureModels.TAPERED, InversionModels.GR_CONSTRAINED);
				String label = faultSysRupSet.getDeformationModel().getShortName()+"_"+sr.getShortName();
				plotImpliedTotalSectGR_MFD(faultSysRupSet, label);				
			}
		}
		
		// now do UCERF2
//		fm = FaultModels.FM2_1;
//		DeformationModels dm = DeformationModels.UCERF2_ALL;
//		for(ScalingRelationships sr:scalingRelList) {
//			InversionFaultSystemRupSet faultSysRupSet = InversionFaultSystemRupSetFactory.forBranch(fm, dm, 
//					sr, SlipAlongRuptureModels.TAPERED, InversionModels.GR_CONSTRAINED);
//			String label = faultSysRupSet.getDeformationModel().getShortName()+"_"+sr.getShortName();
//			plotImpliedTotalSectGR_MFD(faultSysRupSet, label);				
//		}
	}

	
	
	public static String oldTestInversionGR_Setup(double totRegionalM5_Rate, double fractSeisOffFault, double mMaxOffFault,
			InversionFaultSystemRupSet faultSysRupSet) {
		
		FaultModels fm = faultSysRupSet.getFaultModel();
		DeformationModels dm = faultSysRupSet.getDeformationModel();
		
		double offFaultRate = totRegionalM5_Rate*fractSeisOffFault;
		double onFaultRate = totRegionalM5_Rate-offFaultRate;
		
		// GR Branch
		SummedMagFreqDist mfd = calcImpliedGR_NucleationMFD(faultSysRupSet, 0.05, 90, 0.1);
		
		double onCoupCoeff = onFaultRate/mfd.getCumRate(5.05);
		double onFaultOrigMoRate = mfd.getTotalMomentRate();
		double onFaultReducedMoRate = onCoupCoeff*onFaultOrigMoRate;
		
		GutenbergRichterMagFreqDist offFaultGR = new GutenbergRichterMagFreqDist(0.05, 90, 0.1, 0.05, mMaxOffFault, 1.0, 1.0);
		offFaultGR.scaleToCumRate(0, offFaultRate*1e5);
		DeformationModelOffFaultMoRateData defModOffFaultMoRateData = DeformationModelOffFaultMoRateData.getInstance();
		double offFaultOrigMoRate = defModOffFaultMoRateData.getTotalOffFaultMomentRate(fm, dm);
		double offFaultReducedMoRate = offFaultGR.getTotalMomentRate();
		
		// solve for the maximum magnitude off-fault with full moment
		GutenbergRichterMagFreqDist tempOffFaultGR = new GutenbergRichterMagFreqDist(0.005, 900, 0.01);
		tempOffFaultGR.setAllButMagUpper(0.005, offFaultOrigMoRate, offFaultRate*1e5, 1.0, true);
		double maxOffMagWithFullMoment = tempOffFaultGR.getMagUpper();
		
		double offCoupCoeff = offFaultReducedMoRate/offFaultOrigMoRate;

		double moRateReduction = (onFaultReducedMoRate+offFaultReducedMoRate)/(onFaultOrigMoRate+offFaultOrigMoRate);
		// compute the average minimum seismogenic rupture mag
		double aveMinSeismoMag = getMeanMinMag(faultSysRupSet, true);
		
		return (float)mfd.getMaxMagWithNonZeroRate()+"\t"+(float)onCoupCoeff+"\t"+(float)offCoupCoeff+"\t"+(float)moRateReduction+"\t"+
				(float)onFaultOrigMoRate+"\t"+(float)onFaultReducedMoRate+"\t"+
				(float)offFaultOrigMoRate+"\t"+(float)offFaultReducedMoRate+"\t"+(float)maxOffMagWithFullMoment+"\t"+(float)aveMinSeismoMag;
		
	}
	
	
	public static String getInversionSetupInfo(FaultSystemRupSet faultSysRupSet) {
		
		String header = "DefMod\tM(A)\tRge5\tfrSeisOff\tmMaxOff\tmMaxOn\tonCoupCoeff\toffCoupCoeff\tfrMoRateReduct\tonFltOrigMoRate\tonFltReducMoRate\toffFltOrigMoRate\toffFltReducMoRate\tmMaxOffWithFullMo\taveMinSeismoMag";
		return header;
//		System.out.println(header);

//		String lineFirstPart = faultSysRupSet.getDeformationModel().getShortName()+"\t"+ma.getShortName()+
//		"\t"+totRegionalM5_Rate+"\t"+frSeisOff+"\t"+mMaxOff+"\t";
//
//		
//		FaultModels fm = faultSysRupSet.getFaultModel();
//		DeformationModels dm = faultSysRupSet.getDeformationModel();
//		
//		double offFaultRate = totRegionalM5_Rate*fractSeisOffFault;
//		double onFaultRate = totRegionalM5_Rate-offFaultRate;
//		
//		// GR Branch
//		SummedMagFreqDist mfd = calcImpliedGR_NucleationMFD(faultSysRupSet, 0.05, 90, 0.1);
//		
//		double onCoupCoeff = onFaultRate/mfd.getCumRate(5.05);
//		double onFaultOrigMoRate = mfd.getTotalMomentRate();
//		double onFaultReducedMoRate = onCoupCoeff*onFaultOrigMoRate;
//		
//		GutenbergRichterMagFreqDist offFaultGR = new GutenbergRichterMagFreqDist(0.05, 90, 0.1, 0.05, mMaxOffFault, 1.0, 1.0);
//		offFaultGR.scaleToCumRate(0, offFaultRate*1e5);
//		DeformationModelOffFaultMoRateData defModOffFaultMoRateData = DeformationModelOffFaultMoRateData.getInstance();
//		double offFaultOrigMoRate = defModOffFaultMoRateData.getTotalOffFaultMomentRate(fm, dm);
//		double offFaultReducedMoRate = offFaultGR.getTotalMomentRate();
//		
//		// solve for the maximum magnitude off-fault with full moment
//		GutenbergRichterMagFreqDist tempOffFaultGR = new GutenbergRichterMagFreqDist(0.005, 900, 0.01);
//		tempOffFaultGR.setAllButMagUpper(0.005, offFaultOrigMoRate, offFaultRate*1e5, 1.0, true);
//		double maxOffMagWithFullMoment = tempOffFaultGR.getMagUpper();
//		
//		double offCoupCoeff = offFaultReducedMoRate/offFaultOrigMoRate;
//
//		double moRateReduction = (onFaultReducedMoRate+offFaultReducedMoRate)/(onFaultOrigMoRate+offFaultOrigMoRate);
//		// compute the average minimum seismogenic rupture mag
//		double aveMinSeismoMag = getMeanMinMag(faultSysRupSet, true);
//		
//		return (float)mfd.getMaxMagWithNonZeroRate()+"\t"+(float)onCoupCoeff+"\t"+(float)offCoupCoeff+"\t"+(float)moRateReduction+"\t"+
//				(float)onFaultOrigMoRate+"\t"+(float)onFaultReducedMoRate+"\t"+
//				(float)offFaultOrigMoRate+"\t"+(float)offFaultReducedMoRate+"\t"+(float)maxOffMagWithFullMoment+"\t"+(float)aveMinSeismoMag;
		
	}

	
	
	
	public static String oldTestInversionCharSetup(double totRegionalM5_Rate, double fractSeisOffFault, double mMaxOffFault,
			InversionFaultSystemRupSet faultSysRupSet) {
		
		FaultModels fm = faultSysRupSet.getFaultModel();
		DeformationModels dm = faultSysRupSet.getDeformationModel();
		
		double offFaultMgt5_Rate = totRegionalM5_Rate*fractSeisOffFault;
		double totOnFaultMgt5_Rate = totRegionalM5_Rate-offFaultMgt5_Rate;
		
		DeformationModelOffFaultMoRateData defModOffFaultMoRateData = DeformationModelOffFaultMoRateData.getInstance();
		double offFaultOrigMoRate = defModOffFaultMoRateData.getTotalOffFaultMomentRate(fm, dm);
		
		// get fault moment rate and mMax for faultSysRupSet
		double onFaultOrigMoRate=0;
		double mMaxInRegion=0;
		List<FaultSectionPrefData> sectDataList = faultSysRupSet.getFaultSectionDataList();
		for(int i=0; i<sectDataList.size();i++) {
			double mMax = (double)Math.round(10*(faultSysRupSet.getMaxMagForSection(i)-0.05))/10.0 +0.05;
			double moRate = sectDataList.get(i).calcMomentRate(true);
			if(Double.isNaN(moRate))  moRate = 0;
			if(mMax>mMaxInRegion) mMaxInRegion = mMax;
			onFaultOrigMoRate += moRate;
		}
			
		// make the perfect target GR for region
		GutenbergRichterMagFreqDist totGR = new GutenbergRichterMagFreqDist(0.05, 90, 0.1, 0.05, mMaxInRegion, 1.0, 1.0);
		totGR.scaleToCumRate(0, totRegionalM5_Rate*1e5);
		
		double moRateReduction = totGR.getTotalMomentRate()/(onFaultOrigMoRate+offFaultOrigMoRate);
		
		double aveMinSeismoMag = getMeanMinMag(faultSysRupSet, true);
		
		IncrementalMagFreqDist offFaultMFD = getTriLinearCharOffFaultTargetMFD(totGR, totOnFaultMgt5_Rate, aveMinSeismoMag, mMaxOffFault);
		IncrementalMagFreqDist onFaultMFD = new IncrementalMagFreqDist(totGR.getMinX(),totGR.size(),totGR.getDelta());
		for(int i=0;i<onFaultMFD.size();i++) {
			onFaultMFD.set(i,totGR.getY(i)-offFaultMFD.getY(i));
		}
		
		//  	TEST *************************
//		ArrayList<IncrementalMagFreqDist> test = new ArrayList<IncrementalMagFreqDist>();
//		test.add(totGR);
//		test.add(onFaultMFD);
//		test.add(offFaultMFD);
//		GraphWindow graph = new GraphWindow(test, "MFDs");

		
		
		
		// solve for the max off-fault mag needed to satisfy off-fault moRate
		IncrementalMagFreqDist testOffFaultMFD = getTriLinearCharOffFaultTargetMFD(offFaultOrigMoRate,totGR, totOnFaultMgt5_Rate,aveMinSeismoMag);
		double maxOffMagWithFullMoment;
		if(testOffFaultMFD != null)
			maxOffMagWithFullMoment = testOffFaultMFD.getMaxMagWithNonZeroRate();
		else
			maxOffMagWithFullMoment = Double.NaN;

		// OLD STUFF
//		int mMaxOffFaultIndex = totGR.getXIndex(mMaxOffFault);
//		IncrementalMagFreqDist[] mfds = old_getCharOnFaultTargetMFD(totGR, totOnFaultMgt5_Rate, mMaxOffFaultIndex);
//		IncrementalMagFreqDist onFaultMFD = mfds[0];
//		IncrementalMagFreqDist offFaultMFD = mfds[1];
		
		// get off-fault moment rate assuming full def model moment is satisfied
//		mfds = old_getCharOnFaultTargetMFD(offFaultOrigMoRate,totGR, totOnFaultMgt5_Rate);
//		double maxOffMagWithFullMoment;
//		if(mfds != null)
//			maxOffMagWithFullMoment = mfds[1].getMaxMagWithNonZeroRate();
//		else
//			maxOffMagWithFullMoment = Double.NaN;


		
		// test
//		onFaultMFD.setName("onFaultMFD");
//		onFaultMFD.setInfo("(rate(M>=5) = "+(float)onFaultMFD.getCumRate(5.05)+")");
//		offFaultMFD.setName("offFaultMFD");
//		offFaultMFD.setInfo("(rate(M>=5) = "+(float)offFaultMFD.getCumRate(5.05)+")");
//		ArrayList<EvenlyDiscretizedFunc> funcs = new ArrayList<EvenlyDiscretizedFunc>();
//		funcs.add(totGR);
//		funcs.add(offFaultMFD);
//		funcs.add(onFaultMFD);
//		funcs.add(totGR.getCumRateDistWithOffset());
//		funcs.add(offFaultMFD.getCumRateDistWithOffset());
//		funcs.add(onFaultMFD.getCumRateDistWithOffset());
//		GraphWindow graph = new GraphWindow(funcs, "MFDs");
//		graph.setX_AxisRange(5, 9);
//		graph.setY_AxisRange(1e-5, 20);
//		graph.setYLog(true);
//		graph.setX_AxisLabel("Mag");
//		graph.setY_AxisLabel("Rate (per year)");


		double offFaultReducedMoRate = offFaultMFD.getTotalMomentRate();
		double offCoupCoeff = offFaultReducedMoRate/offFaultOrigMoRate;
		double onFaultReducedMoRate = onFaultMFD.getTotalMomentRate();
		double onCoupCoeff = onFaultReducedMoRate/onFaultOrigMoRate;
		
		return (float)mMaxInRegion+"\t"+(float)onCoupCoeff+"\t"+(float)offCoupCoeff+"\t"+(float)moRateReduction+"\t"+
				(float)onFaultOrigMoRate+"\t"+(float)onFaultReducedMoRate+"\t"+
				(float)offFaultOrigMoRate+"\t"+(float)offFaultReducedMoRate+"\t"+maxOffMagWithFullMoment+"\t"+(float)aveMinSeismoMag;
		
	}

	
	/**
	 * This solves for the on-fault target MFD for the characteristic model, where the off-fault wedge 
	 * is removed at lower magnitudes (up to the maximum mag of off-fault seismicity), and such that
	 * the total desired rate of events on fault is maintained.  This assumes the target MFD has a b-value
	 * of about 1.0 (or more precisely, that the final low-mag on-fault b-value is less that 1.0).
	 * 
	 * Note that you can get the implied off-fault mMax by calling the MFDs getMaxMagWithNonZeroRate() method.
	 * 
	 * Warning - this makes no checks that inputs are reasonable (e.g., that totOnFaultMgt5_Rate <= totalTargetGR.getCumRate(transMagIndex))
	 * 
	 * @param totalTargetGR - the total target GR for the region.
	 * @param totOnFaultMgt5_Rate - the total target rate of events on-fault for M ge 5.
	 * @param mMaxOffFaultIndex - the last non-zero magnitude index for off-fault seismicity (one mag bin below transition mag).
	 * @return on-fault mfd is in array element [0] and off-fault mfd is in [1]
	 */
	public static IncrementalMagFreqDist[] old_getCharOnFaultTargetMFD(GutenbergRichterMagFreqDist totalTargetGR, double totOnFaultMgt5_Rate, int mMaxOffFaultIndex) {
		
		int transMagIndex = mMaxOffFaultIndex+1;
		int mag5_Index = totalTargetGR.getXIndex(5.05);
		
		double onFaultRateBelowTransMag = totOnFaultMgt5_Rate - totalTargetGR.getCumRate(transMagIndex);
		
		// b-value is between 0 and 1, so make function and interpolate
		ArbitrarilyDiscretizedFunc rateVsBvalueFunc = new ArbitrarilyDiscretizedFunc();
		double b_incr = 0.01;
		for(double b=-1; b<=1.0; b+=b_incr) {
			GutenbergRichterMagFreqDist tempGR = new GutenbergRichterMagFreqDist(totalTargetGR.getMinX(), totalTargetGR.size(), 
					totalTargetGR.getDelta(), totalTargetGR.getMagLower(), totalTargetGR.getMagUpper(), 1.0, b);
			tempGR.scaleToIncrRate(transMagIndex, totalTargetGR.getY(transMagIndex));
			double testOnFaultRateBelowTransMag = tempGR.getCumRate(mag5_Index) - tempGR.getCumRate(transMagIndex);
			rateVsBvalueFunc.set(testOnFaultRateBelowTransMag,b);
		}
		
		double bVal = rateVsBvalueFunc.getInterpolatedY(onFaultRateBelowTransMag);
		
		GutenbergRichterMagFreqDist tempGR = new GutenbergRichterMagFreqDist(totalTargetGR.getMinX(), totalTargetGR.size(), 
				totalTargetGR.getDelta(), totalTargetGR.getMagLower(), totalTargetGR.getMagUpper(), 1.0, bVal);
		tempGR.scaleToIncrRate(transMagIndex, totalTargetGR.getY(transMagIndex));	// match rates at transition mag
		
		IncrementalMagFreqDist onFaultMFD = new IncrementalMagFreqDist(totalTargetGR.getMinX(), totalTargetGR.size(), totalTargetGR.getDelta());		
		IncrementalMagFreqDist offFaultMFD = new IncrementalMagFreqDist(totalTargetGR.getMinX(), totalTargetGR.size(), totalTargetGR.getDelta());		
		for(int i=0; i<transMagIndex;i++) {
			onFaultMFD.set(i,tempGR.getY(i));
			offFaultMFD.set(i,totalTargetGR.getY(i)-tempGR.getY(i));
		}
		for(int i=transMagIndex; i<totalTargetGR.size(); i++) {
			onFaultMFD.set(i,totalTargetGR.getY(i));
		}

		onFaultMFD.setName("onFaultMFD");
		onFaultMFD.setInfo("(rate(M>=5)="+(float)onFaultMFD.getCumRate(mag5_Index)+"; transMag="+onFaultMFD.getX(transMagIndex)+")");
		offFaultMFD.setName("offFaultMFD");
		offFaultMFD.setInfo("(rate(M>=5)="+(float)offFaultMFD.getCumRate(mag5_Index)+"; maxMag="+onFaultMFD.getX(transMagIndex-1)+")");
		

		// TESTS
		System.out.println("\nInputs:\n");
		System.out.println("\ttotOnFaultMgt5_Rate="+(float)totOnFaultMgt5_Rate);
		double totRate = totalTargetGR.getCumRate(5.05);
		double totMoRate = totalTargetGR.getTotalMomentRate();
		System.out.println("\ttotalTargetGR.getCumRate(5.05)="+(float)totRate);
		System.out.println("\ttotalTargetGR.getTotalMomentRate()="+(float)totMoRate);
		System.out.println("\ttransMag="+(float)onFaultMFD.getX(transMagIndex));
		System.out.println("\tmaxMagOff="+(float)onFaultMFD.getX(transMagIndex-1));

		System.out.println("\nResults:\n");
		System.out.println("\tonFaultMFD.getCumRate(5.05)="+(float)onFaultMFD.getCumRate(5.05)+"\tfraction="+((float)(onFaultMFD.getCumRate(5.05)/totRate)));
		System.out.println("\toffFaultMFD.getCumRate(5.05)="+(float)offFaultMFD.getCumRate(5.05)+"\tfraction="+((float)(offFaultMFD.getCumRate(5.05)/totRate)));
		System.out.println("\tonFaultMFD.getTotalMomentRate()="+(float)onFaultMFD.getTotalMomentRate()+"\tfraction="+((float)(onFaultMFD.getTotalMomentRate()/totMoRate)));
		System.out.println("\toffFaultMFD.getTotalMomentRate()="+(float)offFaultMFD.getTotalMomentRate()+"\tfraction="+((float)(offFaultMFD.getTotalMomentRate()/totMoRate)));
		System.out.println("\nTests (all should be close to 1.0):\n");
		System.out.println("\tTotMoRate: "+(float)(totMoRate/(onFaultMFD.getTotalMomentRate()+offFaultMFD.getTotalMomentRate()))+"\t(totMoRate/(onFaultMFD.getTotalMomentRate()+offFaultMFD.getTotalMomentRate()))");
		System.out.println("\tTotCumRate: "+(float)(totRate/(onFaultMFD.getCumRate(5.05)+offFaultMFD.getCumRate(5.05)))+"\t(totRate/(onFaultMFD.getCumRate(5.05)+offFaultMFD.getCumRate(5.05)))");
		System.out.println("\tOnFaultCumRate: "+(float)(totOnFaultMgt5_Rate/onFaultMFD.getCumRate(5.05))+"\t(totOnFaultMgt5_Rate/onFaultMFD.getCumRate(5.05))");
		System.out.println("\tOffFaultCumRate: "+(float)((totRate-totOnFaultMgt5_Rate)/+offFaultMFD.getCumRate(5.05))+"\t((totRate-totOnFaultMgt5_Rate)/+offFaultMFD.getCumRate(5.05))");

		ArrayList<EvenlyDiscretizedFunc> funcs = new ArrayList<EvenlyDiscretizedFunc>();
		funcs.add(totalTargetGR);
		funcs.add(offFaultMFD);
		funcs.add(onFaultMFD);
		funcs.add(totalTargetGR.getCumRateDistWithOffset());
		funcs.add(offFaultMFD.getCumRateDistWithOffset());
		funcs.add(onFaultMFD.getCumRateDistWithOffset());
		GraphWindow graph = new GraphWindow(funcs, "MFDs");
		graph.setX_AxisRange(5, 9);
		graph.setY_AxisRange(1e-5, 10);
		graph.setYLog(true);
		graph.setX_AxisLabel("Mag");
		graph.setY_AxisLabel("Rate (per year)");

		IncrementalMagFreqDist[] mfds = {onFaultMFD,offFaultMFD};
		return mfds;
	}
	
	

	/**
	 * This returns null if the moRateOffFault is too high or too low to be satisfied 
	 * (mMax would have to be lower than minOffFaultMag (given below) or higher than totalTargetGR.getMagUpper())
	 * @param moRateOffFault
	 * @param totalTargetGR
	 * @param totOnFaultMgt5_Rate
	 * @return
	 */
	public static IncrementalMagFreqDist[]  old_getCharOnFaultTargetMFD(double moRateOffFault, GutenbergRichterMagFreqDist totalTargetGR, double totOnFaultMgt5_Rate) {

		double minOffFaultMag = 6.05;
		double totMoRate = totalTargetGR.getTotalMomentRate();
		double moRateOnFault = totMoRate-moRateOffFault;
		
		// test whether given moRateOffFault is too high and can't be satisfied (if off-fault mMax is one below magUpper)
		int testIndex = totalTargetGR.getXIndex(totalTargetGR.getMagUpper()) - 1;
		IncrementalMagFreqDist[] testMFDs = old_getCharOnFaultTargetMFD(totalTargetGR, totOnFaultMgt5_Rate, testIndex);
		double maxOffFaultMoRate = testMFDs[1].getTotalMomentRate();
		if(maxOffFaultMoRate < moRateOffFault) {
			System.out.println("Error - Can't satisfy given moment (given="+moRateOffFault+" and max possible is "+maxOffFaultMoRate+")");
			return null;
		}
		
		// now test whether given moRateOffFault is too low (satisfying minOffFaultMag would have more moment rate)
		testIndex = totalTargetGR.getXIndex(minOffFaultMag) - 1;
		testMFDs = old_getCharOnFaultTargetMFD(totalTargetGR, totOnFaultMgt5_Rate, testIndex);
		double minOffFaultMoRate = testMFDs[1].getTotalMomentRate();
		if(minOffFaultMoRate > moRateOffFault) {
			System.out.println("Error - Can't satisfy given moment (given="+moRateOffFault+" and min possible is "+minOffFaultMoRate+")");
			return null;
		}

		double fracMoRateDiff = Double.MAX_VALUE;
		int transMagIndex=-1;
		IncrementalMagFreqDist onFaultMFD=null, offFaultMFD=null;
		
		// loop over mMax (from 6.55 to mag upper)
		for(int mMaxOffIndex=totalTargetGR.getXIndex(minOffFaultMag); mMaxOffIndex<totalTargetGR.getXIndex(totalTargetGR.getMagUpper()); mMaxOffIndex++) {
			// mMaxOffIndex ends at 1 increment below totalTargetGR.getMagUpper()
			IncrementalMagFreqDist[] mfds = old_getCharOnFaultTargetMFD(totalTargetGR, totOnFaultMgt5_Rate, mMaxOffIndex);
			IncrementalMagFreqDist tempOnFaultMFD = mfds[0];
			double thisMoRateFracDiff = Math.abs(moRateOnFault-tempOnFaultMFD.getTotalMomentRate())/moRateOnFault;
			if(thisMoRateFracDiff<fracMoRateDiff) {
				onFaultMFD = tempOnFaultMFD;
				offFaultMFD = mfds[1];
				transMagIndex = mMaxOffIndex+1;
				fracMoRateDiff=thisMoRateFracDiff;
			}

		}
		
		// make sure that offFaultMFD.getMinMagWithNonZeroRate() returns correct value
		double mMaxOff = offFaultMFD.getMaxMagWithNonZeroRate();
		double mMaxTest = totalTargetGR.getX(transMagIndex-1);
		if(mMaxOff != mMaxTest)
			throw new RuntimeException("Error: discrepancy with off-fault max mags: "+mMaxOff+" vs "+mMaxTest);

		// TESTS
//		System.out.println("Inputs:");
//		System.out.println("\ttotOnFaultMgt5_Rate="+(float)totOnFaultMgt5_Rate);
//		double totRate = totalTargetGR.getCumRate(5.05);
//		System.out.println("\ttotalTargetGR.getCumRate(5.05)="+(float)totRate);
//		System.out.println("\tmoRateOffFault="+(float)moRateOffFault);
//		System.out.println("\ttotalTargetGR.getTotalMomentRate()="+(float)totMoRate);
//		System.out.println("Results:");
//		System.out.println("\ttransMag="+(float)onFaultMFD.getX(transMagIndex));
//		System.out.println("\tmaxMagOff="+(float)totalTargetGR.getX(transMagIndex-1));
//		System.out.println("\tonFaultMFD.getCumRate(5.05)="+(float)onFaultMFD.getCumRate(5.05)+"\tfraction="+((float)(onFaultMFD.getCumRate(5.05)/totRate)));
//		System.out.println("\toffFaultMFD.getCumRate(5.05)="+(float)offFaultMFD.getCumRate(5.05)+"\tfraction="+((float)(offFaultMFD.getCumRate(5.05)/totRate)));
//		System.out.println("\tonFaultMFD.getTotalMomentRate()="+(float)onFaultMFD.getTotalMomentRate());
//		System.out.println("\toffFaultMFD.getTotalMomentRate()="+(float)offFaultMFD.getTotalMomentRate());
//		System.out.println("Tests (all should be close to 1.0):");
//		System.out.println("\tTotMoRate: "+(float)(totMoRate/(onFaultMFD.getTotalMomentRate()+offFaultMFD.getTotalMomentRate()))+"\t(totMoRate/(onFaultMFD.getTotalMomentRate()+offFaultMFD.getTotalMomentRate()))");
//		System.out.println("\tOnFaultMoRate: "+(float)((totMoRate-moRateOffFault)/onFaultMFD.getTotalMomentRate())+"\t((totMoRate-moRateOffFault)/onFaultMFD.getTotalMomentRate())");
//		System.out.println("\tOffFaultMoRate: "+(float)(moRateOffFault/offFaultMFD.getTotalMomentRate())+"\t(moRateOffFault/offFaultMFD.getTotalMomentRate())");
//		System.out.println("\tTotCumRate: "+(float)(totRate/(onFaultMFD.getCumRate(5.05)+offFaultMFD.getCumRate(5.05)))+"\t(totRate/(onFaultMFD.getCumRate(5.05)+offFaultMFD.getCumRate(5.05)))");
//		System.out.println("\tOnFaultCumRate: "+(float)(totOnFaultMgt5_Rate/onFaultMFD.getCumRate(5.05))+"\t(totOnFaultMgt5_Rate/onFaultMFD.getCumRate(5.05))");
//		System.out.println("\tOffFaultCumRate: "+(float)((totRate-totOnFaultMgt5_Rate)/+offFaultMFD.getCumRate(5.05))+"\t((totRate-totOnFaultMgt5_Rate)/+offFaultMFD.getCumRate(5.05))");
//
//		ArrayList<EvenlyDiscretizedFunc> funcs = new ArrayList<EvenlyDiscretizedFunc>();
//		funcs.add(totalTargetGR);
//		funcs.add(offFaultMFD);
//		funcs.add(onFaultMFD);
//		funcs.add(totalTargetGR.getCumRateDistWithOffset());
//		funcs.add(offFaultMFD.getCumRateDistWithOffset());
//		funcs.add(onFaultMFD.getCumRateDistWithOffset());
//		GraphWindow graph = new GraphWindow(funcs, "MFDs");
//		graph.setX_AxisRange(5, 9);
//		graph.setY_AxisRange(1e-5, 10);
//		graph.setYLog(true);
//		graph.setX_AxisLabel("Mag");
//		graph.setY_AxisLabel("Rate (per year)");

		IncrementalMagFreqDist[] mfds = {onFaultMFD,offFaultMFD};
		return mfds;
	}
	
	
	public static void testAllInversionSetups() {
		
		ArrayList<FaultModels> fltModList = new ArrayList<FaultModels>();
		fltModList.add(FaultModels.FM3_1);
//		fltModList.add(FaultModels.FM3_2);	// 3% higher on-fault MoRate, and 5% lower off-fault moRate;  Not worth looping over
		
		ArrayList<DeformationModels> defModList= new ArrayList<DeformationModels>();
		defModList.add(DeformationModels.GEOLOGIC);
//		defModList.add(DeformationModels.ABM);
//		defModList.add(DeformationModels.NEOKINEMA);
		defModList.add(DeformationModels.ZENGBB);
		
		ArrayList<ScalingRelationships> scalingRelList = new ArrayList<ScalingRelationships>();
		scalingRelList.add(ScalingRelationships.ELLSWORTH_B);
//		scalingRelList.add(ScalingRelationships.HANKS_BAKUN_08);
//		scalingRelList.add(ScalingRelationships.SHAW_2009_MOD);

		ArrayList<InversionModels> invModList = new ArrayList<InversionModels>();
		invModList.add(InversionModels.CHAR_CONSTRAINED);
		invModList.add(InversionModels.GR_CONSTRAINED);
		
		ArrayList<TotalMag5Rate> mag5RateList = new ArrayList<TotalMag5Rate>();
		mag5RateList.add(TotalMag5Rate.RATE_7p9);
		mag5RateList.add(TotalMag5Rate.RATE_6p5);
		mag5RateList.add(TotalMag5Rate.RATE_9p6);
		
		ArrayList<MaxMagOffFault> mMaxOffList = new ArrayList<MaxMagOffFault>();
		mMaxOffList.add(MaxMagOffFault.MAG_7p3);
		mMaxOffList.add(MaxMagOffFault.MAG_7p6);
		mMaxOffList.add(MaxMagOffFault.MAG_7p9);
		
		ArrayList<MomentRateFixes> moRateFixList = new ArrayList<MomentRateFixes>();
		moRateFixList.add(MomentRateFixes.NONE);
//		moRateFixList.add(MomentRateFixes.APPLY_IMPLIED_CC);

		ArrayList<SpatialSeisPDF> seisPDFList = new ArrayList<SpatialSeisPDF>();
		seisPDFList.add(SpatialSeisPDF.UCERF3);
//		seisPDFList.add(SpatialSeisPDF.UCERF2);
//		seisPDFList.add(SpatialSeisPDF.AVG_DEF_MODEL_OFF);
//		seisPDFList.add(SpatialSeisPDF.AVG_DEF_MODEL_ALL);

		boolean first = true;
		ArrayList<String> strings = new ArrayList<String>();
		for(FaultModels fm :fltModList) {
			for(DeformationModels dm :defModList) {
				for(ScalingRelationships sr:scalingRelList) {
					for(InversionModels invMod : invModList) {
						for(TotalMag5Rate r5 : mag5RateList) {
							for(MaxMagOffFault mMaxOff : mMaxOffList) {
								for(MomentRateFixes moRateFix : moRateFixList) {
									for(SpatialSeisPDF seisPDF : seisPDFList) {
										LogicTreeBranch ltb = LogicTreeBranch.fromValues(fm,dm,sr,invMod,r5,mMaxOff,moRateFix,seisPDF);
										InversionFaultSystemRupSet faultSysRupSet = InversionFaultSystemRupSetFactory.forBranch(ltb);
										if(first) {
											strings.add(faultSysRupSet.getPreInversionAnalysisData(true));
											first = false;
										}
										else {
											strings.add(faultSysRupSet.getPreInversionAnalysisData(false));	
										}
									}
								}
							}
						}
					}
				}
			}
		}
		try {
			File dataFile = new File("PreInversionDataForAnalysis0.txt");
			FileWriter fw = new FileWriter(dataFile);
			for(String str: strings) {
				fw.write(str+"\n");
			}
			fw.close ();
		}
		catch (IOException e) {
			System.out.println ("IO exception = " + e );
		}

		System.out.println ("Done making PreInversionDataForAnalysis.txt");

//		for(String str:strings)
//			System.out.println(str);
		
		// now do UCERF2
//		fm = FaultModels.FM2_1;
//		DeformationModels dm = DeformationModels.UCERF2_ALL;
//		for(MagAreaRelationships ma:magAreaList) {
//			FaultSystemRupSet faultSysRupSet = InversionFaultSystemRupSetFactory.forBranch(fm, dm, 
//					ma, AveSlipForRupModels.ELLSWORTH_B, SlipAlongRuptureModels.TAPERED, InversionModels.GR);
//			String label = faultSysRupSet.getDeformationModel().getShortName()+"_"+ma.getShortName();
//			plotImpliedTotalSectGR_MFD(faultSysRupSet, label);				
//		}
	}
	
	
	public static void testImplGR_fracSeisOnFltAssumingSameCC() {
		
		ArrayList<DeformationModels> defModList= new ArrayList<DeformationModels>();
		defModList.add(DeformationModels.GEOLOGIC);
		defModList.add(DeformationModels.ABM);
		defModList.add(DeformationModels.NEOKINEMA);
		defModList.add(DeformationModels.ZENG);
		
		ArrayList<ScalingRelationships> scalingRelList = new ArrayList<ScalingRelationships>();
		scalingRelList.add(ScalingRelationships.ELLSWORTH_B);
		scalingRelList.add(ScalingRelationships.HANKS_BAKUN_08);
		scalingRelList.add(ScalingRelationships.SHAW_2009_MOD);

		ArrayList<MaxMagOffFault> mMaxOffList = new ArrayList<MaxMagOffFault>();
		mMaxOffList.add(MaxMagOffFault.MAG_7p3);
		mMaxOffList.add(MaxMagOffFault.MAG_7p6);
		mMaxOffList.add(MaxMagOffFault.MAG_7p9);

		long startTime = System.currentTimeMillis();
		double MIN_MAG = 0.05;
		int NUM_MAG = 90;
		double DELTA_MAG = 0.1;
		ArrayList<String> strings = new ArrayList<String>();
		for(DeformationModels dm :defModList) {
			for(ScalingRelationships sr:scalingRelList) {
				for(MaxMagOffFault mMaxOff : mMaxOffList) {
					LogicTreeBranch ltb = LogicTreeBranch.fromValues(FaultModels.FM3_1,dm,sr,InversionModels.CHAR_CONSTRAINED,TotalMag5Rate.RATE_7p9,mMaxOff,MomentRateFixes.NONE,SpatialSeisPDF.UCERF3);
					InversionFaultSystemRupSet faultSysRupSet = InversionFaultSystemRupSetFactory.forBranch(ltb);
					double offFltDefModMoRate = DeformationModelsCalc.calcMoRateOffFaultsForDefModel(FaultModels.FM3_1, dm);
					SummedMagFreqDist impliedOnFault_GR_NuclMFD = FaultSystemRupSetCalc.calcImpliedGR_NucleationMFD(faultSysRupSet, MIN_MAG, NUM_MAG, DELTA_MAG);
					GutenbergRichterMagFreqDist trulyOffFaultMFD = new GutenbergRichterMagFreqDist(MIN_MAG, NUM_MAG, DELTA_MAG, MIN_MAG, mMaxOff.getMaxMagOffFault()-DELTA_MAG/2, offFltDefModMoRate, 1.0);
					double onFltFrac = impliedOnFault_GR_NuclMFD.getCumRate(5.05)/(impliedOnFault_GR_NuclMFD.getCumRate(5.05)+trulyOffFaultMFD.getCumRate(5.05));
					String str = (float)onFltFrac+"\t"+dm+"\t"+sr+"\t"+mMaxOff;
					strings.add(str);
					System.out.println(str);
				}
			}
		}
		System.out.println("runtime (min): "+((double)(System.currentTimeMillis()-startTime))/(1000d*60d)); 
		
		System.out.println("Implied Fractions On Fault:");
		for(String str:strings)  System.out.println(str);

	}

	
	/**
	 * This computes a tri-linear MFD for the off-fault MFD on the characteristic branch
	 * 
	 * @param totalTargetGR - the total target MFD
	 * @param totOnFaultMgt5_Rate - the rate of on-fault events at Mge5
	 * @param mMinSeismoOnFault - the average minimum magnitude for seismogenic on-fault ruptures
	 * @param mMaxOffFault - the maximum magnitude for off-fault events.
	 * @return
	 */
	public static IncrementalMagFreqDist getTriLinearCharOffFaultTargetMFD(GutenbergRichterMagFreqDist totalTargetGR, double totOnFaultMgt5_Rate, 
			double mMinSeismoOnFault, double mMaxOffFault) {
		
		int mMinSeismoOnFaultIndex = totalTargetGR.getClosestXIndex(mMinSeismoOnFault);
		int mMaxOffFaultIndex = totalTargetGR.getClosestXIndex(mMaxOffFault);
		
		double offFaultMgt5_Rate = totalTargetGR.getCumRate(5.05) - totOnFaultMgt5_Rate;
		
		// rate corrections since final MFDs are not perfect GRs (found by hand)
		double onCorr = 0.98;
		double offCorr = 1.01;
		
		// create a temp GR with on-fault rate
		GutenbergRichterMagFreqDist tempOnFaultGR = new GutenbergRichterMagFreqDist(totalTargetGR.getMinX(), totalTargetGR.size(), 
				totalTargetGR.getDelta(), totalTargetGR.getMagLower(), totalTargetGR.getMagUpper(), 1.0, 1.0);
		tempOnFaultGR.scaleToCumRate(5.05, totOnFaultMgt5_Rate*onCorr);

		// create a temp GR with off-fault rate
		GutenbergRichterMagFreqDist tempOffFaultGR = new GutenbergRichterMagFreqDist(totalTargetGR.getMinX(), totalTargetGR.size(), 
				totalTargetGR.getDelta(), totalTargetGR.getMagLower(), totalTargetGR.getMagUpper(), 1.0, 1.0);
		tempOffFaultGR.scaleToCumRate(5.05, offFaultMgt5_Rate*offCorr);

		// now create the desired MFDs
		IncrementalMagFreqDist onFaultMFD = new IncrementalMagFreqDist(totalTargetGR.getMinX(), totalTargetGR.size(), totalTargetGR.getDelta());		
		IncrementalMagFreqDist offFaultMFD = new IncrementalMagFreqDist(totalTargetGR.getMinX(), totalTargetGR.size(), totalTargetGR.getDelta());		
		for(int i=0; i<mMinSeismoOnFaultIndex;i++) {
			onFaultMFD.set(i,tempOnFaultGR.getY(i));
			offFaultMFD.set(i,tempOffFaultGR.getY(i));
		}
		for(int i=mMinSeismoOnFaultIndex; i<=mMaxOffFaultIndex+1; i++) {
			double wtOnTotRate = (double)(i-mMinSeismoOnFaultIndex)/(double)((mMaxOffFaultIndex+1)-mMinSeismoOnFaultIndex); // starts at zero and builds
			double wtOnFaultRate = 1.0-wtOnTotRate;
			// way 1
//			double onFltRate = wtOnFaultRate*tempOnFaultGR.getY(i)+wtOnTotRate*totalTargetGR.getY(i);
//			onFaultMFD.set(i,onFltRate);
//			offFaultMFD.set(i,totalTargetGR.getY(i)-onFltRate);
			// way 2 (same as Way 1)
//			offFaultMFD.set(i,tempOffFaultGR.getY(i)*wtOnFaultRate);
//			onFaultMFD.set(i,totalTargetGR.getY(i)-offFaultMFD.getY(i));
			// way 3
			double onFltRate = Math.pow(10,wtOnFaultRate*Math.log10(tempOnFaultGR.getY(i)) + wtOnTotRate*Math.log10(totalTargetGR.getY(i)));
			onFaultMFD.set(i,onFltRate);
			offFaultMFD.set(i,totalTargetGR.getY(i)-onFltRate);
			if(offFaultMFD.getY(i) < 0 ) offFaultMFD.set(i,0); // numerical precision issue at last point if mMaxOffFault = totalTargetGR.getMagUpper()
		}
		for(int i=mMaxOffFaultIndex+1; i<totalTargetGR.size(); i++) {
			onFaultMFD.set(i,totalTargetGR.getY(i));
			offFaultMFD.set(i,0);
		}

		onFaultMFD.setName("onFaultMFD");
		onFaultMFD.setInfo("(rate(M>=5)="+(float)onFaultMFD.getCumRate(5.05)+"; totMoRate="+onFaultMFD.getTotalMomentRate()+")");
		offFaultMFD.setName("offFaultMFD");
		offFaultMFD.setInfo("(rate(M>=5)="+(float)offFaultMFD.getCumRate(5.05)+"; totMoRate="+offFaultMFD.getTotalMomentRate()+")");
		

//		// TESTS
//		System.out.println("\nInputs:\n");
//		System.out.println("\ttotOnFaultMgt5_Rate = "+(float)totOnFaultMgt5_Rate);
//		double totRate = totalTargetGR.getCumRate(5.05);
//		double totMoRate = totalTargetGR.getTotalMomentRate();
//		System.out.println("\ttotalTargetGR.getCumRate(5.05) = "+(float)totRate);
//		System.out.println("\ttotalTargetGR.getTotalMomentRate() = "+(float)totMoRate);
//		System.out.println("\tmMinSeismoOnFault="+(float)mMinSeismoOnFault);
//		System.out.println("\tmMaxOffFault="+(float)mMaxOffFault);
//
//		System.out.println("\nResults:\n");
//		System.out.println("\tonFaultMFD.getCumRate(5.05) = "+(float)onFaultMFD.getCumRate(5.05)+"\tfraction="+((float)(onFaultMFD.getCumRate(5.05)/totRate)));
//		System.out.println("\toffFaultMFD.getCumRate(5.05) = "+(float)offFaultMFD.getCumRate(5.05)+"\tfraction="+((float)(offFaultMFD.getCumRate(5.05)/totRate)));
//		System.out.println("\ttotal implied Rate(>=5.05) = "+(float)(offFaultMFD.getCumRate(5.05)+onFaultMFD.getCumRate(5.05)));
//		System.out.println("\tonFaultMFD.getTotalMomentRate() = "+(float)onFaultMFD.getTotalMomentRate()+"\tfraction="+((float)(onFaultMFD.getTotalMomentRate()/totMoRate)));
//		System.out.println("\toffFaultMFD.getTotalMomentRate() = "+(float)offFaultMFD.getTotalMomentRate()+"\tfraction="+((float)(offFaultMFD.getTotalMomentRate()/totMoRate)));
//		System.out.println("\nTests (all should be close to 1.0):\n");
//		System.out.println("\tTotMoRate: "+(float)(totMoRate/(onFaultMFD.getTotalMomentRate()+offFaultMFD.getTotalMomentRate()))+"\t(totMoRate/(onFaultMFD.getTotalMomentRate()+offFaultMFD.getTotalMomentRate()))");
//		System.out.println("\tTotCumRate: "+(float)(totRate/(onFaultMFD.getCumRate(5.05)+offFaultMFD.getCumRate(5.05)))+"\t(totRate/(onFaultMFD.getCumRate(5.05)+offFaultMFD.getCumRate(5.05)))");
//		System.out.println("\tOnFaultCumRate: "+(float)(totOnFaultMgt5_Rate/onFaultMFD.getCumRate(5.05))+"\t(totOnFaultMgt5_Rate/onFaultMFD.getCumRate(5.05))");
//		System.out.println("\tOffFaultCumRate: "+(float)((totRate-totOnFaultMgt5_Rate)/+offFaultMFD.getCumRate(5.05))+"\t((totRate-totOnFaultMgt5_Rate)/+offFaultMFD.getCumRate(5.05))");
//
//		ArrayList<EvenlyDiscretizedFunc> funcs = new ArrayList<EvenlyDiscretizedFunc>();
//		funcs.add(totalTargetGR);
//		funcs.add(offFaultMFD);
//		funcs.add(onFaultMFD);
////		funcs.add(totalTargetGR.getCumRateDistWithOffset());
////		funcs.add(offFaultMFD.getCumRateDistWithOffset());
////		funcs.add(onFaultMFD.getCumRateDistWithOffset());
//		GraphWindow graph = new GraphWindow(funcs, "MFDs");
//		graph.setX_AxisRange(5, 9);
//		graph.setY_AxisRange(1e-5, 10);
//		graph.setYLog(true);
//		graph.setX_AxisLabel("Mag");
//		graph.setY_AxisLabel("Rate (per year)");
//		graph.setPlotLabelFontSize(18);
//		graph.setAxisLabelFontSize(16);
//		graph.setTickLabelFontSize(14);

//		IncrementalMagFreqDist[] mfds = {onFaultMFD,offFaultMFD};
		return offFaultMFD;
	}
	
	/**
	 * This solves for the mMaxOffFault for the given moRateOffFault.
	 * This assumes that mMaxOffFault > mMinSeismoOnFault, and null is returned if it's 
	 * impossible to match the given moment (there is no mMaxOffFault large or small enough to match the given moment)
	 * 
	 * @param moRateOffFault
	 * @param totalTargetGR
	 * @param totOnFaultMgt5_Rate
	 * @param mMinSeismoOnFault
	 * @return
	 */
	public static IncrementalMagFreqDist getTriLinearCharOffFaultTargetMFD(double moRateOffFault, GutenbergRichterMagFreqDist totalTargetGR, double totOnFaultMgt5_Rate, 
			double mMinSeismoOnFault) {

		double min_mMaxOffFault = totalTargetGR.getX(totalTargetGR.getClosestXIndex(mMinSeismoOnFault)+1);	// the mimim that the off-fault Mmax can be
		
		// test whether given moRateOffFault is too high and can't be satisfied (if off-fault mMax is one below magUpper)
		double magUpper = totalTargetGR.getMagUpper();
		IncrementalMagFreqDist maxOffFaultMFD = getTriLinearCharOffFaultTargetMFD(totalTargetGR, totOnFaultMgt5_Rate, mMinSeismoOnFault, magUpper);
		double maxOffFaultMoRate = maxOffFaultMFD.getTotalMomentRate();
// System.out.println("maxOffFaultMoRate="+(float)maxOffFaultMoRate);
		if(maxOffFaultMoRate < moRateOffFault) {
			System.out.println("Error - Can't satisfy given moment (given="+moRateOffFault+" and max possible is "+maxOffFaultMoRate+")");
			return null;
		}
		
		// now test whether given moRateOffFault is too low (satisfying minOffFaultMag would have more moment rate)
		IncrementalMagFreqDist minOffFaultMFD = getTriLinearCharOffFaultTargetMFD(totalTargetGR, totOnFaultMgt5_Rate, mMinSeismoOnFault, min_mMaxOffFault);
		double minOffFaultMoRate = minOffFaultMFD.getTotalMomentRate();
// System.out.println("minOffFaultMoRate="+(float)minOffFaultMoRate);
		if(minOffFaultMoRate > moRateOffFault) {
			System.out.println("Error - Can't satisfy given moment (given="+moRateOffFault+" and min possible is "+minOffFaultMoRate+")");
			return null;
		}

		IncrementalMagFreqDist offFaultMFD=null;
		double fracMoRateDiff = Double.MAX_VALUE;
		double mMaxOff=0;
		// now loop over all mMaxOff values and keep the one with the clostest moment rate
		for(int mMaxOffIndex=totalTargetGR.getXIndex(min_mMaxOffFault); mMaxOffIndex<=totalTargetGR.getXIndex(totalTargetGR.getMagUpper()); mMaxOffIndex++) {
			double test_mMaxOff = totalTargetGR.getX(mMaxOffIndex);
			IncrementalMagFreqDist testOffFaultMFD = getTriLinearCharOffFaultTargetMFD(totalTargetGR, totOnFaultMgt5_Rate, mMinSeismoOnFault, test_mMaxOff);
			double thisMoRateFracDiff = Math.abs(moRateOffFault-testOffFaultMFD.getTotalMomentRate())/moRateOffFault;
//			System.out.println(mMaxOffIndex+"\t"+(float)test_mMaxOff+"\t"+(float)thisMoRateFracDiff);
			if(thisMoRateFracDiff<fracMoRateDiff) {
				offFaultMFD = testOffFaultMFD;
				mMaxOff = test_mMaxOff;
				fracMoRateDiff=thisMoRateFracDiff;
			}
		}
		
		// make sure that offFaultMFD.getMinMagWithNonZeroRate() returns correct value
		double mMaxTest = offFaultMFD.getMaxMagWithNonZeroRate();
		if(mMaxOff != mMaxTest)
			throw new RuntimeException("Error: discrepancy with off-fault max mags: "+mMaxOff+" vs "+mMaxTest);

//		System.out.println("mMaxOff = "+mMaxOff);
//		double finalMoRate = offFaultMFD.getTotalMomentRate();
//		System.out.println("moRate (and ratio to target) = "+(float)finalMoRate+"\t("+(float)(finalMoRate/moRateOffFault)+")");
//		double targetRate = totalTargetGR.getCumRate(5.05)-totOnFaultMgt5_Rate;
//		double finalRate = offFaultMFD.getCumRate(5.05);
//		System.out.println("rate(M>=5) (and ratio to target) = "+(float)finalRate+"\t("+(float)(finalRate/targetRate)+")");

		return offFaultMFD;
	}
	
	
	/**
	 * This gets the total (summed) sub-seismogenic MFD for all fault sections for the characteristic model 
	 * (summing the MFDs from getCharSubSeismoOnFaultMFD_forEachSection(*))
	 * @param fltSysRupSet
	 * @param gridSeisUtils
	 * @param totalTargetGR
	 * @return
	 */
	public static SummedMagFreqDist getCharSubSeismoOnFaultMFD(InversionFaultSystemRupSet fltSysRupSet, GriddedSeisUtils gridSeisUtils, 
			GutenbergRichterMagFreqDist totalTargetGR) {
		SummedMagFreqDist mfd = new SummedMagFreqDist(totalTargetGR.getMinX(), totalTargetGR.size(), totalTargetGR.getDelta());
		for(GutenbergRichterMagFreqDist gr : getCharSubSeismoOnFaultMFD_forEachSection(fltSysRupSet, gridSeisUtils, totalTargetGR)) {
			mfd.addIncrementalMagFreqDist(gr);
		}
		
//		ArrayList<IncrementalMagFreqDist> funcs = new ArrayList<IncrementalMagFreqDist>();
//		funcs.add(mfd);
//		funcs.add(totalTargetGR);
//		GraphWindow graph = new GraphWindow(funcs, "Total On Fault MFD");

		return mfd;
	}


	/**
	 * This gets the sub-seismogenic MFD for each fault section for the characteristic model,
	 * where each fault gets a GR up to just below the minimum seismogenic magnitude, with a total rate
	 * equal to the rate of events inside the fault section polygon (as determined by the
	 * spatialSeisPDF and tatal regional rate).
	 * @param fltSysRupSet
	 * @param spatialSeisPDF
	 * @param totalTargetGR
	 * @return
	 */
	public static ArrayList<GutenbergRichterMagFreqDist> getCharSubSeismoOnFaultMFD_forEachSection(
			InversionFaultSystemRupSet invRupSet,
			GriddedSeisUtils gridSeisUtils,
			GutenbergRichterMagFreqDist totalTargetGR) {
		
		ArrayList<GutenbergRichterMagFreqDist> mfds = new ArrayList<GutenbergRichterMagFreqDist>();
		double totMgt5_rate = totalTargetGR.getCumRate(0);
		for(int s=0; s<invRupSet.getNumSections(); s++) {
			double sectRate = gridSeisUtils.pdfValForSection(s)*totMgt5_rate;
//			int mMaxIndex = totalTargetGR.getClosestXIndex(fltSysRupSet.getMinMagForSection(s))-1;	// subtract 1 to avoid overlap
			int mMaxIndex = totalTargetGR.getXIndex(invRupSet.getUpperMagForSubseismoRuptures(s));
			if(mMaxIndex == -1) throw new RuntimeException("Problem Mmax: "
					+invRupSet.getUpperMagForSubseismoRuptures(s)
					+"\t"+invRupSet.getFaultSectionData(s).getName()+"\tBRANCH: "+invRupSet.getLogicTreeBranch());
			double mMax = totalTargetGR.getX(mMaxIndex); // rounded to nearest MFD value
if(mMax<5.85)
	System.out.println("PROBLEM SubSesMmax=\t"+mMax+"\tMinSeismoRupMag=\t"
			+invRupSet.getFinalMinMagForSection(s)+"\t"+invRupSet.getFaultSectionData(s).getName());
			GutenbergRichterMagFreqDist tempOnFaultGR = new GutenbergRichterMagFreqDist(totalTargetGR.getMinX(), totalTargetGR.size(), 
					totalTargetGR.getDelta(), totalTargetGR.getMagLower(), mMax, 1.0, 1.0);
			tempOnFaultGR.scaleToCumRate(0, sectRate);
			// check for NaN rates
			if(Double.isNaN(tempOnFaultGR.getTotalIncrRate())) {
				throw new RuntimeException("Bad MFD for section:\t"+s+"\t"+invRupSet.getFaultSectionData(s).getName()+
						"\tsectRate="+sectRate+"\tgridSeisUtils.pdfValForSection(s) = "+gridSeisUtils.pdfValForSection(s)+"\tmMax = "+mMax);
			}
			mfds.add(tempOnFaultGR);
		}
		return mfds;
	}

	
	/**
	 * 
	 * @param invFltSysRupSet
	 * @param plotNvsScalTargets
	 * @param plotSumTests
	 * @param addUCERF2_MFDs
	 * @param pdfFileName - set as null if you don't want to save a PDF file
	 */
	public static void plotPreInversionMFDs(InversionFaultSystemRupSet invFltSysRupSet, boolean plotNvsScalTargets, 
			boolean plotSumTests, boolean addUCERF2_MFDs, String pdfFileName) {
		
		boolean isGR = invFltSysRupSet.getLogicTreeBranch().getValue(InversionModels.class).isGR();
		System.out.println("isGR="+isGR);
		
		InversionTargetMFDs inversionMFDs = invFltSysRupSet.getInversionTargetMFDs();
		
		SummedMagFreqDist targetOnFaultSupraSeisMFD  =inversionMFDs.getOnFaultSupraSeisMFD();
		targetOnFaultSupraSeisMFD.setInfo("Rate(M>=5)="+(float)targetOnFaultSupraSeisMFD.getCumRate(5.05)+"\tMoRate="+(float)targetOnFaultSupraSeisMFD.getTotalMomentRate());
		
		IncrementalMagFreqDist trulyOffFaultMFD = inversionMFDs.getTrulyOffFaultMFD();
		String infoString = "Rate(M>=5)="+(float)trulyOffFaultMFD.getCumRate(5.05)+"\tMoRate="+(float)trulyOffFaultMFD.getTotalMomentRate();
		if(trulyOffFaultMFD instanceof TaperedGR_MagFreqDist) {
			infoString += "\tCornerMag="+(float)((TaperedGR_MagFreqDist)trulyOffFaultMFD).getMagCorner();
		}
		trulyOffFaultMFD.setInfo(infoString);
				
		SummedMagFreqDist totalSubSeismoOnFaultMFD = inversionMFDs.getTotalSubSeismoOnFaultMFD();
		totalSubSeismoOnFaultMFD.setInfo("Rate(M>=5)="+(float)totalSubSeismoOnFaultMFD.getCumRate(5.05)+"\tMoRate="+(float)totalSubSeismoOnFaultMFD.getTotalMomentRate());
		
		GutenbergRichterMagFreqDist totalTargetGR = inversionMFDs.getTotalTargetGR();
		totalTargetGR.setInfo("Rate(M>=5)="+(float)totalTargetGR.getCumRate(5.05)+"\tMoRate="+(float)totalTargetGR.getTotalMomentRate());
		
		SummedMagFreqDist totalGridSeis = new SummedMagFreqDist(totalTargetGR.getX(0),totalTargetGR.size(),totalTargetGR.getDelta());
		totalGridSeis.addIncrementalMagFreqDist(trulyOffFaultMFD);
		totalGridSeis.addIncrementalMagFreqDist(totalSubSeismoOnFaultMFD);
		totalGridSeis.setName("totalGridSeis (trulyOffFaultMFD plus totalSubSeismoOnFaultMFD)");
		totalGridSeis.setInfo("Rate(M>=5)="+(float)totalGridSeis.getCumRate(5.05)+"\tMoRate="+(float)totalGridSeis.getTotalMomentRate());							
	
		ArrayList<IncrementalMagFreqDist> mfds = new ArrayList<IncrementalMagFreqDist>();
		mfds.add(targetOnFaultSupraSeisMFD);
		mfds.add(trulyOffFaultMFD);
		mfds.add(totalSubSeismoOnFaultMFD);
		mfds.add(totalTargetGR);
		mfds.add(totalGridSeis);
		ArrayList<PlotCurveCharacterstics> plotChars = new ArrayList<PlotCurveCharacterstics>();
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID,2f,Color.CYAN));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID,2f,Color.GREEN));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID,2f,Color.PINK));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID,2f,Color.BLACK));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID,2f,Color.GRAY));
		
		System.out.println("targetOnFaultSupraSeisMFD R>=6.5:\t"+targetOnFaultSupraSeisMFD.getCumRate(6.55));
		System.out.println("trulyOffFaultMFD R>=6.5:\t"+trulyOffFaultMFD.getCumRate(6.55));
		System.out.println("totalSubSeismoOnFaultMFD R>=6.5:\t"+totalSubSeismoOnFaultMFD.getCumRate(6.55));
		System.out.println("totalTargetGR R>=6.5:\t"+totalTargetGR.getCumRate(6.55));
		
		if(isGR) {	// plot total implied target
			SummedMagFreqDist totalMFDsum = new SummedMagFreqDist(totalTargetGR.getX(0),totalTargetGR.size(),totalTargetGR.getDelta());
			totalMFDsum.addIncrementalMagFreqDist(targetOnFaultSupraSeisMFD);
			totalMFDsum.addIncrementalMagFreqDist(trulyOffFaultMFD);
			totalMFDsum.addIncrementalMagFreqDist(totalSubSeismoOnFaultMFD);
			totalMFDsum.setName("targetOnFaultSupraSeisMFD+totalSubSeismoOnFaultMFD+trulyOffFaultMFD+");
			totalMFDsum.setInfo("Rate(M>=5)="+(float)totalMFDsum.getCumRate(5.05)+"\tMoRate="+(float)totalMFDsum.getTotalMomentRate());							
			mfds.add(totalMFDsum);
			plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID,2f,Color.ORANGE));		
		}
		else { // plot total target on-fault distribution
			SummedMagFreqDist totalOnFaultTarget = new SummedMagFreqDist(totalTargetGR.getX(0),totalTargetGR.size(),totalTargetGR.getDelta());
			totalOnFaultTarget.addIncrementalMagFreqDist(totalTargetGR);
			totalOnFaultTarget.subtractIncrementalMagFreqDist(trulyOffFaultMFD);
			totalOnFaultTarget.setName("totalOnFaultTarget (totalTargetGR minus trulyOffFaultMFD)");
			totalOnFaultTarget.setInfo("Rate(M>=5)="+(float)totalOnFaultTarget.getCumRate(5.05)+"\tMoRate="+(float)totalOnFaultTarget.getTotalMomentRate());							
			mfds.add(totalOnFaultTarget);
			plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID,2f,Color.ORANGE));
		}

		if(plotSumTests) {
			SummedMagFreqDist totalTest = new SummedMagFreqDist(totalTargetGR.getX(0),totalTargetGR.size(),totalTargetGR.getDelta());
			totalTest.addIncrementalMagFreqDist(trulyOffFaultMFD);
			totalTest.addIncrementalMagFreqDist(totalSubSeismoOnFaultMFD);
			totalTest.setName("totalTest (trulyOffFaultMFD plus totalSubSeismoOnFaultMFD plus targetOnFaultSupraSeisMFD)");
			totalTest.setInfo("Rate(M>=5)="+(float)totalTest.getCumRate(5.05)+"\tMoRate="+(float)totalTest.getTotalMomentRate());	
			mfds.add(totalTest);
			plotChars.add(new PlotCurveCharacterstics(PlotSymbol.CROSS,4f,Color.BLACK));
		}

		
		if(plotNvsScalTargets) {
			IncrementalMagFreqDist noCalTargetFaultMFD  =inversionMFDs.getMFD_ConstraintsForNoAndSoCal().get(0).getMagFreqDist();
			noCalTargetFaultMFD.setName("noCalTargetFaultMFD");
			noCalTargetFaultMFD.setInfo("Rate(M>=5)="+(float)noCalTargetFaultMFD.getCumRate(5.05)+"\tMoRate="+(float)noCalTargetFaultMFD.getTotalMomentRate());
			IncrementalMagFreqDist soCalTargetFaultMFD  =inversionMFDs.getMFD_ConstraintsForNoAndSoCal().get(1).getMagFreqDist();
			soCalTargetFaultMFD.setName("soCalTargetFaultMFD");
			soCalTargetFaultMFD.setInfo("Rate(M>=5)="+(float)soCalTargetFaultMFD.getCumRate(5.05)+"\tMoRate="+(float)soCalTargetFaultMFD.getTotalMomentRate());
			mfds.add(noCalTargetFaultMFD);
			mfds.add(soCalTargetFaultMFD);
			plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID,2f,Color.ORANGE));
			plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID,2f,Color.YELLOW));
			if(plotSumTests) {
				SummedMagFreqDist testTarget = new SummedMagFreqDist(soCalTargetFaultMFD.getX(0),soCalTargetFaultMFD.size(),soCalTargetFaultMFD.getDelta());
				testTarget.addIncrementalMagFreqDist(noCalTargetFaultMFD);
				testTarget.addIncrementalMagFreqDist(soCalTargetFaultMFD);
				testTarget.setName("testTarget (should equal targetOnFaultSupraSeisMFD");
				testTarget.setInfo("Rate(M>=5)="+(float)testTarget.getCumRate(5.05)+"\tMoRate="+(float)testTarget.getTotalMomentRate());							
				mfds.add(testTarget);
				plotChars.add(new PlotCurveCharacterstics(PlotSymbol.CROSS,4f,Color.CYAN));

			}
		}
		
		if(addUCERF2_MFDs) {
			UCERF2_MFD_ConstraintFetcher u2fetcher = new UCERF2_MFD_ConstraintFetcher(RELM_RegionUtils.getGriddedRegionInstance());
			mfds.add(u2fetcher.getTotalMFD());
			mfds.add(u2fetcher.getBackgroundSeisMFD());
			mfds.add(u2fetcher.getFaultMFD());
			mfds.add(u2fetcher.getTargetMinusBackgroundMFD());
			plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID,2f,Color.RED));
			plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID,2f,Color.MAGENTA));
			plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID,2f,Color.BLUE));
			plotChars.add(new PlotCurveCharacterstics(PlotLineType.DASHED,2f,Color.CYAN));
		}

		// plot orig GR nucleation MFD if GR branch
		if(isGR) {
			SummedMagFreqDist impliedOnFault_GR_NuclMFD = calcImpliedGR_NucleationMFD(invFltSysRupSet, InversionTargetMFDs.MIN_MAG, InversionTargetMFDs.NUM_MAG, InversionTargetMFDs.DELTA_MAG);
			impliedOnFault_GR_NuclMFD.setName("ImpliedGR_NucleationMFD");
			impliedOnFault_GR_NuclMFD.setInfo("(if every section nucleates a GR with no implied coupling coeff)");
			mfds.add(impliedOnFault_GR_NuclMFD);
			plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID,2f,Color.DARK_GRAY));
		}
		
		GraphWindow graph = new GraphWindow(mfds, "Pre-Inversion MFDs", plotChars);
		graph.setX_AxisRange(5, 9);
		graph.setY_AxisRange(1e-5, 20);
		graph.setYLog(true);
		graph.setX_AxisLabel("Mag");
		graph.setY_AxisLabel("Rate (per year)");

		graph.setTickLabelFontSize(14);
		graph.setAxisLabelFontSize(16);
		graph.setPlotLabelFontSize(18);
		if(pdfFileName != null)
			try {
				graph.saveAsPDF(pdfFileName);
			} catch (IOException e) {
				e.printStackTrace();
			}
	}
	
	/**
	 * The computes ratios between the implied DDW (since slip does not always 
	 * depend on DDW) and actual DDW for each rupture
	 * @param invFltSysRupSet
	 * @return
	 */
	public static String calcImplDDWvsDDW_Ratio(InversionFaultSystemRupSet invFltSysRupSet) {
		String result = "";
		
//		DefaultXY_DataSet ratioVsLengthData = new DefaultXY_DataSet();
		HistogramFunction hist = new HistogramFunction(0.1, 100, 0.1);
		double ave=0, min=Double.MAX_VALUE, max=Double.NEGATIVE_INFINITY;
		int minIndex=-1;
		for(int r=0; r<invFltSysRupSet.getNumRuptures();r++) {
			double moment = MagUtils.magToMoment(invFltSysRupSet.getMagForRup(r));
			double slip = invFltSysRupSet.getAveSlipForRup(r);
			double length = invFltSysRupSet.getLengthForRup(r);	
			double implWidth = moment/(slip*length*FaultMomentCalc.SHEAR_MODULUS);
			
			double width = invFltSysRupSet.getAveWidthForRup(r);
			
			double ratio = implWidth/width;
			hist.add(ratio, 1.0);
			ave += ratio;
			if(min>ratio) {
				min=ratio;
				if(ratio >=1.01 || ratio <= 0.99)
					minIndex=r;
			}
			if(max<ratio) max=ratio;
			
//			ratioVsLengthData.set(length/1000d, ratio);
		}
		
		ave /= invFltSysRupSet.getNumRuptures();
		
//		GraphWindow graph = new GraphWindow(ratioVsLengthData, "ratio vs length");
//		graph.setX_AxisLabel("length (km)");
//		graph.setY_AxisLabel("implied/actual DDW");
		
		result = "\taveRatio="+(float)ave+"\tmin="+(float)min+"\tmax="+(float)max+"\tminIndex="+minIndex+"\n";
		result += "\tnon-zero bins (ratio, num rups):\n";
		for(int i=0; i<hist.size();i++)
			if(hist.getY(i) > 0.0)
				result += "\t\t"+(float)hist.getX(i)+"\t"+(float)hist.getY(i)+"\n";
		
//		System.out.println("aveRatio="+ave+"\tmin="+min+"\tmax="+max+"\tminIndex="+minIndex+"\n");
		
//		ArrayList<Integer>  secIndicesForRup = invFltSysRupSet.getSectionsIndicesForRup(minIndex);
//		for(int i:secIndicesForRup) {
//			System.out.println(invFltSysRupSet.getFaultSectionData(i).getName());
//		}

		return result;
	}
	
	/**
	 * The computes ratios between the implied DDW (since slip does not always 
	 * depend on DDW) and actual DDW for each rupture for each M(A) D(L) branch
	 * combination
	 * @param invFltSysRupSet
	 * @return
	 */
	public static void testAllImpliedDDWs() {
		String result="";
		
		ArrayList<ScalingRelationships> scalingRelList = new ArrayList<ScalingRelationships>();
		scalingRelList.add(ScalingRelationships.ELLSWORTH_B);
		scalingRelList.add(ScalingRelationships.HANKS_BAKUN_08);
		scalingRelList.add(ScalingRelationships.SHAW_2009_MOD);
		scalingRelList.add(ScalingRelationships.ELLB_SQRT_LENGTH);
		scalingRelList.add(ScalingRelationships.SHAW_CONST_STRESS_DROP);
		
		
		FaultModels fm = FaultModels.FM3_1;
		DeformationModels dm = DeformationModels.GEOLOGIC;
		
		result += "RESULTS FOR:\t"+fm+"  &  "+dm+"\n";
		
		for(ScalingRelationships sr : scalingRelList) {
				InversionFaultSystemRupSet faultSysRupSet = InversionFaultSystemRupSetFactory.forBranch(fm, dm, 
						sr, SlipAlongRuptureModels.TAPERED, InversionModels.GR_CONSTRAINED);
				result += "\n"+sr+":\n";
				result += calcImplDDWvsDDW_Ratio(faultSysRupSet);
						
		}
		
		System.out.println(result);

	}
	
	/**
	 * Returns true if the given rupture occurs on multiple named faults as defined by
	 * <code>FaultModels.getNamedFaultsMap()</code>.
	 * 
	 * @param rupSet
	 * @param rupIndex
	 * @return
	 */
	public static boolean isRupMultiplyNamed(InversionFaultSystemRupSet rupSet, int rupIndex) {
		Map<Integer, List<Integer>> namedMap = rupSet.getFaultModel().getNamedFaultsMap();
		
		List<Integer> parents = rupSet.getParentSectionsForRup(rupIndex);
		
		List<Integer> named = null;
		for (Integer parent : parents) {
			if (named == null) {
				named = namedMap.get(parent);
				Preconditions.checkNotNull(named, "Parent ID '"+parent+"' not found in named faults file!");
			}
			if (!named.contains(parent))
				return true;
		}
		
		return false;
	}
	
	
	
	/**
	 * Calculates the rate of ruptures above the given magnitude that occur on multiple named faults. If
	 * <code>probPaleoVisible</code> is true then rates are convolved with their paleo visible probability.
	 * @param sol
	 * @param minMag
	 * @param probPaleoVisible
	 * @return
	 */
	public static double calcTotRateMultiplyNamedFaults(InversionFaultSystemSolution sol, double minMag, PaleoProbabilityModel paleoProbModel) {
		double rate = 0;
		InversionFaultSystemRupSet rupSet = sol.getRupSet();
		for (int rupIndex=0; rupIndex<rupSet.getNumRuptures(); rupIndex++) {
			double mag = rupSet.getMagForRup(rupIndex);
			if (mag < minMag)
				continue;
			if (isRupMultiplyNamed(rupSet, rupIndex)) {
				double rupRate = sol.getRateForRup(rupIndex);
				if (paleoProbModel != null)
					rupRate *= paleoProbModel.getProbPaleoVisible(mag, 0.5);
				rate += rupRate;
			}
		}
		return rate;
	}
	
	/**
	 * This returns the total rate of events at or above the given minimum magnitude. If <code>probPaleoVisible</code>
	 * is not null then rates are multiplied by the paleo visible probability.
	 * 
	 * @param sol
	 * @param minMag
	 * @param paleoProbModel
	 * @return
	 */
	public static double calcTotRateAboveMag(FaultSystemSolution sol, double minMag, PaleoProbabilityModel paleoProbModel) {
		FaultSystemRupSet rupSet = sol.getRupSet();
		double rate = 0;
		for (int rupIndex=0; rupIndex<rupSet.getNumRuptures(); rupIndex++) {
			double mag = rupSet.getMagForRup(rupIndex);
			if (mag < minMag)
				continue;
			double rupRate = sol.getRateForRup(rupIndex);
			if (paleoProbModel != null)
				rupRate *= paleoProbModel.getProbPaleoVisible(mag, 0.5);
			rate += rupRate;
		}
		return rate;
	}
	
	public static void plotOffFaultTaperedGR_Comparisons(InversionFaultSystemRupSet invFltSysRupSet, String fileNamePrefix) {
		InversionTargetMFDs invMFDs = invFltSysRupSet.getInversionTargetMFDs();
		
		double totalRateOffFault = invMFDs.getOffFaultRegionRateMgt5()*1e5;
		
		ArrayList<EvenlyDiscretizedFunc> mfds = new ArrayList<EvenlyDiscretizedFunc>();
		ArrayList<EvenlyDiscretizedFunc> cumMFDs = new ArrayList<EvenlyDiscretizedFunc>();

		for(MaxMagOffFault mMaxVals : MaxMagOffFault.values()) {
			double mMaxOff = mMaxVals.getMaxMagOffFault()-InversionTargetMFDs.DELTA_MAG/2.0;
			GutenbergRichterMagFreqDist offFaultMFD_Truncated = new GutenbergRichterMagFreqDist(InversionTargetMFDs.MIN_MAG, InversionTargetMFDs.NUM_MAG, 
					InversionTargetMFDs.DELTA_MAG, InversionTargetMFDs.MIN_MAG, mMaxOff, 1.0, 1.0);
			offFaultMFD_Truncated.scaleToCumRate(0, totalRateOffFault);
			offFaultMFD_Truncated.setName("Truncated GR with Mmax="+mMaxOff);
			TaperedGR_MagFreqDist	offFaultMFD_Tapered = new TaperedGR_MagFreqDist(InversionTargetMFDs.MIN_MAG, InversionTargetMFDs.NUM_MAG, InversionTargetMFDs.DELTA_MAG);
			offFaultMFD_Tapered.setAllButCornerMag(InversionTargetMFDs.MIN_MAG, offFaultMFD_Truncated.getTotalMomentRate(), totalRateOffFault, 1.0);
			offFaultMFD_Tapered.setName("Tapered GR with Corner Mag ="+offFaultMFD_Tapered.getMagCorner());

			mfds.add(offFaultMFD_Truncated);
			mfds.add(offFaultMFD_Tapered);
			cumMFDs.add(offFaultMFD_Truncated.getCumRateDistWithOffset());
			cumMFDs.add(offFaultMFD_Tapered.getCumRateDistWithOffset());

		}
		
		ArrayList<PlotCurveCharacterstics> plotChars = new ArrayList<PlotCurveCharacterstics>();
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.DASHED,2f,Color.BLACK));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID,2f,Color.BLACK));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.DASHED,2f,Color.RED));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID,2f,Color.RED));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.DASHED,2f,Color.BLUE));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID,2f,Color.BLUE));
		
		GraphWindow graph = new GraphWindow(mfds, "Incremental Off-Fault MFDs", plotChars);
		graph.setX_AxisRange(5, 9);
		graph.setY_AxisRange(1e-5, 20);
		graph.setYLog(true);
		graph.setX_AxisLabel("Magnitude");
		graph.setY_AxisLabel("Rate (per year)");
		graph.setTickLabelFontSize(16);
		graph.setAxisLabelFontSize(16);
		graph.setPlotLabelFontSize(18);
		if(fileNamePrefix != null) {
			try {
				graph.saveAsPDF(fileNamePrefix+"_Incr.pdf");
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
			
		GraphWindow cumGraph = new GraphWindow(cumMFDs, "Cumulative Off-Fault MFDs", plotChars);
		cumGraph.setX_AxisRange(5, 9);
		cumGraph.setY_AxisRange(1e-5, 20);
		cumGraph.setYLog(true);
		cumGraph.setX_AxisLabel("Magnitude");
		cumGraph.setY_AxisLabel("Rate (per year)");
		cumGraph.setTickLabelFontSize(16);
		cumGraph.setAxisLabelFontSize(18);
		cumGraph.setPlotLabelFontSize(18);
		if(fileNamePrefix != null) {
			try {
				cumGraph.saveAsPDF(fileNamePrefix+"_Cum.pdf");
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	
	/**
	 * This method writes out any zero mag bins between the lower and upper magnitudes for each parent section
	 * 
	 * Note that this is using the faultSystemRupSet.getOrigMinMagForSection(s) method rather than the newly added
	 * faultSystemRupSet.getFinalMinMagForSection(s), as the latter option was added after this method was created,
	 * and we haven't studied which is more appropriate.
	 * 
	 * @param faultSystemRupSet
	 * @param minMag
	 * @param numMag
	 * @param deltaMag
	 */
	public static void writeZeroMagBinsForEachParentSection(FaultSystemRupSet faultSystemRupSet, double minMag, int numMag, double deltaMag) {
		String prevParSectName="junk";
		double minSectMag=-1, maxSectMag=-1, minParSectMag=-1, maxParSectMag=-1;
		ArrayList<Double> sectMagList=null;
		List<FaultSectionPrefData> sectDataList = faultSystemRupSet.getFaultSectionDataList();
		for(int s=0; s< sectDataList.size();s++) {
			String parSectName = sectDataList.get(s).getParentSectionName();
			minSectMag = faultSystemRupSet.getOrigMinMagForSection(s);
			maxSectMag = faultSystemRupSet.getMaxMagForSection(s);
			if(!parSectName.equals(prevParSectName)) { // if it's a new parent section
				if(!prevParSectName.equals("junk")) { // Process results
					// print out previous result
					HistogramFunction mfd = new HistogramFunction(minMag, numMag, deltaMag);
					for(double mag:sectMagList)
						mfd.add(mag, 1.0);
					ArrayList<Double> missingMagList = new ArrayList<Double>();
					for(int m = mfd.getClosestXIndex(minParSectMag); m <= mfd.getClosestXIndex(maxParSectMag); m++) {
						if(mfd.getY(m) <0.5) 
							missingMagList.add(mfd.getX(m));
					}
					if(missingMagList.size()>0) {
						System.out.print("\n"+prevParSectName);
//						System.out.print("\n"+prevParSectName+"\t"+(float)mfd.getX(mfd.getClosestXIndex(minParSectMag))
//								+"\t"+(float)mfd.getX(mfd.getClosestXIndex(maxParSectMag)));
						for(double mag: missingMagList)
							System.out.print("\t"+(float)mag);
					}
				}
				prevParSectName = parSectName;
				sectMagList = new ArrayList<Double>();
				minParSectMag = minSectMag;
				maxParSectMag = maxSectMag;
			}
			if(minSectMag<minParSectMag) minParSectMag = minSectMag;
			if(maxSectMag>maxParSectMag) maxParSectMag = maxSectMag;
			for(int r: faultSystemRupSet.getRupturesForSection(s)) {
				sectMagList.add(faultSystemRupSet.getMagForRup(r));
			}
		}
		// print out last result (not caught in above loop)
		HistogramFunction mfd = new HistogramFunction(minMag, numMag, deltaMag);
		for(double mag:sectMagList)
			mfd.add(mag, 1.0);
		ArrayList<Double> missingMagList = new ArrayList<Double>();
		for(int m = mfd.getClosestXIndex(minParSectMag); m <= mfd.getClosestXIndex(maxParSectMag); m++) {
			if(mfd.getY(m) <0.5) 
				missingMagList.add(mfd.getX(m));
		}
		if(missingMagList.size()>0) {
			System.out.print("\n"+prevParSectName);
//			System.out.print("\n"+prevParSectName+"\t"+(float)mfd.getX(mfd.getClosestXIndex(minParSectMag))
//					+"\t"+(float)mfd.getX(mfd.getClosestXIndex(maxParSectMag)));
			for(double mag: missingMagList)
				System.out.print("\t"+(float)mag);
		}
	}
	
	
	
	public static void tempSubsectionAreaTest(FaultSystemRupSet faultSystemRupSet) {
		String prevParSectName="junk";
		double minSectArea=1e10, maxSectArea=0, aveArea=0;
		int numArea=0;
		List<FaultSectionPrefData> sectDataList = faultSystemRupSet.getFaultSectionDataList();
		for(int s=0; s< sectDataList.size();s++) {
			String parSectName = sectDataList.get(s).getParentSectionName();
			double area = sectDataList.get(s).getReducedDownDipWidth()*sectDataList.get(s).getTraceLength();
			if(!parSectName.equals(prevParSectName)) { // if it's a new parent section
				if(!prevParSectName.equals("junk")) { // Process last results
					aveArea /= numArea;
					System.out.print("\n"+prevParSectName+"\t"+minSectArea+"\t"+maxSectArea+"\t"+
							(float)((maxSectArea-minSectArea)/minSectArea)+"\t"+numArea+"\t"+aveArea);
				}
				prevParSectName = parSectName;
				minSectArea = area;
				maxSectArea = area;
				aveArea = area;
				numArea = 1;
			}
			else {
				if(minSectArea>area) minSectArea=area;
				if(maxSectArea<area) maxSectArea=area;
				aveArea += area;
				numArea += 1;
			}
		}
		aveArea /= numArea;
		// print out last result (not caught in above loop)
		System.out.print("\n"+prevParSectName+"\t"+minSectArea+"\t"+maxSectArea+"\t"+
				(float)((maxSectArea-minSectArea)/minSectArea)+"\t"+numArea+"\t"+aveArea);
	}
	
	
	public static void tempTest(InversionFaultSystemRupSet faultSystemRupSet) {
				
		String prevParSectName="junk";
		double cumSectArea=0, firstArea=0, firstWidth=0;
		ScalingRelationships scalingRel = faultSystemRupSet.getLogicTreeBranch().getValue(ScalingRelationships.class);
		System.out.println("scalingRel="+scalingRel);
		List<FaultSectionPrefData> sectDataList = faultSystemRupSet.getFaultSectionDataList();
		for(int s=0; s< sectDataList.size();s++) {
			String parSectName = sectDataList.get(s).getParentSectionName();
			double width = sectDataList.get(s).getReducedDownDipWidth();
			double area = width*sectDataList.get(s).getTraceLength();
			if(!parSectName.equals(prevParSectName)) { // it's a new parent section
				System.out.print("\n"+parSectName+"\t"+(float)area+"\t"+(float)width);
				cumSectArea = area;
				firstArea = area;
				firstWidth = width;
				prevParSectName = parSectName;
				// don't write first because it only has one subsection
			}
			else {
				double areaDiff = Math.abs((firstArea-area)/firstArea);
				if(areaDiff>0.01)
					throw new RuntimeException("Areas differ: "+firstArea+"\t"+area+"; widths are:  "+firstWidth+"\t"+width);
				cumSectArea += area;
				double mag = scalingRel.getMag(cumSectArea*1e6, width*1e3);
				System.out.print("\t"+(float)mag+"\t"+(float)(cumSectArea*1e6));
				prevParSectName = parSectName;				
			}
		}
	}

	
	public static void tempTest2(InversionFaultSystemRupSet faultSystemRupSet) {
		for (int r=0;r<faultSystemRupSet.getNumRuptures(); r++) {
			double mag = faultSystemRupSet.getMagForRup(r);
			if(mag<6.0) {
				double rupLength = faultSystemRupSet.getLengthForRup(r)*1e-3;
				double sqrtRupArea = Math.sqrt(faultSystemRupSet.getAreaForRup(r))*1e-3;
				int numSubSect = faultSystemRupSet.getSectionsIndicesForRup(r).size();
				System.out.print("\n"+r+"\t"+mag+"\t"+rupLength+"\t"+sqrtRupArea+"\t"+numSubSect);
				// make list of parent sections involved
				ArrayList<String> parentNameList = new ArrayList<String>();
				for (int s : faultSystemRupSet.getSectionsIndicesForRup(r)) {
					String parentName = faultSystemRupSet.getFaultSectionData(s).getParentSectionName();
					if (!parentNameList.contains(parentName))
						parentNameList.add(parentName);
				}
				for(String name : parentNameList) {
					System.out.print("\t"+name);
				}
			}
		}

	}
	
	
	/**
	 * This computes the final minimum seismogenic rupture mag for each section,
	 * where every subsection is given the greatest among all those of the parent section,
	 * or 6.0 if the latter is below 6.0.  This way all subsections of a parent have the
	 * same value, and each subsection has a rupture at that magnitude.  The only exception
	 * to the systemWideMinSeismoMag threshold is Parkfield, where the minimum is the average
	 * of the "Parkfield" ruptures (defined by InversionInputGenerator.findParkfieldRups(fltSystRupSet)) 
	 * if this average falls below systemWideMinSeismoMag.
	 * 
	 */
	public static double[] computeMinSeismoMagForSections(FaultSystemRupSet fltSystRupSet, double systemWideMinSeismoMag) {
		double[] minMagForSect = new double[fltSystRupSet.getNumSections()];
		String prevParSectName = "junk";
		List<FaultSectionPrefData> sectDataList = fltSystRupSet.getFaultSectionDataList();
		
		// lets first compute the average magnitude for Parkfield events
		double aveParkfieldMag=0;
		List<Integer> parkRupIndexList = InversionInputGenerator.findParkfieldRups(fltSystRupSet);
		for( int parkRupIndex: InversionInputGenerator.findParkfieldRups(fltSystRupSet)) {
			aveParkfieldMag += fltSystRupSet.getMagForRup(parkRupIndex)/(double)parkRupIndexList.size();
		}
		if(D) System.out.println("aveParkfieldMag = "+aveParkfieldMag);
		
		// make map between parent section name and maximum magnitude (magForParSectMap)
		HashMap<String,Double> magForParSectMap = new HashMap<String,Double>();
		int PARKFIELD_PAR_SECT_ID = 32;
		String parkfieldParSectName = null;
		double maxMinSeismoMag=0;
		double minMinSeismoMag=0;	// this is for testing
		for(int s=0; s< sectDataList.size();s++) {
			String parSectName = sectDataList.get(s).getParentSectionName();
			double minSeismoMag = fltSystRupSet.getOrigMinMagForSection(s);
			if(!parSectName.equals(prevParSectName)) { // it's a new parent section
				// set the previous result
				if(!prevParSectName.equals("junk")) {
					magForParSectMap.put(prevParSectName, maxMinSeismoMag);
//					System.out.println(prevParSectName+"\t"+minMinSeismoMag+"\t"+maxMinSeismoMag);
				}
				// reset maxMinSeismoMag & prevParSectName
				maxMinSeismoMag = minSeismoMag;
				minMinSeismoMag = minSeismoMag;
				prevParSectName = parSectName;
				if(sectDataList.get(s).getParentSectionId() == PARKFIELD_PAR_SECT_ID)
					parkfieldParSectName = prevParSectName;
			}
			else {
				if(maxMinSeismoMag<minSeismoMag)
					maxMinSeismoMag=minSeismoMag;
				if(minMinSeismoMag>minSeismoMag)
					minMinSeismoMag=minSeismoMag;
			}
		}
		// do the last one:
		magForParSectMap.put(prevParSectName, maxMinSeismoMag);
//		System.out.println(prevParSectName+"\t"+minMinSeismoMag+"\t"+maxMinSeismoMag);
		
		if(D) System.out.println("parkfieldParSectName = "+parkfieldParSectName);

		
//		for(String parName:magForParSectMap.keySet())
//			System.out.println(parName+"\t"+magForParSectMap.get(parName));
		
		// now set the value for each section in the array, giving a value of systemWideMinMinSeismoMag 
		// if the parent section value falls below this
		for(int s=0; s< sectDataList.size();s++) {
			double minMag = magForParSectMap.get(sectDataList.get(s).getParentSectionName());
			if(minMag>systemWideMinSeismoMag)
				minMagForSect[s] = minMag;
			else
				minMagForSect[s] = systemWideMinSeismoMag;
			
			// allow Parkfield to go below systemWideMinSeismoMag if aveParkfieldMag<systemWideMinSeismoMag
			if(sectDataList.get(s).getParentSectionName().equals(parkfieldParSectName) && aveParkfieldMag<systemWideMinSeismoMag)
				minMagForSect[s] = aveParkfieldMag;

		}
		
		return minMagForSect;
		
//		// test result:
//		try {
//			FileWriter fw = new FileWriter("TestHereItIs");
//			for(int s=0; s< sectDataList.size();s++) {
//				String sectName = sectDataList.get(s).getSectionName();
//				double tempMag = magForParSectMap.get(sectDataList.get(s).getParentSectionName());
//				double origSlipRate = sectDataList.get(s).getOrigAveSlipRate();
//				double aseisSlipFactor = sectDataList.get(s).getAseismicSlipFactor();
//				fw.write(sectName+"\t"+getMinMagForSection(s)+"\t"+tempMag+"\t"+minMagForSectionArray[s]+"\t"+origSlipRate+"\t"+aseisSlipFactor+"\n");
//			}
//			fw.close ();
//		}
//		catch (IOException e) {
//			System.out.println ("IO exception = " + e );
//		}

	}
	
	
	/**
	 * This computes whether each rupture has a magnitude below any of the final minimum mags
	 * for the sections the rupture utilizes. Actually, the magnitude must be below the lower
	 * bin edge implied by the minimum magnitude (since minimum magnitudes are at the bin centers),
	 * so this really tests whether rupMag is below SectionMFD_constraint.getLowerEdgeOfFirstBin(rupMag).
	 * 
	 * Need to pass in finalMinMagForSectArray because this is not available from a FaultSystemRupSet
	 */
	public static boolean[] computeWhichRupsFallBelowSectionMinMags(FaultSystemRupSet fltSystRupSet,
			double[] finalMinMagForSectArray) {
		boolean[] rupBelowSectMinMag = new boolean[fltSystRupSet.getNumRuptures()];
		
		for(int r=0; r<fltSystRupSet.getNumRuptures(); r++) {
			double rupMag = fltSystRupSet.getMagForRup(r);
			List<Integer> indicesForRup = fltSystRupSet.getSectionsIndicesForRup(r);
			boolean magTooSmall = false;
			for(int s:indicesForRup) {
				// lower bin edge for this min mag
				double lowerBinEdge = SectionMFD_constraint.getLowerEdgeOfFirstBin(finalMinMagForSectArray[s]);
				if(rupMag < lowerBinEdge) {	// equal ones would be kept
					magTooSmall = true;
				}
			}
			rupBelowSectMinMag[r] = magTooSmall;
		}
		
		return rupBelowSectMinMag;
		
	}


	/**
	 * This gets a list of SectionMFD_constraint objects (one for each sub-section in InversionFaultSystemRupSet).
	 * Where each has a GR nucleation distribution, and all bins should have at least one rupture represented.
	 * The constraints for all subsections of a parent are not necessarily the same here.
	 * 
	 * NEED TO REDUCE MOMENT FOR RUPTURES BELOW MIN MAG
	 * @param fltSystRupSet
	 * @return
	 */
	public static ArrayList<SectionMFD_constraint> getGR_InversionSectMFD_Constraints(InversionFaultSystemRupSet fltSystRupSet) {
		
//		System.out.println("Working on getGR_InversionSectMFD_Constraints(*)");

		double fractGR = 1.0;
		ArrayList<SectionMFD_constraint> mfdConstraintList = new ArrayList<SectionMFD_constraint>();
				
		
		for(int s=0;s <fltSystRupSet.getNumSections(); s++) {
			FaultSectionPrefData data = fltSystRupSet.getFaultSectionData(s);
			double minMag = fltSystRupSet.getFinalMinMagForSection(s);
			double maxMag = fltSystRupSet.getMaxMagForSection(s);
			double moRate = fltSystRupSet.getReducedMomentRate(s); 	// reduced for creep and subseismo ruptures
			double lowerEdgeOfFirstBin = SectionMFD_constraint.getLowerEdgeOfFirstBin(minMag);
				
			// check for too low maxMag
			if(maxMag>lowerEdgeOfFirstBin) {
				mfdConstraintList.add(new SectionMFD_constraint(minMag, maxMag, moRate, fractGR));					
			}
			else {
				mfdConstraintList.add(null);
				System.out.println("Null MFD Constraint for\t"+data.getSectionName()+
							"\tminMag="+(float)minMag+
							"\tmaxMag="+(float)maxMag+
							"\tlowerEdgeOfFirstBin="+(float)lowerEdgeOfFirstBin);
			}
		}
		
		// test to make sure there are no bins with zero ruptures
		int[] numCases = new int[10];
		for(int s=0;s <fltSystRupSet.getNumSections(); s++) {
			SectionMFD_constraint constr =mfdConstraintList.get(s);
			ArrayList<Integer> ithMags = new ArrayList<Integer>();
			for(int i=0;i<constr.getNumMags();i++)
				ithMags.add(i);
			for(int rupID : fltSystRupSet.getRupturesForSection(s)){
				int index = constr.getIndexForMag(fltSystRupSet.getMagForRup(rupID));
				if(ithMags.contains(index)) {
					ithMags.remove(new Integer(index));
				}
			}
			numCases[ithMags.size()] += 1;
			if(ithMags.size()>0) {
				String str = "\n"+fltSystRupSet.getFaultSectionData(s).getName()+" has zero rups at "+ithMags.size()+" mags: ";
				for(int iMag:ithMags)
					str += constr.getMag(iMag)+",  ";
				if(ithMags.size()>3) {
					str += "\n"+constr.toString() +"\nRupMags:";
					for(int rupID : fltSystRupSet.getRupturesForSection(s)){
						str += "\n\t"+fltSystRupSet.getMagForRup(rupID);
					}
				}
				System.out.print(str);
//				throw new RuntimeException(str);
			}
		}
		System.out.print("numCases = "+numCases);

		
		return mfdConstraintList;
	}
	
	
	
	
	/**
	 * This gets a list of SectionMFD_constraint objects (one for each sub-section in InversionFaultSystemRupSet).
	 * All subsections of a parent get an equivalent constraint to apply uniformity along strike.
	 * Note that some bins will still have zero rates on fault sections that were type A in UCERF2.
	 * This is null SectionMFD_constraint for any sections where the maximum magnitude falls below
	 * the lower edge of the constraint.
	 * @param fltSystRupSet
	 * @return
	 */
	public static ArrayList<SectionMFD_constraint> getCharInversionSectMFD_Constraints(InversionFaultSystemRupSet fltSystRupSet) {
		
		double fractGR = 0.33333;
		
		HashMap<Integer,Integer> numSectMap = new HashMap<Integer,Integer>();
		HashMap<Integer,Double> moRateMap = new HashMap<Integer,Double>();
		HashMap<Integer,Double> totAreaMap = new HashMap<Integer,Double>();
		HashMap<Integer,Double> totLengthMap = new HashMap<Integer,Double>();
//		HashMap<Integer,String> parName = new HashMap<Integer,String>();

		ArrayList<SectionMFD_constraint> mfdConstraintList = new ArrayList<SectionMFD_constraint>();
				
		InversionFaultSystemSolution UCERF2_FltSysSol =
				UCERF2_ComparisonSolutionFetcher.getUCERF2Solution(fltSystRupSet);
		

		// first compute momentRate and number of sub-sections for each parent section
		int lastParentIndex = - 1;
		int numSubSec=0;
		double totalMomentRate=0, totalArea=0, totalLength=0;		
		for(int s=0;s <fltSystRupSet.getNumSections(); s++) {
			
			// check that subsection names are the same
			String name1 = fltSystRupSet.getFaultSectionData(s).getSectionName();
			String name2 = UCERF2_FltSysSol.getRupSet().getFaultSectionData(s).getSectionName();
			if(!name1.equals(name2)) {
				throw new RuntimeException("Problem - names differ");
			}
			FaultSectionPrefData data = fltSystRupSet.getFaultSectionData(s);
			if(data.getParentSectionId() != lastParentIndex) {
				if(lastParentIndex != -1) {	// if it's not the first
					numSectMap.put(lastParentIndex, numSubSec);
					moRateMap.put(lastParentIndex, totalMomentRate);
					totAreaMap.put(lastParentIndex, totalArea);
					totLengthMap.put(lastParentIndex, totalLength);
				}
				// reset sums
				numSubSec=0;
				totalMomentRate=0;
				totalArea=0;
				totalLength=0;
			}
			numSubSec += 1;
			totalMomentRate += fltSystRupSet.getReducedMomentRate(s);	// reduced for both creep and subseismo ruptures
			double length = data.getTraceLength();
			totalArea += data.getReducedDownDipWidth()*length;	// km-sq
			totalLength += length;	// km
			lastParentIndex = data.getParentSectionId();
		}
		// add the last one
		numSectMap.put(lastParentIndex, numSubSec);
		moRateMap.put(lastParentIndex, totalMomentRate);
		totAreaMap.put(lastParentIndex, totalArea);
		totLengthMap.put(lastParentIndex, totalLength);
		
//		for(int parSectIndex : numSectMap.keySet())
//			System.out.println(numSectMap.get(parSectIndex)+"\t"+moRateMap.get(parSectIndex));
	
		// test of problem
//		SectionMFD_constraint test = new SectionMFD_constraint(UCERF2_FltSysSol, 76);
//		test.plotMFDs();
		
		ScalingRelationships scalingRel = fltSystRupSet.getLogicTreeBranch().getValue(ScalingRelationships.class);
		
		for(int s=0;s <fltSystRupSet.getNumSections(); s++) {
			FaultSectionPrefData data = fltSystRupSet.getFaultSectionData(s);
			
//			System.out.println(s+"\t"+data.getSectionName());
			
			double minMag = fltSystRupSet.getFinalMinMagForSection(s);
			
			double lowerEdgeOfFirstBin = SectionMFD_constraint.getLowerEdgeOfFirstBin(minMag);

			if(UCERF2_A_FaultMapper.wasUCERF2_TypeAFault(data.getParentSectionId())) {
				 mfdConstraintList.add(new SectionMFD_constraint(minMag, UCERF2_FltSysSol, s));
//				 mfdConstraintList.add(new SectionMFD_constraint(UCERF2_FltSysSol, s));
			}
			else {
				// compute max mag for rupture filling parent section area
				double area = totAreaMap.get(data.getParentSectionId());	// km-sq
				double length = totLengthMap.get(data.getParentSectionId());
				double width = area/length;	// km
				double maxMag = scalingRel.getMag(area*1e6, width*1e3);
				
				// check for too low maxMag
				if(maxMag>lowerEdgeOfFirstBin) {
					// each subsection gets the same moment rate to keep rates constant along the section
					// note that we could save memory by creating only one SectionMFD_constraint per parent section
					double moRate = moRateMap.get(data.getParentSectionId()) / (double)numSectMap.get(data.getParentSectionId());
					mfdConstraintList.add(new SectionMFD_constraint(minMag, maxMag, moRate, fractGR));					
				}
				else {
					mfdConstraintList.add(null);
					System.out.println("Null MFD Constraint for\t"+data.getSectionName()+
							"\tminMag="+(float)minMag+
							"\tmaxMag="+(float)maxMag+
							"\tlowerEdgeOfFirstBin="+(float)lowerEdgeOfFirstBin+
							"\tarea="+(float)area+
							"\tlength="+(float)length+
							"\twidth="+(float)width);
				}

				
				// temp test
//				if(maxMag<minMag-0.00001) {
//					System.out.println("maxMag<minMag for"+data.getParentSectionName()+"\tminMag="+minMag+"\tmaxMag="+maxMag);
//				}

				
			}
		}
		
		
		// test a-fault results (plot all subsect results for each parent section)
//		lastParentIndex = - 1;
//		String parSectName = null;
//		ArrayList<ArbitrarilyDiscretizedFunc> funcList = null;
//		for(int s=0;s <fltSystRupSet.getNumSections(); s++) {
//			FaultSectionPrefData data = fltSystRupSet.getFaultSectionData(s);
//			if(UCERF2_A_FaultMapper.wasUCERF2_TypeAFault(data.getParentSectionId())) {
//				if(data.getParentSectionId() != lastParentIndex) { // it's a new parent section, plot funcs and create new list
//					if(lastParentIndex != -1) { // plot result
//						GraphWindow graph = new GraphWindow(funcList, parSectName); 
//						graph.setX_AxisLabel("Magnitude");
//						graph.setY_AxisLabel("Rate");
//					}
//					funcList = new ArrayList<ArbitrarilyDiscretizedFunc>();
//					parSectName = data.getParentSectionName();
//				} 
//				funcList.add(mfdConstraintList.get(s).getMFD());
//				lastParentIndex = data.getParentSectionId();
//			}
//		}
//		GraphWindow graph = new GraphWindow(funcList, parSectName); 
//		graph.setX_AxisLabel("Magnitude");
//		graph.setY_AxisLabel("Rate");


		
		return mfdConstraintList;
	}
	
	
	/**
	 * This writes out the Parkfield rupture mags (plus other stuff) as well as 
	 * whether these ruptures fall below the section minimum magnitude.  The "Parkfield"
	 * ruptures are defined by the method: InversionInputGenerator.findParkfieldRups(rupSet)
	 */
	public static void writeParkfieldMags() {
		
		ArrayList<DeformationModels> defModList = new ArrayList<DeformationModels>();
		defModList.add(DeformationModels.ZENG);
		defModList.add(DeformationModels.GEOLOGIC);
		defModList.add(DeformationModels.NEOKINEMA);
		defModList.add(DeformationModels.ABM);
		
		ArrayList<ScalingRelationships> scaleRelList = new ArrayList<ScalingRelationships>();
		scaleRelList.add(ScalingRelationships.HANKS_BAKUN_08);
		scaleRelList.add(ScalingRelationships.ELLSWORTH_B);
		scaleRelList.add(ScalingRelationships.SHAW_2009_MOD);
		
		String info = "DefMod\tScRel\tRupID\tmagitude\tnumSect\t1stSect\tlastSect\n";
		
		String rupsBelowMinMag = "\nRups (indices) of those that fall below section min mag:\n";
		
		for(DeformationModels defMod:defModList) {
			for(ScalingRelationships scaleRel:scaleRelList) {
				
//				info += scaleRel.getName()+"\n";
				
				InversionFaultSystemRupSet rupSet = InversionFaultSystemRupSetFactory.forBranch(FaultModels.FM3_1, defMod, 
						InversionModels.CHAR_CONSTRAINED, scaleRel, SlipAlongRuptureModels.TAPERED, 
						TotalMag5Rate.RATE_7p9, MaxMagOffFault.MAG_7p6, MomentRateFixes.NONE, SpatialSeisPDF.UCERF3);
				
				List<Integer> parkfileRupIndexList = InversionInputGenerator.findParkfieldRups(rupSet);
				
				ArrayList<Integer> parkfileRupThatFallBelowMinMag = new ArrayList<Integer>();
				
				for(int index:parkfileRupIndexList) {
					List<Integer> sectIndicesList =rupSet.getSectionsIndicesForRup(index);
					info += defMod+"\t"+scaleRel+"\t";
					info += index+"\t"+(float)rupSet.getMagForRup(index)+"\t"+sectIndicesList.size()+"\t";
					info += rupSet.getFaultSectionData(sectIndicesList.get(0)).getSectionName()+"\t";
					info += rupSet.getFaultSectionData(sectIndicesList.get(sectIndicesList.size()-1)).getSectionName()+"\n";
					
					if(rupSet.isRuptureBelowSectMinMag(index)) {
						parkfileRupThatFallBelowMinMag.add(index);
					}
				}
				
				if(parkfileRupThatFallBelowMinMag.size()>0) {
					rupsBelowMinMag += scaleRel.getName()+"\n";
					for(int index:parkfileRupThatFallBelowMinMag)
						rupsBelowMinMag += "\t"+index+"\n";
				}
			}			
		}
		
		
		System.out.println("info:");
		System.out.println(info);
		System.out.println("rupsBelowMinMag:");
		System.out.println(rupsBelowMinMag);
		
	}
	
	
	
	/**
	 * This writes out the mag and average slip for the parkfield ruptures, looping over
	 * all scaling relationships.  This only used FM3.1 and ZENG deformation model.
	 */
	public static void writeParkfieldAveSlips() {
		
		ArrayList<ScalingRelationships> scaleRelList = new ArrayList<ScalingRelationships>();
		scaleRelList.add(ScalingRelationships.HANKS_BAKUN_08);
		scaleRelList.add(ScalingRelationships.ELLSWORTH_B);
		scaleRelList.add(ScalingRelationships.SHAW_2009_MOD);
		scaleRelList.add(ScalingRelationships.ELLB_SQRT_LENGTH);
		scaleRelList.add(ScalingRelationships.SHAW_CONST_STRESS_DROP);
		
		String info = "RupID\tMagitude\tAveSlip\tArea\tLength\n";
				
		for(ScalingRelationships scaleRel:scaleRelList) {
			
			info += scaleRel.getName()+"\n";
			
			InversionFaultSystemRupSet rupSet = InversionFaultSystemRupSetFactory.forBranch(FaultModels.FM3_1, DeformationModels.ZENG, 
					InversionModels.CHAR_CONSTRAINED, scaleRel, SlipAlongRuptureModels.TAPERED, 
					TotalMag5Rate.RATE_7p9, MaxMagOffFault.MAG_7p6, MomentRateFixes.NONE, SpatialSeisPDF.UCERF3);

			List<Integer> parkfileRupIndexList = InversionInputGenerator.findParkfieldRups(rupSet);
			
			for(int index:parkfileRupIndexList) {
				info += "\t"+index+"\t"+(float)rupSet.getMagForRup(index)+"\t"+(float)rupSet.getAveSlipForRup(index)+
				"\t"+(float)rupSet.getAreaForRup(index)+"\t"+(float)rupSet.getLengthForRup(index)+"\n";
			}
			
		}
		System.out.println(info);
	}

	
	
	
	public static void plotSumOfCharInversionMFD_Constraints(InversionFaultSystemRupSet fltSystRupSet) {
		double minMag = 5.05;
		int numMag = 40;
		double deltaMag =0.1;
		ArrayList<SectionMFD_constraint> constraints = getCharInversionSectMFD_Constraints(fltSystRupSet);
		SummedMagFreqDist summedMFD = new SummedMagFreqDist(minMag, numMag, deltaMag);
		for(SectionMFD_constraint mfdConstr : constraints) {
			if(mfdConstr != null)
				summedMFD.addIncrementalMagFreqDist(mfdConstr.getResampledToEventlyDiscrMFD(minMag, numMag, deltaMag));
		}
		summedMFD.setName("Sum of Char MFD Constraints");
		summedMFD.setInfo("Rate(M>=6.5)="+(float)summedMFD.getCumRate(6.55));

		// make range of target GRs
		GutenbergRichterMagFreqDist totalTargetGR = fltSystRupSet.getInversionTargetMFDs().getTotalTargetGR();
		totalTargetGR.setInfo("Rate(M>=6.5)="+(float)totalTargetGR.getCumRate(6.55));

		// make range of target GRs
//		SummedMagFreqDist subSeisAndOffFaultTarget = fltSystRupSet.getInversionMFDs().getTotalTargetSubSeismoOnPlusTrulyOffFaultMFD();
//		subSeisAndOffFaultTarget.setName("subSeisAndOffFaultTarget");
//		subSeisAndOffFaultTarget.setInfo("Rate(M>=6.5)="+(float)subSeisAndOffFaultTarget.getCumRate(6.55));
		
		SummedMagFreqDist targetOnFaultSupraSeisMFD = fltSystRupSet.getInversionTargetMFDs().getOnFaultSupraSeisMFD();
		targetOnFaultSupraSeisMFD.setInfo("Rate(M>=6.5)="+(float)targetOnFaultSupraSeisMFD.getCumRate(6.55));


		// make plot
		ArrayList<XY_DataSet> funcs = new ArrayList<XY_DataSet>();
		funcs.add(summedMFD);
		funcs.add(totalTargetGR);
		funcs.add(summedMFD.getCumRateDistWithOffset());
		funcs.add(totalTargetGR.getCumRateDistWithOffset());
		funcs.add(targetOnFaultSupraSeisMFD);
		funcs.add(targetOnFaultSupraSeisMFD.getCumRateDistWithOffset());
		ArrayList<PlotCurveCharacterstics> plotChars = new ArrayList<PlotCurveCharacterstics>();
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2, null, 0, Color.RED));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2, null, 0, Color.BLACK));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 2, null, 0, Color.RED));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 2, null, 0, Color.BLACK));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2, null, 0, Color.GRAY));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 2, null, 0, Color.GRAY));
		GraphWindow graph = new GraphWindow(funcs, "Sum of Char MFD Constraints",plotChars);
		graph.setX_AxisRange(5, 9);
		graph.setY_AxisRange(1e-6, 20);
		graph.setYLog(true);
		graph.setX_AxisLabel("Mag");
		graph.setY_AxisLabel("Rate (per year)");

		graph.setTickLabelFontSize(14);
		graph.setAxisLabelFontSize(16);
		graph.setPlotLabelFontSize(18);
		String fileName = "SumOfCharMFD_Constrints.pdf";
//		if(fileName != null) {
//			try {
//				graph.saveAsPDF(fileName);
//			} catch (IOException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}			
//		}			
	}
	
	
	/**
	 * This writes a plot of the average (over all logic tree branches) char MFD 
	 * constraint for each fault section, together with the mean from UCERF2 
	 * (if contained therein) and the UCERF2 mapped MFD.  This was done for visual verification
	 * that all look resonable.  This gives equal weight for each deformation model
	 * (not exactly correct).  Currently implemented for FM 3.1, and not sure how the average
	 * UCERF2 results are weighted (with respect the sections only in FM 2.1).
	 */
	public static void writeEachParentAveCharInversionMFD_Constraint() {
		
		File dir = new File("ParentAveCharMFD_ConstrPlots");
		if (!dir.exists())
			dir.mkdir();


		double minMag = 5.05;
		int numMag = 40;
		double deltaMag =0.1;
		
		ArrayList<DeformationModels> defModsList = new ArrayList<DeformationModels>();
		defModsList.add(DeformationModels.ABM);
		defModsList.add(DeformationModels.ZENG);
		defModsList.add(DeformationModels.GEOLOGIC);
		defModsList.add(DeformationModels.NEOKINEMA);
		
		ArrayList<ScalingRelationships> scalingRelList = new ArrayList<ScalingRelationships>();
		scalingRelList.add(ScalingRelationships.SHAW_2009_MOD);
		scalingRelList.add(ScalingRelationships.HANKS_BAKUN_08);
		scalingRelList.add(ScalingRelationships.ELLSWORTH_B);
		
		// create one and make the list of parent sections names
		InversionFaultSystemRupSet fltSystRupSet = InversionFaultSystemRupSetFactory.forBranch(FaultModels.FM3_1, DeformationModels.ZENG, 
				InversionModels.CHAR_CONSTRAINED, ScalingRelationships.SHAW_2009_MOD, SlipAlongRuptureModels.TAPERED, 
				TotalMag5Rate.RATE_7p9, MaxMagOffFault.MAG_7p6, MomentRateFixes.NONE, SpatialSeisPDF.UCERF3);
		HashMap<Integer,String> parNameFromID_Map = new HashMap<Integer,String>();
		for(FaultSectionPrefData subSectData : fltSystRupSet.getFaultSectionDataList())
			if(!parNameFromID_Map.keySet().contains(subSectData.getParentSectionId()))
				parNameFromID_Map.put(subSectData.getParentSectionId(),subSectData.getParentSectionName());
		
		HashMap<Integer, SummedMagFreqDist> aveParSectMFD_ConstraintMap = new HashMap<Integer, SummedMagFreqDist>();
		HashMap<Integer, Integer> numParSectMFD_ConstraintMap = new HashMap<Integer, Integer>();
		for(Integer parID: parNameFromID_Map.keySet()) {
			aveParSectMFD_ConstraintMap.put(parID, new SummedMagFreqDist(minMag, numMag, deltaMag));
			numParSectMFD_ConstraintMap.put(parID, new Integer(0));
		}		
		
		// loop over all branches
		int totNumBranches = 0;
		for(DeformationModels dm : defModsList) {
			for(ScalingRelationships sr : scalingRelList) {
				totNumBranches += 1;
				System.out.println("Working on "+dm+" & "+sr);
				fltSystRupSet = InversionFaultSystemRupSetFactory.forBranch(FaultModels.FM3_1, dm, 
						InversionModels.CHAR_CONSTRAINED, sr, SlipAlongRuptureModels.TAPERED, 
						TotalMag5Rate.RATE_7p9, MaxMagOffFault.MAG_7p6, MomentRateFixes.NONE, SpatialSeisPDF.UCERF3);
				
				HashMap<Integer, SummedMagFreqDist> sumOfSubSectMFD_ConstraintMap = new HashMap<Integer, SummedMagFreqDist>();
				for(Integer parID: parNameFromID_Map.keySet()) {
					sumOfSubSectMFD_ConstraintMap.put(parID, new SummedMagFreqDist(minMag, numMag, deltaMag));
				}		

				ArrayList<SectionMFD_constraint> constraints = getCharInversionSectMFD_Constraints(fltSystRupSet);
				for(int i=0; i<constraints.size(); i++) {
					Integer parID = fltSystRupSet.getFaultSectionData(i).getParentSectionId();
					SectionMFD_constraint constr =constraints.get(i);
					if(constr != null)
						sumOfSubSectMFD_ConstraintMap.get(parID).addIncrementalMagFreqDist(constr.getResampledToEventlyDiscrMFD(minMag, numMag, deltaMag));
				}
				
				// now add to total average
				for(Integer parID: sumOfSubSectMFD_ConstraintMap.keySet()) {
					SummedMagFreqDist parMFD = sumOfSubSectMFD_ConstraintMap.get(parID);
					if(parMFD.getTotalIncrRate()>1e-15) {	// make sure something was added (all were not null)
						aveParSectMFD_ConstraintMap.get(parID).addIncrementalMagFreqDist(parMFD);
						int newNum = numParSectMFD_ConstraintMap.get(parID) + 1;
						numParSectMFD_ConstraintMap.put(parID, newNum);
					}
				}				
			}
		}
		
		// now divide by number of branches (and write those with null branches)
		System.out.println("totNumBranches = "+totNumBranches);
		for(Integer parID: aveParSectMFD_ConstraintMap.keySet()) {
			int num = numParSectMFD_ConstraintMap.get(parID);
			SummedMagFreqDist mfd = aveParSectMFD_ConstraintMap.get(parID);
			mfd.scale(1.0/(double)num);
			mfd.setName(parNameFromID_Map.get(parID)+" ave MFD constraint");
			mfd.setInfo(" ");
			if(num<totNumBranches) {
				System.out.println(parID+" has null constraint for "+(totNumBranches-num)+" branches");
			}
		}

		// get mean UCERF2 MFD			************	WHAT ABOUT FM 2.1 VS 2.2?
		HashMap<Integer, IncrementalMagFreqDist> aveUCERF2_MFD_Map = new HashMap<Integer, IncrementalMagFreqDist>();
		for(Integer parID: parNameFromID_Map.keySet()) {
			ArrayList<IncrementalMagFreqDist> mfds = UCERF2_Section_MFDsCalc.getMeanMinAndMaxMFD(parID, false, false);
			if(mfds == null)
				aveUCERF2_MFD_Map.put(parID, null);
			else
				aveUCERF2_MFD_Map.put(parID, mfds.get(0));	// zeroeth element is mean MFD
		}

		// get mapped MFD - Note that the IDs are for UCERF2 parent section IDs (OR ARE THEY?)
		InversionFaultSystemSolution UCERF2_FltSysSol =
				UCERF2_ComparisonSolutionFetcher.getUCERF2Solution(fltSystRupSet.getFaultModel());
		HashMap<Integer, SummedMagFreqDist> mappedUCERF2_FSS_ParSectMFD_Map = new HashMap<Integer, SummedMagFreqDist>();
		for(int s=0; s<UCERF2_FltSysSol.getRupSet().getNumSections(); s++) {
			int parSectID = UCERF2_FltSysSol.getRupSet().getFaultSectionData(s).getParentSectionId();
			if(!mappedUCERF2_FSS_ParSectMFD_Map.keySet().contains(parSectID)) {
				mappedUCERF2_FSS_ParSectMFD_Map.put(parSectID, new SummedMagFreqDist(minMag, numMag, deltaMag));
			}
			IncrementalMagFreqDist nuclMFD = UCERF2_FltSysSol.calcNucleationMFD_forSect(s, minMag, minMag+deltaMag*(numMag-1), numMag);
			mappedUCERF2_FSS_ParSectMFD_Map.get(parSectID).addIncrementalMagFreqDist(nuclMFD);
		}
		
		// now make & save the plots
		HashMap<Integer, Integer> u3_to_U2_IDmapping = UCERF2_Section_MFDsCalc.getUCERF3toUCERF2ParentSectionIDMap();
		
		for(Integer parID: parNameFromID_Map.keySet()) {
			String parSectName = parNameFromID_Map.get(parID);
			
			ArrayList<DiscretizedFunc> funcs = Lists.newArrayList();
			ArrayList<PlotCurveCharacterstics> chars = Lists.newArrayList();
			
			funcs.add(aveParSectMFD_ConstraintMap.get(parID));
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLUE));

			IncrementalMagFreqDist aveUCERF2_MFD = aveUCERF2_MFD_Map.get(parID);
			if (aveUCERF2_MFD != null) {
				funcs.add(aveUCERF2_MFD);
				chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.RED));
			}

			SummedMagFreqDist mappedU2_MFD = null;
			if(mappedUCERF2_FSS_ParSectMFD_Map.keySet().contains(parID)) {	// parID exists in UCERF2
				mappedU2_MFD = mappedUCERF2_FSS_ParSectMFD_Map.get(parID);
			}
			else if(mappedUCERF2_FSS_ParSectMFD_Map.keySet().contains(u3_to_U2_IDmapping.get(parID))) {	// check if ID changed
				mappedU2_MFD = mappedUCERF2_FSS_ParSectMFD_Map.get(u3_to_U2_IDmapping.get(parID));
			}
			if (mappedU2_MFD != null) {
				funcs.add(mappedU2_MFD);
				chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLACK));
			}

			
			HeadlessGraphPanel gp = new HeadlessGraphPanel();
			gp.setYLog(true);
			gp.setRenderingOrder(DatasetRenderingOrder.FORWARD);
			gp.setUserBounds(5.0, 9.0, 1e-10, 1e-1);
			String fname = parSectName.replaceAll("\\W+", "_");
			String title = "Nucleation MFDs";
			String yAxisLabel = "Nucleation Rate";
			title += " for "+parSectName+" ("+parID+")";
			gp.drawGraphPanel("Magnitude", yAxisLabel, funcs, chars, title);
			File file = new File(dir, fname);
			gp.getChartPanel().setSize(1000, 800);
			try {
//				gp.saveAsPDF(file.getAbsolutePath()+".pdf");
				gp.saveAsPNG(file.getAbsolutePath()+".png");

			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
//			gp.saveAsTXT(file.getAbsolutePath()+".txt");
		}
	}
	
	
	
	/**
	 * This tests the variability of parent section nucleation MFD constraints over different
	 * UCERF3 logic tree branches (plotting each) for the "San Andreas (Mojave S)" parent section, 
	 * where the constraints are converted back to evently discretized MFDs. Results are pretty
	 * much identical, meaning variation in minimum magnitude among the branches is not significant.
	 */
	public static void testParentCharInversionMFD_Constraint() {
		
		String targetName = "San Andreas (Mojave S)";
		
		double minMag = 5.05;
		int numMag = 40;
		double deltaMag =0.1;
		
		ArrayList<DeformationModels> defModsList = new ArrayList<DeformationModels>();
		defModsList.add(DeformationModels.ABM);
		defModsList.add(DeformationModels.ZENG);
		defModsList.add(DeformationModels.GEOLOGIC);
		defModsList.add(DeformationModels.NEOKINEMA);
		
		ArrayList<ScalingRelationships> scalingRelList = new ArrayList<ScalingRelationships>();
		scalingRelList.add(ScalingRelationships.SHAW_2009_MOD);
		scalingRelList.add(ScalingRelationships.HANKS_BAKUN_08);
		scalingRelList.add(ScalingRelationships.ELLSWORTH_B);
		
		ArrayList<SummedMagFreqDist> mfdList = new ArrayList<SummedMagFreqDist>();
		ArrayList<String> parSectNames = new ArrayList<String>();
		
		// loop over all branches
		int totNumBranches = 0;
		for(DeformationModels dm : defModsList) {
			for(ScalingRelationships sr : scalingRelList) {
				totNumBranches += 1;
				System.out.println("Working on "+dm+" & "+sr);
				InversionFaultSystemRupSet fltSystRupSet = InversionFaultSystemRupSetFactory.forBranch(FaultModels.FM3_1, dm, 
						InversionModels.CHAR_CONSTRAINED, sr, SlipAlongRuptureModels.TAPERED, 
						TotalMag5Rate.RATE_7p9, MaxMagOffFault.MAG_7p6, MomentRateFixes.NONE, SpatialSeisPDF.UCERF3);
				SummedMagFreqDist mfd = new SummedMagFreqDist(minMag, numMag, deltaMag);
				ArrayList<SectionMFD_constraint> constraints = getCharInversionSectMFD_Constraints(fltSystRupSet);
				for(int i=0; i<constraints.size(); i++) {
					String parName = fltSystRupSet.getFaultSectionData(i).getParentSectionName();
					SectionMFD_constraint constr =constraints.get(i);
					if(parName.equals(targetName) && constr != null) {
						mfd.addIncrementalMagFreqDist(constr.getResampledToEventlyDiscrMFD(minMag, numMag, deltaMag));
					}
				}
				mfdList.add(mfd);
			}
		}

//		// get mapped MFD - Note that the IDs are for UCERF2 parent section IDs
//		SimpleFaultSystemSolution UCERF2_FltSysSol = UCERF2_ComparisonSolutionFetcher.getUCERF2Solution(fltSystRupSet.getFaultModel());
//		HashMap<Integer, SummedMagFreqDist> mappedUCERF2_FSS_ParSectMFD_Map = new HashMap<Integer, SummedMagFreqDist>();
//		for(int s=0; s<UCERF2_FltSysSol.getNumSections(); s++) {
//			int parSectID = UCERF2_FltSysSol.getFaultSectionData(s).getParentSectionId();
//			if(!mappedUCERF2_FSS_ParSectMFD_Map.keySet().contains(parSectID)) {
//				mappedUCERF2_FSS_ParSectMFD_Map.put(parSectID, new SummedMagFreqDist(minMag, numMag, deltaMag));
//			}
//			IncrementalMagFreqDist nuclMFD = UCERF2_FltSysSol.calcNucleationMFD_forSect(s, minMag, minMag+deltaMag*(numMag-1), numMag);
//			mappedUCERF2_FSS_ParSectMFD_Map.get(parSectID).addIncrementalMagFreqDist(nuclMFD);
//		}
		
		GraphWindow graph = new GraphWindow(mfdList, "Char MFD Constraint Test");
		graph.setX_AxisRange(5, 9);
		graph.setY_AxisRange(1e-6, 1e-2);
		graph.setYLog(true);
		graph.setX_AxisLabel("Mag");
		graph.setY_AxisLabel("Rate (per year)");

		graph.setTickLabelFontSize(14);
		graph.setAxisLabelFontSize(16);
		graph.setPlotLabelFontSize(18);

	}

	
	
	
	
	/**
	 * This plots the average over def mods and M(A) relationships
	 */
	public static void plotSumOfCharInversionMFD_Constraints() {
		double minMag = 5.05;
		int numMag = 40;
		double deltaMag =0.1;
		
		ArrayList<DeformationModels> defModsList = new ArrayList<DeformationModels>();
		defModsList.add(DeformationModels.ABM);
		defModsList.add(DeformationModels.ZENGBB);
		defModsList.add(DeformationModels.GEOLOGIC);
		defModsList.add(DeformationModels.NEOKINEMA);
		
		ArrayList<ScalingRelationships> scalingRelList = new ArrayList<ScalingRelationships>();
		scalingRelList.add(ScalingRelationships.SHAW_2009_MOD);
		scalingRelList.add(ScalingRelationships.HANKS_BAKUN_08);
		scalingRelList.add(ScalingRelationships.ELLSWORTH_B);
		
		SummedMagFreqDist summedCharMFDs = new SummedMagFreqDist(minMag, numMag, deltaMag);
		SummedMagFreqDist totalTargetGR = new SummedMagFreqDist(minMag, numMag, deltaMag);
		SummedMagFreqDist targetOnFaultSupraSeisMFD = new SummedMagFreqDist(minMag, numMag, deltaMag);
		SummedMagFreqDist subSeisAndOffFaultTarget = new SummedMagFreqDist(minMag, numMag, deltaMag);

		InversionFaultSystemRupSet fltSystRupSet;
		
		int numBranches=0;
		for(DeformationModels dm : defModsList) {
			for(ScalingRelationships sr : scalingRelList) {
				System.out.println("Working on "+dm+" & "+sr);
				numBranches +=1;
				fltSystRupSet = InversionFaultSystemRupSetFactory.forBranch(FaultModels.FM3_1, dm, 
						InversionModels.CHAR_CONSTRAINED, sr, SlipAlongRuptureModels.TAPERED, 
						TotalMag5Rate.RATE_7p9, MaxMagOffFault.MAG_7p6, MomentRateFixes.NONE, SpatialSeisPDF.UCERF3);
				// sum all the char MFD constraints
				ArrayList<SectionMFD_constraint> constraints = getCharInversionSectMFD_Constraints(fltSystRupSet);
				for(SectionMFD_constraint mfdConstr : constraints) {
					if(mfdConstr != null) {
						ArbIncrementalMagFreqDist resampMFD = mfdConstr.getResampledToEventlyDiscrMFD(minMag, numMag, deltaMag);
						if(!Double.isNaN(resampMFD.getTotalIncrRate()))
							summedCharMFDs.addIncrementalMagFreqDist(resampMFD);
						else
							System.out.println("Bad MFD");
					}
				}
				// now get targets
				totalTargetGR.addResampledMagFreqDist(fltSystRupSet.getInversionTargetMFDs().getTotalTargetGR(), true);
				targetOnFaultSupraSeisMFD.addResampledMagFreqDist(fltSystRupSet.getInversionTargetMFDs().getOnFaultSupraSeisMFD(), true);
				subSeisAndOffFaultTarget.addResampledMagFreqDist(fltSystRupSet.getInversionTargetMFDs().getTotalGriddedSeisMFD(), true);
			}
		}

		summedCharMFDs.scale(1.0/(double)numBranches);
		summedCharMFDs.setName("Sum of Char MFD Constraints");
		summedCharMFDs.setInfo("Rate(M>=6.5)="+(float)summedCharMFDs.getCumRate(6.55));

		totalTargetGR.scale(1.0/(double)numBranches);
		totalTargetGR.setName("totalTargetGR");
		totalTargetGR.setInfo("Rate(M>=6.5)="+(float)totalTargetGR.getCumRate(6.55));

		targetOnFaultSupraSeisMFD.scale(1.0/(double)numBranches);
		targetOnFaultSupraSeisMFD.setName("targetOnFaultSupraSeisMFD");
		targetOnFaultSupraSeisMFD.setInfo("Rate(M>=6.5)="+(float)targetOnFaultSupraSeisMFD.getCumRate(6.55));

		subSeisAndOffFaultTarget.scale(1.0/(double)numBranches);
		subSeisAndOffFaultTarget.setName("subSeisAndOffFaultTarget");
		subSeisAndOffFaultTarget.setInfo("Rate(M>=6.5)="+(float)subSeisAndOffFaultTarget.getCumRate(6.55));
		
		SummedMagFreqDist totalImpliedMFD = new SummedMagFreqDist(minMag, numMag, deltaMag);
		totalImpliedMFD.addIncrementalMagFreqDist(summedCharMFDs);
		totalImpliedMFD.addIncrementalMagFreqDist(subSeisAndOffFaultTarget);
		totalImpliedMFD.setName("totalImpliedMFD");
		totalImpliedMFD.setInfo("Rate(M>=6.5)="+(float)totalImpliedMFD.getCumRate(6.55));
		
		// test of target:
		System.out.println("Total target rate test: "+totalTargetGR.getTotalIncrRate());


		// make plot
		ArrayList<XY_DataSet> funcs = new ArrayList<XY_DataSet>();
		funcs.add(summedCharMFDs);
		funcs.add(totalTargetGR);
		funcs.add(targetOnFaultSupraSeisMFD);
		funcs.add(subSeisAndOffFaultTarget);
		funcs.add(totalImpliedMFD);
		funcs.add(totalTargetGR.getCumRateDistWithOffset());
		funcs.add(totalImpliedMFD.getCumRateDistWithOffset());

		ArrayList<PlotCurveCharacterstics> plotChars = new ArrayList<PlotCurveCharacterstics>();
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2, null, 0, Color.RED));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2, null, 0, Color.BLACK));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2, null, 0, Color.CYAN));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2, null, 0, Color.GRAY));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2, null, 0, Color.ORANGE));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 4, null, 0, Color.BLACK));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 4, null, 0, Color.ORANGE));
		GraphWindow graph = new GraphWindow(funcs, "Sum of Char MFD Constraints",plotChars);
		graph.setX_AxisRange(5, 9);
		graph.setY_AxisRange(1e-6, 20);
		graph.setYLog(true);
		graph.setX_AxisLabel("Mag");
		graph.setY_AxisLabel("Rate (per year)");

		graph.setTickLabelFontSize(14);
		graph.setAxisLabelFontSize(16);
		graph.setPlotLabelFontSize(18);
		String fileName = "SumOfAllBranchCharMFD_Constrints.pdf";
		if(fileName != null) {
			try {
				graph.saveAsPDF(fileName);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}			
		}			
	}

	
	
	
	
	public static void plotSumOfGR_InversionMFD_Constraints(InversionFaultSystemRupSet fltSystRupSet) {
		

		System.out.println("Working on plotSumOfGR_InversionMFD_Constraints(*)");
		double minMag = 5.05;
		int numMag = 40;
		double deltaMag =0.1;
		ArrayList<SectionMFD_constraint> constraints = getGR_InversionSectMFD_Constraints(fltSystRupSet);
		SummedMagFreqDist summedMFD = new SummedMagFreqDist(minMag, numMag, deltaMag);
		for(SectionMFD_constraint mfdConstr : constraints) {
			if(mfdConstr != null)
				summedMFD.addIncrementalMagFreqDist(mfdConstr.getResampledToEventlyDiscrMFD(minMag, numMag, deltaMag));
		}
		summedMFD.setName("Sum of GR MFD Constraints");
		summedMFD.setInfo("Rate(M>=6.5)="+(float)summedMFD.getCumRate(6.55));

		// add target GR
		GutenbergRichterMagFreqDist totalTargetGR = fltSystRupSet.getInversionTargetMFDs().getTotalTargetGR();
		totalTargetGR.setInfo("Rate(M>=6.5)="+(float)totalTargetGR.getCumRate(6.55));

		// add what's returned by calcImpliedGR_NucleationMFD(*)
		SummedMagFreqDist altGR = calcImpliedGR_NucleationMFD(fltSystRupSet, minMag, numMag, deltaMag);
		altGR.setName("calcImpliedGR_NucleationMFD(*)");
		altGR.setInfo("Rate(M>=6.5)="+(float)altGR.getCumRate(6.55));

		// make plot
		ArrayList<XY_DataSet> funcs = new ArrayList<XY_DataSet>();
		funcs.add(summedMFD);
		funcs.add(summedMFD.getCumRateDistWithOffset());
		funcs.add(totalTargetGR);
		funcs.add(totalTargetGR.getCumRateDistWithOffset());
		funcs.add(altGR);
		funcs.add(altGR.getCumRateDistWithOffset());
		ArrayList<PlotCurveCharacterstics> plotChars = new ArrayList<PlotCurveCharacterstics>();
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2, null, 0, Color.RED));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 2, null, 0, Color.RED));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2, null, 0, Color.BLACK));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 2, null, 0, Color.BLACK));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2, null, 0, Color.BLUE));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 2, null, 0, Color.BLUE));
		GraphWindow graph = new GraphWindow(funcs, "Sum of GR MFD Constraints",plotChars);
		graph.setX_AxisRange(5, 9);
		graph.setY_AxisRange(1e-6, 20);
		graph.setYLog(true);
		graph.setX_AxisLabel("Mag");
		graph.setY_AxisLabel("Rate (per year)");

		graph.setTickLabelFontSize(14);
		graph.setAxisLabelFontSize(16);
		graph.setPlotLabelFontSize(18);
		String fileName = "SumOfGR_MFD_Constrints.pdf";
//		if(fileName != null) {
//			try {
//				graph.saveAsPDF(fileName);
//			} catch (IOException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}			
//		}			
	}


	/**
	 * @param args
	 */
	public static void main(String[] args) {
		
		plotAllImpliedTotalSectGR_MFD();
		
		plotSumOfCharInversionMFD_Constraints();

		
//		testParentCharInversionMFD_Constraint();
		
//		writeEachParentAveCharInversionMFD_Constraint();
		
//		writeParkfieldMags();
		
//		plotOffFaultTaperedGR_Comparisons(rupSet, "GR_MaxAndTaperComparison");

//		plotAllImpliedTotalSectGR_MFD();
	
//		writeParkfieldAveSlips();
		
//		writeParkfieldMags();
		
//		InversionFaultSystemRupSet rupSet = InversionFaultSystemRupSetFactory.forBranch(FaultModels.FM2_1, DeformationModels.UCERF2_ALL, 
//				InversionModels.CHAR_CONSTRAINED, ScalingRelationships.AVE_UCERF2, SlipAlongRuptureModels.TAPERED, 
//				TotalMag5Rate.RATE_7p6, MaxMagOffFault.MAG_7p6, MomentRateFixes.NONE, SpatialSeisPDF.UCERF2);

//		InversionFaultSystemRupSet rupSet = InversionFaultSystemRupSetFactory.forBranch(FaultModels.FM3_1, DeformationModels.ZENG, 
//				InversionModels.CHAR_CONSTRAINED, ScalingRelationships.ELLSWORTH_B, SlipAlongRuptureModels.TAPERED, 
//				TotalMag5Rate.RATE_8p7, MaxMagOffFault.MAG_7p6, MomentRateFixes.NONE, SpatialSeisPDF.UCERF3);
		
//		HistogramFunction magHist = getMagHistogram(rupSet, 0.05, 90, 0.1);
//		System.out.println();
//		GraphWindow graph = new GraphWindow(magHist, "Mag Hist");

		
//		plotPreInversionMFDs(rupSet, false, false, true, null);
		
//		plotSumOfGR_InversionMFD_Constraints(rupSet);
		
//		ArrayList<SectionMFD_constraint> constraints = getCharInversionSectMFD_Constraints(rupSet);
		
//		SummedMagFreqDist mfd = new SummedMagFreqDist(0.05, 100, 0.1);
//		for(SectionMFD_constraint constr:getCharInversionSectMFD_Constraints(rupSet))
//			mfd.addIncrementalMagFreqDist(constr.getResampledToEventlyDiscrMFD(0.05, 100, 0.1));
//		GraphWindow graph = new GraphWindow(mfd, "Test MFD"); 
//		graph.setX_AxisLabel("Mangitude");
//		graph.setY_AxisLabel("Rate (per year)");
//		graph.setYLog(true);

//		computeMinSeismoMagForSections(rupSet, InversionFaultSystemRupSet.MIN_MAG_FOR_SEISMOGENIC_RUPS);
		
//		tempTest2(rupSet);;
		
		
//		tempSubsectionAreaTest(rupSet);
		
//		tempTest(rupSet);
		
//		writeZeroMagBinsForEachParentSection(rupSet, 0.05, 90, 0.1);
//		writeZeroMagBinsForEachParentSection(rupSet, 0.1, 45, 0.2);

//		plotPreInversionMFDs(rupSet, false, false, true, "preInvCharMFDs.pdf");

		
		
//		String PATH_TO_FILE_BELOW = "/Users/field/workspace/OpenSHA/dev/scratch/UCERF3/data/scratch/FM3_1_NEOK_EllB_DsrUni_GRConst_M5Rate8.7_MMaxOff7.6_NoFix_SpatSeisU3_run5_sol.zip";
//		SimpleFaultSystemSolution tmp = null;
//		try {
//			File f = new File(PATH_TO_FILE_BELOW);
//			System.out.println(f.exists());
//			tmp = SimpleFaultSystemSolution.fromFile(f);
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
//		InversionFaultSystemSolution invFss = new InversionFaultSystemSolution(tmp);
//		List<GutenbergRichterMagFreqDist> list = invFss.getImpliedSubSeisGR_MFD_List();
//		System.out.println(list.size());

		
		
		
		
		
//		plotAllImpliedTotalSectGR_MFD();
//		
//		testImplGR_fracSeisOnFltAssumingSameCC();
//		testAllInversionSetups();
		
//		InversionFaultSystemRupSet rupSet = InversionFaultSystemRupSetFactory.forBranch(FaultModels.FM3_1, DeformationModels.NEOKINEMA, 
//				InversionModels.GR_CONSTRAINED, ScalingRelationships.ELLSWORTH_B, SlipAlongRuptureModels.TAPERED, 
//				TotalMag5Rate.RATE_8p7, MaxMagOffFault.MAG_7p6, MomentRateFixes.NONE, SpatialSeisPDF.UCERF3);

//		System.out.println(rupSet.getPreInversionAnalysisData(true));
//		plotPreInversionMFDs(rupSet, false, false, false, "preInvGR_MFDs_Tapered.pdf");
//		plotOffFaultTaperedGR_Comparisons(rupSet, "GR_MaxAndTaperComparison");
		
//		InversionFaultSystemRupSet rupSet = InversionFaultSystemRupSetFactory.forBranch(FaultModels.FM2_1, DeformationModels.UCERF2_ALL, 
//				InversionModels.GR_UNCONSTRAINED, ScalingRelationships.AVE_UCERF2, SlipAlongRuptureModels.TAPERED, 
//				TotalMag5Rate.RATE_8p7, MaxMagOffFault.MAG_7p6, MomentRateFixes.NONE, SpatialSeisPDF.UCERF3);
//		System.out.println(rupSet.getLogicTreeBranch().getTabSepValStringHeader());
//		System.out.println(rupSet.getLogicTreeBranch().getTabSepValString());
		
		
//		try {
//		SimpleFaultSystemSolution sol = SimpleFaultSystemSolution.fromFile(
//				new File("/home/kevin/workspace/OpenSHA/dev/scratch/UCERF3/data/scratch/InversionSolutions/" +
//						"FM3_1_NEOK_EllB_DsrTap_CharConst_M5Rate8.7_MMaxOff7.6_NoFix_SpatSeisU3_sol.zip"));
//		
//
//		double totalMultiplyNamedM7Rate = FaultSystemRupSetCalc.calcTotRateMultiplyNamedFaults(sol, 7d, false);
//		double totalMultiplyNamedPaleoVisibleRate = FaultSystemRupSetCalc.calcTotRateMultiplyNamedFaults(sol, 0d, true);
//		
//		double totalM7Rate = FaultSystemRupSetCalc.calcTotRateAboveMag(sol, 7d, false);
//		double totalPaleoVisibleRate = FaultSystemRupSetCalc.calcTotRateAboveMag(sol, 0d, true);
//		
//		System.out.println("Total rupture rate (M7+): "+totalM7Rate);
//		System.out.println("Total multiply named rupture rate (M7+): "+totalMultiplyNamedM7Rate);
//		System.out.println("% of M7+ rate that are multiply named: "
//				+(100d * totalMultiplyNamedM7Rate / totalM7Rate)+" %");
//		System.out.println("Total paleo visible rupture rate: "+totalPaleoVisibleRate);
//		System.out.println("Total multiply named paleo visible rupture rate: "+totalMultiplyNamedPaleoVisibleRate);
//		System.out.println("% of paleo visible rate that are multiply named: "
//				+(100d * totalMultiplyNamedPaleoVisibleRate / totalPaleoVisibleRate)+" %");
//		System.exit(0);
//	} catch (IOException e) {
//		// TODO Auto-generated catch block
//		e.printStackTrace();
//	} catch (DocumentException e) {
//		// TODO Auto-generated catch block
//		e.printStackTrace();
//	}


//		double mMaxInRegion = 8.45;
//		double totRegionalM5_Rate = 8.7;
//		double totOnFaultMgt5_Rate = totRegionalM5_Rate*0.6;
//		double mMaxOffFault = 7.95;
//		double mMinSeismoOnFault = 6.45;
//		GutenbergRichterMagFreqDist totGR = new GutenbergRichterMagFreqDist(0.05, 90, 0.1, 0.05, mMaxInRegion, 1.0, 1.0);
//		totGR.scaleToCumRate(0, totRegionalM5_Rate*1e5);
////		old_getCharOnFaultTargetMFD(totGR, totOnFaultMgt5_Rate, totGR.getXIndex(mMaxOffFault));
//		old_getCharOnFaultTargetMFD(1E18, totGR, totOnFaultMgt5_Rate);
//		getTriLinearCharOffFaultTargetMFD(totGR, totOnFaultMgt5_Rate, mMinSeismoOnFault, mMaxOffFault);
//		getTriLinearCharOffFaultTargetMFD(4E18, totGR, totOnFaultMgt5_Rate,mMinSeismoOnFault);
		
//		testAllImpliedDDWs();
		
//		LaughTestFilter laughTest = LaughTestFilter.getDefault();
//		double defaultAseismicityValue = InversionFaultSystemRupSetFactory.DEFAULT_ASEIS_VALUE;
//
//		InversionFaultSystemRupSet faultSysRupSet = InversionFaultSystemRupSetFactory.forBranch(laughTest, defaultAseismicityValue,
//				FaultModels.FM3_1, DeformationModels.GEOLOGIC, MagAreaRelationships.SHAW_09_MOD,
//				AveSlipForRupModels.SHAW_12_CONST_STRESS_DROP, SlipAlongRuptureModels.TAPERED,
//				InversionModels.CHAR_CONSTRAINED, TotalMag5Rate.RATE_8p8, MaxMagOffFault.MAG_7p65,
//				ApplyImpliedCouplingCoeff.FALSE, SpatialSeisPDF.UCERF3);
//
//		System.out.println(calcImplDDWvsDDW_Ratio(faultSysRupSet));
		
//		plotPreInversionMFDs(faultSysRupSet);
//		
//		System.out.println("getFractSpatialPDF_InsideSectionPolygons(faultSysRupSet, SpatialSeisPDF.UCERF3)="+getFractSpatialPDF_InsideSectionPolygons(faultSysRupSet, SpatialSeisPDF.UCERF3));
//		System.out.println("getFractSpatialPDF_InsideSectionPolygons(faultSysRupSet, SpatialSeisPDF.UCERF2)="+getFractSpatialPDF_InsideSectionPolygons(faultSysRupSet, SpatialSeisPDF.UCERF2));
		
		
//		getCharSubSeismoOnFaultMFD(faultSysRupSet, SpatialSeisPDF.UCERF3, totGR);

//
//		System.out.println("getMeanMinMag="+getMeanMinMag(faultSysRupSet, true));
//		System.out.println("getMeanMinMag="+getMeanMinMag(faultSysRupSet, false));

//		FaultSystemRupSet faultSysRupSet = InversionFaultSystemRupSetFactory.forBranch(FaultModels.FM3_1, DeformationModels.GEOLOGIC, 
//					MagAreaRelationships.ELL_B, AveSlipForRupModels.ELLSWORTH_B, SlipAlongRuptureModels.TAPERED, InversionModels.GR);
//		HistogramFunction hist1 = getMinMagHistogram(faultSysRupSet, 5.05, 40, 0.1, false);
//		HistogramFunction hist2 = getMinMagHistogram(faultSysRupSet, 5.05, 40, 0.1, true);
//		ArrayList<HistogramFunction> hists = new ArrayList<HistogramFunction>();
//		hists.add(hist1.getCumulativeDistFunction());
//		hists.add(hist2.getCumulativeDistFunction());
//		GraphWindow graph = new GraphWindow(hists, "Histograms"); 
//		graph.setX_AxisLabel("Magnitude");
//		graph.setY_AxisLabel("Normalized Number");


		
		

		
		
//		FaultSystemRupSet faultSysRupSet = InversionFaultSystemRupSetFactory.forBranch(FaultModels.FM3_1, DeformationModels.GEOLOGIC, 
//				MagAreaRelationships.HB_08, AveSlipForRupModels.ELLSWORTH_B, SlipAlongRuptureModels.TAPERED, InversionModels.GR);
//		getMomentRateReductionHistogram(faultSysRupSet, true, true);
//		plotAllHistograms(faultSysRupSet, 5.05,40,0.1, true);

		
//		getFractMomentReductionForSmallMags(6.0, 8.0, 1.0);
//		getFractMomentReductionForSmallMags(7.0, 8.0, 1.0);
		
//   		try {
//  			System.out.println("Getting rup set");
//			FaultSystemRupSet faultSysRupSet = InversionFaultSystemRupSetFactory.cachedForBranch(
//					FaultModels.FM2_1, DeformationModels.UCERF2_ALL, InversionModels.GR, true);
			
			
//			System.out.println(getMinMagHistogram(faultSysRupSet, 5.05,40,0.1, true).getCumulativeDistFunction());

//   		} catch (IOException e) {
//			e.printStackTrace();
//		}

	}

}
