package com.condation.cms.modules.backup;

/*-
 * #%L
 * recommendations-module
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
import com.condation.cms.api.configuration.configs.SiteConfiguration;
import com.condation.cms.api.extensions.server.ServerLifecycleExtensionPoint;
import com.condation.cms.api.feature.features.ConfigurationFeature;
import com.condation.cms.api.feature.features.CronJobSchedulerFeature;
import com.condation.cms.api.feature.features.DBFeature;
import com.condation.cms.api.feature.features.InjectorFeature;
import com.condation.cms.api.hooks.HookSystem;
import com.condation.cms.api.scheduler.CronJobScheduler;
import com.condation.cms.api.utils.PathUtil;
import com.condation.cms.api.utils.ServerUtil;
import com.condation.modules.api.annotation.Extension;
import com.google.common.base.Strings;
import com.google.inject.Key;
import com.google.inject.name.Names;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

/**
 *
 * @author t.marx
 */
@Slf4j
@Extension(ServerLifecycleExtensionPoint.class)
public class BackupLifecycleExtension extends ServerLifecycleExtensionPoint {

	@Override
	public void started() {

	}

	@Override
	public void stopped() {

	}

	public void activate() {
		try {

			var loadedConfig = ConfigLoader.load();

			if (!loadedConfig.isPresent()) {
				log.info("no backup config found");
				return;
			}

			var backupConig = loadedConfig.get();

			backupConig.getBackups().forEach(backup -> {
				var name = backup.getName();

				try {
					var cron = backup.getCron() != null ? backup.getCron() : backupConig.getCron();
					var target = backup.getTarget() != null ? backup.getTarget() : backupConig.getTarget();

					if (!backup.isEnabled()) {
						return;
					}

					if (Strings.isNullOrEmpty(cron)) {
						log.error("backup {} skipped: cron expression required", name);
						return;
					}
					if (Strings.isNullOrEmpty(target)) {
						log.error("backup {} skipped: target folder required", name);
						return;
					}

					var scheduler = getContext().get(InjectorFeature.class).injector().getInstance(Key.get(CronJobScheduler.class, Names.named("server")));

					var targetPath = Path.of(target);
					Files.createDirectories(targetPath);

					scheduler.schedule(cron,
							"backup-job-%s".formatted(name),
							(context) -> {
								try {
									var timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
									log.debug("start backup at {}", timestamp);
									var sitePath = getContext().get(DBFeature.class).db().getFileSystem().hostBase();
									var backupFilename = "%s-%s.tar.gz".formatted(name, timestamp);
									final Path targetFile = targetPath.resolve(backupFilename);

									BackupUtil.createTarGzBackup(sitePath, targetFile);

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
									
									TarGzPacker.createTarGz(targetFile.toFile(), sources);

									var hookSystem = getContext().get(InjectorFeature.class).injector().getInstance(HookSystem.class);
									hookSystem.execute("module/backup/postprocess", Map.of(
											"file", targetFile.toString(),
											"name", name
									));
								} catch (Exception e) {
									log.error("error creating backup", e);
								}
							}
					);
				} catch (IOException ex) {
					log.error("error configuring backup {}", name, ex);
				}

			});
		} catch (IOException ex) {
			log.error("error register backup cron job", ex);
		}
	}
}
