package org.opensha.sha.earthquake.rupForecastImpl.nshm26.logicTree;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import org.opensha.commons.logicTree.AffectsNone;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.sha.earthquake.rupForecastImpl.nshm26.util.NSHM26_RegionLoader;
import org.opensha.sha.earthquake.rupForecastImpl.nshm26.util.NSHM26_RegionLoader.NSHM26_SeismicityRegions;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

@JsonAdapter(NSHM26_ModelRegimeNode.Adapter.class)
@AffectsNone
public class NSHM26_ModelRegimeNode implements LogicTreeNode.FixedWeightNode, LogicTreeNode.AdapterBackedNode {
	
	private NSHM26_SeismicityRegions region;
	private TectonicRegionType trt;
	
	private NSHM26_ModelRegimeNode() {}

	public NSHM26_ModelRegimeNode(NSHM26_SeismicityRegions region, TectonicRegionType trt) {
		Preconditions.checkNotNull(region, "Must specify region");
		Preconditions.checkNotNull(trt, "Must specify tectonic regime");
		this.region = region;
		this.trt = trt;
	}

	public NSHM26_SeismicityRegions getRegion() {
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
		return region.getShortName()+"-"+NSHM26_RegionLoader.getNameForTRT(trt);
	}

	@Override
	public String getName() {
		return region.getShortName()+" ("+NSHM26_RegionLoader.getNameForTRT(trt)+")";
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
		NSHM26_ModelRegimeNode other = (NSHM26_ModelRegimeNode) obj;
		return region == other.region && trt == other.trt;
	}

	@Override
	public void init(String name, String shortName, String prefix, double weight) {
		// do nothing
	}
	
	public static class Adapter extends TypeAdapter<NSHM26_ModelRegimeNode> {

		@Override
		public void write(JsonWriter out, NSHM26_ModelRegimeNode value) throws IOException {
			out.beginObject();
			out.name("region").value(value.region.name());
			out.name("tectonicRegime").value(value.trt.name());
			out.endObject();
		}

		@Override
		public NSHM26_ModelRegimeNode read(JsonReader in) throws IOException {
			NSHM26_SeismicityRegions region = null;
			TectonicRegionType trt = null;

			in.beginObject();
			while (in.hasNext()) {
				String name = in.nextName();
				switch (name) {
				case "region":
					region = NSHM26_SeismicityRegions.valueOf(in.nextString());
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

			return new NSHM26_ModelRegimeNode(region, trt);
		}
		
	}
	
	public static class Level extends LogicTreeLevel.DataBackedLevel<NSHM26_ModelRegimeNode> {
		
		private NSHM26_SeismicityRegions region;
		private TectonicRegionType trt;
		
		private NSHM26_ModelRegimeNode node;
		
		private static final String NAME = "NSHM26 Model & Regime";
		private static final String SHORT_NAME = "NSHM26 Model";

		private Level() {
			super(NAME, SHORT_NAME);
		}
		
		@SuppressWarnings("unused") // deserialization
		private Level(String levelName, String levelShortName) {
			super(levelName, levelShortName);
		}

		public Level(NSHM26_SeismicityRegions region, TectonicRegionType trt) {
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
			region = NSHM26_SeismicityRegions.valueOf(jsonObj.get("region").getAsString());
			trt = TectonicRegionType.valueOf(jsonObj.get("tectonicRegime").getAsString());
		}
		
		private void checkInitNode() {
			if (node == null)
				node = new NSHM26_ModelRegimeNode(region, trt);
		}

		@Override
		public Class<? extends NSHM26_ModelRegimeNode> getType() {
			return NSHM26_ModelRegimeNode.class;
		}

		@Override
		public List<? extends NSHM26_ModelRegimeNode> getNodes() {
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
