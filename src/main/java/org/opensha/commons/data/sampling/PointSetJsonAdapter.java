package org.opensha.commons.data.sampling;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

/** JSON adapter for point sets using OpenSHA's built-in dimension definitions. */
public final class PointSetJsonAdapter extends TypeAdapter<PointSet> {

	public static final PointSetJsonAdapter INSTANCE = new PointSetJsonAdapter();

	private PointSetJsonAdapter() {}

	@Override
	public void write(JsonWriter out, PointSet pointSet) throws IOException {
		out.beginObject();
		out.name("dimensions").beginArray();
		for (int d=0; d<pointSet.dimensions(); d++) {
			SamplingDimension dimension = pointSet.getDimension(d);
			out.beginObject();
			if (dimension instanceof InactiveSamplingDimension) {
				out.name("type").value("inactive");
			} else if (dimension instanceof ContinuousSamplingDimension) {
				out.name("type").value("continuous");
			} else if (dimension instanceof CategoricalSamplingDimension categorical) {
				out.name("type").value("categorical");
				out.name("probabilities").beginArray();
				for (double probability : categorical.getProbabilities())
					out.value(probability);
				out.endArray();
			} else {
				throw new IllegalArgumentException("Unsupported sampling dimension type: " + dimension.getClass().getName());
			}
			out.endObject();
		}
		out.endArray();
		out.name("points").beginArray();
		for (int p=0; p<pointSet.size(); p++) {
			out.beginArray();
			for (int d=0; d<pointSet.dimensions(); d++)
				out.value(pointSet.get(p, d));
			out.endArray();
		}
		out.endArray();
		out.endObject();
	}

	@Override
	public PointSet read(JsonReader in) throws IOException {
		List<SamplingDimension> dimensions = null;
		List<double[]> points = null;
		in.beginObject();
		while (in.hasNext()) {
			switch (in.nextName()) {
			case "dimensions":
				dimensions = readDimensions(in);
				break;
			case "points":
				points = readPoints(in);
				break;
			default:
				in.skipValue();
			}
		}
		in.endObject();
		if (dimensions == null || dimensions.isEmpty())
			throw new IOException("Point-set JSON is missing dimensions");
		if (points == null || points.isEmpty())
			throw new IOException("Point-set JSON is missing points");
		double[][] array = points.toArray(double[][]::new);
		PointSet coordinates = new ArrayPointSet(array);
		if (coordinates.dimensions() != dimensions.size())
			throw new IOException("Point-set coordinate and dimension counts differ");
		return new DimensionedPointSet(coordinates, dimensions);
	}

	private static List<SamplingDimension> readDimensions(JsonReader in) throws IOException {
		List<SamplingDimension> dimensions = new ArrayList<>();
		in.beginArray();
		while (in.hasNext()) {
			String type = null;
			List<Double> probabilities = null;
			in.beginObject();
			while (in.hasNext()) {
				switch (in.nextName()) {
				case "type": type = in.nextString(); break;
				case "probabilities":
					probabilities = new ArrayList<>();
					in.beginArray();
					while (in.hasNext()) probabilities.add(in.nextDouble());
					in.endArray();
					break;
				default: in.skipValue();
				}
			}
			in.endObject();
			if (type == null)
				throw new IOException("Sampling dimension is missing its type");
			switch (type) {
			case "continuous": dimensions.add(ContinuousSamplingDimension.INSTANCE); break;
			case "inactive": dimensions.add(InactiveSamplingDimension.INSTANCE); break;
			case "categorical":
				if (probabilities == null)
					throw new IOException("Categorical dimension is missing probabilities");
				double[] weights = new double[probabilities.size()];
				for (int i=0; i<weights.length; i++) weights[i] = probabilities.get(i);
				dimensions.add(CategoricalSamplingDimension.forWeights(weights));
				break;
			default: throw new IOException("Unsupported sampling dimension type: " + type);
			}
		}
		in.endArray();
		return dimensions;
	}

	private static List<double[]> readPoints(JsonReader in) throws IOException {
		List<double[]> points = new ArrayList<>();
		in.beginArray();
		while (in.hasNext()) {
			List<Double> values = new ArrayList<>();
			in.beginArray();
			while (in.hasNext()) values.add(in.nextDouble());
			in.endArray();
			double[] point = new double[values.size()];
			for (int i=0; i<point.length; i++) point[i] = values.get(i);
			points.add(point);
		}
		in.endArray();
		return points;
	}
}
