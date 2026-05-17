
package com.badlogic.gdx.tests.sdl3;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Graphics.DisplayMode;
import com.badlogic.gdx.backends.sdl3.Sdl3WindowAdapter;
import com.badlogic.gdx.backends.sdl3.Sdl3Application;
import com.badlogic.gdx.backends.sdl3.Sdl3ApplicationConfiguration;
import com.badlogic.gdx.backends.sdl3.Sdl3Window;
import com.badlogic.gdx.backends.sdl3.Sdl3WindowConfiguration;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Pixmap.Blending;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.tests.NoncontinuousRenderingTest;
import com.badlogic.gdx.tests.UITest;
import com.badlogic.gdx.tests.g3d.Basic3DSceneTest;
import com.badlogic.gdx.tests.g3d.ShaderCollectionTest;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.ScreenUtils;

public class MultiWindowTest {
	static Texture sharedTexture;
	static SpriteBatch sharedSpriteBatch;

	public static class MainWindow extends ApplicationAdapter {
		Class[] childWindowClasses = {NoncontinuousRenderingTest.class, ShaderCollectionTest.class, Basic3DSceneTest.class,
			UITest.class};
		Sdl3Window latestWindow;
		int index;

		@Override
		public void create () {
			System.out.println(Gdx.graphics.getGLVersion().getRendererString());
			sharedSpriteBatch = new SpriteBatch();
			sharedTexture = new Texture("data/badlogic.jpg");
		}

		@Override
		public void render () {
			ScreenUtils.clear(1, 0, 0, 1);
			sharedSpriteBatch.getProjectionMatrix().setToOrtho2D(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
			sharedSpriteBatch.begin();
			sharedSpriteBatch.draw(sharedTexture, Gdx.input.getX(), Gdx.graphics.getHeight() - Gdx.input.getY() - 1);
			sharedSpriteBatch.end();

			if (Gdx.input.justTouched()) {
				Sdl3Application app = (Sdl3Application)Gdx.app;
				Sdl3WindowConfiguration config = new Sdl3WindowConfiguration();
				DisplayMode mode = Gdx.graphics.getDisplayMode();
				config.setWindowPosition(MathUtils.random(0, mode.width - 640), MathUtils.random(0, mode.height - 480));
				config.setTitle("Child window");
				config.useVsync(false);
				config.setWindowListener(new Sdl3WindowAdapter() {
					@Override
					public void created (Sdl3Window window) {
						latestWindow = window;
					}
				});
				Class clazz = childWindowClasses[index++ % childWindowClasses.length];
				ApplicationListener listener = createChildWindowClass(clazz);
				app.newWindow(listener, config);
			}

			if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE) && latestWindow != null) {
				latestWindow.setTitle("Retitled window");
				int size = 48;
				Pixmap icon = new Pixmap(size, size, Pixmap.Format.RGBA8888);
				icon.setBlending(Blending.None);
				icon.setColor(Color.BLUE);
				icon.fill();
				icon.setColor(Color.CLEAR);
				for (int i = 0; i < size; i += 3)
					for (int j = 0; j < size; j += 3)
						icon.drawPixel(i, j);
				latestWindow.setIcon(icon);
				icon.dispose();
			}
		}

		public ApplicationListener createChildWindowClass (Class clazz) {
			try {
				return (ApplicationListener)clazz.newInstance();
			} catch (Throwable t) {
				throw new GdxRuntimeException("Couldn't instantiate app listener", t);
			}
		}
	}

	public static void main (String[] argv) {
		Sdl3ApplicationConfiguration config = new Sdl3ApplicationConfiguration();
		config.setTitle("Multi-window test");
		config.useVsync(true);
		// LWJGL3 original specified ANGLE_GLES20 here; SDL3 backend has no ANGLE adapter (tracked as a follow-up plan).
		// Run on the default desktop GL path instead.
		new Sdl3Application(new MainWindow(), config);
	}
}
