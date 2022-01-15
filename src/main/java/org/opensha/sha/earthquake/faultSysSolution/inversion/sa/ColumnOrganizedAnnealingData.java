package org.opensha.sha.earthquake.faultSysSolution.inversion.sa;

import java.util.Arrays;

import com.google.common.base.Preconditions;

import cern.colt.matrix.tdouble.DoubleMatrix2D;
import cern.colt.matrix.tdouble.impl.SparseCCDoubleMatrix2D;
import cern.colt.matrix.tdouble.impl.SparseDoubleMatrix2D;
import edu.emory.mathcs.csparsej.tdouble.Dcs_common.Dcs;

/**
 * This reorganizes the inversion input data (A matrix and data vector) by column, so that the inversion solver can
 * quickly access the relevant parts for each perturbation. This directly leads to most of the efficiencies in our
 * SA implementation.
 * 
 * @author kevin
 *
 */
public class ColumnOrganizedAnnealingData {
	/**
	 * Input A matrix
	 */
	public final DoubleMatrix2D A;
	/**
	 * Input data vector
	 */
	public final double[] d;
	/**
	 * Number of rows in the A matrix/data vector
	 */
	public final int nRows;
	/*
	 * Number of columns in the A matrix (equal to the number of values we're solving for)
	 */
	public final int nCols;
	/**
	 * Rows indexes in the A matrix that have nonzero values for each column
	 */
	final int[][] colRows;
	/**
	 * Nonzero values in the A matrix for each column (at the corresponding row from {@link #colRows} 
	 */
	final double[][] colA_values;
	/**
	 * The maximum number of nonzero values in any column of the A matrix
	 */
	final int maxRowsPerCol;
	
	public ColumnOrganizedAnnealingData(DoubleMatrix2D A, double[] d) {
		Preconditions.checkNotNull(A, "A is null");
		Preconditions.checkNotNull(d, "d is null");
		nRows = A.rows();
		nCols = A.columns();
		Preconditions.checkArgument(nRows > 0, "nRow of A must be > 0");
		Preconditions.checkArgument(nCols > 0, "nCol of A must be > 0");
		Preconditions.checkArgument(d.length == nRows, "d matrix must be same lenth as nRow of A");
		this.A = A;
		this.d = d;
		colRows = new int[nCols][];
		colA_values = new double[nCols][];
		
		if (A instanceof SparseDoubleMatrix2D) {
			System.out.println("Column compressing A matrix");
			A = ((SparseDoubleMatrix2D)A).getColumnCompressed(true);
		}
		
		if (A instanceof SparseCCDoubleMatrix2D) {
			Dcs dcs = ((SparseCCDoubleMatrix2D)A).elements();
			
			// for each non-zero value, gives the row index
			final int[] rowIndexesA = dcs.i;
			// tells us where the rows associated with this column are in the above array
			final int[] columnPointersA = dcs.p;
			// values array
			final double[] valuesA = dcs.x;
			
			int maxRowsPerCol = 0;
			for (int col=0; col<nCols; col++) {
				int low = columnPointersA[col];
				int high = columnPointersA[col+1];
				int len = high - low;
				maxRowsPerCol = Integer.max(maxRowsPerCol, len);
				colRows[col] = new int[len];
				colA_values[col] = new double[len];
				int index = 0;
				for (int k=low; k<high; k++) {
					colRows[col][index] = rowIndexesA[k];
					colA_values[col][index] = valuesA[k];
					
					index++;
				}
			}
			this.maxRowsPerCol = maxRowsPerCol;
		} else {
			System.out.println("Re-organizing A matrix into colmn vectors, this may be slow. Suggest using "
					+ "SparseDoubleMatrix or SparseCCDoubleMatrix for large matrices.");
			// do it manually for a dense matrix
			int maxRowsPerCol = 0;
			double[] tmpVals = new double[nRows];
			int[] tmpRows = new int[nRows];
			for (int col=0; col<nCols; col++) {
				int index = 0;
				for (int row=0; row<nRows; row++) {
					double val = A.get(row, col);
					if (val != 0d) {
						tmpVals[index] = val;
						tmpRows[index] = row;
						index++;
					}
				}
				maxRowsPerCol = Integer.max(maxRowsPerCol, index);
				colRows[col] = Arrays.copyOf(tmpRows, index);
				colA_values[col] = Arrays.copyOf(tmpVals, index);
			}
			this.maxRowsPerCol = maxRowsPerCol;
		}
	}
}