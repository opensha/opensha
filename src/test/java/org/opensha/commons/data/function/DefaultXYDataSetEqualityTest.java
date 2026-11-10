package org.opensha.commons.data.function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class DefaultXYDataSetEqualityTest {

	@Test
	public void testEqualValuesHaveEqualHashes() {
		DefaultXY_DataSet first = buildDataSet("name", "info", 1d, 2d, 3d, 4d);
		DefaultXY_DataSet second = buildDataSet("name", "info", 1d, 2d, 3d, 4d);

		assertEquals(first, second);
		assertEquals(second, first);
		assertEquals(first.hashCode(), second.hashCode());
		assertEquals(first, first);
	}

	@Test
	public void testMetadataAndPointDifferencesAreNotEqual() {
		DefaultXY_DataSet reference = buildDataSet("name", "info", 1d, 2d, 3d, 4d);

		assertNotEquals(reference, buildDataSet("other", "info", 1d, 2d, 3d, 4d));
		assertNotEquals(reference, buildDataSet("name", "other", 1d, 2d, 3d, 4d));
		assertNotEquals(reference, buildDataSet("name", "info", 1d, 2d));
		assertNotEquals(reference, buildDataSet("name", "info", 1d, 2d, 3d, 5d));
	}

	@Test
	public void testNullMetadata() {
		DefaultXY_DataSet first = buildDataSet(null, null, 1d, 2d);
		DefaultXY_DataSet second = buildDataSet(null, null, 1d, 2d);

		assertEquals(first, second);
		assertEquals(first.hashCode(), second.hashCode());
	}

	@Test
	public void testFloatPrecisionAndSignedZeroHashConsistency() {
		double base = 1d;
		double sameFloat = Math.nextUp(base);
		assertEquals((float)base, (float)sameFloat, 0f);

		DefaultXY_DataSet first = buildDataSet("name", "info", base, -0d);
		DefaultXY_DataSet second = buildDataSet("name", "info", sameFloat, 0d);

		assertEquals(first, second);
		assertEquals(first.hashCode(), second.hashCode());
	}

	@Test
	public void testListEqualityIsOrderedAndDuplicateSensitive() {
		DefaultXY_DataSet first = buildDataSet("first", "", 1d, 1d);
		DefaultXY_DataSet firstCopy = buildDataSet("first", "", 1d, 1d);
		DefaultXY_DataSet second = buildDataSet("second", "", 2d, 2d);

		XY_DataSetList ordered = new XY_DataSetList();
		ordered.add(first);
		ordered.add(second);

		XY_DataSetList equalOrdered = new XY_DataSetList();
		equalOrdered.add(firstCopy);
		equalOrdered.add(second);
		assertEquals(ordered, equalOrdered);
		assertEquals(ordered.hashCode(), equalOrdered.hashCode());

		XY_DataSetList reversed = new XY_DataSetList();
		reversed.add(second);
		reversed.add(firstCopy);
		assertNotEquals(ordered, reversed);

		XY_DataSetList duplicate = new XY_DataSetList();
		duplicate.add(first);
		duplicate.add(firstCopy);
		assertNotEquals(ordered, duplicate);
	}

	@Test
	public void testListEqualityIsSymmetricWithOrdinaryLists() {
		DefaultXY_DataSet dataset = buildDataSet("name", "info", 1d, 2d);
		XY_DataSetList dataSets = new XY_DataSetList();
		dataSets.add(dataset);
		List<XY_DataSet> ordinary = new ArrayList<>(Arrays.asList(dataset));

		assertTrue(dataSets.equals(ordinary));
		assertTrue(ordinary.equals(dataSets));
		assertFalse(dataSets.equals(null));
	}

	private static DefaultXY_DataSet buildDataSet(String name, String info, double... values) {
		DefaultXY_DataSet dataset = new DefaultXY_DataSet(values);
		dataset.setName(name);
		dataset.setInfo(info);
		return dataset;
	}
}
