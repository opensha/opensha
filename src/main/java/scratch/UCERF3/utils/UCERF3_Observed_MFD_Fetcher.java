package scratch.UCERF3.utils;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;

import org.apache.commons.lang3.StringUtils;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.region.CaliforniaRegions;
import org.opensha.commons.geo.Region;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSymbol;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.UCERF2;
import org.opensha.commons.gui.plot.GraphWindow;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;
import org.opensha.sha.magdist.IncrementalMagFreqDist;


/**
 * The reads Karen's MFD files for All Ca, No. Ca., So. Ca., LA box and SF box and provides 
 * target MFD_InversionConstraints for these regions.
 * 
 * To Do:
 * 
 * 1) solve the following (I sent email to Karen):
 * 
 * 			LongTermModelFull.txt is identical to LongTermModelNoCal.txt
 * 			DirectCountsNoCal.txt is identical to DirectCountsSoCal.txt
 * 
 * 2) add getters and setters for the various MFDs.
 * 3) more tests (beyond those in the main method here)?
 * 
 * @author field
 *
 */
public class UCERF3_Observed_MFD_Fetcher {
	
	final static boolean D=true;
	
	private final static String SUB_DIR_NAME = "mfdData";
	
	// discretization params for Karen's MFD files:
	final static double MIN_MAG=4.75;
	final static double DELTA_MAG = 0.5;
	
	IncrementalMagFreqDist directCountsFull, directCountsLA, directCountsNoCal, directCountsSF, directCountsSoCal;
	IncrementalMagFreqDist directCountsFull_Lower95, directCountsLA_Lower95, directCountsNoCal_Lower95, directCountsSF_Lower95, directCountsSoCal_Lower95;
	IncrementalMagFreqDist directCountsFull_Upper95, directCountsLA_Upper95, directCountsNoCal_Upper95, directCountsSF_Upper95, directCountsSoCal_Upper95;

	
	public enum Area {

		ALL_CA,
		NO_CA,
		SO_CA,
		LA_BOX,
		SF_BOX
		
		}
	
	public UCERF3_Observed_MFD_Fetcher() {
		
		readAllData();

	}
	
	private void readAllData() {
		// Read all the files
		ArrayList<IncrementalMagFreqDist> mfds;

		mfds = readMFD_DataFromFile("DirectCountWholeState.txt");
		directCountsFull = mfds.get(0);
		directCountsFull_Lower95 = mfds.get(1);
		directCountsFull_Upper95 = mfds.get(2);
		mfds = readMFD_DataFromFile("DirectCountLA.txt");
		directCountsLA = mfds.get(0);
		directCountsLA_Lower95 = mfds.get(1);
		directCountsLA_Upper95 = mfds.get(2);
		mfds = readMFD_DataFromFile("DirectCountNoCal.txt");
		directCountsNoCal = mfds.get(0);
		directCountsNoCal_Lower95 = mfds.get(1);
		directCountsNoCal_Upper95 = mfds.get(2);
		mfds = readMFD_DataFromFile("DirectCountSF.txt");
		directCountsSF = mfds.get(0);
		directCountsSF_Lower95 = mfds.get(1);
		directCountsSF_Upper95 = mfds.get(2);
		mfds = readMFD_DataFromFile("DirectCountSoCal.txt");
		directCountsSoCal = mfds.get(0);
		directCountsSoCal_Lower95 = mfds.get(1);
		directCountsSoCal_Upper95 = mfds.get(2);

	}
	
	
	/**
	 * This reads the three cum MFDs from one of Karen's files
	 * @param fileName
	 * @return
	 */
	private static ArrayList<IncrementalMagFreqDist> readMFD_DataFromFile(String fileName) {
		
		// make an array of lines
		ArrayList<String> lineList = new ArrayList<String>();
		try {
			BufferedReader reader = new BufferedReader(UCERF3_DataUtils.getReader(SUB_DIR_NAME, fileName));
			String line;
			while ((line = reader.readLine()) != null) {
				lineList.add(line);
			}
		} catch (Exception e) {
			ExceptionUtils.throwAsRuntimeException(e);
		}
		
		int numMag = lineList.size();
		
		IncrementalMagFreqDist mfdMean = new IncrementalMagFreqDist(MIN_MAG,numMag,DELTA_MAG);
		IncrementalMagFreqDist mfdLower95Conf = new IncrementalMagFreqDist(MIN_MAG,numMag,DELTA_MAG);
		IncrementalMagFreqDist mfdUpper95Conf = new IncrementalMagFreqDist(MIN_MAG,numMag,DELTA_MAG);
		
			int l=0;
			for (String line : lineList) {
				System.out.println(line);
				String[] st = StringUtils.split(line," ");
				double magTest = MIN_MAG+l*DELTA_MAG;
				double mag = (Double.valueOf(st[0])+Double.valueOf(st[1]))/2;
				if(mag >= magTest+0.001 || mag <= magTest-0.001)
					throw new RuntimeException("mags are unequal: "+mag+"\t"+magTest);
				mfdMean.set(mag, Double.valueOf(st[2]));
				if(st.length > 3) {
					mfdLower95Conf.set(mag, Double.valueOf(st[3]));
					mfdUpper95Conf.set(mag, Double.valueOf(st[4]));					
				}
				l+=1;
			}
		
		mfdMean.setName("Mean MFD from "+fileName);
		mfdLower95Conf.setName("Lower 95th Conf MFD from "+fileName);
		mfdUpper95Conf.setName("Upper 95th Conf MFD from "+fileName);
		mfdMean.setInfo(" ");
		mfdLower95Conf.setInfo(" ");
		mfdUpper95Conf.setInfo(" ");

		ArrayList<IncrementalMagFreqDist> mfds = new ArrayList<IncrementalMagFreqDist>();
		mfds.add(mfdMean);
		mfds.add(mfdLower95Conf);
		mfds.add(mfdUpper95Conf);
		
		return mfds;
	}
	
	
	
	/**
	 * This plots the computed MFDs
	 */
	public void plotMFDs() {
		
		ArrayList<PlotCurveCharacterstics> plotChars = new ArrayList<PlotCurveCharacterstics>();
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.GREEN));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLUE));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 2f, Color.BLUE));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 2f, Color.BLUE));
		
		// No Cal Plot
		ArrayList<EvenlyDiscretizedFunc> funcs2 = new ArrayList<EvenlyDiscretizedFunc>();
		funcs2.add(directCountsNoCal);
		funcs2.add(directCountsNoCal_Lower95);
		funcs2.add(directCountsNoCal_Upper95);
		GraphWindow graph2 = new GraphWindow(funcs2, "No Cal Mag-Freq Dists", plotChars); 
		graph2.setX_AxisLabel("Mag");
		graph2.setY_AxisLabel("Rate");
		graph2.setY_AxisRange(1e-3, 500);
		graph2.setX_AxisRange(3.5, 8.0);
		graph2.setYLog(true);

		// So Cal Plot
		ArrayList<EvenlyDiscretizedFunc> funcs3 = new ArrayList<EvenlyDiscretizedFunc>();
		funcs3.add(directCountsSoCal);
		funcs3.add(directCountsSoCal_Lower95);
		funcs3.add(directCountsSoCal_Upper95);
		GraphWindow graph3 = new GraphWindow(funcs3, "So Cal Mag-Freq Dists", plotChars); 
		graph3.setX_AxisLabel("Mag");
		graph3.setY_AxisLabel("Rate");
		graph3.setY_AxisRange(1e-3, 500);
		graph3.setX_AxisRange(3.5, 8.0);
		graph3.setYLog(true);

		// All Cal Plot
		ArrayList<EvenlyDiscretizedFunc> funcs = new ArrayList<EvenlyDiscretizedFunc>();
		funcs.add(directCountsFull);
		funcs.add(directCountsFull_Lower95);
		funcs.add(directCountsFull_Upper95);
		
		// add UCERF2 obs MFDs
		funcs.addAll(UCERF2.getObsCumMFD(true));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.RED));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 2f, Color.RED));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 2f, Color.RED));
		
		GraphWindow graph = new GraphWindow(funcs, "All Cal Cum Mag-Freq Dists", plotChars); 
		graph.setX_AxisLabel("Mag");
		graph.setY_AxisLabel("Rate");
		graph.setY_AxisRange(1e-3, 500);
		graph.setX_AxisRange(3.5, 8.0);
		graph.setYLog(true);
		
		// LA Box
		ArrayList<EvenlyDiscretizedFunc> funcs5 = new ArrayList<EvenlyDiscretizedFunc>();
		funcs5.add(directCountsLA);
		funcs5.add(directCountsLA_Lower95);
		funcs5.add(directCountsLA_Upper95);
		GraphWindow graph4 = new GraphWindow(funcs5, "LA Box MFD", plotChars); 
		graph4.setX_AxisLabel("Mag");
		graph4.setY_AxisLabel("Rate");
		graph4.setY_AxisRange(1e-3, 500);
		graph4.setX_AxisRange(3.5, 8.0);
		graph4.setYLog(true);
		
		// SF Box
		ArrayList<EvenlyDiscretizedFunc> funcs6 = new ArrayList<EvenlyDiscretizedFunc>();
		funcs6.add(directCountsSF);
		funcs6.add(directCountsSF_Lower95);
		funcs6.add(directCountsSF_Upper95);
		GraphWindow graph5 = new GraphWindow(funcs6, "SF Box MFD", plotChars); 
		graph5.setX_AxisLabel("Mag");
		graph5.setY_AxisLabel("Rate");
		graph5.setY_AxisRange(1e-3, 500);
		graph5.setX_AxisRange(3.5, 8.0);
		graph5.setYLog(true);
	

	}
	
	/**
	 * This plots the computed MFDs
	 */
	public void plotCumMFDs() {
		
		ArrayList<PlotCurveCharacterstics> plotChars = new ArrayList<PlotCurveCharacterstics>();
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.GREEN));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLUE));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 2f, Color.BLUE));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 2f, Color.BLUE));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLACK));
		
		// No Cal Plot
		ArrayList<EvenlyDiscretizedFunc> funcs2 = new ArrayList<EvenlyDiscretizedFunc>();
		funcs2.add(directCountsNoCal.getCumRateDistWithOffset());
		funcs2.add(directCountsNoCal_Lower95.getCumRateDistWithOffset());
		funcs2.add(directCountsNoCal_Upper95.getCumRateDistWithOffset());
		GraphWindow graph2 = new GraphWindow(funcs2, "No Cal Mag-Freq Dists", plotChars); 
		graph2.setX_AxisLabel("Mag");
		graph2.setY_AxisLabel("Rate");
		graph2.setY_AxisRange(1e-3, 500);
		graph2.setX_AxisRange(3.5, 8.0);
		graph2.setYLog(true);

		// No Cal Plot
		ArrayList<EvenlyDiscretizedFunc> funcs3 = new ArrayList<EvenlyDiscretizedFunc>();
		funcs3.add(directCountsSoCal.getCumRateDistWithOffset());
		funcs3.add(directCountsSoCal_Lower95.getCumRateDistWithOffset());
		funcs3.add(directCountsSoCal_Upper95.getCumRateDistWithOffset());
		GraphWindow graph3 = new GraphWindow(funcs3, "So Cal Mag-Freq Dists", plotChars); 
		graph3.setX_AxisLabel("Mag");
		graph3.setY_AxisLabel("Rate");
		graph3.setY_AxisRange(1e-3, 500);
		graph3.setX_AxisRange(3.5, 8.0);
		graph3.setYLog(true);

		// All Cal Plot
		ArrayList<EvenlyDiscretizedFunc> funcs = new ArrayList<EvenlyDiscretizedFunc>();
		funcs.add(directCountsFull.getCumRateDistWithOffset());
		funcs.add(directCountsFull_Lower95.getCumRateDistWithOffset());
		funcs.add(directCountsFull_Upper95.getCumRateDistWithOffset());
		
		// add UCERF2 obs MFDs
		funcs.addAll(UCERF2.getObsCumMFD(true));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.RED));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 2f, Color.RED));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 2f, Color.RED));
		
		GraphWindow graph = new GraphWindow(funcs, "All Cal Cum Mag-Freq Dists", plotChars); 
		graph.setX_AxisLabel("Mag");
		graph.setY_AxisLabel("Rate");
		graph.setY_AxisRange(1e-3, 500);
		graph.setX_AxisRange(3.5, 8.0);
		graph.setYLog(true);
		
		
		// LA Box
		ArrayList<EvenlyDiscretizedFunc> funcs5 = new ArrayList<EvenlyDiscretizedFunc>();
		funcs5.add(directCountsLA.getCumRateDistWithOffset());
		funcs5.add(directCountsLA_Lower95.getCumRateDistWithOffset());
		funcs5.add(directCountsLA_Upper95.getCumRateDistWithOffset());
		GraphWindow graph4 = new GraphWindow(funcs5, "LA Box Cum MFD", plotChars); 
		graph4.setX_AxisLabel("Mag");
		graph4.setY_AxisLabel("Rate");
		graph4.setY_AxisRange(1e-3, 500);
		graph4.setX_AxisRange(3.5, 8.0);
		graph4.setYLog(true);
		
		// SF Box
		ArrayList<EvenlyDiscretizedFunc> funcs6 = new ArrayList<EvenlyDiscretizedFunc>();
		funcs6.add(directCountsSF.getCumRateDistWithOffset());
		funcs6.add(directCountsSF_Lower95.getCumRateDistWithOffset());
		funcs6.add(directCountsSF_Upper95.getCumRateDistWithOffset());
		GraphWindow graph5 = new GraphWindow(funcs6, "SF Box Cum MFD", plotChars); 
		graph5.setX_AxisLabel("Mag");
		graph5.setY_AxisLabel("Rate");
		graph5.setY_AxisRange(1e-3, 500);
		graph5.setX_AxisRange(3.5, 8.0);
		graph5.setYLog(true);

		
	}

	
	
	
	/**
	 * This plots the computed MFDs
	 */
	public void plotDeclusteredSFandLS_BoxMFDs() {
		
		ArrayList<PlotCurveCharacterstics> plotChars = new ArrayList<PlotCurveCharacterstics>();
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLACK));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 2f, Color.BLUE));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 2f, Color.BLUE));

		// apply aftershock filter
		for(int i=0; i<directCountsLA.size();i++) {
			double fract = GardnerKnopoffAftershockFilter.scaleForMagnitude(directCountsLA.getX(i));
			directCountsLA.set(i,fract*directCountsLA.getY(i));
			directCountsLA_Lower95.set(i,fract*directCountsLA_Lower95.getY(i));
			directCountsLA_Upper95.set(i,fract*directCountsLA_Upper95.getY(i));
			directCountsSF.set(i,fract*directCountsSF.getY(i));
			directCountsSF_Lower95.set(i,fract*directCountsSF_Lower95.getY(i));
			directCountsSF_Upper95.set(i,fract*directCountsSF_Upper95.getY(i));

		}

		// LA Box
		ArrayList<EvenlyDiscretizedFunc> funcs5 = new ArrayList<EvenlyDiscretizedFunc>();
		funcs5.add(directCountsLA.getCumRateDistWithOffset());
		funcs5.add(directCountsLA_Lower95.getCumRateDistWithOffset());
		funcs5.add(directCountsLA_Upper95.getCumRateDistWithOffset());
		GraphWindow graph4 = new GraphWindow(funcs5, "LA Box Cum MFD", plotChars); 
		graph4.setX_AxisLabel("Magnitude");
		graph4.setY_AxisLabel("Rate (per year)");
		graph4.setPlotLabelFontSize(18);
		graph4.setAxisLabelFontSize(18);
		graph4.setTickLabelFontSize(16);
		graph4.setX_AxisRange(5, 9);
		graph4.setY_AxisRange(1e-4, 1);
		graph4.setYLog(true);
		
		try {
			graph4.saveAsPDF("FelzerLA_BoxDeclustered.pdf");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// SF Box
		ArrayList<EvenlyDiscretizedFunc> funcs6 = new ArrayList<EvenlyDiscretizedFunc>();
		funcs6.add(directCountsSF.getCumRateDistWithOffset());
		funcs6.add(directCountsSF_Lower95.getCumRateDistWithOffset());
		funcs6.add(directCountsSF_Upper95.getCumRateDistWithOffset());
		GraphWindow graph5 = new GraphWindow(funcs6, "SF Box Cum MFD", plotChars); 
		graph5.setX_AxisLabel("Magnitude");
		graph5.setY_AxisLabel("Rate (per year)");
		graph5.setPlotLabelFontSize(18);
		graph5.setAxisLabelFontSize(18);
		graph5.setTickLabelFontSize(16);
		graph5.setX_AxisRange(5, 9);
		graph5.setY_AxisRange(1e-4, 1);
		graph5.setYLog(true);
		
		try {
			graph5.saveAsPDF("FelzerSF_BoxDeclustered.pdf");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}


		// rebuild data
		readAllData();

		
	}

	
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		
//		System.out.println(getGarderKnoppoffFractAftershocksMDF());
	
		UCERF3_Observed_MFD_Fetcher test = new UCERF3_Observed_MFD_Fetcher();		
//		test.plotCumMFDs();
		test.plotDeclusteredSFandLS_BoxMFDs();
	}

	
}
