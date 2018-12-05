void call(config = [seleniumVersion : '3.141.59-p2',
                    zaleniumVersion : '3.141.59d',
                    zaleniumVideoDir: "zalenium",
                    debugZalenium   : false], Closure closure) {
    sh "mkdir -p ${config.zaleniumVideoDir}"

    docker.image("elgalu/selenium:${config.seleniumVersion}").pull()
    docker.image("dosel/zalenium:${config.zaleniumVersion}")
            .withRun(
            // Zalenium starts headless browsers in docker containers, so it needs the socket
            '-v /var/run/docker.sock:/var/run/docker.sock ' +
                    "-v ${WORKSPACE}/${config.zaleniumVideoDir}:/home/seluser/videos",
            'start ' +
                    "${config.debugZalenium ? '--debugEnabled true' : ''}"
    ) { zaleniumContainer ->

        def zaleniumIp = findContainerIp(zaleniumContainer)

        waitForSeleniumToGetReady(zaleniumIp)
        // Delete videos from previous builds, if any
        // This also works around the bug that zalenium stores files as root
        // https://github.com/zalando/zalenium/issues/760
        // This workaround still leaves a couple of files owned by root in the zaleniumVideoDir
        resetZalenium(zaleniumIp)

        try {
            closure(zaleniumIp)
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

String findContainerIp(container) {
    sh (returnStdout: true,
            script: "docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' ${container.id}")
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
            .contains('status\": 0')
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