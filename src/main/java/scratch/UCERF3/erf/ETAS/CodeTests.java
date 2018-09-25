package scratch.UCERF3.erf.ETAS;

import java.util.ArrayList;

import org.apache.commons.math3.util.ArithmeticUtils;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.IntegerPDF_FunctionSampler;
import org.opensha.commons.gui.plot.GraphWindow;


/**
 * This class holds a bunch of tests for the ETAS classes in this directory
 * (these should be converted to Junit tests)
 * @author field
 *
 */
public class CodeTests {
	
	
	/**
	 * This test the IntegerPDF_FunctionSampler class
	 */
	public boolean testIntegerPDF_FunctionSampler(IntegerPDF_FunctionSampler sampler) {
		
		// make sure sampler is normalized
		double total = sampler.calcSumOfY_Vals();
		sampler.scale(1.0/total);
		
		
		// now check that monte carlo sampling of the function converges to y-axis values
		EvenlyDiscretizedFunc testFunc = new EvenlyDiscretizedFunc(sampler.getX(0), sampler.size(), sampler.getDelta());
		int numSamples=100000000;
		for(int i=0;i<numSamples;i++) {
			testFunc.add(sampler.getRandomInt(),1.0);
		}
		for(int i=0;i<testFunc.size();i++) testFunc.set(i,testFunc.getY(i)/numSamples);
		
/**/		//  Plot comparison
		ArrayList funcs = new ArrayList();
		funcs.add(sampler);
		funcs.add(testFunc);
		GraphWindow sr_graph = new GraphWindow(funcs, "");  
		
		double maxDiff =0;
		for(int i=0;i<testFunc.size();i++) {
			double diff = Math.abs(testFunc.getY(i)-sampler.getY(i));
			if(diff>maxDiff) maxDiff = diff;
		}
//		System.out.println("testIntegerPDF_FunctionSampler maxDiff="+maxDiff);
		
		if(maxDiff < 1e-4) 
			return true;
		else
			return false;
			
	}
	
	
	public boolean testGetDefaultRandomTimeOfEvent(double tMin, double tMax, double tDelta) {
		
		ETAS_Utils utils = new ETAS_Utils();
		
		// make the target function & change it to a PDF
		EvenlyDiscretizedFunc targetFunc = utils.getDefaultNumWithTimeFunc(5, tMin, tMax, tDelta);
		double sum=0;
		for(int i=0; i<targetFunc.size();i++) sum += targetFunc.getY(i);
		for(int i=0; i<targetFunc.size();i++) targetFunc.set(i, targetFunc.getY(i)/sum);
		
		// now test against a histogram filled from random samples
		EvenlyDiscretizedFunc histogram = new EvenlyDiscretizedFunc(tMin+tDelta/2, tMax-tDelta/2, (int)Math.round((tMax-tMin)/tDelta));
		histogram.setTolerance(tDelta);
		int numSamples = 10000000;
		for(int i=0;i<numSamples;i++) {
			histogram.add(utils.getDefaultRandomTimeOfEvent(tMin, tMax), 1.0);
		}
		for(int i=0; i<histogram.size();i++)
			histogram.set(i,histogram.getY(i)/(numSamples));
		
		// plot functions
		ArrayList funcs = new ArrayList();
		funcs.add(targetFunc);
		funcs.add(histogram);
		GraphWindow sr_graph = new GraphWindow(funcs, "");  
		
		double maxDiff =0;
		for(int i=0;i<targetFunc.size();i++) {
			double diff = Math.abs(targetFunc.getY(i)-histogram.getY(i));
			if(diff>maxDiff) maxDiff = diff;
		}
		System.out.println("testGetDefaultRandomTimeOfEvent maxDiff="+maxDiff);
		
		if(maxDiff < 1e-4) 
			return true;
		else
			return false;

	}
	
	
	public boolean testGetPoissonRandomNumber(double lambda) {
		int maxNum = 20;
		
		ETAS_Utils utils = new ETAS_Utils();
		
		// compute the Poisson probability for the number of events being 0, 1, 2, ...
		EvenlyDiscretizedFunc targetFunc = new EvenlyDiscretizedFunc(0.0,maxNum,1.0);
		for(int i=0; i<maxNum;i++) {
			double y = Math.pow(lambda, i)*Math.exp(-lambda) / ArithmeticUtils.factorial(i);
//			System.out.println(i+"\t"+y);
			targetFunc.set(i, y);
		}
		
		// now populate a histogram using random values
		EvenlyDiscretizedFunc histogram = new EvenlyDiscretizedFunc(0.0,maxNum,1.0);
		histogram.setTolerance(1);
		int numSamples = 100000000;
		for(int i=0;i<numSamples;i++) {
			int index = utils.getPoissonRandomNumber(lambda);
			if(index <maxNum) histogram.add(index, 1.0);
		}
		for(int i=0; i<histogram.size();i++)
			histogram.set(i,histogram.getY(i)/(numSamples));
		
		// plot functions
/*		ArrayList funcs = new ArrayList();
		funcs.add(targetFunc);
		funcs.add(histogram);
		GraphWindow sr_graph = new GraphWindow(funcs, ""); 
*/		
		double maxDiff =0;
		for(int i=0;i<targetFunc.size();i++) {
			double diff = Math.abs(targetFunc.getY(i)-histogram.getY(i));
			if(diff>maxDiff) maxDiff = diff;
		}
		System.out.println("testGetPoissonRandomNumber maxDiff="+maxDiff);
		
		if(maxDiff < 1e-4) 
			return true;
		else
			return false;



	}
	




	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		
		CodeTests tests = new CodeTests();
		
/*		
		// this tests IntegerPDF_FunctionSampler:
		int numIntegers = 100;
		IntegerPDF_FunctionSampler sampler = new IntegerPDF_FunctionSampler(numIntegers);
		double total=0;
		for(int i=0;i<numIntegers;i++)
			sampler.set(i,Math.random());
		sampler.set(50,0.0);
		sampler.set(51,0.0);
		for(int i=0;i<numIntegers;i++)
			total+=sampler.getY(i);
		for(int i=0;i<numIntegers;i++)
			sampler.set(i,sampler.getY(i)/total);
		System.out.println("testIntegerPDF_FunctionSampler passes = "+tests.testIntegerPDF_FunctionSampler(sampler));
*/		
		// This tests ETAS_Utils.getDefaultRandomTimeOfEvent(*)
//		System.out.println("testGetDefaultRandomTimeOfEvent passes = "+tests.testGetDefaultRandomTimeOfEvent(0, 10, 0.01));

		// This tests ETAS_Utils.getPoissonRandomNumber(lambda)
//		System.out.println("testGetPoissonRandomNumber passes = "+tests.testGetPoissonRandomNumber(5));
		 
	}
	
	

}
