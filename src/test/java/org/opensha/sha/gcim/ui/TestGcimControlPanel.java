package org.opensha.sha.gcim.ui;

import static org.junit.Assert.*;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.opensha.sha.gcim.imCorrRel.ImCorrelationRelationship;
import org.opensha.sha.gui.HazardCurveApplication;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.util.TectonicRegionType;

/**
 * TODO: Write these unit tests after logic decoupling.
 * 		 The control panel cannot currently be created outside the context of a
 * 		 HazardCurveApplication. This prevents testing logic directly inside the panel.
 *       
 *       Consider creating a separate Calculator class that does the logic,
 *       to provide a GUI-decoupled interface we can unit test.
 *       
 *       In the interim, test manually by running the HazardCurveApp and monitoring
 *       variables in debug mode.
 */
public class TestGcimControlPanel {
	private GcimControlPanel gcimControlPanel;
	// Note: The GcimControlPanel is tightly integrated with HazardCurveApplication.
	//		 Decoupling will allow for much easier testing and less mocks.
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
		gcimControlPanel = spy(new GcimControlPanel(applet, applet));
		gcimControlPanel.init();
		gcimControlPanel.getComponent().setVisible(false);
		
		// Mock behavior for gcimEditControlPanel, since there are no Gui Beans
//		Whitebox.setInternalState(gcimControlPanel, "gcimEditControlPanel")
		// TODO: We can't mock gcimEditControlPanel since it's a private member.
		// This must be set in order to invoke addIMiDetailsInArrayLists.
		// The addIMiDetailsInArrayLists method is not functional outside the
		// "Add IMi" button's context. Logic must be decoupled prior to unit testing.
		
		// Partial mocking with spy/when - https://stackoverflow.com/a/37110455


		imiTypes = new ArrayList<String>();
		imiMapAttenRels = new ArrayList<Map<TectonicRegionType, ScalarIMR>>();
		imijMapCorrRels = new ArrayList<Map<TectonicRegionType, ImCorrelationRelationship>>();
		imikjMapCorrRels = new ArrayList<Map<TectonicRegionType, ImCorrelationRelationship>>();
		copyIMiValues();
	}
	
//	@Test
//	public void testAddIMiDetailsInArrayLists() {
////		fail("Not yet implemented"); // TODO
//
//		// Test initially has no placeholder
//		assertEquals(gcimControlPanel.getNumIMi(), 0);
//		assertEquals(imiTypes.size(), 0);
//		assertEquals(imiMapAttenRels.size(), 0);
//		assertEquals(imijMapCorrRels.size(), 0);
//		assertEquals(imikjMapCorrRels.size(), 0);
//		
//		// Test has one after insertion
//		gcimControlPanel.addIMiDetailsInArrayLists();
//		copyIMiValues();
//		
//		assertEquals(gcimControlPanel.getNumIMi(), 1);
//		assertEquals(imiTypes.size(), 1);
//		assertEquals(imiMapAttenRels.size(), 1);
//		assertEquals(imijMapCorrRels.size(), 1);
//		assertEquals(imikjMapCorrRels.size(), 0);
//
//		// Test has two after insertion
//
//		// Test has three after insertion
//		
//		// Reinitialize and use placeholder
//		
//		// Should still have one after insertion
//		
//		// Counts should increment the same
//
//	}

//	@Test
//	public void testRemoveIMiDetailsInArrayLists() {
//		fail("Not yet implemented"); // TODO
//
//		// Test unable to remove when already just placeholder
//		
//		// Test removes one after one insertion
//
//		// Test again unable to remove after last removal
//
//		// Test add 4 and remove last four all in reverse order
//
//		// Test add 10 and remove at index 0, 2, and 5.
//
//	}
//
//	@Test
//	public void testUpdateIMiDetailsInArrayLists() {
//		fail("Not yet implemented"); // TODO
//
//		// Test add 10 and update at index 7
//
//		// Test update at index 0
//		
//		// Test update at index 9
//
//	}
}
