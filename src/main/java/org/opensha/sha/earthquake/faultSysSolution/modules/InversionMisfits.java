package org.opensha.sha.earthquake.faultSysSolution.modules;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.util.modules.ArchivableModule;
import org.opensha.commons.util.modules.AverageableModule;
import org.opensha.commons.util.modules.helpers.CSV_BackedModule;
import org.opensha.commons.util.modules.helpers.FileBackedModule;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.ConstraintWeightingType;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.ConstraintRange;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.SimulatedAnnealing;
import org.opensha.sha.earthquake.faultSysSolution.modules.InversionMisfitStats.MisfitStats;

import com.google.common.base.Preconditions;

public class InversionMisfits implements ArchivableModule, AverageableModule<InversionMisfits> {
	
	private List<ConstraintRange> constraintRanges;
	private double[] misfits;
	private double[] data;
	private double[] misfits_ineq;
	private double[] data_ineq;

	public InversionMisfits(SimulatedAnnealing sa) {
		this(sa.getConstraintRanges(), sa.getBestMisfit(), sa.getD(), sa.getBestInequalityMisfit(), sa.getD_ineq());
	}
	
	public InversionMisfits(List<ConstraintRange> constraintRanges, double[] misfits, double[] data) {
		this(constraintRanges, misfits, data, null, null);
	}
	
	public InversionMisfits(List<ConstraintRange> constraintRanges, double[] misfits, double[] data,
			double[] misfits_ineq, double[] data_ineq) {
		this.constraintRanges = constraintRanges;
		this.misfits = misfits;
		this.data = data;
		this.misfits_ineq = misfits_ineq;
		this.data_ineq = data_ineq;
	}
	
	// used for deserialization
	@SuppressWarnings("unused")
	private InversionMisfits() {}

	public List<ConstraintRange> getConstraintRanges() {
		return constraintRanges;
	}

	public double[] getMisfits() {
		return misfits;
	}

	public double[] getData() {
		return data;
	}

	public double[] getInequalityMisfits() {
		return misfits_ineq;
	}

	public double[] getInequalityData() {
		return data_ineq;
	}
	
	/**
	 * Returns the misfits for the given {@link ConstraintRange}, optionally with weighting removed
	 * 
	 * @param range the constraint range to extract from the full misfits array
	 * @param removeWeighting if true, misfits will be divided by the constraint weight to remove weighting
	 * @return misfits for the given constraint range
	 */
	public double[] getMisfits(ConstraintRange range, boolean removeWeighting) {
		double[] ret;
		if (range.inequality) {
			Preconditions.checkNotNull(misfits_ineq, "Inequality range supplied but not inequality misfits found");
			ret = getInRange(range, misfits_ineq);
		} else {
			Preconditions.checkNotNull(misfits, "Equality range supplied but not equality misfits found");
			ret = getInRange(range, misfits);
		}
		if (removeWeighting) {
			Preconditions.checkState(Double.isFinite(range.weight) && range.weight > 0,
					"Bad weight for constraint %s: %s", range.name, range.weight);
			for (int i=0; i<ret.length; i++)
				ret[i] /= range.weight;
		}
		return ret;
	}
	
	public InversionMisfitStats getMisfitStats() {
		List<MisfitStats> stats = new ArrayList<>();
		if (constraintRanges == null) {
			if (misfits != null)
				stats.add(getMisfitStats(new ConstraintRange("Equality", "Eq", 0, misfits.length, false, Double.NaN, null)));
			if (misfits_ineq != null)
				stats.add(getMisfitStats(new ConstraintRange("Inequality", "Ineq", 0, misfits_ineq.length, true, Double.NaN, null)));
		} else {
			for (ConstraintRange range : constraintRanges)
				stats.add(getMisfitStats(range));
		}
		return new InversionMisfitStats(stats);
	}
	
	public MisfitStats getMisfitStats(ConstraintRange range) {
		double[] misfits = getMisfits(range, true);
		
		return new MisfitStats(misfits, range);
	}
	
	private double[] getInRange(ConstraintRange range, double[] array) {
		Preconditions.checkState(range.startRow >= 0, "Bad start row: %s", range.startRow);
		Preconditions.checkState(range.endRow <= array.length,
				"End row (%s) out of data bounds (%s)", range.endRow, array.length);
		double[] ret = new double[range.endRow-range.startRow];
		for (int i=0; i<ret.length; i++)
			ret[i] = array[i+range.startRow];
		return ret;
	}

	@Override
	public String getName() {
		return "Inversion Misfits";
	}
	
	private static final String MISFITS_CSV = "inversion_misfits.csv";
	private static final String MISFITS_INEQ_CSV = "inversion_misfits_ineq.csv";
	private static final String RANGES_CSV = "inversion_constraint_ranges.csv";

	@Override
	public void writeToArchive(ZipOutputStream zout, String entryPrefix) throws IOException {
		if (misfits != null)
			writeData(misfits, data, entryPrefix, MISFITS_CSV, zout);
		if (misfits_ineq != null)
			writeData(misfits_ineq, data_ineq, entryPrefix, MISFITS_INEQ_CSV, zout);
		if (constraintRanges != null && !constraintRanges.isEmpty()) {
			CSVFile<String> rangesCSV = new CSVFile<>(true);
			rangesCSV.addLine("Name", "Short Name", "Start Row (inclusive)", "End Row (exclusive)",
					"Inequality", "Weight", "Weighting Type");
			for (ConstraintRange range : constraintRanges)
				rangesCSV.addLine(range.name, range.shortName, range.startRow+"", range.endRow+"",
						range.inequality+"", range.weight+"",
						range.weightingType == null ? "" : range.weightingType.name());
			CSV_BackedModule.writeToArchive(rangesCSV, zout, entryPrefix, RANGES_CSV);
		}
	}
	
	private static void writeData(double[] misfits, double[] data,
			String entryPrefix, String fileName, ZipOutputStream zout) throws IOException {
		CSVFile<String> csv = new CSVFile<>(true);
		Preconditions.checkState(misfits.length == data.length);
		csv.addLine("Row", "Data", "Misfit");
		for (int i=0; i<misfits.length; i++)
			csv.addLine(i+"", data[i]+"", misfits[i]+"");
		CSV_BackedModule.writeToArchive(csv, zout, entryPrefix, fileName);
	}
	
	private void readData(ZipFile zip, String entryPrefix, boolean ineq) throws IOException {
		String fileName = ineq ? MISFITS_INEQ_CSV : MISFITS_CSV;
		if (!FileBackedModule.hasEntry(zip, entryPrefix, fileName))
			return;
		CSVFile<String> csv = CSV_BackedModule.loadFromArchive(zip, entryPrefix, fileName);
		double[] data = new double[csv.getNumRows()-1];
		double[] misfits = new double[csv.getNumRows()-1];
		for (int i=0; i<data.length; i++) {
			int row = i+1;
			int myRow = csv.getInt(row, 0);
			Preconditions.checkState(myRow == i,
					"Rows must be in order and 0-based after a single header. Expected %s, was %s.", i, myRow);
			data[i] = csv.getDouble(row, 1);
			misfits[i] = csv.getDouble(row, 2);
		}
		if (ineq) {
			this.data_ineq = data;
			this.misfits_ineq = misfits;
		} else {
			this.data = data;
			this.misfits = misfits;
		}
	}

	@Override
	public void initFromArchive(ZipFile zip, String entryPrefix) throws IOException {
		readData(zip, entryPrefix, false);
		readData(zip, entryPrefix, true);
		if (FileBackedModule.hasEntry(zip, entryPrefix, RANGES_CSV)) {
			CSVFile<String> rangesCSV = CSV_BackedModule.loadFromArchive(zip, entryPrefix, RANGES_CSV);
			constraintRanges = new ArrayList<>();
			for (int row=1; row<rangesCSV.getNumRows(); row++) {
				List<String> line = rangesCSV.getLine(row);
				int col = 0;
				String name = line.get(col++);
				String shortName = line.get(col++);
				int startRow = Integer.parseInt(line.get(col++));
				int endRow = Integer.parseInt(line.get(col++));
				boolean inequality = Boolean.parseBoolean(line.get(col++));
				double weight = Double.parseDouble(line.get(col++));
				ConstraintWeightingType weightingType = null;
				if (col < line.size()) {
					String typeStr = line.get(col++);
					if (!typeStr.isBlank())
						weightingType = ConstraintWeightingType.valueOf(typeStr);
				}
				constraintRanges.add(new ConstraintRange(name, shortName, startRow, endRow, inequality, weight, weightingType));
			}
		}
	}
	
	public static InversionMisfits average(List<InversionMisfits> misfitsList) {
		InversionMisfits ref = misfitsList.get(0);
		List<ConstraintRange> ranges = ref.constraintRanges;
		int numEQ = ref.misfits == null ? 0 : ref.misfits.length;
		int numINEQ = ref.misfits_ineq == null ? 0 : ref.misfits_ineq.length;
		double[] misfits = numEQ > 0 ? new double[numEQ] : null;
		double[] data = numEQ > 0 ? new double[numEQ] : null;
		double[] misfits_ineq = numINEQ > 0 ? new double[numINEQ] : null;
		double[] data_ineq = numINEQ > 0 ? new double[numINEQ] : null;
		
		double scalarEach = 1d/misfitsList.size();
		for (InversionMisfits myMisfits : misfitsList) {
			if (numEQ > 0) {
				averageIn(scalarEach, misfits, myMisfits.misfits);
				averageIn(scalarEach, data, myMisfits.data);
			}
			if (numINEQ > 0) {
				averageIn(scalarEach, misfits_ineq, myMisfits.misfits_ineq);
				averageIn(scalarEach, data_ineq, myMisfits.data_ineq);
			}
		}
		
		return new InversionMisfits(ranges, misfits, data, misfits_ineq, data_ineq);
	}
	
	private static void averageIn(double scalar, double[] avgVals, double[] myVals) {
		for (int i=0; i<avgVals.length; i++)
			avgVals[i] += scalar*myVals[i];
	}
	
	private static class MisfitsAccumulator implements AveragingAccumulator<InversionMisfits> {
		
		private List<ConstraintRange> ranges;
		private int numEQ, numINEQ;
		private double[] misfits, data, misfits_ineq, data_ineq;
		private double sumWeight = 0d;
		
		public MisfitsAccumulator(InversionMisfits ref) {
			ranges = ref.constraintRanges;
			numEQ = ref.misfits == null ? 0 : ref.misfits.length;
			numINEQ = ref.misfits_ineq == null ? 0 : ref.misfits_ineq.length;
			misfits = numEQ > 0 ? new double[numEQ] : null;
			data = numEQ > 0 ? new double[numEQ] : null;
			misfits_ineq = numINEQ > 0 ? new double[numINEQ] : null;
			data_ineq = numINEQ > 0 ? new double[numINEQ] : null;
		}

		@Override
		public void process(InversionMisfits module, double relWeight) {
			if (numEQ > 0) {
				averageIn(relWeight, misfits, module.misfits);
				averageIn(relWeight, data, module.data);
			}
			if (numINEQ > 0) {
				averageIn(relWeight, misfits_ineq, module.misfits_ineq);
				averageIn(relWeight, data_ineq, module.data_ineq);
			}
			sumWeight += relWeight;
		}

		@Override
		public InversionMisfits getAverage() {
			if (numEQ > 0) {
				AverageableModule.scaleToTotalWeight(misfits, sumWeight);
				AverageableModule.scaleToTotalWeight(data, sumWeight);
			}
			if (numINEQ > 0) {
				AverageableModule.scaleToTotalWeight(misfits_ineq, sumWeight);
				AverageableModule.scaleToTotalWeight(data_ineq, sumWeight);
			}
			return new InversionMisfits(ranges, misfits, data, misfits_ineq, data_ineq);
		}

		@Override
		public Class<InversionMisfits> getType() {
			return InversionMisfits.class;
		}
		
	}

	@Override
	public AveragingAccumulator<InversionMisfits> averagingAccumulator() {
		return new MisfitsAccumulator(this);
	}

}
