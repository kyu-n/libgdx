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
import static org.lwjgl.sdl.SDLEvents.SDL_EVENT_DROP_BEGIN;
import static org.lwjgl.sdl.SDLEvents.SDL_EVENT_DROP_COMPLETE;
import static org.lwjgl.sdl.SDLEvents.SDL_EVENT_DROP_FILE;
import static org.lwjgl.sdl.SDLEvents.SDL_EVENT_KEY_DOWN;
import static org.lwjgl.sdl.SDLEvents.SDL_EVENT_KEY_UP;
import static org.lwjgl.sdl.SDLEvents.SDL_EVENT_MOUSE_BUTTON_DOWN;
import static org.lwjgl.sdl.SDLEvents.SDL_EVENT_MOUSE_BUTTON_UP;
import static org.lwjgl.sdl.SDLEvents.SDL_EVENT_MOUSE_MOTION;
import static org.lwjgl.sdl.SDLEvents.SDL_EVENT_MOUSE_WHEEL;
import static org.lwjgl.sdl.SDLEvents.SDL_EVENT_QUIT;
import static org.lwjgl.sdl.SDLEvents.SDL_EVENT_TEXT_INPUT;
import static org.lwjgl.sdl.SDLEvents.SDL_EVENT_WINDOW_FIRST;
import static org.lwjgl.sdl.SDLEvents.SDL_EVENT_WINDOW_LAST;
import static org.lwjgl.sdl.SDLEvents.SDL_PollEvent;
import static org.lwjgl.sdl.SDLHints.SDL_HINT_VIDEO_DRIVER;
import static org.lwjgl.sdl.SDLInit.SDL_INIT_EVENTS;
import static org.lwjgl.sdl.SDLInit.SDL_INIT_VIDEO;
import static org.lwjgl.sdl.SDLInit.SDL_Init;
import static org.lwjgl.sdl.SDLInit.SDL_Quit;
import static org.lwjgl.sdl.SDLInit.SDL_SetAppMetadata;
import static org.lwjgl.sdl.SDLVideo.SDL_CreateWindow;
import static org.lwjgl.sdl.SDLVideo.SDL_GL_CONTEXT_DEBUG_FLAG;
import static org.lwjgl.sdl.SDLVideo.SDL_GL_CONTEXT_FLAGS;
import static org.lwjgl.sdl.SDLVideo.SDL_GL_CONTEXT_MAJOR_VERSION;
import static org.lwjgl.sdl.SDLVideo.SDL_GL_CONTEXT_MINOR_VERSION;
import static org.lwjgl.sdl.SDLVideo.SDL_GL_CONTEXT_PROFILE_COMPATIBILITY;
import static org.lwjgl.sdl.SDLVideo.SDL_GL_CONTEXT_PROFILE_CORE;
import static org.lwjgl.sdl.SDLVideo.SDL_GL_CONTEXT_PROFILE_MASK;
import static org.lwjgl.sdl.SDLVideo.SDL_GL_CreateContext;
import static org.lwjgl.sdl.SDLVideo.SDL_GL_MakeCurrent;
import static org.lwjgl.sdl.SDLVideo.SDL_GL_ResetAttributes;
import static org.lwjgl.sdl.SDLVideo.SDL_GL_SHARE_WITH_CURRENT_CONTEXT;
import static org.lwjgl.sdl.SDLVideo.SDL_GL_SetAttribute;
import static org.lwjgl.sdl.SDLVideo.SDL_GL_SetSwapInterval;
import static org.lwjgl.sdl.SDLVideo.SDL_GL_SwapWindow;
import static org.lwjgl.sdl.SDLVideo.SDL_MaximizeWindow;
import static org.lwjgl.sdl.SDLVideo.SDL_SetWindowFullscreenMode;
import static org.lwjgl.sdl.SDLVideo.SDL_SetWindowPosition;
import static org.lwjgl.sdl.SDLVideo.SDL_WINDOWPOS_CENTERED;
import static org.lwjgl.sdl.SDLVideo.SDL_WINDOW_BORDERLESS;
import static org.lwjgl.sdl.SDLVideo.SDL_WINDOW_FULLSCREEN;
import static org.lwjgl.sdl.SDLVideo.SDL_WINDOW_HIDDEN;
import static org.lwjgl.sdl.SDLVideo.SDL_WINDOW_MAXIMIZED;
import static org.lwjgl.sdl.SDLVideo.SDL_WINDOW_OPENGL;
import static org.lwjgl.sdl.SDLVideo.SDL_WINDOW_RESIZABLE;
import static org.lwjgl.sdl.SDLVideo.SDL_WINDOW_TRANSPARENT;

import java.io.File;
import java.io.PrintStream;
import java.nio.IntBuffer;

import com.badlogic.gdx.ApplicationLogger;
import com.badlogic.gdx.backends.sdl3.audio.Sdl3Audio;
import com.badlogic.gdx.backends.sdl3.audio.OpenALSdl3Audio;
import com.badlogic.gdx.graphics.glutils.GLVersion;

import com.badlogic.gdx.utils.*;
import org.lwjgl.opengl.AMDDebugOutput;
import org.lwjgl.opengl.ARBDebugOutput;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL43;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.opengl.GLUtil;
import org.lwjgl.opengl.KHRDebug;
import org.lwjgl.sdl.SDL_DisplayMode;
import org.lwjgl.sdl.SDL_Event;
import org.lwjgl.system.Callback;
import org.lwjgl.system.MemoryStack;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Audio;
import com.badlogic.gdx.Files;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.LifecycleListener;
import com.badlogic.gdx.Net;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.backends.sdl3.audio.mock.MockAudio;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Clipboard;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.ObjectMap;

public class Sdl3Application implements Sdl3ApplicationBase {
	private final Sdl3ApplicationConfiguration config;
	final Array<Sdl3Window> windows = new Array<Sdl3Window>();
	private volatile Sdl3Window currentWindow;
	private Sdl3Audio audio;
	private final Files files;
	private final Net net;
	private final ObjectMap<String, Preferences> preferences = new ObjectMap<String, Preferences>();
	private final Sdl3Clipboard clipboard;
	private int logLevel = LOG_INFO;
	private ApplicationLogger applicationLogger;
	private volatile boolean running = true;
	private final Array<Runnable> runnables = new Array<Runnable>();
	private final Array<Runnable> executedRunnables = new Array<Runnable>();
	private final Array<LifecycleListener> lifecycleListeners = new Array<LifecycleListener>();
	private static boolean sdlInitialized;
	private static GLVersion glVersion;
	private static Callback glDebugCallback;
	private final Sync sync;

	/** Initialize SDL3 once per process. Subsequent invocations are a no-op. SDL has its own internal init refcount via
	 * SDL_InitSubSystem but we want a single deterministic moment of truth so static config is applied exactly once. */
	static void initializeSDL () {
		if (!sdlInitialized) {
			Sdl3NativesLoader.load();
			// SDL_INIT_AUDIO intentionally omitted — audio is handled by lwjgl-openal in OpenALSdl3Audio,
			// not SDL3's audio subsystem. Avoids the extra init cost and any device probing SDL3 would do.
			if (!SDL_Init(SDL_INIT_VIDEO | SDL_INIT_EVENTS)) {
				throw new GdxRuntimeException("Unable to initialize SDL3: " + SDL_GetError());
			}
			sdlInitialized = true;
		}
	}

	/** On macOS, the JVM must be launched with {@code -XstartOnFirstThread} or any UI library that calls into Cocoa (SDL3
	 * included) deadlocks on event-pump entry. Fails fast with platform-specific guidance rather than silently deadlocking or
	 * auto-relaunching the JVM (which is fragile across launchers, IDE-runs, and native-image binaries). */
	private static void ensureMacosStartOnFirstThread () {
		String osName = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
		if (!osName.contains("mac") && !osName.contains("darwin")) return;

		// The JVM auto-sets JAVA_STARTED_ON_FIRST_THREAD_<pid> when -XstartOnFirstThread is given.
		long pid = ProcessHandle.current().pid();
		if (System.getenv("JAVA_STARTED_ON_FIRST_THREAD_" + pid) != null) return;

		// Belt-and-braces: also accept the flag if it appears in JVM input args directly.
		for (String arg : java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments()) {
			if ("-XstartOnFirstThread".equals(arg)) return;
		}

		boolean inNativeImage = System.getProperty("org.graalvm.nativeimage.kind") != null
			|| System.getProperty("org.graalvm.nativeimage.imagecode") != null;

		StringBuilder msg = new StringBuilder("\n*** Sdl3Application: macOS startup error ***\n");
		msg.append("On macOS, the JVM must be launched with -XstartOnFirstThread for SDL3 (and any Cocoa UI) to function.\n");
		if (inNativeImage) {
			msg.append("Detected GraalVM native-image runtime. Pass the flag at build time, e.g.:\n");
			msg.append("    graalvmNative { binaries { main { jvmArgs.add('-XstartOnFirstThread') } } }\n");
		} else {
			msg.append("Re-run the JVM with:\n");
			msg.append("    java -XstartOnFirstThread -cp <classpath> <MainClass>\n");
		}
		msg.append("********************************************\n");
		System.err.println(msg);
		throw new IllegalStateException("Sdl3Application requires -XstartOnFirstThread on macOS; see stderr.");
	}

	public Sdl3Application (ApplicationListener listener) {
		this(listener, new Sdl3ApplicationConfiguration());
	}

	public Sdl3Application (ApplicationListener listener, Sdl3ApplicationConfiguration config) {
		ensureMacosStartOnFirstThread();
		// Opt-in Wayland fallback: if the consumer asked to prefer X11 on Linux and hasn't already set
		// SDL_HINT_VIDEO_DRIVER explicitly, default it to "x11,wayland". Mindustry/Arc precedent for
		// compositors where SDL3's default driver pick misbehaves.
		if (config.preferX11OnLinux && System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("linux")
			&& !config.sdlManualHints.containsKey(SDL_HINT_VIDEO_DRIVER)) {
			config.sdlManualHints.put(SDL_HINT_VIDEO_DRIVER, "x11,wayland");
		}
		// Apply string hints BEFORE SDL_Init so video driver / app_id hints take effect.
		config.applyManualStringHints();
		// SDL3-blessed app identity: drives Wayland compositor .desktop matching, macOS dock identity,
		// Windows taskbar grouping. Set before SDL_Init per SDL3 docs. Null identifier skips the call.
		if (config.appIdentifier != null) {
			// Sdl3ApplicationLogger isn't installed yet (initializeSDL/setApplicationLogger happen below),
			// so route this diagnostic through System.err directly. The metadata call is non-fatal — we
			// just want a breadcrumb if a Wayland compositor refuses our app id.
			if (!SDL_SetAppMetadata(config.appName, config.appVersion, config.appIdentifier)) {
				System.err.println("Sdl3Application: SDL_SetAppMetadata failed: " + SDL_GetError());
			}
		}
		initializeSDL();
		setApplicationLogger(new Sdl3ApplicationLogger());

		this.config = config = Sdl3ApplicationConfiguration.copy(config);
		if (config.title == null) config.title = listener.getClass().getSimpleName();

		Gdx.app = this;
		if (!config.disableAudio) {
			try {
				this.audio = createAudio(config);
			} catch (Throwable t) {
				log("Sdl3Application", "Couldn't initialize audio, disabling audio", t);
				this.audio = new MockAudio();
			}
		} else {
			this.audio = new MockAudio();
		}
		Gdx.audio = audio;
		this.files = Gdx.files = createFiles();
		this.net = Gdx.net = new Sdl3Net(config);
		this.clipboard = new Sdl3Clipboard();

		this.sync = new Sync();

		Sdl3Window window = createWindow(config, listener, 0);
		windows.add(window);
		try {
			loop();
			cleanupWindows();
		} catch (Throwable t) {
			if (t instanceof RuntimeException)
				throw (RuntimeException)t;
			else
				throw new GdxRuntimeException(t);
		} finally {
			cleanup();
		}
	}

	protected void loop () {
		Array<Sdl3Window> closedWindows = new Array<Sdl3Window>();
		while (running && windows.size > 0) {
			// FIXME put it on a separate thread
			audio.update();

			// Pull events from SDL3 BEFORE rendering so close/resize/focus updates are reflected this frame.
			pollEvents();

			boolean haveWindowsRendered = false;
			closedWindows.clear();
			int targetFramerate = -2;
			for (Sdl3Window window : windows) {
				if (currentWindow != window) {
					window.makeCurrent();
					currentWindow = window;
				}
				if (targetFramerate == -2) targetFramerate = window.getConfig().foregroundFPS;
				synchronized (lifecycleListeners) {
					haveWindowsRendered |= window.update();
				}
				if (window.shouldClose()) {
					closedWindows.add(window);
				}
			}

			boolean shouldRequestRendering;
			synchronized (runnables) {
				shouldRequestRendering = runnables.size > 0;
				executedRunnables.clear();
				executedRunnables.addAll(runnables);
				runnables.clear();
			}
			for (Runnable runnable : executedRunnables) {
				runnable.run();
			}
			if (shouldRequestRendering) {
				// Must follow Runnables execution so changes done by Runnables are reflected
				// in the following render.
				for (Sdl3Window window : windows) {
					if (!window.getGraphics().isContinuousRendering()) window.requestRendering();
				}
			}

			for (Sdl3Window closedWindow : closedWindows) {
				if (windows.size == 1) {
					// Lifecycle listener methods have to be called before ApplicationListener methods. The
					// application will be disposed when _all_ windows have been disposed, which is the case,
					// when there is only 1 window left, which is in the process of being disposed.
					synchronized (lifecycleListeners) {
						for (int i = lifecycleListeners.size - 1; i >= 0; i--) {
							LifecycleListener l = lifecycleListeners.get(i);
							l.pause();
							l.dispose();
						}
						lifecycleListeners.clear();
					}
				}
				closedWindow.dispose();

				windows.removeValue(closedWindow, false);
			}

			if (!haveWindowsRendered) {
				// Sleep a few milliseconds in case no rendering was requested
				// with continuous rendering disabled.
				try {
					Thread.sleep(1000 / config.idleFPS);
				} catch (InterruptedException e) {
					// ignore
				}
			} else if (targetFramerate > 0) {
				sync.sync(targetFramerate); // sleep as needed to meet the target framerate
			}
		}
	}

	/** Drain SDL3's event queue and dispatch each event to the appropriate {@link Sdl3Window} or {@link DefaultSdl3Input}.
	 * {@code SDL_Event} is stack-allocated each frame — caching it as a field would violate the {@link MemoryStack} lifetime
	 * contract. */
	private void pollEvents () {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			SDL_Event ev = SDL_Event.malloc(stack);
			while (SDL_PollEvent(ev)) {
				int type = ev.type();
				if (type == SDL_EVENT_QUIT) {
					running = false;
					continue;
				}
				if (type >= SDL_EVENT_WINDOW_FIRST && type <= SDL_EVENT_WINDOW_LAST) {
					Sdl3Window w = findWindowByID(ev.window().windowID());
					if (w != null) {
						w.handleWindowEvent(type, ev.window().data1(), ev.window().data2());
					}
					continue;
				}
				switch (type) {
				case SDL_EVENT_DROP_BEGIN:
				case SDL_EVENT_DROP_COMPLETE: {
					Sdl3Window w = findWindowByID(ev.drop().windowID());
					if (w != null) w.handleDropEvent(type, null);
					break;
				}
				case SDL_EVENT_DROP_FILE: {
					Sdl3Window w = findWindowByID(ev.drop().windowID());
					if (w != null) w.handleDropEvent(type, ev.drop().dataString());
					break;
				}
				case SDL_EVENT_KEY_DOWN:
				case SDL_EVENT_KEY_UP: {
					Sdl3Window w = findWindowByID(ev.key().windowID());
					if (w != null) routeInput(w, ev);
					break;
				}
				case SDL_EVENT_TEXT_INPUT: {
					Sdl3Window w = findWindowByID(ev.text().windowID());
					if (w != null) routeInput(w, ev);
					break;
				}
				case SDL_EVENT_MOUSE_MOTION: {
					Sdl3Window w = findWindowByID(ev.motion().windowID());
					if (w != null) routeInput(w, ev);
					break;
				}
				case SDL_EVENT_MOUSE_BUTTON_DOWN:
				case SDL_EVENT_MOUSE_BUTTON_UP: {
					Sdl3Window w = findWindowByID(ev.button().windowID());
					if (w != null) routeInput(w, ev);
					break;
				}
				case SDL_EVENT_MOUSE_WHEEL: {
					Sdl3Window w = findWindowByID(ev.wheel().windowID());
					if (w != null) routeInput(w, ev);
					break;
				}
				default:
					break;
				}
			}
		}
	}

	/** Route an input-domain SDL_Event to the target window's input handler. P3 leaves the actual translation deferred to P5
	 * (DefaultSdl3Input is still keyboard/mouse-stubbed); this method exists so the dispatch path is wired now and P5 only has to
	 * fill in the body. */
	private void routeInput (Sdl3Window window, SDL_Event event) {
		Sdl3Input in = window.getInput();
		if (in instanceof DefaultSdl3Input) {
			((DefaultSdl3Input)in).handleSDLEvent(event);
		}
	}

	/** Look up a window by its cached {@code SDL_WindowID}. The ID is captured once at window creation in
	 * {@link Sdl3Window#create}, so dispatch is a plain int comparison. */
	private Sdl3Window findWindowByID (int sdlWindowID) {
		for (int i = 0; i < windows.size; i++) {
			Sdl3Window w = windows.get(i);
			if (w.getSdlWindowID() == sdlWindowID) return w;
		}
		return null;
	}

	protected void cleanupWindows () {
		synchronized (lifecycleListeners) {
			for (LifecycleListener lifecycleListener : lifecycleListeners) {
				lifecycleListener.pause();
				lifecycleListener.dispose();
			}
		}
		for (Sdl3Window window : windows) {
			window.dispose();
		}
		windows.clear();
	}

	protected void cleanup () {
		if (sdlInitialized) Sdl3Cursor.disposeSystemCursors();
		if (audio != null) audio.dispose();
		if (glDebugCallback != null) {
			glDebugCallback.free();
			glDebugCallback = null;
		}
		if (sdlInitialized) {
			SDL_Quit();
			sdlInitialized = false;
		}
	}

	@Override
	public ApplicationListener getApplicationListener () {
		return currentWindow.getListener();
	}

	@Override
	public Graphics getGraphics () {
		return currentWindow.getGraphics();
	}

	@Override
	public Audio getAudio () {
		return audio;
	}

	@Override
	public Input getInput () {
		return currentWindow.getInput();
	}

	@Override
	public Files getFiles () {
		return files;
	}

	@Override
	public Net getNet () {
		return net;
	}

	@Override
	public void debug (String tag, String message) {
		if (logLevel >= LOG_DEBUG) getApplicationLogger().debug(tag, message);
	}

	@Override
	public void debug (String tag, String message, Throwable exception) {
		if (logLevel >= LOG_DEBUG) getApplicationLogger().debug(tag, message, exception);
	}

	@Override
	public void log (String tag, String message) {
		if (logLevel >= LOG_INFO) getApplicationLogger().log(tag, message);
	}

	@Override
	public void log (String tag, String message, Throwable exception) {
		if (logLevel >= LOG_INFO) getApplicationLogger().log(tag, message, exception);
	}

	@Override
	public void error (String tag, String message) {
		if (logLevel >= LOG_ERROR) getApplicationLogger().error(tag, message);
	}

	@Override
	public void error (String tag, String message, Throwable exception) {
		if (logLevel >= LOG_ERROR) getApplicationLogger().error(tag, message, exception);
	}

	@Override
	public void setLogLevel (int logLevel) {
		this.logLevel = logLevel;
	}

	@Override
	public int getLogLevel () {
		return logLevel;
	}

	@Override
	public void setApplicationLogger (ApplicationLogger applicationLogger) {
		this.applicationLogger = applicationLogger;
	}

	@Override
	public ApplicationLogger getApplicationLogger () {
		return applicationLogger;
	}

	/** Set the polling rate during idle (non-rendering) time. Mutates the live config. Must be positive.
	 * Parity with {@link Sdl3Graphics#setForegroundFPS(int)}, which has been live-mutable since the backend was added. */
	public void setIdleFPS (int fps) {
		if (fps <= 0) throw new IllegalArgumentException("idleFPS must be positive, got " + fps);
		config.idleFPS = fps;
	}

	public int getIdleFPS () {
		return config.idleFPS;
	}

	@Override
	public ApplicationType getType () {
		return ApplicationType.Desktop;
	}

	@Override
	public int getVersion () {
		return 0;
	}

	@Override
	public long getJavaHeap () {
		return Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
	}

	@Override
	public long getNativeHeap () {
		return getJavaHeap();
	}

	@Override
	public Preferences getPreferences (String name) {
		if (preferences.containsKey(name)) {
			return preferences.get(name);
		} else {
			Preferences prefs = new Sdl3Preferences(
				new Sdl3FileHandle(new File(config.preferencesDirectory, name), config.preferencesFileType));
			preferences.put(name, prefs);
			return prefs;
		}
	}

	@Override
	public Clipboard getClipboard () {
		return clipboard;
	}

	@Override
	public void postRunnable (Runnable runnable) {
		synchronized (runnables) {
			runnables.add(runnable);
		}
	}

	@Override
	public void exit () {
		running = false;
	}

	@Override
	public void addLifecycleListener (LifecycleListener listener) {
		synchronized (lifecycleListeners) {
			lifecycleListeners.add(listener);
		}
	}

	@Override
	public void removeLifecycleListener (LifecycleListener listener) {
		synchronized (lifecycleListeners) {
			lifecycleListeners.removeValue(listener, true);
		}
	}

	@Override
	public Sdl3Audio createAudio (Sdl3ApplicationConfiguration config) {
		return new OpenALSdl3Audio(config.audioDeviceSimultaneousSources, config.audioDeviceBufferCount,
			config.audioDeviceBufferSize);
	}

	@Override
	public Sdl3Input createInput (Sdl3Window window) {
		return new DefaultSdl3Input(window);
	}

	protected Files createFiles () {
		return new Sdl3Files();
	}

	/** Creates a new {@link Sdl3Window} using the provided listener and {@link Sdl3WindowConfiguration}.
	 *
	 * This function only just instantiates a {@link Sdl3Window} and returns immediately. The actual window creation is postponed
	 * with {@link Application#postRunnable(Runnable)} until after all existing windows are updated. */
	public Sdl3Window newWindow (ApplicationListener listener, Sdl3WindowConfiguration config) {
		Sdl3ApplicationConfiguration appConfig = Sdl3ApplicationConfiguration.copy(this.config);
		appConfig.setWindowConfiguration(config);
		if (appConfig.title == null) appConfig.title = listener.getClass().getSimpleName();
		return createWindow(appConfig, listener, windows.get(0).getWindowHandle());
	}

	private Sdl3Window createWindow (final Sdl3ApplicationConfiguration config, ApplicationListener listener,
		final long sharedContext) {
		final Sdl3Window window = new Sdl3Window(listener, lifecycleListeners, config, this);
		if (sharedContext == 0) {
			// the main window is created immediately
			createWindow(window, config, sharedContext);
		} else {
			// creation of additional windows is deferred to avoid GL context trouble
			postRunnable(new Runnable() {
				public void run () {
					createWindow(window, config, sharedContext);
					windows.add(window);
				}
			});
		}
		return window;
	}

	void createWindow (Sdl3Window window, Sdl3ApplicationConfiguration config, long sharedContext) {
		long[] handles = createSdlWindow(config, sharedContext);
		long windowHandle = handles[0];
		long glContext = handles[1];
		window.create(windowHandle, glContext);
		window.setVisible(config.initialVisible);

		// Clear-and-swap twice so the first frame the user sees on Wayland-style compositors isn't garbage.
		for (int i = 0; i < 2; i++) {
			window.getGraphics().gl20.glClearColor(config.initialBackgroundColor.r, config.initialBackgroundColor.g,
				config.initialBackgroundColor.b, config.initialBackgroundColor.a);
			window.getGraphics().gl20.glClear(GL11.GL_COLOR_BUFFER_BIT);
			SDL_GL_SwapWindow(windowHandle);
		}

		if (currentWindow != null) {
			// the call above to createSdlWindow switches the OpenGL context to the newly created window,
			// ensure that the invariant "currentWindow is the window with the current active OpenGL context" holds
			currentWindow.makeCurrent();
		}
	}

	/** Create the underlying {@code SDL_Window} + {@code SDL_GLContext} pair. Returns {@code {windowHandle, glContext}}.
	 *
	 * <p>
	 * Shared-context handling: SDL3 exposes the GL attribute {@code SDL_GL_SHARE_WITH_CURRENT_CONTEXT}. We set that attribute
	 * right before {@code SDL_GL_CreateContext} when {@code sharedContextWindow != 0} (the primary window must already be current
	 * at that moment, which {@code loop()} guarantees because secondary-window creation is deferred via postRunnable). The
	 * attribute is reset to 0 afterwards so it doesn't leak into the next context. */
	static long[] createSdlWindow (Sdl3ApplicationConfiguration config, long sharedContextWindow) {
		// Reset attributes so a previous window's hints don't leak into this one.
		SDL_GL_ResetAttributes();

		// Apply back-buffer attributes (RGBA / depth / stencil / MSAA) before context creation.
		config.applyBackBufferAttributes();

		// Context profile / version selection happens inside createGLContextWithFallback so each ladder attempt sets its
		// own major/minor/profile triple. Debug flag is applied per-attempt as well.

		// Ensure SDL_GL_SHARE_WITH_CURRENT_CONTEXT defaults to 0 unless we're about to create a shared context below. The
		// fallback ladder will re-set it based on sharedContextWindow != 0 and reset it again in its finally block.
		SDL_GL_SetAttribute(SDL_GL_SHARE_WITH_CURRENT_CONTEXT, 0);

		// Apply manual GL attribute overrides last so the user can override defaults if needed.
		config.executeWindowHintOverrides();

		// Compute window flags: OPENGL is mandatory for an OpenGL backend; HIDDEN until we call SDL_ShowWindow after
		// the create-and-clear handshake so the user doesn't see a single uncleared frame on slow compositors.
		long flags = SDL_WINDOW_OPENGL | SDL_WINDOW_HIDDEN;
		if (config.windowResizable) flags |= SDL_WINDOW_RESIZABLE;
		if (config.windowMaximized) flags |= SDL_WINDOW_MAXIMIZED;
		if (!config.windowDecorated) flags |= SDL_WINDOW_BORDERLESS;
		if (config.transparentFramebuffer) flags |= SDL_WINDOW_TRANSPARENT;
		if (config.fullscreenMode != null) flags |= SDL_WINDOW_FULLSCREEN;

		int w, h;
		if (config.fullscreenMode != null) {
			w = config.fullscreenMode.width;
			h = config.fullscreenMode.height;
		} else {
			w = config.windowWidth;
			h = config.windowHeight;
		}

		long windowHandle = SDL_CreateWindow(config.title != null ? config.title : "", w, h, flags);
		if (windowHandle == 0) {
			throw new GdxRuntimeException("Couldn't create window: " + SDL_GetError());
		}

		if (config.fullscreenMode != null) {
			Sdl3Graphics.Sdl3DisplayMode mode = config.fullscreenMode;
			try (MemoryStack stack = MemoryStack.stackPush()) {
				SDL_DisplayMode sdlMode = SDL_DisplayMode.malloc(stack);
				sdlMode.displayID((int)mode.monitorHandle);
				sdlMode.w(mode.width);
				sdlMode.h(mode.height);
				sdlMode.refresh_rate(mode.refreshRate);
				if (!SDL_SetWindowFullscreenMode(windowHandle, sdlMode)) {
					System.err.println("Sdl3Application: SDL_SetWindowFullscreenMode failed: " + SDL_GetError());
				}
			}
		}

		Sdl3Window.setSizeLimits(windowHandle, config.windowMinWidth, config.windowMinHeight, config.windowMaxWidth,
			config.windowMaxHeight);

		if (config.fullscreenMode == null) {
			if (config.windowX == -1 && config.windowY == -1) {
				SDL_SetWindowPosition(windowHandle, SDL_WINDOWPOS_CENTERED, SDL_WINDOWPOS_CENTERED);
			} else {
				SDL_SetWindowPosition(windowHandle, config.windowX, config.windowY);
			}
			if (config.windowMaximized) {
				SDL_MaximizeWindow(windowHandle);
			}
		}

		if (config.windowIconPaths != null) {
			Sdl3Window.setIcon(windowHandle, config.windowIconPaths, config.windowIconFileType);
		}

		// Shared context (secondary windows): the primary window's GL context must be current right now (the loop
		// guarantees this because newWindow() postRunnables createWindow into the main thread). The fallback ladder
		// brackets the SHARE attribute set with a try/finally so an exception inside SDL_GL_CreateContext can't leave
		// the attribute leaked at 1 for the next createSdlWindow() call.
		long glContext = createGLContextWithFallback(windowHandle, config, sharedContextWindow);
		if (glContext == 0) {
			throw new GdxRuntimeException("Couldn't create OpenGL context: " + SDL_GetError());
		}
		SDL_GL_MakeCurrent(windowHandle, glContext);

		if (!SDL_GL_SetSwapInterval(config.vSyncEnabled ? 1 : 0)) {
			System.err.println("Sdl3Application: SDL_GL_SetSwapInterval failed: " + SDL_GetError());
		}

		GL.createCapabilities();

		initiateGL();
		if (!glVersion.isVersionEqualToOrHigher(2, 0))
			throw new GdxRuntimeException("OpenGL 2.0 or higher with the FBO extension is required. OpenGL version: "
				+ glVersion.getVersionString() + "\n" + glVersion.getDebugVersionString());

		if (!supportsFBO()) {
			throw new GdxRuntimeException("OpenGL 2.0 or higher with the FBO extension is required. OpenGL version: "
				+ glVersion.getVersionString() + ", FBO extension: false\n" + glVersion.getDebugVersionString());
		}

		if (config.debug) {
			glDebugCallback = GLUtil.setupDebugMessageCallback(config.debugStream);
			setGLDebugMessageControl(GLDebugMessageSeverity.NOTIFICATION, false);
		}

		return new long[] {windowHandle, glContext};
	}

	/** Attempt context creation with a ladder of (major, minor, profile) triples, returning the first successful
	 * context handle, or 0 if all attempts fail. Preserves the shared-context attribute across retries when
	 * {@code sharedContextWindow != 0}. */
	private static long createGLContextWithFallback (long windowHandle, Sdl3ApplicationConfiguration config,
		long sharedContextWindow) {
		int[][] ladder = chooseLadder(config);
		SDL_GL_SetAttribute(SDL_GL_SHARE_WITH_CURRENT_CONTEXT, sharedContextWindow != 0 ? 1 : 0);
		try {
			for (int[] attempt : ladder) {
				applyProfileAttributes(config, attempt[0], attempt[1], attempt[2]);
				long ctx = SDL_GL_CreateContext(windowHandle);
				if (ctx != 0) return ctx;
				System.err.println("Sdl3Application: GL " + attempt[0] + "." + attempt[1] + " context creation failed: "
					+ SDL_GetError());
			}
			return 0;
		} finally {
			SDL_GL_SetAttribute(SDL_GL_SHARE_WITH_CURRENT_CONTEXT, 0);
		}
	}

	private static int[][] chooseLadder (Sdl3ApplicationConfiguration config) {
		// Trailing triples are progressively more permissive. Each row is {major, minor, profileMask}.
		if (config.glEmulation == Sdl3ApplicationConfiguration.GLEmulation.GL30
			|| config.glEmulation == Sdl3ApplicationConfiguration.GLEmulation.GL31
			|| config.glEmulation == Sdl3ApplicationConfiguration.GLEmulation.GL32) {
			return new int[][] { //
				{config.gles30ContextMajorVersion, config.gles30ContextMinorVersion, SDL_GL_CONTEXT_PROFILE_CORE}, //
				{3, 2, SDL_GL_CONTEXT_PROFILE_CORE}, //
				{3, 0, SDL_GL_CONTEXT_PROFILE_CORE}, //
				{2, 1, SDL_GL_CONTEXT_PROFILE_COMPATIBILITY}};
		}
		// GL20 (or anything else): try 2.1 then 2.0 in compatibility profile.
		return new int[][] { //
			{2, 1, SDL_GL_CONTEXT_PROFILE_COMPATIBILITY}, //
			{2, 0, SDL_GL_CONTEXT_PROFILE_COMPATIBILITY}};
	}

	private static void applyProfileAttributes (Sdl3ApplicationConfiguration config, int major, int minor, int profile) {
		SDL_GL_SetAttribute(SDL_GL_CONTEXT_MAJOR_VERSION, major);
		SDL_GL_SetAttribute(SDL_GL_CONTEXT_MINOR_VERSION, minor);
		SDL_GL_SetAttribute(SDL_GL_CONTEXT_PROFILE_MASK, profile);
		if (config.debug) {
			SDL_GL_SetAttribute(SDL_GL_CONTEXT_FLAGS, SDL_GL_CONTEXT_DEBUG_FLAG);
		}
		// Re-apply user-set GL hint overrides AFTER the ladder's defaults so user-pinned
		// MAJOR/MINOR/PROFILE_MASK/CONTEXT_FLAGS take precedence on every retry. Non-version
		// hints (back-buffer bits etc.) are also re-applied here — harmless since their initial
		// application before SDL_CreateWindow already took effect on the framebuffer config.
		config.executeWindowHintOverrides();
	}

	private static void initiateGL () {
		String versionString = GL11.glGetString(GL11.GL_VERSION);
		String vendorString = GL11.glGetString(GL11.GL_VENDOR);
		String rendererString = GL11.glGetString(GL11.GL_RENDERER);
		glVersion = new GLVersion(Application.ApplicationType.Desktop, versionString, vendorString, rendererString);
	}

	private static boolean supportsFBO () {
		// FBO is in core since OpenGL 3.0, see https://www.opengl.org/wiki/Framebuffer_Object
		if (glVersion.isVersionEqualToOrHigher(3, 0)) return true;
		return org.lwjgl.sdl.SDLVideo.SDL_GL_ExtensionSupported("GL_EXT_framebuffer_object")
			|| org.lwjgl.sdl.SDLVideo.SDL_GL_ExtensionSupported("GL_ARB_framebuffer_object");
	}

	public enum GLDebugMessageSeverity {
		HIGH(GL43.GL_DEBUG_SEVERITY_HIGH, KHRDebug.GL_DEBUG_SEVERITY_HIGH, ARBDebugOutput.GL_DEBUG_SEVERITY_HIGH_ARB,
			AMDDebugOutput.GL_DEBUG_SEVERITY_HIGH_AMD), MEDIUM(GL43.GL_DEBUG_SEVERITY_MEDIUM, KHRDebug.GL_DEBUG_SEVERITY_MEDIUM,
				ARBDebugOutput.GL_DEBUG_SEVERITY_MEDIUM_ARB, AMDDebugOutput.GL_DEBUG_SEVERITY_MEDIUM_AMD), LOW(
					GL43.GL_DEBUG_SEVERITY_LOW, KHRDebug.GL_DEBUG_SEVERITY_LOW, ARBDebugOutput.GL_DEBUG_SEVERITY_LOW_ARB,
					AMDDebugOutput.GL_DEBUG_SEVERITY_LOW_AMD), NOTIFICATION(GL43.GL_DEBUG_SEVERITY_NOTIFICATION,
						KHRDebug.GL_DEBUG_SEVERITY_NOTIFICATION, -1, -1);

		final int gl43, khr, arb, amd;

		GLDebugMessageSeverity (int gl43, int khr, int arb, int amd) {
			this.gl43 = gl43;
			this.khr = khr;
			this.arb = arb;
			this.amd = amd;
		}
	}

	/** Enables or disables GL debug messages for the specified severity level. Returns false if the severity level could not be
	 * set (e.g. the NOTIFICATION level is not supported by the ARB and AMD extensions).
	 *
	 * See {@link Sdl3ApplicationConfiguration#enableGLDebugOutput(boolean, PrintStream)} */
	public static boolean setGLDebugMessageControl (GLDebugMessageSeverity severity, boolean enabled) {
		GLCapabilities caps = GL.getCapabilities();
		final int GL_DONT_CARE = 0x1100; // not defined anywhere yet

		if (caps.OpenGL43) {
			GL43.glDebugMessageControl(GL_DONT_CARE, GL_DONT_CARE, severity.gl43, (IntBuffer)null, enabled);
			return true;
		}

		if (caps.GL_KHR_debug) {
			KHRDebug.glDebugMessageControl(GL_DONT_CARE, GL_DONT_CARE, severity.khr, (IntBuffer)null, enabled);
			return true;
		}

		if (caps.GL_ARB_debug_output && severity.arb != -1) {
			ARBDebugOutput.glDebugMessageControlARB(GL_DONT_CARE, GL_DONT_CARE, severity.arb, (IntBuffer)null, enabled);
			return true;
		}

		if (caps.GL_AMD_debug_output && severity.amd != -1) {
			AMDDebugOutput.glDebugMessageEnableAMD(GL_DONT_CARE, severity.amd, (IntBuffer)null, enabled);
			return true;
		}

		return false;
	}

}
