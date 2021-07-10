package scratch.UCERF3;

import java.util.List;

import org.opensha.commons.calc.FaultMomentCalc;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.sha.earthquake.faultSysSolution.modules.SlipAlongRuptureModel;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import scratch.UCERF3.enumTreeBranches.SlipAlongRuptureModels;

public abstract class SlipAlongRuptureModelRupSet extends SlipEnabledRupSet {
	
	private SlipAlongRuptureModels slipAlongModel;
	private double[] rupAveSlips;

	/**
	 * Constructor for precomputed data where everything is passed in.
	 * 
	 * @param faultSectionData fault section data list (CANNOT be null)
	 * @param sectSlipRates slip rates for each fault section with any reductions applied (CAN be null)
	 * @param sectSlipRateStdDevs slip rate std deviations for each fault section (CAN be null)
	 * @param sectAreas areas for each fault section (CAN be null)
	 * @param sectionForRups list of fault section indexes for each rupture (CANNOT be null)
	 * @param mags magnitudes for each rupture (CANNOT be null)
	 * @param rakes rakes for each rupture (CANNOT be null)
	 * @param rupAreas areas for each rupture (CANNOT be null)
	 * @param rupLengths lengths for each rupture (CAN be null)
	 * @param slipAlongModel Slip Along Rupture model (CANNOT be null)
	 * @param info metadata string
	 */
	public SlipAlongRuptureModelRupSet(
			List<? extends FaultSection> faultSectionData,
			double[] sectSlipRates,
			double[] sectSlipRateStdDevs,
			double[] sectAreas,
			List<List<Integer>> sectionForRups,
			double[] mags,
			double[] rakes,
			double[] rupAreas,
			double[] rupLengths,
			SlipAlongRuptureModels slipAlongModel,
			String info) {
		this(slipAlongModel);
		init(faultSectionData, sectSlipRates, sectSlipRateStdDevs, sectAreas,
				sectionForRups, mags, rakes, rupAreas, rupLengths, info);
	}

	/**
	 * Default constructor for subclasses which will call init on their own.
	 * 
	 * Protected so it can only be invoked by subclasses.
	 */
	protected SlipAlongRuptureModelRupSet(SlipAlongRuptureModels slipAlongModel) {
		// do nothing, it's up to subclass to call init
		this.slipAlongModel = slipAlongModel;
		addModule(SlipAlongRuptureModel.forModel(slipAlongModel));
	}
	
	/**
	 * This gets the slip on each section based on the value of slipModelType.
	 * The slips are in meters.  Note that taper slipped model wts slips by area
	 * to maintain moment balance (so it doesn't plot perfectly); do something about this?
	 * 
	 * Note that for two parallel faults that have some overlap, the slip won't be reduced
	 * along the overlap the way things are implemented here.
	 * 
	 * This has been spot checked, but needs a formal test.
	 *
	 */
	@Override
	protected double[] calcSlipOnSectionsForRup(int rthRup) {
		Preconditions.checkNotNull(slipAlongModel);

		List<Integer> sectionIndices = getSectionsIndicesForRup(rthRup);
		int numSects = sectionIndices.size();

		// compute rupture area
		double[] sectArea = new double[numSects];
		double[] sectMoRate = new double[numSects];
		int index=0;
		for(Integer sectID: sectionIndices) {	
			//				FaultSectionPrefData sectData = getFaultSectionData(sectID);
			//				sectArea[index] = sectData.getTraceLength()*sectData.getReducedDownDipWidth()*1e6;	// aseismicity reduces area; 1e6 for sq-km --> sq-m
			sectArea[index] = getAreaForSection(sectID);
			sectMoRate[index] = FaultMomentCalc.getMoment(sectArea[index], getSlipRateForSection(sectID));
			index += 1;
		}

		double aveSlip = getAveSlipForRup(rthRup); // in meters
		
		return SlipAlongRuptureModels.calcSlipOnSectionsForRup(this, rthRup, slipAlongModel, sectArea, sectMoRate, aveSlip);
	}

}
