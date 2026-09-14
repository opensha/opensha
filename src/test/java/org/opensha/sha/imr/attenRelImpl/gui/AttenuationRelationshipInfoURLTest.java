package org.opensha.sha.imr.attenRelImpl.gui;

import static org.junit.Assert.*;

import java.lang.reflect.Constructor;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;
import org.opensha.commons.param.event.ParameterChangeWarningListener;
import org.opensha.sha.imr.IntensityMeasureRelationship;

/**
 * Verifies the "Get Info" URL wiring for every attenuation relationship offered
 * by the {@link AttenuationRelationshipApplet} "Attenuation Relationship
 * Plotter".
 * <p>
 * Each model that has a published paper must return its paper's DOI
 * ({@code https://doi.org/...}). The few models with no published paper or DOI
 * of their own (conference-only, hybrid, or preliminary pre-publication models)
 * intentionally return {@code null} from {@code getInfoURL()} so the plotter
 * reports "No information exists for the selected Attenuation Relationship"
 * deliberately rather than linking to an unrelated page; the missing DOI is
 * documented in each such {@code getInfoURL()}.
 * <p>
 * This guards against regressions where a model with a paper falls through to
 * the throwing default in {@code AbstractIMR.getInfoURL()}, which the plotter
 * also reports as "No information exists".
 *
 * @author Akash Bhatthal
 */
public class AttenuationRelationshipInfoURLTest {

	/**
	 * Models that intentionally have no information URL (no published paper or
	 * DOI of their own). These must return {@code null} from
	 * {@code getInfoURL()}; every other plotter model must return a DOI. The
	 * missing DOI for each is documented in its {@code getInfoURL()}.
	 */
	private static final Set<String> NO_PAPER_MODELS = Collections
			.unmodifiableSet(new HashSet<>(Arrays.asList(
					"org.opensha.sha.imr.attenRelImpl.Abrahamson_2000_AttenRel",
					"org.opensha.sha.imr.attenRelImpl.ShakeMap_2003_AttenRel",
					"org.opensha.sha.imr.attenRelImpl.CY_2006_AttenRel",
					"org.opensha.sha.imr.attenRelImpl.CB_2006_AttenRel",
					"org.opensha.sha.imr.attenRelImpl.BA_2006_AttenRel")));

	/**
	 * Every model in the plotter dropdown must return either its paper's DOI
	 * or, for the known no-paper models, intentionally {@code null}.
	 */
	@Test
	public void allPlotterModelsHaveInfoURL() throws Exception {
		List<String> failures = new ArrayList<>();
		Set<String> seenNoPaper = new HashSet<>();
		int checked = 0;
		for (String className : AttenuationRelationshipApplet.attenRelClasses) {
			checked++;
			try {
				IntensityMeasureRelationship imr = instantiate(className);
				URL url = imr.getInfoURL();
				if (NO_PAPER_MODELS.contains(className)) {
					seenNoPaper.add(className);
					if (url != null) {
						failures.add(className
								+ ": no-paper model should return null, got "
								+ url.toExternalForm());
					} else {
						System.out.println(className + " -> null (intentional)");
					}
					continue;
				}
				if (url == null) {
					failures.add(className + ": getInfoURL() returned null");
					continue;
				}
				String ext = url.toExternalForm();
				System.out.println(className + " -> " + ext);
				if (!ext.startsWith("https://doi.org/")) {
					failures.add(className + ": unexpected URL " + ext);
				}
			} catch (Throwable t) {
				failures.add(className + ": " + t.getClass().getSimpleName()
						+ ": " + t.getMessage());
			}
		}
		// Every declared no-paper model must actually appear in the plotter
		// dropdown, otherwise a misspelled class name would silently skip it.
		Set<String> missingNoPaper = new HashSet<>(NO_PAPER_MODELS);
		missingNoPaper.removeAll(seenNoPaper);
		for (String m : missingNoPaper) {
			failures.add(m + ": declared no-paper model not in plotter dropdown");
		}
		if (!failures.isEmpty()) {
			fail("Get Info URL wiring failures (" + failures.size() + "/" + checked
					+ "):\n  - " + String.join("\n  - ", failures));
		}
		assertTrue("no plotter models were checked", checked > 0);
	}

	/**
	 * Instantiate an attenuation relationship by class name, mirroring the
	 * reflection path the plotter uses: prefer the constructor that accepts a
	 * {@link ParameterChangeWarningListener} (passing {@code null}), then fall
	 * back to the no-arg constructor.
	 */
	private static IntensityMeasureRelationship instantiate(String className)
			throws Exception {
		Class<?> clazz = Class.forName(className);
		try {
			Constructor<?> c = clazz
					.getConstructor(ParameterChangeWarningListener.class);
			return (IntensityMeasureRelationship) c
					.newInstance((ParameterChangeWarningListener) null);
		} catch (NoSuchMethodException e) {
			return (IntensityMeasureRelationship) clazz.getConstructor().newInstance();
		}
	}
}