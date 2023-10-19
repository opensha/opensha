package org.opensha.sha.earthquake.faultSysSolution.modules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.util.modules.SubModule;
import org.opensha.commons.util.modules.helpers.CSV_BackedModule;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.RupSetDeformationModel;
import org.opensha.sha.earthquake.faultSysSolution.util.TrueMeanSolutionCreator;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;

/**
 * This module stores rake information for individual deformation models. This can be used to override rakes in
 * hazard calculations with those from a particular model, e.g. when using a "true mean" solution
 * (see {@link TrueMeanSolutionCreator}.
 */
public class DefModelRupRakes implements CSV_BackedModule, SubModule<FaultSystemRupSet> {
	
	public static final String FILE_NAME = "def_model_rup_rakes.csv";
	
	private FaultSystemRupSet rupSet;
	private ImmutableMap<String, double[]> rakesMap;
	
	@SuppressWarnings("unused") // for deserialization
	private DefModelRupRakes() {}
	
	public DefModelRupRakes(FaultSystemRupSet rupSet, Map<RupSetDeformationModel, double[]> rakesMap) {
		Preconditions.checkNotNull(rupSet, "Must supply rupture set");
		this.rupSet = rupSet;
		
		Preconditions.checkNotNull(rakesMap, "Must supply rakes map");
		Preconditions.checkArgument(!rakesMap.isEmpty(), "Rakes map cannot be empty");
		Map<String, double[]> namesToRakesMap = new LinkedHashMap<>(); // linked to preserve iteration order
		for (RupSetDeformationModel dm : rakesMap.keySet()) {
			double[] rakes = rakesMap.get(dm);
			Preconditions.checkArgument(rakes.length == rupSet.getNumRuptures(),
					"Rake size mismatch for %s: %s != %s", dm, rakes.length, rupSet.getNumRuptures());
			Preconditions.checkState(!namesToRakesMap.containsKey(dm.getName()), "DM names not unique: %s", dm.getName());
			namesToRakesMap.put(dm.getName(), rakes);
		}
		this.rakesMap = ImmutableMap.copyOf(namesToRakesMap);
	}

	@Override
	public String getFileName() {
		return FILE_NAME;
	}

	@Override
	public String getName() {
		return "Def. Model-Specific Rupture Rakes";
	}
	
	public double[] getRakes(RupSetDeformationModel dm) {
		return getRakes(dm.getName());
	}
	
	public double[] getRakes(String dmName) {
		return rakesMap.get(dmName);
	}

	@Override
	public CSVFile<?> getCSV() {
		CSVFile<String> csv = new CSVFile<>(true);
		List<String> header = new ArrayList<>();
		header.add("Rupture Index");
		List<double[]> dmRakes = new ArrayList<>();
		for (String dm : rakesMap.keySet()) {
			header.add(dm);
			dmRakes.add(rakesMap.get(dm));
		}
		csv.addLine(header);
		
		for (int rupIndex=0; rupIndex<rupSet.getNumRuptures(); rupIndex++) {
			List<String> line = new ArrayList<>(header.size());
			line.add(rupIndex+"");
			for (double[] rakes : dmRakes)
				line.add((float)rakes[rupIndex]+"");
			csv.addLine(line);
		}
		return csv;
	}

	@Override
	public void initFromCSV(CSVFile<String> csv) {
		List<String> header = csv.getLine(0);
		int numRups = csv.getNumRows()-1;
		Map<String, double[]> rakesMap = new LinkedHashMap<>(); // linked for guaranteed iteration order
		List<double[]> rakesList = new ArrayList<>();
		for (int col=1; col<header.size(); col++) {
			String name = header.get(col);
			Preconditions.checkState(!rakesMap.containsKey(name), "Duplicate name: %s", name);
			double[] rakes = new double[numRups];
			rakesMap.put(name, rakes);
			rakesList.add(rakes);
		}
		for (int row=1; row<csv.getNumRows(); row++) {
			int rupIndex = csv.getInt(row, 0);
			Preconditions.checkState(rupIndex == row-1,
					"Ruptures not in order? Expected rupIndex=%s for row %s, got %s", row-1, row, rupIndex);
			for (int d=0; d<rakesMap.size(); d++)
				rakesList.get(d)[rupIndex] = csv.getDouble(row, d+1);
		}
		this.rakesMap = ImmutableMap.copyOf(rakesMap);
	}

	@Override
	public void setParent(FaultSystemRupSet parent) throws IllegalStateException {
		Preconditions.checkNotNull(parent, "Cannot set null rupture set");
		Preconditions.checkNotNull(rakesMap, "Must initialize first");
		if (this.rupSet == null) {
			// validate rakes
			for (String dm : rakesMap.keySet()) {
				double[] rakes = rakesMap.get(dm);
				Preconditions.checkState(rakes.length == rupSet.getNumRuptures());
			}
		} else {
			// make sure they're equivalent
			Preconditions.checkState(this.rupSet.isEquivalentTo(parent),
					"New rupture set is not equivalent to original");
		}
		this.rupSet = parent;
	}

	@Override
	public FaultSystemRupSet getParent() {
		return rupSet;
	}

	@Override
	public SubModule<FaultSystemRupSet> copy(FaultSystemRupSet newParent) throws IllegalStateException {
		DefModelRupRakes ret = new DefModelRupRakes();
		ret.rakesMap = rakesMap;
		ret.setParent(newParent);
		return ret;
	}

}
