package org.opensha.commons.data.uncertainty;

import java.io.IOException;

import com.google.common.base.Preconditions;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

/**
 * Base class for an Uncertainty, represented by a standard deviation.
 * 
 * @author kevin
 *
 */
@JsonAdapter(Uncertainty.UncertaintyAdapter.class)
public class Uncertainty {

	public final double stdDev;
	
	public Uncertainty(double stdDev) {
		this.stdDev = stdDev;
	}
	
	public Uncertainty scaled(double bestEstimate, double scalar) {
		return new Uncertainty(stdDev*scalar);
	}
	
	@Override
	public String toString() {
		return "stdDev="+(float)stdDev;
	}
	
	static class UncertaintyAdapter extends TypeAdapter<Uncertainty> {
	
		@Override
		public void write(JsonWriter out, Uncertainty value) throws IOException {
			out.beginObject();
			
			out.name("stdDev").value(value.stdDev);
			
			if (value instanceof BoundedUncertainty) {
				BoundedUncertainty bounded = (BoundedUncertainty)value;
				if (bounded.type != null)
					out.name("type").value(bounded.type.name());
				out.name("lowerBound").value(bounded.lowerBound);
				out.name("upperBound").value(bounded.upperBound);
			}
			
			out.endObject();
		}
	
		@Override
		public Uncertainty read(JsonReader in) throws IOException {
			Double stdDev = null;
			Double lowerBound = null;
			Double upperBound = null;
			UncertaintyBoundType type = null;
			
			in.beginObject();
			
			while (in.hasNext()) {
				switch (in.nextName()) {
				case "stdDev":
					stdDev = in.nextDouble();
					break;
				case "lowerBound":
					lowerBound = in.nextDouble();
					break;
				case "upperBound":
					upperBound = in.nextDouble();
					break;
				case "type":
					type = UncertaintyBoundType.valueOf(in.nextString());
					break;
	
				default:
					in.skipValue();
					break;
				}
			}
			
			in.endObject();
			
			Preconditions.checkNotNull(stdDev, "must supply standard deviation for uncertainty");
			if (lowerBound != null || upperBound != null || type != null) {
				Preconditions.checkState(lowerBound != null && upperBound != null,
						"Must supply both lower and upper bounds for bounded uncertainty");
				return new BoundedUncertainty(type, lowerBound, upperBound, stdDev);
			}
			
			return new Uncertainty(stdDev);
		}
		
	}
}