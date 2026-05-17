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

package com.badlogic.gdx.tests.sdl3;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.backends.sdl3.Sdl3Application;
import com.badlogic.gdx.backends.sdl3.Sdl3ApplicationConfiguration;
import com.badlogic.gdx.backends.sdl3.Sdl3Graphics;
import com.badlogic.gdx.backends.sdl3.Sdl3WindowConfiguration;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.tests.utils.CommandLineOptions;
import com.badlogic.gdx.tests.utils.GdxTestWrapper;
import com.badlogic.gdx.tests.utils.GdxTests;
import com.badlogic.gdx.utils.Os;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.SharedLibraryLoader;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

public class Sdl3TestStarter {

	static CommandLineOptions options;

	/** Runs libgdx tests against the SDL3 backend.
	 *
	 * Some options can be passed; see {@link CommandLineOptions}. The {@code options.angle} flag is ignored — the SDL3 backend
	 * does not yet have an ANGLE adapter (tracked as a follow-up plan); choose a different GL profile or run the LWJGL3 starter
	 * for ANGLE coverage.
	 *
	 * @param argv command line arguments */
	public static void main (String[] argv) {
		options = new CommandLineOptions(argv);

		Sdl3ApplicationConfiguration config = new Sdl3ApplicationConfiguration();
		config.setWindowedMode(640, 480);

		if (options.gl30 || options.gl31 || options.gl32) {
			ShaderProgram.prependVertexCode = "#version 140\n#define varying out\n#define attribute in\n";
			ShaderProgram.prependFragmentCode = "#version 140\n#define varying in\n#define texture2D texture\n#define gl_FragColor fragColor\nout vec4 fragColor;\n";
		}

		if (options.gl32) {
			config.setOpenGLEmulation(Sdl3ApplicationConfiguration.GLEmulation.GL32, 4, 6);
		} else if (options.gl31) {
			config.setOpenGLEmulation(Sdl3ApplicationConfiguration.GLEmulation.GL31, 4, 5);
		} else if (options.gl30) {
			if (SharedLibraryLoader.os == Os.MacOsX) {
				config.setOpenGLEmulation(Sdl3ApplicationConfiguration.GLEmulation.GL30, 3, 2);
			} else {
				config.setOpenGLEmulation(Sdl3ApplicationConfiguration.GLEmulation.GL30, 4, 3);
			}
		}
		// options.angle intentionally ignored — SDL3 backend has no ANGLE_GLES20 path.

		if (options.startupTestName != null) {
			ApplicationListener test = GdxTests.newTest(options.startupTestName);
			if (test != null) {
				new Sdl3Application(test, config);
				return;
			}
			// Otherwise, fall back to showing the list
		}
		new Sdl3Application(new TestChooser(), config);
	}

	static class TestChooser extends ApplicationAdapter {
		private Stage stage;
		private Skin skin;
		TextButton lastClickedTestButton;

		public void create () {
			System.out.println("OpenGL renderer: " + Gdx.graphics.getGLVersion().getRendererString());
			System.out.println("OpenGL vendor: " + Gdx.graphics.getGLVersion().getVendorString());

			final Preferences prefs = Gdx.app.getPreferences("sdl3-tests");

			stage = new Stage(new ScreenViewport());
			Gdx.input.setInputProcessor(stage);
			skin = new Skin(Gdx.files.internal("data/uiskin.json"));

			Table container = new Table();
			stage.addActor(container);
			container.setFillParent(true);

			Table table = new Table();

			ScrollPane scroll = new ScrollPane(table, skin);
			scroll.setSmoothScrolling(false);
			scroll.setFadeScrollBars(false);
			stage.setScrollFocus(scroll);

			int tableSpace = 4;
			table.pad(10).defaults().expandX().space(tableSpace);
			for (final String testName : GdxTests.getNames()) {
				final TextButton testButton = new TextButton(testName, skin);
				testButton.setDisabled(!options.isTestCompatible(testName));
				testButton.setName(testName);
				table.add(testButton).fillX();
				table.row();
				testButton.addListener(new ChangeListener() {
					@Override
					public void changed (ChangeEvent event, Actor actor) {
						ApplicationListener test = GdxTests.newTest(testName);
						Sdl3WindowConfiguration winConfig = new Sdl3WindowConfiguration();
						winConfig.setTitle(testName);
						winConfig.setWindowedMode(640, 480);
						winConfig.setWindowPosition(((Sdl3Graphics)Gdx.graphics).getWindow().getPositionX() + 40,
							((Sdl3Graphics)Gdx.graphics).getWindow().getPositionY() + 40);
						winConfig.useVsync(false);
						((Sdl3Application)Gdx.app).newWindow(new GdxTestWrapper(test, options.logGLErrors), winConfig);
						System.out.println("Started test: " + testName);
						prefs.putString("LastTest", testName);
						prefs.flush();
						if (testButton != lastClickedTestButton) {
							testButton.setColor(Color.CYAN);
							if (lastClickedTestButton != null) {
								lastClickedTestButton.setColor(Color.WHITE);
							}
							lastClickedTestButton = testButton;
						}
					}
				});
			}

			container.add(scroll).grow();
			container.row();

			lastClickedTestButton = (TextButton)table.findActor(prefs.getString("LastTest"));
			if (lastClickedTestButton != null) {
				lastClickedTestButton.setColor(Color.CYAN);
				scroll.layout();
				float scrollY = lastClickedTestButton.getY() + scroll.getScrollHeight() / 2 + lastClickedTestButton.getHeight() / 2
					+ tableSpace * 2 + 20;
				scroll.scrollTo(0, scrollY, 0, 0, false, false);

				// Since ScrollPane takes some time for scrolling to a position, we just "fake" time
				stage.act(1f);
				stage.act(1f);
				stage.draw();
			}
		}

		@Override
		public void render () {
			ScreenUtils.clear(0, 0, 0, 1);
			stage.act();
			stage.draw();
		}

		@Override
		public void resize (int width, int height) {
			stage.getViewport().update(width, height, true);
		}

		@Override
		public void dispose () {
			skin.dispose();
			stage.dispose();
		}
	}
}
