package scratch.UCERF3;

import java.util.HashMap;
import java.util.List;

import org.opensha.commons.data.function.ArbDiscrEmpiricalDistFunc;
import org.opensha.sha.gui.infoTools.CalcProgressBar;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import scratch.UCERF3.utils.aveSlip.AveSlipConstraint;

public abstract class SlipEnabledSolution extends FaultSystemSolution {
	
	private double[] slipRatesCache;
	
	private HashMap<Integer, ArbDiscrEmpiricalDistFunc> slipPDFMap =
			new HashMap<Integer, ArbDiscrEmpiricalDistFunc>();
		
	private HashMap<Integer, ArbDiscrEmpiricalDistFunc> slipPaleoObsPDFMap =
			new HashMap<Integer, ArbDiscrEmpiricalDistFunc>();
	
	protected SlipEnabledSolution() {
		super();
	}
	
	public SlipEnabledSolution(SlipEnabledRupSet rupSet, double[] rates) {
		super(rupSet, rates);
	}
	
	public SlipEnabledSolution(SlipEnabledRupSet rupSet, double[] rates,
			List<? extends IncrementalMagFreqDist> subSeismoOnFaultMFDs) {
		super(rupSet, rates, subSeismoOnFaultMFDs);
	}

	@Override
	public void clearCache() {
		super.clearCache();
		slipRatesCache = null;
		slipPDFMap.clear();
		slipPaleoObsPDFMap.clear();
	}
	
	/**
	 * This computes the slip rate of the sth section (meters/year))
	 * 
	 * @param sectIndex
	 * @return
	 */
	public double calcSlipRateForSect(int sectIndex) {
		return calcSlipRateForAllSects()[sectIndex];
	}
	
	private double doCalcSlipRateForSect(int sectIndex) {
		double slipRate=0;
		for (int r : getRupSet().getRupturesForSection(sectIndex)) {
			int ind = getRupSet().getSectionsIndicesForRup(r).indexOf(sectIndex);
//			slipRate += getRateForRup(r)*getAveSlipForRup(r);
			slipRate += getRateForRup(r)*getRupSet().getSlipOnSectionsForRup(r)[ind];
		}
		return slipRate;
	}
	
	/**
	 * This computes the slip rate of all sections (meters/year))
	 * 
	 * @return
	 */
	public synchronized double[] calcSlipRateForAllSects() {
		if (slipRatesCache == null) {
			double[] slipRatesCache = new double[getRupSet().getNumSections()];
			CalcProgressBar p = null;
			if (getRupSet().isShowProgress()) {
				p = new CalcProgressBar("Calculating Slip Rates", "Calculating Slip Rates");
			}
			for (int i=0; i<slipRatesCache.length; i++) {
				if (p != null) p.updateProgress(i, slipRatesCache.length);
				slipRatesCache[i] = doCalcSlipRateForSect(i);
			}
			if (p != null) p.dispose();
			this.slipRatesCache = slipRatesCache;
		}
		return slipRatesCache;
	}
	
	public abstract SlipEnabledRupSet getRupSet();
	
	/**
	 * This creates an empirical PDF (ArbDiscrEmpiricalDistFunc) of slips for the 
	 * specified section index, where the rate of each rupture is taken into account.
	 * You can get the mean, standard deviation, or COV by calling the associated method
	 * in the returned object.
	 * @param sectIndex
	 * @return
	 */
	public synchronized ArbDiscrEmpiricalDistFunc calcSlipPFD_ForSect(int sectIndex) {
		ArbDiscrEmpiricalDistFunc slipPDF = slipPDFMap.get(sectIndex);
		if (slipPDF != null)
			return slipPDF;
		slipPDF = new ArbDiscrEmpiricalDistFunc();
		for (int r : getRupSet().getRupturesForSection(sectIndex)) {
			List<Integer> sectIndices = getRupSet().getSectionsIndicesForRup(r);
			double[] slips = getRupSet().getSlipOnSectionsForRup(r);
			for(int s=0; s<sectIndices.size(); s++) {
				if(sectIndices.get(s) == sectIndex) {
					slipPDF.set(slips[s], getRateForRup(r));
					break;
				}
			}
		}
		slipPDFMap.put(sectIndex, slipPDF);
		return slipPDF;
	}
	

	/**
	 * This creates an empirical PDF (ArbDiscrEmpiricalDistFunc) of paleo observable slips for the 
	 * specified section index, where the rate of each rupture and probability of observance is taken 
	 * into account (using the model in AveSlipConstraint.getProbabilityOfObservedSlip(slip)).
	 * You can get the mean, standard deviation, or COV by calling the associated method
	 * in the returned object.
	 * @param sectIndex
	 * @return
	 */
	public synchronized ArbDiscrEmpiricalDistFunc calcPaleoObsSlipPFD_ForSect(int sectIndex) {
		ArbDiscrEmpiricalDistFunc slipPDF = slipPaleoObsPDFMap.get(sectIndex);
		if (slipPDF != null)
			return slipPDF;
		slipPDF = new ArbDiscrEmpiricalDistFunc();
		for (int r : getRupSet().getRupturesForSection(sectIndex)) {
			List<Integer> sectIndices = getRupSet().getSectionsIndicesForRup(r);
			double[] slips = getRupSet().getSlipOnSectionsForRup(r);
			for(int s=0; s<sectIndices.size(); s++) {
				if(sectIndices.get(s) == sectIndex) {
					slipPDF.set(slips[s], getRateForRup(r)*AveSlipConstraint.getProbabilityOfObservedSlip(slips[s]));
					break;
				}
			}
		}
		slipPaleoObsPDFMap.put(sectIndex, slipPDF);
		return slipPDF;
	}

}