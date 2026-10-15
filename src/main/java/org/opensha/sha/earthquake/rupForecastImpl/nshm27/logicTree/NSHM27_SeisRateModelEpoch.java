package org.opensha.sha.earthquake.rupForecastImpl.nshm27.logicTree;

import java.text.DecimalFormat;
import java.util.List;
import java.util.Objects;

import org.opensha.commons.logicTree.LogicTreeLevel.DataBackedLevel;
import org.opensha.commons.logicTree.LogicTreeLevel.ValueBackedLevel;
import org.opensha.commons.logicTree.Affects;
import org.opensha.commons.logicTree.DoesNotAffect;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.logicTree.LogicTreeNode.ValuedLogicTreeNode;
import org.opensha.sha.earthquake.rupForecastImpl.nshm27.util.NSHM27_RegionLoader;
import org.opensha.sha.earthquake.rupForecastImpl.nshm27.util.NSHM27_RegionLoader.NSHM27_SeismicityRegions;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceList;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceProvider;
import org.opensha.sha.util.TectonicRegionType;

import com.google.gson.JsonObject;

@Affects(FaultSystemRupSet.SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_PROPS_FILE_NAME)
@Affects(FaultSystemSolution.RATES_FILE_NAME)
@DoesNotAffect(GridSourceProvider.ARCHIVE_GRID_REGION_FILE_NAME)
@DoesNotAffect(GridSourceList.ARCHIVE_GRID_LOCS_FILE_NAME)
@Affects(GridSourceList.ARCHIVE_GRID_SOURCES_FILE_NAME)
public final class NSHM27_SeisRateModelEpoch implements ValuedLogicTreeNode<Integer> {

	private final NSHM27_SeismicityRegions region;
	private final TectonicRegionType trt;
	private final String datasetId;
	private final double startYear;
	private final double endYear;
	private final double mc;
	private final int index;
	private final double weight;

	NSHM27_SeisRateModelEpoch(NSHM27_SeismicityRegions region, TectonicRegionType trt, String datasetId,
			double startYear, double endYear, double mc, int index, double weight) {
		this.region = region;
		this.trt = trt;
		this.datasetId = datasetId;
		this.startYear = startYear;
		this.endYear = endYear;
		this.mc = mc;
		this.index = index;
		this.weight = weight;
	}

	private static final DecimalFormat oDF = new DecimalFormat("0.#");

	public NSHM27_SeismicityRegions getRegion() { return region; }
	public TectonicRegionType getTectonicRegion() { return trt; }
	public String getDatasetId() { return datasetId; }
	public double getStartYear() { return startYear; }
	public double getEndYear() { return endYear; }
	public double getMagComplete() { return mc; }

	/** @return zero-based index in source-file order */
	public int getEpochIndex() { return index; }

	@Override
	public Integer getValue() { return index; }

	@Override
	public Class<? extends Integer> getValueType() { return Integer.class; }

	@Override
	public void init(Integer value, Class<? extends Integer> valueClass, double weight,
			String name, String shortName, String filePrefix) {
		throw new UnsupportedOperationException("Epoch nodes must be built by their catalog-backed level");
	}

	@Override
	public String getFilePrefix() {
		return NSHM27_RegionLoader.getNameForTRT(trt)+"Epoch"+(index+1);
	}

	@Override
	public String getShortName() {
		return oDF.format(startYear)+"-"+oDF.format(endYear);
	}

	@Override
	public String getName() {
		return "Epoch "+(index+1)+": "+getShortName();
	}

	@Override
	public double getNodeWeight() { return weight; }

	@Override
	public int hashCode() { return Objects.hash(region, trt, datasetId, index); }

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (!(obj instanceof NSHM27_SeisRateModelEpoch)) return false;
		NSHM27_SeisRateModelEpoch other = (NSHM27_SeisRateModelEpoch)obj;
		return region == other.region && trt == other.trt && index == other.index && datasetId.equals(other.datasetId);
	}

	@Override
	public String toString() {
		return getName()+" ["+region+", "+trt+", "+datasetId+"]";
	}

	public static final class Level extends DataBackedLevel<NSHM27_SeisRateModelEpoch>
	implements ValueBackedLevel<Integer, NSHM27_SeisRateModelEpoch> {

		private NSHM27_SeisRateModelEpochCatalog catalog;

		@SuppressWarnings("unused") // deserialization
		private Level(String name, String shortName) {
			super(name, shortName);
		}

		public Level(NSHM27_SeisRateModelEpochCatalog catalog) {
			super(NSHM27_RegionLoader.getNameForTRT(catalog.getTectonicRegion())+" Seismicity Rate Epoch",
					NSHM27_RegionLoader.getNameForTRT(catalog.getTectonicRegion())+"Epoch");
			this.catalog = catalog;
		}

		public NSHM27_SeisRateModelEpochCatalog getCatalog() { return catalog; }

		@Override
		public Class<? extends NSHM27_SeisRateModelEpoch> getType() { return NSHM27_SeisRateModelEpoch.class; }

		@Override
		public List<? extends NSHM27_SeisRateModelEpoch> getNodes() { return catalog.getEpochs(); }

		@Override
		public boolean isMember(LogicTreeNode node) { return catalog.getEpochs().contains(node); }

		@Override
		public Class<? extends Integer> getValueType() { return Integer.class; }

		@Override
		public NSHM27_SeisRateModelEpoch build(Integer index, double weight, String name,
				String shortName, String filePrefix) {
			return catalog.getEpoch(index);
		}

		@Override
		public JsonObject toJsonObject() { return catalog.toJSON(); }

		@Override
		public void initFromJsonObject(JsonObject jsonObj) {
			catalog = NSHM27_SeisRateModelEpochCatalog.fromJSON(jsonObj);
		}
	}
}
