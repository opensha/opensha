package gov.usgs.earthquake.nshmp.erf.nshm27.logicTree;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import org.opensha.commons.logicTree.AffectsNone;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import gov.usgs.earthquake.nshmp.erf.nshm27.util.NSHM27_RegionLoader;
import gov.usgs.earthquake.nshmp.erf.nshm27.util.NSHM27_RegionLoader.NSHM27_SeismicityRegions;

@JsonAdapter(NSHM27_ModelRegimeNode.Adapter.class)
@AffectsNone
public class NSHM27_ModelRegimeNode implements LogicTreeNode.FixedWeightNode, LogicTreeNode.AdapterBackedNode {
	
	private NSHM27_SeismicityRegions region;
	private TectonicRegionType trt;
	
	private NSHM27_ModelRegimeNode() {}

	public NSHM27_ModelRegimeNode(NSHM27_SeismicityRegions region, TectonicRegionType trt) {
		Preconditions.checkNotNull(region, "Must specify region");
		Preconditions.checkNotNull(trt, "Must specify tectonic regime");
		this.region = region;
		this.trt = trt;
	}

	public NSHM27_SeismicityRegions getRegion() {
		return region;
	}

	public TectonicRegionType getTectonicRegime() {
		return trt;
	}

	@Override
	public String getFilePrefix() {
		return region.name()+"_"+trt.name();
	}

	@Override
	public String getShortName() {
		return region.getShortName()+"-"+NSHM27_RegionLoader.getNameForTRT(trt);
	}

	@Override
	public String getName() {
		return region.getShortName()+" ("+NSHM27_RegionLoader.getNameForTRT(trt)+")";
	}

	@Override
	public double getNodeWeight() {
		return 1;
	}

	@Override
	public int hashCode() {
		return Objects.hash(region, trt);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		NSHM27_ModelRegimeNode other = (NSHM27_ModelRegimeNode) obj;
		return region == other.region && trt == other.trt;
	}

	@Override
	public void init(String name, String shortName, String prefix, double weight) {
		// do nothing
	}
	
	public static class Adapter extends TypeAdapter<NSHM27_ModelRegimeNode> {

		@Override
		public void write(JsonWriter out, NSHM27_ModelRegimeNode value) throws IOException {
			out.beginObject();
			out.name("region").value(value.region.name());
			out.name("tectonicRegime").value(value.trt.name());
			out.endObject();
		}

		@Override
		public NSHM27_ModelRegimeNode read(JsonReader in) throws IOException {
			NSHM27_SeismicityRegions region = null;
			TectonicRegionType trt = null;

			in.beginObject();
			while (in.hasNext()) {
				String name = in.nextName();
				switch (name) {
				case "region":
					region = NSHM27_SeismicityRegions.valueOf(in.nextString());
					break;
				case "tectonicRegime":
					trt = TectonicRegionType.valueOf(in.nextString());
					break;
				default:
					throw new IOException("Unexpected name: " + name);
				}
			}
			in.endObject();

			if (region == null)
				throw new IOException("Missing region");
			if (trt == null)
				throw new IOException("Missing tectonic regime");

			return new NSHM27_ModelRegimeNode(region, trt);
		}
		
	}
	
	public static class Level extends LogicTreeLevel.DataBackedLevel<NSHM27_ModelRegimeNode> {
		
		private NSHM27_SeismicityRegions region;
		private TectonicRegionType trt;
		
		private NSHM27_ModelRegimeNode node;
		
		private static final String NAME = "NSHM27 Model & Regime";
		private static final String SHORT_NAME = "NSHM27 Model";

		private Level() {
			super(NAME, SHORT_NAME);
		}
		
		@SuppressWarnings("unused") // deserialization
		private Level(String levelName, String levelShortName) {
			super(levelName, levelShortName);
		}

		public Level(NSHM27_SeismicityRegions region, TectonicRegionType trt) {
			this();
			this.region = region;
			this.trt = trt;
		}

		@Override
		public JsonObject toJsonObject() {
			JsonObject json = new JsonObject();
			json.add("region", new JsonPrimitive(region.name()));
			json.add("tectonicRegime", new JsonPrimitive(trt.name()));
			return json;
		}

		@Override
		public void initFromJsonObject(JsonObject jsonObj) {
			region = NSHM27_SeismicityRegions.valueOf(jsonObj.get("region").getAsString());
			trt = TectonicRegionType.valueOf(jsonObj.get("tectonicRegime").getAsString());
		}
		
		private void checkInitNode() {
			if (node == null)
				node = new NSHM27_ModelRegimeNode(region, trt);
		}

		@Override
		public Class<? extends NSHM27_ModelRegimeNode> getType() {
			return NSHM27_ModelRegimeNode.class;
		}

		@Override
		public List<? extends NSHM27_ModelRegimeNode> getNodes() {
			checkInitNode();
			return List.of(node);
		}

		@Override
		public boolean isMember(LogicTreeNode node) {
			checkInitNode();
			return this.node.equals(node);
		}
		
	}

}
