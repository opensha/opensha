package org.opensha.sha.simulators.utils;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.StringTokenizer;

import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.opensha.commons.data.function.ArbDiscrEmpiricalDistFunc;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.geo.Location;
import org.opensha.commons.util.FileUtils;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.UCERF2;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.data.SegRateConstraint;
import org.opensha.commons.gui.plot.GraphWindow;

public class UCERF2_DataForComparisonFetcher {
	
	private final static String SEG_RATE_FILE_NAME = "org/opensha/sha/earthquake/rupForecastImpl/WGCEP_UCERF_2_Final/data/Appendix_C_Table7_091807.xls";
	private final static String PARSONS_PDF_DATA_DIR = "org/opensha/sha/earthquake/rupForecastImpl/WGCEP_UCERF_2_Final/data/ParsonsMRI_PDFs";
	ArrayList<String> parsonsSiteNames;
	ArrayList<String> parsonsPDF_FileNamesPois;
	ArrayList<String> parsonsPDF_FileNamesBPT;
	ArrayList<Location> parsonsSiteLocs;
	ArrayList<Double> parsonsBestPoisEventRates;
	ArrayList<Double> parsonsEventPoisRateSigmas;
	ArrayList<Double> parsonsEventPoisRateLower95s;
	ArrayList<Double> parsonsEventPoisRateUpper95s;
	
	// These are the lists of PDF functions
	ArrayList<EvenlyDiscretizedFunc> parsonsPoisPDF_Funcs;
	ArrayList<EvenlyDiscretizedFunc> parsonsBPT0pt01_PDF_Funcs;
	ArrayList<EvenlyDiscretizedFunc> parsonsBPT0pt10_PDF_Funcs;
	ArrayList<EvenlyDiscretizedFunc> parsonsBPT0pt20_PDF_Funcs;
	ArrayList<EvenlyDiscretizedFunc> parsonsBPT0pt30_PDF_Funcs;
	ArrayList<EvenlyDiscretizedFunc> parsonsBPT0pt40_PDF_Funcs;
	ArrayList<EvenlyDiscretizedFunc> parsonsBPT0pt50_PDF_Funcs;
	ArrayList<EvenlyDiscretizedFunc> parsonsBPT0pt60_PDF_Funcs;
	ArrayList<EvenlyDiscretizedFunc> parsonsBPT0pt70_PDF_Funcs;
	ArrayList<EvenlyDiscretizedFunc> parsonsBPT0pt80_PDF_Funcs;
	ArrayList<EvenlyDiscretizedFunc> parsonsBPT0pt90_PDF_Funcs;
	ArrayList<EvenlyDiscretizedFunc> parsonsBPT0pt99_PDF_Funcs;
	
	
	public UCERF2_DataForComparisonFetcher() {
		readParsonsXLS_File();
		readParsonsPDF_Data();
		
		
	}

	/**
	 * This returns only a single best-estimate incremental UCERF2 MFDs for the RELM region because
	 * UCERF2 doesn't give more due to dubious uncertainties.  All the rates are multiplied by 0.5 
	 * in order to approximate the rates expected for Northern or Southern California.  This was 
	 * done because UCERF2 does not supply No and/or So cal rates for MFDs that include aftershocks.  
	 * The 0.5 value is justified given overall uncertainties (e.g., when looking at the obs MFDs 
	 * that exclude aftershocks).
	 * @param includeAftershocks
	 * @return
	 */
	public static DiscretizedFunc getHalf_UCERF2_ObsIncrMFDs(boolean includeAftershocks) {
		UCERF2 ucerf2 = new UCERF2();
		ArrayList<DiscretizedFunc> funcs = new ArrayList<DiscretizedFunc>();
		funcs.addAll(ucerf2.getObsIncrMFD(includeAftershocks));
		for(DiscretizedFunc func:funcs) {
			for(int i=0;i<func.size();i++) func.set(i,func.getY(i)*0.5);
			func.setInfo("  ");
		}
		funcs.get(0).setName("UCERF2 Observed Incremental MFD Divided by Two (best estimate)");
		return funcs.get(0);
	}
	
	
	/**
	 * This returns a list of observed cumulative UCERF2 MFDs for the RELM region, where the first is the 
	 * best estimate, the second is the lower 95% confidence bound, and the third is the upper 
	 * 95% confidence bound.  All the rates are multiplied by 0.5 in order to approximate the rates
	 * expected for Northern or Southern California.  This was done because UCERF2 does
	 * not supply No and/or So cal rates for MFDs that include aftershocks.  The 0.5 value is justified 
	 * given overall uncertainties (e.g., when looking at the obs MFDs that exclude aftershocks).
	 * @param includeAftershocks
	 * @return
	 */
	public static ArrayList<DiscretizedFunc> getHalf_UCERF2_ObsCumMFDs(boolean includeAftershocks) {
		ArrayList<DiscretizedFunc> funcs = new ArrayList<DiscretizedFunc>();
		funcs.addAll(UCERF2.getObsCumMFD(includeAftershocks));
		for(DiscretizedFunc func:funcs) {
			for(int i=0;i<func.size();i++) func.set(i,func.getY(i)*0.5);
			func.setInfo("  ");
		}
		funcs.get(0).setName("UCERF2 Observed Cumulative MFD Divided by Two (best estimate)");
		funcs.get(1).setName("UCERF2 Observed Cumulative MFD Divided by Two (lower 95% confidence)");
		funcs.get(2).setName("UCERF2 Observed Cumulative MFD Divided by Two (upper 95% confidence)");
		return funcs;
	}

	
	
	private void readParsonsXLS_File() {
		try {				
			POIFSFileSystem fs = new POIFSFileSystem(getClass().getClassLoader().getResourceAsStream(SEG_RATE_FILE_NAME));
			HSSFWorkbook wb = new HSSFWorkbook(fs);
			HSSFSheet sheet = wb.getSheetAt(0);
			int lastRowIndex = sheet.getLastRowNum();
			double lat, lon;;
			parsonsSiteNames = new ArrayList<String>();
			parsonsSiteLocs = new ArrayList<Location>();
			parsonsBestPoisEventRates = new ArrayList<Double>();
			parsonsEventPoisRateSigmas = new ArrayList<Double>();
			parsonsEventPoisRateLower95s = new ArrayList<Double>();
			parsonsEventPoisRateUpper95s = new ArrayList<Double>();

			for(int r=1; r<=lastRowIndex; ++r) {	
				HSSFRow row = sheet.getRow(r);
				if(row==null) continue;
				HSSFCell cell = row.getCell( (short) 1);
				if(cell==null || cell.getCellType()==HSSFCell.CELL_TYPE_STRING) continue;
				parsonsSiteNames.add(row.getCell( (short) 0).getStringCellValue().trim());
				lat = cell.getNumericCellValue();
				lon = row.getCell( (short) 2).getNumericCellValue();
				parsonsSiteLocs.add(new Location(lat,lon));
				parsonsBestPoisEventRates.add(row.getCell( (short) 3).getNumericCellValue());
				parsonsEventPoisRateSigmas.add(row.getCell( (short) 4).getNumericCellValue());
				parsonsEventPoisRateLower95s.add(row.getCell( (short) 7).getNumericCellValue());
				parsonsEventPoisRateUpper95s.add(row.getCell( (short) 8).getNumericCellValue());
				
			}
		}catch(Exception e) {
			e.printStackTrace();
		}
		
//		for(int i=0;i<parsonsSiteNames.size();i++)
//			System.out.println(i+"\t"+parsonsSiteNames.get(i));

		//		for(Location loc: parsonsSiteLocs) System.out.println((float)loc.getLatitude()+"\t"+(float)loc.getLongitude());

	}
	
	public ArrayList<String> getParsonsSiteNames() {return parsonsSiteNames;}
	
	public ArrayList<Location> getParsonsSiteLocs() { return parsonsSiteLocs; }
	
	public ArrayList<Double> getParsonsBestPoisEventRatess() {return parsonsBestPoisEventRates;}
	
	public ArrayList<Double> getParsonsEventPoisRateSigmas() {return parsonsEventPoisRateSigmas;}
	
	public ArrayList<Double> getParsonsEventPoisRateLower95s() {return parsonsEventPoisRateLower95s;}
	
	public ArrayList<Double> getParsonsEventPoisRateUpper95s() {return parsonsEventPoisRateUpper95s;}
	
	private void setParsonsPDF_FileNames() {
		parsonsPDF_FileNamesPois = new ArrayList<String>();
		parsonsPDF_FileNamesBPT = new ArrayList<String>();

		// POISSON FILES
		parsonsPDF_FileNamesPois.add("poisson/tally1.cal_n.txt");				//	Calaveras fault - North
		parsonsPDF_FileNamesPois.add("poisson/tally1.els_glen_pois.txt");		//	Elsinore - Glen Ivy
		parsonsPDF_FileNamesPois.add("poisson/tally1.els_jul_pois.txt");		//	Elsinore Fault - Julian
		parsonsPDF_FileNamesPois.add("poisson/tally1.els_tem_pois.txt");		//	Elsinore - Temecula
		parsonsPDF_FileNamesPois.add("poisson/tally1.els_whit_pois.txt");		//	Elsinore - Whittier
		parsonsPDF_FileNamesPois.add("poisson/tally1.gar_c_pois.txt");			//	Garlock - Central
		parsonsPDF_FileNamesPois.add("poisson/tally1.gar_w_pois.txt");			//	Garlock - Western
		parsonsPDF_FileNamesPois.add("poisson/tally1.hayn_pois.txt");			//	Hayward fault - North
		parsonsPDF_FileNamesPois.add(null);										//	Hayward fault - South
		parsonsPDF_FileNamesPois.add(null);										//	N. San Andreas - Vendanta		File "poisson/tally1.nsaf_vend_pois.txt" is empty
		parsonsPDF_FileNamesPois.add("poisson/tally1.ssas_pois.txt");			//	SAF - Arano Flat
		parsonsPDF_FileNamesPois.add("poisson/tally1.ft_ross.txt");				//	N. San Andreas -  Fort Ross
		parsonsPDF_FileNamesPois.add("poisson/tally1.san_greg_pois.txt");		//	San Gregorio - North
		parsonsPDF_FileNamesPois.add(null);										//	San Jacinto - Hog Lake			File "poisson/tally1.sjc_hog_pois.txt" is empty
		parsonsPDF_FileNamesPois.add("poisson/tally1.sjc_sup_pois.txt");		//	San Jacinto - Superstition
		parsonsPDF_FileNamesPois.add("poisson/tally_burro_p_new.txt");			//	San Andreas - Burro Flats                         
		parsonsPDF_FileNamesPois.add(null);										//	SAF- Carrizo Bidart
		parsonsPDF_FileNamesPois.add("poisson/tally1.new_carrizo.txt");			//	SAF - Combined Carrizo Plain
		parsonsPDF_FileNamesPois.add("poisson/tallly1.indio_pois.txt");			//	San Andrteas - Indio  			File "poisson/tallly1.pallet_pois.txt" is empty
		parsonsPDF_FileNamesPois.add(null);										//	San Andreas - Pallett Creek
		parsonsPDF_FileNamesPois.add("poisson/tallly1.pitman_pois.txt");		//	San Andreas - Pitman Canyon      
		parsonsPDF_FileNamesPois.add("poisson/tallly1.plunge_pois.txt");		//	San Andreas - Plunge Creek   
		parsonsPDF_FileNamesPois.add("poisson/tallly1.thous_palms_pois.txt");	//	Mission Creek - 1000 Palms		File "poisson/tallly1.wrightwood_pois.txt" is empty
		parsonsPDF_FileNamesPois.add(null);	//	San Andreas - Wrightwood     
		// Not allocated:		
		//					
		//					poisson/tallly1.carrizo_pois.txt	old version
		//					poisson/New_hays_Poiss.txt			**** Has duplicates that need to be summed together
		//					Others with no data couldn't be determined because of computational sampling limits

		
		// BPT FILES
		parsonsPDF_FileNamesBPT.add("bpt/tally_cal_n.txt");			//	Calaveras fault - North
		parsonsPDF_FileNamesBPT.add("bpt/tally_els_glen.txt");		//	Elsinore - Glen Ivy
		parsonsPDF_FileNamesBPT.add("bpt/tally_els_jul.txt");		//	Elsinore Fault - Julian
		parsonsPDF_FileNamesBPT.add("bpt/tally_els_tem.txt");		//	Elsinore - Temecula
		parsonsPDF_FileNamesBPT.add("bpt/tally_els_whit.txt");		//	Elsinore - Whittier
		parsonsPDF_FileNamesBPT.add("bpt/tally_gar_c.txt");			//	Garlock - Central
		parsonsPDF_FileNamesBPT.add("bpt/tally_gar_w.txt");			//	Garlock - Western
		parsonsPDF_FileNamesBPT.add("bpt/tally_hayn.txt");			//	Hayward fault - North
		parsonsPDF_FileNamesBPT.add(null);							//	Hayward fault - South
		parsonsPDF_FileNamesBPT.add(null);							//	N. San Andreas - Vendanta
		parsonsPDF_FileNamesBPT.add("bpt/tally_ssas.txt");			//	SAF - Arano Flat
		parsonsPDF_FileNamesBPT.add("bpt/tally_ft_ross.txt");		//	N. San Andreas -  Fort Ross
		parsonsPDF_FileNamesBPT.add("bpt/tally_san_greg.txt");		//	San Gregorio - North
		parsonsPDF_FileNamesBPT.add(null);							//	San Jacinto - Hog Lake
		parsonsPDF_FileNamesBPT.add("bpt/tally_sjc_sup.txt");		//	San Jacinto - Superstition
		parsonsPDF_FileNamesBPT.add("bpt/tally_burro.txt");			//	San Andreas - Burro Flats                         
		parsonsPDF_FileNamesBPT.add(null);							//	SAF- Carrizo Bidart
		parsonsPDF_FileNamesBPT.add("bpt/tally_carrizo.txt");		//	SAF - Combined Carrizo Plain     
		parsonsPDF_FileNamesBPT.add("bpt/tally_indio.txt");			//	San Andrteas - Indio  
		parsonsPDF_FileNamesBPT.add(null);							//	San Andreas - Pallett Creek
		parsonsPDF_FileNamesBPT.add("bpt/tally_pitman.txt");		//	San Andreas - Pitman Canyon      
		parsonsPDF_FileNamesBPT.add("bpt/tally_plunge.txt");		//	San Andreas - Plunge Creek   
		parsonsPDF_FileNamesBPT.add("bpt/tally_thous_palms.txt");	//	Mission Creek - 1000 Palms
		parsonsPDF_FileNamesBPT.add(null);							//	San Andreas - Wrightwood     
		// Not allocated:		
		//					
		//					bpt/New_hays_BPT_tally.txt			**** Has duplicates that need to be summed together
		//					Others with no data couldn't be determined because of computational sampling limits

	}
	
	
	/**
	 * This assumes that all MRI deltas are 10 yrs (true here)
	 */
	private void readParsonsPDF_Data() {
		setParsonsPDF_FileNames();
		parsonsPoisPDF_Funcs = new ArrayList<EvenlyDiscretizedFunc>();
		ArrayList<EvenlyDiscretizedFunc> testList;
		// Read Poisson PDF Files
		for(int f=0; f<parsonsPDF_FileNamesPois.size();f++) {
			String fileName = parsonsPDF_FileNamesPois.get(f);
			//			System.out.println(fileName);
			if(fileName != null) {
				try {
					String filePath = PARSONS_PDF_DATA_DIR+"/"+fileName;
					ArrayList<String> fileLines = FileUtils.loadJarFile(filePath);
					ArrayList<Double> mriList = new ArrayList<Double> ();
					ArrayList<Double> numHitsList = new ArrayList<Double> ();
					double totalNumHits=0;
					for(String line: fileLines) {
						StringTokenizer st = new StringTokenizer(line);
						mriList.add(Double.parseDouble(st.nextToken()));
						double numHits = Integer.parseInt(st.nextToken());
						if(numHits == 0)  System.out.println("fileName HAD A ZERO!!!");
						numHitsList.add(numHits);
						totalNumHits += numHits;
					}
					int num = (int)Math.round((mriList.get(mriList.size()-1) - mriList.get(0))/10.0) + 1;
					EvenlyDiscretizedFunc func = new EvenlyDiscretizedFunc(mriList.get(0), mriList.get(mriList.size()-1), num);
					func.setTolerance(1.0);
					for(int i=0;i<mriList.size();i++) {
						func.set(mriList.get(i), numHitsList.get(i)/totalNumHits);
					}
					func.setName(parsonsSiteNames.get(f));
					func.setInfo("From file: "+fileName);
					parsonsPoisPDF_Funcs.add(func);
					testList = new ArrayList<EvenlyDiscretizedFunc>();
					testList.add(func);
					//					GraphWindow graph = new GraphWindow(testList, "Parson's PDFs");
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} else {
				parsonsPoisPDF_Funcs.add(null);
			}
		}

		parsonsBPT0pt01_PDF_Funcs = new ArrayList<EvenlyDiscretizedFunc>();
		parsonsBPT0pt10_PDF_Funcs = new ArrayList<EvenlyDiscretizedFunc>();
		parsonsBPT0pt20_PDF_Funcs = new ArrayList<EvenlyDiscretizedFunc>();
		parsonsBPT0pt30_PDF_Funcs = new ArrayList<EvenlyDiscretizedFunc>();
		parsonsBPT0pt40_PDF_Funcs = new ArrayList<EvenlyDiscretizedFunc>();
		parsonsBPT0pt50_PDF_Funcs = new ArrayList<EvenlyDiscretizedFunc>();
		parsonsBPT0pt60_PDF_Funcs = new ArrayList<EvenlyDiscretizedFunc>();
		parsonsBPT0pt70_PDF_Funcs = new ArrayList<EvenlyDiscretizedFunc>();
		parsonsBPT0pt80_PDF_Funcs = new ArrayList<EvenlyDiscretizedFunc>();
		parsonsBPT0pt90_PDF_Funcs = new ArrayList<EvenlyDiscretizedFunc>();
		parsonsBPT0pt99_PDF_Funcs = new ArrayList<EvenlyDiscretizedFunc>();

		//		ArbDiscrEmpiricalDistFunc func = new ArbDiscrEmpiricalDistFunc();
		ArrayList<ArrayList<Double>> mriListList;
		ArrayList<ArrayList<Double>> numHitsListList;
		ArrayList<Double> covList;
		ArrayList<Double> mriList=null;
		ArrayList<Double> numHitsList=null;
		for(int f=0;f<parsonsPDF_FileNamesBPT.size();f++) {
			String fileName = parsonsPDF_FileNamesBPT.get(f);
			if(fileName != null)
				try {
					String filePath = PARSONS_PDF_DATA_DIR+"/"+fileName;
//					System.out.println(filePath);
					ArrayList<String> fileLines = FileUtils.loadJarFile(filePath);
					double lastCOV= -1;
					covList = new ArrayList<Double>();
					mriListList = new ArrayList<ArrayList<Double>>();
					numHitsListList = new ArrayList<ArrayList<Double>>();
					for(String line: fileLines) {
						StringTokenizer st = new StringTokenizer(line);
						double cov = Double.parseDouble(st.nextToken());
						double mri = Double.parseDouble(st.nextToken());
						double numHits = Integer.parseInt(st.nextToken());
						if(cov != lastCOV) {
							covList.add(cov);
							mriList = new ArrayList<Double>();
							numHitsList = new ArrayList<Double>();
							mriListList.add(mriList);
							numHitsListList.add(numHitsList);
						}
						mriList.add(mri);
						numHitsList.add(numHits);
						lastCOV=cov;
						//						func.set(cov, numHits);
					}
//					System.out.println(covList);
					for(int i=0; i<covList.size();i++) {
						mriList = mriListList.get(i);
						numHitsList= numHitsListList.get(i);
						double totalNumHits =0;
						for(Double numHits:numHitsList) totalNumHits+=numHits;
						int num = (int)Math.round((mriList.get(mriList.size()-1) - mriList.get(0))/10.0) + 1;
						EvenlyDiscretizedFunc func = new EvenlyDiscretizedFunc(mriList.get(0), mriList.get(mriList.size()-1), num);
						func.setTolerance(1.0);
						for(int j=0;j<mriList.size();j++) {
							func.set(mriList.get(j), numHitsList.get(j)/totalNumHits);
						}
						double cov = covList.get(i);
						func.setName(parsonsSiteNames.get(f)+" PDF for BPT Model with COV="+cov);
						func.setInfo("From file: "+fileName);
						if(cov == 0.01)     parsonsBPT0pt01_PDF_Funcs.add(func);
						else if(cov == 0.1) parsonsBPT0pt10_PDF_Funcs.add(func);
						else if(cov == 0.2) parsonsBPT0pt20_PDF_Funcs.add(func);
						else if(cov == 0.3) parsonsBPT0pt30_PDF_Funcs.add(func);
						else if(cov == 0.4) parsonsBPT0pt40_PDF_Funcs.add(func);
						else if(cov == 0.5) parsonsBPT0pt50_PDF_Funcs.add(func);
						else if(cov == 0.6) parsonsBPT0pt60_PDF_Funcs.add(func);
						else if(cov == 0.7) parsonsBPT0pt70_PDF_Funcs.add(func);
						else if(cov == 0.8) parsonsBPT0pt80_PDF_Funcs.add(func);
						else if(cov == 0.9) parsonsBPT0pt90_PDF_Funcs.add(func);
						else if(cov == 0.99) parsonsBPT0pt99_PDF_Funcs.add(func);
						else throw new RuntimeException("COV of "+cov+" not recognized");
					}
					// add nulls for any missing COVs
					if(!covList.contains(new Double(0.01))) parsonsBPT0pt01_PDF_Funcs.add(null);
					if(!covList.contains(new Double(0.1)))  parsonsBPT0pt10_PDF_Funcs.add(null);
					if(!covList.contains(new Double(0.2)))  parsonsBPT0pt20_PDF_Funcs.add(null);
					if(!covList.contains(new Double(0.3)))  parsonsBPT0pt30_PDF_Funcs.add(null);
					if(!covList.contains(new Double(0.4)))  parsonsBPT0pt40_PDF_Funcs.add(null);
					if(!covList.contains(new Double(0.5)))  parsonsBPT0pt50_PDF_Funcs.add(null);
					if(!covList.contains(new Double(0.6)))  parsonsBPT0pt60_PDF_Funcs.add(null);
					if(!covList.contains(new Double(0.7)))  parsonsBPT0pt70_PDF_Funcs.add(null);
					if(!covList.contains(new Double(0.8)))  parsonsBPT0pt80_PDF_Funcs.add(null);
					if(!covList.contains(new Double(0.9)))  parsonsBPT0pt90_PDF_Funcs.add(null);
					if(!covList.contains(new Double(0.99))) parsonsBPT0pt99_PDF_Funcs.add(null);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				else {
					parsonsBPT0pt01_PDF_Funcs.add(null);
					parsonsBPT0pt10_PDF_Funcs.add(null);
					parsonsBPT0pt20_PDF_Funcs.add(null);
					parsonsBPT0pt30_PDF_Funcs.add(null);
					parsonsBPT0pt40_PDF_Funcs.add(null);
					parsonsBPT0pt50_PDF_Funcs.add(null);
					parsonsBPT0pt60_PDF_Funcs.add(null);
					parsonsBPT0pt70_PDF_Funcs.add(null);
					parsonsBPT0pt80_PDF_Funcs.add(null);
					parsonsBPT0pt90_PDF_Funcs.add(null);
					parsonsBPT0pt99_PDF_Funcs.add(null);
				}
		}
/*
		System.out.println(parsonsBPT0pt01_PDF_Funcs.size());
		System.out.println(parsonsBPT0pt10_PDF_Funcs.size());
		System.out.println(parsonsBPT0pt20_PDF_Funcs.size());
		System.out.println(parsonsBPT0pt30_PDF_Funcs.size());
		System.out.println(parsonsBPT0pt40_PDF_Funcs.size());
		System.out.println(parsonsBPT0pt50_PDF_Funcs.size());
		System.out.println(parsonsBPT0pt60_PDF_Funcs.size());
		System.out.println(parsonsBPT0pt70_PDF_Funcs.size());
		System.out.println(parsonsBPT0pt80_PDF_Funcs.size());
		System.out.println(parsonsBPT0pt90_PDF_Funcs.size());
		System.out.println(parsonsBPT0pt99_PDF_Funcs.size());
*/
		int index =-1;
		for(String siteName: parsonsSiteNames) {
			ArrayList<EvenlyDiscretizedFunc> testfuncs = new 	ArrayList<EvenlyDiscretizedFunc>();
			index += 1;
			if (parsonsPoisPDF_Funcs.get(index) != null) testfuncs.add(parsonsPoisPDF_Funcs.get(index));
			if (parsonsBPT0pt01_PDF_Funcs.get(index) != null) testfuncs.add(parsonsBPT0pt01_PDF_Funcs.get(index));
			if (parsonsBPT0pt10_PDF_Funcs.get(index) != null) testfuncs.add(parsonsBPT0pt10_PDF_Funcs.get(index));
			if (parsonsBPT0pt20_PDF_Funcs.get(index) != null) testfuncs.add(parsonsBPT0pt20_PDF_Funcs.get(index));
			if (parsonsBPT0pt30_PDF_Funcs.get(index) != null) testfuncs.add(parsonsBPT0pt30_PDF_Funcs.get(index));
			if (parsonsBPT0pt40_PDF_Funcs.get(index) != null) testfuncs.add(parsonsBPT0pt40_PDF_Funcs.get(index));
			if (parsonsBPT0pt50_PDF_Funcs.get(index) != null) testfuncs.add(parsonsBPT0pt50_PDF_Funcs.get(index));
			if (parsonsBPT0pt60_PDF_Funcs.get(index) != null) testfuncs.add(parsonsBPT0pt60_PDF_Funcs.get(index));
			if (parsonsBPT0pt70_PDF_Funcs.get(index) != null) testfuncs.add(parsonsBPT0pt70_PDF_Funcs.get(index));
			if (parsonsBPT0pt80_PDF_Funcs.get(index) != null) testfuncs.add(parsonsBPT0pt80_PDF_Funcs.get(index));
			if (parsonsBPT0pt90_PDF_Funcs.get(index) != null) testfuncs.add(parsonsBPT0pt90_PDF_Funcs.get(index));
			if (parsonsBPT0pt99_PDF_Funcs.get(index) != null) testfuncs.add(parsonsBPT0pt99_PDF_Funcs.get(index));

			/**/
			if(testfuncs.size()>0) {
//				GraphWindow graph = new GraphWindow(testfuncs, "Parson's BPT MRI PDFs for "+siteName);
			}
			
		}



	}
	
	/**
	 * This returns an EvenlyDiscretizedFunc where the best Poisson MRI and 95% conficence bounds 
	 * are the only non-zero values (with arbitrary y-axis values of 0.2 for the best estimates
	 * and 0.05 for the 95% confidence bounds).  This function is useful for comparing with PDFs
	 * from simulators to see whether the latter values are in range of the paleo data estimate.
	 * @param ithSite
	 * @return
	 */
	public EvenlyDiscretizedFunc getParsons95PercentPoisFunction(int ithSite) {
		
		// round RIs to nearest 10 years
		double firstBin = 10*Math.round(0.1/parsonsEventPoisRateUpper95s.get(ithSite));
		double lastBin = 10*Math.round(0.1/parsonsEventPoisRateLower95s.get(ithSite));
		double bestBin = 10*Math.round(0.1/parsonsBestPoisEventRates.get(ithSite));
		int numBin = (int)Math.round((lastBin-firstBin)/10) +1;
		EvenlyDiscretizedFunc func = new EvenlyDiscretizedFunc(firstBin, lastBin, numBin);
/*
		System.out.println(parsonsEventRateUpper95s.get(ithSite)+"\t"+parsonsEventRateLower95s.get(ithSite)+"\t"+
				parsonsBestEventRates.get(ithSite));
		System.out.println(firstBin+"\t"+lastBin+"\t"+bestBin);
		System.out.println(func);
*/
		func.set(firstBin,0.05);
		func.set(lastBin,0.05);
		func.set(bestBin,0.2);
		func.setName("PaleoSeismic Estimate of MRI (and 95% conf bounds) for "+this.getParsonsSiteName(ithSite));
		func.setInfo("(from Appendix C of the UCERF2 report)");
		return func;
	}
	
	
	public EvenlyDiscretizedFunc getParsons95PercentPoisFunction(Location loc) {
		int siteIndex = getParsonsIndexForLoc(loc);
		if(siteIndex == -1) 
			return null;
		else
			return getParsons95PercentPoisFunction(siteIndex);
	}
	
	public String getParsonsSiteName(int index) {return parsonsSiteNames.get(index); }

	public Location getParsonsSiteLoc(int index) {return parsonsSiteLocs.get(index); }
	
	/**
	 * This returns -1 if location not found
	 */
	public int getParsonsIndexForLoc(Location loc) {
		int index = -1;
		for(int i=0; i<parsonsSiteLocs.size(); i++)
			if(parsonsSiteLocs.get(i).equals(loc)) index = i;
		return index;
	}


	
	public ArrayList<EvenlyDiscretizedFunc> getParsonsPois_MRI_PDF_Funcs() { return parsonsPoisPDF_Funcs; }
	public ArrayList<EvenlyDiscretizedFunc> getParsonsBPT_MRI_COV_0pt01_PDF_Funcs() { return parsonsBPT0pt01_PDF_Funcs; }
	public ArrayList<EvenlyDiscretizedFunc> getParsonsBPT_MRI_COV_0pt10_PDF_Funcs() { return parsonsBPT0pt10_PDF_Funcs; }
	public ArrayList<EvenlyDiscretizedFunc> getParsonsBPT_MRI_COV_0pt20_PDF_Funcs() { return parsonsBPT0pt20_PDF_Funcs; }
	public ArrayList<EvenlyDiscretizedFunc> getParsonsBPT_MRI_COV_0pt30_PDF_Funcs() { return parsonsBPT0pt30_PDF_Funcs; }
	public ArrayList<EvenlyDiscretizedFunc> getParsonsBPT_MRI_COV_0pt40_PDF_Funcs() { return parsonsBPT0pt40_PDF_Funcs; }
	public ArrayList<EvenlyDiscretizedFunc> getParsonsBPT_MRI_COV_0pt50_PDF_Funcs() { return parsonsBPT0pt50_PDF_Funcs; }
	public ArrayList<EvenlyDiscretizedFunc> getParsonsBPT_MRI_COV_0pt60_PDF_Funcs() { return parsonsBPT0pt60_PDF_Funcs; }
	public ArrayList<EvenlyDiscretizedFunc> getParsonsBPT_MRI_COV_0pt70_PDF_Funcs() { return parsonsBPT0pt70_PDF_Funcs; }
	public ArrayList<EvenlyDiscretizedFunc> getParsonsBPT_MRI_COV_0pt80_PDF_Funcs() { return parsonsBPT0pt80_PDF_Funcs; }
	public ArrayList<EvenlyDiscretizedFunc> getParsonsBPT_MRI_COV_0pt90_PDF_Funcs() { return parsonsBPT0pt90_PDF_Funcs; }
	public ArrayList<EvenlyDiscretizedFunc> getParsonsBPT_MRI_COV_0pt99_PDF_Funcs() { return parsonsBPT0pt99_PDF_Funcs; }

	private void setParsonsBPT_MRI_statsData() {
			// 0	Calaveras fault - North
			// 1	Elsinore - Glen Ivy
			// 2	Elsinore Fault - Julian
			// 3	Elsinore - Temecula
			// 4	Elsinore - Whittier
			// 5	Garlock - Central
			// 6	Garlock - Western
			// 7	Hayward fault - North
			// 8	Hayward fault - South
			// 9	N. San Andreas - Vendanta
			// 10	SAF - Arano Flat
			// 11	N. San Andreas -  Fort Ross
			// 12	San Gregorio - North
			// 13	San Jacinto - Hog Lake
			// 14	San Jacinto - Superstition
			// 15	San Andreas - Burro Flats                         
			// 16	SAF- Carrizo Bidart
			// 17	SAF - Combined Carrizo Plain     
			// 18	San Andrteas - Indio  
			// 19	San Andreas - Pallett Creek
			// 20	San Andreas - Pitman Canyon      
			// 21	San Andreas - Plunge Creek   
			// 22	Mission Creek - 1000 Palms
			// 23	San Andreas - Wrightwood        
	}


	public static void main(String[] args) {
		UCERF2_DataForComparisonFetcher test = new UCERF2_DataForComparisonFetcher();
		
	}
	

	

}
