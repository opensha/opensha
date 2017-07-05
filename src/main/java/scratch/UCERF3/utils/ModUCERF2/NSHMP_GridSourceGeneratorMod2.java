/**
 * 
 */
package scratch.UCERF3.utils.ModUCERF2;

import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.UCERF2;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.griddedSeis.NSHMP_GridSourceGenerator;
import org.opensha.sha.magdist.SummedMagFreqDist;




/**
 * This simply overrides one method of parent parent in order to go down to M 2.5 in background seismicity
 */
public class NSHMP_GridSourceGeneratorMod2 extends NSHMP_GridSourceGenerator {

	public NSHMP_GridSourceGeneratorMod2() {
		super();
	}

	
	/**
	 * Here I changed the method relative to parent's in the following ways:
	 * 
	 *  1) occurrences of Mmin "5.0" changed to "2.5".
	 *  2) "B_VAL" replaced with 1.0 (previously it was 0.8)
	 *  3) added scaleFactor to increase a-values for consistency with cum rates including aftershocks
	 *     (scaleFactor = 33 gives a total rate >= M5 of 7.5, which is the value including aftershocks
	 *     given in the Summary of Appendix I of UCERF2).
	 *  
	 */
	public SummedMagFreqDist getTotMFD_atLoc(int locIndex, boolean includeC_zones, 
			boolean applyBulgeReduction, boolean applyMaxMagGrid, boolean includeFixedRakeSources, 
			boolean include_agrd_deeps_out) {

		double scaleFactor = 33;	// this gives a total M≥5 rate of 7.5 for ModMeanUCERF2, which is actually 7.42 inside the RELM region
//		double scaleFactor = 25;	// this gives a total M≥5 rate of 5.8 for ModMeanUCERF2 inside the RELM region
		
		// TEMPORARY
//		scaleFactor *= 2;

		// find max mag among all contributions
		double maxMagAtLoc = C_ZONES_MAX_MAG-UCERF2.DELTA_MAG/2;

		// create summed MFD
		int numMags = (int)Math.round((maxMagAtLoc-2.55)/DELTA_MAG) + 1;
		SummedMagFreqDist mfdAtLoc = new SummedMagFreqDist(2.55, maxMagAtLoc, numMags);

		// create and add each contributing MFD
		if(includeFixedRakeSources) {
			mfdAtLoc.addResampledMagFreqDist(getMFD(2.5, 6.5, scaleFactor*agrd_brawly_out[locIndex], 1.0, false), true);
			mfdAtLoc.addResampledMagFreqDist(getMFD(2.5, 7.3, scaleFactor*agrd_mendos_out[locIndex], 1.0, false), true);	
			mfdAtLoc.addResampledMagFreqDist(getMFD(2.5, 6.0, scaleFactor*agrd_creeps_out[locIndex], B_VAL_CREEPING, false), true);			
		}
		
		if(include_agrd_deeps_out)
			mfdAtLoc.addResampledMagFreqDist(getMFD(2.5, 7.2, scaleFactor*agrd_deeps_out[locIndex], 1.0, false), true);
		
		mfdAtLoc.addResampledMagFreqDist(getMFD(2.5, fltmmaxAll21ch_out6[locIndex], scaleFactor*0.667*agrd_impext_out[locIndex], 1.0, applyBulgeReduction), true);
		mfdAtLoc.addResampledMagFreqDist(getMFD(2.5, fltmmaxAll21gr_out6[locIndex], scaleFactor*0.333*agrd_impext_out[locIndex], 1.0, applyBulgeReduction), true);
		if(applyMaxMagGrid) {	 // Apply Max Mag from files

			// 50% weight on the two different Mmax files:
			mfdAtLoc.addResampledMagFreqDist(getMFD(2.5, fltmmaxAll21ch_out6[locIndex], scaleFactor*0.5*0.667*agrd_cstcal_out[locIndex], 1.0, applyBulgeReduction), true);
			mfdAtLoc.addResampledMagFreqDist(getMFD(2.5, fltmmaxAll21gr_out6[locIndex], scaleFactor*0.5*0.333*agrd_cstcal_out[locIndex], 1.0, applyBulgeReduction), true);

			mfdAtLoc.addResampledMagFreqDist(getMFD(2.5, fltmmaxAll24ch_out6[locIndex], scaleFactor*0.5*0.667*agrd_cstcal_out[locIndex], 1.0, applyBulgeReduction), true);
			mfdAtLoc.addResampledMagFreqDist(getMFD(2.5, fltmmaxAll24gr_out6[locIndex], scaleFactor*0.5*0.333*agrd_cstcal_out[locIndex], 1.0, applyBulgeReduction), true);

			mfdAtLoc.addResampledMagFreqDist(getMFD(2.5, fltmmaxAll21ch_out6[locIndex], scaleFactor*0.667*agrd_wuscmp_out[locIndex], 1.0, applyBulgeReduction), true);
			mfdAtLoc.addResampledMagFreqDist(getMFD(2.5, fltmmaxAll21gr_out6[locIndex], scaleFactor*0.333*agrd_wuscmp_out[locIndex], 1.0, applyBulgeReduction), true);

			mfdAtLoc.addResampledMagFreqDist(getMFD(2.5, fltmmaxAll21ch_out6[locIndex], scaleFactor*0.667*agrd_wusext_out[locIndex], 1.0, applyBulgeReduction), true);
			mfdAtLoc.addResampledMagFreqDist(getMFD(2.5, fltmmaxAll21gr_out6[locIndex], scaleFactor*0.333*agrd_wusext_out[locIndex], 1.0, applyBulgeReduction), true);
		} else { // Apply default Mag Max
			mfdAtLoc.addResampledMagFreqDist(getMFD(2.5, DEFAULT_MAX_MAG, scaleFactor*agrd_cstcal_out[locIndex], 1.0, applyBulgeReduction), true);
			mfdAtLoc.addResampledMagFreqDist(getMFD(2.5, DEFAULT_MAX_MAG, scaleFactor*agrd_wuscmp_out[locIndex], 1.0, false), true);
			mfdAtLoc.addResampledMagFreqDist(getMFD(2.5, DEFAULT_MAX_MAG, scaleFactor*agrd_wusext_out[locIndex], 1.0, applyBulgeReduction), true);
		}
		if(includeC_zones && includeFixedRakeSources) { // Include C-Zones
			mfdAtLoc.addResampledMagFreqDist(getMFD(6.5, C_ZONES_MAX_MAG, area1new_agrid[locIndex], 1.0, false), true);
			mfdAtLoc.addResampledMagFreqDist(getMFD(6.5, C_ZONES_MAX_MAG, area2new_agrid[locIndex], 1.0, false), true);
			mfdAtLoc.addResampledMagFreqDist(getMFD(6.5, C_ZONES_MAX_MAG, area3new_agrid[locIndex], 1.0, false), true);
			mfdAtLoc.addResampledMagFreqDist(getMFD(6.5, C_ZONES_MAX_MAG, area4new_agrid[locIndex], 1.0, false), true);
			mfdAtLoc.addResampledMagFreqDist(getMFD(6.5, C_ZONES_MAX_MAG, mojave_agrid[locIndex], 1.0, false), true);
			mfdAtLoc.addResampledMagFreqDist(getMFD(6.5, C_ZONES_MAX_MAG, sangreg_agrid[locIndex], 1.0, false), true);	
		}	

		return mfdAtLoc;
	}
	

}
