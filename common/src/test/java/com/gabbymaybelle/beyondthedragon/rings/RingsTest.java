package com.gabbymaybelle.beyondthedragon.rings;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RingsTest {
	@Test
	void ringsStartWhereTheSettingsSay() {
		assertEquals(Rings.INNER, Rings.at(0, 0, 2000, 5000, 10000));
		assertEquals(Rings.INNER, Rings.at(1999, 0, 2000, 5000, 10000));
		assertEquals(Rings.MIDDLE, Rings.at(2000, 0, 2000, 5000, 10000));
		assertEquals(Rings.MIDDLE, Rings.at(0, -4999, 2000, 5000, 10000));
		assertEquals(Rings.OUTER, Rings.at(0, 5000, 2000, 5000, 10000));
		assertEquals(Rings.EDGE, Rings.at(-10000, 0, 2000, 5000, 10000));
		assertEquals(Rings.EDGE, Rings.at(50000, 50000, 2000, 5000, 10000));
	}

	@Test
	void distanceIsMeasuredStraightFromTheCentre() {
		// 3000 along each axis is about 4243 from the centre: still the middle ring, not the outer.
		assertEquals(Rings.MIDDLE, Rings.at(3000, 3000, 2000, 5000, 10000));
		// 3600 along each axis is about 5091: outer.
		assertEquals(Rings.OUTER, Rings.at(3600, 3600, 2000, 5000, 10000));
	}

	@Test
	void customDistancesWork() {
		assertEquals(Rings.OUTER, Rings.at(600, 0, 100, 500, 1000));
		assertEquals(Rings.EDGE, Rings.at(0, 1000, 100, 500, 1000));
	}
}
