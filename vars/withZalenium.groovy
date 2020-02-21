/**
 * Starts Zalenium in a Selenium grid and executes the given body. When the body finishes, the Zalenium container will
 * gracefully shutdown and archive all videos generated by the tests.
 *
 * @param config contains a map of settings that change the Zalenium behavior. Can be a partial map or even left out.
 *      The defaults are:
 *      [seleniumVersion   : '3.141.59-p8',
 *      seleniumImage      : 'elgalu/selenium',
 *      zaleniumVersion    : '3.141.59g',
 *      zaleniumImage      : 'dosel/zalenium',
 *      zaleniumVideoDir   : 'zalenium',
 *      sendGoogleAnalytics: false,
 *      debugZalenium      : false]
 * @param zaleniumNetwork The Zalenium container will be added to this docker network. This is useful if other containers
 *      must communicate with Zalenium while being in a docker network. If empty or left out, Zalenium will stay in the
 *      default network.
 * @param closure the body
 */
void call(Map config = [:], String zaleniumNetwork, Closure closure) {

    def defaultConfig = [seleniumVersion    : '3.141.59-p8',
                         seleniumImage      : 'elgalu/selenium',
                         zaleniumVersion    : '3.141.59g',
                         zaleniumImage      : 'dosel/zalenium',
                         zaleniumVideoDir   : 'zalenium',
                         debugZalenium      : false,
                         sendGoogleAnalytics: false]

    // Merge default config with the one passed as parameter
    config = defaultConfig << config

    sh "mkdir -p ${config.zaleniumVideoDir}"

    // explicitly pull the image into the registry. The documentation is not fully clear but it seems that pull()
    // will persist the image in the registry better than an docker.image(...).runWith()
    docker.image("${config.seleniumImage}:${config.seleniumVersion}").pull()
    def zaleniumImage = docker.image("${config.zaleniumImage}:${config.zaleniumVersion}")
    zaleniumImage.pull()

    def uid = findUid()
    def gid = findGid()

    networkParameter = ""

    if (zaleniumNetwork != null && !zaleniumNetwork.isEmpty()) {
        networkParameter = "--network ${zaleniumNetwork}"
    }

    lock("zalenium") {
        zaleniumImage.withRun(
                // Run with Jenkins user, so the files created in the workspace by zalenium can be deleted later
                // Otherwise that would be root, and you know how hard it is to get rid of root-owned files.
                "-u ${uid}:${gid} -e HOST_UID=${uid} -e HOST_GID=${gid} " +
                        // Zalenium starts headless browsers in each in a docker container, so it needs the Socket
                        '-v /var/run/docker.sock:/var/run/docker.sock ' +
                        '--privileged ' +
                        "${networkParameter} " +
                        "-v ${WORKSPACE}/${config.zaleniumVideoDir}:/home/seluser/videos",
                'start ' +
                        "--seleniumImageName ${config.seleniumImage} " +
                        "${config.debugZalenium ? '--debugEnabled true' : ''} " +
                        // switch off analytic gathering
                        "${config.sendGoogleAnalytics ? '--sendAnonymousUsageInfo false' : ''} "
        ) { zaleniumContainer ->
            String zaleniumIp = findContainerIp(zaleniumContainer)

            try {
                waitForSeleniumToGetReady(zaleniumIp)
                // Delete videos from previous builds, if any
                // This also works around the bug that zalenium stores files as root (before version 3.141.59f)
                // https://github.com/zalando/zalenium/issues/760
                // This workaround still leaves a couple of files owned by root in the zaleniumVideoDir
                resetZalenium(zaleniumIp)

                closure.call(zaleniumContainer, zaleniumIp, uid, gid)
            } finally {
                // Wait for Selenium sessions to end (i.e. videos to be copied)
                // Leaving the withRun() closure leads to "docker rm -f" being called, cancelling copying
                waitForSeleniumSessionsToEnd(zaleniumIp)
                archiveArtifacts allowEmptyArchive: true, artifacts: "${config.zaleniumVideoDir}/*.mp4"

                // Stop container gracefully and wait
                sh "docker stop ${zaleniumContainer.id}"
                // Store log for debugging purposes
                sh "docker logs ${zaleniumContainer.id} > zalenium-docker.log 2>&1"
            }
        }
    }
}

String findContainerIp(container) {
    sh(returnStdout: true,
            script: "docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' ${container.id}")
            .trim()
}

String findUid() {
    sh(returnStdout: true,
            script: 'id -u')
            .trim()
}

String findGid() {
    sh(returnStdout: true,
            script: 'id -g')
            .trim()
}

void waitForSeleniumToGetReady(String host) {
    timeout(time: 1, unit: 'MINUTES') {
        echo "Waiting for selenium to become ready at http://${host}"
        while (!isSeleniumReady(host)) {
            sleep(time: 1, unit: 'SECONDS')
        }
        echo "Selenium ready at http://${host}"
    }
}

boolean isSeleniumReady(String host) {
    sh(returnStdout: true,
            script: "curl -sSL http://${host}:4444/wd/hub/status || true") // Don't fail
            .contains('ready\": true')
}

void waitForSeleniumSessionsToEnd(String host) {
    timeout(time: 5, unit: 'MINUTES') {
        echo "Waiting for selenium sessions to end at http://${host}"
        while (isSeleniumSessionsActive(host)) {
            sleep(time: 10, unit: 'SECONDS')
        }
        echo "No more selenium sessions active at http://${host}"
    }
}

boolean isSeleniumSessionsActive(String host) {
    sh(returnStatus: true,
            script: "(curl -sSL http://${host}:4444/grid/api/sessions || true) | grep sessions") == 0
}

void resetZalenium(String host) {
    sh(returnStatus: true,
            script: "curl -sSL http://${host}:4444/dashboard/cleanup?action=doReset") == 0
}
