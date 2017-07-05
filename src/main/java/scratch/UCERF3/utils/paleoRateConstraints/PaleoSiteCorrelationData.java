package scratch.UCERF3.utils.paleoRateConstraints;

import java.awt.Color;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.dom4j.DocumentException;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.commons.gui.plot.GraphWindow;

import scratch.UCERF3.AverageFaultSystemSolution;
import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.inversion.InversionFaultSystemSolution;
import scratch.UCERF3.inversion.UCERF2_ComparisonSolutionFetcher;
import scratch.UCERF3.utils.FaultSystemIO;
import scratch.UCERF3.utils.UCERF3_DataUtils;
import scratch.UCERF3.utils.aveSlip.AveSlipConstraint;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Table;

/**
 * This represents correlation data between neighboring paleoseismic sites (loaded from
 * an excel file). It also contains static methods for loading in data, and creating plots
 * of the data with comparisons from a fault system solution.
 * @author kevin
 *
 */
public class PaleoSiteCorrelationData implements Serializable {
	
	private String site1Name;
	private Location site1Loc;
	private int site1SubSect;
	private String site2Name;
	private Location site2Loc;
	private int site2SubSect;
	private int site1Events;
	private int site2Events;
	private int numCorrelated;
	private boolean neighbors;
	private FaultModels fm;

	public PaleoSiteCorrelationData(String site1Name, Location site1Loc, int site1SubSect,
			String site2Name, Location site2Loc, int site2SubSect, int site1Events,
			int site2Events, int numCorrelated, boolean neighbors, FaultModels fm) {
		super();
		this.site1Name = site1Name;
		this.site1Loc = site1Loc;
		this.site1SubSect = site1SubSect;
		this.site2Name = site2Name;
		this.site2Loc = site2Loc;
		this.site2SubSect = site2SubSect;
		this.site1Events = site1Events;
		this.site2Events = site2Events;
		this.numCorrelated = numCorrelated;
		this.neighbors = neighbors;
		this.fm = fm;
	}

	public String getSite1Name() {
		return site1Name;
	}

	public Location getSite1Loc() {
		return site1Loc;
	}
	
	public int getSite1SubSect() {
		return site1SubSect;
	}

	public String getSite2Name() {
		return site2Name;
	}

	public Location getSite2Loc() {
		return site2Loc;
	}
	
	public int getSite2SubSect() {
		return site2SubSect;
	}

	public int getSite1Events() {
		return site1Events;
	}

	public int getSite2Events() {
		return site2Events;
	}

	public int getNumCorrelated() {
		return numCorrelated;
	}
	
	public int getTotNumEvents() {
		return numCorrelated + (site1Events - numCorrelated) + (site2Events - numCorrelated);
	}
	
	public boolean areNeighbors() {
		return neighbors;
	}
	
	public FaultModels getFaultModel() {
		return fm;
	}
	
	public PaleoSiteCorrelationData getReversed() {
		return new PaleoSiteCorrelationData(site2Name, site2Loc, site2SubSect, site1Name, site1Loc, site1SubSect,
				site2Events, site1Events, numCorrelated, neighbors, fm);
	}
	
	/**
	 * String used for hashing
	 * @return
	 */
	public String getHashString() {
		return getSite1Name()+"_"+getSite2Name();
	}
	
	public static final String SUB_DIR_NAME = "paleoRateData";
	public static final String FILE_NAME = "PaleoCorrelationData_2012_09_28.xls";

	/**
	 * @param args
	 * @throws IOException 
	 * @throws DocumentException 
	 */
	public static void main(String[] args) throws IOException, DocumentException {
		File solFile = new File(new File(UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR, "InversionSolutions"),
//		"FM3_1_ZENG_EllB_DsrTap_CharConst_M5Rate8.7_MMaxOff7.6_NoFix_SpatSeisU3_VarPaleo0.1_VarAveSlip0.1_VarMFDSmooth1000_VarSectNuclMFDWt0.1_sol.zip");
		"FM3_1_ZENG_EllB_DsrUni_CharConst_M5Rate8.7_MMaxOff7.6_NoFix_SpatSeisU3_VarPaleo10_VarMFDSmooth1000_VarSectNuclMFDWt0.01_sol.zip");
//		"FM2_1_UC2ALL_AveU2_DsrTap_CharConst_M5Rate8.7_MMaxOff7.6_NoFix_SpatSeisU2_VarNone_mean_sol.zip");
		InversionFaultSystemSolution sol = FaultSystemIO.loadInvSol(solFile);
		
		String fileName = solFile.getAbsolutePath().replaceAll(".zip", "")+"_paleo_correlation.xls";
		File outputFile = new File(fileName);
		
		double[] vals = get95PercentConfidenceBounds(5, 8);
		System.out.println(vals[0]+"\t"+vals[1]);
		vals = get95PercentConfidenceBounds(5, 10);
		System.out.println(vals[0]+"\t"+vals[1]);
		vals = get95PercentConfidenceBounds(3, 8);
		System.out.println(vals[0]+"\t"+vals[1]);
		
		Map<String, Table<String, String, PaleoSiteCorrelationData>> tables =
			loadPaleoCorrelationData(sol, outputFile);
		
		for (String faultName : tables.keySet()) {
			PlotSpec spec = getCorrelationPlotSpec(faultName, tables.get(faultName), sol);
			
			double maxX = 0;
			for (DiscretizedFunc func : spec.getPlotFunctionsOnly()) {
				double myMaxX = func.getMaxX();
				if (myMaxX > maxX)
					maxX = myMaxX;
			}
			
			GraphWindow gw = new GraphWindow(spec);
			gw.setAxisRange(0, maxX, 0, 1);
		}
	}
	
	public static Map<String, Table<String, String, PaleoSiteCorrelationData>> loadPaleoCorrelationData(
			InversionFaultSystemSolution sol) throws IOException {
		return loadPaleoCorrelationData(sol, null);
	}
	
	public static Map<String, Table<String, String, PaleoSiteCorrelationData>> loadPaleoCorrelationData(
			InversionFaultSystemSolution sol, File outputFile) throws IOException {
		return loadPaleoCorrelationData(UCERF3_DataUtils.locateResourceAsStream(SUB_DIR_NAME, FILE_NAME), sol, outputFile);
	}
	
	private static String getCellTextNullAsBlank(HSSFSheet sheet, int row, int col) {
		HSSFRow rowObj = sheet.getRow(row);
		if (rowObj == null)
			return "";
		HSSFCell cell = rowObj.getCell(col);
		if (cell == null)
			return "";
		return cell.getStringCellValue();
	}

	public static Map<String, Table<String, String, PaleoSiteCorrelationData>> loadPaleoCorrelationData(
			InputStream dataIS, InversionFaultSystemSolution sol, File outputFile) throws IOException {
		List<FaultSectionPrefData> faultSectionData = sol.getRupSet().getFaultSectionDataList();

		PaleoProbabilityModel paleoProb = UCERF3_PaleoProbabilityModel.load();

		POIFSFileSystem fs = new POIFSFileSystem(dataIS);
		HSSFWorkbook wb = new HSSFWorkbook(fs);
		HSSFSheet sheet = wb.getSheetAt(0);
		int lastRowIndex = sheet.getLastRowNum();

		List<int[]> ranges = Lists.newArrayList();
		int curStart = -1;
		for (int row=0; row<=lastRowIndex; row++) {
//			System.out.println("Row: "+row);
			
			if (curStart < 0 && getCellTextNullAsBlank(sheet, row, 1).equals("Latitude")) {
				curStart = row;
			} else if (getCellTextNullAsBlank(sheet, row, 0).isEmpty()) {
				if (curStart >= 0) {
					int[] range = { curStart+1, row-1 };
					ranges.add(range);
				}
				curStart = -1;
			}
		}
		
		Map<String, Table<String, String, PaleoSiteCorrelationData>> resultsMap = Maps.newHashMap();

		for (int[] range : ranges) {
			System.out.println("Range: "+range[0]+" => "+range[1]);
			String faultName = sheet.getRow(range[0]-2).getCell(0).getStringCellValue().trim();
			Preconditions.checkState(!faultName.isEmpty(), "No name found for range ["
					+range[0]+","+range[1]+"]. Data file format change?");
			Preconditions.checkState(!resultsMap.containsKey(faultName), "Duplicate fault name '"
					+faultName+"' for range ["+range[0]+","+range[1]+"]. Data file format change?");

			Map<String, Integer> subSectIndexMap = Maps.newHashMap();
			Map<String, Location> siteLocMap = Maps.newHashMap();

			for (int row=range[0]; row<=range[1]; row++) {
				HSSFRow theRow = sheet.getRow(row);
				String name = theRow.getCell(0).getStringCellValue().trim();
				double lat = theRow.getCell(1).getNumericCellValue();
				double lon = theRow.getCell(2).getNumericCellValue();
				Location loc = new Location(lat, lon);
				
				HSSFCell parentOverrideCell = theRow.getCell(3);
				List<Integer> parentOverrides = null;
				if (parentOverrideCell != null) {
					parentOverrides = AveSlipConstraint.loadParentIDs(parentOverrideCell);
					if (parentOverrides.isEmpty())
						parentOverrides = null;
				}

				double minDist = Double.MAX_VALUE, dist;
				int closestFaultSectionIndex=-1;

				for(int sectionIndex=0; sectionIndex<faultSectionData.size(); ++sectionIndex) {
					FaultSectionPrefData data = faultSectionData.get(sectionIndex);
					if (parentOverrides != null && !parentOverrides.contains(data.getParentSectionId()))
						continue;

					dist  = data.getFaultTrace().minDistToLine(loc);
					if(dist<minDist) {
						minDist = dist;
						closestFaultSectionIndex = sectionIndex;
					}
				}
				
//				System.out.println(name);
//				if (parentOverrides != null)
//					System.out.println(Joiner.on(", ").join(parentOverrides));
//				FaultSectionPrefData sect = faultSectionData.get(closestFaultSectionIndex);
//				System.out.println("Mapped "+faultName+" site "+name+" to sub sect: "+sect.getSectionId()
//						+". "+sect.getSectionName());

				Preconditions.checkState(minDist < 10d,
						"Min dist to sub sect greater than 10 KM: "+minDist+"\nloc: "+loc);

				subSectIndexMap.put(name, closestFaultSectionIndex);
				siteLocMap.put(name, loc);
			}
			
			int numSites = subSectIndexMap.size();
			
			Table<String, String, PaleoSiteCorrelationData> table = HashBasedTable.create(numSites, numSites);

			int startCol = 5;
			int endCol = startCol+numSites-1;

			for (int row=range[0]; row<=range[1]; row++) {
				int relativeRow = row-range[0];
				HSSFRow theRow = sheet.getRow(row);
				String name1 = theRow.getCell(0).getStringCellValue().trim();
				Integer sectIndex1 = subSectIndexMap.get(name1);

				for (int col=startCol; col<=endCol; col++) {
					int relativeCol = col-startCol;
					if (relativeCol == relativeRow)
						continue;
					if (relativeRow > relativeCol)
						continue;

					String name2 = sheet.getRow(range[0]-1).getCell(col).getStringCellValue().trim();
					Integer sectIndex2 = subSectIndexMap.get(name2);
					Preconditions.checkNotNull(sectIndex2, "Name not found: "+name2+" ("+row+","+col+")");
					
					HSSFCell correlatedCell = theRow.getCell(col);
					HSSFCell countsCell = sheet.getRow(range[0]+relativeCol).getCell(startCol+relativeRow);
					
					int numCorrelated = (int)correlatedCell.getNumericCellValue();
					String rawCountsStr = countsCell.getStringCellValue().trim().replaceAll(" ", "");
					String[] rawCountsSplit = rawCountsStr.split(",");
					Preconditions.checkState(rawCountsSplit.length == 2);
					int site1Count = Integer.parseInt(rawCountsSplit[0]);
					int site2Count = Integer.parseInt(rawCountsSplit[1]);
					boolean neighbors = relativeCol == relativeRow + 1;
					PaleoSiteCorrelationData corr = new PaleoSiteCorrelationData(name1, siteLocMap.get(name1),
							sectIndex1, name2, siteLocMap.get(name2), sectIndex2, site1Count, site2Count,
							numCorrelated, neighbors, sol.getRupSet().getFaultModel());
					table.put(name1, name2, corr);
					table.put(name2, name1, corr.getReversed());
					
					if (outputFile != null) {
						double prob = getRateCorrelated(paleoProb, sol, sectIndex1, sectIndex2);

						int totEvents = corr.getTotNumEvents();
						correlatedCell.setCellValue(prob);
						double theirProb = (double)numCorrelated / (double)totEvents;
						countsCell.setCellValue(theirProb);
					}
				}
			}
			resultsMap.put(faultName, table);
		}

		if (outputFile != null) {
			FileOutputStream fos = new FileOutputStream(outputFile);
			wb.write(fos);
			fos.close();
		}
		
		return resultsMap;
	}
	
	public static double getRateCorrelated(PaleoProbabilityModel paleoProb, FaultSystemSolution sol, int sectIndex1, int sectIndex2) {
		double rateTogether = 0d;
		double totRate = 0d;

		FaultSystemRupSet rupSet = sol.getRupSet();
		
		HashSet<Integer> rups1 = new HashSet<Integer>(rupSet.getRupturesForSection(sectIndex1));
		HashSet<Integer> rups2 = new HashSet<Integer>(rupSet.getRupturesForSection(sectIndex2));
		HashSet<Integer> totRups = new HashSet<Integer>();
		totRups.addAll(rups1);
		for (Integer rup : rups2)
			if (!totRups.contains(rup))
				totRups.add(rup);

		for (Integer rup : totRups) {
			double rate = sol.getRateForRup(rup);

			boolean sect1 = rups1.contains(rup);
			boolean sect2 = rups2.contains(rup);
			Preconditions.checkState(sect1 || sect2);
			boolean together = sect1 && sect2;
			double prob1 = 0; double prob2 = 0;

			if (sect1)
				prob1 = paleoProb.getProbPaleoVisible(rupSet, rup, sectIndex1);
			if (sect2)
				prob2 = paleoProb.getProbPaleoVisible(rupSet, rup, sectIndex2);

			double myRateTogether = 0;
			if (together)
				myRateTogether += rate * prob1 * prob2;

			// This is [total rate at 1] (rate*prob1) + [total rate at sect2] (rate*prob2) - [rate at both sites]
			// So we are adding the rates at the 2 sites, then subtracting off the overlap
			totRate += rate*(prob1+prob2)-myRateTogether;
			rateTogether += myRateTogether;
		}

		return rateTogether / totRate;
	}
	
	private static final String CONFIDENCE_BOUNDS_UPPER_FILE_NAME = "PaleoCorrelation95ConfidenceUpperBounds.csv";
	private static final String CONFIDENCE_BOUNDS_LOWER_FILE_NAME = "PaleoCorrelation95ConfidenceLowerBounds.csv";
	private static double[][] correlationConfidenceUpperBounds;
	private static double[][] correlationConfidenceLowerBounds;
	
	/**
	 * Returns 95% confidence bounds from table generated by Morgan in Matlab.
	 * @param numCorrelated
	 * @param totalNumEvents
	 * @return 95% confidence bounds [ lower, upper ]
	 */
	public static synchronized double[] get95PercentConfidenceBounds(int numCorrelated, int totalNumEvents) {
		if (correlationConfidenceUpperBounds == null) {
			try {
				CSVFile<String> upperCSV = CSVFile.readStream(UCERF3_DataUtils.locateResourceAsStream(
						SUB_DIR_NAME, CONFIDENCE_BOUNDS_UPPER_FILE_NAME), true);
				CSVFile<String> lowerCSV = CSVFile.readStream(UCERF3_DataUtils.locateResourceAsStream(
						SUB_DIR_NAME, CONFIDENCE_BOUNDS_LOWER_FILE_NAME), true);
				Preconditions.checkState(upperCSV.getNumCols() == lowerCSV.getNumCols());
				Preconditions.checkState(upperCSV.getNumRows() == lowerCSV.getNumRows());
				int len = upperCSV.getNumCols()-1;
				correlationConfidenceUpperBounds = new double[len][len];
				correlationConfidenceLowerBounds = new double[len][len];
				for (int row=0; row<len; row++) {
					for (int col=0; col<len; col++) {
						correlationConfidenceUpperBounds[row][col] = Double.NaN;
						correlationConfidenceLowerBounds[row][col] = Double.NaN;
					}
				}
				for (int row=1; row<upperCSV.getNumRows(); row++) {
					for (int col=1; col<upperCSV.getNumCols(); col++) {
						correlationConfidenceUpperBounds[row-1][col-1] = Double.parseDouble(upperCSV.get(row, col));
						correlationConfidenceLowerBounds[row-1][col-1] = Double.parseDouble(lowerCSV.get(row, col));
					}
				}
					
			} catch (IOException e) {
				ExceptionUtils.throwAsRuntimeException(e);
			}
		}
		
		Preconditions.checkState(numCorrelated <= totalNumEvents, "can't have more correlated than total!");
		
		Preconditions.checkState(totalNumEvents < correlationConfidenceUpperBounds.length,
				"Table too small for given num events: "+totalNumEvents);
		
		double upper = correlationConfidenceUpperBounds[numCorrelated][totalNumEvents];
		double lower = correlationConfidenceLowerBounds[numCorrelated][totalNumEvents];
		double[] ret = { lower, upper };
		return ret;
	}
	
	public static PlotSpec getCorrelationPlotSpec(
			String faultName,
			Table<String, String,PaleoSiteCorrelationData> table,
			FaultSystemSolution sol)
			throws IOException {
		PaleoProbabilityModel paleoProb = UCERF3_PaleoProbabilityModel.load();
		return getCorrelationPlotSpec(faultName, table, sol, paleoProb);
	}
		
	public static PlotSpec getCorrelationPlotSpec(
			String faultName,
			Table<String, String,PaleoSiteCorrelationData> table,
			FaultSystemSolution sol,
			PaleoProbabilityModel paleoProb) {
		List<PaleoSiteCorrelationData> corrs = getCorrelataionsToPlot(table);
		List<double[]> solValues = Lists.newArrayList(); 
		for (PaleoSiteCorrelationData corr : corrs) {
			double myRate = getRateCorrelated(
					paleoProb, sol, corr.getSite1SubSect(), corr.getSite2SubSect());
			if (sol instanceof AverageFaultSystemSolution) {
				AverageFaultSystemSolution avgSol = (AverageFaultSystemSolution)sol;
				double minAvgRate = Double.MAX_VALUE;
				double maxAvgRate = 0d;
				for (FaultSystemSolution subSol : avgSol) {
					double avgRate = getRateCorrelated(
							paleoProb, subSol, corr.getSite1SubSect(), corr.getSite2SubSect());
					if (avgRate < minAvgRate)
						minAvgRate = avgRate;
					if (avgRate > maxAvgRate)
						maxAvgRate = avgRate;
				}
				double[] vals = { minAvgRate, maxAvgRate, myRate };
				solValues.add(vals);
			} else {
				double[] vals = { myRate };
				solValues.add(vals);
			}
		}
		
		return getCorrelationPlotSpec(faultName, corrs, solValues, paleoProb);
	}
	
	public static List<PaleoSiteCorrelationData> getCorrelataionsToPlot(
			Table<String, String,PaleoSiteCorrelationData> table) {
		
		HashSet<String> doneHash = new HashSet<String>();
		
		List<PaleoSiteCorrelationData> corrs = Lists.newArrayList();
		
		for (String name1 : table.rowKeySet()) {
			for (String name2 : table.columnKeySet()) {
				PaleoSiteCorrelationData corr = table.get(name1, name2);
				if (name1.equals(name2))
					continue;
				if (!corr.areNeighbors())
					continue;
				
				String backwardsHash = name2+"___"+name1;
				if (doneHash.contains(backwardsHash))
					// this means we've already done this set
					continue;
				
				String myHash = name1+"___"+name2;
				doneHash.add(myHash);
				
				corrs.add(corr);
			}
		}
		
		Collections.sort(corrs, new Comparator<PaleoSiteCorrelationData>() {
			@Override
			public int compare(PaleoSiteCorrelationData o1, PaleoSiteCorrelationData o2) {
				double lat1 = getNorthernmostLat(o1);
				double lat2 = getNorthernmostLat(o2);
				return -Double.compare(lat1, lat2);
			}
			
			private double getNorthernmostLat(PaleoSiteCorrelationData p) {
				double lat1 = p.getSite1Loc().getLatitude();
				double lat2 = p.getSite2Loc().getLatitude();
				if (lat1 > lat2)
					return lat1;
				return lat2;
			}
		});
		
		return corrs;
	}
	
	public static PlotSpec getCorrelationPlotSpec(
			String faultName,
			List<PaleoSiteCorrelationData> corrs,
			List<double[]> solValues,
			PaleoProbabilityModel paleoProb) {
		
		ArrayList<DiscretizedFunc> funcs = Lists.newArrayList();
		ArrayList<PlotCurveCharacterstics> chars = Lists.newArrayList();
		
		PlotCurveCharacterstics sepChar = new PlotCurveCharacterstics(PlotLineType.DASHED, 1f, Color.BLACK);
		PlotCurveCharacterstics dataChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.RED);
		PlotCurveCharacterstics dataBoundsChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.RED);
		PlotCurveCharacterstics solChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLUE);
		PlotCurveCharacterstics solAvgBoundsChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.BLUE);
		PlotCurveCharacterstics ucerf2Char = new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, new Color(130, 86, 5));
		
		Map<FaultModels, FaultSystemSolution> ucerf2Sols = Maps.newHashMap();
		
		double x = 0;
		
		for (int i=0; i<corrs.size(); i++) {
			PaleoSiteCorrelationData corr = corrs.get(i);

			if (x > 0) {
				// add spacer
				funcs.add(getVerticalLine(x, 0, 1));
				chars.add(sepChar);
			}

			double dataVal = (double)corr.getNumCorrelated() / (double)corr.getTotNumEvents();
			double[] bounds = get95PercentConfidenceBounds(corr.getNumCorrelated(), corr.getTotNumEvents());

			double dist = LocationUtils.horzDistance(corr.getSite1Loc(), corr.getSite2Loc());

			double newX = x+dist;
			String funcNamePrefix = "X: "+x+"=>"+newX+". Site "+corr.getSite1Name()+" to "+corr.getSite2Name();
			funcs.add(getHorizontalLine(dataVal, x, newX, funcNamePrefix+". Data: "+dataVal));
			chars.add(dataChar);
			funcs.add(getHorizontalLine(bounds[0], x, newX, funcNamePrefix+". Lower Bound: "+bounds[0]));
			chars.add(dataBoundsChar);
			funcs.add(getHorizontalLine(bounds[1], x, newX, funcNamePrefix+". Upper Bound: "+bounds[1]));
			chars.add(dataBoundsChar);

			if (solValues != null) {
				double[] rates = solValues.get(i);
				double myRate = rates[rates.length-1];
				funcs.add(getHorizontalLine(myRate, x, newX, funcNamePrefix+". Inversion: "+myRate));
				chars.add(solChar);
				if (rates.length > 1) {
					funcs.add(getHorizontalLine(rates[0], x, newX, funcNamePrefix+". Inversion Min: "+rates[0]));
					chars.add(solAvgBoundsChar);
					funcs.add(getHorizontalLine(rates[1], x, newX, funcNamePrefix+". Inversion Max: "+rates[1]));
					chars.add(solAvgBoundsChar);
				}
			}
			FaultModels fm = corr.getFaultModel();
			if (fm != null) {
				FaultSystemSolution ucerf2Sol = ucerf2Sols.get(fm);
				if (ucerf2Sol == null) {
					ucerf2Sol = UCERF2_ComparisonSolutionFetcher.getUCERF2Solution(fm);
					ucerf2Sols.put(fm, ucerf2Sol);
				}
				double ucerf2Rate = getRateCorrelated(
						paleoProb, ucerf2Sol, corr.getSite1SubSect(), corr.getSite2SubSect());
				funcs.add(getHorizontalLine(ucerf2Rate, x, newX, funcNamePrefix+". UCERF2: "+ucerf2Rate));
				chars.add(ucerf2Char);
			}

			x += dist;
		}
		
		// add spacer
		funcs.add(getVerticalLine(x, 0, 1));
		chars.add(sepChar);
		return new PlotSpec(funcs, chars, "Paleo Site Correlation ("+faultName+")",
				"Site Distance (width)", "Fraction Correlated");
	}
	
	private static ArbitrarilyDiscretizedFunc getVerticalLine(double x, double min, double max) {
		ArbitrarilyDiscretizedFunc func = new ArbitrarilyDiscretizedFunc();
		func.set(x, min);
		func.set(x+1e-6, max);
		func.setName("(separator)");
		return func;
	}
	
	private static ArbitrarilyDiscretizedFunc getHorizontalLine(double y, double min, double max) {
		return getHorizontalLine(y, min, max, null);
	}
	private static ArbitrarilyDiscretizedFunc getHorizontalLine(double y, double min, double max, String name) {
		ArbitrarilyDiscretizedFunc func = new ArbitrarilyDiscretizedFunc();
		func.set(min, y);
		func.set(max, y);
		if (name != null)
			func.setName(name);
		return func;
	}

}
