package org.opensha.sra.calc;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.geo.Location;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.util.DataUtils;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.sha.earthquake.ERF;
import org.opensha.sha.earthquake.rupForecastImpl.Frankel96.Frankel96_AdjustableEqkRupForecast;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.attenRelImpl.CB_2008_AttenRel;
import org.opensha.sra.gui.portfolioeal.Asset;
import org.opensha.sra.gui.portfolioeal.CalculationExceptionHandler;
import org.opensha.sra.gui.portfolioeal.PortfolioParser;

import com.google.common.collect.Lists;

public class EAL_IndependentCalcTest {
	
	private static ArbitrarilyDiscretizedFunc magThreshFunc;
	private static double distance = 200d;
	
	private static ERF erf;
	private static ScalarIMR imr;
	private static Site site;
	
	private static File assetFile;
	private static List<Asset> assets;
	
	private static CalculationExceptionHandler handler;
	
	private static final boolean full_portfolio_test = true;
	
	@BeforeClass
	public static void setUpBeforeClass() throws IOException {
		magThreshFunc = new ArbitrarilyDiscretizedFunc();
		magThreshFunc.set(0d, 0d);
		magThreshFunc.set(distance, 0d);
		
		erf = new Frankel96_AdjustableEqkRupForecast();
		erf.updateForecast();
		
		imr = new CB_2008_AttenRel(null);
		imr.setParamDefaults();
		
		// initialize the site with IMR params. location will be overridden.
		site = new Site(new Location(34, -118));
		for (Parameter<?> param : imr.getSiteParams())
			site.addParameter((Parameter)param.clone());
		
		assetFile = File.createTempFile("openSHA", "_portfolio.csv");
		FileWriter fw = new FileWriter(assetFile);
		
		fw.write("AssetGroupName,AssetID,AssetName,BaseHt,Ded,LimitLiab,Share,SiteName,Elev,Lat,Lon,Soil,ValHi,ValLo,Value,VulnModel,Vs30\n");
		
		fw.write("CEAProxyPof,1325,6001400300,0,0,0,1,6001400300,0,37.8406,-122.2548,C,"
				+ "683797.2134,66777.07163,213686.6292,W1-m-RES1-DF,387\n");
		fw.write("CEAProxyPof,5167,6001401100,0,0,0,1,6001401100,0,37.8306,-122.2643,D,"
				+ "184237.8912,17991.98156,57574.341,W1-m-RES1-DF,387\n");
		fw.write("CEAProxyPof,32736,6001406900,0,0,0,1,6001406900,0,37.793,-122.1903,C,"
				+ "291839.2013,28499.922,91199.7504,W1-h-RES1-DF,609\n");
		fw.write("CEAProxyPof,75732,6001430200,0,0,0,1,6001430200,0,37.7213,-122.0614,C,"
				+ "1446387.149,141248.745,451995.984,W1-m-RES1-DF,540\n");
		fw.write("CEAProxyPof,141183,6001451101,0,0,0,1,6001451101,0,37.6372,-121.6367,C,"
				+ "502224.4339,49045.35488,156945.1356,W1-h-RES1-DF,387\n");
		
//		fw.write("CEA proxy portfolio,348,06001400100,0,0,0,1,06001400100,0,37.8673,-122.2316,D"
//				+ ",411780.8218,40212.97088,128681.5068,W1-HC-RES1,300\n");
//		fw.write("CEA proxy portfolio,3226,06001400700,0,0,0,1,06001400700,0,37.8419,-122.2726,D"
//				+ ",150516.7488,14698.90125,47036.484,W1-HC-RES1,300\n");
//		fw.write("CEA proxy portfolio,6144,06001401300,0,0,0,1,06001401300,0,37.8182,-122.2672,D"
//				+ ",12900.66624,1259.830688,4031.4582,W1-HC-RES1,300\n");
//		fw.write("CEA proxy portfolio,350499,06023011200,0,0,0,1,06023011200,0,40.3826,-124.2353"
//				+ ",D,623715.84,60909.75,194911.2,W1-MC-RES1,300\n");
		
		fw.close();
		PortfolioParser parser = new PortfolioParser();
		if (full_portfolio_test)
			assets = parser.scanFile( new File("/tmp/Porter-28-Mar-2012-CEA-proxy-pof.txt") );
		else
			assets = parser.scanFile( assetFile );
		
		handler = new CalculationExceptionHandler() {
			
			@Override
			public void calculationException(String errorMessage) {
				fail(errorMessage);
			}
		};
	}
	
	@AfterClass
	public static void cleanUp() {
		assetFile.delete();
	}

	@Test
	public void test() {
		MinMaxAveTracker track = new MinMaxAveTracker();
		MinMaxAveTracker noAbsTrack = new MinMaxAveTracker();
		for (int i=0; i<assets.size(); i++) {
			if (assets.size() > 100 && i % 100 == 0) {
				System.out.println("Calculating asset "+i+"/"+assets.size()+" ("+(100f*((float)i/assets.size()))+" %)");
				System.out.println(noAbsTrack);
				System.out.println(track);
			}
			Asset asset = assets.get(i);
			double eal = asset.calculateEAL(imr, distance, site, erf, handler);
			double[][] vals = asset.calculateExpectedLossPerRup(imr, magThreshFunc, site, erf, handler);
			int numNonZeroRups = 0;
			int numNonZeroSources = 0;
			for (double[] srcVals : vals) {
				if (srcVals != null) {
					numNonZeroSources++;
					for (double v : srcVals) {
						if (v > 0)
							numNonZeroRups++;
					}
				}
			}
			// this is calculated by the above call
			double calcEAL = asset.getAssetEAL();
			double pDiff = DataUtils.getPercentDiff(calcEAL, eal);
			double pDiffNoAbs = (calcEAL - eal) / eal * 100d;
			noAbsTrack.addValue(pDiffNoAbs);
			track.addValue(pDiff);
			// test to within 0.1 %
			String message = "Asset "+i+". Acutal: "+eal+", sep calc: "+calcEAL;
			message += "\nnon zero sources: "+numNonZeroSources+", rups: "+numNonZeroRups+"\n";
			if (!full_portfolio_test)
				assertEquals(message, 0d, pDiff, 0.5);
		}
		System.out.println("Tracker: "+track);
	}

}
