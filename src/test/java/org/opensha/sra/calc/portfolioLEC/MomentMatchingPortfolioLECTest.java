package org.opensha.sra.calc.portfolioLEC;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.awt.geom.Point2D;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.ListIterator;

import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.ss.usermodel.Row;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.geo.Location;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.util.DataUtils;
import org.opensha.sha.earthquake.ERFTestSubset;
import org.opensha.sha.earthquake.ERF;
import org.opensha.sha.earthquake.ProbEqkRupture;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.earthquake.rupForecastImpl.Frankel96.Frankel96_AdjustableEqkRupForecast;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.attenRelImpl.CB_2008_AttenRel;
import org.opensha.sra.asset.Asset;
import org.opensha.sra.asset.AssetCategory;
import org.opensha.sra.asset.MonetaryHighLowValue;
import org.opensha.sra.asset.MonetaryValue;
import org.opensha.sra.asset.Portfolio;
import org.opensha.sra.vulnerability.Vulnerability;
import org.opensha.sra.vulnerability.models.servlet.VulnerabilityServletAccessor;

/**
 * This test runs the Portfolio LEC calculator for the reference test with Keith Porter. It reads results from
 * the Excel file created by Keith Porter. The OpenSHA side of the file was filled in programatically in the
 * calculator for comparison with Keith's values.
 * 
 * @author Kevin
 *
 */
public class MomentMatchingPortfolioLECTest {
	
	public static final boolean WRITE_EXCEL_FILE = false;

	private static ERF erf;
	private static ScalarIMR imr;
	private static Portfolio portfolio;
	private static ArbitrarilyDiscretizedFunc function;
	
	private static boolean smallERF = true;
	
	private static HSSFSheet refResults;
	
	@BeforeClass
	public static void setUp() throws Exception {
		if (smallERF) {
			ERFTestSubset erf = new ERFTestSubset(new Frankel96_AdjustableEqkRupForecast());
			erf.updateForecast();
			erf.includeSource(281);
			erf.includeSource(22);
			erf.includeSource(48);
//			erf.includeSource(179);
//			erf.includeSource(63);
//			erf.includeSource(172);
			erf.includeSource(0);
//			erf.includeSource(1);
//			erf.includeSource(282);
//			erf.includeSource(181);
			erf.includeSource(51);
			MomentMatchingPortfolioLECTest.erf = erf;
		} else {
			Frankel96_AdjustableEqkRupForecast erf = new Frankel96_AdjustableEqkRupForecast();
			erf.updateForecast();
			MomentMatchingPortfolioLECTest.erf = erf;
		}

		
		int rupCount = 0;
		for (int sourceID=0; sourceID<erf.getNumSources(); sourceID++) {
			rupCount += erf.getNumRuptures(sourceID);
		}
		
		System.out.println("num sources: " + erf.getNumSources() + ", num rups: " + rupCount);
		
		imr = new CB_2008_AttenRel(null);
		imr.setParamDefaults();
		
		function = new ArbitrarilyDiscretizedFunc();
		for (int k=0; k<51; k++) {
			double x = Math.pow(10d, -5d + 0.1 * k);
			function.set(x, 0d);
		}
		
		portfolio = new Portfolio("Test Portfolio");
		
		VulnerabilityServletAccessor accessor = new VulnerabilityServletAccessor();
		
		MonetaryValue value1 = new MonetaryHighLowValue(220000.0, 330000.0, 110000.0, 2007);
		Site site1 = new Site(new Location(34, -118));
		Vulnerability vuln1 = accessor.getVuln("C1H-h-AGR1-DF");
		
		MonetaryValue value2 = new MonetaryHighLowValue(200000.0, 300000.0, 100000.0, 2004);
		Site site2 = new Site(new Location(34.1, -117.9));
		Vulnerability vuln2 = accessor.getVuln("C1H-h-COM10-DF");
		
		ListIterator<Parameter<?>> it = imr.getSiteParamsIterator();
		while (it.hasNext()) {
			Parameter<?> param = it.next();
			site1.addParameter((Parameter)param.clone());
			site2.addParameter((Parameter)param.clone());
		}
		
		Asset asset1 = new Asset(0, "House 1", AssetCategory.BUILDING, value1, vuln1, site1);
		portfolio.add(asset1);
		Asset asset2 = new Asset(1, "House 2", AssetCategory.BUILDING, value2, vuln2, site2);
		portfolio.add(asset2);
		
		// ********** REFERENCE RESULTS FROM KEITH'S SPREADSHEET **********
		File excelFile = new File(MomentMatchingPortfolioLECTest.class.getResource("output.xls").toURI());
		POIFSFileSystem fs = new POIFSFileSystem(new FileInputStream(excelFile));
		HSSFWorkbook wb = new HSSFWorkbook(fs);
		wb.setMissingCellPolicy(Row.CREATE_NULL_AS_BLANK);
		refResults = wb.getSheetAt(0);
	}
	
	private static final double tolerance_opensha = 0.1;
	// this is pretty high, but the important one is the opensha tolerance. the spreadsheet compares the final values
	private static final double tolerance_porter = 100.0;
	private static final double ndata = -9999.0;
	
	@Test
	public void testCalc() {
		MomentMatchingPortfolioLECCalculator calc = new MomentMatchingPortfolioLECCalculator();
		
		// TODO give real function
		PortfolioRuptureResults[][] rupResults = calc.calcRuptureResults(imr, erf, portfolio, function);
		
		int numRups = 0;
		for (PortfolioRuptureResults[] src : rupResults)
			for (PortfolioRuptureResults rup : src)
				numRups++;
		
		int openshaStartCol = 1;
		int porterStartCol = openshaStartCol+numRups+1;
		
		int realRupID = 0;
		int bottomRow = -1;
		for (int sourceID=0; sourceID<erf.getNumSources(); sourceID++) {
			ProbEqkSource source = erf.getSource(sourceID);
			for (int rupID=0; rupID<source.getNumRuptures(); rupID++) {
				ProbEqkRupture rup = source.getRupture(rupID);
				PortfolioRuptureResults rupResult = rupResults[sourceID][rupID];
				
				int row = 1;
				bottomRow = testRupResult(rup, rupResult, realRupID, row, openshaStartCol+realRupID, tolerance_opensha);
				
				bottomRow = testRupResult(rup, rupResult, realRupID, row, porterStartCol+realRupID, tolerance_porter);
				
				realRupID++;
			}
		}
		
		ArbitrarilyDiscretizedFunc curve = function.deepClone();
		// TODO not null
		calc.calcProbabilityOfExceedanceCurve(
				MomentMatchingPortfolioLECCalculator.RupResultsToFuncArray(rupResults), erf, curve);
		curve.setName("Final portfolio LEC (Equation 42)");
		
		int row = bottomRow + 1;
		checkFunc(row, 0, 1, curve, tolerance_opensha);
	}
	
	private static void assertWithinTol(String message, double expected, double actual, double tolPercent) {
		// adjust for incredibly small values
		if (expected < 1e-15)
			tolPercent *= 1000;
		else if (expected < 1e-13)
			tolPercent *= 500;
		else if (expected < 1e-10)
			tolPercent *= 250;
		else if (expected < 1e-8)
			tolPercent *= 10;
		double pDiff = DataUtils.getPercentDiff(actual, expected);
		if (message == null)
			message = "";
		message += "\nExpected: "+expected+"\tActual: "+actual+"\n% Diff: "+pDiff+"\tTolerance: "+tolPercent;
		assertTrue(message, pDiff <= tolPercent);
	} 
	
	private static double getNum(int row, int col) {
		return refResults.getRow(row).getCell(col).getNumericCellValue();
	}
	
	private static String getStr(int row, int col) {
		return refResults.getRow(row).getCell(col).getStringCellValue();
	}
	
	private int testRupResult(ProbEqkRupture rup, PortfolioRuptureResults rupResult,
			int realRupID, int row, int col, double tolPercent) {
		// check the source number
		int rupIDFromCSV = (int)getNum(row++, col);
		assertEquals("rup IDs messed up in CSV!", realRupID, rupIDFromCSV);
		
		// check probability in CSV
		double probFromCSV = getNum(row++, col);
		assertWithinTol("rup prob wrong in CSV!", rup.getProbability(), probFromCSV, tolPercent);
		
		// check assets
		for (AssetRuptureResult a : rupResult.getAssetRupResults()) {
			// emtpy row
			row++;
			
			double[] vals = {
					a.getMLnIML(),
					a.getInterSTD(),
					a.getIntraSTD(),
					a.getMedIML(),
					a.getIML_hInter(),
					a.getIML_lInter(),
					a.getIML_hIntra(),
					a.getIML_lIntra(),
					a.getMDamage_medIML(),
					a.getMDamage_hInter(),
					a.getMDamage_lInter(),
					a.getMDamage_hIntra(),
					a.getMDamage_lIntra(),
					a.getDeltaJ_medIML(),
					ndata,
					a.getDeltaJ_imlHighInter(),
					a.getDeltaJ_imlLowInter(),
					a.getDeltaJ_imlHighIntra(),
					a.getDeltaJ_imlLowIntra(),
					a.getMedDamage_medIML(),
					a.getHDamage_medIML(),
					a.getLDamage_medIML(),
					a.getMedDamage_hInter(),
					a.getMedDamage_lInter(),
					a.getMedDamage_hIntra(),
					a.getMedDamage_lIntra()
					};
			
			row = checkRows(row, 0, col, vals, tolPercent);
		}
		
		double[][] l_indv = rupResult.getL_indv();
		
		// check results for each asset
		for (int assetNum=0; assetNum<rupResult.getAssetRupResults().size(); assetNum++) {
			AssetRuptureResult a = rupResult.getAssetRupResults().get(assetNum);
			// emtpy row
			row++;
			
			ArrayList<Double> vals = new ArrayList<Double>();
			vals.add(a.getMValue());
			vals.add(a.getHValue());
			vals.add(a.getLValue());
			vals.add(a.getBetaVJ());
			vals.add(a.getMedValue());
			double[] l_for_asset = l_indv[assetNum];
			for (int i=0; i<l_for_asset.length; i++) {
				double l = l_for_asset[i];
				vals.add(l);
				vals.add(l*l);
			}
			vals.add(ndata);
			vals.add(ndata);
			
			double[] aVals = new double[vals.size()];
			for (int i=0; i<vals.size(); i++) {
				aVals[i] = vals.get(i);
			}
			
			row = checkRows(row, 0, col, aVals, tolPercent);
		}
		
		// portfolio
		// emtpy row
		row++;
		
		double[] myLs = new double[rupResult.getL().length*2+2];
		int cnt = 0;
		for (int i=0; i<rupResult.getL().length; i++) {
			myLs[cnt++] = rupResult.getL()[i];
			myLs[cnt++] = rupResult.getLSquared()[i];
		}
		myLs[cnt++] = ndata;
		myLs[cnt++] = ndata;
		
		row = checkRows(row, 0, col, myLs, tolPercent);
		
		// portfolio scenario loss
		// emtpy row
		row++;
		
		double[] vals = {
				rupResult.getW0(),
				rupResult.getWi(),
				rupResult.getE_LgivenS(),
				rupResult.getE_LSuqaredGivenS(),
				rupResult.getVarLgivenS(),
				rupResult.getDeltaSquaredSubLgivenS(),
				rupResult.getThetaSubLgivenS(),
				rupResult.getBetaSubLgivenS()
		};
		row = checkRows(row, 0, col, vals, tolPercent);
		
		// portfolio scenario LEC
		// emtpy row
		row++;
		
		DiscretizedFunc func = rupResult.getExceedanceProbs();
		func.setName("Portfolio SCENARIO LEC Equation 44");
		row = checkFunc(row, 0, col, func, tolPercent);
		
		// now skip the empty rows
		row++; // comment line
		row += func.size();
		
		return row;
	}
	
	private static int checkRows(int row, int nameCol, int valCol, double[] vals, double tolPercent) {
		for (double myVal : vals) {
			if ((float)myVal == (float)ndata) {
				row++;
				continue;
			}
			String valName = getStr(row, nameCol);
			double csvVal = getNum(row, valCol);
			
//			System.out.println("Checking '"+valName+"': "+csvVal+" (myVal: "+myVal+")");
			
			assertWithinTol(valName+" is wrong!", csvVal, myVal, tolPercent);
			
			row++;
		}
		
		return row;
	}
	
	private static int checkFunc(int row, int xCol, int yCol, DiscretizedFunc func, double tolPercent) {
		for (Point2D pt : func) {
			double myX = pt.getX();
			double myY = pt.getY();
			double csvX = getNum(row, xCol);
			double csvY = getNum(row, yCol);
			
			assertTrue("X values not equal for func: "+func.getName(), (float)myX == (float)csvX);
			assertWithinTol("Y value wrong for x="+csvX+" of: "+func.getName(), csvY, myY, tolPercent);
			
			row++;
		}
		return row;
	}
	
//	public static void write

}
