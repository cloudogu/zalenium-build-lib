/**
 * Starts a Selenium grid and executes the given body. When the body finishes, the Selenium containers will
 * gracefully shutdown.
 *
 * @param config contains a map of settings that change the Selenium behavior. Can be a partial map or even left out.
 *      The defaults are:
 *      [seleniumHubImage : 'selenium/hub',
 *      seleniumVersion   : '3.141.59-zinc',
 *      workerImageFF     : "selenium/node-firefox",
 *      workerImageChrome : "selenium/node-chrome",
 *      firefoxWorkerCount: 0,
 *      chromeWorkerCount : 0,
 *      hubPortMapping    : 4444
 *      debugSelenium     : false]
 * @param seleniumNetwork The Selenium grid container and its nodes will be added to this docker network. This is useful if other containers
 *      must communicate with Selenium while being in a docker network. If empty or left out, Selenium grid and nodes will stay in the
 *      default network.
 * @param closure the body
 */
void call(Map config = [:], String seleniumNetwork, Closure closure) {

    def defaultConfig = [
            seleniumHubImage  : 'selenium/hub',
            seleniumVersion   : "3.141.59-zinc",
            workerImageFF     : "selenium/node-firefox",
            workerImageChrome : "selenium/node-chrome",
            firefoxWorkerCount: 0,
            chromeWorkerCount : 0,
            hubPortMapping    : 4444,
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

    def uid = findUid()
    def gid = findGid()

    def networkParameter = "--network ${seleniumNetwork}"

    gridDebugParameter = ""
    if (config.debugSelenium) {
        gridDebugParameter = "-e GRID_DEBUG=true"
    }

    String hubName = generateJobName() + "-seleniumhub"

    // explicitly pull the image into the registry. The documentation is not fully clear but it seems that pull()
    // will persist the image in the registry better than an docker.image(...).runWith()
    def seleniumHubImage = docker.image("${config.seleniumHubImage}:${config.seleniumVersion}")
    seleniumHubImage.pull()
    // Run with Jenkins user, so the files created in the workspace by selenium can be deleted later
    // Otherwise that would be root, and you know how hard it is to get rid of root-owned files.
    def dockerArgs = "-u ${uid}:${gid} ${networkParameter} ${gridDebugParameter} -p ${config.hubPortMapping}:4444 --name ${hubName}"
    seleniumHubImage.withRun(dockerArgs) { hubContainer ->
        String seleniumIp = findContainerIp(hubContainer)

        def firefoxContainers = runWorkerNodes("${config.workerImageFF}:${config.seleniumVersion}", networkParameter, gridDebugParameter, seleniumIp, config.firefoxWorkerCount)
        def chromeContainers = runWorkerNodes("${config.workerImageChrome}:${config.seleniumVersion}", networkParameter, gridDebugParameter, seleniumIp, config.chromeWorkerCount)

        try {
            def seleniumHubHost = "${seleniumIp}:${config.hubPortMapping}"
            waitForSeleniumToGetReady(seleniumHubHost)

            closure.call(hubContainer, seleniumIp, uid, gid)
        } finally {
            stopSeleniumSession(firefoxContainers, chromeContainers)
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
    def result = sh(returnStdout: true,
            script: "curl -sSL http://${host}/wd/hub/status || true") // Don't fail
    result.contains('ready\": true')
}

class ConfigurationException extends RuntimeException {
    ConfigurationException(String message) {
        super(message)
    }
}

ArrayList<String> runWorkerNodes(GString workerNodeImage, String networkParameter, String debugParameter, String hubHost, int count) {
    if (count < 1) {
        return []
    }
    echo "Starting worker nodes with docker image ${workerNodeImage}"

    def workerImage = docker.image(workerNodeImage)
    workerImage.pull()
    def dockerDefaultArgs = "${networkParameter} ${debugParameter} -e HUB_HOST=${hubHost} -v /dev/shm:/dev/shm"
    ArrayList<String> workerIDList = []
    for (int i = 0; i < count; i++) {
        echo "starting new worker node #${i} with args ${dockerDefaultArgs}"
        container = workerImage.run(dockerDefaultArgs)
        echo "started new worker node #${i} with ID ${container.id}"
        workerIDList << container.id
    }
    return workerIDList
}

String generateJobName() {
    return "${JOB_BASE_NAME}-${BUILD_NUMBER}"
}

private void stopSeleniumSession(ArrayList<String> firefoxContainerIDs, ArrayList<String> chromeContainerIDs) {
    echo "Stopping Firefox containers..."
    stopAndLogContainers(firefoxContainerIDs)

    echo "Stopping Chrome containers..."
    stopAndLogContainers(chromeContainerIDs)

    echo "Remove containers..."
    removeContainers(firefoxContainerIDs)
    removeContainers(chromeContainerIDs)
}

void stopAndLogContainers(ArrayList<String> containerIDs) {
    for (containerId in containerIDs) {
        echo "Stopping container with ID ${containerId}"
        sh "docker stop ${containerId}"

        echo "Container with ID ${containerId} produced these logs:"
        sh "docker logs ${containerId} > selenium-docker.log 2>&1"
    }
}

void removeContainers(ArrayList<String> containerIDs) {
    for (containerId in containerIDs) {
        echo "Removing container with ID ${containerId}"
        sh "docker rm -f ${containerId}"
    }
}

Boolean checkNetwork(String networkName) {
    return sh(returnStatus: true, script: "docker network ls | grep ${networkName}") == 0
}
