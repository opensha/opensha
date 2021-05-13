package scratch.UCERF3.utils.UpdatedUCERF2;

import java.util.ArrayList;
import java.util.Collections;

import org.opensha.sha.earthquake.ProbEqkRupture;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.FaultSegmentData;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.UCERF2;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.MeanUCERF2.B_FaultsFetcherForMeanUCERF;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.data.A_FaultsFetcher;
import org.opensha.sha.earthquake.util.EqkSourceNameComparator;
import org.opensha.sha.magdist.SummedMagFreqDist;

import com.google.common.collect.Lists;

import scratch.UCERF3.utils.ModUCERF2.ModMeanUCERF2;
import scratch.UCERF3.utils.ModUCERF2.UnsegmentedSource;

/**
 * Add comments here
 *
 * 
 * @author Peter Powers
 * @version $Id:$
 */
public class ModMeanUCERF2update_FM2p1 extends ModMeanUCERF2 {
	
	private NSHMP08_GridSourceGenerator gridSrcGen;

	public ModMeanUCERF2update_FM2p1() {
		gridSrcGen = new NSHMP08_GridSourceGenerator(GridSources.ALL);
	}
	
	@Override
	public ProbEqkSource getSource(int idx) {
		return (idx < allSources.size()) ? 
			allSources.get(idx) : 
			gridSrcGen.getSource(idx - allSources.size());
	}

	@Override
	public int getNumSources() {
		if(backSeisParam.getValue().equals(UCERF2.BACK_SEIS_INCLUDE) ||
				backSeisParam.getValue().equals(UCERF2.BACK_SEIS_ONLY))
			return allSources.size() + gridSrcGen.getNumSources();
		return allSources.size();
	}

	@Override
	protected void mkNonCA_B_FaultSources() {
		nonCA_bFaultSources = Lists.newArrayList();
	}
	
	@Override
	protected void updateGridSources() {
		// skip adding C-zones to allSurces as they will be handled by
		// gridSrcGen, but they will also be turned off when background
		// siesmicity is turned off
		gridSrcGen.setForecastDuration(timeSpan.getDuration());
	}
	
	@Override
	public ArrayList<ProbEqkSource>  getSourceList(){
		throw new UnsupportedOperationException();
	}

	
	@Override 
	protected void updateFaultSources() {
		super.updateFaultSources();
		// recreate allSources with new bFault array
		allSources = new ArrayList<ProbEqkSource>();
		String backSeis = backSeisParam.getValue();
		if(!backSeis.equalsIgnoreCase(UCERF2.BACK_SEIS_ONLY)) {
			mkB_FaultSources(); // local variant
			Collections.sort(bFaultSources, new EqkSourceNameComparator());
			// add to the master list
			allSources.addAll(aFaultSegmentedSources);
			allSources.addAll(aFaultUnsegmentedSources);
			allSources.addAll(bFaultSources);
			allSources.addAll(nonCA_bFaultSources);
		}
	}

	/*
	 * Make B-Faults sources and caluculate B-Faults Total Summed MFD
	 */
	private void mkB_FaultSources() {
		
		A_FaultsFetcher aFaultsFetcher = ucerf2.getA_FaultsFetcher();
		B_FaultsFetcherForMeanUCERF bFaultsFetcher = new B_FaultsFetcherForMeanUCERF(aFaultsFetcher, true);
		bFaultSources = new ArrayList<UnsegmentedSource> ();
		double rupOffset = ((Double)this.rupOffsetParam.getValue()).doubleValue();
		double empiricalModelWt=0.0;
		
		String probModel = (String)this.probModelParam.getValue();
		if(probModel.equals(UCERF2.PROB_MODEL_BPT) || probModel.equals(UCERF2.PROB_MODEL_POISSON) ) empiricalModelWt = 0;
		else if(probModel.equals(UCERF2.PROB_MODEL_EMPIRICAL)) empiricalModelWt = 1;
		else if(probModel.equals(PROB_MODEL_WGCEP_PREF_BLEND)) empiricalModelWt = 0.3;
		
		double duration = this.timeSpan.getDuration();
		double wt = 0.5;
		boolean ddwCorr = (Boolean)cybershakeDDW_CorrParam.getValue();
		int floaterType = this.getFloaterType();
		
//		System.out.println("getB_FaultsCommonConnOpts, wt="+wt);
		ArrayList<FaultSegmentData> faultSegDataList = bFaultsFetcher.getB_FaultsCommonConnOpts();
		addToB_FaultSources(rupOffset, empiricalModelWt, duration, wt, faultSegDataList, ddwCorr, floaterType);
		
		wt=1.0;
//		System.out.println("getB_FaultsCommonNoConnOpts, wt="+wt);
		faultSegDataList  = bFaultsFetcher.getB_FaultsCommonNoConnOpts();
		addToB_FaultSources(rupOffset, empiricalModelWt, duration, wt, faultSegDataList, ddwCorr, floaterType);
		
		wt=0.25*2;
//		System.out.println("getB_FaultsUniqueToF2_1ConnOpts, wt="+wt);
		faultSegDataList  = bFaultsFetcher.getB_FaultsUniqueToF2_1ConnOpts();
		addToB_FaultSources(rupOffset, empiricalModelWt, duration, wt, faultSegDataList, ddwCorr, floaterType);
		
		wt=0.5*2;
//		System.out.println("getB_FaultsUniqueToF2_1NoConnOpts, wt="+wt);
		faultSegDataList  = bFaultsFetcher.getB_FaultsUniqueToF2_1NoConnOpts();
		addToB_FaultSources(rupOffset, empiricalModelWt, duration, wt, faultSegDataList, ddwCorr, floaterType);
/*		
		wt=0.25;
//		System.out.println("getB_FaultsUniqueToF2_2ConnOpts, wt="+wt);
		faultSegDataList  = bFaultsFetcher.getB_FaultsUniqueToF2_2ConnOpts();
		addToB_FaultSources(rupOffset, empiricalModelWt, duration, wt, faultSegDataList, ddwCorr, floaterType);
		
		wt=0.5;
//		System.out.println("getB_FaultsUniqueToF2_2NoConnOpts, wt="+wt);
		faultSegDataList  = bFaultsFetcher.getB_FaultsUniqueToF2_2NoConnOpts();
		addToB_FaultSources(rupOffset, empiricalModelWt, duration, wt, faultSegDataList, ddwCorr, floaterType);
*/		
		wt=0.75;
//		System.out.println("getB_FaultsCommonWithUniqueConnOpts, wt="+wt);
		faultSegDataList  = bFaultsFetcher.getB_FaultsCommonWithUniqueConnOpts();
		addToB_FaultSources(rupOffset, empiricalModelWt, duration, wt, faultSegDataList, ddwCorr, floaterType);
		
		// Now calculate the B-Faults total MFD
		if(calcSummedMFDs) {
			bFaultSummedMFD= new SummedMagFreqDist(UCERF2.MIN_MAG, UCERF2.MAX_MAG, UCERF2.NUM_MAG);
			double mag, rate;
			for(int srcIndex=0; srcIndex<bFaultSources.size(); ++srcIndex) {
				UnsegmentedSource source = bFaultSources.get(srcIndex);
				//System.out.println(source.getName());
				int numRups = source.getNumRuptures();
				for(int rupIndex=0; rupIndex<numRups; ++rupIndex) {
					ProbEqkRupture rup = source.getRupture(rupIndex);
					mag = rup.getMag();
					rate = rup.getMeanAnnualRate(duration);
					bFaultSummedMFD.add(mag, rate);
				}
			}
		}
	}

	/*
	 * Meke sources from FaultSegmentData List and to bFaultList
	 * @param rupOffset
	 * @param empiricalModelWt
	 * @param duration
	 * @param wt
	 * @param faultSegDataList
	 */
	private void addToB_FaultSources(double rupOffset, double empiricalModelWt, double duration, double wt, 
			ArrayList<FaultSegmentData> faultSegDataList, boolean ddwCorr, int floaterType) {
		for(int i=0; i<faultSegDataList.size(); ++i) {
			if(faultSegDataList.get(i).getFaultName().equalsIgnoreCase("Mendocino")) continue;
//			System.out.println("\t"+faultSegDataList.get(i).getFaultName()+"\t"+wt);
			bFaultSources.add(new UnsegmentedSource(faultSegDataList.get(i), 
					empiricalModel,  rupOffset,  wt, 
					empiricalModelWt, duration, ddwCorr, floaterType, Double.NaN));
		}
	}

}
