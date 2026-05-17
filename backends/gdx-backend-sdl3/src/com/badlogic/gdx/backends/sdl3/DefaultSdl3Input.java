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

import static org.lwjgl.sdl.SDLEvents.SDL_EVENT_KEY_DOWN;
import static org.lwjgl.sdl.SDLEvents.SDL_EVENT_KEY_UP;
import static org.lwjgl.sdl.SDLEvents.SDL_EVENT_MOUSE_BUTTON_DOWN;
import static org.lwjgl.sdl.SDLEvents.SDL_EVENT_MOUSE_BUTTON_UP;
import static org.lwjgl.sdl.SDLEvents.SDL_EVENT_MOUSE_MOTION;
import static org.lwjgl.sdl.SDLEvents.SDL_EVENT_MOUSE_WHEEL;
import static org.lwjgl.sdl.SDLEvents.SDL_EVENT_TEXT_EDITING;
import static org.lwjgl.sdl.SDLEvents.SDL_EVENT_TEXT_INPUT;
import static org.lwjgl.sdl.SDLMouse.SDL_BUTTON_LEFT;
import static org.lwjgl.sdl.SDLMouse.SDL_BUTTON_MIDDLE;
import static org.lwjgl.sdl.SDLMouse.SDL_BUTTON_RIGHT;
import static org.lwjgl.sdl.SDLMouse.SDL_BUTTON_X1;
import static org.lwjgl.sdl.SDLMouse.SDL_BUTTON_X2;
import static org.lwjgl.sdl.SDLScancode.*;

import com.badlogic.gdx.input.NativeInputConfiguration;

import com.badlogic.gdx.AbstractInput;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputEventQueue;
import com.badlogic.gdx.InputProcessor;

public class DefaultSdl3Input extends AbstractInput implements Sdl3Input {

	/** SDL_Scancode ({@code 0..SDL_SCANCODE_COUNT-1}) -> libGDX {@link Input.Keys} integer code. SDL3 scancodes are USB-HID page-7
	 * physical-key identifiers, mapped here to the corresponding libGDX key constants. Unmapped scancodes resolve to
	 * {@link Input.Keys#UNKNOWN}. Sized 512 to cover {@code SDL_SCANCODE_COUNT}. */
	private static final int[] SCANCODE_TO_GDX_KEYS = new int[512];
	static {
		java.util.Arrays.fill(SCANCODE_TO_GDX_KEYS, Input.Keys.UNKNOWN);

		// Letters
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_A] = Input.Keys.A;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_B] = Input.Keys.B;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_C] = Input.Keys.C;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_D] = Input.Keys.D;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_E] = Input.Keys.E;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_F] = Input.Keys.F;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_G] = Input.Keys.G;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_H] = Input.Keys.H;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_I] = Input.Keys.I;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_J] = Input.Keys.J;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_K] = Input.Keys.K;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_L] = Input.Keys.L;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_M] = Input.Keys.M;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_N] = Input.Keys.N;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_O] = Input.Keys.O;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_P] = Input.Keys.P;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_Q] = Input.Keys.Q;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_R] = Input.Keys.R;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_S] = Input.Keys.S;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_T] = Input.Keys.T;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_U] = Input.Keys.U;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_V] = Input.Keys.V;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_W] = Input.Keys.W;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_X] = Input.Keys.X;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_Y] = Input.Keys.Y;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_Z] = Input.Keys.Z;

		// Top-row digits
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_1] = Input.Keys.NUM_1;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_2] = Input.Keys.NUM_2;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_3] = Input.Keys.NUM_3;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_4] = Input.Keys.NUM_4;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_5] = Input.Keys.NUM_5;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_6] = Input.Keys.NUM_6;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_7] = Input.Keys.NUM_7;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_8] = Input.Keys.NUM_8;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_9] = Input.Keys.NUM_9;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_0] = Input.Keys.NUM_0;

		// Whitespace / editing
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_RETURN] = Input.Keys.ENTER;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_ESCAPE] = Input.Keys.ESCAPE;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_BACKSPACE] = Input.Keys.BACKSPACE;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_TAB] = Input.Keys.TAB;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_SPACE] = Input.Keys.SPACE;

		// Punctuation
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_MINUS] = Input.Keys.MINUS;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_EQUALS] = Input.Keys.EQUALS;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_LEFTBRACKET] = Input.Keys.LEFT_BRACKET;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_RIGHTBRACKET] = Input.Keys.RIGHT_BRACKET;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_BACKSLASH] = Input.Keys.BACKSLASH;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_SEMICOLON] = Input.Keys.SEMICOLON;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_APOSTROPHE] = Input.Keys.APOSTROPHE;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_GRAVE] = Input.Keys.GRAVE;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_COMMA] = Input.Keys.COMMA;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_PERIOD] = Input.Keys.PERIOD;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_SLASH] = Input.Keys.SLASH;

		// Locks / system
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_CAPSLOCK] = Input.Keys.CAPS_LOCK;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_SCROLLLOCK] = Input.Keys.SCROLL_LOCK;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_NUMLOCKCLEAR] = Input.Keys.NUM_LOCK;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_PRINTSCREEN] = Input.Keys.PRINT_SCREEN;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_PAUSE] = Input.Keys.PAUSE;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_INSERT] = Input.Keys.INSERT;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_HOME] = Input.Keys.HOME;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_PAGEUP] = Input.Keys.PAGE_UP;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_DELETE] = Input.Keys.FORWARD_DEL;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_END] = Input.Keys.END;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_PAGEDOWN] = Input.Keys.PAGE_DOWN;

		// Arrows
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_RIGHT] = Input.Keys.RIGHT;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_LEFT] = Input.Keys.LEFT;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_DOWN] = Input.Keys.DOWN;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_UP] = Input.Keys.UP;

		// Function keys F1-F24
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_F1] = Input.Keys.F1;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_F2] = Input.Keys.F2;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_F3] = Input.Keys.F3;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_F4] = Input.Keys.F4;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_F5] = Input.Keys.F5;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_F6] = Input.Keys.F6;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_F7] = Input.Keys.F7;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_F8] = Input.Keys.F8;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_F9] = Input.Keys.F9;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_F10] = Input.Keys.F10;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_F11] = Input.Keys.F11;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_F12] = Input.Keys.F12;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_F13] = Input.Keys.F13;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_F14] = Input.Keys.F14;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_F15] = Input.Keys.F15;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_F16] = Input.Keys.F16;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_F17] = Input.Keys.F17;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_F18] = Input.Keys.F18;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_F19] = Input.Keys.F19;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_F20] = Input.Keys.F20;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_F21] = Input.Keys.F21;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_F22] = Input.Keys.F22;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_F23] = Input.Keys.F23;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_F24] = Input.Keys.F24;

		// Keypad
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_KP_DIVIDE] = Input.Keys.NUMPAD_DIVIDE;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_KP_MULTIPLY] = Input.Keys.NUMPAD_MULTIPLY;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_KP_MINUS] = Input.Keys.NUMPAD_SUBTRACT;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_KP_PLUS] = Input.Keys.NUMPAD_ADD;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_KP_ENTER] = Input.Keys.NUMPAD_ENTER;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_KP_1] = Input.Keys.NUMPAD_1;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_KP_2] = Input.Keys.NUMPAD_2;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_KP_3] = Input.Keys.NUMPAD_3;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_KP_4] = Input.Keys.NUMPAD_4;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_KP_5] = Input.Keys.NUMPAD_5;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_KP_6] = Input.Keys.NUMPAD_6;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_KP_7] = Input.Keys.NUMPAD_7;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_KP_8] = Input.Keys.NUMPAD_8;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_KP_9] = Input.Keys.NUMPAD_9;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_KP_0] = Input.Keys.NUMPAD_0;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_KP_PERIOD] = Input.Keys.NUMPAD_DOT;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_KP_EQUALS] = Input.Keys.NUMPAD_EQUALS;

		// Modifiers
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_LCTRL] = Input.Keys.CONTROL_LEFT;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_LSHIFT] = Input.Keys.SHIFT_LEFT;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_LALT] = Input.Keys.ALT_LEFT;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_LGUI] = Input.Keys.SYM;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_RCTRL] = Input.Keys.CONTROL_RIGHT;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_RSHIFT] = Input.Keys.SHIFT_RIGHT;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_RALT] = Input.Keys.ALT_RIGHT;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_RGUI] = Input.Keys.SYM;

		// App / context
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_MENU] = Input.Keys.MENU;
		SCANCODE_TO_GDX_KEYS[SDL_SCANCODE_APPLICATION] = Input.Keys.MENU;
	}

	final Sdl3Window window;
	private InputProcessor inputProcessor;
	final InputEventQueue eventQueue = new InputEventQueue();

	int mouseX, mouseY;
	int mousePressed;
	int deltaX, deltaY;
	boolean justTouched;
	final boolean[] justPressedButtons = new boolean[5];
	/** Backs {@link #isButtonPressed(int)}. Indexed by libGDX {@code Buttons.LEFT/RIGHT/MIDDLE/BACK/FORWARD} (0..4). Kept in sync
	 * with {@link #mousePressed} count in the mouse-button event handler. */
	final boolean[] mouseButtonPressed = new boolean[5];
	char lastCharacter;

	public DefaultSdl3Input (Sdl3Window window) {
		this.window = window;
		windowHandleChanged(window.getWindowHandle());
	}

	/** Receive a routed {@code SDL_Event} from {@link Sdl3Application}. Translates SDL3 input-domain events into
	 * {@link InputEventQueue} entries and updates {@link AbstractInput#pressedKeys}/etc.
	 *
	 * <p>
	 * {@code SDL_EVENT_KEY_DOWN}/{@code _UP} carry an {@code SDL_Scancode}, which we translate via {@link #SCANCODE_TO_GDX_KEYS}.
	 * {@code SDL_EVENT_TEXT_INPUT} carries a UTF-8 string — we iterate Java {@code char}s so multi-codepoint sequences (emoji
	 * surrogate pairs) emit each surrogate. Key repeat events are dropped so only the initial press generates a keyDown. */
	void handleSDLEvent (org.lwjgl.sdl.SDL_Event event) {
		int type = event.type();
		switch (type) {
		case SDL_EVENT_KEY_DOWN: {
			if (event.key().repeat()) break; // drop key repeats; only initial press generates keyDown
			int keyCode = getGdxKeyCode(event.key().scancode());
			long time = System.nanoTime();
			eventQueue.keyDown(keyCode, time);
			pressedKeyCount++;
			keyJustPressed = true;
			pressedKeys[keyCode] = true;
			justPressedKeys[keyCode] = true;
			window.requestRendering();
			lastCharacter = 0;
			char character = characterForKeyCode(keyCode);
			if (character != 0) {
				lastCharacter = character;
				eventQueue.keyTyped(character, time);
			}
			break;
		}
		case SDL_EVENT_KEY_UP: {
			int keyCode = getGdxKeyCode(event.key().scancode());
			pressedKeyCount = Math.max(0, pressedKeyCount - 1);
			pressedKeys[keyCode] = false;
			window.requestRendering();
			eventQueue.keyUp(keyCode, System.nanoTime());
			break;
		}
		case SDL_EVENT_TEXT_INPUT: {
			String text = event.text().textString();
			if (text == null || text.isEmpty()) break;
			long time = System.nanoTime();
			window.requestRendering();
			// Iterate Java chars so multi-codepoint runs (surrogate pairs) deliver both halves in order.
			for (int i = 0; i < text.length(); i++) {
				char c = text.charAt(i);
				if ((c & 0xff00) == 0xf700) continue; // filter private-use AppKit function keys
				lastCharacter = c;
				eventQueue.keyTyped(c, time);
			}
			break;
		}
		case SDL_EVENT_TEXT_EDITING:
			// IME composition events are not surfaced to libGDX; drop them.
			break;
		case SDL_EVENT_MOUSE_MOTION: {
			int x = (int)event.motion().x();
			int y = (int)event.motion().y();
			int dx = (int)event.motion().xrel();
			int dy = (int)event.motion().yrel();
			if (window.getConfig().hdpiMode == com.badlogic.gdx.graphics.glutils.HdpiMode.Pixels) {
				float xScale = window.getGraphics().getBackBufferWidth() / (float)window.getGraphics().getLogicalWidth();
				float yScale = window.getGraphics().getBackBufferHeight() / (float)window.getGraphics().getLogicalHeight();
				x = (int)(x * xScale);
				y = (int)(y * yScale);
				dx = (int)(dx * xScale);
				dy = (int)(dy * yScale);
			}
			deltaX = dx;
			deltaY = dy;
			mouseX = x;
			mouseY = y;
			window.requestRendering();
			long time = System.nanoTime();
			if (mousePressed > 0) {
				eventQueue.touchDragged(mouseX, mouseY, 0, time);
			} else {
				eventQueue.mouseMoved(mouseX, mouseY, time);
			}
			break;
		}
		case SDL_EVENT_MOUSE_BUTTON_DOWN:
		case SDL_EVENT_MOUSE_BUTTON_UP: {
			int gdxButton = toGdxButton(event.button().button());
			if (gdxButton == -1) break;
			// Use the most up-to-date pointer position carried by the button event (HiDPI-scaled like motion).
			int x = (int)event.button().x();
			int y = (int)event.button().y();
			if (window.getConfig().hdpiMode == com.badlogic.gdx.graphics.glutils.HdpiMode.Pixels) {
				float xScale = window.getGraphics().getBackBufferWidth() / (float)window.getGraphics().getLogicalWidth();
				float yScale = window.getGraphics().getBackBufferHeight() / (float)window.getGraphics().getLogicalHeight();
				x = (int)(x * xScale);
				y = (int)(y * yScale);
			}
			mouseX = x;
			mouseY = y;
			long time = System.nanoTime();
			if (type == SDL_EVENT_MOUSE_BUTTON_DOWN) {
				mousePressed++;
				justTouched = true;
				justPressedButtons[gdxButton] = true;
				mouseButtonPressed[gdxButton] = true;
				window.requestRendering();
				eventQueue.touchDown(mouseX, mouseY, 0, gdxButton, time);
			} else {
				mousePressed = Math.max(0, mousePressed - 1);
				mouseButtonPressed[gdxButton] = false;
				window.requestRendering();
				eventQueue.touchUp(mouseX, mouseY, 0, gdxButton, time);
			}
			break;
		}
		case SDL_EVENT_MOUSE_WHEEL: {
			float amountX = event.wheel().x();
			float amountY = event.wheel().y();
			// libGDX scrolled() callers treat a negative delta as "up", so we negate both axes: SDL3 reports a wheel-up roll
			// as positive y, but libGDX wants that surfaced as negative.
			window.requestRendering();
			eventQueue.scrolled(-amountX, -amountY, System.nanoTime());
			break;
		}
		default:
			break;
		}
	}

	/** Map an SDL3 mouse button id ({@code SDL_BUTTON_LEFT=1 ... X2=5}) to a libGDX {@link Buttons} index (0..4). */
	private static int toGdxButton (int sdlButton) {
		if (sdlButton == SDL_BUTTON_LEFT) return Buttons.LEFT;
		if (sdlButton == SDL_BUTTON_RIGHT) return Buttons.RIGHT;
		if (sdlButton == SDL_BUTTON_MIDDLE) return Buttons.MIDDLE;
		if (sdlButton == SDL_BUTTON_X1) return Buttons.BACK;
		if (sdlButton == SDL_BUTTON_X2) return Buttons.FORWARD;
		return -1;
	}

	@Override
	public void resetPollingStates () {
		justTouched = false;
		keyJustPressed = false;
		for (int i = 0; i < justPressedKeys.length; i++) {
			justPressedKeys[i] = false;
		}
		for (int i = 0; i < justPressedButtons.length; i++) {
			justPressedButtons[i] = false;
		}
		eventQueue.drain(null);
	}

	@Override
	public void windowHandleChanged (long windowHandle) {
		// SDL3 routes events to windows by SDL_WindowID, handled in Sdl3Application.routeInput. No per-window callback registration
		// is
		// required for the input domain — the per-event window resolution already happens upstream of handleSDLEvent.
		resetPollingStates();
		// SDL3 only emits SDL_EVENT_TEXT_INPUT while text input is active per-window. libGDX games expect character events
		// unconditionally, so we enable text input by default. Apps that want IME behaviour mediated by
		// setOnscreenKeyboardVisible can still toggle it off and back on.
		if (windowHandle != 0L) {
			org.lwjgl.sdl.SDLKeyboard.SDL_StartTextInput(windowHandle);
		}
	}

	@Override
	public void update () {
		eventQueue.drain(inputProcessor);
	}

	@Override
	public void prepareNext () {
		if (justTouched) {
			justTouched = false;
			for (int i = 0; i < justPressedButtons.length; i++) {
				justPressedButtons[i] = false;
			}
		}

		if (keyJustPressed) {
			keyJustPressed = false;
			for (int i = 0; i < justPressedKeys.length; i++) {
				justPressedKeys[i] = false;
			}
		}
		deltaX = 0;
		deltaY = 0;
	}

	@Override
	public int getMaxPointers () {
		return 1;
	}

	@Override
	public int getX () {
		return mouseX;
	}

	@Override
	public int getX (int pointer) {
		return pointer == 0 ? mouseX : 0;
	}

	@Override
	public int getDeltaX () {
		return deltaX;
	}

	@Override
	public int getDeltaX (int pointer) {
		return pointer == 0 ? deltaX : 0;
	}

	@Override
	public int getY () {
		return mouseY;
	}

	@Override
	public int getY (int pointer) {
		return pointer == 0 ? mouseY : 0;
	}

	@Override
	public int getDeltaY () {
		return deltaY;
	}

	@Override
	public int getDeltaY (int pointer) {
		return pointer == 0 ? deltaY : 0;
	}

	@Override
	public boolean isTouched () {
		return mousePressed > 0;
	}

	@Override
	public boolean justTouched () {
		return justTouched;
	}

	@Override
	public boolean isTouched (int pointer) {
		return pointer == 0 ? isTouched() : false;
	}

	@Override
	public float getPressure () {
		return getPressure(0);
	}

	@Override
	public float getPressure (int pointer) {
		return isTouched(pointer) ? 1 : 0;
	}

	@Override
	public boolean isButtonPressed (int button) {
		if (button < 0 || button >= mouseButtonPressed.length) return false;
		return mouseButtonPressed[button];
	}

	@Override
	public boolean isButtonJustPressed (int button) {
		if (button < 0 || button >= justPressedButtons.length) {
			return false;
		}
		return justPressedButtons[button];
	}

	@Override
	public void getTextInput (TextInputListener listener, String title, String text, String hint) {
		getTextInput(listener, title, text, hint, OnscreenKeyboardType.Default);
	}

	@Override
	public void getTextInput (TextInputListener listener, String title, String text, String hint, OnscreenKeyboardType type) {
		// FIXME getTextInput does nothing
		listener.canceled();
	}

	@Override
	public long getCurrentEventTime () {
		// queue sets its event time for each event dequeued/processed
		return eventQueue.getCurrentEventTime();
	}

	@Override
	public void setInputProcessor (InputProcessor processor) {
		this.inputProcessor = processor;
	}

	@Override
	public InputProcessor getInputProcessor () {
		return inputProcessor;
	}

	@Override
	public void setCursorCatched (boolean catched) {
		org.lwjgl.sdl.SDLMouse.SDL_SetWindowRelativeMouseMode(window.getWindowHandle(), catched);
	}

	@Override
	public boolean isCursorCatched () {
		return org.lwjgl.sdl.SDLMouse.SDL_GetWindowRelativeMouseMode(window.getWindowHandle());
	}

	@Override
	public void setCursorPosition (int x, int y) {
		if (window.getConfig().hdpiMode == com.badlogic.gdx.graphics.glutils.HdpiMode.Pixels) {
			float xScale = window.getGraphics().getLogicalWidth() / (float)window.getGraphics().getBackBufferWidth();
			float yScale = window.getGraphics().getLogicalHeight() / (float)window.getGraphics().getBackBufferHeight();
			x = (int)(x * xScale);
			y = (int)(y * yScale);
		}
		org.lwjgl.sdl.SDLMouse.SDL_WarpMouseInWindow(window.getWindowHandle(), x, y);
		// SDL_WarpMouseInWindow does not generate a SDL_EVENT_MOUSE_MOTION on all platforms; synthesize the libGDX-side state
		// transition so callers observing getX/getY immediately afterwards see the new position.
		deltaX = x - mouseX;
		deltaY = y - mouseY;
		mouseX = x;
		mouseY = y;
	}

	protected char characterForKeyCode (int key) {
		// Map certain key codes to character codes.
		switch (key) {
		case Keys.BACKSPACE:
			return 8;
		case Keys.TAB:
			return '\t';
		case Keys.FORWARD_DEL:
			return 127;
		case Keys.NUMPAD_ENTER:
		case Keys.ENTER:
			return '\n';
		}
		return 0;
	}

	/** Translate an SDL3 {@code SDL_Scancode} to libGDX {@link Input.Keys}. The scancode is a physical-key USB-HID identifier
	 * ({@code SDL_SCANCODE_*}), not an {@code SDL_Keycode} (which represents the translated character for the active layout).
	 * Returns {@link Input.Keys#UNKNOWN} for any scancode the table does not map (exotic media/launcher/international keys).
	 *
	 * @param scancode an {@code SDL_SCANCODE_*} value */
	public int getGdxKeyCode (int scancode) {
		if (scancode < 0 || scancode >= SCANCODE_TO_GDX_KEYS.length) return Input.Keys.UNKNOWN;
		return SCANCODE_TO_GDX_KEYS[scancode];
	}

	@Override
	public void dispose () {
		// SDL3 is event-pull — nothing to free.
	}

	// --------------------------------------------------------------------------
	// -------------------------- Nothing to see below this line except for stubs
	// --------------------------------------------------------------------------
	// Joystick / gamepad / accelerometer / gyroscope: SDL3 has full controller support (SDL_GAMEPAD_*, SDL_SENSOR_*), but
	// libGDX consumers use the gdx-controllers extension for cross-backend controller access. Wiring SDL3 into
	// gdx-controllers is out of scope for the input backend; the stubs below return inert defaults (controllerCount=0,
	// accelerometer=0, no vibration).

	@Override
	public float getAccelerometerX () {
		return 0;
	}

	@Override
	public float getAccelerometerY () {
		return 0;
	}

	@Override
	public float getAccelerometerZ () {
		return 0;
	}

	@Override
	public boolean isPeripheralAvailable (Peripheral peripheral) {
		return peripheral == Peripheral.HardwareKeyboard;
	}

	@Override
	public int getRotation () {
		return 0;
	}

	@Override
	public Orientation getNativeOrientation () {
		return Orientation.Landscape;
	}

	@Override
	public void setOnscreenKeyboardVisible (boolean visible) {
		setOnscreenKeyboardVisible(visible, OnscreenKeyboardType.Default);
	}

	@Override
	public void setOnscreenKeyboardVisible (boolean visible, OnscreenKeyboardType type) {
		// SDL3 gates text-input event delivery on a per-window flag. Toggling it controls SDL_EVENT_TEXT_INPUT generation as well
		// as showing an IME / on-screen keyboard where the platform supports it. Desktop platforms (X11, Wayland, win32) do not
		// surface a soft keyboard; the call is still necessary to receive TEXT_INPUT events.
		long handle = window.getWindowHandle();
		if (handle == 0L) return;
		if (visible) {
			org.lwjgl.sdl.SDLKeyboard.SDL_StartTextInput(handle);
		} else {
			org.lwjgl.sdl.SDLKeyboard.SDL_StopTextInput(handle);
		}
	}

	@Override
	public void openTextInputField (NativeInputConfiguration configuration) {

	}

	@Override
	public void closeTextInputField (boolean sendReturn) {

	}

	@Override
	public void setKeyboardHeightObserver (KeyboardHeightObserver observer) {

	}

	@Override
	public void vibrate (int milliseconds) {
	}

	@Override
	public void vibrate (int milliseconds, boolean fallback) {
	}

	@Override
	public void vibrate (int milliseconds, int amplitude, boolean fallback) {
	}

	@Override
	public void vibrate (VibrationType vibrationType) {
	}

	@Override
	public float getAzimuth () {
		return 0;
	}

	@Override
	public float getPitch () {
		return 0;
	}

	@Override
	public float getRoll () {
		return 0;
	}

	@Override
	public void getRotationMatrix (float[] matrix) {
	}

	@Override
	public float getGyroscopeX () {
		return 0;
	}

	@Override
	public float getGyroscopeY () {
		return 0;
	}

	@Override
	public float getGyroscopeZ () {
		return 0;
	}
}
