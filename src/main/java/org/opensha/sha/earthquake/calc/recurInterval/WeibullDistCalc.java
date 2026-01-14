package org.opensha.sha.earthquake.calc.recurInterval;

import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.gui.plot.GraphWindow;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.event.ParameterChangeListener;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import org.apache.commons.math3.special.Gamma;
import org.apache.commons.math3.distribution.WeibullDistribution;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.data.Range;

/**
 * <b>Title:</b> WeibullDistCalc.java <p>
 * <b>Description:</p>.
 * Based on the equations given at http://en.wikipedia.org/wiki/Weibull_distribution
 <p>
 *
 * @author Edward Field
 * @created    Dec, 2012
 * @version 1.0
 */

public final class WeibullDistCalc extends EqkProbDistCalc implements ParameterChangeListener {
	 
	double k = Double.NaN;
	double lambda = Double.NaN;
	
	public WeibullDistCalc() {
		NAME = "Weibull";
		super.initAdjParams();
	}
	
	
	/*
	 * This computes the PDF and then the cdf from the pdf using 
	 * Trapezoidal integration. 
	 */
	protected void computeDistributionsOld() {
		clearCachedDistributions();

		pdf = new EvenlyDiscretizedFunc(0,numPoints,deltaX);
		cdf = new EvenlyDiscretizedFunc(0,numPoints,deltaX);
		// set first y-values to zero
		pdf.set(0,0);
		cdf.set(0,0);
		
		// convert aperiodicity to shape parameter (k)
		double k = getShapeParameter(aperiodicity);
		double lambda = getScaleParameter(mean,k);
// System.out.println("k="+k+"\nlambda="+lambda+"\ngamma1="+gamma1);
		
		double t,pd,cd=0;
		for(int i=1; i< pdf.size(); i++) { // skip first point because it's NaN
			t = cdf.getX(i);
			pd = (k/lambda)*Math.pow(t/lambda,k-1)*Math.exp(-1*Math.pow(t/lambda, k));
			if(Double.isNaN(pd)){
				pd=0;
				System.out.println("pd=0 for i="+i);
			}
			cd += deltaX*(pd+pdf.getY(i-1))/2;  // Trapizoidal integration
			pdf.set(i,pd);
			cdf.set(i,cd);
		}
	}
	
	
	protected void computeDistributions() {
		pdf = new EvenlyDiscretizedFunc(0,numPoints,deltaX);
		cdf = new EvenlyDiscretizedFunc(0,numPoints,deltaX);
		// set first y-values to zero
		pdf.set(0,0);
		cdf.set(0,0);
		
		// convert aperiodicity to shape parameter (k)
		k = getShapeParameter(aperiodicity);
		lambda = getScaleParameter(mean,k);
		WeibullDistribution weibullDist = new WeibullDistribution(k,lambda);

// System.out.println("k="+k+"\nlambda="+lambda+"\ngamma1="+gamma1);
		
		double t,pd,cd=0;
		for(int i=1; i< pdf.size(); i++) { // skip first point because it's NaN
			t = cdf.getX(i);
//			pd = (k/lambda)*Math.pow(t/lambda,k-1)*Math.exp(-1*Math.pow(t/lambda, k));
//			if(Double.isNaN(pd)){
//				pd=0;
//				System.out.println("pd=0 for i="+i);
//			}
//			cd += deltaX*(pd+pdf.getY(i-1))/2;  // Trapizoidal integration
			pdf.set(i,weibullDist.density(t));
			cdf.set(i,weibullDist.cumulativeProbability(t));
		}
	}


	/**
	 * This assumes the shape parameter (k) is between 0.5 (cov>2) and 20 (COV<0.1).
	 * Final value has better than 1% accuracy.
	 * @param cov - coefficient of variation
	 * @return
	 */
	public static double getShapeParameterOld(double cov) {
		
		double best_k = Double.NaN;
		double minDiff = Double.POSITIVE_INFINITY;
		for(int i=0; i<1950; i++) {
			double test_k = 0.5+i*0.01;
			double bigGamma1 = Gamma.gamma(1d+1d/test_k);
			double bigGamma2 = Gamma.gamma(1d+2d/test_k);
			double testCOV = Math.sqrt(bigGamma2/(bigGamma1*bigGamma1) -1);
			double diff = Math.abs(testCOV-cov);
			if(diff<minDiff) {
				minDiff = diff;
				best_k = test_k;
			}
		}
		return best_k;
	}
	
    public static double getScaleParameter(double mean, double shapeParam) {
		return mean/Gamma.gamma(1d+1d/shapeParam);

    }
    
    
    public static double getCOV_fromShapeParam(double shapeParam) {
        double term1 = Gamma.gamma(1.0 + 2.0 / shapeParam);
        double term2 = Gamma.gamma(1.0 + 1.0 / shapeParam);
        return Math.sqrt(term1/(term2*term2) - 1d);
    }

    
    public static double getMean_fromShapeAndScaleParams(double shapeParam, double scaleParam) {
        return scaleParam * Gamma.gamma(1.0 + 1.0 / shapeParam);
    }

	
    /**
     *  Solve for k using bisection.  This came from ChatGPT on 11/28/25
     * @param mean
     * @param variance
     * @return
     */
    public static double getShapeParameter(double cov) {
        double cv2 = cov*cov;

        // Define the function f(k) = Γ(1+2/k)/Γ(1+1/k)^2 - 1 - CV^2
        java.util.function.DoubleUnaryOperator f = (k) -> {
            double term1 = Gamma.gamma(1.0 + 2.0 / k);
            double term2 = Gamma.gamma(1.0 + 1.0 / k);
            return (term1 / (term2 * term2)) - 1.0 - cv2;
        };

        // Search interval for k (Weibull shape is typically 0.1–20)
        double lower = 0.1;
        double upper = 20.0;

        // Bisection method parameters
        double tol = 1e-10;
        int maxIter = 200;

        double fLower = f.applyAsDouble(lower);
        double fUpper = f.applyAsDouble(upper);

        if (fLower * fUpper > 0) {
            throw new RuntimeException(
                "Root is not bracketed — adjust search interval."
            );
        }

        for (int i = 0; i < maxIter; i++) {
            double mid = 0.5 * (lower + upper);
            double fMid = f.applyAsDouble(mid);

            if (Math.abs(fMid) < tol) {
                return mid;
            }

            if (fLower * fMid < 0) {
                upper = mid;
                fUpper = fMid;
            } else {
                lower = mid;
                fLower = fMid;
            }
        }

        return 0.5 * (lower + upper); // best estimate after maxIter
    }

    
    public static void makeWikipediaPlot(boolean popupWindow, File file) {
    	
    	double scale = 1;
    	double[] shapeArray = {0.5,1.0,1.5,5.0};
    	ArrayList<XY_DataSet> pdfList = new ArrayList<XY_DataSet>();
    	ArrayList<XY_DataSet> cdfList = new ArrayList<XY_DataSet>();
		System.out.println("shape\tmean\tcov");
   	for(double shape:shapeArray) {
    		double cov = getCOV_fromShapeParam(shape);
    		double mean = getMean_fromShapeAndScaleParams(shape,scale);
    		System.out.println((float)shape+"\t"+(float)mean+"\t"+(float)cov);
    		WeibullDistCalc dist = new WeibullDistCalc();
    		dist.setAllParameters(mean, cov, 0.01, 250);
    		//dist.setAll(mean, cov, 0.01, 250); // this doesn't set parameters so info strings not correct
    		pdfList.add(dist.getPDF());
    		cdfList.add(dist.getCDF());
    	}

    	ArrayList<PlotCurveCharacterstics> plotChars = new ArrayList<PlotCurveCharacterstics>();
    	plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLUE));
    	plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.RED));
    	plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.MAGENTA));
    	plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.GREEN));

    	String xAxisLabel = "";
    	String yAxisLabel = "";
    	Range xAxisRange = new Range(0,2.5);
    	Range yAxisRangePDF = new Range(0,2.5);
    	Range yAxisRangeCDF = new Range(0,1.0);

    	if(popupWindow) {

    		GraphWindow graphPDF = new GraphWindow(pdfList, "Weibull PDF");
    		graphPDF.setX_AxisRange(xAxisRange.getLowerBound(),xAxisRange.getUpperBound());
    		graphPDF.setY_AxisRange(yAxisRangePDF.getLowerBound(),yAxisRangePDF.getUpperBound());
    		graphPDF.setXLog(false);
    		graphPDF.setYLog(false);
    		graphPDF.setPlotChars(plotChars);
    		graphPDF.setX_AxisLabel(xAxisLabel);
    		graphPDF.setY_AxisLabel(yAxisLabel);
    		graphPDF.setTickLabelFontSize(18);
    		graphPDF.setAxisLabelFontSize(20);

    		GraphWindow graphCDF = new GraphWindow(cdfList, "Weibull CDF");
    		graphCDF.setX_AxisRange(xAxisRange.getLowerBound(),xAxisRange.getUpperBound());
    		graphCDF.setY_AxisRange(yAxisRangeCDF.getLowerBound(),yAxisRangeCDF.getUpperBound());
    		graphCDF.setXLog(false);
    		graphCDF.setYLog(false);
    		graphCDF.setPlotChars(plotChars);
    		graphCDF.setX_AxisLabel(xAxisLabel);
    		graphCDF.setY_AxisLabel(yAxisLabel);
    		graphCDF.setTickLabelFontSize(18);
    		graphCDF.setAxisLabelFontSize(20);
    	}

    	PlotSpec specPDF = new PlotSpec(pdfList, plotChars, "Weibull PDF", xAxisLabel, yAxisLabel);
    	if (file != null){
    		HeadlessGraphPanel gp = new HeadlessGraphPanel();
    		gp.setUserBounds(xAxisRange, yAxisRangePDF);
    		gp.setTickLabelFontSize(16);
    		gp.setAxisLabelFontSize(22);
    		gp.setPlotLabelFontSize(16);
    		gp.setBackgroundColor(Color.WHITE);
    		gp.drawGraphPanel(specPDF, false, false); // spec can be a list
    		int width = (int)(4*72.);
    		int height = (int)(4*72.);
    		gp.getChartPanel().setSize(width, height); 
    		try {
    			gp.saveAsPNG(file+"_PDF.png");
    			gp.saveAsPDF(file+"_PDF.pdf");
    			gp.saveAsTXT(file+"_PDF.txt");
    		} catch (IOException e) {
    			e.printStackTrace();
    		}
    	}
    }
    
    /**
     * Override this to avoid numerical problems
     */
	public EvenlyDiscretizedFunc getHazFunc() {
		ensureUpToDate();
		EvenlyDiscretizedFunc hazFunc = new EvenlyDiscretizedFunc(0, pdf.getMaxX(), pdf.size());
		double haz;
		for(int i=0;i<hazFunc.size();i++) {
			haz = k*Math.pow(lambda, -k)*Math.pow(hazFunc.getX(i), k-1);// pdf.getY(i)/(1.0-cdf.getY(i));
			if(Double.isInfinite(haz) || Double.isInfinite(-haz)) haz = Double.NaN;
			hazFunc.set(i,haz);
		}
		hazFunc.setName(NAME+" Hazard Function");
		hazFunc.setInfo(adjustableParams.toString());
		return hazFunc;
	}


	/**
	 *  Main method for running tests.
	 */
	public static void main(String args[]) {
		
		double[] oskinShapeArray = {1.8,2.2,1.4,1.8,
				1.6, 1.6,3.0,5.2,2.0};
		String[] oskinFaultArray = {
				"Wrightwood",
				"Pallett Creek",
				"Frazier Mountain",
				"Noyo Canyon",
				"Hog Lake",
				"Mystic Lake",
				"Hokuri Creek",
				"Lake Paringa",
				"Concave/Convex boundary"};
		for(int i=0;i<oskinShapeArray.length;i++) {
			double cov = getCOV_fromShapeParam(oskinShapeArray[i]);
			System.out.println(oskinShapeArray[i]+"\t"+(float)cov+"\t"+oskinFaultArray[i]);
		}
		
//		// compare speed between the two methods  (old one is 2.2 times slower)
//		long time = System.currentTimeMillis();
//		for(int n=0;n<1e4;n++) {
//			WeibullDistCalc calc = new WeibullDistCalc();
//			calc.setAll(1, 0.4+0.1*Math.random(), 0.01, 500);
//			calc.computeDistributionsOld();
//		}
//		long dur1 = System.currentTimeMillis()-time;
//		time = System.currentTimeMillis();
//		for(int n=0;n<1e4;n++) {
//			WeibullDistCalc calc = new WeibullDistCalc();
//			calc.setAll(1, 0.4+0.1*Math.random(), 0.01, 500);
//			calc.computeDistributions();
//		}
//		long dur2 = System.currentTimeMillis()-time;
//		double ratio = (double)dur1/(double)dur2;
//		System.out.println(ratio);
	
				
//		makeWikipediaPlot(true, new File("junkRightHere1010"));
		
//		// compare speed between the two methods  (old one is 34 times slower)
//		long time = System.currentTimeMillis();
//		for(int n=0;n<1e5;n++)
//			getShapeParameterOld(0.4+0.1*Math.random());
//		long dur1 = System.currentTimeMillis()-time;
//		time = System.currentTimeMillis();
//		for(int n=0;n<1e5;n++)
//			getShapeParameter(0.4+0.1*Math.random());
//		long dur2 = System.currentTimeMillis()-time;
//		double ratio = (double)dur1/(double)dur2;
//		System.out.println(ratio);
		
//		// compare shape calc methods
//		for(double cov = 0.1; cov < 2;cov+=0.1) {
//			double k1 = (float)getShapeParameterOld(cov);
//			double k2 = (float)getShapeParameter(cov);
//			System.out.println((float)cov+"\t"+k1+"\t"+k2+"\t"+k1/k2);
//		}
		
		// cov-->k-->cov
//		for(double cov = 0.1; cov < 2;cov+=0.1) {
//			double k = (float)getShapeParameter(cov);
//			double cov2 = getCOV_fromShapeParam(k);
//			System.out.println((float)cov+"\t"+(float)(cov/cov2));
//		}
		
		// mean-->scale-->mean for various cov/shapeParam
//		double[] meanArray = {0.5,1.0,50, 100};
//		for(double cov = 0.1; cov < 2;cov+=0.1) {
//			double shapeParam = (float)getShapeParameter(cov);
//			for(double mean:meanArray) {
//				double scaleParam = getScaleParameter(mean, shapeParam);
//				double mean2 = getMean_fromShapeAndScaleParams(shapeParam, scaleParam);
//				System.out.println((float)cov+"\t"+(float)mean +"\t"+(float)(mean/mean2));
//			}
//		}
	}
}

