/*
 * Declarative pipeline mirroring .github/workflows/mobile-tests.yml.
 *
 * Both exist on purpose. GitHub Actions is the public-facing gate; Jenkins is what most
 * enterprises actually run mobile suites on, because the device lab is on-premises and the
 * phones are plugged into a physical node. Keeping the two in step means the same suite,
 * the same properties and the same artefacts either way - the CI system is a detail, not a
 * second definition of what "tested" means.
 *
 * Requires on the agent: JDK 17, Maven, Node 20, the Android SDK, and either a running
 * emulator or a connected device.
 * TODO: set the agent label to your device-lab node, e.g. agent { label 'android-devices' }.
 */
pipeline {

    agent any

    parameters {
        choice(
            name: 'PLATFORM',
            choices: ['android', 'ios'],
            description: 'Target platform. iOS needs a macOS agent with Xcode.')
        choice(
            name: 'SUITE',
            choices: ['smoke', 'regression', 'mobile-native', 'parallel-devices'],
            description: 'TestNG suite from src/test/resources/suites')
        string(
            name: 'DEVICE_NAME',
            defaultValue: 'emulator-5554',
            description: 'Device or emulator name (adb devices -l)')
        string(
            name: 'UDID',
            defaultValue: '',
            description: 'Device udid. Leave blank when only one device is attached.')
        string(
            name: 'PLATFORM_VERSION',
            defaultValue: '',
            description: 'OS version. Blank lets Appium decide.')
        booleanParam(
            name: 'RECORD_VIDEO',
            defaultValue: false,
            description: 'Record video of failed tests. Slower - use when chasing a flake.')
    }

    options {
        // A hung emulator otherwise holds a lab device until someone notices.
        timeout(time: 90, unit: 'MINUTES')
        // Mobile jobs are long; keeping a month of them is enough to spot a trend without
        // filling the controller's disk with emulator videos.
        buildDiscarder(logRotator(numToKeepStr: '30', artifactNumToKeepStr: '10'))
        timestamps()
        disableConcurrentBuilds()
    }

    triggers {
        // Nightly regression, matching the GitHub Actions schedule.
        cron(env.BRANCH_NAME == 'main' ? 'H 2 * * *' : '')
    }

    environment {
        APPIUM_VERSION = '2.11.3'
        UIAUTOMATOR2_VERSION = '3.7.6'
        // Keeps the local repository inside the workspace so parallel jobs on the same
        // agent cannot corrupt each other's downloads.
        MAVEN_OPTS = '-Dmaven.repo.local=.m2/repository -Xmx1g'
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
                sh 'git log -1 --oneline'
            }
        }

        stage('Verify the toolchain') {
            steps {
                // Failing here with a clear message beats failing thirty minutes later
                // inside a test with an unrelated-looking error.
                sh '''
                    set -e
                    java -version
                    mvn -version
                    node --version
                    adb version
                '''
            }
        }

        stage('Install Appium') {
            steps {
                sh """
                    set -e
                    npm install -g appium@${APPIUM_VERSION}
                    appium driver install uiautomator2@${UIAUTOMATOR2_VERSION} || \
                        appium driver update uiautomator2
                    appium --version
                """
            }
        }

        stage('Fetch the app binaries') {
            steps {
                sh 'bash scripts/fetch-apps.sh'
            }
        }

        stage('Wait for a device') {
            when { expression { params.PLATFORM == 'android' } }
            steps {
                // A suite that starts before the device is ready fails in a way that looks
                // like a product bug. This makes that failure honest and immediate.
                sh '''
                    set -e
                    adb wait-for-device
                    adb shell 'while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 2; done'
                    adb devices -l
                '''
            }
        }

        stage('Test') {
            steps {
                script {
                    def udidArg = params.UDID?.trim() ? "-Dudid=${params.UDID}" : ''
                    def versionArg = params.PLATFORM_VERSION?.trim()
                        ? "-Dplatform.version=${params.PLATFORM_VERSION}" : ''

                    sh """
                        mvn -B test \
                            -Dsuite=${params.SUITE} \
                            -Dplatform=${params.PLATFORM} \
                            -Dexecution=local \
                            -Ddevice.name='${params.DEVICE_NAME}' \
                            ${udidArg} \
                            ${versionArg} \
                            -Drecord.video.on.failure=${params.RECORD_VIDEO}
                    """
                }
            }
        }
    }

    post {
        /*
         * always, not success. The report and the evidence are worth most on the run that
         * failed; publishing them only on green would be exactly backwards.
         */
        always {
            // The Allure Jenkins plugin keeps history across builds itself, which is what
            // drives its trend graph - the equivalent of the gh-pages history copy.
            allure([
                includeProperties: false,
                jdk: '',
                results: [[path: 'allure-results']]
            ])

            junit testResults: 'target/surefire-reports/*.xml', allowEmptyResults: true

            archiveArtifacts(
                artifacts: 'target/screenshots/**, target/logs/**, target/videos/**, target/surefire-reports/**',
                allowEmptyArchive: true,
                fingerprint: false)

            // The Appium server is stopped by the framework, but a crashed JVM can leave one
            // behind holding port 4723 and break the next build on this agent.
            sh 'pkill -f "appium" || true'
        }

        failure {
            echo "Suite '${params.SUITE}' failed on ${params.DEVICE_NAME}. " +
                 'Check the Allure report for screenshots, page source and the device log.'
            // TODO: wire up the team notification, e.g.
            //   slackSend(channel: '#qa-alerts', color: 'danger', message: "...")
        }

        unstable {
            echo 'Some tests were retried or are flaky - see the "Retried" label in Allure.'
        }

        cleanup {
            // Emulator videos and reports add up fast on a shared agent.
            cleanWs(deleteDirs: true, notFailBuild: true,
                    patterns: [[pattern: '.m2/**', type: 'EXCLUDE']])
        }
    }
}
