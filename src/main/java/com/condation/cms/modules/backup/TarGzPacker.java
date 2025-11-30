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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.compress.utils.IOUtils;

public class TarGzPacker {
    public static void createTarGz(Path basePath, File outputFile, List<Path> sources) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(outputFile);
             GzipCompressorOutputStream gzos = new GzipCompressorOutputStream(fos);
             TarArchiveOutputStream taos = new TarArchiveOutputStream(gzos)) {

            for (Path source : sources) {
                if (Files.isDirectory(source)) {
                    try (Stream<Path> stream = Files.walk(source)) {
                        stream.filter(p -> !Files.isDirectory(p)).forEach(p -> {
                            try {
                                addFileToTar(basePath, p, taos);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });
                    }
                } else {
                    addFileToTar(basePath, source, taos);
                }
            }
        }
    }

    private static void addFileToTar(Path basePath, Path file, TarArchiveOutputStream taos) throws IOException {
        String relativePath = basePath.relativize(file).toString();
        TarArchiveEntry entry = new TarArchiveEntry(file.toFile(), relativePath);
        taos.putArchiveEntry(entry);
        try (FileInputStream fis = new FileInputStream(file.toFile())) {
            IOUtils.copy(fis, taos);
        }
        taos.closeArchiveEntry();
    }
}
