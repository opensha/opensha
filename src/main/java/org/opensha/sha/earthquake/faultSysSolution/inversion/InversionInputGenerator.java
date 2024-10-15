package org.opensha.sha.earthquake.faultSysSolution.inversion;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.commons.math3.stat.StatUtils;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.CSVWriter;
import org.opensha.commons.util.io.archive.ArchiveOutput;
import org.opensha.commons.util.modules.helpers.CSV_BackedModule;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.ConstraintRange;

import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;

import cern.colt.function.tdouble.IntIntDoubleFunction;
import cern.colt.list.tdouble.DoubleArrayList;
import cern.colt.list.tint.IntArrayList;
import cern.colt.map.tdouble.AbstractLongDoubleMap;
import cern.colt.matrix.tdouble.DoubleMatrix2D;
import cern.colt.matrix.tdouble.impl.SparseCCDoubleMatrix2D;
import cern.colt.matrix.tdouble.impl.SparseDoubleMatrix2D;
import cern.colt.matrix.tdouble.impl.SparseRCDoubleMatrix2D;
import scratch.UCERF3.utils.MatrixIO;

/**
 * This class builds and encodes inversion inputs (A/A_ineq matrices and D/D_ineq vectors) from
 * a list of constraints
 * @author kevin
 *
 */
public class InversionInputGenerator {
	
	// inputs
	protected FaultSystemRupSet rupSet;
	protected int numRuptures;
	protected List<InversionConstraint> constraints;
	protected double[] initialSolution;
	protected double[] waterLevelRates;
	
	// outputs
	protected DoubleMatrix2D A;
	protected double[] d;
	protected DoubleMatrix2D A_ineq;
	protected double[] d_ineq;
	
	protected List<ConstraintRange> constraintRowRanges;

	public InversionInputGenerator(FaultSystemRupSet rupSet, List<InversionConstraint> constraints) {
		this(rupSet, constraints, null, null);
	}

	public InversionInputGenerator(FaultSystemRupSet rupSet, InversionConfiguration config) {
		this(rupSet, config.getConstraints(), config.getInitialSolution(), config.getWaterLevel());
	}

	public InversionInputGenerator(FaultSystemRupSet rupSet, List<InversionConstraint> constraints,
			double[] initialSolution, double[] waterLevelRates) {
		this.rupSet = rupSet;
		this.numRuptures = rupSet.getNumRuptures();
		this.constraints = constraints;
		this.initialSolution = initialSolution;
		this.waterLevelRates = waterLevelRates;
	}
	
	public void generateInputs() {
		generateInputs(null, false);
	}
	
	public void generateInputs(boolean verbose) {
		generateInputs(null, verbose);
	}
	
	public static List<ConstraintRange> buildConstraintRanges(List<InversionConstraint> constraints, boolean verbose) {
		int numRows = 0;
		int numIneqRows = 0;
		List<ConstraintRange> constraintRowRanges = new ArrayList<>();
		for (InversionConstraint constraint : constraints) {
			constraint.setQuickGetSets(!verbose);
			ConstraintRange range;
			if (constraint.isInequality()) {
				range = calcRowRange(numIneqRows, constraint, verbose);
				numIneqRows = range.endRow;
			} else {
				range = calcRowRange(numRows, constraint, verbose);
				numRows = range.endRow;
			}
			constraintRowRanges.add(range);
		}
		return constraintRowRanges;
	}
	
	public void generateInputs(Class<? extends DoubleMatrix2D> clazz, final boolean verbose) {
		if (verbose)
			System.out.println("Generating inversion inputs with "+numRuptures+" ruptures "
					+"and "+constraints.size()+" constraints");

		if (initialSolution == null) {
			if (verbose)
				System.out.println("Building empty initial solution (all zeroes)");
			initialSolution = new double[numRuptures];
		} else {
			Preconditions.checkState(initialSolution.length == numRuptures,
					"Initial solution is wrong size: %s != %s", initialSolution.length, numRuptures);
		}
		
		if (waterLevelRates != null)
			Preconditions.checkState(waterLevelRates.length == numRuptures,
					"Water level rates are wrong size: %s != %s", waterLevelRates.length, numRuptures);
		
		
		if (verbose)
			System.out.println("Calculating constraint row counts");

		Stopwatch watch = verbose ? Stopwatch.createStarted() : null;
		Stopwatch watchTotal = verbose ? Stopwatch.createStarted() : null;

		constraintRowRanges = buildConstraintRanges(constraints, verbose);
		int numRows = 0;
		int numIneqRows = 0;
		for (ConstraintRange range : constraintRowRanges) {
			if (range.inequality)
				numIneqRows = range.endRow;
			else
				numRows = range.endRow;
		}
		
		if (verbose) {
			watch.stop();
			System.out.println("Took "+getTimeStr(watch)+" to get row counts");
		}
		
		if (numRows > 0) {
			if (verbose)
				System.out.println("Building A matrix with "+numRows
						+" rows and "+numRuptures+" columns");
			// Components of matrix equation to invert (A*x=d)
			A = buildMatrix(clazz, numRows, numRuptures); // A matrix
			d = new double[numRows];	// data vector d
		}
		
		if (numIneqRows > 0) {
			if (verbose)
				System.out.println("Building A inequality matrix with "+numIneqRows
						+" rows and "+numRuptures+" columns");
			// inequality constraint matrix and data vector (A_ineq*x <= d_ineq)
			A_ineq = buildMatrix(clazz, numIneqRows, numRuptures); // A matrix
			d_ineq = new double[numIneqRows];	// data vector d
		}
		
		if (verbose)
			System.out.println("Encoding matrices");
		
		watch = verbose ? Stopwatch.createStarted() : null;
		int numNonZero = 0;
		
		for (int i=0; i<constraints.size(); i++) {
			InversionConstraint constraint = constraints.get(i);
			ConstraintRange rowRange = constraintRowRanges.get(i);
			
			DoubleMatrix2D myA;
			double[] myD;
			if (constraint.isInequality()) {
				myA = A_ineq;
				myD = d_ineq;
			} else {
				myA = A;
				myD = d;
			}
			
			if (verbose)
				System.out.println("\tEncoding "+constraint.getName()
					+", ineq="+constraint.isInequality());
			Stopwatch subWatch = verbose ? Stopwatch.createStarted() : null;
			long myNonZero = constraint.encode(myA, myD, rowRange.startRow);
			if (verbose) {
				long maxNum = (rowRange.endRow - rowRange.startRow)*(long)numRuptures;
				double density = 100d*(double)myNonZero/(double)maxNum;
				System.out.println("\t\tDONE, took "+getTimeStr(subWatch)+" to encode "
						+myNonZero+" values (density: "+oneDigit.format(density)+" %)");
				subWatch.stop();
			}
			numNonZero += myNonZero;
		}
		
		if (verbose) {
			long maxNum = (numRows+numIneqRows)*(long)numRuptures;
			double density = 100d*(double)numNonZero/(double)maxNum;
			System.out.println("DONE encoding, took "+getTimeStr(watch)+" to encode "
					+numNonZero+" values (density: "+oneDigit.format(density)+" %)");
			watch.stop();
		}
		
		if (waterLevelRates != null) {
			// offset data vector: d = d-A*minimumRuptureRates
			watch = verbose ? Stopwatch.createStarted() : null;
			if (numRows > 0) {
				if (verbose)
					System.out.println("Applying minimum rupture rates to A matrix");
				
				A.forEachNonZero(new AdjustDataForMinRates(d, waterLevelRates));
			}
			if (numIneqRows > 0) {
				if (verbose)
					System.out.println("Applying minimum rupture rates to A_ineq matrix");
				
				A_ineq.forEachNonZero(new AdjustDataForMinRates(d_ineq, waterLevelRates));
			}
			
			// also adjust the initial solution by the minimum rates
			initialSolution = Arrays.copyOf(initialSolution, numRuptures);
			for (int i=0; i<numRuptures; i++) {
				double adjustedVal = initialSolution[i] - waterLevelRates[i];
				if (adjustedVal < 0)
					adjustedVal = 0;
				initialSolution[i] = adjustedVal;
			}
			
			if (verbose) {
				System.out.println("Took "+getTimeStr(watch)+" to apply minimum rates");
				watch.stop();
			}
		}
		
		if (verbose) {
			System.out.println("Took "+getTimeStr(watchTotal)+" to generate inputs");
			watchTotal.stop();
		}
	}
	
	private static class AdjustDataForMinRates implements IntIntDoubleFunction {
		
		private double[] d;
		private double[] waterLevelRates;
		
		public AdjustDataForMinRates(double[] d, double[] waterLevelRates) {
			this.d = d;
			this.waterLevelRates = waterLevelRates;
		}

		@Override
		public double apply(int row, int col, double val) {
			// This is the offset data vector: d = d-A*waterLevelRates
			d[row] -= val * waterLevelRates[col];
			return val; // don't change A
		}
		
	}
	
	static ConstraintRange calcRowRange(int startIndex, InversionConstraint constraint, boolean verbose) {
		Stopwatch watch = verbose ? Stopwatch.createStarted() : null;
		ConstraintRange range = constraint.getRange(startIndex);
		if (verbose) {
			System.out.println("\t"+range+" (took "+getTimeStr(watch)+")");
			watch.stop();
		}
		return range;
	}
	
	private static final DecimalFormat oneDigit = new DecimalFormat("0.0");
	
	protected static String getTimeStr(Stopwatch watch) {
		long millis = watch.elapsed(TimeUnit.MILLISECONDS);
		if (millis < 1000)
			return millis+" ms";
		double secs = (double)millis/1000d;
		if (secs < 60d)
			return oneDigit.format(secs)+" s";
		double mins = secs/60d;
		return oneDigit.format(mins)+" m";
	}
	
	protected static DoubleMatrix2D buildMatrix(Class<? extends DoubleMatrix2D> clazz, int rows, int cols) {
		if (clazz == null || clazz.equals(SparseDoubleMatrix2D.class))
			// default
			return new SparseDoubleMatrix2D(rows, cols);
		else if (clazz.equals(SparseRCDoubleMatrix2D.class))
			return new SparseRCDoubleMatrix2D(rows, cols);
		else if (clazz.equals(SparseCCDoubleMatrix2D.class))
			return new SparseCCDoubleMatrix2D(rows, cols);
		else
			throw new IllegalArgumentException("Unknown matrix type: "+clazz);
	}
	
	/**
	 * Column compress the A matrices for faster multiplication
	 */
	public void columnCompress() {
		A = getColumnCompressed(A);
		if (A_ineq != null)
			A_ineq = getColumnCompressed(A_ineq);
	}
	
	private static SparseCCDoubleMatrix2D getColumnCompressed(DoubleMatrix2D mat) {
		if (mat instanceof SparseCCDoubleMatrix2D)
			return (SparseCCDoubleMatrix2D)mat;
		if (mat instanceof SparseRCDoubleMatrix2D)
			return ((SparseRCDoubleMatrix2D)mat).getColumnCompressed();
		if (mat instanceof SparseDoubleMatrix2D)
			return ((SparseDoubleMatrix2D)mat).getColumnCompressed(true);
		throw new RuntimeException("Can't column compress matrix: "+mat);
	}
	
	/**
	 * This adds the water level rates back to the solution
	 * @param solution
	 * @return copy of the solution with water level rates added back in
	 */
	public double[] adjustSolutionForWaterLevel(double[] solution) {
		return adjustSolutionForWaterLevel(solution, waterLevelRates);
	}
	
	/**
	 * This adds the water level rates back to the solution
	 * @param solution
	 * @param waterLevelRates
	 * @return copy of the solution with water level rates added back in
	 */
	public static double[] adjustSolutionForWaterLevel(double[] solution, double[] waterLevelRates) {
		solution = Arrays.copyOf(solution, solution.length);
		
		if (waterLevelRates != null) {
			Preconditions.checkState(waterLevelRates.length == solution.length,
					"minimum rates size mismatch!");
			for (int i=0; i<solution.length; i++) {
				solution[i] = solution[i] + waterLevelRates[i];
			}
		}
		
		return solution;
	}
	
	private static void writeDoubleArrayCSV(double[] array, OutputStream out, String indexHeader, String dataHeader)
			throws IOException {
		CSVWriter csv = new CSVWriter(out, true);
		csv.write(List.of(indexHeader, dataHeader));
		
		for (int i=0; i<array.length; i++)
			csv.write(List.of(i+"", array[i]+""));
		
		csv.flush();
	}
	
	private static void writeSparseCSV(DoubleMatrix2D mat, OutputStream out)
			throws IOException {
		CSVWriter csv = new CSVWriter(out, true);
		csv.write(List.of("Row Index", "Column Index", "Value"));
		
		if (mat instanceof SparseDoubleMatrix2D) {
			AbstractLongDoubleMap map = ((SparseDoubleMatrix2D)mat).elements();
			int nnz = mat.cardinality();
			long[] keys = map.keys().elements();
			double[] values = map.values().elements();
			
			int columns = mat.columns();
			for (int i = 0; i < nnz; i++) {
				int row = (int) (keys[i] / columns);
				int column = (int) (keys[i] % columns);
				//				A.setQuick(row, column, values[i]);
				csv.write(List.of(row+"", column+"", values[i]+""));
			}
		} else {
			IntArrayList rowList = new IntArrayList();
			IntArrayList colList = new IntArrayList();
			DoubleArrayList valList = new DoubleArrayList();
			
			mat.getNonZeros(rowList, colList, valList);
			
			Preconditions.checkState(rowList.size()>0, "rowList is empty!");
			Preconditions.checkState(rowList.size() == colList.size() && colList.size() == valList.size(),
			"array sizes incorrect!");
			
			for (int i=0; i<valList.size(); i++) {
				int row = rowList.get(i);
				int col = colList.get(i);
				double val = valList.get(i);

				csv.write(List.of(row+"", col+"", val+""));
			}
		}
		
		csv.flush();
	}
	
	public void writeArchive(File file, boolean binary) throws IOException {
		writeArchive(file, null, binary);
	}
	
	public void writeArchive(File file, double[] solution, boolean binary) throws IOException {
		writeArchive(ArchiveOutput.getDefaultOutput(file), solution, binary);
	}
	
	public void writeArchive(ArchiveOutput out, boolean binary) throws IOException {
		writeArchive(out, null, binary);
	}
	
	public void writeArchive(ArchiveOutput out, double[] solution, boolean binary) throws IOException {
		if (binary) {
			out.putNextEntry("d.bin");
			MatrixIO.doubleArrayToStream(d, out.getOutputStream());
		} else {
			out.putNextEntry("d.csv");
			writeDoubleArrayCSV(d, out.getOutputStream(), "Constraint Index", "Constraint Target");
		}
		out.closeEntry();
		
		if (binary) {
			out.putNextEntry("a.bin");
			MatrixIO.saveSparse(A, out.getOutputStream());
		} else {
			out.putNextEntry("a.csv");
			writeSparseCSV(A, out.getOutputStream());
		}
		out.closeEntry();
		
		if (initialSolution != null && StatUtils.max(initialSolution) > 0d) {
			if (binary) {
				out.putNextEntry("initial.bin");
				MatrixIO.doubleArrayToStream(initialSolution, out.getOutputStream());
			} else {
				out.putNextEntry("initial.csv");
				writeDoubleArrayCSV(initialSolution, out.getOutputStream(), "Rupture Index", "Initial Value");
			}
			out.closeEntry();
		}
		
		if (solution != null) {
			if (binary) {
				out.putNextEntry("solution.bin");
				MatrixIO.doubleArrayToStream(solution, out.getOutputStream());
			} else {
				out.putNextEntry("solution.csv");
				writeDoubleArrayCSV(solution, out.getOutputStream(), "Rupture Index", "Solution Value (Annual Rate)");
			}
			out.closeEntry();
		}
		
		if (d_ineq != null) {
			if (binary) {
				out.putNextEntry("d_ineq.bin");
				MatrixIO.doubleArrayToStream(d_ineq, out.getOutputStream());
			} else {
				out.putNextEntry("d_ineq.csv");
				writeDoubleArrayCSV(d_ineq, out.getOutputStream(), "Constraint Index", "Constraint Target");
			}
			out.closeEntry();
		}
		
		if (A_ineq != null) {
			if (binary) {
				out.putNextEntry("a_ineq.bin");
				MatrixIO.saveSparse(A_ineq, out.getOutputStream());
			} else {
				out.putNextEntry("a_ineq.csv");
				writeSparseCSV(A_ineq, out.getOutputStream());
			}
			out.closeEntry();
		}
		
		if (waterLevelRates != null) {
			if (binary) {
				out.putNextEntry("waterLevelRates.bin");
				MatrixIO.doubleArrayToStream(waterLevelRates, out.getOutputStream());
			} else {
				out.putNextEntry("waterLevelRates.csv");
				writeDoubleArrayCSV(waterLevelRates, out.getOutputStream(), "Rupture Index", "Water-level Value");
			}
			out.closeEntry();
		}
		
		CSVFile<String> rangeCSV = new CSVFile<String>(true);
		rangeCSV.addLine("Name", "Short Name", "Inequality?",
				"Start Row (inclusive)", "End Row (exclusive)");
		
		for (ConstraintRange range : constraintRowRanges)
			rangeCSV.addLine(range.name, range.shortName, range.inequality+"",
					range.startRow+"", range.endRow+"");
		
		CSV_BackedModule.writeToArchive(rangeCSV, out, "", "constraintRanges.csv");
		
		out.close();
	}
	
	public DoubleMatrix2D getA() {
		return A;
	}

	public DoubleMatrix2D getA_ineq() {
		return A_ineq;
	}

	public double[] getD() {
		return d;
	}

	public double[] getD_ineq() {
		return d_ineq;
	}

	public double[] getInitialSolution() {
		return initialSolution;
	}

	public double[] getWaterLevelRates() {
		return waterLevelRates;
	}
	
	public boolean hasInitialSolution() {
		if (initialSolution == null)
			return false;
		// see if non-zero
		boolean nonZero = false;
		for (double val : initialSolution) {
			if (val > 0) {
				nonZero = true;
				break;
			}
		}
		return nonZero;
	}

	public List<ConstraintRange> getConstraintRowRanges() {
		return constraintRowRanges;
	}

	public List<InversionConstraint> getConstraints() {
		return constraints;
	}
	
	public FaultSystemRupSet getRuptureSet() {
		return rupSet;
	}

}
