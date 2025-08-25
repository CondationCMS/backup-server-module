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
import com.condation.cms.api.annotations.Action;
import com.condation.cms.api.extensions.server.ServerHookSystemRegisterExtensionPoint;
import com.condation.cms.api.hooks.ActionContext;
import com.condation.modules.api.annotation.Extension;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;

/**
 *
 * @author t.marx
 */
@Slf4j
@Extension(ServerHookSystemRegisterExtensionPoint.class)
public class FTPUpload extends ServerHookSystemRegisterExtensionPoint {

	private FTPClient ftpClient = new FTPClient();
	
	public void setFtpClient (FTPClient ftpClient) {
		this.ftpClient = ftpClient;
	}
	
	
	@Action("module/backup/postprocess")
	public void ftp_upload(ActionContext<?> context) throws IOException {
		var backupConfig = ConfigLoader.load();
		if (backupConfig.isEmpty()) {
			return;
		}

		var name = (String) context.arguments().get("name");
		
		var byNameConfig = backupConfig.get().getBackups().stream().filter(backup -> backup.getName().equals(name)).findFirst();
		
		if (byNameConfig.isEmpty()) {
			log.warn("backup config for '{}' not found", name);
			return;
		}
		
		var ftpConfigOpt = byNameConfig.get().getPost_processing().stream().filter(processing -> processing.getType().equals("ftp")).findFirst();
		
		if (ftpConfigOpt.isEmpty()) {
			log.warn("no ftp config found for '{}'", name);
			return;
		}
		
		if (!ftpConfigOpt.get().isEnabled()) {
			return;
		}
		var ftpConfig = ftpConfigOpt.get().getConfig();
		if (!(boolean) ftpConfig.getOrDefault("enabled", false)) {
			log.debug("ftp backup disabled");
			return;
		}

		var fileName = (String) context.arguments().get("file");
		var file = Path.of(fileName);

		
		try {
			ftpClient.connect(
					(String) ftpConfig.getOrDefault("host", null),
					(int) ftpConfig.getOrDefault("port", null)
			);
			ftpClient.login(
					(String) ftpConfig.getOrDefault("username", null),
					(String) ftpConfig.getOrDefault("password", null)
			);

			if (ftpConfig.containsKey("folder")) {
				ftpClient.changeWorkingDirectory((String) ftpConfig.get("folder"));
			}
			ftpClient.enterLocalPassiveMode();
			ftpClient.setFileType(FTP.BINARY_FILE_TYPE);

			try (InputStream inputStream = Files.newInputStream(file)) {
				System.out.println("Starte Upload...");

				boolean done = ftpClient.storeFile(file.getFileName().toString(), inputStream);
				if (done) {
					log.debug("Backup ftp upload sucessfull!");
				} else {
					log.debug("Upload failed!");
				}
			}

			log.debug("backup file uploaded");
		} catch (Exception e) {
			log.error("", e);
		} finally {
			try {
				if (ftpClient.isConnected()) {
					ftpClient.logout();
					ftpClient.disconnect();
				}
			} catch (Exception ex) {
				log.warn("error while closing ftp connection", ex);
			}
		}
	}

}
