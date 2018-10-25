package de.dfki.mary.voicebuilding.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

class GenerateBasenamesList extends DefaultTask {

    @InputDirectory
    final DirectoryProperty wavDir = newInputDirectory()

    @InputDirectory
    final DirectoryProperty textDir = newInputDirectory()

    @InputDirectory
    final DirectoryProperty labDir = newInputDirectory()

    @Optional
    @Input
    ListProperty<String> includes = project.objects.listProperty(String)

    @Optional
    @Input
    ListProperty<String> excludes = project.objects.listProperty(String)

    @OutputFile
    final RegularFileProperty destFile = newOutputFile()

    void include(String... includes) {
        this.includes.addAll(includes)
    }

    void exclude(String... excludes) {
        this.excludes.addAll(excludes)
    }

    @TaskAction
    void generate() {
        destFile.get().asFile.withWriter('UTF-8') { writer ->
            project.fileTree(wavDir).matching {
                include this.includes.getOrElse('*').collect { it + '.wav' }
                exclude this.excludes.getOrElse([]).collect { it + '.wav' }
            }.each { wavFile ->
                def basename = wavFile.name - '.wav'
                writer.println basename
            }
        }
    }
}
