package org.opensha.sha.earthquake.faultSysSolution.inversion.sa;

/**
 * Class to keep track of the rows in the inversion A matrix and data vector which
 * below to a given constraint
 * 
 * @author kevin
 *
 */
public class ConstraintRange {
	
	public final String name;
	public final String shortName;
	/**
	 * First row for this constraint (inclusive)
	 */
	public final int startRow;
	/**
	 * Last row for this constraint (exclusive)
	 */
	public final int endRow;
	/**
	 * True for inequality constraint, false for equality constraint
	 */
	public final boolean inequality;
	/**
	 * Weight assigned to this constraint
	 */
	public final double weight;
	
	public ConstraintRange(String name, String shortName,
			int startRow, int endRow, boolean inequality, double weight) {
		this.name = name;
		this.shortName = shortName;
		this.startRow = startRow;
		this.endRow = endRow;
		this.inequality = inequality;
		this.weight = weight;
	}
	
	@Override
	public String toString() {
		return shortName+": ["+startRow+".."+endRow+"), "+(endRow-startRow)+" rows";
	}
	
	public boolean contains(int row) {
		return row >= startRow && row < endRow;
	}
	
	public boolean contains(int row, boolean inequality) {
		return this.inequality == inequality && row >= startRow && row < endRow;
	}

}
