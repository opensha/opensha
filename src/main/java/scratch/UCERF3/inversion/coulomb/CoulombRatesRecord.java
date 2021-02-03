package scratch.UCERF3.inversion.coulomb;

import org.opensha.commons.util.IDPairing;

import com.google.common.base.Preconditions;

public class CoulombRatesRecord {
	
	private double ds, pds, dcff, pdcff;
	private IDPairing pairing;
	
	public CoulombRatesRecord(IDPairing pairing, double ds, double pds, double dcff, double pdcff) {
		Preconditions.checkNotNull(pairing);
		this.pairing = pairing;
		Preconditions.checkState(Double.isFinite(ds) && Double.isFinite(pds)
				&& Double.isFinite(dcff) && Double.isFinite(pdcff));
		Preconditions.checkState(pds >= 0 && pds <= 1, "bad pds=%s", pds);
		Preconditions.checkState(pdcff >= 0 && pdcff <= 1, "bad pdcff=%s", pdcff);
		this.ds = ds;
		this.pds = pds;
		this.dcff = dcff;
		this.pdcff = pdcff;
	}
	
	/**
	 * This gets the Ds value from the table: change in shear stress
	 * 
	 * @return
	 */
	public double getShearStressChange() {
		return ds;
	}
	
	/**
	 * This gets the probability (based on shear stress change) that a rupture will follow this path
	 * from subsection1 to subsection2 instead of any other choices that it has.
	 * 
	 * @return
	 */
	public double getShearStressProbability() {
		return pds;
	}
	
	/**
	 * This gets the DCFF value from the table: change in Coulomb stress (includes component
	 * of stress normal to the target fault plane). This is important for dip-slip faults especially.
	 * 
	 * @return
	 */
	public double getCoulombStressChange() {
		return dcff;
	}
	
	/**
	 * This gets the probability (based on Coulomb stress change) that a rupture will follow this
	 * path from subsection1 to subsection2 instead of any other choices that it has.
	 * 
	 * @return
	 */
	public double getCoulombStressProbability() {
		return pdcff;
	}
	
	/**
	 * Gives the pairing for these values
	 * 
	 * @return
	 */
	public IDPairing getPairing() {
		return pairing;
	}

	@Override
	public String toString() {
		return "CoulombRatesRecord [pairing="+pairing+", ds=" + ds + ", pds=" + pds + ", dcff="
				+ dcff + ", pdcff=" + pdcff + "]";
	}

}
