package org.opensha.commons.gui.plot;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

import org.jfree.data.Range;
import org.opensha.commons.gui.ControlPanel;

/**
 * <p>Title: AxisLimitsControlPanel</p>
 *
 * <p>Description: This Class pop up window when custom scale is selecetd for the combo box that enables the
 * user to customise the X and Y Axis scale</p>

 * @author : Nitin Gupta and Vipin Gupta
 * @version 1.0
 */

public class AxisLimitsControlPanel extends ControlPanel {
	
	public static final String NAME = "Set Axis";
	
	private JLabel minXLabel = new JLabel();
	private JTextField minXField = new JTextField();
	private JLabel maxXLabel = new JLabel();
	private JTextField maxXField = new JTextField();
	private JLabel minYLabel = new JLabel();
	private JTextField minYField = new JTextField();
	private JLabel maxYLabel = new JLabel();
	private JTextField maxYField = new JTextField();
	
	private JCheckBox customXBox = new JCheckBox("Custom X Range");
	private JCheckBox customYBox = new JCheckBox("Custom Y Range");
	
	private JButton ok = new JButton();
	private JButton cancel = new JButton();
	
	private JDialog dialog = new JDialog();
	
	private Component parent;
	
	private GraphWidget gw;

	/**
	 * Contructor which displays the window so that user can set the X and Y axis
	 * range
	 * @param gw : GraphWidget instance
	 * @param component The parent component. This is the parent window on which
	 * this Axis range window will appear, center aligned
	 */
	public AxisLimitsControlPanel(GraphWidget gw, Component parent) {
		super(NAME);
		this.gw = gw;
		this.parent = parent;
	}
	
	@Override
	public void doinit() {
		dialog.setModal(true);
		// show the window at center of the parent component
		dialog.setLocation(parent.getX()+parent.getWidth()/2,
				parent.getY()+parent.getHeight()/2);
		try{
			jbInit();
		}catch(Exception e){
			System.out.println("Error Occured while running range combo box: "+e);
		}
	}

	/**
	 * This is called whenever this window is shown again
	 */
	public void updateParams() {
		Range curXRange = gw.getX_AxisRange();
		Range curYRange = gw.getY_AxisRange();
		boolean customXRange = gw.getUserX_AxisRange() != null;
		boolean customYRange = gw.getUserY_AxisRange() != null;
		
		customXBox.setSelected(customXRange);
		customYBox.setSelected(customYRange);
		
		this.minXField.setText(""+curXRange.getLowerBound());
		this.maxXField.setText(""+curXRange.getUpperBound());
		this.minYField.setText(""+curYRange.getLowerBound());
		this.maxYField.setText(""+curYRange.getUpperBound());
		
		updateEnables();
	}

	void jbInit() throws Exception {
		JPanel gridPanel = new JPanel();
		gridPanel.setLayout(new GridLayout(4, 2, 10, 10));
		
		dialog.setTitle("Axis Control Panel");
		minXLabel.setForeground(Color.black);
		minXLabel.setText("Min X:");
		maxXLabel.setForeground(Color.black);
		maxXLabel.setText("Max X:");
		minYLabel.setForeground(Color.black);
		minYLabel.setText("Min Y:");
		maxYLabel.setForeground(Color.black);
		maxYLabel.setText("Max Y:");
		ok.setText("OK");
		ok.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(ActionEvent e) {
				ok_actionPerformed(e);
			}
		});
		cancel.setText("Cancel");
		cancel.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(ActionEvent e) {
				cancel_actionPerformed(e);
			}
		});
		updateParams();
		customXBox.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				checkbox_actionPerformed(e);
			}
		});
		customYBox.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				checkbox_actionPerformed(e);
			}
		});
		
		gridPanel.add(customXBox);
		gridPanel.add(customYBox);
		gridPanel.add(buildGridded(minXLabel, minXField));
		gridPanel.add(buildGridded(minYLabel, minYField));
		gridPanel.add(buildGridded(maxXLabel, maxXField));
		gridPanel.add(buildGridded(maxYLabel, maxYField));
		gridPanel.add(buildGridded(new JLabel(), ok));
		gridPanel.add(buildGridded(cancel, new JLabel()));
		
		gridPanel.setMaximumSize(new Dimension(348, 143));
		dialog.setContentPane(gridPanel);
		dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
//		dialog.setSize(600, 400);
//		dialog.setSize(dialog.getPreferredSize());
		dialog.setResizable(false);
	}
	
	private static JPanel buildGridded(JComponent comp1, JComponent comp2) {
		JPanel panel = new JPanel();
		panel.setLayout(new GridLayout(1, 2));
		panel.add(comp1);
		panel.add(comp2);
		return panel;
	}


	/**
	 * This function also calls the setYRange and setXRange functions of application
	 * which sets the range of the axis based on the user input
	 *
	 * @param e= this event occur when the Ok button is clicked on the custom axis popup window
	 */
	void ok_actionPerformed(ActionEvent e) {
		Range xRange = null;
		Range yRange = null;
		try {
			if (customXBox.isSelected())
				xRange = new Range(Double.parseDouble(this.minXField.getText()),
						Double.parseDouble(this.maxXField.getText()));
			if (customYBox.isSelected())
				yRange = new Range(Double.parseDouble(this.minYField.getText()),
						Double.parseDouble(this.maxYField.getText()));
		} catch (NumberFormatException e1) {
			System.out.println("Exception:"+e1);
			JOptionPane.showMessageDialog(dialog,new String("Text Entered must be a valid numerical value"),
					new String("Check Axis Range"),JOptionPane.ERROR_MESSAGE);
		}
		gw.setAxisRange(xRange, yRange);
		dialog.dispose();
	}

	/**
	 *
	 * @param e= this event occurs to destroy the popup window if the user has selected cancel option
	 */
	void cancel_actionPerformed(ActionEvent e) {
		dialog.dispose();
	}

	/**
	 * This is called when user selects "Auto scale" or "Custom scale" option
	 * @param e
	 */
	void checkbox_actionPerformed(ActionEvent e) {
		updateEnables();
	}
	
	/**
	 * updates enabled state of text fields from check boxes
	 */
	private void updateEnables() {
		boolean enableX = customXBox.isSelected();
		boolean enableY = customYBox.isSelected();
		

		this.minXField.setEnabled(enableX);
		this.maxXField.setEnabled(enableX);
		this.minYField.setEnabled(enableY);
		this.maxYField.setEnabled(enableY);
	}

	@Override
	public Window getComponent() {
		return dialog;
	}
}
