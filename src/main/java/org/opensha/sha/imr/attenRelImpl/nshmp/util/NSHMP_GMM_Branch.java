package org.opensha.sha.imr.attenRelImpl.nshmp.util;

import java.io.IOException;

import org.opensha.commons.logicTree.JsonAdapterHelper;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeNode.AdapterBackedNode;
import org.opensha.sha.imr.AttenRelSupplier;
import org.opensha.sha.imr.attenRelImpl.nshmp.GroundMotionLogicTreeFilter;
import org.opensha.sha.imr.attenRelImpl.nshmp.NSHMP_AttenRelSupplier;
import org.opensha.sha.imr.attenRelImpl.nshmp.NSHMP_GMM_Wrapper;
import org.opensha.sha.imr.logicTree.ScalarIMRsLogicTreeNode;
import org.opensha.sha.imr.logicTree.ScalarIMRsLogicTreeNode.SingleTRT;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import gov.usgs.earthquake.nshmp.gmm.Gmm;

@JsonAdapter(NSHMP_GMM_Branch.Adapter.class)
public class NSHMP_GMM_Branch implements ScalarIMRsLogicTreeNode.SingleTRT, AdapterBackedNode {
	
	private Gmm gmm;
	private GroundMotionLogicTreeFilter treeFilter;
	private String name;
	private String shortName;
	private String filePrefix;
	private double weight;
	
	private TectonicRegionType trt;

	private NSHMP_GMM_Branch(Gmm gmm, GroundMotionLogicTreeFilter treeFilter) {
		this.gmm = gmm;
		this.treeFilter = treeFilter;
		
		this.trt = NSHMP_GMM_Wrapper.trtForType(gmm.type());
	}

	public NSHMP_GMM_Branch(Gmm gmm, GroundMotionLogicTreeFilter treeFilter, String name, String shortName,
			String filePrefix, double weight) {
		this.gmm = gmm;
		this.treeFilter = treeFilter;
		this.name = name;
		this.shortName = shortName;
		this.filePrefix = filePrefix;
		this.weight = weight;
		
		this.trt = NSHMP_GMM_Wrapper.trtForType(gmm.type());
	}

	@Override
	public void init(String name, String shortName, String prefix, double weight) {
		Preconditions.checkState(this.name == null, "Already initialized");
		this.name = name;
		this.shortName = shortName;
		this.filePrefix = prefix;
		this.weight = weight;
	}

	@Override
	public double getNodeWeight(LogicTreeBranch<?> fullBranch) {
		return weight;
	}

	@Override
	public String getShortName() {
		return shortName;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public String getFilePrefix() {
		return filePrefix;
	}

	@Override
	public AttenRelSupplier getSupplier() {
		return new NSHMP_AttenRelSupplier(gmm, shortName, false, treeFilter);
	}

	@Override
	public TectonicRegionType getTectonicRegion() {
		return trt;
	}
	
	public static class Adapter extends TypeAdapter<NSHMP_GMM_Branch> {
		
		private Gson gson;

		@Override
		public void write(JsonWriter out, NSHMP_GMM_Branch value) throws IOException {
			out.beginObject();
            out.name("gmm");
            out.value(value.gmm.name());
            if (value.treeFilter != null) {
            	Preconditions.checkState(JsonAdapterHelper.hasTypeAdapter(value.treeFilter),
            			"Tree filter of type %s doesn't have a JsonAdapter annotation", value.treeFilter.getClass().getName());
            	out.name("treeFilter");
            	JsonAdapterHelper.writeAdapterValue(out, value.treeFilter);
            }
            out.endObject();
		}

		@Override
		public NSHMP_GMM_Branch read(JsonReader in) throws IOException {
			in.beginObject();
			Gmm gmm = null;
			GroundMotionLogicTreeFilter filter = null;
			while (in.hasNext()) {
				switch (in.nextName()) {
				case "gmm":
					gmm = Gmm.valueOf(in.nextString());
					break;
				case "treeFilter":
					filter = (GroundMotionLogicTreeFilter) JsonAdapterHelper.readAdapterValue(in);
					break;

				default:
					break;
				}
			}
			in.endObject();
			Preconditions.checkNotNull(gmm);
			return new NSHMP_GMM_Branch(gmm, filter);
		}
		
	}
	
}