@Library('SonarSource@2.2') _
pipeline {
  agent {
    label 'linux'
  }
  parameters {
    string(name: 'GIT_SHA1', description: 'Git SHA1 (provided by travisci hook job)')
    string(name: 'CI_BUILD_NAME', defaultValue: 'sonar-lits', description: 'Build Name (provided by travisci hook job)')
    string(name: 'CI_BUILD_NUMBER', description: 'Build Number (provided by travisci hook job)')
    string(name: 'GITHUB_BRANCH', defaultValue: 'master', description: 'Git branch (provided by travisci hook job)')
    string(name: 'GITHUB_REPOSITORY_OWNER', defaultValue: 'SonarSource', description: 'Github repository owner(provided by travisci hook job)')
  }
  environment {
    SONARSOURCE_QA = 'true'
    MAVEN_TOOL = 'Maven 3.6.x'
    JDK_VERSION = 'Java 11'
  }
  stages {
    stage('Notify') {
      steps {
        sendAllNotificationQaStarted()
      }
    }
    stage('QA') {
      parallel {
        stage('plugin/DOGFOOD/linux') {
          agent {
            label 'linux'
          }
          steps {
            runITs("plugin","DOGFOOD")
          }
        }     
        stage('plugin/LATEST_RELEASE[7.9]/linux') {
          agent {
            label 'linux'
          }
          steps {
            runITs("plugin","LATEST_RELEASE[7.9]")
          }
        }            
        stage('plugin/LATEST_RELEASE/linux') {
          agent {
            label 'linux'
          }
          steps {
            runITs("plugin","LATEST_RELEASE")
          }
        }                               
      }         
      post {
        always {
          sendAllNotificationQaResult()
        }
      }

    }
    stage('Promote') {
      steps {
        repoxPromoteBuild()
      }
      post {
        always {
          sendAllNotificationPromote()
        }
      }
    }
  }
}

def runITs(TEST,SQ_VERSION) {    
  withMaven(maven: MAVEN_TOOL) {
    mavenSetBuildVersion()        
    gitFetchSubmodules()
    dir("its/$TEST") {    
      runMavenOrch(JDK_VERSION,"test -DjavaVersion=\"LATEST_RELEASE\" -Dsonar.runtimeVersion=$SQ_VERSION")
    }
  }
}

