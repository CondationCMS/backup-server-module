package com.condation.cms.modules.backup;

/*-
 * #%L
 * backup-module
 * %%
 * Copyright (C) 2025 CondationCMS
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

import com.condation.cms.api.utils.ServerUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Optional;
import org.yaml.snakeyaml.Yaml;

/**
 *
 * @author thmar
 */
public class ConfigLoader {

	public static Optional<Configuration> load () throws IOException {
		var configFile = ServerUtil.getHome().resolve("config/backup.yaml");
		if (!Files.exists(configFile)) {
			return Optional.empty();
		}
		try (var input = Files.newInputStream(configFile)) {
			return Optional.of(new Yaml().loadAs(input, Configuration.class));
		}
	}
}
