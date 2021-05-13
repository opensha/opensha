package org.opensha.commons.gui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

import org.opensha.commons.data.CSVFile;

public class CSVTable extends JTable implements MouseListener, ActionListener {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	private boolean editMode;
	
	private DefaultTableModel model;
	
	private JPopupMenu menu;
	private JMenuItem deleteColItem;
	private JMenuItem deleteRowItem;
	
	private JPopupMenu tablePopup;
	private int lastClickedCol = -1;
	private int lastClickedRow = -1;
	
	public CSVTable() {
		this(null, true);
	}
	
	public CSVTable(CSVFile<?> csv, boolean firstLineIsHeader) {
		model = new DefaultTableModel() {
			public boolean isCellEditable(int row, int column) {
				return editMode && super.isCellEditable(row, column);
			}
		};
		super.setModel(model);
		
		addMouseListener(this);
		
		setCSV(csv, firstLineIsHeader);
	}
	
	public void setEditingEnabled(boolean editMode) {
		this.editMode = editMode;
		this.getTableHeader().setReorderingAllowed(editMode);
	}
	
	public void setCSV(CSVFile<?> csv, boolean firstLineIsHeader) {
		if (csv == null) {
			model.setDataVector(new Object[0][0], null);
			return;
		}
		
		int numRows = csv.getNumRows();
		int startRow = 0;
		
		Object[] header = null;
		if (firstLineIsHeader) {
			numRows--;
			startRow++;
			header = csv.getLine(0).toArray();
		}
		
		Object[][] data = new Object[numRows][];
		for (int i=0; i<numRows; i++) {
			int csvI = i;
			if (firstLineIsHeader)
				csvI++;
			data[i] = csv.getLine(csvI).toArray();
		}
		
		model.setDataVector(data, header);
	}
	
	public CSVFile<String> buildCSVFromTable(boolean includeHeader) {
		CSVFile<String> csv = new CSVFile<String>(true);
		
		if (includeHeader) {
			ArrayList<String> header = new ArrayList<String>();
			for (int i=0; i<model.getColumnCount(); i++)
				header.add(model.getColumnName(i));
			csv.addLine(header);
		}
		
		for (int i=0; i<model.getRowCount(); i++) {
			ArrayList<String> vals = new ArrayList<String>();
			for (int j=0; j<model.getColumnCount(); j++) {
				Object val = model.getValueAt(i, j);
				if (val == null)
					vals.add(null);
				else
					vals.add(val.toString());
			}
			csv.addLine(vals);
		}
		
		return csv;
	}
	
	private JPopupMenu getTablePopup() {
		if (tablePopup == null) {
			tablePopup = new JPopupMenu();
			deleteColItem = new JMenuItem("Delete Column");
			deleteColItem.addActionListener(this);
			deleteRowItem = new JMenuItem("Delete Row");
			deleteRowItem.addActionListener(this);
			tablePopup.add(deleteRowItem);
			tablePopup.add(deleteColItem);
		}
		return tablePopup;
	}
	
	private void checkPop(MouseEvent e) {
		if (!editMode)
			return;
//		System.out.println("Mouse event!!!");
		int r = rowAtPoint(e.getPoint());
		if (r >= 0 && r < getRowCount()) {
			setRowSelectionInterval(r, r);
		} else {
			clearSelection();
		}

		int rowindex = getSelectedRow();
//		System.out.println("Row index: "+rowindex);
		if (rowindex < 0)
			return;
//		System.out.println("Trigger? "+e.isPopupTrigger());
		if (e.isPopupTrigger()) {
//			System.out.println("pop that UP!");
			JPopupMenu popup = getTablePopup();
			lastClickedCol = columnAtPoint(e.getPoint());
			lastClickedRow = rowindex;
			popup.show(e.getComponent(), e.getX(), e.getY());
		}
	}

	@Override
	public void mouseClicked(MouseEvent e) {}

	@Override
	public void mousePressed(MouseEvent e) {
		checkPop(e);
	}

	@Override
	public void mouseReleased(MouseEvent e) {
		checkPop(e);
	}

	@Override
	public void mouseEntered(MouseEvent e) {}

	@Override
	public void mouseExited(MouseEvent e) {}

	@Override
	public void actionPerformed(ActionEvent e) {
		if (e.getSource() == deleteColItem) {
			if (lastClickedCol >= 0) {
				CSVFile<String> newCSV = buildCSVFromTable(true);
				newCSV.removeColumn(lastClickedCol);
				setCSV(newCSV, true);
				
				lastClickedCol = -1;
			}
		} else if (e.getSource() == deleteRowItem) {
			if (lastClickedCol >= 0) {
				CSVFile<String> newCSV = buildCSVFromTable(true);
				newCSV.removeLine(lastClickedRow);
				setCSV(newCSV, true);
				
				lastClickedCol = -1;
			}
		}
	}
}
