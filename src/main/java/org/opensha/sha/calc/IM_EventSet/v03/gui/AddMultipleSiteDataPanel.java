package org.opensha.sha.calc.IM_EventSet.v03.gui;

import java.util.ArrayList;

import javax.swing.JOptionPane;

import org.opensha.commons.data.siteData.SiteDataValue;

public class AddMultipleSiteDataPanel extends NamesListPanel {
	
	private ArrayList<SiteDataValue<?>> vals;
	private AddSiteDataPanel adder;
	
	public AddMultipleSiteDataPanel() {
		super(null, "Site Data Values:");
		adder = new AddSiteDataPanel();
		
		vals = new ArrayList<SiteDataValue<?>>();
		
		this.setLowerPanel(adder);
	}
	
	public ArrayList<SiteDataValue<?>> getValues() {
		return vals;
	}
	
	public void rebuildList() {
		Object names[] = new String[vals.size()];
		for (int i=0; i<vals.size(); i++) {
			SiteDataValue<?> val = vals.get(i);
			names[i] = SitesPanel.getDataListString(i, val);
		}
		namesList.setListData(names);
	}

	@Override
	public void addButton_actionPerformed() {
		SiteDataValue<?> val;
		try {
			val = adder.getValue();
			vals.add(val);
			rebuildList();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			JOptionPane.showMessageDialog(this, "Error parsing value:\n" + e.getMessage(),
					"Error!", JOptionPane.ERROR_MESSAGE);
		}
	}

	@Override
	public void removeButton_actionPerformed() {
		int index = namesList.getSelectedIndex();
		vals.remove(index);
		rebuildList();
	}

	@Override
	public boolean shouldEnableAddButton() {
		return true;
	}

}
