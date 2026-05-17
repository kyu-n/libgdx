
package com.badlogic.gdx.backends.sdl3.audio;

import com.badlogic.gdx.Audio;
import com.badlogic.gdx.utils.Disposable;

public interface Sdl3Audio extends Audio, Disposable {

	void update ();
}
