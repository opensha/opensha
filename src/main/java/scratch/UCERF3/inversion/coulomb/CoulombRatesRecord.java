package scratch.UCERF3.inversion.coulomb;

import org.opensha.commons.util.IDPairing;

import com.google.common.base.Preconditions;

public class CoulombRatesRecord {
	
	private double ds, pds, dcff, pdcff;
	private IDPairing pairing;
	
	public CoulombRatesRecord(IDPairing pairing, double ds, double pds, double dcff, double pdcff) {
		Preconditions.checkNotNull(pairing);
		this.pairing = pairing;
		Preconditions.checkState(!Double.isNaN(ds) && !Double.isNaN(pds)
				&& !Double.isNaN(dcff) && !Double.isNaN(pdcff) && !Double.isNaN(ds));
		Preconditions.checkState(!Double.isInfinite(ds) && !Double.isInfinite(pds)
				&& !Double.isInfinite(dcff) && !Double.isInfinite(pdcff) && !Double.isInfinite(ds));
		Preconditions.checkState(pds >= 0 && pds <= 1);
		Preconditions.checkState(pdcff >= 0 && pdcff <= 1);
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
