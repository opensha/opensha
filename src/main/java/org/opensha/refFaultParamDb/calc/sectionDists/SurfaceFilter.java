package org.opensha.refFaultParamDb.calc.sectionDists;

import org.opensha.sha.faultSurface.EvenlyGriddedSurface;

public interface SurfaceFilter {
	
	public double getCornerMidptFilterDist();
	
	public boolean isIncluded(EvenlyGriddedSurface surface, int row, int col);

}
