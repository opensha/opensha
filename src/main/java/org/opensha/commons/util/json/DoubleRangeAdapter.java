package org.opensha.commons.util.json;

import java.io.IOException;

import com.google.common.base.Preconditions;
import com.google.common.collect.BoundType;
import com.google.common.collect.Range;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

public class DoubleRangeAdapter extends TypeAdapter<Range<Double>> {

	@Override
	public void write(JsonWriter out, Range<Double> value) throws IOException {
		if (value == null) {
			out.nullValue();
			return;
		}
		out.beginObject();
		if (value.hasLowerBound()) {
			double lower = value.lowerEndpoint();
			if (Double.isFinite(lower))
				out.name("lower").value(lower).name("lowerType").value(value.lowerBoundType().name());
		}
		if (value.hasUpperBound()) {
			double upper = value.upperEndpoint();
			if (Double.isFinite(upper))
				out.name("upper").value(upper).name("upperType").value(value.upperBoundType().name());
		}
		out.endObject();
	}

	@Override
	public Range<Double> read(JsonReader in) throws IOException {
		if (in.peek() == JsonToken.NULL) {
			in.nextNull();
			return null;
		}
		Double lower = null;
		BoundType lowerType = null;
		Double upper = null;
		BoundType upperType = null;
		in.beginObject();
		while (in.hasNext()) {
			String name = in.nextName();
			switch (name) {
			case "lower":
				if (in.peek() == JsonToken.NULL) {
					in.nextNull();
				} else {
					lower = in.nextDouble();
				}
				break;
			case "lowerType":
				if (in.peek() == JsonToken.NULL) {
					in.nextNull();
				} else {
					lowerType = BoundType.valueOf(in.nextString());
				}
				break;
			case "upper":
				if (in.peek() == JsonToken.NULL) {
					in.nextNull();
				} else {
					upper = in.nextDouble();
				}
				break;
			case "upperType":
				if (in.peek() == JsonToken.NULL) {
					in.nextNull();
				} else {
					upperType = BoundType.valueOf(in.nextString());
				}
				break;

			default:
				throw new IllegalStateException("unexpected json name: "+name);
			}
		}
		in.endObject();
		if (lower != null) {
			Preconditions.checkState(Double.isFinite(lower), "Lower endpoint must be finite if supplied");
			Preconditions.checkNotNull(lowerType, "lower bound supplied without type");
		}
		if (upper != null) {
			Preconditions.checkState(Double.isFinite(upper), "Upper endpoint must be finite if supplied");
			Preconditions.checkNotNull(upperType, "upper bound supplied without type");
		}
		if (lower == null && upper == null)
			return Range.all();
		if (lower == null)
			return Range.upTo(upper, upperType);
		if (upper == null)
			return Range.downTo(lower, lowerType);
		return Range.range(lower, lowerType, upper, upperType);
	}

}
