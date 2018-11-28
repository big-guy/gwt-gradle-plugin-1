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
package de.esoco.gwt.gradle.task;

import com.google.common.base.Strings;
import de.esoco.gwt.gradle.action.JavaAction;
import de.esoco.gwt.gradle.extension.CompilerOption;
import de.esoco.gwt.gradle.extension.GwtExtension;
import de.esoco.gwt.gradle.helper.CompileCommandBuilder;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskAction;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.List;
import java.util.concurrent.Callable;

@CacheableTask
public class GwtCompileTask extends AbstractTask {

	public static final String NAME = "gwtCompile";

	private final ListProperty<String> modules = getProject().getObjects().listProperty(String.class);
	private final DirectoryProperty war = getProject().getObjects().directoryProperty();
	private final ConfigurableFileCollection src = getProject().files();

	public GwtCompileTask() {
		setDescription("Compile the GWT modules");

		dependsOn(JavaPlugin.COMPILE_JAVA_TASK_NAME, JavaPlugin.PROCESS_RESOURCES_TASK_NAME);
	}

	@TaskAction
	public void exec() {
		GwtExtension extension = getProject().getExtensions().getByType(GwtExtension.class);
		CompilerOption compilerOptions = extension.getCompile();
		// TODO: This should be folded into the compiler option with Provider API
		if (!Strings.isNullOrEmpty(extension.getSourceLevel()) &&
			Strings.isNullOrEmpty(compilerOptions.getSourceLevel())) {
			compilerOptions.setSourceLevel(extension.getSourceLevel());
		}

		CompileCommandBuilder commandBuilder = new CompileCommandBuilder();
		commandBuilder.configure(compilerOptions, getSrc(), getWar().get().getAsFile(), getModules().get());
		JavaAction compileAction = commandBuilder.buildJavaAction();
		compileAction.execute(this);
		compileAction.join();
		if (compileAction.exitValue() != 0) {
			throw new RuntimeException("Fail to compile GWT modules");
		}

		getProject().getTasks().getByName(GwtCheckTask.NAME).setEnabled(false);
	}

	public void configure(final Project project, final GwtExtension extension) {
		final CompilerOption options = extension.getCompile();
		options.setLocalWorkers(evalWorkers(options));

		modules.set(project.provider(new Callable<List<String>>() {
			@Override
			public List<String> call()  {
				return extension.getModule();
			}
		}));
		war.set(options.getWar());

		JavaPluginConvention javaConvention = project.getConvention().findPlugin(JavaPluginConvention.class);
		if (javaConvention != null) {
			SourceSet mainSourceSet = javaConvention.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
			src.from(project.files(mainSourceSet.getOutput().getResourcesDir()))
					.from(project.files(mainSourceSet.getOutput().getClassesDirs()))
					.from(project.files(mainSourceSet.getAllSource().getSrcDirs()));
		}
	}

	private int evalWorkers(CompilerOption options) {
		long workers = Runtime.getRuntime().availableProcessors();
		OperatingSystemMXBean osMBean = ManagementFactory.getOperatingSystemMXBean();
		if (osMBean instanceof com.sun.management.OperatingSystemMXBean) {
			com.sun.management.OperatingSystemMXBean sunOsMBean = (com.sun.management.OperatingSystemMXBean) osMBean;
			long memPerWorker = 1024L * 1024L * options.getLocalWorkersMem();
			long nbFreeMemInGb = sunOsMBean.getFreePhysicalMemorySize() / memPerWorker;

			if (nbFreeMemInGb < workers) {
				workers = nbFreeMemInGb;
			}
			if (workers < 1) {
				workers = 1;
			}
		}
		return (int) workers;
	}

	@OutputDirectory
	public DirectoryProperty getWar() {
		return war;
	}

	@Input
	public ListProperty<String> getModules() {
		return modules;
	}

	@SkipWhenEmpty
	@InputFiles
	public ConfigurableFileCollection getSrc() {
		return src;
	}

	@Nested
	public CompilerOption getCompilerOptions() {
		GwtExtension extension = getProject().getExtensions().getByType(GwtExtension.class);
		CompilerOption compilerOptions = extension.getCompile();
		return compilerOptions;
	}
}
