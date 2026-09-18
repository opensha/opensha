package org.opensha.commons.data.sampling.generator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.StringTokenizer;

/** Lazy-loaded Joe-Kuo direction numbers used by both Sobol generators. */
final class SobolDirectionNumbers {

	static final int BITS = 52;
	static final int MAX_DIMENSIONS = 21_201;
	private static final String RESOURCE = "new-joe-kuo-6.21201";

	private SobolDirectionNumbers() {}

	static long[][] forDimensions(int dimensions) {
		if (dimensions < 1 || dimensions > MAX_DIMENSIONS)
			throw new IllegalArgumentException("Sobol dimensions must be in [1," + MAX_DIMENSIONS
					+ "], have " + dimensions);
		long[][] all = Holder.DIRECTIONS;
		long[][] selected = new long[dimensions][];
		System.arraycopy(all, 0, selected, 0, dimensions);
		return selected;
	}

	private static final class Holder {
		private static final long[][] DIRECTIONS = load();
	}

	private static long[][] load() {
		long[][] directions = new long[MAX_DIMENSIONS][BITS];
		for (int bit=0; bit<BITS; bit++)
			directions[0][bit] = 1L << (BITS-bit-1);

		InputStream stream = SobolDirectionNumbers.class.getResourceAsStream(RESOURCE);
		if (stream == null)
			throw new IllegalStateException("Missing Sobol direction-number resource: " + RESOURCE);
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.US_ASCII))) {
			String line = reader.readLine(); // column headings
			if (line == null)
				throw new IOException("Empty direction-number resource");
			int expectedDimension = 2;
			while ((line = reader.readLine()) != null) {
				if (line.isBlank())
					continue;
				StringTokenizer tokens = new StringTokenizer(line);
				int dimension = Integer.parseInt(tokens.nextToken());
				if (dimension != expectedDimension)
					throw new IOException("Expected direction parameters for dimension " + expectedDimension
							+ " but found " + dimension);
				int degree = Integer.parseInt(tokens.nextToken());
				int coefficient = Integer.parseInt(tokens.nextToken());
				long[] dimensionDirections = directions[dimension-1];
				for (int bit=1; bit<=degree; bit++) {
					if (!tokens.hasMoreTokens())
						throw new IOException("Missing initial direction number " + bit + " for dimension " + dimension);
					long initial = Long.parseLong(tokens.nextToken());
					dimensionDirections[bit-1] = initial << (BITS-bit);
				}
				for (int bit=degree+1; bit<=BITS; bit++) {
					long value = dimensionDirections[bit-degree-1]
							^ (dimensionDirections[bit-degree-1] >>> degree);
					for (int k=1; k<degree; k++)
						if (((coefficient >>> (degree-k-1)) & 1) != 0)
							value ^= dimensionDirections[bit-k-1];
					dimensionDirections[bit-1] = value;
				}
				if (tokens.hasMoreTokens())
					throw new IOException("Unexpected direction-number data for dimension " + dimension);
				expectedDimension++;
			}
			if (expectedDimension != MAX_DIMENSIONS+1)
				throw new IOException("Direction-number resource ended after dimension " + (expectedDimension-1));
			return directions;
		} catch (IOException | RuntimeException e) {
			throw new ExceptionInInitializerError(e);
		}
	}
}
