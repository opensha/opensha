package scratch.UCERF3.simulatedAnnealing.completion;

import java.util.Iterator;

import org.apache.commons.lang3.time.StopWatch;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.util.DataUtils;

import com.google.common.base.Splitter;

public class EnergyChangeCompletionCriteria implements CompletionCriteria {
	
	private double energyDelta, energyPercentDelta, lookBackMins;
	
	private double lookBackStart = -1;
	private ArbitrarilyDiscretizedFunc energyVsTime = new ArbitrarilyDiscretizedFunc();

	public EnergyChangeCompletionCriteria(double energyDelta,
			double energyPercentDelta, double lookBackMins) {
		super();
		if (energyDelta <= 0)
			energyDelta = Double.POSITIVE_INFINITY;
		if (energyPercentDelta <= 0)
			energyPercentDelta = Double.POSITIVE_INFINITY;
		this.energyDelta = energyDelta;
		this.energyPercentDelta = energyPercentDelta;
		this.lookBackMins = lookBackMins;
	}

	@Override
	public boolean isSatisfied(StopWatch watch, long iter, double[] energy,
			long numPerturbsKept) {
		double mins = watch.getTime()/1000d/60d;
		double e = energy[0];
		energyVsTime.set(mins, e);
		if (lookBackStart < 0)
			lookBackStart = energyVsTime.getMinX() + lookBackMins;
		if (mins > lookBackStart) {
			double prevE = energyVsTime.getInterpolatedY(mins - lookBackMins);
			double delta = prevE - e;
			if (delta > energyDelta)
				return false;
			double pDiff = DataUtils.getPercentDiff(e, prevE);
			return pDiff <= energyPercentDelta;
		}
		return false;
	}
	
	public static EnergyChangeCompletionCriteria fromCommandLineArgument(String arg) {
		Iterator<String> it = Splitter.on(',').split(arg).iterator();
		long millis = TimeCompletionCriteria.parseTimeString(it.next());
		double lookBackMins = millis / 1000d / 60d;
		double energyPercentDelta = Double.parseDouble(it.next());
		double energyDelta = Double.parseDouble(it.next());
		return new EnergyChangeCompletionCriteria(energyDelta, energyPercentDelta, lookBackMins);
	}
	
	public String toCommandLineArgument() {
		String time = TimeCompletionCriteria.getTimeStr((long)(lookBackMins*60*1000 + 0.5));
		return time+","+(float)energyPercentDelta+","+(float)energyDelta;
	}

	@Override
	public String toString() {
		return "EnergyChangeCompletionCriteria [energyDelta=" + energyDelta
				+ ", energyPercentDelta=" + energyPercentDelta
				+ ", lookBackMins=" + lookBackMins + "]";
	}

}
