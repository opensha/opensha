package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.opensha.commons.data.Named;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;

import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

/**
 * Inteface for a rupture probability calculator
 * 
 * @author kevin
 *
 */
public interface RuptureProbabilityCalc extends Named {
	
	/**
	 * This computes the probability of this rupture occurring as defined, conditioned on
	 * it beginning at the first cluster in this rupture
	 * 
	 * @param rupture
	 * @param verbose
	 * @return conditional probability of this rupture
	 */
	public double calcRuptureProb(ClusterRupture rupture, boolean verbose);
	
	public default void init(ClusterConnectionStrategy connStrat, SectionDistanceAzimuthCalculator distAzCalc) {
		// do nothing
	}
	
	public boolean isDirectional(boolean splayed);
	
	public static interface BinaryRuptureProbabilityCalc extends RuptureProbabilityCalc {
		
		public boolean isRupAllowed(ClusterRupture fullRupture, boolean verbose);

		@Override
		default double calcRuptureProb(ClusterRupture rupture, boolean verbose) {
			if (isRupAllowed(rupture, verbose))
				return 1d;
			return 0d;
		}
	}
	
	public class LogicalAnd implements BinaryRuptureProbabilityCalc {
		
		@JsonAdapter(GenericArrayBinaryRupProbCalcAdapter.class)
		private BinaryRuptureProbabilityCalc[] calcs;

		public LogicalAnd(BinaryRuptureProbabilityCalc... calcs) {
			Preconditions.checkState(calcs.length > 1);
			this.calcs = calcs;
		}

		@Override
		public String getName() {
			return "Logical AND of "+calcs.length+" models";
		}

		@Override
		public boolean isDirectional(boolean splayed) {
			for (RuptureProbabilityCalc calc : calcs)
				if (calc.isDirectional(splayed))
					return true;
			return false;
		}

		@Override
		public boolean isRupAllowed(ClusterRupture fullRupture, boolean verbose) {
			for (BinaryRuptureProbabilityCalc calc : calcs)
				if (!calc.isRupAllowed(fullRupture, verbose))
					return false;
			return true;
		}
	}
	
	public class MultiProduct implements RuptureProbabilityCalc {
		
		@JsonAdapter(GenericArrayRupProbCalcAdapter.class)
		private RuptureProbabilityCalc[] calcs;

		public MultiProduct(RuptureProbabilityCalc... calcs) {
			Preconditions.checkState(calcs.length > 1);
			this.calcs = calcs;
		}

		@Override
		public String getName() {
			return "Product of "+calcs.length+" models";
		}

		@Override
		public double calcRuptureProb(ClusterRupture rupture, boolean verbose) {
			double product = 1;
			for (RuptureProbabilityCalc calc : calcs)
				product *= calc.calcRuptureProb(rupture, verbose);
			return product;
		}

		@Override
		public boolean isDirectional(boolean splayed) {
			for (RuptureProbabilityCalc calc : calcs)
				if (calc.isDirectional(splayed))
					return true;
			return false;
		}
		
	}
	
	public static class GenericArrayRupProbCalcAdapter extends TypeAdapter<RuptureProbabilityCalc[]> {
		
		private GenericRupProbCalcAdapter adapter = new GenericRupProbCalcAdapter();

		@Override
		public void write(JsonWriter out, RuptureProbabilityCalc[] calcs) throws IOException {
			out.beginArray();
			
			for (RuptureProbabilityCalc calc : calcs)
				adapter.write(out, calc);
			
			out.endArray();
		}

		@Override
		public RuptureProbabilityCalc[] read(JsonReader in) throws IOException {
			in.beginArray();
			
			List<RuptureProbabilityCalc> calcs = new ArrayList<>();
			
			while (in.hasNext())
				calcs.add(adapter.read(in));
			
			in.endArray();
			
			return calcs.toArray(new RuptureProbabilityCalc[0]);
		}
		
	}
	
	public static class GenericArrayBinaryRupProbCalcAdapter extends TypeAdapter<BinaryRuptureProbabilityCalc[]> {
		
		private GenericRupProbCalcAdapter adapter = new GenericRupProbCalcAdapter();

		@Override
		public void write(JsonWriter out, BinaryRuptureProbabilityCalc[] calcs) throws IOException {
			out.beginArray();
			
			for (RuptureProbabilityCalc calc : calcs)
				adapter.write(out, calc);
			
			out.endArray();
		}

		@Override
		public BinaryRuptureProbabilityCalc[] read(JsonReader in) throws IOException {
			in.beginArray();
			
			List<BinaryRuptureProbabilityCalc> calcs = new ArrayList<>();
			
			while (in.hasNext()) {
				RuptureProbabilityCalc calc = adapter.read(in);
				Preconditions.checkState(calc instanceof BinaryRuptureProbabilityCalc);
				calcs.add((BinaryRuptureProbabilityCalc)calc);
			}
			
			in.endArray();
			
			return calcs.toArray(new BinaryRuptureProbabilityCalc[0]);
		}
		
	}
	
	public static class GenericRupProbCalcAdapter extends TypeAdapter<RuptureProbabilityCalc> {
		
		Gson gson = new Gson();

		@Override
		public void write(JsonWriter out, RuptureProbabilityCalc value) throws IOException {
			if (value == null) {
				out.nullValue();
				return;
			}
			out.beginObject();

			out.name("type").value(value.getClass().getName());
			out.name("data");
			gson.toJson(value, value.getClass(), out);

			out.endObject();
		}

		@SuppressWarnings("unchecked")
		@Override
		public RuptureProbabilityCalc read(JsonReader in) throws IOException {
			if (in.peek() == JsonToken.NULL)
				return null;
			
			Class<? extends RuptureProbabilityCalc> type = null;

			in.beginObject();

			Preconditions.checkState(in.nextName().equals("type"), "JSON 'type' object must be first");
			try {
				type = (Class<? extends RuptureProbabilityCalc>) Class.forName(in.nextString());
			} catch (ClassNotFoundException | ClassCastException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}

			Preconditions.checkState(in.nextName().equals("data"), "JSON 'data' object must be second");
			RuptureProbabilityCalc model = gson.fromJson(in, type);

			in.endObject();
			return model;
		}
		
	}
}