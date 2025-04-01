package org.opensha.sha.gcim.ui;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.opensha.sha.gcim.imCorrRel.ImCorrelationRelationship;
import org.opensha.sha.gui.HazardCurveApplication;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.util.TectonicRegionType;

public class TestGcimControlPanel {
	private GcimControlPanel gcimControlPanel;
	private HazardCurveApplication applet; // Required for gcimControlPanel construction

	// Main four Array lists for storing IMT, IMR, IMCorrRel details
	// Values are copied out to test correct behavior of public methods
	private ArrayList<String> imiTypes;
	private ArrayList<? extends Map<TectonicRegionType, ScalarIMR>> imiMapAttenRels; 
	private ArrayList<? extends Map<TectonicRegionType, ImCorrelationRelationship>> imijMapCorrRels; 
	// Correlation relations for off-diagonal terms i.e. IMi,IMk|Rup=rup,IMj=imj
	private ArrayList<? extends Map<TectonicRegionType, ImCorrelationRelationship>> imikjMapCorrRels; 
	
	/**
	 * Copies IMi values out from gcimControlPanel instance.
	 */
	private void copyIMiValues() {
		imiTypes = gcimControlPanel.getImiTypes();
		imiMapAttenRels = gcimControlPanel.getImris();
		imijMapCorrRels = gcimControlPanel.getImCorrRels();
		imikjMapCorrRels = gcimControlPanel.getImikCorrRels();
		
	}
	@Before
	public void init() {
		applet = new HazardCurveApplication("TestGcimControlPanel");
		applet.setVisible(false);
		applet.init();
		gcimControlPanel = new GcimControlPanel(applet, applet);

		imiTypes = new ArrayList<String>();
		imiMapAttenRels = new ArrayList<Map<TectonicRegionType, ScalarIMR>>();
		imijMapCorrRels = new ArrayList<Map<TectonicRegionType, ImCorrelationRelationship>>();
		imikjMapCorrRels = new ArrayList<Map<TectonicRegionType, ImCorrelationRelationship>>();
	}
	
	@Test
	public void testAddIMiDetailsInArrayLists() {
		fail("Not yet implemented"); // TODO

		// Test initially has placeholder with one
		
		// Test has one after insertion, overriding placeholder

		// Test has two after insertion

		// Test has three after insertion

	}

	@Test
	public void testRemoveIMiDetailsInArrayLists() {
		fail("Not yet implemented"); // TODO

		// Test unable to remove when already just placeholder
		
		// Test removes one after one insertion

		// Test again unable to remove after last removal

		// Test add 4 and remove last four all in reverse order

		// Test add 10 and remove at index 0, 2, and 5.

	}

	@Test
	public void testUpdateIMiDetailsInArrayLists() {
		fail("Not yet implemented"); // TODO

		// Test add 10 and update at index 7

		// Test update at index 0
		
		// Test update at index 9

	}
}
