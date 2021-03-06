def withRetry(iterations, sleepTime, Closure closure) {
    for (i = 0; true; i++) {
        try {
            println "Try number ${i}"
            closure()
            break
        } catch (Exception e) {
            if (i < iterations) {
                sleep sleepTime
                println "Retrying..."
            } else {
                error("Exceeded number of retries. Exception was: ${e.message}")
            }
        }
    }
}

def restGet(url, credentialId, contentType = 'APPLICATION_JSON') {
    // res = httpRequest url: url, contentType: contentType, consoleLogResponseBody: true
    // res.setRequestHeader("Bearer ",credentialId)
    // println res.getStatus()
    // res.getContent()

}

def restPost(url, credentialId, body, contentType = 'APPLICATION_JSON') {
    // res = httpRequest url: url, contentType: contentType, customHeaders: [Bearer: credentialId], httpMode: 'POST', requestBody: body, consoleLogResponseBody: true
    // println res.getStatus()
    // res.getContent()
    sh "curl -x POST -u ${jfrogCred}: -d ${body} ${url} "
    //sh "cat data.json"

}

return this