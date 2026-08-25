package org.opensha.commons.data.sampling.optimization;

import org.opensha.commons.data.sampling.scoring.ProjectionScore;

interface ProjectionScoreState {
	ProjectionScore score();
}
