package org.opensha.sha.earthquake.param;

/**
 * Magnitude-dependent aperiodicity options for discrete magnitude ranges.
 * @author Ned Field
 * @version $Id:$
 */
@SuppressWarnings("javadoc")
public enum MagDependentAperiodicityOptions {
	LOW_VALUES("0.4,0.3,0.2,0.1", new double[] {0.4,0.3,0.2,0.1}, new double[] {6.7,7.2,7.7}),
	MID_VALUES("0.5,0.4,0.3,0.2", new double[] {0.5,0.4,0.3,0.2}, new double[] {6.7,7.2,7.7}),
	HIGH_VALUES("0.6,0.5,0.4,0.3",new double[] {0.6,0.5,0.4,0.3}, new double[] {6.7,7.2,7.7}),
	ALL_PT1_VALUES("All 0.1",new double[] {0.1}, null),
	ALL_PT2_VALUES("All 0.2",new double[] {0.2}, null),
	ALL_PT3_VALUES("All 0.3",new double[] {0.3}, null),
	ALL_PT4_VALUES("All 0.4",new double[] {0.4}, null),
	ALL_PT5_VALUES("All 0.5",new double[] {0.5}, null),
	ALL_PT6_VALUES("All 0.6",new double[] {0.6}, null),
	ALL_PT7_VALUES("All 0.7",new double[] {0.7}, null),
	ALL_PT8_VALUES("All 0.8",new double[] {0.8}, null),
	ALL_PT9_VALUES("All 0.9",new double[] {0.9}, null),
	ALL_1PT0_VALUES("All 1.0",new double[] {1.0}, null);
	
	private String label;
	private double[] aperMagBoundariesArray;	// the magnitude boundaries; must have one less element than the next array
	private double[] aperValuesArray;
	private MagDependentAperiodicityOptions(String label, double[] aperValuesArray, double[] aperMagBoundariesArray) {
		this.label = label;
		this.aperValuesArray=aperValuesArray;
		this.aperMagBoundariesArray=aperMagBoundariesArray;
	}
	
	@Override public String toString() {
		return label;
	}
	
	/**
	 * This is an array of aperiodicity values for the magnitudes ranges defined by 
	 * what's returned by getAperMagThreshArray().
	 * @return
	 */
	public double[] getAperValuesArray(){
		return aperValuesArray;
	}
	
	/**
	 * The magnitude boundaries for the different aperoidicities returned by getAperValuesArray(),
	 * where null is returned if the latter has only one value.
	 * @return
	 */
	public double[] getAperMagBoundariesArray(){
		return aperMagBoundariesArray;
	}
	
	public int getNumAperValues(){
		return aperValuesArray.length;
	}
	
	public int getAperIndexForRupMag(double rupMag) {
		int index = -1;
		if(aperValuesArray.length==1)
			index = 0;	// only one
		else if(rupMag>aperMagBoundariesArray[aperValuesArray.length-2])	// minus 2 to get last value since aperMagBoundaries has one less element
			index = aperValuesArray.length-1;	// the last one
		else {
			for(int m=0; m<aperMagBoundariesArray.length;m++) {
				if(rupMag<=aperMagBoundariesArray[m]) {
					index = m;
					break;
				}
			}
		}
		return index;
	}
	
	public double getAperForRupMag(double rupMag) {
		return aperValuesArray[getAperIndexForRupMag(rupMag)];
	}
	
	public String getMagDepAperInfoString(int aperIndex) {
		if(aperValuesArray.length == 1) {
			return "aper="+aperValuesArray[0];
		}
		if(aperIndex == 0) {	// first one
			return "M<="+aperMagBoundariesArray[0]+"; aper="+aperValuesArray[0];
		}
		else if(aperIndex == aperValuesArray.length-1) {	// last one
			return "M>"+aperMagBoundariesArray[aperValuesArray.length-2]+"; aper="+aperValuesArray[aperValuesArray.length-1];
		}
		else {	// intermediate one
			return aperMagBoundariesArray[aperIndex-1]+"<M<="+aperMagBoundariesArray[aperIndex]+"; aper="+aperValuesArray[aperIndex];
		}
	}



}
