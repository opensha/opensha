package org.opensha.refFaultParamDb.vo;

import java.util.ArrayList;
import java.util.Collection;

import org.opensha.commons.geo.Location;

import com.google.common.collect.Lists;

public class FaultSectionConnectionList extends
		ArrayList<FaultSectionConnection> {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * Gives all connections in the list that involve this section.
	 * 
	 * @param id
	 * @return
	 */
	public FaultSectionConnectionList getConnectionsForSection(int id) {
		FaultSectionConnectionList conns = new FaultSectionConnectionList();
		for (FaultSectionConnection conn : this) {
			if (conn.involvesSection(id))
				conns.add(conn);
		}
		return conns;
	}
	
	/**
	 * Gives all connections in the list that only involve sections in the given list.
	 * Useful for getting all connections within a given fault model.
	 * 
	 * @param ids
	 * @return
	 */
	public FaultSectionConnectionList getConnectionsForSections(Collection<Integer> ids) {
		FaultSectionConnectionList conns = new FaultSectionConnectionList();
		for (FaultSectionConnection conn : this) {
			if (ids.contains(conn.getId1()) && ids.contains(conn.getId2()))
				conns.add(conn);
		}
		return conns;
	}
	
	/**
	 * Gives all connections involving the given section at the given location.
	 * 
	 * @param id
	 * @param loc
	 * @return
	 */
	public FaultSectionConnectionList getConnectionsForLocation(int id, Location loc) {
		FaultSectionConnectionList conns = new FaultSectionConnectionList();
		
		for (FaultSectionConnection conn : this) {
			if (conn.involvesSectionAtLocation(id, loc))
				conns.add(conn);
		}
		
		return conns;
	}
	
	/**
	 * Counts the number of connections involving the given section at the given location.
	 * Faster than <code>getConnectionsForLocation(id, loc).size()</code> because no new
	 * lists are constructed.
	 * 
	 * @param id
	 * @param loc
	 * @return
	 */
	public int countConnectionsForLocation(int id, Location loc) {
		int cnt = 0;
		
		for (FaultSectionConnection conn : this) {
			if (conn.involvesSectionAtLocation(id, loc))
				cnt++;
		}
		
		return cnt;
	}
	
	public boolean containsConnectionBetween(int id1, int id2) {
		for (FaultSectionConnection conn : this) {
			if (conn.involvesSection(id1) && conn.involvesSection(id2))
				return true;
		}
		return false;
	}

}
