package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.opensha.commons.util.IDPairing;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import scratch.UCERF3.inversion.laughTest.PlausibilityResult;

public class DirectPathPlausibilityFilter implements PlausibilityFilter {
	
	private transient HashSet<IDPairing> sectJumps;

	public DirectPathPlausibilityFilter(ClusterConnectionStrategy connStrat) {
		sectJumps = new HashSet<>();
		for (Jump jump : connStrat.getAllPossibleJumps())
			sectJumps.add(pairing(jump.fromSection, jump.toSection));
	}
	
	private IDPairing pairing(FaultSection from, FaultSection to) {
		int id1 = from.getSectionId();
		int id2 = to.getSectionId();
		if (id1 < id2)
			return new IDPairing(id1, id2);
		return new IDPairing(id2, id1);
	}

	@Override
	public String getShortName() {
		return "NoIndirect";
	}

	@Override
	public String getName() {
		return "No Indirect Connections";
	}

	@Override
	public PlausibilityResult apply(ClusterRupture rupture, boolean verbose) {
		PlausibilityResult result = evaluate(rupture, new ArrayList<>(), verbose);
		for (Jump splayJump : rupture.splays.keySet()) {
			ClusterRupture splay = rupture.splays.get(splayJump);
			result = result.logicalAnd(evaluate(splay, Lists.newArrayList(splayJump.fromSection), verbose));
		}
		return result;
	}
	
	private PlausibilityResult evaluate(ClusterRupture rupture, List<FaultSection> prevFroms, boolean verbose) {
		PlausibilityResult result = PlausibilityResult.PASS;
		for (Jump jump : rupture.internalJumps) {
			for (FaultSection prevFrom : prevFroms) {
				IDPairing pair = pairing(prevFrom, jump.toSection);
				if (sectJumps.contains(pair)) {
					result = PlausibilityResult.FAIL_HARD_STOP;
					if (verbose)
						System.out.println(getShortName()+": loopback detected. Could have jumped directly from "
								+prevFrom.getSectionId()+" to "+jump.toSection.getSectionId());
					else
						break;
				}
				if (prevFrom.getParentSectionId() == jump.toSection.getParentSectionId() && pair.getID2() == pair.getID1()+1) {
					result = PlausibilityResult.FAIL_HARD_STOP;
					if (verbose)
						System.out.println(getShortName()+": loopback detected. "+prevFrom.getSectionId()+" to "
								+jump.toSection.getSectionId()+" are neighbors on the same parent, but we took a circuitous route.");
					else
						break;
				}
			}
			prevFroms.add(jump.fromSection);
		}
		return result;
	}

	@Override
	public boolean isDirectional(boolean splayed) {
		// only directional if splayed
		return splayed;
	}

	@Override
	public TypeAdapter<PlausibilityFilter> getTypeAdapter() {
		return new Adapter();
	}
	
	public static class Adapter extends PlausibilityFilterTypeAdapter {

		private ClusterConnectionStrategy connStrategy;

		@Override
		public void init(ClusterConnectionStrategy connStrategy,
				SectionDistanceAzimuthCalculator distAzCalc, Gson gson) {
			this.connStrategy = connStrategy;
		}

		@Override
		public void write(JsonWriter out, PlausibilityFilter value) throws IOException {
			out.beginObject();
			out.endObject();
		}

		@Override
		public PlausibilityFilter read(JsonReader in) throws IOException {
			in.beginObject();
			in.endObject();
			return new DirectPathPlausibilityFilter(connStrategy);
		}
		
	}

}
