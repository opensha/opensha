package scratch.UCERF3.analysis;

import java.awt.Color;
import java.awt.Font;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.dom4j.DocumentException;
import org.jfree.chart.annotations.XYAnnotation;
import org.jfree.chart.annotations.XYLineAnnotation;
import org.jfree.chart.annotations.XYTextAnnotation;
import org.jfree.ui.TextAnchor;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotElement;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotMultiDataLayer;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.gui.plot.PlotSymbol;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.commons.gui.plot.GraphWindow;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.inversion.CommandLineInversionRunner;
import scratch.UCERF3.inversion.InversionFaultSystemRupSet;
import scratch.UCERF3.inversion.InversionFaultSystemSolution;
import scratch.UCERF3.inversion.UCERF2_ComparisonSolutionFetcher;
import scratch.UCERF3.utils.FaultSystemIO;
import scratch.UCERF3.utils.UCERF3_DataUtils;

public class FaultSpecificSegmentationPlotGen {
	
	public static void plotSegmentation(List<Integer> parentSects, InversionFaultSystemSolution sol, double minMag, boolean endsOnly) {
		PlotSpec spec = buildSegmentationPlot(parentSects, sol, minMag, endsOnly, null);
		
		GraphWindow gw = new GraphWindow(spec.getPlotElems(), spec.getTitle(), spec.getChars(), false);
		gw.setX_AxisLabel(spec.getXAxisLabel());
		gw.setY_AxisLabel(spec.getYAxisLabel());
		gw.getGraphWidget().getGraphPanel().setxAxisInverted(true);
		gw.setVisible(true);
	}
	
	public static HeadlessGraphPanel getSegmentationHeadlessGP(List<Integer> parentSects, FaultSystemSolution sol,
			double minMag, boolean endsOnly) throws IOException {
		return getSegmentationHeadlessGP(parentSects, sol, minMag, endsOnly, null);
	}
	
	public static HeadlessGraphPanel getSegmentationHeadlessGP(List<Integer> parentSects, FaultSystemSolution sol,
			double minMag, boolean endsOnly, CSVFile<String> csv) throws IOException {
		PlotSpec spec = buildSegmentationPlot(parentSects, sol, minMag, endsOnly, csv);
		
		HeadlessGraphPanel gp = new HeadlessGraphPanel();
		CommandLineInversionRunner.setFontSizes(gp);
		
		gp.setxAxisInverted(true);
		
		gp.drawGraphPanel(spec);
		
		return gp;
	}
	
	private static PlotSpec buildSegmentationPlot(List<Integer> parentSects, FaultSystemSolution sol, double minMag, boolean endsOnly,
			CSVFile<String> csv) {
		FaultSystemRupSet rupSet = sol.getRupSet();
		// first assemble subsections by parent
		Map<Integer, List<FaultSectionPrefData>> subSectsByParent = Maps.newHashMap();
		int prevParentID = -2;
		List<FaultSectionPrefData> curSects = null;
		for (int sectIndex=0; sectIndex<rupSet.getNumSections(); sectIndex++) {
			FaultSectionPrefData sect = rupSet.getFaultSectionData(sectIndex);
			int parent = sect.getParentSectionId();
			if (parent != prevParentID) {
				prevParentID = parent;
				curSects = Lists.newArrayList();
				subSectsByParent.put(parent, curSects);
			}
			curSects.add(sect);
		}
		
		// now look for places where the connection point is elsewhere on the parent section (other than the end)
		for (Integer parentID : subSectsByParent.keySet()) {
			List<FaultSectionPrefData> subSects = subSectsByParent.get(parentID);
			int num = subSects.size();
			if (num % 2 > 0)
				num--;
			int numToCheck = num/2 - 1;
			if (numToCheck > 0) {
				// does the last sub section connect with another parent?
				int checkNum = subSects.size();
				int connectionIndex = -1;
				for (int i=0; i<numToCheck; i++) {
					if (hasConnectionOnOtherParent(parentSects, subSects.get(i), sol)) {
						connectionIndex = i;
						break;
					}
				}
				if (connectionIndex > 0) {
					subSects = subSects.subList(connectionIndex, subSects.size());
					checkNum -= connectionIndex;
					Preconditions.checkState(checkNum == subSects.size());
					System.out.println("Trimming off "+connectionIndex+" sub sects for parent "+parentID);
				}
				// now the other way
				connectionIndex = -1;
				for (int i=0; i<numToCheck; i++) {
					if (hasConnectionOnOtherParent(parentSects, subSects.get(subSects.size()-1-i), sol)) {
						connectionIndex = i;
						break;
					}
				}
				if (connectionIndex > 0) {
					subSects = subSects.subList(0, subSects.size()-connectionIndex);
					checkNum -= connectionIndex;
					Preconditions.checkState(checkNum == subSects.size(), checkNum+" != "+subSects.size());
					System.out.println("Trimming off "+connectionIndex+" sub sects for parent "+parentID);
				}
				subSectsByParent.put(parentID, subSects);
			}
		}
		
		// mapping from stopping points to subsection IDs
		Map<Location, List<Integer>> stoppingPoints = Maps.newHashMap();
		List<Location> parentSectEnds = Lists.newArrayList();
		Map<Location, String> parentSectNamesMap = Maps.newHashMap();
		
		// stopping point tolerance
		double toleranceKM = 3;
		boolean normalize = true;
		
		// now we find all the the stopping points (location inbetween two subsections)
		for (Integer parent : parentSects) {
			List<FaultSectionPrefData> sects = subSectsByParent.get(parent);
			
			if (sects == null)
				continue;
			
			Location parentStart = sects.get(0).getFaultTrace().first();
			Location parentEnd = sects.get(sects.size()-1).getFaultTrace().last();
			Location midPt = new Location(0.5*(parentStart.getLatitude()+parentEnd.getLatitude()),
					0.5*(parentStart.getLongitude()+parentEnd.getLongitude()));
			parentSectNamesMap.put(midPt, sects.get(0).getParentSectionName());
			
			List<Location> sectStoppingPoints = Lists.newArrayList();
			for (int i=0; i<sects.size(); i++) {
				FaultSectionPrefData sect = sects.get(i);
				FaultTrace trace = sect.getFaultTrace();
				sectStoppingPoints.add(trace.get(0));
				
				if (i == sects.size()-1)
					sectStoppingPoints.add(trace.get(trace.size()-1));
			}
			
			for (int i=0; i<sectStoppingPoints.size(); i++) {
				if (endsOnly && i > 0 && i < sectStoppingPoints.size()-1)
					continue;
				Location loc = sectStoppingPoints.get(i);
				Location testLoc = searchForMatch(loc, stoppingPoints.keySet(), toleranceKM);
				Location parentTestLoc;
				List<Integer> startSects;
				if (testLoc == null) {
					startSects = Lists.newArrayList();
					stoppingPoints.put(loc, startSects);
					parentTestLoc = searchForMatch(loc, stoppingPoints.keySet(), 5);
					if (parentTestLoc == null)
						parentTestLoc = loc;
				} else {
					startSects = stoppingPoints.get(testLoc);
					parentTestLoc = testLoc;
				}
				if (i == 0) {
					// this is the first loc, just add the first section
					startSects.add(sects.get(0).getSectionId());
					if (testLoc == null)
						parentSectEnds.add(loc);
				} else if (i == sectStoppingPoints.size()-1) {
					// this is the last loc, just add the last section
					startSects.add(sects.get(sects.size()-1).getSectionId());
					if (testLoc == null)
						parentSectEnds.add(loc);
				} else {
					// this is in the middle, add before and after
					startSects.add(sects.get(i-1).getSectionId());
					startSects.add(sects.get(i).getSectionId());
				}
			}
		}
		
		ArbitrarilyDiscretizedFunc stopFunc = new ArbitrarilyDiscretizedFunc();
		stopFunc.setName("Fract of rate that stops at this point");
		ArbitrarilyDiscretizedFunc continueFunc = new ArbitrarilyDiscretizedFunc();
		continueFunc.setName("Fract of rate that continues through this point");
		
		String info = null;
		
		List<Location> stoppingKeysSorted = Lists.newArrayList();
		stoppingKeysSorted.addAll(stoppingPoints.keySet());
		// sort by latitude decending
		Collections.sort(stoppingKeysSorted, new Comparator<Location>() {

			@Override
			public int compare(Location o1, Location o2) {
				return -Double.compare(o1.getLatitude(), o2.getLatitude());
			}
		});
		
		HashMap<Integer, List<Integer>> rupStopCountMap = Maps.newHashMap();
		HashMap<Integer, List<Location>> rupStopLocationsMap = Maps.newHashMap();
		
		if (csv != null) {
			Preconditions.checkState(!csv.isStrictRowSizes(), "Strict row sizes not supported");
			csv.addLine("Latitude", "Longitude", "Stop Rate (/yr)", "Continue Rate (/yr)",
					"Normalized Stop Rate", "Normalized Continue Rate", "Sections");
		}
		
		for (Location loc : stoppingKeysSorted) {
			List<Integer> sects = stoppingPoints.get(loc);
			
			double stopRate = 0;
			double continueRate = 0;
			
//			// if a rup includes only 1 section at this stopping point, then it stops there
//			// otherwise it continues through
//			
//			// list of all ruptures involving this stopping point
//			HashSet<Integer> allRups = new HashSet<Integer>();
//			// list of all rups where the first of last section of the rup is at the stopping point
//			// these are possible stops, but could be just next to stopping points
//			HashSet<Integer> possibleStops = new HashSet<Integer>();
//			// this is the same as above but for each section at this stopping point
//			List<HashSet<Integer>> possibleStopsPerSect = Lists.newArrayList();
//			
//			for (int sectIndex : sects) {
//				HashSet<Integer> sectPossibleStops = new HashSet<Integer>();
//				possibleStopsPerSect.add(sectPossibleStops);
//				for (int rupIndex : sol.getRupturesForSection(sectIndex)) {
//					if (sol.getMagForRup(rupIndex) < minMag)
//						continue;
//					
//					if (!allRups.contains(rupIndex))
//						allRups.add(rupIndex);
//					
//					List<Integer> sectsForRup = sol.getSectionsIndicesForRup(rupIndex);
//					boolean stoppingPoint = false;
//					
//					if (sectIndex == sectsForRup.get(0) && !sects.contains(sectsForRup.get(1)))
//						stoppingPoint = true;
//					
//					if (sectIndex == sectsForRup.get(0) || sectIndex == sectsForRup.get(sectsForRup.size()-1)) {
//						if (!possibleStops.contains(rupIndex))
//							possibleStops.add(rupIndex);
//						sectPossibleStops.add(rupIndex);
//					}
//				}
//			}
//			
//			// now go through each rupture
//			for (Integer rupIndex : allRups) {
//				double rate = sol.getRateForRup(rupIndex);
//				
//				if (rate == 0)
//					continue;
//				
//				boolean stoppingPoint = false;
//				if (possibleStops.contains(rupIndex)) {
//					// it could be a stop, but only if only one section here is involved
//					stoppingPoint = true;
//					Integer stopSect = null;
//					for (int i=0; i<possibleStopsPerSect.size(); i++) {
//						HashSet<Integer> sectPossibleStops = possibleStopsPerSect.get(i);
//						if (sectPossibleStops.contains(rupIndex)) {
//							if (stopSect == null)
//								stopSect = sects.get(i);
//							else {
//								stoppingPoint = false;
//								break;
//							}
//						}
//					}
//					Preconditions.checkNotNull(stopSect);
//					
//				}
//				
//				if (stoppingPoint)
//					stopRate += rate;
//				else
//					continueRate += rate;
//			}
			
			
			HashSet<Integer> alreadyCounted = new HashSet<Integer>();
			for (int sectIndex : sects) {
				for (int rupIndex : rupSet.getRupturesForSection(sectIndex)) {
					if (alreadyCounted.contains(rupIndex))
						continue;
					
					if (rupSet.getMagForRup(rupIndex) < minMag)
						continue;
					
					double rate = sol.getRateForRup(rupIndex);
					
					List<Integer> sectsForRup = rupSet.getSectionsIndicesForRup(rupIndex);
					boolean stoppingPoint = false;
					
					if (sectsForRup.size() == 1 || sectIndex == sectsForRup.get(0) && !sects.contains(sectsForRup.get(1)))
						stoppingPoint = true;
					else if (sectIndex == sectsForRup.get(sectsForRup.size()-1) && !sects.contains(sectsForRup.get(sectsForRup.size()-2)))
						stoppingPoint = true;
					
					if (stoppingPoint) {
						List<Integer> ends = rupStopCountMap.get(rupIndex);
						List<Location> endLocs = rupStopLocationsMap.get(rupIndex);
						if (ends == null) {
							ends = Lists.newArrayList();
							rupStopCountMap.put(rupIndex, ends);
							endLocs = Lists.newArrayList();
							rupStopLocationsMap.put(rupIndex, endLocs);
						}
						ends.add(sectIndex);
						endLocs.add(loc);
						if (sol instanceof InversionFaultSystemSolution && ends.size() > 2) {
							String endsStr = null;
							for (int i=0; i<ends.size(); i++) {
								if (i == 0)
									endsStr = "";
								else
									endsStr += ", ";
								endsStr += ends.get(i)+" ["
										+Joiner.on(",").join(stoppingPoints.get(endLocs.get(i)))+"]";
							}
							throw new IllegalStateException("Stop count over 2 for rup "+rupIndex
									+". Stops at: "+endsStr);
						}
					}
					
					if (stoppingPoint)
						stopRate += rate;
					else
						continueRate += rate;
					
					alreadyCounted.add(rupIndex);
				}
			}
			double tot = stopRate + continueRate;
			double normStopRate = stopRate / tot;
			double normContinueRate = continueRate / tot;
			
			if (csv != null) {
				List<String> line = Lists.newArrayList(loc.getLatitude()+"", loc.getLongitude()+"", stopRate+"", continueRate+"",
						normStopRate+"", normContinueRate+"");
				for (int sect : sects)
					line.add(rupSet.getFaultSectionData(sect).getName());
				csv.addLine(line);
			}
			
			double x = loc.getLatitude();
			Preconditions.checkState(stopFunc.getXIndex(x) == -1, "duplicate latitude!! "+loc);
			
			if (normStopRate > 0.075) {
				if (info == null)
					info = "";
				else
					info += "\n";
				String parents = null;
				for (int sectID : sects) {
					String parentName = rupSet.getFaultSectionData(sectID).getParentSectionName();
					if (parents == null)
						parents = "";
					else
						parents += ", ";
					parents += parentName;
				}
				info += "Lat="+(float)loc.getLatitude()+"\tnumSects="+sects.size()+"\tparents=("+parents+")"
						+"\n\tstopRate="+stopRate+"\tcontinueRate="+continueRate+"\tnormStopRate="+normStopRate;
			}
			
			if (normalize) {
				stopFunc.set(x, normStopRate);
				continueFunc.set(x, normContinueRate);
			} else {
				stopFunc.set(x, stopRate);
				continueFunc.set(x, continueRate);
			}
		}
		stopFunc.setInfo(info);
		
		PlotMultiDataLayer sectEnds = new PlotMultiDataLayer();
		sectEnds.setInfo("Parent Section End Points");
		
		for (Location loc : parentSectEnds)
			sectEnds.addVerticalLine(loc.getLatitude(), 0d, 1.5);
		
		System.out.println("Parent stopping pts: "+parentSectEnds.size());
		
		ArrayList<PlotElement> funcs = Lists.newArrayList();
		funcs.add(continueFunc);
		funcs.add(stopFunc);
		funcs.add(sectEnds);
		ArrayList<PlotCurveCharacterstics> chars = Lists.newArrayList();
		chars.add(new PlotCurveCharacterstics(PlotSymbol.CIRCLE, 5f, Color.GREEN.darker()));
		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, PlotSymbol.FILLED_SQUARE, 5f, Color.RED));
		chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 1f, Color.GRAY));
		
		String title = "Fault Segmentation";
		if (minMag > 5)
			title += " ("+(float)minMag+"+)";
		else
			title += " (All Mags)";
		
		PlotSpec spec = new PlotSpec(funcs, chars, title, "Latitude", "Rate Ratio");
		
		// add annotations
		Font font = new Font(Font.SERIF, 0, 16);
		double angle = -0.5*Math.PI;
		TextAnchor rotAnchor = TextAnchor.CENTER_RIGHT;
		TextAnchor textAnchor = TextAnchor.CENTER_RIGHT;
		List<XYAnnotation> annotations = Lists.newArrayList();
		for (Location loc : parentSectNamesMap.keySet()) {
			String name = parentSectNamesMap.get(loc);
			if (name.contains("San Andreas"))
				name = name.replaceAll("San Andreas", "").replaceAll("\\(", "").replaceAll("\\)", "");
			name = name.replaceAll("2011 CFM", "");
			name = name.trim();
			double x = loc.getLatitude();
			XYTextAnnotation a = new XYTextAnnotation(name, x, 1.5);
			a.setFont(font);
			a.setRotationAnchor(rotAnchor);
			a.setTextAnchor(textAnchor);
			a.setRotationAngle(angle);
			annotations.add(a);
		}
		spec.setPlotAnnotations(annotations);
		
		return spec;
	}
	
	private static boolean hasConnectionOnOtherParent(List<Integer> parents,
			FaultSectionPrefData subSect, FaultSystemSolution sol) {
		Collection<Integer> connections;
		FaultSystemRupSet rupSet = sol.getRupSet();
		if (rupSet instanceof InversionFaultSystemRupSet) {
			connections = ((InversionFaultSystemRupSet)rupSet).getCloseSectionsList(subSect.getSectionId());
		} else {
			connections = new HashSet<Integer>();
			for (int rupIndex : rupSet.getRupturesForSection(subSect.getSectionId())) {
				for (int sectIndex : rupSet.getSectionsIndicesForRup(rupIndex))
					connections.add(sectIndex);
			}
		}
		int parentID = subSect.getParentSectionId();
		for (int connection : connections) {
			int connectionParent = sol.getRupSet().getFaultSectionData(connection).getParentSectionId();
			if (connectionParent == parentID)
				// same fault
				continue;
			if (parents.contains(connectionParent))
				return true;
		}
		return false;
	}
	
	private static Location searchForMatch(Location loc, Collection<Location> locs, double toleranceKM) {
		double best = Double.MAX_VALUE;
		Location bestLoc = null;
		for (Location testLoc : locs) {
			double dist = LocationUtils.horzDistance(loc, testLoc);
			if (dist < best && dist <= toleranceKM) {
				best = dist;
				bestLoc = testLoc;
			}
		}
		return bestLoc;
	}
	
	public static List<Integer> getSAFParents(FaultModels fm) {
		if (fm == FaultModels.FM2_1)
			return Lists.newArrayList(295, 284, 283, 119, 301, 286, 287, 300, 285, 32, 57, 56, 67, 27, 26, 13);
		else if (fm == FaultModels.FM3_1)
			return Lists.newArrayList(97, 170, 295, 284, 283, 282, 301, 286, 287, 300,
					285, 32, 658, 657, 655, 654, 653, 13);
		else
			return Lists.newArrayList(97, 171, 295, 284, 283, 282, 301, 286, 287, 300,
					285, 32, 658, 657, 655, 654, 653, 13);
	}
	
	public static List<Integer> getHaywardParents(FaultModels fm) {
		return fm.getNamedFaultsMapAlt().get("Hayward-Rodgers Creek");
//		if (fm == FaultModels.FM2_1)
//			return Lists.newArrayList(25, 68, 69, 4, 5, 55);
//		else
//			return Lists.newArrayList(651, 639, 638, 637, 601, 602, 603, 621);
	}
	
	public static void main(String args[]) throws IOException, DocumentException {
//		File solFile = new File("/tmp/FM3_1_NEOK_EllB_DsrUni_CharConst_M5Rate8.7_MMaxOff7.6_NoFix_SpatSeisU3_mean_sol.zip");
//		File solFile = new File("/tmp/FM3_1_GEOL_EllB_DsrUni_CharConst_M5Rate8.7_MMaxOff7.6_NoFix_SpatSeisU3_sol.zip");
//		File solFile = new File("/tmp/FM3_1_UCERF2_COMPARISON_sol.zip");
//		File solFile = new File("/tmp/FM2_1_UCERF2_COMPARISON_sol.zip");
//		File solFile = new File("/tmp/FM3_1_NEOK_EllB_DsrUni_CharConst_M5Rate8.7_MMaxOff7.6_NoFix_SpatSeisU3_mean_sol_high_a_priori.zip");
		File solFile = new File(new File(UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR, "InversionSolutions"),
				"2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_1_MEAN_BRANCH_AVG_SOL.zip");
		FaultSystemSolution sol = FaultSystemIO.loadSol(solFile);
		List<Integer> safParentSects31 = FaultSpecificSegmentationPlotGen.getSAFParents(FaultModels.FM3_1);
		List<Integer> safParentSects21 = FaultSpecificSegmentationPlotGen.getSAFParents(FaultModels.FM2_1);
		
		File writeDir = new File("/tmp/branch_avg");
		Preconditions.checkState(writeDir.exists() || writeDir.mkdir());
		
		InversionFaultSystemSolution ucerf2Sol = UCERF2_ComparisonSolutionFetcher.getUCERF2Solution(FaultModels.FM2_1);
		
		double[] minMags = { 0, 7d, 7.5d };
		
		for (double minMag : minMags) {
			CSVFile<String> csv = new CSVFile<String>(false);
			HeadlessGraphPanel gp = FaultSpecificSegmentationPlotGen.getSegmentationHeadlessGP(
					safParentSects31, sol, minMag, false, csv);
			String prefix = "ucerf3_saf_seg";
			if (minMag > 0)
				prefix += (float)minMag+"+";
			File file = new File(writeDir, prefix);
			gp.getChartPanel().setSize(1000, 800);
			gp.saveAsPDF(file.getAbsolutePath()+".pdf");
			gp.saveAsPNG(file.getAbsolutePath()+".png");
			gp.saveAsTXT(file.getAbsolutePath()+".txt");
			csv.writeToFile(new File(file.getAbsolutePath()+".csv"));
			
			csv = new CSVFile<String>(false);
			gp = FaultSpecificSegmentationPlotGen.getSegmentationHeadlessGP(
					safParentSects21, ucerf2Sol, minMag, false, csv);
			prefix = "ucerf2_saf_seg";
			if (minMag > 0)
				prefix += (float)minMag+"+";
			file = new File(writeDir, prefix);
			gp.getChartPanel().setSize(1000, 800);
			gp.saveAsPDF(file.getAbsolutePath()+".pdf");
			gp.saveAsPNG(file.getAbsolutePath()+".png");
			gp.saveAsTXT(file.getAbsolutePath()+".txt");
			csv.writeToFile(new File(file.getAbsolutePath()+".csv"));
		}
		
//		HeadlessGraphPanel gp = FaultSpecificSegmentationPlotGen.getSegmentationHeadlessGP(
//				getHaywardParents(sol.getRupSet().getFaultModel()), sol, 0, false);
//		
//		String prefix = "ucerf3_hayward_seg";
//
//		File file = new File(writeDir, prefix);
//		gp.getChartPanel().setSize(1000, 800);
//		gp.saveAsPDF(file.getAbsolutePath()+".pdf");
//		gp.saveAsPNG(file.getAbsolutePath()+".png");
//		gp.saveAsTXT(file.getAbsolutePath()+".txt");
//		
//		gp = FaultSpecificSegmentationPlotGen.getSegmentationHeadlessGP(
//				getHaywardParents(sol.getRupSet().getFaultModel()), sol, 7, false);
//		
//		prefix = "ucerf3_hayward_seg7.0+";
//
//		file = new File(writeDir, prefix);
//		gp.getChartPanel().setSize(1000, 800);
//		gp.saveAsPDF(file.getAbsolutePath()+".pdf");
//		gp.saveAsPNG(file.getAbsolutePath()+".png");
//		gp.saveAsTXT(file.getAbsolutePath()+".txt");
//		
//		gp = FaultSpecificSegmentationPlotGen.getSegmentationHeadlessGP(
//				getHaywardParents(sol.getRupSet().getFaultModel()), sol, 7.5, false);
//		
//		prefix = "ucerf3_hayward_seg7.5+";
//
//		file = new File(writeDir, prefix);
//		gp.getChartPanel().setSize(1000, 800);
//		gp.saveAsPDF(file.getAbsolutePath()+".pdf");
//		gp.saveAsPNG(file.getAbsolutePath()+".png");
//		gp.saveAsTXT(file.getAbsolutePath()+".txt");
//		
//		// now UCERF2
//		
//		gp = FaultSpecificSegmentationPlotGen.getSegmentationHeadlessGP(
//				getHaywardParents(ucerf2Sol.getRupSet().getFaultModel()), ucerf2Sol, 0, false);
//		
//		prefix = "ucerf2_hayward_seg";
//
//		file = new File(writeDir, prefix);
//		gp.getChartPanel().setSize(1000, 800);
//		gp.saveAsPDF(file.getAbsolutePath()+".pdf");
//		gp.saveAsPNG(file.getAbsolutePath()+".png");
//		gp.saveAsTXT(file.getAbsolutePath()+".txt");
//		
//		gp = FaultSpecificSegmentationPlotGen.getSegmentationHeadlessGP(
//				getHaywardParents(ucerf2Sol.getRupSet().getFaultModel()), ucerf2Sol, 7, false);
//		
//		prefix = "ucerf2_hayward_seg7.0+";
//
//		file = new File(writeDir, prefix);
//		gp.getChartPanel().setSize(1000, 800);
//		gp.saveAsPDF(file.getAbsolutePath()+".pdf");
//		gp.saveAsPNG(file.getAbsolutePath()+".png");
//		gp.saveAsTXT(file.getAbsolutePath()+".txt");
		
//		Map<Integer, List<Integer>> namedMap = sol.getFaultModel().getNamedFaultsMap();
//		List<Integer> parents = namedMap.get(32);
		// NBMK: 294
//		List<Integer> parents = getSAFParents(sol.getFaultModel());
//		
//		System.out.println(Joiner.on(", ").join(parents));
//		
////		plotSegmentation(parents, sol, 0, true);
////		plotSegmentation(parents, sol, 0, false);
////		plotSegmentation(parents, sol, 7, true);
////		plotSegmentation(parents, sol, 6.5, false);
//		plotSegmentation(parents, sol, 7, false);
////		plotSegmentation(parents, sol, 7.5, true);
////		plotSegmentation(parents, sol, 7.5, false);
	}

}
