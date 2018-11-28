/**
 * This file is part of gwt-gradle-plugin.
 *
 * gwt-gradle-plugin is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * gwt-gradle-plugin is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the
 * implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser
 * General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with gwt-gradle-plugin. If not,
 * see <http://www.gnu.org/licenses/>.
 */
package de.esoco.gwt.gradle;

import de.esoco.gwt.gradle.extension.GwtExtension;

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.plugins.MavenPlugin;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.testing.Test;
import org.gradle.plugins.ide.eclipse.model.EclipseModel;
import org.gradle.plugins.ide.eclipse.model.EclipseProject;

public class GwtLibPlugin implements Plugin<Project> {

	public static final String CONF_GWT_SDK = "gwtSdk";
	public static final String CONF_JETTY = "jettyConf";

	private static final String ECLIPSE_NATURE = "com.gwtplugins.gwt.eclipse.core.gwtNature";
	private static final String ECLIPSE_GWT_CONTAINER = "com.gwtplugins.gwt.eclipse.core.GWT_CONTAINER";
	private static final String ECLIPSE_BUILDER_PROJECT_VALIDATOR = "com.gwtplugins.gwt.eclipse.core.gwtProjectValidator";
	private static final String ECLIPSE_BUILDER_WEBAPP_VALIDATOR = "com.gwtplugins.gdt.eclipse.core.webAppProjectValidator";

	@Override
	public void apply(Project project) {

		project.getPlugins().apply(JavaPlugin.class);
		project.getPlugins().apply(MavenPlugin.class);

		final GwtExtension extension = project.getExtensions().create(GwtExtension.NAME,
			GwtExtension.class, project.getObjects(), project.getLayout());

		ConfigurationContainer configurationContainer = project.getConfigurations();

		Configuration gwtSdkConfiguration = configurationContainer.create(CONF_GWT_SDK);
		gwtSdkConfiguration.setVisible(false);

		extension.getCompile().getGwtSdkClasspath().from(gwtSdkConfiguration);

		SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
		SourceSet main = sourceSets.getByName("main");
		extension.getCompile().getCompileClasspath().from(main.getCompileClasspath());

		Configuration jettyConfiguration = configurationContainer.create(CONF_JETTY);
		jettyConfiguration.setVisible(false);

		includeSourcesToJar(project);

		project.afterEvaluate(new Action<Project>() {
			@Override
			public void execute(final Project p) {
				String gwtVersion = extension.getGwtVersion();
				String jettyVersion = extension.getJettyVersion();

				DependencyHandler dependencies = p.getDependencies();
				dependencies.add(CONF_GWT_SDK,
					"com.google.gwt:gwt-codeserver" + ":" + gwtVersion);
				dependencies.add(CONF_GWT_SDK,
					"com.google.gwt:gwt-user" + ":" + gwtVersion);
				dependencies.add(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME,
					"com.google.gwt:gwt-codeserver" + ":" + gwtVersion);
				dependencies.add(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME,
					"com.google.gwt:gwt-user" + ":" + gwtVersion);
				dependencies.add(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME,
					"com.google.gwt:gwt-user" + ":" + gwtVersion);

				if (extension.isGwtElementalLib()) {
					dependencies.add(
						JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME, "com.google.gwt:gwt-elemental" + ":" + gwtVersion);
				}
				if (extension.isGwtServletLib()) {
					dependencies.add(
						JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME, "com.google.gwt:gwt-servlet" + ":" + gwtVersion);
				}

				dependencies.add(CONF_JETTY, "org.eclipse.jetty:jetty-runner" + ":" + jettyVersion);

				includeSourcesForTest(p);
				initGwtEclipsePlugin(p);
			}
		});
		
	}

	private void includeSourcesForTest(Project project) {
		JavaPluginConvention javaConvention = project.getConvention().getPlugin(JavaPluginConvention.class);
		SourceSet mainSourset = javaConvention.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
		SourceSet testSourset = javaConvention.getSourceSets().getByName(SourceSet.TEST_SOURCE_SET_NAME);

		FileCollection testClasspath = project
			.files(mainSourset.getAllSource().getSrcDirs().toArray())
			.plus(project.files(testSourset.getAllSource().getSrcDirs().toArray()))
			.plus(testSourset.getRuntimeClasspath());
		testSourset.setRuntimeClasspath(testClasspath);

		Test test = project.getTasks().withType(Test.class).getByName("test");
		test.getSystemProperties().put("gwt.persistentunitcachedir", 
			                           project.getBuildDir() + GwtExtension.DIRECTORY + "/test");
	}

	private void includeSourcesToJar(Project project) {
		Jar jarTask = project.getTasks().withType(Jar.class).getByName("jar");
		JavaPluginConvention javaConvention = project.getConvention().getPlugin(JavaPluginConvention.class);
		SourceSet mainSourset = javaConvention.getSourceSets().getByName("main");
		jarTask.from(mainSourset.getAllSource());
	}

	private void initGwtEclipsePlugin(final Project project) {
		final GwtExtension gwtExtension = (GwtExtension) project.getExtensions()
			.getByName(GwtExtension.NAME);

		if (project.getPlugins().hasPlugin("eclipse") && gwtExtension.isGwtPluginEclipse()) {
			final EclipseModel eclipseModel = 
				project.getExtensions().getByType(EclipseModel.class);

			final EclipseProject eclipseProject = eclipseModel.getProject();
			
			eclipseProject.natures(ECLIPSE_NATURE);
			eclipseProject.buildCommand(ECLIPSE_BUILDER_PROJECT_VALIDATOR);
			eclipseModel.getClasspath().getContainers().add(ECLIPSE_GWT_CONTAINER);
			
			project.getPlugins().withType(GwtPlugin.class, new Action<GwtPlugin>() {
				@Override
				public void execute(GwtPlugin warPlugin) {
					eclipseProject.buildCommand(ECLIPSE_BUILDER_WEBAPP_VALIDATOR);
				}
			});
		}
	}
}
