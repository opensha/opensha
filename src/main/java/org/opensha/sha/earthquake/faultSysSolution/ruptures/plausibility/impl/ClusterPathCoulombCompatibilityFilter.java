package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.JumpPlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.StiffnessAggregationMethod;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.StiffnessResult;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.StiffnessType;

import com.google.common.base.Preconditions;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import scratch.UCERF3.inversion.laughTest.PlausibilityResult;

/**
 * This filter tests the Coulomb compatibility of each possible path through the given rupture. It tries each
 * cluster as the nucleation point, and builds outwards to each rupture endpoint. Ruptures pass if at least
 * one nucleation point is viable.
 * 
 * @author kevin
 *
 */
public class ClusterPathCoulombCompatibilityFilter implements PlausibilityFilter {
	
	private SubSectStiffnessCalculator subSectCalc;
	private StiffnessAggregationMethod aggMethod;
	private float threshold;

	public ClusterPathCoulombCompatibilityFilter(SubSectStiffnessCalculator subSectCalc,
			StiffnessAggregationMethod aggMethod, float threshold) {
		this.subSectCalc = subSectCalc;
		this.aggMethod = aggMethod;
		this.threshold = threshold;
	}

	@Override
	public PlausibilityResult apply(ClusterRupture rupture, boolean verbose) {
		if (rupture.getTotalNumJumps()  == 0)
			return PlausibilityResult.PASS;
		for (FaultSubsectionCluster nucleationCluster : rupture.getClustersIterable()) {
			boolean valid = testNucleationPoint(rupture, nucleationCluster);
			if (verbose)
				System.out.println(getShortName()+": Nucleation point "+nucleationCluster+", result: "+valid);
			if (valid)
				// passes if *any* nucleation point works
				return PlausibilityResult.PASS;
		}
		return PlausibilityResult.FAIL_FUTURE_POSSIBLE;
	}

	@Override
	public PlausibilityResult testJump(ClusterRupture rupture, Jump newJump, boolean verbose) {
		return apply(rupture.take(newJump), verbose);
	}
	
	private boolean testNucleationPoint(ClusterRupture rup, FaultSubsectionCluster nucleationCluster) {
		FaultSubsectionCluster predecessor = rup.clusterPredecessorsMap.get(nucleationCluster);
		if (predecessor != null)
			if (!testStrand(rup, new HashSet<>(), predecessor))
				return false;
		
		for (FaultSubsectionCluster descendant : rup.clusterDescendantsMap.get(nucleationCluster)) {
			if (!testStrand(rup, new HashSet<>(), descendant))
				return false;
		}
		
		// passed all
		return true;
	}
	
	private boolean testStrand(ClusterRupture rup, HashSet<FaultSubsectionCluster> strandClusters,
			FaultSubsectionCluster addition) {
		if (!strandClusters.isEmpty()) {
			StiffnessResult[] stiffness = subSectCalc.calcAggClustersToClusterStiffness(
					strandClusters, addition);
			double val = subSectCalc.getValue(stiffness, StiffnessType.CFF, aggMethod);
			if ((float)val < threshold)
				return false;
		}
		
		// this additon passed, continue downstream
		HashSet<FaultSubsectionCluster> newStrandClusters = new HashSet<>(strandClusters);
		newStrandClusters.add(addition);
		
		// check predecessor of this strand
		FaultSubsectionCluster predecessor = rup.clusterPredecessorsMap.get(addition);
		if (predecessor != null && !strandClusters.contains(predecessor)) {
			// go down that path
			
			if (!testStrand(rup, newStrandClusters, predecessor))
				return false;
		}
		
		// check descendants of this strand
		for (FaultSubsectionCluster descendant : rup.clusterDescendantsMap.get(addition)) {
			if (strandClusters.contains(descendant))
				continue;
			// go down that path

			if (!testStrand(rup, newStrandClusters, descendant))
				return false;
		}
		
		// if we made it here, this either the end of the line or all downstream strand extensions pass
		return true;
	}

	@Override
	public String getShortName() {
		return "JumpClusterCoulomb";
	}

	@Override
	public String getName() {
		return "Jump Cluster Coulomb Compatbility";
	}

	@Override
	public TypeAdapter<PlausibilityFilter> getTypeAdapter() {
		return new Adapter();
	}
	
	public static class Adapter extends PlausibilityFilterTypeAdapter {

		private ClusterConnectionStrategy connStrategy;

		@Override
		public void init(ClusterConnectionStrategy connStrategy, SectionDistanceAzimuthCalculator distAzCalc) {
			this.connStrategy = connStrategy;
		}

		@Override
		public void write(JsonWriter out, PlausibilityFilter filter) throws IOException {
			Preconditions.checkState(filter instanceof ClusterPathCoulombCompatibilityFilter);
			ClusterPathCoulombCompatibilityFilter cFilter = (ClusterPathCoulombCompatibilityFilter)filter;
			out.beginObject();
			
			// serialize stiffness calculator
			out.name("stiffnessCalc").beginObject();
			out.name("gridSpacing").value(cFilter.subSectCalc.getGridSpacing());
			out.name("lameLambda").value(cFilter.subSectCalc.getLameLambda());
			out.name("lameMu").value(cFilter.subSectCalc.getLameMu());
			out.name("coeffOfFriction").value(cFilter.subSectCalc.getCoeffOfFriction());
			out.endObject();
			
			out.name("aggMethod").value(cFilter.aggMethod.name());
			out.name("threshold").value(cFilter.threshold);
			
			out.endObject();
		}

		@Override
		public PlausibilityFilter read(JsonReader in) throws IOException {
			Preconditions.checkNotNull(connStrategy, "Never initialized");
			SubSectStiffnessCalculator stiffnessCalc = null;
			StiffnessAggregationMethod aggMethod = null;
			Double threshold = null;
			
			in.beginObject();
			while (in.hasNext()) {
				switch (in.nextName()) {
				case "stiffnessCalc":
					in.beginObject();
					Double mu = null;
					Double lambda = null;
					Double coeffOfFriction = null;
					Double gridSpacing = null;
					while (in.hasNext()) {
						switch (in.nextName()) {
						case "lameMu":
							mu = in.nextDouble();
							break;
						case "lameLambda":
							lambda = in.nextDouble();
							break;
						case "coeffOfFriction":
							coeffOfFriction = in.nextDouble();
							break;
						case "gridSpacing":
							gridSpacing = in.nextDouble();
							break;

						default:
							break;
						}
					}
					in.endObject();
					stiffnessCalc = new SubSectStiffnessCalculator(connStrategy.getSubSections(),
							gridSpacing, lambda, mu, coeffOfFriction);
					break;
				case "aggMethod":
					aggMethod = StiffnessAggregationMethod.valueOf(in.nextString());
					break;
				case "threshold":
					threshold = in.nextDouble();
					break;

				default:
					break;
				}
			}
			in.endObject();
			return new ClusterPathCoulombCompatibilityFilter(stiffnessCalc, aggMethod, threshold.floatValue());
		}
		
	}

}
