package org.opensha.sha.calc.IM_EventSet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.opensha.sha.calc.IM_EventSet.outputImpl.HAZ01ASegment;
import org.opensha.sha.earthquake.AbstractERF;
import org.opensha.sha.earthquake.ERF;
import org.opensha.sha.earthquake.ProbEqkSource;

public class HAZ01A_FakeERF extends AbstractERF {
	
	ArrayList<ProbEqkSource> sources;
	
	public ERF erf;
	private String haz01aFileName;
	private Set<Integer> includedSourceIDs;

    /**
     * No filtering
     * @param erf
     */
    public HAZ01A_FakeERF(ERF erf) {
		this.erf = erf;
	}

    /**
     * Only include ruptures from the given HAZ01A file
     * @param erf
     * @param haz01aFileName
     */
	public HAZ01A_FakeERF(ERF erf, String haz01aFileName) {
		this.erf = erf;
		this.haz01aFileName = haz01aFileName;
	}

	@Override
	public int getNumSources() {
		return sources.size();
	}

	@Override
	public ProbEqkSource getSource(int source) {
		return sources.get(source);
	}

	@Override
	public List getSourceList() {
		return erf.getSourceList();
	}

	public String getName() {
		return erf.getName() + " (HAZ01A Test Stub!)";
	}

	public void updateForecast() {
		sources = new ArrayList<ProbEqkSource>();
		erf.updateForecast();
		
		// Load the HAZ01A file to see which source IDs are included
		if (haz01aFileName != null) {
			try {
				loadIncludedSourceIDs();
			} catch (IOException e) {
				throw new RuntimeException("Error loading HAZ01A file to determine included sources", e);
			}
		}
		
		for (int i=0; i<erf.getNumSources(); i++) {
			ProbEqkSource source = erf.getSource(i);
			
			// Only include sources that are in the HAZ01A file
			if (includedSourceIDs != null && !includedSourceIDs.contains(i)) {
				// Skip this source - it wasn't included in the HAZ01A file
				continue;
			}
			
			sources.add(new HAZ01A_FakeSource(source, i));
		}
	}
	
	private void loadIncludedSourceIDs() throws IOException {
		includedSourceIDs = new HashSet<Integer>();
		
		// Load the HAZ01A segment to see what source IDs are present
		ArrayList<HAZ01ASegment> segments = HAZ01ASegment.loadHAZ01A(haz01aFileName);
		if (segments.isEmpty()) {
			throw new RuntimeException("No segments found in HAZ01A file: " + haz01aFileName);
		}
		
		// Use the first segment (there's typically only one in these files)
		HAZ01ASegment segment = segments.get(0);
		
		// Extract all unique source IDs from the segment
		for (int i = 0; i < segment.size(); i++) {
			includedSourceIDs.add(segment.getSourceID(i));
		}
		
		System.out.println("HAZ01A file contains " + includedSourceIDs.size() + 
				" unique source IDs out of " + erf.getNumSources() + " total sources");
	}

}
