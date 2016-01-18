package org.bimserver.ifcengine;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.bimserver.emf.PackageMetaData;
import org.bimserver.models.store.ObjectDefinition;
import org.bimserver.plugins.Dependency;
import org.bimserver.plugins.PluginConfiguration;
import org.bimserver.plugins.PluginContext;
import org.bimserver.plugins.PluginManagerInterface;
import org.bimserver.plugins.renderengine.RenderEngine;
import org.bimserver.plugins.renderengine.RenderEngineException;
import org.bimserver.plugins.renderengine.RenderEnginePlugin;
import org.bimserver.shared.exceptions.PluginException;
import org.bimserver.utils.PathUtils;

public class JvmRenderEnginePlugin implements RenderEnginePlugin {

	private PluginManagerInterface pluginManager;
	private boolean initialized = false;
	private Path nativeFolder;
	private Path schemaFile;
	private PluginContext pluginContext;

	@Override
	public String getVersion() {
		return "1.0";
	}

	@Override
	public void init(PluginManagerInterface pluginManager) throws PluginException {
		this.pluginManager = pluginManager;
		try {
			pluginContext = pluginManager.getPluginContext(this);
			String os = System.getProperty("os.name").toLowerCase();
			String libraryName = "";
			if (os.contains("windows")) {
				libraryName = "ifcengine.dll";
			} else if (os.contains("osx") || os.contains("os x") || os.contains("darwin")) {
				libraryName = "libIFCEngine.dylib";
			} else if (os.contains("linux")) {
				libraryName = "libifcengine.so";
			}
			InputStream inputStream = Files.newInputStream(pluginContext.getRootPath().resolve("lib/" + System.getProperty("sun.arch.data.model") + "/" + libraryName));
			if (inputStream != null) {
				try {
					Path tmpFolder = pluginManager.getTempDir();
					nativeFolder = tmpFolder.resolve("ifcenginedll");
					Path file = nativeFolder.resolve(libraryName);
					if (Files.exists(nativeFolder)) {
						try {
							PathUtils.removeDirectoryWithContent(nativeFolder);
						} catch (IOException e) {
							// Ignore
						}
					}
					Files.createDirectories(nativeFolder);
					OutputStream outputStream = Files.newOutputStream(file);
					try {
						IOUtils.copy(inputStream, outputStream);
					} finally {
						outputStream.close();
					}
					initialized = true;
				} finally {
					inputStream.close();
				}
			}
		} catch (Exception e) {
			throw new PluginException(e);
		}
	}

	@Override
	public String getDescription() {
		return "Native implementation of an IFC Engine by RDF";
	}

	@Override
	public RenderEngine createRenderEngine(PluginConfiguration pluginConfiguration, String schema) throws RenderEngineException {
		try {
			PackageMetaData packageMetaData = pluginManager.getMetaDataManager().getPackageMetaData(schema);
			schemaFile = packageMetaData.getSchemaPath();
			if (schemaFile == null) {
				throw new RenderEngineException("No schema file");
			}
			List<String> classPathEntries = new ArrayList<>();
			
			for (Dependency dependency : pluginContext.getDependencies()) {
				Path path = dependency.getPath();
				classPathEntries.add(path.toAbsolutePath().toString());
			}
			
			pluginContext.getClassLocation();
			
			return new JvmIfcEngine(schemaFile, nativeFolder, pluginManager.getTempDir(), pluginContext.getClassLocation(), classPathEntries);
		} catch (PluginException e) {
			throw new RenderEngineException(e);
		}
	}

	@Override
	public boolean isInitialized() {
		return initialized;
	}

	@Override
	public String getDefaultName() {
		return "IFC Engine DLL";
	}

	@Override
	public ObjectDefinition getSettingsDefinition() {
		return null;
	}
}
