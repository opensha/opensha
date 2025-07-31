package org.opensha.sha.calc.IM_EventSet.v03.gui;

import java.util.ArrayList;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.border.Border;

import org.opensha.commons.data.siteData.SiteDataValue;
import org.opensha.commons.data.siteData.impl.WillsMap2000;
import org.opensha.commons.geo.Location;
import org.opensha.commons.gui.LabeledBoxPanel;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.editor.impl.ParameterListEditor;
import org.opensha.commons.param.impl.DoubleParameter;

public class AddSitePanel extends JPanel {
	
	private AddMultipleSiteDataPanel adder;
	
	private DoubleParameter latParam = new DoubleParameter("Latitude", 34.0);
	private DoubleParameter lonParam = new DoubleParameter("Longitude", -118.0);
	
	public AddSitePanel() {
		this.setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
		

		ParameterList paramList = new ParameterList();
		paramList.addParameter(latParam);
		paramList.addParameter(lonParam);
		ParameterListEditor paramEdit = new ParameterListEditor(paramList);
		paramEdit.setTitle("New Site Location");
//		paramEdit.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
		
		LabeledBoxPanel help = new LabeledBoxPanel();
		help.setTitle("Help");
		JTextArea helpText = new JTextArea(5, 20);
		helpText.setText(
				"Specify location and data value, then click \"Add\".\n"
				+ "After adding all values, click \"OK\" to continue.\n\n"
				+ "Default Location (34.0, -118.0) = Los Angeles\n\n"
				+ "Vs30, Depth to Vs : Numeric\n\n"
				+ "Wills Class : " + WillsMap2000.wills_vs30_map.keySet());
		help.add(helpText);

		Border line = BorderFactory.createLineBorder(help.getBorderColor());
		Border padding = BorderFactory.createEmptyBorder(10, 0, 0, 0);
		help.setBorder(BorderFactory.createCompoundBorder(padding, line));

		JPanel leftCol = new JPanel();
		leftCol.setLayout(new BoxLayout(leftCol, BoxLayout.Y_AXIS));
		leftCol.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 10));

		leftCol.add(paramEdit);
		leftCol.add(help);
		
		this.add(leftCol);
		
		adder = new AddMultipleSiteDataPanel();
		this.add(adder);
	}	
	
	public Location getSiteLocation() {
		return new Location(latParam.getValue(), lonParam.getValue());
	}
	
	public ArrayList<SiteDataValue<?>> getDataVals() {
		ArrayList<SiteDataValue<?>> vals = adder.getValues();
		return vals;
	}
	
	public static void main(String args[]) {
		JOptionPane.showConfirmDialog(null, new AddSitePanel(), "Add Site", JOptionPane.OK_CANCEL_OPTION);
	}

}
