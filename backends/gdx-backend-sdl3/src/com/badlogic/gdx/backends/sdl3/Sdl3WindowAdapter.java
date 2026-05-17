
package com.badlogic.gdx.backends.sdl3;

/** Convenience implementation of {@link Sdl3WindowListener}. Derive from this class and only overwrite the methods you are
 * interested in.
 * @author badlogic */
public class Sdl3WindowAdapter implements Sdl3WindowListener {
	@Override
	public void created (Sdl3Window window) {
	}

	@Override
	public void iconified (boolean isIconified) {
	}

	@Override
	public void maximized (boolean isMaximized) {
	}

	@Override
	public void focusLost () {
	}

	@Override
	public void focusGained () {
	}

	@Override
	public boolean closeRequested () {
		return true;
	}

	@Override
	public void filesDropped (String[] files) {
	}

	@Override
	public void refreshRequested () {
	}
}
