package org.opensha.sha.imr.attenRelImpl.nshmp;

import java.util.BitSet;

import gov.usgs.earthquake.nshmp.gmm.GmmInput;
import gov.usgs.earthquake.nshmp.gmm.GmmInput.Field;

/**
 * Builder of {@link GmmInput} that is mutable. The nshmp-lib version can only set a value once before build() is called.
 * 
 * Values that have never been set will hold NaN
 */
class MutableGmmInputBuilder {
	
	private static Field[] FIELDS = Field.values();
	private static final int LEN = FIELDS.length;
	
	// value array, keyed to enum position
	private double[] values;
	// tracks which values have been set
	private BitSet bitSet;
	
	MutableGmmInputBuilder() {
		values = new double[LEN];
		bitSet = new BitSet(LEN);
	}
	
	void setValue(Field field, double value) {
		int index = field.ordinal();
		values[index] = value;
		bitSet.set(index);
	}
	
	static double valueForField(GmmInput input, Field field) {
		switch (field) {
		case DIP:
			return input.dip;
		case MW:
			return input.Mw;
		case RAKE:
			return input.rake;
		case RJB:
			return input.rJB;
		case RRUP:
			return input.rRup;
		case RX:
			return input.rX;
		case VS30:
			return input.vs30;
		case WIDTH:
			return input.width;
		case Z1P0:
			return input.z1p0;
		case Z2P5:
			return input.z2p5;
		case ZHYP:
			return input.zHyp;
		case ZSED:
			return input.zSed;
		case ZTOR:
			return input.zTor;

		default:
			throw new IllegalStateException("Unsupported field: "+field);
		}
	}
	
	void setAll(GmmInput input) {
		for (Field field : FIELDS)
			setValue(field, valueForField(input, field));
	}
	
	/**
	 * 
	 * @return GmmInput containing all fields that have been set, and anything else set to NaN
	 */
	GmmInput build() {
		GmmInput.Builder builder = GmmInput.builder();
		
		for (int i=0; i<LEN; i++) {
			// this field has been set
			double val = bitSet.get(i) ? values[i] : Double.NaN;
			switch (FIELDS[i]) {
			case DIP:
				builder.dip(val);
				break;
			case MW:
				builder.mag(val);
				break;
			case RAKE:
				builder.rake(val);
				break;
			case RJB:
				builder.rJB(val);
				break;
			case RRUP:
				builder.rRup(val);
				break;
			case RX:
				builder.rX(val);
				break;
			case VS30:
				builder.vs30(val);
				break;
			case WIDTH:
				builder.width(val);
				break;
			case Z1P0:
				builder.z1p0(val);
				break;
			case Z2P5:
				builder.z2p5(val);
				break;
			case ZHYP:
				builder.zHyp(val);
				break;
			case ZSED:
				builder.zSed(val);
				break;
			case ZTOR:
				builder.zTor(val);
				break;

			default:
				throw new IllegalStateException("Unsupported field: "+FIELDS[i]);
			}
		}
		
		return builder.build();
	}

}
