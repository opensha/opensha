package scratch.stirling;

import static org.opensha.sha.util.TectonicRegionType.*;
import static org.opensha.nshmp2.util.FocalMech.*;

import java.util.Map;

import org.opensha.nshmp2.util.FocalMech;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.collect.ImmutableMap;

/**
 * Add comments here
 *
 * @author Peter Powers
 */
public enum NZ_SourceID {
	
	// TODO this is missing 'ro'

	RV(90.0, ACTIVE_SHALLOW),
	IF(90.0, SUBDUCTION_INTERFACE),

	SS(0.0, ACTIVE_SHALLOW),
	NN(-90.0, ACTIVE_SHALLOW),
	NV(-90.0, VOLCANIC),
	
	SR(30.0, ACTIVE_SHALLOW),
	RS(60.0, ACTIVE_SHALLOW),
	NS(-60.0, ACTIVE_SHALLOW),
	SN(-30.0, ACTIVE_SHALLOW);	
	
	private double rake;
	private TectonicRegionType trt;
	
	private NZ_SourceID(double rake, TectonicRegionType trt) {
		this.rake = rake;
		this.trt = trt;
	}
	
	/**
	 * Convert 'id' from source file to enum type.
	 * @param id
	 * @return the source identifier
	 */
	public static NZ_SourceID fromString(String id) {
		return NZ_SourceID.valueOf(id.toUpperCase());
	}
	
	/**
	 * Returns the rake.
	 * @return the rake
	 */
	public double rake() {
		return rake;
	}
	
	/**
	 * Returns the {code TectonicRegionType}.
	 * @return the {code TectonicRegionType}
	 */
	public TectonicRegionType tectonicType() {
		return trt;
	}
	
	private static final Map<FocalMech, Double> SS_MAP = ImmutableMap.of(STRIKE_SLIP, 1.0, REVERSE, 0.0, NORMAL, 0.0);
	private static final Map<FocalMech, Double> N_MAP = ImmutableMap.of(STRIKE_SLIP, 0.0, REVERSE, 0.0, NORMAL, 1.0);
	private static final Map<FocalMech, Double> R_MAP = ImmutableMap.of(STRIKE_SLIP, 0.0, REVERSE, 1.0, NORMAL, 0.0);
	private static final Map<FocalMech, Double> R_SS_MAP = ImmutableMap.of(STRIKE_SLIP, 0.333, REVERSE, 0.667, NORMAL, 0.0);
	private static final Map<FocalMech, Double> SS_R_MAP = ImmutableMap.of(STRIKE_SLIP, 0.667, REVERSE, 0.333, NORMAL, 0.0);
	private static final Map<FocalMech, Double> N_SS_MAP = ImmutableMap.of(STRIKE_SLIP, 0.333, REVERSE, 0.0, NORMAL, 0.667);
	private static final Map<FocalMech, Double> SS_N_MAP = ImmutableMap.of(STRIKE_SLIP, 0.667, REVERSE, 0.0, NORMAL, 0.333);
	
	
	/**
	 * Returns the focal mech weights associated with the type.
	 * @return a mech weight map
	 */
	public Map<FocalMech, Double> mechWtMap() {
		switch (this) {
			case IF:
				return R_MAP;
			case NN:
				return N_MAP;
			case NS:
				return N_SS_MAP;
			case NV:
				return N_MAP;
			case RS:
				return R_SS_MAP;
			case RV:
				return R_MAP;
			case SN:
				return SS_N_MAP;
			case SR:
				return SS_R_MAP;
			case SS:
				return SS_MAP;
			default:
				return SS_MAP;
		}
	}
}
