package scratch.UCERF3.erf.ETAS;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.HistogramFunction;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.gui.plot.GraphWindow;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;

public class SeisDepthDistribution {
	
	double[] depthVals = {0.0, 3.0, 6.0, 12.0, 24.0};
	double[] relWtVals = {0.0, 1.0, 10.0, 2.0, 0.0};
	ArbitrarilyDiscretizedFunc depthDistFunc;
	ArbitrarilyDiscretizedFunc inverseCumDepthDistFunc;
	HistogramFunction cumHistFunc;
	
	ETAS_Utils etas_utils;

	/**
	 * 
	 * @param etas_utils - needed for random number reproducibility
	 */
	public SeisDepthDistribution(ETAS_Utils etas_utils) {
		
		this.etas_utils = etas_utils;
		
		depthDistFunc = new ArbitrarilyDiscretizedFunc();
		for(int i=0;i<depthVals.length;i++) {
			depthDistFunc.set(depthVals[i], relWtVals[i]);		
		}
		double totArea = 0;
		for(int i=1;i<depthVals.length;i++) {
			totArea += 0.5*(depthVals[i]-depthVals[i-1])*Math.abs(relWtVals[i]-relWtVals[i-1])+Math.min(relWtVals[i],relWtVals[i-1])*(depthVals[i]-depthVals[i-1]);			
		}
		depthDistFunc.scale(1.0/totArea);	// make it a density function; area of 1.0
		depthDistFunc.setName("Depth Distribution Function");
		
		HistogramFunction histFunc = new HistogramFunction(0.05, 240,0.1);
		for(int i=0;i<histFunc.size();i++)
			histFunc.set(i,depthDistFunc.getInterpolatedY(histFunc.getX(i)));
		
		cumHistFunc = histFunc.getCumulativeDistFunctionWithHalfBinOffset();
		cumHistFunc.scale(1.0/cumHistFunc.getMaxY());
		
		inverseCumDepthDistFunc = new ArbitrarilyDiscretizedFunc();
		for(int i=0;i<cumHistFunc.size();i++) {
			inverseCumDepthDistFunc.set(cumHistFunc.getY(i),cumHistFunc.getX(i));
		}
		
//		GraphWindow graph3 = new GraphWindow(histFunc, "histFunc"); 
//		GraphWindow graph4 = new GraphWindow(cumHistFunc, "cumHistFunc"); 
//		GraphWindow graph2 = new GraphWindow(inverseCumDepthDistFunc, "inverseCumDepthDistFunc"); 

	}
	
	/**
	 * This returns a function giving the relative rate (density) as a function of depth (km)
	 * @return
	 */
	public ArbitrarilyDiscretizedFunc getDepthDistributionFunction() {
		return depthDistFunc;
	}
	
	/**
	 * This returns the relative rate of events at the given depth
	 * @param depth (km, positive number)
	 * @return
	 */
	public double getProbAtDepth(double depth) {
		return depthDistFunc.getInterpolatedY(depth);
	}
	
	/**
	 * This returns the relative rate of events at the given depth
	 * @param depth (km, positive number)
	 * @return
	 */
	public double getProbBetweenDepths(double depth1, double depth2) {
		return cumHistFunc.getInterpolatedY(depth2)-cumHistFunc.getInterpolatedY(depth1);
	}


	/**
	 * This returns a random depth from the depth distribution
	 * @return
	 */
	public double getRandomDepth() {
		// this is a little faster
		return inverseCumDepthDistFunc.getInterpolatedY(etas_utils.getRandomDouble());
//		double randDepth = cumHistFunc.getFirstInterpolatedX(Math.random());
	}
	
	public void plotBinnedDepthDistribution() {
		double delta=2;
		HistogramFunction binnedDepthDistFunc = new HistogramFunction(1d, 12,delta);
		for(int i=0;i<binnedDepthDistFunc.size();i++) {
			double prob = getProbBetweenDepths(binnedDepthDistFunc.getX(i)-delta/2d,binnedDepthDistFunc.getX(i)+delta/2d);
			binnedDepthDistFunc.set(i,prob);
		}
		System.out.println("totBinnedProb="+binnedDepthDistFunc.calcSumOfY_Vals());
		PlotCurveCharacterstics plotChar = new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 1f, Color.BLACK);
		GraphWindow graph2 = new GraphWindow(binnedDepthDistFunc, "Seismicity Depth Distribution", plotChar);
		graph2.setGriddedFuncAxesTicks(true);
		graph2.setX_AxisRange(0, 24);
		graph2.setY_AxisRange(0, 0.301);
		graph2.setX_AxisLabel("Depth (km)");
		graph2.setY_AxisLabel("Fraction");
		graph2.setAxisLabelFontSize(20);
		graph2.setPlotLabelFontSize(22);
		graph2.setTickLabelFontSize(18);
		graph2.setGriddedFuncAxesTicks(true);
	}
	
	
	public void testRandomDepth(int numSamples) {
		HistogramFunction testRandHistFunc = new HistogramFunction(0.05, 240,0.1);
		long st = System.currentTimeMillis();
		for(int i=0;i<numSamples;i++) {
			double depth = getRandomDepth();
			if(depth<0 || depth>24)
				throw new RuntimeException("problem depth: "+depth);
			testRandHistFunc.add(depth, 1.0);
		}
		testRandHistFunc.scale(1.0/(numSamples*0.1));
		System.out.println("That took "+(System.currentTimeMillis()-st)+" millisec");
		testRandHistFunc.setName("Randome Depth Distribution Histogram");

		ArrayList<XY_DataSet> funcs = new ArrayList<XY_DataSet>();
		funcs.add(depthDistFunc);
		funcs.add(testRandHistFunc);
		GraphWindow graph1 = new GraphWindow(funcs, "Test Random Depths"); 
		graph1.setX_AxisLabel("Depth (km)");
		graph1.setY_AxisLabel("Density");
	}
	

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		
		SeisDepthDistribution test = new SeisDepthDistribution(new ETAS_Utils());
		
//		test.plotBinnedDepthDistribution();
		
		test.testRandomDepth(100000000);

	}

}
