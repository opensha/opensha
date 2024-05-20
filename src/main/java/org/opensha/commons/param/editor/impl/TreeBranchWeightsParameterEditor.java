package org.opensha.commons.param.editor.impl;


import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.Iterator;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JOptionPane;

import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.impl.ParameterListParameter;
import org.opensha.commons.param.impl.TreeBranchWeightsParameter;





/**
 * <p>Title: TreeBranchWeightsParameterEditor</p>
 * <p>Description: This class is more like a parameterList consisting of only
 * Double Parameters and considering this parameterList as a single Parameter.
 * This parameter editor will show up as the button on th GUI interface and
 * when the user punches the button, all the parameters will pop up in a seperate window
 * showing all the double parameters contained within this parameter.</p>
 * @author : Ned Field, Nitin Gupta and Vipin Gupta
 * @created : Feb 01,2003
 * @version 1.0
 */

public class TreeBranchWeightsParameterEditor extends ParameterListParameterEditor implements
ActionListener, ItemListener{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	/** Class name for debugging. */
	protected final static String C = "TreeBranchWeightsParameterEditor";
	// convenience parameter to set All weights to 0 or 1
	private final static String AUTO_WEIGHTS_PARAM_NAME= "Set All";
	private final static String EQUAL_WEIGHTS = "Equal Weights";
	private final static String ZERO_WEIGHT = "Zero Weight";
	private  JComboBox autoWeightComboBox;


	public TreeBranchWeightsParameterEditor(Parameter<ParameterList> model){
		super(model, true);
	}

	/**
	 * This function is called when the user click for the ParameterListParameterEditor Button
	 *
	 * @param ae
	 */
	public void actionPerformed(ActionEvent ae ) {
		frame = new JDialog();
		frame.setModal(true);
		frame.setSize(300,400);
		frame.setTitle(getParameter().getName());
		frame.getContentPane().setLayout(new GridBagLayout());
		frame.getContentPane().add(editor,new GridBagConstraints(0, 0, 1, 1, 1.0, 1.0
				,GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(4, 4, 4, 4), 0, 0));

		frame.getContentPane().add(editor,new GridBagConstraints(0, 0, 1, 1, 1.0, 1.0
				,GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(4, 4, 4, 4), 0, 0));
		makeAutoWeightsParamAndEditor();
		frame.getContentPane().add(this.autoWeightComboBox, new GridBagConstraints(0, 1, 1, 1, 1.0, 0.0
				,GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(4, 4, 4, 4), 0, 0));


		//Adding Button to update the forecast
		JButton button = new JButton();
		button.setText("Update "+getParameter().getName());
		button.setForeground(new Color(80,80,133));
		button.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(ActionEvent e) {
				button_actionPerformed(e);
			}
		});
		frame.getContentPane().add(button,new GridBagConstraints(0, 2, 1, 1, 0.0,0.0
				,GridBagConstraints.CENTER, GridBagConstraints.NONE, new Insets(4, 4, 4, 4), 0, 0));
		frame.setVisible(true);
		frame.pack();
	}


	private void makeAutoWeightsParamAndEditor() {
		autoWeightComboBox = new JComboBox();
		autoWeightComboBox.setMaximumRowCount(32);
		autoWeightComboBox.addItem(AUTO_WEIGHTS_PARAM_NAME);
		autoWeightComboBox.addItem(EQUAL_WEIGHTS);
		autoWeightComboBox.addItem(ZERO_WEIGHT);
		autoWeightComboBox.addItemListener(this);

	}

	/**
	 * This function is called when user punches the button to update the ParameterList Parameter
	 * @param e
	 */
	protected void button_actionPerformed(ActionEvent e) {
		ParameterList paramList = editor.getParameterList();
		boolean doSumToOne =((TreeBranchWeightsParameter)getParameter()).doWeightsSumToOne(paramList);
		if(doSumToOne){
			if(parameterChangeFlag){
				getParameter().setValue(paramList);
				parameterChangeFlag = false;
			}
			frame.dispose();
		}
		else{
			JOptionPane.showMessageDialog(frame,"Parameters Value should sum to One",
					"Incorrect Input",JOptionPane.ERROR_MESSAGE);
		}
	}

	/**
	 * This is called when user selects in Auto weight pick list
	 */
	public void itemStateChanged(ItemEvent event) {
		Object source = event.getSource();
		if(source==this.autoWeightComboBox) {
			setWeightsAuto();
		}

	}

	/**
	 * Set weights automatically
	 *
	 */
	private void setWeightsAuto() {
		String weightsAutoOption = (String)autoWeightComboBox.getSelectedItem();
		double weight=0.0;
		ParameterList paramList  =  (ParameterList) getParameter().getValue();
		if(weightsAutoOption==EQUAL_WEIGHTS)  { // equalize the weights
			int numParams=0;
			Iterator it = paramList.getParametersIterator();
			while(it.hasNext()) {
				it.next();
				++numParams;
			}
			weight = 1.0/numParams;
		}
		else if (weightsAutoOption==ZERO_WEIGHT) weight = 0.0; // set all weights to 0
		else return;
		Iterator it = paramList.getParametersIterator();
		while(it.hasNext()) {
			Parameter param = (Parameter)it.next();
			param.setValue(Double.valueOf(weight));
		}
		autoWeightComboBox.setSelectedItem(this.AUTO_WEIGHTS_PARAM_NAME);
		this.editor.refreshParamEditor();
	}

	@Override
	public boolean isParameterSupported(Parameter<ParameterList> param) {
		if (param == null)
			return false;
		
		if (!(param instanceof TreeBranchWeightsParameter))
			return false;
		
		return true;
	}
}
