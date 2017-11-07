package com.prot.tool.controller;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.core.io.support.ResourcePatternUtils;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/api")
public class VersionInfoController {
	private static final Logger logger = LoggerFactory.getLogger(VersionInfoController.class);
	private static final String CPAF_VERSION_INFO_FILE_NAME = "version-info.properties";

	@Autowired
	private ResourceLoader resourceLoader;

	@GetMapping(path = "version-info", produces = MediaType.APPLICATION_JSON_VALUE)
	public @ResponseBody Map<String, Object> getLibVersionInfo(
			@RequestParam(name = "lib", required = false, defaultValue = "all") String libName) {
		logger.debug("retrieve '{}' version info", libName);
		String regex = ".*/versionInfo/(.*)/version-info.properties";
		Pattern p = Pattern.compile(regex);

		Map<String, Object> ret = new HashMap<>(32);
		if ("all".equalsIgnoreCase(libName)) {
			try {
				collectWildcharLibVersionInfo(p, ret, "*");
			} catch (IOException ioEx) {
				ret.put("fail to get [" + libName + "] version info", ExceptionUtils.getStackTrace(ioEx));
			}
		} else {
			String[] libs = libName.split(",");
			for (String lib : libs) {
				if (lib == null || (lib = lib.trim()).length() == 0) {
					continue;
				}
				if (lib.indexOf('*') >= 0) {
					try {
						collectWildcharLibVersionInfo(p, ret, lib);
					} catch (IOException e) {
						ret.put("fail to get [" + lib + "] version info", ExceptionUtils.getStackTrace(e));
					}
				} else {
					Resource resource = ResourcePatternUtils.getResourcePatternResolver(resourceLoader)
							.getResource("classpath:/versionInfo/" + lib + "/" + CPAF_VERSION_INFO_FILE_NAME);
					Map<String, Object> oneInfo = loadOneLibVersion(resource);
					ret.put(lib, oneInfo);
				}
			}
		}
		return ret;
	}

	private void collectWildcharLibVersionInfo(Pattern p, Map<String, Object> collector, String wildcharLib)
			throws IOException {
		Resource[] resources = ResourcePatternUtils.getResourcePatternResolver(resourceLoader)
				.getResources("classpath*:/versionInfo/" + wildcharLib + "/" + CPAF_VERSION_INFO_FILE_NAME);
		for (Resource r : resources) {
			String artifactName = resolveArtifactName(p, r.toString());
			Map<String, Object> oneInfo = loadOneLibVersion(r);
			collector.put(artifactName, oneInfo);
		}
	}

	private String resolveArtifactName(Pattern p, String resourcePath) {
		Matcher m = p.matcher(resourcePath);
		m.find();
		String artifactName = m.group(1);
		return artifactName;
	}

	private Map<String, Object> loadOneLibVersion(Resource resource) {
		Map<String, Object> ret = new HashMap<>(8);
		try {
			Properties p = PropertiesLoaderUtils.loadProperties(resource);
			for (Object k : p.keySet()) {
				Object v = p.get(k);
				ret.put((String) k, (String) v);
			}
		} catch (IOException ex) {
			logger.warn("unable to load cpaf version info file from :" + resource.getDescription(), ex);
			ret.put("version", "not avaible due to error");
			ret.put("error-info", ExceptionUtils.getMessage(ex));
		}
		return ret;
	}
}
