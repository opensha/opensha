package org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion;

import java.util.List;

import org.apache.commons.lang3.time.StopWatch;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.ConstraintRange;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.InversionState;

public class VariableSubTimeCompletionCriteria implements VariableSubCompletionCriteria {
	
	private long min, max, minMaxDiff, cur;
	
	private CompletionCriteria globalCriteria;
	
	public VariableSubTimeCompletionCriteria(long maxMillis, long minMillis) {
		this.max = maxMillis;
		this.min = minMillis;
		this.minMaxDiff = maxMillis - minMillis;
		this.cur = 0l;
	}

	@Override
	public boolean isSatisfied(InversionState state) {
		return state.elapsedTimeMillis >= cur;
	}

	@Override
	public void setGlobalState(StopWatch watch, long iter, double[] energy,
			long numPerturbsKept) {
		if (globalCriteria instanceof TimeCompletionCriteria) {
			long time = watch.getTime();
			long total = ((TimeCompletionCriteria)globalCriteria).getMillis();
			long timeLeft = total - time;
			if (timeLeft < 0l)
				timeLeft = 0l;
			cur = min + (timeLeft * minMaxDiff) / total;
		} else if (globalCriteria instanceof IterationCompletionCriteria) {
			long total = ((IterationCompletionCriteria)globalCriteria).getMinIterations();
			long itersLeft = total - iter;
			if (itersLeft < 0l)
				itersLeft = 0l;
			cur = min + (itersLeft * minMaxDiff) / total;
		} else {
			throw new IllegalStateException("Unupported global criteria: "+globalCriteria);
		}
	}
	
	@Override
	public String toString() {
		return "VariableSubTimeCompletionCriteria("+(min+minMaxDiff)+" => "+min+", cur = "+cur+" = "+(float)(cur / 1000d)+" seconds)";
	}

	@Override
	public void setGlobalCriteria(CompletionCriteria criteria) {
		if (criteria instanceof ProgressTrackingCompletionCriteria)
			criteria = ((ProgressTrackingCompletionCriteria)criteria).getCriteria();
		this.globalCriteria = criteria;
	}
	
	public long getMin() {
		return min;
	}
	
	public long getMax() {
		return max;
	}
	
	public String getTimeStr() {
		return TimeCompletionCriteria.getTimeStr(max)+","+TimeCompletionCriteria.getTimeStr(min);
	}
	
	public static VariableSubTimeCompletionCriteria instance(String maxStr, String minStr) {
		return new VariableSubTimeCompletionCriteria(TimeCompletionCriteria.parseTimeString(maxStr),
				TimeCompletionCriteria.parseTimeString(minStr));
	}

}
