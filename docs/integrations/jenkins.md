# Jenkins Integration

Complete CI/CD pipeline integration with SupplyChain Sentinel — pipeline runs, security scans, SBOM, Cosign verification, and release gate evaluation.

## Prerequisites

- SupplyChain Sentinel backend running
- Jenkins Credential `scs-ingestion-key` (Secret text) containing `CI_INGESTION_API_KEY`
- Syft, Trivy, and Cosign installed on Jenkins agents

## Complete Jenkinsfile

```groovy
pipeline {
    agent any

    environment {
        SCS_URL     = 'http://supplychain-sentinel:8080'
        SCS_APP_KEY = 'payment-service'
    }

    stages {
        stage('Checkout') { steps { checkout scm } }

        stage('Build') {
            steps {
                sh 'mvn clean package -DskipTests -B'
                sh 'mvn test -B'
            }
        }

        stage('Notify Pipeline — RUNNING') {
            steps {
                withCredentials([string(credentialsId: 'scs-ingestion-key', variable: 'SCS_KEY')]) {
                    script {
                        def startedAt = new Date().toInstant().toString()
                        env.BUILD_STARTED_AT = startedAt
                        sh """
                            curl -sf -X POST "\${SCS_URL}/api/v1/integrations/pipeline-runs" \\
                              -H 'Content-Type: application/json' \\
                              -H "X-CI-INGESTION-KEY: \${SCS_KEY}" \\
                              -d '{"applicationKey":"\${SCS_APP_KEY}","provider":"JENKINS","externalRunId":"\${BUILD_NUMBER}","workflowName":"\${JOB_NAME}","repositoryUrl":"\${GIT_URL}","branchName":"main","commitSha":"\${GIT_COMMIT}","status":"RUNNING","startedAt":"\${startedAt}"}'
                        """
                    }
                }
            }
        }

        stage('Trivy Vulnerability Scan') {
            steps {
                sh 'trivy fs --scanners vuln --format json --output trivy-report.json ./backend'
            }
        }

        stage('Generate SBOM') {
            steps {
                sh 'syft scan dir:./backend -o cyclonedx-json@1.7=sbom.cdx.json'
            }
        }

        stage('Build Container Image') {
            steps {
                script {
                    sh 'docker build -t app:latest .'
                    env.IMAGE_DIGEST = sh(script: 'docker inspect app:latest --format="{{index .RepoDigests 0}}" | grep -oP "sha256:\\K.*"', returnStdout: true).trim()
                }
            }
        }

        stage('Cosign Verification') {
            steps {
                withCredentials([string(credentialsId: 'scs-ingestion-key', variable: 'SCS_KEY')]) {
                    script {
                        def ref = "app@sha256:${env.IMAGE_DIGEST}"
                        def cosignVer = sh(script: 'cosign version | head -1 | awk \'{print $NF}\'', returnStdout: true).trim()
                        def now = new Date().toInstant().toString()

                        try {
                            sh "cosign-windows-amd64.exe verify --key cosign.pub ${ref}"
                            env.VERIFY_STATUS = "VERIFIED"
                        } catch (e) {
                            env.VERIFY_STATUS = "FAILED"
                        }

                        sh """
                            curl -sf -X POST "\${SCS_URL}/api/v1/integrations/artifact-verifications" \\
                              -H 'Content-Type: application/json' \\
                              -H "X-CI-INGESTION-KEY: \${SCS_KEY}" \\
                              -d '{"applicationKey":"\${SCS_APP_KEY}","artifactReference":"\${ref}","artifactDigest":"\${IMAGE_DIGEST}","verificationMode":"KEY","verificationStatus":"\${VERIFY_STATUS}","signerIdentity":"Jenkins CI","publicKeyFingerprint":"cosign-pub-fp","cosignVersion":"\${cosignVer}","verifiedAt":"\${now}","reportedAt":"\${now}"}'
                        """
                    }
                }
            }
        }

        stage('Submit Trivy Report') {
            steps {
                withCredentials([string(credentialsId: 'scs-ingestion-key', variable: 'SCS_KEY')]) {
                    sh """
                        jq -n --arg appKey "\${SCS_APP_KEY}" \\
                          --slurpfile report trivy-report.json \\
                          '{applicationKey:\$appKey, scanner:"trivy", reportFormat:"TRIVY_JSON", scanTimestamp:"\${BUILD_STARTED_AT}", report:\$report[0]}' \\
                        | curl -sf -X POST "\${SCS_URL}/api/v1/integrations/security-scans" \\
                          -H 'Content-Type: application/json' \\
                          -H "X-CI-INGESTION-KEY: \${SCS_KEY}" \\
                          -d @-
                    """
                }
            }
        }

        stage('Submit SBOM') {
            steps {
                withCredentials([string(credentialsId: 'scs-ingestion-key', variable: 'SCS_KEY')]) {
                    sh """
                        jq -n --arg appKey "\${SCS_APP_KEY}" \\
                          --arg digest "\${IMAGE_DIGEST}" \\
                          --slurpfile sbom sbom.cdx.json \\
                          '{applicationKey:\$appKey, artifactDigest:\$digest, sbom:\$sbom[0]}' \\
                        | curl -sf -X POST "\${SCS_URL}/api/v1/integrations/sboms" \\
                          -H 'Content-Type: application/json' \\
                          -H "X-CI-INGESTION-KEY: \${SCS_KEY}" \\
                          -d @-
                    """
                }
            }
        }

        stage('Notify Pipeline — SUCCESS') {
            steps {
                withCredentials([string(credentialsId: 'scs-ingestion-key', variable: 'SCS_KEY')]) {
                    sh """
                        curl -sf -X POST "\${SCS_URL}/api/v1/integrations/pipeline-runs" \\
                          -H 'Content-Type: application/json' \\
                          -H "X-CI-INGESTION-KEY: \${SCS_KEY}" \\
                          -d '{"applicationKey":"\${SCS_APP_KEY}","provider":"JENKINS","externalRunId":"\${BUILD_NUMBER}","workflowName":"\${JOB_NAME}","repositoryUrl":"\${GIT_URL}","branchName":"main","commitSha":"\${GIT_COMMIT}","status":"SUCCESS","startedAt":"\${BUILD_STARTED_AT}","completedAt":"\${new Date().toInstant().toString()}","artifactDigest":"\${IMAGE_DIGEST}"}'
                    """
                }
            }
        }

        stage('Evaluate Release Gate') {
            steps {
                withCredentials([string(credentialsId: 'scs-ingestion-key', variable: 'SCS_KEY')]) {
                    script {
                        def gateResponse = sh(
                            script: """
                                curl -sf -X POST "\${SCS_URL}/api/v1/integrations/release-gates/evaluate" \\
                                  -H 'Content-Type: application/json' \\
                                  -H "X-CI-INGESTION-KEY: \${SCS_KEY}" \\
                                  -d '{"applicationKey":"\${SCS_APP_KEY}","pipelineRunId":"\${BUILD_NUMBER}"}'
                            """,
                            returnStdout: true
                        ).trim()

                        def decision = readJSON(text: gateResponse).decision
                        echo "Release gate decision: ${decision}"

                        if (decision == "BLOCK") {
                            error("Release gate BLOCKED — security requirements not met")
                        }
                        // PASS: continue; WARN: requires documented manual approval
                    }
                }
            }
        }
    }
}
```

## CI Status Design

`pipeline_runs.status = SUCCESS` means the build, tests, and evidence-generation workflow completed. The release gate evaluation is a **separate** stage that reads the evidence. The gate outcome is recorded independently.

## Release Gate Handling

| Decision | CI Behavior |
|----------|-------------|
| PASS | Continue deployment |
| WARN | Require documented manual approval (human reviewer) |
| BLOCK | Fail the Jenkins build — do not deploy |
