/**
 * @deprecated Use generateJobName() instead.
 *
 * @return a string containing the generated name.
 */
@Deprecated String generateZaleniumJobName() {
    return generateJobName()
}

/**
 * Generates an unique string based on the job name and the current build number.
 *
 * @return a string containing the generated name.
 */
String generateJobName() {
    return "${JOB_BASE_NAME}-${BUILD_NUMBER}"
}

String findHostName() {
    String regexMatchesHostName = 'https?://([^:/]*)'

    // Storing matcher in a variable might lead to java.io.NotSerializableException: java.util.regex.Matcher
    if (!(env.JENKINS_URL =~ regexMatchesHostName)) {
        script.error 'Unable to determine hostname from env.JENKINS_URL. Expecting http(s)://server:port/jenkins'
    }
    return (env.JENKINS_URL =~ regexMatchesHostName)[0][1]
}
