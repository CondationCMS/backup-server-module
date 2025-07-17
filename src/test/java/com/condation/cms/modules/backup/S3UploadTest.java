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
import com.adobe.testing.s3mock.testcontainers.S3MockContainer;
import com.condation.cms.api.SiteProperties;
import com.condation.cms.api.configuration.Configuration;
import com.condation.cms.api.configuration.configs.SiteConfiguration;
import com.condation.cms.api.feature.features.ConfigurationFeature;
import com.condation.cms.api.hooks.ActionContext;
import com.condation.cms.api.module.CMSModuleContext;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;

/**
 *
 * @author thorstenmarx
 */
@Testcontainers
public class S3UploadTest {

	@Container
	S3MockContainer s3Container = new S3MockContainer("latest");

	@Test
	public void s3Test() throws Exception {

		try (S3Client s3 = S3Client.builder()
				.region(Region.EU_CENTRAL_1)
				.credentialsProvider(ProfileCredentialsProvider.create("test"))
				.endpointOverride(URI.create(s3Container.getHttpEndpoint()))
				.serviceConfiguration(
						S3Configuration.builder()
								.pathStyleAccessEnabled(true)
								.build()
				)
				.build()) {

			s3.createBucket(CreateBucketRequest.builder().bucket("backups").build());

			Path tempFile = Files.createTempFile("backup-test", ".txt");
			Files.writeString(tempFile, "hallo welt");

			ActionContext<?> ctx = Mockito.mock(ActionContext.class);
			Map<String, Object> args = new HashMap<>();
			args.put("file", tempFile.toString());
			when(ctx.arguments()).thenReturn(args);

			Map<String, Object> s3Config = new HashMap<>();
			s3Config.put("enabled", true);
			s3Config.put("bucket", "backups");
			s3Config.put("endpoint", s3Container.getHttpEndpoint());
			s3Config.put("profile", "default");
			s3Config.put("pathStyle", true);

			Map<String, Object> backup = new HashMap<>();
			backup.put("s3", s3Config);

			var context = new CMSModuleContext();
			var config = mock(Configuration.class);
			var siteConfig = mock(SiteConfiguration.class);
			var siteProperties = mock(SiteProperties.class);

			when(config.get(SiteConfiguration.class)).thenReturn(siteConfig);
			when(siteConfig.siteProperties()).thenReturn(siteProperties);
			when(siteProperties.getOrDefault(Mockito.eq("backup"), Mockito.any())).thenReturn(backup);

			ConfigurationFeature cf = new ConfigurationFeature(config);
			context.add(ConfigurationFeature.class, cf);

			S3Upload uploader = new S3Upload();
			uploader.setContext(context);

			uploader.s3_upload(ctx);

			var fileName = tempFile.getFileName().toString();
			
			ListObjectsV2Response resp = s3.listObjectsV2(ListObjectsV2Request.builder()
					.bucket("backups")
					.prefix(fileName)
					.build());

			boolean exists = resp.contents().stream()
					.anyMatch(o -> o.key().equals(fileName));
			
			Assertions.assertThat(exists).isTrue();
		}
	}

}
