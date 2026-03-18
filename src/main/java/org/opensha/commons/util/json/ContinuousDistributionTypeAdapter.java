package org.opensha.commons.util.json;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.statistics.distribution.BetaDistribution;
import org.apache.commons.statistics.distribution.CauchyDistribution;
import org.apache.commons.statistics.distribution.ChiSquaredDistribution;
import org.apache.commons.statistics.distribution.ContinuousDistribution;
import org.apache.commons.statistics.distribution.CorrTruncatedNormalDistribution;
import org.apache.commons.statistics.distribution.ExponentialDistribution;
import org.apache.commons.statistics.distribution.FDistribution;
import org.apache.commons.statistics.distribution.FoldedNormalDistribution;
import org.apache.commons.statistics.distribution.GammaDistribution;
import org.apache.commons.statistics.distribution.GumbelDistribution;
import org.apache.commons.statistics.distribution.LaplaceDistribution;
import org.apache.commons.statistics.distribution.LevyDistribution;
import org.apache.commons.statistics.distribution.LogNormalDistribution;
import org.apache.commons.statistics.distribution.LogUniformDistribution;
import org.apache.commons.statistics.distribution.LogisticDistribution;
import org.apache.commons.statistics.distribution.NakagamiDistribution;
import org.apache.commons.statistics.distribution.NormalDistribution;
import org.apache.commons.statistics.distribution.ParetoDistribution;
import org.apache.commons.statistics.distribution.TDistribution;
import org.apache.commons.statistics.distribution.TrapezoidalDistribution;
import org.apache.commons.statistics.distribution.TriangularDistribution;
import org.apache.commons.statistics.distribution.TruncatedNormalDistribution;
import org.apache.commons.statistics.distribution.UniformContinuousDistribution;
import org.apache.commons.statistics.distribution.WeibullDistribution;

import com.google.common.base.Preconditions;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

public class ContinuousDistributionTypeAdapter extends TypeAdapter<ContinuousDistribution> {
	
	private static ContinuousDistributionTypeAdapter INSTANCE = null;
	
	public static ContinuousDistributionTypeAdapter get() {
		if (INSTANCE == null) {
			synchronized (ContinuousDistributionTypeAdapter.class) {
				if (INSTANCE == null)
					INSTANCE = new ContinuousDistributionTypeAdapter();
			}
		}
		return INSTANCE;
	}
	
	private ContinuousDistributionTypeAdapter() {}

	private static final String TYPE = "type";

	@Override
	public void write(JsonWriter out, ContinuousDistribution value) throws IOException {
		if (value == null) {
			out.nullValue();
			return;
		}

		out.beginObject();

		if (value instanceof TruncatedNormalDistribution) {
			throw new IllegalStateException("TruncatedNormalDistribution serialization is unsupported in this temporary "
					+ "compatibility layer; use CorrTruncatedNormalDistribution until Apache "
					+ "commons-statistics-distribution includes parent getters in a release.");
		} else if (value instanceof CorrTruncatedNormalDistribution) {
			CorrTruncatedNormalDistribution dist = (CorrTruncatedNormalDistribution)value;
			writeType(out, "TruncatedNormalDistribution");
			out.name("parentMean").value(dist.getParentMean());
			out.name("parentStandardDeviation").value(dist.getParentStandardDeviation());
			out.name("lower").value(dist.getSupportLowerBound());
			out.name("upper").value(dist.getSupportUpperBound());
		} else if (value instanceof BetaDistribution) {
			BetaDistribution dist = (BetaDistribution)value;
			writeType(out, "BetaDistribution");
			out.name("alpha").value(dist.getAlpha());
			out.name("beta").value(dist.getBeta());
		} else if (value instanceof CauchyDistribution) {
			CauchyDistribution dist = (CauchyDistribution)value;
			writeType(out, "CauchyDistribution");
			out.name("location").value(dist.getLocation());
			out.name("scale").value(dist.getScale());
		} else if (value instanceof ChiSquaredDistribution) {
			ChiSquaredDistribution dist = (ChiSquaredDistribution)value;
			writeType(out, "ChiSquaredDistribution");
			out.name("degreesOfFreedom").value(dist.getDegreesOfFreedom());
		} else if (value instanceof ExponentialDistribution) {
			ExponentialDistribution dist = (ExponentialDistribution)value;
			writeType(out, "ExponentialDistribution");
			out.name("mean").value(dist.getMean());
		} else if (value instanceof FDistribution) {
			FDistribution dist = (FDistribution)value;
			writeType(out, "FDistribution");
			out.name("numeratorDegreesOfFreedom").value(dist.getNumeratorDegreesOfFreedom());
			out.name("denominatorDegreesOfFreedom").value(dist.getDenominatorDegreesOfFreedom());
		} else if (value instanceof FoldedNormalDistribution) {
			FoldedNormalDistribution dist = (FoldedNormalDistribution)value;
			writeType(out, "FoldedNormalDistribution");
			out.name("mu").value(dist.getMu());
			out.name("sigma").value(dist.getSigma());
		} else if (value instanceof GammaDistribution) {
			GammaDistribution dist = (GammaDistribution)value;
			writeType(out, "GammaDistribution");
			out.name("shape").value(dist.getShape());
			out.name("scale").value(dist.getScale());
		} else if (value instanceof GumbelDistribution) {
			GumbelDistribution dist = (GumbelDistribution)value;
			writeType(out, "GumbelDistribution");
			out.name("location").value(dist.getLocation());
			out.name("scale").value(dist.getScale());
		} else if (value instanceof LaplaceDistribution) {
			LaplaceDistribution dist = (LaplaceDistribution)value;
			writeType(out, "LaplaceDistribution");
			out.name("location").value(dist.getLocation());
			out.name("scale").value(dist.getScale());
		} else if (value instanceof LevyDistribution) {
			LevyDistribution dist = (LevyDistribution)value;
			writeType(out, "LevyDistribution");
			out.name("location").value(dist.getLocation());
			out.name("scale").value(dist.getScale());
		} else if (value instanceof LogNormalDistribution) {
			LogNormalDistribution dist = (LogNormalDistribution)value;
			writeType(out, "LogNormalDistribution");
			out.name("mu").value(dist.getMu());
			out.name("sigma").value(dist.getSigma());
		} else if (value instanceof LogUniformDistribution) {
			LogUniformDistribution dist = (LogUniformDistribution)value;
			writeType(out, "LogUniformDistribution");
			out.name("lower").value(dist.getSupportLowerBound());
			out.name("upper").value(dist.getSupportUpperBound());
		} else if (value instanceof LogisticDistribution) {
			LogisticDistribution dist = (LogisticDistribution)value;
			writeType(out, "LogisticDistribution");
			out.name("location").value(dist.getLocation());
			out.name("scale").value(dist.getScale());
		} else if (value instanceof NakagamiDistribution) {
			NakagamiDistribution dist = (NakagamiDistribution)value;
			writeType(out, "NakagamiDistribution");
			out.name("shape").value(dist.getShape());
			out.name("scale").value(dist.getScale());
		} else if (value instanceof NormalDistribution) {
			NormalDistribution dist = (NormalDistribution)value;
			writeType(out, "NormalDistribution");
			out.name("mean").value(dist.getMean());
			out.name("standardDeviation").value(dist.getStandardDeviation());
		} else if (value instanceof ParetoDistribution) {
			ParetoDistribution dist = (ParetoDistribution)value;
			writeType(out, "ParetoDistribution");
			out.name("scale").value(dist.getScale());
			out.name("shape").value(dist.getShape());
		} else if (value instanceof TDistribution) {
			TDistribution dist = (TDistribution)value;
			writeType(out, "TDistribution");
			out.name("degreesOfFreedom").value(dist.getDegreesOfFreedom());
		} else if (value instanceof TrapezoidalDistribution) {
			TrapezoidalDistribution dist = (TrapezoidalDistribution)value;
			writeType(out, "TrapezoidalDistribution");
			out.name("a").value(dist.getSupportLowerBound());
			out.name("b").value(dist.getB());
			out.name("c").value(dist.getC());
			out.name("d").value(dist.getSupportUpperBound());
		} else if (value instanceof TriangularDistribution) {
			TriangularDistribution dist = (TriangularDistribution)value;
			writeType(out, "TriangularDistribution");
			out.name("a").value(dist.getSupportLowerBound());
			out.name("mode").value(dist.getMode());
			out.name("b").value(dist.getSupportUpperBound());
		} else if (value instanceof UniformContinuousDistribution) {
			UniformContinuousDistribution dist = (UniformContinuousDistribution)value;
			writeType(out, "UniformContinuousDistribution");
			out.name("lower").value(dist.getSupportLowerBound());
			out.name("upper").value(dist.getSupportUpperBound());
		} else if (value instanceof WeibullDistribution) {
			WeibullDistribution dist = (WeibullDistribution)value;
			writeType(out, "WeibullDistribution");
			out.name("shape").value(dist.getShape());
			out.name("scale").value(dist.getScale());
		} else {
			throw new IllegalStateException("Unsupported continuous distribution type: "+value.getClass().getName());
		}

		out.endObject();
	}

	@Override
	public ContinuousDistribution read(JsonReader in) throws IOException {
		if (in.peek() == JsonToken.NULL) {
			in.nextNull();
			return null;
		}

		String type = null;
		Map<String, Double> params = new HashMap<>();

		in.beginObject();
		while (in.hasNext()) {
			String name = in.nextName();
			if (TYPE.equals(name)) {
				type = in.nextString();
			} else if (in.peek() == JsonToken.NUMBER) {
				params.put(name, in.nextDouble());
			} else {
				in.skipValue();
			}
		}
		in.endObject();

		Preconditions.checkNotNull(type, "Continuous distribution type was not supplied");

		switch (type) {
		case "TruncatedNormalDistribution":
			return CorrTruncatedNormalDistribution.of(
					getRequiredDouble(params, "parentMean"),
					getRequiredDouble(params, "parentStandardDeviation"),
					getRequiredDouble(params, "lower"),
					getRequiredDouble(params, "upper"));
		case "BetaDistribution":
			return BetaDistribution.of(getRequiredDouble(params, "alpha"), getRequiredDouble(params, "beta"));
		case "CauchyDistribution":
			return CauchyDistribution.of(getRequiredDouble(params, "location"), getRequiredDouble(params, "scale"));
		case "ChiSquaredDistribution":
			return ChiSquaredDistribution.of(getRequiredDouble(params, "degreesOfFreedom"));
		case "ExponentialDistribution":
			return ExponentialDistribution.of(getRequiredDouble(params, "mean"));
		case "FDistribution":
			return FDistribution.of(getRequiredDouble(params, "numeratorDegreesOfFreedom"),
					getRequiredDouble(params, "denominatorDegreesOfFreedom"));
		case "FoldedNormalDistribution":
			return FoldedNormalDistribution.of(getRequiredDouble(params, "mu"), getRequiredDouble(params, "sigma"));
		case "GammaDistribution":
			return GammaDistribution.of(getRequiredDouble(params, "shape"), getRequiredDouble(params, "scale"));
		case "GumbelDistribution":
			return GumbelDistribution.of(getRequiredDouble(params, "location"), getRequiredDouble(params, "scale"));
		case "LaplaceDistribution":
			return LaplaceDistribution.of(getRequiredDouble(params, "location"), getRequiredDouble(params, "scale"));
		case "LevyDistribution":
			return LevyDistribution.of(getRequiredDouble(params, "location"), getRequiredDouble(params, "scale"));
		case "LogNormalDistribution":
			return LogNormalDistribution.of(getRequiredDouble(params, "mu"), getRequiredDouble(params, "sigma"));
		case "LogUniformDistribution":
			return LogUniformDistribution.of(getRequiredDouble(params, "lower"), getRequiredDouble(params, "upper"));
		case "LogisticDistribution":
			return LogisticDistribution.of(getRequiredDouble(params, "location"), getRequiredDouble(params, "scale"));
		case "NakagamiDistribution":
			return NakagamiDistribution.of(getRequiredDouble(params, "shape"), getRequiredDouble(params, "scale"));
		case "NormalDistribution":
			return NormalDistribution.of(getRequiredDouble(params, "mean"), getRequiredDouble(params, "standardDeviation"));
		case "ParetoDistribution":
			return ParetoDistribution.of(getRequiredDouble(params, "scale"), getRequiredDouble(params, "shape"));
		case "TDistribution":
			return TDistribution.of(getRequiredDouble(params, "degreesOfFreedom"));
		case "TrapezoidalDistribution":
			return TrapezoidalDistribution.of(getRequiredDouble(params, "a"), getRequiredDouble(params, "b"),
					getRequiredDouble(params, "c"), getRequiredDouble(params, "d"));
		case "TriangularDistribution":
			return TriangularDistribution.of(getRequiredDouble(params, "a"), getRequiredDouble(params, "mode"),
					getRequiredDouble(params, "b"));
		case "UniformContinuousDistribution":
			return UniformContinuousDistribution.of(getRequiredDouble(params, "lower"), getRequiredDouble(params, "upper"));
		case "WeibullDistribution":
			return WeibullDistribution.of(getRequiredDouble(params, "shape"), getRequiredDouble(params, "scale"));

		default:
			throw new IllegalStateException("Unsupported continuous distribution type: "+type);
		}
	}

	private static void writeType(JsonWriter out, String type) throws IOException {
		out.name(TYPE).value(type);
	}

	private static double getRequiredDouble(Map<String, Double> params, String name) {
		Double value = params.get(name);
		Preconditions.checkNotNull(value, "Required parameter '%s' was not supplied", name);
		return value;
	}
}
