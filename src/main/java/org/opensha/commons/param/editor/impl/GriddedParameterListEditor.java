package org.opensha.commons.param.editor.impl;

import java.awt.GridLayout;

import javax.swing.JPanel;

import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;

import com.google.common.base.Preconditions;

/**
 * This is a Gridded parameter list where each paremeter is given the exact same
 * amount of space in an even grid. You can speicfy 0 for either rows or columns
 * to allow it to grow infinitely in one direction (you can't specify zero for both).
 * 
 * @author Kevin
 *
 */
public class GriddedParameterListEditor extends JPanel {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	private ParameterList paramList;

	/**
	 * Creates a new empty gridded parameter editor with 2 columns
	 */
	public GriddedParameterListEditor() {
		this(null);
	}
	
	/**
	 * Creates a new gridded with 2 columns parameter editor for the given parameter list
	 * 
	 * @param paramList
	 */
	public GriddedParameterListEditor(ParameterList paramList) {
		this(paramList, 2);
	}
	
	/**
	 * Creates a new gridded parameter editor with the given number of columns and an infinite
	 * amount of rows (as needed) for the given parameter list 
	 * 
	 * @param paramList
	 * @param cols
	 */
	public GriddedParameterListEditor(ParameterList paramList, int cols) {
		this (paramList, 0, cols);
	}
	
	/**
	 * Creates a new gridded parameter editor with the given number of columns and rows
	 * for the given parameter list
	 * 
	 * @param paramList
	 * @param rows
	 * @param cols
	 */
	public GriddedParameterListEditor(ParameterList paramList, int rows, int cols) {
		if (paramList != null) {
			int numParams = paramList.size();
			if (numParams == 0)
				return;
			if (numParams < cols)
				cols = numParams;
		}
		
		Preconditions.checkState(rows > 0 || cols > 0,
				"You must give a non zero value for rows or columns!");
		
		this.setLayout(new GridLayout(rows, cols));
		
		setParameterList(paramList);
	}
	
	public void setParameterList(ParameterList paramList) {
		this.removeAll();
		this.paramList = paramList;
		if (paramList != null) {
			for (Parameter<?> param : paramList) {
//				JPanel tempPanel = new JPanel();
//				tempPanel.add(param.getEditor().getComponent());
//				this.add(tempPanel);
				this.add(param.getEditor().getComponent());
			}
		}
		this.validate();
	}
	
	public ParameterList getParameterList() {
		return paramList;
	}
}
