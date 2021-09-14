package org.opensha.sha.earthquake.faultSysSolution;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.dom4j.Document;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.XMLUtils;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRuptureBuilder;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityConfiguration;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityConfiguration.Builder;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityResult;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.JumpAzimuthChangeFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.JumpAzimuthChangeFilter.AzimuthCalc;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.SplayConnectionsOnlyFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.coulomb.NetRuptureCoulombFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.path.CumulativeProbPathEvaluator;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.path.NucleationClusterEvaluator;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.path.PathPlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.CoulombSectRatioProb;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.CumulativeProbabilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.RelativeCoulombProb;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.RelativeSlipRateProb;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.Shaw07JumpDistProb;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.AdaptiveClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.DistCutoffClosestSectClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ExhaustiveBilateralRuptureGrowingStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ExhaustiveBilateralRuptureGrowingStrategy.SecondaryVariations;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ExhaustiveUnilateralRuptureGrowingStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.PlausibleClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.RuptureGrowingStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.SectCountAdaptiveRuptureGrowingStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.GeoJSONFaultReader;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSysTools;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.simulators.stiffness.AggregatedStiffnessCache;
import org.opensha.sha.simulators.stiffness.AggregatedStiffnessCalculator;
import org.opensha.sha.simulators.stiffness.AggregatedStiffnessCalculator.AggregationMethod;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.PatchAlignment;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.StiffnessType;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Range;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;

import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;
import scratch.UCERF3.utils.DeformationModelFetcher;
import scratch.UCERF3.utils.U3FaultSystemIO;

public class RuptureSets {
	
	public static List<? extends FaultSection> getU3SubSects(FaultModels fm) {
		DeformationModels dm = fm.getFilterBasis();
		DeformationModelFetcher dmFetch = new DeformationModelFetcher(fm, dm, null, 0.1);
		return dmFetch.getSubSectionList();
	}
	
	public static List<? extends FaultSection> getNSHM23SubSects(String state) throws IOException {
		return GeoJSONFaultReader.buildNSHM23SubSects(state);
	}
	
	public static class U3RupSetConfig extends RupSetConfig {
		
		private List<? extends FaultSection> subSects;
		private FaultModels fm;
		private ScalingRelationships scale;

		public U3RupSetConfig(List<? extends FaultSection> subSects, ScalingRelationships scale) {
			init(subSects, scale);
		}
		
		private void detectFM(List<? extends FaultSection> subSects) {
			if (subSects.size() == 2606) {
				this.fm = FaultModels.FM3_1;
			} else if (subSects.size() == 2664) {
				this.fm = FaultModels.FM3_2;
			} else {
				System.err.println("WARNING: UCERF3 rupture set configuration was supplied with a non-UCERF3 fault model, "
						+ "disabling Coulomb (as it was precomputed for UCERF3 FM3.1 and FM3.2 only)");
				this.fm = null;
			}
		}

		public U3RupSetConfig(FaultModels fm, ScalingRelationships scale) {
			this.fm = fm;
			this.subSects = getU3SubSects(fm);
			this.scale = scale;
		}

		@Override
		public List<? extends FaultSection> getSubSects() {
			return subSects;
		}

		@Override
		public PlausibilityConfiguration getPlausibilityConfig() {
			try {
				return PlausibilityConfiguration.getUCERF3(subSects, getDistAzCalc(), fm);
			} catch (IOException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
		}

		@Override
		public RuptureGrowingStrategy getGrowingStrategy() {
			return new ExhaustiveUnilateralRuptureGrowingStrategy();
		}

		@Override
		public String getRupSetFileName() {
			String str;
			if (fm == null)
				str = subSects.size()+"sects";
			else
				str = fm.encodeChoiceString().toLowerCase();
			return str+"_reproduce_ucerf3.zip";
		}

		@Override
		public ScalingRelationships getScalingRelationship() {
			return scale;
		}

		@Override
		protected void init(List<? extends FaultSection> subSects, ScalingRelationships scale) {
			this.subSects = subSects;
			this.scale = scale;
			detectFM(subSects);
		}
		
	}
	
	public static class SimpleAzimuthalRupSetConfig extends RupSetConfig {
		
		private List<? extends FaultSection> subSects;
		private ScalingRelationships scale;
		
		@Expose	private float jumpAzimuthChange = 60f;
		@Expose	private float totalAzimuthChange = 60f;
		@Expose	private boolean leftLateralFlipAzimuth = true;
		@Expose	private float cumulativeAzimuthChange = 560f;
		@Expose	private float cumulativeRakeChange = 180f;
		@Expose	private int minSectsPerParent = 2;
		// maximum individual jump distance
		@Expose	private double maxJumpDist = 5d;
		// if nonzero, apply thinning to growing strategy
		@Expose	private float adaptiveSectFract = 0f;

		public SimpleAzimuthalRupSetConfig(List<? extends FaultSection> subSects, ScalingRelationships scale) {
			init(subSects, scale);
		}

		@Override
		protected void init(List<? extends FaultSection> subSects, ScalingRelationships scale) {
			this.subSects = subSects;
			this.scale = scale;
		}

		@Override
		public List<? extends FaultSection> getSubSects() {
			return subSects;
		}

		@Override
		public PlausibilityConfiguration getPlausibilityConfig() {
			ClusterConnectionStrategy connStrat = new DistCutoffClosestSectClusterConnectionStrategy(
					getSubSects(), getDistAzCalc(), maxJumpDist);
			Builder builder = PlausibilityConfiguration.builder(connStrat, subSects);
			if (minSectsPerParent > 0)
				builder.minSectsPerParent(minSectsPerParent, true, true);
			AzimuthCalc azCalc;
			if (leftLateralFlipAzimuth)
				azCalc = new JumpAzimuthChangeFilter.LeftLateralFlipAzimuthCalc(getDistAzCalc(), Range.closed(-45d, 45d));
			else
				azCalc = new JumpAzimuthChangeFilter.SimpleAzimuthCalc(getDistAzCalc());
			if (jumpAzimuthChange > 0f)
				builder.jumpAzChange(azCalc, jumpAzimuthChange);
			if (totalAzimuthChange > 0f)
				builder.totAzChange(azCalc, totalAzimuthChange, true, false);
			if (cumulativeAzimuthChange > 0f)
				builder.cumulativeAzChange(cumulativeAzimuthChange);
			if (cumulativeRakeChange > 0f)
				builder.cumulativeRakeChange(cumulativeRakeChange);
			return builder.build();
		}

		@Override
		public RuptureGrowingStrategy getGrowingStrategy() {
			RuptureGrowingStrategy strat = new ExhaustiveUnilateralRuptureGrowingStrategy();
			if (adaptiveSectFract > 0f)
				strat = new SectCountAdaptiveRuptureGrowingStrategy(strat, adaptiveSectFract, true, minSectsPerParent);
			return strat;
		}

		@Override
		public String getRupSetFileName() {
			List<String> elements = new ArrayList<>();
			if (jumpAzimuthChange > 0f)
				elements.add("jumpAz"+(int)jumpAzimuthChange);
			if (totalAzimuthChange > 0f)
				elements.add("totalAz"+(int)totalAzimuthChange);
			if (leftLateralFlipAzimuth && (jumpAzimuthChange > 0f || totalAzimuthChange > 0f))
				elements.add("llFlip");
			if (cumulativeAzimuthChange > 0f)
				elements.add("cmlAz"+(int)cumulativeAzimuthChange);
			if (cumulativeRakeChange > 0f)
				elements.add("cmlRake"+(int)cumulativeRakeChange);
			if (minSectsPerParent > 0)
				elements.add(minSectsPerParent+"sectsPerParent");
			return Joiner.on("_").join(elements)+".zip";
		}

		@Override
		public ScalingRelationships getScalingRelationship() {
			return scale;
		}
		
	}
	
	public static class CoulombRupSetConfig extends RupSetConfig {
		
		private List<? extends FaultSection> subSects;
		private String fmPrefix;
		private ScalingRelationships scale;
		
		/*
		 * Thresholds & params
		 */
		// PLAUSIBILITY FILTERS
		// minimum subsections per parent
		@Expose	private int minSectsPerParent = 2;
		// filters out indirect paths
		@Expose	private boolean noIndirectPaths = true;
		// relative slip rate probability
		@Expose	private float slipRateProb = 0.05f;
		// if false, slip rate probabilities only consider alternative jumps up to the distance (+2km) of the taken jump
		@Expose	private boolean slipIncludeLonger = false;
		// fraction of interactions positive
		@Expose	private float cffFractInts = 0.75f;
		// number of denominator values for the CFF favorability ratio
		@Expose	private int cffRatioN = 2;
		// CFF favorability ratio threshold
		@Expose	private float cffRatioThresh = 0.5f;
		// relative CFF probability
		@Expose	private float cffRelativeProb = 0.01f;
		// if true, CFF calculations are computed with the most favorable path (up to max jump distance), which may not
		// use the exact jumping point from the connection strategy
		@Expose	private boolean favorableJumps = true;
		// cumulative jump probability threshold
		@Expose	private float jumpProbThresh = 0.001f;
		// cumulative rake change threshold
		@Expose	private float cmlRakeThresh = 360f;
		// CONNECTION STRATEGY
		// maximum individual jump distance
		@Expose	private double maxJumpDist = 15d;
		// if true, connections happen at places that actually work and paths are optimized. if false, closest points
		@Expose	private boolean plausibleConnections = true;
		// if >0 and <maxDist, connections will only be added above this distance when no other connections exist from
		// a given subsection. e.g., if set to 5, you can jump more than 5 km but only if no <= 5km jumps exist
		@Expose	private double adaptiveMinDist = 6d;
		// GROWING STRATEGY
		// if nonzero, apply thinning to growing strategy
		@Expose	private float adaptiveSectFract = 0.1f;
		// if true, allow bilateral rupture growing (using default settings)
		@Expose	private boolean bilateral = false;
		// if true, allow splays (using default settings)
		@Expose	private boolean splays = false;
		// grid spacing for coulomb calculations
		@Expose	private double stiffGridSpacing = 2d;
		// coefficient of friction for coulomb calculations
		@Expose	private double coeffOfFriction = 0.5;

		public CoulombRupSetConfig(FaultModels fm, ScalingRelationships scale) {
			this(getU3SubSects(fm), fm.encodeChoiceString().toLowerCase(), scale);
		}

		public CoulombRupSetConfig(List<? extends FaultSection> subSects, String fmPrefix, ScalingRelationships scale) {
			this.fmPrefix = fmPrefix;
			init(subSects, scale);
		}

		@Override
		protected void init(List<? extends FaultSection> subSects, ScalingRelationships scale) {
			this.subSects = subSects;
			this.scale = scale;
		}

		@Override
		public List<? extends FaultSection> getSubSects() {
			return subSects;
		}

		@Override
		public synchronized  PlausibilityConfiguration getPlausibilityConfig() {
			if (config == null)
				update();
			return config;
		}

		@Override
		public synchronized RuptureGrowingStrategy getGrowingStrategy() {
			if (growingStrat == null)
				update();
			return growingStrat;
		}

		@Override
		public synchronized String getRupSetFileName() {
			if (fileName == null)
				update();
			return fileName;
		}

		@Override
		public ScalingRelationships getScalingRelationship() {
			return scale;
		}
		
		@Override
		public FaultSystemRupSet build(int numThreads) {
			FaultSystemRupSet rupSet = super.build(numThreads);
			
			if (stiffnessCache != null && stiffnessCacheFile != null
					&& stiffnessCacheSize < stiffnessCache.calcCacheSize()) {
				System.out.println("Writing stiffness cache to "+stiffnessCacheFile.getAbsolutePath());
				try {
					stiffnessCache.writeCacheFile(stiffnessCacheFile);
				} catch (IOException e) {
					e.printStackTrace();
				}
				System.out.println("DONE writing stiffness cache");
			}
			
			return rupSet;
		}

		public void setMinSectsPerParent(int minSectsPerParent) {
			clear();
			this.minSectsPerParent = minSectsPerParent;
		}

		public void setNoIndirectPaths(boolean noIndirectPaths) {
			clear();
			this.noIndirectPaths = noIndirectPaths;
		}

		public void setSlipRateProb(float slipRateProb) {
			clear();
			this.slipRateProb = slipRateProb;
		}

		public void setSlipIncludeLonger(boolean slipIncludeLonger) {
			clear();
			this.slipIncludeLonger = slipIncludeLonger;
		}

		public void setCffFractInts(float cffFractInts) {
			clear();
			this.cffFractInts = cffFractInts;
		}

		public void setCffRatioN(int cffRatioN) {
			clear();
			this.cffRatioN = cffRatioN;
		}

		public void setCffRatioThresh(float cffRatioThresh) {
			clear();
			this.cffRatioThresh = cffRatioThresh;
		}

		public void setCffRelativeProb(float cffRelativeProb) {
			clear();
			this.cffRelativeProb = cffRelativeProb;
		}

		public void setFavorableJumps(boolean favorableJumps) {
			clear();
			this.favorableJumps = favorableJumps;
		}

		public void setJumpProbThresh(float jumpProbThresh) {
			clear();
			this.jumpProbThresh = jumpProbThresh;
		}

		public void setCmlRakeThresh(float cmlRakeThresh) {
			clear();
			this.cmlRakeThresh = cmlRakeThresh;
		}

		public void setMaxJumpDist(double maxJumpDist) {
			clear();
			this.maxJumpDist = maxJumpDist;
		}

		public void setPlausibleConnections(boolean plausibleConnections) {
			clear();
			this.plausibleConnections = plausibleConnections;
		}

		public void setAdaptiveMinDist(double adaptiveMinDist) {
			clear();
			this.adaptiveMinDist = adaptiveMinDist;
		}

		public void setAdaptiveSectFract(float adaptiveSectFract) {
			clear();
			this.adaptiveSectFract = adaptiveSectFract;
		}

		public void setBilateral(boolean bilateral) {
			clear();
			this.bilateral = bilateral;
		}

		public void setSplays(boolean splays) {
			clear();
			this.splays = splays;
		}

		public void setStiffGridSpacing(double stiffGridSpacing) {
			clear();
			this.stiffGridSpacing = stiffGridSpacing;
		}

		public void setCoeffOfFriction(double coeffOfFriction) {
			clear();
			this.coeffOfFriction = coeffOfFriction;
		}
		
		private synchronized void clear() {
			config = null;
			growingStrat = null;
			fileName = null;
			stiffnessCache = null;
			stiffnessCacheFile = null;
			stiffnessCacheSize = -1;
		}

		private PlausibilityConfiguration config;
		private RuptureGrowingStrategy growingStrat;
		private String fileName;
		private File stiffnessCacheFile;
		private AggregatedStiffnessCache stiffnessCache; 
		private int stiffnessCacheSize;
		private synchronized void update() {
			// build stiffness calculator (used for new Coulomb)
			SubSectStiffnessCalculator stiffnessCalc = new SubSectStiffnessCalculator(
					subSects, stiffGridSpacing, 3e4, 3e4, coeffOfFriction, PatchAlignment.FILL_OVERLAP, 1d);
			stiffnessCache = stiffnessCalc.getAggregationCache(StiffnessType.CFF);
			
			File cacheDir = getCacheDir();
			if (cacheDir != null && cacheDir.exists()) {
				stiffnessCacheFile = new File(cacheDir, stiffnessCache.getCacheFileName());
				stiffnessCacheSize = 0;
				if (stiffnessCacheFile.exists()) {
					try {
						stiffnessCacheSize = stiffnessCache.loadCacheFile(stiffnessCacheFile);
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
			// common aggregators
			AggregatedStiffnessCalculator sumAgg = new AggregatedStiffnessCalculator(StiffnessType.CFF, stiffnessCalc, true,
					AggregationMethod.FLATTEN, AggregationMethod.SUM, AggregationMethod.SUM, AggregationMethod.SUM);
//			AggregatedStiffnessCalculator fractRpatchPosAgg = new AggregatedStiffnessCalculator(StiffnessType.CFF, stiffnessCalc, true,
//					AggregationMethod.SUM, AggregationMethod.PASSTHROUGH, AggregationMethod.RECEIVER_SUM, AggregationMethod.FRACT_POSITIVE);
//			AggregatedStiffnessCalculator threeQuarterInts = new AggregatedStiffnessCalculator(StiffnessType.CFF, stiffnessCalc, true,
//					AggregationMethod.NUM_POSITIVE, AggregationMethod.SUM, AggregationMethod.SUM, AggregationMethod.THREE_QUARTER_INTERACTIONS);
			AggregatedStiffnessCalculator fractIntsAgg = new AggregatedStiffnessCalculator(StiffnessType.CFF, stiffnessCalc, true,
					AggregationMethod.FLATTEN, AggregationMethod.NUM_POSITIVE, AggregationMethod.SUM, AggregationMethod.NORM_BY_COUNT);
			
			String outputName = fmPrefix == null || fmPrefix.isBlank() ? subSects.size()+"sects" : fmPrefix;
			
			if (stiffGridSpacing != 2d)
				outputName += "_stiff"+new DecimalFormat("0.#").format(stiffGridSpacing)+"km";
			if (coeffOfFriction != 0.5d)
				outputName += "_coeff"+(float)coeffOfFriction;
			
			SectionDistanceAzimuthCalculator distAzCalc = getDistAzCalc();
			
			/*
			 * Connection strategy: which faults are allowed to connect, and where?
			 */
			// use this for the exact same connections as UCERF3
//			double maxJumpDist = 5d;
//			ClusterConnectionStrategy connectionStrategy =
//					new UCERF3ClusterConnectionStrategy(subSects,
//							distAzCalc, maxJumpDist, CoulombRates.loadUCERF3CoulombRates(fm));
//			if (maxJumpDist != 5d)
//				outputName += "_"+new DecimalFormat("0.#").format(maxJumpDist)+"km";
			ClusterConnectionStrategy connectionStrategy;
			if (plausibleConnections) {
				// use this to pick connections which agree with your plausibility filters

				System.out.println("Building plausible connections w/ "+getNumThreads()+" threads...");
				// some filters need a connection strategy, use one that only includes immediate neighbors at this step
				DistCutoffClosestSectClusterConnectionStrategy neighborsConnStrat =
						new DistCutoffClosestSectClusterConnectionStrategy(subSects, distAzCalc, 0.1d);
				neighborsConnStrat.checkBuildThreaded(getNumThreads());
				List<PlausibilityFilter> connFilters = new ArrayList<>();
				if (cffRatioThresh > 0f) {
					connFilters.add(new CumulativeProbabilityFilter(cffRatioThresh, new CoulombSectRatioProb(
							sumAgg, cffRatioN, favorableJumps, (float)maxJumpDist, distAzCalc)));
					if (cffRelativeProb > 0f)
						connFilters.add(new PathPlausibilityFilter(
								new CumulativeProbPathEvaluator(cffRatioThresh, PlausibilityResult.FAIL_HARD_STOP,
										new CoulombSectRatioProb(sumAgg, cffRatioN, favorableJumps, (float)maxJumpDist, distAzCalc)),
								new CumulativeProbPathEvaluator(cffRelativeProb, PlausibilityResult.FAIL_HARD_STOP,
										new RelativeCoulombProb(sumAgg, neighborsConnStrat, false, true, favorableJumps, (float)maxJumpDist, distAzCalc))));
				} else if (cffRelativeProb > 0f) {
					connFilters.add(new CumulativeProbabilityFilter(cffRatioThresh, new RelativeCoulombProb(
							sumAgg, neighborsConnStrat, false, true, favorableJumps, (float)maxJumpDist, distAzCalc)));
				}
				if (cffFractInts > 0f)
					connFilters.add(new NetRuptureCoulombFilter(fractIntsAgg, cffFractInts));
				connectionStrategy = new PlausibleClusterConnectionStrategy(subSects, distAzCalc, maxJumpDist,
							PlausibleClusterConnectionStrategy.JUMP_SELECTOR_DEFAULT, connFilters);
				outputName += "_plausibleMulti"+new DecimalFormat("0.#").format(maxJumpDist)+"km";
//							PlausibleClusterConnectionStrategy.JUMP_SELECTOR_DEFAULT_SINGLE, connFilters);
//				outputName += "_plausible"+new DecimalFormat("0.#").format(maxJumpDist)+"km";
				connectionStrategy.checkBuildThreaded(getNumThreads());
				System.out.println("DONE building plausible connections");
			} else {
				// just use closest distance
				connectionStrategy = new DistCutoffClosestSectClusterConnectionStrategy(subSects, distAzCalc, maxJumpDist);
				if (maxJumpDist != 5d)
					outputName += "_"+new DecimalFormat("0.#").format(maxJumpDist)+"km";
			}
			if (adaptiveMinDist > 0d && adaptiveMinDist < maxJumpDist) {
				connectionStrategy = new AdaptiveClusterConnectionStrategy(connectionStrategy, adaptiveMinDist, 1);
				outputName += "_adaptive"+new DecimalFormat("0.#").format(adaptiveMinDist)+"km";
			}
			
			Builder configBuilder = PlausibilityConfiguration.builder(connectionStrategy, subSects);
			
			/*
			 * Plausibility filters: which ruptures (utilizing those connections) are allowed?
			 */
			
			/*
			 *  UCERF3 filters
			 */
//			configBuilder.u3All(CoulombRates.loadUCERF3CoulombRates(fm)); outputName += "_ucerf3";
			if (minSectsPerParent > 1) {
				configBuilder.minSectsPerParent(minSectsPerParent, true, true); // always do this one
			}
			if (noIndirectPaths) {
				configBuilder.noIndirectPaths(true);
				outputName += "_direct";
			}
//			configBuilder.u3Cumulatives(); outputName += "_u3Cml"; // cml rake and azimuth
//			configBuilder.cumulativeAzChange(560f); outputName += "_cmlAz"; // cml azimuth only
			if (cmlRakeThresh > 0) {
				configBuilder.cumulativeRakeChange(cmlRakeThresh);
				outputName += "_cmlRake"+(int)cmlRakeThresh; // cml rake only
			}
//			configBuilder.cumulativeRakeChange(270f); outputName += "_cmlRake270"; // cml rake only
//			configBuilder.cumulativeRakeChange(360f); outputName += "_cmlRake360"; // cml rake only
//			configBuilder.u3Azimuth(); outputName += "_u3Az";
//			configBuilder.u3Coulomb(CoulombRates.loadUCERF3CoulombRates(fm)); outputName += "_u3CFF";
			
			/*
			 * Cumulative jump prob
			 */
			// JUMP PROB: only increasing
			if (jumpProbThresh > 0f) {
				configBuilder.cumulativeProbability(jumpProbThresh, new Shaw07JumpDistProb(1d, 3d));
				outputName += "_jumpP"+jumpProbThresh;
			}
			// JUMP RATE PROB
			
			/*
			 * Regular slip prob
			 */
			// SLIP RATE PROB: only increasing
			if (slipRateProb > 0f) {
				configBuilder.cumulativeProbability(slipRateProb,
						new RelativeSlipRateProb(connectionStrategy, true, slipIncludeLonger));
				outputName += "_slipP"+slipRateProb+"incr";
				if (!slipIncludeLonger)
					outputName += "CapDist";
			}
			// END SLIP RATE PROB
			
			/*
			 * Regular CFF prob (not currently used)
			 */
			// CFF prob: allow neg, 0.01
//			configBuilder.cumulativeProbability(0.01f, new RelativeCoulombProb(
//					sumAgg, connectionStrategy, false, true, true));
//			outputName += "_cffP0.01incr";
			// END SLIP RATE PROB
			
			/*
			 *  CFF net rupture filters
			 */
			// FRACT INTERACTIONS POSITIVE
			if (cffFractInts > 0f) {
				configBuilder.netRupCoulomb(fractIntsAgg,
						Range.greaterThan(cffFractInts));
				outputName += "_cff"+cffFractInts+"IntsPos";
			}
			// END MAIN 3/4 INTERACTIONS POSITIVE
			
			/**
			 * Path filters
			 */
			List<NucleationClusterEvaluator> combPathEvals = new ArrayList<>();
			List<String> combPathPrefixes = new ArrayList<>();
			float fractPathsThreshold = 0f; String fractPathsStr = "";
			float favorableDist = Float.max((float)maxJumpDist, 10f);
			String favStr = "";
			if (favorableJumps) {
				favStr = "Fav";
				if (favorableDist != (float)maxJumpDist)
					favStr += (int)favorableDist;
			}
			// SLIP RATE PROB: as a path, only increasing NOT CURRENTLY PREFERRED
//			float pathSlipProb = 0.1f;
//			CumulativeJumpProbPathEvaluator slipEval = new CumulativeJumpProbPathEvaluator(
//					pathSlipProb, PlausibilityResult.FAIL_HARD_STOP, new RelativeSlipRateProb(connectionStrategy, true));
//			combPathEvals.add(slipEval); combPathPrefixes.add("slipP"+pathSlipProb+"incr");
////			configBuilder.path(slipEval); outputName += "_slipPathP"+pathSlipProb+"incr"; // do it separately
			// END SLIP RATE PROB
			// CFF PROB: as a path, allow negative, 0.01
			if (cffRelativeProb > 0f) {
				RelativeCoulombProb cffProbCalc = new RelativeCoulombProb(
						sumAgg, connectionStrategy, false, true, favorableJumps, favorableDist, distAzCalc);
				CumulativeProbPathEvaluator cffProbPathEval = new CumulativeProbPathEvaluator(
						cffRelativeProb, PlausibilityResult.FAIL_HARD_STOP, cffProbCalc);
				combPathEvals.add(cffProbPathEval); combPathPrefixes.add("cff"+favStr+"P"+cffRelativeProb);
			}
//			configBuilder.path(cffProbPathEval); outputName += "_cffPathP0.01"; // do it separately
			// CFF SECT PATH: relBest, 15km
//			SectCoulombPathEvaluator prefCFFSectPathEval = new SectCoulombPathEvaluator(
//					sumAgg, Range.atLeast(0f), PlausibilityResult.FAIL_HARD_STOP, true, 15f, distAzCalc);
//			combPathEvals.add(prefCFFSectPathEval); combPathPrefixes.add("cffSPathFav15");
////			configBuilder.path(prefCFFSectPathEval); outputName += "_cffSPathFav15"; // do it separately
			// END CFF SECT PATH
			// CFF CLUSTER PATH: half RPatches positive
//			ClusterCoulombPathEvaluator prefCFFRPatchEval = new ClusterCoulombPathEvaluator(
//					fractRpatchPosAgg, Range.atLeast(0.5f), PlausibilityResult.FAIL_HARD_STOP);
//			combPathEvals.add(prefCFFRPatchEval); combPathPrefixes.add("cffCPathRPatchHalfPos");
////			configBuilder.path(prefCFFRPatchEval); outputName += "_cffCPathRPatchHalfPos"; // do it separately
			// END CFF CLUSTER PATH
			// CFF RATIO PATH: N=2, relBest, 15km
			if (cffRatioThresh > 0f) {
				CumulativeProbPathEvaluator cffRatioPatchEval = new CumulativeProbPathEvaluator(cffRatioThresh,
						PlausibilityResult.FAIL_HARD_STOP,
						new CoulombSectRatioProb(sumAgg, cffRatioN, favorableJumps, favorableDist, distAzCalc));
				combPathEvals.add(cffRatioPatchEval);
				combPathPrefixes.add("cff"+favStr+"RatioN"+cffRatioN+"P"+cffRatioThresh);
			}
//			configBuilder.path(prefCFFRPatchEval); outputName += "_cffCPathRPatchHalfPos"; // do it separately
			// END CFF RATIO PATH
			// add them
			Preconditions.checkState(combPathEvals.size() == combPathPrefixes.size());
			if (!combPathEvals.isEmpty()) {
				configBuilder.path(fractPathsThreshold, combPathEvals.toArray(new NucleationClusterEvaluator[0]));
				outputName += "_";
				if (combPathEvals.size() > 1)
					outputName += "comb"+combPathEvals.size();
				outputName += fractPathsStr;
				if (fractPathsStr.isEmpty() && combPathEvals.size() == 1) {
					outputName += "path";
				} else {
					outputName += "Path";
					if (combPathEvals.size() > 1)
						outputName += "s";
				}
				outputName += "_"+Joiner.on("_").join(combPathPrefixes);
			}
			
			/*
			 * Splay constraints
			 */
			if (splays) {
				configBuilder.maxSplays(1); outputName += "_max1Splays";
				//configBuilder.splayLength(0.1, true, true); outputName += "_splayLenFract0.1";
				//configBuilder.splayLength(100, false, true, true); outputName += "_splayLen100km";
				configBuilder.splayLength(50, false, true, true); outputName += "_splayLen50km";
				configBuilder.splayLength(.5, true, true, true); outputName += "OrHalf";
				configBuilder.addFirst(new SplayConnectionsOnlyFilter(connectionStrategy, true)); outputName += "_splayConn";
			} else {
				configBuilder.maxSplays(0); // default, no splays
			}
			
			/*
			 * Growing strategies: how should ruptures be broken up and spread onto new faults
			 */
			if (bilateral) {
				growingStrat = new ExhaustiveBilateralRuptureGrowingStrategy(
						SecondaryVariations.EQUAL_LEN, false);
				outputName += "_bilateral";
			} else {
				growingStrat = new ExhaustiveUnilateralRuptureGrowingStrategy();
			}
			if (adaptiveSectFract > 0f) {
				SectCountAdaptiveRuptureGrowingStrategy adaptiveStrat = new SectCountAdaptiveRuptureGrowingStrategy(
						growingStrat, adaptiveSectFract, true, minSectsPerParent);
				configBuilder.add(adaptiveStrat.buildConnPointCleanupFilter(connectionStrategy));
				outputName += "_sectFractGrow"+adaptiveSectFract;
				growingStrat = adaptiveStrat;
			}
			
			// build our configuration
			config = configBuilder.build();
			outputName += ".zip";
			fileName = outputName;
		}
		
	}
	
	public static abstract class RupSetConfig {
		
		public abstract List<? extends FaultSection> getSubSects();
		
		public abstract PlausibilityConfiguration getPlausibilityConfig();
		
		public abstract RuptureGrowingStrategy getGrowingStrategy();
		
		public abstract String getRupSetFileName();
		
		public abstract ScalingRelationships getScalingRelationship();
		
		protected abstract void init(List<? extends FaultSection> subSects, ScalingRelationships scale);
		
		private transient File cacheDir;
		
		private transient File distAzCacheFile;
		private transient int numAzCached = 0;
		private transient int numDistCached = 0;
		private transient SectionDistanceAzimuthCalculator distAzCalc;
		public synchronized SectionDistanceAzimuthCalculator getDistAzCalc() {
			if (distAzCalc == null) {
				List<? extends FaultSection> sects = getSubSects();
				distAzCalc = new SectionDistanceAzimuthCalculator(sects);
				File cacheDir = getCacheDir();
				if (cacheDir != null && cacheDir.exists()) {
					String name = distAzCalc.getDefaultCacheFileName();
					distAzCacheFile = new File(cacheDir, name);
					if (distAzCacheFile.exists()) {
						try {
							distAzCalc.loadCacheFile(distAzCacheFile);
							numAzCached = distAzCalc.getNumCachedAzimuths();
							numDistCached = distAzCalc.getNumCachedDistances();
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				}
			}
			return distAzCalc;
		}
		
		protected File getCacheDir() {
			if (cacheDir == null)
				cacheDir = FaultSysTools.getCacheDir();
			return cacheDir;
		}
		
		protected void setCacheDir(File cacheDir) {
			this.cacheDir = cacheDir;
		}
		
		private transient int numThreads = 1;
		
		protected int getNumThreads() {
			return numThreads;
		}
		
		public FaultSystemRupSet build(int numThreads) {
			this.numThreads = numThreads;
			PlausibilityConfiguration config = getPlausibilityConfig();
			// force it to build clusters
			System.out.println("Initializing connections w/ "+numThreads+" threads...");
			if (numThreads > 1)
				config.getConnectionStrategy().checkBuildThreaded(numThreads);
			else
				config.getConnectionStrategy().getClusters();
			if (distAzCacheFile != null && (numAzCached < distAzCalc.getNumCachedAzimuths()
					|| numDistCached < distAzCalc.getNumCachedDistances())) {
				System.out.println("Writing dist/az cache to "+distAzCacheFile.getAbsolutePath());
				try {
					distAzCalc.writeCacheFile(distAzCacheFile);
					numAzCached = distAzCalc.getNumCachedAzimuths();
					numDistCached = distAzCalc.getNumCachedDistances();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			ClusterRuptureBuilder builder = new ClusterRuptureBuilder(config);
			System.out.println("Building ruptures with "+numThreads+" threads...");
			Stopwatch watch = Stopwatch.createStarted();
			List<ClusterRupture> rups = builder.build(getGrowingStrategy(), numThreads);
			watch.stop();
			long millis = watch.elapsed(TimeUnit.MILLISECONDS);
			double secs = millis/1000d;
			double mins = (secs / 60d);
			DecimalFormat timeDF = new DecimalFormat("0.00");
			System.out.println("Built "+ClusterRuptureBuilder.countDF.format(rups.size())+" ruptures in "+timeDF.format(secs)
				+" secs = "+timeDF.format(mins)+" mins. Total rate: "+ClusterRuptureBuilder.rupRateStr(rups.size(), millis));
			
			if (distAzCacheFile != null && (numAzCached < distAzCalc.getNumCachedAzimuths()
					|| numDistCached < distAzCalc.getNumCachedDistances())) {
				System.out.println("Writing dist/az cache to "+distAzCacheFile.getAbsolutePath());
				try {
					distAzCalc.writeCacheFile(distAzCacheFile);
				} catch (IOException e) {
					e.printStackTrace();
				}
				System.out.println("DONE writing dist/az cache");
			}
			
			return ClusterRuptureBuilder.buildClusterRupSet(getScalingRelationship(),
					getSubSects(), getPlausibilityConfig(), rups); 
		}
	}
	
	static RupSetConfig deserializeConfig(Class<? extends RupSetConfig> clazz, List<? extends FaultSection> subSects,
			ScalingRelationships scale, File jsonFile) throws IOException {
		return deserializeConfig(clazz, subSects, scale, new BufferedReader(new FileReader(jsonFile)));
	}
	
	static RupSetConfig deserializeConfig(Class<? extends RupSetConfig> clazz, List<? extends FaultSection> subSects,
			ScalingRelationships scale, Reader jsonReader) {
		Gson gson = buildGson();
		
		RupSetConfig config = gson.fromJson(jsonReader, clazz);
		config.init(subSects, scale);
		return config;
	}
	
	static void serializeConfig(RupSetConfig config, File jsonFile) throws IOException {
		serializeConfig(config, new BufferedWriter(new FileWriter(jsonFile)));
	}
	
	static void serializeConfig(RupSetConfig config, Writer jsonWriter) throws IOException {
		Gson gson = buildGson();
		
		gson.toJson(config, jsonWriter);
		
		jsonWriter.close();
	}
	
	private static Gson buildGson() {
		return new GsonBuilder().setPrettyPrinting().excludeFieldsWithoutExposeAnnotation().create();
	}
	
	private enum Presets {
		UCERF3(U3RupSetConfig.class) {
			@Override
			public RupSetConfig build(List<? extends FaultSection> subSects, ScalingRelationships scale) {
				return new U3RupSetConfig(subSects, scale);
			}
		},
		COULOMB(CoulombRupSetConfig.class) {
			@Override
			public RupSetConfig build(List<? extends FaultSection> subSects, ScalingRelationships scale) {
				return new CoulombRupSetConfig(subSects, null, scale);
			}
		},
		SIMPLE_AZIMUTHAL(SimpleAzimuthalRupSetConfig.class) {
			@Override
			public RupSetConfig build(List<? extends FaultSection> subSects, ScalingRelationships scale) {
				return new SimpleAzimuthalRupSetConfig(subSects, scale);
			}
		};
		
		private Class<? extends RupSetConfig> configClass;

		private Presets(Class<? extends RupSetConfig> configClass) {
			this.configClass = configClass;
			
		}
		
		public abstract RupSetConfig build(List<? extends FaultSection> subSects, ScalingRelationships scale);
		
		public RupSetConfig deserialize(List<? extends FaultSection> subSects, ScalingRelationships scale,
				Reader jsonReader) {
			return deserializeConfig(configClass, subSects, scale, jsonReader);
		}
		
		public RupSetConfig deserialize(List<? extends FaultSection> subSects, ScalingRelationships scale,
				File jsonFile) throws IOException {
			return deserializeConfig(configClass, subSects, scale, jsonFile);
		}
	}
	
	private static Options createOptions() {
		Options ops = new Options();

		ops.addOption(FaultSysTools.threadsOption());

		Option subSectsOption = new Option("s", "sub-sections", true,
				"Path to GeoJSON file containing subsections from which to build a rupture set. Must supply this or a "
				+ "UCERF3 fault model (via --fault-model)");
		subSectsOption.setRequired(false);
		ops.addOption(subSectsOption);

		Option faultModelOption = new Option("f", "fault-model", true,
				"UCERF3 Fault Model, used to fetch UCERF3 subsections as an alternative to --sub-sections. "
				+ "Options: "+FaultSysTools.enumOptions(FaultModels.class));
		faultModelOption.setRequired(false);
		ops.addOption(faultModelOption);

		Option scaleOption = new Option("sc", "scale", true,
				"Scaling relationship to use (for rupture magnitudes & average slips). "
				+ "Options: "+FaultSysTools.enumOptions(ScalingRelationships.class));
		scaleOption.setRequired(true);
		ops.addOption(scaleOption);

		Option presetOption = new Option("p", "preset", true,
				"Rupture set plausibility configuration preset. "
				+ "Options: "+FaultSysTools.enumOptions(Presets.class));
		presetOption.setRequired(true);
		ops.addOption(presetOption);

		Option configOption = new Option("c", "config", true,
				"Rupture set plausibility configuration JSON file to override default parameters for the selected preset.");
		configOption.setRequired(false);
		ops.addOption(configOption);

		Option wcOption = new Option("wc", "write-config", false,
				"Flag to write the default configuration JSON file for the given preset instead of a rupture set, which can "
				+ "then be edited and passed back in with the --config option when building a rupture set.");
		wcOption.setRequired(false);
		ops.addOption(wcOption);

		Option outputOption = new Option("of", "output-file", true,
				"Path to write output Fault System Rupture Set file. If the supplied path is a directory, then a file "
				+ "name will be determined programatically and placed in that directory.");
		outputOption.setRequired(true);
		ops.addOption(outputOption);
		
		ops.addOption(FaultSysTools.cacheDirOption());
		
		return ops;
	}
	
	private static void writePresetDefaults(Presets preset, File file) throws IOException {
		if (file.exists() && file.isDirectory())
			file = new File(file, preset.name()+".json");
		System.out.println("Writing default configuration for "+preset+" to: "+file.getAbsolutePath());
		ScalingRelationships scale = ScalingRelationships.MEAN_UCERF3;
		List<? extends FaultSection> subSects = List.of();
		RupSetConfig config = preset.build(subSects, scale);
		serializeConfig(config, file);
	}
	
	public static void main(String[] args) {
		CommandLine cmd = FaultSysTools.parseOptions(createOptions(), args, RuptureSets.class);
		
		try {
			Presets preset = Presets.valueOf(cmd.getOptionValue("preset").trim().toUpperCase());
			System.out.println("Rupture plausibility preset: "+preset);
			
			File outputFile = new File(cmd.getOptionValue("output-file"));
			
			if (cmd.hasOption("write-config")) {
				writePresetDefaults(preset, outputFile);
				System.exit(0);
			}
//			writePresetDefaults(new File("/tmp/presets")); System.exit(0);
			List<? extends FaultSection> sects;
			if (cmd.hasOption("sub-sections")) {
				Preconditions.checkArgument(!cmd.hasOption("fault-model"), "Shouldn't supply both --sub-sections and --fault-model");
				File inputFile = new File(cmd.getOptionValue("sub-sections"));
				Preconditions.checkState(inputFile.exists(), "Input file doesn't exist: %s", inputFile.getAbsolutePath());
				
				String fName = inputFile.getName().trim().toLowerCase();
				if (fName.endsWith(".xml")) {
					// old XML file
					Document doc = XMLUtils.loadDocument(inputFile);
					sects = U3FaultSystemIO.fsDataFromXML(doc.getRootElement());
				} else {
					if (!fName.endsWith(".json") && !fName.endsWith(".geojson"))
						System.err.println("Warning: expected a GeoJSON file, but input file has an unexpected "
								+ "extension: "+inputFile.getName()
								+ "\nWill attemp to parse as GeoJSON anyway. See file format details at "
								+ "https://opensha.org/Geospatial-File-Formats");
					sects = GeoJSONFaultReader.readFaultSections(inputFile);
				}
			} else {
				Preconditions.checkArgument(cmd.hasOption("fault-model"), "Must supply either --sub-sections or --fault-model");
				String fmStr = cmd.getOptionValue("fault-model");
				FaultModels fm = FaultModels.valueOf(fmStr.trim().toUpperCase());
				Preconditions.checkNotNull(fm, "Unknown fault model: %s", fmStr);
				sects = getU3SubSects(fm);
			}
			System.out.println("Loaded "+sects.size()+" sub-sections");
			for (int i=0; i<sects.size(); i++) {
				int id = sects.get(i).getSectionId();
				Preconditions.checkState(id == i, "Subsections must be listed in order of increasing ID, and IDs must "
						+ "be 0-based and contiguous. Bad ID=%s at index=%s.", id, i);
			}
			
			Preconditions.checkArgument(cmd.hasOption("scale"), "Must supply scaling relationship (via --scale option)");
			ScalingRelationships scale = ScalingRelationships.valueOf(cmd.getOptionValue("scale"));
			System.out.println("Scaling relationship: "+scale);
			
			RupSetConfig config;
			if (cmd.hasOption("config")) {
				// load a custom configuration
				File jsonFile = new File(cmd.getOptionValue("config"));
				Preconditions.checkArgument(jsonFile.exists(), "JSON configuration file doesn't exist: %s", jsonFile.getAbsolutePath());
				config = preset.deserialize(sects, scale, jsonFile);
			} else {
				// use default values
				config = preset.build(sects, scale);
			}
			FaultSystemRupSet rupSet = config.build(FaultSysTools.getNumThreads(cmd));
			
			if (outputFile.exists() && outputFile.isDirectory())
				outputFile = new File(outputFile, config.getRupSetFileName());
			rupSet.write(outputFile);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

}
