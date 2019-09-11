/**
 * Provides a simple way to use a Java trust store over different Jenkins nodes.
 *
 * @param pathToTruststore provides the path to a Java truststore. It defaults to the truststore on the Jenkins master.
 */
def copy(String pathToTruststore = '/var/lib/jenkins/truststore.jks') {
    String truststoreFile = 'truststore.jks'
    String stashName = 'truststore'

    sh "cp ${pathToTruststore} ./${truststoreFile}"
    stash includes: truststoreFile, name: stashName
    sh "rm -f ./${truststoreFile}"
}

/**
 * Unstashes the previously stashed truststore to the current workspace. The truststore will be deleted after the closure
 * body finished.
 *
 * <code>truststore.copy()</code> MUST be called beforehand, otherwise there would be no truststore to be unstashed.
 *
 * <pre>
 *     node 'master' {
 *      truststore.copy()
 *     }
 *     node 'docker' {
 *         truststore.use { 
 *             javaOrMvn "-Djavax.net.ssl.trustStore=truststore.jks -Djavax.net.ssl.trustStorePassword=changeit"
 *         }
 *     }
 * </pre>
 * @param closure this closure is executed after the truststore was successfully unstashed.
 */
def use(Closure inner) {
    String truststoreFile = 'truststore.jks'
    String stashName = 'truststore'

    try {
        unstash name: stashName

        inner.call(truststoreFile)
        
    } catch (Exception ex) {
        echo "withTruststore failed because an exception occurred: ${ex}"
        throw ex
    } finally {
         sh "rm -f ./${truststoreFile}"
    }
}