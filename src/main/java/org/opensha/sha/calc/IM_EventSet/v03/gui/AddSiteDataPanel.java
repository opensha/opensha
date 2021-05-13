/*******************************************************************************
 * Copyright 2009 OpenSHA.org in partnership with
 * the Southern California Earthquake Center (SCEC, http://www.scec.org)
 * at the University of Southern California and the UnitedStates Geological
 * Survey (USGS; http://www.usgs.gov)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package org.opensha.sha.calc.IM_EventSet.v03.gui;

import java.util.ArrayList;

import javax.swing.BoxLayout;

import org.opensha.commons.data.siteData.SiteData;
import org.opensha.commons.data.siteData.SiteDataValue;
import org.opensha.commons.data.siteData.impl.WillsMap2000;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.editor.impl.ParameterListEditor;
import org.opensha.commons.param.impl.StringParameter;
import org.opensha.sha.util.SiteTranslator;

public class AddSiteDataPanel extends ParameterListEditor {
	
	public static ArrayList<String> siteDataTypes;
	
	static {
		siteDataTypes = new ArrayList<String>();
		siteDataTypes.add(SiteData.TYPE_VS30);
		siteDataTypes.add(SiteData.TYPE_WILLS_CLASS);
		siteDataTypes.add(SiteData.TYPE_DEPTH_TO_2_5);
		siteDataTypes.add(SiteData.TYPE_DEPTH_TO_1_0);
	}
	
	StringParameter typeSelector;
	StringParameter measSelector;
	StringParameter valEntry;
	
	public AddSiteDataPanel() {
		this.setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		
		typeSelector = new StringParameter("Site Data Type", siteDataTypes, siteDataTypes.get(0));
		
		ArrayList<String> measTypes = new ArrayList<String>();
		measTypes.add(SiteData.TYPE_FLAG_INFERRED);
		measTypes.add(SiteData.TYPE_FLAG_MEASURED);
		
		measSelector = new StringParameter("Site Data Measurement Type", measTypes, measTypes.get(0));
		
		valEntry = new StringParameter("Value");
		
		ParameterList paramList = new ParameterList();
		
		paramList.addParameter(typeSelector);
		paramList.addParameter(measSelector);
		paramList.addParameter(valEntry);
		
		this.setTitle("New Site Data Value");
		this.setParameterList(paramList);
	}
	
	public SiteDataValue<?> getValue() {
		String type = typeSelector.getValue();
		String measType = measSelector.getValue();
		String valStr = valEntry.getValue();
		if (valStr == null || valStr.length() == 0)
			throw new RuntimeException("No value was entered!");
		return getValue(type, measType, valStr);
	}
	
	public static SiteDataValue<?> getValue(String type, String measType, String valStr) {
		valStr = valStr.trim();
		Object val;
		
		if (type.equals(SiteData.TYPE_WILLS_CLASS)) {
			if (!WillsMap2000.wills_vs30_map.containsKey(valStr))
				throw new RuntimeException("'" + valStr + "' is not a valid Wills Site Class!");
			val = valStr;
		} else {
			// it's a double that we need to parse
			try {
				val = Double.parseDouble(valStr);
			} catch (NumberFormatException e) {
				throw new NumberFormatException("'" + valStr + "' cannot be parsed into a numerical value!");
			}
		}
		return new SiteDataValue(type, measType, val);
	}

}
