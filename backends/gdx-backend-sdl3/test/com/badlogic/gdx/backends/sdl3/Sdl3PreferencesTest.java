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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import com.badlogic.gdx.Files.FileType;

public class Sdl3PreferencesTest {

	private Path tmpDir;

	@Before
	public void setUp () throws Exception {
		tmpDir = Files.createTempDirectory("sdl3-prefs-test-");
	}

	@After
	public void tearDown () {
		File[] files = tmpDir.toFile().listFiles();
		if (files != null) for (File f : files)
			f.delete();
		tmpDir.toFile().delete();
	}

	private Sdl3Preferences open (String name) {
		return new Sdl3Preferences(new Sdl3FileHandle(new File(tmpDir.toFile(), name), FileType.Absolute));
	}

	@Test
	public void flush_writesAndReloads () {
		Sdl3Preferences prefs = open("prefs.xml");
		prefs.putString("key1", "value1");
		prefs.flush();
		File pf = new File(tmpDir.toFile(), "prefs.xml");
		assertTrue("prefs file must exist after flush", pf.exists());

		Sdl3Preferences reloaded = open("prefs.xml");
		assertEquals("value1", reloaded.getString("key1"));
	}

	@Test
	public void flush_leavesNoTempFile () {
		Sdl3Preferences prefs = open("prefs.xml");
		prefs.putString("key1", "value1");
		prefs.flush();
		File tmp = new File(tmpDir.toFile(), "prefs.xml.tmp");
		assertFalse("temp file must not remain after successful flush", tmp.exists());
	}

	/** The atomic-write invariant we care about: regardless of whether the move into a read-only target succeeds
	 * (platform-dependent — succeeds on tmpfs as root, fails on most ext4-as-user setups), the prefs file is never silently empty
	 * after a flush attempt. This test asserts that floor: the file is always readable as either the original ("yes") or the new
	 * value ("no"), never wiped. */
	@Test
	public void flush_neverLeavesEmptyPrefsAfterFailedAtomicMove () throws Exception {
		// Seed prefs with valid content
		Sdl3Preferences first = open("prefs.xml");
		first.putString("survivor", "yes");
		first.flush();

		// Make the existing prefs file read-only so the atomic move into it fails.
		File pf = new File(tmpDir.toFile(), "prefs.xml");
		Assume.assumeTrue("filesystem must support setWritable", pf.setWritable(false));
		Assume.assumeTrue("setWritable(false) must actually deny writes", !pf.canWrite());
		try {
			Sdl3Preferences second = open("prefs.xml");
			second.putString("survivor", "no");
			try {
				second.flush();
			} catch (Exception ignored) {
				// expected on platforms where the move into a read-only target fails
			}
			pf.setWritable(true);
			Sdl3Preferences reloaded = open("prefs.xml");
			String value = reloaded.getString("survivor");
			assertTrue("after a failed flush, prefs must contain either the original (yes) or replacement (no), never empty",
				value.equals("yes") || value.equals("no"));
		} finally {
			pf.setWritable(true);
		}
	}
}
