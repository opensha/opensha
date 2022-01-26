package org.opensha.commons.util.cpt;

import java.awt.Color;
import java.io.Serializable;

public interface Blender extends Serializable {
	public Color blend(Color small, Color big, float bias);
}
