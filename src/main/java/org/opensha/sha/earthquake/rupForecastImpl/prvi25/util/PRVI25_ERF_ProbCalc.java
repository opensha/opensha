package org.opensha.sha.earthquake.rupForecastImpl.prvi25.util;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.text.DecimalFormat;

import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.geo.Region;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceList;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceList.GriddedRupture;
import org.opensha.sha.earthquake.faultSysSolution.modules.RupMFDsModule;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.erf.NSHM25_PRVI_BranchAveragedERF;

public class PRVI25_ERF_ProbCalc {

	public static void main(String[] args) throws IOException {
		NSHM25_PRVI_BranchAveragedERF erf = new NSHM25_PRVI_BranchAveragedERF();
		erf.updateForecast();
		
		FaultSystemSolution solution = erf.getSolution();
		
		Region reg = PRVI25_RegionLoader.loadPRVI_MapExtents();
		
		double[] durations = {1d, 5d, 10d, 30d};
		double[] mags = {6d, 6.5d, 7d, 7.5d};
		
		FaultSystemRupSet rupSet = solution.getRupSet();
		double[] fractRups = rupSet.getFractRupsInsideRegion(reg, false);
		RupMFDsModule rupMFDs = solution.requireModule(RupMFDsModule.class);
		GridSourceList gridList = solution.requireModule(GridSourceList.class);
		
		DecimalFormat pDF = new DecimalFormat("0.0%");
		
		System.out.println("PRVI Map Region");
		
		for (double mag : mags) {
			double rate = 0d;
			for (int r=0; r<fractRups.length; r++) {
				DiscretizedFunc mfd = rupMFDs.getRuptureMFD(r);
				if (mfd == null) {
					if (rupSet.getMagForRup(r) >= mag)
						rate += fractRups[r]*solution.getRateForRup(r);
				} else {
					for (Point2D pt : mfd) {
						if (pt.getX() >= mag)
							rate += fractRups[r]*pt.getY();
					}
				}
			}
			for (int l=0; l<gridList.getNumLocations(); l++) {
				if (reg.contains(gridList.getLocation(l))) {
					for (GriddedRupture rup : gridList.getRuptures(null, l)) {
						if (rup.properties.magnitude >= mag)
							rate += rup.rate;
					}
				}
			}
			System.out.println("M>"+(float)mag+"\t"+(float)rate+" /yr");
			for (double duration : durations)
				System.out.println("\t"+(int)duration+"yr:\t"+pDF.format(1d-Math.exp(-rate*duration)));
		}
	}

}
