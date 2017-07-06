package org.opensha.commons.mapping.gmt.gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;

import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

import org.opensha.commons.util.cpt.CPT;

public class CPTListCellRenderer extends DefaultListCellRenderer {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	private JComboBox combo;
	
	public CPTListCellRenderer(JComboBox combo) {
		this.combo = combo;
	}

	@Override
	public Component getListCellRendererComponent(JList list, Object value,
			int index, boolean isSelected, boolean cellHasFocus) {
		if (!(value instanceof CPT))
			throw new IllegalArgumentException("JList value must be a CPT object");


		CPT cpt = (CPT)value;
		int width = combo.getWidth()-40;
		if (width < 100)
			width = 100;
		int height = 20;
		
		Color background;
		Color foreground;
		
		if (isSelected) {
			background = list.getSelectionBackground();
			foreground =list.getSelectionForeground();
		} else {
			background = list.getBackground();
			foreground =list.getForeground();
		}
		
		return buildComponent(cpt, width, height, background, foreground);
	}
	
	public JPanel buildComponent(CPT cpt, int width, int height, Color background, Color foreground) {
		CPTPanel cptPanel = new CPTPanel(cpt, width, height, 0, 0);
		JPanel panel= new JPanel();
		
		panel.setBackground(background);
		panel.setForeground(foreground);
		cptPanel.setBackground(background);
		cptPanel.setForeground(foreground);
		
		panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
		JLabel label = new JLabel();
		label.setBorder(new EmptyBorder(2, 2, 2, 6));
		panel.add(label);
		panel.add(cptPanel);
		label.setText(cpt.getName());
		label.updateUI();
		int cptWidth = width - (int)label.getPreferredSize().getWidth() - 4;
		if (cptWidth < 5)
			cptWidth = 5;
		cptPanel.update(cpt, cptWidth-4, height-8, 0, 0);
		
		Dimension cptDims = new Dimension(cptWidth, height);
		cptPanel.setSize(cptDims);
		cptPanel.setPreferredSize(cptDims);
		cptPanel.setMinimumSize(cptDims);
		cptPanel.validate();
		cptPanel.repaint();
//		cptPanel.paint(cptPanel.getGraphics());
		return panel;
	}



}
