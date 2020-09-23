package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl;

import java.io.IOException;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
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
 * This filter tests the Coulomb compatibility of each added cluster as a rupture is built. It ensures
 * that, conditioned on unit slip of the existing rupture, each new cluster has a net Coulomb compatibility
 * at or above the given threshold. For example, to ensure that each added cluster is net postitive, set
 * the threshold to 0.
 * 
 * @author kevin
 *
 */
public class ClusterCoulombCompatibilityFilter extends JumpPlausibilityFilter {
	
	private SubSectStiffnessCalculator subSectCalc;
	private StiffnessAggregationMethod aggMethod;
	private float threshold;

	public ClusterCoulombCompatibilityFilter(SubSectStiffnessCalculator subSectCalc,
			StiffnessAggregationMethod aggMethod, float threshold) {
		this.subSectCalc = subSectCalc;
		this.aggMethod = aggMethod;
		this.threshold = threshold;
	}

	@Override
	public PlausibilityResult testJump(ClusterRupture rupture, Jump newJump, boolean verbose) {
		StiffnessResult[] stiffness = subSectCalc.calcAggRupToClusterStiffness(rupture, newJump.toCluster);
		double val = subSectCalc.getValue(stiffness, StiffnessType.CFF, aggMethod);
		PlausibilityResult result =
				(float)val >= threshold ? PlausibilityResult.PASS : PlausibilityResult.FAIL_HARD_STOP;
		if (verbose)
			System.out.println(getShortName()+": val="+val+"\tresult="+result.name());
		return result;
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
			Preconditions.checkState(filter instanceof ClusterCoulombCompatibilityFilter);
			ClusterCoulombCompatibilityFilter cFilter = (ClusterCoulombCompatibilityFilter)filter;
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
			return new ClusterCoulombCompatibilityFilter(stiffnessCalc, aggMethod, threshold.floatValue());
		}
		
	}

}
