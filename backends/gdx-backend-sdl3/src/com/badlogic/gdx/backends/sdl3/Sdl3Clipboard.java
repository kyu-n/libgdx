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

import static org.lwjgl.sdl.SDLClipboard.SDL_GetClipboardText;
import static org.lwjgl.sdl.SDLClipboard.SDL_SetClipboardText;

import com.badlogic.gdx.utils.Clipboard;

/** Clipboard implementation for desktop that uses the system clipboard via SDL3. The SDL3 clipboard is a process-global API — it
 * does not take a window handle, so this implementation has no dependency on {@code Sdl3Graphics}.
 * @author mzechner */
public class Sdl3Clipboard implements Clipboard {
	@Override
	public boolean hasContents () {
		String contents = getContents();
		return contents != null && !contents.isEmpty();
	}

	@Override
	public String getContents () {
		// SDL_GetClipboardText returns an empty (non-null) String on failure / when the clipboard is empty,
		// matching the contract callers expect.
		return SDL_GetClipboardText();
	}

	@Override
	public void setContents (String content) {
		SDL_SetClipboardText(content == null ? "" : content);
	}
}
