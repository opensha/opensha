package org.opensha.sha.simulators.iden;

import java.util.List;

import org.opensha.sha.simulators.SimulatorEvent;
import org.opensha.sha.simulators.utils.RSQSimSubSectionMapper;
import org.opensha.sha.simulators.utils.RSQSimSubSectionMapper.SubSectionMapping;

public class MinNumMappedSubSectsIden extends AbstractRuptureIdentifier {
	
	private int minSubSects;
	private RSQSimSubSectionMapper mapper;

	public MinNumMappedSubSectsIden(int minSubSects, RSQSimSubSectionMapper mapper) {
		this.minSubSects = minSubSects;
		this.mapper = mapper;
	}

	@Override
	public boolean isMatch(SimulatorEvent event) {
		List<List<SubSectionMapping>> mapped = mapper.getFilteredSubSectionMappings(event);
		int count = 0;
		if (mapped != null)
			for (List<SubSectionMapping> maps : mapped)
				count += maps.size();
		return count >= minSubSects;
	}

	@Override
	public String getName() {
		return ">="+minSubSects+" mapped subsections";
	}

}
