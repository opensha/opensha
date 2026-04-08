package org.opensha.sha.earthquake.faultSysSolution.modules;

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.Region;
import org.opensha.commons.util.modules.AverageableModule;
import org.opensha.commons.util.modules.SubModule;
import org.opensha.commons.util.modules.helpers.CSV_BackedModule;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;

public class RupSetTectonicRegimes implements CSV_BackedModule, SubModule<FaultSystemRupSet>,
BranchAverageableModule<RupSetTectonicRegimes>, AverageableModule.ConstantAverageable<RupSetTectonicRegimes>,
SplittableRuptureModule<RupSetTectonicRegimes>{
	
	private FaultSystemRupSet rupSet;
	private TectonicRegionType[] regimes;
	
	public static final String DATA_FILE_NAME = "tectonic_regimes.csv";
	
	private EnumSet<TectonicRegionType> regimeSet;
	
	public static RupSetTectonicRegimes forCSV(FaultSystemRupSet rupSet, CSVFile<String> csv) {
		RupSetTectonicRegimes regimes = new RupSetTectonicRegimes();
		regimes.setParent(rupSet);
		regimes.initFromCSV(csv);
		return regimes;
	}
	
	public RupSetTectonicRegimes(FaultSystemRupSet rupSet, TectonicRegionType[] regimes) {
		if (rupSet != null)
			Preconditions.checkState(rupSet.getNumRuptures() == regimes.length);
		this.rupSet = rupSet;
		this.regimes = regimes;
	}
	
	public static RupSetTectonicRegimes constant(FaultSystemRupSet rupSet, TectonicRegionType regime) {
		TectonicRegionType[] regimes = new TectonicRegionType[rupSet.getNumRuptures()];
		for (int i=0; i<regimes.length; i++)
			regimes[i] = regime;
		return new RupSetTectonicRegimes(rupSet, regimes);
	}
	
	public static RupSetTectonicRegimes forRegions(FaultSystemRupSet rupSet, Map<Region, TectonicRegionType> regRegimes,
			TectonicRegionType fallback, double minFractInside) {
		TectonicRegionType[] regimes = new TectonicRegionType[rupSet.getNumRuptures()];
		
		// first figure out mapping from sections to areas inside
		RuptureSurface[] sectSurfs = new RuptureSurface[rupSet.getNumSections()];
		double[] sectAreas = new double[sectSurfs.length];
		for (int s=0; s<sectSurfs.length; s++) {
			sectSurfs[s] = rupSet.getFaultSectionData(s).getFaultSurface(1d, false, false);
			sectAreas[s] = sectSurfs[s].getArea();
		}
		
		Map<Region, double[]> regSectAreasInside = new HashMap<>();
		
		for (Region reg : regRegimes.keySet()) {
			double[] areasInside = new double[sectSurfs.length];
			for (int s=0; s<sectSurfs.length; s++) {
				FaultSection sect = rupSet.getFaultSectionData(s);
				boolean traceContains = false;
				for (Location loc : sect.getFaultTrace()) {
					if (reg.contains(loc)) {
						traceContains = true;
						break;
					}
				}
				if (traceContains)
					areasInside[s] = sectSurfs[s].getAreaInsideRegion(reg);
			}
			regSectAreasInside.put(reg, areasInside);
		}
		Map<TectonicRegionType, Integer> mappedCounts = new EnumMap<>(TectonicRegionType.class);
		
		for (int r=0; r<regimes.length; r++) {
			double sumArea = 0d;
			List<Integer> sects = rupSet.getSectionsIndicesForRup(r);
			for (int sectIndex : sects)
				sumArea += sectAreas[sectIndex];
			double maxFract = 0d;
			for (Region reg : regRegimes.keySet()) {
				double[] sectAreasInside = regSectAreasInside.get(reg);
				double areaIn = 0d;
				for (int sectIndex : sects)
					areaIn += sectAreasInside[sectIndex];
				if (areaIn > 0d) {
					double fract = areaIn/sumArea;
					if (fract > maxFract && (float)fract >= (float)minFractInside) {
						maxFract = fract;
						regimes[r] = regRegimes.get(reg);
					}
				}
			}
			if (regimes[r] == null)
				regimes[r] = fallback;
			if (regimes[r] != null) {
				Integer prevCount = mappedCounts.get(regimes[r]);
				if (prevCount == null)
					prevCount = 0;
				mappedCounts.put(regimes[r], prevCount+1);
			}
		}
		for (TectonicRegionType trt : mappedCounts.keySet()) {
			int count = mappedCounts.get(trt);
			System.out.println("Mapped "+count+"/"+regimes.length+" ("+pDF.format((double)count/(double)regimes.length)+") to "+trt.name());
		}
		
		return new RupSetTectonicRegimes(rupSet, regimes);
	}
	
	public synchronized Set<TectonicRegionType> getSet() {
		if (regimeSet == null) {
			regimeSet = EnumSet.noneOf(TectonicRegionType.class);
			for (TectonicRegionType trt : regimes)
				regimeSet.add(trt);
		}
		return Collections.unmodifiableSet(regimeSet);
	}
	
	private static final DecimalFormat pDF = new DecimalFormat("0.00%");
	
	@SuppressWarnings("unused") // for deserialization
	private RupSetTectonicRegimes() {}

	@Override
	public String getFileName() {
		return DATA_FILE_NAME;
	}
	
	public TectonicRegionType get(int rupIndex) {
		return regimes[rupIndex];
	}

	@Override
	public String getName() {
		return "Rupture Tectonic Regimes";
	}

	@Override
	public CSVFile<?> getCSV() {
		CSVFile<String> csv = new CSVFile<>(true);
		csv.addLine("Rupture Index", "Tectonic Regime");
		for (int r=0; r<regimes.length; r++) {
			TectonicRegionType type = regimes[r];
			if (type == null)
				csv.addLine(r+"", "");
			else
				csv.addLine(r+"", type.name());
		}
		return csv;
	}

	@Override
	public void initFromCSV(CSVFile<String> csv) {
		Preconditions.checkNotNull(rupSet, "Rupture set must be attached before initialization");
		regimes = new TectonicRegionType[rupSet.getNumRuptures()];
		for (int row=1; row<csv.getNumRows(); row++) {
			int index = csv.getInt(row, 0);
			Preconditions.checkState(index >= 0 && index < regimes.length, "Bad rupture index encountered: %s, numRups=%s", index, regimes.length);
			String regimeStr = csv.get(row, 1);
			if (regimeStr.isBlank())
				regimes[index] = null;
			else
				regimes[index] = TectonicRegionType.valueOf(regimeStr);
		}
	}

	@Override
	public void setParent(FaultSystemRupSet parent) throws IllegalStateException {
		if (regimes != null && parent != null)
			Preconditions.checkState(regimes.length == parent.getNumRuptures());
		this.rupSet = parent;
	}

	@Override
	public FaultSystemRupSet getParent() {
		return rupSet;
	}

	@Override
	public RupSetTectonicRegimes copy(FaultSystemRupSet newParent) throws IllegalStateException {
		return new RupSetTectonicRegimes(newParent, regimes);
	}

	@Override
	public Class<RupSetTectonicRegimes> getAveragingType() {
		return RupSetTectonicRegimes.class;
	}

	@Override
	public boolean isIdentical(RupSetTectonicRegimes module) {
		return Arrays.equals(regimes, module.regimes);
	}

	@Override
	public RupSetTectonicRegimes getForRuptureSubSet(FaultSystemRupSet rupSubSet, RuptureSubSetMappings mappings) {
		TectonicRegionType[] trts = new TectonicRegionType[rupSubSet.getNumRuptures()];
		for (int r=0; r<trts.length; r++)
			trts[r] = this.regimes[mappings.getOrigRupID(r)];
		return new RupSetTectonicRegimes(rupSubSet, trts);
	}

	@Override
	public RupSetTectonicRegimes getForSplitRuptureSet(FaultSystemRupSet splitRupSet,
			RuptureSetSplitMappings mappings) {
		TectonicRegionType[] trts = new TectonicRegionType[splitRupSet.getNumRuptures()];
		for (int r=0; r<trts.length; r++)
			trts[r] = this.regimes[mappings.getOrigRupID(r)];
		return new RupSetTectonicRegimes(splitRupSet, trts);
	}

}
