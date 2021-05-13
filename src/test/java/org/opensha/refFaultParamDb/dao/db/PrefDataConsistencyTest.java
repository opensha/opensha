package org.opensha.refFaultParamDb.dao.db;


import static org.junit.Assert.*;

import java.util.ArrayList;

import org.junit.BeforeClass;
import org.junit.Test;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.refFaultParamDb.vo.FaultSectionSummary;

public class PrefDataConsistencyTest {

	private static DB_AccessAPI db;
	
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		db = DB_ConnectionPool.getLatestReadOnlyConn();
	}
	
	@Test
	public void testAllFaultSectionsPresent() {
		PrefFaultSectionDataDB_DAO pref2db = new PrefFaultSectionDataDB_DAO(db);
		FaultSectionVer2_DB_DAO fs2db = new FaultSectionVer2_DB_DAO(db);
		
		System.out.print("Getting FS summaries...");
		ArrayList<FaultSectionSummary> sums = fs2db.getAllFaultSectionsSummary();
		System.out.println("DONE ("+sums.size()+")");
		System.out.print("Getting Pref Data...");
		ArrayList<FaultSectionPrefData> prefs = pref2db.getAllFaultSectionPrefData();
		System.out.println("DONE ("+prefs.size()+")");
		
		for (FaultSectionSummary sum : sums) {
			boolean found = false;
			for (FaultSectionPrefData pref : prefs) {
				if (sum.getSectionId() == pref.getSectionId()
						&& sum.getSectionName().equals(pref.getSectionName())) {
					found = true;
					break;
				}
			}
			assertTrue("No match in pref data table for: "+sum.getAsString(), found);
		}
	}

}
