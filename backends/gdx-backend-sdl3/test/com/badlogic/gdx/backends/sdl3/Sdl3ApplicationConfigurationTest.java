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

	/** Sanity check on {@link Sdl3ApplicationConfiguration#copy} — the live config that
	 * Sdl3Application captures in its constructor must keep the same {@code idleFPS} and
	 * {@code disableAudio} values as the user-supplied one. (The actual NPE fix in
	 * cleanup() cannot be exercised here — it only triggers on partial SDL_Init failure
	 * during construction. This test only validates the copy semantics cleanup relies on.) */
	@Test
	public void copy_preservesIdleFpsAndDisableAudio () {
		Sdl3ApplicationConfiguration src = new Sdl3ApplicationConfiguration();
		src.setIdleFPS(30);
		Sdl3ApplicationConfiguration dst = Sdl3ApplicationConfiguration.copy(src);
		assertEquals(src.idleFPS, dst.idleFPS);
		assertEquals(src.disableAudio, dst.disableAudio);
	}

	@Test
	public void setRGBABits_updatesBackBufferConfig () {
		Sdl3ApplicationConfiguration c = new Sdl3ApplicationConfiguration();
		c.setRGBABits(8, 8, 8, 8);
		assertEquals(8, c.r);
		assertEquals(8, c.g);
		assertEquals(8, c.b);
		assertEquals(8, c.a);
	}

	@Test
	public void setDepthBits_updates () {
		Sdl3ApplicationConfiguration c = new Sdl3ApplicationConfiguration();
		c.setDepthBits(24);
		assertEquals(24, c.depth);
	}

	@Test
	public void setStencilBits_updates () {
		Sdl3ApplicationConfiguration c = new Sdl3ApplicationConfiguration();
		c.setStencilBits(8);
		assertEquals(8, c.stencil);
	}

	@Test
	public void setSamples_updates () {
		Sdl3ApplicationConfiguration c = new Sdl3ApplicationConfiguration();
		c.setSamples(4);
		assertEquals(4, c.samples);
	}
}
