package com.condation.cms.modules.backup;

/*-
 * #%L
 * backup-server-module
 * %%
 * Copyright (C) 2025 - 2026 CondationCMS
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.condation.cms.api.extensions.server.ServerLifecycleExtensionPoint;
import com.condation.cms.api.feature.features.InjectorFeature;
import com.condation.cms.api.feature.features.ServerHookSystemFeature;
import com.condation.cms.api.hooks.HookSystem;
import com.condation.cms.api.module.ServerModuleContext;
import com.condation.cms.api.scheduler.CronJobScheduler;
import com.condation.cms.api.scheduler.CronJob;
import com.condation.cms.api.utils.PathUtil;
import com.condation.cms.api.utils.ServerUtil;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Names;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

public class BackupLifecycleExtensionTest {

    private BackupLifecycleExtension extension;
    private CronJobScheduler scheduler;
    private HookSystem hookSystem;
    private Path tempDir;
    private ServerModuleContext context;
	private ServerHookSystemFeature hookSystemFeature;
    private InjectorFeature injectorFeature;
    private Injector injector;

    @BeforeEach
    public void setUp(@TempDir Path tempDir) throws Exception {
        this.tempDir = tempDir;
		
		Files.createDirectory(tempDir.resolve("hosts"));

        // Mocks
        scheduler = mock(CronJobScheduler.class);
        hookSystem = mock(HookSystem.class);
		hookSystemFeature = mock(ServerHookSystemFeature.class);
        context = mock(ServerModuleContext.class);
        injectorFeature = mock(InjectorFeature.class);
        injector = mock(Injector.class);

        // Stubbing
        when(context.get(InjectorFeature.class)).thenReturn(injectorFeature);
        when(injectorFeature.injector()).thenReturn(injector);
        when(injector.getInstance(Key.get(CronJobScheduler.class, Names.named("server")))).thenReturn(scheduler);
        when(injector.getInstance(HookSystem.class)).thenReturn(hookSystem);
		when(hookSystemFeature.hookSystem()).thenReturn(hookSystem);
		when(context.get(ServerHookSystemFeature.class)).thenReturn(hookSystemFeature);

        // Class under test
        extension = new BackupLifecycleExtension();

        extension.setContext(context);
    }

    private void runBackup(Configuration config) {
        try (MockedStatic<ConfigLoader> mockedConfigLoader = mockStatic(ConfigLoader.class);
             MockedStatic<ServerUtil> mockedServerUtil = mockStatic(ServerUtil.class);
             MockedStatic<PathUtil> mockedPathUtil = mockStatic(PathUtil.class)) {

            mockedConfigLoader.when(ConfigLoader::load).thenReturn(Optional.of(config));
            mockedServerUtil.when(ServerUtil::getHome).thenReturn(tempDir);
            mockedPathUtil.when(() -> PathUtil.isChild(any(), any())).thenReturn(true);

            ArgumentCaptor<CronJob> taskCaptor = ArgumentCaptor.forClass(CronJob.class);
            extension.started();
            verify(scheduler).schedule(anyString(), anyString(), taskCaptor.capture());
			taskCaptor.getValue().accept(null);
        }
    }

    @Test
    public void testBackupSkipsPostProcessingWhenNoChanges() throws IOException, NoSuchAlgorithmException {
        // Arrange
        Path sourceFile = Files.createFile(tempDir.resolve("source.txt"));
        Files.writeString(sourceFile, "This is a test file.");

        Path tempBackupFile = tempDir.resolve("temp.tar.gz");
        TarGzPacker.createTarGz(tempDir, tempBackupFile.toFile(), Collections.singletonList(sourceFile));
        String checksum = BackupUtil.calculateSHA256(tempBackupFile);
        Files.delete(tempBackupFile);

        Path checksumFile = tempDir.resolve("testBackup.sha256");
        Files.writeString(checksumFile, checksum);

        Configuration.Backup backupConfig = new Configuration.Backup();
        backupConfig.setName("testBackup");
        backupConfig.setEnabled(true);
        backupConfig.setProcessOnlyOnChange(true);
        backupConfig.setTarget(tempDir.toString());
        backupConfig.setCron("0 0 * * *");
        backupConfig.setInclude_files(Collections.singletonList(sourceFile.toString()));

        Configuration config = new Configuration();
        config.setBackups(Collections.singletonList(backupConfig));

        // Act
        runBackup(config);

        // Assert
        verify(hookSystem, never()).execute(anyString(), any(Map.class));
        try (Stream<Path> files = Files.list(tempDir)) {
            long backupFileCount = files.filter(p -> p.toString().endsWith(".tar.gz")).count();
            assertEquals(0, backupFileCount, "No backup file should exist as it should be deleted.");
        }
    }

    @Test
    public void testBackupRunsPostProcessingWhenChanges() throws IOException, NoSuchAlgorithmException {
        // Arrange
        Path sourceFile = Files.createFile(tempDir.resolve("source.txt"));
        Files.writeString(sourceFile, "This is a test file.");

        Path tempBackupFile = tempDir.resolve("temp.tar.gz");
        TarGzPacker.createTarGz(tempDir, tempBackupFile.toFile(), Collections.singletonList(sourceFile));
        String checksum = BackupUtil.calculateSHA256(tempBackupFile);
        Files.delete(tempBackupFile);

        Path checksumFile = tempDir.resolve("testBackup.sha256");
        Files.writeString(checksumFile, checksum);

        // Change the source file so the new backup has a different checksum
        Files.writeString(sourceFile, "This is a modified test file.");

        Configuration.Backup backupConfig = new Configuration.Backup();
        backupConfig.setName("testBackup");
        backupConfig.setEnabled(true);
        backupConfig.setProcessOnlyOnChange(true);
        backupConfig.setTarget(tempDir.toString());
        backupConfig.setCron("0 0 * * *");
        backupConfig.setInclude_files(Collections.singletonList(sourceFile.toString()));

        Configuration config = new Configuration();
        config.setBackups(Collections.singletonList(backupConfig));

        // Act
        runBackup(config);

        // Assert
        verify(hookSystem, times(1)).execute(eq("module/backup/postprocess"), any(Map.class));
        try (Stream<Path> files = Files.list(tempDir)) {
            long backupFileCount = files.filter(p -> p.toString().endsWith(".tar.gz")).count();
            assertEquals(1, backupFileCount, "A new backup file should exist.");
        }
        assertTrue(Files.exists(checksumFile));
    }
}
