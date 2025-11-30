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
import com.condation.cms.api.Constants;
import com.condation.cms.api.utils.ServerUtil;
import com.condation.cms.api.utils.SiteUtil;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipParameters;
import org.apache.commons.io.IOUtils;

/**
 *
 * @author thmar
 */
@Slf4j
public class TarGzPacker {

	/**
	 * Erstellt ein TAR.GZ-Archiv aus den angegebenen Quellpfaden.
	 *
	 * @param root Der Basisverzeichnis, unter dem die Quellen liegen
	 * @param output Die Ausgabedatei (tar.gz)
	 * @param sources Liste der zu archivierenden Dateien/Verzeichnisse
	 * @throws IOException Bei Ein-/Ausgabefehler
	 */
	public static void createTarGz(Path root, File output, List<Path> sources) throws IOException {
		try (FileOutputStream fos = new FileOutputStream(output); 
				BufferedOutputStream bos = new BufferedOutputStream(fos); 
				GzipCompressorOutputStream gos = new GzipCompressorOutputStream(bos); 
				TarArchiveOutputStream taos = new TarArchiveOutputStream(gos)) {

			// Wichtig für große Dateien und lange Pfade
			taos.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_STAR);
			taos.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);

			List<Path> hostsData = getHostsDataDirectories(root);
			List<Path> searchIndex = getSearchIndexPath(root);
			
			List<Path> ignoredDrectory = new ArrayList<>(hostsData);
			ignoredDrectory.addAll(searchIndex);
			
			// Für jede Quelle
			for (Path source : sources) {
				Path absolutePath = root.resolve(source);

				if (!Files.exists(absolutePath)) {
					throw new FileNotFoundException("Quelle nicht gefunden: " + absolutePath);
				}

				// Datei oder Verzeichnis hinzufügen
				addToArchive(taos, absolutePath, root, ignoredDrectory);
			}

			taos.finish();
			
			
		}
	}

	/**
	 * Rekursiv Dateien und Verzeichnisse zum Archiv hinzufügen.
	 *
	 * @param taos TarArchiveOutputStream
	 * @param filePath Pfad der aktuellen Datei/des Verzeichnisses
	 * @param root Der Basis-Verzeichnis (für relative Pfade im Archiv)
	 * @throws IOException Bei Ein-/Ausgabefehler
	 */
	private static void addToArchive(TarArchiveOutputStream taos, Path filePath, Path root, List<Path> ignoredDrectory)
			throws IOException {

		// Relativen Pfad für das Archiv berechnen
		Path relativePath = root.relativize(filePath);
		String entryName = relativePath.toString().replace("\\", "/");

		File file = filePath.toFile();

		if (shouldExclude(filePath, ignoredDrectory)) {
			return;
		}

		if (file.isDirectory()) {
			// Verzeichniseintrag hinzufügen
			TarArchiveEntry entry = new TarArchiveEntry(file, entryName + "/");
			taos.putArchiveEntry(entry);
			taos.closeArchiveEntry();

			// Verzeichnis rekursiv durchlaufen
			File[] children = file.listFiles();
			if (children != null) {
				for (File child : children) {
					addToArchive(taos, child.toPath(), root, ignoredDrectory);
				}
			}
		} else {
			// Datei hinzufügen
			TarArchiveEntry entry = new TarArchiveEntry(file, entryName);
			entry.setSize(file.length());
			taos.putArchiveEntry(entry);

			try (FileInputStream fis = new FileInputStream(file); BufferedInputStream bis = new BufferedInputStream(fis)) {
				IOUtils.copy(bis, taos);
			} catch (Exception e) {
				log.error("error copying file", e);
			} finally {
				taos.closeArchiveEntry();

			}
		}
	}

	private static List<Path> getHostsDataDirectories(Path root) throws IOException {
		try (var siteStream = Files.list(root.resolve(Constants.Folders.HOSTS))) {
			return siteStream
					.filter(SiteUtil::isSite)
					.map(host -> host.resolve("data"))
					.collect(Collectors.toList());
		}
	}
	private static List<Path> getSearchIndexPath(Path root) throws IOException {
		try (var siteStream = Files.list(root.resolve(Constants.Folders.HOSTS))) {
			return siteStream
					.filter(SiteUtil::isSite)
					.map(host -> host.resolve("modules_data/search-module/index"))
					.collect(Collectors.toList());
		}
	}

	private static boolean shouldExclude(Path filePath, List<Path> ignoredDirectories) {
		for (Path ignored : ignoredDirectories) {
			if (filePath.startsWith(ignored)) {  // ← Prüft Unterpfade!
				return true;
			}
		}
		return false;
	}

}
