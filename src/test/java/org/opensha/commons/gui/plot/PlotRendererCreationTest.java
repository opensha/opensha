package org.opensha.commons.gui.plot;

import static junit.framework.Assert.*;

import java.awt.Shape;
import java.awt.Stroke;

import org.jfree.chart.renderer.xy.StackedXYBarRenderer;
import org.jfree.chart.renderer.xy.XYBarRenderer;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.junit.Test;

public class PlotRendererCreationTest {
	
	public static void testSymbolRendererCorrect(PlotSymbol sym, XYItemRenderer renderer) {
		assertTrue("renderer should have symbols, but isn't correct type for sym="+sym,
				renderer instanceof XYLineAndShapeRenderer);
	}
	
	public static void testLineRendererCorrect(PlotLineType plt, XYItemRenderer renderer) {
		if (plt.isSymbolCompatible()) {
			assertTrue("renderer should have lines & is symbol compatible, but isn't correct type for sym="+plt,
					renderer instanceof XYLineAndShapeRenderer);
			
			XYLineAndShapeRenderer stdRend = (XYLineAndShapeRenderer)renderer;
			Stroke fromRend = stdRend.getDefaultStroke();
			assertNotNull("stroke should not be null!", fromRend);
		} else {
			String msg = "renderer should have lines & isn'tsymbol compatible, but isn't correct type for sym="+plt;
			if (plt == PlotLineType.HISTOGRAM)
				assertTrue(msg, renderer instanceof XYBarRenderer);
			else if (plt == PlotLineType.STACKED_BAR)
				assertTrue(msg, renderer instanceof StackedXYBarRenderer);
			else if (plt == PlotLineType.SOLID_BAR)
				assertTrue(msg, renderer instanceof XYSolidBarRenderer);
		}
	}
	
	public void testBuildShapes() {
		for (PlotSymbol sym : PlotSymbol.values()) {
			assertNotNull("shape shouldn't be null for"+sym, sym.buildShape(1f));
		}
	}
	
	@Test (expected=IllegalArgumentException.class)
	public void testBuildStrokeBadWidth() {
		PlotLineType.DOTTED.buildStroke(0);
	}
	
	@Test (expected=IllegalArgumentException.class)
	public void testBuildShapeBadWidth() {
		PlotSymbol.CIRCLE.buildShape(0);
	}
	
	@Test
	public void testBuildStrokes() {
		for (PlotLineType plt : PlotLineType.values()) {
			if (plt.isSymbolCompatible()) {
				Stroke stroke = plt.buildStroke(1f);
				assertNotNull("stoke shouldn't be null for "+plt, stroke);
			} else {
				try {
					plt.buildStroke(1f);
					fail("Should have thrown an exception");
				} catch (Exception e) {}
			}
		}
	}

	@Test
	public void testAllCombinations() {
		for (PlotLineType plt : PlotLineType.values()) {
			for (PlotSymbol sym : PlotSymbol.values()) {
				if (plt.isSymbolCompatible()) {
					XYItemRenderer renderer = PlotLineType.buildRenderer(plt, sym, 1f);
					testSymbolRendererCorrect(sym, renderer);
					testLineRendererCorrect(plt, renderer);
				}
			}
		}
	}
	
	@Test (expected=IllegalStateException.class)
	public void testBothNull() {
		PlotLineType.buildRenderer(null, null, 1f);
	}
	
	@Test (expected=IllegalStateException.class)
	public void testColorerWhenNotSupported() {
		PlotLineType.buildRenderer(PlotLineType.HISTOGRAM, PlotSymbol.CIRCLE, 1f);
	}
	
	@Test
	public void testOnlyLine() {
		for (PlotLineType plt : PlotLineType.values()) {
			XYItemRenderer renderer = PlotLineType.buildRenderer(plt, null, 1f);
			testLineRendererCorrect(plt, renderer);
		}
	}
	
	@Test
	public void testOnlySymbols() {
		for (PlotSymbol sym : PlotSymbol.values()) {
			XYItemRenderer renderer = PlotLineType.buildRenderer(null, sym, 1f);
			testSymbolRendererCorrect(sym, renderer);
		}
	}
}
