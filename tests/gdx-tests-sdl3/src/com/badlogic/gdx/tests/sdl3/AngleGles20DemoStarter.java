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
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.sdl3.Sdl3Application;
import com.badlogic.gdx.backends.sdl3.Sdl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.ScreenUtils;

/** Proof harness for the SDL3 ANGLE / OpenGL ES backend path. Renders a SpriteBatch quad through GL20 -&gt; Sdl3GLES20 -&gt;
 * GLES20 and logs GL_RENDERER/VERSION so you can tell whether ANGLE engaged (renderer contains "ANGLE") or it fell back to the
 * platform GLES driver. Exits after 180 frames. */
public class AngleGles20DemoStarter {
	public static void main (String[] argv) {
		Sdl3ApplicationConfiguration config = new Sdl3ApplicationConfiguration();
		config.setOpenGLEmulation(Sdl3ApplicationConfiguration.GLEmulation.ANGLE_GLES20, 2, 0);
		config.setWindowedMode(640, 480);
		System.setProperty("java.awt.headless", "true");
		new Sdl3Application(new AngleDemo(), config);
	}

	static class AngleDemo extends ApplicationAdapter {
		SpriteBatch batch;
		Texture texture;
		int frame;
		float t;

		@Override
		public void create () {
			GL20 gl = Gdx.gl20;
			log("GL_RENDERER", gl.glGetString(GL20.GL_RENDERER));
			log("GL_VERSION", gl.glGetString(GL20.GL_VERSION));
			log("GL_VENDOR", gl.glGetString(GL20.GL_VENDOR));
			log("GL_SHADING_LANGUAGE_VERSION", gl.glGetString(GL20.GL_SHADING_LANGUAGE_VERSION));

			String renderer = gl.glGetString(GL20.GL_RENDERER);
			boolean isAngle = renderer != null && renderer.toLowerCase().contains("angle");
			Gdx.app.log("ANGLE-DEMO", isAngle ? "ANGLE backend ACTIVE: GLES is translated to a native API (Direct3D11 on Windows)."
				: "GLES context up via SDL/EGL, but NOT ANGLE on this platform (platform GLES driver). renderer=" + renderer);

			Pixmap pm = new Pixmap(2, 2, Pixmap.Format.RGBA8888);
			pm.setColor(Color.WHITE);
			pm.fill();
			texture = new Texture(pm);
			pm.dispose();

			batch = new SpriteBatch();
		}

		@Override
		public void render () {
			t += Gdx.graphics.getDeltaTime();
			ScreenUtils.clear(0.10f, 0.12f, 0.16f, 1f);
			float w = Gdx.graphics.getWidth();
			float h = Gdx.graphics.getHeight();
			batch.getProjectionMatrix().setToOrtho2D(0, 0, w, h);
			batch.begin();
			float pulse = Math.abs((float)Math.sin(t));
			batch.setColor(0.2f + 0.8f * pulse, 0.6f, 1f - 0.5f * pulse, 1f);
			float s = 120f;
			batch.draw(texture, (w - s) * 0.5f + 80f * (float)Math.sin(t * 1.7f), (h - s) * 0.5f, s, s);
			batch.end();

			if (frame == 0) Gdx.app.log("ANGLE-DEMO", "First frame rendered OK via GL20 -> GLES20.");
			if (++frame >= 180) {
				Gdx.app.log("ANGLE-DEMO", "Rendered " + frame + " frames cleanly; tearing down. PASS.");
				Gdx.app.exit();
			}
		}

		@Override
		public void dispose () {
			if (batch != null) batch.dispose();
			if (texture != null) texture.dispose();
		}

		static void log (String key, String value) {
			Gdx.app.log("ANGLE-DEMO", key + " = " + value);
		}
	}
}
