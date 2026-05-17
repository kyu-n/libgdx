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
import org.junit.Test;

public class Sdl3WindowLifecycleTest {

	@Test
	public void doublePauseEvents_fireListenerOnce () {
		// Simulate Windows behavior: FOCUS_LOST + MINIMIZED arrive together
		// for a single minimize action. The pause listener must fire once,
		// not twice.
		int[] pauseCount = {0};
		int[] resumeCount = {0};
		Sdl3Window.PauseGate gate = new Sdl3Window.PauseGate(
			() -> pauseCount[0]++,
			() -> resumeCount[0]++
		);
		gate.requestPause(); // FOCUS_LOST
		gate.requestPause(); // MINIMIZED
		assertEquals(1, pauseCount[0]);
		gate.requestResume(); // RESTORED
		gate.requestResume(); // FOCUS_GAINED
		assertEquals(1, resumeCount[0]);
	}
}
