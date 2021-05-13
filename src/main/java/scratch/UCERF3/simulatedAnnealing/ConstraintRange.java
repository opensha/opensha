package scratch.UCERF3.simulatedAnnealing;

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
	 * Last for for this constraint (exclusive)
	 */
	public final int endRow;
	public final boolean inequality;
	
	public ConstraintRange(String name, String shortName,
			int startRow, int endRow, boolean inequality) {
		this.name = name;
		this.shortName = shortName;
		this.startRow = startRow;
		this.endRow = endRow;
		this.inequality = inequality;
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
