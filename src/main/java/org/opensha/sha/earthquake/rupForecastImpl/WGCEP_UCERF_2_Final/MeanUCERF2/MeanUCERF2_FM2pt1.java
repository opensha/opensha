/**
 * 
 */
package org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.MeanUCERF2;



import java.util.ArrayList;
import java.util.Collections;

import org.opensha.sha.earthquake.ProbEqkRupture;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.FaultSegmentData;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.UCERF2;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.MeanUCERF2.B_FaultsFetcherForMeanUCERF;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.data.A_FaultsFetcher;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.UnsegmentedSource;
import org.opensha.sha.earthquake.util.EqkSourceNameComparator;
import org.opensha.commons.gui.plot.GraphWindow;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.magdist.SummedMagFreqDist;



/**
 * This has the same attributes of its parent (MeanUCERF2), except the fault model 2.2 
 * sources are removed and the fault model 2.1 sources are given double weight.  This constitutes
 * an average over all fault model 2.1 logic-tree branches.  This has been tested in the main
 * method here.
 */
public class MeanUCERF2_FM2pt1 extends MeanUCERF2 {
	//for Debug purposes
	protected static String  C = new String("MeanUCERF2, FM 2.1");
	// name of this ERF
	public final static String NAME = new String("WGCEP (2007) UCERF2 - Single Branch, Fault Model 2.1 only");




	
	/**
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

	/**
	 * MAe sources from FaultSegmentData List and to bFaultList
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
	
	@Override
	public String getName(){
		return NAME;
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

	// this is temporary for testing purposes
	public static void main(String[] args) {
		
		ArrayList funcs = new ArrayList();

		MeanUCERF2_FM2pt1 ucerf2_fm2pt1 = new MeanUCERF2_FM2pt1();
		ucerf2_fm2pt1.calcSummedMFDs  =true;
		ucerf2_fm2pt1.setParameter(UCERF2.BACK_SEIS_NAME, UCERF2.BACK_SEIS_EXCLUDE);
		ucerf2_fm2pt1.updateForecast();
		IncrementalMagFreqDist ucerf2_fm2pt1MFD = ucerf2_fm2pt1.getB_FaultMFD();
		funcs.add(ucerf2_fm2pt1MFD);
		ucerf2_fm2pt1 = null;
		
		MeanUCERF2_FM2pt2 ucerf2_fm2pt2 = new MeanUCERF2_FM2pt2();
		ucerf2_fm2pt2.calcSummedMFDs  =true;
		ucerf2_fm2pt2.setParameter(UCERF2.BACK_SEIS_NAME, UCERF2.BACK_SEIS_EXCLUDE);
		ucerf2_fm2pt2.updateForecast();
		IncrementalMagFreqDist ucerf2_fm2pt2MFD = ucerf2_fm2pt2.getB_FaultMFD();
		funcs.add(ucerf2_fm2pt2MFD);
		ucerf2_fm2pt2 = null;

		
		MeanUCERF2 ucerf2 = new MeanUCERF2();
		ucerf2.calcSummedMFDs  =true;
		ucerf2.setParameter(UCERF2.BACK_SEIS_NAME, UCERF2.BACK_SEIS_EXCLUDE);
		ucerf2.updateForecast();
		IncrementalMagFreqDist ucerfMFD = ucerf2.getB_FaultMFD();
		funcs.add(ucerfMFD);
		
		double diffTest = ucerfMFD.getY(7.05) - (ucerf2_fm2pt1MFD.getY(7.05)+ucerf2_fm2pt2MFD.getY(7.05))/2;
		System.out.println ("m 7.05 diffTest="+(float)diffTest+" (should be zero)");
		double diff = ucerf2_fm2pt1MFD.getY(7.05)-ucerf2_fm2pt2MFD.getY(7.05);
		System.out.println ("m 7.05 diff between two fault models ="+(float)diff);

		GraphWindow graph = new GraphWindow(funcs, "Incremental Mag-Freq Dists"); 
		graph.setX_AxisLabel("Mag");
		graph.setY_AxisLabel("Rate");
		graph.setYLog(true);
		graph.setY_AxisRange(1e-6, 1.0);

		
	}
}