package org.opensha.refFaultParamDb.gui.view;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.JFrame;
import javax.swing.JPanel;

import org.opensha.commons.data.estimate.Estimate;
import org.opensha.refFaultParamDb.dao.db.DB_AccessAPI;
import org.opensha.refFaultParamDb.dao.db.DB_ConnectionPool;
import org.opensha.refFaultParamDb.dao.db.DeformationModelDB_DAO;
import org.opensha.refFaultParamDb.gui.infotools.GUI_Utils;
import org.opensha.refFaultParamDb.gui.infotools.InfoLabel;
import org.opensha.refFaultParamDb.vo.EstimateInstances;


/**
 * This class allows the user to view Slip Rate and aseismic slip factor for 
 * fault sections within a deformation model.
 * This class is presently used to view info about deformation model in SCEC-VDO
 * 
 * @author vipingupta
 *
 */
public class ViewDeformationModel extends JFrame {
	private DeformationModelDB_DAO deformationModelDAO;
	private final static String SLIP_RATE = "Slip Rate (mm/year)";
	private final static String ASEISMIC_SLIP_FACTOR = "Aseismic Slip Factor";

	/**
	 * View the Slip Rate and Aseismic Slip Factor for fault section within a 
	 * deformation model. 
	 * 
	 * @param deformationModelId
	 * @param faultSectionId
	 */
	public ViewDeformationModel(DB_AccessAPI dbConnection, int deformationModelId, int faultSectionId) {
		deformationModelDAO = new DeformationModelDB_DAO(dbConnection);
		
		this.getContentPane().setLayout(new GridBagLayout());
		EstimateInstances asesmicSlipEstInstance  = deformationModelDAO.getAseismicSlipEstimate(deformationModelId, faultSectionId);
		EstimateInstances slipRateEstInstance  = deformationModelDAO.getSlipRateEstimate(deformationModelId, faultSectionId);
		Estimate slipRateEstimate = null, aseismicSlipEstimate=null;
		if(slipRateEstInstance!=null) slipRateEstimate = slipRateEstInstance.getEstimate();
		if(asesmicSlipEstInstance!=null) aseismicSlipEstimate = asesmicSlipEstInstance.getEstimate();
		// view slip rate info
		JPanel slipRatePanel = GUI_Utils.getPanel(new InfoLabel(slipRateEstimate, SLIP_RATE, ""), SLIP_RATE);
		getContentPane().add(slipRatePanel, new GridBagConstraints(0, 0, 1, 1, 1.0, 1.0
		        , GridBagConstraints.CENTER, GridBagConstraints.BOTH,
		        new Insets(0, 0, 0, 0), 0, 0));
		// view aseismic slip factor
		JPanel aseicmicSlipPanel = GUI_Utils.getPanel(new InfoLabel(aseismicSlipEstimate, ASEISMIC_SLIP_FACTOR, ""), ASEISMIC_SLIP_FACTOR);
		getContentPane().add(aseicmicSlipPanel, new GridBagConstraints(0, 1, 1, 1, 1.0, 1.0
		        , GridBagConstraints.CENTER, GridBagConstraints.BOTH,
		        new Insets(0, 0, 0, 0), 0, 0));
		this.pack();
		this.show();
	}

	
}
