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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.badlogic.gdx.Input.Keys;
import org.junit.Test;

public class DefaultSdl3InputLogicTest {

	@Test
	public void unknownKeyUp_doesNotDecrementForLogicallyPressedKey () {
		DefaultSdl3Input in = DefaultSdl3Input.forTest();
		in.handleKey(Keys.A, true);
		assertTrue(in.isKeyPressed(Keys.A));
		assertTrue(in.isKeyPressed(Keys.ANY_KEY));
		// Spurious UNKNOWN up must not corrupt the pressedKeyCount nor flip Keys.A.
		in.handleKey(Keys.UNKNOWN, false);
		assertTrue(in.isKeyPressed(Keys.A));
		assertTrue(in.isKeyPressed(Keys.ANY_KEY));
		// Properly release Keys.A; ANY_KEY (count > 0) should now read false.
		in.handleKey(Keys.A, false);
		assertFalse(in.isKeyPressed(Keys.A));
		assertFalse(in.isKeyPressed(Keys.ANY_KEY));
	}

	@Test
	public void unknownKeyDown_doesNotFlipAnyKey () {
		DefaultSdl3Input in = DefaultSdl3Input.forTest();
		assertFalse(in.isKeyPressed(Keys.ANY_KEY));
		in.handleKey(Keys.UNKNOWN, true);
		// pressedKeyCount must not have incremented for UNKNOWN.
		assertFalse(in.isKeyPressed(Keys.ANY_KEY));
	}

	@Test
	public void knownKeyDownUp_balances () {
		DefaultSdl3Input in = DefaultSdl3Input.forTest();
		in.handleKey(Keys.A, true);
		in.handleKey(Keys.A, false);
		assertFalse(in.isKeyPressed(Keys.A));
		assertFalse(in.isKeyPressed(Keys.ANY_KEY));
	}

	@Test
	public void isKeyJustPressed_unknown_isFalseAfterUnmappedEvent () {
		DefaultSdl3Input in = DefaultSdl3Input.forTest();
		in.handleKey(Keys.UNKNOWN, true);
		assertFalse(in.isKeyJustPressed(Keys.UNKNOWN));
	}

	@Test
	public void handleTextInput_setsLastCharacter () {
		DefaultSdl3Input in = DefaultSdl3Input.forTest();
		in.handleTextInput('a');
		assertEquals('a', in.lastCharacter);
	}

	@Test
	public void handleTextInput_filtersAppKitPrivateUseCodepoints () {
		DefaultSdl3Input in = DefaultSdl3Input.forTest();
		in.lastCharacter = 'a';
		in.handleTextInput((char)0xf700); // AppKit Up Arrow private-use codepoint
		// Filtered — lastCharacter must not be overwritten with the private-use char.
		assertEquals('a', in.lastCharacter);
	}

	/** Regression guard for Task 14/15 — the pre-fix KEY_DOWN case in {@code handleSDLEvent} unconditionally ran
	 * {@code lastCharacter = 0;} before checking the character mapping. For any key without a character mapping
	 * (letters via SDL3's scancode → keycode pipeline, function keys, arrow keys, etc.) this silently wiped the
	 * most-recent text-input character between {@code SDL_EVENT_TEXT_INPUT} and the next character event.
	 *
	 * <p>Drives {@link DefaultSdl3Input#dispatchKeyDown} directly — the seam {@code handleSDLEvent} routes to after
	 * decoding the SDL_Event. If anyone re-introduces an unconditional {@code lastCharacter = 0;} into this dispatch
	 * (or the case body that wraps it), this test fails. */
	@Test
	public void dispatchKeyDown_arbitraryKeyDoesNotClearLastCharacter () {
		DefaultSdl3Input in = DefaultSdl3Input.forTest();
		in.handleTextInput('a');
		assertEquals('a', in.lastCharacter);
		// Keys.B has no character mapping in characterForKeyCode (only ENTER/TAB/BACKSPACE/DEL do); the dispatch must
		// leave lastCharacter alone for non-character keys.
		in.dispatchKeyDown(Keys.B, 1234567890L);
		assertEquals("non-character key-down must not clear lastCharacter", 'a', in.lastCharacter);
	}

	@Test
	public void dispatchKeyDown_specialKeyUpdatesLastCharacter () {
		DefaultSdl3Input in = DefaultSdl3Input.forTest();
		in.handleTextInput('a');
		// BACKSPACE maps to '\b' via characterForKeyCode — dispatch must update lastCharacter to that char.
		in.dispatchKeyDown(Keys.BACKSPACE, 1234567890L);
		assertEquals('\b', in.lastCharacter);
	}
}
