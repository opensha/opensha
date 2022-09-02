package org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree;

import java.text.DecimalFormat;

import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.uncertainty.UncertainBoundedIncrMagFreqDist;
import org.opensha.commons.data.uncertainty.UncertaintyBoundType;
import org.opensha.commons.logicTree.Affects;
import org.opensha.commons.logicTree.DoesNotAffect;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceProvider;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.util.NSHM23_RegionLoader.SeismicityRegions;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

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
	PREFFERRED("Preffered Seismicity Rate", "PrefSeis", 0.74d) {
		@Override
		public GutenbergRichterMagFreqDist build(SeismicityRegions region, EvenlyDiscretizedFunc refMFD, double mMax) {
			switch (region) {
			case CONUS_U3_RELM:
				return gr(refMFD, mMax, 8.3, 0.9);
			case CONUS_PNW:
				return gr(refMFD, mMax, 0.45, 1d);
			case CONUS_IMW:
				return gr(refMFD, mMax, 1.7, 0.9);
			case CONUS_EAST:
				return gr(refMFD, mMax, 0.49, 0.94);

			default:
				return null;
			}
		}
	},
	LOW("Lower Seismicity Bound (p2.5)", "LowSeis", 0.13d) {
		@Override
		public GutenbergRichterMagFreqDist build(SeismicityRegions region, EvenlyDiscretizedFunc refMFD, double mMax) {
			switch (region) {
			case CONUS_U3_RELM:
				return gr(refMFD, mMax, 3.3, 0.9);
			case CONUS_PNW:
				return gr(refMFD, mMax, 0.35, 1d);
			case CONUS_IMW:
				return gr(refMFD, mMax, 0.1, 0.9);
			case CONUS_EAST:
				return gr(refMFD, mMax, 0.4, 0.94);

			default:
				return null;
			}
		}
	},
	HIGH("Upper Seismicity Bound (p97.5)", "HighSeis", 0.13d) {
		@Override
		public GutenbergRichterMagFreqDist build(SeismicityRegions region, EvenlyDiscretizedFunc refMFD, double mMax) {
			switch (region) {
			case CONUS_U3_RELM:
				return gr(refMFD, mMax, 14.3, 0.9);
			case CONUS_PNW:
				return gr(refMFD, mMax, 2.5, 1d);
			case CONUS_IMW:
				return gr(refMFD, mMax, 4.6, 0.9);
			case CONUS_EAST:
				return gr(refMFD, mMax, 2.2, 0.94);

			default:
				return null;
			}
		}
	};
	
	private static final UncertaintyBoundType BOUND_TYPE = UncertaintyBoundType.CONF_95;
	
	private String name;
	private String shortName;
	private double weight;

	private NSHM23_RegionalSeismicity(String name, String shortName, double weight) {
		this.name = name;
		this.shortName = shortName;
		this.weight = weight;
	}
	
	public abstract GutenbergRichterMagFreqDist build(SeismicityRegions region, EvenlyDiscretizedFunc refMFD, double mMax);
	
	private static final DecimalFormat oDF = new DecimalFormat("0.##");
	
	public static UncertainBoundedIncrMagFreqDist getBounded(SeismicityRegions region, EvenlyDiscretizedFunc refMFD, double mMax) {
		IncrementalMagFreqDist upper = HIGH.build(region, refMFD, mMax);
		IncrementalMagFreqDist lower = LOW.build(region, refMFD, mMax);
		IncrementalMagFreqDist pref = PREFFERRED.build(region, refMFD, mMax);
		
		if (pref == null)
			return null;
		
		UncertainBoundedIncrMagFreqDist bounded = new UncertainBoundedIncrMagFreqDist(pref, lower, upper, BOUND_TYPE);
		bounded.setName(pref.getName());
		
		return bounded;
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

}
