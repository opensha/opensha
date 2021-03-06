package org.opensha.refFaultParamDb.tests.dao.db;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opensha.commons.data.estimate.FractileListEstimate;
import org.opensha.commons.data.function.ArbDiscrEmpiricalDistFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.refFaultParamDb.dao.db.DB_AccessAPI;
import org.opensha.refFaultParamDb.dao.db.EstimateInstancesDB_DAO;
import org.opensha.refFaultParamDb.dao.exception.InsertException;
import org.opensha.refFaultParamDb.dao.exception.QueryException;
import org.opensha.refFaultParamDb.dao.exception.UpdateException;
import org.opensha.refFaultParamDb.tests.AllTests;
import org.opensha.refFaultParamDb.vo.EstimateInstances;
/**
 *
 * <p>Title: TestFractileListEstimateInstancesDB_DAO.java </p>
 * <p>Description: Test the Estimate Instances DB DAO</p>
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */
public class TestFractileListEstimateInstancesDB_DAO {
	private DB_AccessAPI dbConnection;
	private EstimateInstancesDB_DAO estimateInstancesDB_DAO = null;
	static int primaryKey1, primaryKey2;

	public TestFractileListEstimateInstancesDB_DAO() {
		dbConnection = AllTests.dbConnection;
	}

	@Before
	public void setUp() throws Exception {
		estimateInstancesDB_DAO = new EstimateInstancesDB_DAO(dbConnection);
	}

	@After
	public void tearDown() throws Exception {
		estimateInstancesDB_DAO = null;
	}

	@Test
	public void testFractileListEstimateInstancesDB_DAO() {
		estimateInstancesDB_DAO = new EstimateInstancesDB_DAO(dbConnection);
		assertNotNull("estimateInstancesDB_DAO object should not be null",estimateInstancesDB_DAO);
	}

	@Test
	public void testAddIntegerEstimateInstance() throws InsertException {
		ArbDiscrEmpiricalDistFunc func1 = new ArbDiscrEmpiricalDistFunc();

		func1.set(2.0,0.9);
		func1.set(1.0,0.1);
		ArbDiscrEmpiricalDistFunc func2 = new ArbDiscrEmpiricalDistFunc();
		func2.set(1.0,0.4);
		func2.set(4.0,0.7);
		func2.set(8.0,0.8);

		FractileListEstimate estimate1 = new FractileListEstimate(func1);
		FractileListEstimate estimate2 = new FractileListEstimate(func2);
		EstimateInstances estimateInstance = new EstimateInstances();
		estimateInstance.setEstimate(estimate1);
		estimateInstance.setUnits("meters");
		primaryKey1 = estimateInstancesDB_DAO.addEstimateInstance(estimateInstance);
		estimateInstance.setEstimate(estimate2);
		primaryKey2 = estimateInstancesDB_DAO.addEstimateInstance(estimateInstance);
		assertTrue(primaryKey1!=primaryKey2);
		assertEquals("there should be 2 estimate instances", 2, estimateInstancesDB_DAO.getAllEstimateInstances().size());
	}

	@Test
	public void testGetIntegerEstimateInstance() throws QueryException {
		EstimateInstances actualReturn = estimateInstancesDB_DAO.getEstimateInstance(4546);
		assertEquals("No estimate exists with id 4546", null, actualReturn);

		actualReturn = estimateInstancesDB_DAO.getEstimateInstance(primaryKey1);
		assertNotNull("should not be null as estimate exists with id ="+primaryKey1,actualReturn);
		FractileListEstimate estimate  = (FractileListEstimate)actualReturn.getEstimate();
		DiscretizedFunc func1 =  estimate.getValues();
		double tolerance = 0.00001;
		assertEquals(1, func1.getX(0),tolerance);
		assertEquals(0.1, func1.getY(0),tolerance);
		assertEquals(2, func1.getX(1), tolerance);
		assertEquals(0.9, func1.getY(1), tolerance);
		assertEquals("meters", actualReturn.getUnits());

		actualReturn = estimateInstancesDB_DAO.getEstimateInstance(primaryKey2);
		assertNotNull("should not be null as estimate exists with id ="+primaryKey2,actualReturn);
		estimate  = (FractileListEstimate)actualReturn.getEstimate();
		DiscretizedFunc func2 =  estimate.getValues();
		assertEquals(1, func2.getX(0),tolerance);
		assertEquals(0.4, func2.getY(0), tolerance);
		assertEquals(4, func2.getX(1), tolerance);
		assertEquals(0.7, func2.getY(1), tolerance);
		assertEquals(8, func2.getX(2), tolerance);
		assertEquals(0.8, func2.getY(2), tolerance);
		assertEquals("meters", actualReturn.getUnits());


	}

	@Test
	public void testRemoveIntegerEstimateInstance() throws UpdateException {
		boolean status = estimateInstancesDB_DAO.removeEstimateInstance(5225);
		assertFalse("cannot remove estimate with 5225 as it does not exist", status);
		assertEquals("there should be 2 estimate instances", 2, estimateInstancesDB_DAO.getAllEstimateInstances().size());
		status = estimateInstancesDB_DAO.removeEstimateInstance(primaryKey1);
		assertTrue(status);
		assertEquals("there should be 1 estimate instance", 1, estimateInstancesDB_DAO.getAllEstimateInstances().size());
		status = estimateInstancesDB_DAO.removeEstimateInstance(primaryKey2);
		assertTrue(status);
		assertEquals("there should be 0 estimate instances", 0, estimateInstancesDB_DAO.getAllEstimateInstances().size());

	}
}
