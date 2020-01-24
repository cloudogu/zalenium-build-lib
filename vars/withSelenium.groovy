/**
 * Starts a Selenium grid and executes the given body. When the body finishes, the Selenium containers will
 * gracefully shutdown.
 *
 * @param config contains a map of settings that change the Selenium behavior. Can be a partial map or even left out.
 *      The defaults are:
 *      [seleniumImage    : 'selenium/hub',
 *      seleniumHubVersion: '3.141.59-zinc',
 *      workerImageTag    : '3.141.59-zinc',
 *      firefoxWorkerCount: 0,
 *      chromeWorkerCount : 0,
 *      debugSelenium     : false]
 * @param seleniumNetwork The Selenium grid container and its nodes will be added to this docker network. This is useful if other containers
 *      must communicate with Selenium while being in a docker network. If empty or left out, Selenium grid and nodes will stay in the
 *      default network.
 * @param closure the body
 */
void call(Map config = [:], String seleniumNetwork, Closure closure) {

    def defaultConfig = [
            seleniumImage     : 'selenium/hub',
            seleniumHubVersion: "3.141.59-zinc",
            workerImageTag    : "3.141.59-zinc",
            firefoxWorkerCount: 0,
            chromeWorkerCount : 0,
            debugSelenium     : false
    ]

    def networkExists = checkNetwork(seleniumNetwork)
    if(!networkExists) {
        throw new ConfigurationException("the given network '${seleniumNetwork}' does not exist but is mandatory when using selenium grid")
    }

    // Merge default config with the one passed as parameter
    config = defaultConfig << config
    if (config.firefoxWorkerCount == 0 && config.chromeWorkerCount == 0) {
        throw new ConfigurationException("Cannot start selenium test. Please configure at least one workerCount for the desired browser.")
    }

    checkSeleniumVersionCompatibility(config.seleniumHubVersion, config.workerImageTag)
    def uid = findUid()
    def gid = findGid()

    def networkParameter = ""
    if (seleniumNetwork != null && !seleniumNetwork.isEmpty()) {
        networkParameter = "--network ${seleniumNetwork}"
    }

    gridDebugParameter = ""
    if (gridDebugParameter != null && !gridDebugParameter.isEmpty()) {
        gridDebugParameter = "-e GRID_DEBUG=true"
    }

    String hubName = generateJobName() + "-seleniumhub"

    // explicitly pull the image into the registry. The documentation is not fully clear but it seems that pull()
    // will persist the image in the registry better than an docker.image(...).runWith()
    def seleniumHubImage = docker.image("${config.seleniumImage}:${config.seleniumHubVersion}")
    seleniumHubImage.pull()
    seleniumHubImage.withRun(
            // Run with Jenkins user, so the files created in the workspace by selenium can be deleted later
            // Otherwise that would be root, and you know how hard it is to get rid of root-owned files.
            "-u ${uid}:${gid} " +
                    "${networkParameter} " +
                    "${gridDebugParameter} " +
                    "--name ${hubName}"
    ) { hubContainer ->
        String seleniumIp = findContainerIp(hubContainer)

        def firefoxContainers = startFirefoxWorker(hubName, networkParameter, gridDebugParameter, config.workerImageTag, config.firefoxWorkerCount)
        def chromeContainers = startChromeWorker(hubName, networkParameter, gridDebugParameter, config.workerImageTag, config.chromeWorkerCount)

        try {
            waitForSeleniumToGetReady(seleniumIp)

            closure.call(hubContainer, seleniumIp, uid, gid)
        } finally {
            stopSeleniumSession(firefoxContainers, chromeContainers)
        }
    }
}

String generateJobName() {
    return "${JOB_BASE_NAME}-${BUILD_NUMBER}"
}

void checkSeleniumVersionCompatibility(String seleniumVersion, String workerImageTag) {
    def workerSeleniumVersion = seleniumVersion.split("-")[0]
    def hubSeleniumVersion = workerImageTag.split("-")[0]

    if (workerSeleniumVersion != hubSeleniumVersion) {
        def warning = sprintf("The selected Selenium hub version '%s' differs from the worker version '%s'. " +
                "You should consider the same version for both to avoid compatibility issues during the test.",
                hubSeleniumVersion, seleniumVersion)
        unstable(warning)
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

class ConfigurationException extends RuntimeException {
    ConfigurationException(String message) {
        super(message)
    }
}

ArrayList<String> startFirefoxWorker(String hubHost, String networkParameter, String debugParameter, String workerImageTag, int count) {
    GString workerNodeImage = "selenium/node-firefox:${workerImageTag}"

    return runWorkerNodes(workerNodeImage, networkParameter, debugParameter, hubHost, count)
}

ArrayList<String> startChromeWorker(String hubHost, String networkParameter, String debugParameter, String workerImageTag, int count) {
    GString workerNodeImage = "selenium/node-chrome:${workerImageTag}"

    return runWorkerNodes(workerNodeImage, networkParameter, debugParameter, hubHost, count)
}

ArrayList<String> runWorkerNodes(GString workerNodeImage, String networkParameter, String debugParameter, String hubHost, int count) {
    echo "Starting worker node with docker image ${workerNodeImage}"

    def workerImage = docker.image(workerNodeImage)
    workerImage.pull()
    GString dockerDefaultArgs = "${networkParameter} ${debugParameter} -e HUB_HOST=${hubHost} -v /dev/shm:/dev/shm"
    ArrayList<String> workerIDList = []
    for (int i = 0; i < count; i++) {
        container = workerImage.run(dockerDefaultArgs)
        workerIDList << container.id
    }
    return workerIDList
}

private void stopSeleniumSession(ArrayList<String> firefoxIDs, Collection<String> chromeIDs) {
    String[] firefoxContainerIDs = firefoxIDs.toArray()
    String[] chromeContainerIDs = chromeIDs.toArray()

    echo "Stopping Firefox containers..."
    stopAndLogContainers(firefoxContainerIDs)

    echo "Stopping Chrome containers..."
    stopAndLogContainers(chromeContainerIDs)

    echo "Remove containers..."
    removeContainers(firefoxContainerIDs)
    removeContainers(chromeContainerIDs)
}

void stopAndLogContainers(String... containerIDs) {
    for (String containerId : containerIDs) {
        echo "Stopping container with ID ${containerId}"
        sh "docker stop ${containerId}"

        echo "Container with ID ${containerId} produced these logs:"
        sh "docker logs ${containerId} > selenium-docker.log 2>&1"
    }
}

void removeContainers(String... containerIDs) {
    for (String containerId : containerIDs) {
        echo "Removing container with ID ${containerId}"
        sh "docker rm -f ${containerId}"
    }
}

Boolean checkNetwork(String networkName) {
    return sh(returnStatus: true, script: "docker network ls | grep ${networkName}") == 0
}