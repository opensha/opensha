package org.opensha.refFaultParamDb.tests.dao.db;

import java.util.ArrayList;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opensha.refFaultParamDb.dao.db.DB_AccessAPI;
import org.opensha.refFaultParamDb.dao.db.QFault2002B_DB_DAO;
import org.opensha.refFaultParamDb.dao.exception.QueryException;
import org.opensha.refFaultParamDb.tests.AllTests;
import org.opensha.refFaultParamDb.vo.QFault2002B;

/**
 *
 * <p>Title: Test_QFault2002B_DB_DAO.java </p>
 * <p>Description: Test the QFault2002B DB DAO class</p>
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */
public class Test_QFault2002B_DB_DAO {
	private DB_AccessAPI dbConnection;
	private QFault2002B_DB_DAO qFaultDB_DAO = null;

	public Test_QFault2002B_DB_DAO() {
		dbConnection = AllTests.dbConnection;
	}

	@Before
	public void setUp() throws Exception {
		qFaultDB_DAO = new QFault2002B_DB_DAO(dbConnection);
	}

	@After
	public void tearDown() throws Exception {
		qFaultDB_DAO = null;
	}

	@Test
	public void testQFault2002B_DB_DAO() {
		qFaultDB_DAO = new QFault2002B_DB_DAO(dbConnection);
		assertNotNull("QFault2002B_DB_DAO object should not be null",qFaultDB_DAO);
	}

	@Test
	public void testGetAllFaultSections() throws QueryException {
		ArrayList actualReturn = qFaultDB_DAO.getAllFaultSections();
		assertEquals("Should have 57 fault sections in the table", 57, actualReturn.size());
	}

	@Test
	public void testGetFaultSection() throws QueryException {
		QFault2002B actualReturn = qFaultDB_DAO.getFaultSection("69a");
		assertNotNull("should not be null as fault section exists with id = 69a",actualReturn);
	}

}
