@Library('corda-shared-build-pipeline-steps@test-tooling/p2p-poc') _

cordaPipeline(
    nexusAppId: 'flow-worker-5.0',
    runIntegrationTests: true,
    publishRepoPrefix: 'corda-ent-maven',
    createPostgresDb: true,
    publishOSGiImage: true,
    publishPreTestImage: true,
    e2eTestName: 'corda-runtime-os-e2e-tests',
    runE2eTests: true
    )
