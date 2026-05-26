
package com.badlogic.gdx.backends.sdl3;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.utils.Disposable;

public interface Sdl3Input extends Input, Disposable {

	void windowHandleChanged (long windowHandle);

	void update ();

	void prepareNext ();

	void resetPollingStates ();

	/** The current IME pre-edit (composition) text, or {@code ""} when there is no active composition. Polled each frame to render
	 * inline; committed text arrives separately via {@code keyTyped}. */
	String getCompositionText ();

	/** Caret offset (in UTF-16 chars) within {@link #getCompositionText()}. SDL's -1 (not set) is normalized to 0. */
	int getCompositionCursorStart ();

	/** Length (in UTF-16 chars) of the active clause within {@link #getCompositionText()}; {@code 0} when nothing is highlighted.
	 * SDL's -1 (not set) is normalized to 0. */
	int getCompositionCursorLength ();

	/** Monotonic counter bumped on every composition change (including clears), so the app can detect new pre-edit state with an
	 * int compare instead of diffing the text. */
	int getCompositionVersion ();

	/** Position the platform IME candidate window next to the caret, in window-pixel coordinates. {@code (x,y,w,h)} is the caret
	 * rect; {@code cursorX} is the caret x within it. No-op if there is no window. */
	void setTextInputArea (int x, int y, int w, int h, int cursorX);
}
