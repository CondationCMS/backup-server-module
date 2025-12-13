package com.condation.cms.modules.backup;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 *
 * @author thmar
 */
public class SiteUtil {

	public static boolean isSite(Path check) {
		return Files.exists(check.resolve("site.yaml"))
				|| Files.exists(check.resolve("site.toml"));
	}
}
