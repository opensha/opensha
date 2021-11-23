package org.opensha.sha.earthquake.faultSysSolution;

import org.opensha.commons.calc.magScalingRelations.MagAreaRelationship;
import org.opensha.commons.calc.magScalingRelations.MagLengthRelationship;
import org.opensha.commons.data.Named;

/**
 * Interface for the scaling relationships needed to construct a {@link FaultSystemRupSet}. Implementations will likely
 * use a {@link MagAreaRelationship}, {@link MagLengthRelationship}, or a combination therein.
 * 
 * @author kevin
 *
 */
public interface RupSetScalingRelationship extends Named {

	/**
	 * This returns the slip (m) for the given rupture area (m-sq) or rupture length (m)
	 * @param area (m-sq)
	 * @param length (m)
	 * @param origWidth (m) - the original down-dip width (before reducing by aseismicity factor)
	 * @param aveRake average rake of this rupture
	 * @return
	 */
	public abstract double getAveSlip(double area, double length, double origWidth, double aveRake);

	/**
	 * This returns the magnitude for the given rupture area (m-sq) and width (m)
	 * @param area (m-sq)
	 * @param origWidth (m) - the original down-dip width (before reducing by aseismicity factor)
	 * @param aveRake average rake of this rupture
	 * @return
	 */
	public abstract double getMag(double area, double origWidth, double aveRake);

}
