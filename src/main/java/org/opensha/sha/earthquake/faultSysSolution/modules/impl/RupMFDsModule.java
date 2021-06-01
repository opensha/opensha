package org.opensha.sha.earthquake.faultSysSolution.modules.impl;

import java.awt.geom.Point2D;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.CSV_BackedModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.SolutionModule;

import com.google.common.base.Preconditions;

/**
 * Module that assigns magnitude-frequency distributions to each rupture, allowing some calculations to use the full
 * distribution rather than the average magnitude returned by the rupture set. This is mostly useful for branch-averaged
 * solutions.
 * 
 * @author kevin
 *
 */
public class RupMFDsModule extends SolutionModule implements CSV_BackedModule {
	
	private DiscretizedFunc[] rupMFDs;
	
	private RupMFDsModule() {
		super(null);
	}

	public RupMFDsModule(FaultSystemSolution sol, DiscretizedFunc[] rupMFDs) {
		super(sol);
		this.rupMFDs = rupMFDs;
		int numRups = sol.getRupSet().getNumRuptures();
		Preconditions.checkState(numRups == rupMFDs.length,
				"Have %s ruptures but %s rupture MFDs", numRups, rupMFDs.length);
	}

	@Override
	public String getName() {
		return "Rupture MFDs";
	}

	@Override
	public String getCSV_FileName() {
		return "rup_mfds.csv";
	}

	@Override
	public CSVFile<?> getCSV() {
		CSVFile<String> csv = new CSVFile<>(true);
		csv.addLine("Rupture Index", "Magnitude", "Rate");
		for (int r=0; r<rupMFDs.length; r++) {
			if (rupMFDs[r] == null || rupMFDs[r].size() == 1)
				continue;
			for (Point2D pt : rupMFDs[r])
				csv.addLine(r+"", pt.getX()+"", pt.getY()+"");
		}
		return csv;
	}

	@Override
	public void initFromCSV(CSVFile<String> csv) {
		DiscretizedFunc[] rupMFDs = new DiscretizedFunc[getSolution().getRupSet().getNumRuptures()];
		for (int row=1; row<csv.getNumRows(); row++) {
			int r = csv.getInt(row, 0);
			double mag = csv.getDouble(row, 1);
			double rate = csv.getDouble(row, 2);
			if (rupMFDs[r] == null)
				rupMFDs[r] = new ArbitrarilyDiscretizedFunc();
			Preconditions.checkState(!rupMFDs[r].hasX(mag),
					"Duplicate magntiude encountered for rupture %s: %s", r+"", mag);
			rupMFDs[r].set(mag, rate);
		}
		// TODO turn them into light fixed-x functions?
		this.rupMFDs = rupMFDs;
	}
	
	public DiscretizedFunc getRuptureMFD(int rupIndex) {
		return rupMFDs[rupIndex];
	}

}
