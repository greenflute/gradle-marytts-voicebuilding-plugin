package de.dfki.mary.voicebuilding

import de.dfki.mary.voicebuilding.tasks.*

import org.gradle.api.Plugin
import org.gradle.api.Project

class VoicebuildingDataPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        project.plugins.apply VoicebuildingBasePlugin

        project.configurations {
            create 'data'
            create 'marytts'
        }

        project.sourceSets {
            create 'data'
        }

        project.dependencies {
            project.afterEvaluate {
                marytts group: 'de.dfki.mary', name: "marytts-lang-$project.voice.locale.language", version: project.maryttsVersion, {
                    exclude module: 'groovy-all'
                }
            }
            marytts group: 'de.dfki.mary', name: "marytts-voicebuilding", version: '0.1'
        }

        project.extensions.create 'voicebuilding', VoicebuildingExtension, project

        project.task('bootstrap')

        project.task('templates', type: CopyClasspathResources) {
            destDir = project.file("$project.buildDir/templates")
            resources += ['/de/dfki/mary/voicebuilding/templates/extractPitch.praat',
                          '/de/dfki/mary/voicebuilding/templates/pitchmarks.praat']
            project.bootstrap.dependsOn it
        }

        project.task('wav', type: ProcessWav) {
            dependsOn project.processDataResources
            srcFiles = project.fileTree(project.sourceSets.data.output.resourcesDir).include('*.wav')
            destDir = project.file("$project.buildDir/wav")
        }

        project.task('praatPitchExtractor')

        project.task('praatPitchmarker')

        project.task('pitchmarkConverter')

        project.task('mcepExtractor')

        project.voicebuilding.basenames.each { basename ->
            project.task("${basename}_extractPitch", type: PraatExec) {
                def wavFile = project.file("$project.wav.destDir/${basename}.wav")
                dependsOn project.templates, project.wav
                srcFiles << wavFile
                destFile = project.file("$project.buildDir/pm/${basename}.Pitch")
                scriptFile = project.file("$project.templates.destDir/extractPitch.praat")
                project.praatPitchExtractor.dependsOn it
                project.afterEvaluate {
                    props = [wavFile  : wavFile,
                             pitchFile: destFile,
                             minPitch : (project.voice.gender == 'female') ? 100 : 75,
                             maxPitch : (project.voice.gender == 'female') ? 500 : 300]
                }
            }

            project.task("${basename}_extractPitchmarks", type: PraatExec) {
                def wavFile = project.file("$project.wav.destDir/${basename}.wav")
                def pitchTask = project.tasks.getByName("${basename}_extractPitch")
                dependsOn project.templates, project.wav, pitchTask
                srcFiles << [wavFile, pitchTask.destFile]
                destFile = project.file("$project.buildDir/pm/${basename}.PointProcess")
                scriptFile = project.file("$project.templates.destDir/pitchmarks.praat")
                props = [wavFile   : wavFile,
                         pitchFile : pitchTask.destFile,
                         pointpFile: destFile]
                project.praatPitchmarker.dependsOn it
            }

            project.task("${basename}_convertPitchmarks", type: PitchmarkConverter) {
                def pitchmarkTask = project.tasks.getByName("${basename}_extractPitchmarks")
                dependsOn pitchmarkTask
                srcFile = pitchmarkTask.destFile
                destFile = project.file("$project.buildDir/pm/${basename}.pm")
                project.pitchmarkConverter.dependsOn it
            }

            project.task("${basename}_mcep", type: ParallelizableExec) {
                def wavFile = project.file("$project.wav.destDir/${basename}.wav")
                def pitchmarkTask = project.tasks.getByName("${basename}_convertPitchmarks")
                dependsOn project.wav, pitchmarkTask
                srcFiles << [wavFile, pitchmarkTask.destFile]
                destFile = project.file("$project.buildDir/mcep/${basename}.mcep")
                cmd = ['sig2fv',
                       '-window_type', 'hamming',
                       '-factor', 2.5,
                       '-otype', 'est_binary',
                       '-coefs', 'melcep',
                       '-melcep_order', 12,
                       '-fbank_order', 24,
                       '-shift', 0.01,
                       '-preemph', 0.97,
                       '-pm', pitchmarkTask.destFile,
                       '-o', destFile,
                       wavFile]
                project.mcepExtractor.dependsOn it
            }
        }

        project.task('generateAllophones', type: MaryInterfaceBatchTask) {
            srcDir = project.file("$project.buildDir/text")
            destDir = project.file("$project.buildDir/prompt_allophones")
            inputType = 'TEXT'
            inputExt = 'txt'
            outputType = 'ALLOPHONES'
            outputExt = 'xml'
        }

        project.task('generatePhoneFeatures', type: MaryInterfaceBatchTask) {
            dependsOn project.generateAllophones
            srcDir = project.file("$project.buildDir/prompt_allophones")
            destDir = project.file("$project.buildDir/phonefeatures")
            inputType = 'ALLOPHONES'
            inputExt = 'xml'
            outputType = 'TARGETFEATURES'
            outputExt = 'pfeats'
        }

        project.task('generateHalfPhoneFeatures', type: MaryInterfaceBatchTask) {
            dependsOn project.generateAllophones
            srcDir = project.file("$project.buildDir/prompt_allophones")
            destDir = project.file("$project.buildDir/halfphonefeatures")
            inputType = 'ALLOPHONES'
            inputExt = 'xml'
            outputType = 'HALFPHONE_TARGETFEATURES'
            outputExt = 'hpfeats'
        }
    }
}
