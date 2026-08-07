package org.opensha.sha.earthquake.faultSysSolution.modules;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.statistics.distribution.ContinuousDistribution;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.util.json.ContinuousDistributionTypeAdapter;
import org.opensha.commons.util.modules.helpers.JSON_BackedModule;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Doubles;
import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

public class PosteriorSectionBValueDistributions implements JSON_BackedModule {
	
	/* required */
	private ContinuousDistribution priorDist;
	private List<ContinuousDistribution> sectPosteriors;
	
	/* optional */
	private List<double[]> sectPaleoSiteWeights;
	private List<ContinuousDistribution> paleoSitePosteriors;
	private List<EvenlyDiscretizedFunc> paleoSiteMisfits;
	
	@SuppressWarnings("unused") // deserialization
	private PosteriorSectionBValueDistributions() {};
	
	public PosteriorSectionBValueDistributions(ContinuousDistribution priorDist, List<ContinuousDistribution> sectPosteriors) {
		this(priorDist, sectPosteriors, null, null, null);
	}
	
	public PosteriorSectionBValueDistributions(ContinuousDistribution priorDist, List<ContinuousDistribution> sectPosteriors,
			List<double[]> sectPaleoSiteWeights, List<ContinuousDistribution> paleoSitePosteriors,
			List<EvenlyDiscretizedFunc> paleoSiteMisfits) {
		init(priorDist, sectPosteriors, sectPaleoSiteWeights, paleoSitePosteriors, paleoSiteMisfits);
	}

	private void init(ContinuousDistribution priorDist, List<ContinuousDistribution> sectPosteriors,
			List<double[]> sectPaleoSiteWeights, List<ContinuousDistribution> paleoSitePosteriors,
			List<EvenlyDiscretizedFunc> paleoSiteMisfits) {
		Preconditions.checkNotNull(priorDist, "Prior cannot be null");
		this.priorDist = priorDist;
		Preconditions.checkNotNull(sectPosteriors, "Sect posteriors cannot be null");
		this.sectPosteriors = Collections.unmodifiableList(sectPosteriors);
		if (paleoSitePosteriors != null && paleoSiteMisfits != null)
			Preconditions.checkState(paleoSiteMisfits.size() == paleoSitePosteriors.size());
		this.paleoSitePosteriors = paleoSitePosteriors == null ? null : Collections.unmodifiableList(paleoSitePosteriors);
		this.paleoSiteMisfits = paleoSiteMisfits == null ? null : Collections.unmodifiableList(paleoSiteMisfits);
		if (sectPaleoSiteWeights != null) {
			this.sectPaleoSiteWeights = Collections.unmodifiableList(sectPaleoSiteWeights);
			int expectedNum = paleoSitePosteriors == null ? -1 : paleoSitePosteriors.size(); 
			for (double[] weights : sectPaleoSiteWeights) {
				if (weights != null) {
					if (expectedNum < 0)
						expectedNum = weights.length;
					Preconditions.checkState(weights.length == expectedNum);
				}
			}
		}
	}

	public ContinuousDistribution getPriorDist() {
		return priorDist;
	}

	public List<ContinuousDistribution> getSectPosteriors() {
		// already unmodifiable
		return sectPosteriors;
	}
	
	public ContinuousDistribution getSectDistribution(int sectIndex) {
		ContinuousDistribution posterior = sectPosteriors.get(sectIndex);
		return posterior == null ? priorDist : posterior;
	}
	
	public List<double[]> getSectPaleoSiteWeights() {
		// already unmodifiable or null
		return sectPaleoSiteWeights;
	}

	public List<ContinuousDistribution> getPaleoSitePosteriors() {
		// already unmodifiable or null
		return paleoSitePosteriors;
	}

	public List<EvenlyDiscretizedFunc> getPaleoSiteMisfits() {
		// already unmodifiable or null
		return paleoSiteMisfits;
	}

	@Override
	public String getFileName() {
		return "posterior_b_values.json";
	}
	
	@Override
	public String getName() {
		return "Posterior b-values";
	}
	
	private static ContinuousDistributionTypeAdapter distAdapter = ContinuousDistributionTypeAdapter.get();
	private static EvenlyDiscretizedFunc.Adapter funcAdapter = new EvenlyDiscretizedFunc.Adapter();
	
	@Override
	public void writeToJSON(JsonWriter out, Gson gson) throws IOException {
		out.beginObject();
		
		out.name("prior");
		distAdapter.write(out, priorDist);
		
		out.name("sectPosteriors").beginArray();
		for (ContinuousDistribution dist : sectPosteriors) {
			if (dist == null)
				out.nullValue();
			else
				distAdapter.write(out, dist);
		}
		out.endArray();
		
		if (sectPaleoSiteWeights != null) {
			out.name("sectPaleoSiteWeights").beginArray();
			
			for (double[] weights : sectPaleoSiteWeights) {
				if (weights == null) {
					out.nullValue();
				} else {
					out.beginArray();
					for (double value : weights)
						out.value(value);
					out.endArray();
				}
			}
			
			out.endArray();
		}
		
		if (paleoSitePosteriors != null) {
			out.name("paleoSitePosteriors").beginArray();
			for (ContinuousDistribution dist : paleoSitePosteriors) {
				if (dist == null)
					out.nullValue();
				else
					distAdapter.write(out, dist);
			}
			out.endArray();
		}
		
		if (paleoSiteMisfits != null) {
			out.name("paleoSiteMisfits").beginArray();
			for (EvenlyDiscretizedFunc dist : paleoSiteMisfits) {
				if (dist == null)
					out.nullValue();
				else
					funcAdapter.write(out, dist);
			}
			out.endArray();
		}
		
		out.endObject();
	}
	
	@Override
	public void initFromJSON(JsonReader in, Gson gson) throws IOException {
		in.beginObject();
		
		ContinuousDistribution priorDist = null;
		List<ContinuousDistribution> sectPosteriors = null;
		
		List<double[]> sectPaleoSiteWeights = null;
		List<ContinuousDistribution> paleoSitePosteriors = null;
		List<EvenlyDiscretizedFunc> paleoSiteMisfits = null; 
		
		while (in.hasNext()) {
			String name = in.nextName();
			switch (name) {
			case "prior": {
				priorDist = distAdapter.read(in);
				break;
			}
			case "sectPosteriors": {
				sectPosteriors = new ArrayList<>();
				in.beginArray();
				while (in.hasNext()) {
					if (in.peek() == JsonToken.NULL)
						sectPosteriors.add(null);
					else
						sectPosteriors.add(distAdapter.read(in));
				}
				in.endArray();
				break;
			}
			case "sectPaleoSiteWeights": {
				List<Double> weights = new ArrayList<>();
				sectPaleoSiteWeights = new ArrayList<>();
				in.beginArray();
				while (in.hasNext()) {
					if (in.peek() == JsonToken.NULL) {
						sectPaleoSiteWeights.add(null);
					} else {
						weights.clear();
						in.beginArray();
						while (in.hasNext())
							weights.add(in.nextDouble());
						in.endArray();
						sectPaleoSiteWeights.add(Doubles.toArray(weights));
					}
				}
				in.endArray();
			}
			case "paleoSitePosteriors": {
				paleoSitePosteriors = new ArrayList<>();
				in.beginArray();
				while (in.hasNext()) {
					if (in.peek() == JsonToken.NULL)
						paleoSitePosteriors.add(null);
					else
						paleoSitePosteriors.add(distAdapter.read(in));
				}
				in.endArray();
				break;
			}
			case "paleoSiteMisfits": {
				paleoSiteMisfits = new ArrayList<>();
				in.beginArray();
				while (in.hasNext()) {
					if (in.peek() == JsonToken.NULL)
						paleoSiteMisfits.add(null);
					else
						paleoSiteMisfits.add(funcAdapter.read(in));
				}
				in.endArray();
				break;
			}
			default:
				System.err.println("Unexpected JSON value: "+name);
				in.skipValue();
			}
		}
		
		in.endObject();
		
		init(priorDist, sectPosteriors, sectPaleoSiteWeights, paleoSitePosteriors, paleoSiteMisfits);
	}

}
