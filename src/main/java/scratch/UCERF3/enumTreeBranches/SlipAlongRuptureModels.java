package scratch.UCERF3.enumTreeBranches;

import java.util.List;

import org.opensha.commons.calc.FaultMomentCalc;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import scratch.UCERF3.logicTree.LogicTreeBranchNode;

public enum SlipAlongRuptureModels implements LogicTreeBranchNode<SlipAlongRuptureModels> {
	// DO NOT RENAME THESE - they are used in rupture set files
	
	CHAR(		"Characteristic",	"Char",	0d),	// "Characteristic (Dsr=Ds)"
	UNIFORM(	"Uniform",			"Uni",	0.5d),	// "Uniform/Boxcar (Dsr=Dr)"
	WG02(		"WGCEP-2002",		"WG02",	0d),	// "WGCEP-2002 model (Dsr prop to Vs)"
	TAPERED(	"Tapered Ends",		"Tap",	0.5d),
	MEAN_UCERF3("Mean UCERF3 Dsr",	"MeanU3Dsr", 0d);	// "Mean UCERF3"
	
	private String name, shortName;
	private double weight;
	
	private SlipAlongRuptureModels(String name, String shortName, double weight) {
		this.name = name;
		this.shortName = shortName;
		this.weight = weight;
	}
	
	public String getName() {
		return name;
	}
	
	public String getShortName() {
		return shortName;
	}

	@Override
	public String toString() {
		return name;
	}

	@Override
	public double getRelativeWeight(InversionModels im) {
		return weight;
	}

	@Override
	public String encodeChoiceString() {
		return "Dsr"+getShortName();
	}
	
	@Override
	public String getBranchLevelName() {
		return "Slip Along Rupture Model (Dsr)";
	}
	
	private static EvenlyDiscretizedFunc taperedSlipPDF, taperedSlipCDF;

	public static double[] calcSlipOnSectionsForRup(FaultSystemRupSet rupSet, int rthRup,
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
			List<Integer> sectsInRup = rupSet.getSectionsIndicesForRup(rthRup);
			double totMoRateForRup = 0;
			for(Integer sectID:sectsInRup) {
				double area = rupSet.getAreaForSection(sectID);
				totMoRateForRup += FaultMomentCalc.getMoment(area, rupSet.getSlipRateForSection(sectID));
			}
			for(int s=0; s<slipsForRup.length; s++) {
				slipsForRup[s] = aveSlip*sectMoRate[s]*rupSet.getAreaForRup(rthRup)/(totMoRateForRup*sectArea[s]);
			}
		}
		else if (slipModelType == SlipAlongRuptureModels.TAPERED) {
			// note that the ave slip is partitioned by area, not length; this is so the final model is moment balanced.

			// make the taper function if hasn't been done yet
			if(taperedSlipCDF == null) {
				synchronized (SlipAlongRuptureModels.class) {
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
						SlipAlongRuptureModels.taperedSlipCDF = taperedSlipCDF;
						SlipAlongRuptureModels.taperedSlipPDF = taperedSlipPDF;
					}
				}
			}
			double normBegin=0, normEnd, scaleFactor;
			for(int s=0; s<slipsForRup.length; s++) {
				normEnd = normBegin + sectArea[s]/rupSet.getAreaForRup(rthRup);
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
		} else  if (slipModelType == MEAN_UCERF3) {
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

			for (int i=0; i<meanSALs.size(); i++) {
				double weight = meanWeights.get(i);
				double[] subSlips = calcSlipOnSectionsForRup(rupSet, rthRup, meanSALs.get(i), sectArea,
						sectMoRate, aveSlip);

				for (int j=0; j<slipsForRup.length; j++)
					slipsForRup[j] += weight*subSlips[j];
			}
		}

		return slipsForRup;
	}
}