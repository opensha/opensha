package org.opensha.sha.earthquake.faultSysSolution.inversion;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.util.FileUtils;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;

import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;

import cern.colt.function.tdouble.IntIntDoubleFunction;
import cern.colt.matrix.tdouble.DoubleMatrix2D;
import cern.colt.matrix.tdouble.impl.SparseCCDoubleMatrix2D;
import cern.colt.matrix.tdouble.impl.SparseDoubleMatrix2D;
import cern.colt.matrix.tdouble.impl.SparseRCDoubleMatrix2D;
import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.simulatedAnnealing.ConstraintRange;
import scratch.UCERF3.utils.MatrixIO;

/**
 * This class builds and encodes inversion inputs (A/A_ineq matrices and D/D_ineq vectors) from
 * a list of constraints
 * @author kevin
 *
 */
public class InversionInputGenerator {
	
	// inputs
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

	public InversionInputGenerator(FaultSystemRupSet rupSet, List<InversionConstraint> constraints,
			double[] initialSolution, double[] waterLevelRates) {
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
	
	public void generateInputs(Class<? extends DoubleMatrix2D> clazz, final boolean verbose) {
		if (verbose)
			System.out.println("Generating inversion inputs with "+numRuptures+" ruptures "
					+"and "+constraints.size()+" constraints");

		if (initialSolution == null) {
			if (verbose)
				System.out.println("Building empty intial solution (all zeroes)");
			initialSolution = new double[numRuptures];
		} else {
			Preconditions.checkState(initialSolution.length == numRuptures,
					"Initial solution is wrong size: %s != %s", initialSolution.length, numRuptures);
		}
		
		if (waterLevelRates != null)
			Preconditions.checkState(waterLevelRates.length == numRuptures,
					"Water level rates are wrong size: %s != %s", waterLevelRates.length, numRuptures);
		
		constraintRowRanges = new ArrayList<>();
		
		if (verbose)
			System.out.println("Calculating constraint row counts");

		Stopwatch watch = verbose ? Stopwatch.createStarted() : null;
		Stopwatch watchTotal = verbose ? Stopwatch.createStarted() : null;

		int numRows = 0;
		int numIneqRows = 0;
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
	
	private ConstraintRange calcRowRange(int startIndex, InversionConstraint constraint, boolean verbose) {
		Stopwatch watch = verbose ? Stopwatch.createStarted() : null;
		int numRows = constraint.getNumRows();
		ConstraintRange range = new ConstraintRange(constraint.getName(), constraint.getShortName(),
				startIndex, startIndex+numRows, constraint.isInequality());
		if (verbose) {
			System.out.println("\t"+range+" (took "+getTimeStr(watch)+")");
			watch.stop();
		}
		return range;
	}
	
	private static final DecimalFormat oneDigit = new DecimalFormat("0.0");
	
	protected String getTimeStr(Stopwatch watch) {
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
	
	public void writeZipFile(File file, boolean verbose) throws IOException {
		File tempDir = FileUtils.createTempDir();
		writeZipFile(file, FileUtils.createTempDir(), true, verbose);
		tempDir.delete();
	}
	
	/**
	 * Writes the inputs to the given zip file, storing the binary files
	 * in the given directory and optionally cleaning up (deleting them)
	 * when done.
	 * 
	 * @param file target zip file
	 * @param storeDir directory where they should be saved
	 * @param cleanup if true, deletes input files after zipping
	 * @param verbose print progress
	 */
	public void writeZipFile(File zipFile, File storeDir, boolean cleanup, boolean verbose)
			throws IOException {
		if(verbose) System.out.println("Saving to files...");
		ArrayList<String> fileNames = new ArrayList<String>();
		
		fileNames.add("d.bin");			
		MatrixIO.doubleArrayToFile(d, new File(storeDir, "d.bin"));
		if(verbose) System.out.println("d.bin saved");
		
		fileNames.add("a.bin");			
		MatrixIO.saveSparse(A, new File(storeDir, "a.bin"));
		if(verbose) System.out.println("a.bin saved");
		
		fileNames.add("initial.bin");	
		MatrixIO.doubleArrayToFile(initialSolution, new File(storeDir, "initial.bin"));
		if(verbose) System.out.println("initial.bin saved");
		
		if (d_ineq != null) {
			fileNames.add("d_ineq.bin");	
			MatrixIO.doubleArrayToFile(d_ineq, new File(storeDir, "d_ineq.bin"));
			if(verbose) System.out.println("d_ineq.bin saved");
		}
		
		if (A_ineq != null) {
			fileNames.add("a_ineq.bin");	
			MatrixIO.saveSparse(A_ineq,new File(storeDir, "a_ineq.bin"));
			if(verbose) System.out.println("a_ineq.bin saved");
		}
		
		if (waterLevelRates != null) {
			fileNames.add("minimumRuptureRates.bin");	
			MatrixIO.doubleArrayToFile(waterLevelRates,new File(storeDir, "minimumRuptureRates.bin"));
			if(verbose) System.out.println("minimumRuptureRates.bin saved");
		}
		
		CSVFile<String> rangeCSV = new CSVFile<String>(true);
		rangeCSV.addLine("Name", "Short Name", "Inequality?",
				"Start Row (inclusive)", "End Row (exclusive)");
		
		for (ConstraintRange range : constraintRowRanges)
			rangeCSV.addLine(range.name, range.shortName, range.inequality+"",
					range.startRow+"", range.endRow+"");
		fileNames.add("constraintRanges.csv");
		rangeCSV.writeToFile(new File(storeDir, "constraintRanges.csv"));
		if(verbose) System.out.println("constraintRanges.csv saved");
		
		FileUtils.createZipFile(zipFile.getAbsolutePath(), storeDir.getAbsolutePath(), fileNames);
		if(verbose) System.out.println("Zip file saved");
		if (cleanup) {
			if(verbose) System.out.println("Cleaning up");
			for (String fileName : fileNames) {
				new File(storeDir, fileName).delete();
			}
		}
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

	public List<ConstraintRange> getConstraintRowRanges() {
		return constraintRowRanges;
	}

	public List<InversionConstraint> getConstraints() {
		return constraints;
	}

}
