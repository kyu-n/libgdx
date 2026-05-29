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

import static org.lwjgl.sdl.SDLHints.SDL_SetHint;
import static org.lwjgl.sdl.SDLPixels.SDL_BITSPERPIXEL;
import static org.lwjgl.sdl.SDLVideo.SDL_GetCurrentDisplayMode;
import static org.lwjgl.sdl.SDLVideo.SDL_GetDisplayBounds;
import static org.lwjgl.sdl.SDLVideo.SDL_GetDisplayName;
import static org.lwjgl.sdl.SDLVideo.SDL_GetDisplays;
import static org.lwjgl.sdl.SDLVideo.SDL_GetFullscreenDisplayModes;
import static org.lwjgl.sdl.SDLVideo.SDL_GetPrimaryDisplay;
import static org.lwjgl.sdl.SDLVideo.SDL_GL_ALPHA_SIZE;
import static org.lwjgl.sdl.SDLVideo.SDL_GL_BLUE_SIZE;
import static org.lwjgl.sdl.SDLVideo.SDL_GL_CONTEXT_FLAGS;
import static org.lwjgl.sdl.SDLVideo.SDL_GL_CONTEXT_FORWARD_COMPATIBLE_FLAG;
import static org.lwjgl.sdl.SDLVideo.SDL_GL_CONTEXT_MAJOR_VERSION;
import static org.lwjgl.sdl.SDLVideo.SDL_GL_CONTEXT_MINOR_VERSION;
import static org.lwjgl.sdl.SDLVideo.SDL_GL_CONTEXT_PROFILE_CORE;
import static org.lwjgl.sdl.SDLVideo.SDL_GL_CONTEXT_PROFILE_MASK;
import static org.lwjgl.sdl.SDLVideo.SDL_GL_DEPTH_SIZE;
import static org.lwjgl.sdl.SDLVideo.SDL_GL_GREEN_SIZE;
import static org.lwjgl.sdl.SDLVideo.SDL_GL_MULTISAMPLEBUFFERS;
import static org.lwjgl.sdl.SDLVideo.SDL_GL_MULTISAMPLESAMPLES;
import static org.lwjgl.sdl.SDLVideo.SDL_GL_RED_SIZE;
import static org.lwjgl.sdl.SDLVideo.SDL_GL_SetAttribute;
import static org.lwjgl.sdl.SDLVideo.SDL_GL_STENCIL_SIZE;

import java.io.PrintStream;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.lwjgl.PointerBuffer;
import org.lwjgl.sdl.SDL_DisplayMode;
import org.lwjgl.sdl.SDL_Rect;
import org.lwjgl.sdl.SDLStdinc;
import org.lwjgl.system.MemoryStack;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.LifecycleListener;

import com.badlogic.gdx.Audio;
import com.badlogic.gdx.Files;
import com.badlogic.gdx.Files.FileType;
import com.badlogic.gdx.Graphics.DisplayMode;
import com.badlogic.gdx.Graphics.Monitor;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.backends.sdl3.Sdl3Graphics.Sdl3Monitor;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.HdpiMode;
import com.badlogic.gdx.graphics.glutils.HdpiUtils;
import com.badlogic.gdx.math.GridPoint2;

public class Sdl3ApplicationConfiguration extends Sdl3WindowConfiguration {
	public static PrintStream errorStream = System.err;

	boolean disableAudio = false;

	/** The maximum number of threads to use for network requests. Default is {@link Integer#MAX_VALUE}. */
	int maxNetThreads = Integer.MAX_VALUE;

	int audioDeviceSimultaneousSources = 16;
	int audioDeviceBufferSize = 512;
	int audioDeviceBufferCount = 9;

	public enum GLEmulation {
		GL20, GL30, GL31, GL32,
		/** OpenGL ES 2.0 over EGL (ANGLE: GLES-over-Direct3D11 on Windows, platform GLES driver elsewhere). */
		ANGLE_GLES20
	}

	GLEmulation glEmulation = GLEmulation.GL20;
	int gles30ContextMajorVersion = 3;
	int gles30ContextMinorVersion = 2;

	int r = 8, g = 8, b = 8, a = 8;
	int depth = 16, stencil = 0;
	int samples = 0;
	boolean transparentFramebuffer;

	int idleFPS = 60;
	int foregroundFPS = 0;

	boolean pauseWhenMinimized = true;
	boolean pauseWhenLostFocus = false;

	String preferencesDirectory = ".prefs/";
	Files.FileType preferencesFileType = FileType.External;

	HdpiMode hdpiMode = HdpiMode.Logical;

	boolean debug = false;
	PrintStream debugStream = System.err;

	/** Manual SDL_GL_SetAttribute pairs ({@code {attr, value}}) applied just before window/context creation, allowing experimental
	 * GL attributes that aren't covered by the typed setters. */
	List<int[]> sdlManualWindowHintAttrs = new ArrayList<>();
	/** Manual SDL_SetHint key/value pairs applied before {@code SDL_Init}/window creation, allowing experimental string hints
	 * (driver selection, app id, etc.). */
	Map<String, String> sdlManualHints = new HashMap<>();

	/** Human-readable app name passed to {@code SDL_SetAppMetadata}. Drives Wayland compositor .desktop matching, macOS dock
	 * identity, and Windows taskbar grouping. {@code null} skips the call. */
	String appName;
	/** App version string (e.g. {@code "1.0.0"}) passed to {@code SDL_SetAppMetadata}. */
	String appVersion;
	/** Reverse-DNS app identifier (e.g. {@code "com.pokeemu.client"}) passed to {@code SDL_SetAppMetadata}. Required by Wayland
	 * for proper window grouping. */
	String appIdentifier;

	/** When {@code true} on Linux, defaults {@code SDL_HINT_VIDEO_DRIVER} to {@code "x11,wayland"} unless the consumer has already
	 * set that hint via {@link #setWindowHintStrings(String, String)}. Mirrors the Mindustry/Arc workaround for SDL3
	 * Wayland-default issues on some compositors. Off by default — Wayland-first is fine on most setups in 2026, but enable this
	 * if you've shipped binaries that fail on certain distros. */
	boolean preferX11OnLinux = false;

	static Sdl3ApplicationConfiguration copy (Sdl3ApplicationConfiguration config) {
		Sdl3ApplicationConfiguration copy = new Sdl3ApplicationConfiguration();
		copy.set(config);
		return copy;
	}

	void set (Sdl3ApplicationConfiguration config) {
		super.setWindowConfiguration(config);
		disableAudio = config.disableAudio;
		audioDeviceSimultaneousSources = config.audioDeviceSimultaneousSources;
		audioDeviceBufferSize = config.audioDeviceBufferSize;
		audioDeviceBufferCount = config.audioDeviceBufferCount;
		glEmulation = config.glEmulation;
		gles30ContextMajorVersion = config.gles30ContextMajorVersion;
		gles30ContextMinorVersion = config.gles30ContextMinorVersion;
		r = config.r;
		g = config.g;
		b = config.b;
		a = config.a;
		depth = config.depth;
		stencil = config.stencil;
		samples = config.samples;
		transparentFramebuffer = config.transparentFramebuffer;
		idleFPS = config.idleFPS;
		foregroundFPS = config.foregroundFPS;
		pauseWhenMinimized = config.pauseWhenMinimized;
		pauseWhenLostFocus = config.pauseWhenLostFocus;
		preferencesDirectory = config.preferencesDirectory;
		preferencesFileType = config.preferencesFileType;
		hdpiMode = config.hdpiMode;
		debug = config.debug;
		debugStream = config.debugStream;
		sdlManualWindowHintAttrs = new ArrayList<>(config.sdlManualWindowHintAttrs);
		sdlManualHints = new HashMap<>(config.sdlManualHints);
		appName = config.appName;
		appVersion = config.appVersion;
		appIdentifier = config.appIdentifier;
		preferX11OnLinux = config.preferX11OnLinux;
	}

	/** @param visibility whether the window will be visible on creation. (default true) */
	public void setInitialVisible (boolean visibility) {
		this.initialVisible = visibility;
	}

	/** Whether to disable audio or not. If set to true, the returned audio class instances like {@link Audio} or {@link Music}
	 * will be mock implementations. */
	public void disableAudio (boolean disableAudio) {
		this.disableAudio = disableAudio;
	}

	/** Sets the maximum number of threads to use for network requests. */
	public void setMaxNetThreads (int maxNetThreads) {
		this.maxNetThreads = maxNetThreads;
	}

	/** Sets the audio device configuration.
	 *
	 * @param simultaneousSources the maximum number of sources that can be played simultaniously (default 16)
	 * @param bufferSize the audio device buffer size in samples (default 512)
	 * @param bufferCount the audio device buffer count (default 9) */
	public void setAudioConfig (int simultaneousSources, int bufferSize, int bufferCount) {
		this.audioDeviceSimultaneousSources = simultaneousSources;
		this.audioDeviceBufferSize = bufferSize;
		this.audioDeviceBufferCount = bufferCount;
	}

	/** Sets which OpenGL version to use to emulate OpenGL ES. If the given major/minor version is not supported, the backend falls
	 * back to OpenGL ES 2.0 emulation through OpenGL 2.0. The default parameters for major and minor should be 3 and 2
	 * respectively to be compatible with Mac OS X. Specifying major version 4 and minor version 2 will ensure that all OpenGL ES
	 * 3.0 features are supported. Note however that Mac OS X does only support 3.2.
	 *
	 * @see <a href= "http://legacy.lwjgl.org/javadoc/org/lwjgl/opengl/ContextAttribs.html"> LWJGL OSX ContextAttribs note</a>
	 *
	 * @param glVersion which OpenGL ES emulation version to use
	 * @param gles3MajorVersion OpenGL ES major version, use 3 as default
	 * @param gles3MinorVersion OpenGL ES minor version, use 2 as default */
	public void setOpenGLEmulation (GLEmulation glVersion, int gles3MajorVersion, int gles3MinorVersion) {
		this.glEmulation = glVersion;
		this.gles30ContextMajorVersion = gles3MajorVersion;
		this.gles30ContextMinorVersion = gles3MinorVersion;
	}

	/** Configure an OpenGL Core profile at the requested major/minor with the forward-compatible flag. Applied immediately via
	 * {@code SDL_GL_SetAttribute}. Call before window creation.
	 *
	 * <p>
	 * SDL3-only escape hatch — no LWJGL3 counterpart. */
	public static void useOpenGL3 (int major, int minor) {
		SDL_GL_SetAttribute(SDL_GL_CONTEXT_PROFILE_MASK, SDL_GL_CONTEXT_PROFILE_CORE);
		SDL_GL_SetAttribute(SDL_GL_CONTEXT_MAJOR_VERSION, major);
		SDL_GL_SetAttribute(SDL_GL_CONTEXT_MINOR_VERSION, minor);
		SDL_GL_SetAttribute(SDL_GL_CONTEXT_FLAGS, SDL_GL_CONTEXT_FORWARD_COMPATIBLE_FLAG);
	}

	/** Sets the bit depth of the color, depth and stencil buffer as well as multi-sampling.
	 *
	 * @param r red bits (default 8)
	 * @param g green bits (default 8)
	 * @param b blue bits (default 8)
	 * @param a alpha bits (default 8)
	 * @param depth depth bits (default 16)
	 * @param stencil stencil bits (default 0)
	 * @param samples MSAA samples (default 0) */
	public void setBackBufferConfig (int r, int g, int b, int a, int depth, int stencil, int samples) {
		this.r = r;
		this.g = g;
		this.b = b;
		this.a = a;
		this.depth = depth;
		this.stencil = stencil;
		this.samples = samples;
	}

	/** Sets the bit depth for the red, green, blue and alpha components of the back buffer.
	 *
	 * @param r red bits (default 8)
	 * @param g green bits (default 8)
	 * @param b blue bits (default 8)
	 * @param a alpha bits (default 8) */
	public void setRGBABits (int r, int g, int b, int a) {
		this.r = r;
		this.g = g;
		this.b = b;
		this.a = a;
	}

	/** Sets the bit depth of depth buffer.
	 *
	 * @param depth depth bits (default 16) */
	public void setDepthBits (int depth) {
		this.depth = depth;
	}

	/** Sets the bit depth of stencil buffer.
	 *
	 * @param stencil stencil bits (default 0) */
	public void setStencilBits (int stencil) {
		this.stencil = stencil;
	}

	/** Sets the multi-sampling samples value.
	 *
	 * @param samples MSAA samples (default 0) */
	public void setSamples (int samples) {
		this.samples = samples;
	}

	/** Apply the back-buffer / framebuffer attributes captured in this configuration to SDL3 via {@code SDL_GL_SetAttribute}. Must
	 * be called between {@code SDL_Init} and {@code SDL_CreateWindow(SDL_WINDOW_OPENGL)}. */
	void applyBackBufferAttributes () {
		for (int[] pair : backBufferAttributePairs(this)) {
			SDL_GL_SetAttribute(pair[0], pair[1]);
		}
	}

	/** Returns the (attribute, value) pairs that {@link #applyBackBufferAttributes} hands to {@code SDL_GL_SetAttribute}, exposed
	 * as a pure-data seam so unit tests can verify the field-to-enum wiring without a live SDL context. Order is not load-bearing
	 * — the SDL call is idempotent per attribute. */
	static int[][] backBufferAttributePairs (Sdl3ApplicationConfiguration config) {
		boolean ms = config.samples > 0;
		return new int[][] { //
			{SDL_GL_RED_SIZE, config.r}, //
			{SDL_GL_GREEN_SIZE, config.g}, //
			{SDL_GL_BLUE_SIZE, config.b}, //
			{SDL_GL_ALPHA_SIZE, config.a}, //
			{SDL_GL_DEPTH_SIZE, config.depth}, //
			{SDL_GL_STENCIL_SIZE, config.stencil}, //
			{SDL_GL_MULTISAMPLEBUFFERS, ms ? 1 : 0}, //
			{SDL_GL_MULTISAMPLESAMPLES, ms ? config.samples : 0}};
	}

	/** Set transparent window hint. Results may vary on different OS and GPUs.
	 * @param transparentFramebuffer */
	public void setTransparentFramebuffer (boolean transparentFramebuffer) {
		this.transparentFramebuffer = transparentFramebuffer;
	}

	/** Sets the polling rate during idle time in non-continuous rendering mode. Must be positive. Default is 60. */
	public void setIdleFPS (int fps) {
		if (fps <= 0) throw new IllegalArgumentException("idleFPS must be positive, got " + fps);
		this.idleFPS = fps;
	}

	/** Sets the target framerate for the application. The CPU sleeps as needed. Must be positive. Use 0 to never sleep. Default is
	 * 0. */
	public void setForegroundFPS (int fps) {
		this.foregroundFPS = fps;
	}

	/** Sets whether to pause the application {@link ApplicationListener#pause()} and fire
	 * {@link LifecycleListener#pause()}/{@link LifecycleListener#resume()} events on when window is minimized/restored. **/
	public void setPauseWhenMinimized (boolean pauseWhenMinimized) {
		this.pauseWhenMinimized = pauseWhenMinimized;
	}

	/** Sets whether to pause the application {@link ApplicationListener#pause()} and fire
	 * {@link LifecycleListener#pause()}/{@link LifecycleListener#resume()} events on when window loses/gains focus. **/
	public void setPauseWhenLostFocus (boolean pauseWhenLostFocus) {
		this.pauseWhenLostFocus = pauseWhenLostFocus;
	}

	/** Sets the directory where {@link Preferences} will be stored, as well as the file type to be used to store them. Defaults to
	 * "$USER_HOME/.prefs/" and {@link FileType#External}. */
	public void setPreferencesConfig (String preferencesDirectory, Files.FileType preferencesFileType) {
		this.preferencesDirectory = preferencesDirectory;
		this.preferencesFileType = preferencesFileType;
	}

	/** Defines how HDPI monitors are handled. Operating systems may have a per-monitor HDPI scale setting. The operating system
	 * may report window width/height and mouse coordinates in a logical coordinate system at a lower resolution than the actual
	 * physical resolution. This setting allows you to specify whether you want to work in logical or raw pixel units. See
	 * {@link HdpiMode} for more information. Note that some OpenGL functions like {@link GL20#glViewport(int, int, int, int)} and
	 * {@link GL20#glScissor(int, int, int, int)} require raw pixel units. Use {@link HdpiUtils} to help with the conversion if
	 * HdpiMode is set to {@link HdpiMode#Logical}. Defaults to {@link HdpiMode#Logical}. */
	public void setHdpiMode (HdpiMode mode) {
		this.hdpiMode = mode;
	}

	/** Enables use of OpenGL debug message callbacks. If not supported by the core GL driver (since GL 4.3), this uses the
	 * KHR_debug, ARB_debug_output or AMD_debug_output extension if available. By default, debug messages with NOTIFICATION
	 * severity are disabled to avoid log spam.
	 *
	 * You can call with {@link System#err} to output to the "standard" error output stream.
	 *
	 * Use {@link Sdl3Application#setGLDebugMessageControl(Sdl3Application.GLDebugMessageSeverity, boolean)} to enable or disable
	 * other severity debug levels. */
	public void enableGLDebugOutput (boolean enable, PrintStream debugOutputStream) {
		debug = enable;
		debugStream = debugOutputStream;
	}

	/** @return the currently active {@link DisplayMode} of the primary monitor */
	public static DisplayMode getDisplayMode () {
		return getDisplayMode(getPrimaryMonitor());
	}

	/** @return the currently active {@link DisplayMode} of the given monitor */
	public static DisplayMode getDisplayMode (Monitor monitor) {
		int displayID = (int)((Sdl3Monitor)monitor).monitorHandle;
		SDL_DisplayMode mode = SDL_GetCurrentDisplayMode(displayID);
		if (mode == null) {
			// SDL_GetCurrentDisplayMode failed (NULL return). Surface a degenerate but non-null DisplayMode so callers don't
			// NPE — the alternative (throw) would break smoke tests on headless hosts where SDL has a display ID but no
			// usable mode info.
			return new Sdl3Graphics.Sdl3DisplayMode(displayID, 0, 0, 0, 32);
		}
		return new Sdl3Graphics.Sdl3DisplayMode(displayID, mode.w(), mode.h(), Math.round(mode.refresh_rate()),
			SDL_BITSPERPIXEL(mode.format()));
	}

	/** @return the available {@link DisplayMode}s of the primary monitor */
	public static DisplayMode[] getDisplayModes () {
		return getDisplayModes(getPrimaryMonitor());
	}

	/** @return the available {@link DisplayMode}s of the given {@link Monitor} */
	public static DisplayMode[] getDisplayModes (Monitor monitor) {
		int displayID = (int)((Sdl3Monitor)monitor).monitorHandle;
		PointerBuffer modes = SDL_GetFullscreenDisplayModes(displayID);
		if (modes == null) {
			return new DisplayMode[0];
		}
		try {
			int count = modes.remaining();
			DisplayMode[] result = new DisplayMode[count];
			for (int i = 0; i < count; i++) {
				SDL_DisplayMode mode = SDL_DisplayMode.create(modes.get(i));
				result[i] = new Sdl3Graphics.Sdl3DisplayMode(displayID, mode.w(), mode.h(), Math.round(mode.refresh_rate()),
					SDL_BITSPERPIXEL(mode.format()));
			}
			return result;
		} finally {
			// SDL_GetFullscreenDisplayModes returns an SDL-allocated array; the LWJGL binding wraps the raw pointer as a
			// PointerBuffer but does not register it for free. Per SDL3 docs the caller owns the allocation and must
			// SDL_free the base pointer (see lwjgl-sdl 3.4.1 SDLVideo source — the binding returns memPointerBufferSafe
			// over the raw pointer without lifecycle tracking).
			SDLStdinc.SDL_free(modes);
		}
	}

	/** @return the primary {@link Monitor} */
	public static Monitor getPrimaryMonitor () {
		return toSdl3Monitor(SDL_GetPrimaryDisplay());
	}

	/** @return the connected {@link Monitor}s */
	public static Monitor[] getMonitors () {
		IntBuffer ids = SDL_GetDisplays();
		if (ids == null) {
			return new Monitor[0];
		}
		try {
			int count = ids.remaining();
			Monitor[] monitors = new Monitor[count];
			for (int i = 0; i < count; i++) {
				monitors[i] = toSdl3Monitor(ids.get(i));
			}
			return monitors;
		} finally {
			// SDL_GetDisplays returns an SDL-allocated SDL_DisplayID array; the LWJGL binding wraps the raw pointer without
			// registering a free, so the caller owns the lifecycle and must SDL_free the base pointer.
			SDLStdinc.SDL_free(ids);
		}
	}

	/** Build an {@link Sdl3Monitor} from an {@code SDL_DisplayID} (uint32 from SDL, widened to long for downstream cast compat —
	 * see plan's "Monitor handle shape compat" note). Reads the display name via {@code SDL_GetDisplayName} and origin via
	 * {@code SDL_GetDisplayBounds}. */
	static Sdl3Monitor toSdl3Monitor (long sdlDisplayID) {
		int id = (int)sdlDisplayID;
		String name = SDL_GetDisplayName(id);
		if (name == null) name = "";
		int virtualX = 0;
		int virtualY = 0;
		try (MemoryStack stack = MemoryStack.stackPush()) {
			SDL_Rect rect = SDL_Rect.malloc(stack);
			if (SDL_GetDisplayBounds(id, rect)) {
				virtualX = rect.x();
				virtualY = rect.y();
			}
		}
		return new Sdl3Monitor(sdlDisplayID & 0xFFFFFFFFL, virtualX, virtualY, name);
	}

	/** Compute a centered window position for {@code newWidth x newHeight} on the given monitor. Falls back to the monitor's
	 * virtual origin if {@code SDL_GetDisplayBounds} fails. */
	static GridPoint2 calculateCenteredWindowPosition (Sdl3Monitor monitor, int newWidth, int newHeight) {
		int id = (int)monitor.monitorHandle;
		try (MemoryStack stack = MemoryStack.stackPush()) {
			SDL_Rect rect = SDL_Rect.malloc(stack);
			if (SDL_GetDisplayBounds(id, rect)) {
				int x = rect.x() + (rect.w() - newWidth) / 2;
				int y = rect.y() + (rect.h() - newHeight) / 2;
				return new GridPoint2(x, y);
			}
		}
		return new GridPoint2(monitor.virtualX, monitor.virtualY);
	}

	/** Sets an int-valued GL attribute to apply via {@code SDL_GL_SetAttribute} just before window creation. Use for experimental
	 * attributes not covered by the typed setters; {@code attr} must be a {@code SDL_GL_*} constant.
	 *
	 * <p>
	 * SDL3-only escape hatch — no LWJGL3 counterpart. */
	public void setWindowHint (int attr, int value) {
		sdlManualWindowHintAttrs.add(new int[] {attr, value});
	}

	/** Sets a string SDL hint to apply via {@code SDL_SetHint} before {@code SDL_Init}/window creation. Use for experimental hints
	 * such as {@code SDL_HINT_VIDEO_DRIVER} or {@code SDL_HINT_APP_ID}.
	 *
	 * <p>
	 * SDL3-only escape hatch — no LWJGL3 counterpart. */
	public void setWindowHintStrings (String hintName, String value) {
		sdlManualHints.put(hintName, value);
	}

	/** Sets the application identity passed to {@code SDL_SetAppMetadata} before {@code SDL_Init}. Drives Wayland compositor
	 * {@code .desktop} matching, macOS dock identity, Windows taskbar grouping, and SDL3 logging context. The {@code identifier}
	 * should be reverse-DNS (e.g. {@code "com.pokeemu.client"}). All three fields may be {@code null} to skip the call.
	 *
	 * <p>
	 * SDL3-only escape hatch — no LWJGL3 counterpart. */
	public void setAppMetadata (String name, String version, String identifier) {
		this.appName = name;
		this.appVersion = version;
		this.appIdentifier = identifier;
	}

	/** When {@code true} on Linux, defaults {@code SDL_HINT_VIDEO_DRIVER="x11,wayland"} unless the consumer has set that hint
	 * explicitly. Mirrors Mindustry/Arc's field-tested Wayland fallback for compositors where SDL3's default driver pick
	 * misbehaves. Off by default.
	 *
	 * <p>
	 * SDL3-only escape hatch — no LWJGL3 counterpart. */
	public void setPreferX11OnLinux (boolean prefer) {
		this.preferX11OnLinux = prefer;
	}

	/** Applies the manual SDL hints (string) registered on this configuration. Called by {@code Sdl3Application} before
	 * {@code SDL_Init}. */
	void applyManualStringHints () {
		for (Map.Entry<String, String> e : sdlManualHints.entrySet()) {
			SDL_SetHint(e.getKey(), e.getValue());
		}
	}

	/** Applies the manual GL attributes registered on this configuration. Called between {@code SDL_Init} and window creation. */
	protected void executeWindowHintOverrides () {
		for (int[] kv : sdlManualWindowHintAttrs) {
			SDL_GL_SetAttribute(kv[0], kv[1]);
		}
	}
}
