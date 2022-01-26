package org.opensha.commons.data;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

import org.opensha.commons.data.function.IntegerPDF_FunctionSampler;
import org.opensha.commons.util.ExceptionUtils;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Doubles;
import com.google.common.primitives.Ints;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

/**
 * Interface for a random sampler of integer values. Implementations may have non-uniform sampling probabilities,
 * e.g. {@link IntegerPDF_FunctionSampler}, or only sample from a (possibly non-contiguous) subset of available values.
 * 
 * @author kevin
 *
 */
@JsonAdapter(IntegerSampler.Adapter.class)
public interface IntegerSampler {
	
	/**
	 * 
	 * @return a randomly sampled integer
	 */
	public default int getRandomInt() {
		return getRandomInt(Math.random());
	}
	
	/**
	 * This returns a randomly sampled integer using the given random number generator
	 * 
	 * @param r random number generator
	 * @return a randomly sampled integer
	 */
	public default int getRandomInt(Random r) {
		return getRandomInt(r.nextDouble());
	}
	
	/**
	 * This returns a randomly sampled integer using the supplied random number (supplied in cases where
	 * reproducibility is important)
	 * 
	 * @param randDouble - a value between 0 (inclusive) and 1 (exclusive)
	 * @return
	 */
	public int getRandomInt(double randDouble);
	
	/**
	 * Uniformly samples integers in the given contiguous range
	 * 
	 * @author kevin
	 */
	public static class ContiguousIntegerSampler implements IntegerSampler {
		
		private int start, num;
		
		/**
		 * Sampler of the given number of integers starting, starting at zero
		 * 
		 * @param num the number of intergers
		 */
		public ContiguousIntegerSampler(int num) {
			this(0, num);
		}

		/**
		 * @param start starting integer
		 * @param num the number of integers from which to sample
		 */
		public ContiguousIntegerSampler(int start, int num) {
			this.start = start;
			this.num = num;
		}

		@Override
		public int getRandomInt(Random r) {
			return start + r.nextInt(num);
		}

		@Override
		public int getRandomInt(double randDouble) {
			return start + (int)(randDouble * (double)num); // casting as int takes the floor
		}

	}
	
	/**
	 * Uniformly samples integers from the given set
	 * 
	 * @author kevin
	 *
	 */
	public static class ArbitraryIntegerSampler implements IntegerSampler {
		
		private int[] ints;

		public ArbitraryIntegerSampler(Collection<Integer> ints) {
			this(Ints.toArray(ints));
		}
		
		public ArbitraryIntegerSampler(int[] ints) {
			this.ints = ints;
		}

		@Override
		public int getRandomInt(Random r) {
			return ints[r.nextInt(ints.length)];
		}

		@Override
		public int getRandomInt(double randDouble) {
			return ints[(int)(randDouble * (double)ints.length)]; // casting as int takes the floor
		}

	}
	
	/**
	 * Uniformly samples integers from the given range, except for those specified.
	 * 
	 * @author kevin
	 *
	 */
	public static class ExclusionIntegerSampler implements IntegerSampler {
		
		private int start, num;
		private HashSet<Integer> except;
		
		private transient ArbitraryIntegerSampler sampler;

		public ExclusionIntegerSampler(int start, int num, Collection<Integer> except) {
			Preconditions.checkState(num > 0);
			Preconditions.checkState(except.size() > 0);
			Preconditions.checkState(num > except.size());
			this.start = start;
			this.num = num;
			if (except instanceof HashSet)
				this.except = (HashSet<Integer>)except;
			else
				this.except = new HashSet<>(except);
		}
		
		private void checkInitSampler() {
			if (sampler == null) {
				synchronized (this) {
					if (sampler == null) {
						int index = 0;
						int[] ints = new int[num-except.size()];
						for (int i=0; i<num; i++) {
							int val = start+i;
							if (!except.contains(val)) {
								Preconditions.checkState(index < ints.length,
										"At least one excluded value falls outside of the specified range");
								ints[index++] = val;
							}
						}
						Preconditions.checkState(index == ints.length,
								"At least one excluded value falls outside of the specified range");
						sampler = new ArbitraryIntegerSampler(ints);
					}
				}
			}
		}

		@Override
		public int getRandomInt(Random r) {
			checkInitSampler();
			return sampler.getRandomInt(r);
		}

		@Override
		public int getRandomInt(double randDouble) {
			checkInitSampler();
			return sampler.getRandomInt(randDouble);
		}
		
		public ExclusionIntegerSampler getCombinedWith(ExclusionIntegerSampler other) {
			Preconditions.checkState(other.start == start);
			Preconditions.checkState(other.num == num);
			HashSet<Integer> combExcepts = new HashSet<>(except);
			combExcepts.addAll(other.except);
			return new ExclusionIntegerSampler(start, num, combExcepts);
		}

	}
	
	public static class Adapter extends TypeAdapter<IntegerSampler> {
		
		private static Gson gson = new GsonBuilder().create();

		@Override
		public void write(JsonWriter out, IntegerSampler value) throws IOException {
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

		@Override
		public IntegerSampler read(JsonReader in) throws IOException {
			if (in.peek() == JsonToken.NULL)
				return null;
			
			in.beginObject();
			
			Class<? extends IntegerSampler> type = null;
			IntegerSampler ret = null;
			while (in.hasNext()) {
				String name = in.nextName();
				switch (name) {
				case "type":
					String typeStr = in.nextString();
					try {
						type = (Class<? extends IntegerSampler>) Class.forName(typeStr);
					} catch (Exception e) {
						throw ExceptionUtils.asRuntimeException(e);
					}
					break;
				case "data":
					Preconditions.checkNotNull(type, "type must be specified before adapter data");
					ret = gson.fromJson(in, type);
					break;
				case "values":
					System.err.println("WARNING: deserializing legacy IntegerPDF_Function sampler JSON");
					Preconditions.checkState(type == null || IntegerPDF_FunctionSampler.class.isAssignableFrom(type),
							"Have top level 'values' JSON but type isn't an IntegerPDF_FunctionSampler: %s", type);
					in.beginArray();
					List<Double> vals = new ArrayList<>();
					while (in.hasNext()) {
						in.beginArray();
						double x = in.nextDouble();
						double y = in.nextDouble();
						Preconditions.checkState((int)x == vals.size());
						vals.add(y);
						in.endArray();
					}
					in.endArray();
					ret =new IntegerPDF_FunctionSampler(Doubles.toArray(vals));
					break;

				default:
					in.skipValue();
					break;
				}
			}
			
			in.endObject();
			
			Preconditions.checkNotNull(ret, "Missing 'data' and/or 'type' field, can't deserialize");
			return ret;
		}
		
	}


}
