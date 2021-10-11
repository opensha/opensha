package org.opensha.sha.earthquake.faultSysSolution.modules;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.util.modules.ArchivableModule;
import org.opensha.commons.util.modules.helpers.CSV_BackedModule;
import org.opensha.commons.util.modules.helpers.FileBackedModule;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.ConstraintRange;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.SimulatedAnnealing;

import com.google.common.base.Preconditions;

public class InversionMisfits implements ArchivableModule {
	
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
					"Inequality", "Weight");
			for (ConstraintRange range : constraintRanges)
				rangesCSV.addLine(range.name, range.shortName, range.startRow+"", range.endRow+"",
						range.inequality+"", range.weight+"");
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
				String name = rangesCSV.get(row, 0);
				String shortName = rangesCSV.get(row, 1);
				int startRow = rangesCSV.getInt(row, 2);
				int endRow = rangesCSV.getInt(row, 3);
				boolean inequality = rangesCSV.getBoolean(row, 4);
				double weight = rangesCSV.getDouble(row, 5);
				constraintRanges.add(new ConstraintRange(name, shortName, startRow, endRow, inequality, weight));
			}
		}
	}

}
