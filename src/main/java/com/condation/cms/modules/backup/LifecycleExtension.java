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


import com.condation.cms.api.SiteProperties;
import com.condation.cms.api.configuration.configs.SiteConfiguration;
import com.condation.cms.api.db.DB;
import com.condation.cms.api.extensions.BackupFilePostProcessingExtensionPoint;
import com.condation.cms.api.feature.features.ConfigurationFeature;
import com.condation.cms.api.feature.features.CronJobSchedulerFeature;
import com.condation.cms.api.feature.features.DBFeature;
import com.condation.cms.api.module.CMSModuleContext;
import com.condation.cms.api.module.CMSRequestContext;
import com.condation.modules.api.ModuleLifeCycleExtension;
import com.condation.modules.api.ModuleManager;
import com.condation.modules.api.annotation.Extension;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;

import lombok.extern.slf4j.Slf4j;

/**
 *
 * @author t.marx
 */
@Slf4j
@Extension(ModuleLifeCycleExtension.class)
public class LifecycleExtension extends ModuleLifeCycleExtension<CMSModuleContext, CMSRequestContext> {

	@Override
	public void init() {

	}


	@Override
	public void activate() {
		try {
			var siteProperties = getContext().get(ConfigurationFeature.class).configuration().get(SiteConfiguration.class).siteProperties();
			
			if (!siteProperties.backupEnabled()) {
				log.info("backup disabled");
				return;
			}
			
			log.info("init backup");
			var backup = siteProperties.getOrDefault("backup", Collections.emptyMap());
			if (!backup.containsKey("cron")) {
				log.error("backup skipped: cron expression required");
				return;
			}
			if (!backup.containsKey("target")) {
				log.error("backup skipped: target folder required");
				return;
			}
			
			String cron = (String)backup.get("cron");
			String target = (String)backup.get("target");
			
			var scheduler = getContext().get(CronJobSchedulerFeature.class).cronJobScheduler();
			
			var targetPath = Path.of(target);
			Files.createDirectories(targetPath);
			
			scheduler.schedule(cron,
					"backup-job-%s".formatted(siteProperties.id()),
					(context) -> {
						try {
							var timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
							log.debug("start backup at {}", timestamp);
							var sitePath = getContext().get(DBFeature.class).db().getFileSystem().hostBase();
							var backupFilename = "%s-%s.tar.gz".formatted(siteProperties.id(), timestamp);
							final Path targetFile = targetPath.resolve(backupFilename);
							
							BackupUtil.createTarGzBackup(sitePath, targetFile);
							
							/*
							var moduleManager = host.getInjector().getInstance(ModuleManager.class);
							moduleManager.extensions(BackupFilePostProcessingExtensionPoint.class)
							.forEach(extension -> extension.postProcess(targetFile));
							*/
						} catch (Exception e) {
							log.error("error creating backup", e);
						}
					}
			);
		} catch (IOException ex) {
			log.error("error register backup cron job", ex);
		}
	}

	@Override
	public void deactivate() {
		
	}
}
