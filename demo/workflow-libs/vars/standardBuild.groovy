def call(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
    stage 'checkout'
    node {
        checkout scm
        stage 'main'
        docker.image(config.environment).inside {
            sh config.mainScript
        }
        stage 'post'
        sh config.postScript
    }
}
