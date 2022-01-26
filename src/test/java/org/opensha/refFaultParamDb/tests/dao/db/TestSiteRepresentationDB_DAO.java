package org.opensha.refFaultParamDb.tests.dao.db;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opensha.refFaultParamDb.dao.db.DB_AccessAPI;
import org.opensha.refFaultParamDb.dao.db.SiteRepresentationDB_DAO;
import org.opensha.refFaultParamDb.tests.AllTests;
import org.opensha.refFaultParamDb.vo.SiteRepresentation;

/**
 * <p>Title: TestSiteRepresentationDB_DAO.java </p>
 * <p>Description: Tets the Site representation DB DAO to check that database
 * operations are being performed correctly./p>
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class TestSiteRepresentationDB_DAO {
	private DB_AccessAPI dbConnection;
	private SiteRepresentationDB_DAO siteRepresentationDB_DAO = null;


	public TestSiteRepresentationDB_DAO() {
		dbConnection = AllTests.dbConnection;
	}

	@Before
	public void setUp() throws Exception {
		siteRepresentationDB_DAO = new SiteRepresentationDB_DAO(dbConnection);
	}

	@After
	public void tearDown() throws Exception {
		siteRepresentationDB_DAO = null;
	}

	@Test
	public void testSiteRepresentationDB_DAO() {
		siteRepresentationDB_DAO = new SiteRepresentationDB_DAO(dbConnection);
		assertNotNull("siteRepresentationDB_DAO object should not be null",siteRepresentationDB_DAO);
	}

	/**
	 * Get all the representations with which a site can be associated
	 * @return
	 */
	@Test
	public void testGetAllSiteRepresentations() {
		int numSiteRepresentations  = siteRepresentationDB_DAO.getAllSiteRepresentations().size();
		assertEquals("There are 4 poosible representations for a site in the database", 4, numSiteRepresentations);
	}

	/**
	 * Get a representation based on site representation Id
	 * @param siteRepresentationId
	 * @return
	 */
	@Test
	public void testGetSiteRepresentationBasedOnId() {
		SiteRepresentation siteRepresentation = this.siteRepresentationDB_DAO.getSiteRepresentation(6);
		assertNull("There is no site representation with id =6",siteRepresentation);
		siteRepresentation = siteRepresentationDB_DAO.getSiteRepresentation(1);
		assertEquals("Entire Fault", siteRepresentation.getSiteRepresentationName());
		siteRepresentation = siteRepresentationDB_DAO.getSiteRepresentation(2);
		assertEquals("Most Significant Strand", siteRepresentation.getSiteRepresentationName());
		siteRepresentation = siteRepresentationDB_DAO.getSiteRepresentation(3);
		assertEquals("One of Several Strands", siteRepresentation.getSiteRepresentationName());
		siteRepresentation = siteRepresentationDB_DAO.getSiteRepresentation(4);
		assertEquals("Unknown", siteRepresentation.getSiteRepresentationName());
	}


	/**
	 * Get a  representation based on site representation name
	 *
	 * @param siteRepresentationName
	 * @return
	 */
	@Test
	public void testGetSiteRepresentationBasedOnName(String siteRepresentationName) {
		SiteRepresentation siteRepresentation = this.siteRepresentationDB_DAO.getSiteRepresentation("abc");
		assertNull("There is no site representation with name abc",siteRepresentation);
		siteRepresentation = siteRepresentationDB_DAO.getSiteRepresentation("Entire Fault");
		assertEquals(1, siteRepresentation.getSiteRepresentationId());
		siteRepresentation = siteRepresentationDB_DAO.getSiteRepresentation("Most Significant Strand");
		assertEquals(2, siteRepresentation.getSiteRepresentationId());
		siteRepresentation = siteRepresentationDB_DAO.getSiteRepresentation("One of Several Strands");
		assertEquals(3, siteRepresentation.getSiteRepresentationId());
		siteRepresentation = siteRepresentationDB_DAO.getSiteRepresentation("Unknown");
		assertEquals(4, siteRepresentation.getSiteRepresentationId());

	}




}
