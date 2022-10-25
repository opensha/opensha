package org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.opensha.commons.util.ExceptionUtils;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import scratch.UCERF3.inversion.UCERF3InversionInputGenerator;

/**
 * Abstract base class for a the probability of seeing a rupture in a paleoseismic trench 
 * 
 * @author Kevin
 *
 */
@JsonAdapter(PaleoProbabilityModel.Adapter.class)
public abstract class PaleoProbabilityModel {
	
	private transient Map<Integer, Double> traceLengthCache = Maps.newConcurrentMap();
	
	public double getProbPaleoVisible(FaultSystemRupSet rupSet, int rupIndex, int sectIndex) {
		return getProbPaleoVisible(rupSet.getMagForRup(rupIndex),
				rupSet.getFaultSectionDataForRupture(rupIndex), sectIndex);
	}
	
	public abstract double getProbPaleoVisible(double mag, List<? extends FaultSection> rupSections, int sectIndex);
	
	public abstract double getProbPaleoVisible(double mag, double distAlongRup);
	
	protected double getDistAlongRup(List<? extends FaultSection> rupSections, int sectIndex) {
		if (traceLengthCache == null) {
			synchronized (this) {
				if (traceLengthCache == null)
					traceLengthCache = Maps.newConcurrentMap();
			}
		}
		return UCERF3InversionInputGenerator.getDistanceAlongRupture(
				rupSections, sectIndex, traceLengthCache);
	}
	
	public static class Adapter extends TypeAdapter<PaleoProbabilityModel> {
		
		Gson gson = new Gson();

		@Override
		public void write(JsonWriter out, PaleoProbabilityModel value) throws IOException {
			out.beginObject();
			
			out.name("type").value(value.getClass().getName());
			out.name("data");
			gson.toJson(value, value.getClass(), out);
			
			out.endObject();
		}

		@SuppressWarnings("unchecked")
		@Override
		public PaleoProbabilityModel read(JsonReader in) throws IOException {
			Class<? extends PaleoProbabilityModel> type = null;
			
			in.beginObject();
			
			Preconditions.checkState(in.nextName().equals("type"), "JSON 'type' object must be first");
			try {
				type = (Class<? extends PaleoProbabilityModel>) Class.forName(in.nextString());
			} catch (ClassNotFoundException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
			
			Preconditions.checkState(in.nextName().equals("data"), "JSON 'data' object must be second");
			PaleoProbabilityModel model = gson.fromJson(in, type);
			
			in.endObject();
			return model;
		}
		
	}

}