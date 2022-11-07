package org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.List;

import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.uncertainty.UncertainBoundedIncrMagFreqDist;
import org.opensha.commons.data.uncertainty.UncertaintyBoundType;
import org.opensha.commons.data.xyz.GriddedGeoDataSet;
import org.opensha.commons.geo.Region;
import org.opensha.commons.logicTree.Affects;
import org.opensha.commons.logicTree.DoesNotAffect;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceProvider;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.NSHM23_InvConfigFactory;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.SupraSeisBValInversionTargetMFDs;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.util.NSHM23_RegionLoader.SeismicityRegions;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.base.Preconditions;

/**
 * Regional seismicity values from Andrea.
 * 
 * Current version via e-mail 8/12/2022, forwarded by Ned 8/15/2022, subject "Fwd: earthquake rate model"
 * 
 * Weights from 95% confidence are from Table 2.2 of WGCEP (2002)
 * @author kevin
 *
 */
@DoesNotAffect(FaultSystemRupSet.SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_PROPS_FILE_NAME)
@DoesNotAffect(FaultSystemSolution.RATES_FILE_NAME)
@DoesNotAffect(GridSourceProvider.ARCHIVE_GRID_REGION_FILE_NAME)
@DoesNotAffect(GridSourceProvider.ARCHIVE_MECH_WEIGHT_FILE_NAME)
@Affects(GridSourceProvider.ARCHIVE_SUB_SEIS_FILE_NAME)
@Affects(GridSourceProvider.ARCHIVE_UNASSOCIATED_FILE_NAME)
public enum NSHM23_RegionalSeismicity implements LogicTreeNode {
	PREFFERRED("Preffered Seismicity Rate", "PrefSeis", 0.8d) {
		@Override
		public IncrementalMagFreqDist build(SeismicityRegions region, EvenlyDiscretizedFunc refMFD, double mMax) {
			switch (region) {
			case CONUS_WEST:
				return gr(refMFD, mMax, 10.6, 0.84);
			case CONUS_EAST:
				return gr(refMFD, mMax, 0.433, 0.94);

			default:
				return null;
			}
		}
	},
	LOW("Lower Seismicity Bound (p16)", "LowSeis", 0.2d) {
		@Override
		public IncrementalMagFreqDist build(SeismicityRegions region, EvenlyDiscretizedFunc refMFD, double mMax) {
			switch (region) {
			case CONUS_WEST:
				return adjustForCrossover(gr(refMFD, mMax, 10.3, 0.848), true, region, mMax);
			case CONUS_EAST:
				return adjustForCrossover(gr(refMFD, mMax, 0.403, 0.96), true, region, mMax);

			default:
				return null;
			}
		}
	},
	HIGH("Upper Seismicity Bound (p84)", "HighSeis", 0.2d) {
		@Override
		public IncrementalMagFreqDist build(SeismicityRegions region, EvenlyDiscretizedFunc refMFD, double mMax) {
			switch (region) {
			case CONUS_WEST:
				return adjustForCrossover(gr(refMFD, mMax, 11.2, 0.832), false, region, mMax);
			case CONUS_EAST:
				return adjustForCrossover(gr(refMFD, mMax, 0.472, 0.92), false, region, mMax);

			default:
				return null;
			}
		}
	};
	
	/**
	 * If b various on outlier branches, they can cross over suchat that low > pref and high < pref for really small
	 * magnitudes. This sets low/high to the pref values in that case.
	 * @param gr
	 * @param lower
	 * @param region
	 * @param mMax
	 * @return
	 */
	private static IncrementalMagFreqDist adjustForCrossover(GutenbergRichterMagFreqDist gr, boolean lower,
			SeismicityRegions region, double mMax) {
		IncrementalMagFreqDist pref = PREFFERRED.build(region, gr, mMax);
		IncrementalMagFreqDist ret = new IncrementalMagFreqDist(gr.getMinX(), gr.getMaxX(), gr.size());
		
		boolean anyOutside = false;
		for (int i=0; i<gr.size(); i++) {
			double prefY = pref.getY(i);
			double myY = gr.getY(i);
			if ((lower && myY > prefY) || (!lower && myY < prefY)) {
				anyOutside = true;
				ret.set(i, prefY);
			} else {
				ret.set(i, myY);
			}
		}
		
		if (anyOutside) {
			ret.setName(gr.getName());
			return ret;
		}
		return gr;
	}
	
	private static final UncertaintyBoundType BOUND_TYPE = UncertaintyBoundType.CONF_68;
	
	private String name;
	private String shortName;
	private double weight;

	private NSHM23_RegionalSeismicity(String name, String shortName, double weight) {
		this.name = name;
		this.shortName = shortName;
		this.weight = weight;
	}
	
	public abstract IncrementalMagFreqDist build(SeismicityRegions region, EvenlyDiscretizedFunc refMFD, double mMax);
	
	private static final DecimalFormat oDF = new DecimalFormat("0.##");
	
	public static UncertainBoundedIncrMagFreqDist getBounded(SeismicityRegions region, EvenlyDiscretizedFunc refMFD, double mMax) {
		IncrementalMagFreqDist upper = HIGH.build(region, refMFD, mMax);
		IncrementalMagFreqDist lower = LOW.build(region, refMFD, mMax);
		IncrementalMagFreqDist pref = PREFFERRED.build(region, refMFD, mMax);
		
		if (pref == null)
			return null;
		
		UncertainBoundedIncrMagFreqDist bounded = new UncertainBoundedIncrMagFreqDist(pref, lower, upper, BOUND_TYPE);
		bounded.setName(pref.getName());
		bounded.setBoundName(getBoundName(lower, upper));
		
		return bounded;
	}
	
	static String getBoundName(IncrementalMagFreqDist lower, IncrementalMagFreqDist upper) {
		String boundName = BOUND_TYPE.toString();
		double lowerN5 = lower.getCumRateDistWithOffset().getInterpolatedY(5d);
		double upperN5 = upper.getCumRateDistWithOffset().getInterpolatedY(5d);
		boundName += ": N5∈["+oDF.format(lowerN5)+","+oDF.format(upperN5)+"]";
		return boundName;
	}
	
	public static UncertainBoundedIncrMagFreqDist getRemapped(Region region, NSHM23_DeclusteringAlgorithms declustering,
			NSHM23_SeisSmoothingAlgorithms smoothing, EvenlyDiscretizedFunc refMFD, double mMax) throws IOException {
		List<SeismicityRegions> seisRegions = NSHM23_InvConfigFactory.getSeismicityRegions(region);
		
		Preconditions.checkState(!seisRegions.isEmpty());
		
		IncrementalMagFreqDist upper = null;
		IncrementalMagFreqDist lower = null;
		IncrementalMagFreqDist pref = null;
		
		double sumTotalN = 0d;
		double sumFractN = 0d;
		
		for (SeismicityRegions seisRegion : seisRegions) {
			// get pdf
			GriddedGeoDataSet pdf = smoothing.loadXYZ(seisRegion, declustering);
			
			double fractN = 0d;
			for (int i=0; i<pdf.size(); i++)
				if (region.contains(pdf.getLocation(i)))
					fractN += pdf.get(i);
			
			if (fractN == 0d)
				continue;
			
			sumTotalN += 1d;
			sumFractN += fractN;
			
			// rescale for this fractional N
			IncrementalMagFreqDist myPref = PREFFERRED.build(seisRegion, refMFD, mMax);
			myPref.scale(fractN);
			IncrementalMagFreqDist myUpper = HIGH.build(seisRegion, refMFD, mMax);
			myUpper.scale(fractN);
			IncrementalMagFreqDist myLower = LOW.build(seisRegion, refMFD, mMax);
			myLower.scale(fractN);
			
			// now further scale bounds to account for less data
			for (int i=0; i<refMFD.size(); i++) {
				double prefVal = myPref.getY(i);
				if (prefVal > 0d) {
					double origUpper = myUpper.getY(i);
					double origLower = myLower.getY(i);
					
					double upperRatio = origUpper/prefVal;
					double lowerRatio = origLower/prefVal;
					
					upperRatio *= 1/Math.sqrt(fractN);
					lowerRatio /= 1/Math.sqrt(fractN);
					myUpper.set(i, prefVal*upperRatio);
					myLower.set(i, prefVal*lowerRatio);
				}
			}
			
			if (upper == null) {
				upper = myUpper;
				lower = myLower;
				pref = myPref;
			} else {
				// add them
				// now further scale bounds to account for less data
				for (int i=0; i<refMFD.size(); i++) {
					double prefVal = myPref.getY(i);
					if (prefVal > 0d) {
						upper.add(i, myUpper.getY(i));
						lower.add(i, myLower.getY(i));
						pref.add(i, myPref.getY(i));
					}
				}
			}
		}
		Preconditions.checkNotNull(pref);
		
		double prefN5 = pref.getCumRateDistWithOffset().getInterpolatedY(5d);
		String name = "Remmapped Observed [pdfFractN="+oDF.format(sumFractN/sumTotalN)+", N5="+oDF.format(prefN5)+"]";
		
		UncertainBoundedIncrMagFreqDist ret = new UncertainBoundedIncrMagFreqDist(pref, lower, upper, BOUND_TYPE);
		ret.setName(name);
		ret.setBoundName(getBoundName(lower, upper));
		return ret;
	}
	
	private static GutenbergRichterMagFreqDist gr(EvenlyDiscretizedFunc refMFD, double mMax,
			double rateM5, double bVal) {
		GutenbergRichterMagFreqDist gr = new GutenbergRichterMagFreqDist(refMFD.getMinX(), refMFD.size(), refMFD.getDelta());
		
		// this sets shape, min/max
		gr.setAllButTotCumRate(refMFD.getX(0), refMFD.getX(refMFD.getClosestXIndex(mMax)), 1e16, bVal);
		// this scales it to match
		gr.scaleToCumRate(refMFD.getClosestXIndex(5.001), rateM5);
		
		gr.setName("Total Observed [b="+oDF.format(bVal)+", N5="+oDF.format(rateM5)+"]");
		
		return gr;
	}
	
	@Override
	public String getShortName() {
		return shortName;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public double getNodeWeight(LogicTreeBranch<?> fullBranch) {
		return weight;
	}

	@Override
	public String getFilePrefix() {
		return getShortName();
	}
	
	public static void main(String[] args) {
		double mMax = 7.6;
		EvenlyDiscretizedFunc refMFD = SupraSeisBValInversionTargetMFDs.buildRefXValues(mMax);
		for (SeismicityRegions seisReg : SeismicityRegions.values()) {
			IncrementalMagFreqDist pref = PREFFERRED.build(seisReg, refMFD, mMax);
			IncrementalMagFreqDist low = LOW.build(seisReg, refMFD, mMax);
			IncrementalMagFreqDist high = HIGH.build(seisReg, refMFD, mMax);
			
			System.out.println(seisReg);
			for (int i=0; i<refMFD.size(); i++) {
				if (refMFD.getX(i) > refMFD.getClosestXIndex(mMax))
					break;
				System.out.println((float)refMFD.getX(i)+"\t"+(float)pref.getY(i)+"\t["+(float)low.getY(i)+","+(float)high.getY(i)+"]");
			}
			System.out.println();
			
		}
	}

}
