package org.opensha.sha.earthquake.faultSysSolution.ruptures.util;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import org.apache.commons.math3.stat.StatUtils;
import org.dom4j.DocumentException;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.Range;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.HistogramFunction;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSymbol;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.util.DataUtils;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.IDPairing;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.JumpAzimuthChangeFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.CumulativeProbabilityFilter.*;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.PathPlausibilityFilter.SectCoulombPathEvaluator;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.DistCutoffClosestSectClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.InputJumpsOrDistClusterConnectionStrategy;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.simulators.stiffness.AggregatedStiffnessCalculator;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator;
import org.opensha.sha.simulators.stiffness.AggregatedStiffnessCalculator.AggregationMethod;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.PatchAlignment;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.StiffnessType;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;
import com.google.common.primitives.Doubles;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.SlipEnabledSolution;
import scratch.UCERF3.inversion.laughTest.PlausibilityResult;
import scratch.UCERF3.utils.FaultSystemIO;

public class SegmentationCalculator {
	
	public static final double[] MIN_MAGS_DEFAULT = { 0d, 6.5d, 7d, 7.5d };

	/*
	 * Inputs
	 */
	private final List<? extends FaultSection> subSects;
	private final FaultSystemSolution sol;
	private final List<ClusterRupture> ruptures;
	private final SectionDistanceAzimuthCalculator distAzCalc;
	private final double[] minMags;
	
	private SubSectStiffnessCalculator stiffnessCalc;
	
	private JumpProbabilityCalc detrendProb = new Shaw07JumpDistProb(1d, 3d);
	
	/*
	 * Calculations
	 */
	// subsection participation rates, by min mag
	private final double[][] sectParticRates;
	// parent section participation rates
	private final Map<Integer, double[]> parentParticRates;
	// map of parent section ID to full clusters
	private final Map<Integer, FaultSubsectionCluster> fullClustersMap;
	// table of <parent section ID, jump(s) between those parents, rates that jump is taken>
	private final Table<IDPairing, Jump, JumpRates> parentJumpRateTable;
	// if true, there are multiple jumps between a given parent section pair
	private final boolean multipleJumpsPerParent;
	
	public enum RateCombiner {
		MIN("Min Rate") {
			@Override
			public double combine(double rate1, double rate2) {
				return Math.min(rate1, rate2);
			}
		},
		MAX("Max Rate") {
			@Override
			public double combine(double rate1, double rate2) {
				return Math.max(rate1, rate2);
			}
		},
		AVERAGE("Avg Rate") {
			@Override
			public double combine(double rate1, double rate2) {
				return 0.5*(rate1 + rate2);
			}
		};
		
		private String label;

		private RateCombiner(String label) {
			this.label = label;
		}
	
		@Override
		public String toString() {
			return label;
		}
		
		public abstract double combine(double rate1, double rate2);
	}
	
	public enum Scalars {
		JUMP_DIST("Jump Distance", "km") {
			@Override
			public double calc(Jump jump, JumpRates rates, SectionDistanceAzimuthCalculator distAzCalc,
					SubSectStiffnessCalculator stiffnessCalc) {
				return jump.distance;
			}

			@Override
			public Range getPlotRange(double minVal, double maxVal) {
//				return new Range(0d, Math.max(5d, Math.ceil(maxVal)));
				return new Range(0d, 15d);
			}

			@Override
			public HistogramFunction initHistogram(double minVal, double maxVal) {
				return new HistogramFunction(0.5d, 15, 1d);
			}
		},
		SLIP_RATE_CHANGE("|Slip Rate Change|", "mm/yr") {
			@Override
			public double calc(Jump jump, JumpRates rates, SectionDistanceAzimuthCalculator distAzCalc,
					SubSectStiffnessCalculator stiffnessCalc) {
				return Math.abs(1000*rates.fromRates.rupSetSlipRate - 1000*rates.toRates.rupSetSlipRate);
			}

			@Override
			public Range getPlotRange(double minVal, double maxVal) {
				return new Range(0d, Math.max(50d, 10*Math.ceil(maxVal/10d)));
			}

			@Override
			public HistogramFunction initHistogram(double minVal, double maxVal) {
				Range range = getPlotRange(minVal, maxVal);
				double delta;
				if (range.getLength() > 40d)
					delta = 5;
				else if (range.getLength() > 20d)
					delta = 2d;
				else
					delta = 1d;
				return HistogramFunction.getEncompassingHistogram(0d, range.getUpperBound(), delta);
			}
		},
		MIN_SLIP_RATE("Slip Rate Ratio", "mm/yr") {
			@Override
			public double calc(Jump jump, JumpRates rates, SectionDistanceAzimuthCalculator distAzCalc,
					SubSectStiffnessCalculator stiffnessCalc) {
				double slip1 = 1000*rates.fromRates.rupSetSlipRate;
				double slip2 = 1000*rates.toRates.rupSetSlipRate;
				if (slip1 < slip2) {
					double tmp = slip1;
					slip1 = slip2;
					slip2 = tmp;
				}
				return slip1/slip2;
			}

			@Override
			public Range getPlotRange(double minVal, double maxVal) {
				return new Range(1d, Math.max(10, Math.min(1000, 10*Math.ceil(maxVal/10d))));
			}

			@Override
			public HistogramFunction initHistogram(double minVal, double maxVal) {
				Range range = getPlotRange(minVal, maxVal);
				double delta;
				if (range.getLength() > 200d)
					delta = 10;
				else if (range.getLength() > 50d)
					delta = 5;
				else if (range.getLength() > 20d)
					delta = 2d;
				else
					delta = 1d;
				return HistogramFunction.getEncompassingHistogram(0d, range.getUpperBound(), delta);
			}
		},
		DIP_CHANGE("|Dip Change|", "degrees") {
			@Override
			public double calc(Jump jump, JumpRates rates, SectionDistanceAzimuthCalculator distAzCalc,
					SubSectStiffnessCalculator stiffnessCalc) {
				return Math.abs(jump.fromSection.getAveDip() - jump.toSection.getAveDip());
			}

			@Override
			public Range getPlotRange(double minVal, double maxVal) {
				return new Range(0d, Math.max(60, 10*Math.ceil(maxVal/10d)));
			}

			@Override
			public HistogramFunction initHistogram(double minVal, double maxVal) {
				Range range = getPlotRange(minVal, maxVal);
				double delta;
				if (range.getLength() > 30d)
					delta = 5;
				else if (range.getLength() > 10d)
					delta = 2d;
				else
					delta = 1d;
				return HistogramFunction.getEncompassingHistogram(0d, range.getUpperBound(), delta);
			}
		},
//		SECT_RATE_CHANGE("|Subsect Rate Change|", "/yr") {
//			@Override
//			public double calc(Jump jump, JumpRates rates, int magIndex) {
//				return Math.abs(rates.fromRates.sectRates[magIndex] - rates.toRates.sectRates[magIndex]);
//			}
//		},
//		PARENT_SECT_RATE_CHANGE("|Parent Sect Rate Change|", "/yr") {
//			@Override
//			public double calc(Jump jump, JumpRates rates, int magIndex) {
//				return Math.abs(rates.fromRates.parentSectRates[magIndex] - rates.toRates.parentSectRates[magIndex]);
//			}
//		},
		RAKE_CHANGE("|Rake Change|", "degrees") {
			@Override
			public double calc(Jump jump, JumpRates rates, SectionDistanceAzimuthCalculator distAzCalc,
					SubSectStiffnessCalculator stiffnessCalc) {
				double rakeDiff = Math.abs(rates.fromRates.sect.getAveRake() - rates.toRates.sect.getAveRake());
				if (rakeDiff > 180)
					rakeDiff = 360-rakeDiff; // Deal with branch cut (180deg = -180deg)
				Preconditions.checkState(rakeDiff >= 0);
				return rakeDiff;
			}

			@Override
			public Range getPlotRange(double minVal, double maxVal) {
				return new Range(0d, 180d);
			}

			@Override
			public HistogramFunction initHistogram(double minVal, double maxVal) {
				return new HistogramFunction(5d, 18, 10d);
			}
		},
		AZIMUTH_CHANGE("|Azimuth Change|", "degrees") {
			@Override
			public double calc(Jump jump, JumpRates rates, SectionDistanceAzimuthCalculator distAzCalc,
					SubSectStiffnessCalculator stiffnessCalc) {
				double az1 = calcAzimuth(jump.fromSection, jump.fromCluster.subSects, rates.fromAzTrack, distAzCalc, true);
				double az2 = calcAzimuth(jump.toSection, jump.toCluster.subSects, rates.toAzTrack, distAzCalc, false);
				return JumpAzimuthChangeFilter.getAzimuthDifference(az1, az2);
			}
			
			private double calcAzimuth(FaultSection sect, List<? extends FaultSection> subSects,
					AzTracker azTrack, SectionDistanceAzimuthCalculator distAzCalc, boolean reverse) {
				int ind = subSects.indexOf(sect);
				Preconditions.checkState(ind >= 0);
				if (azTrack.forwardRate == azTrack.backwardRate) {
					return Double.NaN;
				}
				
				FaultSection nextSect;
				if (azTrack.forwardRate > azTrack.backwardRate) {
					if (ind == subSects.size()-1)
						return Double.NaN;
					nextSect = subSects.get(ind+1);
				} else {
					if (ind == 0)
						return Double.NaN;
					nextSect = subSects.get(ind-1);
				}
				if (reverse)
					return distAzCalc.getAzimuth(nextSect, sect);
				return distAzCalc.getAzimuth(sect, nextSect);
			}

			@Override
			public Range getPlotRange(double minVal, double maxVal) {
//				return new Range(0d, Math.max(5d, Math.ceil(maxVal)));
				return new Range(0d, 180d);
			}

			@Override
			public HistogramFunction initHistogram(double minVal, double maxVal) {
				return new HistogramFunction(5d, 18, 10d);
			}
		},
		CFF_FRACT_POSITIVE("Best Directional Fract ΔCFF>0", null) {
			private AggregatedStiffnessCalculator aggCalc = null;
			@Override
			public double calc(Jump jump, JumpRates rates, SectionDistanceAzimuthCalculator distAzCalc,
					SubSectStiffnessCalculator stiffnessCalc) {
				if (aggCalc == null)
					aggCalc = new AggregatedStiffnessCalculator(StiffnessType.CFF, stiffnessCalc, false,
							AggregationMethod.FLATTEN, AggregationMethod.NUM_POSITIVE, AggregationMethod.SUM, AggregationMethod.NORM_BY_COUNT);
				return bestDirectionalValue(aggCalc, jump);
			}

			@Override
			public Range getPlotRange(double minVal, double maxVal) {
				return new Range(0d, 1d);
			}

			@Override
			public HistogramFunction initHistogram(double minVal, double maxVal) {
				return new HistogramFunction(0.025, 20, 0.05);
			}
		},
		CFF_FRACT_RPATCH_POSITIVE("Best Directional Fract RPatch ΔCFF>0", null) {
			private AggregatedStiffnessCalculator aggCalc = null;
			@Override
			public double calc(Jump jump, JumpRates rates, SectionDistanceAzimuthCalculator distAzCalc,
					SubSectStiffnessCalculator stiffnessCalc) {
				if (aggCalc == null)
					aggCalc = new AggregatedStiffnessCalculator(StiffnessType.CFF, stiffnessCalc, false,
							AggregationMethod.SUM, AggregationMethod.PASSTHROUGH, AggregationMethod.RECEIVER_SUM, AggregationMethod.FRACT_POSITIVE);
				return bestDirectionalValue(aggCalc, jump);
			}

			@Override
			public Range getPlotRange(double minVal, double maxVal) {
				return new Range(0d, 1d);
			}

			@Override
			public HistogramFunction initHistogram(double minVal, double maxVal) {
				return new HistogramFunction(0.025, 20, 0.05);
			}
		},
		CFF_SUM("Best Directional Sum ΔCFF", "MPa") {
			private AggregatedStiffnessCalculator aggCalc = null;
			@Override
			public double calc(Jump jump, JumpRates rates, SectionDistanceAzimuthCalculator distAzCalc,
					SubSectStiffnessCalculator stiffnessCalc) {
				if (aggCalc == null)
					aggCalc = new AggregatedStiffnessCalculator(StiffnessType.CFF, stiffnessCalc, false,
							AggregationMethod.FLATTEN, AggregationMethod.SUM, AggregationMethod.SUM, AggregationMethod.SUM);
				return bestDirectionalValue(aggCalc, jump);
			}

			@Override
			public Range getPlotRange(double minVal, double maxVal) {
				maxVal = Math.min(100d, Math.pow(10, Math.ceil(Math.log10(maxVal))));
				if (minVal > 0)
					minVal = 0;
				else
					minVal = -Math.min(100d, Math.pow(10, Math.ceil(Math.log10(-minVal))));
				return new Range(minVal, maxVal);
			}

			@Override
			public HistogramFunction initHistogram(double minVal, double maxVal) {
				Range range = getPlotRange(minVal, maxVal);
				double delta;
				if (range.getLength() >= 100d)
					delta = 5;
				else if (range.getLength() >= 10d)
					delta = 1d;
				else if (range.getLength() >= 5d)
					delta = 0.5d;
				else
					delta = 0.1d;
				return HistogramFunction.getEncompassingHistogram(range.getLowerBound(), range.getUpperBound(), delta);
			}
		},
		CFF_MAX("Max ΔCFF", "MPa") {
			private AggregatedStiffnessCalculator aggCalc = null;
			@Override
			public double calc(Jump jump, JumpRates rates, SectionDistanceAzimuthCalculator distAzCalc,
					SubSectStiffnessCalculator stiffnessCalc) {
				if (aggCalc == null)
					aggCalc = new AggregatedStiffnessCalculator(StiffnessType.CFF, stiffnessCalc, false,
							AggregationMethod.FLATTEN, AggregationMethod.MAX, AggregationMethod.MAX, AggregationMethod.MAX);
				return Math.max(aggCalc.calc(jump.fromCluster.subSects, jump.toCluster.subSects),
						aggCalc.calc(jump.toCluster.subSects, jump.fromCluster.subSects));
			}

			@Override
			public Range getPlotRange(double minVal, double maxVal) {
				return new Range(0d, Math.min(100d, 10*Math.ceil(0.1*maxVal)));
			}

			@Override
			public HistogramFunction initHistogram(double minVal, double maxVal) {
				Range range = getPlotRange(minVal, maxVal);
				double delta;
				if (range.getLength() >= 100d)
					delta = 5;
				else if (range.getLength() >= 50d)
					delta = 2d;
				else if (range.getLength() >= 10d)
					delta = 1d;
				else if (range.getLength() >= 5d)
					delta = 0.5d;
				else
					delta = 0.1d;
				return HistogramFunction.getEncompassingHistogram(0d, range.getUpperBound(), delta);
			}
		};
//		CFF_MEDIAN("Median CFF", "MPa") {
//			private AggregatedStiffnessCalculator aggCalc = null;
//			@Override
//			public double calc(Jump jump, JumpRates rates, int magIndex, SubSectStiffnessCalculator stiffnessCalc) {
//				if (aggCalc == null)
//					aggCalc = new AggregatedStiffnessCalculator(StiffnessType.CFF, stiffnessCalc, false,
//							AggregationMethod.FLATTEN, AggregationMethod.FLATTEN, AggregationMethod.FLATTEN, AggregationMethod.MEDIAN);
//				return Math.max(aggCalc.calc(jump.fromCluster.subSects, jump.toCluster.subSects),
//						aggCalc.calc(jump.toCluster.subSects, jump.fromCluster.subSects));
//			}
//
//			@Override
//			public Range getPlotRange(double minVal, double maxVal) {
//				return new Range(0d, Math.min(100d, Math.pow(10, Math.ceil(Math.log10(maxVal)))));
//			}
//
//			@Override
//			public HistogramFunction initHistogram(double minVal, double maxVal) {
//				Range range = getPlotRange(minVal, maxVal);
//				double delta;
//				if (range.getLength() >= 100d)
//					delta = 5;
//				else if (range.getLength() >= 10d)
//					delta = 1d;
//				else if (range.getLength() >= 5d)
//					delta = 0.5d;
//				else
//					delta = 0.1d;
//				return HistogramFunction.getEncompassingHistogram(0d, range.getUpperBound(), delta);
//			}
//		};
		
		private String name;
		private String units;

		private Scalars(String name, String units) {
			this.name = name;
			this.units = units;
		}
		
		public abstract double calc(Jump jump, JumpRates rates, SectionDistanceAzimuthCalculator distAzCalc,
				SubSectStiffnessCalculator stiffnessCalc);
		public abstract Range getPlotRange(double minVal, double maxVal);
		public abstract HistogramFunction initHistogram(double minVal, double maxVal);
		
		@Override
		public String toString() {
			String ret = name;
			if (units != null && !units.isEmpty())
				ret += " ("+units+")";
			return ret;
		}
	}
	
//	private static double bestDirectionalSPath(AggregatedStiffnessCalculator aggCalc, Jump jump) {
//		SectCoulombPathEvaluator eval = new SectCoulombPathEvaluator(aggCalc, com.google.common.collect.Range.atLeast(0f),
//				PlausibilityResult.FAIL_HARD_STOP, true, 15, distAzCalc);
//		
//		eval.getStrandJumpValue(navigator, strandClusters, strandSections, jump, false);
//		
//		int fromIndex = jump.fromCluster.subSects.indexOf(jump.fromSection);
//		Preconditions.checkState(fromIndex >= 0);
//		int toIndex = jump.toCluster.subSects.indexOf(jump.toSection);
//		Preconditions.checkState(toIndex >= 0);
//		
//		List<List<FaultSection>> fromOptions = getDirectionalOptions(jump.fromCluster.subSects, fromIndex);
//		List<List<FaultSection>> toOptions = getDirectionalOptions(jump.toCluster.subSects, toIndex);
//
//		double maxVal = Double.NEGATIVE_INFINITY;
//		for (List<FaultSection> fromSects : fromOptions)
//			for (List<FaultSection> toSects : toOptions)
//				maxVal = Math.max(maxVal, aggCalc.calc(fromSects, toSects));
//		
//		return maxVal;
//	}
	
	private static double bestDirectionalValue(AggregatedStiffnessCalculator aggCalc, Jump jump) {
		int fromIndex = jump.fromCluster.subSects.indexOf(jump.fromSection);
		Preconditions.checkState(fromIndex >= 0);
		int toIndex = jump.toCluster.subSects.indexOf(jump.toSection);
		Preconditions.checkState(toIndex >= 0);
		
		List<List<FaultSection>> fromOptions = getDirectionalOptions(jump.fromCluster.subSects, fromIndex);
		List<List<FaultSection>> toOptions = getDirectionalOptions(jump.toCluster.subSects, toIndex);

		double maxVal = Double.NEGATIVE_INFINITY;
		for (List<FaultSection> fromSects : fromOptions)
			for (List<FaultSection> toSects : toOptions)
				maxVal = Math.max(maxVal, aggCalc.calc(fromSects, toSects));
		
		return maxVal;
	}
	
	private static List<List<FaultSection>> getDirectionalOptions(List<FaultSection> clusterSects, int startIndex) {
		Preconditions.checkState(startIndex >= 0);
		List<List<FaultSection>> ret = new ArrayList<>();
		ret.add(clusterSects);
		if (startIndex > 0)
			ret.add(clusterSects.subList(0, startIndex+1));
		if (startIndex < clusterSects.size()-1)
			ret.add(clusterSects.subList(startIndex, clusterSects.size()));
		Preconditions.checkState(!ret.isEmpty());
		return ret;
	}
	
	public SegmentationCalculator(FaultSystemSolution sol, List<ClusterRupture> ruptures,
			ClusterConnectionStrategy connStrat, SectionDistanceAzimuthCalculator distAzCalc) {
		this(sol, ruptures, connStrat, distAzCalc, MIN_MAGS_DEFAULT);
	}
	
	public SegmentationCalculator(FaultSystemSolution sol, List<ClusterRupture> ruptures,
			ClusterConnectionStrategy connStrat, SectionDistanceAzimuthCalculator distAzCalc, double[] minMags) {
		this.subSects = sol.getRupSet().getFaultSectionDataList();
		this.sol = sol;
		Preconditions.checkState(sol.getRupSet().getNumRuptures() == ruptures.size());
		this.ruptures = ruptures;
		this.distAzCalc = distAzCalc;
		this.minMags = minMags;
		
		// build full clusters, init participation rates to zero
		sectParticRates = new double[subSects.size()][minMags.length];
		parentParticRates = new HashMap<>();
		
		List<FaultSubsectionCluster> clusters = connStrat.getClusters();
		fullClustersMap = new HashMap<>();
		for (FaultSubsectionCluster cluster : clusters) {
			fullClustersMap.put(cluster.parentSectionID, cluster);
			parentParticRates.put(cluster.parentSectionID, new double[minMags.length]);
		}
		
		// process ruptures
		final double[] mags = sol.getRupSet().getMagForAllRups();
		final double[] rates = sol.getRateForAllRups();
		// first figure out parent/sect rates
		for (int r=0; r<rates.length; r++) {
			ClusterRupture rup = ruptures.get(r);
			HashSet<Integer> parentIDs = new HashSet<>();
			
			// process section rates
			for (FaultSubsectionCluster cluster : rup.getClustersIterable()) {
				parentIDs.add(cluster.parentSectionID);
				for (FaultSection sect : cluster.subSects)
					addMagRate(sectParticRates[sect.getSectionId()], mags[r], rates[r]);
			}
			for (Integer parentID : parentIDs)
				addMagRate(parentParticRates.get(parentID), mags[r], rates[r]);
		}
		// now figure out jump rates
		JumpingPointRates[] jumpPointRates = new JumpingPointRates[subSects.size()];
		parentJumpRateTable = HashBasedTable.create();
		// add all possible jumps from the connection strategy
		for (FaultSubsectionCluster cluster : clusters) {
			for (Jump jump : cluster.getConnections()) {
				IDPairing pair = pair(jump.fromCluster.parentSectionID, jump.toCluster.parentSectionID);
				if (!parentJumpRateTable.containsRow(pair)) {
					if (jump.fromCluster.parentSectionID == pair.getID2())
						jump = jump.reverse();
					JumpingPointRates fromRates = getBuildJumpPointRates(jumpPointRates, jump.fromSection);
					JumpingPointRates toRates = getBuildJumpPointRates(jumpPointRates, jump.toSection);
					JumpRates jumpRates = new JumpRates(fromRates, toRates);
					// initialze with tiny tiny rate
					jumpRates.addAzimuiths(jump, jump.fromCluster, jump.toCluster, 1e-16);
					parentJumpRateTable.put(pair, jump, jumpRates);
				}
			}
		}
		
		boolean multipleJumpsPerParent = false;
		for (int r=0; r<rates.length; r++) {
			ClusterRupture rup = ruptures.get(r);
			
			// process jumps
			for (Jump jump : rup.getJumpsIterable()) {
				// we want to store full cluster jumps in order of increasing id
				int fromParentID = jump.fromCluster.parentSectionID;
				int toParentID = jump.toCluster.parentSectionID;
				if (fromParentID == toParentID)
					// this is a jump skipping a section in a parent section, skip it
					continue;
				FaultSection fromSect, toSect;
				if (fromParentID < toParentID) {
					// keep jump order
					fromSect = jump.fromSection;
					toSect = jump.toSection;
				} else {
					// reverse
					fromSect = jump.toSection;
					toSect = jump.fromSection;
					int tmpID = fromParentID;
					fromParentID = toParentID;
					toParentID = tmpID;
					jump = jump.reverse();
				}
				
				FaultSubsectionCluster fullFrom = fullClustersMap.get(fromParentID);
				FaultSubsectionCluster fullTo = fullClustersMap.get(toParentID);
				Jump fullJump = new Jump(fromSect, fullFrom, toSect, fullTo, jump.distance);
				IDPairing pair = pair(fromParentID, toParentID);
				JumpRates jumpRates = parentJumpRateTable.get(pair, fullJump);
				if (jumpRates == null) {
					// new jump
					multipleJumpsPerParent = multipleJumpsPerParent || parentJumpRateTable.containsRow(pair);
					JumpingPointRates fromRates = getBuildJumpPointRates(jumpPointRates, fromSect);
					JumpingPointRates toRates = getBuildJumpPointRates(jumpPointRates, toSect);
					jumpRates = new JumpRates(fromRates, toRates);
					parentJumpRateTable.put(pair, fullJump, jumpRates);
				}
				jumpRates.addAzimuiths(jump, fullFrom, fullTo, rates[r]);
				jumpRates.addRate(mags[r], rates[r]);
			}
		}
		System.out.println("Processed "+ruptures.size()+" ruptures. Found "+parentJumpRateTable.size()
			+" unique jumps. Multiple jumps per parent pair? "+multipleJumpsPerParent);
		this.multipleJumpsPerParent = multipleJumpsPerParent;
	}
	
	private SegmentationCalculator(SegmentationCalculator other, Table<IDPairing, Jump, JumpRates> parentJumpRateTable) {
		this.subSects = other.subSects;
		this.sol = other.sol;
		this.ruptures = other.ruptures;
		this.distAzCalc = other.distAzCalc;
		this.minMags = other.minMags;
		this.sectParticRates = other.sectParticRates;
		this.parentParticRates = other.parentParticRates;
		this.fullClustersMap = other.fullClustersMap;
		boolean multipleJumpsPerParent = false;
		for (IDPairing pair : parentJumpRateTable.rowKeySet()) {
			Map<Jump, JumpRates> map = parentJumpRateTable.row(pair);
			multipleJumpsPerParent = multipleJumpsPerParent || map.size() > 1;
			for (JumpRates jumpRates : map.values())
				Preconditions.checkState(jumpRates.magJumpRates.length == minMags.length);
		}
		this.parentJumpRateTable = parentJumpRateTable;
		this.multipleJumpsPerParent = multipleJumpsPerParent;
	}

	private void addMagRate(double[] magRates, double mag, double rate) {
		for (int m=0; m<minMags.length; m++)
			if (mag >= minMags[m])
				magRates[m] += rate;
	}
	
	private IDPairing pair(int id1, int id2) {
		if (id1 < id2)
			return new IDPairing(id1, id2);
		return new IDPairing(id2, id1);
	}
	
	public boolean areMultipleJumpsPerParent() {
		return multipleJumpsPerParent;
	}
	
	private JumpingPointRates getBuildJumpPointRates(JumpingPointRates[] jumpPointRates, FaultSection sect) {
		if (jumpPointRates[sect.getSectionId()] == null)
			jumpPointRates[sect.getSectionId()] = new JumpingPointRates(sect);
		return jumpPointRates[sect.getSectionId()];
	}
	
	private class JumpingPointRates {
		public final FaultSection sect;
		public final double[] parentSectRates;
		public final double[] sectRates;
		public final double rupSetSlipRate;
		public final double solSlipRate;
		
		private JumpingPointRates(FaultSection sect) {
			this(sect, parentParticRates.get(sect.getParentSectionId()), sectParticRates[sect.getSectionId()]);
		}
		
		private JumpingPointRates(FaultSection sect, double[] parentSectRates, double[] sectRates) {
			this.sect = sect;
			this.parentSectRates = parentSectRates;
			this.sectRates = sectRates;
			this.rupSetSlipRate = sol.getRupSet().getSlipRateForSection(sect.getSectionId());
			if (sol instanceof SlipEnabledSolution)
				this.solSlipRate = ((SlipEnabledSolution)sol).calcSlipRateForSect(sect.getSectionId());
			else
				this.solSlipRate = Double.NaN;
		}

		public JumpingPointRates(FaultSection sect, double[] parentSectRates, double[] sectRates, double rupSetSlipRate,
				double solSlipRate) {
			super();
			this.sect = sect;
			this.parentSectRates = parentSectRates;
			this.sectRates = sectRates;
			this.rupSetSlipRate = rupSetSlipRate;
			this.solSlipRate = solSlipRate;
		}
	}
	
	private class AzTracker {
		private double forwardRate = 0d;
		private double backwardRate = 0d;
	}
	
	private class JumpRates {
		public final JumpingPointRates fromRates;
		public final JumpingPointRates toRates;
		public final double[] magJumpRates;
		
		public final AzTracker fromAzTrack;
		public final AzTracker toAzTrack;
		
		public JumpRates(JumpingPointRates fromRates, JumpingPointRates toRates) {
			this(fromRates, new AzTracker(), toRates, new AzTracker(), new double[minMags.length]);
		}
		
		public JumpRates(JumpingPointRates fromRates, AzTracker fromAzTrack,
				JumpingPointRates toRates, AzTracker toAzTrack, double[] magJumpRates) {
			super();
			this.fromRates = fromRates;
			this.fromAzTrack = fromAzTrack;
			this.toRates = toRates;
			this.toAzTrack = toAzTrack;
			this.magJumpRates = magJumpRates;
		}
		
		public void addRate(double mag, double rate) {
			addMagRate(magJumpRates, mag, rate);
		}
		
		public void addAzimuiths(Jump jump, FaultSubsectionCluster fullFrom, FaultSubsectionCluster fullTo, double rate) {
			Boolean fromForward = calcDirection(jump.fromSection, jump.fromCluster, fullFrom);
			if (fromForward != null) {
				if (fromForward)
					fromAzTrack.forwardRate += rate;
				else
					fromAzTrack.backwardRate += rate;
			}
			Boolean toForward = calcDirection(jump.toSection, jump.toCluster, fullTo);
			if (toForward != null) {
				if (toForward)
					toAzTrack.forwardRate += rate;
				else
					toAzTrack.backwardRate += rate;
			}
		}
		
		private Boolean calcDirection(FaultSection initialSect, FaultSubsectionCluster jumpCluster,
				FaultSubsectionCluster fullCluster) {
			int ind = fullCluster.subSects.indexOf(initialSect);
			Preconditions.checkState(ind >= 0);
			int numForward = 0;
			int numBackward = 0;
			for (int i=ind+1; i<fullCluster.subSects.size(); i++) {
				if (jumpCluster.contains(fullCluster.subSects.get(i)))
					numForward++;
				else
					break;
			}
			for (int i=ind; --i>=0;) {
				if (jumpCluster.contains(fullCluster.subSects.get(i)))
					numBackward++;
				else
					break;
			}
			if (numForward == numBackward)
				return null;
			return numForward > numBackward;
		}
	}
	
	/**
	 * Collapses jumps between parent sections a single jump. Properties of the jumping sections will be taken as 
	 * the rate-weighted average of all jumping sections. If highestRate=true, then the jump with the highest overall
	 * rate is taken as the final jump, otherwise the one with the smallest distance. 
	 * 
	 * @param highestRate if true, keep the jump with the highest rate, otherwise keep the smallest distance
	 * @return
	 */
	public SegmentationCalculator combineMultiJumps(boolean highestRate) {
		if (!multipleJumpsPerParent)
			return this;
		
		Table<IDPairing, Jump, JumpRates> combinedTable = HashBasedTable.create();
		for (IDPairing pair : parentJumpRateTable.rowKeySet()) {
			Map<Jump, JumpRates> jumpMap = parentJumpRateTable.row(pair);
			if (jumpMap.size() < 2) {
				for (Jump jump : jumpMap.keySet())
					combinedTable.put(pair, jump, jumpMap.get(jump));
			} else {
				// combine them
				Jump bestJump = null;
				JumpRates bestJumpRates = null;
				double[] totRates = new double[minMags.length];
				for (Jump jump : jumpMap.keySet()) {
					JumpRates rate = jumpMap.get(jump);
					boolean newBest = bestJump == null
							|| (highestRate && StatUtils.max(rate.magJumpRates) > StatUtils.max(bestJumpRates.magJumpRates))
							|| (!highestRate && jump.distance < bestJump.distance);
					if (newBest) {
						bestJump = jump;
						bestJumpRates = rate;
					}
					for (int m=0; m<minMags.length; m++)
						totRates[m] += rate.magJumpRates[m];
				}
				double totMaxRate = StatUtils.max(totRates);
				double[] combFromSectRates = new double[minMags.length];
				double[] combToSectRates = new double[minMags.length];
				double combFromRupSetSlipRate = 0d;
				double combToRupSetSlipRate = 0d;
				double combFromSolSlipRate = 0d;
				double combToSolSlipRate = 0d;
				for (Jump jump : jumpMap.keySet()) {
					// average everything, weighted by the rate that jump is used
					JumpRates jumpRates = jumpMap.get(jump);
					for (int m=0; m<minMags.length; m++) {
						// mag-specific weight
						double weight = jumpRates.magJumpRates[m]/totRates[m];
						combFromSectRates[m] += jumpRates.fromRates.sectRates[m]*weight;
						combToSectRates[m] += jumpRates.toRates.sectRates[m]*weight;
					}
					// total weight
					double weight = StatUtils.max(jumpRates.magJumpRates)/totMaxRate;
					combFromRupSetSlipRate += jumpRates.fromRates.rupSetSlipRate*weight;
					combToRupSetSlipRate += jumpRates.toRates.rupSetSlipRate*weight;
					combFromSolSlipRate += jumpRates.fromRates.solSlipRate*weight;
					combToSolSlipRate += jumpRates.toRates.solSlipRate*weight;
				}
				FaultSection fromSect = bestJump.fromSection;
				JumpingPointRates combFromRates = new JumpingPointRates(fromSect, parentParticRates.get(fromSect.getParentSectionId()),
						combFromSectRates, combFromRupSetSlipRate, combFromSolSlipRate);
				FaultSection toSect = bestJump.toSection;
				JumpingPointRates combToRates = new JumpingPointRates(toSect, parentParticRates.get(toSect.getParentSectionId()),
						combToSectRates, combToRupSetSlipRate, combToSolSlipRate);
				JumpRates combJumpRates = new JumpRates(combFromRates, bestJumpRates.fromAzTrack, combToRates, bestJumpRates.toAzTrack, totRates);
				combinedTable.put(pair, bestJump, combJumpRates);
			}
		}
		
		SegmentationCalculator ret = new SegmentationCalculator(this, combinedTable);
		Preconditions.checkState(!ret.multipleJumpsPerParent);
		return ret;
	}
	
	private static final DecimalFormat optionalDigitDF = new DecimalFormat("0.#");
	private static final DecimalFormat oneDigitDF = new DecimalFormat("0.0");
	public static String getMagLabel(double mag) {
		if (mag > 0)
			return "M≥"+optionalDigitDF.format(mag);
		return "Supra-Seismogenic";
	}
	public static String getMagPrefix(double mag) {
		if (mag > 0)
			return "m"+oneDigitDF.format(mag);
		return "supra_seis";
	}
	
	public File[] plotConnectionRates(File outputDir, String prefix, String title) throws IOException {
		return plotConnectionRates(outputDir, prefix, title, 800);
	}
	
	private static CPT getConnectionRateCPT() throws IOException {
		return GMT_CPT_Files.RAINBOW_UNIFORM.instance().rescale(-6, -1);
	}
	
	public File[] plotConnectionRates(File outputDir, String prefix, String title, int width) throws IOException {
		RupSetMapMaker plotter = new RupSetMapMaker(sol.getRupSet(), RupSetMapMaker.buildBufferedRegion(subSects));
		
		CPT cpt = getConnectionRateCPT();
		
		File[] ret = new File[minMags.length];
		
		for (int m=0; m<minMags.length; m++) {
			plotter.clearJumpScalars();
			
			List<Jump> jumps = new ArrayList<>();
			List<Double> values = new ArrayList<>();
			for (Cell<IDPairing, Jump, JumpRates> cell : parentJumpRateTable.cellSet()) {
				double val = cell.getValue().magJumpRates[m];
				if (val > 0d) {
					jumps.add(cell.getColumnKey());
					values.add(Math.log10(val));
				}
			}
			plotter.plotJumpScalars(jumps, values, cpt, "Log10 "+getMagLabel(minMags[m])+" Connection Rates");
			
			String myPrefix = prefix+"_"+getMagPrefix(minMags[m]);
			ret[m] = new File(outputDir, myPrefix+".png");
			plotter.plot(outputDir, myPrefix, title, width);
		}
		
		return ret;
	}
	
	public File[] plotConnectionFracts(File outputDir, String prefix, String title, RateCombiner combiner) throws IOException {
		return plotConnectionFracts(outputDir, prefix, title, 800, combiner);
	}
	
	private static CPT getConnectionFractCPT() throws IOException {
		return GMT_CPT_Files.RAINBOW_UNIFORM.instance().rescale(0d, 1d);
	}
	
	public File[] plotConnectionFracts(File outputDir, String prefix, String title, int width, RateCombiner combiner) throws IOException {
		RupSetMapMaker plotter = new RupSetMapMaker(sol.getRupSet(), RupSetMapMaker.buildBufferedRegion(subSects));
//		plotter.setJumpLineThickness(4f);
		
		CPT cpt = getConnectionFractCPT();
		
		File[] ret = new File[minMags.length];
		
		for (int m=0; m<minMags.length; m++) {
			plotter.clearJumpScalars();
			
			List<Jump> jumps = new ArrayList<>();
			List<Double> values = new ArrayList<>();
			for (Cell<IDPairing, Jump, JumpRates> cell : parentJumpRateTable.cellSet()) {
				JumpRates rates = cell.getValue();
				double jumpRate = rates.magJumpRates[m];
				double fromVal = rates.fromRates.sectRates[m];
				double toVal = rates.toRates.sectRates[m];
				double fract = jumpRate/combiner.combine(fromVal, toVal);
				if (fract > 0d) {
					jumps.add(cell.getColumnKey());
					values.add(fract);
				}
			}
			String label = getMagLabel(minMags[m])+" Passthrough Rate (Rel. "+combiner.label+")";
			plotter.plotJumpScalars(jumps, values, cpt, label);
			
			String myPrefix = prefix+"_"+getMagPrefix(minMags[m]);
			ret[m] = new File(outputDir, myPrefix+".png");
			plotter.plot(outputDir, myPrefix, title, width);
		}
		
		return ret;
	}
	
	private SubSectStiffnessCalculator getStiffnessCalc() {
		if (stiffnessCalc == null)
			stiffnessCalc = new SubSectStiffnessCalculator(subSects, 2d, 30000, 30000, 0.5, PatchAlignment.FILL_OVERLAP, 1);
		return stiffnessCalc;
	}
	
	private class ScalarCalcCallable implements Callable<ScalarCalcCallable> {
		
		private final Scalars scalar;
		private final Jump jump;
		private final JumpRates rates;
		private final SubSectStiffnessCalculator stiffnessCalc;
		
		private double value;

		public ScalarCalcCallable(Scalars scalar, Jump jump, JumpRates rates, SubSectStiffnessCalculator stiffnessCalc) {
			this.scalar = scalar;
			this.jump = jump;
			this.rates = rates;
			this.stiffnessCalc = stiffnessCalc;
		}

		@Override
		public ScalarCalcCallable call() throws Exception {
			this.value = scalar.calc(jump, rates, distAzCalc, stiffnessCalc);
			return this;
		}
		
	}
	
	private Map<Jump, Double> calcJumpScalarValues(Scalars scalar) {
		SubSectStiffnessCalculator stiffnessCalc = getStiffnessCalc();
		Map<Jump, Double> ret = new HashMap<>();
		
		int threads = Integer.max(1, Runtime.getRuntime().availableProcessors());
		if (threads > 32)
			threads = 32;
		if (threads > 8)
			threads -= 2;
		ExecutorService exec = Executors.newFixedThreadPool(threads);
		
		List<Future<ScalarCalcCallable>> futures = new ArrayList<>();
		
		for (Cell<IDPairing, Jump, JumpRates> cell : parentJumpRateTable.cellSet()) {
			Jump jump = cell.getColumnKey();
			JumpRates rates = cell.getValue();
			Preconditions.checkState(!ret.containsKey(jump));
			
			futures.add(exec.submit(new ScalarCalcCallable(scalar, jump, rates, stiffnessCalc)));
		}
		
		for (Future<ScalarCalcCallable> future : futures) {
			try {
				ScalarCalcCallable call = future.get();
				ret.put(call.jump, call.value);
			} catch (InterruptedException | ExecutionException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
		}
		
		exec.shutdown();
		return ret;
	}
	
	public File[] plotFractVsScalars(File outputDir, String prefix, Scalars scalar, boolean logY,
			RateCombiner... combiners) throws IOException {
		Preconditions.checkArgument(combiners.length > 0, "Must supply at least 1 rate combiner");
		
		File[] ret = new File[minMags.length];
		
		HistogramFunction marginalTakenHist = null;
		HistogramFunction marginalAllHist = null;
		Range xRange = null;
		Range yRange = logY ? new Range(1e-3, 1) : new Range(0d, 1d);
		
		CPT rateCPT = getConnectionRateCPT();
		Color outlineColor = new Color(0, 0, 0, 100);
		float scatterWidth = 4;
		
		Map<Jump, Double> scalarJumpVals = calcJumpScalarValues(scalar);
		
		for (int m=0; m<minMags.length; m++) {
			MinMaxAveTracker scalarTrack = new MinMaxAveTracker();
			
			List<PlotSpec> specs = new ArrayList<>();
			
			for (RateCombiner combiner : combiners) {
				List<XY_DataSet> funcs = new ArrayList<>();
				List<PlotCurveCharacterstics> chars = new ArrayList<>();
				
				DefaultXY_DataSet fakeXY = new DefaultXY_DataSet();
				fakeXY.set(0d, -1d);
				fakeXY.setName("Connection");
				funcs.add(fakeXY);
				chars.add(new PlotCurveCharacterstics(PlotSymbol.FILLED_CIRCLE, scatterWidth, rateCPT.getMaxColor()));
				
				DefaultXY_DataSet zerosScatter = new DefaultXY_DataSet();
				zerosScatter.setName("Zero-Rate");
				funcs.add(zerosScatter);
				chars.add(new PlotCurveCharacterstics(PlotSymbol.FILLED_CIRCLE, scatterWidth, Color.GRAY));

				List<Double> fracts = new ArrayList<>();
				List<Double> detrendFracts = detrendProb == null ? null : new ArrayList<>();
				List<Double> scalarVals = new ArrayList<>();
				
				for (Cell<IDPairing, Jump, JumpRates> cell : parentJumpRateTable.cellSet()) {
					Jump jump = cell.getColumnKey();
					JumpRates rates = cell.getValue();
					double jumpRate = rates.magJumpRates[m];
					double scalarVal = scalarJumpVals.get(jump);
					double fract;
					if (jumpRate == 0d) {
						fract = 0d;
						zerosScatter.set(scalarVal, yRange.getLowerBound());
					} else {
						double fromVal = rates.fromRates.sectRates[m];
						double toVal = rates.toRates.sectRates[m];
//						Preconditions.checkState(Double.isFinite(fromVal));
//						Preconditions.checkState(Double.isFinite(toVal));
						double rate = combiner.combine(fromVal, toVal);
						fract = jumpRate/rate;
						Color c = rateCPT.getColor((float)Math.log10(rate));
						c = new Color(c.getRed(), c.getGreen(), c.getBlue(), 200);
						
						XY_DataSet scatter = new DefaultXY_DataSet();
						scatter.set(scalarVal, fract);

						funcs.add(scatter);
						chars.add(new PlotCurveCharacterstics(PlotSymbol.FILLED_CIRCLE, scatterWidth, c));
						funcs.add(scatter);
						chars.add(new PlotCurveCharacterstics(PlotSymbol.CIRCLE, scatterWidth, outlineColor));
						if (detrendProb != null) {
							double refProb = detrendProb.calcJumpProbability(null, jump, false);
							detrendFracts.add(Math.min(1d, fract/refProb));
						}
					}
					
					scalarTrack.addValue(scalarVal);
					scalarVals.add(scalarVal);
					fracts.add(fract);
				}
				
				if (m == 0) {
					xRange = scalar.getPlotRange(scalarTrack.getMin(), scalarTrack.getMax());
					marginalTakenHist = scalar.initHistogram(scalarTrack.getMin(), scalarTrack.getMax());
					marginalAllHist = new HistogramFunction(marginalTakenHist.getMinX(),
							marginalTakenHist.getMaxX(), marginalTakenHist.size());
					for (int i=0; i<scalarVals.size(); i++) {
						double val = scalarVals.get(i);
						if (xRange.contains(val)) {
							int ind = marginalTakenHist.getClosestXIndex(val);
							if (fracts.get(i) > 0)
								marginalTakenHist.add(ind, 1d);
							marginalAllHist.add(ind, 1d);
						}
					}
				}
				// now bin values
				List<List<Double>> valLists = new ArrayList<>();
				for (int i=0; i<marginalTakenHist.size(); i++)
					valLists.add(new ArrayList<>());
				for (int i=0; i<fracts.size(); i++) {
					double scalarVal = scalarVals.get(i);
					double fract = fracts.get(i);
					if (xRange.contains(scalarVal) && Double.isFinite(fract)) {
						int ind = marginalTakenHist.getClosestXIndex(scalarVal);
						valLists.get(ind).add(fract);
					}
				}
				XY_DataSet binnedMeans = new DefaultXY_DataSet();
				binnedMeans.setName("Mean");
				XY_DataSet binnedMedians = new DefaultXY_DataSet();
				binnedMedians.setName("Median");
				XY_DataSet probTaken = new DefaultXY_DataSet();
				probTaken.setName("P(>0)");
				for (int i=0; i<valLists.size(); i++) {
					List<Double> binnedVals = valLists.get(i);
					if (binnedVals.isEmpty())
						continue;
					double[] values = Doubles.toArray(binnedVals);
					binnedMeans.set(marginalTakenHist.getX(i), StatUtils.mean(values));
					binnedMedians.set(marginalTakenHist.getX(i), DataUtils.median(values));
					probTaken.set(marginalTakenHist.getX(i), marginalTakenHist.getY(i)/marginalAllHist.getY(i));
				}
				// add fake values so that the legend works
				if (binnedMeans.size() == 0) {
					binnedMeans.set(0d, -1d);
					binnedMedians.set(0d, -1d);
				}
				if (zerosScatter.size() == 0)
					zerosScatter.set(0d, -1d);
				funcs.add(binnedMeans);
				chars.add(new PlotCurveCharacterstics(PlotSymbol.FILLED_CIRCLE, 10f, new Color(0, 0, 0, 150)));
				funcs.add(binnedMedians);
				chars.add(new PlotCurveCharacterstics(PlotSymbol.FILLED_SQUARE, 10f, new Color(0, 0, 150, 150)));
				funcs.add(probTaken);
				chars.add(new PlotCurveCharacterstics(PlotSymbol.FILLED_TRIANGLE, 6f, new Color(150, 0, 0, 150)));
				if (detrendProb != null) {
					// now do it all again, detrended
					valLists = new ArrayList<>();
					for (int i=0; i<marginalTakenHist.size(); i++)
						valLists.add(new ArrayList<>());
					for (int i=0; i<detrendFracts.size(); i++) {
						double scalarVal = scalarVals.get(i);
						double fract = detrendFracts.get(i);
						if (xRange.contains(scalarVal) && Double.isFinite(fract)) {
							int ind = marginalTakenHist.getClosestXIndex(scalarVal);
							valLists.get(ind).add(fract);
						}
					}
					XY_DataSet detrendMeans = new DefaultXY_DataSet();
					detrendMeans.setName("Detrended ("+detrendProb.getName()+") Mean");
					for (int i=0; i<valLists.size(); i++) {
						List<Double> binnedVals = valLists.get(i);
						if (binnedVals.isEmpty())
							continue;
						double[] values = Doubles.toArray(binnedVals);
						detrendMeans.set(marginalTakenHist.getX(i), StatUtils.mean(values));
					}
					// add fake values so that the legend works
					if (detrendMeans.size() == 0) {
						detrendMeans.set(0d, -1d);
					}
					if (zerosScatter.size() == 0)
						zerosScatter.set(0d, -1d);
					funcs.add(detrendMeans);
					chars.add(new PlotCurveCharacterstics(PlotSymbol.FILLED_CIRCLE, 10f, new Color(0, 150, 150, 150)));
				}
				
				PlotSpec spec = new PlotSpec(funcs, chars, scalar.name+" Dependence", scalar.toString(),
						"Passthrough Rate (Rel. "+combiner.label+")");
				spec.setLegendVisible(specs.isEmpty());
				specs.add(spec);
			}
			List<Range> xRanges = new ArrayList<>();
			xRanges.add(xRange);
			List<Range> yRanges = new ArrayList<>();
			for (int i=0; i<specs.size(); i++)
				yRanges.add(yRange);
			
			List<XY_DataSet> funcs = new ArrayList<>();
			List<PlotCurveCharacterstics> chars = new ArrayList<>();
			
			marginalAllHist.setName("All Available Jumps");
			funcs.add(marginalAllHist);
			chars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 1f, Color.GRAY));
			
			marginalTakenHist.setName("Taken Jumps");
			funcs.add(marginalTakenHist);
			chars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 1f, Color.GREEN.darker()));
			
			PlotSpec marginalSpec = new PlotSpec(funcs, chars, scalar.name+" Dependence", scalar.toString(),
					"Marginal Count");
			marginalSpec.setLegendInset(true);
			marginalSpec.addSubtitle(RupSetMapMaker.buildCPTLegend(rateCPT, "Log10 Rate (Denomiator)"));
			specs.add(marginalSpec);
			double maxMarginal = marginalAllHist.getMaxY();
			if (logY)
				maxMarginal = Math.max(10, Math.pow(10, Math.ceil(Math.log10(maxMarginal))));
			else
				maxMarginal = Math.max(10, maxMarginal*1.05);
			yRanges.add(logY ? new Range(1d, maxMarginal) : new Range(0d, maxMarginal));
			
			System.out.println(getMagLabel(minMags[m])+" "+scalar+": "+scalarTrack);
			
			HeadlessGraphPanel gp = new HeadlessGraphPanel();
			gp.setTickLabelFontSize(18);
			gp.setAxisLabelFontSize(24);
			gp.setPlotLabelFontSize(24);
			gp.setLegendFontSize(18);
			gp.setBackgroundColor(Color.WHITE);
			
			int width = 1000;
			int height = 300 + 400*specs.size();
			gp.drawGraphPanel(specs, false, logY, xRanges, yRanges);
			
			CombinedDomainXYPlot plot = (CombinedDomainXYPlot)gp.getPlot();
			for (int i=0; i<specs.size(); i++)
				((XYPlot)plot.getSubplots().get(i)).setWeight(i < specs.size()-1 ? 5: 3);

			String myPrefix = prefix+"_"+getMagPrefix(minMags[m]);
			ret[m] = new File(outputDir, myPrefix+".png");
			gp.getChartPanel().setSize(width, height);
			gp.saveAsPNG(ret[m].getAbsolutePath());
		}
		
		return ret;
	}
	
	public File[][] plotMagScatters(File outputDir, String prefix, boolean logY,
			RateCombiner... combiners) throws IOException {
		File[][] ret = new File[minMags.length][minMags.length];
		
		for (int m1=0; m1<minMags.length; m1++) {
			for (int m2=m1+1; m2<minMags.length; m2++) {
				ret[m1][m2] = plotMagScatter(outputDir, prefix+"_"+getMagPrefix(minMags[m1])
					+"_"+getMagPrefix(minMags[m2]), logY, m1, m2, combiners);
				ret[m2][m1] = ret[m1][m2];
			}
		}
		
		return ret;
	}
	
	public File plotMagScatter(File outputDir, String prefix, boolean log, int index1, int index2,
			RateCombiner... combiners) throws IOException {
		Preconditions.checkArgument(combiners.length > 0, "Must supply at least 1 rate combiner");
		
		List<PlotSpec> specs = new ArrayList<>();
		
		Range xRange = log ? new Range(1e-3, 1) : new Range(0d, 1d);
		Range yRange = xRange;
		
		for (RateCombiner combiner : combiners) {
			XY_DataSet scatter = new DefaultXY_DataSet();
			
			for (Cell<IDPairing, Jump, JumpRates> cell : parentJumpRateTable.cellSet()) {
				Jump jump = cell.getColumnKey();
				JumpRates rates = cell.getValue();
				
				double jumpRate1 = rates.magJumpRates[index1];
				double fromVal1 = rates.fromRates.sectRates[index1];
				double toVal1 = rates.toRates.sectRates[index1];
				double fract1 = jumpRate1/combiner.combine(fromVal1, toVal1);
				
				double jumpRate2 = rates.magJumpRates[index2];
				double fromVal2 = rates.fromRates.sectRates[index2];
				double toVal2 = rates.toRates.sectRates[index2];
				double fract2 = jumpRate2/combiner.combine(fromVal2, toVal2);
				
				scatter.set(fract1, fract2);
			}
			
			List<XY_DataSet> funcs = new ArrayList<>();
			List<PlotCurveCharacterstics> chars = new ArrayList<>();
			
			DefaultXY_DataSet oneToOne = new DefaultXY_DataSet();
			oneToOne.set(xRange.getLowerBound(), xRange.getLowerBound());
			oneToOne.set(xRange.getUpperBound(), xRange.getUpperBound());
			funcs.add(oneToOne);
			chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 2f, Color.GRAY));
			
			funcs.add(scatter);
			chars.add(new PlotCurveCharacterstics(PlotSymbol.FILLED_CIRCLE, 2f, new Color(0, 0, 0, 127)));
			
			specs.add(new PlotSpec(funcs, chars, getMagLabel(minMags[index1])+" vs "+getMagLabel(minMags[index2]),
					getMagLabel(minMags[index1])+" Passthrough Rate",
					getMagLabel(minMags[index2])+" Passthrough Rate (Rel. "+combiner.label+")"));
		}
		
		HeadlessGraphPanel gp = new HeadlessGraphPanel();
		gp.setTickLabelFontSize(18);
		gp.setAxisLabelFontSize(24);
		gp.setPlotLabelFontSize(24);
		gp.setLegendFontSize(18);
		gp.setBackgroundColor(Color.WHITE);
		
		int width = 1000;
		int height = 300 + 400*specs.size();
		List<Range> xRanges = new ArrayList<>();
		xRanges.add(xRange);
		List<Range> yRanges = new ArrayList<>();
		for (int i=0; i<specs.size(); i++)
			yRanges.add(yRange);
		gp.drawGraphPanel(specs, log, log, xRanges, yRanges);

		File ret = new File(outputDir, prefix+".png");
		gp.getChartPanel().setSize(width, height);
		gp.saveAsPNG(ret.getAbsolutePath());
		return ret;
	}
	
	public File[][] plotCombinerScatters(File outputDir, String prefix, boolean logY, int magIndex,
			RateCombiner... combiners) throws IOException {
		Preconditions.checkState(combiners.length > 1);
		File[][] ret = new File[combiners.length][combiners.length];
		
		for (int c1=0; c1<combiners.length; c1++) {
			for (int c2=c1+1; c2<combiners.length; c2++) {
				ret[c1][c2] = plotCombinerScatter(outputDir, prefix+"_"+combiners[c1].name()+"_"+combiners[c2].name(),
						logY, magIndex, combiners[c1], combiners[c2]);
				ret[c2][c1] = ret[c1][c2];
			}
		}
		
		return ret;
	}
	
	public File plotCombinerScatter(File outputDir, String prefix, boolean log, int magIndex, RateCombiner combiner1,
			RateCombiner combiner2) throws IOException {
		
		XY_DataSet scatter = new DefaultXY_DataSet();
		
		for (Cell<IDPairing, Jump, JumpRates> cell : parentJumpRateTable.cellSet()) {
			Jump jump = cell.getColumnKey();
			JumpRates rates = cell.getValue();
			
			double jumpRate = rates.magJumpRates[magIndex];
			double fromVal = rates.fromRates.sectRates[magIndex];
			double toVal = rates.toRates.sectRates[magIndex];
			
			double fract1 = jumpRate/combiner1.combine(fromVal, toVal);
			double fract2 = jumpRate/combiner2.combine(fromVal, toVal);
			
			scatter.set(fract1, fract2);
		}
		
		List<XY_DataSet> funcs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		
		Range xRange = log ? new Range(1e-3, 1) : new Range(0d, 1d);
		Range yRange = xRange;
		
		DefaultXY_DataSet oneToOne = new DefaultXY_DataSet();
		oneToOne.set(xRange.getLowerBound(), xRange.getLowerBound());
		oneToOne.set(xRange.getUpperBound(), xRange.getUpperBound());
		funcs.add(oneToOne);
		chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 2f, Color.GRAY));
		
		funcs.add(scatter);
		chars.add(new PlotCurveCharacterstics(PlotSymbol.FILLED_CIRCLE, 2f, new Color(0, 0, 0, 127)));
		
		PlotSpec spec = new PlotSpec(funcs, chars, getMagLabel(minMags[magIndex])+" Relative Rate Combiners: "
				+combiner1.label+" vs "+combiner2.label, "Passthrough Rate (Rel. "+combiner1.label+")",
				"Passthrough Rate (Rel. "+combiner2.label+")");
		
		HeadlessGraphPanel gp = new HeadlessGraphPanel();
		gp.setTickLabelFontSize(18);
		gp.setAxisLabelFontSize(24);
		gp.setPlotLabelFontSize(24);
		gp.setLegendFontSize(18);
		gp.setBackgroundColor(Color.WHITE);
		
		int width = 1000;
		int height = 700;
		gp.drawGraphPanel(spec, log, log, xRange, yRange);

		File ret = new File(outputDir, prefix+".png");
		gp.getChartPanel().setSize(width, height);
		gp.saveAsPNG(ret.getAbsolutePath());
		return ret;
	}
	
	public static void main(String[] args) throws IOException, DocumentException {
		File rupSetDir = new File("/home/kevin/OpenSHA/UCERF4/rup_sets");
		FaultSystemSolution sol = FaultSystemIO.loadSol(new File(
				rupSetDir, "rsqsim_4983_stitched_m6.5_skip65000_sectArea0.5.zip"));
		double jumpDist = 15d;
		File distCacheFile = new File(rupSetDir, "fm3_1_dist_az_cache.csv");
		FaultSystemRupSet rupSet = sol.getRupSet();
		SectionDistanceAzimuthCalculator distAzCalc = new SectionDistanceAzimuthCalculator(rupSet.getFaultSectionDataList());
		distAzCalc.loadCacheFile(distCacheFile);
//		ClusterConnectionStrategy connStrat = new DistCutoffClosestSectClusterConnectionStrategy(rupSet.getFaultSectionDataList(), distAzCalc, jumpDist);
		ClusterConnectionStrategy connStrat = new InputJumpsOrDistClusterConnectionStrategy(rupSet.getFaultSectionDataList(), distAzCalc, jumpDist, new ArrayList<>());
		RuptureConnectionSearch rsConnSearch = new RuptureConnectionSearch(rupSet, distAzCalc,
				1000d, RuptureConnectionSearch.CUMULATIVE_JUMPS_DEFAULT);
		rupSet.buildClusterRups(rsConnSearch);
		List<ClusterRupture> rups = rupSet.getClusterRuptures();
		
		SegmentationCalculator calc = new SegmentationCalculator(sol, rups, connStrat, distAzCalc, new double[] {6.5d, 7.5d});
		calc = calc.combineMultiJumps(true);
		
		File outputDir = new File("/tmp/test_seg");
		calc.plotConnectionRates(outputDir, "conn_rates", "Connection Rates", 800);
		calc.plotConnectionFracts(outputDir, "conn_passthrough_rel_min", "Relative Connection Passthrough Rates", 800, RateCombiner.MIN);
		calc.plotConnectionFracts(outputDir, "conn_passthrough_rel_max", "Relative Connection Passthrough Rates", 800, RateCombiner.MAX);
		calc.plotConnectionFracts(outputDir, "conn_passthrough_rel_avg", "Relative Connection Passthrough Rates", 800, RateCombiner.AVERAGE);
		
		for (Scalars scalar : Scalars.values()) {
			calc.plotFractVsScalars(outputDir, "conn_passthrough_scalars_"+scalar.name(), scalar, false, RateCombiner.values());
			calc.plotFractVsScalars(outputDir, "conn_passthrough_scalars_"+scalar.name()+"_log", scalar, true, RateCombiner.values());
		}
		
	}

}
