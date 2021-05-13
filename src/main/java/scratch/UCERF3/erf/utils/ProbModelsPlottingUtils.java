/**
 * 
 */
package scratch.UCERF3.erf.utils;

import java.awt.Color;
import java.util.ArrayList;

import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.HistogramFunction;
import org.opensha.commons.gui.plot.GraphWindow;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.sha.earthquake.calc.recurInterval.BPT_DistCalc;
import org.opensha.sha.earthquake.calc.recurInterval.LognormalDistCalc;
import org.opensha.sha.earthquake.calc.recurInterval.WeibullDistCalc;

/**
 * @author field
 *
 */
public class ProbModelsPlottingUtils {
	
	
	
	
	/**
	 * This utility plots the histogram of normalized recurrence intervals from normRI_List, 
	 * along with best-fit BPT, Lognormal, and Weibull distributions.  Use the returned object
	 * if you want to save to a file.
	 * This is an alternative version of getNormRI_DistributionGraphPanel(*)
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
		String info = "NumObs\tObsMean\tObsCOV\tObsOverBPTAboveFive\tbestFitBPTmean\tbestFitBPTaper\n"+ normRI_List.size()+ "\t"+
				(float)dist.computeMean()+"\t"+(float)dist.computeCOV()+"\t"+(float)(obsAboveFive/bptAboveFive);
		info += "\t"+bptDist.getInfo().split("\n")[1];
		dist.setInfo(info);

		
		if(!Double.isNaN(bptAperForComparison)) {
			BPT_DistCalc bpt_calc = new BPT_DistCalc();
			bpt_calc.setAll(1.0, bptAperForComparison, funcList.get(1).getDelta()/2, funcList.get(1).size());	// not the first one because that's the obs histogram
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
	 * This utility plots the histogram of normalized recurrence intervals from normRI_List, 
	 * along with best-fit BPT, Lognormal, and Weibull distributions.  Use the returned object
	 * if you want to save to a file.
	 * This is an alternative version of getNormRI_DistributionGraphPanel(*)
	 * @param normRI_List
	 * @param plotTitle
	 * @param bptAperForComparison - this will add a BPT dist with mean=1 and given aper for comparison (set as Double.NaN if not desired)
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

	
	
	
	public static HistogramFunction getNormRI_Distribution(ArrayList<Double> normRI_List, double deltaT) {
		// find max value
		double max=0;
		for(Double val:normRI_List)
			if(val>max) max = val;
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
		fitBPT_func.setInfo("mean\taper\n"+(float)bpt_calc.getMean()+"\t"+(float)bpt_calc.getAperiodicity());
		funcList.add(fitBPT_func);
		
		// add best-fit Lognormal dist function
		LognormalDistCalc logNorm_calc = new LognormalDistCalc();
		logNorm_calc.fitToThisFunction(dist, 0.5, 1.5, 101, 0.1, 1.5, 141);
		EvenlyDiscretizedFunc fitLogNorm_func = logNorm_calc.getPDF();
		fitLogNorm_func.setName("Best Fit Lognormal Dist");
		fitLogNorm_func.setInfo("mean\taper\n"+(float)logNorm_calc.getMean()+"\t"+(float)logNorm_calc.getAperiodicity()+")");
		funcList.add(fitLogNorm_func);
		
		// add best-fit Weibull dist function
		WeibullDistCalc weibull_calc = new WeibullDistCalc();
		weibull_calc.fitToThisFunction(dist, 0.5, 1.5, 101, 0.1, 1.5, 141);
		EvenlyDiscretizedFunc fitWeibull_func = weibull_calc.getPDF();
		fitWeibull_func.setName("Best Fit Weibull Dist");
		fitWeibull_func.setInfo("mean\taper\n"+(float)weibull_calc.getMean()+"\t"+(float)weibull_calc.getAperiodicity()+")");
		funcList.add(fitWeibull_func);

		return funcList;
	}

	

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}

}
