package org.opensha.sha.earthquake.faultSysSolution.inversion.constraints;

import org.opensha.commons.data.ShortNamed;

import cern.colt.matrix.tdouble.DoubleMatrix2D;

/**
 * Abstract class for an inversion constraint
 * 
 * @author kevin
 *
 */
public abstract class InversionConstraint implements ShortNamed {
	
	private boolean quickGetsSets = true;
	
	/**
	 * @return the number of rows in the A matrix/d vector for this constraint
	 */
	public abstract int getNumRows();
	
	/**
	 * @return true if this is an inequality constraint (A_ineq, d_ineq), else a regular
	 * equality constraint
	 */
	public abstract boolean isInequality();
	
	/**
	 * Encodes this constraint into the given A matrix and d vector, beginning at the
	 * given starting row and ending before (startRow+getNumRows())
	 * @param A
	 * @param d
	 * @param startRow
	 * @return number of non-zero elements added
	 */
	public abstract long encode(DoubleMatrix2D A, double[] d, int startRow);
	
	/**
	 * Utility method to set a value in the given A matrix, respecting the quickGetsSets value
	 * 
	 * @param A
	 * @param row
	 * @param col
	 * @param val
	 */
	protected void setA(DoubleMatrix2D A, int row, int col, double val) {
		if (quickGetsSets)
			A.setQuick(row, col, val);
		else
			A.set(row, col, val);
	}
	
	/**
	 * Utility method to get a value in the given A matrix, respecting the quickGetsSets value
	 * 
	 * @param A
	 * @param row
	 * @param col
	 * @return value at that location
	 */
	protected double getA(DoubleMatrix2D A, int row, int col) {
		if (quickGetsSets)
			return A.getQuick(row, col);
		return A.get(row, col);
	}
	
	/**
	 * Utility method to add a value in the given A matrix, respecting the quickGetsSets value
	 * 
	 * @param A
	 * @param row
	 * @param col
	 * @param val
	 * @return true if the previous value was nonzero
	 */
	protected boolean addA(DoubleMatrix2D A, int row, int col, double val) {
		double prevVal = getA(A, row, col);
		if (quickGetsSets)
			A.setQuick(row, col, val+prevVal);
		else
			A.set(row, col, val+prevVal);
		return prevVal != 0d;
	}
	
	/**
	 * Sets whether or not we should use quick set/get methods on the A matrix. The quick
	 * versions of these methods are faster, but don't do any input validation (range checking)
	 * @param quickGetsSets
	 */
	public void setQuickGetSets(boolean quickGetsSets) {
		this.quickGetsSets = quickGetsSets;
	}

}