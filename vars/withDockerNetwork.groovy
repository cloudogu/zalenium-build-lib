evaluate(new File("./helper.groovy"))

/**
 * Start a temporary docker network so docker containers can interact even without IP address. The created network will
 * be removed automatically once the body finishes.
 * @param printDebugOutput logs creation and removal of the network if set to true. Can be left out.
 * @param inner the body to be executed
  */
void call(printDebugOutput = false, Closure inner) {
    def networkName = "net_" + generateJobName()

    try {
        debugOut(printDebugOutput, "create docker bridge network")
        sh "docker network create ${networkName}"
        // provide network name to closure
        inner.call(networkName)
    } finally {
        debugOut(printDebugOutput, "remove docker network")
        sh "docker network rm ${networkName}"
    }
}

void debugOut(boolean printDebugOutput, String logMessage) {
    if (printDebugOutput) {
        echo "DEBUG: " + logMessage
    }
}