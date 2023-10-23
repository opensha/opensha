package org.opensha.sha.imr.attenRelImpl.nshmp;

import gov.usgs.earthquake.nshmp.gmm.Gmm;
import gov.usgs.earthquake.nshmp.gmm.GmmInput.Constraints;
import gov.usgs.earthquake.nshmp.gmm.GmmInput.Field;

class ConstraintDebug {

	public static void main(String[] args) {
		Gmm gmmRef = Gmm.AB_03_GLOBAL_INTERFACE;
		Field field = Field.ZTOR;
		
		System.out.println("Debugging constraints for "+gmmRef.name()+", "+field.name());
		
		Constraints constraints = gmmRef.constraints();
		constraints.get(field);
		
		System.out.println("Bounds:\t"+constraints.get(field));
		System.out.println("Default value: "+field.defaultValue);
	}

}
