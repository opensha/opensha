package scratch.UCERF3;

import java.util.List;

import org.opensha.commons.calc.FaultMomentCalc;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
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
		this.slipAlongModel = slipAlongModel;
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
//		if (rupMeanSlip != null) {
//			aveSlip = getAveSlipForRup(rthRup);
//		} else {
//			double area = getAreaForRup(rthRup);
//			double length = getLengthForRup(rthRup);
//			double totOrigArea = 0d;
//			for (FaultSection sect : getFaultSectionDataForRupture(rthRup))
//				totOrigArea += sect.getTraceLength()*1e3*sect.getOrigDownDipWidth()*1e3;
//			double origDDW = totOrigArea/length;
//			aveSlip = scalingRelationship.getAveSlip(area, length, origDDW);
//		}

		if (slipAlongModel == SlipAlongRuptureModels.MEAN_UCERF3) {
			// get mean weights
			List<Double> meanWeights = Lists.newArrayList();
			List<SlipAlongRuptureModels> meanSALs = Lists.newArrayList();

			double sum = 0;
			for (SlipAlongRuptureModels sal : SlipAlongRuptureModels.values()) {
				double weight = sal.getRelativeWeight(null);
				if (weight > 0) {
					meanWeights.add(weight);
					meanSALs.add(sal);
					sum += weight;
				}
			}
			if (sum != 0)
				for (int i=0; i<meanWeights.size(); i++)
					meanWeights.set(i, meanWeights.get(i)/sum);

			// calculate mean
			double[] slipsForRup = new double[numSects];

			for (int i=0; i<meanSALs.size(); i++) {
				double weight = meanWeights.get(i);
				double[] subSlips = calcSlipOnSectionsForRup(rthRup, meanSALs.get(i), sectArea,
						sectMoRate, aveSlip);

				for (int j=0; j<numSects; j++)
					slipsForRup[j] += weight*subSlips[j];
			}

			return slipsForRup;
		}

		return calcSlipOnSectionsForRup(rthRup, slipAlongModel, sectArea,
				sectMoRate, aveSlip);
	}

	private static EvenlyDiscretizedFunc taperedSlipPDF, taperedSlipCDF;

	private double[] calcSlipOnSectionsForRup(int rthRup,
			SlipAlongRuptureModels slipModelType,
			double[] sectArea, double[] sectMoRate, double aveSlip) {
		double[] slipsForRup = new double[sectArea.length];

		// for case segment slip is independent of rupture (constant), and equal to slip-rate * MRI
		if(slipModelType == SlipAlongRuptureModels.CHAR) {
			throw new RuntimeException("SlipModelType.CHAR_SLIP_MODEL not yet supported");
		}
		// for case where ave slip computed from mag & area, and is same on all segments 
		else if (slipModelType == SlipAlongRuptureModels.UNIFORM) {
			for(int s=0; s<slipsForRup.length; s++)
				slipsForRup[s] = aveSlip;
		}
		// this is the model where section slip is proportional to section slip rate 
		// (bumped up or down based on ratio of seg slip rate over wt-ave slip rate (where wts are seg areas)
		else if (slipModelType == SlipAlongRuptureModels.WG02) {
			// TODO if we revive this, we need to change the cache copying due to moment changes
			double totMoRateForRup = calcTotalAvailableMomentRate(rthRup);
			for(int s=0; s<slipsForRup.length; s++) {
				slipsForRup[s] = aveSlip*sectMoRate[s]*getAreaForRup(rthRup)/(totMoRateForRup*sectArea[s]);
			}
		}
		else if (slipModelType == SlipAlongRuptureModels.TAPERED) {
			// note that the ave slip is partitioned by area, not length; this is so the final model is moment balanced.

			// make the taper function if hasn't been done yet
			if(taperedSlipCDF == null) {
				synchronized (FaultSystemRupSet.class) {
					if (taperedSlipCDF == null) {
						EvenlyDiscretizedFunc taperedSlipCDF = new EvenlyDiscretizedFunc(0, 5001, 0.0002);
						EvenlyDiscretizedFunc taperedSlipPDF = new EvenlyDiscretizedFunc(0, 5001, 0.0002);
						double x,y, sum=0;
						int num = taperedSlipPDF.size();
						for(int i=0; i<num;i++) {
							x = taperedSlipPDF.getX(i);
							y = Math.pow(Math.sin(x*Math.PI), 0.5);
							taperedSlipPDF.set(i,y);
							sum += y;
						}
						// now make final PDF & CDF
						y=0;
						for(int i=0; i<num;i++) {
							y += taperedSlipPDF.getY(i);
							taperedSlipCDF.set(i,y/sum);
							taperedSlipPDF.set(i,taperedSlipPDF.getY(i)/sum);
							//									System.out.println(taperedSlipCDF.getX(i)+"\t"+taperedSlipPDF.getY(i)+"\t"+taperedSlipCDF.getY(i));
						}
						SlipAlongRuptureModelRupSet.taperedSlipCDF = taperedSlipCDF;
						SlipAlongRuptureModelRupSet.taperedSlipPDF = taperedSlipPDF;
					}
				}
			}
			double normBegin=0, normEnd, scaleFactor;
			for(int s=0; s<slipsForRup.length; s++) {
				normEnd = normBegin + sectArea[s]/getAreaForRup(rthRup);
				// fix normEnd values that are just past 1.0
				//					if(normEnd > 1 && normEnd < 1.00001) normEnd = 1.0;
				if(normEnd > 1 && normEnd < 1.01) normEnd = 1.0;
				scaleFactor = taperedSlipCDF.getInterpolatedY(normEnd)-taperedSlipCDF.getInterpolatedY(normBegin);
				scaleFactor /= (normEnd-normBegin);
				Preconditions.checkState(normEnd>=normBegin, "End is before beginning!");
				Preconditions.checkState(aveSlip >= 0, "Negative ave slip: "+aveSlip);
				slipsForRup[s] = aveSlip*scaleFactor;
				normBegin = normEnd;
			}
		}

		return slipsForRup;
	}

}
