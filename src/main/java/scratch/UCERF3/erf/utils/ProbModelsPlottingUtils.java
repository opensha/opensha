/**
 * 
 */
package scratch.UCERF3.erf.utils;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import org.jfree.chart.axis.NumberAxis;
import org.jfree.data.Range;
import org.opensha.commons.data.function.ArbDiscrEmpiricalDistFunc_3D;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.HistogramFunction;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.gui.plot.GeographicMapMaker;
import org.opensha.commons.gui.plot.GraphWindow;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.gui.plot.PlotSymbol;
import org.opensha.sha.earthquake.calc.recurInterval.BPT_DistCalc;
import org.opensha.sha.earthquake.calc.recurInterval.LognormalDistCalc;
import org.opensha.sha.earthquake.calc.recurInterval.WeibullDistCalc;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

/**
 * @author field
 *
 */
public class ProbModelsPlottingUtils {
	
	
	final static boolean integerYaxisTickLabeIncrements = false;

	
	/**
	 * This utility returns a histogram of normalized recurrence intervals from normRI_List, 
	 * along with best-fit BPT, Lognormal, and Weibull distributions.  
	 * @param normRI_List
	 * @param plotTitle
	 * @param bptAperForComparison - this will add a BPT dist with mean=1 and given aper for comparison (set as Double.NaN if not desired)
	 */
	public static ArrayList<EvenlyDiscretizedFunc> getNormRI_DistributionWithFits(ArrayList<Double> normRI_List, double bptAperForComparison) {
		
		// get the normalized RI dist
		double delta=0.1;
		HistogramFunction dist = getNormRI_Distribution(normRI_List, delta);
		
		ArrayList<EvenlyDiscretizedFunc> funcList = new ArrayList<EvenlyDiscretizedFunc>();
		funcList.add(dist);
		
		// now make the list of best-fit functions for the plot
		funcList.addAll(getRenewalModelFunctionFitsToDist(dist));
		
		// fill in name and Info for obs dist:
		double obsAboveFive=0;
		int firstIndex = dist.getClosestXIndex(5.0);
		for(int i=firstIndex;i<dist.size();i++)
			obsAboveFive += dist.getY(i);
		
		double bptAboveFive=0;
		EvenlyDiscretizedFunc bptDist = funcList.get(1);
		firstIndex = bptDist.getClosestXIndex(5.0);
		for(int i=firstIndex;i<bptDist.size();i++)
			bptAboveFive += bptDist.getY(i);

		dist.setName("Recur. Int. Dist");
		String info = "NumObs, ObsMean, ObsCOV, ObsOverBPTAboveFive, bestFitBPTmean, bestFitBPTaper\n"+ normRI_List.size()+ ", "+
				(float)dist.computeMean()+", "+(float)dist.computeCOV()+", "+(float)(obsAboveFive/bptAboveFive);
		info += ", "+bptDist.getInfo().split("\n")[1];
		dist.setInfo(info);

		
		if(!Double.isNaN(bptAperForComparison)) {
			BPT_DistCalc bpt_calc = new BPT_DistCalc();
			bpt_calc.setAll(1.0, bptAperForComparison, funcList.get(1).getDelta(), funcList.get(1).size());	// not the first one because that's the obs histogram
			EvenlyDiscretizedFunc bpt_func = bpt_calc.getPDF();
			bpt_func.setName("BPT Dist for comparison");
			bpt_func.setInfo("(mean="+1.0+", aper="+bptAperForComparison+")");
			funcList.add(bpt_func);
		}

		
		return funcList;
						
		
	}
	
	
	
	public static ArrayList<EvenlyDiscretizedFunc> addBPT_Fit(HistogramFunction dist) {
		
		ArrayList<EvenlyDiscretizedFunc> funcList = new ArrayList<EvenlyDiscretizedFunc>();
		funcList.add(dist);
		
		// now make the list of best-fit functions for the plot
		funcList.add(getRenewalModelFunctionFitsToDist(dist).get(0));	// just the first one
		
		// fill in name and Info for obs dist:
		double obsAboveFive=0;
		int firstIndex = dist.getClosestXIndex(5.0);
		for(int i=firstIndex;i<dist.size();i++)
			obsAboveFive += dist.getY(i);
		
		double bptAboveFive=0;
		EvenlyDiscretizedFunc bptDist = funcList.get(1);
		firstIndex = bptDist.getClosestXIndex(5.0);
		for(int i=firstIndex;i<bptDist.size();i++)
			bptAboveFive += bptDist.getY(i);

		dist.setName("Recur. Int. Dist");
		String info = "ObsMean\tObsCOV\tObsOverBPTAboveFive\n"+ (float)dist.computeMean()+"\t"+(float)dist.computeCOV()+"\t"+(float)(obsAboveFive/bptAboveFive);
		dist.setInfo(info);
		
		return funcList;
						
		
	}

	
	
	/**
	 * This is an old method that pops up a window and returns the GraphWindow for saving elsewhere
	 * This utility plots the histogram of normalized recurrence intervals from normRI_List, 
	 * along with best-fit BPT, Lognormal, and Weibull distributions.  
	 * @param normRI_List
	 * @param plotTitle
	 */
	public static GraphWindow plotNormRI_DistributionWithFits(ArrayList<EvenlyDiscretizedFunc> funcList, String plotTitle) {
		
						
		ArrayList<PlotCurveCharacterstics> curveCharacteristics = new ArrayList<PlotCurveCharacterstics>();
		curveCharacteristics.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 2f, Color.RED));
		curveCharacteristics.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLACK));
		curveCharacteristics.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLUE));
		curveCharacteristics.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.GREEN));
		
		if(funcList.size()==5) {
			curveCharacteristics.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.GRAY));
		}
		
		// make plot
		GraphWindow graph = new GraphWindow(funcList, plotTitle, curveCharacteristics); 
		graph.setX_AxisLabel("RI (yrs)");
		graph.setY_AxisLabel("Density");
		graph.setX_AxisRange(0, 5);
		graph.setY_AxisRange(0, 5);
		graph.setAxisLabelFontSize(22);
		graph.setTickLabelFontSize(20);
		graph.setPlotLabelFontSize(22);
		return graph;
		
	}
	
	public static void writeMFD_ComprisonPlot(IncrementalMagFreqDist targetMFD, IncrementalMagFreqDist simMFD, File outputDir) {
		double maxX = Math.ceil(targetMFD.getMaxMagWithNonZeroRate()/0.5)*0.5;
		double minX = Math.floor(targetMFD.getMinMagWithNonZeroRate()/0.5)*0.5;
		ArrayList<XY_DataSet> funcs = new ArrayList<XY_DataSet>();
		funcs.add(targetMFD);
		funcs.add(simMFD);
		funcs.add(targetMFD.getCumRateDistWithOffset());
		funcs.add(simMFD.getCumRateDistWithOffset());
		ArrayList<PlotCurveCharacterstics> plotChars = new ArrayList<PlotCurveCharacterstics>();
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.DOTTED, 1f, Color.BLACK));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.DOTTED, 1f, Color.RED));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.BLACK));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.RED));
		String plotName = "MFD Comparsion";
		String xAxisLabel = "Magnitude";
		String yAxisLabel = "Rate (per year)";
		Range xAxisRange = new Range(minX,maxX);
		Range yAxisRange = new Range(1e-5,1);
		boolean logX = false;
		boolean logY = true;
		double widthInches = 7; // inches
		double heightInches = 6; // inches
		String fileNamePrefix = "magFreqDists";
		boolean popupWindow = false;
		writeAndOrPlotFuncs(funcs,plotChars,plotName,xAxisLabel,yAxisLabel,xAxisRange,yAxisRange,
				logX,logY,widthInches,heightInches, new File(outputDir,fileNamePrefix), popupWindow);
	}
	
	
	
	public static String writeNormalizedDistPlotWithFits(ArrayList<Double> normRI_List, double bptAperForComparison, 
			File outputDir, String plotTitle, String fileNamePrefix) {
		
		ArrayList<EvenlyDiscretizedFunc> funcList = ProbModelsPlottingUtils.getNormRI_DistributionWithFits(normRI_List, bptAperForComparison);

		ArrayList<XY_DataSet> funcs = new ArrayList<XY_DataSet>();
		funcs.addAll(funcList);
//		GraphWindow grapha_a = ProbModelsPlottingUtils.plotNormRI_DistributionWithFits(funcList, "Normalized Rupture RIs; "+plotLabelString);
//
		String infoString = "\n\nNorm "+funcList.get(0).getName()+" for "+fileNamePrefix+":\n";
		infoString += "\n"+funcList.get(0).getInfo();
//		infoString += "\n\n"+funcList.get(1).getName(); // this BPT fit is included right above
//		infoString += "\n"+funcList.get(1).getInfo();
		
		ArrayList<PlotCurveCharacterstics> plotChars = new ArrayList<PlotCurveCharacterstics>();
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 2f, Color.RED));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.BLACK));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.BLUE));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.GREEN));
		if(funcList.size()==5) {
			plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.GRAY));
		}
		
		String xAxisLabel = "RI (yrs)";
		String yAxisLabel = "Density";
		Range xAxisRange = new Range(0, 5);
		Range yAxisRange = new Range(0, 5);
		boolean logX = false;
		boolean logY = false;
		double widthInches = 7; // inches
		double heightInches = 6; // inches
		
		boolean popupWindow = false;
		writeAndOrPlotFuncs(funcs,plotChars,plotTitle,xAxisLabel,yAxisLabel,xAxisRange,yAxisRange,
				logX,logY,widthInches,heightInches, new File(outputDir,fileNamePrefix), popupWindow);

		return infoString;
	}
	
	public static void writeNormalizedDistPlotWithFits(ArrayList<EvenlyDiscretizedFunc> funcList, 
			File outputDir, String plotTitle, String fileNamePrefix) {
		
		ArrayList<XY_DataSet> funcs = new ArrayList<XY_DataSet>();
		funcs.addAll(funcList);
		
		ArrayList<PlotCurveCharacterstics> plotChars = new ArrayList<PlotCurveCharacterstics>();
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 2f, Color.RED));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.BLACK));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.BLUE));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.GREEN));
		if(funcList.size()==5) {
			plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.GRAY));
		}
		
		String xAxisLabel = "RI (yrs)";
		String yAxisLabel = "Density";
		Range xAxisRange = new Range(0, 5);
		Range yAxisRange = new Range(0, 5);
		boolean logX = false;
		boolean logY = false;
		double widthInches = 7; // inches
		double heightInches = 6; // inches
		
		boolean popupWindow = false;
		writeAndOrPlotFuncs(funcs,plotChars,plotTitle,xAxisLabel,yAxisLabel,xAxisRange,yAxisRange,
				logX,logY,widthInches,heightInches, new File(outputDir,fileNamePrefix), popupWindow);
	}

	
	
	
	/**
	 * The general x-y plotting method
	 * @param funcs - ArrayList<XY_DataSet>
	 * @param plotChars
	 * @param plotName
	 * @param xAxisLabel
	 * @param yAxisLabel
	 * @param xAxisRange
	 * @param yAxisRange
	 * @param logX
	 * @param logY
	 * @param widthInches
	 * @param heightInches
	 * @param fileNamePrefix - set a null if you don't want to save to files
	 * @param popupWindow - set as false if you don't want a pop-up windows with the plots
	 */
	public static PlotSpec writeAndOrPlotFuncs(
			ArrayList<XY_DataSet> funcs, 
			ArrayList<PlotCurveCharacterstics> plotChars, 
			String plotName,
			String xAxisLabel,
			String yAxisLabel,
			Range xAxisRange,
			Range yAxisRange,
			boolean logX,
			boolean logY,
			double widthInches,
			double heightInches,
			File fileNamePrefix, 
			boolean popupWindow) {
		
		if(popupWindow) {
			
			GraphWindow graph = new GraphWindow(funcs, plotName);

			if(xAxisRange != null)
				graph.setX_AxisRange(xAxisRange.getLowerBound(),xAxisRange.getUpperBound());
			if(yAxisRange != null)
				graph.setY_AxisRange(yAxisRange.getLowerBound(),yAxisRange.getUpperBound());
			graph.setXLog(logX);
			graph.setYLog(logY);
			graph.setPlotChars(plotChars);
			graph.setX_AxisLabel(xAxisLabel);
			graph.setY_AxisLabel(yAxisLabel);
			graph.setTickLabelFontSize(18);
			graph.setAxisLabelFontSize(20);

		}
		
		PlotSpec spec = new PlotSpec(funcs, plotChars, plotName, xAxisLabel, yAxisLabel);
		
		if (fileNamePrefix != null){
			HeadlessGraphPanel gp = new HeadlessGraphPanel();
			gp.setUserBounds(xAxisRange, yAxisRange);
			gp.setTickLabelFontSize(16);
			gp.setAxisLabelFontSize(22);
			gp.setPlotLabelFontSize(16);
			gp.setBackgroundColor(Color.WHITE);
			gp.drawGraphPanel(spec, logX, logY); // spec can be a list
			int width = (int)(widthInches*72.);
			int height = (int)(heightInches*72.);
			gp.getChartPanel().setSize(width, height); 

			if(integerYaxisTickLabeIncrements)
				gp.getChartPanel().getChart().getXYPlot().getRangeAxis().setStandardTickUnits(NumberAxis.createIntegerTickUnits());
			
//			XYTextAnnotation annotation = new XYTextAnnotation("here",xAxisRange.getCentralValue(),yAxisRange.getCentralValue());
//			gp.getChartPanel().getChart().getXYPlot().addAnnotation(annotation);	
//
			try {
				gp.saveAsPNG(fileNamePrefix+".png");
				gp.saveAsPDF(fileNamePrefix+".pdf");
				gp.saveAsTXT(fileNamePrefix+".txt");
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		return spec;
	}
	
	
	
	public static HistogramFunction getNormRI_Distribution(ArrayList<Double> normRI_List, double deltaT) {
		// find max value
		double max=0;
		for(Double val:normRI_List)
			if(val>max) max = val;
		if(max<4.0) // this is to ensure any fits consider the zero bins above the data max
			max=4.0;
//		double deltaX=0.1;
		int num = (int)Math.ceil(max/deltaT)+2;
		HistogramFunction dist = new HistogramFunction(deltaT/2,num,deltaT);
		dist.setTolerance(2*deltaT);
		int numData=normRI_List.size();
		for(Double val:normRI_List) {
//System.out.println(val);
			dist.add(val, 1.0/(numData*deltaT));  // this makes it a true PDF
		}
		return dist;
	}


	public static ArrayList<EvenlyDiscretizedFunc> getRenewalModelFunctionFitsToDist(EvenlyDiscretizedFunc dist) {
		// now make the function list for the plot
		ArrayList<EvenlyDiscretizedFunc> funcList = new ArrayList<EvenlyDiscretizedFunc>();
		
		// add best-fit BPT function
		BPT_DistCalc bpt_calc = new BPT_DistCalc();
		bpt_calc.fitToThisFunction(dist, 0.5, 1.5, 101, 0.1, 1.5, 141);
		EvenlyDiscretizedFunc fitBPT_func = bpt_calc.getPDF();
		fitBPT_func.setName("Best Fit BPT Dist");
		fitBPT_func.setInfo("mean,aper\n"+(float)bpt_calc.getMean()+","+(float)bpt_calc.getAperiodicity());
		funcList.add(fitBPT_func);
		
		// add best-fit Lognormal dist function
		LognormalDistCalc logNorm_calc = new LognormalDistCalc();
		logNorm_calc.fitToThisFunction(dist, 0.5, 1.5, 101, 0.1, 1.5, 141);
		EvenlyDiscretizedFunc fitLogNorm_func = logNorm_calc.getPDF();
		fitLogNorm_func.setName("Best Fit Lognormal Dist");
		fitLogNorm_func.setInfo("mean,aper\n"+(float)logNorm_calc.getMean()+","+(float)logNorm_calc.getAperiodicity()+")");
		funcList.add(fitLogNorm_func);
		
		// add best-fit Weibull dist function
		WeibullDistCalc weibull_calc = new WeibullDistCalc();
		weibull_calc.fitToThisFunction(dist, 0.5, 1.5, 101, 0.1, 1.5, 141);
		EvenlyDiscretizedFunc fitWeibull_func = weibull_calc.getPDF();
		fitWeibull_func.setName("Best Fit Weibull Dist");
		fitWeibull_func.setInfo("mean,aper\n"+(float)weibull_calc.getMean()+","+(float)weibull_calc.getAperiodicity()+")");
		funcList.add(fitWeibull_func);

		return funcList;
	}

	
	public static void writeSimExpRateVsTime(TreeMap<Long, Integer> nthRupAtEpochMap, ArrayList<Double> totExpRateAtEventTimeList,
			double totalLongTermRate, File outputDir) {
		
		ArrayList<Double> yearsIntoSimulation = new ArrayList<Double>();
		Long lastEpoch = Long.MIN_VALUE;
		for(Long epoch:nthRupAtEpochMap.keySet()) {
			if(epoch<=lastEpoch)
				throw new RuntimeException("Not ordered)");
			double yrs = 1970 + (double)epoch/ProbabilityModelsCalc.MILLISEC_PER_YEAR;
			yearsIntoSimulation.add(yrs);
			lastEpoch=epoch;
		}
		DefaultXY_DataSet totRateVersusTime = new DefaultXY_DataSet(yearsIntoSimulation,totExpRateAtEventTimeList);
		double meanTotRate=0;
		double totWt=0;
		for(int i=0;i<totExpRateAtEventTimeList.size()-1;i++) {
			double wt = yearsIntoSimulation.get(i+1)-yearsIntoSimulation.get(i); // apply rate until time of next event
			meanTotRate+=totExpRateAtEventTimeList.get(i)*wt;
			totWt+=wt;
		}
		meanTotRate /= totWt;
		totRateVersusTime.setName("Total Exp Rate vs Time");
		totRateVersusTime.setInfo("Mean Total Rate = "+meanTotRate+"\nLong Term Rate = "+totalLongTermRate);
		DefaultXY_DataSet longTermRateFunc = new DefaultXY_DataSet();
		longTermRateFunc.set(totRateVersusTime.getMinX(),totalLongTermRate);
		longTermRateFunc.set(totRateVersusTime.getMaxX(),totalLongTermRate);
		longTermRateFunc.setName("Long term rate");
		
		double maxDif = 1.1*Math.max(totRateVersusTime.getMaxY()-totalLongTermRate, totalLongTermRate-totRateVersusTime.getMinY());
		double minY =totalLongTermRate-maxDif;
		double maxY =totalLongTermRate+maxDif;
		if(minY==maxY) {
			minY *= 0.9;
			maxY *= 1.1;
		}
		
		ArrayList<XY_DataSet> funcs = new ArrayList<XY_DataSet>();
		funcs.add(totRateVersusTime);
		funcs.add(longTermRateFunc);
		ArrayList<PlotCurveCharacterstics> plotChars = new ArrayList<PlotCurveCharacterstics>();
		plotChars.add(new PlotCurveCharacterstics(PlotSymbol.FILLED_CIRCLE, 2f, Color.RED));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.BLACK));
		String plotName = "Total Expected Rate vs Time";
		String xAxisLabel = "Year";
		String yAxisLabel = "Rate (per year)";
		Range xAxisRange = null; //new Range(,);
		Range yAxisRange = new Range(minY,maxY);
		boolean logX = false;
		boolean logY = false;
		double widthInches = 7.0; // inches
		double heightInches = 3.0; // inches
		String fileNamePrefix = "totalExpRateVsTime";
		boolean popupWindow = false;
		writeAndOrPlotFuncs(funcs,plotChars,plotName,xAxisLabel,yAxisLabel,xAxisRange,yAxisRange,
				logX,logY,widthInches,heightInches, new File(outputDir,fileNamePrefix), popupWindow);
	}
	
	
	public static void writeSimVsImposedRateScatterPlot(double imposedRateArray[], double[] simRateArray, File outputDir, String fileNamePrefix, 
			String plotTitle, String xAxisLabel, String yAxisLabel, double minVal, double maxVal) {
		
		DefaultXY_DataSet obsVsImposedRupRates = new DefaultXY_DataSet(imposedRateArray,simRateArray);
		
		if(Double.isNaN(minVal)) {
			minVal=Double.MAX_VALUE;
			for(int i=0;i<imposedRateArray.length;i++)
				if(minVal>imposedRateArray[i] && imposedRateArray[i]>0)
					minVal=imposedRateArray[i];			
		}
		if(Double.isNaN(maxVal)) {
			maxVal=Double.NEGATIVE_INFINITY;
			for(int i=0;i<imposedRateArray.length;i++)
				if(maxVal<imposedRateArray[i])
					maxVal=imposedRateArray[i];
		}
		obsVsImposedRupRates.setName("plotTitle");
		XY_DataSet perfectAgreementFunc = new DefaultXY_DataSet();
		perfectAgreementFunc.set(minVal,minVal);
		perfectAgreementFunc.set(maxVal,maxVal);
		perfectAgreementFunc.setName("Perfect agreement line");
		ArrayList<XY_DataSet> funcs = new ArrayList<XY_DataSet>();
		funcs.add(obsVsImposedRupRates);
		funcs.add(perfectAgreementFunc);
		ArrayList<PlotCurveCharacterstics> plotChars = new ArrayList<PlotCurveCharacterstics>();
		plotChars.add(new PlotCurveCharacterstics(PlotSymbol.FILLED_CIRCLE, 0.5f, Color.RED));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.BLACK));

		Range xAxisRange = new Range(minVal,maxVal);
		Range yAxisRange = new Range(minVal,maxVal);
		boolean logX = true;
		boolean logY = true;
		double widthInches = 7.0; // inches
		double heightInches = 6.0; // inches
		boolean popupWindow = false;
		writeAndOrPlotFuncs(funcs,plotChars,plotTitle,xAxisLabel,yAxisLabel,xAxisRange,yAxisRange,
				logX,logY,widthInches,heightInches, new File(outputDir,fileNamePrefix), popupWindow);
	}
	
	public static void writeNormRI_SlongStrikePlot(ArbDiscrEmpiricalDistFunc_3D normRI_AlongStrike, File outputDir) {

		ArrayList<XY_DataSet> funcs = new ArrayList<XY_DataSet>();
		EvenlyDiscretizedFunc meanAlongFunc = normRI_AlongStrike.getMeanCurve();
		meanAlongFunc.setName("mean");
		funcs.add(normRI_AlongStrike.getMeanCurve());
		EvenlyDiscretizedFunc alongFunc2pt5 = normRI_AlongStrike.getInterpolatedFractileCurve(0.025);
		EvenlyDiscretizedFunc alongFunc97pt5 = normRI_AlongStrike.getInterpolatedFractileCurve(0.975);
		alongFunc2pt5.setInfo("2.5 percentile");
		alongFunc97pt5.setInfo("97.5 percentile");
		funcs.add(alongFunc2pt5);
		funcs.add(alongFunc97pt5);
		ArrayList<PlotCurveCharacterstics> plotChars = new ArrayList<PlotCurveCharacterstics>();
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.RED));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLACK));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLACK));
		
		Range xAxisRange = null;
		Range yAxisRange = null;
		boolean logX = false;
		boolean logY = false;
		double widthInches = 7.0; // inches
		double heightInches = 6.0; // inches
		boolean popupWindow = false;
		writeAndOrPlotFuncs(funcs,plotChars,"","Norm Dist Along Strike","Normalized RI",xAxisRange,yAxisRange,
				logX,logY,widthInches,heightInches, new File(outputDir,"normRI_AlongRupTrace"), popupWindow);
	}
	
	
	/**
	 * 	This plots the aveGainVsMagFunction, weighted by rupture rate.  Average gain for each rupture is averaged 
	 * over all time steps in the simulation (regardless of whether it was the one that occurred)

	 * @param magOfNthRups
	 * @param aveRupProbGainArray
	 * @param longTermRateOfNthRups
	 * @param outputDir
	 */
	public static void writeAveGainVsMagHistPlot(double magOfNthRups[], double aveRupProbGainArray[], double longTermRateOfNthRups[], File outputDir) {
		HistogramFunction aveGainVsMagHist = new HistogramFunction(5.05, 50, 0.1);
		HistogramFunction tempWtHist = new HistogramFunction(5.05, 50, 0.1);
		for(int i=0;i<aveRupProbGainArray.length;i++) {
			aveGainVsMagHist.add(magOfNthRups[i], aveRupProbGainArray[i]*longTermRateOfNthRups[i]);
			tempWtHist.add(magOfNthRups[i], longTermRateOfNthRups[i]);
		}
		for(int i=0;i<aveGainVsMagHist.size();i++) {
			double wt = tempWtHist.getY(i);
			if(wt>1e-15) // avoid division by zero
				aveGainVsMagHist.set(i,aveGainVsMagHist.getY(i)/wt);
		}
		aveGainVsMagHist.setName("aveGainVsMagHist");
		aveGainVsMagHist.setInfo("weighted by rupture long-term rates");
		ArrayList<XY_DataSet> funcs = new ArrayList<XY_DataSet>();
		funcs.add(aveGainVsMagHist);
		ArrayList<PlotCurveCharacterstics> plotChars = new ArrayList<PlotCurveCharacterstics>();
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 2f, Color.BLUE));
		
		Range xAxisRange = null;
		Range yAxisRange = null;
		boolean logX = false;
		boolean logY = false;
		double widthInches = 7.0; // inches
		double heightInches = 6.0; // inches
		boolean popupWindow = false;
		writeAndOrPlotFuncs(funcs,plotChars,"","Magnitude","Average Gain",xAxisRange,yAxisRange,
				logX,logY,widthInches,heightInches, new File(outputDir,"aveRupGainVsMagHist"), popupWindow);

	}
	
	/**
	 * This plots the ave, min, and max gain for each rupture over the simulation, versus magnitude.
	 * Not sure how useful this is.
	 * @param magOfNthRups
	 * @param aveRupProbGainArray
	 * @param minRupProbGainArray
	 * @param maxRupProbGainArray
	 * @param outputDir
	 */
	public static void writeRupGainMeanMinMaxVsMagPlot(double[] magOfNthRups,double[] aveRupProbGainArray,double[] minRupProbGainArray,
			double[] maxRupProbGainArray, File outputDir) {
		
		DefaultXY_DataSet aveRupProbGainVsMag = new DefaultXY_DataSet(magOfNthRups,aveRupProbGainArray);
		DefaultXY_DataSet minRupProbGainVsMag = new DefaultXY_DataSet(magOfNthRups,minRupProbGainArray);
		DefaultXY_DataSet maxRupProbGainVsMag = new DefaultXY_DataSet(magOfNthRups,maxRupProbGainArray);
		aveRupProbGainVsMag.setName("Ave Rup Prob Gain vs Mag (for each nth rupture over the simulation, regardless of whether event occurred)");
		double meanProbGain =0;
		for(double val:aveRupProbGainArray) 
			meanProbGain += val;
		meanProbGain /= aveRupProbGainArray.length;
		aveRupProbGainVsMag.setInfo("meanProbGain="+(float)meanProbGain);
		minRupProbGainVsMag.setName("Min Rup Prob Gain vs Mag");
		maxRupProbGainVsMag.setName("Max Rup Prob Gain vs Mag");
		ArrayList<XY_DataSet> funcs = new ArrayList<XY_DataSet>();
		funcs.add(aveRupProbGainVsMag);
		funcs.add(minRupProbGainVsMag);
		funcs.add(maxRupProbGainVsMag);
		ArrayList<PlotCurveCharacterstics> plotChars = new ArrayList<PlotCurveCharacterstics>();
		plotChars.add(new PlotCurveCharacterstics(PlotSymbol.CROSS, 4f, Color.BLUE));
		plotChars.add(new PlotCurveCharacterstics(PlotSymbol.CROSS, 4f, Color.GREEN));
		plotChars.add(new PlotCurveCharacterstics(PlotSymbol.CROSS, 4f, Color.RED));

		Range xAxisRange = null;
		Range yAxisRange = null;
		boolean logX = false;
		boolean logY = false;
		double widthInches = 7.0; // inches
		double heightInches = 6.0; // inches
		boolean popupWindow = false;
		writeAndOrPlotFuncs(funcs,plotChars,"","Magnitude","Ave, Min, & Max Gain",xAxisRange,yAxisRange,
				logX,logY,widthInches,heightInches, new File(outputDir,"aveMinMaxGainVsMagForEachRup"), popupWindow);
	}

	
	public static void writeNormTS_andGainForAllRupsOverSimPlots(File outputDir, HistogramFunction simNormTimeSinceLastHist,
			HistogramFunction simNormTimeSinceLastForMagBelow7_Hist,
			HistogramFunction simNormTimeSinceLastForMagAbove7_Hist,
			HistogramFunction simProbGainHist,
			HistogramFunction simProbGainForMagBelow7_Hist,
			HistogramFunction simProbGainForMagAbove7_Hist) {

			simNormTimeSinceLastHist.scale(1.0/(simNormTimeSinceLastHist.calcSumOfY_Vals()*simNormTimeSinceLastHist.getDelta())); // makes it a density function
			ArrayList<EvenlyDiscretizedFunc> funcListForSimNormTimeSinceLastHist = ProbModelsPlottingUtils.addBPT_Fit(simNormTimeSinceLastHist);
			simNormTimeSinceLastHist.setName("simNormTimeSinceLastHist");
			simNormTimeSinceLastHist.setInfo("Dist of normalized time since last for each rupture at all event times (not just events that occurred)\nMean = "+simNormTimeSinceLastHist.computeMean());
			writeNormalizedDistPlotWithFits(funcListForSimNormTimeSinceLastHist, outputDir, "simNormTimeSinceLastHist", "simNormTimeSinceLastHist");
									
			simNormTimeSinceLastForMagBelow7_Hist.scale(1.0/(simNormTimeSinceLastForMagBelow7_Hist.calcSumOfY_Vals()*simNormTimeSinceLastForMagBelow7_Hist.getDelta())); // makes it a density function
			ArrayList<EvenlyDiscretizedFunc> funcListForSimNormTimeSinceLastHistForSmall = ProbModelsPlottingUtils.addBPT_Fit(simNormTimeSinceLastForMagBelow7_Hist);
			simNormTimeSinceLastForMagBelow7_Hist.setName("simNormTimeSinceLastForMagBelow7_Hist");
			simNormTimeSinceLastForMagBelow7_Hist.setInfo("Dist of normalized time since last for each rupture at all event times for M<=7 (not just events that occurred)\nMean = "+simNormTimeSinceLastForMagBelow7_Hist.computeMean());
			writeNormalizedDistPlotWithFits(funcListForSimNormTimeSinceLastHistForSmall, outputDir, "simNormTimeSinceLastForMagBelow7_Hist", "simNormTimeSinceLastForMagBelow7_Hist");

			simNormTimeSinceLastForMagAbove7_Hist.scale(1.0/(simNormTimeSinceLastForMagAbove7_Hist.calcSumOfY_Vals()*simNormTimeSinceLastForMagAbove7_Hist.getDelta())); // makes it a density function
			ArrayList<EvenlyDiscretizedFunc> funcListForSimNormTimeSinceLastHistForLarge = ProbModelsPlottingUtils.addBPT_Fit(simNormTimeSinceLastForMagAbove7_Hist);
			simNormTimeSinceLastForMagAbove7_Hist.setName("simNormTimeSinceLastForMagAbove7_Hist");
			simNormTimeSinceLastForMagAbove7_Hist.setInfo("Dist of normalized time since last for each rupture at all event times from M>7 (not just events that occurred)\nMean = "+simNormTimeSinceLastForMagAbove7_Hist.computeMean());
			writeNormalizedDistPlotWithFits(funcListForSimNormTimeSinceLastHistForLarge, outputDir, "simNormTimeSinceLastForMagAbove7_Hist", "simNormTimeSinceLastForMagAbove7_Hist");

			simProbGainHist.scale(1.0/(simProbGainHist.calcSumOfY_Vals()*simProbGainHist.getDelta())); // makes it a density function
			simProbGainHist.setName("simProbGainHist");
			simProbGainHist.setInfo("Dist of gains for each rupture at all event times, and not just events that occurred (simProbGainHist)\nMean = "+simProbGainHist.computeMean());
			ArrayList<XY_DataSet> funcListForSimProbGainHist = new ArrayList<XY_DataSet>();
			funcListForSimProbGainHist.add(simProbGainHist);
			simProbGainForMagBelow7_Hist.scale(1.0/(simProbGainForMagBelow7_Hist.calcSumOfY_Vals()*simProbGainForMagBelow7_Hist.getDelta())); // makes it a density function
			simProbGainForMagAbove7_Hist.scale(1.0/(simProbGainForMagAbove7_Hist.calcSumOfY_Vals()*simProbGainForMagAbove7_Hist.getDelta())); // makes it a density function
			simProbGainForMagBelow7_Hist.setName("simProbGainForMagBelow7_Hist");
			simProbGainForMagAbove7_Hist.setName("simProbGainForMagAbove7_Hist");
			simProbGainForMagBelow7_Hist.setInfo("Mean = "+simProbGainForMagBelow7_Hist.computeMean());
			simProbGainForMagAbove7_Hist.setInfo("Mean = "+simProbGainForMagAbove7_Hist.computeMean());
			funcListForSimProbGainHist.add(simProbGainForMagBelow7_Hist);
			funcListForSimProbGainHist.add(simProbGainForMagAbove7_Hist);
			ArrayList<PlotCurveCharacterstics> plotChars = new ArrayList<PlotCurveCharacterstics>();
			plotChars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 2f, Color.RED));
			plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLUE));
			plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLACK));
			String xAxisLabel = "Gain";
			String yAxisLabel = "Density";
			Range xAxisRange = null;
			Range yAxisRange = null;
			boolean logX = false;
			boolean logY = false;
			double widthInches = 7; // inches
			double heightInches = 6; // inches
			
			boolean popupWindow = false;
			writeAndOrPlotFuncs(funcListForSimProbGainHist,plotChars,"simProbGainHist",xAxisLabel,yAxisLabel,xAxisRange,yAxisRange,
					logX,logY,widthInches,heightInches, new File(outputDir,"simProbGainHist"), popupWindow);

		}
	
	/**
	 * 
	 */
	public static void writeSimOverImposedVsImposedSecPartRates_Plot(File outputDir, double[] imposedSectRateArray, 
			double[] simSectRateArray, double minNumSimEvents, double numYears, String fileNameSuffix) {
		DefaultXY_DataSet obs_pred_ratioForSections = new DefaultXY_DataSet();
		for(int s=0;s<simSectRateArray.length;s++) {
			if(imposedSectRateArray[s]*numYears>=10 && imposedSectRateArray[s]>0)	// only keep where 10 are expected have occurred
				obs_pred_ratioForSections.set(imposedSectRateArray[s], simSectRateArray[s]/imposedSectRateArray[s]);
		}
		// make sure there is enough data to warrant a plot
		if(obs_pred_ratioForSections.size()>2) {
			DefaultXY_DataSet perfectAgreementFunc2 = new DefaultXY_DataSet();
			perfectAgreementFunc2.set(10.0/numYears,1d);
			perfectAgreementFunc2.set(0.1,1d);
			perfectAgreementFunc2.setName("Perfect agreement line");
			ArrayList<XY_DataSet> funcs = new ArrayList<XY_DataSet>();
			funcs.add(obs_pred_ratioForSections);
			funcs.add(perfectAgreementFunc2);
			ArrayList<PlotCurveCharacterstics> plotChars = new ArrayList<PlotCurveCharacterstics>();
			plotChars.add(new PlotCurveCharacterstics(PlotSymbol.CROSS, 4f, Color.BLUE));
			plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.RED));
			
			String xAxisLabel = "Imposed Part Rate";
			String yAxisLabel = "Part Rate Ratio";
			Range xAxisRange = null;
			Range yAxisRange = null; //new Range(0,3);
			boolean logX = true;
			boolean logY = false;
			double widthInches = 7; // inches
			double heightInches = 6; // inches
			
			boolean popupWindow = false;
			writeAndOrPlotFuncs(funcs,plotChars,fileNameSuffix,xAxisLabel,yAxisLabel,xAxisRange,yAxisRange,
					logX,logY,widthInches,heightInches, new File(outputDir,"simOverImposedVsImposedSectionPartRates"+fileNameSuffix), popupWindow);
		}
	}

	/*
	 * The gets the color for the map of simulated to target value ratios, where
	 * the colors fades to white as the uncertainty increases.
	 * 
	 * sigma is the uncertainty on ratio
	 */
	public static Color getRatioMapColor(double ratio, double sigma) {
		Color color;
		float h=-100f,s=1f, b=1f;
		double numSigmaDiff = 10; // the default for zero sigma
		if(sigma>0.0) {
			numSigmaDiff = Math.abs(ratio-1.0)/sigma;
		}
		s = (float)Math.min(1.0, numSigmaDiff/3.0); // richest color when ratio is at 3 sigma
//		s=0.66f;
		if(ratio<0.9) {
			h = (float)(240.0/360.0); // blue
			color = Color.getHSBColor(h, s, b);
		}
		else if(ratio<0.95) {
			h = (float)(120.0/360.0); // green
			color = Color.getHSBColor(h, s, b);
		}
		else if(ratio<1.05) {// this covers -0.05 to 0.05
			color = Color.gray;
		}
		else if (ratio<1.1) {
			h = (float)(35.0/360.0); // orange
			color = Color.getHSBColor(h, s, b);
		}
		else {
			h = 0f; // red
			color = Color.getHSBColor(h, s, b);
		}
		
		return color;
	}
	
	public static void writeMapOfSimOverTargetPartRates (double[] sectRatioArray, double[] sectSigmaArray, 
			List<? extends FaultSection> subSects, File outputDir) {
		try {
			Color[] sectColorArray = new Color[subSects.size()];
			GeographicMapMaker mapMaker = new GeographicMapMaker(subSects);
			mapMaker.setWriteGeoJSON(true);
			mapMaker.clearSectScalars();
			
			List<Color> sectColorList = new ArrayList<>();
			for (int i=0;i<sectRatioArray.length;i++) {
				Color color = ProbModelsPlottingUtils.getRatioMapColor(sectRatioArray[i], sectSigmaArray[i]);
				sectColorList.add(color);
			}
			mapMaker.plotSectColors(sectColorList, null, null);
			mapMaker.setSectNaNChar(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, new Color(0, 0, 255)));
			mapMaker.plot(outputDir, "mapOfSectPartRatios", " ");
		} catch (IOException e) {
			e.printStackTrace();
		}

	}
	
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}

}
