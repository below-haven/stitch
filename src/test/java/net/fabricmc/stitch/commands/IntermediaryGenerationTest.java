/*
 * Copyright (c) 2016, 2017, 2018, 2019 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.fabricmc.stitch.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.format.MappingFormat;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import net.fabricmc.stitch.plugin.PluginLoader;
import net.fabricmc.stitch.representation.JarReader;
import net.fabricmc.stitch.representation.JarRootEntry;

class IntermediaryGenerationTest {
	@TempDir
	Path tempDir;

	@Test
	void generateIntermediaryUsesVirtualPrefixForOrphanDollarClasses() throws Exception {
		File jarFile = createJar(Arrays.asList(
				classSpec("pkg/Outer$5"),
				classSpec("pkg/Outer$6")
		));

		MemoryMappingTree mappings = generateMappings(jarFile);

		assertNull(mappings.getClass("pkg/Outer"));
		assertEquals("Class_1$5", mappings.getClass("pkg/Outer$5").getDstName(0));
		assertEquals("Class_1$6", mappings.getClass("pkg/Outer$6").getDstName(0));
	}

	@Test
	void generateIntermediaryKeepsRealOuterClassMapping() throws Exception {
		File jarFile = createJar(Arrays.asList(
				classSpec("pkg/RealOuter"),
				classSpec("pkg/RealOuter$Inner")
		));

		MemoryMappingTree mappings = generateMappings(jarFile);

		assertEquals("Class_1", mappings.getClass("pkg/RealOuter").getDstName(0));
		assertEquals("Class_1$Class_2", mappings.getClass("pkg/RealOuter$Inner").getDstName(0));
	}

	@Test
	void validateMappingsReportsMissingClassesAndMembers() throws Exception {
		File jarFile = createJar(Arrays.asList(classSpec("pkg/Exists", true)));
		Path mappingsFile = tempDir.resolve("mappings.tiny");
		Files.write(mappingsFile, Arrays.asList(
				"tiny\t2\t0\tofficial\tintermediary",
				"c\tpkg/Missing\tClass_1",
				"c\tpkg/Exists\tClass_2",
				"\tf\tI\tok\tfield_1",
				"\tf\tI\tmissing\tfield_2",
				"\tm\t()V\trun\tmethod_1",
				"\tm\t()V\tmissing\tmethod_2"
		), StandardCharsets.UTF_8);

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		PrintStream previousOut = System.out;
		PrintStream previousErr = System.err;

		try {
			System.setOut(new PrintStream(out));
			System.setErr(new PrintStream(new ByteArrayOutputStream()));
			new CommandValidateMappings().run(new String[] { jarFile.getAbsolutePath(), mappingsFile.toString() });
		} finally {
			System.setOut(previousOut);
			System.setErr(previousErr);
		}

		String output = new String(out.toByteArray(), StandardCharsets.UTF_8);
		assertContains(output, "missing-class\tpkg/Missing\tClass_1");
		assertContains(output, "missing-field\tpkg/Exists\tmissing\tI\tfield_2");
		assertContains(output, "missing-method\tpkg/Exists\tmissing\t()V\tmethod_2");
		assertFalse(output.contains("missing-field\tpkg/Exists\tok\tI"));
		assertFalse(output.contains("missing-method\tpkg/Exists\trun\t()V"));
	}

	private MemoryMappingTree generateMappings(File jarFile) throws Exception {
		JarRootEntry jar = new JarRootEntry(jarFile);
		new JarReader(jar).apply();

		File mappingsFile = tempDir.resolve("generated-" + System.nanoTime() + ".tiny").toFile();

		if (PluginLoader.getLoadedPlugins().isEmpty()) {
			PluginLoader.loadPlugins();
		}

		GenState state = new GenState();
		state.disableInteractive();
		state.generate(mappingsFile, jar, null);

		MemoryMappingTree mappings = new MemoryMappingTree();
		MappingFormat format = MappingReader.detectFormat(mappingsFile.toPath());
		MappingReader.read(mappingsFile.toPath(), format, mappings);
		return mappings;
	}

	private File createJar(List<ClassSpec> classes) throws IOException {
		File jarFile = tempDir.resolve("fixture-" + System.nanoTime() + ".jar").toFile();

		try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(jarFile.toPath()))) {
			for (ClassSpec spec : classes) {
				jar.putNextEntry(new JarEntry(spec.name + ".class"));
				jar.write(createClass(spec));
				jar.closeEntry();
			}
		}

		return jarFile;
	}

	private byte[] createClass(ClassSpec spec) {
		ClassWriter writer = new ClassWriter(0);
		writer.visit(Opcodes.V1_8, Opcodes.ACC_SUPER, spec.name, null, "java/lang/Object", null);

		if (spec.withMembers) {
			writer.visitField(Opcodes.ACC_PUBLIC, "ok", "I", null, null).visitEnd();

			MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "run", "()V", null, null);
			method.visitCode();
			method.visitInsn(Opcodes.RETURN);
			method.visitMaxs(0, 1);
			method.visitEnd();
		}

		writer.visitEnd();
		return writer.toByteArray();
	}

	private ClassSpec classSpec(String name) {
		return new ClassSpec(name, false);
	}

	private ClassSpec classSpec(String name, boolean withMembers) {
		return new ClassSpec(name, withMembers);
	}

	private void assertContains(String actual, String expected) {
		assertTrue(actual.contains(expected), "Expected output to contain: " + expected + "\nActual output:\n" + actual);
	}

	private static final class ClassSpec {
		private final String name;
		private final boolean withMembers;

		private ClassSpec(String name, boolean withMembers) {
			this.name = name;
			this.withMembers = withMembers;
		}
	}
}
