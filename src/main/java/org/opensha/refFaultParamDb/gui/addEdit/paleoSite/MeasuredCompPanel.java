package org.opensha.refFaultParamDb.gui.addEdit.paleoSite;

import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.util.ArrayList;

import javax.swing.JPanel;

import org.opensha.commons.param.editor.impl.ConstrainedStringParameterEditor;
import org.opensha.commons.param.impl.StringParameter;
import org.opensha.refFaultParamDb.gui.infotools.GUI_Utils;

/**
 * <p>Title: SenseOfMotion_MeasuredCompPanel.java </p>
 * <p>Description: this panel can be added to various GUI componenets where
 * we need Measured Component of Slip Parameters.</p>
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class MeasuredCompPanel extends JPanel  {
  private final static String MEASURED_COMP_PARAM_NAME = "Measured Component";
  private final static String UNKNOWN  = "Unknown";
  private StringParameter measuredCompParam; // measured component pick list

  private ConstrainedStringParameterEditor measuredCompParamEditor;

  public MeasuredCompPanel() {
    this.setLayout(GUI_Utils.gridBagLayout);
    initParamListAndEditor();
    addEditorsToGUI();
  }

  private void initParamListAndEditor() {
    try {
      // measured component
      ArrayList allowedMeasuredComps = getAllowedMeasuredComponents();
      measuredCompParam = new StringParameter(MEASURED_COMP_PARAM_NAME,
                                              allowedMeasuredComps,
                                              (String) allowedMeasuredComps.get(
          0));
      measuredCompParamEditor = new ConstrainedStringParameterEditor(measuredCompParam);
    }catch(Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Set the value for measured component of slip
   *
   * @param value
   */
  public void setMeasuredCompVal(String value) {
    if(value==null) value=this.UNKNOWN;
    measuredCompParam.setValue(value);
    measuredCompParamEditor.refreshParamEditor();
  }

  private void addEditorsToGUI() {
    int yPos=0;
    this.add(measuredCompParamEditor,new GridBagConstraints(0, yPos++, 1, 1, 1.0, 1.0
        , GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));
  }

  /**
   * Get the allowed measured components
   * @return
   */
  private ArrayList getAllowedMeasuredComponents() {
    ArrayList measuredComps = new ArrayList();
    measuredComps.add(UNKNOWN);
    measuredComps.add("Total");
    measuredComps.add("Vertical");
    measuredComps.add("Horizontal,Trace-Parallel");
    measuredComps.add("Horizontal,Trace-NORMAL");
    return measuredComps;
  }

  /**
  * Get the measured component qualitative value
  * If it Unknown or if rake is provided, null is returned
  * @return
  */
 public String getMeasuredComp() {
   String value = (String)this.measuredCompParam.getValue();
   if(value.equalsIgnoreCase(UNKNOWN)) return null;
   return value;
 }
}
