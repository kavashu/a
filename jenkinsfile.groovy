import groovy.json.JsonOutput

properties(
    [
        parameters(
            [
                string(name: 'distributionUrl',defaultValue: 'https://shubhamdevops1.jfrog.io/', description: 'Distribution server URL'),
                string(name: 'releaseBundleName',defaultValue: 'myteam-project-bundle', description: 'release bundle name for distribution')
            ]
        )
    ]
)

timestamps {

    node("test") {

        def server
        def rtFullUrl
        def rtIpAddress
        def buildNumber
        def mavenBuildName
        def dockerBuildName
        def buildInfo
        def stagingPromotionRepo = 'myteam-maven-stage-local'
        def prodPromotionRepo = 'myteam-maven-prod-local'
        def distributionUrl = params.distributionUrl
        def releaseBundleName = params.releaseBundleName
        def pipelineUtils
        def artifactoryCredentialId = 'jfrog'
withCredentials([string(credentialsId: 'jfrog', variable: 'jfrogCred')]) {
        stage("Checkout") {
            checkout scm
        }
  
        stage("Preparations") {
            buildNumber = env.BUILD_NUMBER
            def jobName = env.JOB_NAME

            echo '==================='
            echo 'pwd:'
            sh 'pwd'
            echo 'ls:'
            sh "ls -la"
            echo "build number: ${buildNumber}"
            echo "job name: ${jobName}"
            echo "==================="

            mavenBuildName = "maven-${jobName}"
            dockerBuildName = "docker-${jobName}"
            server = Artifactory.server "jfrog"
            server.username="shubham.devops.1@gmail.com"
            server.password="AKCp8kqX8yfBdbhcyNwvGdQU9ZYw3vVR7R1Zrc4ctmTgfLFA1PkphskFxKtzLoyKYau1ah6Cn"
            rtFullUrl = server.url      
            rtIpAddress = rtFullUrl - ~/^http?.:\/\// - ~/\/artifactory$/
            pipelineUtils = load 'pipelineUtils.groovy'
        }

        stage("create build context"){
            
        }

        stage("Build+Deploy") {
            def rtMaven = Artifactory.newMavenBuild()
            rtMaven.deployer server: server, releaseRepo: 'demo-libs-release', snapshotRepo: 'demo-libs-snapshot'
            rtMaven.tool = 'mavenTool'
            String mvnGoals = "clean install -DartifactVersion=${buildNumber} -s settings.xml"
            buildInfo = Artifactory.newBuildInfo()
            buildInfo.name = mavenBuildName
            buildInfo.env.collect()
            rtMaven.run pom: 'pomForEplus.xml', goals: mvnGoals, buildInfo: buildInfo
            server.publishBuildInfo buildInfo
        }

        stage("static code analysis") {
            //generate sleeping time between 2 to 7 seconds
            def sleepingTime = Math.abs(new Random().nextInt() % ([7] - [2])) + [2]
            sleep(sleepingTime)
        }

        stage("Promote to stage") {

            def promotionConfig = [
                    'buildName'  : buildInfo.name,
                    'buildNumber': buildInfo.number,
                    'targetRepo' : stagingPromotionRepo,
                    'comment'    : 'This is a stable java-project version',
                    'status'     : 'Released',
                    'sourceRepo' : 'demo-libs-snapshot-local',
                    'copy'       : true,
                    'failFast'   : true
            ]

            server.promote promotionConfig

        }

        stage("Scan") {

            def scanConfig = [
                    'buildName'  : buildInfo.name,
                    'buildNumber': buildInfo.number,
                    'failBuild'  : true
            ]
            //server.xrayScan scanConfig
        }

        stage("Promote to prod") {

            def promotionConfig = [
                    'buildName'  : buildInfo.name,
                    'buildNumber': buildInfo.number,
                    'targetRepo' : prodPromotionRepo,
                    'comment'    : 'This is a release ready java-project version',
                    'status'     : 'Scanned',
                    'sourceRepo' : 'demo-libs-snapshot-local',
                    'copy'       : true,
                    'failFast'   : true
            ]

            server.promote promotionConfig

        }
/*  
        stage("Build docker image") {
            def dockerBuildInfo = Artifactory.newBuildInfo()
            dockerBuildInfo.name = dockerBuildName
            def downloadSpec = """{
             "files": [
              {
                  "pattern": "myteam-maven-dev-local/com/myjfrog/demo/${buildNumber}-SNAPSHOT/demo-*.jar",
                  "target": "/myzips/downloads/",
                  "flat": "true"
                }
             ]
            }"""

            server.download spec: downloadSpec, buildInfo: dockerBuildInfo
            def rtDocker = Artifactory.docker server: server


            echo '==================='
            echo 'pwd:'
            sh 'pwd'
            echo 'ls:'
            sh "ls -la /myzips/downloads"
            echo "===================${rtIpAddress}"

            
            def dockerImageTag = "${rtIpAddress}/docker:${buildNumber}"
            docker.build(dockerImageTag, "-f demo/Dockerfile --build-arg DOCKER_REGISTRY_URL=${rtIpAddress} .")
            dockerBuildInfo.env.collect()
            rtDocker.push(dockerImageTag, 'docker', dockerBuildInfo)
            server.publishBuildInfo dockerBuildInfo
        }
*/
/*
        stage("Scan docker image") {
            
            def dockerScanConfig = [
                    'buildName'  : dockerBuildInfo.name,
                    'buildNumber': dockerBuildInfo.number,
                    'failBuild'  : true
            ]
            server.xrayScan dockerScanConfig
        }
*/        
/*
        stage("Promote docker image") {

            def dockerPromotionConfig = [
                    'buildName'  : dockerBuildInfo.name,
                    'buildNumber': dockerBuildInfo.number,
                    'targetRepo' : 'stable-docker-repo',
                    'comment'    : 'This is a stable java-project docker image',
                    'status'     : 'Released',
                    'sourceRepo' : 'docker-repo',
                    'copy'       : true,
                    'failFast'   : true
            ]

            server.promote dockerPromotionConfig
 
        }
*/        
//*
        stage("Create release bundle") {
            echo "${rtFullUrl}/api/system/service_id"
            //rtServiceId = pipelineUtils.restGet("https://shubhamdevops1.jfrog.io/artifactory/api/system/service_id?", "${jfrogCred}")


            // def aqlQuery = 'items.find({"repo":{"$match":"myteam-*-prod-local"}})'
            // def aqlQuery = 'items.find( { "$and":[ { "repo":{"$match":"myteam-*-prod-local"} }, { "@build.number":"27" } ] } )'
            def aqlQuery = """items.find( { \"\$and\":[ { \"repo\":{\"\$match\":\"myteam-*-prod-local\"} }, { \"@build.number\":\"${buildNumber}\" } ] } )"""
            aqlQuery = aqlQuery.replaceAll(" ", "").replaceAll("\n", "")

            def releaseBundleBody = [
                    'name': "${releaseBundleName}",
                    'version': "${buildNumber}",
                    'dry_run': false,
                    'sign_immediately': true,
                    'description': 'Release bundle for the example java-project',
                    'release_notes': [
                        'syntax': "plain_text",
                        'content': "myteam release notes are very important"
                    ],
                    'spec': [
                            'source_artifactory_id': "jfrt@01ftzpsnkmz4751h2ajvc41p8y",
                            'queries': [
                                    [
                                            'aql': "${aqlQuery}",
                                            'query_name': 'java-project-query'
                                    ]
                            ]
                    ]
            ]

            releaseBundleBodyJson = JsonOutput.toJson(releaseBundleBody)
// echo "===========after JSON creation============="

// echo "${releaseBundleBodyJson}"

// echo "===========after JSON output============="
            res = pipelineUtils.restPost("${distributionUrl}/api/v1/release_bundle","Bearer ${jfrogCred}", releaseBundleBodyJson)
 //echo "===========after rest call============="
        }


        stage('Distribute release bundle') {
            def distributeReleaseBundleBody = '{"dry_run": false, "distribution_rules": [{"service_name": "*edge*"}]}'
            res = pipelineUtils.restPost("${distributionUrl}/api/v1/distribution/${releaseBundleName}/${buildNumber}", artifactoryCredentialId, distributeReleaseBundleBody.toString())

            def jsonResult = readJSON text: res
            println "the result is: "
            println "${jsonResult}"                
        }      
    }
}

}