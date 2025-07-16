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
import com.condation.cms.api.annotations.Action;
import com.condation.cms.api.configuration.configs.SiteConfiguration;
import com.condation.cms.api.extensions.HookSystemRegisterExtensionPoint;
import com.condation.cms.api.feature.features.ConfigurationFeature;
import com.condation.cms.api.hooks.ActionContext;
import com.condation.modules.api.annotation.Extension;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.Bucket;
import software.amazon.awssdk.services.s3.model.ListBucketsResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 *
 * @author t.marx
 */
@Slf4j
@Extension(HookSystemRegisterExtensionPoint.class)
public class S3Upload extends HookSystemRegisterExtensionPoint {

	@Action("module/backup/postprocess")
	public void action1(ActionContext<?> context) {
		System.out.println("check s3");
		var siteProperties = getContext().get(ConfigurationFeature.class).configuration().get(SiteConfiguration.class).siteProperties();

		var backup = siteProperties.getOrDefault("backup", Collections.emptyMap());
		if (!(boolean) backup.getOrDefault("s3", false)) {
			log.info("s3 backup disabled");
			return;
		}
		System.out.println("upload to s3");

		var fileName = (String) context.arguments().get("file");
		var file = Path.of(fileName);
		System.out.println("uploading: " + file.getFileName().toString());
		System.out.println(Files.exists(file));
		System.setProperty("software.amazon.awssdk.level", "DEBUG");

		try (S3Client s3 = S3Client.builder()
				.region(Region.EU_CENTRAL_1) // Dummy, viele S3-kompatible ignorieren das
				.credentialsProvider(ProfileCredentialsProvider.create("default"))
				.endpointOverride(URI.create("https://eu2.contabostorage.com/")) // <-- HIER!
				.serviceConfiguration(
						S3Configuration.builder()
								.pathStyleAccessEnabled(true)
								.build()
				)
				.build()) {

			ListBucketsResponse lbResponse = s3.listBuckets();
			for (Bucket bucket : lbResponse.buckets()) {
				System.out.println(bucket.name() + "\t" + bucket.creationDate());
			}

			s3.putObject(
					PutObjectRequest.builder()
							.bucket("backups")
							.key("test.text")
							.build(),
					//RequestBody.fromFile(file)
					RequestBody.fromString("Hallo")
			);
			System.out.println("backup file uploaded to s3");
		} catch (Exception e) {
			e.printStackTrace();
			log.error("", e);
		}
	}

}
