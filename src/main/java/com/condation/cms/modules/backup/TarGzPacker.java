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
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.io.IOUtils;

/**
 *
 * @author thmar
 */
@Slf4j
public class TarGzPacker {

	private static final long FIXED_TIMESTAMP = LocalDateTime.of(2026, 1, 1, 0, 0, 0)
			.atZone(ZoneId.of("UTC"))
			.toInstant()
			.getEpochSecond();

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

		Path relativePath = root.relativize(filePath);
		String entryName = relativePath.toString().replace("\\", "/");
		File file = filePath.toFile();

		if (shouldExclude(filePath, ignoredDrectory)) {
			return;
		}

		if (file.isDirectory()) {
			// ← WICHTIG: Verwende NICHT TarArchiveEntry(file, name)
			// Erstelle einen leeren Entry mit ONLY dem Namen
			TarArchiveEntry entry = new TarArchiveEntry(entryName + "/");
			entry.setSize(0);
			entry.setModTime(FIXED_TIMESTAMP);
			taos.putArchiveEntry(entry);
			taos.closeArchiveEntry();

			File[] children = file.listFiles();
			if (children != null) {
				Arrays.sort(children);
				for (File child : children) {
					addToArchive(taos, child.toPath(), root, ignoredDrectory);
				}
			}
		} else {
			TarArchiveEntry entry = new TarArchiveEntry(entryName);
			entry.setSize(file.length());
			entry.setModTime(FIXED_TIMESTAMP);
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

	public static String createTarGz(Path root, File output, List<Path> sources) throws IOException {

		File tempTar = File.createTempFile("backup_", ".tar");
		tempTar.deleteOnExit();

		try {
			// 1. Erstelle TAR
			try (FileOutputStream fos = new FileOutputStream(tempTar); TarArchiveOutputStream taos = new TarArchiveOutputStream(fos)) {

				taos.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_STAR);
				taos.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);

				List<Path> hostsData = getHostsDataDirectories(root);
				List<Path> searchIndex = getSearchIndexPath(root);

				List<Path> ignoredDrectory = new ArrayList<>(hostsData);
				ignoredDrectory.addAll(searchIndex);

				List<Path> sortedSources = new ArrayList<>(sources);
				sortedSources.sort(Path::compareTo);

				for (Path source : sortedSources) {
					Path absolutePath = root.resolve(source);

					if (!Files.exists(absolutePath)) {
						throw new FileNotFoundException("Quelle nicht gefunden: " + absolutePath);
					}

					addToArchive(taos, absolutePath, root, ignoredDrectory);
				}

				taos.finish();
			}

			String tarHash = calculateFileHash(tempTar);

			try (FileInputStream fis = new FileInputStream(tempTar); FileOutputStream fos = new FileOutputStream(output); GzipCompressorOutputStream gos = new GzipCompressorOutputStream(fos)) {

				byte[] buffer = new byte[65536];
				int bytesRead;
				while ((bytesRead = fis.read(buffer)) != -1) {
					gos.write(buffer, 0, bytesRead);
				}
			}

			return tarHash;

		} finally {
			tempTar.delete();
		}
	}

	private static String calculateFileHash(File file) throws IOException {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			try (FileInputStream fis = new FileInputStream(file); BufferedInputStream bis = new BufferedInputStream(fis)) {
				byte[] buffer = new byte[8192];
				int bytesRead;
				while ((bytesRead = bis.read(buffer)) != -1) {
					md.update(buffer, 0, bytesRead);
				}
			}
			String hash = bytesToHex(md.digest());
			return hash;
		} catch (NoSuchAlgorithmException ex) {
			log.error("error creating hash", ex);
		}
		return null;
	}

	// Helper-Methode (vereinfacht, benötigt Implementierung)
	private static String bytesToHex(byte[] bytes) {
		// ... Implementierung zur Umwandlung des Byte-Arrays in einen Hex-String
		// (Diese Logik sollte in Ihrem BackupUtil enthalten sein)
		return BackupUtil.bytesToHex(bytes);
	}

}
