package org.opensha.sha.magdist;

import java.util.ArrayList;
import java.util.Collection;

import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.HistogramFunction;
import org.opensha.commons.eq.MagUtils;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Ints;

/**
 * Utility class that solves for a G-R equivalent MFD accounting for holes in available ruptures. Input is a
 * target b-value, total moment rate, and list of available rupture magnitudes. Output is a G-R equivalent using
 * only available magnitude bins.
 * <p>
 * The {@link SpreadingMethod} can be specified in order to control how rates are spread from bins without magnitudes
 * to bins with them, but NEAREST seems to work best. 
 * @author kevin
 *
 */
public class SparseGutenbergRichterSolver {
	
	public enum SpreadingMethod {
		PREV,
		NEAREST,
		ALL,
		NEAREST_GROUP
	}
	
	public static SpreadingMethod METHOD_DEFAULT = SpreadingMethod.NEAREST_GROUP;
	
	/**
	 * Calculates a G-R distribution for the given total moment rate and target b-value, only using magnitude bins
	 * that contain at least 1 rupture.
	 * 
	 * @param refFunc reference function (determines binning, returned G-R will have these x-values)
	 * @param mags available magnitudes for this G-R
	 * @param totMoRate total moment rate to fit
	 * @param targetBValue target b-value to fit
	 * @return G-R only using the given magnitude bins, fitting the total moment rate and b-value
	 */
	public static IncrementalMagFreqDist getEquivGR(EvenlyDiscretizedFunc refFunc, Collection<Double> mags,
			double totMoRate, double targetBValue) {
		return getEquivGR(refFunc, mags, totMoRate, targetBValue, 0d, METHOD_DEFAULT, false);
	}
	
	/**
	 * Calculates a G-R distribution for the given total moment rate and target b-value, only using magnitude bins
	 * that contain at least 1 rupture.
	 * 
	 * @param refFunc reference function (determines binning, returned G-R will have these x-values)
	 * @param mags available magnitudes for this G-R
	 * @param totMoRate total moment rate to fit
	 * @param targetBValue target b-value to fit
	 * @param sampleDiscr sampling to use when distributing rates. Small values here (compared to the reference gridding)
	 * will super-sample the distribution, which may be more accurate but doesn't seem necessary
	 * @param method method used to spread ruptures from empty bins to neighboring bins
	 * @param preserveRates if true, total event rate will be preserved (default preserves moment-rate)
	 * @return G-R only using the given magnitude bins, fitting the total moment rate and b-value
	 */
	public static IncrementalMagFreqDist getEquivGR(EvenlyDiscretizedFunc refFunc, Collection<Double> mags,
			double totMoRate, double targetBValue, double sampleDiscr, SpreadingMethod method, boolean preserveRates) {
		double minMag = Double.POSITIVE_INFINITY;
		double maxMag = Double.NEGATIVE_INFINITY;
		for (double mag : mags) {
			minMag = Math.min(minMag, mag);
			maxMag = Math.max(maxMag, mag);
		}
		int minIndex = refFunc.getClosestXIndex(minMag);
		int maxIndex = refFunc.getClosestXIndex(maxMag);
		
		if (minIndex == maxIndex) {
			// single bin, just populate that bin
			IncrementalMagFreqDist ret = new IncrementalMagFreqDist(refFunc.getMinX(), refFunc.size(), refFunc.getDelta());
			ret.set(minIndex, 1d);
			ret.scaleToTotalMomentRate(totMoRate);
			return ret;
		}
		
		// gridding for super-sampled function from min to max actual magnitude
		EvenlyDiscretizedFunc superSampledDiscretization;
		if (sampleDiscr >= refFunc.getDelta() || sampleDiscr == 0d)
			superSampledDiscretization = new EvenlyDiscretizedFunc(
					refFunc.getX(minIndex), 1+maxIndex-minIndex, refFunc.getDelta());
		else
			superSampledDiscretization = HistogramFunction.getEncompassingHistogram(
					minMag, maxMag, sampleDiscr);
		// true for each sampled mag bin if there is at least 1 rupture;
		boolean[] superSampledParticipation = new boolean[superSampledDiscretization.size()];
		for (double mag : mags)
			superSampledParticipation[superSampledDiscretization.getClosestXIndex(mag)] = true;
		
		IncrementalMagFreqDist ret = new IncrementalMagFreqDist(refFunc.getMinX(), refFunc.size(), refFunc.getDelta());
		
		GutenbergRichterMagFreqDist superSampledGR = new GutenbergRichterMagFreqDist(
				superSampledDiscretization.getMinX(), superSampledDiscretization.size(),
				superSampledDiscretization.getDelta(), totMoRate, targetBValue);
		
		double targetRate = superSampledGR.calcSumOfY_Vals();
		
		int prevNonEmptyIndex = -1;
		for (int i=0; i<superSampledGR.size(); i++) {
			if (superSampledParticipation[i]) {
				ret.add(ret.getClosestXIndex(superSampledGR.getX(i)), superSampledGR.getY(i));
				prevNonEmptyIndex = i;
			} else {
				Preconditions.checkState(i > 0 && i < superSampledGR.size()-1,
						"First and last bins of super-sampled should always have a rupture");
				int[] assignedBins;
				
				if (method == SpreadingMethod.PREV) {
					assignedBins = new int[] {prevNonEmptyIndex};
				} else if (method == SpreadingMethod.NEAREST || method == SpreadingMethod.NEAREST_GROUP) {
					int numAway = 1;
					while (true) {
						int upperIndex = i+numAway;
						int lowerIndex = i-numAway;
						boolean upper = upperIndex < superSampledParticipation.length && superSampledParticipation[upperIndex];
						boolean lower = lowerIndex >= 0 && superSampledParticipation[lowerIndex];
						if (upper || lower) {
							if (upper && lower)
								assignedBins = new int[] { lowerIndex, upperIndex};
							else if (upper)
								assignedBins = new int[] { upperIndex};
							else
								assignedBins = new int[] { lowerIndex};
							break;
						}
						numAway++;
					}
					if (method == SpreadingMethod.NEAREST_GROUP) {
						// expand to include the full contiguous group(s) of nonzero bins
						ArrayList<Integer> netAssignedBins = new ArrayList<>();
						for (int startBin : assignedBins) {
							int direction = startBin > i ? 1 : -1;
							for (int bin=startBin;
									bin>=0 && bin <superSampledParticipation.length && superSampledParticipation[bin];
									bin+=direction) {
								netAssignedBins.add(bin);
							}
						}
						assignedBins = Ints.toArray(netAssignedBins);
					}
				} else if (method == SpreadingMethod.ALL) {
					// dealt with externally
					assignedBins = new int[0];
				} else {
					throw new IllegalStateException("Unsupported method: "+method);
				}
				
				
				double moRatePerAssignment = superSampledGR.getMomentRate(i)/(double)assignedBins.length;
				double ratePerAssignment = superSampledGR.getIncrRate(i)/(double)assignedBins.length;
				for (int assignedBin : assignedBins) {
					if (preserveRates) {
						// assign the rate to the given
						double myTargetRate = ratePerAssignment;
						ret.add(ret.getClosestXIndex(superSampledGR.getX(assignedBin)), myTargetRate);
					} else {
						// assign the moment to the given bin
//						double targetMag = superSampledGR.getX(assignedBin);
						int retIndex = ret.getClosestXIndex(superSampledGR.getX(assignedBin));
						double targetMag = ret.getX(retIndex);
						double targetMo = MagUtils.magToMoment(targetMag);
						double myTargetRate = moRatePerAssignment/targetMo;
						ret.add(retIndex, myTargetRate);
					}
				}
			}
		}
		
		if (method == SpreadingMethod.ALL) {
			if (preserveRates)
				ret.scaleToCumRate(0, targetRate);
			else
				ret.scaleToTotalMomentRate(totMoRate);
		}
		
		if (preserveRates)
			Preconditions.checkState((float)targetRate == (float)ret.calcSumOfY_Vals(), "Target rate mismatch: %s != %s",
				(float)targetRate, (float)ret.calcSumOfY_Vals());
		// TODO: moment here is calculated using the bin center, but ours (if super-sampled) is integrated
		// across the bin and thus more accurate, so they shouldn't match
		else
			Preconditions.checkState((sampleDiscr < refFunc.getDelta() && sampleDiscr > 0d) ||
				(float)totMoRate == (float)ret.getTotalMomentRate(),
				"Target moment rate mismatch: %s != %s",
				(float)totMoRate, (float)ret.getTotalMomentRate());
		
		return ret;
	}

//	public static void main(String[] args) throws IOException {
////		FaultSystemRupSet rupSet = FaultSystemRupSet.load(new File("/tmp/rupture_set.zip"));
//		FaultSystemRupSet rupSet = FaultSystemRupSet.load(
//				new File("/home/kevin/markdown/inversions/fm3_1_u3ref_uniform_reproduce_ucerf3.zip"));
//		
//		boolean preserveRates = false;
//		SpreadingMethod method = SpreadingMethod.NEAREST;
////		double sampleDiscr = 0.0;
//		double sampleDiscr = 0.01;
//
//		DefaultXY_DataSet scatter1 = new DefaultXY_DataSet();
//		DefaultXY_DataSet scatter2 = new DefaultXY_DataSet();
//		
//		DefaultXY_DataSet totRateScatter = new DefaultXY_DataSet();
//		DefaultXY_DataSet totMoRateScatter = new DefaultXY_DataSet();
//		
//		DefaultXY_DataSet scatter1Avg = new DefaultXY_DataSet();
//		DefaultXY_DataSet scatter2Avg = new DefaultXY_DataSet();
//		
//		int debugIndex = 100;
//		
////		for (double bValue : new double[] {0.0, 0.2, 0.4, 0.6, 0.8, 1.0, 1.2, 1.4}) {
//		for (double bValue : new double[] {0.0, 0.4, 0.8, 1.2}) {
//			System.out.println("Doing b="+bValue);
//			SupraSeisBValInversionTargetMFDs targets = new SupraSeisBValInversionTargetMFDs.Builder(rupSet, bValue).sparseGR(false).build();
//
//			MinMaxAveTracker equivBTrack = new MinMaxAveTracker();
//			MinMaxAveTracker equivBTrack2 = new MinMaxAveTracker();
//			
//			for (int s=0; s<rupSet.getNumSections(); s++) {
//				IncrementalMagFreqDist orig = targets.getSectSupraSeisNuclMFDs().get(s);
//				
//				int minNonZero = -1;
//				int maxNonZero = 0;
//				for (int i=0; i<orig.size(); i++) {
//					if (orig.getY(i) > 0d) {
//						if (minNonZero < 0)
//							minNonZero = i;
//						maxNonZero = i;
//					}
//				}
//				if (minNonZero == maxNonZero)
//					continue;
//				GutenbergRichterMagFreqDist gr = new GutenbergRichterMagFreqDist(
//						orig.getX(minNonZero), 1+maxNonZero-minNonZero, orig.getDelta(), orig.getTotalMomentRate(), bValue);
//				
//				if ((float)gr.getTotalIncrRate() != (float)orig.getTotalIncrRate()) {
//					System.err.println("WARNING: rate mismatch on input for "+s+": "
//							+(float)gr.getTotalIncrRate()+" != "+(float)orig.getTotalIncrRate());
//					for (int i=0; i<orig.size(); i++) {
//						double x = orig.getX(i);
//						double y1 = orig.getY(i);
//						if (gr.hasX(x)) {
//							double y2 = gr.getY(i);
//							System.out.println(x+"\t"+y1+"\t"+y2);
//						} else {
//							Preconditions.checkState(y1 == 0d);
//						}
//					}
//				}
//				
//				double minMag = gr.getMinX() - 0.5*gr.getDelta();
//				
//				double minEncounter = Double.POSITIVE_INFINITY;
//				double minAdded = Double.POSITIVE_INFINITY;
//				double maxAdded = 0d;
//				List<Double> mags = new ArrayList<>();
//				for (int r : rupSet.getRupturesForSection(s)) {
//					double mag = rupSet.getMagForRup(r);
//					minEncounter = Math.min(minEncounter, mag);
//					if ((float)mag >= (float)minMag) {
//						mags.add(mag);
//						minAdded = Math.min(minAdded, mag);
//					}
//					maxAdded = Math.max(maxAdded, mag);
//				}
////				System.out.println(s+". minEncountered="+(float)minEncounter+"\tminAdded="
////						+(float)minAdded+"\tfirstBin="+(float)gr.getMinX()+"\tlowerEdge="+(float)minMag);
//				
//				IncrementalMagFreqDist sparseGR = getEquivGR(orig, mags, orig.getTotalMomentRate(),
//						bValue, sampleDiscr, method, preserveRates);
////				IncrementalMagFreqDist sparseGR = invertEquivGR(orig, mags, orig.getTotalMomentRate(), bValue);
//				
//				double equivB = SectBValuePlot.estBValue(gr.getMinX(), gr.getMaxX(),
//						sparseGR.getTotalIncrRate(), sparseGR.getTotalMomentRate());
//				scatter1.set(bValue, equivB);
//				equivBTrack.addValue(equivB);
//				
////				equivB = SectBValuePlot.estBValue(minAdded, maxAdded,
////						sparseGR.getTotalIncrRate(), sparseGR.getTotalMomentRate());
//				GutenbergRichterMagFreqDist testGR = new GutenbergRichterMagFreqDist(minAdded, maxAdded, 100);
//				testGR.setAllButBvalue(minAdded, maxAdded, sparseGR.getTotalMomentRate(), sparseGR.getTotalIncrRate());
////				GutenbergRichterMagFreqDist testGR = new GutenbergRichterMagFreqDist(gr.getMinX(), gr.getMaxX(), gr.size());
////				testGR.setAllButBvalue(gr.getMinX(), gr.getMaxX(), gr.getTotalMomentRate(), gr.getTotalIncrRate());
//				
//				double equivB2 = testGR.get_bValue();
//				scatter2.set(bValue, equivB2);
//				equivBTrack2.addValue(equivB2);
//				
//				totRateScatter.set(orig.getTotalIncrRate(), sparseGR.getTotalIncrRate());
//				totMoRateScatter.set(orig.getTotalMomentRate(), sparseGR.getTotalMomentRate());
//				
//				if (s == debugIndex) {
//					List<XY_DataSet> funcs = new ArrayList<>();
//					List<PlotCurveCharacterstics> chars = new ArrayList<>();
//					
//					funcs.add(gr);
//					chars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 1f, Color.BLACK));
//					
//					funcs.add(sparseGR);
//					chars.add(new PlotCurveCharacterstics(PlotSymbol.FILLED_SQUARE, 5f, Color.RED));
//					
//					MinMaxAveTracker nonZeroTrack = new MinMaxAveTracker();
//					for (XY_DataSet func : funcs)
//						for (Point2D pt : func)
//							if (pt.getY() > 0)
//								nonZeroTrack.addValue(pt.getY());
//					
//					Range yRange = new Range(Math.min(1e-8, nonZeroTrack.getMin()*0.9), nonZeroTrack.getMax()*1.1);
//					
//					DefaultXY_DataSet magFunc = new DefaultXY_DataSet();
//					for (double mag : mags)
//						magFunc.set(mag, yRange.getLowerBound());
//					funcs.add(magFunc);
//					chars.add(new PlotCurveCharacterstics(PlotSymbol.FILLED_SQUARE, 5f, Color.GREEN.darker()));
//					
//					double origRate = sampleDiscr == 0d ? gr.calcSumOfY_Vals() : testGR.calcSumOfY_Vals();
//					
//					String title = rupSet.getFaultSectionData(s).getSectionName()+" , b="+(float)bValue
//							+" , bEst="+(float)equivB+", GR rate="+(float)origRate
//							+", new rate="+(float)sparseGR.calcSumOfY_Vals();
//					GraphWindow gw = new GraphWindow(funcs, title, chars);
//					gw.setYLog(true);
//					gw.setX_AxisRange(gr.getMinX()-0.5*gr.getDelta(), gr.getMaxX()+0.5*gr.getDelta());
//					gw.setY_AxisRange(yRange);
//					gw.setDefaultCloseOperation(GraphWindow.EXIT_ON_CLOSE);
//				}
//			}
//			System.out.println("Equivalent b-value distributions (w/ snapped min/max) for b="+(float)bValue+": "+equivBTrack);
//			System.out.println("Equivalent b-value distributions (w/ actual min/max) for b="+(float)bValue+": "+equivBTrack2);
//			
//			scatter1Avg.set(bValue, equivBTrack.getAverage());
//			scatter2Avg.set(bValue, equivBTrack2.getAverage());
//		}
//		List<XY_DataSet> funcs = new ArrayList<>();
//		List<PlotCurveCharacterstics> chars = new ArrayList<>();
//		
//		funcs.add(scatter1);
//		chars.add(new PlotCurveCharacterstics(PlotSymbol.CROSS, 3f, Color.BLACK));
//		funcs.add(scatter1Avg);
//		chars.add(new PlotCurveCharacterstics(PlotSymbol.FILLED_CIRCLE, 4f, Color.GREEN.darker()));
//		GraphWindow gw = new GraphWindow(funcs, "B-Value Scatter 1", chars);
//		gw.setDefaultCloseOperation(GraphWindow.EXIT_ON_CLOSE);
//		
//		funcs.clear();
//		chars.clear();
//		funcs.add(scatter2);
//		chars.add(new PlotCurveCharacterstics(PlotSymbol.CROSS, 3f, Color.BLUE));
//		funcs.add(scatter2Avg);
//		chars.add(new PlotCurveCharacterstics(PlotSymbol.FILLED_CIRCLE, 4f, Color.GREEN.darker()));
//		gw = new GraphWindow(funcs, "B-Value Scatter 2", chars);
//		gw.setDefaultCloseOperation(GraphWindow.EXIT_ON_CLOSE);
//		
//		funcs.clear();
//		chars.clear();
//		funcs.add(totRateScatter);
//		chars.add(new PlotCurveCharacterstics(PlotSymbol.CROSS, 3f, Color.GREEN.darker()));
//		Range range = calcBoundedLogRange(totRateScatter);
//		funcs.add(oneToOne(range));
//		chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 2f, Color.GRAY));
//		gw = new GraphWindow(funcs, "Total Rate Scatter", chars);
//		gw.setXLog(true);
//		gw.setYLog(true);
//		gw.setAxisRange(range, range);
//		gw.setDefaultCloseOperation(GraphWindow.EXIT_ON_CLOSE);
//		
//		funcs.clear();
//		chars.clear();
//		funcs.add(totMoRateScatter);
//		chars.add(new PlotCurveCharacterstics(PlotSymbol.CROSS, 3f, Color.RED.darker()));
//		range = calcBoundedLogRange(totMoRateScatter);
//		funcs.add(oneToOne(range));
//		chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 2f, Color.GRAY));
//		gw = new GraphWindow(funcs, "Moment Rate Scatter", chars);
//		gw.setXLog(true);
//		gw.setYLog(true);
//		gw.setAxisRange(range, range);
//		gw.setDefaultCloseOperation(GraphWindow.EXIT_ON_CLOSE);
//	}
//	
//	private static Range calcBoundedLogRange(XY_DataSet scatter) {
//		double min = Math.min(scatter.getMinX(), scatter.getMinY());
//		Preconditions.checkState(min > 0);
//		double max = Math.max(scatter.getMaxX(), scatter.getMaxY());
//		Preconditions.checkState(max > 0);
//		
//		return new Range(Math.pow(10, Math.floor(Math.log10(min))), Math.pow(10, Math.ceil(Math.log10(max))));
//	}
//	
//	private static XY_DataSet oneToOne(Range range) {
//		DefaultXY_DataSet line = new DefaultXY_DataSet();
//		line.set(range.getLowerBound(), range.getLowerBound());
//		line.set(range.getUpperBound(), range.getUpperBound());
//		return line;
//	}

}
