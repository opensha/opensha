package org.opensha.sha.earthquake.rupForecastImpl.prvi25.gridded;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSysTools;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.base.Preconditions;

public class SeismicityRateFileLoader {
	
	public static enum RateType {
		M1("M1 Branches"),
		M1_TO_MMAX("M1-Mmax Branches"),
		EXACT("Exact Branches");
		
		private final String fileHeading;

		private RateType(String fileHeading) {
			this.fileHeading = fileHeading;
		}
		
		@Override
		public String toString() {
			return fileHeading;
		}
	}
	
	public static abstract class RateRecord {
		public final RateType type;
		public final double M1;
		public final double rateAboveM1;
		public final double quantile;
		public final boolean mean;
		
		private RateRecord(RateType type, double M1, double rateAboveM1, double quantile, boolean mean) {
			super();
			this.type = type;
			this.M1 = M1;
			this.rateAboveM1 = rateAboveM1;
			Preconditions.checkState(mean || quantile >= 0d && quantile <= 1d, "Bad quantile: %s", quantile);
			this.quantile = quantile;
			this.mean = mean;
		}
	}
	
	public static class PureGR extends RateRecord {
		public final double Mmax;
		public final double b;
		
		private PureGR(RateType type, double M1, double Mmax, double rateAboveM1, double b, double quantile, boolean mean) {
			super(type, M1, rateAboveM1, quantile, mean);
			this.Mmax = Mmax;
			this.b = b;
		}

		@Override
		public String toString() {
			return "PureGR [M1="+(float)M1+", Mmax="+(float)Mmax+", rateAboveM1="+(float)rateAboveM1
					+", b="+(float)b+", quantile="+(float)quantile+ ", mean="+mean+"]";
		}
	}
	
	public static class Exact extends RateRecord {
		public final EvenlyDiscretizedFunc cumulativeDist;
		
		private Exact(double M1, EvenlyDiscretizedFunc cumulativeDist, double quantile, boolean mean) {
			super(RateType.EXACT, M1, cumulativeDist.getY(cumulativeDist.getClosestXIndex(M1)), quantile, mean);
			this.cumulativeDist = cumulativeDist;
		}
	}
	
	public static IncrementalMagFreqDist buildIncrementalMFD(RateRecord record, EvenlyDiscretizedFunc refMFD, double mMax) {
		Preconditions.checkNotNull(record, "Record is null");
		if (record instanceof PureGR)
			return buildIncrementalMFD((PureGR)record, refMFD, mMax);
		else if (record instanceof Exact)
			return buildIncrementalMFD((Exact)record, refMFD, mMax);
		else
			throw new IllegalStateException("Record is of unexpected type: "+record.getClass());
	}
	
	public static IncrementalMagFreqDist buildIncrementalMFD(PureGR grRecord, EvenlyDiscretizedFunc refMFD, double mMax) {
		Preconditions.checkState(mMax-0.001 < refMFD.getMaxX()+0.5*refMFD.getDelta(),
				"Reference incremental MFD doesn't have a bin for mMax=%s", mMax);
		GutenbergRichterMagFreqDist gr = new GutenbergRichterMagFreqDist(refMFD.getMinX(), refMFD.size(), refMFD.getDelta());
		
		// this sets shape, min/max
		// subtract a tiny amount from mMax so that if it's exactly at a bin edge, e.g. 7.9, it rounds down, e.g. to 7.85
		gr.setAllButTotCumRate(refMFD.getX(0), refMFD.getX(refMFD.getClosestXIndex(mMax-0.001)), 1e16, grRecord.b);
		// this scales it to match
		// similarly, add a tiny amount to M1 so that if it's exactly at a bin edge (which it should be as it's determined
		// using cumulative binning), it rounds up to the incremental bin for that cumulative edge
		gr.scaleToCumRate(refMFD.getClosestXIndex(grRecord.M1+0.001), grRecord.rateAboveM1);
		
		gr.setName("Total Observed [b="+oDF.format(grRecord.b)
				+", N"+oDF.format(grRecord.M1)+"="+oDF.format(grRecord.rateAboveM1)+"]");
		
		return gr;
	}
	
	public static IncrementalMagFreqDist buildIncrementalMFD(Exact exactRecord, EvenlyDiscretizedFunc refMFD, double mMax) {
		// make sure the reference and data MFDs have the same spacing
		double delta = refMFD.getDelta();
		Preconditions.checkState((float)exactRecord.cumulativeDist.getDelta() == (float)delta,
				"MFD spacing mismatch between reference (%s) and data (%s)",
				(float)delta, (float)exactRecord.cumulativeDist.getDelta());
		// make sure the supplied Mmax maps to both distributions
		// reference mfd should be incremental, so check against the bin edge
		Preconditions.checkState(mMax-0.001 < refMFD.getMaxX()+0.5*refMFD.getDelta(),
				"Reference incremental MFD doesn't have a bin for mMax=%s", mMax);
		// data MFD is cumulative, so check Mmax values directly
		Preconditions.checkState((float)mMax < (float)exactRecord.cumulativeDist.getMaxX(),
				"Data cumulative MFD doesn't have a bin for mMax=%s", mMax);
		// make sure the reference Mmin is above the data Mmin (above because it's incremental)
		double mMinData = exactRecord.cumulativeDist.getMinX();
		double mMinIncr = refMFD.getMinX();
		Preconditions.checkState(mMinIncr > mMinData, "Reference MFD Mmin=%s must be > data cumulative Mmin=%s",
				mMinIncr, mMinData);
		// make sure the data cumulative is offset from the reference incremental
		// e.g., incremental should be at 5.05, 5.15, 5.25, etc
		// but cumulative should be at 5.0, 5.1, 5.2, etc
		double minIncrLowerEdge = mMinIncr - 0.5*delta;
		double mMinMatchingData = exactRecord.cumulativeDist.getX(exactRecord.cumulativeDist.getClosestXIndex(minIncrLowerEdge));
		Preconditions.checkState((float)mMinMatchingData == (float)minIncrLowerEdge,
				"Discretization mismatch; incremental should be offset by a half bin from the cumulative. e.g.,"
				+ "incremental min value at %s representing bin [%s, %s]; cumulative should have a bin a %s, but closest is %s",
				(float)mMinIncr, (float)minIncrLowerEdge, (float)(mMinIncr+0.5*delta), (float)minIncrLowerEdge, (float)mMinMatchingData);
		// find incremental index of Mmax; it's probably (hopefully) supplied at a cumulative bin edge 
		int mMaxRefBin = refMFD.getClosestXIndex(mMax-0.001);
		// find the data bin corresponding to the first incremental
		int dataBin0 = exactRecord.cumulativeDist.getClosestXIndex(mMinIncr-0.01);
		
		// ok, got all of the binning and tests out of the way, lets build the thing
		IncrementalMagFreqDist incr = new IncrementalMagFreqDist(refMFD.getMinX(), refMFD.size(), delta);
		for (int i=0; i<=mMaxRefBin; i++) {
			int cmlI = dataBin0 + i;
			Preconditions.checkState(cmlI < exactRecord.cumulativeDist.size());
			double binStart = exactRecord.cumulativeDist.getY(cmlI);
			double binEnd;
			if (cmlI+1 == exactRecord.cumulativeDist.size())
				binEnd = 0d;
			else
				binEnd = exactRecord.cumulativeDist.getY(cmlI+1);
			Preconditions.checkState(binStart >= binEnd);
			incr.set(i, binStart - binEnd);
		}
		
		// rescale to match the rate above M1
		int incrM1index = incr.getClosestXIndex(exactRecord.M1+0.001); // add 0.001 to snap to the incremental bin above the cumulative magnitude
		double incrAboveM1 = incr.getCumRate(incrM1index);
		double scalar = exactRecord.rateAboveM1/incrAboveM1;
		incr.scale(scalar);
		return incr;
	}
	
	private static final DecimalFormat oDF = new DecimalFormat("0.##");

	private static final String MMAX_FIELD_NAME = "Mmax";
	private static final String M1_FIELD_NAME = "M1";
	private static final String N_QUANTILES_FIELD_NAME = "n quantiles";
	private static final String N_MAGNITUDES_FIELD_NAME = "n magnitudes";
	private static final double MEAN_QUANT_FLAG = 2d;
	
	public static List<? extends RateRecord> loadRecords(CSVFile<String> csv, RateType type) {
		if (type == RateType.EXACT)
			return loadExactBranches(csv);
		return loadPureGR_branches(csv, type);
	}
	
	private static List<PureGR> loadPureGR_branches(CSVFile<String> csv, RateType type) {
		Double M1 = null;
		Double Mmax = null;
		
		for (int row=0; row<csv.getNumRows(); row++) {
			String col1 = csv.get(row, 0);
			if (col1 == null || col1.isBlank())
				continue;
			col1 = col1.trim();
			if (M1 == null && col1.equals(M1_FIELD_NAME))
				M1 = csv.getDouble(row, 1);
			if (Mmax == null && col1.equals(MMAX_FIELD_NAME))
				Mmax = csv.getDouble(row, 1);
			if (col1.equals(type.fileHeading)) {
				// start reading
				Preconditions.checkNotNull(M1, "'%s' not found before reading quantiles", M1_FIELD_NAME);
				Preconditions.checkNotNull(Mmax, "'%s' not found before reading quantiles", MMAX_FIELD_NAME);
				Integer nQuantiles = null;
				while (++row < csv.getNumRows()) {
					col1 = csv.get(row, 0);
					if (col1 == null || col1.isBlank())
						continue;
					col1 = col1.trim();
					if (col1.equals(N_QUANTILES_FIELD_NAME)) {
						// found it
						nQuantiles = csv.getInt(row, 1);
						row++;
						break;
					}
				}
				Preconditions.checkNotNull(nQuantiles, "Expected to find '%s' before reading quantiles", N_QUANTILES_FIELD_NAME);
				row++; // skip header
				List<PureGR> ret = new ArrayList<>(nQuantiles);
				for (int i=0; i<nQuantiles; i++) {
					Preconditions.checkState(row<csv.getNumRows(), "Ran out of rows befor reaching nQuantiles=%s", nQuantiles);
					double quantile = csv.getDouble(row, 0);
					double rate = csv.getDouble(row, 1);
					double b = csv.getDouble(row, 2);
					
					boolean mean = (float)quantile == (float)MEAN_QUANT_FLAG;
					ret.add(new PureGR(type, M1, Mmax, rate, b, mean ? Double.NaN : quantile, mean));
					row++;
				}
				return ret;
			}
			
		}
		throw new IllegalStateException("Never found '"+type.fileHeading+"'");
	}
	
	public static List<Exact> loadExactBranches(CSVFile<String> csv) {
		Double M1 = null;
		
		for (int row=0; row<csv.getNumRows(); row++) {
			String col1 = csv.get(row, 0);
			if (col1 == null || col1.isBlank())
				continue;
			col1 = col1.trim();
			if (M1 == null && col1.equals(M1_FIELD_NAME))
				M1 = csv.getDouble(row, 1);
			if (col1.equals(RateType.EXACT.fileHeading)) {
				// start reading
				Preconditions.checkNotNull(M1, "'%s' not found before reading quantiles", M1_FIELD_NAME);
				Integer nQuantiles = null;
				while (++row < csv.getNumRows()) {
					col1 = csv.get(row, 0);
					if (col1 == null || col1.isBlank())
						continue;
					col1 = col1.trim();
					if (col1.equals(N_QUANTILES_FIELD_NAME)) {
						// found it
						nQuantiles = csv.getInt(row, 1);
						break;
					}
				}
				Preconditions.checkNotNull(nQuantiles, "Expected to find '%s' before reading exact distributions", N_QUANTILES_FIELD_NAME);
				Integer nMagnitudes = null;
				while (++row < csv.getNumRows()) {
					col1 = csv.get(row, 0);
					if (col1 == null || col1.isBlank())
						continue;
					col1 = col1.trim();
					if (col1.equals(N_MAGNITUDES_FIELD_NAME)) {
						// found it
						nMagnitudes = csv.getInt(row, 1);
						row++;
						break;
					}
				}
				Preconditions.checkNotNull(nMagnitudes, "Expected to find '%s' before reading exact distributions", N_MAGNITUDES_FIELD_NAME);
				row++; // skip header
				double[] quantiles = new double[nQuantiles];
				// read in quantiles
				for (int n=0; n<nQuantiles; n++)
					quantiles[n] = csv.getDouble(row, n+1);
				row++; // now at first magnitude
				double mag1 = csv.getDouble(row, 0);
				double mag2 = csv.getDouble(row+1, 0);
				Preconditions.checkState(mag2 > mag1);
				double delta = mag2-mag1;
				EvenlyDiscretizedFunc[] funcs = new EvenlyDiscretizedFunc[nQuantiles];
				for (int i=0; i<nQuantiles; i++)
					funcs[i] = new EvenlyDiscretizedFunc(mag1, nMagnitudes, delta);
				for (int m=0; m<nMagnitudes; m++) {
					Preconditions.checkState(row<csv.getNumRows(), "Ran out of rows befor reaching nMagnitudes=%s", nMagnitudes);
					double mag = funcs[0].getX(m);
					double csvMag = csv.getDouble(row, 0);
					Preconditions.checkState((float)mag == (float)csvMag, "Expected m=%s at index %s, have %s", mag, m, csvMag);
					for (int n=0; n<nQuantiles; n++) {
						double val = csv.getDouble(row, n+1);
						Preconditions.checkState(val >= 0d);
						funcs[n].set(m, val);
					}
					row++;
				}
				List<Exact> ret = new ArrayList<>(nQuantiles);
				for (int n=0; n<nQuantiles; n++) {
					boolean mean = (float)quantiles[n] == (float)MEAN_QUANT_FLAG;
					ret.add(new Exact(M1, funcs[n], mean ? Double.NaN : quantiles[n], mean));
				}
				return ret;
			}
			
		}
		throw new IllegalStateException("Never found '"+RateType.EXACT.fileHeading+"'");
	}
	
	public static <E extends RateRecord> E locateQuantile(List<E> records, double quantile) {
		for (E record : records)
			if ((float)record.quantile == (float)quantile)
				return record;
		throw new IllegalStateException("No record found for quantile: "+(float)quantile);
	}
	
	public static <E extends RateRecord> E locateMean(List<E> records) {
		for (E record : records)
			if (record.mean)
				return record;
		throw new IllegalStateException("No mean record found");
	}

	public static void main(String[] args) throws IOException {
		CSVFile<String> csv = CSVFile.readFile(new File("/tmp/rateunc-Crustal-Full-oct2124.csv"), false);
		
		System.out.println(RateType.M1.fileHeading+":");
		List<PureGR> m1Branches = loadPureGR_branches(csv, RateType.M1);
		for (PureGR branch : m1Branches)
			System.out.println("\t"+branch);
		
		System.out.println();
		System.out.println(RateType.M1_TO_MMAX.fileHeading+":");
		List<PureGR> m1toMmaxBranches = loadPureGR_branches(csv, RateType.M1_TO_MMAX);
		for (PureGR branch : m1toMmaxBranches)
			System.out.println("\t"+branch);
		
		System.out.println();
		System.out.println(RateType.EXACT.fileHeading);
		List<Exact> exacts = loadExactBranches(csv);
		EvenlyDiscretizedFunc refFunc = exacts.get(0).cumulativeDist;
		System.out.print("Mag");
		for (Exact exact : exacts)
			System.out.print("\t" + (exact.mean ? "Mean" : "p"+(float)exact.quantile));
		System.out.println();
		for (int i=0; i<refFunc.size(); i++) {
			System.out.print((float)refFunc.getX(i));
			for (Exact exact : exacts)
				System.out.print("\t"+(float)exact.cumulativeDist.getY(i));
			System.out.println();
		}
		
		System.out.println();
		double testMmax = 7.6;
		EvenlyDiscretizedFunc refMFD = FaultSysTools.initEmptyMFD(PRVI25_GridSourceBuilder.OVERALL_MMIN, 8.45);
		PureGR meanM1 = locateMean(m1Branches);
		IncrementalMagFreqDist m1GR = buildIncrementalMFD(meanM1, refMFD, testMmax);
		PureGR meanM1toMmax = locateMean(m1toMmaxBranches);
		IncrementalMagFreqDist m1toMmaxGR = buildIncrementalMFD(meanM1toMmax, refMFD, testMmax);
		Exact meanExact = locateMean(exacts);
		IncrementalMagFreqDist exact = buildIncrementalMFD(meanExact, refMFD, testMmax);
		Preconditions.checkState(exact.getMinX() >= refMFD.getMinX());
		Preconditions.checkState(exact.getMaxX() == refMFD.getMaxX());
		int exactIndexOffset = refMFD.getXIndex(exact.getMinX());
		Preconditions.checkState(exactIndexOffset >= 0);
		System.out.println("Mag\tM1\tM1toMmax\tExact");
		for (int i=0; i<refMFD.size(); i++) {
			double x = refMFD.getX(i);
			String str = (float)x+"\t"+(float)m1GR.getY(i)+"\t"+(float)m1toMmaxGR.getY(i);
			int exactIndex = i - exactIndexOffset;
			if (exactIndex >= 0 && exactIndex < exact.size()) {
				double exactX = exact.getX(exactIndex);
				Preconditions.checkState((float)exactX == (float)x, "%s != %s", (float)exactX, (float)x);
				str += "\t"+(float)exact.getY(exactIndex);
			}
			System.out.println(str);
		}
		System.out.println();
		System.out.println("Rates above M5");
		System.out.println("\tM1 G-R: "+(float)m1GR.getCumRateDistWithOffset().getY(5d)+"\t(expected "+(float)meanM1.rateAboveM1+")");
		System.out.println("\tM1toMmax G-R: "+(float)m1toMmaxGR.getCumRateDistWithOffset().getY(5d)+"\t(expected "+(float)meanM1toMmax.rateAboveM1+")");
		System.out.println("\tExact G-R: "+(float)exact.getCumRateDistWithOffset().getY(5d)+"\t(expected "+(float)meanExact.rateAboveM1+")");
	}

}
