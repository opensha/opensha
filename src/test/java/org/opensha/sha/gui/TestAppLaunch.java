package org.opensha.sha.gui;

import static org.junit.Assert.*;

import org.junit.Test;
import org.opensha.commons.data.siteData.gui.SiteDataCombinedApp;
import org.opensha.commons.mapping.gmt.gui.GMT_MapGeneratorApplet;
import org.opensha.sha.earthquake.calc.recurInterval.gui.ProbabilityDistGUI;
import org.opensha.sha.imr.attenRelImpl.gui.AttenuationRelationshipApplet;
import org.opensha.sha.magdist.gui.MagFreqDistApp;
import org.opensha.sha.magdist.gui.MagFreqDistAppWindow;

public class TestAppLaunch {

	@Test
	public void testLaunchAttenRel() {
		AttenuationRelationshipApplet app = AttenuationRelationshipApplet.launch();
		app.setVisible(true);
		app.setVisible(false);
		app.dispose();
		app = null;
		System.gc();
	}

	@Test
	public void testLaunchGMT() {
		GMT_MapGeneratorApplet app = GMT_MapGeneratorApplet.launch();
		app.setVisible(true);
		app.setVisible(false);
//		app.dispose();
		app = null;
		System.gc();
	}

	@Test
	public void testLaunchHazardCurve() {
		HazardCurveApplication app = HazardCurveApplication.launch(null);
		app.setVisible(true);
		app.setVisible(false);
		app.dispose();
		app = null;
		System.gc();
	}

	@Test
	public void testLaunchHazardSpectrum() {
		HazardSpectrumApplication app = HazardSpectrumApplication.launch(null);
		app.setVisible(true);
		app.setVisible(false);
		app.dispose();
		app = null;
		System.gc();
	}

	@Test
	public void testLaunchMFD() {
		MagFreqDistAppWindow app = MagFreqDistApp.launch();
		app.setVisible(true);
		app.setVisible(false);
		app.dispose();
		app = null;
		System.gc();
	}

	@Test
	public void testLaunchProbDist() {
		ProbabilityDistGUI app = ProbabilityDistGUI.launch();
		app.setVisible(true);
		app.setVisible(false);
		app.dispose();
		app = null;
		System.gc();
	}

	@Test
	public void testLaunchShakeMap() {
		ScenarioShakeMapApp app = ScenarioShakeMapApp.launch(null);
		app.setVisible(true);
		app.setVisible(false);
		app.dispose();
		app = null;
		System.gc();
	}

	@Test
	public void testLaunchSiteData() {
		SiteDataCombinedApp app = SiteDataCombinedApp.launch(null);
		app.setVisible(true);
		app.setVisible(false);
		app.dispose();
		app = null;
		System.gc();
	}

}
