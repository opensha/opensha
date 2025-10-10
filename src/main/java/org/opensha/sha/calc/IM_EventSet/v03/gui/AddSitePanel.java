package org.opensha.sha.calc.IM_EventSet.v03.gui;

import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.border.Border;

import org.opensha.commons.data.siteData.SiteData;
import org.opensha.commons.data.siteData.SiteDataValue;
import org.opensha.commons.data.siteData.impl.WillsMap2000;
import org.opensha.commons.geo.Location;
import org.opensha.commons.gui.LabeledBoxPanel;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.editor.ParameterEditor;
import org.opensha.commons.param.editor.impl.ParameterListEditor;
import org.opensha.commons.param.impl.DoubleParameter;
import org.opensha.nshmp2.imr.impl.Campbell_2003_AttenRel;
import org.opensha.sha.imr.IntensityMeasureRelationship;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.attenRelImpl.Field_2000_AttenRel;
import org.opensha.sha.imr.param.SiteParams.DepthTo1pt0kmPerSecParam;
import org.opensha.sha.imr.param.SiteParams.DepthTo2pt5kmPerSecParam;
import org.opensha.sha.imr.param.SiteParams.Vs30_Param;
import org.opensha.sha.imr.param.SiteParams.Vs30_TypeParam;

/**
 * Panel to add multiple sites and set its corresponding set of site data params.
 */
public class AddSitePanel extends JPanel {
	
    private ParameterListEditor siteDataParamEditor;
	
	private DoubleParameter latParam = new DoubleParameter("Latitude", 34.0);
	private DoubleParameter lonParam = new DoubleParameter("Longitude", -118.0);

    /**
     * Constructor for AddSitePanel with existing site data values
     * Used for editing an existing site.
     * @param siteDataParams List of site data parameters to edit in this panel
     * @param dataList values for site data parameters to populate this panel
     */
    public AddSitePanel(ParameterList siteDataParams, ArrayList<SiteDataValue<?>> dataList) {
        this.setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        // Create a location list to specify sites
        ParameterList paramList = new ParameterList();
        paramList.addParameter(latParam);
        paramList.addParameter(lonParam);
        ParameterListEditor paramEdit = new ParameterListEditor(paramList);
        paramEdit.setTitle("New Site Location");
//		paramEdit.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));

        JPanel leftCol = new JPanel();
        leftCol.setLayout(new BoxLayout(leftCol, BoxLayout.Y_AXIS));
        leftCol.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 10));

        leftCol.add(paramEdit);

        this.add(leftCol);

        // Create editor for provided site data parameters
        setSiteDataParams(siteDataParams, dataList);
        siteDataParamEditor = new ParameterListEditor(siteDataParams);
        siteDataParamEditor.setTitle("Set Site Params");
        this.add(siteDataParamEditor);
    }

    /**
     * Constructor for AddSitePanel.
     * Used for creating a new site.
     * @param siteDataParams List of site data parameters to edit in this panel
     */
	public AddSitePanel(ParameterList siteDataParams) {
        this(siteDataParams, null);
	}

	public Location getSiteLocation() {
		return new Location(latParam.getValue(), lonParam.getValue());
	}

    /**
     * Gets the site data values from the site data parameters
     * @return ArrayList of SiteDataValue objects with proper metadata
     */
	public ArrayList<SiteDataValue<?>> getDataVals() {
        ArrayList<SiteDataValue<?>> values = new ArrayList<>();
        
        // Iterate through all parameters in the editor
        for (Parameter<?> param : siteDataParamEditor.getParameterList()) {
            String paramName = param.getName();
            Object paramValue = param.getValue();
            
            // Skip if value is null
            if (paramValue == null) continue;
            
            // Map parameter name to SiteData type and create SiteDataValue
            String dataType = null;
            String measurementType = SiteData.TYPE_FLAG_INFERRED; // default
            Object value = paramValue;
            
            // Map common site parameters to their SiteData types
            if (paramName.equals(Vs30_Param.NAME)) {
                dataType = SiteData.TYPE_VS30;
                value = (Double) paramValue;
            } else if (paramName.equals(Vs30_TypeParam.NAME)) {
                // Handle Vs30 Type - this determines measurement type for Vs30
                String vs30Type = (String) paramValue;
                if (vs30Type.equals(Vs30_TypeParam.VS30_TYPE_MEASURED)) {
                    measurementType = SiteData.TYPE_FLAG_MEASURED;
                }
                // Don't add Vs30_Type as its own SiteDataValue
                continue;
            } else {
                // For other parameters, use the parameter name as dataType
                dataType = paramName;
                // Try to determine if it's a numeric or string parameter
                if (paramValue instanceof Double || paramValue instanceof Integer) {
                    value = ((Number) paramValue).doubleValue();
                } else if (paramValue instanceof Boolean) {
                    value = ((Boolean) paramValue).booleanValue();
                } else {
                    value = paramValue.toString();
                }
            }
            
            // Create and add the SiteDataValue
            if (dataType != null) {
                if (value instanceof Double) {
                    SiteDataValue<Double> sdv = new SiteDataValue<>(
                            dataType, measurementType, (Double) value);
                    values.add(sdv);

                } else if (value instanceof Boolean) {
                    SiteDataValue<Boolean> sdv = new SiteDataValue<>(
                            dataType, measurementType, (Boolean) value);
                    values.add(sdv);
                } else if (value != null) {
                    SiteDataValue<String> sdv = new SiteDataValue<>(
                            dataType, measurementType, (String) value);
                    values.add(sdv);
                } else {
                    throw new RuntimeException("Null value for data type: " + dataType);
                }
            }
        }
        
        return values;
	}

    private void setSiteDataParams(ParameterList siteDataParams, ArrayList<SiteDataValue<?>> dataVals) {
      if (dataVals != null) {
          String measurementType = Vs30_TypeParam.VS30_TYPE_INFERRED;
          for (SiteDataValue<?> sdv : dataVals) {
              if (sdv.getDataType().equals(Vs30_Param.NAME)) {
                  measurementType = sdv.getDataMeasurementType();
                  continue;
              }
              if (siteDataParams.containsParameter(sdv.getDataType())) {
                  ParameterEditor param = siteDataParams.getParameter(sdv.getDataType()).getEditor();
                  param.setValue(sdv.getValue());
              }
          }
          if (siteDataParams.containsParameter(Vs30_TypeParam.NAME)) {
            ParameterEditor param = siteDataParams.getParameter(Vs30_TypeParam.NAME).getEditor();
            param.setValue(measurementType);
          }
      }
    }

    /**
     * Tester main function
     * @param args
     */
	public static void main(String args[]) {
        // For demo, get siteDataParams for Campbell(2003) and Field(2000)
        List<ScalarIMR> imrs = new ArrayList<>();
        imrs.add(new Campbell_2003_AttenRel(null));
        imrs.add(new Field_2000_AttenRel(null));

        ParameterList siteDataParams = ParameterList.union(imrs.stream()
                .map(IntensityMeasureRelationship::getSiteParams)
                .toArray(ParameterList[]::new));

		JOptionPane.showConfirmDialog(
                null,
                siteDataParams,
                "Add Site",
                JOptionPane.OK_CANCEL_OPTION);
	}

}
