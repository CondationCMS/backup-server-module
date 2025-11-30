package com.condation.cms.modules.backup;

/*-
 * #%L
 * backup-server-module
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

import com.condation.cms.api.feature.features.InjectorFeature;
import com.condation.cms.api.feature.features.ServerHookSystemFeature;
import com.condation.cms.api.hooks.HookSystem;
import com.condation.cms.api.module.ServerModuleContext;
import com.condation.cms.api.scheduler.CronJob;
import com.condation.cms.api.scheduler.CronJobContext;
import com.condation.cms.api.utils.PathUtil;
import com.condation.cms.api.utils.ServerUtil;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 *
 * @author thmar
 */
@RequiredArgsConstructor
@Slf4j
public class BackupCronJob implements CronJob {

	private boolean running = false;
	
	private final Path targetPath;
	private final ServerModuleContext serverModuleContext;
	private final Configuration.Backup backup;

	@Override
	public void accept(CronJobContext context) {
		if (running) {
			log.debug("backug {} already running", backup.getName());
			return;
		}
		running = true;
		try {
			
			log.debug("start backup {}", backup.getName());
			long start = System.currentTimeMillis();
			
			final String name = backup.getName();

			var timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
			log.debug("start backup at {}", timestamp);
			var backupFilename = "%s-%s.tar.gz".formatted(name, timestamp);
			final Path targetFile = targetPath.resolve(backupFilename);

			List<Path> sources = new ArrayList<>();
			backup.getInclude_files().forEach(file -> {
				var bf = Path.of(file);
				if (PathUtil.isChild(ServerUtil.getHome(), bf)) {
					if (Files.exists(bf)) {
						sources.add(bf);
					}
				} else {
					log.warn("online files inside server home are allowed for backup");
				}
			});
			backup.getInclude_dirs().forEach(file -> {
				var bf = Path.of(file);
				if (PathUtil.isChild(ServerUtil.getHome(), bf)) {
					if (Files.exists(bf)) {
						sources.add(bf);
					}
				} else {
					log.warn("online folders inside server home are allowed for backup");
				}
			});

			log.debug("creating backup {} into {}", name, targetFile.getFileName().toString());
			TarGzPacker.createTarGz(ServerUtil.getHome(), targetFile.toFile(), sources);

			if (backup.isProcessOnlyOnChange()) {
				Path checksumFile = targetPath.resolve(name + ".sha256");
				String newChecksum = BackupUtil.calculateSHA256(targetFile);

				if (Files.exists(checksumFile)) {
					String oldChecksum = Files.readString(checksumFile);
					if (oldChecksum.equals(newChecksum)) {
						log.debug("backup {} has not changed, skipping post-processing and deleting new backup.", name);
						Files.delete(targetFile);
						return; // Skip post-processing
					}
				}
				Path tempChecksumFile = Files.createTempFile(targetPath, name, ".sha256.tmp");
				Files.writeString(tempChecksumFile, newChecksum);
				Files.move(tempChecksumFile, checksumFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
			}

			var hookSystem = serverModuleContext.get(ServerHookSystemFeature.class).hookSystem();
			hookSystem.execute("module/backup/postprocess", Map.of(
					"file", targetFile.toString(),
					"name", name
			));
			log.debug("backup {} finished after {}ms", backup.getName(), (System.currentTimeMillis() - start));
		} catch (Exception e) {
			log.error("error creating backup", e);
		} finally {
			running = false;
		}
	}

}
