package org.opensha.sha.earthquake.faultSysSolution.modules.impl;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.modules.JSON_BackedModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.RupSetModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.StatefulModule;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;

import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

public abstract class ClusterRuptures extends RupSetModule {

	public ClusterRuptures(FaultSystemRupSet rupSet) {
		super(rupSet);
	}
	
	public abstract List<ClusterRupture> get();
	
	public static ClusterRuptures instance(FaultSystemRupSet rupSet, List<ClusterRupture> clusterRuptures) {
		boolean singleStrand = true;
		for (ClusterRupture rup : clusterRuptures) {
			if (!rup.singleStrand) {
				singleStrand = false;
				break;
			}
		}
		if (singleStrand)
			return new SingleStranded(rupSet, clusterRuptures);
		return new Precomputed(rupSet, clusterRuptures);
	}
	
	private static class SingleStranded extends ClusterRuptures implements StatefulModule {
		
		private List<ClusterRupture> clusterRuptures;
		
		private SingleStranded() {
			super(null);
		}

		public SingleStranded(FaultSystemRupSet rupSet, List<ClusterRupture> clusterRuptures) {
			super(rupSet);
			this.clusterRuptures = clusterRuptures;
		}

		@Override
		public String getName() {
			return "Single-Strand Cluster Ruptures";
		}

		@Override
		public void writeToArchive(ZipOutputStream zout, String entryPrefix) throws IOException {
			// do nothing
		}

		@Override
		public void initFromArchive(ZipFile zip, String entryPrefix) throws IOException {
			// do nothing
		}

		@Override
		public List<ClusterRupture> get() {
			if (clusterRuptures == null) {
				synchronized (this) {
					if (clusterRuptures == null) {
						// build them
						FaultSystemRupSet rupSet = getRupSet();
						SectionDistanceAzimuthCalculator distAzCalc = null;
						if (rupSet.hasModule(PlausibilityConfigurationModule.class))
							distAzCalc = rupSet.getModule(PlausibilityConfigurationModule.class).get().getDistAzCalc();
						if (distAzCalc == null)
							distAzCalc = new SectionDistanceAzimuthCalculator(getRupSet().getFaultSectionDataList());
						List<ClusterRupture> rups = new ArrayList<>(rupSet.getNumRuptures());
						for (int r=0; r<rupSet.getNumRuptures(); r++)
							rups.add(ClusterRupture.forOrderedSingleStrandRupture(rupSet.getFaultSectionDataForRupture(r), distAzCalc));
						this.clusterRuptures = rups;
					}
				}
			}
			return clusterRuptures;
		}
		
	}
	
	private static class Precomputed extends ClusterRuptures implements JSON_BackedModule {
		
		private List<ClusterRupture> clusterRuptures;
		
		private Precomputed() {
			super(null);
		}

		public Precomputed(FaultSystemRupSet rupSet, List<ClusterRupture> clusterRuptures) {
			super(rupSet);
			Preconditions.checkState(clusterRuptures.size() == rupSet.getNumRuptures());
			this.clusterRuptures = clusterRuptures;
		}

		@Override
		public String getName() {
			return "Precomputed Cluster Ruptures";
		}
		
		@Override
		public String getJSON_FileName() {
			return "cluster_ruptures.json";
		}

		@Override
		public Gson buildGson() {
			return ClusterRupture.buildGson(getRupSet().getFaultSectionDataList(), false);
		}

		@Override
		public void writeToJSON(JsonWriter out, Gson gson) throws IOException {
			// TODO make JSON more compact
			Type listType = new TypeToken<List<ClusterRupture>>(){}.getType();
			gson.toJson(clusterRuptures, listType, out);
		}

		@Override
		public void initFromJSON(JsonReader in, Gson gson) throws IOException {
			Type listType = new TypeToken<List<ClusterRupture>>(){}.getType();
			List<ClusterRupture> ruptures = gson.fromJson(in, listType);
			Preconditions.checkState(ruptures.size() == getRupSet().getNumRuptures());
			this.clusterRuptures = ruptures;
		}

		@Override
		public List<ClusterRupture> get() {
			return clusterRuptures;
		}
	}

}