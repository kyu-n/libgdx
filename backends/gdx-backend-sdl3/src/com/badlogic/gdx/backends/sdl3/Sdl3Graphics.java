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

import static org.lwjgl.sdl.SDLError.SDL_GetError;
import static org.lwjgl.sdl.SDLVideo.SDL_GL_ExtensionSupported;
import static org.lwjgl.sdl.SDLVideo.SDL_GL_SetSwapInterval;
import static org.lwjgl.sdl.SDLVideo.SDL_GetFullscreenDisplayModes;
import static org.lwjgl.sdl.SDLVideo.SDL_GetWindowDisplayScale;
import static org.lwjgl.sdl.SDLVideo.SDL_GetWindowPixelDensity;
import static org.lwjgl.sdl.SDLVideo.SDL_GetWindowSize;
import static org.lwjgl.sdl.SDLVideo.SDL_GetWindowSizeInPixels;
import static org.lwjgl.sdl.SDLVideo.SDL_SetWindowBordered;
import static org.lwjgl.sdl.SDLVideo.SDL_SetWindowFullscreen;
import static org.lwjgl.sdl.SDLVideo.SDL_SetWindowFullscreenMode;
import static org.lwjgl.sdl.SDLVideo.SDL_SetWindowPosition;
import static org.lwjgl.sdl.SDLVideo.SDL_SetWindowResizable;
import static org.lwjgl.sdl.SDLVideo.SDL_SetWindowSize;
import static org.lwjgl.sdl.SDLVideo.SDL_SyncWindow;

import java.nio.IntBuffer;

import com.badlogic.gdx.AbstractGraphics;
import com.badlogic.gdx.Application;

import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;

import com.badlogic.gdx.graphics.Cursor;
import com.badlogic.gdx.graphics.Cursor.SystemCursor;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.GL31;
import com.badlogic.gdx.graphics.GL32;
import com.badlogic.gdx.graphics.glutils.GLVersion;
import com.badlogic.gdx.graphics.glutils.HdpiMode;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.utils.Disposable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.sdl.SDL_DisplayMode;
import org.lwjgl.system.MemoryStack;
import com.badlogic.gdx.math.GridPoint2;

public class Sdl3Graphics extends AbstractGraphics implements Disposable {
	final Sdl3Window window;
	GL20 gl20;
	private GL30 gl30;
	private GL31 gl31;
	private GL32 gl32;
	private GLVersion glVersion;
	private volatile int backBufferWidth;
	private volatile int backBufferHeight;
	private volatile int logicalWidth;
	private volatile int logicalHeight;
	private volatile boolean isContinuous = true;
	private BufferFormat bufferFormat;
	private long lastFrameTime = -1;
	private float deltaTime;
	private boolean resetDeltaTime = false;
	private long frameId;
	private long frameCounterStart = 0;
	private int frames;
	private int fps;
	private int windowPosXBeforeFullscreen;
	private int windowPosYBeforeFullscreen;
	private int windowWidthBeforeFullscreen;
	private int windowHeightBeforeFullscreen;
	private DisplayMode displayModeBeforeFullscreen = null;

	IntBuffer tmpBuffer = BufferUtils.createIntBuffer(1);
	IntBuffer tmpBuffer2 = BufferUtils.createIntBuffer(1);

	public Sdl3Graphics (Sdl3Window window) {
		this.window = window;
		if (window.getConfig().glEmulation == Sdl3ApplicationConfiguration.GLEmulation.GL32) {
			this.gl20 = this.gl30 = this.gl31 = this.gl32 = new Sdl3GL32();
		} else if (window.getConfig().glEmulation == Sdl3ApplicationConfiguration.GLEmulation.GL31) {
			this.gl20 = this.gl30 = this.gl31 = new Sdl3GL31();
		} else if (window.getConfig().glEmulation == Sdl3ApplicationConfiguration.GLEmulation.GL30) {
			this.gl20 = this.gl30 = new Sdl3GL30();
		} else {
			this.gl20 = new Sdl3GL20();
			this.gl30 = null;
		}
		updateFramebufferInfo();
		initiateGL();
		// SDL3 delivers resize as SDL_EVENT_WINDOW_PIXEL_SIZE_CHANGED through the application event pump; Sdl3Window invokes
		// updateFramebufferInfo from that event handler. This is the idiomatic SDL3 pattern — there is no per-window
		// callback API equivalent to glfwSetWindowSizeCallback to register against.
	}

	/** Initialize the {@link GLVersion} record from the live OpenGL context. Requires SDL_GL_MakeCurrent to have been called on
	 * this window's GL context (handled by {@link Sdl3Window#makeCurrent()} during construction). */
	private void initiateGL () {
		String versionString = gl20.glGetString(GL11.GL_VERSION);
		String vendorString = gl20.glGetString(GL11.GL_VENDOR);
		String rendererString = gl20.glGetString(GL11.GL_RENDERER);
		glVersion = new GLVersion(Application.ApplicationType.Desktop, versionString, vendorString, rendererString);
		if (supportsCubeMapSeamless()) {
			enableCubeMapSeamless(true);
		}
	}

	/** @return whether cubemap seamless feature is supported. */
	public boolean supportsCubeMapSeamless () {
		return glVersion.isVersionEqualToOrHigher(3, 2) || supportsExtension("GL_ARB_seamless_cube_map");
	}

	/** Enable or disable cubemap seamless feature. Default is true if supported. Should only be called if this feature is
	 * supported. (see {@link #supportsCubeMapSeamless()})
	 * @param enable */
	public void enableCubeMapSeamless (boolean enable) {
		if (enable) {
			gl20.glEnable(org.lwjgl.opengl.GL32.GL_TEXTURE_CUBE_MAP_SEAMLESS);
		} else {
			gl20.glDisable(org.lwjgl.opengl.GL32.GL_TEXTURE_CUBE_MAP_SEAMLESS);
		}
	}

	public Sdl3Window getWindow () {
		return window;
	}

	void updateFramebufferInfo () {
		// SDL_GetWindowSizeInPixels returns the backbuffer/drawable size in pixels (HiDPI-aware); SDL_GetWindowSize returns
		// the logical window size in screen-coords.
		SDL_GetWindowSizeInPixels(window.getWindowHandle(), tmpBuffer, tmpBuffer2);
		this.backBufferWidth = tmpBuffer.get(0);
		this.backBufferHeight = tmpBuffer2.get(0);
		SDL_GetWindowSize(window.getWindowHandle(), tmpBuffer, tmpBuffer2);
		this.logicalWidth = tmpBuffer.get(0);
		this.logicalHeight = tmpBuffer2.get(0);
		Sdl3ApplicationConfiguration config = window.getConfig();
		bufferFormat = new BufferFormat(config.r, config.g, config.b, config.a, config.depth, config.stencil, config.samples,
			false);
	}

	void update () {
		long time = System.nanoTime();
		if (lastFrameTime == -1) lastFrameTime = time;
		if (resetDeltaTime) {
			resetDeltaTime = false;
			deltaTime = 0;
		} else
			deltaTime = (time - lastFrameTime) / 1000000000.0f;
		lastFrameTime = time;

		if (time - frameCounterStart >= 1000000000) {
			fps = frames;
			frames = 0;
			frameCounterStart = time;
		}
		frames++;
		frameId++;
	}

	@Override
	public boolean isGL30Available () {
		return gl30 != null;
	}

	@Override
	public boolean isGL31Available () {
		return gl31 != null;
	}

	@Override
	public boolean isGL32Available () {
		return gl32 != null;
	}

	@Override
	public GL20 getGL20 () {
		return gl20;
	}

	@Override
	public GL30 getGL30 () {
		return gl30;
	}

	@Override
	public GL31 getGL31 () {
		return gl31;
	}

	@Override
	public GL32 getGL32 () {
		return gl32;
	}

	@Override
	public void setGL20 (GL20 gl20) {
		this.gl20 = gl20;
	}

	@Override
	public void setGL30 (GL30 gl30) {
		this.gl30 = gl30;
	}

	@Override
	public void setGL31 (GL31 gl31) {
		this.gl31 = gl31;
	}

	@Override
	public void setGL32 (GL32 gl32) {
		this.gl32 = gl32;
	}

	@Override
	public int getWidth () {
		if (window.getConfig().hdpiMode == HdpiMode.Pixels) {
			return backBufferWidth;
		} else {
			return logicalWidth;
		}
	}

	@Override
	public int getHeight () {
		if (window.getConfig().hdpiMode == HdpiMode.Pixels) {
			return backBufferHeight;
		} else {
			return logicalHeight;
		}
	}

	@Override
	public int getBackBufferWidth () {
		return backBufferWidth;
	}

	@Override
	public int getBackBufferHeight () {
		return backBufferHeight;
	}

	public int getLogicalWidth () {
		return logicalWidth;
	}

	public int getLogicalHeight () {
		return logicalHeight;
	}

	@Override
	public long getFrameId () {
		return frameId;
	}

	@Override
	public float getDeltaTime () {
		return deltaTime;
	}

	public void resetDeltaTime () {
		resetDeltaTime = true;
	}

	@Override
	public int getFramesPerSecond () {
		return fps;
	}

	@Override
	public GraphicsType getType () {
		return GraphicsType.SDL3;
	}

	@Override
	public GLVersion getGLVersion () {
		return glVersion;
	}

	@Override
	public float getPpiX () {
		// SDL3 exposes per-window pixel density (HiDPI scale) but not physical mm. We approximate DPI by multiplying the
		// relative pixel density by the standard 96 DPI baseline that SDL uses internally for HiDPI scaling. This matches what
		// Win32/macOS expose as "logical DPI" and is the conventional libGDX-Desktop DPI proxy.
		return SDL_GetWindowPixelDensity(window.getWindowHandle()) * 96f;
	}

	@Override
	public float getPpiY () {
		return getPpiX();
	}

	@Override
	public float getPpcX () {
		return getPpiX() / 2.54f;
	}

	@Override
	public float getPpcY () {
		return getPpiY() / 2.54f;
	}

	@Override
	public float getDensity () {
		// Per-window display scale (e.g. 1.0 on a 1080p panel, 2.0 on a Retina/4K@200%). Returned directly by SDL3 as a
		// stable float — no DPI / 160 normalization needed (SDL already produces the libGDX-Mobile-style multiplier).
		return SDL_GetWindowDisplayScale(window.getWindowHandle());
	}

	@Override
	public boolean supportsDisplayModeChange () {
		return true;
	}

	@Override
	public Monitor getPrimaryMonitor () {
		return Sdl3ApplicationConfiguration.getPrimaryMonitor();
	}

	@Override
	public Monitor getMonitor () {
		// SDL_GetDisplayForWindow returns the display the window is currently on. If it returns 0 (no display) fall back to
		// the primary monitor so callers never get null.
		int displayID = org.lwjgl.sdl.SDLVideo.SDL_GetDisplayForWindow(window.getWindowHandle());
		if (displayID == 0) {
			return getPrimaryMonitor();
		}
		return Sdl3ApplicationConfiguration.toSdl3Monitor(displayID);
	}

	@Override
	public Monitor[] getMonitors () {
		return Sdl3ApplicationConfiguration.getMonitors();
	}

	@Override
	public DisplayMode[] getDisplayModes () {
		return Sdl3ApplicationConfiguration.getDisplayModes(getMonitor());
	}

	@Override
	public DisplayMode[] getDisplayModes (Monitor monitor) {
		return Sdl3ApplicationConfiguration.getDisplayModes(monitor);
	}

	@Override
	public DisplayMode getDisplayMode () {
		return Sdl3ApplicationConfiguration.getDisplayMode(getMonitor());
	}

	@Override
	public DisplayMode getDisplayMode (Monitor monitor) {
		return Sdl3ApplicationConfiguration.getDisplayMode(monitor);
	}

	@Override
	public int getSafeInsetLeft () {
		return 0;
	}

	@Override
	public int getSafeInsetTop () {
		return 0;
	}

	@Override
	public int getSafeInsetBottom () {
		return 0;
	}

	@Override
	public int getSafeInsetRight () {
		return 0;
	}

	@Override
	public boolean setFullscreenMode (DisplayMode displayMode) {
		window.getInput().resetPollingStates();
		Sdl3DisplayMode newMode = (Sdl3DisplayMode)displayMode;
		if (!isFullscreen()) {
			// Store the windowed-mode geometry so setWindowedMode can restore later.
			storeCurrentWindowPositionAndDisplayMode();
		}
		// SDL3 splits "go fullscreen" (boolean) and "fullscreen display mode" (struct) into two calls. Set the mode first so
		// that SDL has the target geometry ready when fullscreen is enabled — otherwise the first transition picks the
		// desktop default and we'd see a flicker.
		//
		// Use SDL3's authoritative SDL_DisplayMode from SDL_GetFullscreenDisplayModes rather than constructing one from
		// scratch. SDL3 requires pixel_density, format, and refresh_rate_numerator/denominator to be set correctly;
		// passing a partially-populated struct produces "Invalid fullscreen display mode" even when w/h/refresh_rate
		// nominally match an exposed mode. Looking up by w/h/refresh_rate and reusing SDL's own struct sidesteps the
		// problem and survives future SDL_DisplayMode field additions.
		//
		// Memory-lifetime note: the PointerBuffer returned by SDL_GetFullscreenDisplayModes is GC-managed by LWJGL,
		// and the SDL_DisplayMode wrappers we get from it hold pointers INTO that array. The SetWindowFullscreenMode
		// call must happen while the PointerBuffer is still alive on the local stack; returning a wrapper from a
		// helper method and using it after the helper returns is a use-after-free risk.
		PointerBuffer modes = SDL_GetFullscreenDisplayModes((int)newMode.monitorHandle);
		if (modes == null) {
			System.err.println("Sdl3Graphics: SDL_GetFullscreenDisplayModes failed: " + SDL_GetError());
			return false;
		}
		// Find the matching candidate and copy its fields into a stack-allocated SDL_DisplayMode.
		// The candidate struct points into the PointerBuffer's GC-managed memory (see lifetime
		// note above) — copying lets us own the struct outright for the SetWindowFullscreenMode call.
		SDL_DisplayMode matched = null;
		int count = modes.remaining();
		for (int i = 0; i < count; i++) {
			SDL_DisplayMode candidate = SDL_DisplayMode.create(modes.get(i));
			if (candidate.w() == newMode.width
				&& candidate.h() == newMode.height
				&& Math.abs(candidate.refresh_rate() - newMode.refreshRate) < 0.5f) {
				matched = candidate;
				break;
			}
		}
		if (matched == null) {
			System.err.println("Sdl3Graphics: setFullscreenMode: no SDL_DisplayMode matched "
				+ newMode.width + "x" + newMode.height + "@" + newMode.refreshRate
				+ "Hz on display " + newMode.monitorHandle);
			return false;
		}
		try (MemoryStack stack = MemoryStack.stackPush()) {
			SDL_DisplayMode sdlMode = SDL_DisplayMode.malloc(stack);
			sdlMode.displayID(matched.displayID());
			sdlMode.format(matched.format());
			sdlMode.w(matched.w());
			sdlMode.h(matched.h());
			sdlMode.pixel_density(matched.pixel_density());
			sdlMode.refresh_rate(matched.refresh_rate());
			sdlMode.refresh_rate_numerator(matched.refresh_rate_numerator());
			sdlMode.refresh_rate_denominator(matched.refresh_rate_denominator());
			if (!SDL_SetWindowFullscreenMode(window.getWindowHandle(), sdlMode)) {
				System.err.println("Sdl3Graphics: SDL_SetWindowFullscreenMode failed: " + SDL_GetError());
				return false;
			}
		}
		if (!SDL_SetWindowFullscreen(window.getWindowHandle(), true)) {
			System.err.println("Sdl3Graphics: SDL_SetWindowFullscreen(true) failed: " + SDL_GetError());
			return false;
		}
		// SDL_SetWindowFullscreenMode/SDL_SetWindowFullscreen are async on Wayland and macOS — pending window-state
		// changes don't take effect until the compositor confirms. Without SDL_SyncWindow, updateFramebufferInfo()
		// below reads pre-toggle dimensions and the first frame after the transition uses the wrong glViewport.
		SDL_SyncWindow(window.getWindowHandle());
		updateFramebufferInfo();
		setVSync(window.getConfig().vSyncEnabled);
		return true;
	}

	private void storeCurrentWindowPositionAndDisplayMode () {
		windowPosXBeforeFullscreen = window.getPositionX();
		windowPosYBeforeFullscreen = window.getPositionY();
		windowWidthBeforeFullscreen = logicalWidth;
		windowHeightBeforeFullscreen = logicalHeight;
		displayModeBeforeFullscreen = getDisplayMode();
	}

	@Override
	public boolean setWindowedMode (int width, int height) {
		window.getInput().resetPollingStates();
		boolean wasFullscreen = isFullscreen();
		if (wasFullscreen) {
			if (displayModeBeforeFullscreen == null) {
				storeCurrentWindowPositionAndDisplayMode();
			}
			if (!SDL_SetWindowFullscreen(window.getWindowHandle(), false)) {
				System.err.println("Sdl3Graphics: SDL_SetWindowFullscreen(false) failed: " + SDL_GetError());
				return false;
			}
		}
		SDL_SetWindowSize(window.getWindowHandle(), width, height);
		// Re-center if the size changed compared to the last logical size we knew about. On macOS centering must happen _after_
		// the resize, which is what we do here.
		if (!wasFullscreen && (width != logicalWidth || height != logicalHeight)) {
			GridPoint2 newPos = Sdl3ApplicationConfiguration.calculateCenteredWindowPosition((Sdl3Monitor)getMonitor(), width,
				height);
			SDL_SetWindowPosition(window.getWindowHandle(), newPos.x, newPos.y);
		} else if (wasFullscreen) {
			// Restoring from fullscreen: SDL keeps the previous windowed-mode position but if the size changed we still
			// re-center so the window isn't off-screen.
			if (width != windowWidthBeforeFullscreen || height != windowHeightBeforeFullscreen) {
				GridPoint2 newPos = Sdl3ApplicationConfiguration.calculateCenteredWindowPosition((Sdl3Monitor)getMonitor(), width,
					height);
				SDL_SetWindowPosition(window.getWindowHandle(), newPos.x, newPos.y);
			} else {
				SDL_SetWindowPosition(window.getWindowHandle(), windowPosXBeforeFullscreen, windowPosYBeforeFullscreen);
			}
		}
		// Same async-toggle concern as setFullscreenMode — SDL_SetWindowSize/SDL_SetWindowFullscreen(false) are
		// confirmed by the compositor on Wayland/macOS, not synchronously applied.
		SDL_SyncWindow(window.getWindowHandle());
		updateFramebufferInfo();
		return true;
	}

	@Override
	public void setTitle (String title) {
		if (title == null) title = "";
		org.lwjgl.sdl.SDLVideo.SDL_SetWindowTitle(window.getWindowHandle(), title);
	}

	@Override
	public void setUndecorated (boolean undecorated) {
		getWindow().getConfig().setDecorated(!undecorated);
		// SDL_SetWindowBordered takes the "is bordered" boolean — the inverse of "undecorated".
		SDL_SetWindowBordered(window.getWindowHandle(), !undecorated);
	}

	@Override
	public void setResizable (boolean resizable) {
		getWindow().getConfig().setResizable(resizable);
		SDL_SetWindowResizable(window.getWindowHandle(), resizable);
	}

	@Override
	public void setVSync (boolean vsync) {
		getWindow().getConfig().vSyncEnabled = vsync;
		// SDL_GL_SetSwapInterval(1) = vsync on, 0 = off, -1 = adaptive. We only expose the boolean toggle to match the
		// libGDX Graphics contract. Adaptive vsync is opt-in via the manual SDL hint mechanism on Sdl3ApplicationConfiguration.
		if (!SDL_GL_SetSwapInterval(vsync ? 1 : 0)) {
			System.err.println("Sdl3Graphics: SDL_GL_SetSwapInterval failed: " + SDL_GetError());
		}
	}

	/** Sets the target framerate for the application, when using continuous rendering. Must be positive. The cpu sleeps as needed.
	 * Use 0 to never sleep. If there are multiple windows, the value for the first window created is used for all. Default is 0.
	 *
	 * @param fps fps */
	@Override
	public void setForegroundFPS (int fps) {
		getWindow().getConfig().foregroundFPS = fps;
	}

	@Override
	public BufferFormat getBufferFormat () {
		return bufferFormat;
	}

	@Override
	public boolean supportsExtension (String extension) {
		return SDL_GL_ExtensionSupported(extension);
	}

	@Override
	public void setContinuousRendering (boolean isContinuous) {
		this.isContinuous = isContinuous;
	}

	@Override
	public boolean isContinuousRendering () {
		return isContinuous;
	}

	@Override
	public void requestRendering () {
		window.requestRendering();
	}

	@Override
	public boolean isFullscreen () {
		return (org.lwjgl.sdl.SDLVideo.SDL_GetWindowFlags(window.getWindowHandle())
			& org.lwjgl.sdl.SDLVideo.SDL_WINDOW_FULLSCREEN) != 0;
	}

	@Override
	public Cursor newCursor (Pixmap pixmap, int xHotspot, int yHotspot) {
		return new Sdl3Cursor(getWindow(), pixmap, xHotspot, yHotspot);
	}

	@Override
	public void setCursor (Cursor cursor) {
		// SDL_SetCursor is process-global (no window handle). If the cursor's underlying SDL handle is 0 (creation failed in
		// Sdl3Cursor), silently ignore.
		long sdlCursor = ((Sdl3Cursor)cursor).sdlCursor;
		if (sdlCursor != 0L) {
			org.lwjgl.sdl.SDLMouse.SDL_SetCursor(sdlCursor);
		}
	}

	@Override
	public void setSystemCursor (SystemCursor systemCursor) {
		Sdl3Cursor.setSystemCursor(getWindow().getWindowHandle(), systemCursor);
	}

	@Override
	public void dispose () {
		// No per-window callbacks to free — SDL3's event model is pull-based; window events are dispatched from
		// Sdl3Application.pollEvents and routed via Sdl3Window.handleWindowEvent.
	}

	public static class Sdl3DisplayMode extends DisplayMode {
		final long monitorHandle;

		Sdl3DisplayMode (long monitor, int width, int height, int refreshRate, int bitsPerPixel) {
			super(width, height, refreshRate, bitsPerPixel);
			this.monitorHandle = monitor;
		}

		public long getMonitor () {
			return monitorHandle;
		}
	}

	public static class Sdl3Monitor extends Monitor {
		final long monitorHandle;

		Sdl3Monitor (long monitor, int virtualX, int virtualY, String name) {
			super(virtualX, virtualY, name);
			this.monitorHandle = monitor;
		}

		public long getMonitorHandle () {
			return monitorHandle;
		}
	}
}
