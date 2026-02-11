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
import com.condation.cms.api.extensions.server.ServerLifecycleExtensionPoint;
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
	public void stopped() {

	}

	@Override
	public void started() {
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
							new BackupCronJob(targetPath, context, backup)
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
