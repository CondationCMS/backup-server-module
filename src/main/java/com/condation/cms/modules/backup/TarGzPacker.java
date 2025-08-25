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

import com.condation.cms.api.utils.ServerUtil;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.io.IOUtils;

/**
 *
 * @author thmar
 */
public class TarGzPacker {

	public static void createTarGz(Path root, File output, List<Path> sources) throws IOException {
        Path rootPath = root.toAbsolutePath().normalize();

        try (FileOutputStream fos = new FileOutputStream(output);
             BufferedOutputStream bos = new BufferedOutputStream(fos);
             GzipCompressorOutputStream gzos = new GzipCompressorOutputStream(bos);
             TarArchiveOutputStream taos = new TarArchiveOutputStream(gzos)) {

            taos.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);

			for (Path source : sources) {
                Path sourcePath = source.toAbsolutePath().normalize();
                if (!sourcePath.startsWith(rootPath)) {
                    throw new IllegalArgumentException("source directory not inside server home: " + source);
                }
                addFileToTarGz(taos, sourcePath, rootPath);
            }
        }
    }

    private static void addFileToTarGz(TarArchiveOutputStream taos, Path path, Path root) throws IOException {
        Path relativePath = root.relativize(path);
        String entryName = relativePath.toString().replace("\\", "/");

        TarArchiveEntry entry = new TarArchiveEntry(path.toFile(), entryName);
        taos.putArchiveEntry(entry);

        if (Files.isRegularFile(path)) {
            try (InputStream is = Files.newInputStream(path)) {
                IOUtils.copy(is, taos);
            }
            taos.closeArchiveEntry();
        } else if (Files.isDirectory(path)) {
            taos.closeArchiveEntry();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                for (Path child : stream) {
                    addFileToTarGz(taos, child, root);
                }
            }
        }
    }
}
