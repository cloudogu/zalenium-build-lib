/**
 * Provides a simple custom maven settings.xml file to current working directory with Maven Central mirror in the
 * current CES instance.
 *
 * Example call:
 *
 * <pre>
 * nexusCreds = usernamePassword(credentialsId: 'jenkinsNexusServiceUser', passwordVariable: 'PASSWORD', usernameVariable: 'USERNAME')
 * withMavenSettings(nexusCreds, 'cesinstance.stage.host.tld', '/usr/share/maven') { settingsXml ->
 *     // do your maven call here
 *     mvn "-s ${settingsXml} test"
 * } // settings.xml will be removed automatically
 * </pre>
 *
 * @param nexusCredentials Jenkins credentials which provide USERNAME and PASSWORD to an account which enables Nexus interaction
 * @param cesFQDN the full qualified domain name of the current CES instance, f. i. <code>cesinstance.stage.host.tld</code>
 * @param pathToLocalMavenRepository without the .m2 directory part, f. i. <code>/usr/share/maven</code>. The suffix <code>/.m2/repository</code> will be added automatically.
 * @param mirrorNexusPath relativ path to a maven central mirror hosted inside a nexus instance. The suffix <code>/nexus/repository</code> will be added automatically.
 */
def settings(def nexusCredentials, String cesFQDN, String pathToLocalMavenRepository, String mirrorNexusPath, Closure closure) {
    echo "write settings.xml to ${pathToLocalMavenRepository}"
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

def mvnWithSettings(def nexusCredentials, String cesFQDN, String mvnCallArgs) {
    def currentHome = env.HOME
    settings(nexusCredentials, cesFQDN, currentHome, "maven-central/") { settingsXml ->
        mvn settingsXml, mvnCallArgs
    }
}

/**
 * This method extracts the Maven 3 installation from Jenkins and calls Maven with the given settings.xml and Maven arguments
 */
def mvn(String settingsXml, String mvnCallArgs) {
    def mvnHome = tool 'M3'
    
    mvnWithHome(mvnHome, settingsXml, mvnCallArgs)
}

def customMvnWithSettings(def nexusCredentials, String cesFQDN, String mvnHome, String pathToLocalMavenRepository, String mvnCallArgs) {
    settings(nexusCredentials, cesFQDN, pathToLocalMavenRepository, "maven-central/") { settingsXml ->
        mvnWithHome(mvnHome, settingsXml, mvnCallArgs)
    }
}
def customMvnWithSettings(def nexusCredentials, String cesFQDN, String mvnHome, String pathToLocalMavenRepository, String customMirror, String mvnCallArgs){
    settings(nexusCredentials, cesFQDN, pathToLocalMavenRepository, customMirror) { settingsXml ->
        mvnWithHome(mvnHome, settingsXml, mvnCallArgs)
    }
}

def mvnWithHome(String mvnHome, String settingsXml, String mvnCallArgs) {
    sh "${mvnHome}/bin/mvn -s ${settingsXml} --batch-mode -V -U -e -Dsurefire.useFile=false ${mvnCallArgs}"
}
