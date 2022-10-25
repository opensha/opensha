package org.opensha.refFaultParamDb.gui.addEdit.faultModel;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;

import org.opensha.refFaultParamDb.dao.db.DB_AccessAPI;
import org.opensha.refFaultParamDb.gui.view.ViewFaultSection;


/**
 * 
 *  this class makes the JTable to view Fault sections within a FaultModel
 * @author vipingupta
 *
 */
public class FaultModelTable extends JTable{
	/**
	 * @param dm
	 */
	public FaultModelTable(DB_AccessAPI dbConnection, TableModel dm) {
		super(dm);
		getTableHeader().setReorderingAllowed(false);
		getColumnModel().getColumn(1).setCellRenderer(new ButtonRenderer());
		addMouseListener(new MouseListener(dbConnection, this));
		// set width of first column 
		TableColumn col1 = getColumnModel().getColumn(0);
		col1.setPreferredWidth(100);
        //col1.setMinWidth(26);
        col1.setMaxWidth(100);
        // set width of second column
        TableColumn col2 = getColumnModel().getColumn(1);
		col2.setPreferredWidth(100);
        //col2.setMinWidth(26);
        col2.setMaxWidth(100);
	}

	
}


/**
 * It handles the clicking whenever user clicks on JTable
 * 
 * @author vipingupta
 *
 */
class MouseListener extends MouseAdapter {
	private JTable table;
	private ViewFaultSection viewFaultSection ;
	private JFrame frame;
	
	private DB_AccessAPI dbConnection;
	
	public MouseListener(DB_AccessAPI dbConnection, JTable table) {
		this.dbConnection = dbConnection;
		this.table = table;
	}
	
	public void mouseClicked(MouseEvent event) {
		//System.out.println("Mouse clicked");
		Point p = event.getPoint();
        int row = table.rowAtPoint(p);
        int column = table.columnAtPoint(p); // This is the view column!
        if(column!=1) return;
        if(viewFaultSection==null) 
        		viewFaultSection = new ViewFaultSection(dbConnection);
        if(frame==null || !frame.isShowing()) {
        		frame = new JFrame();
        		frame.getContentPane().setLayout(new BorderLayout());
        		frame.getContentPane().add(viewFaultSection, BorderLayout.CENTER);
        		frame.pack();
        		frame.show();
        } 
        viewFaultSection.setSelectedFaultSectionNameId((String)table.getModel().getValueAt(row, column));
	}

}	

class ButtonRenderer extends JButton implements TableCellRenderer {

	  public ButtonRenderer() {
	    setOpaque(true);
	  }

	  public Component getTableCellRendererComponent(JTable table, Object value,
	      boolean isSelected, boolean hasFocus, int row, int column) {
	    setText("Info");
	    return this;
	  }
	}

