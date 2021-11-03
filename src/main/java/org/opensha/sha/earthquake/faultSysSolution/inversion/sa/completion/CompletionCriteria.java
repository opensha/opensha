package org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion;

import java.io.IOException;
import java.util.List;

import org.apache.commons.lang3.time.StopWatch;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.ConstraintRange;

import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

@JsonAdapter(CompletionCriteria.Adapter.class)
public interface CompletionCriteria {
	
	/**
	 * Evaluates if the completion criteria is satisfied
	 * 
	 * @param watch stop watch for keeping track of time
	 * @param iter number of iterations completed
	 * @param energy energy of the best solution
	 * @param numPerturbsKept the total number of perturbations kept
	 * @param numNonZero the number of non-zero rates
	 * @param misfits misfit values for each row
	 * @param misfits_ineq misfit values for each inequalty row (note that values below zero should be ignored)
	 * @param constraintRanges constraint ranges, for interpreting misfits and or analyzing individual constraint energies
	 * @return true if completions criteria is satisfied
	 */
	public boolean isSatisfied(StopWatch watch, long iter, double[] energy, long numPerturbsKept, int numNonZero,
			double[] misfits, double[] misfits_ineq, List<ConstraintRange> constraintRanges);
	
	public static class Adapter extends TypeAdapter<CompletionCriteria> {
		
		Gson gson = new Gson();

		@Override
		public void write(JsonWriter out, CompletionCriteria value) throws IOException {
			out.beginObject();
			
			out.name("type").value(value.getClass().getName());
			out.name("criteria");
			gson.toJson(value, value.getClass(), out);
			
			out.endObject();
		}

		@SuppressWarnings("unchecked")
		@Override
		public CompletionCriteria read(JsonReader in) throws IOException {
			Class<? extends CompletionCriteria> type = null;
			
			in.beginObject();
			
			Preconditions.checkState(in.nextName().equals("type"), "JSON 'type' object must be first");
			try {
				type = (Class<? extends CompletionCriteria>) Class.forName(in.nextString());
			} catch (ClassNotFoundException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
			
			Preconditions.checkState(in.nextName().equals("criteria"), "JSON 'data' object must be second");
			CompletionCriteria criteria = gson.fromJson(in, type);
			
			in.endObject();
			return criteria;
		}
		
	}

}
