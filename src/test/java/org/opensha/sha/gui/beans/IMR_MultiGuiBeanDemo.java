package org.opensha.sha.gui.beans;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;

import org.opensha.commons.exceptions.ParameterException;
import org.opensha.commons.param.Parameter;
import org.opensha.sha.earthquake.rupForecastImpl.Frankel96.Frankel96_AdjustableEqkRupForecast;
import org.opensha.sha.gui.beans.event.IMTChangeEvent;
import org.opensha.sha.gui.beans.event.IMTChangeListener;
import org.opensha.sha.imr.AttenRelRef;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.attenRelImpl.BA_2008_AttenRel;
import org.opensha.sha.imr.attenRelImpl.CB_2008_AttenRel;
import org.opensha.sha.imr.attenRelImpl.ShakeMap_2003_AttenRel;
import org.opensha.sha.imr.param.IntensityMeasureParams.MMI_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGA_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.PeriodParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_Param;

public class IMR_MultiGuiBeanDemo extends JPanel implements ActionListener {
	
	private JButton noERFButton = new JButton("No ERF");
	private JButton frankelERFButton = new JButton("Frankel 96");
	private JButton multiERFButton = new JButton("Multi-Tect.");
	
	private JButton noIMTButton = new JButton("No IMT");
	private JButton pgaButton = new JButton("PGA");
	private JButton mmiButton = new JButton("MMI");
	private JButton sa10Button = new JButton("SA 1s");
	
	private Frankel96_AdjustableEqkRupForecast frankel = new Frankel96_AdjustableEqkRupForecast();
	private MultiERFDummy multi = new MultiERFDummy();
	
	List<? extends ScalarIMR> imrs;
	
	private IMR_MultiGuiBean bean;
	
	private IMT_NewGuiBean imtBean;
	
	public IMR_MultiGuiBeanDemo() {
		super(new BorderLayout());
		
		//AttenuationRelationshipsInstance attenRelInst = new AttenuationRelationshipsInstance();
		//imrs = attenRelInst.createIMRClassInstance(null);
		imrs = AttenRelRef.instanceList(null, true);
		
		for (ScalarIMR imr : imrs)
			imr.setParamDefaults();
		
		bean = new IMR_MultiGuiBean(imrs);
		
		imtBean = new IMT_NewGuiBean(imrs);
		imtBean.addIMTChangeListener(bean);
		
		this.add(imtBean, BorderLayout.NORTH);
		this.add(bean, BorderLayout.CENTER);
		
		JPanel buttonPanels = new JPanel();
		buttonPanels.setLayout(new BoxLayout(buttonPanels, BoxLayout.Y_AXIS));
		
		JPanel erfButtons = new JPanel();
		erfButtons.setLayout(new BoxLayout(erfButtons, BoxLayout.X_AXIS));
		erfButtons.add(noERFButton);
		erfButtons.add(frankelERFButton);
		erfButtons.add(multiERFButton);
		
		noERFButton.addActionListener(this);
		frankelERFButton.addActionListener(this);
		multiERFButton.addActionListener(this);
		
		buttonPanels.add(erfButtons);
		
		JPanel imtButtons = new JPanel();
		imtButtons.setLayout(new BoxLayout(imtButtons, BoxLayout.X_AXIS));
		imtButtons.add(noIMTButton);
		imtButtons.add(pgaButton);
		imtButtons.add(mmiButton);
		imtButtons.add(sa10Button);
		
		noIMTButton.addActionListener(this);
		pgaButton.addActionListener(this);
		mmiButton.addActionListener(this);
		sa10Button.addActionListener(this);
		
		buttonPanels.add(imtButtons);
		
		this.add(buttonPanels, BorderLayout.SOUTH);
		
		JFrame window = new JFrame();
		window.setSize(400, 900);
		
		window.setContentPane(this);
		
		window.setVisible(true);
		window.setLocationRelativeTo(null);
		window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		new IMR_MultiGuiBeanDemo();
	}

	public void actionPerformed(ActionEvent e) {
		if (e.getSource() == noERFButton) {
			bean.setTectonicRegions(null);
		} else if (e.getSource() == frankelERFButton) {
			bean.setTectonicRegions(frankel.getIncludedTectonicRegionTypes());
		} else if (e.getSource() == multiERFButton) {
			bean.setTectonicRegions(multi.getIncludedTectonicRegionTypes());
		} else if (e.getSource() == noIMTButton) {
			Parameter<Double> imt = null;
			bean.setIMT(imt);
		} else if (e.getSource() == pgaButton) {
			imtBean.getParameterList().getParameter(IMT_NewGuiBean.IMT_PARAM_NAME).setValue(PGA_Param.NAME);
//			ParameterAPI<Double> imt = getIMT(PGA_Param.NAME, -1);
//			bean.setIMT(imt);
		} else if (e.getSource() == mmiButton) {
			imtBean.getParameterList().getParameter(IMT_NewGuiBean.IMT_PARAM_NAME).setValue(MMI_Param.NAME);
//			ParameterAPI<Double> imt = getIMT(ShakeMap_2003_AttenRel.MMI_NAME, -1);;
//			bean.setIMT(imt);
		} else if (e.getSource() == sa10Button) {
			imtBean.getParameterList().getParameter(IMT_NewGuiBean.IMT_PARAM_NAME).setValue(SA_Param.NAME);
			imtBean.getParameterList().getParameter(PeriodParam.NAME).setValue(Double.valueOf(1.0));
			imtBean.refreshParamEditor();
//			ParameterAPI<Double> imt = getIMT(SA_Param.NAME, 1.0);
//			bean.setIMT(imt);
		}
	}
	
	public Parameter<Double> getIMT(String name, double period) {
		for (ScalarIMR imr : imrs) {
			try {
				imr.setIntensityMeasure(name);
				Parameter<Double> imt = (Parameter<Double>) imr.getIntensityMeasure();
				if (period > 0) {
					Parameter<Double> periodParam = imt.getIndependentParameter(PeriodParam.NAME);
					periodParam.setValue(period);
				}
				return imt;
			} catch (Exception e) {
				// do nothing
			}
		}
		return null;
	}

}

