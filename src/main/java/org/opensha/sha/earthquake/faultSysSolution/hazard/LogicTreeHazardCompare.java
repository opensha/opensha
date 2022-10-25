package org.opensha.sha.earthquake.faultSysSolution.hazard;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.imageio.ImageIO;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.apache.commons.math3.stat.descriptive.moment.Variance;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.ChartRenderingInfo;
import org.jfree.chart.title.PaintScaleLegend;
import org.jfree.chart.ui.RectangleEdge;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.function.LightFixedXFunc;
import org.opensha.commons.data.xyz.GriddedGeoDataSet;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.Region;
import org.opensha.commons.geo.json.Feature;
import org.opensha.commons.gui.plot.GraphPanel;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotPreferences;
import org.opensha.commons.gui.plot.PlotUtils;
import org.opensha.commons.gui.plot.jfreechart.xyzPlot.XYZPlotSpec;
import org.opensha.commons.gui.plot.pdf.PDF_UTF8_FontMapper;
import org.opensha.commons.logicTree.BranchWeightProvider;
import org.opensha.commons.logicTree.LogicTree;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.util.DataUtils;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.Interpolate;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.hazard.mpj.MPJ_LogicTreeHazardCalc;
import org.opensha.sha.earthquake.faultSysSolution.modules.SolutionLogicTree;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RupSetMapMaker;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSysTools;
import org.opensha.sha.earthquake.faultSysSolution.util.SolHazardMapCalc;
import org.opensha.sha.earthquake.faultSysSolution.util.SolHazardMapCalc.MapPlot;
import org.opensha.sha.earthquake.faultSysSolution.util.SolHazardMapCalc.ReturnPeriods;

import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.primitives.Doubles;
import com.itextpdf.awt.FontMapper;
import com.itextpdf.awt.PdfGraphics2D;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfTemplate;
import com.itextpdf.text.pdf.PdfWriter;

public class LogicTreeHazardCompare {
	
	private static boolean TITLES = false;
	
	public static void main(String[] args) throws IOException {
		System.setProperty("java.awt.headless", "true");
		File invDir = new File("/home/kevin/OpenSHA/UCERF4/batch_inversions");
		
		boolean currentWeights = false;
		boolean compCurrentWeights = false;
		
//		File mainDir = new File(invDir, "2021_11_24-nshm23_draft_branches-FM3_1");
//		String mainName = "NSHM23 Draft";
//		LogicTreeNode[] subsetNodes = null;
//		File compDir = new File(invDir, "2021_11_23-u3_branches-FM3_1-5h");
//		String compName = "UCERF3 Redo";
//		LogicTreeNode[] compSubsetNodes = null;
//		File outputDir = new File(mainDir, "hazard_maps_vs_ucerf3_redo");
////		File compDir = new File(invDir, "2021_11_30-u3_branches-orig_calcs-5h");
////		String compName = "UCERF3 As Published";
////		LogicTreeNode[] compSubsetNodes = null;
////		File outputDir = new File(mainDir, "hazard_maps_vs_ucerf3_as_published");

////		File mainDir = new File(invDir, "2022_02_15-nshm23_u3_hybrid_branches-FM3_1-CoulombRupSet-DsrUni-TotNuclRate-SubB1-JumpProb-2000ip");
////		String mainName = "Draft-Model-Coulomb-Shaw-Seg-Jump-Prob";
//		File mainDir = new File(invDir, "2022_02_22-nshm23_u3_hybrid_branches-FM3_1-CoulombRupSet-DsrUni-TotNuclRate-SubB1-StrictEquivJumpProb-2000ip");
//		String mainName = "Seg-Strict-Equiv-Jump-Prob";
////		File mainDir = new File(invDir, "2022_02_15-nshm23_u3_hybrid_branches-FM3_1-CoulombRupSet-DsrUni-TotNuclRate-SubB1-CappedRdst-2000ip");
////		String mainName = "Draft-Model-Coulomb-Shaw-Seg-Capped-Redist";
////		String mainName = "Max-Dist-Seg";
////		File mainDir = new File(invDir, "2022_02_08-nshm23_u3_hybrid_branches-seg_bin_dist_capped_distr-FM3_1-CoulombRupSet-DsrUni-SubB1-2000ip");
////		String mainName = "Draft-Model-Coulomb-Shaw-Seg";
//		LogicTreeNode[] subsetNodes = null;
////		LogicTreeNode[] subsetNodes = { SubSectConstraintModels.TOT_NUCL_RATE };
////		File compDir = null;
////		String compName = null;
////		File outputDir = new File(mainDir, "hazard_maps");
////		File compDir = new File(invDir, "2022_02_10-nshm23_u3_hybrid_branches-seg-no_adj-FM3_1-CoulombRupSet-DsrUni-TotNuclRate-SubB1-2000ip");
////		String compName = "No-Seg";
////		File outputDir = new File(mainDir, "hazard_maps_comp_no_seg");
//////		File compDir = new File(invDir, "2022_02_10-nshm23_u3_hybrid_branches-seg-capped_self_contained-FM3_1-CoulombRupSet-DsrUni-TotNuclRate-SubB1-2000ip");
////		File compDir = new File(invDir, "2022_02_10-nshm23_u3_hybrid_branches-seg-greedy_self_contained-FM3_1-CoulombRupSet-DsrUni-TotNuclRate-SubB1-2000ip");
////		String compName = "Self-Contained";
////		File outputDir = new File(mainDir, "hazard_maps_comp_self_contained");
//		File compDir = new File(invDir, "2022_02_11-nshm23_u3_hybrid_branches-max_dist-FM3_1-CoulombRupSet-DsrUni-TotNuclRate-SubB1-2000ip");
//		String compName = "Strict-Cutoff-Seg";
//		File outputDir = new File(mainDir, "hazard_maps_comp_strict_cutoff");
////		File compDir = new File(invDir, "2022_02_15-nshm23_u3_hybrid_branches-FM3_1-CoulombRupSet-DsrUni-TotNuclRate-SubB1-CappedRdst-2000ip");
////		String compName = "Seg-Capped-Redist";
////		File outputDir = new File(mainDir, "hazard_maps_comp_capped_redist");
////		File compDir = new File(invDir, "2022_02_08-nshm23_u3_hybrid_branches-seg_bin_dist_capped_distr-FM3_1-CoulombRupSet-DsrUni-SubB1-2000ip");
////		String compName = "Capped-Distribution-Seg";
////		File outputDir = new File(mainDir, "hazard_maps_comp_capped_distr_tot_rate");
////		File compDir = new File(invDir, "2022_01_28-nshm23_u3_hybrid_branches-FM3_1-CoulombRupSet-DsrUni-SubB1-5000ip");
////		String compName = "5000 Iters/Rup";
////		File outputDir = new File(mainDir, "hazard_maps_comp_5000ip");
//		LogicTreeNode[] compSubsetNodes = null;
////		LogicTreeNode[] compSubsetNodes = { SubSectConstraintModels.TOT_NUCL_RATE };
		
		/*
		 * Sweep of draft models, late Feb 2022
		 */
//		File mainDir = new File(invDir, "2022_02_16-u3_branches-orig_calc_params-FM3_1");
//		String mainName = "UCERF3 2022 Reproduction";
		
//		File mainDir = new File(invDir, "2022_02_17-u3_branches-orig_calc_params-new_avg-converged-FM3_1-2000ip");
//		String mainName = "UCERF3 New-Avg Converged";
		
//		File mainDir = new File(invDir, "2022_02_20-u3_branches-orig_calc_params-new_avg-converged-noWL-FM3_1-2000ip");
//		String mainName = "UCERF3 New-Avg Converged No-WL";
		
//		File mainDir = new File(invDir, "2022_02_20-u3_branches-orig_calc_params-new_avg-converged-new_perturb-FM3_1-2000ip");
//		String mainName = "UCERF3 New-Avg Converged New-Perturb";
		
//		File mainDir = new File(invDir, "2022_02_22-u3_branches-orig_calc_params-new_avg-converged-noWL-new_perturb-FM3_1-2000ip");
//		String mainName = "UCERF3 New-Avg Converged No-WL New-Perturb";
		
//		File mainDir = new File(invDir, "2022_02_21-u3_branches-orig_calc_params-new_avg-converged-noWL-new_perturb-try_zero_often-FM3_1-2000ip");
//		String mainName = "UCERF3 New-Avg Converged New-Perturb No-WL Try-Zero";
		
//		File mainDir = new File(invDir, "2022_02_22-u3_branches-FM3_1-2000ip");
//		String mainName = "UCERF3 New-Anneal Params";
		
//		File mainDir = new File(invDir, "2022_02_22-u3_branches-thinned_0.1-FM3_1-2000ip");
//		String mainName = "UCERF3 New-Anneal Params, Reduced RS";
		
//		File mainDir = new File(invDir, "2022_03_25-u3_branches-coulomb-FM3_1-2000ip");
//		String mainName = "UCERF3 New-Anneal Params, Coulomb RS";
		
//		File mainDir = new File(invDir, "2022_02_23-nshm23_u3_hybrid_branches-no_seg-FM3_1-U3RupSet-DsrUni-TotNuclRate-SubB1-2000ip");
//		String mainName = "NSHM23 Draft, U3 RS, No Seg";
		
//		File mainDir = new File(invDir, "2022_02_23-nshm23_u3_hybrid_branches-no_seg-FM3_1-U3RedRupSet-DsrUni-TotNuclRate-SubB1-2000ip");
//		String mainName = "NSHM23 Draft, U3 Reduced RS, No Seg";
		
//		File mainDir = new File(invDir, "2022_02_23-nshm23_u3_hybrid_branches-no_seg-FM3_1-CoulombRupSet-DsrUni-TotNuclRate-SubB1-2000ip");
//		String mainName = "NSHM23 Draft, Coulomb RS, No Seg";
		
//		File mainDir = new File(invDir, "2022_02_27-nshm23_u3_hybrid_branches-strict_cutoff_seg-FM3_1-CoulombRupSet-DsrUni-TotNuclRate-SubB1-2000ip");
//		String mainName = "NSHM23 Draft, Coulomb RS, Strict Seg";
//		currentWeights = true;
		
//		File mainDir = new File(invDir, "2022_02_27-nshm23_u3_hybrid_branches-strict_cutoff_seg-FM3_1-CoulombRupSet-DsrUni-TotNuclRate-SubB1-2000ip-branch-translated");
//		String mainName = "NSHM23 Draft, Coulomb RS, Strict Seg Translated";
		
//		File mainDir = new File(invDir, "2022_02_27-nshm23_u3_hybrid_branches-strict_cutoff_seg-FM3_1-CoulombRupSet-DsrUni-TotNuclRate-SubB1-2000ip-branch-translated-min3km");
//		String mainName = "NSHM23 Draft, Coulomb RS, Strict Seg Translated >=3km";
		
//		File mainDir = new File(invDir, "2022_02_23-nshm23_u3_hybrid_branches-FM3_1-CoulombRupSet-DsrUni-TotNuclRate-SubB1-NoAdj-2000ip");
//		String mainName = "NSHM23 Draft, Coulomb RS, Seg No-Adj";
		
//		File mainDir = new File(invDir, "2022_02_15-nshm23_u3_hybrid_branches-FM3_1-CoulombRupSet-DsrUni-TotNuclRate-SubB1-JumpProb-2000ip");
//		String mainName = "NSHM23 Draft, Coulomb RS, Seg Thresh-Avg";
		
//		File mainDir = new File(invDir, "2022_02_22-nshm23_u3_hybrid_branches-FM3_1-CoulombRupSet-DsrUni-TotNuclRate-SubB1-StrictEquivJumpProb-2000ip");
//		String mainName = "NSHM23 Draft, Coulomb RS, Seg Thresh-Avg @ Strict Bins";
		
//		File mainDir = new File(invDir, "2022_05_09-nshm23_u3_hybrid_branches-shift_seg_1km-FM3_1-CoulombRupSet-DsrUni-TotNuclRate-SubB1-ThreshAvg");
//		String mainName = "NSHM23 Draft, Coulomb RS, Seg Thresh-Avg 1km-Shift";
		
//		File mainDir = new File(invDir, "2022_02_15-nshm23_u3_hybrid_branches-FM3_1-CoulombRupSet-DsrUni-TotNuclRate-SubB1-CappedRdst-2000ip");
//		String mainName = "NSHM23 Draft, Coulomb RS, Seg Capped-Redist";
		
//		File mainDir = new File(invDir, "2022_03_03-nshm23_u3_hybrid_branches-shift_seg_1km-FM3_1-CoulombRupSet-DsrUni-NuclMFD-SubB1-JumpProb-2000ip");
//		String mainName = "NSHM23 Draft, Coulomb RS, Seg Thresh-Avg 1km-Shift, Nucl-MFD";
		
//		File compDir = new File(invDir, "2022_03_24-nshm23_u3_hybrid_branches-shift_seg_1km-FM3_1-CoulombRupSet-DsrUni-TotNuclRate-SubB1-JumpProb-2000ip");
//		String compName = "Previous Step";
//		File outputDir = new File(mainDir, "hazard_maps_comp_prev");

//		File mainDir = new File(invDir, "2022_04_27-u3_branches-scaleLowerDepth1.3-FM3_1-2000ip");
//		String mainName = "UCERF3, 1.3x Lower-Seis Depth";
		
//		File mainDir = new File(invDir, "2022_05_09-nshm23_u3_hybrid_branches-cluster_specific_inversion-shift_seg_1km-FM3_1-CoulombRupSet-DsrUni-TotNuclRate-SubB1-ThreshAvg");
//		String mainName = "NSHM23 Draft, Cluster-Specific Inversions";
		
//		File mainDir = new File(invDir, "2022_05_20-nshm23_u3_hybrid_branches-scaleLowerDepth1.3-shift_seg_1km-FM3_1-CoulombRupSet-DsrUni-TotNuclRate-SubB1-ThreshAvg");
//		String mainName = "NSHM23 Draft, 1.3x Lower-Seis Depth";
		
//		File mainDir = new File(invDir, "2022_05_20-nshm23_u3_hybrid_branches-test_scale_rels-scaleLowerDepth1.3-shift_seg_1km-FM3_1-CoulombRupSet-DsrUni-TotNuclRate-SubB1-ThreshAvg");
//		String mainName = "NSHM23 Draft, Test Scale Rels, 1.3x Lower-Seis Depth";
		
//		File mainDir = new File(invDir, "2022_05_20-nshm23_u3_hybrid_branches-test_scale_rels-shift_seg_1km-FM3_1-CoulombRupSet-DsrUni-TotNuclRate-SubB1-ThreshAvg");
//		String mainName = "NSHM23 Draft, Test Scale Rels";
		
////		File mainDir = new File(invDir, "2022_07_23-nshm23_branches-NSHM23_v1p4-CoulombRupSet-DsrUni-TotNuclRate-SubB1-Shift2km-ThreshAvgIterRelGR-IncludeThruCreep");
//		File mainDir = new File(invDir, "2022_08_12-nshm23_branches-NSHM23_v2-CoulombRupSet-NSHM23_Avg-TotNuclRate-SubB1-ThreshAvgIterRelGR");
////		File mainDir = new File(invDir, "2022_08_08-nshm23_branches-NSHM23_v2-CoulombRupSet-TotNuclRate-SubB1-ThreshAvgIterRelGR");
//		String mainName = "NSHM23 Draft";
		
//		File mainDir = new File(invDir, "2022_08_09-nshm23_u3_hybrid_branches-FM3_1-CoulombRupSet-DsrUni-TotNuclRate-SubB1-ThreshAvgIterRelGR");
//		String mainName = "NSHM23/U3 Draft";
		
//		File mainDir = new File(invDir, "2022_08_09-nshm23_u3_hybrid_branches-wide_seg_branches-FM3_1-CoulombRupSet-DsrUni-TotNuclRate-SubB1-ThreshAvgIterRelGR");
//		String mainName = "NSHM23/U3 Draft, Wide-Seg";
		
//		File mainDir = new File(invDir, "2022_08_05-nshm23_branches-NSHM23_v2-CoulombRupSet-NSHM23_Avg-TotNuclRate-SubB1-ThreshAvg");
//		String mainName = "Thresh-Avg";
		
//		File mainDir = new File(invDir, "2022_07_29-nshm23_branches-NSHM23_v1p4-CoulombRupSet-DsrUni-TotNuclRate-SubB1-NoAdj");
//		String mainName = "No-Adj";
		
//		File mainDir = new File(invDir, "2022_08_11-nshm23_branches-wide_seg_branches-NSHM23_v2-CoulombRupSet-NSHM23_Avg-TotNuclRate-SubB1-ThreshAvgIterRelGR");
//		String mainName = "Wide-Seg-Branches";
		
//		File mainDir = new File(invDir, "2022_08_12-nshm23_branches-paleo_uncerts-NSHM23_v2-CoulombRupSet-NSHM23_Avg-TotNuclRate-SubB1-ThreshAvgIterRelGR");
//		String mainName = "Paleo-Branches";
		
//		File mainDir = new File(invDir, "2022_08_11-u3_branches-new_seg-FM3_1");
//		String mainName = "U3-Plus-Seg";
		
//		File mainDir = new File(invDir, "2022_08_22-nshm18_branches-NSHM18_WUS_NoCA-CoulombRupSet-TotNuclRate-NoRed-ThreshAvgIterRelGR");
//		String mainName = "NSHM23/18 Draft";
		
//		File mainDir = new File(invDir, "2022_08_18-nshm23_u3_hybrid_branches-FM3_1-CoulombRupSet-DsrUni-TotNuclRate-NoRed-ThreshAvgIterRelGR");
//		String mainName = "NSHM23/U3 Draft";
		
//		File mainDir = new File(invDir, "2022_08_17-nshm23_branches-NSHM23_v2-CoulombRupSet-TotNuclRate-SubB1-ThreshAvgIterRelGR-360_samples");
//		String mainName = "NSHM23 Draft";
		
		File mainDir = new File(invDir, "2022_08_22-nshm23_branches-NSHM23_v2-CoulombRupSet-TotNuclRate-NoRed-ThreshAvgIterRelGR");
		String mainName = "NSHM23 Draft";
		
//		File mainDir = new File(invDir, "2022_08_15-nshm23_branches-paleo_uncerts-NSHM23_v2-CoulombRupSet-AVERAGE-TotNuclRate-SubB1-SupraB0.5-MidSeg-ThreshAvgIterRelGR");
//		String mainName = "NSHM23 Draft, Mini-Scaling-Sweep";
		
		LogicTreeNode[] subsetNodes = null;
		LogicTreeNode[] compSubsetNodes = null;
//		LogicTreeNode[] compSubsetNodes = { FaultModels.FM3_1 };
		
//		File compDir = null;
//		String compName = null;
//		File outputDir = new File(mainDir, "hazard_maps");
//		File compDir = new File(invDir, "2022_07_23-nshm23_branches-no_seg-NSHM23_v1p4-CoulombRupSet-DsrUni-TotNuclRate-SubB1-IncludeThruCreep");
//		String compName = "No Segmentation";
//		File outputDir = new File(mainDir, "hazard_maps_comp_no_seg");
//		File compDir = new File(invDir, "2022_08_05-nshm23_branches-NSHM23_v2-CoulombRupSet-NSHM23_Avg-TotNuclRate-SubB1-ThreshAvg");
//		String compName = "No Segmentation";
//		File outputDir = new File(mainDir, "hazard_maps_comp_thresh_avg");
//		File compDir = new File(invDir, "2022_08_10-nshm23_branches-no_ghost_trans-NSHM23_v2-CoulombRupSet-NSHM23_Avg-TotNuclRate-SubB1-ThreshAvgIterRelGR");
//		String compName = "No Segmentation";
//		File outputDir = new File(mainDir, "hazard_maps_comp_no_ghost_trans");
//		File compDir = new File(invDir, "2022_08_09-nshm23_u3_hybrid_branches-FM3_1-CoulombRupSet-DsrUni-TotNuclRate-SubB1-ThreshAvgIterRelGR");
//		String compName = "NSHM23/U3 Draft";
//		File outputDir = new File(mainDir, "hazard_maps_comp_nshm23_draft");
//		File compDir = new File(invDir, "2022_07_29-nshm23_branches-NSHM23_v1p4-CoulombRupSet-DsrUni-TotNuclRate-SubB1-ThreshAvgIterRelGR");
//		String compName = "NSHM23 Draft";
//		File outputDir = new File(mainDir, "hazard_maps_comp_nshm23_draft");
		File compDir = new File(invDir, "2022_08_22-nshm23_branches-NSHM23_v2-CoulombRupSet-TotNuclRate-NoRed-ThreshAvgIterRelGR");
		String compName = "Draft Subset";
		File outputDir = new File(mainDir, "hazard_maps_comp_draft_subset");
//		File compDir = new File(invDir, "2021_11_30-u3_branches-orig_calcs-5h");
//		String compName = "UCERF3 As Published";
//		File outputDir = new File(mainDir, "hazard_maps_comp_ucerf3_as_published");
//		String compName = "UCERF3 As Published, Uniform";
//		File outputDir = new File(mainDir, "hazard_maps_comp_ucerf3_as_published_uniform");
//		LogicTreeNode[] compSubsetNodes = { FaultModels.FM3_1, SlipAlongRuptureModels.UNIFORM };
//		File compDir = new File(invDir, "2022_03_24-u3_branches-FM3_1-2000ip");
//		String compName = "UCERF3 New Anneal";
//		File outputDir = new File(mainDir, "hazard_maps_comp_ucerf3_new_anneal");
//		File compDir = new File(invDir, "2022_03_13-u3_branches-coulomb-bilateral-FM3_1-2000ip");
//		String compName = "UCERF3 Coulomb Bilateral";
//		File outputDir = new File(mainDir, "hazard_maps_comp_ucerf3_coulomb_bilateral");
//		String compName = "UCERF3 New Anneal, Uniform";
//		File outputDir = new File(mainDir, "hazard_maps_comp_ucerf3_new_anneal_uniform");
//		LogicTreeNode[] compSubsetNodes = { FaultModels.FM3_1, SlipAlongRuptureModels.UNIFORM };
//		File compDir = new File(invDir, "2022_02_27-nshm23_u3_hybrid_branches-strict_cutoff_seg-FM3_1-CoulombRupSet-DsrUni-TotNuclRate-SubB1-2000ip");
//		String compName = "Strict Segmentation";
//		File outputDir = new File(mainDir, "hazard_maps_comp_strict_seg");
//		File compDir = new File(invDir, "");
//		String compName = "Threshold Avg Shift-1km";
//		String compName = "NSHM23 Draft";
//		File outputDir = new File(mainDir, "hazard_maps_comp_thresh_avg_shift_1km");
//		File compDir = new File(invDir, "2022_05_09-nshm23_u3_hybrid_branches-shift_seg_1km-FM3_1-CoulombRupSet-DsrUni-TotNuclRate-SubB1-ThreshAvg");
//		String compName = "NSHM23 Draft, Test Scale Rels";
//		File outputDir = new File(mainDir, "hazard_maps_comp_test_scale_rels");
//		File compDir = new File(invDir, "2022_05_20-nshm23_u3_hybrid_branches-test_scale_rels-shift_seg_1km-FM3_1-CoulombRupSet-DsrUni-TotNuclRate-SubB1-ThreshAvg");
//		String compName = "UCERF3 Full Segmented";
//		File outputDir = new File(mainDir, "hazard_maps_comp_fully_segmented");
		
		/**
		 * NSHM18 ingredients
		 */
//		File mainDir = new File(invDir, "2022_04_08-nshm18_branches-shift_seg_1km-NSHM18_WUS_NoCA-CoulombRupSet-DsrUni-TotNuclRate-NoRed-JumpProb-10000ip");
//		String mainName = "NSHM18 Ingred, Coulomb RS, Seg Thresh-Avg 1km-Shift";
////		File mainDir = new File(invDir, "2022_04_08-nshm18_branches-no_seg-NSHM18_WUS_NoCA-AzimuthalRupSet-DsrUni-TotNuclRate-NoRed-10000ip");
////		String mainName = "NSHM18 Ingred, Azimuthal RS, No Seg";
////		File mainDir = new File(invDir, "2022_04_08-nshm18_branches-no_seg-NSHM18_WUS_NoCA-FullSegRupSet-DsrUni-TotNuclRate-NoRed-10000ip");
////		String mainName = "NSHM18 Ingred, Full Segmented RS";
//		
//		LogicTreeNode[] subsetNodes = null;
//		File compDir = new File(invDir, "2022_04_08-nshm18_branches-no_seg-NSHM18_WUS_NoCA-FullSegRupSet-DsrUni-TotNuclRate-NoRed-10000ip");
//		String compName = "Fully Segmented RS";
//		File outputDir = new File(mainDir, "hazard_maps_comp_full_segmented");
////		File compDir = new File(invDir, "2022_04_08-nshm18_branches-shift_seg_1km-NSHM18_WUS_NoCA-CoulombRupSet-DsrUni-TotNuclRate-NoRed-JumpProb-10000ip");
////		String compName = "Coulomb RS w/ Seg";
////		File outputDir = new File(mainDir, "hazard_maps_comp_coulomb");
//		LogicTreeNode[] compSubsetNodes = null;
		
		File resultsFile, hazardFile;
		File compResultsFile, compHazardFile;
		
		if (args.length > 0) {
			// assume CLI instead
			Preconditions.checkArgument(args.length == 4 || args.length == 7,
					"USAGE: <primary-results-zip> <primary-hazard-zip> <primary-name> [<comparison-results-zip> "
					+ "<comparison-hazard-zip> <comparison-name>] <output-dir>");
			int cnt = 0;
			resultsFile = new File(args[cnt++]);
			hazardFile = new File(args[cnt++]);
			mainName = args[cnt++];
			if (args.length > 4) {
				if (args[cnt].toLowerCase().trim().equals("null")) {
					compResultsFile = null;
					cnt++;
				} else {
					compResultsFile = new File(args[cnt++]);
				}
				compHazardFile = new File(args[cnt++]);
				compName = args[cnt++];
			} else {
				compResultsFile = null;
				compHazardFile = null;
				compName = null;
			}
			outputDir = new File(args[cnt++]);
			
			subsetNodes = null;
			compSubsetNodes = null;
			currentWeights = false;
			compCurrentWeights = false;
		} else {
			resultsFile = new File(mainDir, "results.zip");
			hazardFile = new File(mainDir, "results_hazard.zip");
			if (compDir == null) {
				compResultsFile = null;
				compHazardFile = null;
			} else {
				compResultsFile = new File(compDir, "results.zip");
				compHazardFile = new File(compDir, "results_hazard.zip");
			}
		}
		
		SolutionLogicTree solTree = SolutionLogicTree.load(resultsFile);
		
		ReturnPeriods[] rps = ReturnPeriods.values();
		double[] periods = { 0d, 1d };
		double spacing = -1; // detect
//		double spacing = 0.1;
//		double spacing = 0.25;
		
		LogicTree<?> tree = solTree.getLogicTree();
		if (subsetNodes != null)
			tree = tree.matchingAll(subsetNodes);
		if (currentWeights)
			tree.setWeightProvider(new BranchWeightProvider.CurrentWeights());
		
		LogicTreeHazardCompare mapper = null;
		LogicTreeHazardCompare comp = null;
		int exit = 0;
		try {
			mapper = new LogicTreeHazardCompare(solTree, tree,
					hazardFile, rps, periods, spacing);
			
			if (compHazardFile != null) {
				SolutionLogicTree compSolTree;
				LogicTree<?> compTree;
				if (compResultsFile == null) {
					// just have an average hazard result, possibly an external ERF
					compSolTree = null;
					compTree = null;
				} else {
					compSolTree = SolutionLogicTree.load(compResultsFile);
					compTree = compSolTree.getLogicTree();
					if (compSubsetNodes != null)
						compTree = compTree.matchingAll(compSubsetNodes);
					if (compCurrentWeights)
						compTree.setWeightProvider(new BranchWeightProvider.CurrentWeights());
				}
				comp = new LogicTreeHazardCompare(compSolTree, compTree,
						compHazardFile, rps, periods, spacing);
			}
			
			Preconditions.checkState(outputDir.exists() || outputDir.mkdir());
			
			mapper.buildReport(outputDir, mainName, comp, compName);
		} catch (Exception e) {
			e.printStackTrace();
			exit = 1;
		} finally {
			if (mapper != null) {
				mapper.exec.shutdown();
				mapper.zip.close();
			}
			if (comp != null) {
				comp.zip.close();
				comp.exec.shutdown();
			}
		}
		System.exit(exit);
	}
	private ReturnPeriods[] rps;
	private double[] periods;
	
	private ZipFile zip;
	private List<? extends LogicTreeBranch<?>> branches;
	private List<Double> weights;
	private double totWeight;
	private GriddedRegion gridReg;
	private double spacing;
	private CPT logCPT;
	private CPT spreadCPT;
	private CPT spreadDiffCPT;
	private CPT covCPT;
	private CPT covDiffCPT;
	private CPT diffCPT;
	private CPT pDiffCPT;
	private CPT percentileCPT;
	
	private SolHazardMapCalc mapper;
	private ExecutorService exec;
	private List<Future<?>> futures;
	
	private SolutionLogicTree solLogicTree;

	public LogicTreeHazardCompare(SolutionLogicTree solLogicTree, File mapsZipFile,
			ReturnPeriods[] rps, double[] periods, double spacing) throws IOException {
		this(solLogicTree, solLogicTree.getLogicTree(), mapsZipFile, rps, periods, spacing);
	}

	public LogicTreeHazardCompare(SolutionLogicTree solLogicTree, LogicTree<?> tree, File mapsZipFile,
			ReturnPeriods[] rps, double[] periods, double spacing) throws IOException {
		this.solLogicTree = solLogicTree;
		this.rps = rps;
		this.periods = periods;
		this.spacing = spacing;

		logCPT = GMT_CPT_Files.RAINBOW_UNIFORM.instance().rescale(-3d, 1d);
		spreadCPT = GMT_CPT_Files.RAINBOW_UNIFORM.instance().rescale(0, 1d);
		spreadCPT.setNanColor(Color.LIGHT_GRAY);
		spreadDiffCPT = GMT_CPT_Files.DIVERGING_BAM_UNIFORM.instance().reverse().rescale(-1d, 1d);
		spreadDiffCPT.setNanColor(Color.LIGHT_GRAY);
		covCPT = GMT_CPT_Files.RAINBOW_UNIFORM.instance().rescale(0, 1d);
		covCPT.setNanColor(Color.LIGHT_GRAY);
		covDiffCPT = GMT_CPT_Files.DIVERGING_BAM_UNIFORM.instance().reverse().rescale(-0.5d, 0.5d);
		covDiffCPT.setNanColor(Color.LIGHT_GRAY);
//		pDiffCPT = GMT_CPT_Files.GMT_POLAR.instance().rescale(-100d, 100d);
//		pDiffCPT = GMT_CPT_Files.GMT_POLAR.instance().rescale(-50d, 50d);
		pDiffCPT = GMT_CPT_Files.DIVERGING_VIK_UNIFORM.instance().rescale(-50d, 50d);
//		pDiffCPT = GMT_CPT_Files.DIVERGING_DARK_BLUE_RED_UNIFORM.instance().rescale(-50d, 50d);
		pDiffCPT.setNanColor(Color.LIGHT_GRAY);
		diffCPT = GMT_CPT_Files.DIVERGING_BAM_UNIFORM.instance().reverse().rescale(-0.2, 0.2d);
		diffCPT.setNanColor(Color.LIGHT_GRAY);
		percentileCPT = GMT_CPT_Files.RAINBOW_UNIFORM.instance().rescale(0d, 100d);
		percentileCPT.setNanColor(Color.BLACK);
		percentileCPT.setBelowMinColor(Color.LIGHT_GRAY);
		
		zip = new ZipFile(mapsZipFile);
		
		// see if the zip file has a region attached
		ZipEntry regEntry = zip.getEntry(MPJ_LogicTreeHazardCalc.GRID_REGION_ENTRY_NAME);
		if (regEntry != null) {
			System.out.println("Reading gridded region from zip file: "+regEntry.getName());
			BufferedReader bRead = new BufferedReader(new InputStreamReader(zip.getInputStream(regEntry)));
			gridReg = GriddedRegion.fromFeature(Feature.read(bRead));
			spacing = gridReg.getSpacing();
		}
		
		if (tree == null) {
			// assume external
			branches = new ArrayList<>();
			weights = new ArrayList<>();
			
			branches.add(null);
			weights.add(1d);
			totWeight = 1d;
			
			Preconditions.checkNotNull(gridReg, "Must supply gridded region in zip file if external");
		} else {
			branches = tree.getBranches();
			weights = new ArrayList<>();
			
			BranchWeightProvider weightProv = tree.getWeightProvider();
			
			for (int i=0; i<branches.size(); i++) {
				LogicTreeBranch<?> branch = branches.get(i);
				double weight = weightProv.getWeight(branch);
				Preconditions.checkState(weight > 0, "Bad weight=%s for branch %s, weightProv=%s",
						weight, branch, weightProv.getClass().getName());
				weights.add(weight);
				totWeight += weight;
			}
		}
		
		int threads = Integer.max(2, Integer.min(16, FaultSysTools.defaultNumThreads()));
//		exec = Executors.newFixedThreadPool(threads);
		// this will block to make sure the queue is never too large
		exec = new ThreadPoolExecutor(threads, threads,
                0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<Runnable>(Integer.max(threads*4, threads+10)), new ThreadPoolExecutor.CallerRunsPolicy());
		
		System.out.println(branches.size()+" branches, total weight: "+totWeight);
	}
	
	private GriddedGeoDataSet[] loadMaps(ReturnPeriods rp, double period) throws IOException {
		System.out.println("Loading maps for rp="+rp+", period="+period);
		GriddedGeoDataSet[] rpPerMaps = new GriddedGeoDataSet[branches.size()];
		int printMod = 10;
		LinkedList<Future<?>> loadFutures = new LinkedList<>();
		Stopwatch watch = Stopwatch.createStarted();
		for (int i=0; i<branches.size(); i++) {
			if (i % printMod == 0) {
				try {
					while (!loadFutures.isEmpty())
						loadFutures.pop().get();
				} catch (InterruptedException | ExecutionException e) {
					throw ExceptionUtils.asRuntimeException(e);
				}
				System.out.println("Loading map for branch "+i+"/"+branches.size());
			}
			if (i >= printMod*10 && printMod < 1000)
				printMod *= 10;
			LogicTreeBranch<?> branch = branches.get(i);
			if (branch == null) {
				// external
				Preconditions.checkState(branches.size() == 1);
				Preconditions.checkNotNull(gridReg, "Must supply gridded region in zip file if external");
				
				ZipEntry entry = zip.getEntry(MPJ_LogicTreeHazardCalc.mapPrefix(period, rp)+".txt");
				
				GriddedGeoDataSet xyz = new GriddedGeoDataSet(gridReg, false);
				BufferedReader bRead = new BufferedReader(new InputStreamReader(zip.getInputStream(entry)));
				String line = bRead.readLine();
				int index = 0;
				while (line != null) {
					line = line.trim();
					if (!line.startsWith("#")) {
						StringTokenizer tok = new StringTokenizer(line);
						double lon = Double.parseDouble(tok.nextToken());
						double lat = Double.parseDouble(tok.nextToken());
						double val = Double.parseDouble(tok.nextToken());
						Location loc = new Location(lat, lon);
						Preconditions.checkState(LocationUtils.areSimilar(loc, gridReg.getLocation(index)));
						xyz.set(index++, val);
					}
					line = bRead.readLine();
				}
				Preconditions.checkState(index == gridReg.getNodeCount());
				rpPerMaps[0] = xyz;
			} else {
				FaultSystemSolution sol = null;
				if (mapper == null)
					sol = solLogicTree.forBranch(branch, false);
				
				if (gridReg == null) {
					if (spacing <= 0d) {
						// detect spacing

						String dirName = branch.buildFileName();
						String name = dirName+"/"+MPJ_LogicTreeHazardCalc.mapPrefix(periods[0], rps[0])+".txt";
						ZipEntry entry = zip.getEntry(name);
						Preconditions.checkNotNull(entry, "Entry is null for %s", name);
						InputStream is = zip.getInputStream(entry);
						Preconditions.checkNotNull(is, "IS is null for %s", name);
						
						BufferedReader bRead = new BufferedReader(new InputStreamReader(is));
						String line = bRead.readLine();
						double prevLat = Double.NaN;
						double prevLon = Double.NaN;
						List<Double> deltas = new ArrayList<>();
						while (line != null) {
							line = line.trim();
							if (!line.startsWith("#")) {
								StringTokenizer tok = new StringTokenizer(line);
								double lon = Double.parseDouble(tok.nextToken());
								double lat = Double.parseDouble(tok.nextToken());
								if (Double.isFinite(prevLat)) {
									deltas.add(Math.abs(lat - prevLat));
									deltas.add(Math.abs(lon - prevLon));
								}
								prevLat = lat;
								prevLon = lon;
							}
							line = bRead.readLine();
						}
						double medianSpacing = DataUtils.median(Doubles.toArray(deltas));
						medianSpacing = (double)Math.round(medianSpacing * 1000d) / 1000d;
						System.out.println("Detected spacing: "+medianSpacing+" degrees");
						spacing = medianSpacing;
					}
					
					sol = solLogicTree.forBranch(branch);
					Region region = ReportMetadata.detectRegion(sol);
					if (region == null)
						// just use bounding box
						region = RupSetMapMaker.buildBufferedRegion(sol.getRupSet().getFaultSectionDataList());
					gridReg = new GriddedRegion(region, spacing, GriddedRegion.ANCHOR_0_0);
				}
				
				if (mapper == null)
					mapper = new SolHazardMapCalc(sol, null, gridReg, periods);
				
//				System.out.println("Processing maps for "+branch);
				
				String dirName = branch.buildFileName();
				
				int mapIndex = i;
				loadFutures.push(exec.submit(new Runnable() {
					
					@Override
					public void run() {
						try {
							ZipEntry entry = zip.getEntry(dirName+"/"+MPJ_LogicTreeHazardCalc.mapPrefix(period, rp)+".txt");
							
							GriddedGeoDataSet xyz = new GriddedGeoDataSet(gridReg, false);
							BufferedReader bRead = new BufferedReader(new InputStreamReader(zip.getInputStream(entry)));
							String line = bRead.readLine();
							int index = 0;
							while (line != null) {
								line = line.trim();
								if (!line.startsWith("#")) {
									StringTokenizer tok = new StringTokenizer(line);
									double lon = Double.parseDouble(tok.nextToken());
									double lat = Double.parseDouble(tok.nextToken());
									double val = Double.parseDouble(tok.nextToken());
									Location loc = new Location(lat, lon);
									Preconditions.checkState(LocationUtils.areSimilar(loc, gridReg.getLocation(index)));
									xyz.set(index++, val);
								}
								line = bRead.readLine();
							}
							Preconditions.checkState(index == gridReg.getNodeCount());
							rpPerMaps[mapIndex] = xyz;
						} catch (Exception e) {
							throw ExceptionUtils.asRuntimeException(e);
						}
					}
				}));
			}
		}
		
		try {
			while (!loadFutures.isEmpty())
				loadFutures.pop().get();
		} catch (InterruptedException | ExecutionException e) {
			throw ExceptionUtils.asRuntimeException(e);
		}
		watch.stop();
		printTime(watch, "load "+branches.size()+" maps", 0d);
		
		return rpPerMaps;
	}
	
	private GriddedGeoDataSet buildMean(GriddedGeoDataSet[] maps) {
		GriddedGeoDataSet avg = new GriddedGeoDataSet(maps[0].getRegion(), false);
		
		for (int i=0; i<avg.size(); i++) {
			double val = 0d;
			for (int j=0; j<maps.length; j++)
				val += maps[j].get(i)*weights.get(j);
			val /= totWeight;
			avg.set(i, val);
		}
		
		return avg;
	}
	
	private GriddedGeoDataSet buildMean(List<GriddedGeoDataSet> maps, List<Double> weights) {
		GriddedGeoDataSet avg = new GriddedGeoDataSet(maps.get(0).getRegion(), false);
		
		double totWeight = 0d;
		for (Double weight : weights)
			totWeight += weight;
		
		for (int i=0; i<avg.size(); i++) {
			double val = 0d;
			for (int j=0; j<maps.size(); j++)
				val += maps.get(j).get(i)*weights.get(j);
			val /= totWeight;
			avg.set(i, val);
		}
		
		return avg;
	}
	
	private GriddedGeoDataSet buildMin(GriddedGeoDataSet[] maps) {
		GriddedGeoDataSet min = new GriddedGeoDataSet(maps[0].getRegion(), false);
		
		for (int i=0; i<min.size(); i++) {
			double val = Double.POSITIVE_INFINITY;
			for (int j=0; j<maps.length; j++)
				val = Math.min(val, maps[j].get(i));
			min.set(i, val);
		}
		
		return min;
	}
	
	private GriddedGeoDataSet buildMax(GriddedGeoDataSet[] maps) {
		GriddedGeoDataSet max = new GriddedGeoDataSet(maps[0].getRegion(), false);
		
		for (int i=0; i<max.size(); i++) {
			double val = Double.NEGATIVE_INFINITY;
			for (int j=0; j<maps.length; j++)
				val = Math.max(val, maps[j].get(i));
			max.set(i, val);
		}
		
		return max;
	}
	
	private GriddedGeoDataSet buildPDiffFromRange(GriddedGeoDataSet min, GriddedGeoDataSet max, GriddedGeoDataSet comp) {
		GriddedGeoDataSet diff = new GriddedGeoDataSet(min.getRegion(), false);
		
		for (int i=0; i<max.size(); i++) {
			double compVal = comp.get(i);
			double minVal = min.get(i);
			double maxVal = max.get(i);
			double pDiff;
			if (compVal >= minVal && compVal <= maxVal)
				pDiff = 0d;
			else if (compVal < minVal)
				pDiff = 100d*(compVal-minVal)/minVal;
			else
				pDiff = 100d*(compVal-maxVal)/maxVal;
			diff.set(i, pDiff);
		}
		
		return diff;
	}
	
	private GriddedGeoDataSet buildSpread(GriddedGeoDataSet min, GriddedGeoDataSet max) {
		GriddedGeoDataSet diff = new GriddedGeoDataSet(min.getRegion(), false);
		
		for (int i=0; i<max.size(); i++) {
			double minVal = min.get(i);
			double maxVal = max.get(i);
			diff.set(i, maxVal - minVal);
		}
		
		return diff;
	}
	
	private GriddedGeoDataSet buildPDiff(GriddedGeoDataSet ref, GriddedGeoDataSet comp) {
		GriddedGeoDataSet diff = new GriddedGeoDataSet(ref.getRegion(), false);
		
		for (int i=0; i<ref.size(); i++) {
			double compVal = comp.get(i);
			double refVal = ref.get(i);
			double pDiff = 100d*(compVal-refVal)/refVal;
			diff.set(i, pDiff);
		}
		
		return diff;
	}
	
	private LightFixedXFunc[] buildNormCDFs(GriddedGeoDataSet[] maps, List<Double> weights) {
		return buildNormCDFs(List.of(maps), weights);
	}
	
	private static class ValWeights implements Comparable<ValWeights> {
		double val;
		double weight;
		public ValWeights(double val, double weight) {
			super();
			this.val = val;
			this.weight = weight;
		}
		@Override
		public int compareTo(ValWeights o) {
			return Double.compare(val, o.val);
		}
	}
	
	private LightFixedXFunc[] buildNormCDFs(List<GriddedGeoDataSet> maps, List<Double> weights) {
		Preconditions.checkState(maps.size() == weights.size());
		LightFixedXFunc[] ret = new LightFixedXFunc[maps.get(0).size()];
		
		double totWeight = 0d;
		for (double weight : weights)
			totWeight += weight;
		
		Stopwatch watch = Stopwatch.createStarted();
		if (maps.size() < 500 || ret.length < 100) {
			// just do it serially
			for (int i=0; i<ret.length; i++)
				ret[i] = calcNormCDF(maps, weights, i, totWeight);
		} else {
			// do it in parallel
			List<Future<?>> futures = new ArrayList<>(ret.length);
			final double finalTotWeight = totWeight;
			for (int i=0; i<ret.length; i++) {
				int gridIndex = i;
				futures.add(exec.submit(new Runnable() {
					
					@Override
					public void run() {
						ret[gridIndex] = calcNormCDF(maps, weights, gridIndex, finalTotWeight);
					}
				}));
			}
			try {
				for (Future<?> future : futures)
					future.get();
			} catch (Exception e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
		}
		watch.stop();
		printTime(watch, "build nrom CDFs for "+maps.size()+" maps", 10d);
		
		for (int i=0; i<ret.length; i++) {
			LightFixedXFunc ncdf = ret[i];
			if (ncdf.size() > 1) {
				double first = ncdf.getY(0);
				double last = ncdf.getY(ncdf.size()-1);
				Preconditions.checkState((float)last == 1f, "last value in ncdf not 1: %s", last);
				Preconditions.checkState(first < last, "first value in ncdf is not less than last: %s %s", first, last);
			}
		}
		
		return ret;
	}
	
	private static void printTime(Stopwatch watch, String operation, double minSecsToPrint) {
		double secs = watch.elapsed(TimeUnit.MILLISECONDS)/1000d;
		if (secs < minSecsToPrint)
			return;
		if (secs > 90d)
			System.out.println("Took "+twoDigits.format(secs/60d)+" mins to "+operation);
		else
			System.out.println("Took "+twoDigits.format(secs)+" secs to "+operation);
	}
	
	private static LightFixedXFunc calcNormCDF(List<GriddedGeoDataSet> maps, List<Double> weights,
			int gridIndex, double totWeight) {
		// could use ArbDiscrEmpiricalDistFunc, but this version is 25-50% faster
		ValWeights[] valWeights = new ValWeights[maps.size()];
		for (int j=0; j<valWeights.length; j++)
			valWeights[j] = new ValWeights(maps.get(j).get(gridIndex), weights.get(j));
		// sort ascending
		Arrays.sort(valWeights);
		int destIndex = -1;
		double[] xVals = new double[valWeights.length];
		double[] yVals = new double[valWeights.length];
		for (int srcIndex=0; srcIndex<valWeights.length; srcIndex++) {
			ValWeights val = valWeights[srcIndex];
			if (destIndex >= 0 && (float)val.val == (float)xVals[destIndex]) {
				// add it, don't increment
				yVals[destIndex] += val.weight;
			} else {
				// move to a new index
				destIndex++;
				xVals[destIndex] = val.val;
				yVals[destIndex] = val.weight;
			}
		}
		int size = destIndex+1;
		if (size < xVals.length) {
			// we have duplicates, trim them
			xVals = Arrays.copyOf(xVals, size);
			yVals = Arrays.copyOf(yVals, size);
		}

		// now convert yVals to a CDF
		double sum = 0d;
		for (int j=0; j<yVals.length; j++) {
			sum += yVals[j];
			yVals[j] = sum/totWeight;
			if (j > 0)
				Preconditions.checkState(xVals[j] > xVals[j-1]);
		}

		return new LightFixedXFunc(xVals, yVals);
	}
	
	private GriddedGeoDataSet calcMapAtPercentile(LightFixedXFunc[] ncdfs, GriddedRegion gridReg,
			double percentile) {
		Preconditions.checkState(gridReg.getNodeCount() == ncdfs.length, "grid reg has %s, but have %s ncdfs",
				gridReg.getNodeCount(), ncdfs.length);
		GriddedGeoDataSet ret = new GriddedGeoDataSet(gridReg, false);
		
		double fractile = percentile/100d;
		Preconditions.checkState(fractile >= 0d && fractile <= 1d);

		Stopwatch watch = Stopwatch.createStarted();
		for (int i=0; i<ret.size(); i++) {
			int len = ncdfs[i].size();
			double val;
			if (len == 1) {
				if (percentile == 50d)
					val = ncdfs[i].getX(0);
				else
					val = Double.NaN;
			} else if (fractile == 0d) {
				val = ncdfs[i].getX(0);
			} else if (fractile == 1d) {
				val = ncdfs[i].getX(len-1);
			} else {
				double[] yVals = ncdfs[i].getYVals();
				int index = Arrays.binarySearch(yVals, fractile);
				if (index >= 0) {
					// probably will never actually happen
					val = ncdfs[i].getX(index);
				} else if (yVals[0] >= fractile) {
					val = ncdfs[i].getX(0);
				} else {
					// insertion index, value below this will be < fractile, value at will be >
					index = -(index + 1);
					// these cases should have been taken care of above
					Preconditions.checkState(index > 0 && index < len,
							"Unexpected insertion index=%s with len=%s, fractile=%s", index, len, fractile);
					double v1 = ncdfs[i].getX(index-1);
					double v2 = ncdfs[i].getX(index);
					double f1 = ncdfs[i].getY(index-1);
					double f2 = ncdfs[i].getY(index);
					Preconditions.checkState(f1<fractile);
					Preconditions.checkState(f2>fractile);
					val = Interpolate.findX(v1, f1, v2, f2, fractile);
				}
			}
			ret.set(i, val);
		}
		watch.stop();
		printTime(watch, "calc map at p"+(float)percentile+" for "+ncdfs.length+" normCDFs", 10d);
		
		return ret;
	}
	
	private GriddedGeoDataSet calcPercentileWithinDist(LightFixedXFunc[] ncdfs, GriddedGeoDataSet comp) {
		Preconditions.checkState(comp.size() == ncdfs.length);
		GriddedGeoDataSet ret = new GriddedGeoDataSet(comp.getRegion(), false);
		
		for (int i=0; i<ret.size(); i++) {
			double compVal = comp.get(i);
			
			double percentile;
			if (ncdfs[i].size() == 1) {
				percentile = -1d;
			} else if (compVal < ncdfs[i].getMinX() || compVal > ncdfs[i].getMaxX()) {
				percentile = Double.NaN;
			} else {
				percentile = 100d * ncdfs[i].getInterpolatedY(compVal);
			}
			ret.set(i, percentile);
		}
		
		return ret;
	}
	
	private GriddedGeoDataSet calcCOV(GriddedGeoDataSet[] maps, List<Double> weights,
			GriddedGeoDataSet meanMap, GriddedRegion gridReg) {
		GriddedGeoDataSet ret = new GriddedGeoDataSet(gridReg, false);
		
		double[] weightsArray = Doubles.toArray(weights);

		Stopwatch watch = Stopwatch.createStarted();
		if (maps.length < 500 || ret.size() < 100) {
			// serial
			for (int i=0; i<ret.size(); i++)
				ret.set(i, calcCOV(maps, weightsArray, meanMap, i));
		} else {
			// parallel
			List<Future<?>> futures = new ArrayList<>(ret.size());
			for (int i=0; i<ret.size(); i++) {
				int gridIndex = i;
				futures.add(exec.submit(new Runnable() {
					
					@Override
					public void run() {
						ret.set(gridIndex, calcCOV(maps, weightsArray, meanMap, gridIndex));
					}
				}));
			}
			try {
				for (Future<?> future : futures)
					future.get();
			} catch (Exception e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
		}
		watch.stop();
		printTime(watch, "calc COVs for "+maps.length+" maps", 10d);
		
		return ret;
	}
	
	private static double calcCOV(GriddedGeoDataSet[] maps, double[] weights, GriddedGeoDataSet meanMap, int gridIndex) {
		double mean = meanMap.get(gridIndex);
		double val;
		if (maps.length == 1) {
			val = 0d;
		} else if (mean == 0d) {
			val = Double.NaN;
		} else {
			Variance var = new Variance();
			double[] cellVals = new double[maps.length];
			for (int j=0; j<cellVals.length; j++)
				cellVals[j] = maps[j].get(gridIndex);
			double stdDev = Math.sqrt(var.evaluate(cellVals, weights));
			val = stdDev/mean;
		}
		return val;
	}
	
//	private GriddedGeoDataSet calcPercentile(GriddedGeoDataSet[] maps, GriddedGeoDataSet comp) {
//		return calcPercentile(List.of(maps), weights, comp);
//	}
//	 
//	private GriddedGeoDataSet calcPercentile(List<GriddedGeoDataSet> maps, List<Double> weights, GriddedGeoDataSet comp) {
//		Preconditions.checkState(maps.size() == weights.size());
//		GriddedGeoDataSet ret = new GriddedGeoDataSet(maps.get(0).getRegion(), false);
//		
//		double totWeight;
//		if (weights == this.weights) {
//			totWeight = this.totWeight;
//		} else {
//			totWeight = 0d;
//			for (double weight : weights)
//				totWeight += weight;
//		}
//		
//		for (int i=0; i<ret.size(); i++) {
//			double compVal = comp.get(i);
//			double weightAbove = 0d; // weight of tree thats above comp value
//			double weightEqual = 0d; // weight of tree thats equal to comp value
//			double weightBelow = 0d; // weight of tree thats below comp value
//			int numAbove = 0;
//			int numBelow = 0;
//			for (int j=0; j<maps.size(); j++) {
//				double val = maps.get(j).get(i);
//				double weight = weights.get(j);
//				if (((float) compVal == (float)val) || (Double.isNaN(compVal)) && Double.isNaN(val)) {
//					weightEqual += weight;
//				} else if (compVal < val) {
//					weightAbove += weight;
//					numAbove++;
//				} else if (compVal > val) {
//					numBelow++;
//					weightBelow += weight;
//				}
//			}
//			if (weightEqual != 0d) {
//				// redistribute any exactly equal to either side
//				weightAbove += 0.5*weightEqual;
//				weightBelow += 0.5*weightEqual;
//			}
//			// normalize by total weight
//			weightAbove /= totWeight;
//			weightBelow /= totWeight;
//			double percentile;
//			if (numAbove == maps.size() || numBelow == maps.size())
//				percentile = Double.NaN;
//			else
//				percentile = 100d*weightBelow;
////			if (numAbove == maps.size() || numBelow == maps.size()) {
////				percentile = Double.NaN;
////			} else if (weightAbove > weightBelow) {
////				// more of the distribution is above my value
////				// this means that the percentile is <50
////				percentile = 100d*weightBelow/totWeight;
////			} else {
////				// more of the distribution is below my value
////				// this means that the percentile is >50
////				percentile = 100d*weightAbove/totWeight;
////				percentile = 100d*(1d - (weightAbove/totWeight));
////			}
//			ret.set(i, percentile);
//		}
//		
//		return ret;
//	}
	
	public void buildReport(File outputDir, String name, LogicTreeHazardCompare comp, String compName) throws IOException {
		List<String> lines = new ArrayList<>();
		
		File resourcesDir = new File(outputDir, "resources");
		Preconditions.checkState(resourcesDir.exists() || resourcesDir.mkdir());
		
		lines.add("# "+name+" Hazard Maps");
		lines.add("");
		int tocIndex = lines.size();
		String topLink = "_[(top)](#table-of-contents)_";
		
		futures = new ArrayList<>();
		
		for (double period : periods) {
			String perLabel, perPrefix;
			if (period == 0d) {
				perLabel = "PGA (g)";
				perPrefix = "pga";
			} else {
				perLabel = (float)period+"s SA";
				perPrefix = (float)period+"s";
			}
			
			for (ReturnPeriods rp : rps) {
				String label = perLabel+", "+rp.label;
				String prefix = perPrefix+"_"+rp.name();
				
				System.out.println(label);
				
				lines.add("## "+label);
				lines.add(topLink);
				lines.add("");
				
				GriddedGeoDataSet[] maps = this.loadMaps(rp, period);
				Preconditions.checkNotNull(maps);
				for (int i=0; i<maps.length; i++)
					Preconditions.checkNotNull(maps[i], "map %s is null", i);
				
				GriddedRegion region = maps[0].getRegion();
				
				System.out.println("Calculating norm CDFs");
				LightFixedXFunc[] mapNCDFs = buildNormCDFs(maps, weights);
				System.out.println("Calculating mean, median, bounds, COV");
				GriddedGeoDataSet mean = buildMean(maps);
				GriddedGeoDataSet median = calcMapAtPercentile(mapNCDFs, region, 50d);
				GriddedGeoDataSet max = buildMax(maps);
				GriddedGeoDataSet min = buildMin(maps);
				GriddedGeoDataSet spread = buildSpread(log10(min), log10(max));
				GriddedGeoDataSet cov = calcCOV(maps, weights, mean, region);
				
				File hazardCSV = new File(resourcesDir, prefix+".csv");
				System.out.println("Writing CSV: "+hazardCSV.getAbsolutePath());
				writeHazardCSV(hazardCSV, mean, median, min, max);
				
//				table.addLine(meanMinMaxSpreadMaps(mean, min, max, spread, name, label, prefix, resourcesDir));
				
				LightFixedXFunc[] cmapNCDFs = null;
				GriddedGeoDataSet cmean = null;
				GriddedGeoDataSet cmedian = null;
				GriddedGeoDataSet cmin = null;
				GriddedGeoDataSet cmax = null;
				GriddedGeoDataSet cspread = null;
				GriddedGeoDataSet ccov = null;
				
				if (comp != null) {
					System.out.println("Loading comparison");
					GriddedGeoDataSet[] cmaps = comp.loadMaps(rp, period);
					Preconditions.checkNotNull(cmaps);
					for (int i=0; i<cmaps.length; i++)
						Preconditions.checkNotNull(cmaps[i], "map %s is null", i);

					System.out.println("Calculating comparison norm CDFs");
					cmapNCDFs = buildNormCDFs(cmaps, comp.weights);
					System.out.println("Calculating comparison mean, median, bounds, COV");
					cmean = comp.buildMean(cmaps);
					cmedian = calcMapAtPercentile(cmapNCDFs, region, 50d);
					cmax = comp.buildMax(cmaps);
					cmin = comp.buildMin(cmaps);
					cspread = comp.buildSpread(log10(cmin), log10(cmax));
					ccov = calcCOV(cmaps, comp.weights, cmean, region);
//					table.addLine(meanMinMaxSpreadMaps(cmean, cmin, cmax, cspread, compName, label, prefix+"_comp", resourcesDir));
					
					File compHazardCSV = new File(resourcesDir, prefix+"_comp.csv");
					System.out.println("Writing CSV: "+compHazardCSV.getAbsolutePath());
					writeHazardCSV(compHazardCSV, cmean, cmedian, cmin, cmax);
					
					lines.add("Download Mean Hazard CSVs: ["+hazardCSV.getName()+"]("+resourcesDir.getName()+"/"+hazardCSV.getName()
						+")  ["+compHazardCSV.getName()+"]("+resourcesDir.getName()+"/"+compHazardCSV.getName()+")");
				} else {
					lines.add("Download Mean Hazard CSV: ["+hazardCSV.getName()+"]("+resourcesDir.getName()+"/"+hazardCSV.getName()+")");
				}
				lines.add("");
				
				MapPlot meanMapPlot = mapper.buildMapPlot(resourcesDir, prefix+"_mean", log10(mean),
						logCPT, TITLES ? name : " ", "Log10 Weighted-Average, "+label, false);
				File meanMapFile = new File(resourcesDir, meanMapPlot.prefix+".png");
				GriddedGeoDataSet.writeXYZFile(mean, new File(resourcesDir, prefix+"_mean.xyz"));
				File medianMapFile = submitMapFuture(mapper, exec, futures, resourcesDir, prefix+"_median", log10(median),
						logCPT, TITLES ? name : " ", "Log10 Weighted-Median, "+label);
				GriddedGeoDataSet.writeXYZFile(median, new File(resourcesDir, prefix+"_median.xyz"));
				
				if (cmean == null) {
					lines.add("### Mean and median hazard maps, "+label);
					lines.add(topLink); lines.add("");
					
					TableBuilder table = MarkdownUtils.tableBuilder();
					// no comparison, simple table
					table.initNewLine();
					table.addColumn(MarkdownUtils.boldCentered("Weighted-Average"));
					table.addColumn(MarkdownUtils.boldCentered("Weighted-Median"));
					table.finalizeLine().initNewLine();
					table.addColumn("![Mean Map]("+resourcesDir.getName()+"/"+meanMapFile.getName()+")");
					table.addColumn("![Median Map]("+resourcesDir.getName()+"/"+medianMapFile.getName()+")");
					table.finalizeLine();
					lines.addAll(table.build());
					lines.add("");
				} else {
					lines.add("### Mean hazard maps and comparisons, "+label);
					lines.add(topLink); lines.add("");
					
					TableBuilder table = MarkdownUtils.tableBuilder();
					
					// comparison
					File cmeanMapFile = submitMapFuture(mapper, exec, futures, resourcesDir, prefix+"_comp_mean", log10(cmean),
							logCPT, TITLES ? compName : " ", "Log10 Weighted-Average, "+label);
					GriddedGeoDataSet.writeXYZFile(cmean, new File(resourcesDir, prefix+"_comp_mean.xyz"));
					File cmedianMapFile = submitMapFuture(mapper, exec, futures, resourcesDir, prefix+"_comp_median", log10(cmedian),
							logCPT, TITLES ? compName : " ", "Log10 Weighted-Median, "+label);
					GriddedGeoDataSet.writeXYZFile(cmedian, new File(resourcesDir, prefix+"_comp_median.xyz"));
					
					table.initNewLine();
					table.addColumn(MarkdownUtils.boldCentered("Primary (_"+name+"_) Weighted-Average"));
					table.addColumn(MarkdownUtils.boldCentered("Comparison (_"+compName+"_) Weighted-Average"));
					table.finalizeLine().initNewLine();
					table.addColumn("![Mean Map]("+resourcesDir.getName()+"/"+meanMapFile.getName()+")");
					table.addColumn("![Mean Map]("+resourcesDir.getName()+"/"+cmeanMapFile.getName()+")");
					table.finalizeLine();
					
					lines.addAll(table.build());
					lines.add("");
					
					table = MarkdownUtils.tableBuilder();
					addDiffInclExtremesLines(mean, name, min, max, cmean, compName, prefix+"_mean", resourcesDir, table, "Mean", label, false);
					addDiffInclExtremesLines(mean, name, min, max, cmean, compName, prefix+"_mean", resourcesDir, table, "Mean", label, true);
					
					lines.add("The following plots compare mean hazard between _"+name+"_ and a comparison model, _"
							+ compName+"_. The top row gives hazard ratios, expressed as % change, and the bottom row "
							+ "gives differences.");
					lines.add("");
					lines.add("The left column compares the mean maps directly, with the comparison model as the "
							+ "divisor/subtrahend. Warmer colors indicate increased hazard in _"+name
							+ "_ relative to _"+compName+"_.");
					lines.add("");
					lines.add("The right column shows where and by how much the comparison mean model (_"+compName
							+ "_) is outside the distribution of values across all branches of the primary model (_"
							+ name+"_). Here, places that are zeros (light gray) indicate that the comparison mean "
							+ "hazard map is fully contained within the range of values in _"+name+"_, cool colors "
							+ "indicate areas where the primary model is always lower than the comparison mean model, "
							+ "and warm colors areas where the primary model is always greater. Note that the color "
							+ "scales are reversed here so that colors are consistent with the left column even though "
							+ "the comparison model is now the dividend/minuend.");
					lines.add("");
					lines.addAll(table.build());
					lines.add("");
					
					lines.add("### Median hazard maps and comparisons, "+label);
					lines.add(topLink); lines.add("");
					
					table = MarkdownUtils.tableBuilder();
					table.initNewLine();
					table.addColumn(MarkdownUtils.boldCentered("Primary Weighted-Median"));
					table.addColumn(MarkdownUtils.boldCentered("Comparison Weighted-Median"));
					table.finalizeLine().initNewLine();
					table.addColumn("![Median Map]("+resourcesDir.getName()+"/"+medianMapFile.getName()+")");
					table.addColumn("![Median Map]("+resourcesDir.getName()+"/"+cmedianMapFile.getName()+")");
					table.finalizeLine();
					
					lines.addAll(table.build());
					lines.add("");
					
					table = MarkdownUtils.tableBuilder();
					
					addDiffInclExtremesLines(median, name, min, max, cmedian, compName, prefix+"_median", resourcesDir, table, "Median", label, false);
					addDiffInclExtremesLines(median, name, min, max, cmedian, compName, prefix+"_median", resourcesDir, table, "Median", label, true);
					
					lines.add("");
					lines.add("This section is the same as above, but using median hazard maps rather than mean.");
					lines.add("");
					lines.addAll(table.build());
					lines.add("");
					
					table = MarkdownUtils.tableBuilder();
					
					table.addLine(MarkdownUtils.boldCentered("Comparison Mean Percentile"),
							MarkdownUtils.boldCentered("Comparison Median Percentile"));
					GriddedGeoDataSet cMeanPercentile = calcPercentileWithinDist(mapNCDFs, cmean);
					GriddedGeoDataSet cMedianPercentile = calcPercentileWithinDist(mapNCDFs, cmedian);
					table.initNewLine();
					File map = submitMapFuture(mapper, exec, futures, resourcesDir, prefix+"_comp_mean_percentile", cMeanPercentile,
							percentileCPT, TITLES ? name+" vs "+compName : " ", "Comparison Mean %-ile, "+label);
					table.addColumn("![Mean Percentile Map]("+resourcesDir.getName()+"/"+map.getName()+")");
					map = submitMapFuture(mapper, exec, futures, resourcesDir, prefix+"_comp_median_percentile", cMedianPercentile,
							percentileCPT, TITLES ? name+" vs "+compName : " ", "Comparison Median %-ile, "+label);
					table.addColumn("![Median Percentile Map]("+resourcesDir.getName()+"/"+map.getName()+")");
					table.finalizeLine();
					lines.add("### Percentile comparison maps, "+label);
					lines.add(topLink); lines.add("");
					lines.add("The maps below show where the comparison (_"+compName+"_) model mean (left column) and "
							+ "median (right column) map lies within the primary model (_"+name+"_) distribution. "
							+ "Areas where the comparison mean or median map is outside the primary model distribution "
							+ "are shown here in black regardless of if they are above or below.");
					lines.add("");
					lines.addAll(table.build());
					lines.add("");
				}
				
				// plot mean percentile
				TableBuilder table = MarkdownUtils.tableBuilder();
				GriddedGeoDataSet meanPercentile = calcPercentileWithinDist(mapNCDFs, mean);
				File meanPercentileMap = submitMapFuture(mapper, exec, futures, resourcesDir, prefix+"_mean_percentile",
						meanPercentile, percentileCPT, TITLES ? "Branch-Averaged Percentiles" : " ",
						"Branch Averaged %-ile, "+label);
//				GriddedGeoDataSet median = calc
				table.addLine("Mean Map Percentile", "Mean vs Median");
				GriddedGeoDataSet meanMedDiff = buildPDiff(median, mean);
				File meanMedDiffMap = submitMapFuture(mapper, exec, futures, resourcesDir, prefix+"_mean_median_diff",
						meanMedDiff, pDiffCPT, TITLES ? name : " ", "Mean / Median, % Change, "+label, true);
				table.addLine("![BA percentiles]("+resourcesDir.getName()+"/"+meanPercentileMap.getName()+")",
						"![Median vs Mean]("+resourcesDir.getName()+"/"+meanMedDiffMap.getName()+")");
				String branchStr = "Branched-average hazard can be dominated by outlier branches. The map below on the "
						+ "left shows the percentile at which the ";
				if (comp != null)
					branchStr += "primary model's mean map lies within its own full hazard distribution. ";
				else
					branchStr += "mean map lies within the full hazard distribution. ";
				branchStr += "Areas far from the 50-th percentile here are likely outlier-dominated and may show up in "
						+ "percentile comparison maps, even if mean hazard differences are minimal. Keep this in mind "
						+ "when evaluating ";
				if (comp != null)
					branchStr += "the maps above, and ";
				branchStr += "the influence of individual logic tree branches by this metric. The right map show the ratio "
							+ "of mean to median hazard.";
				
				lines.add(branchStr);
				lines.add("");
				lines.addAll(table.build());
				lines.add("");
				
				lines.add("### Bounds, spread, and COV, "+label);
				lines.add(topLink); lines.add("");
				
				String minMaxStr = "The maps below show the range of values across all logic tree branches, the ratio of "
						+ "the maximum to minimum value, and the coefficient of variation (std. dev. / mean). Note that "
						+ "the minimum and maximum maps are not a result for any single logic tree branch, but rather "
						+ "the smallest or largest value encountered at each location across all logic tree branches.";

				if (cmean == null || comp.branches.size() == 1) {
					// no comparison
					table = MarkdownUtils.tableBuilder();
					table.addLine(MarkdownUtils.boldCentered("Minimum"), MarkdownUtils.boldCentered("Maximum"));
					
					File minMapFile = submitMapFuture(mapper, exec, futures, resourcesDir, prefix+"_min", log10(min),
							logCPT, TITLES ? name : " ", "Log10 Minimum, "+label);
					File maxMapFile = submitMapFuture(mapper, exec, futures, resourcesDir, prefix+"_max", log10(max),
							logCPT, TITLES ? name : " ", "Log10 Maximum, "+label);
					File spreadMapFile = submitMapFuture(mapper, exec, futures, resourcesDir, prefix+"_spread", spread,
							spreadCPT, TITLES ? name : " ", "Log10 (Max/Min), "+label);
					File covMapFile = submitMapFuture(mapper, exec, futures, resourcesDir, prefix+"_cov", cov,
							covCPT, TITLES ? name : " ", "COV, "+label);
					
					table.initNewLine();
					table.addColumn("![Min Map]("+resourcesDir.getName()+"/"+minMapFile.getName()+")");
					table.addColumn("![Max Map]("+resourcesDir.getName()+"/"+maxMapFile.getName()+")");
					table.finalizeLine();
					table.addLine(MarkdownUtils.boldCentered("Log10 (Max/Min)"), MarkdownUtils.boldCentered("COV"));
					table.initNewLine();
					table.addColumn("![Spread Map]("+resourcesDir.getName()+"/"+spreadMapFile.getName()+")");
					table.addColumn("![COV Map]("+resourcesDir.getName()+"/"+covMapFile.getName()+")");
					table.finalizeLine();
					
					lines.add("");
					lines.add(minMaxStr);
					lines.add("");
					lines.addAll(table.build());
					lines.add("");
				} else {
					table = MarkdownUtils.tableBuilder();
					// comparison
					addMapCompDiffLines(min, name, cmin, compName, prefix+"_min", resourcesDir, table,
							"Minimum", "Minimum "+label, logCPT, false, pDiffCPT);
					addMapCompDiffLines(max, name, cmax, compName, prefix+"_max", resourcesDir, table,
							"Maximum", "Maximum"+label, logCPT, false, pDiffCPT);
					addMapCompDiffLines(spread, name, cspread, compName, prefix+"_spread", resourcesDir, table,
							"Log10 (Max/Min)", "Log10 (Max/Min) "+label, spreadCPT, true, spreadDiffCPT);
					addMapCompDiffLines(cov, name, ccov, compName, prefix+"_cov", resourcesDir, table,
							"COV", label+", COV", covCPT, true, covDiffCPT);
					
					lines.add("");
					lines.add(minMaxStr+" Each of those quantities is plotted separately for the primary and comparison "
							+ "model, then compared in the rightmost column.");
					lines.add("");
					lines.addAll(table.build());
					lines.add("");
				}
				
				lines.add("### "+label+" Logic Tree Comparisons");
				lines.add(topLink); lines.add("");
				
				lines.add("This section shows how hazard changes across branch choices at each level of the logic tree. "
						+ "The summary figures below show mean hazard on the left, and then ratios & differences between "
						+ "the mean map considering subsets of the model holding each branch choice constant, and the "
						+ "overall mean map.");
				lines.add("");
				
				int combinedMapIndex = lines.size();
				
				System.out.println("Building logic tree plots");
				
				List<LogicTreeLevel<?>> branchLevels = new ArrayList<>();
				List<List<LogicTreeNode>> branchLevelValues = new ArrayList<>();
				List<List<MapPlot>> branchLevelPDiffPlots = new ArrayList<>();
				List<List<MapPlot>> branchLevelDiffPlots = new ArrayList<>();
				
				for (LogicTreeLevel<?> level : solLogicTree.getLogicTree().getLevels()) {
					HashMap<LogicTreeNode, List<GriddedGeoDataSet>> choiceMaps = new HashMap<>();
					HashMap<LogicTreeNode, List<Double>> choiceWeights = new HashMap<>();
					for (int i=0; i<branches.size(); i++) {
						LogicTreeBranch<?> branch = branches.get(i);
						LogicTreeNode choice = branch.getValue(level.getType());
						List<GriddedGeoDataSet> myChoiceMaps = choiceMaps.get(choice);
						if (myChoiceMaps == null) {
							myChoiceMaps = new ArrayList<>();
							choiceMaps.put(choice, myChoiceMaps);
							choiceWeights.put(choice, new ArrayList<>());
						}
						myChoiceMaps.add(maps[i]);
						choiceWeights.get(choice).add(weights.get(i));
					}
					if (choiceMaps.size() > 1) {
						System.out.println(level.getName()+" has "+choiceMaps.size()+" choices");
						lines.add("#### "+level.getName()+", "+label);
						lines.add(topLink); lines.add("");
						lines.add("This section shows how mean hazard varies accross "+choiceMaps.size()+" choices at the "
								+ "_"+level.getName()+"_ branch level.");
						lines.add("");
						
						HashMap<LogicTreeNode, GriddedGeoDataSet> choiceMeans = new HashMap<>();
						for (LogicTreeNode choice : choiceMaps.keySet())
							choiceMeans.put(choice, buildMean(choiceMaps.get(choice), choiceWeights.get(choice)));
						
						table = MarkdownUtils.tableBuilder();
						table.initNewLine().addColumn("**Choice**").addColumn("**Vs Mean**");
						TableBuilder mapVsChoiceTable = MarkdownUtils.tableBuilder();
						mapVsChoiceTable.initNewLine().addColumn("**Choice**");
						List<LogicTreeNode> choices = new ArrayList<>();
						for (LogicTreeNode choice : level.getNodes())
							if (choiceMaps.containsKey(choice))
								choices.add(choice);
						Preconditions.checkState(choices.size() == choiceMaps.size());
//						List<LogicTreeNode> choices = new ArrayList<>(choiceMaps.keySet());
//						Collections.sort(choices, nodeNameCompare);
						
						for (LogicTreeNode choice : choices) {
							table.addColumn("**Vs "+choice.getShortName()+"**");
							mapVsChoiceTable.addColumn("**Vs "+choice.getShortName()+"**");
						}
						table.finalizeLine();
						mapVsChoiceTable.finalizeLine();
						
						File choicesCSV = new File(resourcesDir, prefix+"_"+level.getShortName().replaceAll("\\W+", "_")+".csv");
						writeChoiceHazardCSV(choicesCSV, mean, choices, choiceMeans);
						lines.add("Download Choice Hazard CSV: ["+choicesCSV.getName()+"]("+resourcesDir.getName()+"/"+choicesCSV.getName()+")");
						lines.add("");
						
						TableBuilder mapTable = MarkdownUtils.tableBuilder();
						mapTable.addLine("", "Choice Mean vs Full Mean, % Change", "Choice Mean - Full Mean",
								"Choice Percentile in Full Dist", "Choice Percentile in Dist Without");
						
						MinMaxAveTracker runningDiffAvg = new MinMaxAveTracker();
						MinMaxAveTracker runningAbsDiffAvg = new MinMaxAveTracker();
						
						branchLevels.add(level);
						branchLevelValues.add(choices);
						List<MapPlot> branchPDiffPlots = new ArrayList<>();
						List<MapPlot> branchDiffPlots = new ArrayList<>();
						branchLevelPDiffPlots.add(branchPDiffPlots);
						branchLevelDiffPlots.add(branchDiffPlots);
						
						for (int c=0; c<choices.size(); c++) {
							LogicTreeNode choice = choices.get(c);
							table.initNewLine().addColumn("**"+choice.getShortName()+"**");
							mapVsChoiceTable.initNewLine().addColumn("**"+choice.getShortName()+"**");
							
							GriddedGeoDataSet choiceMap = choiceMeans.get(choice);
							table.addColumn(mapPDiffStr(choiceMap, mean, null, null));
							
							for (LogicTreeNode oChoice : choices) {
								if (choice == oChoice) {
									table.addColumn("");
									mapVsChoiceTable.addColumn("");
								} else {
									table.addColumn(mapPDiffStr(choiceMap, choiceMeans.get(oChoice),
											runningDiffAvg, runningAbsDiffAvg));
									// plot choice vs choice map
									GriddedGeoDataSet oChoiceMap = choiceMeans.get(oChoice);
									GriddedGeoDataSet pDiff = buildPDiff(oChoiceMap, choiceMap);
									File map = submitMapFuture(mapper, exec, futures, resourcesDir, prefix+"_"+choice.getFilePrefix()+"_vs_"+oChoice.getFilePrefix(),
											pDiff, pDiffCPT, TITLES ? choice.getShortName()+" vs "+oChoice.getShortName() : " ",
											choice.getShortName()+" / "+oChoice.getShortName()+", % Change, "+label, true);
									mapVsChoiceTable.addColumn("![Difference Map]("+resourcesDir.getName()+"/"+map.getName()+")");
								}
							}
							
							table.finalizeLine();
							mapVsChoiceTable.finalizeLine();
							
							// now maps
							
							mapTable.initNewLine().addColumn("**"+choice.getShortName()+"**");
							
							// pDiff
							GriddedGeoDataSet pDiff = buildPDiff(mean, choiceMap);
							MapPlot pDiffMap = mapper.buildMapPlot(resourcesDir, prefix+"_"+choice.getFilePrefix()+"_pDiff",
									pDiff, pDiffCPT, TITLES ? choice.getShortName()+" Comparison" : " ",
									choice.getShortName()+" / Mean, % Change, "+label, true);
							branchPDiffPlots.add(pDiffMap);
							File map = new File(resourcesDir, pDiffMap.prefix+".png");
							mapTable.addColumn("![Percent Difference Map]("+resourcesDir.getName()+"/"+map.getName()+")");
							
							// regular diff
							GriddedGeoDataSet diff = new GriddedGeoDataSet(region, false);
							for (int i=0; i<diff.size(); i++)
								diff.set(i, choiceMap.get(i) - mean.get(i));
							MapPlot diffMap = mapper.buildMapPlot(resourcesDir, prefix+"_"+choice.getFilePrefix()+"_diff",
									diff, diffCPT, TITLES ? choice.getShortName()+" Comparison" : " ",
									choice.getShortName()+" - Mean, "+label, false);
							branchDiffPlots.add(diffMap);
							map = new File(resourcesDir, diffMap.prefix+".png");
							mapTable.addColumn("![Difference Map]("+resourcesDir.getName()+"/"+map.getName()+")");
							
							// percentile
							GriddedGeoDataSet percentile = calcPercentileWithinDist(mapNCDFs, choiceMap);
							map = submitMapFuture(mapper, exec, futures, resourcesDir, prefix+"_"+choice.getFilePrefix()+"_percentile",
									percentile, percentileCPT, TITLES ? choice.getShortName()+" Comparison" : " ",
									choice.getShortName()+" %-ile, "+label);
							mapTable.addColumn("![Percentile Map]("+resourcesDir.getName()+"/"+map.getName()+")");
							
							// percentile without
							// percentile without
							List<GriddedGeoDataSet> mapsWithout = new ArrayList<>();
							List<Double> weightsWithout = new ArrayList<>();
							for (int i=0; i<branches.size(); i++) {
								LogicTreeBranch<?> branch = branches.get(i);
								if (!branch.hasValue(choice)) {
									mapsWithout.add(maps[i]);
									weightsWithout.add(weights.get(i));
								}
							}
							Preconditions.checkState(!mapsWithout.isEmpty());
							LightFixedXFunc[] mapsWithoutNCDFs = buildNormCDFs(mapsWithout, weightsWithout);
							GriddedGeoDataSet percentileWithout = calcPercentileWithinDist(mapsWithoutNCDFs, choiceMap);
							map = submitMapFuture(mapper, exec, futures, resourcesDir, prefix+"_"+choice.getFilePrefix()+"_percentile_without",
									percentileWithout, percentileCPT, TITLES ? choice.getShortName()+" Comparison" : " ",
									choice.getShortName()+" %-ile, "+label);
							mapTable.addColumn("![Percentile Map]("+resourcesDir.getName()+"/"+map.getName()+")");
							
							mapTable.finalizeLine();
						}
						lines.add("");
						lines.add("The table below gives summary statistics for the spatial average difference and average "
								+ "absolute difference of hazard between mean hazard maps for each individual branch "
								+ "choices. In other words, it gives the expected difference (or absolute "
								+ "difference) between two models if you picked a location at random. Values are listed "
								+ "between each pair of branch choices, and also between that choice and the overall "
								+ "mean map in the first column.");
						lines.add("");
						lines.add("The overall average absolute difference between the map for any choice to each other "
								+ "choice, a decent summary measure of how much hazard varies due to this branch choice, "
								+ "is: **"+twoDigits.format(runningAbsDiffAvg.getAverage())+"%**");
						System.out.println("\tOverall MAD: "+twoDigits.format(runningAbsDiffAvg.getAverage())+" %");
						lines.add("");
						lines.addAll(table.build());
						lines.add("");
						lines.add("The map table below shows how the mean map for each branch choice compares to the overall "
								+ "mean map, expressed as % change (first column) and difference (second column). The "
								+ "third column, 'Choice Percentile in Full Dist', shows at what percentile the map for "
								+ "that branch choice lies within the full distribution, and the fourth column, 'Choice "
								+ "Percentile in Dist Without', shows the same but for the distribution of all other "
								+ "branches (without this choice included).");
						lines.add("");
						lines.add("Note that these percentile comparisons can be outlier dominated, in which case even "
								+ "if a choice is near the overall mean hazard it may still lie far from the 50th "
								+ "percentile (see 'Mean Map Percentile' above to better understand outlier dominated "
								+ "regions).");
						lines.add("");
						lines.addAll(mapTable.build());
						lines.add("");
						lines.add("The table below gives % change maps between each option, head-to-head.");
						lines.add("");
						lines.addAll(mapVsChoiceTable.build());
						lines.add("");
					}
				}
				
				if (!branchLevels.isEmpty()) {
					String combPrefix = prefix+"_branches_combined";
					writeCombinedBranchMap(resourcesDir, combPrefix, name+", Logic Tree Comparison",
							meanMapPlot, branchLevels, branchLevelValues,
							branchLevelPDiffPlots, "Branch Choice / Mean, % Change",
							branchLevelDiffPlots, "Branch Choice - Mean (g)");
					combPrefix = prefix+"_branches_combined_pDiff";
					writeCombinedBranchMap(resourcesDir, combPrefix, name+", Logic Tree Comparison",
							meanMapPlot, branchLevels, branchLevelValues, branchLevelPDiffPlots,
							"Branch Choice / Mean, % Change");
					table = MarkdownUtils.tableBuilder();
					table.addLine("Combined Summary Maps");
					table.addLine("![Combined Map]("+resourcesDir.getName()+"/"+combPrefix+".png)");
					combPrefix = prefix+"_branches_combined_diff";
					writeCombinedBranchMap(resourcesDir, combPrefix, name+", Logic Tree Comparison",
							meanMapPlot, branchLevels, branchLevelValues, branchLevelDiffPlots,
							"Branch Choice - Mean (g)");
					table.addLine("![Combined Map]("+resourcesDir.getName()+"/"+combPrefix+".png)");
					lines.addAll(combinedMapIndex, table.build());
				}
			}
		}
		
		System.out.println("Waiting on "+futures.size()+" map futures....");
		for (Future<?> future : futures) {
			try {
				future.get();
			} catch (InterruptedException | ExecutionException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
		}
		System.out.println("DONE with maps");
		
		// add TOC
		lines.addAll(tocIndex, MarkdownUtils.buildTOC(lines, 2, 4));
		lines.add(tocIndex, "## Table Of Contents");
		
		// write markdown
		MarkdownUtils.writeReadmeAndHTML(lines, outputDir);
	}
	
	private static File submitMapFuture(SolHazardMapCalc mapper, ExecutorService exec, List<Future<?>> futures,
			File outputDir, String prefix, GriddedGeoDataSet xyz, CPT cpt,
			String title, String zLabel) {
		return submitMapFuture(mapper, exec, futures, outputDir, prefix, xyz, cpt, title, zLabel, false);
	}
	
	private static File submitMapFuture(SolHazardMapCalc mapper, ExecutorService exec, List<Future<?>> futures,
			File outputDir, String prefix, GriddedGeoDataSet xyz, CPT cpt,
			String title, String zLabel, boolean diffStats) {
		File ret = new File(outputDir, prefix+".png");
		
		futures.add(exec.submit(new Runnable() {
			
			@Override
			public void run() {
				try {
					mapper.plotMap(outputDir, prefix, xyz, cpt, title, zLabel, diffStats);
				} catch (IOException e) {
					throw ExceptionUtils.asRuntimeException(e);
				}
			}
		}));
		
		return ret;
	}
	
	private void writeHazardCSV(File outputFile, GriddedGeoDataSet mean, GriddedGeoDataSet median,
			GriddedGeoDataSet min, GriddedGeoDataSet max) throws IOException {
		CSVFile<String> csv = new CSVFile<>(true);
		
		csv.addLine("Location Index", "Latitutde", "Longitude", "Weighted Mean", "Weighted Median", "Min", "Max");
		
		GriddedRegion reg = mean.getRegion();
		for (int i=0; i<reg.getNodeCount(); i++) {
			Location loc = reg.getLocation(i);
			
			csv.addLine(i+"", (float)loc.getLatitude()+"", (float)loc.getLongitude()+"", mean.get(i)+"",
					median.get(i)+"", min.get(i)+"", max.get(i)+"");
		}
		
		csv.writeToFile(outputFile);
	}
	
	private void writeChoiceHazardCSV(File outputFile, GriddedGeoDataSet mean, List<LogicTreeNode> choices,
			Map<LogicTreeNode, GriddedGeoDataSet> choiceMeans) throws IOException {
		CSVFile<String> csv = new CSVFile<>(true);
		
		List<String> header = new ArrayList<>();
		header.add("Location Index");
		header.add("Latitutde");
		header.add("Longitude");
		header.add("Weighted Mean");
		for (LogicTreeNode choice : choices)
			header.add(choice.getShortName());
		csv.addLine(header);
		
		GriddedRegion reg = mean.getRegion();
		for (int i=0; i<reg.getNodeCount(); i++) {
			Location loc = reg.getLocation(i);
			
			List<String> line = new ArrayList<>();
			line.add(i+"");
			line.add((float)loc.getLatitude()+"");
			line.add((float)loc.getLongitude()+"");
			line.add(mean.get(i)+"");
			for (LogicTreeNode choice : choices)
				line.add(choiceMeans.get(choice).get(i)+"");
			
			csv.addLine(line);
		}
		
		csv.writeToFile(outputFile);
	}
	
	private static String mapPDiffStr(GriddedGeoDataSet map, GriddedGeoDataSet ref,
			MinMaxAveTracker runningDiffAvg, MinMaxAveTracker runningAbsDiffAvg) {
		double mean = 0d;
		double meanAbs = 0d;
		int numFinite = 0;;
		for (int i=0; i<map.size(); i++) {
			double z1 = map.get(i);
			double z2 = ref.get(i);
			double pDiff = 100d*(z1-z2)/z2;
			if (z1 == z2 && z1 > 0)
				pDiff = 0d;
			if (Double.isFinite(pDiff)) {
				mean += pDiff;
				meanAbs += Math.abs(pDiff);
				if (runningDiffAvg != null)
					runningDiffAvg.addValue(pDiff);
				if (runningAbsDiffAvg != null)
					runningAbsDiffAvg.addValue(Math.abs(pDiff));
				numFinite++;
			}
		}
		mean /= (double)numFinite;
		meanAbs /= (double)numFinite;
		
		return "Mean: "+twoDigits.format(mean)+"%, Mean Abs: "+twoDigits.format(meanAbs)+"%";
	}
	
	private static final DecimalFormat twoDigits = new DecimalFormat("0.00");
	
	private static final Comparator<LogicTreeNode> nodeNameCompare = new Comparator<LogicTreeNode>() {

		@Override
		public int compare(LogicTreeNode o1, LogicTreeNode o2) {
			return o1.getShortName().compareTo(o2.getShortName());
		}
	};
	
	private static GriddedGeoDataSet log10(GriddedGeoDataSet map) {
		map = map.copy();
		map.log10();
		return map;
	}
	
	private void addDiffInclExtremesLines(GriddedGeoDataSet primary, String name, GriddedGeoDataSet min,
			GriddedGeoDataSet max, GriddedGeoDataSet comparison, String compName, String prefix, File resourcesDir,
			TableBuilder table, String type, String label, boolean difference) throws IOException {
		
		String diffLabel, diffPrefix;
		CPT cpt;
		GriddedGeoDataSet diff, diffFromRange;
		if (difference) {
			diffLabel = "Difference";
			diffPrefix = "diff";
			cpt = diffCPT;
			// regular diff
			diff = new GriddedGeoDataSet(primary.getRegion(), false);
			diffFromRange = new GriddedGeoDataSet(primary.getRegion(), false);
			for (int i=0; i<diff.size(); i++) {
				double val = primary.get(i);
				double compVal = comparison.get(i);
				diff.set(i, val - compVal);
				double minVal = min.get(i);
				double maxVal = max.get(i);
				double rangeDiff;
				if (compVal >= minVal && compVal <= maxVal)
					rangeDiff = 0d;
				else if (compVal < minVal)
					rangeDiff = compVal-minVal;
				else
					rangeDiff = compVal-maxVal;
				diffFromRange.set(i, rangeDiff);
			}
		} else {
			diffLabel = "% Change";
			diffPrefix = "pDiff";
			cpt = pDiffCPT;
			diff = buildPDiff(comparison, primary);
			diffFromRange = buildPDiffFromRange(min, max, comparison);
		}
		table.addLine(MarkdownUtils.boldCentered(type+" "+diffLabel),
				MarkdownUtils.boldCentered("Comparison "+type+" "+diffLabel+" From Extremes"));
		
		table.initNewLine();
		
		String op = difference ? "-" : "/";
		
		File map = submitMapFuture(mapper, exec, futures, resourcesDir, prefix+"_comp_"+diffPrefix, diff, cpt, TITLES ? name+" vs "+compName : " ",
				(TITLES ? "Primary "+op+" Comparison, " : "")+diffLabel+", "+label, true);
		table.addColumn("![Difference Map]("+resourcesDir.getName()+"/"+map.getName()+")");
		map = submitMapFuture(mapper, exec, futures, resourcesDir, prefix+"_comp_"+diffPrefix+"_range", diffFromRange, cpt.reverse(), TITLES ? name+" vs "+compName : " ",
				"Comparison "+op+" Extremes, "+diffLabel+", "+label, true);
		table.addColumn("![Range Difference Map]("+resourcesDir.getName()+"/"+map.getName()+")");
		table.finalizeLine();
	}
	
	private void addMapCompDiffLines(GriddedGeoDataSet primary, String name, GriddedGeoDataSet comparison,
			String compName, String prefix, File resourcesDir, TableBuilder table, String type, String label,
			CPT cpt, boolean difference, CPT diffCPT) throws IOException {

		GriddedGeoDataSet diff;
		String diffLabel;
		if (difference) {
			diff = new GriddedGeoDataSet(primary.getRegion(), false);
			for (int i=0; i<diff.size(); i++)
				diff.set(i, primary.get(i)-comparison.get(i));
			diffLabel = "Primary - Comparison, "+label;
		} else {
			diff = buildPDiff(comparison, primary);
			primary = log10(primary);
			comparison = log10(comparison);
			diffLabel = "Primary / Comparison, % Change, "+label;
		}
		
		table.addLine(MarkdownUtils.boldCentered("Primary "+type), MarkdownUtils.boldCentered("Comparison "+type),
				MarkdownUtils.boldCentered(difference ? "Difference" : "% Change"));
		
		table.initNewLine();
		File map = submitMapFuture(mapper, exec, futures, resourcesDir, prefix, primary, cpt, name, (difference ? "" : "Log10 ")+label);
		table.addColumn("!["+type+"]("+resourcesDir.getName()+"/"+map.getName()+")");
		map = submitMapFuture(mapper, exec, futures, resourcesDir, prefix+"_comp", comparison, cpt, compName, (difference ? "" : "Log10 ")+label);
		table.addColumn("!["+type+"]("+resourcesDir.getName()+"/"+map.getName()+")");
		map = submitMapFuture(mapper, exec, futures, resourcesDir, prefix+"_comp_pDiff", diff, diffCPT, name+" vs "+compName, diffLabel, !difference);
		table.addColumn("![Difference Map]("+resourcesDir.getName()+"/"+map.getName()+")");
		table.finalizeLine();
	}
	
	public void writeCombinedBranchMap(File resourcesDir, String prefix, String fullTitle, MapPlot meanMap,
			List<LogicTreeLevel<?>> branchLevels, List<List<LogicTreeNode>> branchLevelValues,
			List<List<MapPlot>> branchLevelPlots, String label) throws IOException {
		writeCombinedBranchMap(resourcesDir, prefix, fullTitle, meanMap, branchLevels,
				branchLevelValues, branchLevelPlots, label, null, null);
	}
	
	public void writeCombinedBranchMap(File resourcesDir, String prefix, String fullTitle, MapPlot meanMap,
			List<LogicTreeLevel<?>> branchLevels, List<List<LogicTreeNode>> branchLevelValues,
			List<List<MapPlot>> branchLevelPlots1, String label1, List<List<MapPlot>> branchLevelPlots2, String label2)
					throws IOException {
		// constants
		int primaryWidth = 1200;
		int primaryWidthDelta = 5;
		int secondaryWidth = primaryWidth/primaryWidthDelta;
		int padding = 10;
		int heightLevelName = 40;
		int heightChoice = 25;
		
		double secondaryScale = (double)secondaryWidth/(double)primaryWidth;
		
		int maxNumChoices = 0;
		for (List<MapPlot> plots : branchLevelPlots1)
			maxNumChoices = Integer.max(maxNumChoices, plots.size());
		if (branchLevelPlots2 != null)
			Preconditions.checkState(branchLevelPlots2.size() == branchLevelPlots1.size());
		Preconditions.checkState(maxNumChoices > 0);
		
		PlotPreferences primaryPrefs = PlotUtils.getDefaultFigurePrefs();
		primaryPrefs.scaleFontSizes(1.25d);
		PlotPreferences secondaryPrefs = primaryPrefs.clone();
		secondaryPrefs.scaleFontSizes(secondaryScale);
		
		Font fontLevelName = new Font(Font.SANS_SERIF, Font.BOLD, primaryPrefs.getPlotLabelFontSize());
		Font fontChoiceName = new Font(Font.SANS_SERIF, Font.BOLD, primaryPrefs.getLegendFontSize());
		
		// prepare primary figure and determine aspect ratio
		HeadlessGraphPanel primaryGP = new HeadlessGraphPanel(primaryPrefs);
		XYZPlotSpec primarySpec = meanMap.spec;
		primarySpec.setCPTTickUnit(0.5d);
		primarySpec.setTitle(fullTitle);
		CPT diffCPT = branchLevelPlots1.get(0).get(0).spec.getCPT();
		PlotPreferences prefs = primaryGP.getPlotPrefs();
		PaintScaleLegend diffSubtitle = GraphPanel.getLegendForCPT(diffCPT, label1,
				prefs.getAxisLabelFontSize(), prefs.getTickLabelFontSize(),
				-1, RectangleEdge.BOTTOM);
		if (branchLevelPlots2 != null) {
			CPT diffCPT2 = branchLevelPlots2.get(0).get(0).spec.getCPT();
			PaintScaleLegend diffSubtitle2 = GraphPanel.getLegendForCPT(diffCPT2, label2,
					prefs.getAxisLabelFontSize(), prefs.getTickLabelFontSize(),
					-1, RectangleEdge.BOTTOM);
			primarySpec.addSubtitle(diffSubtitle2);
		}
		primarySpec.addSubtitle(diffSubtitle);
		primaryGP.drawGraphPanel(primarySpec, false, false, meanMap.xRnage, meanMap.yRange);
		PlotUtils.setXTick(primaryGP, meanMap.xTick);
		PlotUtils.setYTick(primaryGP, meanMap.yTick);
		int primaryHeight = PlotUtils.calcHeight(primaryGP, primaryWidth, true);
		
		// figure out secondary height
		MapPlot secondaryPlot = branchLevelPlots1.get(0).get(0);
		HeadlessGraphPanel secondaryGP = drawSimplifiedSecondaryPlot(secondaryPlot, secondaryPrefs, 1d);
		int secondaryHeight = PlotUtils.calcHeight(secondaryGP, secondaryWidth, true);
		
		int secondaryHeightEach = heightLevelName + heightChoice + secondaryHeight;
		if (branchLevelPlots2 != null)
			secondaryHeightEach += secondaryHeight;
		
		int totalHeight = Integer.max(primaryHeight, padding*2 + branchLevels.size()*secondaryHeightEach);
		
		// TODO maybe revisit this
//		if (totalHeight > primaryHeight) {
//			// see if we can put some under the map
//			int heightMinusOne = totalHeight - secondaryHeightEach;
//			if (heightMinusOne < primaryHeight) {
//				// we can
//				// figure out which is the first one on the new row
//				int firstRowIndex = -1;
//				for (int i=1; i<branchLevelValues.size(); i++) {
//					int yStart = padding + secondaryHeightEach*i;
//					if (yStart > primaryHeight) {
//						firstRowIndex = i;
//						break;
//					}
//				}
//				Preconditions.checkState(firstRowIndex > 0);
//				int maxNumAfter = 0;
//				for (int i=firstRowIndex; i<branchLevelValues.size(); i+=2)
//					maxNumAfter = Integer.max(maxNumAfter, branchLevelValues.get(i).size());
//				System.out.println("Wrapping starting with "+firstRowIndex+", maxNumAfter="+maxNumAfter);
//				if (maxNumAfter > primaryWidthDelta) {
//					// need more room
//					primaryWidth = secondaryWidth*maxNumAfter;
//					primaryGP.drawGraphPanel(primarySpec, false, false, meanMap.xRnage, meanMap.yRange);
//					PlotUtils.setXTick(primaryGP, meanMap.xTick);
//					PlotUtils.setYTick(primaryGP, meanMap.yTick);
//					primaryHeight = PlotUtils.calcHeight(primaryGP, primaryWidth, true);
//					System.out.println("Mod primary size: "+primaryWidth+"x"+primaryHeight);
//				}
//				
//			}
//		}
		
		int totalWidth = padding*2 + primaryWidth + maxNumChoices*secondaryWidth;
		
		System.out.println("Building combined map with dimensions: primary="+primaryWidth+"x"+primaryHeight
				+", secondary="+secondaryWidth+"x"+secondaryHeight+", total="+totalWidth+"x"+totalHeight);
		
		JPanel panel = new JPanel();
		panel.setLayout(null);
		panel.setBackground(Color.WHITE);
		panel.setSize(totalWidth, totalHeight);
		
		List<Consumer<Graphics2D>> redraws = new ArrayList<>();
		
		// X/Y reference frame is top left
//		int primaryTopY = (totalHeight - primaryHeight)/2;
		int primaryTopY = 0; // place it on top
		redraws.add(placeGraph(panel, 0, primaryTopY, primaryWidth, primaryHeight, primaryGP.getChartPanel()));
		// clear the custom subtitle we added
		primarySpec.setSubtitles(null);
		
		int secondaryStartX = padding + primaryWidth;
		int levelLabelX = secondaryStartX;
		int levelLabelWidth = totalWidth - secondaryStartX - padding;
		int y = 0;
		for (int l=0; l<branchLevels.size(); l++) {
			// TODO big label
			// y is currently TOP, x is secondaryLabelX
			int levelLabelY = y; // top
			LogicTreeLevel<?> level = branchLevels.get(l);
			placeLabel(panel, levelLabelX, levelLabelY, levelLabelWidth, heightLevelName, level.getName(), fontLevelName);
			
			int choiceLabelY = y + heightLevelName;

			int choiceMapY = y + heightLevelName + heightChoice; // top
			int choiceMapY2 = y + heightLevelName + heightChoice + secondaryHeight; // top
			
			List<LogicTreeNode> myChoices = branchLevelValues.get(l);
			List<MapPlot> myPlots = branchLevelPlots1.get(l);
			int x = secondaryStartX;
			for (int i=0; i<myChoices.size(); i++) {
				secondaryPlot = myPlots.get(i);
				secondaryGP = drawSimplifiedSecondaryPlot(secondaryPlot, secondaryPrefs, secondaryScale);
				
				redraws.add(placeGraph(panel, x, choiceMapY, secondaryWidth, secondaryHeight, secondaryGP.getChartPanel()));
				
				LogicTreeNode choice = branchLevelValues.get(l).get(i);
				placeLabel(panel, x, choiceLabelY, secondaryWidth, heightChoice, choice.getShortName(), fontChoiceName);
				
				x += secondaryWidth;
			}
			
			if (branchLevelPlots2 != null) {
				x = secondaryStartX;
				List<MapPlot> myPlots2 = branchLevelPlots2.get(l);
				for (int i=0; i<myChoices.size(); i++) {
					secondaryPlot = myPlots2.get(i);
					secondaryGP = drawSimplifiedSecondaryPlot(secondaryPlot, secondaryPrefs, secondaryScale);
					
					redraws.add(placeGraph(panel, x, choiceMapY2, secondaryWidth, secondaryHeight, secondaryGP.getChartPanel()));
					
					x += secondaryWidth;
				}
			}
			
			y += secondaryHeightEach;
		}
		
		// write PNG
		BufferedImage bi = new BufferedImage(totalWidth, totalHeight, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2d = bi.createGraphics();
		g2d.addRenderingHints(new RenderingHints(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON));
		panel.paint(bi.getGraphics());
		ImageIO.write(bi, "png", new File(resourcesDir, prefix+".png"));
		
		// write PDF
		// step 1
		Document metadataDocument = new Document(new com.itextpdf.text.Rectangle(
				totalWidth, totalHeight));
		metadataDocument.addAuthor("OpenSHA");
		metadataDocument.addCreationDate();
//		HeaderFooter footer = new HeaderFooter(new Phrase("Powered by OpenSHA"), true);
//		metadataDocument.setFooter(footer);
		try {
			// step 2
			PdfWriter writer;

			writer = PdfWriter.getInstance(metadataDocument,
					new FileOutputStream(new File(resourcesDir, prefix+".pdf")));
			// step 3
			metadataDocument.open();
			// step 4
			PdfContentByte cb = writer.getDirectContent();
			PdfTemplate tp = cb.createTemplate(totalWidth, totalHeight);

			FontMapper fontMapper = new PDF_UTF8_FontMapper();
			g2d = new PdfGraphics2D(tp, totalWidth, totalHeight, fontMapper);
			
//			Graphics2D g2d = tp.createGraphics(width, height,
//					new DefaultFontMapper());
			panel.paint(g2d);
			// repaint all chart panels
			for (Consumer<Graphics2D> redraw : redraws)
				redraw.accept(g2d);
//			Rectangle2D r2d = new Rectangle2D.Double(0, 0, totalWidth, totalHeight);
//			panel.draw(g2d, r2d);
			g2d.dispose();
			cb.addTemplate(tp, 0, 0);
		}
		catch (DocumentException de) {
			de.printStackTrace();
		}
		// step 5
		metadataDocument.close();
	}
	
	private HeadlessGraphPanel drawSimplifiedSecondaryPlot(MapPlot secondaryPlot, PlotPreferences secondaryPrefs,
			double lineScalar) {
		secondaryPrefs.setAxisLabelFontSize(2);
		HeadlessGraphPanel secondaryGP = new HeadlessGraphPanel(secondaryPrefs);
		XYZPlotSpec secondarySpec = secondaryPlot.spec;
		List<PlotCurveCharacterstics> origChars = null;
		if (lineScalar != 1d && secondarySpec.getChars() != null) {
			origChars = secondarySpec.getChars();
			List<PlotCurveCharacterstics> modChars = new ArrayList<>();
			for (PlotCurveCharacterstics pChar : origChars)
				modChars.add(new PlotCurveCharacterstics(pChar.getLineType(), (float)(pChar.getLineWidth()*lineScalar),
						pChar.getSymbol(), (float)(pChar.getSymbolWidth()*lineScalar), pChar.getColor()));
			secondarySpec.setChars(modChars);
		}
		secondarySpec.setCPTVisible(false);
		secondarySpec.setTitle(null);
		secondarySpec.setPlotAnnotations(null);
		secondaryGP.drawGraphPanel(secondarySpec, false, false, secondaryPlot.xRnage, secondaryPlot.yRange);
		secondaryGP.getXAxis().setTickLabelsVisible(false);
		secondaryGP.getXAxis().setLabel(" ");
		secondaryGP.getYAxis().setTickLabelsVisible(false);
		secondaryGP.getYAxis().setLabel(null);
		PlotUtils.setXTick(secondaryGP, secondaryPlot.xTick);
		PlotUtils.setYTick(secondaryGP, secondaryPlot.yTick);
		if (origChars != null)
			secondarySpec.setChars(origChars);
		return secondaryGP;
	}
	
	private Consumer<Graphics2D> placeGraph(JPanel panel, int xTop, int yTop, int width, int height, ChartPanel chart) {
		chart.setBorder(null);
		// this forces it to actually render
		chart.getChart().createBufferedImage(width, height, new ChartRenderingInfo());
		chart.setSize(width, height);
		panel.add(chart);
		chart.setLocation(xTop, yTop);
		return new Consumer<Graphics2D>() {
			@Override
			public void accept(Graphics2D t) {
				chart.getChart().draw(t, new Rectangle2D.Double(xTop, yTop, width, height));
			}
		};
	}
	
	private void placeLabel(JPanel panel, int xTop, int yTop, int width, int height, String text, Font font) {
		JLabel label = new JLabel(text, JLabel.CENTER);
		label.setFont(font);
		label.setSize(width, height);
		panel.add(label);
		label.setLocation(xTop, yTop);
	}

}
