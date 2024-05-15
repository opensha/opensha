package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityResult;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

/**
 * This enforces a minimum aspect ratio for each involved fault section (total rupture length on that section divided by
 * its down dip width). A threshold value of 1 will require all ruptures to be at least as long as they are wide
 * (regardless of subsection count) on each involved fault section.
 * 
 * A special case can be enabled (allowIfNoDirect) which allows a deficient cluster (i.e., one that has a small aspect
 * ratio) only if the clusters immediately before and after the deficient cluster can only connect
 * through that cluster. This allows deficient connections only in cases where they are required in order
 * to maintain connectivity.
 * 
 * If that special case is enabled, allowChained will allow multiple deficient clusters in a row, only
 * if each additional deficient cluster is the only way to connect.
 * 
 * @author kevin
 *
 */
public class AspectRatioFilter extends AbstractClusterSizeFilter {
	
	private float minAspectRatio;
	
	/**
	 * @param minAspectRatio minimum aspect ratio per cluster
	 * @param allowIfNoDirect if true, enable special case to allow violations if it's the only way to
	 * connect two clusters 
	 * @param allowChained if true and allowIfNoDirect, allow multiple chained violations
	 * @param connStrategy connection strategy, needed if allowIfNoDirect in order to test for direct
	 * connections
	 */
	public AspectRatioFilter(float minAspectRatio, boolean allowIfNoDirect, boolean allowChained,
			ClusterConnectionStrategy connStrategy) {
		super(allowIfNoDirect, allowChained, connStrategy);
		this.minAspectRatio = minAspectRatio;
	}

	@Override
	String getQuantityStr(FaultSubsectionCluster cluster) {
		return "aspect="+(float)clusterAspectRatio(cluster);
	}

	@Override
	boolean isClusterSufficient(FaultSubsectionCluster cluster) {
		return clusterAspectRatio(cluster) >= minAspectRatio;
	}
	
	public static float clusterAspectRatio(FaultSubsectionCluster cluster) {
		return clusterAspectRatio(cluster.subSects);
	}
	
	public static float clusterAspectRatio(Collection<? extends FaultSection> subSects) {
		boolean debug = false;
//		if (subSects.size() == 3) {
//			Iterator<? extends FaultSection> iterator = subSects.iterator();
//			debug = iterator.next().getSectionId() == 20 && iterator.next().getSectionId() == 21 && iterator.next().getSectionId() == 22;
//		}
		double sumArea = 0d;
		double sumLength = 0d;
		for (FaultSection sect : subSects) {
			sumArea += sect.getArea(false)*1e-6; // m^2 -> km^2
			double len = sect.getTraceLength();
			if (sect.getLowerFaultTrace() != null)
				len = 0.5*len + 0.5*sect.getLowerFaultTrace().getTraceLength();
			if (debug) {
				double myArea = sect.getArea(false)*1e-6;
				double ddw = myArea/len;
				System.out.println("Sect "+sect.getSectionId()+": len="+(float)len+", area="+(float)myArea+", impliedDDW="+(float)ddw);
				System.out.println("\tdip="+sect.getAveDip());
				System.out.println("\treported ddw="+(float)sect.getOrigDownDipWidth());
			}
			sumLength += len; // already km
		}
		double ddw = sumArea / sumLength;
		if (debug) {
			System.out.println("total ddw = "+(float)sumArea+" / "+(float)sumLength+" = "+(float)ddw);
			System.out.println("aspect = "+(float)sumLength+" / "+(float)ddw+" = "+(float)(sumLength/ddw));
		}
		return (float)(sumLength / ddw);
	}

	@Override
	public String getShortName() {
		return "Aspect";
	}

	@Override
	public String getName() {
		return "Minimum Aspect Ratio";
	}

	@Override
	public TypeAdapter<PlausibilityFilter> getTypeAdapter() {
		return new Adapter();
	}

	@Override
	public boolean isDirectional(boolean splayed) {
		// only directional if splayed
		return splayed;
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
			AspectRatioFilter filter = (AspectRatioFilter)value;
			out.beginObject();
			out.name("minAspectRatio").value(filter.minAspectRatio);
			out.name("allowIfNoDirect").value(filter.allowIfNoDirect);
			out.name("allowChained").value(filter.allowChained);
			out.endObject();
		}

		@Override
		public PlausibilityFilter read(JsonReader in) throws IOException {
			in.beginObject();
			Integer minPerParent = null;
			Boolean allowIfNoDirect = null;
			boolean allowChained = false;
			while (in.hasNext()) {
				switch (in.nextName()) {
				case "minPerParent":
					minPerParent = in.nextInt();
					break;
				case "allowIfNoDirect":
					allowIfNoDirect = in.nextBoolean();
					break;
				case "allowChained":
					allowChained = in.nextBoolean();
					break;

				default:
					break;
				}
			}
			
			in.endObject();
			return new AspectRatioFilter(minPerParent, allowIfNoDirect, allowChained, connStrategy);
		}
		
	}

}
