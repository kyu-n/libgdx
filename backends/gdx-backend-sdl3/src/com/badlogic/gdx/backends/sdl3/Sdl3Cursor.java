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

import static org.lwjgl.sdl.SDLMouse.SDL_CreateColorCursor;
import static org.lwjgl.sdl.SDLMouse.SDL_CreateSystemCursor;
import static org.lwjgl.sdl.SDLMouse.SDL_DestroyCursor;
import static org.lwjgl.sdl.SDLMouse.SDL_HideCursor;
import static org.lwjgl.sdl.SDLMouse.SDL_SetCursor;
import static org.lwjgl.sdl.SDLMouse.SDL_ShowCursor;
import static org.lwjgl.sdl.SDLMouse.SDL_SYSTEM_CURSOR_CROSSHAIR;
import static org.lwjgl.sdl.SDLMouse.SDL_SYSTEM_CURSOR_DEFAULT;
import static org.lwjgl.sdl.SDLMouse.SDL_SYSTEM_CURSOR_EW_RESIZE;
import static org.lwjgl.sdl.SDLMouse.SDL_SYSTEM_CURSOR_MOVE;
import static org.lwjgl.sdl.SDLMouse.SDL_SYSTEM_CURSOR_NESW_RESIZE;
import static org.lwjgl.sdl.SDLMouse.SDL_SYSTEM_CURSOR_NOT_ALLOWED;
import static org.lwjgl.sdl.SDLMouse.SDL_SYSTEM_CURSOR_NS_RESIZE;
import static org.lwjgl.sdl.SDLMouse.SDL_SYSTEM_CURSOR_NWSE_RESIZE;
import static org.lwjgl.sdl.SDLMouse.SDL_SYSTEM_CURSOR_POINTER;
import static org.lwjgl.sdl.SDLMouse.SDL_SYSTEM_CURSOR_TEXT;
import static org.lwjgl.sdl.SDLPixels.SDL_PIXELFORMAT_ABGR8888;
import static org.lwjgl.sdl.SDLSurface.SDL_CreateSurfaceFrom;
import static org.lwjgl.sdl.SDLSurface.SDL_DestroySurface;

import java.util.HashMap;
import java.util.Map;

import org.lwjgl.sdl.SDL_Surface;

import com.badlogic.gdx.graphics.Cursor;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Pixmap.Blending;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxRuntimeException;

/** Cursor implementation for desktop that uses SDL3 cursor APIs. SDL3 cursors are process-global — {@code SDL_SetCursor} does not
 * take a window handle. The {@link Sdl3Window} parameter is retained on this class for per-window dispose tracking so cursors can
 * be cleaned up when their owning window is destroyed (see {@link #dispose(Sdl3Window)}). */
public class Sdl3Cursor implements Cursor {
	static final Array<Sdl3Cursor> cursors = new Array<Sdl3Cursor>();
	static final Map<SystemCursor, Long> systemCursors = new HashMap<SystemCursor, Long>();

	/** Tracks whether {@link SystemCursor#None} has hidden the cursor so we can {@code SDL_ShowCursor} the next time a real cursor
	 * is selected. SDL3 has no per-window hidden state — visibility is global. */
	private static boolean cursorHiddenByNone = false;

	final Sdl3Window window;
	Pixmap pixmapCopy;
	/** The {@code SDL_Surface*} owning the cursor pixel data. Kept alive until {@link #dispose()} because
	 * {@code SDL_CreateSurfaceFrom} does not copy the pixel buffer — destroying the surface (or the backing pixmap) before the
	 * cursor is destroyed would point SDL at freed memory. */
	SDL_Surface sdlSurface;
	/** The {@code SDL_Cursor*} handle returned by {@code SDL_CreateColorCursor}. Zero means "creation failed" — surfaced as a
	 * non-fatal log line. */
	final long sdlCursor;

	Sdl3Cursor (Sdl3Window window, Pixmap pixmap, int xHotspot, int yHotspot) {
		this.window = window;
		if (pixmap.getFormat() != Pixmap.Format.RGBA8888) {
			throw new GdxRuntimeException("Cursor image pixmap is not in RGBA8888 format.");
		}

		if (pixmap.getWidth() <= 0 || (pixmap.getWidth() & (pixmap.getWidth() - 1)) != 0) {
			throw new GdxRuntimeException(
				"Cursor image pixmap width of " + pixmap.getWidth() + " is not a power-of-two greater than zero.");
		}

		if (pixmap.getHeight() <= 0 || (pixmap.getHeight() & (pixmap.getHeight() - 1)) != 0) {
			throw new GdxRuntimeException(
				"Cursor image pixmap height of " + pixmap.getHeight() + " is not a power-of-two greater than zero.");
		}

		if (xHotspot < 0 || xHotspot >= pixmap.getWidth()) {
			throw new GdxRuntimeException(
				"xHotspot coordinate of " + xHotspot + " is not within image width bounds: [0, " + pixmap.getWidth() + ").");
		}

		if (yHotspot < 0 || yHotspot >= pixmap.getHeight()) {
			throw new GdxRuntimeException(
				"yHotspot coordinate of " + yHotspot + " is not within image height bounds: [0, " + pixmap.getHeight() + ").");
		}

		this.pixmapCopy = new Pixmap(pixmap.getWidth(), pixmap.getHeight(), Pixmap.Format.RGBA8888);
		this.pixmapCopy.setBlending(Blending.None);
		this.pixmapCopy.drawPixmap(pixmap, 0, 0);

		// Pixmap RGBA8888 is byte-packed R,G,B,A. On little-endian Desktop hosts this matches SDL_PIXELFORMAT_ABGR8888 — same
		// rationale as Sdl3Window.setIcon. SDL_CreateSurfaceFrom does NOT copy the pixel buffer; it stores the pointer. We
		// retain pixmapCopy + sdlSurface for the cursor's lifetime to keep the backing memory alive.
		int pitch = pixmapCopy.getWidth() * 4;
		sdlSurface = SDL_CreateSurfaceFrom(pixmapCopy.getWidth(), pixmapCopy.getHeight(), SDL_PIXELFORMAT_ABGR8888,
			pixmapCopy.getPixels(), pitch);
		if (sdlSurface != null) {
			sdlCursor = SDL_CreateColorCursor(sdlSurface, xHotspot, yHotspot);
		} else {
			sdlCursor = 0L;
		}
		cursors.add(this);
	}

	@Override
	public void dispose () {
		if (pixmapCopy == null) {
			throw new GdxRuntimeException("Cursor already disposed");
		}
		cursors.removeValue(this, true);
		if (sdlCursor != 0L) {
			SDL_DestroyCursor(sdlCursor);
		}
		if (sdlSurface != null) {
			SDL_DestroySurface(sdlSurface);
			sdlSurface = null;
		}
		pixmapCopy.dispose();
		pixmapCopy = null;
	}

	static void dispose (Sdl3Window window) {
		for (int i = cursors.size - 1; i >= 0; i--) {
			Sdl3Cursor cursor = cursors.get(i);
			if (cursor.window.equals(window)) {
				cursors.removeIndex(i).dispose();
			}
		}
	}

	static void disposeSystemCursors () {
		for (long systemCursor : systemCursors.values()) {
			SDL_DestroyCursor(systemCursor);
		}
		systemCursors.clear();
		cursorHiddenByNone = false;
		// Best-effort: dispose any user-created cursors the application forgot to dispose. Otherwise the static
		// `cursors` array carries stale SDL_Cursor / SDL_Surface pointers across Sdl3Application re-init in the
		// same JVM (test harnesses), and a later dispose() call on a held-over Sdl3Cursor reference would
		// SDL_DestroyCursor a freed-or-recycled pointer. Snapshot first because Sdl3Cursor.dispose() mutates the
		// array via removeValue.
		Sdl3Cursor[] leftover = cursors.toArray(Sdl3Cursor.class);
		for (Sdl3Cursor cursor : leftover) {
			try {
				cursor.dispose();
			} catch (Throwable t) {
				// best-effort during teardown — keep going
			}
		}
		cursors.clear();
	}

	static void setSystemCursor (long windowHandle, SystemCursor systemCursor) {
		if (systemCursor == SystemCursor.None) {
			if (!cursorHiddenByNone) {
				SDL_HideCursor();
				cursorHiddenByNone = true;
			}
			return;
		}
		if (cursorHiddenByNone) {
			SDL_ShowCursor();
			cursorHiddenByNone = false;
		}
		Long sdlSystemCursor = systemCursors.get(systemCursor);
		if (sdlSystemCursor == null) {
			long handle;
			if (systemCursor == SystemCursor.Arrow) {
				handle = SDL_CreateSystemCursor(SDL_SYSTEM_CURSOR_DEFAULT);
			} else if (systemCursor == SystemCursor.Crosshair) {
				handle = SDL_CreateSystemCursor(SDL_SYSTEM_CURSOR_CROSSHAIR);
			} else if (systemCursor == SystemCursor.Hand) {
				handle = SDL_CreateSystemCursor(SDL_SYSTEM_CURSOR_POINTER);
			} else if (systemCursor == SystemCursor.HorizontalResize) {
				handle = SDL_CreateSystemCursor(SDL_SYSTEM_CURSOR_EW_RESIZE);
			} else if (systemCursor == SystemCursor.VerticalResize) {
				handle = SDL_CreateSystemCursor(SDL_SYSTEM_CURSOR_NS_RESIZE);
			} else if (systemCursor == SystemCursor.Ibeam) {
				handle = SDL_CreateSystemCursor(SDL_SYSTEM_CURSOR_TEXT);
			} else if (systemCursor == SystemCursor.NWSEResize) {
				handle = SDL_CreateSystemCursor(SDL_SYSTEM_CURSOR_NWSE_RESIZE);
			} else if (systemCursor == SystemCursor.NESWResize) {
				handle = SDL_CreateSystemCursor(SDL_SYSTEM_CURSOR_NESW_RESIZE);
			} else if (systemCursor == SystemCursor.AllResize) {
				handle = SDL_CreateSystemCursor(SDL_SYSTEM_CURSOR_MOVE);
			} else if (systemCursor == SystemCursor.NotAllowed) {
				handle = SDL_CreateSystemCursor(SDL_SYSTEM_CURSOR_NOT_ALLOWED);
			} else {
				throw new GdxRuntimeException("Unknown system cursor " + systemCursor);
			}

			if (handle == 0L) {
				return;
			}
			sdlSystemCursor = handle;
			systemCursors.put(systemCursor, sdlSystemCursor);
		}
		SDL_SetCursor(sdlSystemCursor);
	}
}
