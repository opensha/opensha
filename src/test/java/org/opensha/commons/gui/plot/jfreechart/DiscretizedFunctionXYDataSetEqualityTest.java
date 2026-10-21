package org.opensha.commons.gui.plot.jfreechart;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.util.HashSet;
import java.util.Set;

import org.jfree.data.xy.AbstractXYDataset;
import org.junit.Test;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.XY_DataSetList;

public class DiscretizedFunctionXYDataSetEqualityTest {

	@Test
	public void testWrapperUsesIdentityEquality() {
		DiscretizedFunctionXYDataSet first = buildWrapper();
		DiscretizedFunctionXYDataSet second = buildWrapper();

		assertEquals(first, first);
		assertNotEquals(first, second);
		assertNotEquals(second, first);

		Set<DiscretizedFunctionXYDataSet> wrappers = new HashSet<>();
		wrappers.add(first);
		wrappers.add(second);
		assertEquals(2, wrappers.size());
	}

	@Test
	public void testIdentityEqualityIsSymmetricWithOtherJFreeDatasets() {
		DiscretizedFunctionXYDataSet wrapper = buildWrapper();
		AbstractXYDataset other = new EmptyXYDataset();

		assertNotEquals(wrapper, other);
		assertNotEquals(other, wrapper);
	}

	private static class EmptyXYDataset extends AbstractXYDataset {

		private static final long serialVersionUID = 1L;

		@Override
		public int getSeriesCount() {
			return 0;
		}

		@Override
		public Comparable<?> getSeriesKey(int series) {
			throw new IndexOutOfBoundsException();
		}

		@Override
		public int getItemCount(int series) {
			throw new IndexOutOfBoundsException();
		}

		@Override
		public Number getX(int series, int item) {
			throw new IndexOutOfBoundsException();
		}

		@Override
		public Number getY(int series, int item) {
			throw new IndexOutOfBoundsException();
		}
	}

	private static DiscretizedFunctionXYDataSet buildWrapper() {
		DefaultXY_DataSet function = new DefaultXY_DataSet(1d, 2d, 3d, 4d);
		function.setName("same-name");
		function.setInfo("same-info");
		XY_DataSetList functions = new XY_DataSetList();
		functions.add(function);

		DiscretizedFunctionXYDataSet wrapper = new DiscretizedFunctionXYDataSet();
		wrapper.setFunctions(functions);
		return wrapper;
	}
}
