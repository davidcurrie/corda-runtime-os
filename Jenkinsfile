@Library('corda-shared-build-pipeline-steps@ronanb/testTestsIssue') _

cordaPipeline(
    nexusAppId: 'flow-worker-5.0',
    runIntegrationTests: true,
    publishRepoPrefix: 'corda-ent-maven',
    createPostgresDb: false,
    publishOSGiImage: true,
    publishPreTestImage: true,
    publishHelmChart: true,
    e2eTestName: 'corda-runtime-os-e2e-tests',
    runE2eTests: true
    )
