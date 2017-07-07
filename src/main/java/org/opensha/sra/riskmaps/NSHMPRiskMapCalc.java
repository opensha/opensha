package org.opensha.sra.riskmaps;

import java.util.ArrayList;
import java.util.Arrays;

import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.xyz.ArbDiscrXYZ_DataSet;
import org.opensha.commons.data.xyz.EvenlyDiscrXYZ_DataSet;
import org.opensha.sha.calc.hazardMap.BinaryHazardCurveReader;
import org.opensha.sra.calc.LossCurveCalculator;
import org.opensha.sra.riskmaps.func.DiscreteInterpExterpFunc;

import com.google.common.primitives.Doubles;

public class NSHMPRiskMapCalc {
	
	public static final double gridSpacing = 0.05;
	public static final double minLat = 24.6;
	public static final double minLon = -125;
	public static final int ncols = 1201;
	public static final int nrows = 509;
	
	public static boolean interp = true;
	
	private BinaryHazardCurveReader curveReader;
	private DiscreteInterpExterpFunc fragilityCurve;
	
	private static long interpTime = 0;
	private static long calcTime = 0;
	private static long fetchTime = 0;
	
	private ArrayList<Double> testVals = null;
	
	public NSHMPRiskMapCalc(BinaryHazardCurveReader curveReader, DiscreteInterpExterpFunc fragilityCurve) {
		this.curveReader = curveReader;
		this.fragilityCurve = fragilityCurve;
	}
	
	public static ArbitrarilyDiscretizedFunc[] getInterpolatedCurvesFast(ArbitrarilyDiscretizedFunc hazCurve,
			ArbitrarilyDiscretizedFunc fragilityCurve) {
		long start = System.currentTimeMillis();
		DiscreteInterpExterpFunc interpHazFunc = DiscreteInterpExterpFunc.fromArbDistFunc(hazCurve);
		DiscreteInterpExterpFunc interpFragFunc = DiscreteInterpExterpFunc.fromArbDistFunc(fragilityCurve);
		
		ArbitrarilyDiscretizedFunc interpHazCurve = new ArbitrarilyDiscretizedFunc();
		ArbitrarilyDiscretizedFunc interpFragCurve = new ArbitrarilyDiscretizedFunc();
		double xVals[] = boundedMerge(hazCurve.getXVals(), fragilityCurve.getXVals());
		for (double x : xVals) {
			interpHazCurve.set(x, interpHazFunc.valueOf(x));
			interpFragCurve.set(x, interpFragFunc.valueOf(x));
		}
		ArbitrarilyDiscretizedFunc result[]  = { interpHazCurve, interpFragCurve};
		long end = System.currentTimeMillis();
		interpTime += (end - start);
		return result;
	}
	
	private double[] getXVals(ArbitrarilyDiscretizedFunc hazCurve,
			ArbitrarilyDiscretizedFunc fragilityCurve) {
		return getXVals(DiscreteInterpExterpFunc.fromArbDistFunc(hazCurve),
				DiscreteInterpExterpFunc.fromArbDistFunc(fragilityCurve));
	}
	
	private double[] getXVals(DiscreteInterpExterpFunc hazCurve,
			DiscreteInterpExterpFunc fragilityCurve) {
		return boundedMerge(hazCurve.getXVals(), fragilityCurve.getXVals());
	}
	
	private ArbitrarilyDiscretizedFunc interp(ArbitrarilyDiscretizedFunc func, double xvals[]) {
		DiscreteInterpExterpFunc dfunc = DiscreteInterpExterpFunc.fromArbDistFunc(func);
		dfunc = interp(dfunc, xvals);
		return getArbDist(dfunc);
	}
	
	private DiscreteInterpExterpFunc interp(DiscreteInterpExterpFunc func, double xvals[]) {
		long start = System.currentTimeMillis();
		double yvals[] = new double[xvals.length];
		for (int i=0; i<xvals.length; i++) {
			double x = xvals[i];
			yvals[i] = func.valueOf(x);
		}
		long end = System.currentTimeMillis();
		interpTime += (end - start);
		return new DiscreteInterpExterpFunc(xvals, yvals);
	}
	
	public static DiscreteInterpExterpFunc[] getInterpolatedCurvesFast(DiscreteInterpExterpFunc hazCurve,
			DiscreteInterpExterpFunc fragilityCurve) {
		long start = System.currentTimeMillis();
		
		double xVals[] = boundedMerge(hazCurve.getXVals(), fragilityCurve.getXVals());
		double hazYVals[] = new double[xVals.length];
		double fragYVals[] = new double[xVals.length];
		for (int i=0; i<xVals.length; i++) {
			double x = xVals[i];
//			interpHazCurve.set(x, interpHazFunc.valueOf(x));
//			interpFragCurve.set(x, interpFragFunc.valueOf(x));
			hazYVals[i] = hazCurve.valueOf(x);
			fragYVals[i] = fragilityCurve.valueOf(x);
		}
		DiscreteInterpExterpFunc result[]  = { new DiscreteInterpExterpFunc(xVals, hazYVals),
				new DiscreteInterpExterpFunc(xVals, fragYVals) };
		long end = System.currentTimeMillis();
		interpTime += (end - start);
		return result;
	}
	
	public static ArbitrarilyDiscretizedFunc[] getInterpolatedCurves(ArbitrarilyDiscretizedFunc hazCurve,
			ArbitrarilyDiscretizedFunc fragilityCurve) {
		long start = System.currentTimeMillis();
		ArbitrarilyDiscretizedFunc interpHazCurve = new ArbitrarilyDiscretizedFunc();
		ArbitrarilyDiscretizedFunc interpFragCurve = new ArbitrarilyDiscretizedFunc();
		double xVals[] = boundedMerge(hazCurve.getXVals(), fragilityCurve.getXVals());
		for (double x : xVals) {
			interpHazCurve.set(x, hazCurve.getInterpExterpY_inLogYDomain(x));
			interpFragCurve.set(x, fragilityCurve.getInterpExterpY_inLogYDomain(x));
		}
		ArbitrarilyDiscretizedFunc result[]  = { interpHazCurve, interpFragCurve};
		long end = System.currentTimeMillis();
		interpTime += (end - start);
		return result;
	}
	
	private DiscreteInterpExterpFunc getNextCurve() throws Exception {
		long start = System.currentTimeMillis();
		DiscreteInterpExterpFunc hazCurve = curveReader.nextDiscreteCurve();
		long end = System.currentTimeMillis();
		fetchTime += (end - start);
		return hazCurve;
	}
	
	private static void printSingleTime(String desc, long val, long total) {
		double secs = val / 1000d;
		double precent = (double)val / (double)total * 100d;
		System.out.println(desc + ": " + secs + "s (" + precent + " %)");
	}
	
	private static void printTimes() {
		long total = interpTime + calcTime + fetchTime;
		
		printSingleTime("interpolate", interpTime, total);
		printSingleTime("calculate", calcTime, total);
		printSingleTime("fetch", fetchTime, total);
	}
	
	public EvenlyDiscrXYZ_DataSet calcRiskMap() throws Exception {
		DiscreteInterpExterpFunc hazCurve = getNextCurve();
		DiscreteInterpExterpFunc fragilityCurve = this.fragilityCurve;
		double loc[];
		
		double xvals[] = getXVals(hazCurve, fragilityCurve);
		DiscreteInterpExterpFunc interpDiscreteFrag = interp(fragilityCurve, xvals);
		ArbitrarilyDiscretizedFunc interpFrag = getArbDist(interpDiscreteFrag);
		
		LossCurveCalculator calc = new LossCurveCalculator();
		
		EvenlyDiscrXYZ_DataSet result = new EvenlyDiscrXYZ_DataSet(ncols, nrows,
				minLon, minLat, gridSpacing);
		
		int count = 0;
		System.out.println("Starting calc!");
		while (hazCurve != null) {
			loc = curveReader.currentLocationArray();
			
//			if (count % 1000 == 0) {
//				System.out.println("Calculating site " + count);
//				printTimes();
//			}
			if (testVals != null && count == 10000) {
				break;
			}
			
			/*			BEGIN CALC			*/
			
//			System.out.println("site " + count + " hazCurve: " + hazCurve.getNum() + " xVals");
			
			// interpolate the curves
			if (interp) {
//				ArbitrarilyDiscretizedFunc interpCurves[] = getInterpolatedCurves(hazCurve, fragilityCurve);
				hazCurve = interp(hazCurve, xvals);
			}
			
			long calcStart = System.currentTimeMillis();
			double val = calc.getLossExceedance(getArbDist(hazCurve), interpFrag);
			long calcEnd = System.currentTimeMillis();
			calcTime += (calcEnd - calcStart);
			
//			System.out.println("X: " + loc[1] + " Y: " + loc[0] + " Z: " + val);
			
			result.set(loc[1], loc[0], val);
			if (testVals != null && count < 10000)
				testVals.add(val);
			
			count++;
			
			/*			END CALC			*/
			
			hazCurve = getNextCurve();
		}
		
		System.out.println("Calculated " + count + " sites!");
		printTimes();
		
		return result;
	}
	
	public static ArbitrarilyDiscretizedFunc getArbDist(DiscreteInterpExterpFunc func) {
		ArbitrarilyDiscretizedFunc afunc = new ArbitrarilyDiscretizedFunc();
		
		double xvals[] = func.getXVals();
		double yvals[] = func.getYVals();
		for (int i=0; i<xvals.length; i++) {
			afunc.set(xvals[i], yvals[i]);
		}
		
		return afunc;
	}
	
	public void setStoreTestVals() {
		testVals = new ArrayList<Double>();
	}
	
	public ArrayList<Double> getTestVals() {
		return testVals;
	}

	/**
	 * @param args
	 * @throws Exception 
	 */
	public static void main(String[] args) throws Exception {
		if (args.length != 4) {
			System.err.println("USAGE: NSHMPRiskMapCalc <hazard curve bin file>" +
					" <fragility xml file> <damage state> <outfile>");
			System.exit(2);
		}
		
//		String curveFile = "/home/kevin/OpenSHA/nico/curve_input_file.bin";
		String curveFile = args[0];
		BinaryHazardCurveReader curveReader = new BinaryHazardCurveReader(curveFile);
//		ArbitrarilyDiscretizedFunc fragilityCurve = new ArbitrarilyDiscretizedFunc();
		
//		String fragFile = "/home/kevin/OpenSHA/nico/Fragility_C1H_High_2p0sec.xml";
		String fragFile = args[1];
		
//		String damageState = "Slight";
		String damageState = args[2];
		
		DiscreteInterpExterpFunc fragilityCurve = DiscreteInterpExterpFunc.fromArbDistFunc(
				FragilityCurveReader.loadFunc(fragFile, damageState));
		
		interp = fragilityCurve.getXVals().length != 20;
		
		long start = System.currentTimeMillis();
		NSHMPRiskMapCalc calc = new NSHMPRiskMapCalc(curveReader, fragilityCurve);
		
		EvenlyDiscrXYZ_DataSet result = calc.calcRiskMap();
		long end = System.currentTimeMillis();
		
		System.out.println("Took " + ((end - start) / 1000d) + " secs");
		
//		String outputFile = "/home/kevin/OpenSHA/nico/testData";
		String outputFile = args[3];
		
		result.writeXYZBinFile(outputFile);
	}
	
	
	
	// decompiled methods from old gov.usgs.util.ArrayUtils.java
	
	  private static double[] boundedMerge(double[] paramArrayOfDouble1, double[] paramArrayOfDouble2)
	  {
	    double d1 = Math.max(Doubles.min(paramArrayOfDouble1), Doubles.min(paramArrayOfDouble2));
	    double d2 = Math.min(Doubles.max(paramArrayOfDouble1), Doubles.max(paramArrayOfDouble2));
	    return merge(trim(paramArrayOfDouble1, d1, d2), trim(paramArrayOfDouble2, d1, d2));
	  }
	  
	  private static double[] trim(double[] paramArrayOfDouble, double paramDouble1, double paramDouble2)
	  {
	    int i = 0;
	    int j = paramArrayOfDouble.length;
	    double[] arrayOfDouble = new double[j];
	    for (int k = 0; k < j; ++k)
	    {
	      if ((paramArrayOfDouble[k] < paramDouble1) || (paramArrayOfDouble[k] > paramDouble2))
	        continue;
	      arrayOfDouble[(i++)] = paramArrayOfDouble[k];
	    }
	    return trim(arrayOfDouble, i);
	  }

	  private static double[] trim(double[] paramArrayOfDouble, int paramInt)
	  {
	    double[] arrayOfDouble = new double[paramInt];
	    System.arraycopy(paramArrayOfDouble, 0, arrayOfDouble, 0, paramInt);
	    return arrayOfDouble;
	  }


		private static double [] merge(double [] vals1, double [] vals2) {
		int i = 0;
		int len1 = vals1.length;
		int len2 = vals2.length;
		int maxlen = len1 + len2;
		double [] allVals = new double[maxlen];
		int idx = 0;

		for (i = 0; i < len1; i++) {
			boolean [] isNewVal = { true, true };
			double [] curVal = {
					vals1[i],
					(len2 > i) ? vals2[i] : (0.0D / 0.0D)
			};

			for (int j = 0; (j < idx) && (isNewVal[0] || isNewVal[1]); j++) {
				isNewVal[0] = ( (isNewVal[0]) && (allVals[i] != curVal[0]) );
				isNewVal[1] = ( (isNewVal[1]) && (allVals[i] != curVal[1]) );
			}

			if (isNewVal[0]) { allVals[idx++] = curVal[0]; }
			if (!isNewVal[1] || (curVal[0] == curVal[1])) { continue; }
			allVals[idx++] = curVal[1];
		}

		for (i = len1; i < len2; i++) {
			boolean isNewVal = true;
			double curVal = vals2[i];

			for (int i5 = 0; (i5 < idx) && isNewVal; ++i5) {
				isNewVal = (isNewVal && (allVals[i] != curVal));
			}
			if (!isNewVal) { continue; }
			allVals[(idx++)] = curVal;
		}

		double [] mergedVals = new double[idx];
		System.arraycopy(allVals, 0, mergedVals, 0, idx);
		Arrays.sort(mergedVals);
		return mergedVals;
	}


}
