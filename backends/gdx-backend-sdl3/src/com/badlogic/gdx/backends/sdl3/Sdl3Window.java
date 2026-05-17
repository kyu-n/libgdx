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

import static org.lwjgl.sdl.SDLPixels.SDL_PIXELFORMAT_ABGR8888;
import static org.lwjgl.sdl.SDLSurface.SDL_AddSurfaceAlternateImage;
import static org.lwjgl.sdl.SDLSurface.SDL_CreateSurfaceFrom;
import static org.lwjgl.sdl.SDLSurface.SDL_DestroySurface;
import static org.lwjgl.sdl.SDLVideo.SDL_FLASH_UNTIL_FOCUSED;
import static org.lwjgl.sdl.SDLVideo.SDL_FlashWindow;
import static org.lwjgl.sdl.SDLVideo.SDL_GL_DestroyContext;
import static org.lwjgl.sdl.SDLVideo.SDL_GL_MakeCurrent;
import static org.lwjgl.sdl.SDLVideo.SDL_GL_SwapWindow;
import static org.lwjgl.sdl.SDLVideo.SDL_DestroyWindow;
import static org.lwjgl.sdl.SDLVideo.SDL_GetWindowID;
import static org.lwjgl.sdl.SDLVideo.SDL_GetWindowPosition;
import static org.lwjgl.sdl.SDLVideo.SDL_HideWindow;
import static org.lwjgl.sdl.SDLVideo.SDL_MaximizeWindow;
import static org.lwjgl.sdl.SDLVideo.SDL_MinimizeWindow;
import static org.lwjgl.sdl.SDLVideo.SDL_RaiseWindow;
import static org.lwjgl.sdl.SDLVideo.SDL_RestoreWindow;
import static org.lwjgl.sdl.SDLVideo.SDL_SetWindowIcon;
import static org.lwjgl.sdl.SDLVideo.SDL_SetWindowMaximumSize;
import static org.lwjgl.sdl.SDLVideo.SDL_SetWindowMinimumSize;
import static org.lwjgl.sdl.SDLVideo.SDL_SetWindowPosition;
import static org.lwjgl.sdl.SDLVideo.SDL_SetWindowTitle;
import static org.lwjgl.sdl.SDLVideo.SDL_ShowWindow;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.sdl.SDL_Surface;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;

public class Sdl3Window implements Disposable {
	private long windowHandle;
	/** Cached {@code SDL_WindowID} (a small uint32 SDL assigns at creation). Resolved once in {@link #create(long, long)} so that
	 * {@code Sdl3Application.findWindowByID} can match incoming events against this field without making a native
	 * {@code SDL_GetWindowID} call per event. Reset to 0 in {@link #dispose()} so post-dispose comparisons can never alias a
	 * reused ID. */
	private int sdlWindowID;
	/** The {@code SDL_GLContext} pointer owned by this window. Each window holds its own context — secondary contexts are created
	 * with {@code SDL_GL_SHARE_WITH_CURRENT_CONTEXT=1} to share textures/buffers with the primary. */
	private long glContext;
	final ApplicationListener listener;
	private final Array<LifecycleListener> lifecycleListeners;
	final Sdl3ApplicationBase application;
	private boolean listenerInitialized = false;
	Sdl3WindowListener windowListener;
	private Sdl3Graphics graphics;
	private Sdl3Input input;
	private final Sdl3ApplicationConfiguration config;
	private final Array<Runnable> runnables = new Array<Runnable>();
	private final Array<Runnable> executedRunnables = new Array<Runnable>();
	private final IntBuffer tmpBuffer;
	private final IntBuffer tmpBuffer2;
	boolean iconified = false;
	boolean focused = false;
	boolean asyncResized = false;
	private boolean requestRendering = false;
	private boolean shouldClose = false;
	private final PauseGate pauseGate = new PauseGate(this::firePause, this::fireResume);

	/** Buffer of file paths accumulated between {@code SDL_EVENT_DROP_BEGIN} and {@code SDL_EVENT_DROP_COMPLETE}. SDL3 emits one
	 * event per file inside a BEGIN/COMPLETE bracket; we batch into this list and flush on COMPLETE so
	 * {@code Sdl3WindowListener.filesDropped(String[])} receives the full set in a single callback. */
	private final List<String> pendingDrops = new ArrayList<String>();

	Sdl3Window (ApplicationListener listener, Array<LifecycleListener> lifecycleListeners, Sdl3ApplicationConfiguration config,
		Sdl3ApplicationBase application) {
		this.listener = listener;
		this.lifecycleListeners = lifecycleListeners;
		this.windowListener = config.windowListener;
		this.config = config;
		this.application = application;
		this.tmpBuffer = BufferUtils.createIntBuffer(1);
		this.tmpBuffer2 = BufferUtils.createIntBuffer(1);
	}

	/** Bind a freshly-created {@code SDL_Window} handle and its {@code SDL_GLContext} to this {@code Sdl3Window}. Called by
	 * {@code Sdl3Application.createWindow} after {@code SDL_CreateWindow} + {@code SDL_GL_CreateContext}. */
	void create (long windowHandle, long glContext) {
		this.windowHandle = windowHandle;
		this.glContext = glContext;
		// Cache SDL_WindowID once at construction. The event loop matches incoming events against this field instead of
		// re-querying SDL per event — measurable savings at high event rates (mouse motion, text input).
		this.sdlWindowID = SDL_GetWindowID(windowHandle);
		this.input = application.createInput(this);
		this.graphics = new Sdl3Graphics(this);
		if (windowListener != null) {
			windowListener.created(this);
		}
	}

	/** @return the {@link ApplicationListener} associated with this window **/
	public ApplicationListener getListener () {
		return listener;
	}

	/** @return the {@link Sdl3WindowListener} set on this window **/
	public Sdl3WindowListener getWindowListener () {
		return windowListener;
	}

	public void setWindowListener (Sdl3WindowListener listener) {
		this.windowListener = listener;
	}

	/** Post a {@link Runnable} to this window's event queue. Use this if you access statics like {@link Gdx#graphics} in your
	 * runnable instead of {@link Application#postRunnable(Runnable)}. */
	public void postRunnable (Runnable runnable) {
		synchronized (runnables) {
			runnables.add(runnable);
		}
	}

	/** Sets the position of the window in logical coordinates. All monitors span a virtual surface together. The coordinates are
	 * relative to the first monitor in the virtual surface. **/
	public void setPosition (int x, int y) {
		SDL_SetWindowPosition(windowHandle, x, y);
	}

	/** @return the window position in logical coordinates. All monitors span a virtual surface together. The coordinates are
	 *         relative to the first monitor in the virtual surface. **/
	public int getPositionX () {
		SDL_GetWindowPosition(windowHandle, tmpBuffer, tmpBuffer2);
		return tmpBuffer.get(0);
	}

	/** @return the window position in logical coordinates. All monitors span a virtual surface together. The coordinates are
	 *         relative to the first monitor in the virtual surface. **/
	public int getPositionY () {
		SDL_GetWindowPosition(windowHandle, tmpBuffer, tmpBuffer2);
		return tmpBuffer2.get(0);
	}

	/** Sets the visibility of the window. Invisible windows will still call their {@link ApplicationListener} */
	public void setVisible (boolean visible) {
		if (visible) {
			SDL_ShowWindow(windowHandle);
		} else {
			SDL_HideWindow(windowHandle);
		}
	}

	/** Closes this window and pauses and disposes the associated {@link ApplicationListener}. */
	public void closeWindow () {
		shouldClose = true;
	}

	/** Minimizes (iconifies) the window. Iconified windows do not call their {@link ApplicationListener} until the window is
	 * restored. */
	public void iconifyWindow () {
		SDL_MinimizeWindow(windowHandle);
	}

	/** Whether the window is iconfieid */
	public boolean isIconified () {
		return iconified;
	}

	/** De-minimizes (de-iconifies) and de-maximizes the window. */
	public void restoreWindow () {
		SDL_RestoreWindow(windowHandle);
	}

	/** Maximizes the window. */
	public void maximizeWindow () {
		SDL_MaximizeWindow(windowHandle);
	}

	/** Brings the window to front and sets input focus. The window should already be visible and not iconified. */
	public void focusWindow () {
		SDL_RaiseWindow(windowHandle);
	}

	public boolean isFocused () {
		return focused;
	}

	/** Sets the icon that will be used in the window's title bar. Has no effect in macOS, which doesn't use window icons.
	 * @param image One or more images. The one closest to the system's desired size will be scaled. Good sizes include 16x16,
	 *           32x32 and 48x48. Pixmap format {@link com.badlogic.gdx.graphics.Pixmap.Format#RGBA8888 RGBA8888} is preferred so
	 *           the images will not have to be copied and converted. The chosen image is copied, and the provided Pixmaps are not
	 *           disposed. */
	public void setIcon (Pixmap... image) {
		setIcon(windowHandle, image);
	}

	private static boolean isMacOS () {
		String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
		return os.contains("mac") || os.contains("darwin");
	}

	static void setIcon (long windowHandle, String[] imagePaths, Files.FileType imageFileType) {
		if (isMacOS()) return;

		Pixmap[] pixmaps = new Pixmap[imagePaths.length];
		for (int i = 0; i < imagePaths.length; i++) {
			pixmaps[i] = new Pixmap(Gdx.files.getFileHandle(imagePaths[i], imageFileType));
		}

		setIcon(windowHandle, pixmaps);

		for (Pixmap pixmap : pixmaps) {
			pixmap.dispose();
		}
	}

	static void setIcon (long windowHandle, Pixmap[] images) {
		if (isMacOS()) return;
		if (images == null || images.length == 0) return;

		SDL_Surface[] surfaces = new SDL_Surface[images.length];
		Pixmap[] conversions = new Pixmap[images.length];
		try {
			for (int i = 0; i < images.length; i++) {
				Pixmap source = images[i];
				Pixmap rgba = source;
				if (source.getFormat() != Pixmap.Format.RGBA8888) {
					rgba = new Pixmap(source.getWidth(), source.getHeight(), Pixmap.Format.RGBA8888);
					rgba.setBlending(Pixmap.Blending.None);
					rgba.drawPixmap(source, 0, 0);
					conversions[i] = rgba;
				}
				// Pixmap RGBA8888 is byte-packed R,G,B,A in memory. On little-endian hosts this matches SDL_PIXELFORMAT_ABGR8888
				// (which reads bytes A,B,G,R as a uint32). libGDX targets little-endian Desktop platforms.
				int pitch = rgba.getWidth() * 4;
				surfaces[i] = SDL_CreateSurfaceFrom(rgba.getWidth(), rgba.getHeight(), SDL_PIXELFORMAT_ABGR8888,
					rgba.getPixels(), pitch);
				if (surfaces[i] == null) continue;
				if (i > 0 && surfaces[0] != null) {
					SDL_AddSurfaceAlternateImage(surfaces[0], surfaces[i]);
				}
			}
			if (surfaces[0] != null) {
				SDL_SetWindowIcon(windowHandle, surfaces[0]);
			}
		} finally {
			// Destroy surfaces FIRST (they reference the Pixmap's pixel buffer via SDL_CreateSurfaceFrom — no copy),
			// then dispose the temporary RGBA conversion pixmaps.
			for (SDL_Surface surface : surfaces) {
				if (surface != null) SDL_DestroySurface(surface);
			}
			for (Pixmap conv : conversions) {
				if (conv != null) conv.dispose();
			}
		}
	}

	public void setTitle (CharSequence title) {
		SDL_SetWindowTitle(windowHandle, title == null ? "" : title.toString());
	}

	/** Sets minimum and maximum size limits for the window. If the window is full screen or not resizable, these limits are
	 * ignored. Use -1 to indicate an unrestricted dimension. */
	public void setSizeLimits (int minWidth, int minHeight, int maxWidth, int maxHeight) {
		setSizeLimits(windowHandle, minWidth, minHeight, maxWidth, maxHeight);
	}

	static void setSizeLimits (long windowHandle, int minWidth, int minHeight, int maxWidth, int maxHeight) {
		// SDL3 has no DONT_CARE sentinel; pass 0 to "remove" a limit per SDL convention.
		SDL_SetWindowMinimumSize(windowHandle, minWidth > -1 ? minWidth : 0, minHeight > -1 ? minHeight : 0);
		SDL_SetWindowMaximumSize(windowHandle, maxWidth > -1 ? maxWidth : 0, maxHeight > -1 ? maxHeight : 0);
	}

	Sdl3Graphics getGraphics () {
		return graphics;
	}

	Sdl3Input getInput () {
		return input;
	}

	public long getWindowHandle () {
		return windowHandle;
	}

	/** @return the cached {@code SDL_WindowID} assigned by SDL at creation. Returns 0 after {@link #dispose()} or before
	 *         {@link #create(long, long)}. Used by {@code Sdl3Application.findWindowByID} for O(n) event routing without per-event
	 *         native calls. */
	public int getSdlWindowID () {
		return sdlWindowID;
	}

	long getGLContext () {
		return glContext;
	}

	void windowHandleChanged (long windowHandle) {
		this.windowHandle = windowHandle;
		input.windowHandleChanged(windowHandle);
	}

	boolean update () {
		if (!listenerInitialized) {
			initializeListener();
		}
		synchronized (runnables) {
			executedRunnables.addAll(runnables);
			runnables.clear();
		}
		for (Runnable runnable : executedRunnables) {
			runnable.run();
		}
		boolean shouldRender = executedRunnables.size > 0 || graphics.isContinuousRendering();
		executedRunnables.clear();

		if (!iconified) input.update();

		synchronized (this) {
			shouldRender |= requestRendering && !iconified;
			requestRendering = false;
		}

		// Async-resize path: in SDL3 the resize event is delivered on the same thread as the main loop, so async should be
		// rare — the path is preserved so external resize hooks driven outside the main loop still get serviced.
		if (asyncResized) {
			asyncResized = false;
			graphics.updateFramebufferInfo();
			graphics.gl20.glViewport(0, 0, graphics.getBackBufferWidth(), graphics.getBackBufferHeight());
			listener.resize(graphics.getWidth(), graphics.getHeight());
			graphics.update();
			listener.render();
			SDL_GL_SwapWindow(windowHandle);
			return true;
		}

		if (shouldRender) {
			graphics.update();
			listener.render();
			SDL_GL_SwapWindow(windowHandle);
		}

		if (!iconified) input.prepareNext();

		return shouldRender;
	}

	void requestRendering () {
		synchronized (this) {
			this.requestRendering = true;
		}
	}

	boolean shouldClose () {
		return shouldClose;
	}

	Sdl3ApplicationConfiguration getConfig () {
		return config;
	}

	boolean isListenerInitialized () {
		return listenerInitialized;
	}

	void initializeListener () {
		if (!listenerInitialized) {
			listener.create();
			listener.resize(graphics.getWidth(), graphics.getHeight());
			listenerInitialized = true;
		}
	}

	void makeCurrent () {
		Gdx.graphics = graphics;
		Gdx.gl32 = graphics.getGL32();
		Gdx.gl31 = Gdx.gl32 != null ? Gdx.gl32 : graphics.getGL31();
		Gdx.gl30 = Gdx.gl31 != null ? Gdx.gl31 : graphics.getGL30();
		Gdx.gl20 = Gdx.gl30 != null ? Gdx.gl30 : graphics.getGL20();
		Gdx.gl = Gdx.gl20;
		Gdx.input = input;
		SDL_GL_MakeCurrent(windowHandle, glContext);
	}

	/** Dispatch one {@code SDL_EVENT_WINDOW_*}-family event to this window's listener and bookkeeping. Called by
	 * {@code Sdl3Application.pollEvents} after routing by {@code windowID}.
	 *
	 * <p>
	 * {@code eventData1} / {@code eventData2} carry the {@code SDL_WindowEvent.data1} / {@code data2} union payload as-is. The
	 * meaning is event-type-specific and only well-defined for a subset of events:
	 * <ul>
	 * <li>{@code SDL_EVENT_WINDOW_RESIZED} / {@code SDL_EVENT_WINDOW_PIXEL_SIZE_CHANGED}: width, height</li>
	 * <li>{@code SDL_EVENT_WINDOW_MOVED}: x, y</li>
	 * <li>{@code SDL_EVENT_WINDOW_DISPLAY_CHANGED}: displayID, unused</li>
	 * </ul>
	 * For focus/iconify/maximize/restore/close-requested both values are 0 and should be ignored. */
	void handleWindowEvent (int eventType, int eventData1, int eventData2) {
		switch (eventType) {
		case org.lwjgl.sdl.SDLEvents.SDL_EVENT_WINDOW_CLOSE_REQUESTED: {
			if (windowListener != null) {
				if (windowListener.closeRequested()) {
					shouldClose = true;
				}
			} else {
				shouldClose = true;
			}
			break;
		}
		case org.lwjgl.sdl.SDLEvents.SDL_EVENT_WINDOW_FOCUS_GAINED: {
			if (config.pauseWhenLostFocus) pauseGate.requestResume();
			if (windowListener != null) windowListener.focusGained();
			focused = true;
			break;
		}
		case org.lwjgl.sdl.SDLEvents.SDL_EVENT_WINDOW_FOCUS_LOST: {
			if (windowListener != null) windowListener.focusLost();
			if (config.pauseWhenLostFocus) pauseGate.requestPause();
			focused = false;
			break;
		}
		case org.lwjgl.sdl.SDLEvents.SDL_EVENT_WINDOW_MINIMIZED: {
			if (windowListener != null) windowListener.iconified(true);
			iconified = true;
			if (config.pauseWhenMinimized) pauseGate.requestPause();
			break;
		}
		case org.lwjgl.sdl.SDLEvents.SDL_EVENT_WINDOW_RESTORED: {
			if (windowListener != null) windowListener.iconified(false);
			iconified = false;
			if (config.pauseWhenMinimized) pauseGate.requestResume();
			break;
		}
		case org.lwjgl.sdl.SDLEvents.SDL_EVENT_WINDOW_MAXIMIZED: {
			if (windowListener != null) windowListener.maximized(true);
			break;
		}
		case org.lwjgl.sdl.SDLEvents.SDL_EVENT_WINDOW_EXPOSED: {
			if (windowListener != null) windowListener.refreshRequested();
			requestRendering();
			break;
		}
		case org.lwjgl.sdl.SDLEvents.SDL_EVENT_WINDOW_MOVED: {
			// No positionChanged hook on Sdl3WindowListener — the event is consumed without dispatch.
			break;
		}
		case org.lwjgl.sdl.SDLEvents.SDL_EVENT_WINDOW_RESIZED:
		case org.lwjgl.sdl.SDLEvents.SDL_EVENT_WINDOW_PIXEL_SIZE_CHANGED: {
			asyncResized = true;
			break;
		}
		default:
			break;
		}
	}

	/** Dispatch a {@code SDL_EVENT_DROP_*} event. {@code BEGIN} clears, {@code FILE} appends, {@code COMPLETE} flushes the batched
	 * paths to {@code Sdl3WindowListener.filesDropped(String[])}. */
	void handleDropEvent (int eventType, String data) {
		switch (eventType) {
		case org.lwjgl.sdl.SDLEvents.SDL_EVENT_DROP_BEGIN:
			pendingDrops.clear();
			break;
		case org.lwjgl.sdl.SDLEvents.SDL_EVENT_DROP_FILE:
			if (data != null) pendingDrops.add(data);
			break;
		case org.lwjgl.sdl.SDLEvents.SDL_EVENT_DROP_COMPLETE:
			if (windowListener != null && !pendingDrops.isEmpty()) {
				windowListener.filesDropped(pendingDrops.toArray(new String[0]));
			}
			pendingDrops.clear();
			break;
		default:
			break;
		}
	}

	@Override
	public void dispose () {
		listener.pause();
		listener.dispose();
		Sdl3Cursor.dispose(this);
		graphics.dispose();
		input.dispose();
		if (glContext != 0) {
			// SDL3 leaves the behavior of SDL_GL_DestroyContext undefined when
			// the context is still current on this thread. Some Intel/Mesa
			// drivers crash. Un-bind explicitly before destroy.
			SDL_GL_MakeCurrent(0L, 0L);
			SDL_GL_DestroyContext(glContext);
			glContext = 0;
		}
		if (windowHandle != 0) {
			SDL_DestroyWindow(windowHandle);
			windowHandle = 0;
		}
		// Clear the cached ID after the handle is gone so any late event lookup can't accidentally match a recycled SDL_WindowID
		// against this disposed window.
		sdlWindowID = 0;
	}

	@Override
	public int hashCode () {
		final int prime = 31;
		int result = 1;
		result = prime * result + (int)(windowHandle ^ (windowHandle >>> 32));
		return result;
	}

	@Override
	public boolean equals (Object obj) {
		if (this == obj) return true;
		if (obj == null) return false;
		if (getClass() != obj.getClass()) return false;
		Sdl3Window other = (Sdl3Window)obj;
		if (windowHandle != other.windowHandle) return false;
		return true;
	}

	public void flash () {
		SDL_FlashWindow(windowHandle, SDL_FLASH_UNTIL_FOCUSED);
	}

	private void firePause () {
		synchronized (lifecycleListeners) {
			for (LifecycleListener l : lifecycleListeners)
				l.pause();
		}
		listener.pause();
	}

	private void fireResume () {
		synchronized (lifecycleListeners) {
			for (LifecycleListener l : lifecycleListeners)
				l.resume();
		}
		listener.resume();
	}

	/** Idempotent pause/resume dispatcher. Multiple pause requests fire the listener once; same for resume.
	 * Deduplicates FOCUS_LOST+MINIMIZED pairs that arrive together on Windows. Not thread-safe — call only
	 * from the SDL event-pump thread. */
	static final class PauseGate {
		private boolean paused;
		private final Runnable onPause, onResume;

		PauseGate (Runnable onPause, Runnable onResume) {
			this.onPause = onPause;
			this.onResume = onResume;
		}

		void requestPause () {
			if (!paused) {
				paused = true;
				onPause.run();
			}
		}

		void requestResume () {
			if (paused) {
				paused = false;
				onResume.run();
			}
		}
	}
}
