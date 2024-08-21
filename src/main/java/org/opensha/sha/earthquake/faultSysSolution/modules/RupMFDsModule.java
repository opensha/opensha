package org.opensha.sha.earthquake.faultSysSolution.modules;

import java.awt.geom.Point2D;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.LightFixedXFunc;
import org.opensha.commons.util.modules.AverageableModule;
import org.opensha.commons.util.modules.SubModule;
import org.opensha.commons.util.modules.helpers.CSV_BackedModule;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;

import com.google.common.base.Preconditions;

/**
 * Module that assigns magnitude-frequency distributions to each rupture, allowing some calculations to use the full
 * distribution rather than the average magnitude returned by the rupture set. This is mostly useful for branch-averaged
 * solutions.
 * 
 * TODO support averaging?
 * 
 * @author kevin
 *
 */
public class RupMFDsModule implements CSV_BackedModule, SubModule<FaultSystemSolution>, AverageableModule<RupMFDsModule> {
	
	private DiscretizedFunc[] rupMFDs;
	
	private FaultSystemSolution parent;
	
	public static final String FILE_NAME = "rup_mfds.csv";
	
	private RupMFDsModule() {
	}

	public RupMFDsModule(FaultSystemSolution sol, DiscretizedFunc[] rupMFDs) {
		super();
		if (rupMFDs != null) {
			Preconditions.checkNotNull(sol);
			Preconditions.checkState(rupMFDs.length == sol.getRupSet().getNumRuptures());
		}
		this.parent = sol;
		this.rupMFDs = rupMFDs;
	}

	@Override
	public String getName() {
		return "Rupture MFDs";
	}

	@Override
	public String getFileName() {
		return FILE_NAME;
	}

	@Override
	public CSVFile<?> getCSV() {
		CSVFile<String> csv = new CSVFile<>(true);
		csv.addLine("Rupture Index", "Magnitude", "Rate");
		for (int r=0; r<rupMFDs.length; r++) {
			// we now serialize single-valued rup MFDs as the x-value for that rupture will likely not equal the
			// 'mean' magnitude for branch averaged solutions, i.e., it will be from a single branch choice and will
			// not be me branch-averaged magnitude
			if (rupMFDs[r] == null || rupMFDs[r].size() == 0)
				continue;
			for (Point2D pt : rupMFDs[r])
				csv.addLine(r+"", pt.getX()+"", pt.getY()+"");
		}
		return csv;
	}

	@Override
	public void initFromCSV(CSVFile<String> csv) {
		Preconditions.checkNotNull(parent, "Cannot init from CSV without parent solution set");
		DiscretizedFunc[] rupMFDs = new DiscretizedFunc[parent.getRupSet().getNumRuptures()];
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
		for (int r=0; r<rupMFDs.length; r++)
			if (rupMFDs[r] != null)
				rupMFDs[r] = new LightFixedXFunc(rupMFDs[r]);
		this.rupMFDs = rupMFDs;
	}
	
	public DiscretizedFunc getRuptureMFD(int rupIndex) {
		return rupMFDs[rupIndex];
	}
	
	public DiscretizedFunc[] getRuptureMFDs() {
		return rupMFDs;
	}

	@Override
	public void setParent(FaultSystemSolution parent) throws IllegalStateException {
		if (parent != null && this.parent != null)
			Preconditions.checkState(this.parent.getRupSet().isEquivalentTo(parent.getRupSet()),
					"Can't update parent! New rupture set with numSects=%s and numRups=%s isn't compatible with "
					+ "previous with numSects=%s and numRups=%s",
					parent.getRupSet().getNumSections(), parent.getRupSet().getNumRuptures(),
					this.parent.getRupSet().getNumSections(), this.parent.getRupSet().getNumRuptures());
		if (rupMFDs != null)
			Preconditions.checkState(rupMFDs.length == parent.getRupSet().getNumRuptures(), "RupMFDs has %s ruptures, but new parent has %s",
					rupMFDs.length, parent.getRupSet().getNumRuptures());
		this.parent = parent;
	}

	@Override
	public FaultSystemSolution getParent() {
		return parent;
	}

	@Override
	public RupMFDsModule copy(FaultSystemSolution newParent) throws IllegalStateException {
		if (parent != null)
			Preconditions.checkState(this.parent.getRupSet().isEquivalentTo(newParent.getRupSet()));
		return new RupMFDsModule(newParent, rupMFDs);
	}

	@Override
	public AveragingAccumulator<RupMFDsModule> averagingAccumulator() {
		return new AveragingAccumulator<RupMFDsModule>() {
			
			private double sumWeight = 0d;
			
			private DiscretizedFunc[] rupMFDs = null;
			
			@Override
			public void process(RupMFDsModule module, double relWeight) {
				if (rupMFDs == null)
					rupMFDs = new DiscretizedFunc[parent.getRupSet().getNumRuptures()];
				
				sumWeight += relWeight;
				
				for (int r=0; r<rupMFDs.length; r++) {
					DiscretizedFunc mfd = module.rupMFDs[r];
					if (mfd != null && mfd.calcSumOfY_Vals() > 0d) {
						if (rupMFDs[r] == null)
							rupMFDs[r] = new ArbitrarilyDiscretizedFunc();
						for (Point2D pt : mfd) {
							double x = pt.getX();
							double y = pt.getY()*relWeight;
							if (rupMFDs[r].hasX(x))
								y += rupMFDs[r].getY(x);
							rupMFDs[r].set(x, y);
						}
					}
				}
			}
			
			@Override
			public Class<RupMFDsModule> getType() {
				return RupMFDsModule.class;
			}
			
			@Override
			public RupMFDsModule getAverage() {
				double scale = 1d/sumWeight;
				for (DiscretizedFunc mfd : rupMFDs) {
					if (mfd != null)
						mfd.scale(scale);
				}
				RupMFDsModule avg = new RupMFDsModule();
				// do it this way to bypass setting the solution, which will be set later
				avg.rupMFDs = rupMFDs;
				return avg;
			}
		};
	}

}