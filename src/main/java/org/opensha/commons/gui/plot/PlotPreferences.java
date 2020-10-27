package org.opensha.commons.gui.plot;

import java.awt.Color;
import java.util.List;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import com.google.common.collect.Lists;

/**
 * Class for storing plot preferences (font sizes and colors).
 * Classes can subscribe as a listener for updates.
 * 
 * @author kevin
 *
 */
public class PlotPreferences {
	
	private int axisLabelFontSize;
	private int tickLabelFontSize;
	private int plotLabelFontSize;
	private int legendFontSize;
	private Color backgroundColor;
	
	private Color insetLegendBackground = new Color(255, 255, 255, 180);
	private Color insetLegendBorder = Color.BLACK;
	
	private List<ChangeListener> listeners = Lists.newArrayList();
	
	/**
	 * Default OpenSHA plot preferences.
	 * @return
	 */
	public static PlotPreferences getDefault() {
		PlotPreferences pref = new PlotPreferences();
		pref.tickLabelFontSize = 12;
		pref.axisLabelFontSize = 14;
		pref.plotLabelFontSize = 16;
		pref.legendFontSize = 14;
		pref.backgroundColor = new Color( 200, 200, 230 );
		return pref;
	}
	
	private PlotPreferences() {
		
	}
	
	public int getAxisLabelFontSize() {
		return axisLabelFontSize;
	}

	public void setAxisLabelFontSize(int axisLabelFontSize) {
		this.axisLabelFontSize = axisLabelFontSize;
		fireChangeEvent();
	}

	public int getTickLabelFontSize() {
		return tickLabelFontSize;
	}

	public void setTickLabelFontSize(int tickLabelFontSize) {
		this.tickLabelFontSize = tickLabelFontSize;
		fireChangeEvent();
	}

	public int getPlotLabelFontSize() {
		return plotLabelFontSize;
	}

	public void setPlotLabelFontSize(int plotLabelFontSize) {
		this.plotLabelFontSize = plotLabelFontSize;
		fireChangeEvent();
	}

	public Color getBackgroundColor() {
		return backgroundColor;
	}

	public void setBackgroundColor(Color backgroundColor) {
		this.backgroundColor = backgroundColor;
		fireChangeEvent();
	}

	public int getLegendFontSize() {
		return legendFontSize;
	}

	public void setLegendFontSize(int legendFontSize) {
		this.legendFontSize = legendFontSize;
	}

	public void addChangeListener(ChangeListener l) {
		listeners.add(l);
	}
	
	public boolean removeChangeListener(ChangeListener l) {
		return listeners.remove(l);
	}
	
	private void fireChangeEvent() {
		if (listeners.isEmpty())
			return;
		ChangeEvent e = new ChangeEvent(this);
		for (ChangeListener l : listeners)
			l.stateChanged(e);
	}

	public Color getInsetLegendBackground() {
		return insetLegendBackground;
	}

	public void setInsetLegendBackground(Color insetLegendBackground) {
		this.insetLegendBackground = insetLegendBackground;
	}

	public Color getInsetLegendBorder() {
		return insetLegendBorder;
	}

	public void setInsetLegendBorder(Color insetLegendBorder) {
		this.insetLegendBorder = insetLegendBorder;
	}

}
