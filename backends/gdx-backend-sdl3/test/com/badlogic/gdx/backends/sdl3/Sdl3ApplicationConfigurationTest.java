package com.badlogic.gdx.backends.sdl3;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

public class Sdl3ApplicationConfigurationTest {

	@Test
	public void setIdleFPS_rejectsZero () {
		Sdl3ApplicationConfiguration c = new Sdl3ApplicationConfiguration();
		try {
			c.setIdleFPS(0);
			fail("Expected IllegalArgumentException for idleFPS = 0");
		} catch (IllegalArgumentException expected) {
		}
	}

	@Test
	public void setIdleFPS_rejectsNegative () {
		Sdl3ApplicationConfiguration c = new Sdl3ApplicationConfiguration();
		try {
			c.setIdleFPS(-1);
			fail("Expected IllegalArgumentException for idleFPS = -1");
		} catch (IllegalArgumentException expected) {
		}
	}

	@Test
	public void setIdleFPS_acceptsOne () {
		Sdl3ApplicationConfiguration c = new Sdl3ApplicationConfiguration();
		c.setIdleFPS(1);
		assertEquals(1, c.idleFPS);
	}
}
