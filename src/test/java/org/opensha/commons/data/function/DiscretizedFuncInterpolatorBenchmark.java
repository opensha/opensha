package org.opensha.commons.data.function;

import java.text.DecimalFormat;
import java.util.Random;

import static org.opensha.commons.data.function.DiscretizedFuncInterpolator.*;

public class DiscretizedFuncInterpolatorBenchmark {
	
	private static volatile double BLACKHOLE;

	public static BenchmarkResult benchmark(
			final DiscretizedFuncInterpolator interp,
			final double[] xs,
			final int warmupIters,
			final int measureIters,
			final int trials
	) {
		// Warmup
		double sum = 0d;
		for (int t = 0; t < warmupIters; t++) {
			for (int i = 0; i < xs.length; i++)
				sum += interp.findY(xs[i]);
		}
		BLACKHOLE = sum;

		// Measurement trials
		long bestNanos = Long.MAX_VALUE;
		double bestSum = Double.NaN;

		for (int tr = 0; tr < trials; tr++) {
			sum = 0d;
			final long start = System.nanoTime();
			for (int t = 0; t < measureIters; t++) {
				for (int i = 0; i < xs.length; i++)
					sum += interp.findY(xs[i]);
			}
			final long elapsed = System.nanoTime() - start;
			BLACKHOLE = sum;

			if (elapsed < bestNanos) {
				bestNanos = elapsed;
				bestSum = sum;
			}
		}

		final long calls = (long)measureIters * (long)xs.length;
		return new BenchmarkResult(calls, bestNanos, bestSum);
	}

	public static class BenchmarkResult {
		public final long calls;
		public final long nanos;
		public final double sum;

		public final double secs;
		public final double rate;

		public BenchmarkResult(long calls, long nanos, double sum) {
			this.calls = calls;
			this.nanos = nanos;
			this.sum = sum;
			this.secs = nanos / 1e9;
			this.rate = calls / secs;
		}

		@Override
		public String toString() {
			DecimalFormat grouped = new DecimalFormat("0");
			grouped.setGroupingSize(3);
			grouped.setGroupingUsed(true);
			return "calls=" + grouped.format(calls)
					+ ", time=" + (float)secs + " s"
					+ ", rate=" + (float)rate + " /s"
					+ ", sum=" + sum;
		}
	}

	private static double[] buildXs(DiscretizedFunc f, int n, long seed) {
		double minX = f.getMinX();
		double maxX = f.getMaxX();
		double span = maxX - minX;

		Random r = new Random(seed);
		double[] xs = new double[n];
		for (int i = 0; i < n; i++) {
			// stay away from endpoints to avoid validate/snap dominating
			double u = 0.0001 + r.nextDouble() * 0.9998;
			xs[i] = minX + u * span;
		}
		return xs;
	}
	
	public static void main(String[] args) {
		DiscretizedFunc arbFunc = new ArbitrarilyDiscretizedFunc();
		int funSize = 10000;
		for (int i=0; i<funSize; i++)
			arbFunc.set(Math.random()*100d, Math.random());
		EvenlyDiscretizedFunc evenFunc = new EvenlyDiscretizedFunc(0d, funSize, 1d);
		for (int i=0; i<evenFunc.size(); i++)
			evenFunc.set(i, Math.random());

		int batchSize = 65536;
		long seed = 123456789l;
		int warmupIters = 30;
//		int measureIters = 60;
//		int trials = 7;
//		int measureIters = 100;
//		int trials = 20;
		int measureIters = 200;
		int trials = 50;
		
		boolean[] logXs = {false, true};
		boolean[] logYs = {false, true};
		
		for (boolean even : new boolean[] {false,true}) {
			if (even) {
				System.out.println("**************************************");
				System.out.println("EvenlyDiscretizedFunc benchmarks");
				System.out.println("**************************************");
			} else {
				System.out.println("**************************************");
				System.out.println("ArbitrarilyDiscretizedFunc benchmarks");
				System.out.println("**************************************");
			}
			
			DiscretizedFunc func = even ? evenFunc : arbFunc;
			double[] xs = buildXs(func, batchSize, seed);
			
			for (boolean logX : logXs) {
				if (even && logX)
					continue;
				for (boolean logY : logYs) {
					if (logX && logY)
						System.out.println("LogX&Y:");
					else if (logX)
						System.out.println("LogX:");
					else if (logY)
						System.out.println("LogY:");
					else
						System.out.println("Linear:");
					Basic basic = new Basic(func, logX, logY);
					DiscretizedFuncInterpolator precomputed = getOptimized(func, logX, logY);
					BenchmarkResult basicBenchmark = benchmark(basic, xs, warmupIters, measureIters, trials);
					System.out.println("\tBasic:\t"+basicBenchmark);
					BenchmarkResult optimizedBenchmark = benchmark(precomputed, xs, warmupIters, measureIters, trials);
					double speedup = optimizedBenchmark.rate/basicBenchmark.rate;
					System.out.println("\tOptimized:\t"+optimizedBenchmark+";\tspeedup="+(float)speedup+"x");
				}
			}
			
			System.out.println();
		}
	}

}
