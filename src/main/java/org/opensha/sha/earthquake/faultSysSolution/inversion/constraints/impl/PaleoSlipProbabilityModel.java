package org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl;

import java.io.IOException;

import org.opensha.commons.util.ExceptionUtils;

import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

/**
 * Probability of observing slip in a paleoseismic trench. This was used in UCERF3 for paleo-slip inversion constraints
 * 
 * @author kevin
 *
 */
@JsonAdapter(PaleoSlipProbabilityModel.Adapter.class)
public interface PaleoSlipProbabilityModel {
	
	/**
	 * Returns the probability of observing slip this amount of slip in a trench at the surface
	 * 
	 * @param slip slip in meters
	 * @return
	 */
	public double getProbabilityOfObservedSlip(double slip);
	
	public static class Adapter extends TypeAdapter<PaleoSlipProbabilityModel> {
		
		Gson gson = new Gson();

		@Override
		public void write(JsonWriter out, PaleoSlipProbabilityModel value) throws IOException {
			out.beginObject();
			
			out.name("type").value(value.getClass().getName());
			out.name("data");
			gson.toJson(value, value.getClass(), out);
			
			out.endObject();
		}

		@SuppressWarnings("unchecked")
		@Override
		public PaleoSlipProbabilityModel read(JsonReader in) throws IOException {
			Class<? extends PaleoSlipProbabilityModel> type = null;
			
			in.beginObject();
			
			Preconditions.checkState(in.nextName().equals("type"), "JSON 'type' object must be first");
			try {
				type = (Class<? extends PaleoSlipProbabilityModel>) Class.forName(in.nextString());
			} catch (ClassNotFoundException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
			
			Preconditions.checkState(in.nextName().equals("data"), "JSON 'data' object must be second");
			PaleoSlipProbabilityModel model = gson.fromJson(in, type);
			
			in.endObject();
			return model;
		}
		
	}

}
