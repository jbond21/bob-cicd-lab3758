// ═══════════════════════════════════════════════════════════════════
// Base Pipeline (Jenkinsfile)
//
// Starting point for the workshop. Provisions a Kubernetes
// agent pod with three containers — build-tools (Maven + JDK17),
// oc-tools (OpenShift CLI), and bob (IBM Bob CLI) — sharing a
// workspace-volume emptyDir mounted at /workspace on all three,
// with HOME set to /workspace so Bob can read .bob/custom_modes.yaml
// from the checkout.
//
// This file has a single Checkout stage. Each lab adds one more
// stage beneath it; see labs/LAB<N>_*.md for instructions.
//
// Before running: if your OpenShift project is NOT named `jenkins`,
// update the `bob` container image URL below.
// ═══════════════════════════════════════════════════════════════════
pipeline {
    agent {
        kubernetes {
            yaml """
apiVersion: v1
kind: Pod
spec:
  serviceAccountName: jenkins
  containers:
  - name: build-tools
    image: maven:3.9-eclipse-temurin-17
    command: ['sleep', 'infinity']
    workingDir: /workspace
    volumeMounts:
    - name: workspace-volume
      mountPath: /workspace
    env:
    - name: HOME
      value: /workspace
  - name: oc-tools
    image: quay.io/openshift/origin-cli:latest
    command: ['sleep', 'infinity']
    workingDir: /workspace
    volumeMounts:
    - name: workspace-volume
      mountPath: /workspace
    env:
    - name: HOME
      value: /workspace
  - name: lint-tools
    image: image-registry.openshift-image-registry.svc:5000/jenkins/lint-tools:latest
    command: ['sleep', 'infinity']
    workingDir: /workspace
    volumeMounts:
    - name: workspace-volume
      mountPath: /workspace
    env:
    - name: HOME
      value: /workspace
  - name: bob
    image: image-registry.openshift-image-registry.svc:5000/jenkins/bob-cli:latest
    command: ['sleep', 'infinity']
    workingDir: /workspace
    volumeMounts:
    - name: workspace-volume
      mountPath: /workspace
    env:
    - name: BOBSHELL_API_KEY
      valueFrom:
        secretKeyRef:
          name: bob-cli-credentials
          key: BOBSHELL_API_KEY
    - name: BOB_ACCEPT_LICENSE
      value: "true"
    - name: HOME
      value: /workspace
  volumes:
  - name: workspace-volume
    emptyDir: {}
"""
            defaultContainer 'build-tools'
        }
    }

    stages {
        stage('Checkout') {
            steps {
                echo "=== Checking out repository ==="
                checkout scm

                echo "=== Verifying workspace contents ==="
                sh '''
                    echo "Current directory: $(pwd)"
                    echo "Workspace contents:"
                    ls -la
                '''
            }
        }

        // ── Lab 1: PR / Git Diff Review ──────────────────────────
        stage('PR Review') {
            options {
                timeout(time: 5, unit: 'MINUTES')
            }
            steps {
                script {
                    catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                        // Default container (build-tools) has git; fall back to an empty diff
                        sh '''
                            git config --global --add safe.directory "$WORKSPACE"
                            git diff origin/main...HEAD > git-diff.txt || : > git-diff.txt
                        '''

                        def analysis = askBob(
                            "Read git-diff.txt and produce the senior-developer PR overview.",
                            'pipeline-git-diff-overview'
                        )

                        echo '════════════════════════════════════════════════════════'
                        echo '  Bob — PR Review'
                        echo '════════════════════════════════════════════════════════'
                        echo analysis
                        echo '════════════════════════════════════════════════════════'

                        writeFile file: 'bob-pr-review.md', text: analysis
                    }
                }
            }
            post {
                always {
                    archiveArtifacts artifacts: 'bob-pr-review.md', allowEmptyArchive: true
                }
            }
        }

        // ── Lab 2: Unit Testing ──────────────────────────────────
        stage('Unit Tests') {
            options {
                timeout(time: 10, unit: 'MINUTES')
            }
            steps {
                script {
                    // Run tests; || true keeps the stage alive so we can publish
                    // results and run Bob analysis regardless of outcome.
                    def mvnResult = sh(
                        script: 'mvn -f order-service/pom.xml test -B 2>&1 | tee order-service/mvn-test.log; exit ${PIPESTATUS[0]}',
                        returnStatus: true
                    )

                    // Always publish JUnit results (even on failure)
                    junit testResults: 'order-service/target/surefire-reports/*.xml',
                          allowEmptyResults: true

                    // Only invoke Bob when tests actually failed
                    if (mvnResult != 0) {
                        catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                            def prompt = """\
You are analyzing a Maven Surefire test run that reported failures.

Read the XML reports under order-service/target/surefire-reports/ to identify \
every failing test method, then read the relevant source files under \
order-service/src/ to understand the production code and the test code involved.

For each failure provide:
1. Test class and method name
2. Failure type and message
3. Root cause (one sentence)
4. Suggested fix (code snippet if helpful)

Group related failures together and end with a short prioritised action list."""

                            def analysis = askBob(prompt, 'pipeline-test-failure-analyzer')

                            echo '════════════════════════════════════════════════════════'
                            echo '  Bob — Test Failure Analysis'
                            echo '════════════════════════════════════════════════════════'
                            echo analysis
                            echo '════════════════════════════════════════════════════════'

                            writeFile file: 'bob-test-analysis.md', text: analysis
                        }
                    }
                }
            }
            post {
                always {
                    archiveArtifacts artifacts: 'bob-test-analysis.md', allowEmptyArchive: true
                }
            }
        }

        // ── Lab 3: Security Scanning ─────────────────────────────
        stage('Security Analysis') {
            options {
                timeout(time: 15, unit: 'MINUTES')
            }
            environment {
                SONAR_TOKEN    = credentials('sonarqube-token')
                SONAR_HOST_URL = 'https://sonarqube-sonarqube.apps.itz-y6rg5f.infra01-lb.wdc07.techzone.ibm.com'
                PROJECT_KEY    = "order-service-${env.USER}-${env.BUILD_NUMBER}"
            }
            steps {
                script {
                    // Serializable results map — String/Integer/Boolean only
                    def securityResults = [
                        sonarqube : [:],
                        trivy     : [critical: 0, high: 0, medium: 0],
                        riskLevel : 'UNKNOWN',
                        riskScore : 0,
                        decision  : 'PROCEED'
                    ]

                    // ─────────────────────────────────────────────────────
                    // Phase 1 — SonarQube Security Scan
                    // ─────────────────────────────────────────────────────
                    echo '════════════════════════════════════════════════════════'
                    echo '  🔍 Phase 1: SonarQube Security Scan'
                    echo '════════════════════════════════════════════════════════'

                    catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                        // Run Maven → SonarQube
                        sh """
                            mvn -f order-service/pom.xml test-compile \
                                org.sonarsource.scanner.maven:sonar-maven-plugin:sonar \
                                -Dsonar.projectKey=${PROJECT_KEY} \
                                -Dsonar.projectName="Order Service ${env.USER}" \
                                -Dsonar.host.url=${SONAR_HOST_URL} \
                                -Dsonar.token=${SONAR_TOKEN} \
                                -Dsonar.java.source=17 \
                                -B 2>&1 | tee order-service/sonar-scan.log
                        """

                        // Extract the task ID from the scan log so we can poll it
                        def taskId = sh(
                            script: "grep -o 'task?id=[^&\"]*' order-service/sonar-scan.log | head -1 | cut -d= -f2 || true",
                            returnStdout: true
                        ).trim()

                        echo "  SonarQube task ID: ${taskId ?: '(not found — will poll by component)'}"

                        // ── Phase 1a: Wait for task completion ───────────
                        echo '  ⏳ Phase 1a: Waiting for SonarQube analysis task to complete...'
                        def taskDone = false
                        for (int i = 0; i < 12 && !taskDone; i++) {
                            try {
                                def statusJson = sh(
                                    script: """curl -sf -u "${SONAR_TOKEN}:" \
                                        "${SONAR_HOST_URL}/api/ce/component?component=${PROJECT_KEY}" || echo '{"queue":[],"current":{"status":"PENDING"}}'""",
                                    returnStdout: true
                                ).trim()
                                def taskStatus = sh(
                                    script: """echo '${statusJson.replace("'", "'\\''")}' | grep -o '"status":"[^"]*"' | head -1 | cut -d'"' -f4 || true""",
                                    returnStdout: true
                                ).trim()
                                echo "    Attempt ${i+1}/12 — task status: ${taskStatus ?: 'PENDING'}"
                                if (taskStatus == 'SUCCESS') {
                                    taskDone = true
                                } else if (taskStatus == 'FAILED' || taskStatus == 'CANCELED') {
                                    echo "  ⚠️  SonarQube task ${taskStatus} — skipping metrics fetch"
                                    taskDone = true   // break the loop; metrics fetch will be skipped
                                } else {
                                    sleep(10)
                                }
                            } catch (Exception e) {
                                echo "    Polling attempt ${i+1} failed: ${e.message}"
                                sleep(10)
                            }
                        }

                        // ── Phase 1b: Fetch metrics ───────────────────────
                        echo '  📊 Phase 1b: Fetching SonarQube metrics...'
                        def metricsFetched = false
                        for (int j = 0; j < 6 && !metricsFetched; j++) {
                            try {
                                def metricsJson = sh(
                                    script: """curl -sf -u "${SONAR_TOKEN}:" \
                                        "${SONAR_HOST_URL}/api/measures/component?component=${PROJECT_KEY}&metricKeys=bugs,vulnerabilities,code_smells,security_hotspots,security_rating,reliability_rating,security_review_rating" || echo '{}'""",
                                    returnStdout: true
                                ).trim()

                                // Parse with Groovy JsonSlurper (no jq needed)
                                def sonarData = new groovy.json.JsonSlurper().parseText(metricsJson)
                                if (sonarData?.component?.measures && sonarData.component.measures.size() > 0) {
                                    sonarData.component.measures.each { m ->
                                        securityResults.sonarqube[(String) m.metric] = (String) (m.value ?: '0')
                                    }
                                    metricsFetched = true
                                    echo "    Metrics fetched successfully (${sonarData.component.measures.size()} metrics)"
                                } else {
                                    echo "    Attempt ${j+1}/6 — measures not ready yet, retrying..."
                                    sleep(5)
                                }
                                sonarData = null   // clear non-serializable object
                            } catch (Exception e) {
                                echo "    Metrics fetch attempt ${j+1} failed: ${e.message}"
                                sleep(5)
                            }
                        }

                        // Display metrics
                        echo "  ✅ SonarQube results:"
                        echo "     Bugs:              ${securityResults.sonarqube.bugs            ?: 'N/A'}"
                        echo "     Vulnerabilities:   ${securityResults.sonarqube.vulnerabilities ?: 'N/A'}"
                        echo "     Code Smells:       ${securityResults.sonarqube.code_smells     ?: 'N/A'}"
                        echo "     Security Hotspots: ${securityResults.sonarqube.security_hotspots ?: 'N/A'}"
                        echo "     Security Rating:   ${securityResults.sonarqube.security_rating   ?: 'N/A'}"
                        echo "     Reliability:       ${securityResults.sonarqube.reliability_rating ?: 'N/A'}"
                    }

                    // ─────────────────────────────────────────────────────
                    // Phase 2 — Grype Vulnerability Scan
                    // ─────────────────────────────────────────────────────
                    echo '════════════════════════════════════════════════════════'
                    echo '  🛡️  Phase 2: Grype Vulnerability Scan'
                    echo '════════════════════════════════════════════════════════'

                    catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                        container('build-tools') {
                            echo '  📥 Installing Grype...'
                            sh 'curl -sSfL https://raw.githubusercontent.com/anchore/grype/main/install.sh | sh -s -- -b /usr/local/bin'

                            echo '  🔎 Running Grype scan on order-service...'
                            sh '''
                                cd order-service
                                grype dir:. --scope all-layers -o json > grype-report.json 2>&1 || true
                            '''

                            // Count severities without jq
                            securityResults.trivy.critical = sh(
                                script: 'grep -o \'"severity":"Critical"\' order-service/grype-report.json 2>/dev/null | wc -l | tr -d " " || echo 0',
                                returnStdout: true
                            ).trim().toInteger()
                            securityResults.trivy.high = sh(
                                script: 'grep -o \'"severity":"High"\' order-service/grype-report.json 2>/dev/null | wc -l | tr -d " " || echo 0',
                                returnStdout: true
                            ).trim().toInteger()
                            securityResults.trivy.medium = sh(
                                script: 'grep -o \'"severity":"Medium"\' order-service/grype-report.json 2>/dev/null | wc -l | tr -d " " || echo 0',
                                returnStdout: true
                            ).trim().toInteger()
                        }

                        echo "  ✅ Grype results:"
                        echo "     Critical CVEs: ${securityResults.trivy.critical}"
                        echo "     High CVEs:     ${securityResults.trivy.high}"
                        echo "     Medium CVEs:   ${securityResults.trivy.medium}"
                    }

                    // ─────────────────────────────────────────────────────
                    // Phase 3 — Bob CVE Analysis (conditional)
                    // ─────────────────────────────────────────────────────
                    def totalCves = securityResults.trivy.critical + securityResults.trivy.high + securityResults.trivy.medium
                    if (totalCves > 0) {
                        echo '════════════════════════════════════════════════════════'
                        echo '  🤖 Phase 3: Bob CVE & Security Analysis'
                        echo '════════════════════════════════════════════════════════'

                        catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                            def bobPrompt = """\
You are a senior application security engineer. Analyze the following security scan results for the order-service application.

SONARQUBE RESULTS:
- Bugs: ${securityResults.sonarqube.bugs ?: '0'}
- Vulnerabilities: ${securityResults.sonarqube.vulnerabilities ?: '0'}
- Code Smells: ${securityResults.sonarqube.code_smells ?: '0'}
- Security Hotspots: ${securityResults.sonarqube.security_hotspots ?: '0'}
- Security Rating: ${securityResults.sonarqube.security_rating ?: 'N/A'}
- Reliability Rating: ${securityResults.sonarqube.reliability_rating ?: 'N/A'}

GRYPE VULNERABILITY SCAN:
- Critical CVEs: ${securityResults.trivy.critical}
- High CVEs:     ${securityResults.trivy.high}
- Medium CVEs:   ${securityResults.trivy.medium}
- Total:         ${totalCves}

Please provide:
1. Overall risk assessment (CRITICAL / HIGH / MEDIUM / LOW)
2. Top 3 most dangerous vulnerabilities with CVE IDs if available
3. Concrete remediation steps for each top vulnerability
4. Deployment recommendation — respond with exactly one of:
   RECOMMENDATION: BLOCK  (if critical risk, do not deploy)
   RECOMMENDATION: WARN   (if high risk, deploy with caution)
   RECOMMENDATION: PROCEED (if medium/low risk, safe to deploy)
"""
                            def cveAnalysis = askBob(bobPrompt, null)

                            echo '════════════════════════════════════════════════════════'
                            echo '  Bob — CVE Analysis'
                            echo '════════════════════════════════════════════════════════'
                            echo cveAnalysis
                            echo '════════════════════════════════════════════════════════'

                            writeFile file: 'bob-cve-analysis.txt', text: cveAnalysis

                            // Extract deployment recommendation
                            if (cveAnalysis.contains('RECOMMENDATION: BLOCK')) {
                                securityResults.decision = 'BLOCK'
                            } else if (cveAnalysis.contains('RECOMMENDATION: WARN')) {
                                securityResults.decision = 'WARN'
                            } else {
                                securityResults.decision = 'PROCEED'
                            }
                        }
                    } else {
                        echo '  ✅ Phase 3: No CVEs found — skipping Bob CVE analysis'
                    }

                    // ─────────────────────────────────────────────────────
                    // Phase 4 — Risk Level Calculation
                    // ─────────────────────────────────────────────────────
                    echo '════════════════════════════════════════════════════════'
                    echo '  📈 Phase 4: Risk Level Calculation'
                    echo '════════════════════════════════════════════════════════'

                    def sonarBugs            = Integer.parseInt(securityResults.sonarqube.bugs            ?: '0')
                    def sonarVulnerabilities = Integer.parseInt(securityResults.sonarqube.vulnerabilities ?: '0')
                    def sonarHotspots        = Integer.parseInt(securityResults.sonarqube.security_hotspots ?: '0')

                    def riskScore = sonarVulnerabilities * 10 +
                                    sonarHotspots        *  5 +
                                    sonarBugs            *  2 +
                                    securityResults.trivy.critical * 20 +
                                    securityResults.trivy.high     * 10

                    def riskLevel = riskScore >= 50 ? 'CRITICAL' :
                                    riskScore >= 30 ? 'HIGH'     :
                                    riskScore >= 10 ? 'MEDIUM'   : 'LOW'

                    securityResults.riskScore = riskScore
                    securityResults.riskLevel = riskLevel

                    echo "  Risk score:  ${riskScore}"
                    echo "  Risk level:  ${riskLevel}"

                    // ─────────────────────────────────────────────────────
                    // Phase 5 — Deployment Decision
                    // ─────────────────────────────────────────────────────
                    echo '════════════════════════════════════════════════════════'
                    echo '  🚦 Phase 5: Deployment Decision'
                    echo '════════════════════════════════════════════════════════'

                    if (riskLevel == 'CRITICAL') {
                        securityResults.decision = 'BLOCK'
                        echo '  🔴 DECISION: BLOCK — Critical risk detected. Deployment blocked.'
                        echo '     Resolve all critical/high vulnerabilities before deploying.'
                        currentBuild.result = 'UNSTABLE'
                    } else if (riskLevel == 'HIGH') {
                        if (securityResults.decision != 'BLOCK') { securityResults.decision = 'WARN' }
                        echo '  🟠 DECISION: WARN — High risk. Manual security review recommended before deploying.'
                    } else {
                        if (securityResults.decision == 'UNKNOWN') { securityResults.decision = 'PROCEED' }
                        echo "  🟢 DECISION: PROCEED — Risk level ${riskLevel}. Approved with standard monitoring."
                    }

                    // ─────────────────────────────────────────────────────
                    // Phase 6 — Consolidated Markdown Report
                    // ─────────────────────────────────────────────────────
                    echo '════════════════════════════════════════════════════════'
                    echo '  📄 Phase 6: Generating Security Report'
                    echo '════════════════════════════════════════════════════════'

                    def decisionEmoji = securityResults.decision == 'BLOCK'   ? '🔴' :
                                        securityResults.decision == 'WARN'    ? '🟠' : '🟢'

                    def bobAnalysisSection = fileExists('bob-cve-analysis.txt') ?
                        readFile('bob-cve-analysis.txt') :
                        '_No CVEs detected — Bob CVE analysis was not required._'

                    def report = """\
# Security Analysis Report

**Project:** ${PROJECT_KEY}
**Build:**   ${env.BUILD_NUMBER}
**Date:**    ${new Date()}
**Branch:**  ${env.GIT_BRANCH ?: env.BRANCH_NAME ?: 'unknown'}

---

## Executive Summary

| Metric | Value |
|---|---|
| Risk Level | **${riskLevel}** |
| Risk Score | ${riskScore} |
| Deployment Decision | ${decisionEmoji} **${securityResults.decision}** |

---

## SonarQube Results

| Metric | Value |
|---|---|
| Bugs | ${securityResults.sonarqube.bugs ?: 'N/A'} |
| Vulnerabilities | ${securityResults.sonarqube.vulnerabilities ?: 'N/A'} |
| Code Smells | ${securityResults.sonarqube.code_smells ?: 'N/A'} |
| Security Hotspots | ${securityResults.sonarqube.security_hotspots ?: 'N/A'} |
| Security Rating | ${securityResults.sonarqube.security_rating ?: 'N/A'} |
| Reliability Rating | ${securityResults.sonarqube.reliability_rating ?: 'N/A'} |

[View full SonarQube dashboard](${SONAR_HOST_URL}/dashboard?id=${PROJECT_KEY})

---

## Grype Vulnerability Scan

| Severity | Count |
|---|---|
| Critical | ${securityResults.trivy.critical} |
| High | ${securityResults.trivy.high} |
| Medium | ${securityResults.trivy.medium} |
| **Total** | **${totalCves}** |

---

## Bob CVE Analysis

${bobAnalysisSection}

---

## Deployment Recommendation

${decisionEmoji} **${securityResults.decision}**

${securityResults.decision == 'BLOCK'   ? 'Deployment is **blocked**. Resolve all critical and high-severity findings before proceeding.' :
  securityResults.decision == 'WARN'    ? 'Deployment is **permitted with caution**. A manual security review is strongly recommended.' :
  'Deployment is **approved**. Continue with standard monitoring and alerting.'}
"""
                    writeFile file: 'security-analysis-report.md', text: report
                    echo '  ✅ security-analysis-report.md written'
                    echo '════════════════════════════════════════════════════════'
                }
            }
            post {
                always {
                    archiveArtifacts artifacts: 'security-analysis-report.md,bob-cve-analysis.txt,order-service/grype-report.json',
                                     allowEmptyArchive: true
                }
            }
        }

        // ── Lab 4: Linting ───────────────────────────────────────
        //    Add a lint stage + Bob lint analysis + parse and post
        //    Jenkins report as a PR comment.
        //    See labs/lab4/LINTING.md.

    }

    post {
        always {
            echo "=== Pipeline Complete ==="
            echo "Result: ${currentBuild.result ?: 'SUCCESS'}"
        }
    }
}

// ── Helper: ask Bob, optionally with a specific custom mode ───────────────────
// Writes the prompt to a tempfile in the shared workspace and runs `bob` in
// the bob container.
// Returns the analysis as a string. Using a tempfile (instead of inlining the
// prompt on the command line) avoids shell-escaping issues when the prompt
// contains quotes, backticks, or newlines — common with diffs.
def askBob(String prompt, String mode = null) {
    container('bob') {
        def promptFile = ".bob-prompt-${System.currentTimeMillis()}.txt"
        writeFile file: promptFile, text: prompt

        def modeFlag = mode ? "--mode ${mode}" : ""
        def analysis = sh(
            script: """cat ${promptFile} | bob run ${modeFlag} --accept-license""",
            returnStdout: true
        ).trim()

        sh "rm -f ${promptFile}"
        return analysis
    }
}
