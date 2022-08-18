package org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Collection;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.opensha.commons.data.CSVFile;

@RunWith(Parameterized.class)
public class NSHM23_ScalingRelationshipsTest {
	
	private static CSVFile<String> dataCSV;
	private NSHM23_ScalingRelationships scale;
	
	public NSHM23_ScalingRelationshipsTest(NSHM23_ScalingRelationships scale) {
		this.scale = scale;
	}
	
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		dataCSV = CSVFile.readStream(NSHM23_ScalingRelationshipsTest.class.getResourceAsStream(
				"/org/opensha/sha/earthquake/rupForecastImpl/nshm23/logicTree/scaling_test_file.csv"), false);
	}

	@Test
	public void testMags() {
		System.out.println("Testing "+scale.getName()+" magnitudes");
		doTest(false);
	}
	
	@Test
	public void testSlips() {
		System.out.println("Testing "+scale.getName()+" slips");
		doTest(true);
	}
	
	private void doTest(boolean isSlip) {
		int numTested = 0;
		try {
			for (int row=1; row<dataCSV.getNumRows(); row++) {
				if (isRowForRelationship(dataCSV, row)) {
					double area = dataCSV.getDouble(row, 1) * 1e6; // km^2 -> m^2
					double length = dataCSV.getDouble(row, 2) * 1e3; // km -> m
					double width = dataCSV.getDouble(row, 3) * 1e3; // km -> m
					if (area == 0d || length == 0d)
						continue;
					if (isSlip) {
						double slip = dataCSV.getDouble(row, 4); // m
						double calcSlip = scale.getAveSlip(area, length, width, width, Double.NaN);
						assertEquals(scale.name()+": bad slip for area="+area+", len="+length+", width="+width, slip, calcSlip, 1e-4);
					} else {
						String magStr = dataCSV.get(row, 5);
						if (magStr.isBlank() || magStr.equalsIgnoreCase("nan"))
							continue;
						double mag = dataCSV.getDouble(row, 5);
						double calcMag = scale.getMag(area, length, width, width, Double.NaN);
						assertEquals(scale.name()+": bad magnitude for area="+area+", len="+length+", width="+width, mag, calcMag, 1e-4);
					}
					numTested++;
				}
			}
		} catch (AssertionError e) {
			System.out.flush();
			System.err.println("FAILED "+(isSlip ? "slip" : "magnitude")+" after "+numTested+" tests for "+scale.name());
			System.err.println(e.getMessage());
			System.err.flush();
			throw e;
		}
		System.out.println("Completed "+numTested+" "+(isSlip ? "slip" : "magnitude")+" tests for "+scale.name());
//		assertTrue("No tests found for "+scale.getName(), numTested > 0);
	}
	
	private boolean isRowForRelationship(CSVFile<String> csv, int row) {
		String name = csv.get(row, 6);
		return name.equalsIgnoreCase(scale.name()) || name.equalsIgnoreCase(scale.getShortName());
	}
	
	@Parameters
	public static Collection<NSHM23_ScalingRelationships[]> data() {
		ArrayList<NSHM23_ScalingRelationships[]> ret = new ArrayList<NSHM23_ScalingRelationships[]>();
		for (NSHM23_ScalingRelationships scale : NSHM23_ScalingRelationships.values()) {
			if (scale.getNodeWeight(null) > 0d) {
				NSHM23_ScalingRelationships[] array = { scale };
				ret.add(array);
			}
		}
		System.out.println("Scaling relationships to test: "+ret.size());
		return ret;
	}
}
