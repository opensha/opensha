package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import org.opensha.commons.util.IDPairing;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RuptureTreeNavigator;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import scratch.UCERF3.inversion.laughTest.PlausibilityResult;

public class DirectPathPlausibilityFilter implements PlausibilityFilter {
	
	private transient HashMap<IDPairing, Double> sectJumps;
	private boolean onlyLargerDist;

	public DirectPathPlausibilityFilter(ClusterConnectionStrategy connStrat, boolean onlyLargerDist) {
		this.onlyLargerDist = onlyLargerDist;
		sectJumps = new HashMap<>();
		for (Jump jump : connStrat.getAllPossibleJumps())
			sectJumps.put(pairing(jump.fromSection, jump.toSection), jump.distance);
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
	
	private double calcJumpDistBetween(RuptureTreeNavigator nav, FaultSection from, FaultSection to) {
		double dist = 0d;
		FaultSection sect = to;
		while (true) {
			FaultSection predecessor = nav.getPredecessor(sect);
			Preconditions.checkNotNull(predecessor, "Didn't find path between %s and %s, stalled out at %s",
					from.getSectionId(), to.getSectionId(), sect.getSectionId());
			if (predecessor.getParentSectionId() != sect.getParentSectionId())
				dist += nav.getJump(predecessor, sect).distance;
			if (predecessor.equals(from))
				break;
			sect = predecessor;
		}
		return dist;
	}
	
	private PlausibilityResult evaluate(ClusterRupture rupture, List<FaultSection> prevFroms, boolean verbose) {
		PlausibilityResult result = PlausibilityResult.PASS;
		for (Jump jump : rupture.internalJumps) {
			for (FaultSection prevFrom : prevFroms) {
				FaultSection[] dests;
				if (jump.toCluster.subSects.get(0).equals(jump.toSection))
					// simple jump
					dests = new FaultSection[] { jump.toSection };
				else
					// T jump, include both toSection and the start of toCluster
					dests = new FaultSection[] { jump.toSection, jump.toCluster.subSects.get(0) };
				for (FaultSection dest : dests) {
					IDPairing pair = pairing(prevFrom, dest);
					if (sectJumps.containsKey(pair)) {
						// we have taken an indirect path
						if (onlyLargerDist) {
							// check and make sure that the indirect path jumped more
							double directDist = sectJumps.get(pair);
							if ((float)directDist > 0f) {
								double myDist = calcJumpDistBetween(rupture.getTreeNavigator(), prevFrom, dest);
								if ((float)myDist < (float)directDist) {
									if (verbose)
										System.out.println(getShortName()+": loopback detected but it used shorter jumps ("
												+(float)myDist+ " km) than the direct path ("+(float)directDist+" km) between"
												+prevFrom.getSectionId()+" and "+jump.toSection.getSectionId()+", so it's"
												+ " allowed");
									continue;
								}
							}
						}
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
			out.name("onlyLargerDist").value(((DirectPathPlausibilityFilter)value).onlyLargerDist);
			out.endObject();
		}

		@Override
		public PlausibilityFilter read(JsonReader in) throws IOException {
			in.beginObject();
			boolean onlyLargerDist = false;
			if (in.peek() == JsonToken.NAME) {
				Preconditions.checkState(in.nextName().equals("onlyLargerDist"));
				onlyLargerDist = in.nextBoolean();
			}
			in.endObject();
			return new DirectPathPlausibilityFilter(connStrategy, onlyLargerDist);
		}
		
	}

}
