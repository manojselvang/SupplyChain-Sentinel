# Jenkins Integration

This guide shows how to submit build metadata from Jenkins to SupplyChain Sentinel.

## Prerequisites

- SupplyChain Sentinel backend running and accessible from Jenkins
- An `applicationKey` matching a registered application in the inventory
- A Jenkins Credential storing the ingestion API key

## API contract

**Endpoint:** `POST /api/v1/integrations/pipeline-runs`  
**Auth header:** `X-CI-INGESTION-KEY: <key>`  
**Content-Type:** `application/json`

The same endpoint is used by Jenkins and GitHub Actions — there is no separate backend per provider.

## Jenkins Credential setup

1. In Jenkins → **Manage Jenkins → Credentials → System → Global credentials**
2. Add a **Secret text** credential
3. Set **ID** to `scs-ingestion-key`
4. Paste the value of `CI_INGESTION_API_KEY` from the backend configuration

## Jenkinsfile

```groovy
pipeline {
    agent any

    environment {
        SCS_URL         = 'http://supplychain-sentinel:8080'
        SCS_APP_KEY     = 'my-application'  // matches applicationKey in inventory
    }

    stages {
        stage('Notify — RUNNING') {
            steps {
                withCredentials([string(credentialsId: 'scs-ingestion-key', variable: 'SCS_KEY')]) {
                    script {
                        def startedAt = new Date().toInstant().toString()
                        env.BUILD_STARTED_AT = startedAt

                        def payload = groovy.json.JsonOutput.toJson([
                            applicationKey: env.SCS_APP_KEY,
                            provider      : 'JENKINS',
                            externalRunId : env.BUILD_NUMBER,
                            workflowName  : env.JOB_NAME,
                            repositoryUrl : scm.getUserRemoteConfigs()[0].getUrl(),
                            branchName    : env.GIT_BRANCH?.replaceFirst('origin/', '') ?: env.BRANCH_NAME,
                            commitSha     : env.GIT_COMMIT,
                            commitMessage : sh(script: 'git log -1 --pretty=%s', returnStdout: true).trim(),
                            runNumber     : env.BUILD_NUMBER,
                            triggerEvent  : env.BUILD_CAUSE ?: 'manual',
                            status        : 'RUNNING',
                            startedAt     : startedAt,
                        ])

                        sh """
                            curl -sf -X POST "${SCS_URL}/api/v1/integrations/pipeline-runs" \\
                              -H 'Content-Type: application/json' \\
                              -H "X-CI-INGESTION-KEY: \${SCS_KEY}" \\
                              -d '${payload}' || echo 'SCS notification failed (non-blocking)'
                        """
                    }
                }
            }
        }

        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build') {
            steps {
                sh 'mvn clean package -DskipTests -B'
            }
        }

        stage('Test') {
            steps {
                sh 'mvn test -B'
            }
        }
    }

    post {
        always {
            withCredentials([string(credentialsId: 'scs-ingestion-key', variable: 'SCS_KEY')]) {
                script {
                    def finalStatus = currentBuild.result == 'SUCCESS' ? 'SUCCESS' : 'FAILED'
                    def completedAt = new Date().toInstant().toString()

                    // Calculate artifact digest when present
                    def digest = ''
                    if (fileExists('target/app.jar')) {
                        def sha256 = sh(
                            script: "sha256sum target/app.jar | awk '{print \$1}'",
                            returnStdout: true
                        ).trim()
                        if (sha256 ==~ /[a-fA-F0-9]{64}/) digest = sha256
                    }

                    def payload = groovy.json.JsonOutput.toJson([
                        applicationKey : env.SCS_APP_KEY,
                        provider       : 'JENKINS',
                        externalRunId  : env.BUILD_NUMBER,
                        workflowName   : env.JOB_NAME,
                        repositoryUrl  : scm.getUserRemoteConfigs()[0].getUrl(),
                        branchName     : env.GIT_BRANCH?.replaceFirst('origin/', '') ?: env.BRANCH_NAME,
                        commitSha      : env.GIT_COMMIT,
                        runNumber      : env.BUILD_NUMBER,
                        status         : finalStatus,
                        startedAt      : env.BUILD_STARTED_AT,
                        completedAt    : completedAt,
                        artifactName   : 'app.jar',
                        artifactDigest : digest ?: null,
                    ])

                    sh """
                        curl -sf -X POST "${SCS_URL}/api/v1/integrations/pipeline-runs" \\
                          -H 'Content-Type: application/json' \\
                          -H "X-CI-INGESTION-KEY: \${SCS_KEY}" \\
                          -d '${payload}' || echo 'SCS notification failed (non-blocking)'
                    """
                }
            }
        }
    }
}
```

## Notes

- The `RUNNING` call and the final `post { always }` call share the same `externalRunId` (Jenkins build number + job name). The second call updates the existing record — it does **not** create a duplicate.
- The `curl` calls use `|| echo ...` so a SupplyChain Sentinel outage does not block the Jenkins build.
- The credential value is bound to `SCS_KEY` and never echoed or printed in logs.
- Adjust `SCS_URL` and `SCS_APP_KEY` via Jenkins environment variables or parameters for multi-application pipelines.
