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
import com.condation.cms.api.hooks.ActionContext;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

@ExtendWith(MockitoExtension.class)
@Testcontainers
public class S3UploadTest {

	@Container
	S3MockContainer s3Container = new S3MockContainer("latest");

	@Test
	void testS3Upload() throws Exception {
		// --- Testdatei anlegen ---
		Path tempFile = Files.createTempFile("backup-test", ".txt");
		Files.writeString(tempFile, "hallo welt");

		// --- ActionContext mocken ---
		ActionContext<Object> ctx = Mockito.mock(ActionContext.class);
		Map<String, Object> args = new HashMap<>();
		args.put("file", tempFile.toString());
		args.put("name", "testBackup");
		when(ctx.arguments()).thenReturn(args);

		// --- Backup-Config vorbereiten ---
		Map<String, Object> s3Config = new HashMap<>();
		s3Config.put("enabled", true);
		s3Config.put("bucket", "backups");
		s3Config.put("endpoint", s3Container.getHttpEndpoint()); // Dummy, wird im Test nicht wirklich genutzt
		s3Config.put("profile", "default");
		s3Config.put("pathStyle", true);

		Configuration.PostProcessing post = new Configuration.PostProcessing();
		post.setType("s3");
		post.setEnabled(true);
		post.setConfig(s3Config);

		Configuration.Backup backup = new Configuration.Backup();
		backup.setName("testBackup");
		backup.setPost_processing(Collections.singletonList(post));

		Configuration backupConfig = new Configuration();
		backupConfig.setBackups(Collections.singletonList(backup));

		// --- ConfigLoader mocken ---
		try (MockedStatic<ConfigLoader> loaderMock = Mockito.mockStatic(ConfigLoader.class)) {
			loaderMock.when(ConfigLoader::load).thenReturn(java.util.Optional.of(backupConfig));

			// --- Prüfen, dass die Datei existiert ---
			try (S3Client s3 = S3Client.builder()
					.region(Region.EU_CENTRAL_1)
					.credentialsProvider(ProfileCredentialsProvider.create("default"))
					.endpointOverride(URI.create(s3Container.getHttpEndpoint()))
					.serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
					.build()) {

				s3.createBucket(CreateBucketRequest.builder().bucket("backups").build());

				// --- S3Upload instanziieren ---
				S3Upload uploader = new S3Upload();

				uploader.s3_upload(ctx);

				ListObjectsV2Response resp = s3.listObjectsV2(ListObjectsV2Request.builder()
						.bucket("backups")
						.prefix(tempFile.getFileName().toString())
						.build());

				boolean exists = resp.contents().stream()
						.anyMatch(o -> o.key().equals(tempFile.getFileName().toString()));

				assertThat(exists).isTrue();
			}
		}
	}
}
