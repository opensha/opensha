package org.opensha.sha.calc.IM_EventSet.gui;

import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import org.opensha.commons.data.siteData.SiteData;
import org.opensha.commons.data.siteData.SiteDataValue;
import org.opensha.commons.geo.Location;
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
	
    private final ParameterListEditor siteDataParamEditor;
    private final DoubleParameter latParam;
    private final DoubleParameter lonParam;
    private final static boolean D = false;


    /**
     * Constructor for AddSitePanel with existing site data values
     * Used for editing an existing site.
     * @param siteDataParams List of site data parameters to edit in this panel
     * @param dataList values for site data parameters to populate this panel
     * @param loc location of site to edit
     */
    public AddSitePanel(ParameterList siteDataParams,
                        ArrayList<SiteDataValue<?>> dataList,
                        Location loc) {
        this.setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        // Create a location list to specify sites
        ParameterList paramList = new ParameterList();
        this.latParam = new DoubleParameter("Latitude", loc.getLatitude());
        this.lonParam = new DoubleParameter("Longitude", loc.getLongitude());
        paramList.addParameter(latParam);
        paramList.addParameter(lonParam);
        ParameterListEditor paramEdit = new ParameterListEditor(paramList);
        paramEdit.setTitle("New Site Location");

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
        this(siteDataParams, /*dataList=*/null, new Location(34.0, -118.0));
	}

	public Location getSiteLocation() {
		return new Location(latParam.getValue(), lonParam.getValue());
	}

    /**
     * Gets the site data values from the site data parameters
     * @return ArrayList of SiteDataValue objects with proper metadata
     */
	public ArrayList<SiteDataValue<?>> getDataVals() {
        ParameterList siteDataParams = siteDataParamEditor.getParameterList();
        ArrayList<SiteDataValue<?>> values = new ArrayList<>();

        // Handle adding Vs30 value separately
        boolean hasVs30 = siteDataParams.containsParameter(Vs30_Param.NAME);
        boolean hasVs30Type = siteDataParams.containsParameter(Vs30_TypeParam.NAME);
        String measurementType = SiteData.TYPE_FLAG_INFERRED; // default
        if (hasVs30Type) {
            measurementType = siteDataParams
                    .getParameter(Vs30_TypeParam.NAME)
                    .getValue().toString();
        }
        if (hasVs30) {
            Double vs30Value = (Double)siteDataParams.getValue(Vs30_Param.NAME);
            SiteDataValue<Double> sdv = new SiteDataValue<>(
                    Vs30_Param.NAME, measurementType, vs30Value);
            values.add(sdv);
        }


        // Add all other site data values
        for (Parameter<?> param : siteDataParams) {
            String paramName = param.getName();
            Object paramValue = param.getValue();
            
            // Skip if the value is null
            if (paramValue == null) continue;
            
            // Cast paramValue to the appropriate type and add new SiteDataValue
           if (!(paramName.equals(Vs30_TypeParam.NAME) || paramName.equals(Vs30_Param.NAME))) {
               SiteDataValue<?> sdv;
                if (paramValue instanceof Double || paramValue instanceof Integer) {
                    paramValue = ((Number) paramValue).doubleValue();
                    sdv = new SiteDataValue<Double>(paramName, measurementType, (Double)paramValue);
                } else if (paramValue instanceof Boolean) {
                    sdv = new SiteDataValue<Boolean>(paramName, measurementType, (Boolean)paramValue);
                } else if (paramValue instanceof String) {
                    paramValue = paramValue.toString();
                    sdv = new SiteDataValue<String>(paramName, measurementType, (String)paramValue);
                } else {
                    sdv = new SiteDataValue<>(paramName, measurementType, paramValue);
                }
                if (D) System.out.println("Adding site data value: " + sdv);
               values.add(sdv);
            }
        }
        
        return values;
	}

    private void setSiteDataParams(ParameterList siteDataParams, ArrayList<SiteDataValue<?>> dataVals) {
        if (siteDataParams == null) return;
        // Need to explicitly set parameters with default null values or they are overwritten with the last non-null value
        // This is because null paramValues are skipped in AddSitePanel.getDataVals
        if (siteDataParams.containsParameter(DepthTo1pt0kmPerSecParam.NAME)) {
            ParameterEditor param = siteDataParams.getParameter(DepthTo1pt0kmPerSecParam.NAME).getEditor();
            param.setValue(null);
        }
        if (siteDataParams.containsParameter(DepthTo2pt5kmPerSecParam.NAME)) {
            ParameterEditor param = siteDataParams.getParameter(DepthTo2pt5kmPerSecParam.NAME).getEditor();
            param.setValue(null);
        }
        if (dataVals != null) {
            String measurementType = Vs30_TypeParam.VS30_TYPE_INFERRED;
            for (SiteDataValue<?> sdv : dataVals) {
                if (sdv.getDataType().equals(Vs30_Param.NAME)) {
                    measurementType = sdv.getDataMeasurementType();
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
	public static void main(String[] args) {
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
