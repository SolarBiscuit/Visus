package dev.visus.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/** V-16: debug dump file names. */
class FileNamesTest {
	@Test
	void irisflwProgramNameBecomesOneFile() {
		assertEquals("gbuffers_flw_pipeline_iris_instancing_frag",
			FileNames.sanitize("gbuffers_flw_pipeline/iris_instancing/frag"));
	}

	@Test
	void packProgramNamesAreUnchanged() {
		String name = "gbuffers_terrain_cutout";
		assertSame(name, FileNames.sanitize(name));
		String composite = "composite15";
		assertSame(composite, FileNames.sanitize(composite));
	}

	@Test
	void windowsReservedCharacters() {
		assertEquals("a_b_c_d_e", FileNames.sanitize("a:b*c?d|e"));
	}
}
