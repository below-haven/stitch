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

import java.io.File;

import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.format.MappingFormat;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import net.fabricmc.mappingio.tree.MappingTree.ClassMapping;
import net.fabricmc.mappingio.tree.MappingTree.FieldMapping;
import net.fabricmc.mappingio.tree.MappingTree.MethodMapping;
import net.fabricmc.stitch.Command;
import net.fabricmc.stitch.representation.JarClassEntry;
import net.fabricmc.stitch.representation.JarReader;
import net.fabricmc.stitch.representation.JarRootEntry;

public class CommandValidateMappings extends Command {
	public CommandValidateMappings() {
		super("validateMappings");
	}

	@Override
	public String getHelpString() {
		return "<input-jar> <mapping-file>";
	}

	@Override
	public boolean isArgumentCountValid(int count) {
		return count == 2;
	}

	@Override
	public void run(String[] args) throws Exception {
		JarRootEntry jar = new JarRootEntry(new File(args[0]));
		new JarReader(jar).apply();

		File mappingFile = new File(args[1]);
		MappingFormat format = MappingReader.detectFormat(mappingFile.toPath());
		MemoryMappingTree mappings = new MemoryMappingTree();
		MappingReader.read(mappingFile.toPath(), format, mappings);

		int missingClasses = 0;
		int missingFields = 0;
		int missingMethods = 0;

		for (ClassMapping cls : mappings.getClasses()) {
			JarClassEntry classEntry = jar.getClass(cls.getSrcName(), false);
			boolean classPresent = classEntry != null && classEntry.isClassFilePresent();

			if (!classPresent) {
				missingClasses++;
				System.out.println("missing-class\t" + cls.getSrcName() + "\t" + valueOrEmpty(cls.getDstName(0)));
			}

			for (FieldMapping fld : cls.getFields()) {
				if (!classPresent || classEntry.getField(fld.getSrcName() + fld.getSrcDesc()) == null) {
					missingFields++;
					System.out.println("missing-field\t" + cls.getSrcName()
							+ "\t" + fld.getSrcName()
							+ "\t" + fld.getSrcDesc()
							+ "\t" + valueOrEmpty(fld.getDstName(0)));
				}
			}

			for (MethodMapping mth : cls.getMethods()) {
				if (!classPresent || classEntry.getMethod(mth.getSrcName() + mth.getSrcDesc()) == null) {
					missingMethods++;
					System.out.println("missing-method\t" + cls.getSrcName()
							+ "\t" + mth.getSrcName()
							+ "\t" + mth.getSrcDesc()
							+ "\t" + valueOrEmpty(mth.getDstName(0)));
				}
			}
		}

		System.err.println("Missing classes: " + missingClasses);
		System.err.println("Missing fields: " + missingFields);
		System.err.println("Missing methods: " + missingMethods);
	}

	private static String valueOrEmpty(String value) {
		return value == null ? "" : value;
	}
}
