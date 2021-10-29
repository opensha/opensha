package org.opensha.sha.earthquake.faultSysSolution.inversion.sa;

import java.util.Objects;

import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.ConstraintWeightingType;

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
	/**
	 * Weighting type of this constraint, useful for interpreting misfits
	 */
	public final ConstraintWeightingType weightingType;
	
	public ConstraintRange(String name, String shortName,
			int startRow, int endRow, boolean inequality, double weight, ConstraintWeightingType weightingType) {
		this.name = name;
		this.shortName = shortName;
		this.startRow = startRow;
		this.endRow = endRow;
		this.inequality = inequality;
		this.weight = weight;
		this.weightingType = weightingType;
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

	@Override
	public int hashCode() {
		return Objects.hash(endRow, inequality, name, shortName, startRow, weight);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ConstraintRange other = (ConstraintRange) obj;
		return endRow == other.endRow && inequality == other.inequality && Objects.equals(name, other.name)
				&& Objects.equals(shortName, other.shortName) && startRow == other.startRow
				&& Double.doubleToLongBits(weight) == Double.doubleToLongBits(other.weight);
	}

}
