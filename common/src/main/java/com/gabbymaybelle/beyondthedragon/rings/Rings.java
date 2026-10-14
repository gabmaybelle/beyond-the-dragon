package com.gabbymaybelle.beyondthedragon.rings;

import com.gabbymaybelle.beyondthedragon.Config;

/**
 * The Far End rings: the End gets more rewarding the farther you fly from its centre. A ring is
 * decided by the horizontal distance from (0, 0), so it costs one square root per check.
 */
public enum Rings {
	INNER("inner"),
	MIDDLE("middle"),
	OUTER("outer"),
	EDGE("edge");

	private final String key;

	Rings(String key) {
		this.key = key;
	}

	/** The translation key of this ring's title. */
	public String titleKey() {
		return "title.beyond_the_dragon.ring." + key;
	}

	/** The ring at a horizontal position, with the ring starts from the settings. */
	public static Rings at(double x, double z) {
		return at(x, z, Config.ringMiddle, Config.ringOuter, Config.ringEdge);
	}

	/** The ring at a horizontal position, given where the middle, outer and edge rings start. */
	public static Rings at(double x, double z, int middleStart, int outerStart, int edgeStart) {
		double distance = Math.sqrt(x * x + z * z);
		if (distance >= edgeStart) {
			return EDGE;
		}
		if (distance >= outerStart) {
			return OUTER;
		}
		if (distance >= middleStart) {
			return MIDDLE;
		}
		return INNER;
	}
}
