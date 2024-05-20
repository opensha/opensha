/**
 * 
 */
package scratch.UCERF3.utils.FindEquivUCERF2_Ruptures;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.dom4j.DocumentException;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.xyz.GriddedGeoDataSet;
import org.opensha.commons.eq.MagUtils;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.ProbEqkRupture;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.earthquake.calc.ERF_Calculator;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.UCERF2;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.MeanUCERF2.MeanUCERF2;
import org.opensha.sha.faultSurface.CompoundSurface;
import org.opensha.sha.faultSurface.EvenlyGriddedSurface;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.commons.gui.plot.GraphWindow;
import org.opensha.sha.magdist.SummedMagFreqDist;

import com.google.common.base.Preconditions;

import scratch.UCERF3.analysis.GMT_CA_Maps;
import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.enumTreeBranches.InversionModels;
import scratch.UCERF3.inversion.InversionFaultSystemRupSetFactory;
import scratch.UCERF3.utils.DeformationModelFetcher;
import scratch.UCERF3.utils.UCERF3_DataUtils;
import scratch.UCERF3.utils.ModUCERF2.ModMeanUCERF2;
import scratch.UCERF3.utils.ModUCERF2.ModMeanUCERF2_FM2pt1;
import scratch.UCERF3.utils.ModUCERF2.ModMeanUCERF2_FM2pt2;

/**
 * This class associates UCERF2 ruptures with ruptures in the given FM 3 FaultSystemRuptureSet.  That is, for 
 * a given FaultSystemRuptureSet rupture, this will give a total rate and average magnitude (preserving 
 * moment rate) from UCERF2 for that rupture (an average in case more than one UCERF2 rupture associates 
 * with a given FaultSystemRuptureSet rupture).  The resultant UCERF2-equivalent mag and rate for each 
 * FaultSystemRuptureSet rupture is given by the getMagsAndRatesForRuptures() method.
 * 
 * This class assumes the FaultSystemRuptureSet has been constructed using parent sections from
 * Fault Model 3.1 or 3.2 (specified in the constructor).
 * 
 * This class works for any subset of sections used in building the FaultSystemRuptureSet (e.g., just the
 * bay area or just N. Cal.).
 * 
 * On first run this class will generate data file that gets stored in the given precomputedDataDir, and
 * on subsequent runs it will read this file rather than recompute everything.  The naming convention for
 * this file is DATA_FILE_PREFIX plus the fault model number, plus the number of sections and ruptures in the 
 * given FaultSystemRuptureSet (separated by "_").  This convention assumes that the number of sections and 
 * ruptures uniquely defines a faultSysRupSet (in terms of the UCERF2 mapping).
 * 
 * This class also generates an info file on the first run (named INFO_FILE_PATH_PREFIX plus the fault number and
 * the number of sections and ruptures, as in the data file described above).  For each rupture in UCERF2, this info  
 * file gives the associated FaultSystemRuptureSet sections assigned to the end of the rupture.  This file also 
 * reports any problems in this mapping (i.e., where no sections from FaultSystemRuptureSet could be assigned
 * to the ends of a given UCERF2 rupture (e.g., as will be the case for any S. Cal. UCERF2 ruptures when 
 * FaultSystemRuptureSet has been created for N. Cal.).  All sources that suffer this problem are listed under
 * "Problem Sources (can't find associated inv section for one end of at least one rupture):" at the end of
 * this info file.  Also listed are any "subseismogenic" ruptures, defined as UCERF2 ruptures that are assigned
 * the same FaultSystemRuptureSet section to both ends of the rupture (for which there will be no mapping since
 * FaultSystemRuptureSet ruptures are composed of two or more sections, which we assume here).  Sources
 * that have one or more subsection ruptures are also listed at the end of this info file (under
 * "Subseimso Sources (has one or more subseismogenic ruptures):"), together with the fraction moment rate
 * these subseismogenic ruptures constitute for the source.  Finally, the very end of this file also lists
 * any UCERF2 ruptures that went un-associated (but not reported as a "problem" source above, meaning 
 * sections were assigned to the rup ends so some corresponding FaultSystemRuptureSet rupture should exist)
 * All ruptures in this category should utilize only one sub-section of a parent section (which are filtered
 * out of the inversion rupture set as of 2/22/12) or they violate some other rule (such as cumulative azimuth
 * change).
 * 
 * This class also computes various MFDs for verification.
 * 
 * 
 * Important Notes:
 * 
 * 1) Most of the inversion ruptures have no UCERF2 association; these are null.
 * 2) This ignores the non-CA type B faults in UCERF2 (since they are not included in the current inversion).
 * 3) This uses a special version of MeanUCERF2 (that computes floating rupture areas as the average of 
 *    HB and EllB) and also used only branches for fault model 2.1 (correcting rates accordingly.  This also 
 *    applies the option where floating ruptures are extended all the way down dip.
 * 4) Note that the Mendocino section was not used in UCERF2, so there are no UCERF2 ruptures for this fault.
 * 
 * 
 *
 * TO DO:
 * ------
 * 
 * 1) Further verify mappings in SCEC-VDO?
 * 
 * 2) Verify that a valid deformation model was used by checking what comes from 
 *    FaultSystemRuptureSet.getDeformationModelName() (as soon as Kevin adds the latter 
 *    as I requested on 10-13-11)?
 *    
 * 3) Fix the problem where the associated rupture can be extended on both ends (relative to the UCERF2 orig rup), whereas if one 
 *    end is and extension the other should be shorter (to preserve area better)?
 * 
 * @author field
 */
public class FindEquivUCERF2_FM3_Ruptures extends FindEquivUCERF2_Ruptures {
	
	protected final static boolean D = false;  // for debugging
	
	ArrayList<ArrayList<String>> parentSectionNamesForUCERF2_Sources;
	
	ArrayList<double[]> magsAndRatesForRuptures;
	
	String DATA_FILE_PREFIX = "equivUCERF2_RupDataFM3";
	final static String INFO_FILE_PATH_PREFIX = "InfoForUCERF2_RupAssociationsFM3";
	final static String SECT_FOR_UCERF2_SRC_FILE_PATH_NAME_1 = "FM3_1_SectionsForUCERF2_Sources.txt";
	final static String SECT_FOR_UCERF2_SRC_FILE_PATH_NAME_2 = "FM3_2_SectionsForUCERF2_Sources.txt";
	FileWriter info_fw;
	File dataFile;
	
	FaultModels faultModel;
	
	
	// the following hold info about each UCERF2 rupture
	int[] firstSectOfUCERF2_Rup, lastSectOfUCERF2_Rup, srcIndexOfUCERF2_Rup, rupIndexOfUCERF2_Rup, invRupIndexForUCERF2_Rup;
	double[] magOfUCERF2_Rup, lengthOfUCERF2_Rup, rateOfUCERF2_Rup;
	boolean[] subSeismoUCERF2_Rup, problemUCERF2_Source;
	
	// the following lists the indices of all  UCERF2 ruptures associated with each inversion rupture
	ArrayList<ArrayList<Integer>> rupAssociationList;
	
	SummedMagFreqDist mfdOfAssocRupsAndModMags;		// this is the mfd of the associated ruptures (including ave mag from mult rups)
	SummedMagFreqDist mfdOfAssocRupsWithOrigMags;	// this is the mfd of the associated ruptures (including orig mag from all rups)
	SummedMagFreqDist mfdOfSummedUCERF2_Sources;
	SummedMagFreqDist mfdOfSubSeismoRups,mfdOfOtherUnassocInvsionRups;
	EvenlyDiscretizedFunc ucerf2_AandB_FaultCumMFD;
	
	/**
	 * 
	 * See important notes given above for this class.
	 * 
	 * Note that the file saved/read here in precomputedDataDir (with name defined by DATA_FILE_PREFIX
	 * plus the fault number and number of inversion sections and ruptures separated by "_") assumes that the number of
	 * inversion sections and ruptures uniquely defines a faultSysRupSet.
	 * 
	 * @param faultSysRupSet
	 * @param precomputedDataDir
	 * @param faultModel - this could alternatively should be obtained from the faultSysRupSet
	 */
	public FindEquivUCERF2_FM3_Ruptures(FaultSystemRupSet faultSysRupSet, File precomputedDataDir, FaultModels faultModel) {
		super(faultSysRupSet, precomputedDataDir, getUCERF2_FM(faultModel));
		
		this.faultModel = faultModel;
		
		if(D) {
			System.out.println("NUM_INVERSION_RUPTURES = " +NUM_INVERSION_RUPTURES);
			System.out.println("NUM_SECTIONS = " +NUM_SECTIONS);
		}
		
		// these are what we want to fill in here
		firstSectOfUCERF2_Rup = new int[ucerf2_fm.numRuptures];	// contains -1 if no association
		lastSectOfUCERF2_Rup = new int[ucerf2_fm.numRuptures];	// contains -1 if no association
		srcIndexOfUCERF2_Rup = new int[ucerf2_fm.numRuptures];
		rupIndexOfUCERF2_Rup = new int[ucerf2_fm.numRuptures];
		magOfUCERF2_Rup = new double[ucerf2_fm.numRuptures];
		lengthOfUCERF2_Rup = new double[ucerf2_fm.numRuptures];
		rateOfUCERF2_Rup = new double[ucerf2_fm.numRuptures];
		subSeismoUCERF2_Rup = new boolean[ucerf2_fm.numRuptures]; 
		invRupIndexForUCERF2_Rup = new int[ucerf2_fm.numRuptures];
		
		problemUCERF2_Source = new boolean[ucerf2_fm.sourcesToUse];

		if(faultModel == FaultModels.FM3_1)
			dataFile = new File(scratchDir, DATA_FILE_PREFIX+"_1_"+NUM_SECTIONS+"_"+NUM_INVERSION_RUPTURES);
		else
			dataFile = new File(scratchDir, DATA_FILE_PREFIX+"_2_"+NUM_SECTIONS+"_"+NUM_INVERSION_RUPTURES);

		
		// read from file if it exists
		if(dataFile.exists()) {
			if(D) System.out.println("Reading existing file: "+ dataFile.getAbsolutePath());
			try {
				// Wrap the FileInputStream with a DataInputStream
				FileInputStream file_input = new FileInputStream (dataFile);
				DataInputStream data_in    = new DataInputStream (file_input );
				int numInvSections = data_in.readInt();
				int numInvRups = data_in.readInt();
				if(NUM_SECTIONS != numInvSections)
					throw new RuntimeException("Error: Input file number of inversion sections ("+numInvSections+
							") is inconsistent with that from the given faultSysRupSet ("+faultSectionData.size()+
					"); there must be a filename problem");
				if(NUM_INVERSION_RUPTURES != numInvRups) {
					throw new RuntimeException("Error: Input file number of rupturess ("+numInvRups+
							") is inconsistent with that from the given faultSysRupSet ("+faultSysRupSet.getNumRuptures()+
					"); there must be a filename problem");
				}
				// now read the rest of the file
				for(int i=0; i<ucerf2_fm.numRuptures;i++) {
					firstSectOfUCERF2_Rup[i]=data_in.readInt();
					lastSectOfUCERF2_Rup[i]=data_in.readInt();
					srcIndexOfUCERF2_Rup[i]=data_in.readInt();
					rupIndexOfUCERF2_Rup[i]=data_in.readInt();
					magOfUCERF2_Rup[i]=data_in.readDouble();
					lengthOfUCERF2_Rup[i]=data_in.readDouble();
					rateOfUCERF2_Rup[i]=data_in.readDouble();
					Preconditions.checkState(Double.isFinite(magOfUCERF2_Rup[i]), "Bad U2 mag[%s]=%s", i, (Double)magOfUCERF2_Rup[i]);
					Preconditions.checkState(Double.isFinite(rateOfUCERF2_Rup[i]), "Bad U2 rate[%s]=%s", i, (Double)rateOfUCERF2_Rup[i]);
					subSeismoUCERF2_Rup[i]=data_in.readBoolean();
					invRupIndexForUCERF2_Rup[i]=data_in.readInt();
				}
				for(int s=0;s<ucerf2_fm.sourcesToUse;s++) {
					problemUCERF2_Source[s] = data_in.readBoolean();
				}

				data_in.close ();
				if(D) System.out.println("Done reading file:"+dataFile.getAbsolutePath());
			} catch  (IOException e) {
				System.out.println ( "IO Exception =: " + e );
			}

			// make the rupAssociationList from invRupIndexForUCERF2_Rup
			rupAssociationList = new ArrayList<ArrayList<Integer>>();
			for(int ir=0; ir<NUM_INVERSION_RUPTURES;ir++)
				rupAssociationList.add(new ArrayList<Integer>());  // add a list of UCERF2 ruptures associated with this inversion rupture (ir)
			for(int ur=0;ur<invRupIndexForUCERF2_Rup.length;ur++) // loop over all UCERF2 ruptures
				if(invRupIndexForUCERF2_Rup[ur] != -1)
					rupAssociationList.get(invRupIndexForUCERF2_Rup[ur]).add(ur);
		}
		else {	// create the files
			
			// check that the deformation model is legitimate
			// DeformationModelFetcher.DefModName defModName = 

			// this fills in parentSectionNamesForUCERF2_Sources
			readSectionNamesForUCERF2_SourcesFile();
			
			
			// find fault sections that are not used by UCERF2 (the new ones in UCERF3)
			ArrayList<String> allParentSectionNames = new ArrayList<String>();
			for(FaultSection data : faultSysRupSet.getFaultSectionDataList()){
				if(!allParentSectionNames.contains(data.getParentSectionName()))
					allParentSectionNames.add(data.getParentSectionName());
			}
			ArrayList<String> usedParentSectionNames = new ArrayList<String>();
			for(ArrayList<String> names:parentSectionNamesForUCERF2_Sources) {
				for(String name:names) {
					if(!usedParentSectionNames.contains(name))
						usedParentSectionNames.add(name);
				}
			}
			ArrayList<String> unusedParentSectionNames = new ArrayList<String>();
			for(String name:allParentSectionNames) {
				if(!usedParentSectionNames.contains(name))
					unusedParentSectionNames.add(name);
			}


			

			// do the following methods (which write to the info file)
			try {
				if(faultModel == FaultModels.FM3_1)
					info_fw = new FileWriter(new File(scratchDir, INFO_FILE_PATH_PREFIX+"_1_"+NUM_SECTIONS+"_"+NUM_INVERSION_RUPTURES+".txt"));
				else
					info_fw = new FileWriter(new File(scratchDir, INFO_FILE_PATH_PREFIX+"_2_"+NUM_SECTIONS+"_"+NUM_INVERSION_RUPTURES+".txt"));
			} catch (IOException e) {
//				e.printStackTrace();
				// it's ok to fail here actually
				info_fw = null;
			}
			findSectionEndsForUCERF2_Rups();
			findAssociations(faultSysRupSet.getSectionIndicesForAllRups());
			
			
			if (info_fw != null)
				try {
					// write out the new section and close
					info_fw.write("\nThese sections are not used by any UCERF2 source (they're new):\n\n");
					for(String name : unusedParentSectionNames)
						info_fw.write("\t"+name+"\n");
					info_fw.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

			writePreComputedDataFile();
		}	

		computeMagsAndRatesForAllRuptures();
	}
	
	/**
	 * This method computes the following data: 
	 * 
	 * 	firstSectOfUCERF2_Rup, 
	 * 	lastSectOfUCERF2_Rup, 
	 * 	srcIndexOfUCERF2_Rup, 
	 * 	rupIndexOfUCERF2_Rup, 
	 * 	magOfUCERF2_Rup, 
	 * 	lengthOfUCERF2_Rup,
	 * 	rateOfUCERF2_Rup,
	 *  subSeismoUCERF2_Rup,
	 * 
	 * This also saves an info file (INFO_FILE_NAME) that gives the status of associations for each 
	 * UCERF2 rupture (and the problem sources at the end, all of which are only due to sub-seismogenic
	 * ruptures).
	 */
	private void findSectionEndsForUCERF2_Rups() {
		
		// Note that we're considering all but non-CA B fault sources
		if(D) {
			System.out.println("Considering All but non-CA B fault sources");
			System.out.println("Num UCERF2 Sources to Consider = "+ucerf2_fm.sourcesToUse);
			if(D) System.out.println("Num UCERF2 Ruptues to Consider = "+ucerf2_fm.numRuptures);
		}
		
		// initialize the following to bogus indices (the default)
		for(int r=0;r<ucerf2_fm.numRuptures;r++) {
			firstSectOfUCERF2_Rup[r]=-1;
			lastSectOfUCERF2_Rup[r]=-1;
		}
		
		ArrayList<String> resultsString = new ArrayList<String>();
		ArrayList<String> problemSourceList = new ArrayList<String>();
		ArrayList<String> subseismoRateString = new ArrayList<String> ();
		problemUCERF2_Source = new boolean[ucerf2_fm.sourcesToUse];	// this will not include sources that have subseismo ruptures
		double duration = modifiedUCERF2.getTimeSpan().getDuration();
		int rupIndex = -1;
		for(int s=0; s<ucerf2_fm.sourcesToUse; s++){
			problemUCERF2_Source[s] = false;				// this will indicate that source has some problem
			boolean srcHasSubSeismogenicRups = false;	// this will check whether any ruptures are sub-seismogenic
			ProbEqkSource src = modifiedUCERF2.getSource(s);
			if (D) System.out.println("working on source "+src.getName()+" "+s+" of "+ucerf2_fm.sourcesToUse);
			double srcDDW = src.getSourceSurface().getAveWidth();
			double totMoRate=0, partMoRate=0;
			
			ArrayList<String> parentSectionNames = parentSectionNamesForUCERF2_Sources.get(s);

			for(int r=0; r<src.getNumRuptures(); r++){
				rupIndex += 1;
				ProbEqkRupture rup = src.getRupture(r);
				double ddw = rup.getRuptureSurface().getAveWidth();
				double len = rup.getRuptureSurface().getAveLength();
				double mag = ((int)(rup.getMag()*100.0))/100.0;	// nice value for writing
				totMoRate += MagUtils.magToMoment(rup.getMag())*rup.getMeanAnnualRate(duration);
				srcIndexOfUCERF2_Rup[rupIndex] = s;
				rupIndexOfUCERF2_Rup[rupIndex] = r;
				magOfUCERF2_Rup[rupIndex] = rup.getMag();
				lengthOfUCERF2_Rup[rupIndex] = len;
				rateOfUCERF2_Rup[rupIndex] = rup.getMeanAnnualRate(duration);
				Preconditions.checkState(Double.isFinite(magOfUCERF2_Rup[rupIndex]),
						"Bad U2 mag[%s]=%s\ts=%s, r=%s", rupIndex, (Double)magOfUCERF2_Rup[rupIndex], s, r);
				Preconditions.checkState(Double.isFinite(rateOfUCERF2_Rup[rupIndex]),
						"Bad U2 rate[%s]=%s\ts=%s, r=%s", rupIndex, (Double)rateOfUCERF2_Rup[rupIndex], s, r);
				
				subSeismoUCERF2_Rup[rupIndex] = false;  // the default

				FaultTrace rupTrace = rup.getRuptureSurface().getEvenlyDiscritizedUpperEdge();
				Location rupEndLoc1 = rupTrace.get(0);
				Location rupEndLoc2 = rupTrace.get(rupTrace.size()-1);	

				// find the appropriate sections for each rup endpoint
				int firstEndIndex = getCloseSection(rupEndLoc1, rupTrace, parentSectionNames);
				int secondEndIndex  = getCloseSection(rupEndLoc2, rupTrace, parentSectionNames);
				
				// check if it's sub-seismogenic (first and last sections are the same and not -1)
				if(firstEndIndex == secondEndIndex && firstEndIndex != -1){
					if(ddw != srcDDW)
						throw new RuntimeException("Problem");
					subSeismoUCERF2_Rup[rupIndex] = true;
					srcHasSubSeismogenicRups = true;
//					problemSource = true;
					String errorString = rupIndex+":\t"+"Sub-Seismogenic Rupture:  rup & src ddw="+(float)ddw+
										"\trupLen="+(float)len+"\tlength/ddw="+(float)(len/ddw)+"\tmag="+(float)mag+
					                     "\tiRup="+r+"\tiSrc="+s+"\t("+src.getName()+")"+
					                     "\tfirstEndIndex="+firstEndIndex+"\tsecondEndIndex="+secondEndIndex+
					                     "\t("+faultSectionData.get(secondEndIndex).getSectionName()+")\n";
					if(D) System.out.print(errorString);
					resultsString.add(errorString);
					partMoRate += MagUtils.magToMoment(rup.getMag())*rup.getMeanAnnualRate(duration);
					continue;
				}
				
				String sectName1 = "None Found"; // default
				String sectName2 = "None Found"; // default
				
				if(firstEndIndex != -1) {
					firstSectOfUCERF2_Rup[rupIndex]=firstEndIndex;
					sectName1=faultSectionData.get(firstEndIndex).getSectionName();
				}
				else {
					problemUCERF2_Source[s] = true;
					String errorString = "Error - end1 section not found for rup "+r+
										 " of src "+s+", M="+(float)rup.getMag()+", ("+src.getName()+")\n";
					if(D) System.out.print(errorString);
					resultsString.add(errorString);
				}

				if(secondEndIndex != -1) {
					lastSectOfUCERF2_Rup[rupIndex]=secondEndIndex;
					sectName2=faultSectionData.get(secondEndIndex).getSectionName();
				}
				else {
					problemUCERF2_Source[s] = true;
					String errorString = "Error - end2 section not found for rup "+r+
										 " of src "+s+", M="+(float)rup.getMag()+", ("+src.getName()+")\n";
					if(D) System.out.print(errorString);
					resultsString.add(errorString);
				}

				String result = rupIndex+":\t"+firstSectOfUCERF2_Rup[rupIndex]+"\t"+lastSectOfUCERF2_Rup[rupIndex]+"\t("+sectName1+"   &  "+
								sectName2+")  are the Sections at ends of rup "+ r+" of src "+s+", M="+(float)rup.getMag()+", ("+src.getName()+")\n";
				resultsString.add(result);
				
				if(problemUCERF2_Source[s] && !problemSourceList.contains(src.getName()))
					problemSourceList.add(src.getName());

			}
			String infoString = (float)(partMoRate/totMoRate) +"\tis the fract MoRate below for\t"+src.getName();
			if(srcHasSubSeismogenicRups) {
				if(D) System.out.println(infoString);
				subseismoRateString.add(infoString);
			}
		}
		
		// write info results
		try {
			if (info_fw != null) {
				for(String line:resultsString)
					info_fw.write(line);
				info_fw.write("\nProblem Sources (can't find associated inv section for one end of at least one rupture):\n\n");
				for(String line:problemSourceList)
					info_fw.write("\t"+line+"\n");
				info_fw.write("\nSubseimso Sources (has one or more subseismogenic ruptures):\n\n");
				for(String line:subseismoRateString)
					info_fw.write("\t"+line+"\n");
			}
		} catch (IOException e) {
			// it's ok to fail here actually
			e.printStackTrace();
		}
		
	}
	
	
	/**
	 * This saves the following data to a file with name set by DATA_FILE_NAME: 
	 * 
	 * 	firstSectOfUCERF2_Rup, 
	 * 	lastSectOfUCERF2_Rup, 
	 * 	srcIndexOfUCERF2_Rup, 
	 * 	rupIndexOfUCERF2_Rup, 
	 * 	magOfUCERF2_Rup, 
	 * 	lengthOfUCERF2_Rup,
	 * 	rateOfUCERF2_Rup,
	 *  subSeismoUCERF2_Rup,
	 *  invRupIndexForUCERF2_Rup
	 * 
	 */
	private void writePreComputedDataFile() {
		// write out the data
		File parentDir = dataFile.getParentFile();
		if (!parentDir.exists() && !parentDir.mkdir())
			return;
		try {
			FileOutputStream file_output = new FileOutputStream (dataFile);
			// Wrap the FileOutputStream with a DataOutputStream
			DataOutputStream data_out = new DataOutputStream (file_output);
			data_out.writeInt(NUM_SECTIONS);
			data_out.writeInt(NUM_INVERSION_RUPTURES);
			for(int i=0; i<ucerf2_fm.numRuptures;i++) {
				data_out.writeInt(firstSectOfUCERF2_Rup[i]);
				data_out.writeInt(lastSectOfUCERF2_Rup[i]);
				data_out.writeInt(srcIndexOfUCERF2_Rup[i]);
				data_out.writeInt(rupIndexOfUCERF2_Rup[i]);
				data_out.writeDouble(magOfUCERF2_Rup[i]);
				data_out.writeDouble(lengthOfUCERF2_Rup[i]);
				data_out.writeDouble(rateOfUCERF2_Rup[i]);
				data_out.writeBoolean(subSeismoUCERF2_Rup[i]);
				data_out.writeInt(invRupIndexForUCERF2_Rup[i]);
			}
			for(int s=0;s<ucerf2_fm.sourcesToUse;s++) {
				data_out.writeBoolean(problemUCERF2_Source[s]);
			}

			file_output.close ();
		}
		catch (IOException e) {
			System.out.println ("IO exception = " + e );
		}
	}
	
	
	/**
	 * This returns the magnitude and rate of the equivalent UCERF2 ruptures.  If more than one UCERF2 
	 * rupture are associated with the inversion rupture (as would come from the type-A segmented models 
	 * where there are diff mags for the same char rup), then the total rate of these is returned with a 
	 * magnitude that preserves the total moment rate.
	 * 
	 * @param invRupIndex - the index of the inversion rupture
	 * @return - double[2] where mag is in the first element and rate is in the second

	 */
	private double[] computeMagAndRateForRupture(int invRupIndex) {
		
		ArrayList<Integer> ucerf2_assocRups = rupAssociationList.get(invRupIndex);
		
		// return null if there are no associations
		if(ucerf2_assocRups.size()==0) {
			return null;
		}
		else if(ucerf2_assocRups.size()==1) {
			int r = ucerf2_assocRups.get(0);
			// this makes sure a UCERF2 rupture is not used more than once
			double[] result = new double[2];
			result[0]=magOfUCERF2_Rup[r];
			result[1]=rateOfUCERF2_Rup[r];
			mfdOfAssocRupsAndModMags.addResampledMagRate(magOfUCERF2_Rup[r], rateOfUCERF2_Rup[r], true);
			mfdOfAssocRupsWithOrigMags.addResampledMagRate(magOfUCERF2_Rup[r], rateOfUCERF2_Rup[r], true);
//System.out.println("\t\t"+result[0]+"\t"+result[1]);
			return result;
		}
		else {
			double totRate=0, totMoRate=0;
			for(Integer ur:ucerf2_assocRups) {
				totRate+=rateOfUCERF2_Rup[ur];
				totMoRate+=rateOfUCERF2_Rup[ur]*MagUtils.magToMoment(magOfUCERF2_Rup[ur]);
				mfdOfAssocRupsWithOrigMags.addResampledMagRate(magOfUCERF2_Rup[ur], rateOfUCERF2_Rup[ur], true);
			}
			double aveMoment = totMoRate/totRate;
			double mag = MagUtils.momentToMag(aveMoment);
			double[] result = new double[2];
			result[0]=mag;
			result[1]=totRate;
			mfdOfAssocRupsAndModMags.addResampledMagRate(mag, totRate, false);
			return result;
		}
	}


	
	
	
	
	/**
	 * This fills in invRupIndexForUCERF2_Rup (which inversion rupture each UCERF2 
	 * rupture is associated), with a value of -1 if there is no association.
	 * This also creates rupAssociationList.
	 * @param inversionRups
	 */
	private void findAssociations(List<? extends List<Integer>> inversionRups) {
		
		if(D) System.out.println("Starting associations");

		rupAssociationList = new ArrayList<ArrayList<Integer>>();
		
		// this will give the inversion rup index for each UCERF2 rupture (-1 if no association)
//		invRupIndexForUCERF2_Rup = new int[ucerf2_fm.numRuptures];
		for(int r=0;r<ucerf2_fm.numRuptures;r++)
			// initialize to -1 (no association)
			invRupIndexForUCERF2_Rup[r] = -1;
		
		// loop over inversion ruptures (ir)
		for(int ir=0; ir<inversionRups.size();ir++) {
			List<Integer> invRupSectIDs = inversionRups.get(ir);
			// make a list of parent section names for this inversion rupture
			ArrayList<String> parSectNamesList = new ArrayList<String>();
			for(Integer s : invRupSectIDs) {
				String parName = faultSectionData.get(s).getParentSectionName();	// could do IDs instead
				if(!parSectNamesList.contains(parName))
					parSectNamesList.add(parName);
			}
			// set the first and last section on the inversion rupture
			int invSectID_1 = invRupSectIDs.get(0);
			int invSectID_2 = invRupSectIDs.get(invRupSectIDs.size()-1);

			// now loop over all UCERF2 ruptures
			ArrayList<Integer> ucerfRupsIndexList = new ArrayList<Integer>();  // a list of UCERF2 ruptures associated with this inversion rupture (ir)
			for(int ur=0;ur<ucerf2_fm.numRuptures;ur++) {
				int ucerf2_SectID_1 = firstSectOfUCERF2_Rup[ur];
				int ucerf2_SectID_2 = lastSectOfUCERF2_Rup[ur];
				// check that section ends are the same (& check both ways)
				if((invSectID_1==ucerf2_SectID_1 && invSectID_2==ucerf2_SectID_2) || 
						(invSectID_1==ucerf2_SectID_2 && invSectID_2==ucerf2_SectID_1)) {
					boolean match = true;
					// make sure inv rup does not have parent sections that are not in the UCERF2 source (this filters our the multi-path ruptures)
					ArrayList<String> ucerf2_sectNames = parentSectionNamesForUCERF2_Sources.get(srcIndexOfUCERF2_Rup[ur]);
					for(String invParSectName:parSectNamesList) {
						if(!ucerf2_sectNames.contains(invParSectName))
							match = false;
					}
					if(match==true) {
						// check to make sure the UCERF2 rupture was not already associated
						if(invRupIndexForUCERF2_Rup[ur] != -1) {
							int chosen_index = findClosestInvRupToUCERF2_Rup(invRupIndexForUCERF2_Rup[ur], ir, ur);
							System.out.println("UCERF2 rupture "+ur+" was already associated with inv rup "+
									invRupIndexForUCERF2_Rup[ur]+"\t it can also assoc with: "+ir+"; "+chosen_index+" was chosen because it fit better");
							try {
								if (info_fw != null)
									info_fw.write("UCERF2 rupture "+ur+" was already associated with inv rup "+
										invRupIndexForUCERF2_Rup[ur]+"\t it can also assoc with: "+ir+"; "+chosen_index+" was chosen because it fit better\n");
							} catch (IOException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
//							throw new RuntimeException("UCERF2 rupture "+ur+" was already associated with inv rup "+
//									invRupIndexForUCERF2_Rup[ur]+"\t it can also assoc with: "+ir+"\nucerf2_sectNames: "+ucerf2_sectNames);
						}
						ucerfRupsIndexList.add(ur);
						invRupIndexForUCERF2_Rup[ur] = ir;
					}
				}
			}
			rupAssociationList.add(ucerfRupsIndexList);
		}
		
		// test results
		if(D) {
			ArrayList<ArrayList<Integer>> tempRupAssociationList = new ArrayList<ArrayList<Integer>>();
			for(ArrayList<Integer> list: rupAssociationList)
				tempRupAssociationList.add((ArrayList<Integer>)list.clone());
			for(int r=0;r<ucerf2_fm.numRuptures;r++) {
				int invRup = invRupIndexForUCERF2_Rup[r];
				if(invRup != -1)
					tempRupAssociationList.get(invRup).remove(Integer.valueOf(r));
			}
			for(ArrayList<Integer> list:tempRupAssociationList)
				if(list.size() != 0)
					throw new RuntimeException("List should be zero for!");
		}
		
		// write un-associated UCERF2 ruptures
		try {
			int numUnassociated=0;
			if (info_fw != null) {
				info_fw.write("\nUnassociated UCERF2 ruptures (not from other FM nor subseismogenic, so there should be a mapping?)\n");
				info_fw.write("\n(because these do not pass the laugh-test filter?)\n");
				info_fw.write("\n\tu2_rup\tsrcIndex\trupIndex\tsubSeis\tinvRupIndex\tsrcName\t(first-subsect-name\tlast-subsect-name\n");
				for(int r=0;r<ucerf2_fm.numRuptures;r++) {
					int srcIndex = srcIndexOfUCERF2_Rup[r];
					if(!subSeismoUCERF2_Rup[r] && (invRupIndexForUCERF2_Rup[r] == -1 && problemUCERF2_Source[srcIndex] == false)) { // first make sure it's not for fault model 2.2
						boolean isFirstOrLastSubsect = false;
						// this doesn't really test whether only one subsection of parent is used
						if(isFirstOrLastSubsectInSect(firstSectOfUCERF2_Rup[r]) || isFirstOrLastSubsectInSect(lastSectOfUCERF2_Rup[r]))
							isFirstOrLastSubsect = true;

						info_fw.write("\t"+r+"\t"+srcIndexOfUCERF2_Rup[r]+"\t"+rupIndexOfUCERF2_Rup[r]+
								"\t"+subSeismoUCERF2_Rup[r]+"\t"+invRupIndexForUCERF2_Rup[r]+"\t"+
								modifiedUCERF2.getSource(srcIndexOfUCERF2_Rup[r]).getName()+
								"\t("+faultSectionData.get(firstSectOfUCERF2_Rup[r]).getName()+
								"\t"+faultSectionData.get(lastSectOfUCERF2_Rup[r]).getName()+");  atLeastOneIsFirstOrLastSubsectInSect = "+isFirstOrLastSubsect+"\n");
						numUnassociated+=1;
					}
				}
				info_fw.write("\tTot Num of Above Problems = "+numUnassociated+" (of "+ucerf2_fm.numRuptures+")\n\n");
			}
		} catch (IOException e) {
			// it's ok to fail here actually
			e.printStackTrace();
		}

		if(D) System.out.println("Done with associations");
	}
	
	
	
	/**
	 * This computes which inversion rupture is closest to the given UCERF2 rupture
	 * @param iInvRup1 - index of first inversion rupture
	 * @param iInvRup2 - index of second inversion rupture
	 * @param ucerf2_iRup - index of UCERF rupture
	 * @return
	 */
	private int findClosestInvRupToUCERF2_Rup(int iInvRup1, int iInvRup2, int ucerf2_iRup) {
		FaultTrace targetTrace = modifiedUCERF2.getSource(srcIndexOfUCERF2_Rup[ucerf2_iRup]).getRupture(rupIndexOfUCERF2_Rup[ucerf2_iRup]).getRuptureSurface().getEvenlyDiscritizedUpperEdge();
		
		List<? extends FaultSection> sectData1 = faultSysRupSet.getFaultSectionDataForRupture(iInvRup1);
		double rms_dist1=0;
		for(FaultSection data:sectData1) {
			for(Location loc:data.getFaultTrace()) {
				double dist = targetTrace.minDistToLocation(loc);
				rms_dist1 += dist*dist;
			}
		}
		
		List<? extends FaultSection> sectData2 = faultSysRupSet.getFaultSectionDataForRupture(iInvRup2);
		double rms_dist2=0;
		for(FaultSection data:sectData2) {
			for(Location loc:data.getFaultTrace()) {
				double dist = targetTrace.minDistToLocation(loc);
				rms_dist2 += dist*dist;
			}
		}
		if(rms_dist1 <rms_dist2)
			return iInvRup1;
		else
			return iInvRup2;
	}
	
	
	/**
	 * This returns the UCERF2 mag and rate for the specified inversion rupture
	 * (or null if there was no association, which will usually be the case).
	 * 
	 * @param invRupIndex - the index of the inversion rupture
	 * @return - double[2] with mag in index 0 and rate in index 1.
	 */
	public ArrayList<double[]> getMagsAndRatesForRuptures() {
		return magsAndRatesForRuptures;
	}


	/**
	 * This returns the mag and rate for the specified inversion rupture 
	 * (or null if there is no corresponding UCERF2 rupture, which will usually be the case).
	 * The mag and rate are in the double[] object at index 0 and 1, respectively.
	 * 
	 * @return
	 */
	public double[] getMagsAndRatesForRupture(int invRupIndex) {
		return magsAndRatesForRuptures.get(invRupIndex);
	}
	
	
	/**
	 * This compute various other MFDs of interest
	 */
	private void computeOtherMFDs() {

		mfdOfSubSeismoRups = new SummedMagFreqDist(5.05,35,0.1);
		mfdOfSubSeismoRups.setName("MFD for Sub-seismogenic rups");
		mfdOfSubSeismoRups.setInfo("");

		mfdOfOtherUnassocInvsionRups = new SummedMagFreqDist(5.05,35,0.1);
		mfdOfOtherUnassocInvsionRups.setName("MFD for UCERF2 rups with no association");
		mfdOfOtherUnassocInvsionRups.setInfo("and not including sub-seismo or other fault model rups");
		
		for(int r=0; r<ucerf2_fm.numRuptures; r++) {
			double rate = magOfUCERF2_Rup[r];
			double mag = rateOfUCERF2_Rup[r];
			
			if(subSeismoUCERF2_Rup[r]) {
				mfdOfSubSeismoRups.addResampledMagRate(rate,mag,true);
			}
			if(!subSeismoUCERF2_Rup[r] && this.invRupIndexForUCERF2_Rup[r] == -1)
				mfdOfOtherUnassocInvsionRups.addResampledMagRate(rate,mag,true);
		}
		
		// make MFD of non-problematic UCERF2 sources
		mfdOfSummedUCERF2_Sources = new SummedMagFreqDist(5.05,35,0.1);
		mfdOfSummedUCERF2_Sources.setName("MFD summed from UCERF2 sources");
		mfdOfSummedUCERF2_Sources.setInfo("(only including non-problematic sources)");
		double duration = modifiedUCERF2.getTimeSpan().getDuration();
		for(int s=0;s<ucerf2_fm.sourcesToUse;s++) {
			if(!problemUCERF2_Source[s]) {
				ProbEqkSource src = modifiedUCERF2.getSource(s);
				mfdOfSummedUCERF2_Sources.addIncrementalMagFreqDist(ERF_Calculator.getTotalMFD_ForSource(src, duration, 5.05, 8.45, 35, true));
			}
		}
		
		// now make UCERF2 A & B fault Cum MFD (from Table 8 of the report)
		ucerf2_AandB_FaultCumMFD = new EvenlyDiscretizedFunc(5.0,35,0.1);
		ucerf2_AandB_FaultCumMFD.setName("MFD for A and B Faults");
		ucerf2_AandB_FaultCumMFD.setInfo("(from Table 8 of UCERF2 Report)");
		ucerf2_AandB_FaultCumMFD.setTolerance(0.0001);
		ucerf2_AandB_FaultCumMFD.set(5.0, 0.329465);
		ucerf2_AandB_FaultCumMFD.set(5.1, 0.329465);
		ucerf2_AandB_FaultCumMFD.set(5.2, 0.329465);
		ucerf2_AandB_FaultCumMFD.set(5.3, 0.329465);
		ucerf2_AandB_FaultCumMFD.set(5.4, 0.329072);
		ucerf2_AandB_FaultCumMFD.set(5.5, 0.327401);
		ucerf2_AandB_FaultCumMFD.set(5.6, 0.323692);
		ucerf2_AandB_FaultCumMFD.set(5.7, 0.317977);
		ucerf2_AandB_FaultCumMFD.set(5.8, 0.310212);
		ucerf2_AandB_FaultCumMFD.set(5.9, 0.300378);
		ucerf2_AandB_FaultCumMFD.set(6.0, 0.291053);
		ucerf2_AandB_FaultCumMFD.set(6.1, 0.27998);
		ucerf2_AandB_FaultCumMFD.set(6.2, 0.269713);
		ucerf2_AandB_FaultCumMFD.set(6.3, 0.260056);
		ucerf2_AandB_FaultCumMFD.set(6.4, 0.249241);
		ucerf2_AandB_FaultCumMFD.set(6.5, 0.235843);
		ucerf2_AandB_FaultCumMFD.set(6.6, 0.19227);
		ucerf2_AandB_FaultCumMFD.set(6.7, 0.157186);
		ucerf2_AandB_FaultCumMFD.set(6.8, 0.126887);
		ucerf2_AandB_FaultCumMFD.set(6.9, 0.100995);
		ucerf2_AandB_FaultCumMFD.set(7.0, 0.078341);
		ucerf2_AandB_FaultCumMFD.set(7.1, 0.059611);
		ucerf2_AandB_FaultCumMFD.set(7.2, 0.044868);
		ucerf2_AandB_FaultCumMFD.set(7.3, 0.033416);
		ucerf2_AandB_FaultCumMFD.set(7.4, 0.024701);
		ucerf2_AandB_FaultCumMFD.set(7.5, 0.018085);
		ucerf2_AandB_FaultCumMFD.set(7.6, 0.013207);
		ucerf2_AandB_FaultCumMFD.set(7.7, 0.009341);
		ucerf2_AandB_FaultCumMFD.set(7.8, 0.006075);
		ucerf2_AandB_FaultCumMFD.set(7.9, 0.003351);
		ucerf2_AandB_FaultCumMFD.set(8.0, 0.001473);
		ucerf2_AandB_FaultCumMFD.set(8.1, 0.00051);
		ucerf2_AandB_FaultCumMFD.set(8.2, 0.0001);
		ucerf2_AandB_FaultCumMFD.set(8.3, 0.000007);
		ucerf2_AandB_FaultCumMFD.set(8.4, 0.000001);

	}
	
	public void plotMFD_Test() {
		
		computeOtherMFDs();
		ArrayList funcs = new ArrayList();
		funcs.add(mfdOfAssocRupsAndModMags);
		funcs.add(mfdOfAssocRupsWithOrigMags);
		funcs.add(mfdOfSummedUCERF2_Sources);
		funcs.add(mfdOfSubSeismoRups);
		funcs.add(mfdOfOtherUnassocInvsionRups);
		
/*		// sum 
		SummedMagFreqDist sumMFD = new SummedMagFreqDist(5.05,35,0.1);
		sumMFD.addIncrementalMagFreqDist(mfdsForUCERF2AssocRups.get(0));
*/		
		GraphWindow graph = new GraphWindow(funcs, "Incremental Mag-Freq Dists"); 
		graph.setX_AxisLabel("Mag");
		graph.setY_AxisLabel("Rate");
		graph.setYLog(true);
		graph.setY_AxisRange(1e-6, 1.0);
		
		// plot cumulative dists
		ArrayList cumFuncs = new ArrayList();
		cumFuncs.add(mfdOfAssocRupsAndModMags.getCumRateDistWithOffset());
		cumFuncs.add(mfdOfAssocRupsWithOrigMags.getCumRateDistWithOffset());
		cumFuncs.add(mfdOfSummedUCERF2_Sources.getCumRateDistWithOffset());
		cumFuncs.add(ucerf2_AandB_FaultCumMFD);
		cumFuncs.add(mfdOfSubSeismoRups.getCumRateDistWithOffset());
		cumFuncs.add(mfdOfOtherUnassocInvsionRups.getCumRateDistWithOffset());
		GraphWindow graph2 = new GraphWindow(cumFuncs, "Cumulative Mag-Freq Dists"); 
		graph2.setX_AxisLabel("Mag");
		graph2.setY_AxisLabel("Rate");
		graph2.setYLog(true);
		graph2.setY_AxisRange(1e-6, 1.0);
		
		

	}
	
	public void plotGMT_MapRatio_Tests() throws IOException {

//		GMT_CA_Maps.plotParticipationRateMap(modifiedUCERF2, 0, 10, "Test Ratio", "FindEquivUCERF2_FM3_Ruptures Test Ratio", "ucerf2to3_MapingTest");

		GriddedRegion griddedRegion = GMT_CA_Maps.getDefaultGriddedRegion();

		// First do nucleation rates
		GriddedGeoDataSet erfNucleationRates = ERF_Calculator.getNucleationRatesInRegion(modifiedUCERF2, griddedRegion, 0, 10);
		GriddedGeoDataSet mappingNucleationRates = new GriddedGeoDataSet(griddedRegion, true);	// true makes X latitude
		double[] zVals = new double[mappingNucleationRates.size()];
		// loop over ucerf2 ruptures
		for(int ur=0; ur<rateOfUCERF2_Rup.length;ur++) {
			int ir = invRupIndexForUCERF2_Rup[ur];	// get inversion rupture index of the ucerf2 rup
			if(ir>=0) {
				ArrayList<RuptureSurface> surfaces = new ArrayList<>();
				for(FaultSection fltData: faultSysRupSet.getFaultSectionDataForRupture(ir)) {
					surfaces.add(fltData.getFaultSurface(1.0, false, true));
				}
				CompoundSurface compSurf = new CompoundSurface(surfaces);
				LocationList surfLocs = compSurf.getEvenlyDiscritizedListOfLocsOnSurface();
				double ptRate = rateOfUCERF2_Rup[ur]/surfLocs.size();
				for(Location loc: surfLocs) {
					int index = griddedRegion.indexForLocation(loc);
					//int index = xyzData.indexOf(loc);	// was too slow
					if(index >= 0) {
						//	xyzData.set(index, xyzData.get(index)+ptRate);	// was too slow
						zVals[index] += ptRate;
					}			  
				}
			}
		}
		for(int i=0;i<griddedRegion.getNodeCount();i++)
			mappingNucleationRates.set(i, zVals[i]);
		GMT_CA_Maps.plotRatioOfRateMaps(mappingNucleationRates, erfNucleationRates, 
				"Nucleation Rates Ratio", "FindEquivUCERF2_FM3_Ruptures Nucleation Rate Ratio Test", "ucerf2to"+faultModel+"_MapNuclRatioTest");

		
		// Now do participation rates
		GriddedGeoDataSet erfParticipationRates = ERF_Calculator.getParticipationRatesInRegion(modifiedUCERF2, griddedRegion, 0, 10);
		GriddedGeoDataSet mappingParticipationRates = new GriddedGeoDataSet(griddedRegion, true);	// true makes X latitude
		zVals = new double[mappingParticipationRates.size()];
		// loop over ucerf2 ruptures
		for(int ur=0; ur<rateOfUCERF2_Rup.length;ur++) {
			int ir = invRupIndexForUCERF2_Rup[ur];	// get inversion rupture index of the ucerf2 rup
			if(ir>=0) {
				ArrayList<RuptureSurface> surfaces = new ArrayList<>();
				for(FaultSection fltData: faultSysRupSet.getFaultSectionDataForRupture(ir)) {
					surfaces.add(fltData.getFaultSurface(1.0, false, true));
				}
				CompoundSurface compSurf = new CompoundSurface(surfaces);
				LocationList surfLocs = compSurf.getEvenlyDiscritizedListOfLocsOnSurface();
				HashSet<Integer> locIndices = new HashSet<Integer>();	// this will prevent duplicate entries
				for(Location loc: surfLocs) {
					int index = griddedRegion.indexForLocation(loc);
					if(index >= 0)
						locIndices.add(griddedRegion.indexForLocation(loc));
				}
				double qkRate = rateOfUCERF2_Rup[ur];
				for(Integer locIndex : locIndices) {
						  zVals[locIndex] += qkRate;
				}
			}
		}

		for(int i=0;i<griddedRegion.getNodeCount();i++)
			mappingParticipationRates.set(i, zVals[i]);
		GMT_CA_Maps.plotRatioOfRateMaps(mappingParticipationRates, erfParticipationRates, 
				"Participation Rates Ratio", "FindEquivUCERF2_FM3_Ruptures Participation Rate Ratio Test", "ucerf2to"+faultModel+"_MapPartRatioTest");

	}
	
		



	
	/**
	 * This generates the array list containing the UCERF2 equivalent mag and rate for each rupture 
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
	private void computeMagsAndRatesForAllRuptures() {

		if(D) System.out.println("Starting computeMagsAndRatesForRuptures");
		
		mfdOfAssocRupsAndModMags = new SummedMagFreqDist(5.05,35,0.1);
		mfdOfAssocRupsAndModMags.setName("MFD for UCERF2 associated ruptures");
		mfdOfAssocRupsAndModMags.setInfo("using modified (average) mags; this excludes sub-seimogenic rups");
		mfdOfAssocRupsWithOrigMags = new SummedMagFreqDist(5.05,35,0.1);
		mfdOfAssocRupsWithOrigMags.setName("MFD for UCERF2 associated ruptures");
		mfdOfAssocRupsWithOrigMags.setInfo("using original mags; this excludes sub-seimogenic rups");
		
		// now get the mags and rates of each
		magsAndRatesForRuptures = new ArrayList<double[]>();
		for(int r=0; r<NUM_INVERSION_RUPTURES; r++) 
			magsAndRatesForRuptures.add(computeMagAndRateForRupture(r));

// System.out.println(mfdOfAssocRupsAndModMags);
		if(D) {
			System.out.println("Rate & Moment Rate for mfdOfAssocRupsAndModMags: "+
					(float)mfdOfAssocRupsAndModMags.getTotalIncrRate()+",\t"+
					(float)mfdOfAssocRupsAndModMags.getTotalMomentRate());
			System.out.println("Rate & Moment Rate for mfdOfAssocRupsWithOrigMags: "+
					(float)mfdOfAssocRupsWithOrigMags.getTotalIncrRate()+",\t"+
					(float)mfdOfAssocRupsWithOrigMags.getTotalMomentRate());
		}
		
		if(D) System.out.println("Done with computeMagsAndRatesForRuptures");

	}
	
	
	/**
	 * This reads a file that contains the sections names used by each UCERF2 source put in 
	 * parentSectionNamesForUCERF2_Sources).
	 */
	private void readSectionNamesForUCERF2_SourcesFile() {
		
		parentSectionNamesForUCERF2_Sources = new ArrayList<ArrayList<String>>();
		int faultModelToIgnore;
		String sectsFileName;
		if(faultModel == FaultModels.FM3_1) {
			sectsFileName = SECT_FOR_UCERF2_SRC_FILE_PATH_NAME_1;
			faultModelToIgnore=2;
		}
		else {
			sectsFileName = SECT_FOR_UCERF2_SRC_FILE_PATH_NAME_2;
			faultModelToIgnore=1;
		}
		if(D) System.out.println("Reading file: "+sectsFileName);
	    try {
			BufferedReader reader = new BufferedReader(UCERF3_DataUtils.getReader(SUB_DIR_NAME, sectsFileName));
			int l=-1;
			int s = -1;	// source index for ModMeanUCERF2_FM2pt1
			String line;
			while ((line = reader.readLine()) != null) {
				l+=1;
	        	String[] st = StringUtils.split(line,"\t");
	        	int srcIndex = Integer.valueOf(st[0]);  // note that this is the index for  ModMeanUCERF2, not ModMeanUCERF2_FM2pt1
	        	if(srcIndex != l)
	        		throw new RuntimeException("problem with source index "+srcIndex+" ("+st[1]+")");
	        	String srcName = st[1]; //st.nextToken();
	        	int faultModelForSource = Integer.valueOf(st[2]);
	        	// Skip if this is for faultModelToIgnore
	        	if(faultModelForSource == faultModelToIgnore)
	        		continue; 
	        	s += 1;		// increment source index for modifiedUCERF2
	        	String targetSrcName = modifiedUCERF2.getSource(s).getName();
	        	if(!srcName.equals(targetSrcName))
	        		throw new RuntimeException("problem with source name:\t"+srcName+"\t"+targetSrcName);
	        	ArrayList<String> parentSectNamesList = new ArrayList<String>();
	        	for(int i=3;i<st.length;i++) {
	        		if(st[i].equals("REMOVED"))
	        			continue;	// emply list for removed sources
	        		else
	        			parentSectNamesList.add(st[i]);
	        	}
	        	
	        	parentSectionNamesForUCERF2_Sources.add(parentSectNamesList);

	        	// TEST:
	        	// reconstruct string
	        	String test = l+"\t"+srcName+"\t"+faultModelForSource;
	        	if(parentSectNamesList.size()==0)
	        		test += "\tREMOVED";
	        	else
	        		for(String name:parentSectNamesList)
	        			test += "\t"+name;
	        	//				System.out.println(test);
	        	if(!test.equals(line))
	        		throw new RuntimeException("problem with recreating file line:\n"+test+"\n"+line);

	        }
       } catch (Exception e) {
			ExceptionUtils.throwAsRuntimeException(e);
		}
       
		if(D) System.out.println("Done reading file");

       // the rest is testing
       if(D) {
    	   // Check to make sure contents of parentSectionNamesForUCERF2_Sources are consistent with parent section names
    	   System.out.println("Num SubSections = "+faultSectionData.size());
    	   ArrayList<String> parentSectionNames = new ArrayList<String>();
    	   for(int i=0; i<faultSectionData.size();i++) {
    		   if(!parentSectionNames.contains(faultSectionData.get(i).getParentSectionName()))
    			   parentSectionNames.add(faultSectionData.get(i).getParentSectionName());
    	   }

    	   ArrayList<String> leftoverSectionNames = (ArrayList<String>)parentSectionNames.clone();
    	   System.out.println("\nTesting parentSectionNamesForUCERF2_Sources (index,name):\n");
    	   for(int s=0;s<parentSectionNamesForUCERF2_Sources.size();s++) {
    		   ArrayList<String> parNames = parentSectionNamesForUCERF2_Sources.get(s);
    		   for(String name: parNames) {
    			   if(!parentSectionNames.contains(name)) {
    				   System.out.println("\t"+s+"\t"+name);
    			   }
    			   leftoverSectionNames.remove(name);
    		   }
    	   }

    	   System.out.println("\nUnassociated elements of parentSectionNames (no UCERF2 source):\n");
    	   System.out.println("\t"+leftoverSectionNames);

       }
	}
	
	
	
	/**
	 * This reads the SECT_FOR_UCERF2_SRC_FILE_PATH_NAME file and compiles a list of sections names used
	 * in the mapping here, including those for both fault models (3.1 and 3.2).  Fault sections that are
	 * new to FM 3.1 and 3.2 will not be in this list
	 */
	public static ArrayList<String> getAllSectionNames(FaultModels faultModel) {
		
		
		
		ArrayList<String> sectNames = new ArrayList<String>();
		String sectsFile;
		if(faultModel == FaultModels.FM3_1)
			sectsFile = SECT_FOR_UCERF2_SRC_FILE_PATH_NAME_1;
		else
			sectsFile = SECT_FOR_UCERF2_SRC_FILE_PATH_NAME_2;
//		if(D) System.out.println("Reading file: "+sectsFile.getPath());
	    try {
//			BufferedReader reader = new BufferedReader(new FileReader(sectsFile.getPath()));
	    	BufferedReader reader = new BufferedReader(UCERF3_DataUtils.getReader(SUB_DIR_NAME, sectsFile));
			int l=-1;
			int s = -1;	// source index for ModMeanUCERF2_FM2pt1
			String line;
			while ((line = reader.readLine()) != null) {
				l+=1;
	        	String[] st = StringUtils.split(line,"\t");
	        	int srcIndex = Integer.valueOf(st[0]);  // note that this is the index for  ModMeanUCERF2, not ModMeanUCERF2_FM2pt1
	        	if(srcIndex != l)
	        		throw new RuntimeException("problem with source index");
	        	String srcName = st[1]; //st.nextToken();
	        	int faultModelForSource = Integer.valueOf(st[2]);
	        	for(int i=3;i<st.length;i++) {
	        		if(!sectNames.contains(st[i])) {
	        			sectNames.add(st[i]);
	        		}
	        	}
	        }
       } catch (Exception e) {
			ExceptionUtils.throwAsRuntimeException(e);
		}
       
		if(D) System.out.println("Done reading file");
		return sectNames;
	}	
	
	
	
	
	/**
	 * This finds the end subsection for the given end of a rupture.  This first finds the two closest subsections (to any
	 * point on each subsection).  The closest of the two is assigned if both ends are within half the section length to
	 * any point on the rupture trace.  If not, the second closest is assigned if it passes this test.  If neither pass 
	 * this test (which can happen where there are gaps between faults), we assign whichever section's farthest end point
	 * (from the rupture trace) is closest.
	 * @param rupEndLoc
	 * @param rupTrace
	 * @param parentSectionNames
	 * @return
	 */
	private int getCloseSection(Location rupEndLoc, FaultTrace rupTrace, ArrayList<String> parentSectionNames) {

		int targetSection=-1;
		double dist;
	
		boolean debug = false;
//		if(parentSectionNames.size()>0 && parentSectionNames.get(0).equals("Burnt Mtn")){
//			debug = true;
//			System.out.println("DEBUG STUFF:");
//			System.out.println("rupEndLoc: "+rupEndLoc);
//			System.out.println("\nrupTrace:\n"+rupTrace);
//		}

		double closestDist=Double.MAX_VALUE, secondClosestDist=Double.MAX_VALUE;
		int clostestSect=-1, secondClosestSect=-1;
		for(int i=0; i<faultSectionData.size(); i++) {

			FaultSection sectionData = faultSectionData.get(i);
			
			if(!parentSectionNames.contains(sectionData.getParentSectionName()))
				continue;

			// TODO: should aseis be false instead of true?
			FaultTrace sectionTrace = sectionData.getFaultSurface(1.0, false, true).getEvenlyDiscritizedUpperEdge();
			dist = sectionTrace.minDistToLocation(rupEndLoc);
			if(dist<closestDist) {
				secondClosestDist=closestDist;
				secondClosestSect=clostestSect;
				closestDist=dist;
				clostestSect = i;
			}
			else if(dist<secondClosestDist) {
				secondClosestDist=dist;
				secondClosestSect=i;
			}
		}	
		
		if(debug) {
			System.out.println("closestDist="+(float)closestDist+"\tclostestSect="+clostestSect+
					"\tclostestSectName="+faultSectionData.get(clostestSect).getName());
			System.out.println("secondClosestDist="+(float)secondClosestDist+"\tsecondClosestSect="+secondClosestSect+
					"\tsecondClosestSectName="+faultSectionData.get(secondClosestSect).getName());
			FaultSection closestData = faultSectionData.get(clostestSect);
			System.out.println("Closest section trace:\n"+closestData.getFaultSurface(1.0, false, true).getEvenlyDiscritizedUpperEdge());
			System.out.println("Closest section trace:\n"+closestData.getFaultSurface(1.0, false, true).toString());
			System.out.println(closestData.toString());
		}
		
		// return -1 if nothing found
		if(clostestSect == -1)
			return targetSection;
			
		// now see if both ends of closest section are within half the section length
		// TODO: should aseis be false instead of true?
		FaultTrace sectionTrace = faultSectionData.get(clostestSect).getFaultSurface(1.0, false, true).getEvenlyDiscritizedUpperEdge();
		double sectHalfLength = 0.5*sectionTrace.getTraceLength()+0.5; 
		Location sectEnd1 = sectionTrace.get(0);
		Location sectEnd2 = sectionTrace.get(sectionTrace.size()-1);
		double dist1 =rupTrace.minDistToLocation(sectEnd1);
		double dist2 =rupTrace.minDistToLocation(sectEnd2);
		double maxDistClosest = Math.max(dist1, dist2);
		if(dist1 <sectHalfLength && dist2 <sectHalfLength)
			targetSection = clostestSect;
		
		if(debug) {
			System.out.println("dist of clostestSect end 1 to rup trace: dist1="+(float)dist1);
			System.out.println("dist of clostestSect end 2 to rup trace: dist2="+(float)dist2);
			System.out.println("clostestSect sectHalfLength="+(float)sectHalfLength+"\ttargetSection="+targetSection);
		}
		
		
		// check the second closest if the above failed
		double maxDistSecondClosest=Double.NaN;
		if(targetSection == -1) {	// check the second closest if the above failed
			// TODO: should aseis be false instead of true?
			sectionTrace = faultSectionData.get(secondClosestSect).getFaultSurface(1.0, false, true).getEvenlyDiscritizedUpperEdge();
			sectHalfLength = 0.5*sectionTrace.getTraceLength()+0.5; 
			sectEnd1 = sectionTrace.get(0);
			sectEnd2 = sectionTrace.get(sectionTrace.size()-1);
			dist1 =rupTrace.minDistToLocation(sectEnd1);
			dist2 =rupTrace.minDistToLocation(sectEnd2);
			maxDistSecondClosest = Math.max(dist1, dist2);
			if(dist1 <sectHalfLength && dist2 <sectHalfLength)
				targetSection = secondClosestSect;
		}
		
		if(debug) {
			System.out.println("dist of secondClosestSect end 1 to rup trace: dist1="+(float)dist1);
			System.out.println("dist of secondClosestSect end 2 to rup trace: dist2="+(float)dist2);
			System.out.println("secondClosestSect sectHalfLength="+(float)sectHalfLength+"\ttargetSection="+targetSection);
		}

		
		if(targetSection == -1) {
			if(maxDistClosest<maxDistSecondClosest)
				targetSection = clostestSect;
			else
				targetSection = secondClosestSect;
		}
		
		if(debug) {
			System.out.println("maxDistClosest="+(float)maxDistClosest);
			System.out.println("maxDistSecondClosest="+(float)maxDistSecondClosest);
			System.out.println("\ttargetSection="+targetSection);
		}

		
if(debug) System.exit(0);


		// Can't get here?
		if(targetSection == -1) {
			System.out.println("clostestSect\t"+faultSectionData.get(clostestSect).getName()+"\tclosestDist="+closestDist);
			System.out.println("secondClosestSect\t"+faultSectionData.get(secondClosestSect).getName()+"\tsecondClosestDist="+secondClosestDist);
		}

		
		return targetSection;
	}
	
	/**
	 * This gives the ProbEqkRupture for the rth UCERF2 rupture (where the index is
	 * relative to the total number given by getNumUCERF2_Ruptures())
	 * @param r
	 * @return
	 */
	public ProbEqkRupture getRthUCERF2_Rupture(int r) {
		return modifiedUCERF2.getSource(srcIndexOfUCERF2_Rup[r]).getRupture(rupIndexOfUCERF2_Rup[r]);
	}
	
	
	/**
	 * This gives the faultSystemRupSet index for the rupture that is equivalent to the 
	 * specified UCERF2 rupture.  The returned value is -1 if there is no equivalent.
	 * @param r
	 * @return
	 */
	public int getEquivFaultSystemRupIndexForUCERF2_Rupture(int r) {
		return invRupIndexForUCERF2_Rup[r];
	}
	

	/**
	 * @param args
	 * @throws IOException 
	 * @throws DocumentException 
	 */
	public static void main(String[] args) throws IOException, DocumentException {
		// TODO Auto-generated method stub
		
		System.out.println("STARTING");

		File precompDataDir = UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR;

		System.out.println("Getting rup set");
		
   		FaultSystemRupSet faultSysRupSet = InversionFaultSystemRupSetFactory.forBranch(
   				FaultModels.FM3_1,DeformationModels.GEOLOGIC,InversionModels.CHAR_CONSTRAINED);
		FindEquivUCERF2_FM3_Ruptures test = new FindEquivUCERF2_FM3_Ruptures(faultSysRupSet, precompDataDir, FaultModels.FM3_1);
		
		System.out.println("Done getting rup set");
		
//		test.plotMFD_Test();
		
		test.plotGMT_MapRatio_Tests();

		
	}

}
