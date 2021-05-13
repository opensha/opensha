package scratch.UCERF3.utils;

import java.io.IOException;
import java.util.ArrayList;
import org.opensha.commons.data.region.CaliforniaRegions;
import org.opensha.commons.geo.BorderType;
import org.opensha.commons.geo.Region;
import org.opensha.commons.geo.RegionUtils;
import org.opensha.sha.earthquake.ProbEqkRupture;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.UCERF2;
import org.opensha.commons.gui.plot.GraphWindow;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;
import org.opensha.sha.magdist.SummedMagFreqDist;

import scratch.UCERF3.utils.ModUCERF2.ModMeanUCERF2;

/**
 * This class computes the following Mag Freq Dists for UCERF2 inside the supplied region:
 * 
 * totalMFD - total nucleation MFD for all ruptures in the region
 * faultMFD - total nucleation MFD for fault-based sources in the region
 * backgroundSeisMFD - total nucleation MFD for background seismicity in the region (including type C zones)
 * targetMFD - a GR distribution up to M 8.5, w/ b=1, and scaled to match totalMFD rate at M 5
 * targetMinusBackgroundMFD = targetMFD - backgroundSeisMFD (to use as equality constraint in inversion)
 * 
 * 
 * Note that if CaliforniaRegions.RELM_NOCAL() is used this does not give the same  
 * result as FindEquivUCERF2_Ruptures.getN_CalTargetMinusBackground_MFD()
 * for reasons that include:  1) background there includes non-CA b faults; 
 * 2) slightly different a-value; and 3) that class uses a modified version
 * of CaliforniaRegions.RELM_NOCAL().
 * @author field
 *
 */
public class UCERF2_MFD_ConstraintFetcher {
	
	ModMeanUCERF2 modMeanUCERF2;	// using this because it has aftershocks added in
	Region region;
	SummedMagFreqDist totalMFD, faultMFD, backgroundSeisMFD, targetMinusBackgroundMFD;
	GutenbergRichterMagFreqDist targetMFD;
	
	// discretization params for MFDs:
	final static double MIN_MAG=5.05;
	final static int NUM_MAG=40;
	final static double DELTA_MAG=0.1;
	
	final static double B_VALUE = 1.0;	// b-value for total target distribution
	final static int LAST_FLT_SRC_INDEX = 408; // found by hand!
	
	public UCERF2_MFD_ConstraintFetcher() {
		this(null);
	}
	
	public UCERF2_MFD_ConstraintFetcher(Region region) {

		long startRunTime=System.currentTimeMillis();

//		System.out.println("Starting MeanUCERF2 instantiation");
		double forecastDuration = 1.0;	// years
		modMeanUCERF2 = new ModMeanUCERF2();
		modMeanUCERF2.setParameter(UCERF2.RUP_OFFSET_PARAM_NAME, new Double(10.0));
		modMeanUCERF2.getParameter(UCERF2.PROB_MODEL_PARAM_NAME).setValue(UCERF2.PROB_MODEL_POISSON);
//		meanUCERF2_ETAS.setParameter(UCERF2.BACK_SEIS_NAME, UCERF2.BACK_SEIS_ONLY);
		modMeanUCERF2.setParameter(UCERF2.BACK_SEIS_NAME, UCERF2.BACK_SEIS_INCLUDE);
//		meanUCERF2_ETAS.setParameter(UCERF2.BACK_SEIS_NAME, UCERF2.BACK_SEIS_EXCLUDE);
		modMeanUCERF2.setParameter(UCERF2.BACK_SEIS_RUP_NAME, UCERF2.BACK_SEIS_RUP_POINT);
		modMeanUCERF2.getTimeSpan().setDuration(forecastDuration);
		modMeanUCERF2.updateForecast();
		double runtime = (System.currentTimeMillis()-startRunTime)/1000;
//		System.out.println("MeanUCERF2 instantiation took "+runtime+" seconds");
		
		
		
		// this shouldn't be called by default, only if we actually have a region!
//		startRunTime=System.currentTimeMillis();
//		System.out.println("Starting computeMFDs()");
//		computeMFDs();
//		runtime = (System.currentTimeMillis()-startRunTime)/1000;
//		System.out.println("Computing MFDs took "+runtime+" seconds");

		if (region != null)
			// this also computes MFDs
			setRegion(region);
	}
	
	/**
	 * Set a new region (and compute the MFDs)
	 * @param region
	 */
	public void setRegion(Region region) {
		this.region=region;
		this.computeMFDs();
	}
	
	/**
	 * This returns an MFD_InversionConstraint containing the targetMinusBackgroundMFD
	 * (to use for equality constraint)
	 * @return
	 */
	public MFD_InversionConstraint getTargetMinusBackgrMFD_Constraint() {
		return new MFD_InversionConstraint(targetMinusBackgroundMFD, region);
	}
	
	/**
	 * This returns an MFD_InversionConstraint containing the targetMFD
	 * (to use for inequality constraint)
	 * @return
	 */
	public MFD_InversionConstraint getTargetMFDConstraint() {
		return new MFD_InversionConstraint(targetMFD, region);
	}
	
	public SummedMagFreqDist getTotalMFD() { return totalMFD; }
	
	public SummedMagFreqDist getFaultMFD() { return faultMFD; }
	
	public SummedMagFreqDist getBackgroundSeisMFD() { return backgroundSeisMFD; }
	
	public SummedMagFreqDist getTargetMinusBackgroundMFD() { return targetMinusBackgroundMFD; }
	
	public GutenbergRichterMagFreqDist targetMFD() {return targetMFD; }
	
	/**
	 * This computes the various MFDs
	 */
	private void computeMFDs() {
		if(region == null)
			throw new RuntimeException("Error: Region has not been set");
		
		 totalMFD = new SummedMagFreqDist(MIN_MAG,NUM_MAG,DELTA_MAG); 
		 faultMFD = new SummedMagFreqDist(MIN_MAG,NUM_MAG,DELTA_MAG); 
		 backgroundSeisMFD = new SummedMagFreqDist(MIN_MAG,NUM_MAG,DELTA_MAG); 
		 targetMinusBackgroundMFD = new SummedMagFreqDist(MIN_MAG,NUM_MAG,DELTA_MAG); 
		 
		  double duration = modMeanUCERF2.getTimeSpan().getDuration();
		  for (int s = 0; s < modMeanUCERF2.getNumSources(); ++s) {
			  ProbEqkSource source = modMeanUCERF2.getSource(s);
			  for (int r = 0; r < source.getNumRuptures(); ++r) {
				  ProbEqkRupture rupture = source.getRupture(r);
				  double mag = rupture.getMag();
				  double equivRate = rupture.getMeanAnnualRate(duration);
				  double fractionInside = RegionUtils.getFractionInside(region, rupture.getRuptureSurface().getEvenlyDiscritizedListOfLocsOnSurface());
				  totalMFD.addResampledMagRate(mag, equivRate*fractionInside, true);
				  if(s<=LAST_FLT_SRC_INDEX)
					  faultMFD.addResampledMagRate(mag, equivRate*fractionInside, true);
				  else
					  backgroundSeisMFD.addResampledMagRate(mag, equivRate*fractionInside, true);
			  }
		  }
		  targetMFD = new GutenbergRichterMagFreqDist(MIN_MAG,NUM_MAG,DELTA_MAG,1.0,B_VALUE);
		  targetMFD.scaleToIncrRate(MIN_MAG, totalMFD.getY(MIN_MAG));
		  
		  targetMinusBackgroundMFD.addIncrementalMagFreqDist(targetMFD);
		  targetMinusBackgroundMFD.subtractIncrementalMagFreqDist(backgroundSeisMFD);
		  
		  totalMFD.setName("Total MFD for UCERF2 in Region");
		  faultMFD.setName("Total Fault MFD for UCERF2 in Region");
		  backgroundSeisMFD.setName("Total Background Seis. MFD for UCERF2 in Region");
		  targetMFD.setName("Target MFD for UCERF2 in Region");
		  targetMinusBackgroundMFD.setName("Target minus Background MFD for UCERF2 in Region");
		  totalMFD.setInfo(" ");
		  faultMFD.setInfo(" ");
		  backgroundSeisMFD.setInfo(" ");
		  targetMinusBackgroundMFD.setInfo(" ");
	}
	
	
	/**
	 * This plots the computed MFDs
	 */
	public void plotMFDs() {
		ArrayList funcs = new ArrayList();
		funcs.add(totalMFD);
		funcs.add(faultMFD);
		funcs.add(backgroundSeisMFD);
		funcs.add(targetMFD);
		funcs.add(targetMinusBackgroundMFD);
		GraphWindow graph = new GraphWindow(funcs, "Mag-Freq Dists"); 
		graph.setX_AxisLabel("Mag");
		graph.setY_AxisLabel("Rate");
		graph.setY_AxisRange(3e-6, 3);
		graph.setX_AxisRange(5, 9.0);
		graph.setYLog(true);
		
		try {
			graph.saveAsPDF("UCERF2_MFD_ConstraintFetcherPlot.pdf");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	
	/**
	 * This plots the cumulative MFDs
	 */
	public void plotCumMFDs() {
		ArrayList funcs = new ArrayList();
		funcs.add(totalMFD.getCumRateDistWithOffset());
		funcs.add(faultMFD.getCumRateDistWithOffset());
		funcs.add(backgroundSeisMFD.getCumRateDistWithOffset());
		funcs.add(targetMFD.getCumRateDistWithOffset());
		funcs.add(targetMinusBackgroundMFD.getCumRateDistWithOffset());
		GraphWindow graph = new GraphWindow(funcs, "Cumulative Mag-Freq Dists"); 
		graph.setX_AxisLabel("Mag");
		graph.setY_AxisLabel("Cumulative Rate");
		graph.setY_AxisRange(1e-4, 10);
		graph.setX_AxisRange(5, 8.5);
		graph.setYLog(true);
		
	}

	
	
	private void computeMomentRates() {
		int lastIndexOfFltSources = 288;
		int lastIndexOfNonCA_FltSources = 408;
		int lastIndexOfFirstBackground = 815;	// this is because Brawley, Mentods, and Creeps are the first fixed rate sources, but they are not C zones
		int lastIndexOfC_zones = 1919;
		double faultMoRate=0, nonCA_faultMoRate=0, cZoneMoRate=0, backSrcMoRate=0;
		double duration = modMeanUCERF2.getTimeSpan().getDuration();
		for(int s=0;s<=lastIndexOfFltSources;s++)
			faultMoRate+=modMeanUCERF2.getSource(s).computeEquivTotalMomentRate(duration);
		for(int s=lastIndexOfFltSources+1;s<=lastIndexOfNonCA_FltSources;s++)
			nonCA_faultMoRate+=modMeanUCERF2.getSource(s).computeEquivTotalMomentRate(duration);
		for(int s=lastIndexOfNonCA_FltSources+1;s<=lastIndexOfFirstBackground;s++) {
			backSrcMoRate+=modMeanUCERF2.getSource(s).computeEquivTotalMomentRate(duration);
		}
		for(int s=lastIndexOfFirstBackground+1;s<=lastIndexOfC_zones;s++) {
			cZoneMoRate+=modMeanUCERF2.getSource(s).computeEquivTotalMomentRate(duration);
		}
		for(int s=lastIndexOfC_zones+1;s<modMeanUCERF2.getNumSources();s++)
			backSrcMoRate+=modMeanUCERF2.getSource(s).computeEquivTotalMomentRate(duration);
		
		double moRate=0;
		for(ProbEqkSource source : modMeanUCERF2)
			moRate += source.computeEquivTotalMomentRate(duration);

		System.out.println("totMoRate = "+(float)moRate+"\ttest="+(float)(faultMoRate+nonCA_faultMoRate+cZoneMoRate+backSrcMoRate)+")\n"+
				"faultMoRate = "+(float)faultMoRate+"\t("+Math.round(100.0*(faultMoRate/moRate))+"%)\n"+
				"nonCA_faultMoRate = "+(float)nonCA_faultMoRate+"\t("+Math.round(100.0*(nonCA_faultMoRate/moRate))+"%)\n"+
				"cZoneMoRate = "+(float)cZoneMoRate+"\t("+Math.round(100.0*(cZoneMoRate/moRate))+"%)\n"+
				"backSrcMoRate = "+(float)backSrcMoRate+"\t("+Math.round(100.0*(backSrcMoRate/moRate))+"%)\n"+
				"faultMoRate/(faultMoRate+cZoneMoRate+backSrcMoRate) = "+Math.round(100.0*faultMoRate/(faultMoRate+cZoneMoRate+backSrcMoRate))+" (%)\n"+
				"faultMoRate/(faultMoRate+2*cZoneMoRate+backSrcMoRate) = "+Math.round(100.0*faultMoRate/(faultMoRate+2*cZoneMoRate+backSrcMoRate))+" (%)");

		// now compute moment rate of special zones
		
		moRate=0;
		String srcName = "Brawley Point2Vert_FaultPoisSource";
		for(ProbEqkSource source : modMeanUCERF2)
			if(source.getName().equals(srcName))
				moRate += source.computeEquivTotalMomentRate(duration);
		System.out.println("\ntotMoRate = "+(float)moRate+"\tfor\t"+srcName);
		moRate=0;
		srcName = "Mendos Point2Vert_FaultPoisSource";
		for(ProbEqkSource source : modMeanUCERF2)
			if(source.getName().equals(srcName))
				moRate += source.computeEquivTotalMomentRate(duration);
		System.out.println("\ntotMoRate = "+(float)moRate+"\tfor\t"+srcName);
		moRate=0;
		srcName = "Creeps Point2Vert_FaultPoisSource";
		for(ProbEqkSource source : modMeanUCERF2)
			if(source.getName().equals(srcName))
				moRate += source.computeEquivTotalMomentRate(duration);
		System.out.println("\ntotMoRate = "+(float)moRate+"\tfor\t"+srcName);
		

	}
	
	
	

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		
//		Region relmOrigNCal = new CaliforniaRegions.RELM_NOCAL();
//		Region region  = new Region(relmOrigNCal.getBorder(), BorderType.GREAT_CIRCLE);
		
		Region region = new CaliforniaRegions.RELM_TESTING();

		
		UCERF2_MFD_ConstraintFetcher fetcher = new UCERF2_MFD_ConstraintFetcher(region);
		System.out.println(fetcher.getTotalMFD().getCumRateDistWithOffset());
//		fetcher.computeMomentRates();
		fetcher.plotCumMFDs();
		fetcher.plotMFDs();
	}
}
