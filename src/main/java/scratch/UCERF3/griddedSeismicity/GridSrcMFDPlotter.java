package scratch.UCERF3.griddedSeismicity;

import java.awt.Color;
import java.io.File;
import java.util.ArrayList;

import javax.swing.SwingUtilities;

import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.region.CaliforniaRegions;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.GraphWindow;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import scratch.UCERF3.inversion.InversionFaultSystemSolution;
import scratch.UCERF3.utils.FaultSystemIO;

import com.google.common.base.CaseFormat;
import com.google.common.collect.Lists;

/**
 * Add comments here
 *
 * 
 * @author Peter Powers
 * @version $Id:$
 */
public class GridSrcMFDPlotter {
	
	private static InversionFaultSystemSolution fss;
	private static ArrayList<PlotCurveCharacterstics> plotChars;
	private static SmallMagScaling magScaling = SmallMagScaling.SPATIAL;
	private static boolean incremental = false;
//	private static String fName = "tmp/invSols/reference_ch_sol2.zip";
//	private static String fName = "tmp/invSols/ucerf2/FM2_1_UC2ALL_MaAvU2_DsrTap_DrAveU2_Char_VarAPrioriZero_VarAPrioriWt100_mean_sol.zip";
	private static String fName = "tmp/invSols/ucerf2/FM2_1_UC2ALL_MaAvU2_DsrTap_DrAveU2_Char_VarAPrioriZero_VarAPrioriWt1000_mean_sol.zip";
	
	static {
		
		// init fss
		try {
			File f = new File(fName);
			fss = FaultSystemIO.loadInvSol(f);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		// init plot characteristics
		plotChars = Lists.newArrayList(
			new PlotCurveCharacterstics(PlotLineType.SOLID,2f, Color.BLACK),
			new PlotCurveCharacterstics(PlotLineType.SOLID,2f, Color.MAGENTA.darker()),
			new PlotCurveCharacterstics(PlotLineType.SOLID,2f, Color.BLUE.darker()),
			new PlotCurveCharacterstics(PlotLineType.SOLID,2f, Color.ORANGE.darker()),
			new PlotCurveCharacterstics(PlotLineType.SOLID,2f, Color.RED.brighter()),
			new PlotCurveCharacterstics(PlotLineType.SOLID,2f, Color.GREEN.darker()),
			new PlotCurveCharacterstics(PlotLineType.SOLID,2f, Color.CYAN.darker()),
			new PlotCurveCharacterstics(PlotLineType.SOLID,2f, Color.YELLOW.darker()));
	}
	
	
//	Map<String, FaultSectionPrefData> sectionMap;
	GridSrcMFDPlotter() {
		if (fName.contains("_ch_") || fName.contains("_Char_")) plotChar();
		if (fName.contains("_gr_")) plotGR();
		
//		plotFault(1617);
	}
	
	void plotFault(int idx) {

		UCERF3_GridSourceGenerator gridGen = new UCERF3_GridSourceGenerator(fss);
		System.out.println("init done");
		
		ArrayList<EvenlyDiscretizedFunc> funcs = Lists.newArrayList();

		String name = fss.getRupSet().getFaultSectionData(idx).getName();
		
		
		IncrementalMagFreqDist fSubSeisIncr = gridGen.getSectSubSeisMFD(idx);
		fSubSeisIncr.setName(name + " SubSeis MFD");
		EvenlyDiscretizedFunc fSubSeisCum = fSubSeisIncr.getCumRateDist();
		
		IncrementalMagFreqDist fSupSeisIncr = fss.calcNucleationMFD_forSect(idx,
			fSubSeisIncr.getMinX(), fSubSeisIncr.getMaxX(),fSubSeisIncr.size());
		fSupSeisIncr.setName(name + " SupraSeis MFD");
		EvenlyDiscretizedFunc fSupSeisCum = fSupSeisIncr.getCumRateDist();
		
		IncrementalMagFreqDist fTotalIncr = fSubSeisIncr.deepClone();
		fTotalIncr.setName(name + "Total MFD");
		addDistro(fTotalIncr, fSupSeisIncr);
		EvenlyDiscretizedFunc fTotalCum = fTotalIncr.getCumRateDist();
		
		funcs.add(incremental ? fSupSeisIncr : fSupSeisCum);
		funcs.add(incremental ? fSubSeisIncr : fSubSeisCum);
		funcs.add(incremental ? fTotalIncr : fTotalCum);

		GraphWindow plotter = new GraphWindow(funcs,
				(fName.contains("_gr_") ? "GR" : "CH") + " : " +
				CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL,
					magScaling.toString()) + " : " +
				(incremental ? "Incr" : "Cum") + " : " + name, plotChars);
		plotter.setX_AxisRange(fTotalIncr.getMinX(), fTotalIncr.getMaxX());
		plotter.setY_AxisRange(1e-10, 1e-4);
		plotter.setYLog(true);
		

		// for (FaultSectionPrefData fault : fss.getFaultSectionDataList()) {
		// System.out.println(fault.getSectionId() + " : " +
		// fault.getSectionName());
		// }

	}
	void plotChar() {

		UCERF3_GridSourceGenerator gridGen = new UCERF3_GridSourceGenerator(fss);
		System.out.println("init done");
		
		ArrayList<EvenlyDiscretizedFunc> funcs = Lists.newArrayList();
		
		// Total on-fault
//		IncrementalMagFreqDist tOnIncr = fss.calcTotalNucleationMFD(5.05, 8.45, 0.1);
		IncrementalMagFreqDist tOnIncr = fss.calcNucleationMFD_forRegion(new CaliforniaRegions.RELM_TESTING(), 5.05, 8.45, 0.1, true);
		tOnIncr.setName("Total on-fault MFD from inversion");
		EvenlyDiscretizedFunc tOnCum = tOnIncr.getCumRateDist();
		funcs.add(incremental ? tOnIncr : tOnCum);
		
		// Total off fault
		IncrementalMagFreqDist tOffIncr = fss.getFinalTotalGriddedSeisMFD();
		EvenlyDiscretizedFunc tOffCum = tOffIncr.getCumRateDist();
		funcs.add(incremental ? tOffIncr : tOffCum);
		
		// Total on + off fault
		IncrementalMagFreqDist tOnOffIncr = tOnIncr.deepClone();
		addDistro(tOnOffIncr, tOffIncr);
		tOnOffIncr.setName("Total on + off");
		EvenlyDiscretizedFunc tOnOffCum = tOnOffIncr.getCumRateDist();
		funcs.add(incremental ? tOnOffIncr : tOnOffCum);
		
		// Total sub seismogenic (section sum)
		IncrementalMagFreqDist tssSectIncr = gridGen.getSectSubSeisMFD();
		EvenlyDiscretizedFunc tssSectCum = tssSectIncr.getCumRateDist();
		funcs.add(incremental ? tssSectIncr : tssSectCum);
		
		// Total sub seismogenic (node sum)
//		IncrementalMagFreqDist tssNodeIncr = gridGen.getNodeSubSeisMFD();
//		EvenlyDiscretizedFunc tssNodeCum = tssNodeIncr.getCumRateDist();
//		funcs.add(incremental ? tssNodeIncr : tssNodeCum);
		
		// the two above should be equal
		
		// Total true off-fault (unassociated)
		IncrementalMagFreqDist trueOffIncr = gridGen.getNodeUnassociatedMFD();
		EvenlyDiscretizedFunc trueOffCum = trueOffIncr.getCumRateDist();
		funcs.add(incremental ? trueOffIncr : trueOffCum);
		
		// unassoc sum + section sum
		IncrementalMagFreqDist tssIncr = tssSectIncr.deepClone();
		addDistro(tssIncr, trueOffIncr);
		tssIncr.setName("Summed sub-seismogenic MFDs (sect + unnassociated)");
		EvenlyDiscretizedFunc tssCum = tssIncr.getCumRateDist();
		funcs.add(incremental ? tssIncr : tssCum);

		
//		// test scaling total off fault by total MoReduc
//		if (magScaling.equals(SmallMagScaling.MO_REDUCTION)) {
//			IncrementalMagFreqDist testScale = tofIncr.deepClone();
//			testScale.setName("MFD scaled to total mo reduction");
//			testScale.zeroAtAndAboveMag(6.6);
//			double reduction = fss.getTotalSubseismogenicMomentRateReduction();
//			testScale.scaleToTotalMomentRate(reduction);
//			funcs.add(testScale);
//		}
		
		
		GraphWindow plotter = new GraphWindow(funcs,
			"UCERF2wt1000 : " +
					(fName.contains("_gr_") ? "GR" : "CH") + " : " +
					CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL,
						magScaling.toString()) + " : " +
					(incremental ? "Incr" : "Cum"), plotChars);
		plotter.setX_AxisRange(tssSectIncr.getMinX(), tssSectIncr.getMaxX());
		plotter.setY_AxisRange(1e-6, 1e1);
		plotter.setYLog(true);
		
	}
	
	private static void addDistro(EvenlyDiscretizedFunc f1, EvenlyDiscretizedFunc f2) {
		for (int i=0; i<f1.size(); i++) {
			f1.set(i, f1.getY(i) + f2.getY(i));
		}
	}
	
	void plotGR() {

		UCERF3_GridSourceGenerator gridGen = new UCERF3_GridSourceGenerator(fss);
		System.out.println("init done");
		
		ArrayList<EvenlyDiscretizedFunc> funcs = Lists.newArrayList();
		
		// Total on-fault
		IncrementalMagFreqDist tOnIncr = fss.calcTotalNucleationMFD(5.05, 8.45, 0.1);
		tOnIncr.setName("Total on-fault MFD from inversion");
		EvenlyDiscretizedFunc tOnCum = tOnIncr.getCumRateDist();
		funcs.add(incremental ? tOnIncr : tOnCum);
		
		// Total off fault
		IncrementalMagFreqDist tOffIncr = fss.getFinalTotalGriddedSeisMFD();
		EvenlyDiscretizedFunc tOffCum = tOffIncr.getCumRateDist();
		funcs.add(incremental ? tOffIncr : tOffCum);
		
//		// Total on + off fault
//		IncrementalMagFreqDist tOnOffIncr = tOnIncr.deepClone();
//		addDistro(tOnOffIncr, tOffIncr);
//		tOnOffIncr.setName("Total on + off");
//		EvenlyDiscretizedFunc tOnOffCum = tOnOffIncr.getCumRateDist();
//		funcs.add(incremental ? tOnOffIncr : tOnOffCum);
		
		// Total sub seismogenic (section sum)
		IncrementalMagFreqDist tssSectIncr = gridGen.getSectSubSeisMFD();
		EvenlyDiscretizedFunc tssSectCum = tssSectIncr.getCumRateDist();
		funcs.add(incremental ? tssSectIncr : tssSectCum);
		
		// Total sub seismogenic (node sum)
//		IncrementalMagFreqDist tssNodeIncr = gridGen.getNodeSubSeisMFD();
//		EvenlyDiscretizedFunc tssNodeCum = tssNodeIncr.getCumRateDist();
//		funcs.add(incremental ? tssNodeIncr : tssNodeCum);
		
		// the two above should be equal
		
		// Total true off-fault (unassociated)
		IncrementalMagFreqDist trueOffIncr = gridGen.getNodeUnassociatedMFD();
		EvenlyDiscretizedFunc trueOffCum = trueOffIncr.getCumRateDist();
		funcs.add(incremental ? trueOffIncr : trueOffCum);
		
//		// unassoc sum + section sum
//		IncrementalMagFreqDist unSectIncr = tssSectIncr.deepClone();
//		addDistro(unSectIncr, trueOffIncr);
//		unSectIncr.setName("Summed sub-seismogenic MFDs (sect + unnassociated)");
//		EvenlyDiscretizedFunc unSectCum = unSectIncr.getCumRateDist();
//		funcs.add(incremental ? unSectIncr : unSectCum);

		// onFault + subSeis + unassociated
		IncrementalMagFreqDist tssRegionIncr = tssSectIncr.deepClone();
		addDistro(tssRegionIncr, tOnIncr);
		addDistro(tssRegionIncr, trueOffIncr);
		tssRegionIncr.setName("Total on-fault + subSeis + unassociated");
		EvenlyDiscretizedFunc tssRegionCum = tssRegionIncr.getCumRateDist();
		funcs.add(incremental ? tssRegionIncr : tssRegionCum);
		
		
		GraphWindow plotter = new GraphWindow(funcs,
			(fName.contains("_gr_") ? "GR" : "CH") +
				" : " +
				CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL,
					magScaling.toString()) + " : " +
				(incremental ? "Incr" : "Cum"), plotChars);
		plotter.setX_AxisRange(tssSectIncr.getMinX(), tssSectIncr.getMaxX());
		plotter.setY_AxisRange(1e-6, 3e1);
		plotter.setYLog(true);
		
	}
	
	public static void main(String[] args) {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				new GridSrcMFDPlotter();
			}
		});
	}
	
}
