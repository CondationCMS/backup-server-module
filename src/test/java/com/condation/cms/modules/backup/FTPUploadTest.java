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

import com.condation.cms.api.hooks.ActionContext;
import org.apache.commons.net.ftp.FTPClient;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class FTPUploadTest {

    @Test
    void testFtpUploadSuccessful() throws Exception {
        // --- Temporäre Testdatei anlegen ---
        Path tempFile = Files.createTempFile("backup-test", ".zip");
        Files.writeString(tempFile, "dummy content");

        // --- FTPClient mocken ---
        FTPClient mockFtp = Mockito.mock(FTPClient.class);
        when(mockFtp.storeFile(any(String.class), any())).thenReturn(true);
        when(mockFtp.isConnected()).thenReturn(true);

        // --- ActionContext mocken ---
        ActionContext<Object> mockCtx = Mockito.mock(ActionContext.class);
        Map<String, Object> args = new HashMap<>();
        args.put("file", tempFile.toString());
        args.put("name", "testBackup");
        when(mockCtx.arguments()).thenReturn(args);

        // --- Backup-Konfiguration vorbereiten ---
        Map<String, Object> ftpConfig = new HashMap<>();
        ftpConfig.put("enabled", true);
        ftpConfig.put("host", "localhost");
        ftpConfig.put("port", 21);
        ftpConfig.put("username", "user");
        ftpConfig.put("password", "pass");
        ftpConfig.put("folder", "/upload");

        // Post-Processing-Objekt (wie in der Methode erwartet)
        Configuration.PostProcessing ftpProcessing = new Configuration.PostProcessing();
        ftpProcessing.setType("ftp");
        ftpProcessing.setEnabled(true);
        ftpProcessing.setConfig(ftpConfig);

        Configuration.Backup backup = new Configuration.Backup();
        backup.setName("testBackup");
        backup.setPost_processing(Collections.singletonList(ftpProcessing));

        Configuration backupConfig = new Configuration();
        backupConfig.setBackups(Collections.singletonList(backup));

        // --- ConfigLoader mocken ---
        try (MockedStatic<ConfigLoader> mockedLoader = Mockito.mockStatic(ConfigLoader.class)) {
            mockedLoader.when(ConfigLoader::load).thenReturn(Optional.of(backupConfig));

            FTPUpload uploader = new FTPUpload();
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
}
