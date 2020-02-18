final String defaultMavenCentralRepoName = "maven-central"

/**
 * Provides a simple custom maven settings.xml file to current working directory with Maven Central mirror in the
 * current CES instance.
 *
 * Example call:
 *
 * <pre>
 * nexusCreds = usernamePassword(credentialsId: 'jenkinsNexusServiceUser', passwordVariable: 'PASSWORD', usernameVariable: 'USERNAME')
 * withMavenSettings.settings(nexusCreds, 'cesinstance.stage.host.tld', '/usr/share/maven', 'maven-central/') { settingsXml ->
 *     // do your maven call here
 *     mvn "-s ${settingsXml} clean build"
 *     // or from this library
 *     withMavenSettings.mvn ${settingsXml}, "clean build"
 *} // settings.xml will be removed automatically
 * </pre>
 *
 * @param nexusCredentials Jenkins credentials which provide USERNAME and PASSWORD to an account which enables Nexus interaction
 * @param cesFQDN the full qualified domain name of the current CES instance, f. i. <code>cesinstance.stage.host.tld</code>. The prefix <code>https://</code> will be added automatically.
 * @param pathToLocalMavenRepository without the .m2 directory part, f. i. <code>/usr/share/maven</code>. The suffix <code>/.m2/repository</code> will be added automatically.
 * @param mirrorNexusPath relative path to a maven central mirror hosted inside a nexus instance. The prefix <code>/nexus/repository</code> will be added automatically. Defaults to "maven-central"
 * @param closure This closure will be executed when the Maven <code>settings.xml</code> file was successfully created.
 */
def settings(def nexusCredentials, String cesFQDN, String pathToLocalMavenRepository, String mirrorNexusPath=defaultMavenCentralRepoName, Closure closure) {
    echo "write settings.xml to ${pathToLocalMavenRepository}"
    echo "local Maven repository should be located at ${pathToLocalMavenRepository}/.m2/repository"

    String settingsXml = "settings.xml"
    withCredentials([nexusCredentials]) {
        writeFile file: settingsXml, text: """
            <settings>
                <localRepository>${pathToLocalMavenRepository}/.m2/repository</localRepository>
                <servers>
                    <server>
                      <id>${cesFQDN}</id>
                      <username>${USERNAME}</username>
                      <password>${PASSWORD}</password>
                    </server>
                </servers>
                <mirrors>
                    <mirror>
                      <id>${cesFQDN}</id>
                      <name>${cesFQDN} Central Mirror</name>
                      <url>https://${cesFQDN}/nexus/repository/${mirrorNexusPath}</url>
                      <mirrorOf>central</mirrorOf>
                    </mirror>
                </mirrors>
            </settings>"""
    }

    try {
        closure.call(settingsXml)
    } finally {
        sh "rm -f ${settingsXml}"
    }
}

/**
 *  Provides a simple custom maven settings.xml file to current working directory with Maven Central mirror in the
 *  current CES instance.
 * @param nexusCredentials Jenkins credentials which provide USERNAME and PASSWORD to an account which enables Nexus interaction
 * @param cesFQDN the full qualified domain name of the current CES instance, f. i. <code>cesinstance.stage.host.tld</code>. The prefix <code>https://</code> will be added automatically.
 * @param mirrorNexusPath relative path to a maven central mirror hosted inside a nexus instance. The prefix <code>/nexus/repository</code> will be added automatically. Defaults to "maven-central"
 * @param closure This closure will be executed when the Maven <code>settings.xml</code> file was successfully created.
 */
def settingsWithEnvHome(def nexusCredentials, String cesFQDN, String mirrorNexusPath=defaultMavenCentralRepoName, Closure closure) {
    def currentHome = env.HOME
    settings(nexusCredentials, cesFQDN, currentHome, mirrorNexusPath, closure)
}

/**
 * Creates a Maven <code>settings.xml</code> and executes Maven with the given arguments.
 * @param nexusCredentials Jenkins credentials which provide USERNAME and PASSWORD to an account which enables Nexus interaction
 * @param cesFQDN the full qualified domain name of the current CES instance, f. i. <code>cesinstance.stage.host.tld</code>. The prefix <code>https://</code> will be added automatically.
 * @param mirrorNexusPath relative path to a maven central mirror hosted inside a nexus instance. The prefix <code>/nexus/repository</code> will be added automatically. Defaults to "maven-central"
 * @param mvnCallArgs these arguments contain the Maven arguments
 */
def mvnWithSettings(def nexusCredentials, String cesFQDN, String mirrorNexusPath, String mvnCallArgs) {
    def currentHome = env.HOME
    settings(nexusCredentials, cesFQDN, currentHome, mirrorNexusPath) { settingsXml ->
        mvn settingsXml, mvnCallArgs
    }
}

/**
 * Execute maven. This method extracts the Maven 3 installation from Jenkins and calls Maven with the given settings.xml and Maven arguments.
 *
 * Example call:
 *
 * <pre>
 *  withMavenSettings.settings(nexusCreds, 'cesinstance.stage.host.tld', '/usr/share/maven', 'maven-central/') { settingsXml ->
 *      // parameter parenthesis is optional
 *      withMavenSettings.mvn settingsXml, "clean build"
 *      // can be written also like this
 *      withMavenSettings.mvn(settingsXml, "clean build")
 *  }
 * </pre>
 *
 * @param settingsXml the path to a Maven <code>settings.xml</code> file. (mandatory)
 * @param mvnCallArgs these arguments contain the Maven arguments. Whether these arguments are optional or not depends on your <code>pom.xml</code>.
 */
def mvn(String settingsXml, String mvnCallArgs) {
    def mvnHome = tool 'M3'

    mvnWithHome(mvnHome, settingsXml, mvnCallArgs)
}

/**
 * Creates a Maven <code>settings.xml</code> and executes Maven with the given arguments.
 * @param nexusCredentials Jenkins credentials which provide USERNAME and PASSWORD to an account which enables Nexus interaction
 * @param cesFQDN the full qualified domain name of the current CES instance, f. i. <code>cesinstance.stage.host.tld</code>. The prefix <code>https://</code> will be added automatically.
 * @param mvnHome the Maven home path
 * @param pathToLocalMavenRepository the path to the local Maven repository
 * @param mvnCallArgs these arguments contain the Maven arguments. Whether these arguments are optional or not depends on your <code>pom.xml</code>.
 */
def customMvnWithSettings(def nexusCredentials, String cesFQDN, String mvnHome, String pathToLocalMavenRepository, String mvnCallArgs) {
    settings(nexusCredentials, cesFQDN, pathToLocalMavenRepository) { settingsXml ->
        mvnWithHome(mvnHome, settingsXml, mvnCallArgs)
    }
}

/**
 * Creates a Maven <code>settings.xml</code> and executes Maven with the given arguments.
 * @param nexusCredentials Jenkins credentials which provide USERNAME and PASSWORD to an account which enables Nexus interaction
 * @param cesFQDN the full qualified domain name of the current CES instance, f. i. <code>cesinstance.stage.host.tld</code>. The prefix <code>https://</code> will be added automatically.
 * @param mirrorNexusPath relative path to a maven central mirror hosted inside a nexus instance. The prefix <code>/nexus/repository</code> will be added automatically. Defaults to "maven-central"
 * @param mvnCallArgs these arguments contain the Maven arguments
 */
def customMvnWithSettings(def nexusCredentials, String cesFQDN, String mvnHome, String pathToLocalMavenRepository, String customMirror, String mvnCallArgs) {
    settings(nexusCredentials, cesFQDN, pathToLocalMavenRepository, customMirror) { settingsXml ->
        mvnWithHome(mvnHome, settingsXml, mvnCallArgs)
    }
}

/**
 * Execute maven from the given Maven home.
 * @param mvnHome the path where Maven is installed. (mandatory)
 * @param settingsXml the path to a Maven <code>settings.xml</code> file. (mandatory)
 * @param mvnCallArgs these arguments contain the Maven arguments. Whether these arguments are optional or not depends on your <code>pom.xml</code>.
 */
def mvnWithHome(String mvnHome, String settingsXml, String mvnCallArgs) {
    sh "${mvnHome}/bin/mvn -s ${settingsXml} --batch-mode -V -U -e -Dsurefire.useFile=false ${mvnCallArgs}"
}
