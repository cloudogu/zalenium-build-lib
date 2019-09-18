String generateZaleniumJobName() {
    return "${JOB_BASE_NAME}_${BUILD_NUMBER}"
}

String findHostName() {
    String regexMatchesHostName = 'https?://([^:/]*)'

    // Storing matcher in a variable might lead to java.io.NotSerializableException: java.util.regex.Matcher
    if (!(env.JENKINS_URL =~ regexMatchesHostName)) {
        script.error 'Unable to determine hostname from env.JENKINS_URL. Expecting http(s)://server:port/jenkins'
    }
    return (env.JENKINS_URL =~ regexMatchesHostName)[0][1]
}
