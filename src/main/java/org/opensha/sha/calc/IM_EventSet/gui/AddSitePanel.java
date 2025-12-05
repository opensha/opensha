package org.opensha.sha.calc.IM_EventSet.gui;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.*;

import org.opensha.commons.data.siteData.OrderedSiteDataProviderList;
import org.opensha.commons.data.siteData.SiteData;
import org.opensha.commons.data.siteData.SiteDataValue;
import org.opensha.commons.geo.Location;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.editor.impl.ParameterListEditor;
import org.opensha.commons.param.impl.DoubleParameter;
import org.opensha.nshmp2.imr.impl.Campbell_2003_AttenRel;
import org.opensha.sha.imr.IntensityMeasureRelationship;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.attenRelImpl.Field_2000_AttenRel;
import org.opensha.sha.imr.param.SiteParams.Vs30_Param;
import org.opensha.sha.imr.param.SiteParams.Vs30_TypeParam;
import org.opensha.sha.util.SiteTranslator;

/**
 * Panel to add or edit sites and set corresponding site data params.
 */
public class AddSitePanel extends JPanel implements ActionListener {
	
    private final ParameterListEditor siteDataParamEditor;
    private final DoubleParameter latParam;
    private final DoubleParameter lonParam;
    private final static boolean D = false;
    private JButton setFromWebButton;
    OrderedSiteDataProviderList providers;

    /**
     * Used for editing an existing site.
     * @param siteDataParams List of site data parameters to edit in this panel
     * @param loc location of site to edit
     */
    public AddSitePanel(ParameterList siteDataParams, OrderedSiteDataProviderList providers, Location loc) {
        this.setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        this.providers = providers;
        // Create a location list to specify sites
        ParameterList paramList = new ParameterList();
        this.latParam = new DoubleParameter("Latitude", loc.getLatitude());
        this.lonParam = new DoubleParameter("Longitude", loc.getLongitude());
        paramList.addParameter(latParam);
        paramList.addParameter(lonParam);
        ParameterListEditor paramEdit = new ParameterListEditor(paramList);
        paramEdit.setTitle("New Site Location");


        // Add an informational message about site data precedence
//        LabeledBoxPanel setFromWebPanel = new LabeledBoxPanel();
        JPanel setFromWebPanel = new JPanel();
        setFromWebPanel.setLayout(new BoxLayout(setFromWebPanel, BoxLayout.Y_AXIS));
        setFromWebPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
//        setFromWebPanel.setTitle("Site Data Providers");
        setFromWebButton = new JButton("Set Params from Web Services");
        JTextArea infoText = new JTextArea(
                "Clicking the above button will overwrite user-provided site data values\n"
                + "with the values from the web services. See the \"Site Data Providers\" pane\n"
                + "to add or remove selected providers.\n\n"
                + "Hover over site data types on right for more information.\n"
                + "Hover over text fields for min and max values for numeric inputs.\n");
        setFromWebPanel.add(Box.createVerticalStrut(5));
        setFromWebPanel.add(setFromWebButton);
        setFromWebButton.addActionListener(this);
        setFromWebPanel.add(Box.createVerticalStrut(5));
        setFromWebPanel.add(infoText);

        // Create and populate the left column
        JPanel leftCol = new JPanel();
        leftCol.setLayout(new BoxLayout(leftCol, BoxLayout.Y_AXIS));
        leftCol.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 10));
        leftCol.add(paramEdit);
        leftCol.add(setFromWebPanel);
        this.add(leftCol);

        // Create editor for provided site data parameters
        siteDataParamEditor = new ParameterListEditor(siteDataParams);
        siteDataParamEditor.setPreferredSize(new Dimension(250, siteDataParamEditor.getPreferredSize().height));
        siteDataParamEditor.setMaximumSize(new Dimension(250, 500));
        siteDataParamEditor.setTitle("Set Site Params");
        this.add(siteDataParamEditor);
    }

    /**
     * Used for creating a new site.
     * @param siteDataParams List of site data parameters to edit in this panel
     */
	public AddSitePanel(ParameterList siteDataParams, OrderedSiteDataProviderList providers) {
        this(siteDataParams, providers, new Location(34.0, -118.0));
	}

	public Location getSiteLocation() {
		return new Location(latParam.getValue(), lonParam.getValue());
	}

    /**
     * TODO: Delete this before next OpenSHA release (v26.1.0)
     * Gets the site data values from the site data parameters
     * @return ArrayList of SiteDataValue objects with proper metadata
     */
    @Deprecated
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

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource().equals(setFromWebButton)) {
            // Fetch all data values from providers that provide at least one new data type
            ArrayList<SiteDataValue<?>> provData = providers.getBestAvailableData(getSiteLocation());
            ParameterList siteDataParams = siteDataParamEditor.getParameterList();
            SiteTranslator siteTrans = new SiteTranslator();
            for (Parameter<?> param : siteDataParams) {
                siteTrans.setParameterValue(param, provData);
            }
            siteDataParamEditor.refreshParamEditor();
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
