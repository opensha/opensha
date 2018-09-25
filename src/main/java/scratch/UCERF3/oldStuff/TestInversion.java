package scratch.UCERF3.oldStuff;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.inversion.InversionFaultSystemRupSetFactory;
import scratch.UCERF3.utils.DeformationModelFetcher;
import scratch.UCERF3.utils.FaultSectionDataWriter;

import org.opensha.commons.calc.magScalingRelations.MagAreaRelationship;
import org.opensha.commons.calc.magScalingRelations.magScalingRelImpl.Ellsworth_B_WG02_MagAreaRel;
import org.opensha.commons.calc.magScalingRelations.magScalingRelImpl.HanksBakun2002_MagAreaRel;
import org.opensha.commons.data.NamedComparator;
import org.opensha.commons.data.NamedComparator;
import org.opensha.commons.data.region.CaliforniaRegions;
import org.opensha.commons.geo.BorderType;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.Region;
import org.opensha.commons.util.IDPairing;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.data.finalReferenceFaultParamDb.DeformationModelPrefDataFinal;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.StirlingGriddedSurface;

@Deprecated
public class TestInversion {

	protected final static boolean D = true;  // for debugging

	ArrayList<FaultSectionPrefData> subSectionPrefDataList;
	int numSubSections; int numRuptures;
	double subSectionDistances[][],subSectionAzimuths[][];
	double maxSubSectionLength;
	boolean includeSectionsWithNaN_slipRates;
	int minNumSectInRup;
	double[] rupMeanMag;
	double[] segSlipRate;
	double moRateReduction;
	MagAreaRelationship magAreaRel;
	
	String subsectsNameForFile;
	
	RupsInFaultSystemInversion rupsInFaultSysInv;

	int deformationModelId;
	
	private File precomputedDataDir;
	
	
	public TestInversion() {
		this(new File("dev/scratch/UCERF3/preComputedData/"));
	}
	
	
	public TestInversion(File precomputedDataDir) {
		this.precomputedDataDir = precomputedDataDir;
		
		maxSubSectionLength = 0.5;  // in units of seismogenic thickness
		double maxJumpDist = 5.0;
		double maxAzimuthChange = 45;
		double maxTotAzimuthChange = 90;
		double maxRakeDiff = 90;
		minNumSectInRup = 2;
		moRateReduction = 0.1;
		includeSectionsWithNaN_slipRates = false;	// other things assume this is false!
		ArrayList<MagAreaRelationship> magAreaRelList = new ArrayList<MagAreaRelationship>();
		magAreaRelList.add(new Ellsworth_B_WG02_MagAreaRel());
		magAreaRelList.add(new HanksBakun2002_MagAreaRel());
		
		double relativeSegRateWt = 0.01;  // weight of paleo-rate constraint relative to slip-rate constraint (recommended: 0.01)
		double relativeMagDistWt = 10.0;  // weight of UCERF2 magnitude-distribution constraint relative to slip-rate constraint - WORKS ONLY FOR NORTHERN CALIFORNIA INVERSION (recommended: 10.0)
		double relativeRupRateConstraintWt = 0.1;  // weight of rupture rate constraint (recommended strong weight: 5.0, weak weight: 0.1) - can be UCERF2 rates or Smooth G-R rates
		int numIterations = 10000;
		
		// slip model:
		String slipModelType = RupsInFaultSystemInversion.TAPERED_SLIP_MODEL;

		
		/** Set the deformation model (this only works with UCERF2 deformation models)
		 * D2.1 = 82
		 * D2.2 = 83
		 * D2.3 = 84
		 * D2.4 = 85
		 * D2.5 = 86
		 * D2.6 = 87
		 */
//		deformationModelId = 82;	
		
		// Create subSectionPrefDataList
		if(D) System.out.println("Making subsections...");
//		createAllSubSections();
//		createBayAreaSubSections(); 
//		createNorthCalSubSections();
		
		// create (or read) subSectionDistances[i][j]
//		calcSubSectionDistances();
		
		// create (or read) subSectionAzimuths[i][j]
//		calcSubSectionAzimuths();
		
		DeformationModelFetcher deformationModelFetcher =
				new DeformationModelFetcher(FaultModels.FM2_1, DeformationModels.UCERF2_NCAL,precomputedDataDir, InversionFaultSystemRupSetFactory.DEFAULT_ASEIS_VALUE);
		subSectionPrefDataList = deformationModelFetcher.getSubSectionList();
		numSubSections = subSectionPrefDataList.size();
		Map<IDPairing, Double> dists = deformationModelFetcher.getSubSectionDistanceMap(maxJumpDist);
		subSectionDistances=mapToArrays(dists);
		subSectionAzimuths=mapToArrays(deformationModelFetcher.getSubSectionAzimuthMap(dists.keySet()));
		
		
		
		// Instantiate rupsInFaultSysInv
		rupsInFaultSysInv = new RupsInFaultSystemInversion(subSectionPrefDataList,
				subSectionDistances, subSectionAzimuths, maxJumpDist, 
				maxAzimuthChange, maxTotAzimuthChange, maxRakeDiff, minNumSectInRup, 
				magAreaRelList, precomputedDataDir, moRateReduction,
				relativeSegRateWt,relativeMagDistWt,relativeRupRateConstraintWt,numIterations,
				slipModelType);
		
//		rupsInFaultSysInv.writeCloseSubSections(precomputedDataDir.getAbsolutePath()+File.separator+"closeSubSections.txt");
		
	}
	
	private double[][] mapToArrays(Map<IDPairing, Double> map) {
		int num = subSectionPrefDataList.size();
		double[][] array = new double[num][num];
		for (int i=0; i<num; i++)
			for (int j=0; j<num; j++)
				array[i][j] = Double.MAX_VALUE;
		for (IDPairing ind : map.keySet()) {
			int i = ind.getID1();
			int j = ind.getID2();
			double val = map.get(ind);
			array[i][j] = val;
			array[j][i] = val;
		}
		return array;
	}
	
	/**
	 * This is needed by SCEC VDO
	 * @return
	 */
	public RupsInFaultSystemInversion getRupsInFaultSystemInversion() {
		return rupsInFaultSysInv;
	}
	
	
	/**
	 * This gets all section data in the N. Cal RELM region.
	 * Note that this has to use a modified version of CaliforniaRegions.RELM_NOCAL() in 
	 * order to not include the Parkfield section (one that uses BorderType.GREAT_CIRCLE 
	 * rather than the default BorderType.MERCATOR_LINEAR).
	 */
	private void createNorthCalSubSections() {

		if(includeSectionsWithNaN_slipRates)
			subsectsNameForFile = "nCal_1_";
		else
			subsectsNameForFile = "nCal_0_";

		// fetch the sections
		DeformationModelPrefDataFinal deformationModelPrefDB = new DeformationModelPrefDataFinal();
		ArrayList<FaultSectionPrefData> allFaultSectionPrefData = deformationModelPrefDB.getAllFaultSectionPrefData(deformationModelId);

		//Alphabetize:
		Collections.sort(allFaultSectionPrefData, new NamedComparator());
		
		// remove those with no slip rate if appropriate
		if(!includeSectionsWithNaN_slipRates) {
			if (D)System.out.println("Removing the following due to NaN slip rate:");
			for(int i=allFaultSectionPrefData.size()-1; i>=0;i--)
				if(Double.isNaN(allFaultSectionPrefData.get(i).getOrigAveSlipRate())) {
					if(D) System.out.println("\t"+allFaultSectionPrefData.get(i).getSectionName());
					allFaultSectionPrefData.remove(i);
				}	 
		}

		// remove those that don't have a trace in in the N Cal RELM region
		Region relm_nocal_reg = new CaliforniaRegions.RELM_NOCAL();
		Region mod_relm_nocal_reg = new Region(relm_nocal_reg.getBorder(), BorderType.GREAT_CIRCLE); // needed to exclude Parkfield
		ArrayList<FaultSectionPrefData> nCalFaultSectionPrefData = new ArrayList<FaultSectionPrefData>();
		for(FaultSectionPrefData sectData:allFaultSectionPrefData) {
			FaultTrace trace = sectData.getFaultTrace();
			Location endLoc1 = trace.get(0);
			Location endLoc2 = trace.get(trace.size()-1);
			if(mod_relm_nocal_reg.contains(endLoc1) || mod_relm_nocal_reg.contains(endLoc2))
				nCalFaultSectionPrefData.add(sectData);
		}
		
		
		// REMOVE CREEPING SECTION for now (aseismicity not incorporated correctly)
		if (D)System.out.println("Removing SAF Creeping Section.");
		for(int i=0; i< nCalFaultSectionPrefData.size();i++) {
			if (nCalFaultSectionPrefData.get(i).getSectionId() == 57)
				nCalFaultSectionPrefData.remove(i);
		}
		
		
		/*	*/	  
		  // write sections IDs and names
		if (D) {
			System.out.println("Fault Sections in the N Cal RELM region");
			  for(int i=0; i< nCalFaultSectionPrefData.size();i++)
					System.out.println("\t"+nCalFaultSectionPrefData.get(i).getSectionId()+"\t"+nCalFaultSectionPrefData.get(i).getName());			
		}


		// make subsection data
		subSectionPrefDataList = new ArrayList<FaultSectionPrefData>();
		int subSectionIndex=0;
		for(int i=0; i<nCalFaultSectionPrefData.size(); ++i) {
			FaultSectionPrefData faultSectionPrefData = (FaultSectionPrefData)nCalFaultSectionPrefData.get(i);
			double maxSectLength = faultSectionPrefData.getOrigDownDipWidth()*maxSubSectionLength;
			ArrayList<FaultSectionPrefData> subSectData = faultSectionPrefData.getSubSectionsList(maxSectLength, subSectionIndex);
			subSectionIndex += subSectData.size();
			subSectionPrefDataList.addAll(subSectData);
		}		
		
		numSubSections = subSectionPrefDataList.size();
		if(D) System.out.println("numSubsections = "+numSubSections);
		
		/*
		if (D) {
			System.out.println("\n\nPrinting Section Fault Trace Information:");
			System.out.println("Section Index / Lat1 / Lon1 / Lat2 / Lat 2");
			for (int sect=0; sect<numSubSections; sect++) {
				FaultTrace faultTrace = subSectionPrefDataList.get(sect).getFaultTrace();
				int numTracePts = faultTrace.getNumLocations();
				Location loc1=faultTrace.get(0);
				Location loc2=faultTrace.get(numTracePts-1);
				System.out.println(sect+"\t"+loc1.getLatitude()+"\t"+loc1.getLongitude()+"\t"+loc2.getLatitude()+"\t"+loc2.getLongitude());
			}
			System.out.println("\n\n");
		}
		*/
		
		
	}



	/**
	 * This gets all section data & creates subsections
	 * @param includeSectionsWithNaN_slipRates
	 */
	private void createAllSubSections() {

		if(includeSectionsWithNaN_slipRates)
			subsectsNameForFile = "all_1_";
		else
			subsectsNameForFile = "all_0_";

		// fetch the sections
		DeformationModelPrefDataFinal deformationModelPrefDB = new DeformationModelPrefDataFinal();
		ArrayList<FaultSectionPrefData> allFaultSectionPrefData = deformationModelPrefDB.getAllFaultSectionPrefData(deformationModelId);

		//Alphabetize:
		Collections.sort(allFaultSectionPrefData, new NamedComparator());

		/*	*/	  
		  // write sections IDs and names
		  for(int i=0; i< allFaultSectionPrefData.size();i++)
				System.out.println(allFaultSectionPrefData.get(i).getSectionId()+"\t"+allFaultSectionPrefData.get(i).getName());
		 

		// remove those with no slip rate if appropriate
		if(!includeSectionsWithNaN_slipRates) {
			if (D)System.out.println("Removing the following due to NaN slip rate:");
			for(int i=allFaultSectionPrefData.size()-1; i>=0;i--)
				if(Double.isNaN(allFaultSectionPrefData.get(i).getOrigAveSlipRate())) {
					if(D) System.out.println("\t"+allFaultSectionPrefData.get(i).getSectionName());
					allFaultSectionPrefData.remove(i);
				}	 
		}

		// make subsection data
		subSectionPrefDataList = new ArrayList<FaultSectionPrefData>();
		int subSectionIndex=0;
		for(int i=0; i<allFaultSectionPrefData.size(); ++i) {
			FaultSectionPrefData faultSectionPrefData = (FaultSectionPrefData)allFaultSectionPrefData.get(i);
			double maxSectLength = faultSectionPrefData.getOrigDownDipWidth()*maxSubSectionLength;
			ArrayList<FaultSectionPrefData> subSectData = faultSectionPrefData.getSubSectionsList(maxSectLength, subSectionIndex);
			subSectionIndex += subSectData.size();
			subSectionPrefDataList.addAll(subSectData);
		}		
		
		numSubSections = subSectionPrefDataList.size();
		if(D) System.out.println("numSubsections = "+numSubSections);
	}
	
	
	/**
	 * This gets all section data & creates subsections
	 * @param includeSectionsWithNaN_slipRates
	 */
	private void createBayAreaSubSections() {

		if(includeSectionsWithNaN_slipRates)
			subsectsNameForFile = "bayArea_1_";
		else
			subsectsNameForFile = "bayArea_0_";

		DeformationModelPrefDataFinal deformationModelPrefDB = new DeformationModelPrefDataFinal();	
		
		ArrayList<Integer> faultSectionIds = new ArrayList<Integer>();
		// Bay Area Faults
		faultSectionIds.add(26); //  San Andreas (Offshore)
		faultSectionIds.add(27); //  San Andreas (North Coast)
		faultSectionIds.add(67); //  San Andreas (Peninsula)
		faultSectionIds.add(56); //  San Andreas (Santa Cruz Mtn) 
		faultSectionIds.add(25); //  Rodgers Creek
		faultSectionIds.add(68); //  Hayward (No)
		faultSectionIds.add(69); //  Hayward (So)
		faultSectionIds.add(4);  //  Calaveras (No)
		faultSectionIds.add(5);  //  Calaveras (Central)
		faultSectionIds.add(55); //  Calaveras (So)
		faultSectionIds.add(71); //  Green Valley (No)
		faultSectionIds.add(1);  //  Green Valley (So)
		faultSectionIds.add(3);  //  Concord
		faultSectionIds.add(12); //  San Gregorio (No)
		faultSectionIds.add(29); //  San Gregorio (So)
		faultSectionIds.add(6);  //  Greenville (No)
		faultSectionIds.add(7);  //  Greenville (So)
		faultSectionIds.add(2);  //  Mount Diablo Thrust
		
/*		// Other important faults to add for scalability testing
		faultSectionIds.add(287);	//San Andreas (Big Bend)
		faultSectionIds.add(300);	//San Andreas (Carrizo) rev
		faultSectionIds.add(285);	//San Andreas (Cholame) rev
		faultSectionIds.add(295);	//San Andreas (Coachella) rev
		faultSectionIds.add(57);	//San Andreas (Creeping Segment)
		faultSectionIds.add(286);	//San Andreas (Mojave N)
		faultSectionIds.add(301);	//San Andreas (Mojave S)
		faultSectionIds.add(32);	//San Andreas (Parkfield)
		faultSectionIds.add(282);	//San Andreas (San Bernardino N)
		faultSectionIds.add(283);	//San Andreas (San Bernardino S)
		faultSectionIds.add(284);	//San Andreas (San Gorgonio Pass-Garnet HIll)
		faultSectionIds.add(56);	//San Andreas (Santa Cruz Mtn)
		faultSectionIds.add(341);	//Garlock (Central)
		faultSectionIds.add(48);	//Garlock (East)
		faultSectionIds.add(49);	//Garlock (West)
		faultSectionIds.add(293);	//San Jacinto (Anza) rev
		faultSectionIds.add(291);	//San Jacinto (Anza, stepover)
		faultSectionIds.add(99);	//San Jacinto (Borrego)
		faultSectionIds.add(292);	//San Jacinto (Clark) rev
		faultSectionIds.add(101);	//San Jacinto (Coyote Creek)
		faultSectionIds.add(119);	//San Jacinto (San Bernardino)
		faultSectionIds.add(289);	//San Jacinto (San Jacinto Valley) rev
		faultSectionIds.add(290);	//San Jacinto (San Jacinto Valley, stepover)
		faultSectionIds.add(28);	//San Jacinto (Superstition Mtn)
*/		
		
		subSectionPrefDataList = new ArrayList<FaultSectionPrefData>();
		int subSectIndex = 0;
		for (int i = 0; i < faultSectionIds.size(); ++i) {
			FaultSectionPrefData faultSectionPrefData = deformationModelPrefDB
					.getFaultSectionPrefData(deformationModelId, faultSectionIds.get(i));
			double maxSectLength = faultSectionPrefData.getOrigDownDipWidth()*maxSubSectionLength;
			ArrayList<FaultSectionPrefData> subSectData = faultSectionPrefData.getSubSectionsList(maxSectLength, subSectIndex);
			subSectIndex += subSectData.size();
			subSectionPrefDataList.addAll(subSectData);
		}
		
		numSubSections = subSectionPrefDataList.size();
		if(D) System.out.println("numSubsections = "+numSubSections);
	}

	
	

	/**
	 * This computes the distances between subsection if it hasn't already been done.
	 * Otherwise the values are read from a file.
	 */
	private void calcSubSectionDistances() {

		subSectionDistances = new double[numSubSections][numSubSections];

		// construct filename
		String name = subsectsNameForFile+deformationModelId+"_"+(int)(maxSubSectionLength*1000)+"_Distances";
		String fullpathname = precomputedDataDir.getAbsolutePath()+File.separator+name;
		File file = new File (fullpathname);

		// Read data if already computed and saved
		if(file.exists()) {
			if(D) System.out.println("Reading existing file: "+ name);
			try {
				// Wrap the FileInputStream with a DataInputStream
				FileInputStream file_input = new FileInputStream (file);
				DataInputStream data_in    = new DataInputStream (file_input );
				for(int i=0; i<numSubSections;i++)
					for(int j=i; j<numSubSections;j++) {
						subSectionDistances[i][j] = data_in.readDouble();
						subSectionDistances[j][i] = subSectionDistances[i][j];
					}
				data_in.close ();
			} catch  (IOException e) {
				System.out.println ( "IO Exception =: " + e );
			}

		}
		else {// Calculate new distance matrix & save to a file
			System.out.println("Calculating data and will save to file: "+name);

			int progress = 0, progressInterval=10;  // for progress report
			System.out.print("Dist Calc % Done:");
			for(int a=0;a<numSubSections;a++) {
				if (100*a/numSubSections > progress) {
					System.out.print("\t"+progress);
					progress += progressInterval;
				}
//				StirlingGriddedSurface surf1 = new StirlingGriddedSurface(subSectionPrefDataList.get(a).getSimpleFaultData(false), 2.0);
				StirlingGriddedSurface surf1 = new StirlingGriddedSurface(subSectionPrefDataList.get(a).getSimpleFaultDataOld(false), 1.0, 1.0);

				for(int b=a+1;b<numSubSections;b++) { // a+1 because array is initialized to zero
//					StirlingGriddedSurface surf2 = new StirlingGriddedSurface(subSectionPrefDataList.get(b).getSimpleFaultData(false), 2.0);
					StirlingGriddedSurface surf2 = new StirlingGriddedSurface(subSectionPrefDataList.get(b).getSimpleFaultDataOld(false), 1.0, 1.0);
					double minDist = surf1.getMinDistance(surf2);
					subSectionDistances[a][b] = minDist;
					subSectionDistances[b][a] = minDist;
				}
			}
			System.out.print("\n");
			// Now save to a binary file
			try {
				// Create an output stream to the file.
				FileOutputStream file_output = new FileOutputStream (file);
				// Wrap the FileOutputStream with a DataOutputStream
				DataOutputStream data_out = new DataOutputStream (file_output);
				for(int i=0; i<numSubSections;i++)
					for(int j=i; j<numSubSections;j++)
						data_out.writeDouble(subSectionDistances[i][j]);
				// Close file
				file_output.close ();
			}
			catch (IOException e) {
				System.out.println ("IO exception = " + e );
			}
		}
	}



	  private void calcSubSectionAzimuths() {

		  subSectionAzimuths = new double[numSubSections][numSubSections];

		  // construct filename
		  String name = subsectsNameForFile+deformationModelId+"_"+(int)(maxSubSectionLength*1000)+"_Azimuths";
		  String fullpathname = precomputedDataDir.getAbsolutePath()+File.separator+name;
		  File file = new File (fullpathname);
		  
		  // Read data if already computed and saved
		  if(file.exists()) {
			  if(D) System.out.println("Reading existing file: "+ name);
			    try {
			        // Wrap the FileInputStream with a DataInputStream
			        FileInputStream file_input = new FileInputStream (file);
			        DataInputStream data_in    = new DataInputStream (file_input );
					  for(int i=0; i<numSubSections;i++)
						  for(int j=0; j<numSubSections;j++) {
							  subSectionAzimuths[i][j] = data_in.readDouble();
						  }
			        data_in.close ();
			      } catch  (IOException e) {
			         System.out.println ( "IO Exception =: " + e );
			      }

		  }
		  else {// Calculate new distance matrix & save to a file
			  System.out.println("Calculating azimuth data and will save to file: "+name);

			  int progress = 0, progressInterval=10;  // for progress report
			  System.out.print("Azimuth Calc % Done:");
			  
			  for(int a=0;a<numSubSections;a++) {
				  if (100*a/numSubSections > progress) {
					  System.out.print("\t"+progress);
					  progress += progressInterval;
				  }
				  StirlingGriddedSurface surf1 = new StirlingGriddedSurface(subSectionPrefDataList.get(a).getSimpleFaultDataOld(false), 1.0);
				  Location loc1 = surf1.getLocation(surf1.getNumRows()/2, surf1.getNumCols()/2);
				  for(int b=0;b<numSubSections;b++) {
					  StirlingGriddedSurface surf2 = new StirlingGriddedSurface(subSectionPrefDataList.get(b).getSimpleFaultDataOld(false), 1.0);
					  Location loc2 = surf2.getLocation((int)(surf2.getNumRows()/2), (int)(surf2.getNumCols()/2));
					  subSectionAzimuths[a][b] = LocationUtils.azimuth(loc1, loc2);
				  }
			  }
			  System.out.print("\n");
			  // Now save to a binary file
			  try {
				  // Create an output stream to the file.
				  FileOutputStream file_output = new FileOutputStream (file);
				  // Wrap the FileOutputStream with a DataOutputStream
				  DataOutputStream data_out = new DataOutputStream (file_output);
				  for(int i=0; i<numSubSections;i++)
					  for(int j=0; j<numSubSections;j++)
						  data_out.writeDouble(subSectionAzimuths[i][j]);
				  // Close file
				  file_output.close ();
			  }
			  catch (IOException e) {
				  System.out.println ("IO exception = " + e );
			  }
		  }
	  }
	  

	  /**
	   * This writes the section data to an ASCII file
	 * @throws IOException 
	   */
	  public void writeSectionsToFile(String filePathAndName) throws IOException {
		  ArrayList<String> metaData = new ArrayList<String>();
		  metaData.add("deformationModelId = "+deformationModelId);
		  metaData.add("includeSectionsWithNaN_slipRates = "+includeSectionsWithNaN_slipRates);
		  metaData.add("maxSubSectionLength = "+maxSubSectionLength+"  (in units of section down-dip width)");
		  FaultSectionDataWriter.writeSectionsToFile(subSectionPrefDataList, 
					metaData, filePathAndName);

	  }
	  
	  
	  /**
	   * This writes the rupture sections to an ASCII file
	   * @param filePathAndName
	   */
	  public void writeRupsToFiles(String filePathAndName) {
		  rupsInFaultSysInv.writeRupsToFiles(filePathAndName);  // should this use the utility class (FaultSectionDataWriter with aname change)?
	  }
	  


	/**
	 * @param args
	 */
	public static void main(String[] args) {
		
		TestInversion test = new TestInversion();
		
		// the following was run on April 19th for N Cal case for Tom Parsons (see email that day)
//		test.writeSectionsToFile("sectionsForTom041911");
//		test.writeRupsToFiles("rupturesForTom041911");
		
		/* Tests for the Loc at the N. end of the Parkfield Trace
		Region nCalRegion = new CaliforniaRegions.RELM_NOCAL();
		System.out.println(nCalRegion.contains(new Location(36.002647,-120.56089000000001)));
		Region mod_relm_nocal = new Region(nCalRegion.getBorder(), BorderType.GREAT_CIRCLE);
		System.out.println(mod_relm_nocal.contains(new Location(36.002647,-120.56089000000001)));
		*/

	}


}

