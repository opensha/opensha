package org.opensha.commons.logicTree;

import java.io.IOException;

import org.opensha.commons.util.ExceptionUtils;

import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

@JsonAdapter(BranchWeightProvider.Adapter.class)
public interface BranchWeightProvider {
	
	/**
	 * Returns the weight for the given {@link LogicTreeBranch}.
	 * 
	 * @param branch
	 * @return weight (should be normalized externally)
	 */
	public double getWeight(LogicTreeBranch<?> branch);
	
	/**
	 * This weight provider returns the weight that was calculated when each branch was first instantiated, ignoring
	 * any later changes to weighting of involved nodes
	 * 
	 * @author kevin
	 *
	 */
	public static class OriginalWeights implements BranchWeightProvider {

		@Override
		public double getWeight(LogicTreeBranch<?> branch) {
			return branch.getOrigBranchWeight();
		}
		
	}
	
	/**
	 * This weight provider just passes through to return the curren calculated weight for each branch
	 * 
	 * @author kevin
	 *
	 */
	public static class CurrentWeights implements BranchWeightProvider {

		@Override
		public double getWeight(LogicTreeBranch<?> branch) {
			return branch.getBranchWeight();
		}
		
	}
	
	/**
	 * This weight provider assigns constant weights to each logic tree branch
	 * 
	 * @author kevin
	 *
	 */
	public static class ConstantWeights implements BranchWeightProvider {
		
		private double weightEach;

		public ConstantWeights() {
			this(1d);
		}
		
		public ConstantWeights(double weightEach) {
			this.weightEach = weightEach;
		}

		@Override
		public double getWeight(LogicTreeBranch<?> branch) {
			return weightEach;
		}
		
	}
	
	public static class Adapter extends TypeAdapter<BranchWeightProvider> {
		
		private Gson gson = new Gson();

		@Override
		public void write(JsonWriter out, BranchWeightProvider value) throws IOException {
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
		public BranchWeightProvider read(JsonReader in) throws IOException {
			if (in.peek() == JsonToken.NULL)
				return null;
			
			in.beginObject();
			
			Class<? extends BranchWeightProvider> type = null;
			BranchWeightProvider ret = null;
			while (in.hasNext()) {
				switch (in.nextName()) {
				case "type":
					String typeStr = in.nextString();
					try {
						type = (Class<? extends BranchWeightProvider>) Class.forName(typeStr);
					} catch (Exception e) {
						throw ExceptionUtils.asRuntimeException(e);
					}
					break;
				case "data":
					Preconditions.checkNotNull(type, "type must be specified before adapter data");
					ret = gson.fromJson(in, type);
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
