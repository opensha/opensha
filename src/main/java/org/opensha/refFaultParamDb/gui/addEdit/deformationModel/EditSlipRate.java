package org.opensha.refFaultParamDb.gui.addEdit.deformationModel;

import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JOptionPane;

import org.opensha.commons.data.estimate.Estimate;
import org.opensha.commons.param.constraint.impl.EstimateConstraint;
import org.opensha.commons.param.editor.impl.ConstrainedEstimateParameterEditor;
import org.opensha.commons.param.impl.EstimateParameter;
import org.opensha.refFaultParamDb.dao.db.DB_AccessAPI;
import org.opensha.refFaultParamDb.dao.db.DB_ConnectionPool;
import org.opensha.refFaultParamDb.dao.db.DeformationModelDB_DAO;
import org.opensha.refFaultParamDb.gui.infotools.GUI_Utils;
import org.opensha.refFaultParamDb.gui.infotools.SessionInfo;
import org.opensha.refFaultParamDb.vo.EstimateInstances;
/**
 * Edit the slip rate for a fault section in the deformation model
 * 
 * @author vipingupta
 *
 */
public class EditSlipRate extends JFrame implements ActionListener {
	 // SLIP RATE
	  private final static String SLIP_RATE_PARAM_NAME="Slip Rate Estimate";
	  private final static String SLIP_RATE="Slip Rate";
	  public final static String SLIP_RATE_UNITS = "mm/yr";
	  private final static double SLIP_RATE_MIN = Double.NEGATIVE_INFINITY;
	  private final static double SLIP_RATE_MAX = Double.POSITIVE_INFINITY;
	  
	  private EstimateParameter slipRateEstimateParam;
	  private ConstrainedEstimateParameterEditor slipRateEstimateParamEditor;
	  private final static String SLIP_RATE_PARAMS_TITLE = "Slip Rate Params";
	  // update, Cancel Button
	  private JButton okButton = new JButton("OK");
	  private JButton cancelButton = new JButton("Cancel");
	  // deformation model DAO
	  DeformationModelDB_DAO deformationModelDAO;
	  private final static String MSG_UPDATE_SUCCESS = "Slip Rate updated succesfully for fault section in deformation model";
	  private int deformationModelId, faultSectionId;
	  private EstimateInstances slipRateEst;
	  
	  public EditSlipRate(DB_AccessAPI dbConnection, int deformationModelId,
			  int faultSectionId, EstimateInstances slipRateEst) {
		  deformationModelDAO = new DeformationModelDB_DAO(dbConnection);
		  this.deformationModelId = deformationModelId;
		  this.faultSectionId = faultSectionId;
		  this.slipRateEst = slipRateEst;
		  this.getContentPane().setLayout(GUI_Utils.gridBagLayout);
		  setTitle(SLIP_RATE_PARAMS_TITLE);
		  makeSlipRateParameterAndEditor();
		  addToGUI();
		  if(SessionInfo.getContributor()==null) this.okButton.setEnabled(false);
		  pack();
		  show();
		  
	  }
	  
	  public void actionPerformed(ActionEvent event) {
		  Object source = event.getSource();
		  if(source == this.cancelButton) this.dispose();
		  else if(source == this.okButton) { // update slip rate in database
			  try {
				  // update slip rate in the database
				  deformationModelDAO.updateSlipRate(deformationModelId, faultSectionId,  getSlipRateEstimate());
				  JOptionPane.showMessageDialog(this, this.MSG_UPDATE_SUCCESS);
				  this.dispose();
			  }catch(Exception e) {
				  JOptionPane.showMessageDialog(this, e.getMessage());
			  }
		  }
	  }
	  
	  /**
	   * Add GUI components to JFrame
	   *
	   */
	  private void addToGUI() {
		  // slip rate parameter editor
		  this.getContentPane().add(slipRateEstimateParamEditor,
		             new GridBagConstraints(0, 0, 2, 1, 1.0, 1.0
		                                    , GridBagConstraints.CENTER,
		                                    GridBagConstraints.BOTH,
		                                    new Insets(0, 0, 0, 0), 0, 0));
		  // cancel button
		  this.getContentPane().add(this.cancelButton,
		             new GridBagConstraints(0, 1, 1, 1, 1.0, 0.0
		                                    , GridBagConstraints.CENTER,
		                                    GridBagConstraints.NONE,
		                                    new Insets(0, 0, 0, 0), 0, 0));
		  
		  //ok button
		  this.getContentPane().add(this.okButton,
		             new GridBagConstraints(1, 1, 1, 1, 1.0, 0.0
		                                    , GridBagConstraints.CENTER,
		                                    GridBagConstraints.NONE,
		                                    new Insets(0, 0, 0, 0), 0, 0));
		  
		  this.okButton.addActionListener(this);
		  this.cancelButton.addActionListener(this);
	  }
	  
	  /**
	   * Make slip rate parameter and editor
	   *
	   */
	  private void makeSlipRateParameterAndEditor() {
		  // slip rate estimate
		   ArrayList allowedEstimates = EstimateConstraint.createConstraintForPositiveDoubleValues();
		   this.slipRateEstimateParam = new EstimateParameter(SLIP_RATE_PARAM_NAME,
		        SLIP_RATE_UNITS, SLIP_RATE_MIN, SLIP_RATE_MAX, allowedEstimates);
		   if(slipRateEst!=null && slipRateEst.getEstimate()!=null) slipRateEstimateParam.setValue(slipRateEst.getEstimate());
		    slipRateEstimateParamEditor = new ConstrainedEstimateParameterEditor(slipRateEstimateParam, true);
	  }
	  
	  /**
	    * Get the slip rate estimate along with units
	    * @return
	    */
	   private  EstimateInstances getSlipRateEstimate() {
	     this.slipRateEstimateParamEditor.setEstimateInParameter();
	     return new EstimateInstances((Estimate)this.slipRateEstimateParam.getValue(),
	                                  this.SLIP_RATE_UNITS);
	   }

}
