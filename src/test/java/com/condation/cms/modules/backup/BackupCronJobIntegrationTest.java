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

import com.condation.cms.api.Constants;
import com.condation.cms.api.feature.features.InjectorFeature;
import com.condation.cms.api.feature.features.ServerHookSystemFeature;
import com.condation.cms.api.hooks.HookSystem;
import com.condation.cms.api.module.ServerModuleContext;
import com.condation.cms.api.scheduler.CronJobContext;
import com.condation.cms.api.utils.PathUtil;
import com.condation.cms.api.utils.ServerUtil;
import com.google.inject.Injector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BackupCronJobIntegrationTest {

    @Mock
    private ServerModuleContext serverModuleContext;
    @Mock
    private Configuration.Backup backupConfig;
    @Mock
    private CronJobContext cronJobContext;
    @Mock
    private InjectorFeature injectorFeature;
    @Mock
    private Injector injector;
    @Mock
    private HookSystem hookSystem;

    @TempDir
    Path tempDir;

    private Path targetPath;
    private Path serverHome;
    private BackupCronJob backupCronJob;

    @BeforeEach
    void setUp() throws Exception {
        // 1. Verzeichnisse im temporären Ordner anlegen
        targetPath = tempDir.resolve("backups");
        serverHome = tempDir.resolve("server_home");
        Files.createDirectories(targetPath);
        Files.createDirectories(serverHome);
		Files.createDirectories(serverHome.resolve(Constants.Folders.HOSTS));

        // 2. Mock-Kette für den HookSystem Zugriff vorbereiten
        lenient().when(serverModuleContext.get(InjectorFeature.class)).thenReturn(injectorFeature);
        lenient().when(injectorFeature.injector()).thenReturn(injector);
        lenient().when(injector.getInstance(HookSystem.class)).thenReturn(hookSystem);
		lenient().when(serverModuleContext.get(ServerHookSystemFeature.class)).thenReturn(new ServerHookSystemFeature(hookSystem));

        backupCronJob = new BackupCronJob(targetPath, serverModuleContext, backupConfig);
    }

    @Test
    void shouldCreateRealBackupFileAndTriggerHook() {
        // GIVEN
        String backupName = "integration-backup";
        Path sourceFile = serverHome.resolve("my-data.txt");
        createDummyFile(sourceFile, "Wichtige Daten");

        when(backupConfig.getName()).thenReturn(backupName);
        // Wichtig: Hier geben wir den echten Pfad als String zurück
        when(backupConfig.getInclude_files()).thenReturn(List.of(sourceFile.toString()));
        when(backupConfig.getInclude_dirs()).thenReturn(List.of());
        when(backupConfig.isProcessOnlyOnChange()).thenReturn(false);

        // Wir mocken NUR ServerUtil und PathUtil (externe API), 
        // TarGzPacker und BackupUtil laufen ECHT.
        try (MockedStatic<ServerUtil> serverUtilMock = Mockito.mockStatic(ServerUtil.class);
             MockedStatic<PathUtil> pathUtilMock = Mockito.mockStatic(PathUtil.class)) {

            // Server Home auf unser Temp-Dir umbiegen
            serverUtilMock.when(ServerUtil::getHome).thenReturn(serverHome);
            
            // PathUtil so einstellen, dass es unsere Temp-Pfade akzeptiert
            pathUtilMock.when(() -> PathUtil.isChild(eq(serverHome), any(Path.class))).thenReturn(true);

            // WHEN
            backupCronJob.accept(cronJobContext);

            // THEN
            // 1. Prüfen, ob eine echte Datei erstellt wurde
            try (Stream<Path> files = Files.list(targetPath)) {
                List<Path> createdFiles = files.toList();
                
                assertThat(createdFiles)
                        .withFailMessage("Es sollte genau eine Backup-Datei erstellt worden sein")
                        .hasSize(1);
                
                Path createdBackup = createdFiles.get(0);
                assertThat(createdBackup.getFileName().toString())
                        .startsWith(backupName)
                        .endsWith(".tar.gz");
                
                assertThat(Files.size(createdBackup))
                        .withFailMessage("Die Backup-Datei darf nicht leer sein")
                        .isGreaterThan(0);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            // 2. Verify Hook (wurde der Hook mit dem Pfad aufgerufen?)
            verify(hookSystem).execute(eq("module/backup/postprocess"), anyMap());
        }
    }

    @Test
    void shouldCreateChecksumFile_WhenChangeDetectionIsActive() throws Exception {
        // GIVEN
        String backupName = "checksum-test";
        Path sourceFile = serverHome.resolve("config.xml");
        createDummyFile(sourceFile, "<config>value</config>");

        when(backupConfig.getName()).thenReturn(backupName);
        when(backupConfig.getInclude_files()).thenReturn(List.of(sourceFile.toString()));
        when(backupConfig.getInclude_dirs()).thenReturn(List.of());
        when(backupConfig.isProcessOnlyOnChange()).thenReturn(true); // Aktiviert Checksum Logik

        try (MockedStatic<ServerUtil> serverUtilMock = Mockito.mockStatic(ServerUtil.class);
             MockedStatic<PathUtil> pathUtilMock = Mockito.mockStatic(PathUtil.class)) {

            serverUtilMock.when(ServerUtil::getHome).thenReturn(serverHome);
            pathUtilMock.when(() -> PathUtil.isChild(eq(serverHome), any(Path.class))).thenReturn(true);

            // WHEN
            backupCronJob.accept(cronJobContext);

            // THEN
            // Wir erwarten nun ZWEI Dateien: .tar.gz UND .sha256
            Path checksumFile = targetPath.resolve(backupName + ".sha256");
            
            assertThat(checksumFile)
                    .exists()
                    .content().isNotBlank(); // Prüft, ob BackupUtil wirklich etwas geschrieben hat
            
            // Prüfen, ob auch das TarGz da ist
            boolean hasTarGz = Files.list(targetPath)
                    .anyMatch(p -> p.toString().endsWith(".tar.gz"));
            assertThat(hasTarGz).isTrue();

            verify(hookSystem).execute(eq("module/backup/postprocess"), anyMap());
        }
    }

    /**
     * Testet den Fall, dass sich die Checksumme ändert.
     * Da TarGzPacker und BackupUtil echt sind, simulieren wir eine Änderung,
     * indem wir eine alte Checksum-Datei mit falschem Inhalt bereitstellen.
     */
    @Test
    void shouldOverwriteOldChecksum_WhenContentChanged() throws Exception {
        // GIVEN
        String backupName = "update-test";
        Path sourceFile = serverHome.resolve("dynamic.log");
        createDummyFile(sourceFile, "Log Entry 1");

        when(backupConfig.getName()).thenReturn(backupName);
        when(backupConfig.getInclude_files()).thenReturn(List.of(sourceFile.toString()));
        when(backupConfig.getInclude_dirs()).thenReturn(List.of());
        when(backupConfig.isProcessOnlyOnChange()).thenReturn(true);

        // Wir erstellen eine "alte" Checksum-Datei mit einem Fake-Hash
        Path checksumFile = targetPath.resolve(backupName + ".sha256");
        String fakeOldHash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"; // Empty SHA256
        Files.writeString(checksumFile, fakeOldHash);

        try (MockedStatic<ServerUtil> serverUtilMock = Mockito.mockStatic(ServerUtil.class);
             MockedStatic<PathUtil> pathUtilMock = Mockito.mockStatic(PathUtil.class)) {

            serverUtilMock.when(ServerUtil::getHome).thenReturn(serverHome);
            pathUtilMock.when(() -> PathUtil.isChild(eq(serverHome), any(Path.class))).thenReturn(true);

            // WHEN
            backupCronJob.accept(cronJobContext);

            // THEN
            // Die Checksum-Datei muss nun einen neuen Hash enthalten (den echten vom TarGz)
            String content = Files.readString(checksumFile);
            assertThat(content)
                    .isNotEqualTo(fakeOldHash)
                    .hasSize(64); // SHA256 Hex String Länge
            
            verify(hookSystem).execute(eq("module/backup/postprocess"), anyMap());
        }
    }

    // Helper
    private void createDummyFile(Path path, String content) {
        try {
            if (path.getParent() != null) Files.createDirectories(path.getParent());
            Files.writeString(path, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
