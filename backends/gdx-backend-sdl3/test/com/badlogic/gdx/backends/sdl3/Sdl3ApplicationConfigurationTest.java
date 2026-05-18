/*******************************************************************************
 * Copyright 2011 See AUTHORS file.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.badlogic.gdx.backends.sdl3;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.lwjgl.sdl.SDLVideo.SDL_GL_ALPHA_SIZE;
import static org.lwjgl.sdl.SDLVideo.SDL_GL_BLUE_SIZE;
import static org.lwjgl.sdl.SDLVideo.SDL_GL_DEPTH_SIZE;
import static org.lwjgl.sdl.SDLVideo.SDL_GL_GREEN_SIZE;
import static org.lwjgl.sdl.SDLVideo.SDL_GL_MULTISAMPLEBUFFERS;
import static org.lwjgl.sdl.SDLVideo.SDL_GL_MULTISAMPLESAMPLES;
import static org.lwjgl.sdl.SDLVideo.SDL_GL_RED_SIZE;
import static org.lwjgl.sdl.SDLVideo.SDL_GL_STENCIL_SIZE;

import java.util.HashMap;
import java.util.Map;

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

	/** Verifies the field→SDL_GL_* enum wiring inside {@link Sdl3ApplicationConfiguration#applyBackBufferAttributes}. The
	 * previous setter tests only checked that {@code setRGBABits} wrote to {@code r/g/b/a} — a tautology that would still
	 * pass if {@code applyBackBufferAttributes} swapped red and blue or ignored the fields entirely. Distinct r/g/b/a
	 * values catch any cross-wiring bug. */
	@Test
	public void backBufferAttributePairs_wiresFieldsToCorrectGLEnums () {
		Sdl3ApplicationConfiguration c = new Sdl3ApplicationConfiguration();
		c.setRGBABits(7, 6, 5, 4);
		c.setDepthBits(24);
		c.setStencilBits(8);
		c.setSamples(4);
		Map<Integer, Integer> pairs = pairsAsMap(c);
		assertEquals(7, (int)pairs.get(SDL_GL_RED_SIZE));
		assertEquals(6, (int)pairs.get(SDL_GL_GREEN_SIZE));
		assertEquals(5, (int)pairs.get(SDL_GL_BLUE_SIZE));
		assertEquals(4, (int)pairs.get(SDL_GL_ALPHA_SIZE));
		assertEquals(24, (int)pairs.get(SDL_GL_DEPTH_SIZE));
		assertEquals(8, (int)pairs.get(SDL_GL_STENCIL_SIZE));
		assertEquals(1, (int)pairs.get(SDL_GL_MULTISAMPLEBUFFERS));
		assertEquals(4, (int)pairs.get(SDL_GL_MULTISAMPLESAMPLES));
	}

	@Test
	public void backBufferAttributePairs_zeroSamples_disablesMultisample () {
		Sdl3ApplicationConfiguration c = new Sdl3ApplicationConfiguration();
		c.setSamples(0);
		Map<Integer, Integer> pairs = pairsAsMap(c);
		assertEquals(0, (int)pairs.get(SDL_GL_MULTISAMPLEBUFFERS));
		assertEquals(0, (int)pairs.get(SDL_GL_MULTISAMPLESAMPLES));
	}

	private static Map<Integer, Integer> pairsAsMap (Sdl3ApplicationConfiguration c) {
		Map<Integer, Integer> map = new HashMap<>();
		for (int[] pair : Sdl3ApplicationConfiguration.backBufferAttributePairs(c)) {
			map.put(pair[0], pair[1]);
		}
		return map;
	}
}
