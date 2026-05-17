
package com.badlogic.gdx.backends.sdl3;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.backends.sdl3.audio.Sdl3Audio;

public interface Sdl3ApplicationBase extends Application {

	Sdl3Audio createAudio (Sdl3ApplicationConfiguration config);

	Sdl3Input createInput (Sdl3Window window);
}
