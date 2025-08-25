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
import com.condation.cms.api.annotations.Action;
import com.condation.cms.api.extensions.server.ServerHookSystemRegisterExtensionPoint;
import com.condation.cms.api.hooks.ActionContext;
import com.condation.modules.api.annotation.Extension;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 *
 * @author t.marx
 */
@Slf4j
@Extension(ServerHookSystemRegisterExtensionPoint.class)
public class S3Upload extends ServerHookSystemRegisterExtensionPoint {

	@Action("module/backup/postprocess")
	public void s3_upload(ActionContext<?> context) throws IOException {
		var backupConfig = ConfigLoader.load();
		if (backupConfig.isEmpty()) {
			return;
		}

		var name = (String) context.arguments().get("name");

		var byNameConfig = backupConfig.get().getBackups().stream().filter(backup -> backup.getName().equals(name)).findFirst();

		if (byNameConfig.isEmpty()) {
			log.warn("backup config for '{}' not found", name);
			return;
		}

		var s3ConfigOpt = byNameConfig.get().getPost_processing().stream().filter(processing -> processing.getType().equals("s3")).findFirst();

		if (s3ConfigOpt.isEmpty()) {
			log.warn("no s3 config found for '{}'", name);
			return;
		}

		if (!s3ConfigOpt.get().isEnabled()) {
			return;
		}
		var s3Config = s3ConfigOpt.get().getConfig();
		if (!(boolean) s3Config.getOrDefault("enabled", false)) {
			log.debug("ftp backup disabled");
			return;
		}

		var fileName = (String) context.arguments().get("file");
		var file = Path.of(fileName);

		
		try (S3Client s3 = S3Client.builder()
				.region(Region.EU_CENTRAL_1)
				.credentialsProvider(ProfileCredentialsProvider.create((String) s3Config.getOrDefault("profile", "default")))
				.endpointOverride(URI.create((String) s3Config.getOrDefault("endpoint", null)))
				.serviceConfiguration(
						S3Configuration.builder()
								.pathStyleAccessEnabled((boolean) s3Config.getOrDefault("pathStyle", false))
								.build()
				)
				.build()) {

			s3.putObject(
					PutObjectRequest.builder()
							.bucket((String) s3Config.getOrDefault("bucket", "backups"))
							.key(file.getFileName().toString())
							.build(),
					RequestBody.fromFile(file)
			);
			log.debug("backup file uploaded");
		} catch (Exception e) {
			log.error("", e);
		}
	}

}
