/**
 * 
 */
package scratch.UCERF3.oldStuff;

import java.awt.Color;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;

import org.opensha.commons.calc.magScalingRelations.MagAreaRelationship;
import org.opensha.commons.calc.magScalingRelations.magScalingRelImpl.HanksBakun2002_MagAreaRel;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.region.CaliforniaRegions;
import org.opensha.commons.eq.MagUtils;
import org.opensha.commons.geo.BorderType;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.Region;
import org.opensha.commons.gui.plot.PlotColorAndLineTypeSelectorControlPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.ERF;
import org.opensha.sha.earthquake.ProbEqkRupture;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.earthquake.calc.ERF_Calculator;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.UCERF2;
import scratch.UCERF3.utils.ModUCERF2.ModMeanUCERF2;

import org.opensha.sha.faultSurface.AbstractEvenlyGriddedSurface;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.commons.gui.plot.GraphWindow;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.magdist.SummedMagFreqDist;

/**
 * This class associates UCERF2 ruptures with inversion ruptures.  That is, for a given inversion rupture,
 * this will give a total rate and average magnitude (preserving moment rate) from UCERF2 for that rupture
 * (an average in case more than one UCERF2 associates with a given inversion rupture).  Right now this throws
 * an exception if the run is not for N. Cal. (as determined by checking that  faultSectionData.size()==NUM_SECTIONS).
 * Applying this to the entire state will require more work, mostly because S. Ca. has two alternative fault models
 * and the greater number of faults makes associating sections at the end of ruptures more difficult.  Right now 
 * the only N. Cal faults exhibiting a problem are those that have sub-seismogenic ruptures:
 * 
 * 		Big Lagoon-Bald Mtn
 * 		Fickle Hill
 * 		Great Valley 13 (Coalinga)
 * 		Little Salmon (Offshore)
 * 		Little Salmon (Onshore)
 * 		Little Salmon Connected
 * 		Mad River
 * 		McKinleyville
 * 		Table Bluff
 * 		Trinidad
 * 
 * Important Notes:
 * 
 * 1) This currently only works for inversions in the N Cal RELM region
 * 2) UCERF2 ruptures that are sub-seimogenic are not associated (since there is no meaningful mapping)
 * 3) The UCERF2 rates are not reduce by the fraction of rupture that extends outside the region
 * 4) Most of the inversion ruptures have no UCERF2 association (I think); these are null.
 * 5) A modified version of RELM_NOCAL region has to be used because otherwise Parkfield is included
 * 6) This ignores the non-CA type B faults in UCERF2 (since they are not included in the current inversion).
 * 7) This reads from or writes to some pre-computed data files; these must be deleted if inputs change as
 *    noted in the constructor below.
 * 8) If more than one inversion ruptures have the same end sections (multi pathing), then the one with the 
 *    minimum number of sections get's the equivalent UCERF2 ruptures (see additional notes for the method
 *    getMagsAndRatesForRuptures(ArrayList<ArrayList<Integer>>)).
 * 9) This uses a special version of MeanUCERF2 (that computes floating rupture areas as the ave of HB and EllB)
 * 
 * This class also compute various MFDs.
 * 
 * @author field
 */
public class FindEquivNoCalUCERF2_Ruptures {
	
	protected final static boolean D = false;  // for debugging
	
	String DATA_FILE_NAME = "equivUCERF2_RupDataNoCal";
	String INFO_FILE_NAME = "sectEndsForUCERF2_RupsResults_AllButNonCA_B_NoCal";
	private File precomputedDataDir;
	File dataFile;
	
	final static int NUM_RUPTURES=12463;	// this was found after running this once
	//final static int NUM_SECTIONS=717;		// this was found after running this once
	final static int NUM_SECTIONS=717;		// Morgan edit: This is for NCal without the SAF Creeping Section
	
	List<FaultSectionPrefData> faultSectionData;
	
	Region relm_nocal_reg;
	
	// the following hold info about each UCERF2 rupture
	int[] firstSectOfUCERF2_Rup, lastSectOfUCERF2_Rup, srcIndexOfUCERF2_Rup, rupIndexOfUCERF2_Rup;
	double[] magOfUCERF2_Rup, lengthOfUCERF2_Rup, rateOfUCERF2_Rup;
	boolean[] subSeismoUCERF2_Rup;
	
	ModMeanUCERF2 meanUCERF2;	// note that this is a special version (see notes above)
	int NUM_UCERF2_SRC=289; // this is to exclude non-CA B faults

	SummedMagFreqDist mfdOfAssocRupsAndModMags;		// this is the mfd of the associated ruptures (including ave mag from mult rups)
	SummedMagFreqDist mfdOfAssocRupsWithOrigMags;	// this is the mfd of the associated ruptures (including orig mag from all rups)
	
	IncrementalMagFreqDist nCal_UCERF2_BackgrMFD_WithAftShocks;
	IncrementalMagFreqDist nCalTargetMinusBackground;
	GutenbergRichterMagFreqDist nCalTotalTargetGR_MFD;
	
	boolean[] ucerf2_rupUsed;
		
	/**
	 * See important notes given above for this class.
	 * 
	 * Note that files saved/read here in precomputedDataDir (DATA_FILE_NAME and INFO_FILE_NAME)  should be 
	 * deleted any time the contents of faultSectionData or inversionRups change (read by this constructor 
	 * and the method getMagsAndRatesForRuptures(), respectively)
	 * 
	 * @param faultSectionData
	 * @param precomputedDataDir
	 */
	public FindEquivNoCalUCERF2_Ruptures(List<FaultSectionPrefData> faultSectionData, File precomputedDataDir) {
		
		this.faultSectionData = faultSectionData;
		this.precomputedDataDir=precomputedDataDir;
		
		Region region = new CaliforniaRegions.RELM_NOCAL();
		relm_nocal_reg  = new Region(region.getBorder(), BorderType.GREAT_CIRCLE);
		// the above region is modified as noted in getMFD_forNcal() to avoid the inclusion of Parkfield
		
		// Make sure the sections correspond to N California (a weak test)
		if(faultSectionData.size() != NUM_SECTIONS)
			throw new RuntimeException("Error: Number of sections changed (this has only been verified for for N Cal. which should have )"+
					NUM_SECTIONS+" sections; this run has "+faultSectionData.size()+")");
		
		String fullpathname = precomputedDataDir.getAbsolutePath()+File.separator+DATA_FILE_NAME;
		dataFile = new File (fullpathname);

		// these are what we want to fill in here
		firstSectOfUCERF2_Rup = new int[NUM_RUPTURES];	// contains -1 if no association
		lastSectOfUCERF2_Rup = new int[NUM_RUPTURES];	// contains -1 if no association
		srcIndexOfUCERF2_Rup = new int[NUM_RUPTURES];
		rupIndexOfUCERF2_Rup = new int[NUM_RUPTURES];
		magOfUCERF2_Rup = new double[NUM_RUPTURES];
		lengthOfUCERF2_Rup = new double[NUM_RUPTURES];
		rateOfUCERF2_Rup = new double[NUM_RUPTURES];
		subSeismoUCERF2_Rup = new boolean[NUM_RUPTURES]; 

		// read from file if it exists
		if(dataFile.exists()) {
			if(D) System.out.println("Reading existing file: "+ fullpathname);
			try {
				// Wrap the FileInputStream with a DataInputStream
				FileInputStream file_input = new FileInputStream (dataFile);
				DataInputStream data_in    = new DataInputStream (file_input );
				for(int i=0; i<NUM_RUPTURES;i++) {
					firstSectOfUCERF2_Rup[i]=data_in.readInt();
					lastSectOfUCERF2_Rup[i]=data_in.readInt();
					srcIndexOfUCERF2_Rup[i]=data_in.readInt();
					rupIndexOfUCERF2_Rup[i]=data_in.readInt();
					magOfUCERF2_Rup[i]=data_in.readDouble();
					lengthOfUCERF2_Rup[i]=data_in.readDouble();
					rateOfUCERF2_Rup[i]=data_in.readDouble();
					subSeismoUCERF2_Rup[i]=data_in.readBoolean();
				}
				data_in.close ();
			} catch  (IOException e) {
				System.out.println ( "IO Exception =: " + e );
			}
		}
		else {	// compute section ends for each UCERF2 rupture
			findSectionEndsForUCERF2_Rups();
		}
		
	}
	
	
	/**
	 * This generates the UCERF2 instance used here (for a specific set of adjustable params).
	 * @return
	 */
	public ModMeanUCERF2 getMeanUCERF2_Instance() {
		if(meanUCERF2 == null) {
			if(D) System.out.println("Instantiating UCERF2");
			meanUCERF2 = new ModMeanUCERF2();
			meanUCERF2.setParameter(UCERF2.BACK_SEIS_NAME, UCERF2.BACK_SEIS_EXCLUDE);
			meanUCERF2.setParameter(UCERF2.PROB_MODEL_PARAM_NAME, UCERF2.PROB_MODEL_POISSON);
			meanUCERF2.getTimeSpan().setDuration(30.0);
			meanUCERF2.setParameter(UCERF2.FLOATER_TYPE_PARAM_NAME, UCERF2.CENTERED_DOWNDIP_FLOATER);
			meanUCERF2.updateForecast();
		}
		return meanUCERF2;
	}
	

	/**
	 * This computes 4 UCERF3 mag-freq dists for the N Cal RELM Region (with the region modified as noted below):
	 * 
	 * 	nucleationMFD - rates of nucleation within the region (rate multiplied by fraction inside region)
	 * 	subSeismoMFD - mfd for ruptures that are subseismogenic (multiplied by fraction inside)
	 * 	outsideRegionMFD - mfd of the fraction of those outside the region (but only if used in the association)
	 *  diffrenceMFD = mfdOfAssocRupsWithOrigMags - (nucleationMFD + outsideRegionMFD - subSeismoMFD)
	 * 
	 * The latter should be zero if everything is correct (from and MFD perspective)
	 * 
	 * This is only meaningful if the faultSectionData is for the N Cal region, and if getMagsAndRatesForRuptures()
	 * has been run for this case.
	 * 
	 * This is the code here is basically the same as in ERF_Calculator, except the sources 
	 * are cut off to exclude non-CA type B faults (using NUM_UCERF2_SRC).
	 *   
	 * Note that this has to use a modified version of CaliforniaRegions.RELM_NOCAL() in order 
	 * to not include Parkfield-connected ruptures (one that uses BorderType.GREAT_CIRCLE rather 
	 * than the default BorderType.MERCATOR_LINEAR).
	 * 
	 * @return
	 */
	public ArrayList<IncrementalMagFreqDist> getMFDsForNcal() {
		ModMeanUCERF2 erf = getMeanUCERF2_Instance();
		SummedMagFreqDist nucleationMFD = new SummedMagFreqDist(5.05,35,0.1);
		SummedMagFreqDist subSeismoMFD = new SummedMagFreqDist(5.05,35,0.1);  // to show what's definitely excluded
		SummedMagFreqDist outsideRegionMFD = new SummedMagFreqDist(5.05,35,0.1);  // to show what's definitely excluded
		IncrementalMagFreqDist diffrenceMFD = new IncrementalMagFreqDist(5.05,35,0.1);  // to show what's definitely excluded
		double duration = erf.getTimeSpan().getDuration();
		int rupIndex=-1;
		for (int s = 0; s < NUM_UCERF2_SRC; ++s) {
			ProbEqkSource source = erf.getSource(s);
			for (int r = 0; r < source.getNumRuptures(); ++r) {
				rupIndex += 1;
				ProbEqkRupture rupture = source.getRupture(r);
				double mag = rupture.getMag();
				double equivRate = rupture.getMeanAnnualRate(duration);
				LocationList locsOnRupSurf = rupture.getRuptureSurface().getEvenlyDiscritizedListOfLocsOnSurface();
				ListIterator<Location> it = locsOnRupSurf.listIterator();
				double fractInside = 0;
				while (it.hasNext()) {
					if (relm_nocal_reg.contains(it.next()))
						fractInside += 1.0;
				}
				fractInside /= locsOnRupSurf.size();
				nucleationMFD.addResampledMagRate(mag, equivRate*fractInside, true);
				if(ucerf2_rupUsed[rupIndex]) // if assoc w/ Inversion add to outside region MFD
					outsideRegionMFD.addResampledMagRate(mag, equivRate*(1.0-fractInside), true);
				boolean subseismogenic = subSeismoUCERF2_Rup[rupIndex];
				if(subseismogenic)
					subSeismoMFD.addResampledMagRate(mag, equivRate*fractInside, true);
				if(D) {
					if(fractInside > 0 && ucerf2_rupUsed[rupIndex] == false) {
						System.out.println("SomeInsideButNotUsed\t"+rupIndex+"\t"+s+"\t"+r+"\t"+(float)fractInside+"\t"+subseismogenic+"\t"+firstSectOfUCERF2_Rup[rupIndex]+
								"\t"+lastSectOfUCERF2_Rup[rupIndex]+"\t"+(float)magOfUCERF2_Rup[rupIndex]+"\t"+source.getName());					
					}
					if(fractInside < 1 && ucerf2_rupUsed[rupIndex] == true) {
						System.out.println("NotAllInsideButUsed\t"+rupIndex+"\t"+s+"\t"+r+"\t"+(float)fractInside+"\t"+subseismogenic+"\t"+firstSectOfUCERF2_Rup[rupIndex]+
								"\t"+lastSectOfUCERF2_Rup[rupIndex]+"\t"+(float)magOfUCERF2_Rup[rupIndex]+"\t"+source.getName());										
					}					
				}
			}
		}
		// make the difference MFD (which should be zero)
		for(int m=0; m< diffrenceMFD.size(); m++) {
			double diff = mfdOfAssocRupsWithOrigMags.getY(m)-(nucleationMFD.getY(m)+outsideRegionMFD.getY(m)-subSeismoMFD.getY(m));
			diffrenceMFD.set(m,diff);
		}
		nucleationMFD.setName("N Cal MFD for UCERF2");
		nucleationMFD.setInfo("this is for the Modified N Cal RELM region, and excludes non-ca type B faults");
		subSeismoMFD.setName("Sub-Seismo N Cal MFD for UCERF2");
		subSeismoMFD.setInfo("this is for the Modified N Cal RELM region, and excludes non-ca type B faults");
		outsideRegionMFD.setName("Outside N Cal MFD for UCERF2");
		outsideRegionMFD.setInfo("this is the MFD for the fraction of inversion-associated ruptures that extend outside the N Cal RELM region");
		diffrenceMFD.setName("Difference (should be zero");
		diffrenceMFD.setInfo("this = mfdOfAssocRupsWithOrigMags-(nucleationMFD+outsideRegionMFD-subSeismoMFD)");
		ArrayList<IncrementalMagFreqDist> mfds = new ArrayList<IncrementalMagFreqDist>();
		mfds.add(nucleationMFD);
		mfds.add(subSeismoMFD);
		mfds.add(outsideRegionMFD);
		mfds.add(diffrenceMFD);
		return mfds;
	}
	
	
	/**
	 * This returns an ArrayList<SummedMagFreqDist> containing mfdOfAssocRupsAndModMags & 
	 * mfdOfAssocRupsWithOrigMags (in that order)
	 * @return
	 */
	public ArrayList<SummedMagFreqDist> getMFDsForUCERF2AssocRups(){
		ArrayList<SummedMagFreqDist> mfds = new ArrayList<SummedMagFreqDist>();
		mfds.add(mfdOfAssocRupsAndModMags);
		mfds.add(mfdOfAssocRupsWithOrigMags);
		return mfds;
	}

	
	/**
	 * This method computes and saves the following data to a file with name set by DATA_FILE_NAME: 
	 * 
	 * 	firstSectOfUCERF2_Rup, 
	 * 	lastSectOfUCERF2_Rup, 
	 * 	srcIndexOfUCERF2_Rup, 
	 * 	rupIndexOfUCERF2_Rup, 
	 * 	magOfUCERF2_Rup, 
	 * 	lengthOfUCERF2_Rup,
	 * 	rateOfUCERF2_Rup
	 * 
	 * This also saves an info file (INFO_FILE_NAME) that gives the status of associations for each 
	 * UCERF2 rupture.  A summary of the results are in the file FindEquivUCERF2_Ruptures_INFO
	 * (generated by hand), which reveals that the associations are good for all ruptures in the 
	 * N Cal RELM region (if this region is modified as noted in getMFD_forNcal() to avoid the inclusion 
	 * of Parkfield).  
	 * 
	 * I need to describe what's actually done here better.
	 * 
	 * More work will be need to go to the entire RELM Region.
	 */
	private void findSectionEndsForUCERF2_Rups() {
				
		ModMeanUCERF2 meanUCERF2 = getMeanUCERF2_Instance();
		
		// the following is a weak test
		if(meanUCERF2.getNumSources() != 409)
			throw new RuntimeException("Error - wrong number of sources; some UCERF2 adj params not set correctly?");
		
		// Make the list of UCERF2 sources to consider -- not needed if all non-CA B faults are included!
		ArrayList<Integer> ucerf2_srcIndexList = new ArrayList<Integer>();
		
		// Note that we're considering all but non-CA B fault sources
		if(D) System.out.println("Considering All but non-CA B fault sources");
		
		// the following indicates whether the source name equals the parent name of subsections
		// true for all non-connected type B faults
		boolean[] srcNameEqualsParentSectName = new boolean[NUM_UCERF2_SRC];
		for(int i=0; i<NUM_UCERF2_SRC;i++) {
			ucerf2_srcIndexList.add(i);
			srcNameEqualsParentSectName[i]=true;
//			System.out.println(i+"\t"+meanUCERF2.getSource(i).getName());
		}
		
		// now set the cases where srcNameEqualsParentSectName[i]=false (hard coded; found "by hand")
		for(int i=0; i<131; i++) // these are the segmented and A-fault connected sources
			srcNameEqualsParentSectName[i]=false;
		srcNameEqualsParentSectName[154]=false;	// the following all have "Connected" in the names
		srcNameEqualsParentSectName[178]=false;
		srcNameEqualsParentSectName[179]=false;
		srcNameEqualsParentSectName[188]=false;
		srcNameEqualsParentSectName[202]=false;
		srcNameEqualsParentSectName[218]=false;
		srcNameEqualsParentSectName[219]=false;
		srcNameEqualsParentSectName[227]=false;
		srcNameEqualsParentSectName[232]=false;
		srcNameEqualsParentSectName[239]=false;
		srcNameEqualsParentSectName[256]=false;
		srcNameEqualsParentSectName[264]=false;
		srcNameEqualsParentSectName[265]=false;
		srcNameEqualsParentSectName[270]=false;
		srcNameEqualsParentSectName[273]=false;
		
//		for(int i=0; i<numSrc;i++)
//			System.out.println(i+"\t"+srcNameEqualsParentSectName[i]+"\t"+meanUCERF2.getSource(i).getName());
			
		int numUCERF2_Ruptures = 0;
		for(int s=0; s<ucerf2_srcIndexList.size(); s++){
			numUCERF2_Ruptures += meanUCERF2.getSource(ucerf2_srcIndexList.get(s)).getNumRuptures();
		}
		// another weak test to make sure nothing has changed
		if(numUCERF2_Ruptures != NUM_RUPTURES)
			throw new RuntimeException("problem with NUM_RUPTURES; something changed?  old="+NUM_RUPTURES+"\tnew="+numUCERF2_Ruptures);
		
		if(D) System.out.println("Num UCERF2 Sources to Consider = "+ucerf2_srcIndexList.size());
		if(D) System.out.println("Num UCERF2 Ruptues to Consider = "+NUM_RUPTURES);
		
		// initialize the following to bogus indices (the default)
		for(int r=0;r<NUM_RUPTURES;r++) {
			firstSectOfUCERF2_Rup[r]=-1;
			lastSectOfUCERF2_Rup[r]=-1;
		}
		
		ArrayList<String> resultsString = new ArrayList<String>();
		ArrayList<String> problemSourceList = new ArrayList<String>();
		
		int rupIndex = -1;
		for(int s=0; s<ucerf2_srcIndexList.size(); s++){
			boolean problemSource = false;				// this will indicate that source has some problem
			boolean srcHasSubSeismogenicRups = false;	// this will check whether any ruptures are sub-seismogenic
			ProbEqkSource src = meanUCERF2.getSource(ucerf2_srcIndexList.get(s));
			if (D) System.out.println("working on source "+src.getName()+" "+s+" of "+ucerf2_srcIndexList.size());
			double srcDDW = src.getSourceSurface().getAveWidth();
			double totMoRate=0, partMoRate=0;
			// determine if src is in N Cal.
			RuptureSurface srcSurf = src.getSourceSurface();
			Location loc1 = srcSurf.getFirstLocOnUpperEdge();
			Location loc2 = srcSurf.getLastLocOnUpperEdge();
			boolean srcInsideN_Cal = false;
			if(relm_nocal_reg.contains(loc1) || relm_nocal_reg.contains(loc2))
				srcInsideN_Cal = true;

			for(int r=0; r<src.getNumRuptures(); r++){
				rupIndex += 1;
				ProbEqkRupture rup = src.getRupture(r);
				double ddw = rup.getRuptureSurface().getAveWidth();
				double len = rup.getRuptureSurface().getAveLength();
				double mag = ((int)(rup.getMag()*100.0))/100.0;	// nice value for writing
				totMoRate += MagUtils.magToMoment(rup.getMag())*rup.getMeanAnnualRate(30.0);
				srcIndexOfUCERF2_Rup[rupIndex] = s;
				rupIndexOfUCERF2_Rup[rupIndex] = r;;
				magOfUCERF2_Rup[rupIndex] = rup.getMag();
				lengthOfUCERF2_Rup[rupIndex] = len;
				rateOfUCERF2_Rup[rupIndex] = rup.getMeanAnnualRate(30.0);
				subSeismoUCERF2_Rup[rupIndex] = false;  // the default
				if(ddw < srcDDW) {
					subSeismoUCERF2_Rup[rupIndex] = true;
					srcHasSubSeismogenicRups = true;
					problemSource = true;
					String errorString = rupIndex+":\t"+"Sub-Seismogenic Rupture:  ddw="+ddw+"\t"+srcDDW+"\t"+len+"\t"+(float)mag+"\tiRup="+r+"\tiSrc="+s+"\t("+src.getName()+")\n";
					if(D) System.out.print(errorString);
					resultsString.add(errorString);
					partMoRate += MagUtils.magToMoment(rup.getMag())*rup.getMeanAnnualRate(30.0);
					continue;
				}
				FaultTrace rupTrace = rup.getRuptureSurface().getEvenlyDiscritizedUpperEdge();

				Location rupEndLoc1 = rupTrace.get(0);
				Location rupEndLoc2 = rupTrace.get(rupTrace.size()-1);
				ArrayList<Integer> closeSections1 = new ArrayList<Integer>();
				ArrayList<Integer> closeSections2 = new ArrayList<Integer>();
				double dist;
				for(int i=0; i<faultSectionData.size(); i++) {
					
					// skip if section's parent name should equal source name
					if(srcNameEqualsParentSectName[s])
						if(!faultSectionData.get(i).getParentSectionName().equals(src.getName()))
							continue;
					
					FaultTrace sectionTrace = faultSectionData.get(i).getStirlingGriddedSurface(1.0).getRowAsTrace(0);
					double sectHalfLength = 0.5*sectionTrace.getTraceLength()+0.5; 
					// Process first end of rupture
					dist = sectionTrace.minDistToLocation(rupEndLoc1);
					if(dist<sectHalfLength)  {
						// now keep only if both ends of section trace is less than 1/2 trace length away
						Location traceEnd1 = sectionTrace.get(0);
						Location traceEnd2 = sectionTrace.get(sectionTrace.size()-1);
						double dist1 =rupTrace.minDistToLocation(traceEnd1);
						double dist2 =rupTrace.minDistToLocation(traceEnd2);
						if(dist1 <sectHalfLength && dist2 <sectHalfLength)
							closeSections1.add(i);
					}
					// Process second end of rupture
					dist = sectionTrace.minDistToLocation(rupEndLoc2);
					if(dist<sectHalfLength)  {
						// now keep only if both ends of section trace is less than 1/2 trace length away
						Location traceEnd1 = sectionTrace.get(0);
						Location traceEnd2 = sectionTrace.get(sectionTrace.size()-1);
						double dist1 =rupTrace.minDistToLocation(traceEnd1);
						double dist2 =rupTrace.minDistToLocation(traceEnd2);
						if(dist1 <sectHalfLength && dist2 <sectHalfLength)
							closeSections2.add(i);
					}

				}
				
				// For cases that have 2 close sections, choose whichever is closest: 1) farthest point on closest section;or 2) nearest point
				// of farthest section (where distance of section is the ave of the distances of the ends to the end of rupture)
				// this breaks down for closely space sections (like Fickle Hill & Mad River)
				if(closeSections1.size() == 2) {
					FaultTrace sect1_trace = faultSectionData.get(closeSections1.get(0)).getStirlingGriddedSurface(1.0).getRowAsTrace(0);
					FaultTrace sect2_trace = faultSectionData.get(closeSections1.get(1)).getStirlingGriddedSurface(1.0).getRowAsTrace(0);
					double dist_tr1_end1=LocationUtils.horzDistanceFast(rupEndLoc1, sect1_trace.get(0));
					double dist_tr1_end2=LocationUtils.horzDistanceFast(rupEndLoc1, sect1_trace.get(sect1_trace.size()-1));
					double aveDistTr1 =  (dist_tr1_end1+dist_tr1_end2)/2;
					double dist_tr2_end1=LocationUtils.horzDistanceFast(rupEndLoc1, sect2_trace.get(0));
					double dist_tr2_end2=LocationUtils.horzDistanceFast(rupEndLoc1, sect2_trace.get(sect2_trace.size()-1));
					double aveDistTr2 =  (dist_tr2_end1+dist_tr2_end2)/2;
					double farEndOfCloseTr, closeEndOfFarTr;
					if(aveDistTr1<aveDistTr2) {  // trace 1 is closer
						if(dist_tr1_end1>dist_tr1_end2)
							farEndOfCloseTr=dist_tr1_end1;
						else
							farEndOfCloseTr=dist_tr1_end2;
						if(dist_tr2_end1<dist_tr2_end2)
							closeEndOfFarTr=dist_tr2_end1;
						else
							closeEndOfFarTr=dist_tr2_end2;
						if(farEndOfCloseTr<closeEndOfFarTr) // remove far trace from list, which is trace 2 (index 1)
							closeSections1.remove(1);
						else
							closeSections1.remove(0);
					}
					else {						// trace 2 is closer
						if(dist_tr2_end1>dist_tr2_end2)
							farEndOfCloseTr=dist_tr2_end1;
						else
							farEndOfCloseTr=dist_tr2_end2;
						if(dist_tr1_end1<dist_tr1_end2)
							closeEndOfFarTr=dist_tr1_end1;
						else
							closeEndOfFarTr=dist_tr1_end2;
						if(farEndOfCloseTr<closeEndOfFarTr) // remove far trace from list, which is trace 1 (index 0)
							closeSections1.remove(0);
						else
							closeSections1.remove(1);
					}
						
				}
				if(closeSections2.size() == 2) {
					FaultTrace sect1_trace = faultSectionData.get(closeSections2.get(0)).getStirlingGriddedSurface(1.0).getRowAsTrace(0);
					FaultTrace sect2_trace = faultSectionData.get(closeSections2.get(1)).getStirlingGriddedSurface(1.0).getRowAsTrace(0);
					double dist_tr1_end1=LocationUtils.horzDistanceFast(rupEndLoc1, sect1_trace.get(0));
					double dist_tr1_end2=LocationUtils.horzDistanceFast(rupEndLoc1, sect1_trace.get(sect1_trace.size()-1));
					double aveDistTr1 =  (dist_tr1_end1+dist_tr1_end2)/2;
					double dist_tr2_end1=LocationUtils.horzDistanceFast(rupEndLoc1, sect2_trace.get(0));
					double dist_tr2_end2=LocationUtils.horzDistanceFast(rupEndLoc1, sect2_trace.get(sect2_trace.size()-1));
					double aveDistTr2 =  (dist_tr2_end1+dist_tr2_end2)/2;
					double farEndOfCloseTr, closeEndOfFarTr;
					if(aveDistTr1<aveDistTr2) {  // trace 1 is closer
						if(dist_tr1_end1>dist_tr1_end2)
							farEndOfCloseTr=dist_tr1_end1;
						else
							farEndOfCloseTr=dist_tr1_end2;
						if(dist_tr2_end1<dist_tr2_end2)
							closeEndOfFarTr=dist_tr2_end1;
						else
							closeEndOfFarTr=dist_tr2_end2;
						if(farEndOfCloseTr<closeEndOfFarTr) // remove far trace from list, which is trace 2 (index 1)
							closeSections2.remove(1);
						else
							closeSections2.remove(0);
					}
					else {						// trace 2 is closer
						if(dist_tr2_end1>dist_tr2_end2)
							farEndOfCloseTr=dist_tr2_end1;
						else
							farEndOfCloseTr=dist_tr2_end2;
						if(dist_tr1_end1<dist_tr1_end2)
							closeEndOfFarTr=dist_tr1_end1;
						else
							closeEndOfFarTr=dist_tr1_end2;
						if(farEndOfCloseTr<closeEndOfFarTr) // remove far trace from list, which is trace 1 (index 0)
							closeSections2.remove(0);
						else
							closeSections2.remove(1);
					}
						
				}

				String sectName1 = "None Found";
				String sectName2 = "None Found";
				
				if(closeSections1.size() == 1) {
					firstSectOfUCERF2_Rup[rupIndex]=closeSections1.get(0);
					sectName1=faultSectionData.get(firstSectOfUCERF2_Rup[rupIndex]).getSectionName();
				}
				else if(closeSections1.size() > 1){
					problemSource = true;
					String errorString = "Error - end1 num found not 1, but "+closeSections1.size() + " -- for rup "+r+
										 " of src "+s+", M="+(float)rup.getMag()+", ("+src.getName()+"); Sections:";
					for(int sect:closeSections1)
						errorString += "\t"+faultSectionData.get(sect).getName();
					errorString += "\n";
					if(D) System.out.print(errorString);
					resultsString.add(errorString);
				}
				else { // if zero found
					problemSource = true;
					String end="S";
					if(rupEndLoc1.getLatitude()>rupEndLoc2.getLatitude())
						end = "N";
					String errorString = "Error - "+end+"-end num found not 1, but "+closeSections1.size() +
					" -- for rup "+r+" of src "+s+", M="+(float)rup.getMag()+", ("+src.getName()+")\n";
					if(D) System.out.print(errorString);
					resultsString.add(errorString);
				}

				
				if(closeSections2.size() == 1) {
					lastSectOfUCERF2_Rup[rupIndex]=closeSections2.get(0);
					sectName2=faultSectionData.get(lastSectOfUCERF2_Rup[rupIndex]).getSectionName();
				}
				else if(closeSections2.size() > 1){
					problemSource = true;
					String errorString = "Error - end2 num found not 1, but "+closeSections2.size() + " -- for rup "+r+
										 " of src "+s+", M="+(float)rup.getMag()+", ("+src.getName()+"); Sections:";
					for(int sect:closeSections2)
						errorString += "\t"+faultSectionData.get(sect).getName();
					errorString += "\n";
					if(D) System.out.print(errorString);
					resultsString.add(errorString);
				}
				else { // if zero found
					problemSource = true;
					String end="S";
					if(rupEndLoc2.getLatitude()>rupEndLoc1.getLatitude())
						end = "N";
					String errorString = "Error - "+end+"-end num found not 1, but "+closeSections2.size() +
					" -- for rup "+r+" of src "+s+", M="+(float)rup.getMag()+", ("+src.getName()+")\n";
					if(D) System.out.print(errorString);	
					resultsString.add(errorString);
				}
				
				// *****************************
				// Hard coded special case fixes
				if(firstSectOfUCERF2_Rup[rupIndex]==707 & lastSectOfUCERF2_Rup[rupIndex] < 700) {
					String fixSectName=faultSectionData.get(608).getSectionName();
					if(!sectName1.equals("Zayante-Vergeles, Subsection 0") || !fixSectName.equals("San Andreas (Santa Cruz Mtn), Subsection 0")) 
						throw new RuntimeException("Problem: something changed and hard coded fix no longer applicable");
					System.out.println("Fixing problem: "+firstSectOfUCERF2_Rup[rupIndex]+"\t"+lastSectOfUCERF2_Rup[rupIndex]);
					firstSectOfUCERF2_Rup[rupIndex]=608;
					sectName1 = fixSectName;
				}
				if(lastSectOfUCERF2_Rup[rupIndex]==707 & firstSectOfUCERF2_Rup[rupIndex] < 700) {
					String fixSectName=faultSectionData.get(608).getSectionName();
					if(!sectName2.equals("Zayante-Vergeles, Subsection 0") || !fixSectName.equals("San Andreas (Santa Cruz Mtn), Subsection 0")) 
						throw new RuntimeException("Problem: something changed and hard coded fix no longer applicable");
					System.out.println("Fixing problem: "+firstSectOfUCERF2_Rup[rupIndex]+"\t"+lastSectOfUCERF2_Rup[rupIndex]);
					lastSectOfUCERF2_Rup[rupIndex]=608;
					sectName2 = fixSectName;
				}
				// ********** End of hard-coded fix ****************
				

				String result = rupIndex+":\t"+firstSectOfUCERF2_Rup[rupIndex]+"\t"+lastSectOfUCERF2_Rup[rupIndex]+"\t("+sectName1+"   &  "+
								sectName2+")  are the Sections at ends of rup "+ r+" of src "+s+", M="+(float)rup.getMag()+", ("+src.getName()+")\n";
//				if(D) System.out.print(result);
				resultsString.add(result);
				
				// check if source is inside N Cal region
				
				
				if(problemSource && srcInsideN_Cal && !problemSourceList.contains(src.getName()))
					problemSourceList.add(src.getName());


//				if(D) System.out.println("Sections at ends of rup "+r+" of src "+s+", M="+(float)rup.getMag()+", ("+
//						src.getName()+") are: "+firstSectOfUCERF2_Rup[rupIndex]+" ("+sectName1+")  & "+lastSectOfUCERF2_Rup[rupIndex]+" ("+sectName2+")");
			}
			String infoString = (float)(partMoRate/totMoRate) +" is the fract MoRate below for "+src.getName();
			if(srcHasSubSeismogenicRups) {
				if(D) System.out.println(infoString);
				resultsString.add(infoString+"\n");
			}
		}
		
		// write info results
		String fullpathname = precomputedDataDir.getAbsolutePath()+File.separator+INFO_FILE_NAME;
		FileWriter fw;
		try {
			fw = new FileWriter(fullpathname);
			for(String line:resultsString)
				fw.write(line);
			fw.write("N Cal Problem Sources:\n");
			for(String line:problemSourceList)
				fw.write(line+"\n");
			fw.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		
		// write out the data
		try {
			FileOutputStream file_output = new FileOutputStream (dataFile);
			// Wrap the FileOutputStream with a DataOutputStream
			DataOutputStream data_out = new DataOutputStream (file_output);
			for(int i=0; i<NUM_RUPTURES;i++) {
				data_out.writeInt(firstSectOfUCERF2_Rup[i]);
				data_out.writeInt(lastSectOfUCERF2_Rup[i]);
				data_out.writeInt(srcIndexOfUCERF2_Rup[i]);
				data_out.writeInt(rupIndexOfUCERF2_Rup[i]);
				data_out.writeDouble(magOfUCERF2_Rup[i]);
				data_out.writeDouble(lengthOfUCERF2_Rup[i]);
				data_out.writeDouble(rateOfUCERF2_Rup[i]);
				data_out.writeBoolean(subSeismoUCERF2_Rup[i]);
			}
			file_output.close ();
		}
		catch (IOException e) {
			System.out.println ("IO exception = " + e );
		}
	}
	
	

	
	
	/**
	 * This returns the magnitude and rate of the equivalent UCERF2 ruptures by finding those that have
	 * the same first and last section index.  If more than one UCERF2 rupture has the same end sections
	 * (as would come from the type-A segmented models where there are diff mags for the same char rup),
	 * then the total rate of these is returned with a magnitude that preserves the total moment rate.
	 * 
	 * @param sectIndicesForRup
	 * @return - double[2] where mag is in the first element and rate is in the second
	 */
	private double[] getMagAndRateForRupture(List<Integer> sectIndicesForRup) {
		Integer firstSectIndex = sectIndicesForRup.get(0);
		Integer lastSectIndex = sectIndicesForRup.get(sectIndicesForRup.size()-1);
		ArrayList<Integer> equivUCERF2_Rups = new ArrayList<Integer>();
		for(int r=0; r<NUM_RUPTURES;r++) {
			if(firstSectOfUCERF2_Rup[r] == firstSectIndex && lastSectOfUCERF2_Rup[r] == lastSectIndex)
				equivUCERF2_Rups.add(r);
			// check for ends swapped between the two ruptures
			else if(firstSectOfUCERF2_Rup[r] == lastSectIndex && lastSectOfUCERF2_Rup[r] == firstSectIndex)
				equivUCERF2_Rups.add(r);			
		}

		if(equivUCERF2_Rups.size()==0) {
			return null;
		}
		else if(equivUCERF2_Rups.size()==1) {
			int r = equivUCERF2_Rups.get(0);
			// this makes sure a UCERF2 rupture is not used more than once
			if(ucerf2_rupUsed[r]) throw new RuntimeException("Error - UCERF2 rutpure already used");
			ucerf2_rupUsed[r] = true;
			double[] result = new double[2];
			result[0]=magOfUCERF2_Rup[r];
			result[1]=rateOfUCERF2_Rup[r];
			mfdOfAssocRupsAndModMags.addResampledMagRate(result[0], result[1], true);
			mfdOfAssocRupsWithOrigMags.addResampledMagRate(result[0], result[1], true);
			return result;
		}
		else {
			double totRate=0;
			double totMoRate=0;
			for(Integer r:equivUCERF2_Rups) {
				if(ucerf2_rupUsed[r]) throw new RuntimeException("Error - UCERF2 rutpure already used");
				ucerf2_rupUsed[r] = true;
				totRate+=rateOfUCERF2_Rup[r];
				totMoRate+=rateOfUCERF2_Rup[r]*MagUtils.magToMoment(magOfUCERF2_Rup[r]);
				mfdOfAssocRupsWithOrigMags.addResampledMagRate(magOfUCERF2_Rup[r], rateOfUCERF2_Rup[r], true);
			}
			double aveMoment = totMoRate/totRate;
			double mag = MagUtils.momentToMag(aveMoment);
			double[] result = new double[2];
			result[0]=mag;
			result[1]=totRate;
			mfdOfAssocRupsAndModMags.addResampledMagRate(mag, totRate, true);
			return result;
		}
	}

	
	
	/**
	 * This returns an array list containing the UCERF2 equivalent mag and rate for each rupture 
	 * (or null if there is no corresponding UCERF2 rupture, which will usually be the case).
	 * The mag and rate are in the double[] objects (at index 0 and 1, respectively).
	 * 
	 * If more than one inversion ruptures have the same end sections (multi pathing), then the one with the minimum number 
	 * of sections get's the equivalent UCERF2 ruptures (and an exception is thrown if there are more than one inversion 
	 * rupture that have this same minimum number of sections; which doesn't occur for N Cal ruptures).
	 * 
	 * This also computes the mag-freq-dists: mfdOfAssocRupsAndModMags and mfdOfAssocRupsWithOrigMags, where the latter
	 * preserves the aleatory range of mags for given area (e.g., on the char events) and the former uses the combined
	 * mean mag used for the association (preserving moment rates)
	 * @param inversionRups
	 * @return
	 */
	public ArrayList<double[]> getMagsAndRatesForRuptures(List<? extends List<Integer>> inversionRups) {

		ucerf2_rupUsed = new boolean[NUM_RUPTURES];
		for(int r=0;r<NUM_RUPTURES;r++) ucerf2_rupUsed[r] = false;
		
		mfdOfAssocRupsAndModMags = new SummedMagFreqDist(5.05,35,0.1);
		mfdOfAssocRupsAndModMags.setName("MFD for UCERF2 associated ruptures");
		mfdOfAssocRupsAndModMags.setInfo("using modified (average) mags; this excludes sub-seimogenic rups");
		mfdOfAssocRupsWithOrigMags = new SummedMagFreqDist(5.05,35,0.1);
		mfdOfAssocRupsWithOrigMags.setName("MFD for UCERF2 associated ruptures");
		mfdOfAssocRupsWithOrigMags.setInfo("using original mags; this excludes sub-seimogenic rups");

		int numInvRups = inversionRups.size();
		
		// first establish which ruptures to use for multi-path cases (set in ignoreRup[])
		boolean[] ignoreRup = new boolean[numInvRups];
		boolean[] rupChecked = new boolean[numInvRups];
		for(int r=0;r<numInvRups;r++) {
			rupChecked[r] = false;	// this will indicate that a rupture has already been encountered via multi-pathing and to therefore ignore it
			ignoreRup[r] = false;	// this will indicate to ignore a rupture because it is a multi path and another has been chosen
		}
		for(int r=0;r<numInvRups;r++) {
			if(rupChecked[r])	// if this rupture was already checked because it was in a 
				continue;		// multi-path list of a previous rupture, then skip it
			List<Integer> rup = inversionRups.get(r);
			int firstSectID = rup.get(0);
			int lastSectID = rup.get(rup.size()-1);
			ArrayList<Integer> multiPathRups = new ArrayList<Integer>();	// this will be the list of ruptures that share the same end sections
			multiPathRups.add(r);
			for(int r2=r+1;r2<inversionRups.size();r2++) {
				List<Integer> rup2 = inversionRups.get(r2);
				int firstSectID2 = rup2.get(0);
				int lastSectID2 = rup2.get(rup2.size()-1);
				if((firstSectID2 == firstSectID && lastSectID2 == lastSectID) || (firstSectID2 == lastSectID && lastSectID2 == firstSectID)) {
					multiPathRups.add(r2);
				}
			}
			// now deal with it if it's a multi-path
			if(multiPathRups.size()>1) {
				// find the minNumSections
				int minNumSect = Integer.MAX_VALUE;
				for(Integer i:multiPathRups)
					if(inversionRups.get(i).size()<minNumSect) 
						minNumSect=inversionRups.get(i).size();
				// check that only one rupture here has that minimum, and throw exception if not (since I don't know how to choose)
				int numWithMinNumSect =0;
				for(Integer i:multiPathRups)
					if(inversionRups.get(i).size() == minNumSect) numWithMinNumSect += 1;
				if(numWithMinNumSect != 1)
					throw new RuntimeException("Problem: two inversion ruptures (with same section ends) have the same min number of sections; this case is not supported");
				// now set what to do with these ruptures
				for(Integer i:multiPathRups) {
					rupChecked[i] = true;	// indicate that this rupture has already been considered for multi pathing (so it won't check this one again)
					if(inversionRups.get(i).size() != minNumSect) 
						ignoreRup[i] = true;	// take this rupture out of consideration (the one with the min stays with the default ignoreRup=false)
				}
			}
		}
		
		// now get the mags and rates of each
		ArrayList<double[]> results = new ArrayList<double[]>();
		for(int r=0; r<numInvRups; r++) {
			if(!ignoreRup[r])	// make sure it's not a multi-path rupture that should be ignored
				results.add(getMagAndRateForRupture(inversionRups.get(r)));
			else
				results.add(null);
		}
		
		if(D) {
			System.out.println("Rate & Moment Rate for mfdOfAssocRupsAndModMags: "+
					(float)mfdOfAssocRupsAndModMags.getTotalIncrRate()+",\t"+
					(float)mfdOfAssocRupsAndModMags.getTotalMomentRate());
			System.out.println("Rate & Moment Rate for mfdOfAssocRupsWithOrigMags: "+
					(float)mfdOfAssocRupsWithOrigMags.getTotalIncrRate()+",\t"+
					(float)mfdOfAssocRupsWithOrigMags.getTotalMomentRate());
		}
		return results;
	}
	
	
	public void plotMFD_TestForNcal() {
		ArrayList funcs = new ArrayList();
		ArrayList<SummedMagFreqDist> mfdsForUCERF2AssocRups = getMFDsForUCERF2AssocRups();
		funcs.addAll(mfdsForUCERF2AssocRups);
		funcs.addAll(getMFDsForNcal());
		funcs.add(getN_CalTotalTargetGR_MFD());
		IncrementalMagFreqDist backWshks = getN_Cal_UCERF2_BackgrMFD_WithAfterShocks();
		funcs.add(backWshks);
		
		// sum 
		SummedMagFreqDist sumMFD = new SummedMagFreqDist(5.05,35,0.1);
		sumMFD.addIncrementalMagFreqDist(mfdsForUCERF2AssocRups.get(0));
		sumMFD.addIncrementalMagFreqDist(backWshks);
		sumMFD.setName("total a-priori MFD");
		sumMFD.setName("this = getApproxN_Cal_UCERF2_BackgrMFDwAftShks() + mfdsForUCERF2AssocRups.get(0)");
		funcs.add(sumMFD);
		
		// target minus background
		IncrementalMagFreqDist targetMinusBackground = getN_CalTargetMinusBackground_MFD();
		funcs.add(targetMinusBackground);
		
		GraphWindow graph = new GraphWindow(funcs, "Mag-Freq Dists"); 
		graph.setX_AxisLabel("Mag");
		graph.setY_AxisLabel("Rate");
		graph.setYLog(true);
		graph.setY_AxisRange(1e-6, 1.0);
		
		ArrayList funcs2 = new ArrayList();
		funcs2.add(getMFDsForUCERF2AssocRups().get(0).getCumRateDistWithOffset());
		funcs2.add(backWshks.getCumRateDistWithOffset());
		funcs2.add(getN_CalTotalTargetGR_MFD().getCumRateDistWithOffset());
		EvenlyDiscretizedFunc sumCumMFD = sumMFD.getCumRateDistWithOffset();
		sumCumMFD.setName("Sum of Dataset 1 and 2 above");
		sumCumMFD.setInfo("This is a measure of the current bulge problem in N Cal.");
		funcs2.add(sumCumMFD);
		funcs2.add(targetMinusBackground.getCumRateDistWithOffset());
		GraphWindow graph2 = new GraphWindow(funcs2, "Cum Mag-Freq Dists"); 
		graph2.setX_AxisLabel("Mag");
		graph2.setY_AxisLabel("Rate");
		graph2.setYLog(true);
		graph2.setY_AxisRange(1e-6, 10.0);
	}
	
	
	/**
	 * This is a target GR Distribution for N Cal RELM Region (based on info from Karen)
	 * @return
	 */
	public GutenbergRichterMagFreqDist getN_CalTotalTargetGR_MFD() {
		if(nCalTotalTargetGR_MFD != null)
			return nCalTotalTargetGR_MFD;
		double totCumRateWithAftershocks = 3.4;	// From Karen; see my email to her on 3/1/11
		nCalTotalTargetGR_MFD = new GutenbergRichterMagFreqDist(5.05,35,0.1);
		nCalTotalTargetGR_MFD.setAllButTotMoRate(5.05, 8.45, totCumRateWithAftershocks, 1.0);
		nCalTotalTargetGR_MFD.setName("Target N Cal GR Dist");
		nCalTotalTargetGR_MFD.setInfo("Assuming Karen's total rate above 5.0 of 3.4 per yr (including aftershocks)");
		return nCalTotalTargetGR_MFD;
	}
	
	
	/**
	 * 
	 * @return
	 */
	public IncrementalMagFreqDist getN_CalTargetMinusBackground_MFD() {
		if(nCalTargetMinusBackground != null)
			return nCalTargetMinusBackground;
		
		// create the other MFDs if they don't exist
		if(nCalTotalTargetGR_MFD == null)
			getN_CalTotalTargetGR_MFD();
		if(nCal_UCERF2_BackgrMFD_WithAftShocks == null)
			getN_Cal_UCERF2_BackgrMFD_WithAfterShocks();

		nCalTargetMinusBackground= new IncrementalMagFreqDist(5.05,35,0.1);
		for(int m=0; m<nCalTargetMinusBackground.size();m++)
			nCalTargetMinusBackground.set(m,nCalTotalTargetGR_MFD.getY(m)-nCal_UCERF2_BackgrMFD_WithAftShocks.getY(m));
		nCalTargetMinusBackground.setName("N. Cal Target Minus Background");
		nCalTargetMinusBackground.setInfo(" ");
		return nCalTargetMinusBackground;
	}

	
	
	/**
	 * Background here include C zones and non-CA B faults, plus aftershocks
	 */
	public IncrementalMagFreqDist getN_Cal_UCERF2_BackgrMFD_WithAfterShocks() {
		
		if(nCal_UCERF2_BackgrMFD_WithAftShocks != null)
			return nCal_UCERF2_BackgrMFD_WithAftShocks;
		
		getMeanUCERF2_Instance(); 
		meanUCERF2.setParameter(UCERF2.BACK_SEIS_NAME, UCERF2.BACK_SEIS_INCLUDE);
		meanUCERF2.updateForecast();
		
		SummedMagFreqDist totMFD = ERF_Calculator.getMagFreqDistInRegion(meanUCERF2, relm_nocal_reg,5.05,35,0.1, true);

		IncrementalMagFreqDist faultMFD = getMFDsForNcal().get(0);
		
		nCal_UCERF2_BackgrMFD_WithAftShocks = new IncrementalMagFreqDist(5.05,35,0.1);
		
		for(int m=0; m<nCal_UCERF2_BackgrMFD_WithAftShocks.size();m++) {
			double mag = nCal_UCERF2_BackgrMFD_WithAftShocks.getX(m);
			nCal_UCERF2_BackgrMFD_WithAftShocks.set(m, totMFD.getY(mag)-faultMFD.getY(mag));
		}
		
		// convert to cum MFD and add aftershocks using Karen's formula
		EvenlyDiscretizedFunc cumBackgroundMFD = nCal_UCERF2_BackgrMFD_WithAftShocks.getCumRateDistWithOffset();
		//now add the aftershocks
		for(int m=0; m<cumBackgroundMFD.size();m++) {
			// Karen's fraction of earthquakes >=M that are aftershocks or foreshocks = 1 - 0.039*10^(0.2*M)
			double mag = cumBackgroundMFD.getX(m);
			double frAshock = 1.0 - 0.039*Math.pow(10.0,0.2*mag);
			if(frAshock<0) frAshock=0;
			double corr = 1.0/(1.0-frAshock);
			// System.out.println("\t"+mag+"\t"+corr);

			cumBackgroundMFD.set(m,cumBackgroundMFD.getY(m)*corr);
		}
		
		// convert back to incremental dist
		for(int m=0; m<nCal_UCERF2_BackgrMFD_WithAftShocks.size();m++) {
			double yVal = cumBackgroundMFD.getY(m)-cumBackgroundMFD.getY(m+1);
			nCal_UCERF2_BackgrMFD_WithAftShocks.set(m,yVal);
			if(nCal_UCERF2_BackgrMFD_WithAftShocks.getX(m) > 7.8) nCal_UCERF2_BackgrMFD_WithAftShocks.set(m,0.0);  // fix weird values
		}
		
		// finally, correct so that faultMFD + backgroundMFD = target
		double target = getN_CalTotalTargetGR_MFD().getTotalIncrRate() - faultMFD.getTotalIncrRate();
		double totHere = nCal_UCERF2_BackgrMFD_WithAftShocks.getTotalIncrRate();
		for(int m=0; m<nCal_UCERF2_BackgrMFD_WithAftShocks.size();m++) {
			nCal_UCERF2_BackgrMFD_WithAftShocks.set(m,nCal_UCERF2_BackgrMFD_WithAftShocks.getY(m)*target/totHere);
		}
		
//System.out.println(target+"\t"+totHere);

		nCal_UCERF2_BackgrMFD_WithAftShocks.setName("UCERF2 N Cal Background MFD - Approximate");
		nCal_UCERF2_BackgrMFD_WithAftShocks.setInfo("Including C-zones, non CA B faults, and aftershocks");
	/*	
		totMFD.setInfo(" ");
		totMFD.setName(" ");
		faultMFD.setInfo(" ");
		faultMFD.setName(" ");
		backgroundMFD.setInfo(" ");
		backgroundMFD.setName(" ");
		ArrayList funcs = new ArrayList();
		funcs.add(totMFD);
		funcs.add(faultMFD);
		funcs.add(backgroundMFD);
		GraphWindow graph = new GraphWindow(funcs, "Mag-Freq Dists"); 
		graph.setX_AxisLabel("Mag");
		graph.setY_AxisLabel("Rate");
		graph.setYLog(true);
		graph.setY_AxisRange(1e-6, 1.0);
*/
		return nCal_UCERF2_BackgrMFD_WithAftShocks;
	}
	
	
	/**
	 * This plots various nucleation MDFs in a box around the Northridge earthquake
	 */
	public static void plotMFD_InRegionNearNorthridge() {

		Region region = new Region(new Location(34.25,-119.15),new Location(34.55,-118.35));

		ModMeanUCERF2 meanUCERF2 = new ModMeanUCERF2();
		meanUCERF2.setParameter(UCERF2.BACK_SEIS_NAME, UCERF2.BACK_SEIS_INCLUDE);
		meanUCERF2.setParameter(UCERF2.BACK_SEIS_RUP_NAME, UCERF2.BACK_SEIS_RUP_POINT);
		meanUCERF2.setParameter(UCERF2.PROB_MODEL_PARAM_NAME, UCERF2.PROB_MODEL_POISSON);
		meanUCERF2.getTimeSpan().setDuration(30.0);
		meanUCERF2.setParameter(UCERF2.FLOATER_TYPE_PARAM_NAME, UCERF2.CENTERED_DOWNDIP_FLOATER);
		meanUCERF2.updateForecast();
		
		ArrayList<String> srcNamesList = new ArrayList<String>();
		HashMap<String,SummedMagFreqDist> srcMFDs = new HashMap<String,SummedMagFreqDist>();

		SummedMagFreqDist magFreqDist = new SummedMagFreqDist(5.05, 36, 0.1);
		double duration = meanUCERF2.getTimeSpan().getDuration();
		for (int s = 0; s < meanUCERF2.getNumSources(); ++s) {
			ProbEqkSource source = meanUCERF2.getSource(s);
			for (int r = 0; r < source.getNumRuptures(); ++r) {
				ProbEqkRupture rupture = source.getRupture(r);
				double mag = rupture.getMag();
				double equivRate = rupture.getMeanAnnualRate(duration);
				RuptureSurface rupSurface = rupture.getRuptureSurface();
				LocationList locsOnRupSurf = rupture.getRuptureSurface().getEvenlyDiscritizedListOfLocsOnSurface();
				double ptRate = equivRate/locsOnRupSurf.size();
				ListIterator<Location> it = locsOnRupSurf.listIterator();
				while (it.hasNext()) {
					//discard the pt if outside the region 
					if (!region.contains(it.next()))
						continue;
					magFreqDist.addResampledMagRate(mag, ptRate, true);
					if(!srcMFDs.containsKey(source.getName())) {
						SummedMagFreqDist srcMFD = new SummedMagFreqDist(5.05, 36, 0.1);
						srcMFD.setName(source.getName());
						srcMFD.setInfo(" ");
						srcMFDs.put(source.getName(), srcMFD);
						srcNamesList.add(source.getName());
					}
					srcMFDs.get(source.getName()).addResampledMagRate(mag, ptRate, true);
				}
			}
		}
		
		String info  = "Sources in box:\n\n";
		for(String name:srcNamesList)
			info += "\t"+name+"\n";

//		magFreqDist.setInfo(info);
		magFreqDist.setInfo(" ");
		magFreqDist.setName("Total Incremental MFD in Box");
		ArrayList funcs = new ArrayList();
		funcs.add(magFreqDist);
		EvenlyDiscretizedFunc cumMFD = magFreqDist.getCumRateDistWithOffset();
		cumMFD.setName("Total Cumulative MFD in Box");
		cumMFD.setInfo(" ");
		funcs.add(cumMFD);	
		for(String key:srcMFDs.keySet())
			funcs.add(srcMFDs.get(key));
		GraphWindow graph = new GraphWindow(funcs, "Mag-Freq Dists"); 
		graph.setX_AxisLabel("Mag");
		graph.setY_AxisLabel("Rate");
		graph.setY_AxisRange(1e-6, 0.1);
		graph.setX_AxisRange(5, 8);
		/**/
		ArrayList<PlotCurveCharacterstics> curveCharacteristics = new ArrayList<PlotCurveCharacterstics>();
		curveCharacteristics.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 1f, null, 4f, Color.BLUE, 1));
		curveCharacteristics.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 4f, null, 4f, Color.BLACK, 1));
		graph.setPlotChars(curveCharacteristics);
		
		graph.setYLog(true);
		try {
			graph.saveAsPDF("MFD_NearNorthridgeUCERF2.pdf");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}


	

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		
		plotMFD_InRegionNearNorthridge();
		
//		FindEquivUCERF2_Ruptures test = new FindEquivUCERF2_Ruptures();

	}

}
