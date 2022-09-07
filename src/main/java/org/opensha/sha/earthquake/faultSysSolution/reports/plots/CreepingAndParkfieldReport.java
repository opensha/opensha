package org.opensha.sha.earthquake.faultSysSolution.reports.plots;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.jfree.data.Range;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.geo.Location;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.RupSetScalingRelationship;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.reports.AbstractRupSetPlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportPageGen;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.SectBySectDetailPlots.AlongStrikePlot;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectIDRange;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSectionUtils;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.NSHM23_ConstraintBuilder;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;

public class CreepingAndParkfieldReport extends AbstractRupSetPlot {

	@Override
	public String getName() {
		return "SAF Creeping Section and Parkfield";
	}

	@Override
	public List<String> plot(FaultSystemRupSet rupSet, FaultSystemSolution sol, ReportMetadata meta, File resourcesDir,
			String relPathToResources, String topLink) throws IOException {
		List<? extends FaultSection> subSects = rupSet.getFaultSectionDataList();
		
		int creepingID = FaultSectionUtils.findParentSectionID(subSects, "San", "Andreas", "Creeping");
		int parkfieldID = FaultSectionUtils.findParentSectionID(subSects, "San", "Andreas", "Parkfield");
		
		if (creepingID < 0 && parkfieldID < 0)
			// they don't exist in this rupture set, skip this plot
			return null;
		
		System.out.println("Detected Parkfield ID="+parkfieldID+" and Creeping Section ID="+creepingID);
		
		List<FaultSection> parkfieldSects = null;
		if (parkfieldID >= 0)
			parkfieldSects = subSects.stream().filter(s->s.getParentSectionId() == parkfieldID).collect(Collectors.toList());
		List<FaultSection> creepingSects = null;
		if (creepingID >= 0)
			creepingSects = subSects.stream().filter(s->s.getParentSectionId() == creepingID).collect(Collectors.toList());
		
		List<String> lines = new ArrayList<>();
		
		if (parkfieldID >= 0) {
			lines.add(getSubHeading()+" Parkfield Magnitudes");
			lines.add(topLink); lines.add("");
			
			SectIDRange subSectRange = SectIDRange.build(parkfieldSects.get(0).getSectionId(),
					parkfieldSects.get(parkfieldSects.size()-1).getSectionId());
			
			int minSectCount = Integer.MAX_VALUE;
			int maxSectCount = 0;
			
			List<Integer> parkfieldRups = new ArrayList<>(rupSet.getRupturesForParentSection(parkfieldID));
			// filter out ruptures involving other sections
			for (int i=parkfieldRups.size(); --i>=0;) {
				int rupIndex = parkfieldRups.get(i);
				boolean match = true;
				List<Integer> rupSects = rupSet.getSectionsIndicesForRup(rupIndex);
				for (int sectID : rupSects) {
					if (!subSectRange.contains(sectID)) {
						match = false;
						break;
					}
				}
				if (match) {
					minSectCount = Integer.min(minSectCount, rupSects.size());
					maxSectCount = Integer.max(maxSectCount, rupSects.size());
				} else {
					parkfieldRups.remove(i);
				}
			}
			
			lines.add(parkfieldRups.size()+" ruptures found that rupture only the Parkfield section.");
			lines.add("");
			if (!parkfieldRups.isEmpty()) {
				TableBuilder table = MarkdownUtils.tableBuilder();
				
				table.initNewLine().addColumns("# Subsections", "Rupture Count", "Length (km)", "Magnitude Range");
				
				// see if we have other scaling relatinoship options
				RupSetScalingRelationship scaleOption = null;
				List<RupSetScalingRelationship> altScales = null;
				if (rupSet.hasModule(LogicTreeBranch.class)) {
					LogicTreeBranch<?> branch = rupSet.getModule(LogicTreeBranch.class);
					scaleOption = branch.getValue(RupSetScalingRelationship.class);
					if (scaleOption != null) {
						System.out.println("Detected scaling relationship from logic tree branch: "+scaleOption.getName());
						Class<? extends RupSetScalingRelationship> scaleClass = scaleOption.getClass();
						if (scaleClass.getEnclosingClass() != null) {
							Class<?> temp = scaleClass.getEnclosingClass();
							if (temp != null && RupSetScalingRelationship.class.isAssignableFrom(temp))
								scaleClass = (Class<? extends RupSetScalingRelationship>) temp;
						}
						System.out.println("Class: "+scaleClass.getName());
						if (scaleClass.isEnum() && RupSetScalingRelationship.class.isAssignableFrom(scaleClass)) {
							altScales = new ArrayList<>();
							for (RupSetScalingRelationship option : scaleClass.getEnumConstants())
								if (option.getNodeWeight(branch) > 0d)
									altScales.add(option);
							System.out.println("It's an enum! Found "+altScales.size()+" options");
							if (altScales.size() < 2)
								altScales = null;
						}
					}
				}
				if (altScales != null) {
					table.addColumn("Mag Range Across "+altScales.size()+" Scaling Options");
					
					lines.add("Magnitudes are listed for the chosen scaling relationship, _"+scaleOption.getName()
							+"_, as well as all "+altScales.size()+" alternative logic tree branch choices.");
					lines.add("");
				}
				
				if (sol != null)
					table.addColumns("Annual Rate", "Recurrence Interval");
				table.finalizeLine();
				
				double minLen = Double.POSITIVE_INFINITY;
				double maxLen = Double.NEGATIVE_INFINITY;
				double overallMinMag = Double.POSITIVE_INFINITY;
				double overallMaxMag = Double.NEGATIVE_INFINITY;
				double overallScaleMinMag = Double.POSITIVE_INFINITY;
				double overallScaleMaxMag = Double.NEGATIVE_INFINITY;
				double totRate = 0d;
				
				for (int count=minSectCount; count<=maxSectCount; count++) {
					int rupCount = 0;
					double len = Double.NaN;
					double minMag = Double.POSITIVE_INFINITY;
					double maxMag = Double.NEGATIVE_INFINITY;
					double scaleMinMag = Double.POSITIVE_INFINITY;
					double scaleMaxMag = Double.NEGATIVE_INFINITY;
					double myRate = 0d;
					for (int rupIndex : parkfieldRups) {
						if (count == rupSet.getSectionsIndicesForRup(rupIndex).size()) {
							rupCount++;
							if (rupCount == 1)
								len = rupSet.getLengthForRup(rupIndex)*1e-3;
							double mag = rupSet.getMagForRup(rupIndex);
							minMag = Double.min(minMag, mag);
							maxMag = Double.max(maxMag, mag);
							if (altScales != null) {
								for (RupSetScalingRelationship scale : altScales) {
									double rupArea = rupSet.getAreaForRup(rupIndex);
									double rupLength = rupSet.getLengthForRup(rupIndex);
									double totOrigArea = 0;
									for (FaultSection sect : rupSet.getFaultSectionDataForRupture(rupIndex))
										totOrigArea += sect.getArea(false);	// sq-m
									double origDDW = totOrigArea / rupLength;
									double altMag = scale.getMag(rupArea, rupLength, rupArea/rupLength, origDDW,
											rupSet.getAveRakeForRup(rupIndex));
									scaleMinMag = Math.min(scaleMinMag, altMag);
									scaleMaxMag = Math.max(scaleMaxMag, altMag);
								}
							}
							if (sol != null)
								myRate += sol.getRateForRup(rupIndex);
						}
					}
					minLen = Math.min(minLen, len);
					maxLen = Math.max(maxLen, len);
					overallMinMag = Math.min(overallMinMag, minMag);
					overallMaxMag = Math.max(overallMaxMag, maxMag);
					totRate += myRate;
					if (rupCount > 0) {
						table.initNewLine();
						table.addColumns("**"+countDF.format(count)+"**", countDF.format(rupCount), twoDigits.format(len),
								"["+twoDigits.format(minMag)+","+twoDigits.format(maxMag)+"]");
						if (altScales != null) {
							table.addColumn("["+twoDigits.format(scaleMinMag)+","+twoDigits.format(scaleMaxMag)+"]");
							overallScaleMinMag = Math.min(overallMinMag, scaleMinMag);
							overallScaleMaxMag = Math.max(overallMaxMag, scaleMaxMag);
						}
						if (sol != null) {
							table.addColumn((float)myRate);
							table.addColumn(twoDigits.format(1d/myRate));
						}
						table.finalizeLine();
					}
				}
				table.initNewLine();
				table.addColumns("**Total**", countDF.format(parkfieldRups.size()),
						"["+twoDigits.format(minLen)+","+twoDigits.format(maxLen)+"]",
						"["+twoDigits.format(overallMinMag)+","+twoDigits.format(overallMaxMag)+"]");
				if (altScales != null)
					table.addColumn("["+twoDigits.format(overallScaleMinMag)+","+twoDigits.format(overallScaleMaxMag)+"]");
				if (sol != null) {
					table.addColumn((float)totRate);
					table.addColumn(twoDigits.format(1d/totRate));
				}
				table.finalizeLine();
				
				lines.addAll(table.build());
				lines.add("");
			}
		}
		
		if (creepingID >= 0 && sol != null) {
			lines.add(getSubHeading()+" Rupture Rates Through Creeping Section");
			lines.add(topLink); lines.add("");
			lines.add("A rupture is considered to be through the creeping section if it ruptures the creeping section "
					+ "as well as at least part of one section on each side of the creeping section.");
			lines.add("");
			
			int santaCruzID;
			try {
				santaCruzID = FaultSectionUtils.findParentSectionID(subSects, "San", "Andreas", "Santa", "Cruz");
			} catch (Exception e) {
				e.printStackTrace();
				santaCruzID = -1;
			}
			
			int calaverasID;
			try {
				calaverasID = FaultSectionUtils.findParentSectionID(subSects, "Calaveras", "Paicines");
			} catch (Exception e) {
				e.printStackTrace();
				calaverasID = -1;
			}
			
			List<Double> minMags = new ArrayList<>();

			double rupSetMinMag = rupSet.getMinMag();
			double rupSetMaxMag = rupSet.getMaxMag();
			Range rupSetMagRange = new Range(rupSetMinMag, rupSetMaxMag);
			
			ClusterRuptures cRups = rupSet.requireModule(ClusterRuptures.class);
			
			List<Integer> rupsThroughCreeping = new ArrayList<>();
			for (int rupIndex : rupSet.getRupturesForParentSection(creepingID)) {
				ClusterRupture cRup = cRups.get(rupIndex);
				if (NSHM23_ConstraintBuilder.isRupThroughCreeping(creepingID, cRup))
					rupsThroughCreeping.add(rupIndex);
			}
			
			if (rupsThroughCreeping.isEmpty()) {
				lines.add("No ruptures were found through the creeping section.");
			} else {
				minMags.add(0d);
				
				if (rupSetMagRange.contains(6d))
					minMags.add(6d);
				
				if (rupSetMagRange.contains(7d))
					minMags.add(7d);
				
				if (rupSetMagRange.contains(7.5d))
					minMags.add(7.5d);
				
				if (rupSetMagRange.contains(8d))
					minMags.add(8d);
				
				TableBuilder table = MarkdownUtils.tableBuilder();
				
				table.initNewLine().addColumns("Minimum Magnitude", "Rate Through Creeping", "RI (years)", "% of Creeping Sect Rate");
				if (santaCruzID >= 0)
					table.addColumn("% of Santa Cruz Rate");
				if (calaverasID >= 0)
					table.addColumn("% of Calaveras Rate");
				if (parkfieldID >= 0)
					table.addColumn("% of Parkfield Rate");
				table.finalizeLine();
				
				for (double minMag : minMags) {
					table.initNewLine();
					if (minMag <= 0d)
						table.addColumn("**Supra-Seismogenic**");
					else
						table.addColumn("**M&ge;"+optionalDigitDF.format(minMag)+"**");
					double rateThrough = 0d;
					for (int rupIndex : rupsThroughCreeping)
						if (rupSet.getMagForRup(rupIndex) >= minMag)
							rateThrough += sol.getRateForRup(rupIndex);
					double totCreepingRate = rateForSect(sol, creepingID, minMag);
					
					table.addColumn((float)rateThrough);
					if (rateThrough > 0d)
						table.addColumn(twoDigits.format(1d/rateThrough));
					else
						table.addColumn("_(N/A)_");
					if (totCreepingRate == 0d)
						table.addColumn("_(N/A)_");
					else
						table.addColumn(percentDF.format(rateThrough/totCreepingRate));
					if (santaCruzID >= 0) {
						double cruzRate = rateForSect(sol, santaCruzID, minMag);
						if (cruzRate == 0d)
							table.addColumn("_(N/A)_");
						else
							table.addColumn(percentDF.format(rateThrough/cruzRate));
					}
					if (calaverasID >= 0) {
						double calaverasRate = rateForSect(sol, calaverasID, minMag);
						if (calaverasRate == 0d)
							table.addColumn("_(N/A)_");
						else
							table.addColumn(percentDF.format(rateThrough/calaverasRate));
					}
					if (parkfieldID >= 0) {
						double parkRate = rateForSect(sol, parkfieldID, minMag);
						if (parkRate == 0d)
							table.addColumn("_(N/A)_");
						else
							table.addColumn(percentDF.format(rateThrough/parkRate));
					}
					table.finalizeLine();
				}
				lines.addAll(table.build());
			}
			lines.add("");
		}
		
		List<FaultSection> plotSects = new ArrayList<>();
		Map<Integer, List<FaultSection>> parentsMap = new HashMap<>();
		
		if (creepingSects != null) {
			plotSects.addAll(creepingSects);
			parentsMap.put(creepingID, creepingSects);
		}
		
		if (parkfieldSects != null) {
			plotSects.addAll(parkfieldSects);
			parentsMap.put(parkfieldID, parkfieldSects);
		}
		
		Preconditions.checkState(!plotSects.isEmpty(), "Have creeping/parkfield IDs but no sections?");
		
		// always use latitutde for the SAF
		boolean latX = true;
		String xLabel = "Latitude (degrees)";
		
		List<XY_DataSet> emptySectFuncs = new ArrayList<>();
		double minX = Double.POSITIVE_INFINITY;
		double maxX = Double.NEGATIVE_INFINITY;
		for (FaultSection sect : plotSects) {
			XY_DataSet func = new DefaultXY_DataSet();
			for (Location loc : sect.getFaultTrace()) {
				if (latX)
					func.set(loc.getLatitude(), 0d);
				else
					func.set(loc.getLongitude(), 0d);
			}
			emptySectFuncs.add(func);
			minX = Math.min(minX, func.getMinX());
			maxX = Math.max(maxX, func.getMaxX());
		}
		
		Range xRange = new Range(minX, maxX);
		
		double legendRelX = latX ? 0.975 : 0.025;
		
		List<AlongStrikePlot> plots = new ArrayList<>();
		
		String title = "Slip Rates & Reductions";

		plots.add(SectBySectDetailPlots.buildSlipRatePlot(meta, plotSects, title, emptySectFuncs, xLabel, legendRelX, false));
		plots.add(SectBySectDetailPlots.buildSlipRateReductionPlot(meta, plotSects, title, emptySectFuncs, xLabel, legendRelX));
		
		String prefix = "saf_creep_park_along_strike";
		SectBySectDetailPlots.writeAlongStrikePlots(resourcesDir, prefix, plots, parentsMap, latX, xLabel, xRange, "San Andreas");
		
		lines.add(getSubHeading()+" Along-Strike Slip Rates and Reductions");
		lines.add(topLink); lines.add("");
		
		lines.add("![Plot]("+relPathToResources+"/"+prefix+".png)");
		
		return lines;
	}
	
	private double rateForSect(FaultSystemSolution sol, int parentID, double minMag) {
		FaultSystemRupSet rupSet = sol.getRupSet();
		double rate = 0d;
		for (int rupIndex : rupSet.getRupturesForParentSection(parentID))
			if (rupSet.getMagForRup(rupIndex) >= minMag)
				rate += sol.getRateForRup(rupIndex);
		return rate;
	}

	@Override
	public Collection<Class<? extends OpenSHA_Module>> getRequiredModules() {
		return null;
	}
	
	public static void main(String[] args) throws IOException {
//		File rupSetFile = new File("/home/kevin/OpenSHA/nshm23/def_models/NSHM23_v1p4/GEOLOGIC.zip");
//		FaultSystemRupSet rupSet = FaultSystemRupSet.load(rupSetFile);
//		FaultSystemSolution sol = null;
		
		File solFile = new File("/data/kevin/nshm23/rup_sets/fm3_1_ucerf3.zip");
		FaultSystemSolution sol = FaultSystemSolution.load(solFile);
		FaultSystemRupSet rupSet = sol.getRupSet();
		
		File outputDir = new File("/tmp/test_report");
		Preconditions.checkState(outputDir.exists() || outputDir.mkdir());
		ReportPageGen pageGen = new ReportPageGen(rupSet, sol, "Test", outputDir,
				List.of(new SlipRatePlots(), new CreepingAndParkfieldReport()));
		pageGen.setReplot(true);
		pageGen.generatePage();
	}

}
