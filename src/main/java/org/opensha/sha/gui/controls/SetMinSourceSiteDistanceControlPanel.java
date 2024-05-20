package org.opensha.sha.gui.controls;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.JFrame;

import org.opensha.commons.param.editor.ParameterEditor;
import org.opensha.commons.param.editor.impl.DoubleParameterEditor;
import org.opensha.commons.param.impl.DoubleParameter;
import org.opensha.sha.calc.HazardCurveCalculator;
import org.opensha.sha.calc.params.MaxDistanceParam;

/**
 * <p>Title: SetMinSourceSiteDistanceControlPanel </p>
 * <p>Description: This provides the user with a input screen so that
 * user can enter distance between source and site beyond which source
 * will not be considered in hazard calculation</p>
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author unascribed
 * @version 1.0
 */

public class SetMinSourceSiteDistanceControlPanel extends JFrame {

  // distance Parameter
   private MaxDistanceParam distanceParam =
       new MaxDistanceParam();
   // double param editor
  private ParameterEditor<Double> distanceEditor=distanceParam.getEditor();
  GridBagLayout gridBagLayout1 = new GridBagLayout();

  /**
   * default constructor
   */
  public SetMinSourceSiteDistanceControlPanel(Component parent) {
    try {
      jbInit();
      // show the window at center of the parent component
     this.setLocation(parent.getX()+parent.getWidth()/2,
                     parent.getY()+parent.getHeight()/2);
    }
    catch(Exception e) {
      e.printStackTrace();
    }

  }
  private void jbInit() throws Exception {
    this.getContentPane().setLayout(gridBagLayout1);
    // add the distance editor to the window
    distanceEditor.setParameter(distanceParam);
    this.getContentPane().add(distanceEditor.getComponent(),  new GridBagConstraints(0, 0, 1, 1, 1.0, 1.0
            ,GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(5, 5, 8, 7), 0, 0));
  }

  /**
   * return the distanace value entered by the user
   * @return : Distance selected by the user
   */
  public double getDistance() {
    return ((Double)distanceParam.getValue()).doubleValue();
  }

  /**
   * set the value by hand in this panel
   */
  public void setDistance(double distance) {
    distanceParam.setValue(Double.valueOf(distance));
    distanceEditor.refreshParamEditor();
  }
}

