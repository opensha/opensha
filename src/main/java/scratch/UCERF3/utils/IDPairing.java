package scratch.UCERF3.utils;

import java.io.Serializable;

/**
 * This class represents a pairing between two ID numbers, mostly useful with hashmaps.
 * The order of the numbers does matter here, so (0,1) is different than (1,0)
 * 
 * @author Kevin
 *
 */
public class IDPairing implements Comparable<IDPairing>, Serializable {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	private int id1, id2;
	
	public IDPairing(int id1, int id2) {
		this.id1 = id1;
		this.id2 = id2;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + id1;
		result = prime * result + id2;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		IDPairing other = (IDPairing) obj;
		if (id1 != other.id1)
			return false;
		if (id2 != other.id2)
			return false;
		return true;
	}

	public int getID1() {
		return id1;
	}

	public int getID2() {
		return id2;
	}
	
	public IDPairing getReversed() {
		return new IDPairing(id2, id1);
	}

	@Override
	public int compareTo(IDPairing o) {
		int o1 = o.getID1();
		int o2 = o.getID2();
		if (id1 < o1) {
			return -1;
		} else if (id1 > o1) {
			return 1;
		} else {
			if (id2 < o2) {
				return -1;
			} else if (id2 > o2) {
				return 1;
			} else {
				return 0;
			}
		}
	}

	@Override
	public String toString() {
		return "IDPairing [id1=" + id1 + ", id2=" + id2 + "]";
	}

}
