package org.opensha.refFaultParamDb.gui.addEdit.connections;

import java.util.Map;

import javax.swing.table.AbstractTableModel;

import org.opensha.refFaultParamDb.vo.FaultSectionConnection;
import org.opensha.refFaultParamDb.vo.FaultSectionConnectionList;
import org.opensha.refFaultParamDb.vo.FaultSectionData;

public class ConnectionsTableModel extends AbstractTableModel {
	
	private FaultSectionConnectionList connections;
	private Map<Integer, FaultSectionData> datas;
	
	private static String[] columnNames =
		{ "ID 1", "Name 1", "ID 2", "Name 2", "Horz. Dist (KM)" };
	
	public ConnectionsTableModel(FaultSectionConnectionList connections, Map<Integer, FaultSectionData> datas) {
		setConnections(connections);
		setFaultSectionData(datas);
	}
	
	public void setConnections(FaultSectionConnectionList connections) {
		this.connections = connections;
		
		fireTableDataChanged();
	}
	
	public void setFaultSectionData(Map<Integer, FaultSectionData> datas) {
		this.datas = datas;
		
		fireTableDataChanged();
	}

	@Override
	public int getRowCount() {
		return connections == null ? 0 : connections.size();
	}

	@Override
	public int getColumnCount() {
		return columnNames.length;
	}

	@Override
	public String getColumnName(int column) {
		return columnNames[column];
	}

	@Override
	public Object getValueAt(int rowIndex, int columnIndex) {
		FaultSectionConnection conn = connections.get(rowIndex);
		
		switch (columnIndex) {
		case 0:
			// id 1
			return conn.getId1();
		case 1:
			// name 1
			if (datas == null)
				return null;
			FaultSectionData data1 = datas.get(conn.getId1());
			if (data1 == null)
				return null;
			return data1.getName();
		case 2:
			// id 2
			return conn.getId2();
		case 3:
			// name 2
			if (datas == null)
				return null;
			FaultSectionData data2 = datas.get(conn.getId2());
			if (data2 == null)
				return null;
			return data2.getName();
		case 4:
			// dist
			return conn.calcDistance();
		default:
			throw new IllegalStateException("unkown column");
		}
	}

}
