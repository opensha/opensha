package org.opensha.commons.data.sampling.scoring;

/**
 * Defines how values in one sampling dimension are compared when measuring discrepancy from an ideal distribution.
 * A kernel is simply a similarity function: {@code k(x,y)} is large when two values are similar in the sense relevant
 * to that dimension. Continuous dimensions use overlap of CDF-indicator functions, while categorical dimensions use
 * same-category equality.
 * <p>
 * The point-set score compares three average similarities:
 * <ol>
 * <li>sample points with other sample points, using {@link #value(double, double)};</li>
 * <li>each sample point with the ideal target, using {@link #targetMean(double)};</li>
 * <li>the ideal target with itself, using {@link #targetGrandMean()}.</li>
 * </ol>
 * Combining those as {@code sample-sample - 2*sample-target + target-target} gives a squared distance between the
 * sampled and ideal distributions. For a multidimensional projection, the scorer multiplies the one-dimensional
 * kernel quantities, so all projected dimensions must be similar at once for a pair of points to be considered
 * similar in that projection.
 * <p>
 * {@link #targetDiagonalMean()} is not part of the discrepancy itself. It supplies the expected self-similarity of one
 * random target point and is used with {@link #targetGrandMean()} to derive the finite-sample IID-random baseline.
 * Implementations must be positive-semidefinite and return finite values for coordinates in {@code [0,1)}.
 */
public interface DiscrepancyKernel {

	/**
	 * Measures the similarity of two observed coordinate values. The scorer averages this over every pair of sample
	 * points to form the sample-to-sample term. A kernel defines mathematical similarity, not necessarily ordinary
	 * numeric distance; for example, categorical values are either equal or unequal.
	 *
	 * @param value1 first observed coordinate
	 * @param value2 second observed coordinate
	 * @return kernel similarity {@code k(value1, value2)}
	 */
	double value(double value1, double value2);

	/**
	 * Measures how similar one observed value is, on average, to the entire ideal target distribution. This is the
	 * analytic equivalent of drawing many ideal values {@code Y}, evaluating {@code k(value,Y)}, and averaging them. The
	 * scorer averages this quantity over observed points to form the sample-to-target term.
	 *
	 * @param value observed coordinate
	 * @return {@code E[k(value,Y)]} for target-distributed {@code Y}
	 */
	double targetMean(double value);

	/**
	 * Gives the average similarity between two independent values drawn from the ideal target. This constant forms the
	 * target-to-target term: the reference similarity that a perfect infinite sample is trying to reproduce.
	 *
	 * @return {@code E[k(X,Y)]} for independent target-distributed {@code X} and {@code Y}
	 */
	double targetGrandMean();

	/**
	 * Gives the average similarity of an ideal target value with itself. Finite samples include {@code N} diagonal
	 * sample-pair terms {@code k(x_i,x_i)}, which are systematically larger than similarities between independent
	 * points. The difference between this value and {@link #targetGrandMean()}, divided by {@code N}, is therefore the
	 * expected raw discrepancy of an IID-random sample.
	 *
	 * @return {@code E[k(X,X)]} for target-distributed {@code X}
	 */
	double targetDiagonalMean();
}
