package com.badlogic.gdx.backends.sdl3;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class Sdl3NetTest {

	@Test
	public void chooseStrategy_mac_returnsOpen () {
		assertEquals(Sdl3Net.OpenStrategy.OPEN,
			Sdl3Net.chooseStrategy("Mac OS X", true));
	}

	@Test
	public void chooseStrategy_linux_prefersXdgOpen () {
		// Critical: Linux must try xdg-open BEFORE Desktop.browse(), because
		// Desktop.browse() can hang on GTK/Wayland.
		assertEquals(Sdl3Net.OpenStrategy.XDG_OPEN,
			Sdl3Net.chooseStrategy("Linux", true));
	}

	@Test
	public void chooseStrategy_windows_usesDesktop () {
		assertEquals(Sdl3Net.OpenStrategy.DESKTOP_BROWSE,
			Sdl3Net.chooseStrategy("Windows 11", true));
	}

	@Test
	public void chooseStrategy_unknownOS_fallsBackToDesktop () {
		assertEquals(Sdl3Net.OpenStrategy.DESKTOP_BROWSE,
			Sdl3Net.chooseStrategy("BeOS", true));
	}

	@Test
	public void chooseStrategy_noDesktop_unknownOS_returnsNone () {
		assertEquals(Sdl3Net.OpenStrategy.NONE,
			Sdl3Net.chooseStrategy("BeOS", false));
	}
}
