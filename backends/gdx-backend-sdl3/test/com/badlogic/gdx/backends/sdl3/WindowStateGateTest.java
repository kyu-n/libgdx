package com.badlogic.gdx.backends.sdl3;

import static org.junit.Assert.assertEquals;
import com.badlogic.gdx.backends.sdl3.Sdl3Window.WindowStateGate;
import com.badlogic.gdx.backends.sdl3.Sdl3Window.WindowStateGate.Transition;
import org.junit.Test;

public class WindowStateGateTest {
	@Test public void minimizeThenRestore_firesIconify () {
		WindowStateGate g = new WindowStateGate();
		assertEquals(Transition.ICONIFIED_TRUE, g.onMinimized());
		assertEquals(Transition.ICONIFIED_FALSE, g.onRestored());
		assertEquals(Transition.NONE, g.onRestored());
	}
	@Test public void maximizeThenRestore_firesMaximizeFalse () {
		WindowStateGate g = new WindowStateGate();
		assertEquals(Transition.MAXIMIZED_TRUE, g.onMaximized());
		assertEquals(Transition.MAXIMIZED_FALSE, g.onRestored());
	}
	@Test public void maximizeThenMinimizeThenRestore_unminimizesButStaysMaximized () {
		WindowStateGate g = new WindowStateGate();
		g.onMaximized();
		g.onMinimized();
		assertEquals(Transition.ICONIFIED_FALSE, g.onRestored());
		assertEquals(true, g.isMaximized());
	}
}
