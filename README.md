![Cloudogu logo](https://cloudogu.com/images/logo.png)
zalenium-build-lib
=======

Jenkins shared library that provides a step that runs a temporary [zalenium server](https://github.com/zalando/zalenium) 
in a via docker, that records videos of selenium tests. The videos are archived at the Jenkins job by this library.

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

# Troubleshooting

The logs of the zalenium container are stored in $WORKSPACE/zalenium-docker.log.

You can enable debug logging like so:

```groovy
withZalenium([ debugZalenium : true ]) { zaleniumIp ->
        // Run your build, passing ${zaleniumIp} to it
 }
```

# Examples

* [cloudogu/spring-petclinic](https://github.com/cloudogu/spring-petclinic/blob/548db42f320f0f9065876c588c93754beffacc36/Jenkinsfile) (Java / Maven)
* [cloudogu/nexus](https://github.com/cloudogu/nexus/blob/434c8c3ebef740cd887462aad1292971c46d883e/Jenkinsfile)  (JavaScript / yarn)