![Cloudogu logo](https://cloudogu.com/images/logo.png)

zalenium-build-lib
=======

Jenkins shared library that provides a step that runs a temporary [zalenium server](https://github.com/zalando/zalenium) 
in docker container, that records videos of selenium tests. The videos are archived at the Jenkins job by this library.

# Usage

In your `Jenkinsfile`

```groovy
@Library('github.com/cloudogu/zalenium-build-lib@d8b74327') _

// ...
stage('UI Test') {
    withZalenium { zaleniumIp ->
        // Run your build, passing ${zaleniumIp} to it
    }
}
```

There are also a number of (optional) Parameters:
                           
- seleniumVersion - version of the "elgalu/selenium" docker image
- zaleniumVersion - version of the "dosel/zalenium" docker image
- zaleniumVideoDir - workspace relative path where the videos are stored
- debugZalenium - makes the zalenium container write a lot more logs

```groovy
withZalenium([ seleniumVersion : '3.14.0-p15' ]) { zaleniumIp ->
        // Run your build, passing ${zaleniumIp} to it
 }
```

When the build is done you can download the videos from the jenkins build and watch them.  
Even more convenient: You could watch the videos in the browser directly. This in possible if either

* your Jenkins does not have a Content Security Policy (CSP), which is not recommended or
* you extend the default CSP by `media-src 'self';` which allows the Jenkins Web UI in your browser to display audio
  and video that is provided by the Jenkins host.
  * This can be setup temporarily via Groovy Console (is not persisted during restart) with this command:  
  `System.setProperty("hudson.model.DirectoryBrowserSupport.CSP", "sandbox; default-src 'none'; img-src 'self'; style-src 'self'; media-src 'self';")`
  * Or you start your Jenkins instance with   
  `-Dhudson.model.DirectoryBrowserSupport.CSP="sandbox; default-src 'none'; img-src 'self'; style-src 'self'; media-src 'self';"`

## Docker Network creation

It is possible (although not necessary) to explicitly work with docker networks. This library supports the automatic creation and removal of a bridge network with a unique name.

### How

`withZalenium` accepts now an optional network name the Zalenium container can attach to the given network. Conveniently a docker network can be created with this pipeline step which provides the dynamically created network name.

```
    withDockerNetwork { networkName ->
        def yourConfig = [:]
        withZalenium(yourConfig, networkName) {}
            docker.image("foo/bar:1.2.3").withRun("--network ${network}") {
            ...
    }

```

## Working with Truststores

Often comes a Truststore into play while working with Jenkins and Java. Jenkins can accommodate necessary certificates in its truststore so Java applications like Maven (and others too!) can successfully interact with other parties, like download artifacts from artifact repositories or transport data over the network. Even so, it may be necessary to provide these Java applications with the right certificates when otherwise encrypted communication would fail without doing so.

## Simple Truststore pipeline

For such circumstances this library provides a small snippet. The global `truststore` variable ensures that any truststore files which are copied in the process are also removed at the end of both `copy` and `use` actions.

In order to successfully provide a truststore to any Java process this sequence must be in order:

1. copy the truststore with `truststore.copy()`   
1. use the copied truststore with `truststore.use { truststoreFile -> }`

Here is a more elaborate example:
 
```
Library ('zalenium-build-lib') _

node 'master' {
    truststore.copy()
}
node 'docker' {
    truststore.use { truststoreFile ->
        javaOrMvn "-Djavax.net.ssl.trustStore=${truststoreFile} -Djavax.net.ssl.trustStorePassword=changeit"
    }
}
```

## Alternative Ways of Configuration

It is possible to supply a different truststore than the Jenkins one. Also it is possible to provide a different name in order to avoid filename collision:

```
Library ('zalenium-build-lib') _

node('master') {
    truststore.copy('/path/to/alternative/truststore/file.jks')
}
node('anotherOne') {
    //truststore.use ... as usual
}
```

## Locking

Right now, only one Job can run Zalenium Tests at a time.
This could be improved in the future. 

### Why?

When multiple jobs executed we faced non-deterministic issues that the zalenium container was gone all of a sudden, 
connections were aborted or timed out.

So we implemented a lock before starting zalenium that can only be passed by one job at a time.
It feels like this issue is gone now, but we're not sure if the lock was the proper fix.

### How?

We use the `lock` step of the [Lockable Resources Plugin](https://wiki.jenkins.io/display/JENKINS/Lockable+Resources+Plugin).

# Troubleshooting

The logs of the zalenium container are stored in `$WORKSPACE/zalenium-docker.log`.

You can enable debug logging like so:

```groovy
withZalenium([ debugZalenium : true ]) { zaleniumIp ->
        // Run your build, passing ${zaleniumIp} to it
 }
```

# Examples

* [cloudogu/spring-petclinic](https://github.com/cloudogu/spring-petclinic/blob/548db42f320f0f9065876c588c93754beffacc36/Jenkinsfile) (Java / Maven)
* [cloudogu/nexus](https://github.com/cloudogu/nexus/blob/434c8c3ebef740cd887462aad1292971c46d883e/Jenkinsfile)  (JavaScript / yarn)
* [cloudogu/jenkins](https://github.com/cloudogu/jenkins/blob/3bc8b6ab406477e0c3bb232e05f745b1fc91ba70/Jenkinsfile)  (JavaScript / yarn)
* [cloudogu/redmine](https://github.com/cloudogu/redmine/blob/740dd3a99a8111c31e4ada1ccb3023d84d6d205f/Jenkinsfile)  (JavaScript / yarn)
* [cloudogu/scm](https://github.com/cloudogu/scm/blob/4f1c998425e175a7a52f97dff5b78e82f244a9bf/Jenkinsfile)  (JavaScript / yarn)
* [cloudogu/sonar](https://github.com/cloudogu/sonar/blob/3488a6e7e38ee5e2e8fde807de570028e15835a1/Jenkinsfile)  (JavaScript / yarn)