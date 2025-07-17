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
import com.condation.cms.api.SiteProperties;
import com.condation.cms.api.configuration.Configuration;
import com.condation.cms.api.configuration.configs.SiteConfiguration;
import com.condation.cms.api.feature.features.ConfigurationFeature;
import com.condation.cms.api.hooks.ActionContext;
import com.condation.cms.api.module.CMSModuleContext;
import org.apache.commons.net.ftp.FTPClient;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class FTPUploadTest {

    @Test
    void testFtpUploadSuccessful() throws Exception {
        Path tempFile = Files.createTempFile("backup-test", ".zip");
        Files.writeString(tempFile, "dummy content");

        FTPClient mockFtp = Mockito.mock(FTPClient.class);
        when(mockFtp.storeFile(any(String.class), any())).thenReturn(true);
        when(mockFtp.isConnected()).thenReturn(true);

        ActionContext<Object> mockCtx = Mockito.mock(ActionContext.class);
        Map<String, Object> args = new HashMap<>();
        args.put("file", tempFile.toString());
        when(mockCtx.arguments()).thenReturn(args);

        Map<String, Object> ftpConfig = new HashMap<>();
        ftpConfig.put("enabled", true);
        ftpConfig.put("host", "localhost");
        ftpConfig.put("port", 21);
        ftpConfig.put("username", "user");
        ftpConfig.put("password", "pass");
        ftpConfig.put("folder", "/upload");

        Map<String, Object> backup = new HashMap<>();
        backup.put("ftp", ftpConfig);

        var context = new CMSModuleContext();
		var config = mock(Configuration.class);
		var siteConfig = mock(SiteConfiguration.class);
		var siteProperties = mock(SiteProperties.class);
		
		when(config.get(SiteConfiguration.class)).thenReturn(siteConfig);
		when(siteConfig.siteProperties()).thenReturn(siteProperties);
		when(siteProperties.getOrDefault(Mockito.eq("backup"), Mockito.any())).thenReturn(backup);
		
		ConfigurationFeature cf = new ConfigurationFeature(config);
		context.add(ConfigurationFeature.class, cf);
		
        FTPUpload uploader = new FTPUpload();
		uploader.setContext(context);

        // Unserem Uploader den gemockten FTPClient geben
        uploader.setFtpClient(mockFtp);

        // ---- Test durchführen ----
        uploader.ftp_upload(mockCtx);

        // ---- Überprüfen ----
        verify(mockFtp).connect("localhost", 21);
        verify(mockFtp).login("user", "pass");
        verify(mockFtp).changeWorkingDirectory("/upload");
        verify(mockFtp).storeFile(eq(tempFile.getFileName().toString()), any());
        verify(mockFtp).logout();
        verify(mockFtp).disconnect();
    }
}
