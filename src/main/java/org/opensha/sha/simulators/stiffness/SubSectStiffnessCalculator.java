package org.opensha.sha.simulators.stiffness;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.zip.ZipException;

import org.dom4j.DocumentException;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.utm.UTM;
import org.opensha.commons.util.DataUtils;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.IDPairing;
import org.opensha.sha.earthquake.FocalMechanism;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.faultSurface.EvenlyGriddedSurface;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.simulators.stiffness.StiffnessCalc.Patch;

import com.google.common.base.Preconditions;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.primitives.Doubles;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.utils.FaultSystemIO;

public class SubSectStiffnessCalculator {
	
	private List<? extends FaultSection> subSects;
	
	private int utmZone;
	private char utmChar;
	
	private transient Map<FaultSection, List<Patch>> patchesMap;
	
	private transient LoadingCache<IDPairing, StiffnessResult[]> subSectStiffnessCache;

	private double gridSpacing;
	private double lameLambda;
	private double lameMu;
	private double coeffOfFriction;
	
	public enum StiffnessType {
		SIGMA("ΔSigma", "&Delta;Sigma", "MPa"),
		TAU("ΔTau", "&Delta;Tau", "MPa"),
		CFF("ΔCFF", "&Delta;CFF", "MPa");
		
		private String name;
		private String html;
		private String units;
		
		private StiffnessType(String name, String html, String units) {
			this.name = name;
			this.html = html;
			this.units = units;
		}
		
		@Override
		public String toString() {
			return name+" ("+units+")";
		}
		
		public String getName() {
			return name;
		}
		
		public String getHTML() {
			return html;
		}
		
		public String getUnits() {
			return units;
		}
	}
	
	public enum StiffnessAggregationMethod {
		MEAN("Mean", "Mean stiffness across all patch-to-patch calculations"),
		MEDIAN("Median", "Median stiffness across all patch-to-patch calculations"),
		MIN("Min", "Minimum individual stiffness across all patch-to-patch calculations"),
		MAX("Max", "Maximum individual stiffness across all patch-to-patch calculations"),
		FRACT_POSITIVE("Fract Positive", "The fraction of individual patch-to-patch stiffness "
				+ "calculations which are &ge;0");
		
		private String name;
		private String description;
		private StiffnessAggregationMethod(String name, String description) {
			this.name = name;
			this.description = description;
		}
		
		@Override
		public String toString() {
			return name;
		}
		
		public String getDescription() {
			return description;
		}
	}

	public SubSectStiffnessCalculator(List<? extends FaultSection> subSects, double gridSpacing,
			double lameLambda, double lameMu, double coeffOfFriction) {
		this.subSects = subSects;
		this.gridSpacing = gridSpacing;
		this.lameLambda = lameLambda;
		this.lameMu = lameMu;
		this.coeffOfFriction = coeffOfFriction;
		
		MinMaxAveTracker latTrack = new MinMaxAveTracker();
		MinMaxAveTracker lonTrack = new MinMaxAveTracker();
		for (FaultSection sect : subSects) {
			for (Location loc : sect.getFaultTrace()) {
				latTrack.addValue(loc.getLatitude());
				lonTrack.addValue(loc.getLongitude());
			}
		}
		
		double centerLat = latTrack.getMin() + 0.5*(latTrack.getMax() - latTrack.getMin());
		double centerLon = lonTrack.getMin() + 0.5*(lonTrack.getMax() - lonTrack.getMin());
		utmZone = UTM.calcZone(centerLon);
		utmChar = UTM.calcLetter(centerLat);
		System.out.println("UTM zone: "+utmZone+" "+utmChar);
	}
	
	private void checkInit() {
		if (subSectStiffnessCache == null) {
			synchronized (this) {
				if (subSectStiffnessCache != null)
					return;
				System.out.println("Building source patches...");
				patchesMap = new HashMap<>();
				MinMaxAveTracker patchCountTrack = new MinMaxAveTracker();
				for (FaultSection sect : subSects) {
					RuptureSurface surf = sect.getFaultSurface(gridSpacing, false, false);
					Preconditions.checkState(surf instanceof EvenlyGriddedSurface);
					EvenlyGriddedSurface gridSurf = (EvenlyGriddedSurface)surf;
					
					double aveDip = surf.getAveDip();
					double aveRake = sect.getAveRake();
					
					MinMaxAveTracker lenTrack = new MinMaxAveTracker();
					MinMaxAveTracker widthTrack = new MinMaxAveTracker();
					MinMaxAveTracker strikeTrack = new MinMaxAveTracker();
					
					List<Patch> myPatches = new ArrayList<>();
					
					for (int row=0; row<gridSurf.getNumRows()-1; row++) {
						for (int col=0; col<gridSurf.getNumCols()-1; col++) {
							Location loc1 = gridSurf.getLocation(row, col);
							Location loc2 = gridSurf.getLocation(row, col + 1);
							Location loc3 = gridSurf.getLocation(row + 1, col);
							Location loc4 = gridSurf.getLocation(row + 1, col + 1);
							double locLat = (loc1.getLatitude() + loc2.getLatitude() +
									loc3.getLatitude() +
									loc4.getLatitude()) / 4;
							double locLon = (loc1.getLongitude() + loc2.getLongitude() +
									loc3.getLongitude() +
									loc4.getLongitude()) / 4;
							double locDepth = (loc1.getDepth() + loc2.getDepth() + loc3.getDepth() +
									loc4.getDepth()) / 4;
							double strike = LocationUtils.azimuth(loc1, loc2);
							Location center = new Location(locLat, locLon, locDepth);
							FocalMechanism mech = new FocalMechanism(strike, aveDip, aveRake);
//							double length = LocationUtils.horzDistanceFast(loc1, loc2)*1000d;
//							double width = LocationUtils.vertDistance(loc1, loc3)*1000d;
							// use the input grid spacing instead. this will be less than or equal
							// to the full grid spacing, and ensures that the moment is identical for
							// all patches across all subsections
							double length = gridSpacing*1000d; // km -> m
							double width = length;
							strikeTrack.addValue(strike);
							lenTrack.addValue(length);
							widthTrack.addValue(width);
							myPatches.add(new Patch(center, utmZone, utmChar, length, width, mech));
						}
					}
					Preconditions.checkState(myPatches.size() > 0, "must have at least 1 patch");
					patchCountTrack.addValue(myPatches.size());
//					System.out.println(sect.getSectionName()+" has "+myPatches.size()
//						+" patches, aveStrike="+sect.getFaultTrace().getAveStrike());
//					System.out.println("\tStrike range: "+strikeTrack);
//					System.out.println("\tLength range: "+lenTrack);
//					System.out.println("\tWidth range: "+widthTrack);
					patchesMap.put(sect, myPatches);
				}
				System.out.println("Patch stats: "+patchCountTrack);
				
				int concurLevel = Runtime.getRuntime().availableProcessors();
				concurLevel = Integer.max(concurLevel, 4);
				concurLevel = Integer.min(concurLevel, 20);
				LoadingCache<IDPairing, StiffnessResult[]> cache = CacheBuilder.newBuilder()
						.concurrencyLevel(concurLevel).build(
						new CacheLoader<IDPairing, StiffnessResult[]>() {

					@Override
					public StiffnessResult[] load(IDPairing key) throws Exception {
						List<Patch> sourcePatches = patchesMap.get(subSects.get(key.getID1()));
						List<Patch> receiverPatches = patchesMap.get(subSects.get(key.getID2()));
						
						int count = sourcePatches.size()*receiverPatches.size();
						
						double[] sigmaVals = new double[count];
						double[] tauVals = new double[count];
						double[] cffVals = new double[count];
						
						int index = 0;
						for (Patch source : sourcePatches) {
							for (Patch receiver : receiverPatches) {
								double[] stiffness = StiffnessCalc.calcStiffness(
										lameLambda, lameMu, source, receiver);
								if (stiffness == null) {
									sigmaVals[index] = Double.NaN;
									tauVals[index] = Double.NaN;
									cffVals[index] = Double.NaN;
								} else {
									sigmaVals[index] = stiffness[0];
									tauVals[index] = stiffness[1];
									cffVals[index] = StiffnessCalc.calcCoulombStress(
											stiffness[1], stiffness[0], coeffOfFriction);
								}
								index++;
							}
						}
						
						return new StiffnessResult[] {
								new StiffnessResult(key.getID1(), key.getID2(), sigmaVals, StiffnessType.SIGMA),
								new StiffnessResult(key.getID1(), key.getID2(), tauVals, StiffnessType.TAU),
								new StiffnessResult(key.getID1(), key.getID2(), cffVals, StiffnessType.CFF)
						};
					}
					
				});
				this.subSectStiffnessCache = cache;
			}
		}
	}
	
	/**
	 * Calculates stiffness between the given sub sections
	 * 
	 * @param sourceSect
	 * @param receiverSect
	 * @return { sigma, tau, cff }
	 */
	public StiffnessResult[] calcStiffness(FaultSection sourceSect, FaultSection receiverSect) {
		checkInit();
		try {
			return subSectStiffnessCache.get(
					new IDPairing(sourceSect.getSectionId(), receiverSect.getSectionId()));
		} catch (ExecutionException e) {
			throw ExceptionUtils.asRuntimeException(e);
		}
	}
	
	/**
	 * @return calculated UTM zone for the center of the fault region
	 */
	public int getUTMZone() {
		return utmZone;
	}

	/**
	 * @return calculated UTM letter for the center of the fault region
	 */
	public char getUTMLetter() {
		return utmChar;
	}

	/**
	 * @return grid spacing used to subdivide subsections into patches for stiffness calculations (km)
	 */
	public double getGridSpacing() {
		return gridSpacing;
	}

	/**
	 * @return Lame lambda (first parameter) MPa
	 */
	public double getLameLambda() {
		return lameLambda;
	}

	/**
	 * Lame mu (second parameter, shear modulus) in MPa
	 * @return
	 */
	public double getLameMu() {
		return lameMu;
	}
	
	/**
	 * @return coefficient of friction used in Coulomb stress change calculations
	 */
	public double getCoeffOfFriction() {
		return coeffOfFriction;
	}

	/**
	 * Calculates stiffness between the given parent sections
	 * 
	 * @param sourceSect
	 * @param receiverSect
	 * @return { sigma, tau, cff }
	 */
	public StiffnessResult[] calcParentStiffness(int sourceParentID, int receiverParentID) {
		List<FaultSection> sourceSects = new ArrayList<>();
		List<FaultSection> receiverSects = new ArrayList<>();
		for (FaultSection sect : subSects) {
			int parentID = sect.getParentSectionId();
			if (parentID == sourceParentID)
				sourceSects.add(sect);
			if (parentID == receiverParentID)
				receiverSects.add(sect);
		}
		return calcCombinedStiffness(sourceSects, receiverSects, sourceParentID, receiverParentID);
	}
	
	/**
	 * Calculates stiffness between the given clusters
	 * 
	 * @param sourceSect
	 * @param receiverSect
	 * @return { sigma, tau, cff }
	 */
	public StiffnessResult[] calcClusterStiffness(FaultSubsectionCluster sourceCluster,
			FaultSubsectionCluster receiverCluster) {
		return calcCombinedStiffness(sourceCluster.subSects, receiverCluster.subSects,
				sourceCluster.parentSectionID, receiverCluster.parentSectionID);
	}
	
	/**
	 * Calculates aggregated stiffness from the given rupture to the given clusters
	 * 
	 * @param sourceSect
	 * @param receiverSect
	 * @return { sigma, tau, cff }
	 */
	public StiffnessResult[] calcAggRupToClusterStiffness(ClusterRupture sourceRupture,
			FaultSubsectionCluster receiverCluster) {
		List<FaultSection> sourceSects = new ArrayList<>();
		for (FaultSubsectionCluster cluster : sourceRupture.getClustersIterable())
			if (cluster != receiverCluster)
				sourceSects.addAll(cluster.subSects);
		return calcCombinedStiffness(sourceSects, receiverCluster.subSects,
				-1, receiverCluster.parentSectionID);
	}
	
	/**
	 * Calculates aggregated stiffness from the given set of clusters to the given cluster
	 * 
	 * @param sourceSect
	 * @param receiverSect
	 * @return { sigma, tau, cff }
	 */
	public StiffnessResult[] calcAggClustersToClusterStiffness(Collection<FaultSubsectionCluster> sourceClusters,
			FaultSubsectionCluster receiverCluster) {
		List<FaultSection> sourceSects = new ArrayList<>();
		for (FaultSubsectionCluster cluster : sourceClusters)
			if (cluster != receiverCluster)
				sourceSects.addAll(cluster.subSects);
		return calcCombinedStiffness(sourceSects, receiverCluster.subSects,
				-1, receiverCluster.parentSectionID);
	}
	
	private StiffnessResult[] calcCombinedStiffness(List<FaultSection> sources, List<FaultSection> receivers,
			int sourceID, int receiverID) {
		List<StiffnessResult> sigmaResults = new ArrayList<>();
		List<StiffnessResult> tauResults = new ArrayList<>();
		List<StiffnessResult> cffResults = new ArrayList<>();
		checkInit();
		for (FaultSection source : sources) {
			for (FaultSection receiver : receivers) {
				try {
					StiffnessResult[] myResults = subSectStiffnessCache.get(
							new IDPairing(source.getSectionId(), receiver.getSectionId()));
					sigmaResults.add(myResults[0]);
					tauResults.add(myResults[1]);
					cffResults.add(myResults[2]);
				} catch (ExecutionException e) {
					throw ExceptionUtils.asRuntimeException(e);
				}
			}
		}
		
		return new StiffnessResult[] { new StiffnessResult(sourceID, receiverID, sigmaResults),
				new StiffnessResult(sourceID, receiverID, tauResults),
				new StiffnessResult(sourceID, receiverID, cffResults) };
	}
	
	public double getValue(StiffnessResult[] stiffness, StiffnessType type, StiffnessAggregationMethod quantity) {
		if (type == StiffnessType.SIGMA)
			return getValue(stiffness[0], quantity);
		if (type == StiffnessType.TAU)
			return getValue(stiffness[1], quantity);
		// CFF
		Preconditions.checkState(type == StiffnessType.CFF);
		return getValue(stiffness[2], quantity);
	}
	
	public static double getValue(StiffnessResult stiffness, StiffnessAggregationMethod quantity) {
		switch (quantity) {
		case MEAN:
			return stiffness.mean;
		case MEDIAN:
			return stiffness.median;
		case MIN:
			return stiffness.min;
		case MAX:
			return stiffness.max;
		case FRACT_POSITIVE:
			return stiffness.fractPositive;

		default:
			throw new IllegalStateException("Unexpected quantity: "+quantity);
		}
	}
	
	public class StiffnessResult {
		public final int sourceID;
		public final int receiverID;
		public final double mean;
		public final double median;
		public final double min;
		public final double max;
		public final double fractPositive;
		public final double fractSingular;
		public final StiffnessType type;
		public final int numValues;
		
		public StiffnessResult(int sourceID, int receiverID,
				double[] vals, StiffnessType type) {
			super();
			this.sourceID = sourceID;
			this.receiverID = receiverID;
			this.type = type;
			int numSingular = 0;
			double mean = 0d;
			double min = Double.POSITIVE_INFINITY;
			double max = Double.NEGATIVE_INFINITY;
			double fractPositive = 0d;
			List<Double> nonSingularSortedVals = new ArrayList<>();
			for (double val : vals) {
				if (Double.isNaN(val)) {
					numSingular++;
				} else {
					min = Math.min(min, val);
					max = Math.max(max, val);
					mean += val;
					if (val > 0)
						fractPositive += 1d;
					int index = Collections.binarySearch(nonSingularSortedVals, val);
					if (index < 0)
						index = -(index+1);
					nonSingularSortedVals.add(index, val);
				}
			}
			int nonSingular = vals.length - numSingular;
			fractPositive /= (double)vals.length;
			this.mean = mean/(double)nonSingular;
			this.median = DataUtils.median_sorted(Doubles.toArray(nonSingularSortedVals));
			this.min = min;
			this.max = max;
			this.fractPositive = fractPositive;
			this.fractSingular = (double)numSingular/(double)vals.length;
			this.numValues = vals.length;
		}
		
		public StiffnessResult(int sourceID, int receiverID,
				List<StiffnessResult> results) {
			// combine
			double min = Double.POSITIVE_INFINITY;
			double max = Double.NEGATIVE_INFINITY;
			// will sum mean & medians across all
			double mean = 0d;
			double median = 0d;
			// will average fractions
			double fractPositive = 0d;
			double fractSingular = 0d;
			
			int num = 0;
			
			StiffnessType type = results.get(0).type;
			for (StiffnessResult result : results) {
				Preconditions.checkState(result.type == type);
				min = Math.min(min, result.min);
				max = Math.max(max, result.max);
				mean += result.mean;
				median += result.median;
				fractPositive += result.fractPositive;
				fractSingular += result.fractSingular;
				num += result.numValues;
			}
			
			fractPositive /= results.size();
			fractSingular /= results.size();
			
			this.sourceID = sourceID;
			this.receiverID = receiverID;
			this.mean = mean;
			this.median = median;
			this.min = min;
			this.max = max;
			this.fractPositive = fractPositive;
			this.fractSingular = fractSingular;
			this.type = type;
			this.numValues = num;
		}
		
		@Override
		public String toString() {
			StringBuilder str = new StringBuilder();
			str.append("[").append(sourceID).append("=>").append(receiverID).append("] ");
			str.append(type.name).append(":\t");
			str.append("mean=").append((float)mean);
			str.append("\tmedian=").append((float)median);
			str.append("\trange=[").append((float)min).append(",").append((float)max).append("]");
			str.append("\tfractPositive=").append((float)fractPositive);
			str.append("\tfractSingular=").append((float)fractSingular);
			return str.toString();
		}
	}
	
	public static void main(String[] args) throws ZipException, IOException, DocumentException {
		File fssFile = new File("/home/kevin/Simulators/catalogs/rundir4983_stitched/fss/"
				+ "rsqsim_sol_m6.5_skip5000_sectArea0.2.zip");
		FaultSystemRupSet rupSet = FaultSystemIO.loadRupSet(fssFile);
		double lambda = 30000;
		double mu = 30000;
		double coeffOfFriction = 0.5;
		SubSectStiffnessCalculator calc = new SubSectStiffnessCalculator(
				rupSet.getFaultSectionDataList(), 2d, lambda, mu, coeffOfFriction);
		
		System.out.println(calc.utmZone+" "+calc.utmChar);

		FaultSection[] sects = {
				calc.subSects.get(1836), calc.subSects.get(1837),
//				calc.subSects.get(625), calc.subSects.get(1772),
//				calc.subSects.get(771), calc.subSects.get(1811)
		};
		
		StiffnessAggregationMethod quantity = StiffnessAggregationMethod.MEDIAN;
		
		for (int i=0; i<sects.length; i++) {
			for (int j=0; j<sects.length; j++) {
				System.out.println("Source: "+sects[i].getSectionName());
				System.out.println("Receiver: "+sects[j].getSectionName());
				StiffnessResult[] stiffness = calc.calcStiffness(sects[i], sects[j]);
				for (StiffnessType type : StiffnessType.values())
					System.out.println("\t"+type+": "+calc.getValue(stiffness, type, quantity));
//				System.out.println("\t"+stiffness[0]);
//				System.out.println("\t"+stiffness[1]);
			}
		}
	}
	
}
