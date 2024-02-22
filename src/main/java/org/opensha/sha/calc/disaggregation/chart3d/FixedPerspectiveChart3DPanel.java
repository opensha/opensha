package org.opensha.sha.calc.disaggregation.chart3d;

import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;

import org.jfree.chart3d.Chart3D;
import org.jfree.chart3d.Chart3DPanel;

public class FixedPerspectiveChart3DPanel extends Chart3DPanel {
	
	public FixedPerspectiveChart3DPanel(Chart3D chart) {
		super(chart);
	}

	@Override
	public void mouseClicked(MouseEvent e) {
		// do nothing
	}

	@Override
	public void mouseMoved(MouseEvent e) {
		// do nothing
	}

	@Override
	public void mousePressed(MouseEvent e) {
		// do nothing
	}

	@Override
	public void mouseReleased(MouseEvent e) {
		// do nothing
	}

	@Override
	public void mouseDragged(MouseEvent e) {
		// do nothing
	}

	@Override
	public void mouseWheelMoved(MouseWheelEvent mwe) {
		// do nothing
	}

}
